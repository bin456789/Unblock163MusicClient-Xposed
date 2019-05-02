package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;

import com.annimon.stream.Stream;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XposedHelpers;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.ClassHelper.getFilteredClasses;
import static bin.xposed.Unblock163MusicClient.Utils.log;
import static de.robv.android.xposed.XposedBridge.invokeOriginalMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.findMethodsByExactParameters;

public class CloudMusicPackage {
    static final String PACKAGE_NAME = "com.netease.cloudmusic";
    private static String version;
    private static WeakReference<List<String>> allClassList = new WeakReference<>(null);

    public static String getVersion() {
        return version;
    }

    static void init(Context context) throws PackageManager.NameNotFoundException {
        version = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName;
        NeteaseMusicApplication.init(context);
        Okhttp.init();
    }

    public static ClassLoader getClassLoader() {
        try {
            return NeteaseMusicApplication.getApplication().getClassLoader();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class ClassHelper {

        private static File getApkPath() throws PackageManager.NameNotFoundException, IllegalAccessException {
            ApplicationInfo applicationInfo = CloudMusicPackage.NeteaseMusicApplication.getApplication().getPackageManager().getApplicationInfo(PACKAGE_NAME, 0);
            return new File(applicationInfo.sourceDir);
        }


        static List<String> getAllClasses() {
            List<String> list = allClassList.get();
            if (list == null) {
                list = new ArrayList<>();

                try {
                    File apkFile = getApkPath();
                    // 不用 ZipDexContainer 因为会验证zip里面的文件是不是dex，会慢一点
                    Enumeration zip = new ZipFile(apkFile).entries();
                    while (zip.hasMoreElements()) {
                        ZipEntry dexInZip = (ZipEntry) zip.nextElement();
                        if (dexInZip.getName().endsWith(".dex")) {
                            DexBackedDexFile dexFile = DexFileFactory.loadDexEntry(apkFile, dexInZip.getName(), true, null);
                            for (DexBackedClassDef classDef : dexFile.getClasses()) {
                                String classType = classDef.getType();
                                classType = classType.substring(1, classType.length() - 1).replace("/", ".");
                                list.add(classType);
                            }
                        }
                    }

                    allClassList = new WeakReference<>(list);

                } catch (Throwable t) {
                    log("read classes from apk failed", t);
                }
            }
            return list;
        }

        public static List<String> getFilteredClasses(Pattern pattern) {
            return getFilteredClasses(pattern, null);
        }

        public static List<String> getFilteredClasses(Pattern pattern, Comparator<String> comparator) {
            List<String> list = Utils.filterList(getAllClasses(), pattern);
            Collections.sort(list, comparator);
            return list;
        }

        public static List<String> getFilteredClasses(String start, String end) {
            return getFilteredClasses(start, end, null);
        }

        public static List<String> getFilteredClasses(String start, String end, Comparator<String> comparator) {
            List<String> list = Utils.filterList(getAllClasses(), start, end);
            Collections.sort(list, comparator);
            return list;
        }
    }

    public static class MusicInfo {
        private static Class clazz;

        private final Object musicInfo;
        private static Method hasCopyRightMethod;

        public MusicInfo(Object musicInfo) {
            this.musicInfo = musicInfo;
        }

        public static Class getClazz() {
            if (clazz == null) {
                clazz = findClass("com.netease.cloudmusic.meta.MusicInfo", getClassLoader());
            }

            return clazz;
        }

        public static Method getHasCopyRightMethod() {
            if (hasCopyRightMethod == null) {
                hasCopyRightMethod = findMethodExact(getClazz(), "hasCopyRight");
            }
            return hasCopyRightMethod;
        }

        public static boolean isStarred(long musicId) {
            return (boolean) callStaticMethod(getClazz(), "isStarred", musicId);
        }

        public boolean hasCopyRight() throws InvocationTargetException, IllegalAccessException {
            if (Settings.isPreventGrayEnabled()) {
                return (boolean) invokeOriginalMethod(getHasCopyRightMethod(), musicInfo, null);
            } else {
                // workaround "method not hooked, cannot call original method"
                return (boolean) getHasCopyRightMethod().invoke(musicInfo);
            }
        }

        public long getMatchedMusicId() {
            return (long) callMethod(musicInfo, "getMatchedMusicId");
        }

        public String get3rdSourceString() throws JSONException, IOException {
            long musicId = getMatchedMusicId();
            int br = (int) callMethod(musicInfo, "getCurrentBitRate");
            // 未播放的br为0
            if (br > 0) {
                File dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                String start = String.format("%s-%s", musicId, br);
                String end = ".xp!";
                // 不用md5查找，因为从本地歌曲列表播放没有md5
                File file = Utils.findFirstFile(dir, start, end);
                if (file != null) {
                    String jsonStr = Utils.readFile(file);
                    Song song = new Song();
                    song.parseMatchInfo(new JSONObject(jsonStr));
                    if (song.is3rdPartySong()) {
                        return String.format("(音源%s：%s - %s)",
                                song.getMatchedPlatform(),
                                song.getMatchedArtistName(),
                                song.getMatchedSongName());
                    }
                }
            }
            return null;
        }


    }

    public static class Program {
        private static Class clazz;

        private final Object program;

        public Program(Object program) {
            this.program = program;
        }

        public static Class getClazz() {
            if (clazz == null) {
                clazz = findClass("com.netease.cloudmusic.meta.Program", getClassLoader());
            }

            return clazz;
        }

        public boolean isLiked() {
            return (boolean) callMethod(program, "isLiked");
        }


    }

    public static class NeteaseMusicUtils {
        private static Class clazz;

        public static Class getClazz() {
            if (clazz == null) {
                clazz = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", getClassLoader());
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

        static void init(Context context) {
            clazz = findClass("com.netease.cloudmusic.NeteaseMusicApplication", context.getClassLoader());
            singletonField = XposedHelpers.findFirstFieldByExactType(getClazz(), getClazz());
        }

        static Class getClazz() {
            return clazz;
        }

        static Application getApplication() throws IllegalAccessException {
            return (Application) singletonField.get(null);
        }


        static File getMusicCacheDir() {
            if (musicCacheDir == null) {
                // find class
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern);

                Class z = Stream.of(list)
                        .map(s -> findClass(s, getClassLoader()))
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
        private static final List<Method> rawStringMethodList = new ArrayList<>();
        private static Class clazz;
        private static Field uriField;
        private static Field dataField;
        private static Field dataMapField;

        final Object httpBase;

        public HttpEapi(Object httpBase) {
            this.httpBase = httpBase;
        }

        public static Class getClazz() {
            if (clazz == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());
                if (list.isEmpty()) {
                    throw new RuntimeException("init failed");
                }

                clazz = Stream.of(list)
                        .map(s -> findClass(s, CloudMusicPackage.getClassLoader()))
                        .filter(c -> Modifier.isAbstract(c.getModifiers()))
                        .filter(c -> Modifier.isPublic(c.getModifiers()))
                        .filter(c -> c.getSuperclass() == Object.class)
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType().getName().startsWith("okhttp3")))
                        .findFirst()
                        .get();

            }
            return clazz;
        }

        public static List<Method> getRawStringMethodList() {
            if (rawStringMethodList.isEmpty()) {
                List<Method> list = new ArrayList<>();
                list.addAll(Arrays.asList(findMethodsByExactParameters(getClazz(), JSONObject.class)));
                list.addAll(Arrays.asList(findMethodsByExactParameters(getClazz(), String.class)));

                rawStringMethodList.addAll(Stream.of(list)
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .filter(m -> !Modifier.isFinal(m.getModifiers()))
                        .filter(m -> !Modifier.isStatic(m.getModifiers()))
                        .toList());

            }
            return rawStringMethodList;
        }

        public Map<String, String> getRequestData() throws IllegalAccessException {
            if (dataMapField == null) {
                Field[] fields = getClazz().getDeclaredFields();

                dataField = Stream.of(fields)
                        .filter(f -> Stream.of(f.getType().getInterfaces()).anyMatch(i -> i == Serializable.class))
                        .filter(f -> Stream.of(f.getType().getDeclaredFields()).anyMatch(pf -> pf.getType() == LinkedHashMap.class))
                        .filter(f -> Stream.of(f.getType().getDeclaredFields()).anyMatch(pf -> pf.getType().getName().startsWith("okhttp3")))
                        .findFirst().get();

                dataField.setAccessible(true);

                dataMapField = XposedHelpers.findFirstFieldByExactType(dataField.getType(), LinkedHashMap.class);

            }

            Object data = dataField.get(this.httpBase);
            @SuppressWarnings("unchecked")
            Map<String, String> dataMap = (Map<String, String>) dataMapField.get(data);
            return Utils.combineRequestData(getUri(), dataMap);
        }

        public Uri getUri() throws IllegalAccessException {
            if (uriField == null) {
                uriField = XposedHelpers.findFirstFieldByExactType(getClazz(), Uri.class);
                uriField.setAccessible(true);

            }
            return (Uri) uriField.get(this.httpBase);
        }

        public static class CookieUtil {
            private static Class clazz;
            private static Method getSingletonMethod;
            private static Method getListMethod;

            static Class getClazz() {
                if (clazz == null) {

                    String pre = HttpEapi.getClazz().getName().substring(0, PACKAGE_NAME.length() + 2);
                    Pattern pattern = Pattern.compile(String.format("^%s\\.[a-z]\\.[a-z]\\.[a-z]$", pre));
                    List<String> list = getFilteredClasses(pattern);

                    clazz = Stream.of(list)
                            .map(s -> findClass(s, getClassLoader()))
                            .filter(c -> Modifier.isPublic(c.getModifiers()))
                            .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                            .filter(c -> c.getSuperclass() == Object.class)
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == ConcurrentHashMap.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == SharedPreferences.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == File.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == long.class))
                            .findFirst()
                            .get();


                    try {
                        findClass("okhttp3.Cookie", getClassLoader());
                        cookieMethodMap.put("name", "name");
                        cookieMethodMap.put("value", "value");
                        cookieMethodMap.put("domain", "domain");
                    } catch (XposedHelpers.ClassNotFoundError e) {
                        cookieMethodMap.put("name", "a");
                        cookieMethodMap.put("value", "b");
                        cookieMethodMap.put("domain", "e");
                    }
                }
                return clazz;
            }


            static String getDefaultCookie() throws InvocationTargetException, IllegalAccessException {
                if (getSingletonMethod == null) {
                    getSingletonMethod = XposedHelpers.findMethodsByExactParameters(getClazz(), getClazz())[0];
                }
                if (getListMethod == null) {
                    getListMethod = XposedHelpers.findMethodsByExactParameters(getClazz(), List.class)[0];
                }

                Object singleton = getSingletonMethod.invoke(null);
                List cookieList = (List) getListMethod.invoke(singleton);
                return Utils.serialCookies(cookieList, cookieMethodMap, "music.163.com");

            }
        }
    }

