package org.vody.browser.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.vody.browser.BrowseStore;
import org.vody.browser.PrivacyConfig;
import org.vody.browser.R;
import org.vody.browser.VodyApplication;

import org.json.JSONObject;

/**
 * Owner/settings panel: general options, the full privacy / anti-fingerprinting control surface
 * (WebGL, timezone, fonts, username, arbitrary API responses — all user-set, never random), and
 * data tools. Persists to the default shared preferences plus {@link BrowseStore} for the privacy
 * config and history clearing.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.list, new SettingsFragment())
                    .commit();
        }
        setTitle(R.string.menu_settings);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private VodyApplication mApp;

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.prefs, rootKey);
            mApp = (VodyApplication) requireActivity().getApplication();

            EditTextPreference home = findPreference("homepage");
            if (home != null) {
                home.setSummary(home.getText());
                home.setOnPreferenceChangeListener((p, v) -> {
                    p.setSummary((String) v);
                    return true;
                });
            }

            SwitchPreferenceCompat js = findPreference("javascript");
            if (js != null) {
                js.setOnPreferenceChangeListener((p, v) -> {
                    mApp.getRuntime().getSettings().setJavaScriptEnabled((Boolean) v);
                    return true;
                });
            }

            // Summaries for the editable text prefs.
            bindSummary("privacy_timezone_value");
            bindSummary("privacy_fonts_value");
            bindSummary("privacy_username_value");
            bindSummary("privacy_apis_value");

            Preference clear = findPreference("clear_data");
            if (clear != null) {
                clear.setOnPreferenceClickListener(p -> {
                    mApp.getStore().clearHistory();
                    requireContext().getSharedPreferences("vody", 0).edit().clear().apply();
                    Toast.makeText(requireContext(), R.string.clear_data_done, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }

        private void bindSummary(String key) {
            EditTextPreference p = findPreference(key);
            if (p != null) {
                p.setSummary(p.getText());
                p.setOnPreferenceChangeListener((pref, v) -> {
                    pref.setSummary((String) v);
                    return true;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        /** Rebuilds the PrivacyConfig from all prefs and pushes it to the engine on any change. */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            PrivacyConfig cfg = new PrivacyConfig();
            cfg.resistFingerprinting = sp.getBoolean("privacy_rfp", false);
            cfg.spoofWebGL = sp.getBoolean("privacy_webgl", false);
            cfg.spoofTimezone = sp.getBoolean("privacy_timezone", false);
            cfg.customTimezone = sp.getString("privacy_timezone_value", "America/New_York");
            cfg.spoofFonts = sp.getBoolean("privacy_fonts", false);
            cfg.customFonts = sp.getString("privacy_fonts_value", "sans-serif");
            cfg.spoofUsername = sp.getBoolean("privacy_username", false);
            cfg.customUsername = sp.getString("privacy_username_value", "anonymous");
            cfg.spoofApis = sp.getBoolean("privacy_apis", false);
            try {
                cfg.apiResponses = new JSONObject(sp.getString("privacy_apis_value", "{}"));
            } catch (Exception e) {
                cfg.apiResponses = new JSONObject();
            }

            mApp.getStore().setPrivacy(cfg);
            mApp.applyPrivacyConfig(cfg);
        }
    }
}
