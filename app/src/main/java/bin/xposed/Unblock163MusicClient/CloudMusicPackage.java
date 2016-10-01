package bin.xposed.Unblock163MusicClient;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;

@SuppressWarnings({"deprecation", "unchecked"})
class CloudMusicPackage {

    static String PACKAGE_NAME = "com.netease.cloudmusic";
    private static String VERSION;
    private static ClassLoader CLASS_LOADER;

    static void init(XC_LoadPackage.LoadPackageParam lpparam) throws PackageManager.NameNotFoundException {
        CLASS_LOADER = lpparam.classLoader;

        // get version
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context systemContext = (Context) callMethod(activityThread, "getSystemContext");
        VERSION = systemContext.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;

        // NeteaseMusicApplication
        NeteaseMusicApplication.CLASS = findClass("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader);

        // NeteaseMusicUtils
        NeteaseMusicUtils.CLASS = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", lpparam.classLoader);

        // http api
        if (VERSION.startsWith("3.0"))
            HttpEapi.CLASS = findClass("com.netease.cloudmusic.h.c", lpparam.classLoader);
        else if (VERSION.startsWith("3.2") || VERSION.startsWith("3.7.3"))
            HttpEapi.CLASS = findClass("com.netease.cloudmusic.i.b", lpparam.classLoader);
        else if (VERSION.startsWith("3.7.2"))
            HttpEapi.CLASS = findClass("com.netease.cloudmusic.i.g", lpparam.classLoader);
        else
            HttpEapi.CLASS = findClass("com.netease.cloudmusic.i.f", lpparam.classLoader);

        // http base
        HttpBase.CLASS = HttpEapi.CLASS.getSuperclass();
    }

    static Class findMamClass(Class clazz) {
        try {
            return XposedHelpers.findClass("com.netease.mam." + clazz.getName(), CLASS_LOADER);
        } catch (Throwable t) {
            return clazz;
        }
    }

    static class CAC {
        static void getMyPlaylist() {
            Object object;
            if (VERSION.compareTo("3.7.3") >= 0)
                object = XposedHelpers.getStaticObjectField(findClass("com.netease.cloudmusic.c.a.b", CLASS_LOADER), "a");
            else if (VERSION.compareTo("3.1") >= 0)
                object = XposedHelpers.getStaticObjectField(findClass("com.netease.cloudmusic.c.a.c", CLASS_LOADER), "a");
            else
                object = XposedHelpers.getStaticObjectField(findClass("com.netease.cloudmusic.b.a.c", CLASS_LOADER), "b");

            callMethod(object, "a", 1000, 0); // 参数分别为 limit, offset, 然而服务器会忽略
        }
    }

    static class NeteaseMusicUtils {
        static Class CLASS;

        static int getPlayQuality() {
            return (int) callStaticMethod(CLASS, "k");
        }

        static int getDownloadQuality() {
            return (int) callStaticMethod(CLASS, "m");
        }
    }

    static class NeteaseMusicApplication {
        static Class CLASS;

        static Application getApplication() {
            return (Application) callStaticMethod(CLASS, "a");
        }

        static void showToast(final String text) {
            final Application application = getApplication();
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

    static class HttpEapi {
        static Class CLASS;
        static Field URL_FIELD;

        static String post(String path, Map dataMap) {
            if (VERSION.startsWith("3.0"))
                return (String) callMethod(callMethod(newInstance(CLASS, path, dataMap), "c"), "h");
            else
                return (String) callMethod(callMethod(newInstance(CLASS, path, dataMap), "c"), "i");
        }

        static String getDefaultCookie() throws UnsupportedEncodingException, JSONException {
            if (VERSION.startsWith("3.0")) {
                List<org.apache.http.cookie.Cookie> cookiesList = (List<org.apache.http.cookie.Cookie>) callStaticMethod(CLASS, "f");
                return Utility.serialCookies(cookiesList);
            } else
                return (String) callStaticMethod(CLASS, "b", "music.163.com");
        }

        static String getUrl(Object httpEapiObject) throws NoSuchFieldException, IllegalAccessException {
            if (URL_FIELD == null) {
                URL_FIELD = HttpEapi.CLASS.getSuperclass().getDeclaredField("c");
                URL_FIELD.setAccessible(true);
            }
            return (String) URL_FIELD.get(httpEapiObject);
        }
    }

    static class HttpBase {
        private static Class CLASS;
        private static Character methodSeq;
        private Object _httpBase;


        HttpBase() {
            if (methodSeq == null)
                methodSeq = VERSION.startsWith("3.0") ? 'g' : 'h';
        }


        void doRequest(String method, String urlString, Map<String, String> postData, Map<String, String> additionHeaders) throws Throwable {
            if (method != null && urlString != null) {

                // new
                _httpBase = XposedHelpers.newInstance(CloudMusicPackage.HttpBase.CLASS, urlString, postData, method.toUpperCase());

                // set addition header
                if (additionHeaders != null) {
                    for (Map.Entry<String, String> entry : additionHeaders.entrySet()) {
                        callMethod(_httpBase, "a", entry.getKey(), entry.getValue());
                    }
                }

                // start request
                startRequest();
            }
        }

        Object getResponseObject() {
            return XposedHelpers.getObjectField(_httpBase, "f");
        }

        int getResponseCode() {
            return (int) callMethod(_httpBase, String.valueOf((char) methodSeq));
        }

        String getResponseText() {
            return (String) callMethod(_httpBase, String.valueOf((char) (methodSeq + 1)));
        }

        void startRequest() {
            callMethod(_httpBase, String.valueOf((char) (methodSeq + 2)));
        }

        long getFileSize() {
            Object response = getResponseObject();
            // get whole size for music file
            if ((boolean) callMethod(response, "containsHeader", "Content-Range")) {
                Object header = callMethod(response, "getFirstHeader", "Content-Range");
                String value = (String) callMethod(header, "getValue");
                return Integer.parseInt(Utility.getLastPartOfString(value, "/"));
            } else
                return (long) callMethod(callMethod(response, "getEntity"), "getContentLength");
        }

        String getRedirectLocation() {
            Object response = getResponseObject();
            if ((boolean) callMethod(response, "containsHeader", "Location")) {
                Object header = callMethod(response, "getFirstHeader", "Location");
                return (String) callMethod(header, "getValue");
            }
            return null;
        }
    }

    static class BottomSheetDialog {
        private static Method setTitleMethod;
        private static String musicInfoField;

        static Method getSetTitleMethod() {
            if (setTitleMethod == null) {
                String common = "com.netease.cloudmusic.ui.BottomSheetDialog.";
                String s;

                if (VERSION.startsWith("3.0"))
                    s = "s";
                else if (VERSION.startsWith("3.1") || VERSION.startsWith("3.3") || VERSION.startsWith("3.4"))
                    s = "ad";
                else if (VERSION.startsWith("3.2") || VERSION.startsWith("3.7.3"))
                    s = "r";
                else
                    s = "ae";

                if (VERSION.compareTo("3.4") >= 0)
                    setTitleMethod = findMethodExact(findClass(common + s, CLASS_LOADER), "a", Context.class, CharSequence.class, CharSequence.class, ArrayList.class, boolean.class);
                else if (VERSION.compareTo("3.1") >= 0)
                    setTitleMethod = findMethodExact(findClass(common + s, CLASS_LOADER), "a", Context.class, CharSequence.class, ArrayList.class, boolean.class);
                else
                    setTitleMethod = findMethodExact(findClass(common + s, CLASS_LOADER), "a", Context.class, CharSequence.class, ArrayList.class);

            }
            return setTitleMethod;
        }

        static String getMusicInfoField() {
            if (musicInfoField == null) {
                if (VERSION.compareTo("3.7.3") >= 0)
                    musicInfoField = "a";
                else
                    musicInfoField = "g";
            }
            return musicInfoField;
        }
    }

    static class DislikeConfirm {
        private static Method onClickMethod;
        private static String MusicInfoField;


        static Method getOnClickMethod() {
            if (onClickMethod == null) {
                String common = "com.netease.cloudmusic.activity.";
                List<String> list = new LinkedList<>();

                if (VERSION.compareTo("3.7") >= 0) {
                    list.add("i$4");// 3.7.3
                    list.add("ch$4");
                } else if (VERSION.compareTo("3.6") >= 0) {
                    list.add("cg$4");
                    list.add("cf$4");
                } else if (VERSION.compareTo("3.4") >= 0) {
                    list.add("cg$4");
                } else if (VERSION.compareTo("3.3") >= 0) {
                    list.add("cd$4");
                } else if (VERSION.compareTo("3.2") >= 0) {
                    list.add("h$4");
                } else if (VERSION.compareTo("3.1") >= 0) {
                    list.add("cf$4");
                    list.add("cd$4");
                } else {
                    list.add("PlayerMusicActivity$5");
                }

                for (String s : list) {
                    try {
                        onClickMethod = findMethodExact(findClass(common + s, CLASS_LOADER), "onClick", View.class);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }

            return onClickMethod;
        }


        static Object getMusicInfo(Activity ch) {
            if (MusicInfoField == null) {
                if (VERSION.compareTo("3.2") >= 0)
                    MusicInfoField = "h";
                else if (VERSION.compareTo("3.1") >= 0)
                    MusicInfoField = "j";
                else
                    MusicInfoField = "r";
            }
            return getObjectField(ch, MusicInfoField);
        }

        static long getMusicId(Activity ch, Object musicInfo) {
            if (VERSION.compareTo("3.1") >= 0)
                return (long) callMethod(ch, "c", musicInfo);
            else
                return (long) callMethod(ch, "b", musicInfo);
        }
    }
}

