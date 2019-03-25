package bin.xposed.Unblock163MusicClient;

import android.content.res.Resources;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import de.robv.android.xposed.XSharedPreferences;

import static bin.xposed.Unblock163MusicClient.Utils.log;

public class Settings {
    private static final Date EXPIRED_DATE = new GregorianCalendar(2019, 10 - 1, 1).getTime();
    private static String chinaIP;
    private static WeakReference<XSharedPreferences> weakModulePreferences = new WeakReference<>(null);
    private static WeakReference<Resources> weakModuleResources = new WeakReference<>(null);

    public static boolean isExpired() {
        return Calendar.getInstance().getTime().after(EXPIRED_DATE);
    }

    private static XSharedPreferences getModuleSharedPreferences() {
        XSharedPreferences preferences = weakModulePreferences.get();
        if (preferences == null) {
            preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
            preferences.makeWorldReadable();
            weakModulePreferences = new WeakReference<>(preferences);
        } else {
            preferences.reload();
        }
        return preferences;
    }

    private static Resources getWeakModuleResources() {
        Resources resources = weakModuleResources.get();
        if (resources == null) {
            try {
                resources = Utils.getModuleResources();
                weakModuleResources = new WeakReference<>(resources);
            } catch (Throwable t) {
                log(t);
            }

        }
        return resources;
    }


    public static String getModuleResourcesString(int id) {
        return getWeakModuleResources().getString(id);
    }


    public static boolean getModulePreferencesBoolean(int keyId, int defaultValueId) {
        String valueString = getModuleResourcesString(defaultValueId);
        boolean defaultValue = Boolean.parseBoolean(valueString);

        return getModuleSharedPreferences().getBoolean(getModuleResourcesString(keyId), defaultValue);
    }


    public static boolean isUnblockEnabled() {
        return getModulePreferencesBoolean(R.string.unblock_key, R.string.unblock_default_value);
    }

    public static boolean isOverseaModeEnabled() {
        return getModulePreferencesBoolean(R.string.oversea_mode_key, R.string.oversea_mode_default_value);
    }

    public static boolean isDislikeConfirmEnabled() {
        return getModulePreferencesBoolean(R.string.dislike_confirm_key, R.string.dislike_confirm_default_value);
    }

    public static boolean isMagiskFixEnabled() {
        return getModulePreferencesBoolean(R.string.magisk_fix_key, R.string.magisk_fix_default_value);
    }

    public static boolean isPreventGrayEnabled() {
        return getModulePreferencesBoolean(R.string.prevent_gray_key, R.string.prevent_gray_default_value);
    }

    public static boolean isUpgradeBitrateFrom3rdParty() {
        return getModulePreferencesBoolean(R.string.upgrade_bitrate_from_3rd_party_key, R.string.upgrade_bitrate_from_3rd_party_default_value);
    }


    public static boolean isTransparentPlayerNavBar() {
        return getModulePreferencesBoolean(R.string.transparent_player_navigation_bar_key, R.string.transparent_player_navigation_default_value);
    }

    public static boolean isTransparentBaseNavBar() {
        return getModulePreferencesBoolean(R.string.transparent_base_navigation_bar_key, R.string.transparent_base_navigation_bar_default_value);
    }

    public static String getChinaIP() {
        if (chinaIP == null) {
            chinaIP = String.format("%s.%s.%s.%s",
                    111,
                    Utils.randInt(1, 63),
                    Utils.randInt(1, 255),
                    Utils.randInt(1, 254));
        }
        return chinaIP;
    }
}
