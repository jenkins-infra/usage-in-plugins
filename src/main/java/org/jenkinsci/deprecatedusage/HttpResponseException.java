package org.jenkinsci.deprecatedusage;

import java.io.IOException;

public class HttpResponseException extends IOException {
    public HttpResponseException(int statusCode, String responseMessage) {
        super("HTTP " + statusCode + " " + responseMessage);
    }
}
