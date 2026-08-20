package org.chromium.chrome.browser.extensions.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core model representing an installed Chromium Extension in Vody Browser.
 */
public class Extension {
    private final String mId;
    private final String mName;
    private final String mVersion;
    private final String mDescription;
    private final int mManifestVersion;
    private final String mInstallPath;
    private final List<String> mPermissions;
    private final Map<Integer, String> mIcons;
    private final List<String> mBackgroundScripts;
    private final String mBackgroundServiceWorker;
    private final String mOptionsPage;
    private final List<ExtensionContentScript> mContentScripts;
    private final ExtensionAction mAction;
    private boolean mIsEnabled;
    private final long mInstallTime;

    public Extension(
            String id,
            String name,
            String version,
            String description,
            int manifestVersion,
            String installPath,
            List<String> permissions,
            Map<Integer, String> icons,
            List<String> backgroundScripts,
            String backgroundServiceWorker,
            String optionsPage,
            List<ExtensionContentScript> contentScripts,
            ExtensionAction action,
            boolean isEnabled,
            long installTime) {
        mId = id;
        mName = name != null ? name : "";
        mVersion = version != null ? version : "1.0";
        mDescription = description != null ? description : "";
        mManifestVersion = manifestVersion;
        mInstallPath = installPath;
        mPermissions = permissions != null ? permissions : new ArrayList<>();
        mIcons = icons != null ? icons : new HashMap<>();
        mBackgroundScripts = backgroundScripts != null ? backgroundScripts : new ArrayList<>();
        mBackgroundServiceWorker = backgroundServiceWorker != null ? backgroundServiceWorker : "";
        mOptionsPage = optionsPage != null ? optionsPage : "";
        mContentScripts = contentScripts != null ? contentScripts : new ArrayList<>();
        mAction = action != null ? action : new ExtensionAction(mId, mName, "", "");
        mIsEnabled = isEnabled;
        mInstallTime = installTime > 0 ? installTime : System.currentTimeMillis();
    }

    public String getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getVersion() {
        return mVersion;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getManifestVersion() {
        return mManifestVersion;
    }

    public String getInstallPath() {
        return mInstallPath;
    }

    public List<String> getPermissions() {
        return mPermissions;
    }

    public boolean hasPermission(String permission) {
        return mPermissions.contains(permission);
    }

    public Map<Integer, String> getIcons() {
        return mIcons;
    }

    public String getBestIconPath() {
        if (mIcons.containsKey(128)) return new File(mInstallPath, mIcons.get(128)).getAbsolutePath();
        if (mIcons.containsKey(48)) return new File(mInstallPath, mIcons.get(48)).getAbsolutePath();
        if (mIcons.containsKey(32)) return new File(mInstallPath, mIcons.get(32)).getAbsolutePath();
        if (mIcons.containsKey(16)) return new File(mInstallPath, mIcons.get(16)).getAbsolutePath();
        if (!mIcons.isEmpty()) {
            return new File(mInstallPath, mIcons.values().iterator().next()).getAbsolutePath();
        }
        return "";
    }

    public List<String> getBackgroundScripts() {
        return mBackgroundScripts;
    }

    public String getBackgroundServiceWorker() {
        return mBackgroundServiceWorker;
    }

    public String getOptionsPage() {
        return mOptionsPage;
    }

    public List<ExtensionContentScript> getContentScripts() {
        return mContentScripts;
    }

    public ExtensionAction getAction() {
        return mAction;
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
    }

    public long getInstallTime() {
        return mInstallTime;
    }

    public String getAbsoluteResourcePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return "";
        if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        return new File(mInstallPath, relativePath).getAbsolutePath();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", mId);
        obj.put("name", mName);
        obj.put("version", mVersion);
        obj.put("description", mDescription);
        obj.put("manifest_version", mManifestVersion);
        obj.put("install_path", mInstallPath);
        obj.put("permissions", new JSONArray(mPermissions));

        JSONObject iconsObj = new JSONObject();
        for (Map.Entry<Integer, String> entry : mIcons.entrySet()) {
            iconsObj.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        obj.put("icons", iconsObj);

        obj.put("background_scripts", new JSONArray(mBackgroundScripts));
        obj.put("background_service_worker", mBackgroundServiceWorker);
        obj.put("options_page", mOptionsPage);

        JSONArray contentScriptsArr = new JSONArray();
        for (ExtensionContentScript cs : mContentScripts) {
            contentScriptsArr.put(cs.toJson());
        }
        obj.put("content_scripts", contentScriptsArr);

        if (mAction != null) {
            obj.put("action", mAction.toJson());
        }

        obj.put("is_enabled", mIsEnabled);
        obj.put("install_time", mInstallTime);
        return obj;
    }

    public static Extension fromJson(JSONObject obj) throws JSONException {
        String id = obj.getString("id");
        String name = obj.optString("name");
        String version = obj.optString("version");
        String description = obj.optString("description");
        int manifestVersion = obj.optInt("manifest_version", 2);
        String installPath = obj.optString("install_path");

        List<String> permissions = new ArrayList<>();
        JSONArray permArr = obj.optJSONArray("permissions");
        if (permArr != null) {
            for (int i = 0; i < permArr.length(); i++) {
                permissions.add(permArr.getString(i));
            }
        }

        Map<Integer, String> icons = new HashMap<>();
        JSONObject iconsObj = obj.optJSONObject("icons");
        if (iconsObj != null) {
            JSONArray keys = iconsObj.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    try {
                        icons.put(Integer.parseInt(key), iconsObj.getString(key));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        List<String> backgroundScripts = new ArrayList<>();
        JSONArray bgArr = obj.optJSONArray("background_scripts");
        if (bgArr != null) {
            for (int i = 0; i < bgArr.length(); i++) {
                backgroundScripts.add(bgArr.getString(i));
            }
        }

        String bgWorker = obj.optString("background_service_worker", "");
        String optionsPage = obj.optString("options_page", "");

        List<ExtensionContentScript> contentScripts = new ArrayList<>();
        JSONArray csArr = obj.optJSONArray("content_scripts");
        if (csArr != null) {
            for (int i = 0; i < csArr.length(); i++) {
                contentScripts.add(ExtensionContentScript.fromJson(csArr.getJSONObject(i)));
            }
        }

        ExtensionAction action = null;
        JSONObject actionObj = obj.optJSONObject("action");
        if (actionObj != null) {
            action = ExtensionAction.fromJson(actionObj);
        }

        boolean isEnabled = obj.optBoolean("is_enabled", true);
        long installTime = obj.optLong("install_time", System.currentTimeMillis());

        return new Extension(
                id,
                name,
                version,
                description,
                manifestVersion,
                installPath,
                permissions,
                icons,
                backgroundScripts,
                bgWorker,
                optionsPage,
                contentScripts,
                action,
                isEnabled,
                installTime);
    }
}
