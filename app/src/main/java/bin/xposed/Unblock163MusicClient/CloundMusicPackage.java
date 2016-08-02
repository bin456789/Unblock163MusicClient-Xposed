package bin.xposed.Unblock163MusicClient;

import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;

@SuppressWarnings({"deprecation", "unchecked"})
public class CloundMusicPackage {

    private static String shortVersion;

    protected static void init(XC_LoadPackage.LoadPackageParam lpparam) throws NoSuchFieldException, JSONException, PackageManager.NameNotFoundException, IOException {
        // get context
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context systemContext = (Context) callMethod(activityThread, "getSystemContext");

        // get version
        String versionName = systemContext.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
        String[] versionNameSplits = versionName.split("\\.");
        shortVersion = versionNameSplits[0] + "." + versionNameSplits[1];

        // NeteaseMusicUtils
        NeteaseMusicUtils.Class = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", lpparam.classLoader);

        // http api
        String hookStr;
        switch (shortVersion) {
            case "3.0":
                hookStr = "com.netease.cloudmusic.h.c";
                break;
            case "3.2":
                hookStr = "com.netease.cloudmusic.i.b";
                break;
            default:
                hookStr = "com.netease.cloudmusic.i.f";
        }

        Http.Class = findClass(hookStr, lpparam.classLoader);
        Http.Constructor = findConstructorExact(Http.Class, String.class, Map.class);

        HttpBase.Class = Http.Class.getSuperclass();
        HttpBase.Url = HttpBase.Class.getDeclaredField("c");
        HttpBase.Url.setAccessible(true);

        // org.apache.http
        if (shortVersion.equals("3.6")) {
            org2.apache.http.impl.client.AbstractHttpClient._class = findClass("com.netease.mam.org.apache.http.impl.client.AbstractHttpClient", lpparam.classLoader);
            org2.apache.http.client.methods.HttpUriRequest._class = findClass("com.netease.mam.org.apache.http.client.methods.HttpUriRequest", lpparam.classLoader);
            org2.apache.http.client.methods.HttpGet._class = findClass("com.netease.mam.org.apache.http.client.methods.HttpGet", lpparam.classLoader);
        } else {
            org2.apache.http.impl.client.AbstractHttpClient._class = org.apache.http.impl.client.AbstractHttpClient.class;
            org2.apache.http.client.methods.HttpUriRequest._class = org.apache.http.client.methods.HttpUriRequest.class;
            org2.apache.http.client.methods.HttpGet._class = org.apache.http.client.methods.HttpGet.class;
        }
    }

    protected static String getDefaultCookie() throws UnsupportedEncodingException, JSONException {
        if (shortVersion.equals("3.0")) {
            List<org.apache.http.cookie.Cookie> cookiesList = (List<org.apache.http.cookie.Cookie>) callStaticMethod(Http.Class, "f");
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

        public static int getPlayQuality() {
            return (int) callStaticMethod(NeteaseMusicUtils.Class, "k");
        }

        public static int getDownloadQuality() {
            return (int) callStaticMethod(NeteaseMusicUtils.Class, "m");
        }
    }

    protected static class Http {
        public static Class Class;
        public static Constructor Constructor;
    }

    protected static class HttpBase {
        public static Class Class;
        public static Field Url;
    }

    public static class org2 {
        public static class apache {
            public static class http {
                public static class impl {
                    public static class client {
                        public static class AbstractHttpClient {
                            public static Class _class;
                        }
                    }
                }

                public static class client {
                    public static class methods {
                        public static class HttpUriRequest {
                            public static Class _class;
                        }

                        public static class HttpGet {
                            public static Class _class;
                            public Object _object;

                            public HttpGet(Object object) {
                                _object = object;
                            }

                            public URI getURI() {
                                return (URI) callMethod(_object, "getURI");
                            }

                            public void setURI(URI uri) {
                                callMethod(_object, "setURI", uri);
                            }

                            public void setHeader(String name, String value) {
                                callMethod(_object, "setHeader", name, value);
                            }

                        }
                    }
                }

                public static class HttpResponse {
                    public Object _object;

                    public HttpResponse(Object object) {
                        _object = object;
                    }

                    public StatusLine getStatusLine() {
                        return new StatusLine(callMethod(_object, "getStatusLine"));
                    }
                }

                public static class StatusLine {
                    public Object _object;

                    public StatusLine(Object object) {
                        _object = object;
                    }

                    public int getStatusCode() {
                        return (int) callMethod(_object, "getStatusCode");
                    }
                }
            }
        }
    }
}
