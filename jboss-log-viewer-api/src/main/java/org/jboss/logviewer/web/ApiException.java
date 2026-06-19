package org.jboss.logviewer.web;

/**
 * A request-level failure carrying the HTTP status and a short error code to
 * return to the client. Used for client-input problems (400/404); other
 * failures map through {@link ApiErrorHandler}.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }
}
