package org.jenkinsci.deprecatedusage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scan HPI / JPI / WAR files
 */
public class WarReader implements Closeable {
    private final File warFile;
    private final ZipFile zipFile;
    private final Enumeration<? extends ZipEntry> entries;
    private final boolean scanOnlyJarOfPlugin;
    private ZipEntry entry;
    private JarReader jarReader;

    public WarReader(File warFile, boolean scanOnlyJarOfPlugin) throws IOException {
        super();
        this.warFile = warFile;
        this.zipFile = new ZipFile(warFile);
        this.entries = zipFile.entries();
        this.scanOnlyJarOfPlugin = scanOnlyJarOfPlugin;
    }

    public String nextClass() throws IOException {
        if (jarReader != null) {
            final String fileName = jarReader.nextClass();
            if (fileName != null) {
                return fileName;
            } else {
                jarReader.close();
                jarReader = null;
            }
        }
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            final String fileName = entry.getName();
            if (fileName.startsWith("WEB-INF/lib/") && fileName.endsWith(".jar")) {
                final boolean shouldScanJar = !scanOnlyJarOfPlugin
                        || warFile.getName().equals(fileName.replace("WEB-INF/lib/", "").replace(".jar", ".hpi"))
                        || fileName.contains("jenkins-core");
                if (shouldScanJar) {
                    jarReader = new JarReader(zipFile.getInputStream(entry));
                    return this.nextClass();
                }
            } else if (fileName.startsWith("WEB-INF/classes/") && fileName.endsWith(".class")) {
                return fileName;
            }
        }
        return null;
    }

    public InputStream getInputStream() throws IOException {
        if (jarReader != null) {
            return jarReader.getInputStream();
        }
        return zipFile.getInputStream(entry);
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }
}
