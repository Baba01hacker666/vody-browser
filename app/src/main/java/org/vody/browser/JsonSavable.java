package org.vody.browser;

import org.json.JSONException;
import org.json.JSONObject;

/** Anything that can serialize itself into a JSONObject for JSON persistence. */
public interface JsonSavable {
    JSONObject toJson() throws JSONException;
}
