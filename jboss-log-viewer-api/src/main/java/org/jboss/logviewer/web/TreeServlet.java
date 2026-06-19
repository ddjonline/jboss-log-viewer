package org.jboss.logviewer.web;

import java.io.IOException;

import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.json.JsonSupport;
import org.jboss.logviewer.model.LogNode;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code GET /tree?set=server|application} → the filtered log directory tree as
 * JSON. The mapping is short; the API WAR context root supplies
 * {@code /jboss/logs/viewer/api}.
 */
@WebServlet("/tree")
public class TreeServlet extends AbstractApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            LogSet set = requireSet(req);
            LogNode tree = service().listTree(set);
            ApiErrorHandler.writeJson(resp, HttpServletResponse.SC_OK,
                    JsonSupport.tree(tree).toString());
        } catch (Exception e) {
            ApiErrorHandler.handle(resp, e);
        }
    }
}
