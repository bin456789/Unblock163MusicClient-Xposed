package bin.xposed.Unblock163MusicClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

@Deprecated()
@SuppressWarnings({"unused", "WeakerAccess"})
class HandlerDeprecated {
    private static final Map<String, Integer> QUALITY_MAP = new LinkedHashMap<String, Integer>() {
        {
            put("h", 320000);
            put("m", 160000);
            put("l", 96000);
            put("a", 64000);
        }
    };

    static JSONObject getSongByPlayerApi(long songId, int expectBitrate) {
        try {
            String ids = URLEncoder.encode(String.format("[\"%s_0\"]", songId), "UTF-8");
            String url = String.format("song/enhance/player/url?br=%s&ids=%s", expectBitrate, ids);
            String raw = CloudMusicPackage.HttpEapi.post(url, null);
            return new JSONObject(raw).getJSONArray("data").getJSONObject(0);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    static JSONObject getSongByDownloadApi(long songId, int expectBitrate) {
        String url = String.format("song/enhance/download/url?br=%s&id=%s_0", expectBitrate, songId);
        try {
            String raw = CloudMusicPackage.HttpEapi.post(url, null);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    static JSONObject getSongByDetailApi(long songId, int expectBitrate) {
        List<Long> songIds = new ArrayList<>();
        songIds.add(songId);
        try {
            return getSongByDetailApi(songIds, expectBitrate)[0];
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    static JSONObject[] getSongByDetailApi(List<Long> songIds, int expectBitrate) throws JSONException, InvocationTargetException, InstantiationException, UnsupportedEncodingException, IllegalAccessException {
        JSONObject[] returnObjects = new JSONObject[songIds.size()];

        JSONArray c = new JSONArray();
        for (long songId : songIds) {
            c.put(new JSONObject().put("id", songId).put("v", 0));
        }

        Map<String, String> map = new HashMap<>();
        map.put("c", c.toString());
        String page = CloudMusicPackage.HttpEapi.post("v3/song/detail", map);
        JSONArray jsonArraySong = new JSONObject(page).getJSONArray("songs");


        for (int i = 0; i < jsonArraySong.length(); i++) {
            JSONObject songObject = (JSONObject) jsonArraySong.get(i);
            for (String quality : QUALITY_MAP.keySet()) {
                if (songObject.has(quality) && !songObject.isNull(quality)
                        && expectBitrate >= songObject.getJSONObject(quality).getInt("br")) {
                    returnObjects[i] = songObject.getJSONObject(quality);
                    break;
                }
            }
        }
        return returnObjects;
    }
}
