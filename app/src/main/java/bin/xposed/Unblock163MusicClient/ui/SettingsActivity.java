package bin.xposed.Unblock163MusicClient.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.ListView;
import android.widget.Toast;

import com.gyf.barlibrary.ImmersionBar;

import java.io.File;

import bin.xposed.Unblock163MusicClient.BuildConfig;
import bin.xposed.Unblock163MusicClient.R;
import bin.xposed.Unblock163MusicClient.Settings;
import bin.xposed.Unblock163MusicClient.Utils;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {


    private boolean isExpModuleActive() {
        boolean isExp = false;

        try {
            ContentResolver contentResolver = getContentResolver();
            Uri uri = Uri.parse("content://me.weishu.exposed.CP/");
            Bundle result = contentResolver.call(uri, "active", null, null);
            if (result == null) {
                return false;
            }
            isExp = result.getBoolean("active", false);
        } catch (Throwable ignored) {
        }
        return isExp;
    }

    private boolean isModuleActive() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWorldReadable();
        addPreferencesFromResource(R.xml.pref_general);

        setInfo();
        setIcon();
        checkExpired();
        removeDivider();
        // checkState();
    }

    private Preference findPreference(int id) {
        return findPreference(getString(id));
    }

    private void setNavigationBarPreferences() {
        boolean hasNavigationBar = ImmersionBar.hasNavigationBar(this);
        findPreference(R.string.transparent_player_navigation_bar_key).setEnabled(hasNavigationBar);
        findPreference(R.string.transparent_base_navigation_bar_key).setEnabled(hasNavigationBar);
    }

    private void removeDivider() {
        ListView listView = findViewById(android.R.id.list);
        listView.setDivider(null);
    }

    private void setInfo() {
        findPreference(R.string.modver_key).setSummary(BuildConfig.VERSION_NAME);
        findPreference(R.string.compatible_appver_key).setSummary("4.3 ~ 6.0");
        findPreference(R.string.best_appver_key).setSummary("6.0");

        setOnTenClickListener(findPreference(R.string.modver_key), this::openGithub);
        setOnTenClickListener(findPreference(R.string.author_key), this::openCoolapk);
    }

    private void setOnTenClickListener(Preference preference, Runnable runnable) {
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            int clickCount = 0;

            @Override
            public boolean onPreferenceClick(Preference preference) {
                clickCount++;
                if (clickCount >= 10) {
                    runnable.run();
                }
                return false;
            }
        });
    }

    private void checkState() {
        String method = null;

        if (isModuleActive()) {
            method = "Xposed / EdXposed";
        } else if (isVXP()) {
            method = "VirtualXposed";
        } else if (isExpModuleActive()) {
            method = "太极";
        }

        if (method != null) {
            Toast.makeText(this, getString(R.string.hint_active, method), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.hint_not_active), Toast.LENGTH_LONG).show();
        }
    }

    private void checkExpired() {
        if (Settings.isExpired()) {
            Toast.makeText(this, getString(R.string.hint_expired), Toast.LENGTH_LONG).show();
        }
    }

    private void openXposed() {
        if (Utils.isAppInstalled(this, "de.robv.android.xposed.installer")) {
            Intent intent = new Intent("de.robv.android.xposed.installer.OPEN_SECTION");
            if (getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                intent = getPackageManager().getLaunchIntentForPackage("de.robv.android.xposed.installer");
            }
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("section", "modules")
                        .putExtra("fragment", 1)
                        .putExtra("module", BuildConfig.APPLICATION_ID);
                startActivity(intent);
            }
        } else {
            Toast.makeText(this, R.string.xposed_installer_not_installed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openCoolapk() {
        String pkg = "com.coolapk.market";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.coolapk.com/u/185162"));
        if (Utils.isAppInstalled(this, pkg)) {
            intent.setPackage(pkg);
        }
        startActivity(intent);
    }

    private void openGithub() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bin456789/Unblock163MusicClient-Xposed/releases"));
        startActivity(intent);
    }

    private boolean isVXP() {
        return System.getProperty("vxp") != null;
    }

    private void setIcon() {
        ComponentName aliasName = new ComponentName(SettingsActivity.this, SettingsActivity.this.getClass().getName() + "Alias");
        PackageManager packageManager = getPackageManager();

        CheckBoxPreference hideIconPreference = (CheckBoxPreference) findPreference(R.string.hide_module_icon_key);
        hideIconPreference.setChecked(packageManager.getComponentEnabledSetting(aliasName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        hideIconPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            int state = (Boolean) newValue ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            packageManager.setComponentEnabledSetting(aliasName, state, PackageManager.DONT_KILL_APP);
            return true;
        });
    }

    @SuppressWarnings({"deprecation", "ResultOfMethodCallIgnored"})
    @SuppressLint({"SetWorldReadable", "WorldReadableFiles"})
    private void setWorldReadable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            File dataDir = new File(getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, getPreferenceManager().getSharedPreferencesName() + ".xml");
            if (prefsFile.exists()) {
                for (File file : new File[]{dataDir, prefsDir, prefsFile}) {
                    file.setReadable(true, false);
                    file.setExecutable(true, false);
                }
            }
        } else {
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        setNavigationBarPreferences();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        setWorldReadable();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!key.equals(getString(R.string.hide_module_icon_key))) {
            Toast.makeText(this, R.string.hint_reboot_setting_changed, Toast.LENGTH_SHORT).show();
        }
    }
}
