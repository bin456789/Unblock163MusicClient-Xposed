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

import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.newInstance;

@SuppressWarnings({"deprecation", "unchecked"})
public class CloundMusicPackage {

    private static String SHORT_VERSION;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) throws PackageManager.NameNotFoundException {
        // get context
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context systemContext = (Context) callMethod(activityThread, "getSystemContext");

        // get version
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

        HttpBase.CLASS = HttpEapi.CLASS.getSuperclass();


        // org.apache.http
        try {
            // 3.6.0 google play
            org2.apache.http.impl.client.AbstractHttpClient.CLASS = findClass("com.netease.mam.org.apache.http.impl.client.AbstractHttpClient", lpparam.classLoader);
            org2.apache.http.client.methods.HttpUriRequest.CLASS = findClass("com.netease.mam.org.apache.http.client.methods.HttpUriRequest", lpparam.classLoader);
            org2.apache.http.client.methods.HttpRequestBase.CLASS = findClass("com.netease.mam.org.apache.http.client.methods.HttpRequestBase", lpparam.classLoader);
            org2.apache.http.HttpRequestInterceptor.CLASS = findClass("com.netease.mam.org.apache.http.HttpRequestInterceptor", lpparam.classLoader);
        } catch (Error e) {
            org2.apache.http.impl.client.AbstractHttpClient.CLASS = org.apache.http.impl.client.AbstractHttpClient.class;
            org2.apache.http.client.methods.HttpUriRequest.CLASS = org.apache.http.client.methods.HttpUriRequest.class;
            org2.apache.http.client.methods.HttpRequestBase.CLASS = org.apache.http.client.methods.HttpRequestBase.class;
            org2.apache.http.HttpRequestInterceptor.CLASS = org.apache.http.HttpRequestInterceptor.class;
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
                        final Toast toast = Toast.makeText(application, text, Toast.LENGTH_SHORT);
                        toast.show();

                        android.os.Handler handler = new android.os.Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                toast.cancel();
                            }
                        }, 1000);
                    }
                });
            }
        }
    }

    public static class HttpEapi {
        public static Class CLASS;

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
    }

    public static class HttpBase {
        public static Class CLASS;
        public static Field URL_FIELD;
        private Object _object;


        public HttpBase(Object object) {
            _object = object;
        }

        public String getUrl() throws NoSuchFieldException, IllegalAccessException {
            if (URL_FIELD == null) {
                URL_FIELD = CLASS.getDeclaredField("c");
                URL_FIELD.setAccessible(true);
            }
            return (String) URL_FIELD.get(_object);
        }
    }

    public static class org2 {
        public static class apache {
            public static class http {

                public static class impl {
                    public static class client {
                        public static class AbstractHttpClient {
                            public static Class CLASS;
                        }

                    }
                }

                public static class client {
                    public static class methods {
                        public static class HttpUriRequest {
                            public static Class CLASS;
                        }


                        public static class HttpRequestBase {
                            public static Class CLASS;
                        }
                    }
                }

                public static class HttpRequestInterceptor {
                    public static Class CLASS;
                }
            }
        }
    }
}
