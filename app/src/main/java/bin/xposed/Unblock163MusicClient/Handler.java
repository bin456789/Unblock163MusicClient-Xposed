package bin.xposed.Unblock163MusicClient;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;

public class Handler {

    final private static Pattern REX_PL = Pattern.compile("\"pl\":\\d+");
    final private static Pattern REX_DL = Pattern.compile("\"dl\":\\d+");
    final private static Pattern REX_ST = Pattern.compile("\"st\":-\\d+");
    final private static Pattern REX_SUBP = Pattern.compile("\"subp\":\\d+");
    final private static Map<String, Integer> QUALITY_MAP = new LinkedHashMap<String, Integer>() {
        {
            put("h", 320000);
            put("m", 160000);
            put("l", 96000);
            put("a", 64000);
        }
    };

    protected static Map LAST_PLAYLIST_MANIPULATE_MAP;
    protected static String LAST_LIKE_STRING;
    protected static long LIKE_PLAYLIST_ID = 0;


    protected static String modifyByRegex(String originalContent) {
        String modified = originalContent;
        if (modified != null) {
            modified = REX_ST.matcher(modified).replaceAll("\"st\":0");
            modified = REX_PL.matcher(modified).replaceAll("\"pl\":320000");
            modified = REX_DL.matcher(modified).replaceAll("\"dl\":320000");
            modified = REX_SUBP.matcher(modified).replaceAll("\"subp\":1");
            return modified;
        }
        return null;
    }

    protected static String modifyPlayerOrDownloadApi(String path, String originalContent, String from) throws JSONException, IllegalAccessException, InstantiationException, InvocationTargetException, IOException {
        JSONObject originalJson = new JSONObject(originalContent);

        int expectBitrate;
        try {
            expectBitrate = Integer.parseInt(Uri.parse(path).getQueryParameter("br"));
        } catch (Exception e) {
            expectBitrate = "player".equals(from) ? CloudMusicPackage.NeteaseMusicUtils.getPlayQuality() : CloudMusicPackage.NeteaseMusicUtils.getDownloadQuality();
        }

        boolean isModified = false;

        Object data = originalJson.get("data");
        if (data instanceof JSONObject) {
            JSONObject originalSong = (JSONObject) data;
            if (processSong(originalSong, expectBitrate, from))
                isModified = true;
        } else {
            JSONArray originalSongs = (JSONArray) data;
            for (int i = 0; i < originalSongs.length(); i++)
                if (processSong(originalSongs.getJSONObject(i), expectBitrate, from))
                    isModified = true;
        }

        if (isModified)
            return originalJson.toString();
        return originalContent;
    }

    protected static String modifyPlaylistManipulateApi(String originalContent) throws JSONException, IOException {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");

        if (code != 200) {
            @SuppressWarnings("unchecked")
            String postData = Utility.serialData(LAST_PLAYLIST_MANIPULATE_MAP);
            return Http.post("http://music.xposed.tk/xapi/v1/manipulate", postData, true);
        }
        return originalContent;
    }


    protected static String modifyLike(String originalContent) throws JSONException, IOException, URISyntaxException {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code != 200) {
            if (LIKE_PLAYLIST_ID == 0)
                CloudMusicPackage.CAC.getMyPlaylist();

            String query = new URI(LAST_LIKE_STRING).getQuery();
            String postData = query + "&playlistId=" + LIKE_PLAYLIST_ID;
            return Http.post("http://music.xposed.tk/xapi/v1/like", postData, true);
        }

