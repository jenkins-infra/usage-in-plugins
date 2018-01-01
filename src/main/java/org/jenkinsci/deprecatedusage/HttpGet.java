package org.jenkinsci.deprecatedusage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class HttpGet {
    /** Timeout to connect in ms. */
    private static final int CONNECTION_TIMEOUT = 120000;

    /**
     * Timeout to read in ms.
     */
    private static final int READ_TIMEOUT = 300000;

    private final URL url;

    public HttpGet(URL url) {
        super();
        this.url = url;
    }

    public void copy(OutputStream output) throws IOException {
        final URLConnection connection = url.openConnection();
        if (CONNECTION_TIMEOUT > 0) {
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
        }
        if (READ_TIMEOUT > 0) {
            connection.setReadTimeout(READ_TIMEOUT);
        }
        try {
            try (InputStream input = connection.getInputStream()) {
                final byte[] buffer = new byte[50 * 1024];
                int len = input.read(buffer);
                while (len != -1) {
                    output.write(buffer, 0, len);
                    len = input.read(buffer);
                }
            } finally {
                if (connection instanceof HttpURLConnection) {
                    final InputStream error = ((HttpURLConnection) connection).getErrorStream();
                    if (error != null) {
                        error.close();
                    }
                }
            }
        } catch (final ConnectException e) {
            final String message = e.getMessage()
                    + " (Do you need to set http proxy with -Dhttp.proxyHost=myproxyHost -Dhttp.proxyPort=myproxyPort ?)";
            final ConnectException e2 = new ConnectException(message);
            e2.initCause(e);
            throw e2;
        }
    }

    public byte[] read() throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(output);
        return output.toByteArray();
    }
}
