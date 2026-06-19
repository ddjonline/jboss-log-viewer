package org.jboss.logviewer.web;

import java.util.Locale;

import org.jboss.logviewer.config.LogConfigListener;
import org.jboss.logviewer.config.LogDirectoryConfig;
import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.service.LogFileService;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared plumbing for the API servlets: retrieves the {@link LogDirectoryConfig}
 * published by {@link LogConfigListener}, builds a {@link LogFileService}, and
 * parses common request parameters. Keeps each concrete servlet thin.
 */
abstract class AbstractApiServlet extends HttpServlet {

    /** @return the configured service, or fails with 500 if config is absent. */
    protected LogFileService service() {
        Object cfg = getServletContext().getAttribute(LogConfigListener.CONFIG_ATTRIBUTE);
        if (!(cfg instanceof LogDirectoryConfig config)) {
            throw new IllegalStateException("Log viewer configuration is not initialized");
        }
        return new LogFileService(config);
    }

    /** Parses the required {@code set} parameter into a {@link LogSet}. */
    protected LogSet requireSet(HttpServletRequest req) {
        String set = req.getParameter("set");
        if (set == null || set.isBlank()) {
            throw new ApiException(400, "bad_request", "Missing required parameter: set");
        }
        try {
            return LogSet.valueOf(set.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "bad_request",
                    "Invalid 'set' (expected server|application): " + set);
        }
    }

    /** Parses a required non-blank string parameter. */
    protected String requireParam(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "bad_request", "Missing required parameter: " + name);
        }
        return value;
    }

    /** Parses an optional long parameter, returning {@code defaultValue} if absent. */
    protected long longParam(HttpServletRequest req, String name, long defaultValue) {
        String value = req.getParameter(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "bad_request", "Invalid numeric parameter: " + name);
        }
    }

    /** Parses an optional int parameter, returning {@code defaultValue} if absent. */
    protected int intParam(HttpServletRequest req, String name, int defaultValue) {
        long value = longParam(req, name, defaultValue);
        return (int) Math.max(1, Math.min(value, Integer.MAX_VALUE));
    }
}
