package bin.xposed.Unblock163MusicClient;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;


final class Utility {
    final static Map<String, Integer> QUALITY_MAP = new LinkedHashMap<String, Integer>() {
        {
            put("h", 320000);
            put("m", 160000);
            put("l", 96000);
            put("a", 64000);
        }
    };
    private final static Pattern RexPl = Pattern.compile("\"pl\":\\d+");
    private final static Pattern RexDl = Pattern.compile("\"dl\":\\d+");
    private final static Pattern RexSt = Pattern.compile("\"st\":-?\\d+");
    private final static Pattern RexSubp = Pattern.compile("\"subp\":\\d+");
    public static Class<?> CLASS_utils_NeteaseMusicUtils;
    public static Constructor<?> CONSTRUCTOR_i_f;
    public static Field FIELD_utils_c;

    public static boolean Init(ClassLoader classLoader) throws NoSuchFieldException {
        CLASS_utils_NeteaseMusicUtils = XposedHelpers.findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", classLoader);
        CONSTRUCTOR_i_f = findConstructorExact(findClass("com.netease.cloudmusic.i.f", classLoader), String.class, Map.class);
        FIELD_utils_c = findClass("com.netease.cloudmusic.utils.u", classLoader).getDeclaredField("c");
        FIELD_utils_c.setAccessible(true);
        return true;
    }

    public static String ModifyDetailApi(String originalContent) {
        String modified = originalContent;
        modified = RexSt.matcher(modified).replaceAll("\"st\":0");
        modified = RexPl.matcher(modified).replaceAll("\"pl\":320000");
        modified = RexDl.matcher(modified).replaceAll("\"dl\":320000");
        modified = RexSubp.matcher(modified).replaceAll("\"subp\":1");
        return modified;
    }

    public static String ModifyPlayerApi(String url, String originalContent) throws JSONException, IllegalAccessException, InstantiationException, InvocationTargetException, MalformedURLException {
        int expectBitrate = Integer.parseInt(Uri.parse(url).getQueryParameter("br"));
        JSONObject originalJson = new JSONObject(originalContent);
        JSONArray originalSongArray = originalJson.getJSONArray("data");

        List<Long> blockedSongIdList = new ArrayList<>();

        for (int i = 0; i < originalSongArray.length(); i++) {
            if (originalSongArray.getJSONObject(i).isNull("url")) {
                long songId = originalSongArray.getJSONObject(i).getLong("id");
                blockedSongIdList.add(songId);
            }
        }

        if (blockedSongIdList.size() > 0) {
            Long[] blockedSongIdStrings = blockedSongIdList.toArray(new Long[blockedSongIdList.size()]);
            JSONObject[] newSongJson = GetOneQualityFromSongId(blockedSongIdStrings, expectBitrate);
            for (int i = 0; i < originalSongArray.length(); i++) {
                if (originalSongArray.getJSONObject(i).isNull("url")) {
                    originalSongArray.getJSONObject(i)
                            .put("url", GenerateUrl(newSongJson[i].getLong("fid")))
                            .put("br", newSongJson[i].getInt("br"))
                            .put("size", newSongJson[i].getString("size"))
                            .put("code", 200)
                            .put("type", "mp3")
                            .put("gain", newSongJson[i].getString("vd"));
                }
            }
        }
        return originalJson.toString();
    }


    public static JSONObject[] GetOneQualityFromSongId(Long[] songIds, Integer expectBitrate) throws JSONException, IllegalAccessException, InvocationTargetException, InstantiationException {
        JSONArray c = new JSONArray();
        for (Long songId : songIds) {
            c.put(new JSONObject().put("id", songId).put("v", 0));
        }

        Map<String, String> map = new HashMap<>();
        map.put("c", c.toString());

        String page = (String) XposedHelpers.callMethod(XposedHelpers.callMethod(CONSTRUCTOR_i_f.newInstance("v3/song/detail", map), "c"), "i");
        JSONObject root = new JSONObject(page);

        return GetOneQualityFromJson(root, expectBitrate);
    }

    private static JSONObject[] GetOneQualityFromJson(JSONObject root, Integer expectBitrate) throws JSONException {
        JSONArray jsonArraySong = root.getJSONArray("songs");
        JSONObject[] returnObjects = new JSONObject[jsonArraySong.length()];

        for (int i = 0; i < jsonArraySong.length(); i++) {
            JSONObject songObject = (JSONObject) jsonArraySong.get(i);
            for (String q : QUALITY_MAP.keySet()) {
                if (songObject.has(q) && !songObject.isNull(q) && expectBitrate >= songObject.getJSONObject(q).getInt("br")) {
                    returnObjects[i] = songObject.getJSONObject(q);
                    break;
                }
            }
        }

        return returnObjects;
    }

    public static String GenerateUrl(Long fid) {
        return (String) XposedHelpers.callStaticMethod(CLASS_utils_NeteaseMusicUtils, "a", fid);
    }
}