        return originalContent;
    }

    protected static boolean processSong(JSONObject originalSong, int expectBitrate, String from) {
        Song oldSong = new Song(originalSong);
        if ((oldSong.fee != 0 && oldSong.payed == 0 && oldSong.br < expectBitrate)
                || oldSong.url == null
                && oldSong.uf == null) {

            // p
            JSONObject pJson = Handler.getSongByRemoteApi(oldSong.id, expectBitrate);
            Song song = new Song(pJson);
            boolean isAccessable = song.checkAccessable();

            // m
            if (!isAccessable && song.url != null) {
                song.url = Handler.convertPtoM(song.url);
                isAccessable = song.checkAccessable();
            }

            // p 320k
            if (!isAccessable && pJson != null && pJson.has("h")) {
                song = new Song(pJson.optJSONObject("h"));
                isAccessable = song.checkAccessable();
            }

            // m 320k
            if (!isAccessable && song.url != null) {
                song.url = Handler.convertPtoM(song.url);
                isAccessable = song.checkAccessable();
            }


            if (!isAccessable) {
                if (oldSong.code == 404 || ("download".equals(from) && oldSong.code == -110)) {
                    song = new Song(Handler.getSongByXiami(oldSong.id, expectBitrate));
                    song.checkAccessable(); // fix music length
                } else {
                    song = new Song(Handler.getSongByRemoteApiEnhance(oldSong.id, expectBitrate));
                }
            }

            try {
                originalSong.put("br", song.br)
                        .put("code", 200)
                        .put("gain", song.gain)
                        .put("md5", song.md5)
                        .put("size", song.size)
                        .put("type", song.type)
                        .put("url", song.url);
            } catch (JSONException e) {
                return false;
            }
            return true;
        }
        return false;
    }

    protected static JSONObject getSongByRemoteApi(long songId, int expectBitrate) {
        try {
            String raw = Http.post("http://music.xposed.tk/xapi/v1/song", String.format("id=%s&br=%s&withHQ=1", songId, expectBitrate), true);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    protected static JSONObject getSongByRemoteApiEnhance(long songId, int expectBitrate) {
        try {
            String raw = Http.post("http://music.xposed.tk/xapi/v1/songx", String.format("id=%s&br=%s", songId, expectBitrate), true);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    protected static JSONObject getSongByXiami(long songId, int expectBitrate) {
        try {
            String raw = Http.post("http://music.xposed.tk/xapi/v1/xiami/match", String.format("id=%s&br=%s", songId, expectBitrate), true);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    protected static void cacheLikePlaylistId(String originalContent) throws JSONException {
        if (LIKE_PLAYLIST_ID == 0 && originalContent.contains("\"/api/user/playlist\"")) {
            LIKE_PLAYLIST_ID = new JSONObject(originalContent)
                    .getJSONObject("/api/user/playlist")
                    .getJSONArray("playlist")
                    .getJSONObject(0).getLong("id");
        }
    }

    protected static String convertPtoM(String pUrl) {
        return "http://m2" + pUrl.substring(pUrl.indexOf('.'));
    }


    protected static JSONObject getSongByPlayerApi(long songId, int expectBitrate) {
        try {
            String ids = URLEncoder.encode(String.format("[\"%s_0\"]", songId), "UTF-8");
            String url = String.format("song/enhance/player/url?br=%s&ids=%s", expectBitrate, ids);
            String raw = CloudMusicPackage.HttpEapi.post(url, null);
            return new JSONObject(raw).getJSONArray("data").getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    protected static JSONObject getSongByDownloadApi(long songId, int expectBitrate) {
        String url = String.format("song/enhance/download/url?br=%s&id=%s_0", expectBitrate, songId);
        try {
            String raw = CloudMusicPackage.HttpEapi.post(url, null);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }


    protected static String generateUrl(long fid) {
        return (String) XposedHelpers.callStaticMethod(CloudMusicPackage.NeteaseMusicUtils.CLASS, "a", fid);
    }

    protected static JSONObject getSongByDetailApi(long songId, int expectBitrate) {
        List<Long> songIds = new ArrayList<>();
        songIds.add(songId);
        try {
            return getSongByDetailApi(songIds, expectBitrate)[0];
        } catch (Exception e) {
            return null;
        }
    }

    protected static JSONObject[] getSongByDetailApi(List<Long> songIds, int expectBitrate) throws JSONException, InvocationTargetException, InstantiationException, UnsupportedEncodingException, IllegalAccessException {
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

