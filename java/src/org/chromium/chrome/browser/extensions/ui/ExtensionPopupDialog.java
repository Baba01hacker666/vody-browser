package org.chromium.chrome.browser.extensions.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.chromium.chrome.browser.extensions.model.Extension;
import org.chromium.chrome.browser.extensions.runtime.ExtensionApiBridge;
import org.chromium.chrome.browser.extensions.runtime.ExtensionManager;

import java.io.File;

/**
 * Dialog that renders an extension's Action popup HTML interface (e.g., uBlock Origin, Dark Reader, MetaMask).
 */
public class ExtensionPopupDialog extends Dialog {

    private final Extension mExtension;
    private final ExtensionManager mExtensionManager;

    public ExtensionPopupDialog(Context context, Extension extension) {
        super(context);
        mExtension = extension;
        mExtensionManager = ExtensionManager.getInstance(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.75));
        }

        FrameLayout container = new FrameLayout(getContext());
        WebView webView = new WebView(getContext());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        ExtensionApiBridge bridge = new ExtensionApiBridge(getContext(), mExtensionManager);
        webView.addJavascriptInterface(bridge.new ExtensionJsInterface(), "__vodyExtBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String polyfill = ExtensionApiBridge.generateChromeApiPolyfill(mExtension);
                view.evaluateJavascript(polyfill, null);
            }
        });

        String popupPath = mExtension.getAction().getDefaultPopup();
        File popupFile = new File(mExtension.getInstallPath(), popupPath);
        if (popupFile.exists()) {
            webView.loadUrl("file://" + popupFile.getAbsolutePath());
        } else {
            webView.loadDataWithBaseURL(null, "<h3>Popup not found</h3>", "text/html", "UTF-8", null);
        }

        container.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(container);
    }
}
