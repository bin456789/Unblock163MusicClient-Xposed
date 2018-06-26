package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;

import com.annimon.stream.Stream;

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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

public class CloudMusicPackage {
    static final String PACKAGE_NAME = "com.netease.cloudmusic";
    private static String version;
    private static WeakReference<List<String>> allClassList = new WeakReference<>(null);
    private static ClassLoader classLoader;

    public static String getVersion() {
        return version;
    }

    static void init(Context context) throws PackageManager.NameNotFoundException, IllegalAccessException {
        classLoader = context.getClassLoader();
        version = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName;
        HttpEapi.init();
    }

    public static ClassLoader getClassLoader() {
        return classLoader;
    }

    public static class ClassHelper {
        static List<String> getAllClasses() throws IllegalAccessException, PackageManager.NameNotFoundException {
            List<String> list = allClassList.get();
            if (list == null || list.isEmpty()) {
                list = MultiDexHelper.getAllClasses(NeteaseMusicApplication.getApplication());
                allClassList = new WeakReference<>(list);
            }
            return list;
        }

        public static List<String> getFilteredClasses(Pattern pattern) throws IllegalAccessException, PackageManager.NameNotFoundException {
            return getFilteredClasses(pattern, null);
        }

        public static List<String> getFilteredClasses(Pattern pattern, Comparator<String> comparator) throws IllegalAccessException, PackageManager.NameNotFoundException {
            List<String> list = Utility.filterList(getAllClasses(), pattern);
            Collections.sort(list, comparator);
            return list;
        }

        public static List<String> getFilteredClasses(String start, String end) throws IllegalAccessException, PackageManager.NameNotFoundException {
            return getFilteredClasses(start, end, null);
        }

        public static List<String> getFilteredClasses(String start, String end, Comparator<String> comparator) throws IllegalAccessException, PackageManager.NameNotFoundException {
            List<String> list = Utility.filterList(getAllClasses(), start, end);
            Collections.sort(list, comparator);
            return list;
        }
    }

    public static class MusicInfo {
        private static Class clazz;

        private final Object musicInfo;

        public MusicInfo(Object musicInfo) {
            this.musicInfo = musicInfo;
        }

        public static Class getClazz() {
            if (clazz == null) {
                clazz = findClass("com.netease.cloudmusic.meta.MusicInfo", classLoader);
            }

            return clazz;
        }

        public static boolean isStarred(long musicId) {
            return (boolean) callStaticMethod(MusicInfo.getClazz(), "isStarred", musicId);
        }

        public long getMatchedMusicId() {
            return (long) callMethod(musicInfo, "getMatchedMusicId");
        }

        public String get3rdSourceString() throws JSONException, PackageManager.NameNotFoundException, IllegalAccessException, IOException {
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

    public static class Mam {
        private static String prefix;

        static String getPrefix() {
            return prefix;
        }

        static void setPrefix(String prefix) {
            Mam.prefix = prefix;
        }

        public static Class findMamClass(Class clazz) {
            assert prefix != null;
            return findClass(prefix + clazz.getName(), classLoader);
        }
    }

    public static class NeteaseMusicUtils {
        private static Class clazz;

        public static Class getClazz() {
            if (clazz == null) {
                clazz = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", classLoader);
            }

            return clazz;
        }

        static String generateUrl(long fid) {
            String serial = (String) callStaticMethod(getClazz(), "serialurl", String.valueOf(fid));
            return String.format("http://p1.music.126.net/%s/%s.mp3", serial, fid);
        }
    }

    static class NeteaseMusicApplication {
        private static Class clazz;
        private static Field singletonField;
        private static File musicCacheDir;

        static Class getClazz() {
            if (clazz == null) {
                clazz = findClass("com.netease.cloudmusic.NeteaseMusicApplication", classLoader);
            }

            return clazz;
        }

        static Application getApplication() throws IllegalAccessException {
            if (singletonField == null) {
                singletonField = XposedHelpers.findFirstFieldByExactType(NeteaseMusicApplication.getClazz(), NeteaseMusicApplication.getClazz());
            }

            return (Application) singletonField.get(null);
        }


        static File getMusicCacheDir() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (musicCacheDir == null) {
                // find class
                Class findClass = null;
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern);

                Class z = Stream.of(list)
                        .map(s -> findClass(s, classLoader))
                        .sortBy(c -> Stream.of(c.getDeclaredFields())
                                .filter(f -> f.getType() == String.class)
                                .filter(f -> !Modifier.isFinal(f.getModifiers()))
                                .count())
                        .findLast()
                        .get();


                // get cache dir
                String cacheFile = (String) callStaticMethod(z,
                        "a",
                        1L, 320000, "00000000000000000000000000000000");
                musicCacheDir = new File(cacheFile).getParentFile();

            }

            return musicCacheDir;
        }
    }

