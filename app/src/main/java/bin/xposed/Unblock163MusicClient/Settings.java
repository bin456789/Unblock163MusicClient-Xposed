package bin.xposed.Unblock163MusicClient;

import de.robv.android.xposed.XSharedPreferences;

public final class Settings {
    public static final String SETTING_CHANGED = "bin.xposed.Unblock163MusicClient.SETTING_CHANGED";

    public static final String OVERSEA_MODE_KEY = "OVERSEA_MODE";
    public static final boolean OVERSEA_MODE_DEFAULT = false;

    public static final String DNS_SERVER_KEY = "DNS_SERVER";
    public static final String DNS_SERVER_DEFAULT = "219.141.140.10"; // beijing telecom dns

    public static final String DNS_INSTRUCTION_KEY = "DNS_INSTRUCTION";
    public static final String DNS_TEST_KEY = "DNS_TEST";

    public static XSharedPreferences getModuleSharedPreferences() {
        XSharedPreferences xSharedPreferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
        xSharedPreferences.makeWorldReadable();
        return xSharedPreferences;
    }


    public static boolean isOverseaModeEnabled() {
        return getModuleSharedPreferences().getBoolean(Settings.OVERSEA_MODE_KEY, Settings.OVERSEA_MODE_DEFAULT);
    }

    public static String getDnsServer() {
        return getModuleSharedPreferences().getString(Settings.DNS_SERVER_KEY, Settings.DNS_SERVER_DEFAULT);
    }

}
