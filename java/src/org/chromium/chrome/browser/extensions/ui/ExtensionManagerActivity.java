package org.chromium.chrome.browser.extensions.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.chromium.chrome.browser.extensions.installer.ExtensionInstaller;
import org.chromium.chrome.browser.extensions.model.Extension;
import org.chromium.chrome.browser.extensions.runtime.ExtensionManager;

import java.io.File;
import java.util.List;

/**
 * Management activity for installed browser extensions (chrome://extensions equivalent).
 */
public class ExtensionManagerActivity extends Activity implements ExtensionManager.ExtensionChangeListener {

    private static final int REQUEST_PICK_EXTENSION_FILE = 1001;

    private ExtensionManager mExtensionManager;
    private LinearLayout mExtensionsListContainer;
    private ProgressBar mProgressBar;

    public static void start(Context context) {
        Intent intent = new Intent(context, ExtensionManagerActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExtensionManager = ExtensionManager.getInstance(this);

        // Root Layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F7FA"));

        // Header Toolbar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(16));
        header.setBackgroundColor(Color.parseColor("#1A73E8"));
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Vody Extensions");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(header);

        // Action Toolbar (Buttons: Load Unpacked / Web Store)
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(dp(16), dp(12), dp(16), dp(12));
        actionRow.setBackgroundColor(Color.WHITE);

        Button loadButton = createStyledButton("Load (.crx / .zip)", Color.parseColor("#1A73E8"));
        loadButton.setOnClickListener(v -> pickExtensionFile());
        actionRow.addView(loadButton);

        View spacer = new View(this);
        actionRow.addView(spacer, new LinearLayout.LayoutParams(dp(12), 1));

        Button webStoreButton = createStyledButton("Add from Web Store", Color.parseColor("#0F9D58"));
        webStoreButton.setOnClickListener(v -> showWebStoreInstallDialog());
        actionRow.addView(webStoreButton);

        root.addView(actionRow);

        // Progress Bar
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setIndeterminate(true);
        mProgressBar.setVisibility(View.GONE);
        root.addView(mProgressBar);

        // Scrollable List Container
        ScrollView scrollView = new ScrollView(this);
        mExtensionsListContainer = new LinearLayout(this);
        mExtensionsListContainer.setOrientation(LinearLayout.VERTICAL);
        mExtensionsListContainer.setPadding(dp(16), dp(16), dp(16), dp(32));
        scrollView.addView(mExtensionsListContainer);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        refreshExtensionsList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mExtensionManager.addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mExtensionManager.removeListener(this);
    }

    @Override
    public void onExtensionsListChanged() {
        runOnUiThread(this::refreshExtensionsList);
    }

    @Override
    public void onExtensionActionChanged(Extension extension) {
        runOnUiThread(this::refreshExtensionsList);
    }

    private void refreshExtensionsList() {
        mExtensionsListContainer.removeAllViews();
        List<Extension> extensions = mExtensionManager.getInstalledExtensions();

        if (extensions.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No extensions installed.\nTap 'Load' or 'Add from Web Store' above.");
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(dp(24), dp(48), dp(24), dp(48));
            emptyText.setTextColor(Color.GRAY);
            emptyText.setTextSize(16);
            mExtensionsListContainer.addView(emptyText);
            return;
        }

        for (Extension ext : extensions) {
            mExtensionsListContainer.addView(createExtensionCard(ext));
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
            mExtensionsListContainer.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        }
    }

    private View createExtensionCard(Extension ext) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(Color.WHITE);

        // Header: Icon + Name + Version + Switch
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView iconView = new ImageView(this);
        String iconPath = ext.getBestIconPath();
        if (!iconPath.isEmpty() && new File(iconPath).exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(iconPath);
            if (bmp != null) {
                iconView.setImageBitmap(bmp);
            }
        }
        topRow.addView(iconView, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(dp(12), 0, dp(12), 0);

        TextView nameText = new TextView(this);
        nameText.setText(ext.getName() + " " + ext.getVersion());
        nameText.setTextSize(16);
        nameText.setTypeface(null, Typeface.BOLD);
        nameText.setTextColor(Color.parseColor("#202124"));
        infoCol.addView(nameText);

        TextView idText = new TextView(this);
        idText.setText("ID: " + ext.getId());
        idText.setTextSize(12);
        idText.setTextColor(Color.GRAY);
        infoCol.addView(idText);

        topRow.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggleSwitch = new Switch(this);
        toggleSwitch.setChecked(ext.isEnabled());
        toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            mExtensionManager.setExtensionEnabled(ext.getId(), isChecked);
        });
        topRow.addView(toggleSwitch);

