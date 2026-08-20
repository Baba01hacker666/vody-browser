package org.vody.browser;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * User-controlled privacy / anti-fingerprinting configuration.
 *
 * Everything here is set MANUALLY by the user in Settings — nothing is randomized or chosen for
 * them. The browser applies it two ways:
 *   - canvas/webgl/randomization prefs are pushed straight into GeckoView's engine (privacy.resistFingerprinting)
 *   - timezone / fonts / username / custom API responses are enforced per-page by the built-in
 *     "vodyprivacy" content script (assets/extensions/vodyprivacy/).
 */
public final class PrivacyConfig implements JsonSavable {

    // ---- switches ----
    public boolean resistFingerprinting = true;   // master RFP (GeckoView): canvas, audio, spoofed screen, etc.
    public boolean spoofWebGL = true;             // override WebGL vendor/renderer + mask GPU
    public boolean spoofTimezone = false;         // when true, use customTimezone
    public boolean spoofFonts = false;            // when true, use customFonts
    public boolean spoofUsername = false;         // when true, use customUsername
    public boolean spoofApis = false;             // when true, use apiResponses overrides

    // ---- manual values ----
    public String customTimezone = "UTC";         // e.g. "America/New_York", "Asia/Tokyo", "UTC"
    public String customFonts = "";               // comma-separated, e.g. "Arial, Helvetica, sans-serif"
    public String customUsername = "";            // navigator.identity / custom UA-ish username token
    public JSONObject apiResponses = new JSONObject(); // {"navigator.platform":"Linux x86_64", ...}

    public static PrivacyConfig load(JSONObject o) {
        PrivacyConfig c = new PrivacyConfig();
        if (o == null) return c;
        c.resistFingerprinting = o.optBoolean("resistFingerprinting", true);
        c.spoofWebGL = o.optBoolean("spoofWebGL", true);
        c.spoofTimezone = o.optBoolean("spoofTimezone", false);
        c.spoofFonts = o.optBoolean("spoofFonts", false);
        c.spoofUsername = o.optBoolean("spoofUsername", false);
        c.spoofApis = o.optBoolean("spoofApis", false);
        c.customTimezone = o.optString("customTimezone", "UTC");
        c.customFonts = o.optString("customFonts", "");
        c.customUsername = o.optString("customUsername", "");
        try { c.apiResponses = o.optJSONObject("apiResponses") == null ? new JSONObject() : o.optJSONObject("apiResponses"); }
        catch (Exception ignore) { c.apiResponses = new JSONObject(); }
        return c;
    }

    @Override
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("resistFingerprinting", resistFingerprinting);
            o.put("spoofWebGL", spoofWebGL);
            o.put("spoofTimezone", spoofTimezone);
            o.put("spoofFonts", spoofFonts);
            o.put("spoofUsername", spoofUsername);
            o.put("spoofApis", spoofApis);
            o.put("customTimezone", customTimezone);
            o.put("customFonts", customFonts);
            o.put("customUsername", customUsername);
            o.put("apiResponses", apiResponses);
        } catch (Exception ignore) {}
        return o;
    }

    /** Flat map handed to the vodyprivacy content script on every page load. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("resistFingerprinting", resistFingerprinting);
        m.put("spoofWebGL", spoofWebGL);
        m.put("spoofTimezone", spoofTimezone);
        m.put("spoofFonts", spoofFonts);
        m.put("spoofUsername", spoofUsername);
        m.put("spoofApis", spoofApis);
        m.put("customTimezone", customTimezone);
        m.put("customFonts", customFonts);
        m.put("customUsername", customUsername);
        m.put("apiResponses", apiResponses.toString());
        return m;
    }
}
