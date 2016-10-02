package bin.xposed.Unblock163MusicClient;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;

class Handler {


    final static LinkedHashMap<Long, Song> THIRD_PARTY_MUSIC_INFO = new LinkedHashMap<Long, Song>() {
        @Override
        protected boolean removeEldestEntry(Entry eldest) {
            return size() > 10;
        }
    };
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
    static Map<String, String> LAST_PLAYLIST_MANIPULATE_MAP;
    static String LAST_LIKE_STRING;
    static String XAPI = "http://xmusic.xmusic.top/xapi/v1/";
    private static long LIKE_PLAYLIST_ID = -1;

    static String modifyByRegex(String originalContent) {
        if (originalContent != null) {
            originalContent = REX_ST.matcher(originalContent).replaceAll("\"st\":0");
            originalContent = REX_PL.matcher(originalContent).replaceAll("\"pl\":320000");
            originalContent = REX_DL.matcher(originalContent).replaceAll("\"dl\":320000");
            originalContent = REX_SUBP.matcher(originalContent).replaceAll("\"subp\":1");
        }
        return originalContent;
    }

    static String modifyPlayerOrDownloadApi(String path, String originalContent, String from) throws JSONException, IllegalAccessException, InstantiationException, InvocationTargetException {
        if (originalContent == null)
            return null;

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

    static String modifyPlaylistManipulateApi(String originalContent) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");

        if (code != 200) {
            return Http.post(XAPI + "manipulate", LAST_PLAYLIST_MANIPULATE_MAP).getResponseText();
        }
        return originalContent;
    }

    static String modifyLike(String originalContent) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code != 200) {
            if (LIKE_PLAYLIST_ID == -1)
                CloudMusicPackage.CAC.getMyPlaylist();

            String query = new URI(LAST_LIKE_STRING).getQuery();
            Map<String, String> dataMap = Utility.queryToMap(query);
            dataMap.put("playlistId", String.valueOf(LIKE_PLAYLIST_ID));

            return Http.post(XAPI + "like", dataMap).getResponseText();
        }

        return originalContent;
    }

    static void cacheLikePlaylistId(String originalContent) throws JSONException {
        if (LIKE_PLAYLIST_ID == -1 && originalContent.contains("\"/api/user/playlist\"")) {
            LIKE_PLAYLIST_ID = new JSONObject(originalContent)
                    .getJSONObject("/api/user/playlist")
                    .getJSONArray("playlist")
                    .getJSONObject(0).getLong("id");
        }
    }


    private static boolean processSong(JSONObject originalSong, int expectBitrate, String from) {
        Song oldSong = new Song(originalSong);

        if ((oldSong.fee != 0 && oldSong.payed == 0 && oldSong.br < expectBitrate)
                || oldSong.url == null
                && oldSong.uf == null) {

            boolean isAccessable;

            // p
            JSONObject pJson = Handler.getSongByRemoteApi(oldSong.id, expectBitrate);
            Song song = new Song(pJson);
            isAccessable = song.checkAccessable();

            // m
            if (!isAccessable) {
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

                if ("player".equals(from))
                    THIRD_PARTY_MUSIC_INFO.put(song.id, song);

            } catch (JSONException e) {
                return false;
            }
            return true;
        } else {
            if ("player".equals(from))
                THIRD_PARTY_MUSIC_INFO.remove(oldSong.id);
            return false;
        }
    }

    private static JSONObject getSongByRemoteApi(final long songId, final int expectBitrate) {
        try {
            Map<String, String> map = new LinkedHashMap<String, String>() {{
                put("id", String.valueOf(songId));
                put("br", String.valueOf(expectBitrate));
                put("withHQ", "1");
            }};
            String raw = Http.post(XAPI + "song", map).getResponseText();
            return new JSONObject(raw).getJSONObject("data");
        } catch (Throwable t) {
            return null;
        }
    }

    private static JSONObject getSongByRemoteApiEnhance(final long songId, final int expectBitrate) {
        try {
            Map<String, String> map = new LinkedHashMap<String, String>() {{
                put("id", String.valueOf(songId));
                put("br", String.valueOf(expectBitrate));
            }};
            String raw = Http.post(XAPI + "songx", map).getResponseText();
            return new JSONObject(raw).getJSONObject("data");
        } catch (Throwable t) {
            return null;
        }
    }

    private static JSONObject getSongByXiami(final long songId, final int expectBitrate) {
        try {
            Map<String, String> map = new LinkedHashMap<String, String>() {{
                put("id", String.valueOf(songId));
                put("br", String.valueOf(expectBitrate));
            }};
            String raw = Http.post(XAPI + "3rd/match", map).getResponseText();
            return new JSONObject(raw).getJSONObject("data");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String convertPtoM(String pUrl) {
        if (pUrl != null)
            return "http://m2" + pUrl.substring(pUrl.indexOf('.'));
        else
            return null;
    }


    @Deprecated
    private static JSONObject getSongByPlayerApi(long songId, int expectBitrate) {
        try {
            String ids = URLEncoder.encode(String.format("[\"%s_0\"]", songId), "UTF-8");
            String url = String.format("song/enhance/player/url?br=%s&ids=%s", expectBitrate, ids);
            String raw = CloudMusicPackage.HttpEapi.post(url, null);
            return new JSONObject(raw).getJSONArray("data").getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    @Deprecated
    private static JSONObject getSongByDownloadApi(long songId, int expectBitrate) {
        String url = String.format("song/enhance/download/url?br=%s&id=%s_0", expectBitrate, songId);
        try {
            String raw = CloudMusicPackage.HttpEapi.post(url, null);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    @Deprecated
    private static String generateUrl(long fid) {
        return (String) XposedHelpers.callStaticMethod(CloudMusicPackage.NeteaseMusicUtils.CLASS, "a", fid);
    }

    @Deprecated
    private static JSONObject getSongByDetailApi(long songId, int expectBitrate) {
        List<Long> songIds = new ArrayList<>();
        songIds.add(songId);
        try {
            return getSongByDetailApi(songIds, expectBitrate)[0];
        } catch (Exception e) {
            return null;
        }
    }

    @Deprecated
    private static JSONObject[] getSongByDetailApi(List<Long> songIds, int expectBitrate) throws JSONException, InvocationTargetException, InstantiationException, UnsupportedEncodingException, IllegalAccessException {
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

