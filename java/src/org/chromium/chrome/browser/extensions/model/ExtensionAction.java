package org.chromium.chrome.browser.extensions.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Model representing a Browser Action or Action for an Extension.
 */
public class ExtensionAction {
    private final String mExtensionId;
    private String mTitle;
    private String mDefaultPopup;
    private String mDefaultIcon;
    private String mBadgeText;
    private String mBadgeBackgroundColor;
    private String mBadgeTextColor;

    public ExtensionAction(String extensionId, String title, String defaultPopup, String defaultIcon) {
        mExtensionId = extensionId;
        mTitle = title != null ? title : "";
        mDefaultPopup = defaultPopup != null ? defaultPopup : "";
        mDefaultIcon = defaultIcon != null ? defaultIcon : "";
        mBadgeText = "";
        mBadgeBackgroundColor = "#0000FF";
        mBadgeTextColor = "#FFFFFF";
    }

    public String getExtensionId() {
        return mExtensionId;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getDefaultPopup() {
        return mDefaultPopup;
    }

    public void setDefaultPopup(String defaultPopup) {
        mDefaultPopup = defaultPopup;
    }

    public String getDefaultIcon() {
        return mDefaultIcon;
    }

    public void setDefaultIcon(String defaultIcon) {
        mDefaultIcon = defaultIcon;
    }

    public String getBadgeText() {
        return mBadgeText;
    }

    public void setBadgeText(String badgeText) {
        mBadgeText = badgeText;
    }

    public String getBadgeBackgroundColor() {
        return mBadgeBackgroundColor;
    }

    public void setBadgeBackgroundColor(String color) {
        mBadgeBackgroundColor = color;
    }

    public String getBadgeTextColor() {
        return mBadgeTextColor;
    }

    public void setBadgeTextColor(String color) {
        mBadgeTextColor = color;
    }

    public boolean hasPopup() {
        return mDefaultPopup != null && !mDefaultPopup.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("extension_id", mExtensionId);
        obj.put("title", mTitle);
        obj.put("default_popup", mDefaultPopup);
        obj.put("default_icon", mDefaultIcon);
        obj.put("badge_text", mBadgeText);
        obj.put("badge_bg_color", mBadgeBackgroundColor);
        obj.put("badge_text_color", mBadgeTextColor);
        return obj;
    }

    public static ExtensionAction fromJson(JSONObject obj) {
        String id = obj.optString("extension_id");
        String title = obj.optString("title");
        String popup = obj.optString("default_popup");
        String icon = obj.optString("default_icon");

        ExtensionAction action = new ExtensionAction(id, title, popup, icon);
        action.setBadgeText(obj.optString("badge_text", ""));
        action.setBadgeBackgroundColor(obj.optString("badge_bg_color", "#0000FF"));
        action.setBadgeTextColor(obj.optString("badge_text_color", "#FFFFFF"));
        return action;
    }
}
