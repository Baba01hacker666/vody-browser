package org.chromium.chrome.browser.extensions.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import org.chromium.chrome.browser.extensions.model.Extension;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * JavaScript Bridge and polyfill generator for Chrome Extensions APIs (chrome.runtime, chrome.storage, chrome.tabs, chrome.action).
 */
public class ExtensionApiBridge {

    private final Context mContext;
    private final ExtensionManager mExtensionManager;

    public ExtensionApiBridge(Context context, ExtensionManager manager) {
        mContext = context.getApplicationContext();
        mExtensionManager = manager;
    }

    /**
     * Generates the JS polyfill code that exposes the chrome.* extension API namespace inside web views / content scripts.
     */
    public static String generateChromeApiPolyfill(Extension extension) {
        String extId = extension.getId();
        return "(function() {\n"
                + "  if (window.chrome && window.chrome.runtime && window.chrome.runtime.id) return;\n"
                + "  window.chrome = window.chrome || {};\n"
                + "  var _extId = '" + extId + "';\n"
                + "  var _messageListeners = [];\n"
                + "\n"
                + "  // chrome.runtime API\n"
                + "  chrome.runtime = {\n"
                + "    id: _extId,\n"
                + "    getURL: function(path) {\n"
                + "      if (!path) return '';\n"
                + "      if (path.startsWith('/')) path = path.substring(1);\n"
                + "      return 'chrome-extension://' + _extId + '/' + path;\n"
                + "    },\n"
                + "    sendMessage: function(targetId, message, options, responseCallback) {\n"
                + "      if (typeof targetId !== 'string') {\n"
                + "        responseCallback = options;\n"
                + "        options = message;\n"
                + "        message = targetId;\n"
                + "        targetId = _extId;\n"
                + "      }\n"
                + "      if (typeof options === 'function') {\n"
                + "        responseCallback = options;\n"
                + "        options = {};\n"
                + "      }\n"
                + "      try {\n"
                + "        if (window.__vodyExtBridge) {\n"
                + "          var res = window.__vodyExtBridge.sendMessage(_extId, JSON.stringify(message));\n"
                + "          if (responseCallback && res) responseCallback(JSON.parse(res));\n"
                + "        }\n"
                + "      } catch(e) { console.error('Ext sendMessage error:', e); }\n"
                + "    },\n"
                + "    onMessage: {\n"
                + "      addListener: function(fn) { _messageListeners.push(fn); },\n"
                + "      removeListener: function(fn) {\n"
                + "        var idx = _messageListeners.indexOf(fn);\n"
                + "        if (idx !== -1) _messageListeners.splice(idx, 1);\n"
                + "      },\n"
                + "      hasListener: function(fn) { return _messageListeners.indexOf(fn) !== -1; }\n"
                + "    }\n"
                + "  };\n"
                + "\n"
                + "  // chrome.storage API (local & sync)\n"
                + "  function createStorageArea(areaName) {\n"
                + "    return {\n"
                + "      get: function(keys, callback) {\n"
                + "        var res = {};\n"
                + "        try {\n"
                + "          if (window.__vodyExtBridge) {\n"
                + "            var data = window.__vodyExtBridge.storageGet(_extId, areaName, JSON.stringify(keys || null));\n"
                + "            if (data) res = JSON.parse(data);\n"
                + "          }\n"
                + "        } catch(e) { console.error(e); }\n"
                + "        if (callback) callback(res);\n"
                + "        return Promise.resolve(res);\n"
                + "      },\n"
                + "      set: function(items, callback) {\n"
                + "        try {\n"
                + "          if (window.__vodyExtBridge) {\n"
                + "            window.__vodyExtBridge.storageSet(_extId, areaName, JSON.stringify(items));\n"
                + "          }\n"
                + "        } catch(e) { console.error(e); }\n"
                + "        if (callback) callback();\n"
                + "        return Promise.resolve();\n"
                + "      },\n"
                + "      remove: function(keys, callback) {\n"
                + "        try {\n"
                + "          if (window.__vodyExtBridge) {\n"
                + "            window.__vodyExtBridge.storageRemove(_extId, areaName, JSON.stringify(keys));\n"
                + "          }\n"
                + "        } catch(e) { console.error(e); }\n"
                + "        if (callback) callback();\n"
                + "        return Promise.resolve();\n"
                + "      },\n"
                + "      clear: function(callback) {\n"
                + "        try {\n"
                + "          if (window.__vodyExtBridge) {\n"
                + "            window.__vodyExtBridge.storageClear(_extId, areaName);\n"
                + "          }\n"
                + "        } catch(e) { console.error(e); }\n"
                + "        if (callback) callback();\n"
                + "        return Promise.resolve();\n"
                + "      }\n"
                + "    };\n"
                + "  }\n"
                + "\n"
                + "  chrome.storage = {\n"
                + "    local: createStorageArea('local'),\n"
                + "    sync: createStorageArea('sync')\n"
                + "  };\n"
                + "\n"
                + "  // chrome.action / browserAction API\n"
                + "  var actionApi = {\n"
                + "    setBadgeText: function(details) {\n"
                + "      if (window.__vodyExtBridge && details) window.__vodyExtBridge.setBadgeText(_extId, details.text || '');\n"
                + "    },\n"
                + "    setBadgeBackgroundColor: function(details) {\n"
                + "      if (window.__vodyExtBridge && details && details.color) window.__vodyExtBridge.setBadgeBgColor(_extId, JSON.stringify(details.color));\n"
                + "    },\n"
                + "    setTitle: function(details) {\n"
                + "      if (window.__vodyExtBridge && details) window.__vodyExtBridge.setActionTitle(_extId, details.title || '');\n"
                + "    }\n"
                + "  };\n"
                + "  chrome.action = actionApi;\n"
                + "  chrome.browserAction = actionApi;\n"
                + "})();\n";
    }

