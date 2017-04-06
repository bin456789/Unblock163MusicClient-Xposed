package bin.xposed.Unblock163MusicClient;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XSharedPreferences;

class Settings {
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


    static boolean isOverseaModeEnabled() {
        return getModuleSharedPreferences().getBoolean("OVERSEA_MODE", false);
    }

    static boolean isConfirmDislikeEnabled() {
        return getModuleSharedPreferences().getBoolean("DISLIKE_CONFIRM", false);
    }

    static boolean isPreventGray() {
        return getModuleSharedPreferences().getBoolean("PREVENT_GRAY", false);
    }

    static String getDnsServer() {
        return "219.141.140.10";
    }

}
