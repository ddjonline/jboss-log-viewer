package org.jboss.logviewer.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the singleton {@link LogDirectoryConfig} from the real process
 * environment at deployment time and publishes it as a {@link ServletContext}
 * attribute for the servlets (M6) to retrieve.
 *
 * <p>This listener is the only place that reads the actual environment; the
 * resolution logic itself lives in {@link LogDirectoryConfig} so it stays
 * unit-testable without a container.
 */
@WebListener
public class LogConfigListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(LogConfigListener.class);

    /** {@link ServletContext} attribute key under which the config is stored. */
    public static final String CONFIG_ATTRIBUTE = LogDirectoryConfig.class.getName();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LogDirectoryConfig config = LogDirectoryConfig.fromEnvironment();
        config.logResolvedRoots();
        sce.getServletContext().setAttribute(CONFIG_ATTRIBUTE, config);
        LOG.info("JBoss Log Viewer configuration initialized.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        sce.getServletContext().removeAttribute(CONFIG_ATTRIBUTE);
    }
}
