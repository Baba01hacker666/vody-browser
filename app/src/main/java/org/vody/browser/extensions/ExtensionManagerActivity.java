package org.vody.browser.extensions;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.vody.browser.BrowseStore;
import org.vody.browser.ExtensionInfo;
import org.vody.browser.R;
import org.vody.browser.VodyApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages WebExtensions through GeckoView's WebExtensionController.
 *
 * Install sources:
 *  - Chrome Web Store ID / URL (GeckoView resolves the .crx redirect to an .xpi)
 *  - Any https:// .xpi or AMO URL
 *  - A local packed .xpi picked with the system file picker (copied to app storage first)
 *  - An unpacked folder pushed over adb (installed as a built-in location)
 *
 * Each entry can be enabled/disabled (persisted across restarts) and removed; state is stored in
 * {@link BrowseStore} and re-applied at runtime start-up by {@link VodyApplication}.
 */
public class ExtensionManagerActivity extends AppCompatActivity {
    private static final String TAG = "VodyExt";
    private static final Pattern ID_PATTERN = Pattern.compile("([a-p]{32})");
    private static final int REQ_PICK_XPI = 4101;

    private VodyApplication mApp;
    private BrowseStore mStore;
    private final List<ExtensionInfo> mList = new ArrayList<>();
    private ExtAdapter mAdapter;
    private View mEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        mApp = (VodyApplication) getApplication();
        mStore = mApp.getStore();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mEmptyState = findViewById(R.id.empty_state);
        RecyclerView rv = findViewById(R.id.list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ExtAdapter();
        rv.setAdapter(mAdapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showInstallSheet());

        refresh();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void refresh() {
        mList.clear();
        mList.addAll(mStore.getExtensions());
        mAdapter.notifyDataSetChanged();
        boolean empty = mList.isEmpty();
        mEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.list).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ---- install ------------------------------------------------------------

    private void showInstallSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_ext_install, null);
        v.findViewById(R.id.install_row_store).setOnClickListener(x -> {
            sheet.dismiss();
            browseStore();
        });
        v.findViewById(R.id.install_row_url).setOnClickListener(x -> {
            sheet.dismiss();
            promptUrl();
        });
        v.findViewById(R.id.install_row_file).setOnClickListener(x -> {
            sheet.dismiss();
            pickFile();
        });
        v.findViewById(R.id.install_row_dir).setOnClickListener(x -> {
            sheet.dismiss();
            promptDir();
        });
        sheet.setContentView(v);
        sheet.show();
    }

    /** Opens the Firefox Add-ons (AMO) mobile store in a new browser tab. */
    private void browseStore() {
        Intent i = new Intent(this, org.vody.browser.MainActivity.class);
        i.putExtra("open_url", "https://addons.mozilla.org/android/");
        i.putExtra("new_tab", true);
        startActivity(i);
    }

    private void promptUrl() {
        EditText input = new EditText(this);
        input.setHint(R.string.ext_install_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ext_from_url_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> install(input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptDir() {
        EditText input = new EditText(this);
        input.setHint("/sdcard/my-extension/");
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ext_from_dir_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String raw = input.getText().toString().trim();
                    if (TextUtils.isEmpty(raw)) return;
                    String location = raw.startsWith("/") ? "file://" + raw : raw;
                    mApp.getRuntime().getWebExtensionController()
                            .installBuiltIn(location)
                            .accept(ext -> onInstalled(ext, ext.id))
                            .exceptionally(th -> {
                                installFailed(th);
                                return null;
                            });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void installFailed(Throwable th) {
        Log.w(TAG, "install failed", th);
        Toast.makeText(this, "Install failed: " + th.getMessage(), Toast.LENGTH_LONG).show();
    }

    /** Opens the system document picker for a local packed extension. */
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream", "application/x-xpinstall", "application/zip"});
        startActivityForResult(i, REQ_PICK_XPI);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_XPI && resultCode == RESULT_OK && data != null && data.getData() != null) {
            installLocalXpi(data.getData());
        }
    }

    /** Copies the picked .xpi into private storage, then installs it from a file:// location. */
    private void installLocalXpi(Uri uri) {
        Toast.makeText(this, R.string.ext_copying, Toast.LENGTH_SHORT).show();
        try {
            File addonsDir = new File(getFilesDir(), "addons");
            addonsDir.mkdirs();
            String name = displayNameOf(uri);
            if (TextUtils.isEmpty(name)) name = "extension-" + System.currentTimeMillis() + ".xpi";
            File dest = new File(addonsDir, name);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("cannot open " + uri);
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            String id = "local-" + dest.getName();
            String fileUri = Uri.fromFile(dest).toString();
            // Packed packages go through install(); fall back to installBuiltIn for older engines.
            mApp.getRuntime().getWebExtensionController().install(fileUri)
                    .accept(ext -> onInstalled(ext, id))
                    .exceptionally(th -> {
                        Log.w(TAG, "file:// install failed, trying built-in", th);
                        mApp.getRuntime().getWebExtensionController().installBuiltIn(fileUri)
                                .accept(ext -> onInstalled(ext, id))
                                .exceptionally(th2 -> {
                                    installFailed(th2);
                                    return null;
                                });
                        return null;
                    });
        } catch (IOException e) {
            Log.w(TAG, "copy failed", e);
            Toast.makeText(this, "Copy failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String displayNameOf(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Routes any raw user input to the right installer. */
    private void install(String raw) {
        if (TextUtils.isEmpty(raw)) return;
        String location;
        Matcher m = ID_PATTERN.matcher(raw);
        if (raw.startsWith("file://") || raw.startsWith("/")) {
            location = raw.startsWith("/") ? "file://" + raw : raw;
            installUnpacked(location);
        } else if (m.find()) {
            String id = m.group(1);
            location = "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=131.0&x=id%3D"
                    + id + "%26installsource%3Dondemand%26uc";
            installFrom(location, id);
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
                    installFailed(th);
                    return null;
                });
    }

    private void installUnpacked(String location) {
        mApp.getRuntime().getWebExtensionController().installBuiltIn(location)
                .accept(ext -> onInstalled(ext, ext.id))
                .exceptionally(th -> {
                    installFailed(th);
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
        Toast.makeText(this, getString(R.string.ext_installed, info.name), Toast.LENGTH_SHORT).show();
        refresh();
    }

    // ---- manage ---------------------------------------------------------------

    private void toggle(ExtensionInfo e) {
        e.enabled = !e.enabled;
        applyDisabled(e);
        mStore.updateExtension(e);
        Toast.makeText(this, e.enabled ? R.string.ext_enabled : R.string.ext_disabled, Toast.LENGTH_SHORT).show();
    }

    private void applyDisabled(ExtensionInfo e) {
        GeckoResult<List<WebExtension>> res = mApp.getRuntime().getWebExtensionController().list();
        if (res == null) return;
        res.accept(installed -> {
            for (WebExtension ext : installed) {
                if (e.id.equals(ext.id)) {
                    org.mozilla.geckoview.WebExtensionController c =
                            mApp.getRuntime().getWebExtensionController();
                    if (e.enabled) {
                        c.enable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER);
                    } else {
                        c.disable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER);
                    }
                    break;
                }
            }
        });
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

    // ---- adapter ----------------------------------------------------------------

    private class ExtAdapter extends RecyclerView.Adapter<ExtViewHolder> {
        @NonNull
        @Override
        public ExtViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ext_row, parent, false);
            return new ExtViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ExtViewHolder h, int pos) {
            ExtensionInfo e = mList.get(pos);
            h.title.setText(e.name);
            h.sub.setText((e.enabled ? getString(R.string.ext_enabled) : getString(R.string.ext_disabled))
                    + "  •  " + e.id);

            // Avoid listener-triggered loops when recycling.
            h.switcher.setOnCheckedChangeListener(null);
            h.switcher.setChecked(e.enabled);
            h.switcher.setOnCheckedChangeListener((b, checked) -> {
                e.enabled = checked;
                applyDisabled(e);
                mStore.updateExtension(e);
                h.sub.setText((checked ? getString(R.string.ext_enabled) : getString(R.string.ext_disabled))
                        + "  •  " + e.id);
            });

            h.itemView.setOnClickListener(v -> new MaterialAlertDialogBuilder(ExtensionManagerActivity.this)
                    .setTitle(e.name)
                    .setMessage(e.id + "\n" + e.location)
                    .setPositiveButton(R.string.ext_remove, (d, w) -> remove(e))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());

            h.remove.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(ExtensionManagerActivity.this)
                            .setTitle(R.string.ext_remove_confirm)
                            .setMessage(e.name)
                            .setPositiveButton(R.string.ext_remove, (d, w) -> remove(e))
                            .setNegativeButton(android.R.string.cancel, null)
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
        final SwitchMaterial switcher;
        final View remove;

        ExtViewHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.row_title);
            sub = v.findViewById(R.id.row_sub);
            switcher = v.findViewById(R.id.row_switch);
            remove = v.findViewById(R.id.row_remove);
        }
    }
}
