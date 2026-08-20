package org.vody.browser;

import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.GeckoSession;

/**
 * On-device DevTools console. Evaluates JavaScript in the active session via the built-in
 * DevTools eval WebExtension (see assets/extensions/vodyeval). Results are delivered asynchronously
 * through {@link VodyApplication.EvalListener}. For full DOM/Network inspection over USB, the
 * runtime also exposes GeckoView's remote debugger (about:debugging / WebIDE).
 */
public final class DevToolsConsoleDialog implements VodyApplication.EvalListener {
    private final AppCompatActivity mActivity;
    private Dialog mDialog;
    private TextView mOutput;

    private DevToolsConsoleDialog(AppCompatActivity activity) {
        mActivity = activity;
    }

    public static void show(AppCompatActivity activity, @Nullable GeckoSession session) {
        if (session == null) {
            Toast.makeText(activity, R.string.no_history, Toast.LENGTH_SHORT).show();
            return;
        }
        new DevToolsConsoleDialog(activity).build();
    }

    private void build() {
        VodyApplication app = (VodyApplication) mActivity.getApplication();
        mDialog = new Dialog(mActivity);
        View v = LayoutInflater.from(mActivity).inflate(R.layout.dialog_devtools, null);
        EditText input = v.findViewById(R.id.devtools_input);
        mOutput = v.findViewById(R.id.devtools_output);
        Button eval = v.findViewById(R.id.devtools_eval);

        eval.setOnClickListener(b -> {
            String js = input.getText().toString();
            if (js.isEmpty()) return;
            mOutput.append("\n> " + js + "\n");
            app.evaluate(js);
        });

        app.addEvalListener(this);
        mDialog.setOnDismissListener(d -> app.removeEvalListener(this));
        mDialog.setContentView(v);
        mDialog.setTitle(R.string.devtools_title);
        mDialog.show();
    }

    @Override
    public void onEvalResult(Object message) {
        if (mOutput == null || mDialog == null || !mDialog.isShowing()) return;
        if (!(message instanceof java.util.Map)) return;
        java.util.Map<?, ?> m = (java.util.Map<?, ?>) message;
        mActivity.runOnUiThread(() -> {
            Object ok = m.get("ok");
            if (Boolean.TRUE.equals(ok)) {
                mOutput.append((m.get("result") != null ? m.get("result") : "") + "\n");
            } else {
                mOutput.append("<error> " + m.get("error") + "\n");
            }
        });
    }
}
