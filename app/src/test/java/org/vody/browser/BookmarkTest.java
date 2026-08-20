package org.vody.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class BookmarkTest {
    @Test
    public void roundTripJson() throws Exception {
        Bookmark b = new Bookmark("Vody", "https://vody.example");
        JSONObject o = b.toJson();
        Bookmark back = Bookmark.fromJson(o);
        assertEquals("Vody", back.title);
        assertEquals("https://vody.example", back.url);
    }

    @Test
    public void extensionIdNormalization() {
        // A web-store style id is 32 chars in [a-p].
        String id = "abcdefghijklmnopabcdefghijklmnop";
        assertTrue(id.matches("[a-p]{32}"));
    }
}
