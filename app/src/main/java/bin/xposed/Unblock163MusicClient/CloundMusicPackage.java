package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.newInstance;

@SuppressWarnings({"deprecation", "unchecked"})
public class CloundMusicPackage {

    private static String SHORT_VERSION;
    private static ClassLoader CLASS_LOADER;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) throws PackageManager.NameNotFoundException {
        CLASS_LOADER = lpparam.classLoader;

        // get version
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context systemContext = (Context) callMethod(activityThread, "getSystemContext");
        String versionName = systemContext.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
        String[] versionNameSplits = versionName.split("\\.");
        SHORT_VERSION = versionNameSplits[0] + "." + versionNameSplits[1];

        // NeteaseMusicApplication
        NeteaseMusicApplication.CLASS = findClass("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader);

        // NeteaseMusicUtils
        NeteaseMusicUtils.CLASS = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", lpparam.classLoader);

        // http api
        switch (SHORT_VERSION) {
            case "3.0":
                HttpEapi.CLASS = findClass("com.netease.cloudmusic.h.c", lpparam.classLoader);
                break;
            case "3.2":
                HttpEapi.CLASS = findClass("com.netease.cloudmusic.i.b", lpparam.classLoader);
                break;
            default:
                HttpEapi.CLASS = findClass("com.netease.cloudmusic.i.f", lpparam.classLoader);
        }
    }

    public static Class findMamClass(Class clazz) {
        try {
            return XposedHelpers.findClass("com.netease.mam." + clazz.getName(), CLASS_LOADER); // 3.6.0 google play, 3.7.0
        } catch (Throwable t) {
            return clazz;
        }
    }

    public static class CAC {
        public static void getMyPlaylist() {
            Object object;
            if (SHORT_VERSION.equals("3.0"))
                object = XposedHelpers.getStaticObjectField(findClass("com.netease.cloudmusic.b.a.c", CLASS_LOADER), "b");
            else
                object = XposedHelpers.getStaticObjectField(findClass("com.netease.cloudmusic.c.a.c", CLASS_LOADER), "a");

            callMethod(object, "a", 1000, 0); // 参数分别为 limit, offset, 然而服务器会忽略
        }
    }

    public static class NeteaseMusicUtils {
        public static Class CLASS;

        public static int getPlayQuality() {
            return (int) callStaticMethod(CLASS, "k");
        }

        public static int getDownloadQuality() {
            return (int) callStaticMethod(CLASS, "m");
        }
    }

    public static class NeteaseMusicApplication {
        public static Class CLASS;

        public static Application getApplicaion() {
            return (Application) callStaticMethod(CLASS, "a");
        }

        public static void showToast(final String text) {
            final Application application = getApplicaion();
            if (application != null) {
                android.os.Handler h = new android.os.Handler(application.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(application, text, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    public static class HttpEapi {
        public static Class CLASS;
        public static Field URL_FIELD;

        public static String post(String path, Map dataMap) {
            if (SHORT_VERSION.equals("3.0"))
                return (String) callMethod(callMethod(newInstance(CLASS, path, dataMap), "c"), "h");
            else
                return (String) callMethod(callMethod(newInstance(CLASS, path, dataMap), "c"), "i");
        }

        public static String getDefaultCookie() throws UnsupportedEncodingException, JSONException {
            if (SHORT_VERSION.equals("3.0")) {
                List<org.apache.http.cookie.Cookie> cookiesList = (List<org.apache.http.cookie.Cookie>) callStaticMethod(CLASS, "f");
                return Utility.serialCookies(cookiesList);
            } else
                return (String) callStaticMethod(CLASS, "b", "music.163.com");
        }

        public static String getUrl(Object httpEapiObject) throws NoSuchFieldException, IllegalAccessException {
            if (URL_FIELD == null) {
                URL_FIELD = HttpEapi.CLASS.getSuperclass().getDeclaredField("c");
                URL_FIELD.setAccessible(true);
            }
            return (String) URL_FIELD.get(httpEapiObject);
        }
    }
}
