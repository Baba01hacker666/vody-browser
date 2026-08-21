package org.vody.browser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * Main browser window. Hosts a single GeckoView that swaps between {@link GeckoSession} tabs.
 * Tab sessions are created through the shared {@link VodyApplication} GeckoRuntime so all tabs
 * share one engine process.
 */
public class MainActivity extends AppCompatActivity {
    private GeckoView mGeckoView;
    private EditText mUrlBar;
    private ImageView mSecurityIcon;
    private ImageButton mBookmarkBtn;
    private TextView mTabCountBadge;
    private VodyApplication mApp;
    private final List<Tab> mTabs = new ArrayList<>();
    private int mActive = -1;
    private boolean mCanGoBack = false;
    private boolean mCanGoForward = false;
    private View mProgress;
    private String mHomepage;
    private BottomSheetDialog mTabSheet;

    @Override
    @SuppressLint("NonConstantResourceId")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApp = (VodyApplication) getApplication();
        mHomepage = getSharedPreferences("vody", 0)
                .getString("homepage", getString(R.string.home_url));

        mGeckoView = findViewById(R.id.geckoview);
        mUrlBar = findViewById(R.id.url_bar);
        mSecurityIcon = findViewById(R.id.security_icon);
        mBookmarkBtn = findViewById(R.id.bookmark_btn);
        mTabCountBadge = findViewById(R.id.tab_count_badge);
        mProgress = findViewById(R.id.progress);

        mUrlBar.setOnEditorActionListener((v, actionId, event) -> {
            navigate(mUrlBar.getText().toString());
            return true;
        });
        mBookmarkBtn.setOnClickListener(v -> toggleBookmark());
        findViewById(R.id.go_btn).setOnClickListener(v -> navigate(mUrlBar.getText().toString()));

        setupInstallPrompt();

        ImageButton back = findViewById(R.id.nav_back);
        ImageButton fwd = findViewById(R.id.nav_forward);
        ImageButton reload = findViewById(R.id.nav_reload);
        back.setOnClickListener(v -> { GeckoSession s = currentSession(); if (s != null && mCanGoBack) s.goBack(); });
        fwd.setOnClickListener(v -> { GeckoSession s = currentSession(); if (s != null) s.goForward(); });
        reload.setOnClickListener(v -> { GeckoSession s = currentSession(); if (s != null) s.reload(); });
        findViewById(R.id.tab_count_badge).setOnClickListener(v -> openTabSwitcher());
        findViewById(R.id.nav_menu).setOnClickListener(v -> openMenuSheet());

        createTab(null);

