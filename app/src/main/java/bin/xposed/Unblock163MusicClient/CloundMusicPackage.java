package bin.xposed.Unblock163MusicClient;

import android.content.Context;
import android.content.pm.PackageManager;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;

public class CloundMusicPackage {

    private static String shortVersion;

    protected static boolean init(XC_LoadPackage.LoadPackageParam lpparam) throws NoSuchFieldException, JSONException, PackageManager.NameNotFoundException, IOException {
        // get context
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context systemContext = (Context) callMethod(activityThread, "getSystemContext");

        // get version
        String versionName = systemContext.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
        String[] versionNameSplits = versionName.split("\\.");
        shortVersion = versionNameSplits[0] + "." + versionNameSplits[1];


        String hookStr;
        switch (shortVersion) {
            case "3.0":
                hookStr = "com.netease.cloudmusic.h.c";
                break;
            case "3.2":
                hookStr = "com.netease.cloudmusic.i.b";
                break;
            default:
                // rest
                hookStr = "com.netease.cloudmusic.i.f";
                break;
        }


        Http.Class = findClass(hookStr, lpparam.classLoader);
        Http.Constructor = findConstructorExact(Http.Class, String.class, Map.class);

        HttpBase.Class = Http.Class.getSuperclass();
        HttpBase.Url = HttpBase.Class.getDeclaredField("c");
        HttpBase.Url.setAccessible(true);

        NeteaseMusicUtils.Class = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", lpparam.classLoader);

        return true;
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    protected static String getDefaultCookie() throws UnsupportedEncodingException, JSONException {
        if (shortVersion.equals("3.0")) {
            List<Cookie> cookiesList = (List<Cookie>) callStaticMethod(Http.Class, "f");
            return Utility.serialCookies(cookiesList);
        } else
            return (String) callStaticMethod(Http.Class, "b", "music.163.com");
    }

    protected static String postEapi(String path, Map dataMap) throws IllegalAccessException, InvocationTargetException, InstantiationException, UnsupportedEncodingException {
        if (shortVersion.equals("3.0"))
            return (String) callMethod(callMethod(Http.Constructor.newInstance(path, dataMap), "c"), "h");
        else
            return (String) callMethod(callMethod(Http.Constructor.newInstance(path, dataMap), "c"), "i");
    }

    protected static class NeteaseMusicUtils {
        public static Class Class;
    }

    protected static class Http {
        public static Class Class;
        public static Constructor Constructor;
    }

    protected static class HttpBase {
        public static Class Class;
        public static Field Url;
    }

}
