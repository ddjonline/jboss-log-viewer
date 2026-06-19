package org.jboss.logviewer.config;

/**
 * The two categories of log directory the viewer exposes. The UI toggle maps
 * directly onto these values, and each maps to a configured filesystem root.
 */
public enum LogSet {

    /** JBoss/WildFly server logs (e.g. {@code standalone/log}). */
    SERVER,

    /** Application log directory (may differ from the server log directory). */
    APPLICATION
}
