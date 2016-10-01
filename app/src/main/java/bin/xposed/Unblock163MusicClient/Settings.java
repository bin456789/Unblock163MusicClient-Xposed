package bin.xposed.Unblock163MusicClient;

import de.robv.android.xposed.XSharedPreferences;

final class Settings {

    private static XSharedPreferences getModuleSharedPreferences() {
        XSharedPreferences xSharedPreferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
        xSharedPreferences.makeWorldReadable();
        return xSharedPreferences;
    }

    static boolean isOverseaModeEnabled() {
        return getModuleSharedPreferences().getBoolean("OVERSEA_MODE", false);
    }

    static boolean isConfirmDislikeEnabled() {
        return getModuleSharedPreferences().getBoolean("DISLIKE_CONFIRM", false);
    }

    static String getDnsServer() {
        return "219.141.140.10";
    }

}