        card.addView(topRow);

        // Description
        if (!ext.getDescription().isEmpty()) {
            TextView descText = new TextView(this);
            descText.setText(ext.getDescription());
            descText.setTextSize(13);
            descText.setTextColor(Color.parseColor("#5F6368"));
            descText.setPadding(0, dp(8), 0, dp(8));
            card.addView(descText);
        }

        // Action Buttons Row (Details, Reload, Remove)
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0, dp(8), 0, 0);

        Button reloadBtn = createSmallButton("Reload");
        reloadBtn.setOnClickListener(v -> {
            mExtensionManager.reloadExtension(ext.getId());
            Toast.makeText(this, "Reloaded " + ext.getName(), Toast.LENGTH_SHORT).show();
        });
        actionsRow.addView(reloadBtn);

        Button permBtn = createSmallButton("Permissions");
        permBtn.setOnClickListener(v -> showPermissionsDialog(ext));
        actionsRow.addView(permBtn);

        if (ext.getAction().hasPopup()) {
            Button popupBtn = createSmallButton("Popup");
            popupBtn.setOnClickListener(v -> new ExtensionPopupDialog(this, ext).show());
            actionsRow.addView(popupBtn);
        }

        Button removeBtn = createSmallButton("Remove");
        removeBtn.setTextColor(Color.parseColor("#D93025"));
        removeBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Remove Extension")
                    .setMessage("Are you sure you want to remove " + ext.getName() + "?")
                    .setPositiveButton("Remove", (d, w) -> mExtensionManager.uninstallExtension(ext.getId()))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        actionsRow.addView(removeBtn);

        card.addView(actionsRow);
        return card;
    }

    private void showPermissionsDialog(Extension ext) {
        StringBuilder sb = new StringBuilder();
        if (ext.getPermissions().isEmpty()) {
            sb.append("This extension requests no special permissions.");
        } else {
            for (String p : ext.getPermissions()) {
                sb.append("• ").append(p).append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(ext.getName() + " Permissions")
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showWebStoreInstallDialog() {
        final EditText input = new EditText(this);
        input.setHint("Extension ID (32 chars) or Chrome Web Store URL");
        new AlertDialog.Builder(this)
                .setTitle("Install from Chrome Web Store")
                .setView(input)
                .setPositiveButton("Install", (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (!query.isEmpty()) {
                        mProgressBar.setVisibility(View.VISIBLE);
                        mExtensionManager.getInstaller().installFromWebStore(this, query, new ExtensionInstaller.InstallCallback() {
                            @Override
                            public void onSuccess(Extension extension) {
                                mProgressBar.setVisibility(View.GONE);
                                Toast.makeText(ExtensionManagerActivity.this, "Installed " + extension.getName(), Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String message) {
                                mProgressBar.setVisibility(View.GONE);
                                Toast.makeText(ExtensionManagerActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickExtensionFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select .crx or .zip file"), REQUEST_PICK_EXTENSION_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_EXTENSION_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                mProgressBar.setVisibility(View.VISIBLE);
                mExtensionManager.getInstaller().installFromUri(this, uri, new ExtensionInstaller.InstallCallback() {
                    @Override
                    public void onSuccess(Extension extension) {
                        mProgressBar.setVisibility(View.GONE);
                        Toast.makeText(ExtensionManagerActivity.this, "Installed " + extension.getName(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        mProgressBar.setVisibility(View.GONE);
                        Toast.makeText(ExtensionManagerActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private Button createStyledButton(String text, int bgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bgColor);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setTextSize(14);
        return b;
    }

    private Button createSmallButton(String text) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setTextColor(Color.parseColor("#1A73E8"));
        b.setTextSize(12);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
