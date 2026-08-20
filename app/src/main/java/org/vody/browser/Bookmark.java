package org.vody.browser;

import org.json.JSONException;
import org.json.JSONObject;

/** A saved bookmark or history entry (title + url). */
public class Bookmark implements JsonSavable {
    public final String title;
    public final String url;

    public Bookmark(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("title", title);
        o.put("url", url);
        return o;
    }

    public static Bookmark fromJson(JSONObject o) throws JSONException {
        return new Bookmark(o.optString("title", ""), o.optString("url", ""));
    }
}
