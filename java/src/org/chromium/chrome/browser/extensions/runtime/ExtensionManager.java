package org.chromium.chrome.browser.extensions.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import org.chromium.chrome.browser.extensions.installer.ExtensionInstaller;
import org.chromium.chrome.browser.extensions.manifest.ExtensionManifestParser;
import org.chromium.chrome.browser.extensions.model.Extension;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main Extension Manager service for Vody Browser. Manages lifecycle, persistent state, and runtime injection.
 */
public class ExtensionManager {

    private static final String PREFS_NAME = "vody_extensions_prefs";
    private static final String KEY_EXTENSIONS_INDEX = "installed_extensions_index";
    private static final String EXTENSIONS_DIR_NAME = "app_extensions";

    private static volatile ExtensionManager sInstance;

    private final Context mContext;
    private final Map<String, Extension> mExtensions = new HashMap<>();
    private final List<ExtensionChangeListener> mListeners = new CopyOnWriteArrayList<>();
    private final ExtensionInstaller mInstaller;

    public interface ExtensionChangeListener {
        void onExtensionsListChanged();
        void onExtensionActionChanged(Extension extension);
    }

    public static ExtensionManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ExtensionManager.class) {
                if (sInstance == null) {
                    sInstance = new ExtensionManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private ExtensionManager(Context context) {
        mContext = context;
        mInstaller = new ExtensionInstaller();
        loadExtensionsFromStorage();
    }

    public static File getExtensionsStorageDir(Context context) {
        File dir = new File(context.getFilesDir(), EXTENSIONS_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public ExtensionInstaller getInstaller() {
        return mInstaller;
    }

    public synchronized List<Extension> getInstalledExtensions() {
        return new ArrayList<>(mExtensions.values());
    }

    public synchronized Extension getExtension(String id) {
        return mExtensions.get(id);
    }

    public synchronized void registerInstalledExtension(Extension extension) {
        if (extension == null) return;
        mExtensions.put(extension.getId(), extension);
        saveExtensionsIndex();
        notifyExtensionsChanged();
    }

    public synchronized void setExtensionEnabled(String id, boolean enabled) {
        Extension ext = mExtensions.get(id);
        if (ext != null) {
            ext.setEnabled(enabled);
            saveExtensionsIndex();
            notifyExtensionsChanged();
        }
    }

    public synchronized boolean uninstallExtension(String id) {
        Extension ext = mExtensions.remove(id);
        if (ext != null) {
            File dir = new File(ext.getInstallPath());
            if (dir.exists()) {
                ExtensionInstaller.deleteRecursively(dir);
            }
            saveExtensionsIndex();
            notifyExtensionsChanged();
            return true;
        }
        return false;
    }

    public synchronized void reloadExtension(String id) {
        Extension ext = mExtensions.get(id);
        if (ext != null) {
            try {
                File dir = new File(ext.getInstallPath());
                Extension reloaded = ExtensionManifestParser.parseManifest(dir, ext.getId());
                reloaded.setEnabled(ext.isEnabled());
                mExtensions.put(id, reloaded);
                saveExtensionsIndex();
                notifyExtensionsChanged();
            } catch (Exception ignored) {}
        }
    }

    public void addListener(ExtensionChangeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(ExtensionChangeListener listener) {
        mListeners.remove(listener);
    }

    public void notifyExtensionsChanged() {
        for (ExtensionChangeListener l : mListeners) {
            l.onExtensionsListChanged();
        }
    }

    public void notifyExtensionActionChanged(Extension extension) {
        for (ExtensionChangeListener l : mListeners) {
            l.onExtensionActionChanged(extension);
        }
    }

    private synchronized void saveExtensionsIndex() {
        SharedPreferences sp = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray();
            for (Extension ext : mExtensions.values()) {
                arr.put(ext.toJson());
            }
            sp.edit().putString(KEY_EXTENSIONS_INDEX, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private synchronized void loadExtensionsFromStorage() {
        mExtensions.clear();
        SharedPreferences sp = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String indexJson = sp.getString(KEY_EXTENSIONS_INDEX, null);

        if (indexJson != null && !indexJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(indexJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Extension ext = Extension.fromJson(obj);
                    // Verify directory still exists
                    if (new File(ext.getInstallPath()).exists()) {
                        mExtensions.put(ext.getId(), ext);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Also scan storage directory for any un-indexed directories
        File baseDir = getExtensionsStorageDir(mContext);
        File[] subs = baseDir.listFiles();
        if (subs != null) {
            for (File sub : subs) {
                if (sub.isDirectory() && new File(sub, "manifest.json").exists()) {
                    String extId = sub.getName();
                    if (!mExtensions.containsKey(extId)) {
                        try {
                            Extension ext = ExtensionManifestParser.parseManifest(sub, extId);
                            mExtensions.put(ext.getId(), ext);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }
}
