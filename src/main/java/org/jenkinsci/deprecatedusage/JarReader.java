package org.jenkinsci.deprecatedusage;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarReader implements Closeable {
    private final ZipInputStream zipInputStream;
    private ZipEntry entry;

    public JarReader(InputStream input) {
        super();
        this.zipInputStream = new ZipInputStream(new BufferedInputStream(input, 50 * 1024));
    }

    public String nextClass() throws IOException {
        do {
            entry = zipInputStream.getNextEntry();
        } while (entry != null && !entry.getName().endsWith(".class"));
        if (entry != null) {
            return entry.getName();
        }
        return null;
    }

    public InputStream getInputStream() {
        return zipInputStream;
    }

    @Override
    public void close() throws IOException {
        zipInputStream.close();
    }
}
