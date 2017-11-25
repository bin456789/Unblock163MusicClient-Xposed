package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.ClassHelper.getFilteredClasses;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.findMethodsByExactParameters;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

class CloudMusicPackage {
    static final String PACKAGE_NAME = "com.netease.cloudmusic";
    private static String version;
    private static WeakReference<List<String>> allClassList = new WeakReference<>(null);
    private static ClassLoader classLoader;

    static String getVersion() {
        return version;
    }

    static void init(Context context) throws PackageManager.NameNotFoundException, IllegalAccessException {
        classLoader = context.getClassLoader();
        version = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName;
        HttpEapi.init();
    }

    static class ClassHelper {
        static List<String> getAllClasses() throws IllegalAccessException, PackageManager.NameNotFoundException {
            List<String> list = allClassList.get();
            if (list == null || list.isEmpty()) {
                list = MultiDexHelper.getAllClasses(NeteaseMusicApplication.getApplication());
                allClassList = new WeakReference<>(list);
            }
            return list;
        }

        static List<String> getFilteredClasses(Pattern pattern) throws IllegalAccessException, PackageManager.NameNotFoundException {
            return getFilteredClasses(pattern, null);
        }

        static List<String> getFilteredClasses(Pattern pattern, Comparator<String> comparator) throws IllegalAccessException, PackageManager.NameNotFoundException {
            List<String> list = Utility.filterList(getAllClasses(), pattern);
            Collections.sort(list, comparator);
            return list;
        }

        static List<String> getFilteredClasses(String start, String end) throws IllegalAccessException, PackageManager.NameNotFoundException {
            return getFilteredClasses(start, end, null);
        }

        static List<String> getFilteredClasses(String start, String end, Comparator<String> comparator) throws IllegalAccessException, PackageManager.NameNotFoundException {
            List<String> list = Utility.filterList(getAllClasses(), start, end);
            Collections.sort(list, comparator);
            return list;
        }
    }

    static class MusicInfo {
        private static Class clazz;

        private final Object musicInfo;

        MusicInfo(Object musicInfo) {
            this.musicInfo = musicInfo;
        }

        static Class getClazz() {
            if (clazz == null)
                clazz = findClass("com.netease.cloudmusic.meta.MusicInfo", classLoader);

            return clazz;
        }

        static boolean isStarred(long musicId) {
            return (boolean) callStaticMethod(MusicInfo.getClazz(), "isStarred", musicId);
        }

        long getMatchedMusicId() {
            return (long) callMethod(musicInfo, "getMatchedMusicId");
        }

        String get3rdSourceString() throws JSONException, PackageManager.NameNotFoundException, IllegalAccessException, IOException {
            long musicId = getMatchedMusicId();
            int br = (int) callMethod(musicInfo, "getCurrentBitRate");
            // 未播放的br为0
            if (br > 0) {
                File dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                String start = String.format("%s-%s", musicId, br);
                String end = ".xp!";
                // 不用md5查找，因为从本地歌曲列表播放没有md5
                File file = Utility.findFirstFile(dir, start, end);
                if (file != null) {
                    String jsonStr = Utility.readFile(file);
                    Song song = new Song();
                    song.parseMatchInfo(new JSONObject(jsonStr));
                    if (song.is3rdPartySong()) {
                        return String.format("(音源%s：%s - %s)",
                                song.matchedPlatform,
                                song.matchedArtistName,
                                song.matchedSongName);
                    }
                }
            }
            return null;
        }


    }

    static class Mam {
        private static String prefix;

        static String getPrefix() {
            return prefix;
        }

        static void setPrefix(String prefix) {
            Mam.prefix = prefix;
        }

        static Class findMamClass(Class clazz) {
            assert prefix != null;
            return findClass(prefix + clazz.getName(), classLoader);
        }
    }

