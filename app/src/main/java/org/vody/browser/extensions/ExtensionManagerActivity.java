package org.vody.browser.extensions;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.vody.browser.BrowseStore;
import org.vody.browser.ExtensionInfo;
import org.vody.browser.R;
import org.vody.browser.VodyApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages WebExtensions through GeckoView's WebExtensionController. Extensions can be installed from
 * a Chrome Web Store URL/ID (GeckoView resolves the .xpi) or an unpacked file:// path. Each entry can
 * be enabled/disabled and removed; state is persisted in {@link BrowseStore}.
 */
public class ExtensionManagerActivity extends AppCompatActivity {
    private static final Pattern ID_PATTERN = Pattern.compile("([a-p]{32})");
    private VodyApplication mApp;
    private BrowseStore mStore;
    private final List<ExtensionInfo> mList = new ArrayList<>();
    private ExtAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);
        setTitle(R.string.extensions_title);

        mApp = (VodyApplication) getApplication();
        mStore = mApp.getStore();

        RecyclerView rv = findViewById(R.id.list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ExtAdapter();
        rv.setAdapter(mAdapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> promptInstall());

        refresh();
    }

    private void refresh() {
        mList.clear();
        mList.addAll(mStore.getExtensions());
        mAdapter.notifyDataSetChanged();
    }

    private void promptInstall() {
        EditText input = new EditText(this);
        input.setHint(R.string.ext_install_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ext_install)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> install(input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void install(String raw) {
        if (TextUtils.isEmpty(raw)) return;
        String location;
        Matcher m = ID_PATTERN.matcher(raw);
        if (m.find()) {
            String id = m.group(1);
            location = "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=131.0&x=id%3D"
                    + id + "%26installsource%3Dondemand%26uc";
            installFrom(location, id);
        } else if (raw.startsWith("file://") || raw.startsWith("/")) {
            location = raw.startsWith("/") ? "file://" + raw : raw;
            installBuiltIn(location);
        } else if (raw.startsWith("http")) {
            installFrom(raw, "ext-" + Integer.toHexString(raw.hashCode()));
        } else {
            Toast.makeText(this, R.string.ext_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void installFrom(String url, String id) {
        mApp.getRuntime().getWebExtensionController().install(url)
                .accept(ext -> onInstalled(ext, id))
                .exceptionally(th -> {
                    Toast.makeText(this, "install failed: " + th.getMessage(), Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    private void installBuiltIn(String location) {
        mApp.getRuntime().getWebExtensionController().installBuiltIn(location)
                .accept(ext -> onInstalled(ext, ext.id))
                .exceptionally(th -> {
                    Toast.makeText(this, "install failed: " + th.getMessage(), Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    private void onInstalled(WebExtension ext, String fallbackId) {
        ExtensionInfo info = new ExtensionInfo(
                ext.id != null ? ext.id : fallbackId,
                ext.metaData != null && ext.metaData.name != null ? ext.metaData.name : (ext.id != null ? ext.id : fallbackId),
                ext.location,
                true);
        mStore.addExtension(info);
        Toast.makeText(this, getString(R.string.ext_installed, info.id), Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void toggle(ExtensionInfo e) {
        e.enabled = !e.enabled;
        mStore.updateExtension(e);
        Toast.makeText(this, e.enabled ? R.string.ext_enabled : R.string.ext_disabled, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void remove(ExtensionInfo e) {
        GeckoResult<List<WebExtension>> res = mApp.getRuntime().getWebExtensionController().list();
        if (res != null) {
            res.accept(list -> {
                for (WebExtension ext : list) {
                    if (e.id.equals(ext.id)) {
                        mApp.getRuntime().getWebExtensionController().uninstall(ext);
                        break;
                    }
                }
            });
        }
        mStore.removeExtension(e.id);
        refresh();
    }

    // ---- adapter ----------------------------------------------------------
    private class ExtAdapter extends RecyclerView.Adapter<ExtViewHolder> {
        @NonNull
        @Override
        public ExtViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
            return new ExtViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ExtViewHolder h, int pos) {
            ExtensionInfo e = mList.get(pos);
            h.title.setText(e.name);
            h.sub.setText((e.enabled ? getString(R.string.ext_enabled) : getString(R.string.ext_disabled))
                    + "  •  " + e.id);
            h.itemView.setOnClickListener(v -> new MaterialAlertDialogBuilder(ExtensionManagerActivity.this)
                    .setTitle(e.name)
                    .setMessage(e.id + "\n" + e.location)
                    .setPositiveButton(R.string.ext_toggle, (d, w) -> toggle(e))
                    .setNegativeButton(R.string.ext_remove, (d, w) -> remove(e))
                    .setNeutralButton(android.R.string.cancel, null)
                    .show());
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class ExtViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView sub;

        ExtViewHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.row_title);
            sub = v.findViewById(R.id.row_sub);
        }
    }
}
