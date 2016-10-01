package bin.xposed.Unblock163MusicClient;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.text.TextUtils;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Utility {
    private static SimpleResolver CN_DNS_RESOLVER;

    private static String getFirstPartOfString(String str, String separator) {
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

    static String getIpByHost(String domain) {
        try {
            if (CN_DNS_RESOLVER == null)
                CN_DNS_RESOLVER = new SimpleResolver(Settings.getDnsServer());

            // caches mechanism built-in, just look it up
            Lookup lookup = new Lookup(domain, Type.A);
            lookup.setResolver(CN_DNS_RESOLVER);
            Record[] records = lookup.run();
            if (lookup.getResult() == Lookup.SUCCESSFUL) {
                // already random, just pick index 0
                return records[0].rdataToString();
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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

    static Resources getModuleResources() throws PackageManager.NameNotFoundException {
        return CloudMusicPackage.NeteaseMusicApplication.getApplication().createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY).getResources();
    }

}
