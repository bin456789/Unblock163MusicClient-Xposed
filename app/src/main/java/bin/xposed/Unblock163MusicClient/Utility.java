package bin.xposed.Unblock163MusicClient;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Utility {

    public static String getFirstPartOfString(String str, String separator) {
        return str.substring(0, str.indexOf(separator));
    }

    public static String getLastPartOfString(String str, String separator) {
        return str.substring(str.lastIndexOf(separator) + 1);
    }

    public static String serialData(JSONObject json) throws UnsupportedEncodingException, JSONException {
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

    public static String serialData(Map<String, String> dataMap) throws UnsupportedEncodingException {
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
    public static String serialCookies(List<Cookie> cookieList) throws UnsupportedEncodingException, JSONException {
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
}