    /**
     * Injected Javascript Interface for bridge communication.
     */
    public class ExtensionJsInterface {
        @JavascriptInterface
        public String storageGet(String extId, String area, String keysJson) {
            SharedPreferences sp = mContext.getSharedPreferences("ext_storage_" + extId + "_" + area, Context.MODE_PRIVATE);
            JSONObject result = new JSONObject();
            try {
                Map<String, ?> all = sp.getAll();
                if (keysJson == null || keysJson.equals("null")) {
                    for (Map.Entry<String, ?> entry : all.entrySet()) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                } else if (keysJson.startsWith("{")) {
                    JSONObject defaults = new JSONObject(keysJson);
                    Iterator<String> it = defaults.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        result.put(k, sp.getString(k, defaults.optString(k)));
                    }
                } else if (keysJson.startsWith("[")) {
                    org.json.JSONArray arr = new org.json.JSONArray(keysJson);
                    for (int i = 0; i < arr.length(); i++) {
                        String k = arr.getString(i);
                        if (sp.contains(k)) {
                            result.put(k, sp.getString(k, ""));
                        }
                    }
                } else {
                    String k = keysJson.replace("\"", "");
                    if (sp.contains(k)) {
                        result.put(k, sp.getString(k, ""));
                    }
                }
            } catch (Exception ignored) {}
            return result.toString();
        }

        @JavascriptInterface
        public void storageSet(String extId, String area, String itemsJson) {
            SharedPreferences sp = mContext.getSharedPreferences("ext_storage_" + extId + "_" + area, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            try {
                JSONObject items = new JSONObject(itemsJson);
                Iterator<String> it = items.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    Object val = items.get(key);
                    editor.putString(key, val.toString());
                }
                editor.apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void storageRemove(String extId, String area, String keysJson) {
            SharedPreferences sp = mContext.getSharedPreferences("ext_storage_" + extId + "_" + area, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            try {
                if (keysJson.startsWith("[")) {
                    org.json.JSONArray arr = new org.json.JSONArray(keysJson);
                    for (int i = 0; i < arr.length(); i++) {
                        editor.remove(arr.getString(i));
                    }
                } else {
                    editor.remove(keysJson.replace("\"", ""));
                }
                editor.apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void storageClear(String extId, String area) {
            SharedPreferences sp = mContext.getSharedPreferences("ext_storage_" + extId + "_" + area, Context.MODE_PRIVATE);
            sp.edit().clear().apply();
        }

        @JavascriptInterface
        public void setBadgeText(String extId, String text) {
            Extension ext = mExtensionManager.getExtension(extId);
            if (ext != null && ext.getAction() != null) {
                ext.getAction().setBadgeText(text);
                mExtensionManager.notifyExtensionActionChanged(ext);
            }
        }

        @JavascriptInterface
        public void setBadgeBgColor(String extId, String color) {
            Extension ext = mExtensionManager.getExtension(extId);
            if (ext != null && ext.getAction() != null) {
                ext.getAction().setBadgeBackgroundColor(color);
                mExtensionManager.notifyExtensionActionChanged(ext);
            }
        }

        @JavascriptInterface
        public void setActionTitle(String extId, String title) {
            Extension ext = mExtensionManager.getExtension(extId);
            if (ext != null && ext.getAction() != null) {
                ext.getAction().setTitle(title);
                mExtensionManager.notifyExtensionActionChanged(ext);
            }
        }

        @JavascriptInterface
        public String sendMessage(String extId, String messageJson) {
            return "{\"success\": true}";
        }
    }
}
