package org.vody.browser;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists bookmarks, history and installed extensions to the app's private files directory
 * as small JSON documents. All access is synchronous on the caller's thread (the data sets are
 * tiny); UI code calls this from the main thread which is fine for these sizes.
 */
public class BrowseStore {
    private static final String TAG = "VodyStore";
    private final File mDir;
    private final File mBookmarks;
    private final File mHistory;
    private final File mExtensions;
    private final File mPrivacy;

    public BrowseStore(Context ctx) {
        mDir = new File(ctx.getFilesDir(), "vody");
        mDir.mkdirs();
        mBookmarks = new File(mDir, "bookmarks.json");
        mHistory = new File(mDir, "history.json");
        mExtensions = new File(mDir, "extensions.json");
        mPrivacy = new File(mDir, "privacy.json");
    }

    // ---- bookmarks --------------------------------------------------------
    public synchronized List<Bookmark> getBookmarks() {
        return readList(mBookmarks, Bookmark::fromJson);
    }

    public synchronized void addBookmark(Bookmark b) {
        List<Bookmark> list = getBookmarks();
        for (Bookmark x : list) {
            if (x.url.equals(b.url)) return; // de-dupe
        }
        list.add(b);
        writeList(mBookmarks, list);
    }

    public synchronized void removeBookmark(String url) {
        List<Bookmark> list = getBookmarks();
        list.removeIf(x -> x.url.equals(url));
        writeList(mBookmarks, list);
    }

    public synchronized boolean isBookmarked(String url) {
        for (Bookmark x : getBookmarks()) {
            if (x.url.equals(url)) return true;
        }
        return false;
    }

    // ---- history ----------------------------------------------------------
    public synchronized List<Bookmark> getHistory() {
        return readList(mHistory, Bookmark::fromJson);
    }

    public synchronized void addHistory(String title, String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return;
        List<Bookmark> list = getHistory();
        list.removeIf(x -> x.url.equals(url)); // newest first, no dup urls
        list.add(0, new Bookmark(title, url));
        if (list.size() > 200) list = new ArrayList<>(list.subList(0, 200));
        writeList(mHistory, list);
    }

    public synchronized void clearHistory() {
        writeJson(mHistory, new JSONArray());
    }

    /** Removes a single history entry by URL. */
    public synchronized void removeHistory(String url) {
        List<Bookmark> list = getHistory();
        list.removeIf(x -> x.url.equals(url));
        writeJson(mHistory, toJsonArray(list));
    }

    // ---- extensions -------------------------------------------------------
    public synchronized List<ExtensionInfo> getExtensions() {
        return readList(mExtensions, ExtensionInfo::fromJson);
    }

    public synchronized void addExtension(ExtensionInfo e) {
        List<ExtensionInfo> list = getExtensions();
        for (ExtensionInfo x : list) {
            if (x.id.equals(e.id)) return;
        }
        list.add(e);
        writeList(mExtensions, list);
    }

    public synchronized void updateExtension(ExtensionInfo e) {
        List<ExtensionInfo> list = getExtensions();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(e.id)) {
                list.set(i, e);
                break;
            }
        }
        writeList(mExtensions, list);
    }

    public synchronized void removeExtension(String id) {
        List<ExtensionInfo> list = getExtensions();
        list.removeIf(x -> x.id.equals(id));
        writeList(mExtensions, list);
    }

    // ---- privacy config --------------------------------------------------
    public synchronized PrivacyConfig getPrivacy() {
        if (!mPrivacy.exists()) return new PrivacyConfig();
        try {
            String s = new String(Files.readAllBytes(mPrivacy.toPath()), StandardCharsets.UTF_8);
            if (s.isEmpty()) return new PrivacyConfig();
            return PrivacyConfig.load(new JSONObject(s));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "read privacy failed", e);
            return new PrivacyConfig();
        }
    }

    public synchronized void setPrivacy(PrivacyConfig c) {
        writeJson(mPrivacy, c.toJson());
    }

    // ---- generic json helpers --------------------------------------------
    private interface FromJson<T> {
        T apply(JSONObject o) throws JSONException;
    }

    private <T> List<T> readList(File f, FromJson<T> ctor) {
        List<T> out = new ArrayList<>();
        JSONArray arr = readJson(f);
        for (int i = 0; i < arr.length(); i++) {
            try {
                out.add(ctor.apply(arr.getJSONObject(i)));
            } catch (JSONException e) {
                Log.w(TAG, "bad entry", e);
            }
        }
        return out;
    }

    private void writeList(File f, List<? extends JsonSavable> list) {
        writeJson(f, toJsonArray(list));
    }

    private JSONArray toJsonArray(List<? extends JsonSavable> list) {
        JSONArray arr = new JSONArray();
        for (JsonSavable o : list) {
            try {
                arr.put(o.toJson());
            } catch (JSONException e) {
                Log.w(TAG, "bad entry", e);
            }
        }
        return arr;
    }

    private JSONArray readJson(File f) {
        if (!f.exists()) return new JSONArray();
        try {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (s.isEmpty()) return new JSONArray();
            return new JSONArray(s);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "read failed " + f.getName(), e);
            return new JSONArray();
        }
    }

    private void writeJson(File f, JSONArray arr) {
        try {
            Files.write(f.toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "write failed " + f.getName(), e);
        }
    }

    private void writeJson(File f, JSONObject obj) {
        try {
            Files.write(f.toPath(), obj.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "write failed " + f.getName(), e);
        }
    }
}
