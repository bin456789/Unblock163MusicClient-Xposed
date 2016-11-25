package bin.xposed.Unblock163MusicClient;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.text.TextUtils;

import org.apache.http.cookie.Cookie;
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
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

@SuppressWarnings({"unused", "WeakerAccess"})
class Utility {
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
            if (first)
                first = false;
            else
                result.append("&");

            String key = keys.next();
            Object val = json.get(key);
            result.append(URLEncoder.encode(key, "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(val.toString(), "UTF-8"));
        }
        return result.toString();
    }

    static String serialData(Map<String, String> dataMap) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    @SuppressWarnings("deprecation")
    static String serialCookies(List<Cookie> cookieList) throws UnsupportedEncodingException, JSONException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Cookie cookie : cookieList) {
            if (first)
                first = false;
            else
                result.append("; ");

            result.append(URLEncoder.encode(cookie.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(cookie.getValue(), "UTF-8"));
        }
        return result.toString();
    }

    static String getIpByHost(String domain) throws UnknownHostException, TextParseException {
        if (cnDnsResolver == null)
            cnDnsResolver = new SimpleResolver(Settings.getDnsServer());

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

    static String optString(JSONObject json, String key) {
        // http://code.google.com/p/android/issues/detail?id=13830
        if (json.isNull(key))
            return null;
        else
            return json.optString(key, null);
    }

    static Map<String, String> queryToMap(String dataString) {
        Map<String, String> dataMap = new LinkedHashMap<>();
        if (!TextUtils.isEmpty(dataString)) {
            for (String s : dataString.split("&")) {
                String[] data = s.split("=");
                dataMap.put(data[0], data[1]);
            }
        }
        return dataMap;
    }

    static Context getSystemContext() {
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        return (Context) callMethod(activityThread, "getSystemContext");
    }

    static Resources getModuleResources() throws PackageManager.NameNotFoundException, IllegalAccessException {
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
            IOException exception = null;
            try {
                input = new BufferedReader(new FileReader(file));
                String line;
                boolean isFirstLine = true;
                while ((line = input.readLine()) != null) {
                    sb.append(line);
                    if (isFirstLine)
                        isFirstLine = false;
                    else
                        sb.append(System.getProperty("line.separator"));
                }
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    XposedBridge.log(e);
                }
            }
            return sb.toString();
        } else {
            throw new RuntimeException("file not exists or file can't read");
        }
    }

    static void writeFile(File file, String string) throws IOException {
        FileWriter fileWriter;
        fileWriter = new FileWriter(file);
        fileWriter.write(string);
        fileWriter.flush();
        fileWriter.close();
    }

    static File findFirstFile(String dirStr, final String start, final String end) {
        File[] fs = findFiles(dirStr, start, end, 1);
        return fs.length > 0 ? fs[0] : null;
    }

    static File[] findFiles(String dirStr, final String start, final String end, final int limit) {
        File dir = new File(dirStr);

        return dir.listFiles(new FilenameFilter() {
            int find = 0;

            @Override
            public boolean accept(File file, String s) {
                if (find < limit
                        && (TextUtils.isEmpty(start) || s.startsWith(start))
                        && (TextUtils.isEmpty(end) || s.endsWith(end))) {
                    find++;
                    return true;
                } else
                    return false;
            }
        });
    }

    static boolean deleteFile(File file) {
        return file != null && file.exists() && file.isFile() && file.delete();
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
            if (returnFs.size() == limit)
                break;

            String s = f.getType().getName();
            if ((TextUtils.isEmpty(exact) || s.equals(exact))
                    && (TextUtils.isEmpty(start) || s.startsWith(start))
                    && (TextUtils.isEmpty(end) || s.endsWith(end)))
                returnFs.add(f);

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
}
