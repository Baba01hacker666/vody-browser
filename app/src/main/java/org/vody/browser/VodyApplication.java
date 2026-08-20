package org.vody.browser;

import android.app.Application;
import android.util.Log;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-level singleton. Owns the shared GeckoRuntime (one per process) and the in-memory
 * browse session store (bookmarks / history / extensions).
 *
 * Security hardening: on first runtime creation it applies WebLibre's "privacy by default" posture
 * through GeckoView's own runtime settings (ETP/anti-tracking + strict social, Global Privacy
 * Control, remote debugging for on-device DevTools, JavaScript on) and registers the built-in
 * DevTools eval extension (assets/extensions/vodyeval) used by the on-device console. Additional
 * enforced prefs from WebLibre are documented in assets/weblibre_hardening.json.
 */
public class VodyApplication extends Application {
    private static final String TAG = "VodyApp";
    private GeckoRuntime mRuntime;
    private BrowseStore mStore;
    private WebExtension mEvalExtension;
    private final List<EvalListener> mEvalListeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mStore = new BrowseStore(this);
    }

    /** Returns the process-wide GeckoRuntime, creating it on first use. */
    public synchronized GeckoRuntime getRuntime() {
        if (mRuntime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .remoteDebuggingEnabled(true)
                    .globalPrivacyControlEnabled(true)
                    .contentBlocking(new ContentBlocking.Settings.Builder()
                            .antiTracking(ContentBlocking.AntiTracking.AD |
                                    ContentBlocking.AntiTracking.SOCIAL |
                                    ContentBlocking.AntiTracking.CRYPTOMINING)
                            .strictSocialTrackingProtection(true)
                            .build())
                    .build();
            GeckoRuntime runtime = GeckoRuntime.create(this, settings);
            // Register the built-in DevTools eval extension (no-op if it fails).
            registerEvalExtension(runtime);
            mRuntime = runtime;
        }
        return mRuntime;
    }

    private void registerEvalExtension(GeckoRuntime runtime) {
        try {
            runtime.getWebExtensionController()
                    .installBuiltIn("resource://android/assets/extensions/vodyeval/")
                    .accept(ext -> {
                        mEvalExtension = ext;
                        ext.setMessageDelegate(new WebExtension.MessageDelegate() {
                            @Override
                            public GeckoResult<Object> onMessage(String nativeApp, Object message,
                                                  WebExtension.MessageSender sender) {
                                if (message instanceof java.util.Map) {
                                    Object type = ((java.util.Map<?, ?>) message).get("type");
                                    if ("eval".equals(type)) broadcastEval(message);
                                }
                                return GeckoResult.fromValue(null);
                            }
                        }, "vodyeval");
                        Log.i(TAG, "DevTools eval extension registered");
                    })
                    .exceptionally(th -> {
                        Log.w(TAG, "eval extension registration failed", th);
                        return null;
                    });
        } catch (Exception e) {
            Log.w(TAG, "eval extension install failed", e);
        }
    }

    /** Routes an eval result from the extension to the open DevTools dialog(s). */
    private void broadcastEval(Object message) {
        synchronized (mEvalListeners) {
            for (EvalListener l : mEvalListeners) l.onEvalResult(message);
        }
    }

    public void addEvalListener(EvalListener l) {
        synchronized (mEvalListeners) { mEvalListeners.add(l); }
    }

    public void removeEvalListener(EvalListener l) {
        synchronized (mEvalListeners) { mEvalListeners.remove(l); }
    }

    /** Runs JS in the active session via the eval extension (content-script isolated world). */
    public void evaluate(String js) {
        if (mEvalExtension == null || mActiveSession == null) return;
        String safe = js.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        mActiveSession.loadUri("javascript:window.dispatchEvent(new CustomEvent('vody-eval',{detail:'"
                + safe + "'}))");
    }

    // The currently displayed session, updated by MainActivity.
    private org.mozilla.geckoview.GeckoSession mActiveSession;
    public void setActiveSession(org.mozilla.geckoview.GeckoSession s) { mActiveSession = s; }

    public BrowseStore getStore() {
        return mStore;
    }

    /** Callback for DevTools eval results. */
    public interface EvalListener {
        void onEvalResult(Object message);
    }
}