        // Deep-link / VIEW intent: open the supplied URL in the active tab.
        handleIntent(getIntent());
        if (mTabs.get(mActive).getUrl().isEmpty()) {
            loadInActive(mHomepage);
        }
        updateChrome();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /** Routes VIEW deep-links and "open_url" extras (from bookmarks/history/store screens). */
    private void handleIntent(Intent i) {
        if (i == null) return;
        String url = null;
        if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            url = i.getData().toString();
        } else if (i.getStringExtra("open_url") != null) {
            if (i.getBooleanExtra("new_tab", false)) newTab();
            url = i.getStringExtra("open_url");
        }
        if (!TextUtils.isEmpty(url)) loadInActive(url);
    }

    // ---- Firefox Add-ons store install flow ------------------------------------
    // While browsing addons.mozilla.org, tapping "Add to Firefox" triggers this
    // prompt through GeckoView's WebExtensionController; we confirm with the user
    // and let the engine download + install the .xpi.

    /** Shows a confirmation dialog when a site requests to install an extension. */
    private void setupInstallPrompt() {
        mApp.getRuntime().getWebExtensionController().setPromptDelegate(
                new org.mozilla.geckoview.WebExtensionController.PromptDelegate() {
                    @NonNull
                    @Override
                    public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                            @NonNull WebExtension extension,
                            @Nullable String[] permissions,
                            @Nullable String[] origins) {
                        GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                        runOnUiThread(() -> showInstallConfirmDialog(extension, permissions, result));
                        return result;
                    }
                });
    }

    private void showInstallConfirmDialog(WebExtension ext, String[] permissions,
                                          GeckoResult<WebExtension.PermissionPromptResponse> result) {
        WebExtension.PermissionPromptResponse deny = new WebExtension.PermissionPromptResponse(false, false);
        if (isDestroyed() || isFinishing()) {
            result.complete(deny);
            return;
        }
        StringBuilder msg = new StringBuilder(getString(R.string.ext_install_confirm,
                ext.metaData != null && ext.metaData.name != null ? ext.metaData.name : ext.id));
        if (ext.metaData != null && ext.metaData.version != null) {
            msg.append("\n\nv").append(ext.metaData.version);
        }
        int count = permissions == null ? 0 : permissions.length;
        msg.append("\n\n").append(count == 0
                ? getString(R.string.ext_no_permissions)
                : getString(R.string.ext_permissions_header));
        for (int i = 0; count > 0 && i < count; i++) {
            msg.append("\n• ").append(permissions[i]);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ext_install_prompt_title)
                .setMessage(msg)
                .setPositiveButton(R.string.ext_add,
                        (d, w) -> result.complete(new WebExtension.PermissionPromptResponse(true, false)))
                .setNegativeButton(android.R.string.cancel,
                        (d, w) -> result.complete(deny))
                .setOnCancelListener(d -> result.complete(deny))
                .show();
    }

    // ---- tabs ---------------------------------------------------------------

    /** Creates a new tab (GeckoSession) bound to the shared runtime. */
    private Tab createTab(String url) {
        GeckoSession session = new GeckoSession();
        session.open(mApp.getRuntime());
        Tab tab = new Tab(session);
        attachObservers(tab);
        mTabs.add(tab);
        mActive = mTabs.size() - 1;
        if (url != null) tab.setUrl(url);
        return tab;
    }

    private void attachObservers(Tab tab) {
        GeckoSession s = tab.getSession();
        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                tab.setUrl(url);
                if (isActive(tab)) runOnUiThread(() -> {
                    if (!mUrlBar.hasFocus()) mUrlBar.setText(prettyUrl(url));
                    mProgress.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                mApp.getStore().addHistory(tab.getTitle(), tab.getUrl());
                if (isActive(tab)) runOnUiThread(() -> {
                    mProgress.setVisibility(View.INVISIBLE);
                    updateBookmarkStar();
                });
            }
        });
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession session, @NonNull String url,
                                          @NonNull java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                                          Boolean isReload) {
                tab.setUrl(url);
                if (isActive(tab)) runOnUiThread(() -> {
                    if (!mUrlBar.hasFocus()) mUrlBar.setText(prettyUrl(url));
                    updateSecurityIcon(url);
                });
            }

            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                mCanGoBack = canGoBack;
                runOnUiThread(() -> setNavEnabled(R.id.nav_back, canGoBack));
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean canGoForward) {
                mCanGoForward = canGoForward;
                runOnUiThread(() -> setNavEnabled(R.id.nav_forward, canGoForward));
            }
        });
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                if (title != null) tab.setTitle(title);
            }
        });
    }

    private boolean isActive(Tab tab) {
        return mActive >= 0 && mActive < mTabs.size() && mTabs.get(mActive) == tab;
    }

    private void loadInActive(String url) {
        Tab tab = (mActive >= 0) ? mTabs.get(mActive) : createTab(null);
        tab.setUrl(url);
        mGeckoView.setSession(tab.getSession());
        mApp.setActiveSession(tab.getSession());
        tab.getSession().loadUri(url);
    }

    private void switchToTab(int pos) {
        if (pos < 0 || pos >= mTabs.size()) return;
        mActive = pos;
        Tab t = mTabs.get(pos);
        mGeckoView.setSession(t.getSession());
        mApp.setActiveSession(t.getSession());
        mUrlBar.setText(prettyUrl(t.getUrl()));
        updateSecurityIcon(t.getUrl());
        updateBookmarkStar();
        updateChrome();
    }

    /** Opens a brand-new tab loading the configured homepage and switches to it. */
    private void newTab() {
        Tab t = createTab(mHomepage);
        mGeckoView.setSession(t.getSession());
        mApp.setActiveSession(t.getSession());
        t.getSession().loadUri(t.getUrl());
        updateChrome();
        Toast.makeText(this, getString(R.string.tab_count, mTabs.size()), Toast.LENGTH_SHORT).show();
    }

    private void closeTab(int pos) {
        if (pos < 0 || pos >= mTabs.size()) return;
        Tab t = mTabs.remove(pos);
        t.getSession().close();
        if (mTabs.isEmpty()) {
            finish();
            return;
        }
        int next = Math.max(0, Math.min(pos, mTabs.size() - 1));
        mActive = next;
        switchToTab(next);
    }

    // ---- chrome (url bar / badges / nav buttons) -----------------------------

    /** Strips https:// and trailing slash so the address bar reads clean. */
    private static String prettyUrl(String url) {
        if (url == null) return "";
        String out = url;
        if (out.startsWith("https://")) out = out.substring(8);
        else if (out.startsWith("http://")) out = out.substring(7);
        while (out.endsWith("/") && !out.equals("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private void updateSecurityIcon(String url) {
        boolean secure = url != null && url.startsWith("https://");
        mSecurityIcon.setImageResource(secure ? R.drawable.ic_lock : R.drawable.ic_search);
    }

    private void updateBookmarkStar() {
        Tab t = currentTab();
        if (t == null) return;
        boolean marked = mApp.getStore().isBookmarked(t.getUrl());
        mBookmarkBtn.setImageResource(marked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        mBookmarkBtn.setColorFilter(ContextCompat.getColor(this,
                marked ? R.color.vody_accent : R.color.md_on_surface_variant));
    }

    private void updateChrome() {
        mTabCountBadge.setText(String.valueOf(mTabs.size()));
        setNavEnabled(R.id.nav_back, mCanGoBack);
        setNavEnabled(R.id.nav_forward, mCanGoForward);
        Tab t = currentTab();
        updateSecurityIcon(t == null ? "" : t.getUrl());
        updateBookmarkStar();
    }

    private void setNavEnabled(int buttonId, boolean enabled) {
        View v = findViewById(buttonId);
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.35f);
    }

    // ---- navigation ----------------------------------------------------------

    /** Normalises user input (searches vs URLs) and loads it in the active tab. */
    private void navigate(String input) {
        if (TextUtils.isEmpty(input)) return;
        String trimmed = input.trim();
        String url;
        if (URLUtil.isValidUrl(trimmed) || trimmed.startsWith("about:")) {
            url = trimmed;
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            url = "https://" + trimmed;
        } else {
            url = "https://www.google.com/search?q=" + trimmed.replace(" ", "+");
        }
        loadInActive(url);
        View focus = getCurrentFocus();
        if (focus != null) focus.clearFocus();
    }

    private GeckoSession currentSession() {
        return (mActive >= 0 && mActive < mTabs.size()) ? mTabs.get(mActive).getSession() : null;
    }

    private Tab currentTab() {
        return (mActive >= 0 && mActive < mTabs.size()) ? mTabs.get(mActive) : null;
    }

    // ---- menu sheet ------------------------------------------------------------

    private void openMenuSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_menu, null);
        bindMenuRow(sheet, v, R.id.menu_row_new_tab, () -> { sheet.dismiss(); newTab(); });
        bindMenuRow(sheet, v, R.id.menu_row_bookmarks, () -> { sheet.dismiss(); startActivity(listIntent(true)); });
        bindMenuRow(sheet, v, R.id.menu_row_history, () -> { sheet.dismiss(); startActivity(listIntent(false)); });
        bindMenuRow(sheet, v, R.id.menu_row_extensions,
                () -> { sheet.dismiss(); startActivity(new Intent(this, org.vody.browser.extensions.ExtensionManagerActivity.class)); });
        bindMenuRow(sheet, v, R.id.menu_row_devtools,
                () -> { sheet.dismiss(); DevToolsConsoleDialog.show(this, currentSession()); });
        bindMenuRow(sheet, v, R.id.menu_row_share, () -> { sheet.dismiss(); shareCurrent(); });
        bindMenuRow(sheet, v, R.id.menu_row_settings,
                () -> { sheet.dismiss(); startActivity(new Intent(this, org.vody.browser.settings.SettingsActivity.class)); });
        sheet.setContentView(v);
        sheet.show();
    }

    private interface RowAction { void run(); }

    private void bindMenuRow(BottomSheetDialog sheet, View root, int rowId, RowAction action) {
        root.findViewById(rowId).setOnClickListener(v -> action.run());
    }

    private Intent listIntent(boolean bookmarks) {
        Intent i = new Intent(this, ListActivity.class);
        i.putExtra(ListActivity.EXTRA_MODE, bookmarks ? ListActivity.MODE_BOOKMARKS : ListActivity.MODE_HISTORY);
        return i;
    }

    // ---- tab switcher sheet -----------------------------------------------------

    private void openTabSwitcher() {
        if (mTabSheet != null && mTabSheet.isShowing()) mTabSheet.dismiss();
        mTabSheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_tabs, null);
        RecyclerView list = v.findViewById(R.id.tabs_list);
        list.setLayoutManager(new LinearLayoutManager(this));

        TextView closeAll = v.findViewById(R.id.close_all_btn);
        TextView newTab = v.findViewById(R.id.new_tab_btn);
        TextView title = v.findViewById(R.id.tabs_title);

        Runnable refresh = () -> {
            title.setText(getString(R.string.tab_count, mTabs.size()));
            ((TabSheetAdapter) list.getAdapter()).notifyDataSetChanged();
        };

        TabSheetAdapter adapter = new TabSheetAdapter(refresh);
        list.setAdapter(adapter);

        closeAll.setOnClickListener(x -> {
            mTabSheet.dismiss();
            List<Tab> old = new ArrayList<>(mTabs);
            mTabs.clear();
            for (Tab t : old) t.getSession().close();
            createTab(null);
            loadInActive(mHomepage);
        });
        newTab.setOnClickListener(x -> {
            mTabSheet.dismiss();
            newTab();
        });

        mTabSheet.setContentView(v);
        mTabSheet.show();
    }

    private class TabSheetAdapter extends RecyclerView.Adapter<TabVH> {
        private final Runnable mRefresh;

        TabSheetAdapter(Runnable refresh) { mRefresh = refresh; }

        @NonNull
        @Override
        public TabVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab_row, parent, false);
            return new TabVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TabVH h, int pos) {
            Tab t = mTabs.get(pos);
            h.title.setText(t.getTitle().isEmpty()
                    ? prettyUrl(t.getUrl()) : t.getTitle());
            h.url.setText(prettyUrl(t.getUrl()));
            h.itemView.setAlpha(pos == mActive ? 1f : 0.6f);
            h.itemView.setOnClickListener(v -> {
                mTabSheet.dismiss();
                switchToTab(h.getBindingAdapterPosition());
            });
            h.close.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p < 0) return;
                closeTab(p);
                if (!mTabs.isEmpty() && mTabSheet != null && mTabSheet.isShowing()) {
                    mRefresh.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mTabs.size();
        }
    }

    private static class TabVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView url;
        final ImageView close;

        TabVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tab_title);
            url = v.findViewById(R.id.tab_url);
            close = v.findViewById(R.id.tab_close);
        }
    }

    // ---- bookmark / share -------------------------------------------------------

    private void toggleBookmark() {
        Tab t = currentTab();
        if (t == null || TextUtils.isEmpty(t.getUrl())) return;
        BrowseStore store = mApp.getStore();
        String url = t.getUrl();
        String title = t.getTitle();
        if (store.isBookmarked(url)) {
            store.removeBookmark(url);
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
        } else {
            store.addBookmark(new Bookmark(title.isEmpty() ? prettyUrl(url) : title, url));
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show();
        }
        updateBookmarkStar();
    }

    private void shareCurrent() {
        Tab t = currentTab();
        if (t == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, t.getUrl());
        startActivity(Intent.createChooser(share, getString(R.string.share_via)));
    }

    // Pressing back navigates the active session if it can go back.
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        GeckoSession s = currentSession();
        if (s != null && mCanGoBack) {
            s.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (Tab t : mTabs) t.getSession().close();
        mTabs.clear();
        super.onDestroy();
    }
}
