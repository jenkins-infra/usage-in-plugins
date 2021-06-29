package org.jenkinsci.deprecatedusage;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class Downloader {
    private final ExecutorService executor;
    private final Semaphore concurrentDownloadsPermit;

    public Downloader(ExecutorService executor, int maxConcurrentDownloads) {
        this.executor = executor;
        concurrentDownloadsPermit = new Semaphore(maxConcurrentDownloads);
    }

    public Collection<JenkinsFile> useExistingFiles(Collection<JenkinsFile> files){
        final Collection<JenkinsFile> synced = ConcurrentHashMap.newKeySet(files.size());
        for (JenkinsFile file : files) {
            if (file.isFileSynchronized()) {
                synced.add(file);
            }
        }
        return synced;
    }
    
    public Future<Collection<JenkinsFile>> synchronize(Collection<JenkinsFile> files) {
        final Collection<JenkinsFile> synced = ConcurrentHashMap.newKeySet(files.size());
        final CountDownLatch latch = new CountDownLatch(files.size());
        for (JenkinsFile file : files) {
            if (file.isFileSynchronized()) {
                synced.add(file);
                latch.countDown();
            } else {
                Path path = file.getFile().toPath();
                Path parent = path.getParent();
                if (Files.notExists(parent)) {
                    try {
                        Files.createDirectories(parent);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                download(file).handle((success, failure) -> {
                    if (failure != null) {
                        // do not throw away the message!
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        pw.println("failure synching " + file.getName());
                        pw.println(failure.getMessage());
                        failure.printStackTrace(pw);
                        pw.flush();
                        System.err.println(sw.toString());
                    } else {
                        synced.add(file);
                    }
                    latch.countDown();
                    return null;
                });
            }
        }
        return executor.submit(() -> {
            latch.await();
            return synced;
        });
    }

    private CompletableFuture<Void> download(JenkinsFile file) {
        Retryable retryable = new Retryable(file);
        retryable.run();
        return retryable.result;
    }

    private class Retryable implements Runnable {
        private final AtomicInteger retriesRemaining = new AtomicInteger(2);
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private final JenkinsFile file;

        private Retryable(JenkinsFile file) {
            this.file = file;
        }

        @Override
        public void run() {
            executor.execute(() -> {
                try {
                    concurrentDownloadsPermit.acquire();
                    doRun();
                    result.complete(null);
                } catch (IOException | DigestException e) {
                    result.completeExceptionally(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.completeExceptionally(e);
                } finally {
                    concurrentDownloadsPermit.release();
                }
            });
        }

        private void doRun() throws IOException, DigestException {
            URL url = new URL(file.getUrl());
            try {
                HttpURLConnection request = (HttpURLConnection) url.openConnection();
                int responseCode = request.getResponseCode();
                if (responseCode == 502) {
                    throw new IOException("Flaky Update Center returned HTTP 502");
                } else if (responseCode >= 400) {
                    throw new HttpResponseException(responseCode, request.getResponseMessage());
                } else {
                    long fileSize;
                    try (InputStream in = request.getInputStream();
                         OutputStream out = file.getFileOutputStream()) {
                        fileSize = IOUtils.copyLarge(in, out);
                    }
                    if (file.isFileMessageDigestValid()) {
                        System.out.printf("Downloaded %s @ %.2f kiB%n", file.getUrl(), (fileSize / 1024.0));
                    } else {
                        throw new DigestException("Downloaded file message digest does not match update center for " + url);
                    }
                }
            } catch (IOException ioEx) {
                if (shouldRetryForException(ioEx) && retriesRemaining.getAndDecrement() > 0) {
                    try {
                        System.out.printf("Failed to download %s due to %s, will retry in 750ms%n", StringUtils.isEmpty(ioEx.getMessage()) ? ioEx.getClass().getName() : ioEx.getMessage() , file.getUrl());
                        Thread.sleep(7500L);
                    } catch (InterruptedException ex) {
                        IOException toThrow = new IOException("InterruptedException in sleep backoff", ex);
                        toThrow.addSuppressed(ioEx);
                        throw toThrow;
                    }
                    doRun();
                } else {
                    throw ioEx;
                }
            }
        }

        private boolean shouldRetryForException(IOException ioEx) {
            if (ioEx instanceof SocketException) {
                return true;
            }
            if ("Premature EOF".equals(ioEx.getMessage())) {
                return true;
            }
            if ("Flaky Update Center returned HTTP 502".equals(ioEx.getMessage())) {
                return true;
            }
            return false;
        }
    }
}
