package org.jboss.logviewer.web;

import java.io.IOException;

import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.json.JsonSupport;
import org.jboss.logviewer.model.TailResult;
import org.jboss.logviewer.service.LogFileService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code GET /content?set=…&path=…&entry?&offset?&max?} → a window of a log file
 * as JSON {@code {content, nextOffset, fileSize, truncated, compressed}}.
 *
 * <p>{@code offset} defaults to {@code -1} (initial tail load); {@code max}
 * defaults to {@link LogFileService#DEFAULT_TAIL_BYTES}. {@code entry} selects an
 * archive entry for multi-entry compressed files.
 */
@WebServlet("/content")
public class ContentServlet extends AbstractApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            LogSet set = requireSet(req);
            String path = requireParam(req, "path");
            String entry = req.getParameter("entry"); // optional
            long offset = longParam(req, "offset", -1L);
            int max = intParam(req, "max", LogFileService.DEFAULT_TAIL_BYTES);

            TailResult result = service().readTail(set, path, offset, max, entry);
            ApiErrorHandler.writeJson(resp, HttpServletResponse.SC_OK,
                    JsonSupport.tail(result).toString());
        } catch (Exception e) {
            ApiErrorHandler.handle(resp, e);
        }
    }
}