    public static class HttpEapi {
        final static Map<String, String> cookieMethodMap = new HashMap<>(3);
        private static final List<Member> constructorList = new ArrayList<>();
        private static final List<Method> rawStringMethodList = new ArrayList<>();
        private static boolean useOkHttp = false;
        private static Class clazz;
        private static Method getCookieStringMethod;
        private static Class cookieUtilClass;
        final Object httpBase;

        public HttpEapi(Object httpBase) {
            this.httpBase = httpBase;
        }

        static void init() throws PackageManager.NameNotFoundException, IllegalAccessException {
            if (!getFilteredClasses("okhttp3", null).isEmpty()) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());
                if (list.isEmpty()) {
                    throw new RuntimeException("init failed");
                }

                clazz = Stream.of(list)
                        .map(s -> findClass(s, classLoader))
                        .filter(c -> Modifier.isAbstract(c.getModifiers()))
                        .filter(c -> Modifier.isPublic(c.getModifiers()))
                        .filter(c -> c.getSuperclass() == Object.class)
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType().getName().startsWith("okhttp3")))
                        .findFirst()
                        .get();

                useOkHttp = true;
                Okhttp.init();

            } else {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());

                @SuppressWarnings("deprecation")
                String cookieStore = org.apache.http.client.CookieStore.class.getName();

                Field cookieStoreField = Stream.of(list)
                        .map(s -> findClass(s, classLoader))
                        .filter(c -> findMethodsByExactParameters(c, c).length > 0)
                        .map(c -> Utility.findFirstField(c, null, null, cookieStore))
                        .findFirst()
                        .get();

                clazz = cookieStoreField.getDeclaringClass();
                String cookieStoreWithPrefix = cookieStoreField.getType().getName();
                String prefix = cookieStoreWithPrefix.substring(0, cookieStoreWithPrefix.indexOf(cookieStore));
                Mam.setPrefix(prefix);

                if (clazz == null) {
                    throw new RuntimeException("init failed");
                }
            }
        }

        public static Class getClazz() {
            return clazz;
        }

        static String getDefaultCookie() throws UnsupportedEncodingException, InvocationTargetException, IllegalAccessException {
            if (useOkHttp) {
                // okHttp 4.x
                if (cookieUtilClass == null) {
                    String className = getClazz().getName().substring(0, PACKAGE_NAME.length() + 2) + ".e.a.a";
                    cookieUtilClass = findClass(className, classLoader);

                    try {
                        findClass("okhttp3.Cookie", classLoader);
                        cookieMethodMap.put("name", "name");
                        cookieMethodMap.put("value", "value");
                        cookieMethodMap.put("domain", "domain");
                    } catch (XposedHelpers.ClassNotFoundError e) {
                        cookieMethodMap.put("name", "a");
                        cookieMethodMap.put("value", "b");
                        cookieMethodMap.put("domain", "e");
                    }
                }

                List cookieList = (List) callMethod(callStaticMethod(cookieUtilClass, "a"), "d");
                return Utility.serialCookies(cookieList, cookieMethodMap, "music.163.com");

            } else if (version.startsWith("3.0")) {
                // HttpClient 3.0
                if (cookieMethodMap.isEmpty()) {
                    cookieMethodMap.put("name", "getName");
                    cookieMethodMap.put("value", "getValue");
                    cookieMethodMap.put("domain", "getDomain");
                }

                List cookieList = (List) findMethodsByExactParameters(HttpEapi.getClazz(), List.class)[0].invoke(null);
                return Utility.serialCookies(cookieList, cookieMethodMap, "music.163.com");

            } else {
                // HttpClient 3.1-4.x
                if (getCookieStringMethod == null) {
                    Method[] methods = XposedHelpers.findMethodsByExactParameters(getClazz(), String.class, String.class);
                    getCookieStringMethod = Stream.of(methods)
                            .filter(m -> Modifier.isStatic(m.getModifiers()))
                            .filter(m -> Modifier.isPublic(m.getModifiers()))
                            .findFirst()
                            .get();
                }
                return (String) getCookieStringMethod.invoke(null, "music.163.com");
            }
        }

        public static List<Member> getConstructorList() {
            if (constructorList.isEmpty()) {
                if (useOkHttp) {
                    constructorList.add(findConstructorExact(getClazz(), String.class, Map.class));
                    constructorList.add(findMethodExact(getClazz(), "a", Map.class));
                } else {
                    constructorList.add(findConstructorExact(getClazz(), String.class, Map.class, String.class, boolean.class));
                }
            }
            return constructorList;
        }


        public static List<Method> getRawStringMethodList() {
            if (rawStringMethodList.isEmpty()) {
                if (useOkHttp) {
                    List<Method> list = new ArrayList<>();
                    list.addAll(Arrays.asList(findMethodsByExactParameters(getClazz(), JSONObject.class)));
                    list.addAll(Arrays.asList(findMethodsByExactParameters(getClazz(), String.class)));

                    rawStringMethodList.addAll(Stream.of(list)
                            .filter(m -> Modifier.isPublic(m.getModifiers()))
                            .filter(m -> !Modifier.isFinal(m.getModifiers()))
                            .filter(m -> !Modifier.isStatic(m.getModifiers()))
                            .toList());


                } else {
                    rawStringMethodList.add(findMethodExact(getClazz(), "a", String.class));
                }
            }
            return rawStringMethodList;
        }

        public static boolean isUseOkHttp() {
            return useOkHttp;
        }

        @SuppressWarnings("unchecked")
        public Map<String, String> getRequestForm() {
            return (Map<String, String>) getAdditionalInstanceField(this.httpBase, "__form");
        }

        public void setRequestForm(Map<String, String> map) {
            @SuppressWarnings("unchecked")
            Map<String, String> form = (Map<String, String>) getAdditionalInstanceField(this.httpBase, "__form");
            if (form == null) {
                setAdditionalInstanceField(this.httpBase, "__form", map);
            } else {
                form.putAll(map);
            }
        }

        public Map<String, String> getRequestData() throws URISyntaxException {
            return Utility.combineRequestData(getPath(), getRequestForm());
        }

        public String getPath() {
            return (String) getAdditionalInstanceField(this.httpBase, "__path");
        }

        public void setPath(String path) {
            setAdditionalInstanceField(this.httpBase, "__path", path);
        }
    }

    public static class UIAA {
        private static Class clazz;
        private static Method getQualityBoxMethod;

        public static Class getClazz() {
            if (clazz == null) {
                try {
                    clazz = findClass("com.netease.cloudmusic.ui.MaterialDiloagCommon.a", classLoader);
                } catch (XposedHelpers.ClassNotFoundError e) {
                    clazz = findClass("com.netease.cloudmusic.ui.a.a", classLoader);
                }
            }
            return clazz;
        }

        public static Method getQualityBoxMethod() {
            if (getQualityBoxMethod == null) {
                try {
                    Method[] methods = UIAA.getClazz().getDeclaredMethods();
                    getQualityBoxMethod = Stream.of(methods)
                            .filter(m -> m.getParameterTypes().length == 6)
                            .filter(m -> m.getParameterTypes()[0] == Context.class)
                            .filter(m -> m.getParameterTypes()[1] == Object.class)
                            .filter(m -> m.getParameterTypes()[2] == Object.class)
                            .filter(m -> m.getParameterTypes()[3] == (version.startsWith("3") ? int[].class : Object.class))
                            .filter(m -> m.getParameterTypes()[4] == int.class)
                            .findLast() // 3.0 有两个同参数的方法
                            .get();
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("can't find getQualityBoxMethod");
                }
            }
            return getQualityBoxMethod;
        }
    }

    public static class PlayerActivity {
        private static Class clazz;
        private static Field musicInfoField;
        private static Method likeButtonOnClickMethod;
        private final Object playerActivity;

        public PlayerActivity(Object playerActivity) {
            this.playerActivity = playerActivity;
        }

        public static Class getClazz() {
            if (clazz == null) {
                String className = version.startsWith("3.0")
                        ? "com.netease.cloudmusic.activity.PlayerMusicActivity"
                        : "com.netease.cloudmusic.activity.PlayerActivity";
                clazz = findClass(className, classLoader);
            }
            return clazz;
        }

        public static Method getLikeButtonOnClickMethod() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (likeButtonOnClickMethod == null) {
                try {
                    String playerActivity = version.startsWith("3.0")
                            ? PlayerActivity.getClazz().getName()
                            : PlayerActivity.getClazz().getSuperclass().getName();


                    Pattern pattern = Pattern.compile(String.format("^%s\\$(\\d+)", playerActivity));
                    List<String> list = getFilteredClasses(pattern, new Utility.AlphanumComparator());
                    int num = Stream.of(list)
                            .groupBy(s -> {
                                Matcher m = pattern.matcher(s);
                                return m.find() ? Integer.parseInt(m.group(1)) : -1;
                            })
                            .filter(g -> g.getKey() > -1)
                            .max((x, y) -> {
                                int sizeDiff = x.getValue().size() - y.getValue().size();
                                if (sizeDiff != 0) {
                                    return sizeDiff;
                                }
                                int nameDiff = x.getKey() - y.getKey();
                                return -nameDiff;
                            }).get().getKey();


                    if (num >= 0) {
                        likeButtonOnClickMethod = findMethodExact(playerActivity + "$" + num, classLoader, "onClick", View.class);
                        return likeButtonOnClickMethod;
                    }
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("can't find getLikeButtonOnClickMethod");
                }
            }

            return likeButtonOnClickMethod;
        }

        public Object getMusicInfo() throws IllegalAccessException {
            if (musicInfoField == null) {
                musicInfoField = XposedHelpers.findFirstFieldByExactType(playerActivity.getClass(), MusicInfo.getClazz());
            }

            return musicInfoField.get(playerActivity);
        }
    }

    public static class Transfer {
        private static Method calcMd5Method;

        public static Method getCalcMd5Method() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (calcMd5Method == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());

                try {
                    calcMd5Method = Stream.of(list)
                            .map(c -> findClass(c, classLoader).getDeclaredMethods())
                            .flatMap(Stream::of)
                            .filter(m -> m.getReturnType() == String.class)
                            .filter(m -> m.getParameterTypes().length == 2)
                            .filter(m -> m.getParameterTypes()[0] == File.class)
                            .findFirst()
                            .get();
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("can't find getCalcMd5Method");
                }
            }
            return calcMd5Method;
        }
    }

    static class E {
        private static Class clazz;
        private static Method showToastWithContextMethod;

        static Class getClazz() throws IllegalAccessException, PackageManager.NameNotFoundException {
            if (clazz == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());
                clazz = Stream.of(list)
                        .map(s -> findClass(s, classLoader))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType() == Pattern.class))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType().getName().startsWith("com.netease.cloudmusic.ui.")))
                        .findFirst().get();
            }
            return clazz;
        }

        static void showToast(final String text) {
            Utility.postDelayed(() -> {
                try {
                    // Toast.makeText(NeteaseMusicApplication.getApplication(), text, Toast.LENGTH_SHORT).show();
                    E.getShowToastWithContextMethod().invoke(null, null, text);
                } catch (Throwable t) {
                    log(t);
                }
            }, 0);
        }

        static Method getShowToastWithContextMethod() throws PackageManager.NameNotFoundException, IllegalAccessException {
            if (showToastWithContextMethod == null) {
                showToastWithContextMethod = findMethodExact(getClazz(), "a", Context.class, String.class);
            }
            return showToastWithContextMethod;
        }

        static List<Method> getSuspectedShowToastMethods() throws PackageManager.NameNotFoundException, IllegalAccessException {
            List<Method> ret = new ArrayList<>();
            Stream.of(getClazz().getMethods())
                    .filter(m -> m.getParameterTypes().length == 1)
                    .filter(m -> m.getParameterTypes()[0].equals(String.class))
                    .filter(m -> m.getReturnType() == Void.TYPE)
                    .filter(m -> Modifier.isStatic(m.getModifiers()))
                    .forEach(ret::add);
            return ret;
        }

    }

    public static class Okhttp {
        static void init() throws PackageManager.NameNotFoundException, IllegalAccessException {
            RequestBuilder.init();
        }

        public static class RequestBuilder {
            static Field headerBuilderField;
            static Field httpUrlField;
            static Method buildMethod;
            static Class clazz;
            final Object o;

            public RequestBuilder(Object o) {
                this.o = o;
            }

            public static Method getBuildMethod() {
                return buildMethod;
            }

            static void init() throws PackageManager.NameNotFoundException, IllegalAccessException {
                Pattern pattern = Pattern.compile("^okhttp3\\.[a-z]+\\$[a-z]+$", Pattern.CASE_INSENSITIVE);
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());

                clazz = Stream.of(list)
                        .map(s -> findClass(s, getClassLoader()))
                        .filter(c -> Modifier.isPublic(c.getModifiers()))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType() == Object.class))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType() == String.class))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType().getName().contains("$")))
                        .findFirst()
                        .get();


                if (clazz != null) {

                    // Header
                    headerBuilderField = Stream.of(clazz.getDeclaredFields())
                            .filter(f -> f.getType().getName().startsWith("okhttp3"))
                            .filter(f -> f.getType().getName().contains("$"))
                            .findFirst().get();

                    headerBuilderField.setAccessible(true);
                    HeaderBuilder.init(headerBuilderField.getType());


                    // HttpUrl
                    httpUrlField = Stream.of(clazz.getDeclaredFields())
                            .filter(f -> f.getType().getName().startsWith("okhttp3"))
                            .filter(f -> !f.getType().getName().contains("$"))
                            .filter(f -> Modifier.isPublic(f.getType().getModifiers()))
                            .filter(f -> Modifier.isFinal(f.getType().getModifiers()))
                            .findFirst().get();

                    httpUrlField.setAccessible(true);
                    HttpUrl.init(httpUrlField.getType());


                    // buildMethod
                    String okhttpRequest = clazz.getName().substring(0, clazz.getName().lastIndexOf("$"));
                    buildMethod = Stream.of(clazz.getMethods())
                            .filter(m -> m.getParameterTypes().length == 0)
                            .filter(m -> Modifier.isPublic(m.getModifiers()))
                            .filter(m -> !Modifier.isStatic(m.getModifiers()))
                            .filter(m -> m.getReturnType().getName().equals(okhttpRequest))
                            .findFirst().get();

                }


            }

            Object getHeader() throws IllegalAccessException {
                return headerBuilderField.get(o);
            }

            public HeaderBuilder getHeaderBuilderWrapper() throws IllegalAccessException {
                return new HeaderBuilder(getHeader());
            }

            public String getUrl() throws IllegalAccessException {
                return httpUrlField.get(o).toString();
            }

            public void setUrl(String str) throws InvocationTargetException, IllegalAccessException {
                httpUrlField.set(o, HttpUrl.parseUrl(str));
            }
        }

        static class HttpUrl {
            static Method parseMethod;
            static Class clazz;

            static void init(Class clazz) {
                HttpUrl.clazz = clazz;

                parseMethod = Stream.of(clazz.getMethods())
                        .filter(m -> Modifier.isStatic(m.getModifiers()))
                        .filter(m -> m.getReturnType() == clazz)
                        .filter(m -> m.getParameterTypes().length == 1)
                        .filter(m -> m.getParameterTypes()[0] == String.class)
                        .findFirst()
                        .get();


            }

            static Object parseUrl(String str) throws InvocationTargetException, IllegalAccessException {
                return parseMethod.invoke(null, str);

            }
        }

        public static class HeaderBuilder {
            static Class clazz;
            static Field headerListField;
            final Object o;

            HeaderBuilder(Object o) {
                this.o = o;
            }

            static void init(Class clazz) {
                HeaderBuilder.clazz = clazz;

                headerListField = Stream.of(clazz.getDeclaredFields())
                        .filter(f -> f.getType() == List.class)
                        .findFirst()
                        .get();

                headerListField.setAccessible(true);

            }

            public String get(String name) throws IllegalAccessException {
                List l = (List) headerListField.get(o);
                for (int i = 0; i < l.size(); i += 2) {
                    if (name.equals(l.get(i))) {
                        return l.get(i + 1).toString();
                    }
                }
                return null;
            }


            @SuppressWarnings("unchecked")
            public void set(String name, String value) throws IllegalAccessException {
                removeAll(name);

                List l = (List) headerListField.get(o);
                l.add(name);
                l.add(value);
            }

            public void removeAll(String name) throws IllegalAccessException {
                List l = (List) headerListField.get(o);
                for (int i = 0; i < l.size(); i += 2) {
                    if (l.get(i).equals(name)) {
                        l.remove(i);
                        l.remove(i);
                        i -= 2;
                    }
                }
            }
        }

    }
}