    public static class UIAA {
        private static Class clazz;
        private static Method getQualityBoxMethod;
        private static Method materialDialogWithPositiveBtnMethod;

        public static Class getClazz() {
            if (clazz == null) {
                try {
                    clazz = findClass("com.netease.cloudmusic.ui.MaterialDiloagCommon.MaterialDialogHelper", getClassLoader());
                } catch (XposedHelpers.ClassNotFoundError e) {
                    try {
                        clazz = findClass("com.netease.cloudmusic.ui.MaterialDiloagCommon.a", getClassLoader());
                    } catch (XposedHelpers.ClassNotFoundError ex) {
                        clazz = findClass("com.netease.cloudmusic.ui.a.a", getClassLoader());
                    }
                }
            }
            return clazz;
        }

        public static Method getQualityBoxMethod() {
            if (getQualityBoxMethod == null) {
                try {
                    Method[] methods = UIAA.getClazz().getDeclaredMethods();
                    getQualityBoxMethod = Stream.of(methods)
                            .filter(m -> m.getParameterTypes().length == 9)
                            .filter(m -> m.getParameterTypes()[0] == Context.class)
                            .filter(m -> m.getParameterTypes()[1] == Object.class)
                            .filter(m -> m.getParameterTypes()[2] == Object.class)
                            .filter(m -> m.getParameterTypes()[3] == CharSequence[].class)
                            .filter(m -> m.getParameterTypes()[4] == Object.class)
                            .filter(m -> m.getParameterTypes()[5] == int.class)
                            .filter(m -> m.getParameterTypes()[6] == boolean.class)
                            .filter(m -> m.getParameterTypes()[8] == boolean.class)
                            .findFirst()
                            .get();
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("can't find getQualityBoxMethod");
                }
            }
            return getQualityBoxMethod;
        }

