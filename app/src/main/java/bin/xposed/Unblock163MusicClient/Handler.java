package bin.xposed.Unblock163MusicClient;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;

class Handler {
    static final String XAPI = "http://xmusic.xmusic.top/xapi/v1/";
    private static final Date DOMAIN_EXPIRED_DATE = new GregorianCalendar(2017, 10 - 1, 1).getTime();
    private static final Pattern REX_PL = Pattern.compile("\"pl\":(?!999000)\\d+");
    private static final Pattern REX_DL = Pattern.compile("\"dl\":(?!999000)\\d+");
    private static final Pattern REX_SUBP = Pattern.compile("\"subp\":\\d+");
    private static final Map<String, Integer> QUALITY_MAP = new LinkedHashMap<String, Integer>() {
        {
            put("a", 64000);
            put("l", 128000);
            put("m", 192000);
            put("h", 320000);
        }
    };
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

    static String modifyPlayerOrDownloadApi(String originalContent, Object eapiObj, String from) throws JSONException {
        JSONObject originalJson = new JSONObject(originalContent);
        String path = new CloudMusicPackage.HttpEapi(eapiObj).getPath();

        int expectBitrate;
        try {
            expectBitrate = Integer.parseInt(Uri.parse(path).getQueryParameter("br"));
        } catch (Throwable t) {
            try {
                expectBitrate = Integer.parseInt(new CloudMusicPackage.HttpEapi(eapiObj).getRequestMap().get("br"));
            } catch (Throwable th) {
                expectBitrate = 320000;
            }
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
        else
            return originalContent;
    }

    static String modifyPlaylistManipulateApi(String originalContent, Object eapiObj) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");

        // 重复收藏 502
        if (code != 200 && code != 502) {
            @SuppressWarnings({"unchecked"})
            Map<String, String> requestMap = new CloudMusicPackage.HttpEapi(eapiObj).getRequestMap();
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
            String likeString = new CloudMusicPackage.HttpEapi(eapiObj).getPath();
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
        if (new CloudMusicPackage.HttpEapi(eapiObj).getRequestMap().containsKey(api)) {
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
            Map<String, String> requestMap = new CloudMusicPackage.HttpEapi(eapiObj).getRequestMap();
            String raw = Http.post(XAPI + "pub", requestMap).getResponseText();
            if (Utility.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    private static boolean processSong(JSONObject oldSongJson, int expectBitrate, String from) {
        // 异常在这方法里处理，防止影响下一曲
        if (oldSongJson == null)
            return false;

        try {
            Song oldSong = Song.parseFromOther(oldSongJson);

            if (oldSong.uf != null)
                return false;

            if (expectBitrate > 320000)
                expectBitrate = 320000;

            if (oldSong.url == null
                    || (oldSong.fee != 0 && oldSong.payed == 0 && oldSong.br < expectBitrate)) {

                Song song1 = null;
                Song song3 = null;
                boolean song1Accessible = false;
                boolean song3Accessible = false;


                // detail
                try {
                    song1 = getSongByDetailApi(oldSong.id, expectBitrate);
                    if (song1 != null) {
                        song1Accessible = true;
                    }
                } catch (Throwable t) {
                    XposedBridge.log("detail api failed");
                    XposedBridge.log(t);
                }

                // 3rd / enhance
                if (!song1Accessible || song1.br < expectBitrate) {
                    try {
                        if (oldSong.code == 404 || ("download".equals(from) && oldSong.code == -110)) {
                            song3 = Handler.getSongBy3rdApi(oldSong.id, expectBitrate);
                            song3Accessible = song3.checkAccessible(); // fix music size
                        } else {
                            song1 = Handler.getSongByRemoteApiEnhance(oldSong.id, expectBitrate);
                            song1Accessible = true;
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("songx/3rd api failed");
                        XposedBridge.log(t);
                    }
                }


                Song song;
                // 如果song1不可用，song3可用，选择song3
                if (!song1Accessible && song3Accessible) {
                    song = song3;
                }
                // 如果song3完全匹配且br比song1高，选择song3
                else if (song3Accessible
                        && song3.matchedDuration
                        && song3.br > song1.br) {
                    song = song3;
                }
                // 其余情况选择song1
                else {
                    song = song1;
                }

                if (song != null) {
                    oldSongJson.put("br", song.br)
                            .put("code", 200)
                            .put("gain", song.gain)
                            .put("md5", song.md5)
                            .put("size", song.size)
                            .put("type", song.type)
                            .put("url", song.url);

                    try {
                        if (song.isMatchedSong()) {
                            File dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                            String fileName = String.format("%s-%s-%s.%s.xp!", song.id, song.br, song.md5, song.type);
                            File file = new File(dir, fileName);
                            String str = song.getMatchedJson().toString();
                            Utility.writeFile(file, str);
                        } else {
                            File dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                            String start = String.format("%s-%s", song.id, song.br);
                            String end = ".xp!";
                            File file = Utility.findFirstFile(dir, start, end);
                            Utility.deleteFile(file);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("read 3rd party tips failed");
                        XposedBridge.log(t);
                    }
                    return true;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return false;
    }

    private static Song getSongByRemoteApi(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
            put("withHQ", "1");
        }};
        String raw = Http.post(XAPI + "song", map).getResponseText();
        JSONObject json = new JSONObject(raw).getJSONObject("data");
        return Song.parseFromOther(json);
    }

    private static Song getSongByRemoteApiEnhance(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
        }};
        String raw = Http.post(XAPI + "songx", map).getResponseText();
        JSONObject json = new JSONObject(raw).getJSONObject("data");
        return Song.parseFromOther(json);
    }

    private static Song getSongBy3rdApi(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
        }};
        String raw = Http.post(XAPI + "3rd/match", map).getResponseText();
        JSONObject json = new JSONObject(raw).getJSONObject("data");
        return Song.parseFromOther(json);
    }


    private static Song getSongByDetailApi(long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new HashMap<>();
        JSONArray c = new JSONArray().put(new JSONObject().put("id", songId).put("v", 0));
        map.put("c", c.toString());

        String page = CloudMusicPackage.HttpEapi.post("v3/song/detail", map);
        JSONObject detail = (JSONObject) new JSONObject(page).getJSONArray("songs").get(0);

        // find first detail br >= expect br
        int firstIndex = 0;
        List<Integer> values = new ArrayList<>(QUALITY_MAP.values());
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) >= expectBitrate) {
                firstIndex = i;
                break;
            }
        }

        // rearrange seq
        List<String> keys = new ArrayList<>(QUALITY_MAP.keySet());
        List<String> seqList = new ArrayList<>(QUALITY_MAP.size());
        for (int i = firstIndex; i < keys.size(); i++) {
            String key = keys.get(i);
            seqList.add(key);
        }
        for (int i = firstIndex - 1; i >= 0; i--) {
            String key = keys.get(i);
            seqList.add(key);
        }

        for (String quality : seqList) {
            if (detail.has(quality) && !detail.isNull(quality)) {
                try {
                    Song song = Song.parseFromDetail(detail.getJSONObject(quality), QUALITY_MAP.get(quality));
                    boolean accessible = song.checkAccessible();
                    if (!accessible) {
                        // m
                        song.url = convertPtoM(song.url);
                        accessible = song.checkAccessible();
                    }
                    if (accessible) {
                        return song;
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        }
        return null;
    }

    private static String convertPtoM(String pUrl) {
        if (pUrl != null && pUrl.startsWith("http://p")) {
            return "http://m2" + pUrl.substring(pUrl.indexOf('.'));
        }
        return null;
    }
}

