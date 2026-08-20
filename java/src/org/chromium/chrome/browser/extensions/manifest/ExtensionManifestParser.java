package org.chromium.chrome.browser.extensions.manifest;

import org.chromium.chrome.browser.extensions.model.Extension;
import org.chromium.chrome.browser.extensions.model.ExtensionAction;
import org.chromium.chrome.browser.extensions.model.ExtensionContentScript;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for extension manifest.json files supporting Manifest V2 and V3.
 */
public class ExtensionManifestParser {

    public static Extension parseManifest(File extensionDir, String fallbackId) throws Exception {
        File manifestFile = new File(extensionDir, "manifest.json");
        if (!manifestFile.exists()) {
            throw new IllegalArgumentException("manifest.json not found in " + extensionDir.getAbsolutePath());
        }

        String jsonString;
        try (InputStream is = new FileInputStream(manifestFile)) {
            byte[] buffer = new byte[(int) manifestFile.length()];
            is.read(buffer);
            jsonString = new String(buffer, StandardCharsets.UTF_8);
        }

        JSONObject root = new JSONObject(jsonString);

        int manifestVersion = root.optInt("manifest_version", 2);
        String name = resolveI18n(extensionDir, root.optString("name", "Unnamed Extension"));
        String version = root.optString("version", "1.0");
        String description = resolveI18n(extensionDir, root.optString("description", ""));

        // Determine unique extension ID (either derived from name/path or fallback)
        String extensionId = fallbackId;
        if (extensionId == null || extensionId.isEmpty()) {
            extensionId = generateExtensionId(name + "_" + extensionDir.getAbsolutePath());
        }

        // Permissions
        List<String> permissions = new ArrayList<>();
        JSONArray permArr = root.optJSONArray("permissions");
        if (permArr != null) {
            for (int i = 0; i < permArr.length(); i++) {
                permissions.add(permArr.getString(i));
            }
        }
        JSONArray hostPermArr = root.optJSONArray("host_permissions");
        if (hostPermArr != null) {
            for (int i = 0; i < hostPermArr.length(); i++) {
                permissions.add(hostPermArr.getString(i));
            }
        }

        // Icons
        Map<Integer, String> icons = new HashMap<>();
        JSONObject iconsObj = root.optJSONObject("icons");
        if (iconsObj != null) {
            JSONArray keys = iconsObj.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String size = keys.getString(i);
                    try {
                        icons.put(Integer.parseInt(size), iconsObj.getString(size));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Background scripts / service worker
        List<String> bgScripts = new ArrayList<>();
        String bgWorker = "";
        JSONObject bgObj = root.optJSONObject("background");
        if (bgObj != null) {
            JSONArray scriptsArr = bgObj.optJSONArray("scripts");
            if (scriptsArr != null) {
                for (int i = 0; i < scriptsArr.length(); i++) {
                    bgScripts.add(scriptsArr.getString(i));
                }
            }
            bgWorker = bgObj.optString("service_worker", "");
            if (bgWorker.isEmpty() && bgObj.has("page")) {
                bgScripts.add(bgObj.optString("page"));
            }
        }

        // Options page
        String optionsPage = root.optString("options_page", "");
        JSONObject optionsUi = root.optJSONObject("options_ui");
        if (optionsUi != null && optionsUi.has("page")) {
            optionsPage = optionsUi.optString("page");
        }

        // Content scripts
        List<ExtensionContentScript> contentScripts = new ArrayList<>();
        JSONArray csArr = root.optJSONArray("content_scripts");
        if (csArr != null) {
            for (int i = 0; i < csArr.length(); i++) {
                JSONObject csObj = csArr.getJSONObject(i);
                List<String> matches = new ArrayList<>();
                JSONArray mArr = csObj.optJSONArray("matches");
                if (mArr != null) {
                    for (int j = 0; j < mArr.length(); j++) {
                        matches.add(mArr.getString(j));
                    }
                }

                List<String> jsList = new ArrayList<>();
                JSONArray jsArr = csObj.optJSONArray("js");
                if (jsArr != null) {
                    for (int j = 0; j < jsArr.length(); j++) {
                        jsList.add(jsArr.getString(j));
                    }
                }

                List<String> cssList = new ArrayList<>();
                JSONArray cssArr = csObj.optJSONArray("css");
                if (cssArr != null) {
                    for (int j = 0; j < cssArr.length(); j++) {
                        cssList.add(cssArr.getString(j));
                    }
                }

                String runAtStr = csObj.optString("run_at", "document_idle");
                ExtensionContentScript.RunAt runAt = ExtensionContentScript.RunAt.DOCUMENT_IDLE;
                if ("document_start".equalsIgnoreCase(runAtStr)) {
                    runAt = ExtensionContentScript.RunAt.DOCUMENT_START;
                } else if ("document_end".equalsIgnoreCase(runAtStr)) {
                    runAt = ExtensionContentScript.RunAt.DOCUMENT_END;
                }

                boolean allFrames = csObj.optBoolean("all_frames", false);
                boolean matchAboutBlank = csObj.optBoolean("match_about_blank", false);

                contentScripts.add(new ExtensionContentScript(matches, jsList, cssList, runAt, allFrames, matchAboutBlank));
            }
        }

        // Action / Browser Action
        JSONObject actionObj = root.optJSONObject("action");
        if (actionObj == null) {
            actionObj = root.optJSONObject("browser_action");
        }
        if (actionObj == null) {
            actionObj = root.optJSONObject("page_action");
        }

        ExtensionAction action = null;
        if (actionObj != null) {
            String title = actionObj.optString("default_title", name);
            String popup = actionObj.optString("default_popup", "");
            String defaultIcon = "";
            Object iconVal = actionObj.opt("default_icon");
            if (iconVal instanceof String) {
                defaultIcon = (String) iconVal;
            } else if (iconVal instanceof JSONObject) {
                JSONObject defaultIcons = (JSONObject) iconVal;
                if (defaultIcons.has("128")) defaultIcon = defaultIcons.optString("128");
                else if (defaultIcons.has("48")) defaultIcon = defaultIcons.optString("48");
                else if (defaultIcons.has("32")) defaultIcon = defaultIcons.optString("32");
                else if (defaultIcons.has("16")) defaultIcon = defaultIcons.optString("16");
            }
            action = new ExtensionAction(extensionId, resolveI18n(extensionDir, title), popup, defaultIcon);
        } else {
            action = new ExtensionAction(extensionId, name, "", "");
        }

        return new Extension(
                extensionId,
                name,
                version,
                description,
                manifestVersion,
                extensionDir.getAbsolutePath(),
                permissions,
                icons,
                bgScripts,
                bgWorker,
                optionsPage,
                contentScripts,
                action,
                true,
                System.currentTimeMillis());
    }

    private static String resolveI18n(File extensionDir, String key) {
        if (key == null || !key.startsWith("__MSG_") || !key.endsWith("__")) {
            return key;
        }
        String msgName = key.substring(6, key.length() - 2);
        File localesDir = new File(extensionDir, "_locales");
        if (!localesDir.exists()) return key;

        File enMessages = new File(new File(localesDir, "en"), "messages.json");
        if (!enMessages.exists()) {
            File[] subs = localesDir.listFiles();
            if (subs != null && subs.length > 0) {
                enMessages = new File(subs[0], "messages.json");
            }
        }

        if (enMessages != null && enMessages.exists()) {
            try (InputStream is = new FileInputStream(enMessages)) {
                byte[] buffer = new byte[(int) enMessages.length()];
                is.read(buffer);
                JSONObject root = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
                JSONObject item = root.optJSONObject(msgName);
                if (item != null && item.has("message")) {
                    return item.getString("message");
                }
            } catch (Exception ignored) {}
        }
        return msgName;
    }

    public static String generateExtensionId(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                int val = (hash[i] & 0xFF) % 26;
                sb.append((char) ('a' + val));
            }
            return sb.toString();
        } catch (Exception e) {
            return "ext" + Math.abs(input.hashCode());
        }
    }
}
