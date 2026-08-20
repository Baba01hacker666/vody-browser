package org.chromium.chrome.browser.devtools;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;

/**
 * On-device Developer Tools (Inspector, Console, Network monitor, DOM explorer) for Vody Browser.
 */
public class VodyDevTools {

    private static final String DEVTOOLS_INJECTION_SCRIPT =
            "(function() {\n"
            + "  if (window.eruda && window.eruda._isInit) {\n"
            + "    window.eruda.show();\n"
            + "    return;\n"
            + "  }\n"
            + "  var script = document.createElement('script');\n"
            + "  script.src = 'https://cdn.jsdelivr.net/npm/eruda';\n"
            + "  script.onload = function() {\n"
            + "    if (window.eruda) {\n"
            + "      window.eruda.init();\n"
            + "      window.eruda.show();\n"
            + "    }\n"
            + "  };\n"
            + "  script.onerror = function() {\n"
            + "    // Fallback: minimal inline DevTools console\n"
            + "    initInlineConsole();\n"
            + "  };\n"
            + "  document.head.appendChild(script);\n"
            + "\n"
            + "  function initInlineConsole() {\n"
            + "    if (document.getElementById('__vody_console')) return;\n"
            + "    var div = document.createElement('div');\n"
            + "    div.id = '__vody_console';\n"
            + "    div.style.cssText = 'position:fixed;bottom:0;left:0;right:0;height:40vh;background:#1e1e1e;color:#0f0;font-family:monospace;font-size:12px;z-index:999999;overflow:auto;padding:8px;border-top:2px solid #007acc;';\n"
            + "    div.innerHTML = '<div style=\"display:flex;justify-content:space-between;border-bottom:1px solid #444;padding-bottom:4px;margin-bottom:4px;\"><b>Vody DevTools Console</b><button onclick=\"this.parentElement.parentElement.remove()\" style=\"color:#fff;background:#444;border:none;\">Close</button></div><div id=\"__vody_logs\"></div><input type=\"text\" id=\"__vody_cmd\" placeholder=\"JavaScript expression...\" style=\"width:100%;background:#333;color:#fff;border:1px solid #555;padding:4px;margin-top:4px;\">';\n"
            + "    document.body.appendChild(div);\n"
            + "    var cmdInput = document.getElementById('__vody_cmd');\n"
            + "    cmdInput.addEventListener('keydown', function(e) {\n"
            + "      if (e.key === 'Enter') {\n"
            + "        var val = cmdInput.value;\n"
            + "        cmdInput.value = '';\n"
            + "        var logDiv = document.getElementById('__vody_logs');\n"
            + "        try {\n"
            + "          var res = eval(val);\n"
            + "          logDiv.innerHTML += '<div style=\"color:#80d4ff;\">&gt; ' + val + '</div><div>&lt; ' + String(res) + '</div>';\n"
            + "        } catch(err) {\n"
            + "          logDiv.innerHTML += '<div style=\"color:#ff6b6b;\">&gt; ' + val + '<br>Error: ' + err.message + '</div>';\n"
            + "        }\n"
            + "        div.scrollTop = div.scrollHeight;\n"
            + "      }\n"
            + "    });\n"
            + "  }\n"
            + "})();";

    /**
     * Injects on-device DevTools into the given tab.
     */
    public static void toggleDevTools(Context context, Tab tab) {
        if (tab == null) {
            Toast.makeText(context, "No active tab to inspect", Toast.LENGTH_SHORT).show();
            return;
        }

        WebContents webContents = tab.getWebContents();
        if (webContents == null || webContents.isDestroyed()) {
            Toast.makeText(context, "Page is not ready for DevTools", Toast.LENGTH_SHORT).show();
            return;
        }

        evaluateJavaScriptSafely(webContents, DEVTOOLS_INJECTION_SCRIPT);
        Toast.makeText(context, "DevTools activated for current page", Toast.LENGTH_SHORT).show();
    }

    /**
     * Opens native DevTools interactive console dialog.
     */
    public static void openConsoleDialog(Activity activity, Tab tab) {
        new DevToolsConsoleDialog(activity, tab).show();
    }

    public static void evaluateJavaScriptSafely(WebContents webContents, String script) {
        if (webContents == null || webContents.isDestroyed()) return;
        try {
            webContents.evaluateJavaScriptForTests(script, null);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Method m = webContents.getClass().getMethod("evaluateJavaScript", String.class, Object.class);
                m.invoke(webContents, script, null);
            } catch (Throwable ignored) {}
        }
    }
}
