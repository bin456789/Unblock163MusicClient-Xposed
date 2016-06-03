package bin.xposed.Unblock163MusicClient.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.xbill.DNS.TextParseException;

import java.io.IOException;
import java.net.UnknownHostException;

import bin.xposed.Unblock163MusicClient.Http;
import bin.xposed.Unblock163MusicClient.Oversea;
import bin.xposed.Unblock163MusicClient.R;
import bin.xposed.Unblock163MusicClient.Settings;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private Preference dnsTestPreference;
    private DnsTestTask dnsTestTask;

    private int getActivatedModuleVersion() {
        return -1;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.pref_general);

        dnsTestPreference = findPreference(Settings.DNS_TEST_KEY);
        dnsTestPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                if (dnsTestTask == null) {
                    dnsTestTask = new DnsTestTask();
                    dnsTestTask.execute();
                }
                return false;
            }
        });

        checkState();
        checkIcon();

        // unnecessary for now, hide temporarily
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removePreference(findPreference(Settings.DNS_SERVER_KEY));
        preferenceScreen.removePreference(findPreference(Settings.DNS_TEST_KEY));
        preferenceScreen.removePreference(findPreference(Settings.DNS_INSTRUCTION_KEY));
    }


    private void checkState() {
        if (getActivatedModuleVersion() == -1)
            showNotActive();
    }


    private void showNotActive() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.pref_hint_reboot_not_active)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openXposed();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openXposed() {
        Intent intent = new Intent()
                .setPackage("de.robv.android.xposed.installer")
                .putExtra("section", "modules");
        startActivity(intent);
    }

    private void checkIcon() {
        final ComponentName aliasName = new ComponentName(this, SettingsActivity.this.getClass().getName() + "-Alias");
        final PackageManager packageManager = getPackageManager();
        if (packageManager.getComponentEnabledSetting(aliasName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.pref_hint_hide_icon)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            packageManager.setComponentEnabledSetting(
                                    aliasName,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    PackageManager.DONT_KILL_APP);
                        }
                    }).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Settings.OVERSEA_MODE_KEY.equals(key)) {
            Toast.makeText(this, R.string.pref_hint_reboot_setting_changed, Toast.LENGTH_SHORT).show();
        } else if (Settings.DNS_SERVER_KEY.equals(key)) {
            String dns = sharedPreferences.getString(key, Settings.DNS_SERVER_DEFAULT);
            Intent intent = new Intent(Settings.SETTING_CHANGED)
                    .putExtra(key, dns);
            sendBroadcast(intent);
        }
    }

    private class DnsTestTask extends AsyncTask<String, Integer, String> {
        @Override
        protected void onPreExecute() {
            dnsTestPreference.setSummary(R.string.dns_testing);
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                String dns = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(Settings.DNS_SERVER_KEY, Settings.DNS_SERVER_DEFAULT);
                String neteaseIp = Oversea.getIpByHostForUiDnsTest("m1.music.126.net", dns);
                String page = Http.get("http://ip.taobao.com/service/getIpInfo.php?ip=" + neteaseIp, false);
                String countryId = new JSONObject(page).getJSONObject("data").getString("country_id");
                if ("CN".equals(countryId))
                    return getString(R.string.dns_pass);
                else
                    return getString(R.string.dns_fail);
            } catch (TextParseException | UnknownHostException e) {
                return getString(R.string.dns_unavailable);
            } catch (JSONException | IOException e) {
                return getString(R.string.dns_test_interrupted);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            dnsTestPreference.setSummary(result);
            dnsTestTask = null;
        }
    }
}
