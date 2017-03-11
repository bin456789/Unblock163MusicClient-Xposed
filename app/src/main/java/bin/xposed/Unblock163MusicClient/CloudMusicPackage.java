package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.findMethodsByExactParameters;
import static de.robv.android.xposed.XposedHelpers.newInstance;

class CloudMusicPackage {
    static final String PACKAGE_NAME = "com.netease.cloudmusic";
    static String version;
    private static WeakReference<List<String>> allClassList = new WeakReference<>(null);
    private static ClassLoader classLoader;


    static void init(XC_LoadPackage.LoadPackageParam lpparam, Application application) throws PackageManager.NameNotFoundException, IOException, IllegalAccessException {
        classLoader = lpparam.classLoader;
        version = application.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
        HttpEapi.init();
    }

    private static List<String> getAllClasses() throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        List<String> list = allClassList.get();
        if (list == null || list.isEmpty()) {
            list = MultiDexHelper.getAllClasses(NeteaseMusicApplication.getApplication());
            allClassList = new WeakReference<>(list);
        }
        return list;
    }

    private static List<String> getFilteredClasses(Pattern pattern) throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        return getFilteredClasses(pattern, null);
    }

    private static List<String> getFilteredClasses(Pattern pattern, Comparator<String> comparator) throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        List<String> list = Utility.filterList(getAllClasses(), pattern);
        Collections.sort(list, comparator);
        return list;
    }

    private static List<String> getFilteredClasses(String start, String end) throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        return getFilteredClasses(start, end, null);
    }

    private static List<String> getFilteredClasses(String start, String end, Comparator<String> comparator) throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        List<String> list = Utility.filterList(getAllClasses(), start, end);
        Collections.sort(list, comparator);
        return list;
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
                    Song song = new Song(new JSONObject(jsonStr));
                    if (song.isMatchedSong()) {
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

        static void setPrefix(String prefix) {
            Mam.prefix = prefix;
        }

        static Class findMamClass(Class clazz) {
            assert prefix != null;
            return findClass(prefix + clazz.getName(), classLoader);
        }
    }

    static class CAC {
        static void refreshMyPlaylist() throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
            Class caInterface = version.startsWith("3.0") ?
                    findClass("com.netease.cloudmusic.b.a", classLoader) :
                    findClass("com.netease.cloudmusic.c.a", classLoader);
            String caPackage = caInterface.getPackage().getName();

            for (String curStr : getFilteredClasses(caPackage + ".", null)) {
                Class curClass = XposedHelpers.findClass(curStr, classLoader);
                if (curClass.getInterfaces().length > 0 && curClass.getInterfaces()[0] == caInterface) {
                    Method[] methods = findMethodsByExactParameters(curClass, version.startsWith("3") ? Map.class : Object[].class,
                            int.class, int.class);
                    if (methods != null && methods.length > 0) {
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
            return (String) callStaticMethod(getClazz(), "a", fid);
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

        static void showToast(final String text) throws IllegalAccessException {
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


        static File getMusicCacheDir() throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
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

    static class HttpEapi extends HttpBase {
        private static Class clazz;
        private static Method m_startRequest;
        private static Method m_getCookieString;

        HttpEapi(Object httpEapi) {
            super(httpEapi);
        }

        HttpEapi(String path, Map<String, String> dataMap) {
            this(newInstance(getClazz(), path, dataMap));
        }

        static void init() throws IllegalAccessException, IOException, PackageManager.NameNotFoundException {
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
                        m_startRequest = methods[0];
                        String cookieStoreWithPrefix = cookieStoreField.getType().getName();
                        String prefix = cookieStoreWithPrefix.substring(0, cookieStoreWithPrefix.indexOf(cookieStore));
                        Mam.setPrefix(prefix);
                        return;
                    }
                }
            }
            throw new RuntimeException("init failed");
        }

        static Class getClazz() {
            return clazz;
        }

        static String getDefaultCookie() throws UnsupportedEncodingException, JSONException, InvocationTargetException, IllegalAccessException {
            if (version.startsWith("3.0")) {
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

        static String post(String path, Map<String, String> dataMap) throws InvocationTargetException, IllegalAccessException {
            return new HttpEapi(path, dataMap).startRequestChained().getResponseText();
        }

        @SuppressWarnings("unchecked")
        Map<String, String> getRequestMap() {
            return (Map<String, String>) XposedHelpers.getAdditionalInstanceField(this.httpBase, "__map");
        }

        void setRequestMap(Map<String, String> map) {
            XposedHelpers.setAdditionalInstanceField(this.httpBase, "__map", map);
        }

        String getPath() {
            return (String) XposedHelpers.getAdditionalInstanceField(this.httpBase, "__path");
        }

        void setPath(String path) {
            XposedHelpers.setAdditionalInstanceField(this.httpBase, "__path", path);
        }

        HttpEapi startRequestChained() throws InvocationTargetException, IllegalAccessException {
            m_startRequest.invoke(this.httpBase);
            return this;
        }
    }

    static class HttpBase {
        private static Class clazz;
        private static Field f_responseObject;
        private static Method m_setAdditionHeader;
        private static Method m_getResponseCode;
        private static Method m_getResponseText;
        private static Method m_startRequest;

        Object httpBase;


        HttpBase(Object httpBase) {
            this.httpBase = httpBase;
        }


        HttpBase(String method, String urlString, Map<String, String> postData) {
            if (method != null && urlString != null) {
                // new
                httpBase = XposedHelpers.newInstance(CloudMusicPackage.HttpBase.getClazz(), urlString, postData, method.toUpperCase());
            }
        }

        static Class getClazz() {
            if (clazz == null)
                clazz = HttpEapi.getClazz().getSuperclass();

            return clazz;
        }

        void setAdditionHeader(Map<String, String> additionHeaders) throws InvocationTargetException, IllegalAccessException {
            // set addition header
            if (additionHeaders != null) {
                for (Map.Entry<String, String> entry : additionHeaders.entrySet()) {
                    setAdditionHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        void setAdditionHeader(String key, String value) throws InvocationTargetException, IllegalAccessException {
            if (m_setAdditionHeader == null)
                m_setAdditionHeader = XposedHelpers.findMethodsByExactParameters(getClazz(), void.class, String.class, String.class)[0];

            m_setAdditionHeader.invoke(httpBase, key, value);
        }

        void startRequest() throws InvocationTargetException, IllegalAccessException {
            if (m_startRequest == null)
                m_startRequest = XposedHelpers.findMethodsByExactParameters(getClazz(), void.class)[0];

            m_startRequest.invoke(httpBase);
        }

        @SuppressWarnings("deprecation")
        Object getResponseObject() throws IllegalAccessException {
            if (f_responseObject == null)
                f_responseObject = XposedHelpers.findFirstFieldByExactType(getClazz(), Mam.findMamClass(org.apache.http.HttpResponse.class));

            return f_responseObject.get(httpBase);
        }

        int getResponseCode() throws InvocationTargetException, IllegalAccessException {
            if (m_getResponseCode == null)
                m_getResponseCode = XposedHelpers.findMethodsByExactParameters(getClazz(), int.class)[0];

            return (int) m_getResponseCode.invoke(httpBase);
        }

        String getResponseText() throws InvocationTargetException, IllegalAccessException {
            if (m_getResponseText == null)
                m_getResponseText = XposedHelpers.findMethodsByExactParameters(getClazz(), String.class)[0];

            return (String) m_getResponseText.invoke(httpBase);
        }

        long getFileSize() throws IllegalAccessException {
            Object response = getResponseObject();
            // get whole size for music file
            if ((boolean) callMethod(response, "containsHeader", "Content-Range")) {
                Object header = callMethod(response, "getFirstHeader", "Content-Range");
                String value = (String) callMethod(header, "getValue");
                return Integer.parseInt(Utility.getLastPartOfString(value, "/"));
            } else
                return (long) callMethod(callMethod(response, "getEntity"), "getContentLength");
        }

        String getRedirectLocation() throws IllegalAccessException {
            Object response = getResponseObject();
            if ((boolean) callMethod(response, "containsHeader", "Location")) {
                Object header = callMethod(response, "getFirstHeader", "Location");
                return (String) callMethod(header, "getValue");
            }
            return null;
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

        static Method getLikeButtonOnClickMethod() throws IllegalAccessException, IOException, PackageManager.NameNotFoundException {
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

        static Method getCalcMd5Method() throws IllegalAccessException, IOException, PackageManager.NameNotFoundException {
            if (m_calcMd5 == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern);
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
}