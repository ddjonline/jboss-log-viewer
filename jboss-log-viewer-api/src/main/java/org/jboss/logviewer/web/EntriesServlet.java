package org.jboss.logviewer.web;

import java.io.IOException;

import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.json.JsonSupport;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code GET /entries?set=…&path=<archive>} → the selectable entries inside a
 * multi-entry archive ({@code .zip}/TAR) as a JSON array of {@code {name, size}}.
 */
@WebServlet("/entries")
public class EntriesServlet extends AbstractApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            LogSet set = requireSet(req);
            String path = requireParam(req, "path");
            var entries = service().listEntries(set, path);
            ApiErrorHandler.writeJson(resp, HttpServletResponse.SC_OK,
                    JsonSupport.entries(entries).toString());
        } catch (Exception e) {
            ApiErrorHandler.handle(resp, e);
        }
    }
}
