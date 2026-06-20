package org.jboss.logviewer.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jboss.logviewer.model.ArchiveEntry;
import org.jboss.logviewer.model.LogNode;
import org.jboss.logviewer.model.TailResult;
import org.junit.jupiter.api.Test;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Unit tests for {@link JsonSupport} JSON-P serialization.
 */
class JsonSupportTest {

    @Test
    void treeSerializesDirectoriesAndFiles() {
        LogNode file = LogNode.file("server.log", "server.log", 1234L, 99L, false);
        LogNode gz = LogNode.file("old.log.gz", "sub/old.log.gz", 50L, 1L, true);
        LogNode sub = LogNode.directory("sub", "sub", List.of(gz));
        LogNode root = LogNode.directory("", "", List.of(sub, file));

        JsonObject json = JsonSupport.tree(root);
        JsonArray children = json.getJsonArray("children");

        assertEquals(2, children.size());
        // First child: directory "sub" with nested children.
        JsonObject dir = children.getJsonObject(0);
        assertEquals("sub", dir.getString("name"));
        assertEquals("directory", dir.getString("type"));
        assertEquals(1, dir.getJsonArray("children").size());
        JsonObject nested = dir.getJsonArray("children").getJsonObject(0);
        assertEquals("old.log.gz", nested.getString("name"));
        assertTrue(nested.getBoolean("compressed"));
        // Second child: file.
        JsonObject f = children.getJsonObject(1);
        assertEquals("file", f.getString("type"));
        assertEquals(1234L, f.getJsonNumber("size").longValue());
        assertFalse(f.getBoolean("compressed"));
    }

    @Test
    void tailSerializesAllFields() {
        TailResult result = new TailResult("hello\n", 42L, 42L, false, true, "/var/log/server.log");
        JsonObject json = JsonSupport.tail(result);

        assertEquals("hello\n", json.getString("content"));
        assertEquals(42L, json.getJsonNumber("nextOffset").longValue());
        assertEquals(42L, json.getJsonNumber("fileSize").longValue());
        assertFalse(json.getBoolean("truncated"));
        assertTrue(json.getBoolean("compressed"));
        assertEquals("/var/log/server.log", json.getString("absolutePath"));
    }

    @Test
    void entriesSerializeNameAndSize() {
        JsonArray json = JsonSupport.entries(List.of(
                new ArchiveEntry("a.log", 10L),
                new ArchiveEntry("b.log", 20L)));

        assertEquals(2, json.size());
        assertEquals("a.log", json.getJsonObject(0).getString("name"));
        assertEquals(20L, json.getJsonObject(1).getJsonNumber("size").longValue());
    }

    @Test
    void errorSerializesErrorAndMessage() {
        JsonObject json = JsonSupport.error("forbidden", "nope");
        assertEquals("forbidden", json.getString("error"));
        assertEquals("nope", json.getString("message"));
    }
}
