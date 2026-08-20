package org.vody.browser;

import org.json.JSONException;
import org.json.JSONObject;

/** Describes an installed WebExtension: its id, name, source location, and enabled state. */
public class ExtensionInfo implements JsonSavable {
    public final String id;
    public String name;
    public String location; // file:// path or web-store URL
    public boolean enabled;

    public ExtensionInfo(String id, String name, String location, boolean enabled) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.enabled = enabled;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("location", location);
        o.put("enabled", enabled);
        return o;
    }

    public static ExtensionInfo fromJson(JSONObject o) throws JSONException {
        return new ExtensionInfo(
                o.getString("id"),
                o.optString("name", o.getString("id")),
                o.optString("location", ""),
                o.optBoolean("enabled", true));
    }
}
