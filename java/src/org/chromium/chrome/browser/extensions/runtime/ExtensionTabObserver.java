package org.chromium.chrome.browser.extensions.runtime;

import org.chromium.chrome.browser.extensions.model.Extension;
import org.chromium.chrome.browser.extensions.model.ExtensionContentScript;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.GURL;

import java.util.List;

/**
 * TabObserver that triggers content script injection for active web pages.
 */
public class ExtensionTabObserver extends EmptyTabObserver {

    private final ExtensionManager mExtensionManager;

    public ExtensionTabObserver(ExtensionManager manager) {
        mExtensionManager = manager;
    }

    @Override
    public void onDidFinishLoadInPrimaryMainFrame(Tab tab, GURL url) {
        if (tab == null || url == null) return;
        injectForTab(tab, url.getSpec(), ExtensionContentScript.RunAt.DOCUMENT_IDLE);
    }

    @Override
    public void onPageLoadStarted(Tab tab, GURL url) {
        if (tab == null || url == null) return;
        injectForTab(tab, url.getSpec(), ExtensionContentScript.RunAt.DOCUMENT_START);
    }

    @Override
    public void onPageLoadFinished(Tab tab, GURL url) {
        if (tab == null || url == null) return;
        injectForTab(tab, url.getSpec(), ExtensionContentScript.RunAt.DOCUMENT_END);
    }

    private void injectForTab(Tab tab, String urlSpec, ExtensionContentScript.RunAt runAt) {
        WebContents webContents = tab.getWebContents();
        if (webContents == null || webContents.isDestroyed()) return;

        List<Extension> extensions = mExtensionManager.getInstalledExtensions();
        ExtensionContentScriptInjector.injectScripts(
                extensions,
                urlSpec,
                runAt,
                script -> org.chromium.chrome.browser.devtools.VodyDevTools.evaluateJavaScriptSafely(webContents, script));
    }
}
