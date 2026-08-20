package org.chromium.chrome.browser.extensions.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Model representing a content script defined in an extension's manifest.
 */
public class ExtensionContentScript {
    public enum RunAt {
        DOCUMENT_START,
        DOCUMENT_END,
        DOCUMENT_IDLE
    }

    private final List<String> mMatches;
    private final List<String> mJsFiles;
    private final List<String> mCssFiles;
    private final RunAt mRunAt;
    private final boolean mAllFrames;
    private final boolean mMatchAboutBlank;
    private final List<Pattern> mCompiledPatterns;

    public ExtensionContentScript(
            List<String> matches,
            List<String> jsFiles,
            List<String> cssFiles,
            RunAt runAt,
            boolean allFrames,
            boolean matchAboutBlank) {
        mMatches = matches != null ? matches : new ArrayList<>();
        mJsFiles = jsFiles != null ? jsFiles : new ArrayList<>();
        mCssFiles = cssFiles != null ? cssFiles : new ArrayList<>();
        mRunAt = runAt != null ? runAt : RunAt.DOCUMENT_IDLE;
        mAllFrames = allFrames;
        mMatchAboutBlank = matchAboutBlank;
        mCompiledPatterns = new ArrayList<>();

        for (String match : mMatches) {
            Pattern pattern = compileMatchPattern(match);
            if (pattern != null) {
                mCompiledPatterns.add(pattern);
            }
        }
    }

    public List<String> getMatches() {
        return mMatches;
    }

    public List<String> getJsFiles() {
        return mJsFiles;
    }

    public List<String> getCssFiles() {
        return mCssFiles;
    }

    public RunAt getRunAt() {
        return mRunAt;
    }

    public boolean isAllFrames() {
        return mAllFrames;
    }

    public boolean isMatchAboutBlank() {
        return mMatchAboutBlank;
    }

    /**
     * Checks if a given URL matches this content script's match patterns.
     */
    public boolean matchesUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.startsWith("chrome://") || url.startsWith("chrome-extension://") || url.startsWith("about:")) {
            return false;
        }

        for (Pattern pattern : mCompiledPatterns) {
            if (pattern.matcher(url).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a Chrome extension match pattern (e.g. "*://*.example.com/*", "<all_urls>") into regex.
     */
    public static Pattern compileMatchPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) return null;
        if ("<all_urls>".equals(pattern)) {
            return Pattern.compile("^(https?|file|ftp)://.*$");
        }

        try {
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("*://", "(https?|file|ftp)://")
                    .replace("*.", "([^/]+\\.)?")
                    .replace("/*", "(/.*)?")
                    .replace("*", ".*");
            return Pattern.compile("^" + regex + "$");
        } catch (Exception e) {
            return null;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("matches", new JSONArray(mMatches));
        obj.put("js", new JSONArray(mJsFiles));
        obj.put("css", new JSONArray(mCssFiles));
        obj.put("run_at", mRunAt.name());
        obj.put("all_frames", mAllFrames);
        obj.put("match_about_blank", mMatchAboutBlank);
        return obj;
    }

    public static ExtensionContentScript fromJson(JSONObject obj) throws JSONException {
        List<String> matches = new ArrayList<>();
        JSONArray matchesArr = obj.optJSONArray("matches");
        if (matchesArr != null) {
            for (int i = 0; i < matchesArr.length(); i++) {
                matches.add(matchesArr.getString(i));
            }
        }

        List<String> jsFiles = new ArrayList<>();
        JSONArray jsArr = obj.optJSONArray("js");
        if (jsArr != null) {
            for (int i = 0; i < jsArr.length(); i++) {
                jsFiles.add(jsArr.getString(i));
            }
        }

        List<String> cssFiles = new ArrayList<>();
        JSONArray cssArr = obj.optJSONArray("css");
        if (cssArr != null) {
            for (int i = 0; i < cssArr.length(); i++) {
                cssFiles.add(cssArr.getString(i));
            }
        }

        String runAtStr = obj.optString("run_at", RunAt.DOCUMENT_IDLE.name());
        RunAt runAt = RunAt.DOCUMENT_IDLE;
        try {
            runAt = RunAt.valueOf(runAtStr);
        } catch (IllegalArgumentException ignored) {}

        boolean allFrames = obj.optBoolean("all_frames", false);
        boolean matchAboutBlank = obj.optBoolean("match_about_blank", false);

        return new ExtensionContentScript(matches, jsFiles, cssFiles, runAt, allFrames, matchAboutBlank);
    }
}
