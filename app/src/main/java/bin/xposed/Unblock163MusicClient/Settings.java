package bin.xposed.Unblock163MusicClient;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XSharedPreferences;

public class Settings {
    private static String chinaIP;

    private static WeakReference<XSharedPreferences> xSharedPreferences = new WeakReference<>(null);

    private static XSharedPreferences getModuleSharedPreferences() {
        XSharedPreferences preferences = xSharedPreferences.get();
        if (preferences == null) {
            preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
            preferences.makeWorldReadable();
            xSharedPreferences = new WeakReference<>(preferences);
        } else {
            preferences.reload();
        }
        return preferences;
    }


    public static boolean isUnblockEnabled() {
        return getModuleSharedPreferences().getBoolean("UNBLOCK", true);
    }

    public static boolean isOverseaModeEnabled() {
        return getModuleSharedPreferences().getBoolean("OVERSEA_MODE", false);
    }

    public static boolean isConfirmDislikeEnabled() {
        return getModuleSharedPreferences().getBoolean("DISLIKE_CONFIRM", false);
    }

    public static boolean isPreventGrayEnabled() {
        return getModuleSharedPreferences().getBoolean("PREVENT_GRAY", false);
    }

    public static boolean isTryHighBitrate() {
        return getModuleSharedPreferences().getBoolean("TRY_HIGH_BITRATE", false);
    }


    public static boolean isTransparentNavBar() {
        return getModuleSharedPreferences().getBoolean("TRANSPARENT_NAVIGATION_BAR", false);
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
