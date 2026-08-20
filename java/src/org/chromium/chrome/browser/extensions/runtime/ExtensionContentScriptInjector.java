package org.chromium.chrome.browser.extensions.runtime;

import android.util.Base64;

import org.chromium.chrome.browser.extensions.model.Extension;
import org.chromium.chrome.browser.extensions.model.ExtensionContentScript;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles injecting CSS stylesheets and Javascript content scripts into web pages matching extension manifest rules.
 */
public class ExtensionContentScriptInjector {

    public interface ScriptEvaluator {
        void evaluateJavascript(String script);
    }

    /**
     * Injects matching content scripts for the given URL and timing phase (document_start, document_end, document_idle).
     */
    public static void injectScripts(
            List<Extension> extensions,
            String url,
            ExtensionContentScript.RunAt runAt,
            ScriptEvaluator evaluator) {
        if (extensions == null || url == null || evaluator == null) return;

        for (Extension ext : extensions) {
            if (!ext.isEnabled()) continue;

            for (ExtensionContentScript cs : ext.getContentScripts()) {
                if (cs.getRunAt() == runAt && cs.matchesUrl(url)) {
                    // Inject CSS first
                    for (String cssFile : cs.getCssFiles()) {
                        String cssContent = readFileContent(new File(ext.getInstallPath(), cssFile));
                        if (cssContent != null && !cssContent.isEmpty()) {
                            String injectCssJs = buildCssInjectionScript(cssContent);
                            evaluator.evaluateJavascript(injectCssJs);
                        }
                    }

                    // Inject Polyfill + JS Content Script
                    for (String jsFile : cs.getJsFiles()) {
                        String jsContent = readFileContent(new File(ext.getInstallPath(), jsFile));
                        if (jsContent != null && !jsContent.isEmpty()) {
                            String polyfill = ExtensionApiBridge.generateChromeApiPolyfill(ext);
                            String wrappedScript = wrapScript(polyfill, jsContent, ext.getId(), jsFile);
                            evaluator.evaluateJavascript(wrappedScript);
                        }
                    }
                }
            }
        }
    }

    private static String buildCssInjectionScript(String css) {
        String base64Css = Base64.encodeToString(css.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "(function() {\n"
                + "  var style = document.createElement('style');\n"
                + "  style.type = 'text/css';\n"
                + "  style.textContent = decodeURIComponent(escape(window.atob('" + base64Css + "')));\n"
                + "  (document.head || document.documentElement).appendChild(style);\n"
                + "})();";
    }

    private static String wrapScript(String polyfill, String userScript, String extId, String filename) {
        return "(function() {\n"
                + "  try {\n"
                + polyfill
                + "\n"
                + userScript
                + "\n"
                + "  } catch(e) {\n"
                + "    console.error('Vody Extension [" + extId + "] error in " + filename + ":', e);\n"
                + "  }\n"
                + "})();";
    }

    private static String readFileContent(File file) {
        if (!file.exists()) return null;
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            is.read(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
