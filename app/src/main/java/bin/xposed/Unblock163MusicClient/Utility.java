package bin.xposed.Unblock163MusicClient;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import static android.os.Looper.getMainLooper;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Utility {
    private static Map<String, InetAddress[]> dnsCache = new HashMap<>();
    private static SimpleResolver cnDnsResolver;
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

    static String serialData(JSONObject json) throws UnsupportedEncodingException, JSONException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            String key = keys.next();
            Object val = json.get(key);
            result.append(URLEncoder.encode(key, "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(val.toString(), "UTF-8"));
        }
        return result.toString();
    }

    static String serialData(Map<String, String> dataMap) throws UnsupportedEncodingException {
        if (dataMap == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    @SuppressWarnings("deprecation")
    static String serialCookies(List cookieList, Map<String, String> cookieMethods, String filterDomain) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Object cookie : cookieList) {

            String domain = (String) callMethod(cookie, cookieMethods.get("domain"));
            if (filterDomain == null || filterDomain.equals(domain)) {
                if (first) {
                    first = false;
                } else {
                    result.append("; ");
                }

                String name = (String) callMethod(cookie, cookieMethods.get("name"));
                String value = (String) callMethod(cookie, cookieMethods.get("value"));

                result.append(URLEncoder.encode(name, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(value, "UTF-8"));
            }
        }
        return result.toString();
    }


    static String getIpByHost(String domain) throws UnknownHostException, TextParseException {
        if (cnDnsResolver == null) {
            cnDnsResolver = new SimpleResolver(Settings.getDnsServer());
        }

        // caches mechanism built-in, just look it up
        Lookup lookup = new Lookup(domain, Type.A);
        lookup.setResolver(cnDnsResolver);
        Record[] records = lookup.run();
        if (lookup.getResult() == Lookup.SUCCESSFUL) {
            // already random, just pick index 0
            return records[0].rdataToString();
        } else {
            throw new RuntimeException("No IP found");
        }
    }

    public static InetAddress[] getIpByHostViaHttpDns(String domain) throws IOException, InvocationTargetException, IllegalAccessException, JSONException {
        if (dnsCache.containsKey(domain)) {
            return dnsCache.get(domain);
        } else {
            String raw = Http.get(String.format("http://119.29.29.29/d?dn=%s&ip=119.29.29.29", domain), false)
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

    static Map<String, String> combineRequestData(String path, Map<String, String> dataMap) throws URISyntaxException {
        Map<String, String> map = dataMap != null ? dataMap : new HashMap<>();

        String query = new URI(path).getQuery();
        if (!TextUtils.isEmpty(query)) {
            for (String s : query.split("&")) {
                String[] data = s.split("=");
                map.put(data[0], data[1]);
            }
        }
        return map;
    }

    static Context getSystemContext() {
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        return (Context) callMethod(activityThread, "getSystemContext");
    }

    static Resources getModuleResources() throws PackageManager.NameNotFoundException {
        Resources resources = moduleResources.get();
        if (resources == null) {
            resources = getSystemContext().createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY).getResources();
            moduleResources = new WeakReference<>(resources);
        }

        return resources;

        // 或者用 CloudMusicPackage.NeteaseMusicApplication.getApplication()
    }


    static String readFile(File file) throws IOException {
        if (file.exists() && file.isFile() && file.canRead()) {
            StringBuilder sb = new StringBuilder();
            BufferedReader input = null;
            try {
                input = new BufferedReader(new FileReader(file));
                String line;
                boolean isFirstLine = true;
                while ((line = input.readLine()) != null) {
                    sb.append(line);
                    if (isFirstLine) {
                        isFirstLine = false;
                    } else {
                        sb.append(System.getProperty("line.separator"));
                    }
                }
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    log(e);
                }
            }
            return sb.toString();
        } else {
            throw new RuntimeException("file not exists or file can't read");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void writeFile(File file, String string) throws IOException {
        file.getParentFile().mkdirs();

        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(string);
        fileWriter.flush();
        fileWriter.close();
    }

    static File findFirstFile(File dir, final String start, final String end) {
        File[] fs = findFiles(dir, start, end, 1);
        return fs != null && fs.length > 0 ? fs[0] : null;
    }

    static File[] findFiles(File dir, final String start, final String end, final Integer limit) {
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
        for (File file : files) {
            deleteFile(file);
        }
    }

    static boolean containsField(Class source, String exact, String start, String end) {
        return findFirstField(source, exact, start, end) != null;
    }

    static Field findFirstField(Class source, String exact, String start, String end) {
        Field[] fs = findFields(source, exact, start, end, 1);
        return fs.length > 0 ? fs[0] : null;
    }

    static Field[] findFields(Class source, String exact, String start, String end, int limit) {
        Field[] fs = source.getDeclaredFields();
        List<Field> returnFs = new ArrayList<>();

        for (Field f : fs) {
            if (returnFs.size() == limit) {
                break;
            }

            String s = f.getType().getName();
            if ((TextUtils.isEmpty(exact) || s.equals(exact))
                    && (TextUtils.isEmpty(start) || s.startsWith(start))
                    && (TextUtils.isEmpty(end) || s.endsWith(end))) {
                returnFs.add(f);
            }

        }
        return returnFs.toArray(new Field[returnFs.size()]);
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
        List<String> filteredList = new ArrayList<>();
        for (String curStr : list) {
            if (pattern.matcher(curStr).find()) {
                filteredList.add(curStr);
            }
        }
        return filteredList;
    }

    static List<String> filterList(List<String> list, String start, String end) {
        List<String> filteredList = new ArrayList<>();
        for (String s : list) {
            if ((TextUtils.isEmpty(start) || s.startsWith(start))
                    && (TextUtils.isEmpty(end) || s.endsWith(end))) {
                filteredList.add(s);
            }
        }
        return filteredList;
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isCallFromMyself() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        boolean findAppStack = false;
        boolean findModStack = false;

        for (StackTraceElement element : elements) {
            if (!findAppStack && element.getClassName().startsWith(BuildConfig.APPLICATION_ID)) {
                findAppStack = true;
                continue;
            }
            if (findAppStack && element.getClassName().startsWith(CloudMusicPackage.PACKAGE_NAME)) {
                findModStack = true;
                continue;
            }
            if (findModStack && element.getClassName().startsWith(BuildConfig.APPLICATION_ID)) {
                return true;
            }
        }
        return false;
    }

    public static void postDelayed(Runnable runnable, long delay) {
        new android.os.Handler(getMainLooper()).postDelayed(runnable, delay);
    }

    public static int randInt(int min, int max) {
        Random rand = new Random();
        return rand.nextInt((max - min) + 1) + min;
    }

    static class AlphanumComparator implements Comparator<String> {
        // http://www.davekoelle.com/files/AlphanumComparator.java

        private boolean isDigit(char ch) {
            return ch >= 48 && ch <= 57;
        }

        private String getChunk(String s, int slength, int marker) {
            StringBuilder chunk = new StringBuilder();
            char c = s.charAt(marker);
            chunk.append(c);
            marker++;
            if (isDigit(c)) {
                while (marker < slength) {
                    c = s.charAt(marker);
                    if (!isDigit(c)) {
                        break;
                    }
                    chunk.append(c);
                    marker++;
                }
            } else {
                while (marker < slength) {
                    c = s.charAt(marker);
                    if (isDigit(c)) {
                        break;
                    }
                    chunk.append(c);
                    marker++;
                }
            }
            return chunk.toString();
        }

        @Override
        public int compare(String s1, String s2) {
            int thisMarker = 0;
            int thatMarker = 0;
            int s1Length = s1.length();
            int s2Length = s2.length();

            while (thisMarker < s1Length && thatMarker < s2Length) {
                String thisChunk = getChunk(s1, s1Length, thisMarker);
                thisMarker += thisChunk.length();

                String thatChunk = getChunk(s2, s2Length, thatMarker);
                thatMarker += thatChunk.length();

                int result;
                if (isDigit(thisChunk.charAt(0)) == isDigit(thatChunk.charAt(0))) {
                    int thisChunkLength = thisChunk.length();
                    result = thisChunkLength - thatChunk.length();
                    if (result == 0) {
                        for (int i = 0; i < thisChunkLength; i++) {
                            result = thisChunk.charAt(i) - thatChunk.charAt(i);
                            if (result != 0) {
                                return result;
                            }
                        }
                    }
                } else {
                    result = thisChunk.compareTo(thatChunk);
                }

                if (result != 0) {
                    return result;
                }
            }

            return s1Length - s2Length;
        }
    }
}
