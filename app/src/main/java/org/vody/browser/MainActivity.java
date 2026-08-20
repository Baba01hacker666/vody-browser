package org.vody.browser;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private VodyApplication mApp;
    private final List<Tab> mTabs = new ArrayList<>();
    private int mActive = -1;
    private boolean mCanGoBack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApp = (VodyApplication) getApplication();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mGeckoView = findViewById(R.id.geckoview);
        mGeckoView.setSession(createTab(null).getSession());

        mUrlBar = findViewById(R.id.url_bar);
        ImageButton go = findViewById(R.id.go_btn);
        go.setOnClickListener(v -> navigate(mUrlBar.getText().toString()));
        mUrlBar.setOnEditorActionListener((v, actionId, event) -> {
            navigate(mUrlBar.getText().toString());
            return true;
        });

        // Bottom navigation bar: back / forward / reload / new tab / menu.
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_back) {
                if (mCanGoBack && currentSession() != null) currentSession().goBack();
                return true;
            } else if (id == R.id.nav_forward) {
                if (currentSession() != null) currentSession().goForward();
                return true;
            } else if (id == R.id.nav_reload) {
                if (currentSession() != null) currentSession().reload();
                return true;
            } else if (id == R.id.nav_tabs) {
                newTab();
                return true;
            } else if (id == R.id.nav_menu) {
                openMenuSheet();
                return true;
            }
            return false;
        });

        // Deep-link / VIEW intent: open the supplied URL in a new tab.
        Intent i = getIntent();
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            openUrl(i.getData().toString());
        } else {
            openUrl(getString(R.string.home_url));
        }
    }

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
                if (isActive(tab)) runOnUiThread(() -> mUrlBar.setText(url));
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                mApp.getStore().addHistory(tab.getTitle(), tab.getUrl());
            }
        });
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession session, @NonNull String url,
                                          @NonNull java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                                          Boolean isReload) {
                tab.setUrl(url);
                if (isActive(tab)) runOnUiThread(() -> mUrlBar.setText(url));
            }

            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                mCanGoBack = canGoBack;
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

    /** Normalises user input (searches vs URLs) and loads it in the active tab. */
    private void navigate(String input) {
        if (TextUtils.isEmpty(input)) return;
        String url;
        if (URLUtil.isValidUrl(input) || input.startsWith("about:")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + input.replace(" ", "+");
        }
        openUrl(url);
    }

    private void openUrl(String url) {
        Tab tab = (mActive >= 0) ? mTabs.get(mActive) : createTab(null);
        tab.setUrl(url);
        mGeckoView.setSession(tab.getSession());
        mUrlBar.setText(url);
        mApp.setActiveSession(tab.getSession());
        tab.getSession().loadUri(url);
    }

    private void newTab() {
        Tab t = createTab(getString(R.string.home_url));
        mGeckoView.setSession(t.getSession());
        t.getSession().loadUri(t.getUrl());
        Toast.makeText(this, getString(R.string.tab_count, mTabs.size()), Toast.LENGTH_SHORT).show();
    }

    private void closeActiveTab() {
        if (mTabs.isEmpty()) return;
        Tab t = mTabs.remove(mActive);
        t.getSession().close();
        if (mTabs.isEmpty()) {
            finish();
            return;
        }
        mActive = Math.max(0, mActive - 1);
        mGeckoView.setSession(mTabs.get(mActive).getSession());
        mUrlBar.setText(mTabs.get(mActive).getUrl());
    }

    // ---- menu -------------------------------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_tab) {
            newTab();
            return true;
        } else if (id == R.id.action_bookmarks) {
            showBookmarks();
            return true;
        } else if (id == R.id.action_history) {
            showHistory();
            return true;
        } else if (id == R.id.action_devtools) {
            DevToolsConsoleDialog.show(this, currentSession());
            return true;
        } else if (id == R.id.action_extensions) {
            startActivity(new Intent(this, org.vody.browser.extensions.ExtensionManagerActivity.class));
            return true;
        } else if (id == R.id.action_share) {
            shareCurrent();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, org.vody.browser.settings.SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openMenuSheet() {
        CharSequence[] items = new CharSequence[]{
                getString(R.string.menu_bookmarks),
                getString(R.string.menu_history),
                getString(R.string.menu_devtools),
                getString(R.string.menu_extensions),
                getString(R.string.menu_share),
                getString(R.string.menu_settings)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_open)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showBookmarks(); break;
                        case 1: showHistory(); break;
                        case 2: DevToolsConsoleDialog.show(this, currentSession()); break;
                        case 3: startActivity(new Intent(this, org.vody.browser.extensions.ExtensionManagerActivity.class)); break;
                        case 4: shareCurrent(); break;
                        case 5: startActivity(new Intent(this, org.vody.browser.settings.SettingsActivity.class)); break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private GeckoSession currentSession() {
        return (mActive >= 0 && mActive < mTabs.size()) ? mTabs.get(mActive).getSession() : null;
    }

    private void shareCurrent() {
        Tab t = (mActive >= 0) ? mTabs.get(mActive) : null;
        if (t == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, t.getUrl());
        startActivity(Intent.createChooser(share, getString(R.string.share_via)));
    }

    private void showBookmarks() {
        List<Bookmark> list = mApp.getStore().getBookmarks();
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) items[i] = list.get(i).title + "\n" + list.get(i).url;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bookmarks_title)
                .setItems(items, (d, which) -> openUrl(list.get(which).url))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHistory() {
        List<Bookmark> list = mApp.getStore().getHistory();
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_history, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) items[i] = list.get(i).title + "\n" + list.get(i).url;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_title)
                .setItems(items, (d, which) -> openUrl(list.get(which).url))
                .setPositiveButton(R.string.clear_history, (d, w) -> {
                    mApp.getStore().clearHistory();
                    Toast.makeText(this, R.string.clear_history, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Pressing back navigates the active session if it can go back.
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
