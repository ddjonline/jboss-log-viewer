package org.jboss.logviewer.json;

import java.util.List;

import org.jboss.logviewer.model.ArchiveEntry;
import org.jboss.logviewer.model.LogNode;
import org.jboss.logviewer.model.TailResult;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Serializes the service-layer models to JSON using Jakarta JSON-P
 * ({@code jakarta.json.*}). No third-party JSON library is used.
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    /**
     * Serializes a directory tree. The supplied node is the (nameless) root
     * directory; its children form the top-level array under {@code "children"}.
     */
    public static JsonObject tree(LogNode root) {
        return Json.createObjectBuilder()
                .add("children", childrenArray(root))
                .build();
    }

    private static JsonArray childrenArray(LogNode dir) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        List<LogNode> children = dir.getChildren();
        if (children != null) {
            for (LogNode child : children) {
                array.add(node(child));
            }
        }
        return array.build();
    }

    private static JsonObject node(LogNode node) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("name", node.getName())
                .add("path", node.getRelativePath())
                .add("type", node.isDirectory() ? "directory" : "file");
        if (node.isDirectory()) {
            b.add("children", childrenArray(node));
        } else {
            b.add("size", node.getSize())
                    .add("lastModified", node.getLastModified())
                    .add("compressed", node.isCompressed());
        }
        return b.build();
    }

    /** Serializes a {@link TailResult} content response. */
    public static JsonObject tail(TailResult result) {
        return Json.createObjectBuilder()
                .add("content", result.content())
                .add("nextOffset", result.nextOffset())
                .add("fileSize", result.fileSize())
                .add("truncated", result.truncated())
                .add("compressed", result.compressed())
                .add("absolutePath", result.absolutePath() == null ? "" : result.absolutePath())
                .build();
    }

    /** Serializes a list of archive entries to a JSON array of {@code {name, size}}. */
    public static JsonArray entries(List<ArchiveEntry> entries) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (ArchiveEntry entry : entries) {
            array.add(Json.createObjectBuilder()
                    .add("name", entry.name())
                    .add("size", entry.size()));
        }
        return array.build();
    }

    /** Serializes an error response to {@code {error, message}}. */
    public static JsonObject error(String error, String message) {
        return Json.createObjectBuilder()
                .add("error", error)
                .add("message", message == null ? "" : message)
                .build();
    }
}