    static class CAC {
        static void refreshMyPlaylist() throws PackageManager.NameNotFoundException, IllegalAccessException {
            Class caInterface = version.startsWith("3.0") ?
                    findClass("com.netease.cloudmusic.b.a", classLoader) :
                    findClass("com.netease.cloudmusic.c.a", classLoader);
            String caPackage = caInterface.getPackage().getName();

            for (String curStr : getFilteredClasses(caPackage + ".", null)) {
                Class curClass = XposedHelpers.findClass(curStr, classLoader);
                if (curClass.getInterfaces().length > 0 && curClass.getInterfaces()[0] == caInterface) {
                    Method[] methods = findMethodsByExactParameters(curClass, version.startsWith("3") ? Map.class : Object[].class,
                            int.class, int.class);
                    if (methods.length > 0) {
                        Object singleton = XposedHelpers.findFirstFieldByExactType(curClass, caInterface).get(null);
                        // methods[0].invoke(singleton, 1000, 0); // not work
                        callMethod(singleton, methods[0].getName(), 1000, 0);
                        return;
                    }
                }
            }
            throw new RuntimeException("can't find refreshMyPlaylistMethod");
        }
    }

    static class NeteaseMusicUtils {
        private static Class clazz;

        static Class getClazz() {
            if (clazz == null)
                clazz = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", classLoader);

            return clazz;
        }

        static String generateUrl(long fid) {
            String serial = (String) callStaticMethod(getClazz(), "serialurl", String.valueOf(fid));
            return String.format("http://p1.music.126.net/%s/%s.mp3", serial, fid);
        }
    }

    static class NeteaseMusicApplication {
        private static Class clazz;
        private static Field f_singleton;
        private static File musicCacheDir;

        static Class getClazz() {
            if (clazz == null)
                clazz = findClass("com.netease.cloudmusic.NeteaseMusicApplication", classLoader);

            return clazz;
        }

        static Application getApplication() throws IllegalAccessException {
            if (f_singleton == null)
                f_singleton = XposedHelpers.findFirstFieldByExactType(NeteaseMusicApplication.getClazz(), NeteaseMusicApplication.getClazz());

            return (Application) f_singleton.get(null);
        }


        static File getMusicCacheDir() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (musicCacheDir == null) {
                // find class
                Class findClass = null;
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern);

                int i = 0;
                for (String curStr : list) {
                    Class curClass = XposedHelpers.findClass(curStr, classLoader);
                    int c = 0;
                    Field[] fields = curClass.getDeclaredFields();
                    for (Field field : fields) {
                        if (field.getType() == String.class && !Modifier.isFinal(field.getModifiers())) {
                            c++;
                        }
                    }

                    if (c > i) {
                        i = c;
                        findClass = curClass;
                    }
                }

                // get cache dir
                String cacheFile = (String) callStaticMethod(findClass,
                        "a",
                        1L, 320000, "00000000000000000000000000000000");
                musicCacheDir = new File(cacheFile).getParentFile();

            }

