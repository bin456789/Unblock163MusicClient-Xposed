package bin.xposed.Unblock163MusicClient;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.Files;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import static android.os.Looper.getMainLooper;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Utils {
    private static final String TAG = "unblock163";
    private static final Map<String, InetAddress[]> dnsCache = new HashMap<>();
    private static WeakReference<Resources> moduleResources = new WeakReference<>(null);

    static String getFirstPartOfString(String str, String separator) {
        return str.substring(0, str.indexOf(separator));
    }

    static String getLastPartOfString(String str, String separator) {
        return str.substring(str.lastIndexOf(separator) + 1);
    }

    static String getFileName(String url) {
        return getFirstPartOfString(getLastPartOfString(url, "/"), ".");
    }

    public static String encode(String s) {
        try {
            return s == null ? "" : java.net.URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String serialData(Map<String, String> map) {
        return Stream.of(map.entrySet())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    static String serialData(JSONObject json) {
        return Stream.of(json.keys())
                .map(k -> encode(k) + "=" + encode(getValByJsonKey(json, k).toString()))
                .collect(Collectors.joining("&"));
    }

    static Object getValByJsonKey(JSONObject json, String key) {
        try {
            return json.get(key);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("deprecation")
    static String serialCookies(List cookieList, Map<String, String> cookieMethods, String filterDomain) {
        String domainMethod = cookieMethods.get("domain");
        String nameMethod = cookieMethods.get("name");
        String valueMethod = cookieMethods.get("value");

        return (String) Stream.of(cookieList)
                .map(cookie -> {
                    String domain = (String) callMethod(cookie, domainMethod);
                    if (filterDomain == null || filterDomain.equals(domain)) {
                        String name = (String) callMethod(cookie, nameMethod);
                        String value = (String) callMethod(cookie, valueMethod);
                        return String.format("%s=%s", encode(name), encode(value));
                    }
                    return null;
                })
                .withoutNulls()
                .collect(Collectors.joining("; "));
    }

    public static InetAddress[] getIpByHostPretendInChina(String domain) throws IOException, InvocationTargetException, IllegalAccessException, JSONException, PackageManager.NameNotFoundException {
        return getIpByHostViaHttpDns(domain, "119.29.29.29");
    }

    public static InetAddress[] getIpByHostPretendOverSea(String domain) throws IOException, InvocationTargetException, IllegalAccessException, JSONException, PackageManager.NameNotFoundException {
        return getIpByHostViaHttpDns(domain, "45.30.1.1");
    }

    public static InetAddress[] getIpByHostViaHttpDns(String domain, String pretendIp) throws IOException, InvocationTargetException, IllegalAccessException, JSONException, PackageManager.NameNotFoundException {
        if (dnsCache.containsKey(domain)) {
            return dnsCache.get(domain);
        } else {
            String raw = Http.get(String.format("http://119.29.29.29/d?dn=%s&ip=%s", domain, pretendIp), false)
                    .getResponseText();
            String[] ss = raw.replaceAll("[ \r\n]", "").split(";");


            InetAddress[] ips = new InetAddress[ss.length];
            for (int i = 0; i < ss.length; i++) {
                ips[i] = InetAddress.getByAddress(domain, InetAddress.getByName(ss[i]).getAddress());
            }
            dnsCache.put(domain, ips);
            return ips;
        }
    }

    static String optString(JSONObject json, String key) {
        // http://code.google.com/p/android/issues/detail?id=13830
        if (json.isNull(key)) {
            return null;
        } else {
            return json.optString(key, null);
        }
    }

    static Map<String, String> stringToMap(String data) {
        if (false) {
            if (TextUtils.isEmpty(data)) {
                return new HashMap<>();
            }
            return Splitter.on("&").omitEmptyStrings().withKeyValueSeparator("=").split(data);

        } else {
            Map<String, String> map = new HashMap<>();

            if (!TextUtils.isEmpty(data)) {
                for (String s : data.split("&")) {
                    String[] ss = s.split("=");
                    map.put(ss[0], ss.length > 1 ? ss[1] : "");
                }
            }
            return map;
        }
    }

    static Map<String, String> combineRequestData(Uri path, Map<String, String> mapFromData) {
        Map<String, String> hashMap = new LinkedHashMap<>(mapFromData);
        for (String name : path.getQueryParameterNames()) {
            String val = path.getQueryParameter(name);
            hashMap.put(name, val != null ? val : "");
        }
        return hashMap;
    }

    static Context getSystemContext() {
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        return (Context) callMethod(activityThread, "getSystemContext");
    }

    public static Resources getModuleResources() throws PackageManager.NameNotFoundException {
        Resources resources = moduleResources.get();
        if (resources == null) {
            resources = getSystemContext().createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY).getResources();
            moduleResources = new WeakReference<>(resources);
        }

        return resources;

    }

    static String readFile(File file) throws IOException {
        if (file.exists() && file.isFile() && file.canRead()) {
            List<String> lines = Files.readLines(file, Charsets.UTF_8);
            return Joiner.on("\n").join(lines);
        } else {
            return null;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void writeFile(File file, String string) throws IOException {
        file.getParentFile().mkdirs();
        Files.asCharSink(file, Charsets.UTF_8).write(string);
    }

    static File findFirstFile(File dir, String start, String end) {
        File[] fs = findFiles(dir, start, end, 1);
        return fs != null && fs.length > 0 ? fs[0] : null;
    }

    static File[] findFiles(File dir, String start, String end, Integer limit) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            return dir.listFiles(new FilenameFilter() {
                int find = 0;

                @Override
                public boolean accept(File file, String s) {
                    if ((limit == null || find < limit)
                            && (TextUtils.isEmpty(start) || s.startsWith(start))
                            && (TextUtils.isEmpty(end) || s.endsWith(end))) {
                        find++;
                        return true;
                    } else {
                        return false;
                    }
                }
            });
        } else {
            return null;
        }
    }

    static void deleteFile(File file) {
        try {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable ignored) {
        }
    }

    static void deleteFiles(File[] files) {
        if (files != null) {
            for (File file : files) {
                deleteFile(file);
            }
        }
    }

    static boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (Exception e) {
            try {
                new JSONArray(test);
            } catch (Exception ez) {
                return false;
            }
        }
        return true;
    }

    static List<String> filterList(List<String> list, Pattern pattern) {
        return Stream.of(list)
                .filter(s -> pattern.matcher(s).find())
                .toList();
    }

    static List<String> filterList(List<String> list, String start, String end) {
        return Stream.of(list)
                .filter(s -> TextUtils.isEmpty(start) || s.startsWith(start))
                .filter(s -> TextUtils.isEmpty(end) || s.endsWith(end))
                .toList();
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void postDelayed(Runnable runnable, long delay) {
        new android.os.Handler(getMainLooper()).postDelayed(runnable, delay);
    }

    public static int randInt(int min, int max) {
        Random rand = new Random();
        return rand.nextInt((max - min) + 1) + min;
    }

    public static String getCurrentProcessName(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (mActivityManager != null) {
            for (ActivityManager.RunningAppProcessInfo appProcess : mActivityManager.getRunningAppProcesses()) {
                if (appProcess.pid == pid) {
                    return appProcess.processName;
                }
            }
        }
        throw new RuntimeException("can't get current process name");
    }

    public static void log(Throwable t) {
        log("", t);
    }


    public static void log(String message, Throwable t) {
        if (t.getMessage().toLowerCase().contains("timeout")) {
            log(t.getMessage() + " " + message);
        } else {
            Log.e(TAG, message, t);
        }
    }

    public static void log(String content) {
        if (content.length() > 4000) {
            Log.d(TAG, content.substring(0, 4000));
            log(content.substring(4000));
        } else {
            Log.d(TAG, content);
        }
    }


    public static void copyTextViewStyle(TextView source, TextView dist) {
        dist.setPadding(source.getPaddingLeft(), source.getPaddingTop(), source.getPaddingRight(), source.getPaddingBottom());
        dist.setLayoutParams(source.getLayoutParams());
        dist.setGravity(source.getGravity());
        dist.setTextColor(source.getTextColors());
        dist.setTextSize(TypedValue.COMPLEX_UNIT_PX, source.getTextSize());
        dist.setTypeface(source.getTypeface());
        copyBackground(source, dist);
    }

    public static void copyBackground(View source, View dist) {
        Drawable sourceBackground = source.getBackground();
        Drawable.ConstantState constantState = sourceBackground.getConstantState();
        if (constantState != null) {
            setBackground(dist, constantState.newDrawable());
        } else {
            setBackground(dist, sourceBackground);
        }
    }

    public static void setBackground(View view, Drawable background) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(background);
        } else {
            view.setBackgroundDrawable(background);
        }
    }
}
