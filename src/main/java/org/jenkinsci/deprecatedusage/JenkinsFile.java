package org.jenkinsci.deprecatedusage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class JenkinsFile {
    // relative to user dir
    private static final File WORK_DIRECTORY = new File("work");

    private static final ThreadFactory DAEMON_THREAD_FACTORY = r -> {
        final Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    };
    private static final ExecutorService downloadExecutorService = Executors.newFixedThreadPool(8,
            DAEMON_THREAD_FACTORY);

    private final String name;
    private final String version;
    private final URL url;
    private final String wiki;
    private final HashSet<String> dependencies;
    private File file;
    private final File versionsRootDirectory;
    private Future<?> downloadFuture;

    public JenkinsFile(String name, String version, String url, String wiki, HashSet<String> dependencies)
            throws MalformedURLException {
        super();
        this.name = name;
        this.version = version;
        this.url = new URL(url);
        this.wiki = wiki;
        this.dependencies = dependencies;
        final String fileName = url.substring(url.lastIndexOf('/'));
        this.versionsRootDirectory = new File(WORK_DIRECTORY, name);
        this.file = new File(versionsRootDirectory, version + '/' + fileName);
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
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void startDownloadIfNotExists() {
        if (file.exists()) {
            // if file is already downloaded, do not download again
            return;
        }
        final String tmpPrefix = getClass().getPackage().getName() + '-';
        downloadFuture = downloadExecutorService.submit(() -> {
            final File tempFile = File.createTempFile(tmpPrefix, ".tmp");
            try {
                try (OutputStream output = new BufferedOutputStream(
                    new FileOutputStream(tempFile))) {
                    new HttpGet(url).copy(output);
                }
                versionsRootDirectory.mkdirs();
                // delete previous version
                deleteRecursive(versionsRootDirectory);
                file.getParentFile().mkdirs();
                // write target file only if complete
                try {
                    Files.move(tempFile.toPath(), file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (final AtomicMoveNotSupportedException e) {
                    Files.move(tempFile.toPath(), file.toPath());
                }
                System.out.println("Downloaded " + file.getName() + ", " + file.length() / 1024 + " Kb");
            } finally {
                tempFile.delete();
            }
            return null;
        });
    }

    private static boolean deleteRecursive(File path) {
        boolean ret = true;
        if (path.isDirectory()) {
            final File[] files = path.listFiles();
            if (files != null) {
                for (final File f : files) {
                    ret = ret && deleteRecursive(f);
                }
            }
        }
        return ret && path.delete();
    }

    public void waitDownload() throws Exception {
        if (downloadFuture != null) {
            try {
                downloadFuture.get();
            } catch (final ExecutionException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public String toString() {
        return file.getName();
    }

    public boolean isDependentOn(JenkinsFile plugin) {
        return dependencies.contains(plugin.getName());
    }
}
