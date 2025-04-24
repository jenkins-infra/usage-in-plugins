package org.jenkinsci.deprecatedusage;

import java.io.IOException;
import java.io.Serial;

public class HttpResponseException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public HttpResponseException(int statusCode, String responseMessage) {
        super("HTTP " + statusCode + " " + responseMessage);
    }
}
