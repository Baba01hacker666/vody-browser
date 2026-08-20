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
import org.vody.browser.R;
import org.vody.browser.VodyApplication;

/**
 * Owner/settings panel: homepage, JavaScript toggle, clear browsing data, about.
 * Persists to the default shared preferences (plus {@link BrowseStore} for data clearing).
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

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.prefs, rootKey);
            VodyApplication app = (VodyApplication) requireActivity().getApplication();

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
                    boolean on = (Boolean) v;
                    app.getRuntime().getSettings().setJavaScriptEnabled(on);
                    return true;
                });
            }

            Preference clear = findPreference("clear_data");
            if (clear != null) {
                clear.setOnPreferenceClickListener(p -> {
                    BrowseStore store = app.getStore();
                    store.clearHistory();
                    requireContext().getSharedPreferences("vody", 0).edit().clear().apply();
                    Toast.makeText(requireContext(), R.string.clear_data_done, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }
    }
}