        public static Method getMaterialDialogWithPositiveBtnMethod() {
            if (materialDialogWithPositiveBtnMethod == null) {
                try {
                    materialDialogWithPositiveBtnMethod = findMethodExact(getClazz(),
                            "materialDialogWithPositiveBtn", Context.class, Object.class, Object.class, View.OnClickListener.class);
                } catch (Throwable t) {
                    materialDialogWithPositiveBtnMethod = findMethodExact(getClazz(),
                            "a", Context.class, Object.class, Object.class, View.OnClickListener.class);
                }
            }
            return materialDialogWithPositiveBtnMethod;
        }
    }

    public static class PlayerActivity {
        private static Class clazz;
        private static Field musicInfoField;
        private static Field programField;
        private static Method likeButtonOnClickMethod;
        private final Object playerActivity;

        public PlayerActivity(Object playerActivity) {
            this.playerActivity = playerActivity;
        }

        public static Class getClazz() {
            if (clazz == null) {
                String className = "com.netease.cloudmusic.activity.PlayerActivity";
                clazz = findClass(className, getClassLoader());
            }
            return clazz;
        }

        public static Method getLikeButtonOnClickMethod() {
            if (likeButtonOnClickMethod == null) {
                try {
                    Class playerActivitySuperClass = getClazz().getSuperclass();
                    if (playerActivitySuperClass != null) {
                        Pattern pattern = Pattern.compile(String.format("^%s\\$\\d+$", playerActivitySuperClass.getName()));
                        // List<String> list = getFilteredClasses(pattern, Ordering.natural());
                        List<String> list = getFilteredClasses(pattern, Utils.alphanumComparator());
                        Class clazz = Stream.of(list)
                                .map(s -> findClass(s, getClassLoader()))
                                .filter(c -> Arrays.asList(c.getInterfaces()).contains(View.OnClickListener.class))
                                .findFirst().get();
                        likeButtonOnClickMethod = findMethodExact(clazz, "onClick", View.class);
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

        public Object getProgram() throws IllegalAccessException {
            if (programField == null) {
                programField = XposedHelpers.findFirstFieldByExactType(playerActivity.getClass(), Program.getClazz());
            }

            return programField.get(playerActivity);
        }
    }

    public static class Transfer {
        private static Method calcMd5Method;

        public static Method getCalcMd5Method() {
            if (calcMd5Method == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.[a-z]\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());

                try {
                    calcMd5Method = Stream.of(list)
                            .map(c -> findClass(c, getClassLoader()).getDeclaredMethods())
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

        static Class getClazz() {
            if (clazz == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]$");
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());
                clazz = Stream.of(list)
                        .map(s -> findClass(s, getClassLoader()))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType() == Pattern.class))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType().getName().startsWith("com.netease.cloudmusic.ui.")))
                        .findFirst().get();
            }
            return clazz;
        }

        static void showToast(String text) {
            Utils.postDelayed(() -> {
                try {
                    // Toast.makeText(NeteaseMusicApplication.getApplication(), text, Toast.LENGTH_SHORT).show();
                    E.getShowToastWithContextMethod().invoke(null, null, text);
                } catch (Throwable t) {
                    log(t);
                }
            }, 0);
        }

        static Method getShowToastWithContextMethod() {
            if (showToastWithContextMethod == null) {
                showToastWithContextMethod = findMethodExact(getClazz(), "a", Context.class, String.class);
            }
            return showToastWithContextMethod;
        }

        static List<Method> getSuspectedShowToastMethods() {
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
        static void init() {
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

            static void init() {
                Pattern pattern = Pattern.compile("^okhttp3\\.[a-z]+\\$[a-z]+$", Pattern.CASE_INSENSITIVE);
                List<String> list = getFilteredClasses(pattern, Collections.reverseOrder());

                clazz = Stream.of(list)
                        .map(s -> findClass(s, getClassLoader()))
                        .filter(c -> Modifier.isPublic(c.getModifiers()))
                        .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType() == Object.class)
                                || Stream.of(c.getDeclaredFields()).anyMatch(f -> f.getType() == Map.class))
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