            return musicCacheDir;
        }
    }

    static class HttpEapi {
        private static final List<Member> m_constructor = new ArrayList<>();
        private static final List<Method> m_rawStringMethodList = new ArrayList<>();
        private static boolean useOkHttp = false;
        private static Class clazz;
        private static Method m_getCookieString;
        final Object httpBase;

        HttpEapi(Object httpBase) {
            this.httpBase = httpBase;
        }

        static void init() throws PackageManager.NameNotFoundException, IllegalAccessException {
            if (!getFilteredClasses("okhttp3", null).isEmpty()) {
                clazz = findClass("com.netease.cloudmusic.h.f.d.d", classLoader);
                useOkHttp = true;
            } else {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.<String>reverseOrder());

                @SuppressWarnings("deprecation")
                String cookieStore = org.apache.http.client.CookieStore.class.getName();
                for (String curStr : list) {
                    Class curClass = XposedHelpers.findClass(curStr, classLoader);
                    Field cookieStoreField = Utility.findFirstField(curClass, null, null, cookieStore);
                    if (cookieStoreField != null) {
                        Method[] methods = findMethodsByExactParameters(curClass, curClass);
                        if (methods.length > 0) {
                            clazz = curClass;
                            String cookieStoreWithPrefix = cookieStoreField.getType().getName();
                            String prefix = cookieStoreWithPrefix.substring(0, cookieStoreWithPrefix.indexOf(cookieStore));
                            Mam.setPrefix(prefix);
                            return;
                        }
                    }
                }
                throw new RuntimeException("init failed");
            }
        }

        static Class getClazz() {
            return clazz;
        }

        static String getDefaultCookie() throws UnsupportedEncodingException, JSONException, InvocationTargetException, IllegalAccessException {
            if (useOkHttp) {
                List list = (List) callMethod(callStaticMethod(findClass("com.netease.cloudmusic.h.e.a.a", classLoader), "a"), "d");
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object l : list) {
                    if ("music.163.com".equals(callMethod(l, "e"))) {
                        if (first)
                            first = false;
                        else
                            sb.append("; ");

                        sb.append(URLEncoder.encode((String) callMethod(l, "a"), "UTF-8"));
                        sb.append("=");
                        sb.append(URLEncoder.encode((String) callMethod(l, "b"), "UTF-8"));
                    }
                }
                return sb.toString();

            } else if (version.startsWith("3.0")) {
                @SuppressWarnings({"deprecation", "unchecked"})
                List<org.apache.http.cookie.Cookie> cookies = (List<org.apache.http.cookie.Cookie>) findMethodsByExactParameters(HttpEapi.getClazz(), List.class)[0].invoke(null);
                return Utility.serialCookies(cookies);
            } else {
                if (m_getCookieString == null) {
                    Method[] methods = XposedHelpers.findMethodsByExactParameters(getClazz(), String.class, String.class);
                    for (Method method : methods) {
                        if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                            m_getCookieString = method;
                            break;
                        }
                    }
                }
                return (String) m_getCookieString.invoke(null, "music.163.com");
            }
        }

        static List<Member> getConstructor() {
            if (m_constructor.isEmpty()) {
                if (useOkHttp) {
                    m_constructor.add(findConstructorExact(getClazz(), String.class, Map.class));
                    m_constructor.add(findMethodExact(getClazz(), "a", Map.class));
                } else {
                    m_constructor.add(findConstructorExact(getClazz(), String.class, Map.class, String.class, boolean.class));
                }
            }
            return m_constructor;
        }


        static List<Method> getRawStringMethods() {
            if (m_rawStringMethodList.isEmpty()) {
                if (useOkHttp) {
                    List<Method> list = new ArrayList<>();
                    list.addAll(Arrays.asList(findMethodsByExactParameters(getClazz(), JSONObject.class)));
                    list.addAll(Arrays.asList(findMethodsByExactParameters(getClazz(), String.class)));

                    for (Method method : list) {
                        if (Modifier.isPublic(method.getModifiers())
                                && !Modifier.isFinal(method.getModifiers())
                                && !Modifier.isStatic(method.getModifiers()))
                            m_rawStringMethodList.add(method);
                    }

                } else {
                    m_rawStringMethodList.add(findMethodExact(getClazz(), "a", String.class));
                }
            }
            return m_rawStringMethodList;
        }

        static boolean isUseOkHttp() {
            return useOkHttp;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> getRequestMap() {
            return (Map<String, String>) getAdditionalInstanceField(this.httpBase, "__map");
        }

        void setRequestMap(Map<String, String> map) {
            try {
                //noinspection unchecked
                Map<String, String> m = (Map<String, String>) getObjectField(this.httpBase, "__map");
                m.putAll(map);
            } catch (Throwable ignored) {
                setAdditionalInstanceField(this.httpBase, "__map", map);
            }
        }

        String getPath() {
            return (String) getAdditionalInstanceField(this.httpBase, "__path");
        }

        void setPath(String path) {
            setAdditionalInstanceField(this.httpBase, "__path", path);
        }
    }

    static class UIAA {
        private static Class clazz;
        private static Method m_qualityBox;

        static Class getClazz() {
            if (clazz == null)
                clazz = findClass("com.netease.cloudmusic.ui.a.a", classLoader);

            return clazz;
        }

        static Method getQualityBoxMethod() {
            if (m_qualityBox == null) {
                Method[] methods = UIAA.getClazz().getDeclaredMethods();
                for (int i = methods.length - 1; i >= 0; i--) {
                    Method m = methods[i];
                    Class[] paras = m.getParameterTypes();
                    if (paras.length == 6
                            && paras[0] == Context.class
                            && paras[1] == Object.class
                            && paras[2] == Object.class
                            && paras[3] == (version.startsWith("3") ? int[].class : Object.class)
                            && paras[4] == int.class) {
                        m_qualityBox = m;
                        return m_qualityBox;
                    }
                }
                throw new RuntimeException("can't find getQualityBoxMethod");
            }
            return m_qualityBox;
        }
    }

    static class PlayerActivity {
        private static Class clazz;
        private static Field f_musicInfo;
        private static Method m_likeButtonOnClick;
        private final Object playerActivity;

        PlayerActivity(Object playerActivity) {
            this.playerActivity = playerActivity;
        }

        static Class getClazz() {
            if (clazz == null) {
                String className = version.startsWith("3.0")
                        ? "com.netease.cloudmusic.activity.PlayerMusicActivity"
                        : "com.netease.cloudmusic.activity.PlayerActivity";
                clazz = findClass(className, classLoader);
            }
            return clazz;
        }

        static Method getLikeButtonOnClickMethod() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (m_likeButtonOnClick == null) {
                String playerActivity = version.startsWith("3.0")
                        ? PlayerActivity.getClazz().getName()
                        : PlayerActivity.getClazz().getSuperclass().getName();


                Pattern pattern = Pattern.compile(String.format("^%s\\$(\\d+)", playerActivity));
                List<String> list = getFilteredClasses(pattern, new Utility.AlphanumComparator());

                int maxK = -1;
                int maxCount = -1;
                int lastK = -1;
                int lastCount = -1;

                for (String s : list) {
                    Matcher matcher = pattern.matcher(s);
                    if (matcher.find()) {
                        int thisK = Integer.parseInt(matcher.group(1));
                        // reset
                        if (thisK != lastK) {
                            lastK = thisK;
                            lastCount = 0;
                        }

                        lastCount++;
                        if (lastCount > maxCount) {
                            maxK = lastK;
                            maxCount = lastCount;
                        }
                    }
                }

                if (maxK > -1) {
                    m_likeButtonOnClick = findMethodExact(playerActivity + "$" + maxK, classLoader, "onClick", View.class);
                    return m_likeButtonOnClick;
                }

                throw new RuntimeException("can't find getLikeButtonOnClickMethod");
            }

            return m_likeButtonOnClick;
        }

        Object getMusicInfo() throws IllegalAccessException {
            if (f_musicInfo == null)
                f_musicInfo = XposedHelpers.findFirstFieldByExactType(playerActivity.getClass(), MusicInfo.getClazz());

            return f_musicInfo.get(playerActivity);
        }
    }

    static class Transfer {
        private static Method m_calcMd5;

        static Method getCalcMd5Method() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (m_calcMd5 == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.<String>reverseOrder());
                for (String curStr : list) {
                    for (Method m : XposedHelpers.findClass(curStr, classLoader).getDeclaredMethods()) {
                        Class[] params = m.getParameterTypes();
                        if (params.length == 2 && params[0] == File.class) {
                            m_calcMd5 = m;
                            return m_calcMd5;
                        }
                    }
                }
                throw new RuntimeException("can't find getCalcMd5Method");
            }
            return m_calcMd5;
        }
    }

    static class E {
        private static Class clazz;
        private static Method m_showToastWithContext;

        static Class getClazz() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (clazz == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.<String>reverseOrder());
                for (String curStr : list) {
                    Class curClass = XposedHelpers.findClass(curStr, classLoader);
                    Field[] fields = curClass.getDeclaredFields();
                    boolean findPattern = false;
                    boolean findUi = false;
                    for (Field field : fields) {
                        if (!findPattern && field.getType().equals(pattern.getClass())) {
                            findPattern = true;
                        }
                        if (!findUi && field.getType().getName().startsWith("com.netease.cloudmusic.ui.")) {
                            findUi = true;
                        }
                        if (findPattern && findUi) {
                            clazz = curClass;
                            return clazz;
                        }
                    }
                }
            }
            return clazz;
        }

        static void showToast(final String text) {
            Utility.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Toast.makeText(NeteaseMusicApplication.getApplication(), text, Toast.LENGTH_SHORT).show();
                        E.getShowToastWithContextMethod().invoke(null, null, text);
                    } catch (Throwable t) {
                        log(t);
                    }
                }
            }, 0);
        }

        static Method getShowToastWithContextMethod() throws PackageManager.NameNotFoundException, IllegalAccessException {
            if (m_showToastWithContext == null) {
                m_showToastWithContext = findMethodExact(getClazz(), "a", Context.class, String.class);
            }
            return m_showToastWithContext;
        }

        static List<Method> getSuspectedShowToastMethods() throws PackageManager.NameNotFoundException, IllegalAccessException {
            List<Method> ret = new ArrayList<>();
            for (Method method : getClazz().getMethods()) {
                if (method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].equals(String.class)
                        && method.getReturnType() == Void.TYPE
                        && Modifier.isStatic(method.getModifiers())) {
                    ret.add(method);
                }
            }
            return ret;
        }

    }
}