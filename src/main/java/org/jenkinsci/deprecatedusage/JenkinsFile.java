package org.jenkinsci.deprecatedusage;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Objects;

public class JenkinsFile {
    private final String name;
    private final String version;
    private final String url;
    private final String wiki;
    private Path file;
    private final MessageDigest messageDigest;
    private final byte[] expectedDigest;

    public JenkinsFile(String name, String version, String url, String wiki, MessageDigest messageDigest, byte[] expectedDigest) {
        super();
        this.name = name;
        this.version = version;
        this.url = url;
        this.wiki = wiki;
        this.messageDigest = messageDigest;
        this.expectedDigest = expectedDigest;
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        file = Paths.get("work", name, version, fileName).toAbsolutePath();
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

    public String getUrl() {
        return url;
    }

    public void setFile(File file) {
        this.file = file.toPath();
    }

    public void deleteFile() throws IOException {
        Files.deleteIfExists(file);
    }

    public boolean isFileSynchronized() {
        if (Files.notExists(file)) {
            return false;
        }
        if (messageDigest == null) {
            return true;
        }
        try (InputStream in = Files.newInputStream(file)) {
            messageDigest.reset();
            return MessageDigest.isEqual(expectedDigest, DigestUtils.digest(messageDigest, in));
        } catch (IOException ignored) {
            return false;
        }
    }

    public OutputStream getFileOutputStream() throws IOException {
        OutputStream fileStream = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if (messageDigest != null) {
            messageDigest.reset();
            return new DigestOutputStream(fileStream, messageDigest);
        }
        return fileStream;
    }

    public boolean isFileMessageDigestValid() {
        return messageDigest == null || MessageDigest.isEqual(expectedDigest, messageDigest.digest());
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
