package org.jenkinsci.deprecatedusage;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class JenkinsFile {
    private final String name;
    private final String version;
    private final String url;
    private final String wiki;
    private Path file;
    private final Checksum checksum;
    private final RequestBuilder requestBuilder;

    public JenkinsFile(String name, String version, String url, String wiki, Checksum checksum) {
        super();
        this.name = name;
        this.version = version;
        this.url = url;
        this.wiki = wiki;
        this.checksum = checksum;
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        file = Paths.get("work", name, version, fileName).toAbsolutePath();
        requestBuilder = Dsl.get(url);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getWiki() {
        return wiki;
    }

    public File getFile() {
        return file.toFile();
    }

    public void setFile(File file) {
        this.file = file.toPath();
    }

    public void deleteFile() throws IOException {
        Files.deleteIfExists(file);
    }

    public CompletableFuture<Void> downloadIfNotExists(AsyncHttpClient client) {
        if (Files.exists(file)) {
            return CompletableFuture.completedFuture(null);
        }
        return download(client);
    }

    private CompletableFuture<Void> download(AsyncHttpClient client) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            future.completeExceptionally(e);
            return future;
        }
        class RetryAsync implements Function<Response, CompletableFuture<Void>> {
            final AtomicInteger retryCounter = new AtomicInteger(3);
            final CompletableFuture<Void> completableFuture = new CompletableFuture<>();

            @Override
            public CompletableFuture<Void> apply(Response response) {
                if (response.getStatusCode() >= 400) {
                    completableFuture.completeExceptionally(new IOException(response.getStatusText() + "\nURL: " + url));
                    return completableFuture;
                }
                try {
                    byte[] data = response.getResponseBodyAsBytes();
                    checksum.check(data, url);
                    Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.printf("Downloaded %s @ %.2f kiB%n", url, (data.length / 1024.0));
                    return CompletableFuture.completedFuture(null);
                } catch (DigestException e) {
                    if (retryCounter.getAndDecrement() > 0) {
                        System.out.println("Retrying download of " + url + " due to invalid message digest");
                        return client.executeRequest(requestBuilder).toCompletableFuture().thenCompose(this);
                    } else {
                        completableFuture.completeExceptionally(e);
                    }
                } catch (IOException e) {
                    completableFuture.completeExceptionally(e);
                }
                return completableFuture;
            }
        }
        return client.executeRequest(requestBuilder)
                .toCompletableFuture()
                .thenComposeAsync(checksum == null ? response -> CompletableFuture.completedFuture(null) : new RetryAsync());
    }

    @Override
    public String toString() {
        return url + " -> " + file.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JenkinsFile that = (JenkinsFile) o;
        return Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file);
    }
}
