package bin.xposed.Unblock163MusicClient;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;

class Handler {
    static final String XAPI = "http://xmusic.xmusic.top/xapi/v1/";
    private static final Date DOMAIN_EXPIRED_DATE = new GregorianCalendar(2017, 10 - 1, 1).getTime();
    private static final Pattern REX_PL = Pattern.compile("\"pl\":(?!999000)\\d+");
    private static final Pattern REX_DL = Pattern.compile("\"dl\":(?!999000)\\d+");
    private static final Pattern REX_SUBP = Pattern.compile("\"subp\":\\d+");

    private static long likePlaylistId = -1;

    static boolean isDomainExpired() {
        return Calendar.getInstance().getTime().after(Handler.DOMAIN_EXPIRED_DATE);
    }


    static String modifyByRegex(String originalContent) {
        originalContent = REX_PL.matcher(originalContent).replaceAll("\"pl\":320000");
        originalContent = REX_DL.matcher(originalContent).replaceAll("\"dl\":320000");
        originalContent = REX_SUBP.matcher(originalContent).replaceAll("\"subp\":1");
        return originalContent;
    }

    static String modifyPlayerOrDownloadApi(String originalContent, Object eapiObj, String from) throws JSONException, IllegalAccessException {
        JSONObject originalJson = new JSONObject(originalContent);
        String path = CloudMusicPackage.HttpEapi.getPath(eapiObj);
        int expectBitrate = Integer.parseInt(Uri.parse(path).getQueryParameter("br"));

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
        else
            return originalContent;
    }

    static String modifyPlaylistManipulateApi(String originalContent, Object eapiObj) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");

        // 重复收藏 502
        if (code != 200 && code != 502) {
            @SuppressWarnings({"unchecked"})
            Map<String, String> requestMap = CloudMusicPackage.HttpEapi.getRequestMap(eapiObj);
            String raw = Http.post(XAPI + "manipulate", requestMap).getResponseText();
            if (Utility.isJSONValid(raw)) {
                return raw;
            }
        }
        return originalContent;
    }

    static String modifyLike(String originalContent, Object eapiObj) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code != 200) {
            if (likePlaylistId == -1) {
                CloudMusicPackage.CAC.refreshMyPlaylist();
            }
            String likeString = CloudMusicPackage.HttpEapi.getPath(eapiObj);
            String query = URI.create(likeString).getQuery();
            Map<String, String> dataMap = Utility.queryToMap(query);
            dataMap.put("playlistId", String.valueOf(likePlaylistId));
            String raw = Http.post(XAPI + "like", dataMap).getResponseText();
            if (Utility.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    static void cacheLikePlaylistId(String originalContent, Object eapiObj) throws JSONException {
        String api = "/api/user/playlist";
        if (CloudMusicPackage.HttpEapi.getRequestMap(eapiObj).containsKey(api)) {
            likePlaylistId = new JSONObject(originalContent)
                    .getJSONObject(api)
                    .getJSONArray("playlist")
                    .getJSONObject(0).getLong("id");
        }
    }

    static String modifyPub(String originalContent, Object eapiObj) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code != 200) {
            @SuppressWarnings({"unchecked"})
            Map<String, String> requestMap = CloudMusicPackage.HttpEapi.getRequestMap(eapiObj);
            String raw = Http.post(XAPI + "pub", requestMap).getResponseText();
            if (Utility.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    private static boolean processSong(JSONObject originalSong, int expectBitrate, String from) {
        // 异常在这方法里处理，防止影响下一曲
        try {
            Song oldSong = new Song(originalSong);
            Song song = null;

            if (oldSong.uf != null)
                return false;

            if (expectBitrate > 320000)
                expectBitrate = 320000;

            if ((oldSong.fee != 0 && oldSong.payed == 0 && oldSong.br < expectBitrate)
                    || oldSong.url == null) {
                boolean isAccessable = false;

                // p
                JSONObject pJson = Handler.getSongByRemoteApi(oldSong.id, expectBitrate);
                if (pJson != null) {
                    song = new Song(pJson);
                    isAccessable = song.checkAccessable();

                    // m
                    if (!isAccessable) {
                        song.url = Handler.convertPtoM(song.url);
                        isAccessable = song.checkAccessable();
                    }

                    // p 320k
                    if (!isAccessable && pJson.has("h")) {
                        song = new Song(pJson.optJSONObject("h"));
                        isAccessable = song.checkAccessable();
                    }

                    // m 320k
                    if (!isAccessable && song.url != null) {
                        song.url = Handler.convertPtoM(song.url);
                        isAccessable = song.checkAccessable();
                    }
                }

                if (!isAccessable) {
                    if (oldSong.code == 404 || ("download".equals(from) && oldSong.code == -110)) {
                        JSONObject thirdJson = Handler.getSongBy3rd(oldSong.id, expectBitrate);
                        if (thirdJson != null) {
                            song = new Song(thirdJson);
                            song.checkAccessable(); // fix music size
                        }
                    } else {
                        JSONObject enhanceJson = Handler.getSongByRemoteApiEnhance(oldSong.id, expectBitrate);
                        if (enhanceJson != null) {
                            song = new Song(enhanceJson);
                        }
                    }
                }
            }

            if (song == null || song.url == null)
                return false;


            originalSong.put("br", song.br)
                    .put("code", 200)
                    .put("gain", song.gain)
                    .put("md5", song.md5)
                    .put("size", song.size)
                    .put("type", song.type)
                    .put("url", song.url);

            try {
                if (song.isMatchedSong()) {
                    String dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                    String fileName = String.format("%s-%s-%s.%s.xp!", song.id, song.br, song.md5, song.type);
                    File file = new File(dir, fileName);
                    String str = song.getMatchedJson().toString();
                    Utility.writeFile(file, str);
                } else {
                    String dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                    String start = String.format("%s-%s", song.id, song.br);
                    String end = ".xp!";
                    File file = Utility.findFirstFile(dir, start, end);
                    Utility.deleteFile(file);
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            return true;

        } catch (Throwable t) {
            XposedBridge.log(t);
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
            XposedBridge.log(t);
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
            XposedBridge.log(t);
            return null;
        }
    }

    private static JSONObject getSongBy3rd(final long songId, final int expectBitrate) {
        try {
            Map<String, String> map = new LinkedHashMap<String, String>() {{
                put("id", String.valueOf(songId));
                put("br", String.valueOf(expectBitrate));
            }};
            String raw = Http.post(XAPI + "3rd/match", map).getResponseText();
            return new JSONObject(raw).getJSONObject("data");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    private static String convertPtoM(String pUrl) {
        if (pUrl != null)
            return "http://m2" + pUrl.substring(pUrl.indexOf('.'));
        else
            return null;
    }

}

