package org.chromium.chrome.browser.devtools;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;

/**
 * Interactive developer console and JavaScript evaluation dialog for Vody Browser.
 */
public class DevToolsConsoleDialog extends Dialog {

    private final Tab mTab;
    private LinearLayout mLogsContainer;
    private EditText mScriptInput;
    private ScrollView mScrollView;

    public DevToolsConsoleDialog(Context context, Tab tab) {
        super(context);
        mTab = tab;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#1E1E1E")));
            getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.70));
        }

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        // Header
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(getContext());
        title.setText("Vody DevTools Console");
        title.setTextColor(Color.parseColor("#00E676"));
        title.setTextSize(16);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button injectErudaBtn = createSmallButton("Full Inspector", Color.parseColor("#1A73E8"));
        injectErudaBtn.setOnClickListener(v -> {
            VodyDevTools.toggleDevTools(getContext(), mTab);
            dismiss();
        });
        header.addView(injectErudaBtn);

        Button closeBtn = createSmallButton("✕", Color.parseColor("#555555"));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn);

        root.addView(header);

        // Logs ScrollView
        mScrollView = new ScrollView(getContext());
        mLogsContainer = new LinearLayout(getContext());
        mLogsContainer.setOrientation(LinearLayout.VERTICAL);
        mLogsContainer.setPadding(0, dp(8), 0, dp(8));
        mScrollView.addView(mLogsContainer);

        root.addView(mScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Command Input Row
        LinearLayout inputRow = new LinearLayout(getContext());
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView prompt = new TextView(getContext());
        prompt.setText("> ");
        prompt.setTextColor(Color.parseColor("#00E676"));
        prompt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        inputRow.addView(prompt);

        mScriptInput = new EditText(getContext());
        mScriptInput.setHint("console.log(document.title)");
        mScriptInput.setHintTextColor(Color.parseColor("#777777"));
        mScriptInput.setTextColor(Color.WHITE);
        mScriptInput.setTypeface(Typeface.MONOSPACE);
        mScriptInput.setTextSize(13);
        mScriptInput.setBackgroundColor(Color.parseColor("#2D2D2D"));
        mScriptInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        inputRow.addView(mScriptInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button runBtn = createSmallButton("Run", Color.parseColor("#00C853"));
        runBtn.setOnClickListener(v -> evaluateCommand());
        inputRow.addView(runBtn);

        Button clearBtn = createSmallButton("Clear", Color.parseColor("#424242"));
        clearBtn.setOnClickListener(v -> mLogsContainer.removeAllViews());
        inputRow.addView(clearBtn);

        root.addView(inputRow);

        setContentView(root);
        appendLog("Vody DevTools initialized. Page: " + (mTab != null ? mTab.getUrl().getSpec() : "No tab"), Color.parseColor("#80D8FF"));
    }

    private void evaluateCommand() {
        String code = mScriptInput.getText().toString().trim();
        if (code.isEmpty()) return;
        mScriptInput.setText("");

        appendLog("> " + code, Color.parseColor("#B3E5FC"));

        if (mTab == null) {
            appendLog("Error: No tab attached", Color.parseColor("#FF5252"));
            return;
        }

        WebContents webContents = mTab.getWebContents();
        if (webContents == null || webContents.isDestroyed()) {
            appendLog("Error: WebContents destroyed", Color.parseColor("#FF5252"));
            return;
        }

        webContents.evaluateJavaScriptForTests(code, resultJson -> {
            appendLog("< " + (resultJson != null ? resultJson : "undefined"), Color.parseColor("#69F0AE"));
        });
    }

    private void appendLog(String text, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setPadding(0, dp(2), 0, dp(2));
        mLogsContainer.addView(tv);
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private Button createSmallButton(String text, int bgColor) {
        Button b = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bgColor);
        b.setTextSize(12);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        return b;
    }

    private int dp(int val) {
        return (int) (val * getContext().getResources().getDisplayMetrics().density);
    }
}
