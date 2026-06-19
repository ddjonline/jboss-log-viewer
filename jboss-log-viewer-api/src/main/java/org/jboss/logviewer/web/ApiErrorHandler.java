package org.jboss.logviewer.web;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import org.jboss.logviewer.json.JsonSupport;
import org.jboss.logviewer.service.PathSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Maps exceptions thrown by the API servlets to JSON error responses with the
 * appropriate HTTP status. Centralizes the {@code {error, message}} contract.
 */
public final class ApiErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    private ApiErrorHandler() {
    }

    /** Writes a JSON error response for the given exception. */
    public static void handle(HttpServletResponse resp, Exception e) throws IOException {
        int status;
        String error;
        String message = e.getMessage();

        if (e instanceof ApiException api) {
            status = api.getStatus();
            error = api.getError();
        } else if (e instanceof PathSecurityException) {
            status = HttpServletResponse.SC_FORBIDDEN; // 403
            error = "forbidden";
        } else if (e instanceof NoSuchFileException || e instanceof java.io.FileNotFoundException) {
            status = HttpServletResponse.SC_NOT_FOUND; // 404
            error = "not_found";
        } else if (e instanceof IllegalArgumentException) {
            status = HttpServletResponse.SC_BAD_REQUEST; // 400
            error = "bad_request";
        } else {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; // 500
            error = "internal_error";
            LOG.error("Unexpected error handling request", e);
        }

        writeJson(resp, status, JsonSupport.error(error, message).toString());
    }

    /** Writes a JSON body with the given status, UTF-8. */
    public static void writeJson(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(json);
    }
}
