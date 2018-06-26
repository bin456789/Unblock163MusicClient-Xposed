package bin.xposed.Unblock163MusicClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage.HttpEapi;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.E.showToast;
import static de.robv.android.xposed.XposedBridge.log;

public class Handler {
    private static final String XAPI = "http://xmusic.xmusic.top/xapi/v1/";
    private static final Date DOMAIN_EXPIRED_DATE = new GregorianCalendar(2018, 10 - 1, 1).getTime();
    private static final Pattern REX_PL = Pattern.compile("\"pl\":(?!999000)\\d+");
    private static final Pattern REX_DL = Pattern.compile("\"dl\":(?!999000)\\d+");
    private static final Pattern REX_SUBP = Pattern.compile("\"subp\":\\d+");
    private static final Map<String, Integer> QUALITY_MAP = new LinkedHashMap<String, Integer>() {
        {
            put("l", 128000);
            put("m", 192000);
            put("h", 320000);
        }
    };
    private static final ExecutorService handlerPool = Executors.newCachedThreadPool();

    static boolean isDomainExpired() {
        return Calendar.getInstance().getTime().after(DOMAIN_EXPIRED_DATE);
    }

    public static String modifyByRegex(String originalContent) {
        originalContent = REX_PL.matcher(originalContent).replaceAll("\"pl\":320000");
        originalContent = REX_DL.matcher(originalContent).replaceAll("\"dl\":320000");
        originalContent = REX_SUBP.matcher(originalContent).replaceAll("\"subp\":1");
        return originalContent;
    }

    public static String modifyPlayerOrDownloadApi(String originalContent, HttpEapi eapi, final String from) throws JSONException, URISyntaxException {
        JSONObject originalJson = new JSONObject(originalContent);

        int expectBitrate = Integer.parseInt(eapi.getRequestData().get("br"));


        boolean isModified = false;
        Object data = originalJson.get("data");
        if (data instanceof JSONObject) {
            JSONObject originalSong = (JSONObject) data;
            if (processSong(originalSong, expectBitrate, from)) {
                isModified = true;
            }
        } else {
            JSONArray originalSongs = (JSONArray) data;
            Set<Future<Boolean>> futureSet = new HashSet<>();
            final int finalExpectBitrate = expectBitrate;
            for (int i = 0; i < originalSongs.length(); i++) {
                final JSONObject songJson = originalSongs.getJSONObject(i);
                futureSet.add(handlerPool.submit(() -> processSong(songJson, finalExpectBitrate, from)));
            }
            for (Future<Boolean> booleanFuture : futureSet) {
                try {
                    if (booleanFuture.get()) {
                        isModified = true;
                    }
                } catch (Throwable t) {
                    log(t);
                }
            }
        }

        if (isModified) {
            return originalJson.toString();
        } else {
            return originalContent;
        }
    }

    public static String modifyPlaylistManipulateApi(String originalContent, HttpEapi eapi) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code == 502) {
            showToast("歌曲已存在");

        } else if (code != 200) {
            @SuppressWarnings({"unchecked"})
            Map<String, String> map = eapi.getRequestData();
            String raw = Http.post(XAPI + "manipulate", map, true).getResponseText();
            JSONObject json = new JSONObject(raw);
            int xcode = json.optInt("code");
            if (xcode == 401 || xcode == 512) {
                json.put("code", 502);
                showToast("下架/未购买的付费歌曲无法添加到歌单");
            }
            return json.toString();
        }
        return originalContent;
    }

    public static String modifyLike(String originalContent, HttpEapi eapi) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code != 200) {
            Map<String, String> map = eapi.getRequestData();
            String raw = Http.post(XAPI + "like", map, true).getResponseText();
            if (Utility.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    public static String modifyPub(String originalContent, HttpEapi eapi) throws Throwable {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");
        if (code != 200) {
            @SuppressWarnings({"unchecked"})
            Map<String, String> map = eapi.getRequestData();
            String raw = Http.post(XAPI + "pub", map, true).getResponseText();
            if (Utility.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    private static boolean processSong(JSONObject oldSongJson, int expectBr, String from) {
        // 异常在这方法里处理，防止影响下一曲
        if (oldSongJson == null) {
            return false;
        }

        try {
            Song oldSong = Song.parseFromOther(oldSongJson);

            // 云盘
            if (oldSong.uf != null) {
                return false;
            }

            // 原始 mp3 可以连接
            if (oldSong.br > 0 && oldSong.url != null) {
                oldSong.accessible = true;
            }

            // 忽略无损
            if (expectBr > 320000) {
                expectBr = 320000;
            }

            // 要处理的歌曲
            if (oldSong.url == null
                    || (oldSong.fee != 0 && oldSong.payed == 0 && oldSong.br < expectBr)) {

                Song preferSong = oldSong;
                DetailApi detail = null;
                int maxBr = 320000;

                // detail which br >= expect br
                if (preferSong.getPrefer() < expectBr
                        && preferSong.getPrefer() < maxBr) {
                    try {
                        detail = new DetailApi(oldSong.id, expectBr);
                        if (detail != null && detail.maxBr > 0) {
                            maxBr = detail.maxBr;
                            Song tmp = detail.find(1, 0);
                            preferSong = Song.getPreferSong(tmp, preferSong);
                        }
                    } catch (Throwable t) {
                        log("detail api failed " + oldSong.id);
                        log(t);
                    }
                }


                // enhance
                if (oldSong.fee != 0
                        && preferSong.getPrefer() < expectBr
                        && preferSong.getPrefer() < maxBr) {
                    try {
                        Song tmp = getSongByRemoteApiEnhance(oldSong.id, expectBr);
                        preferSong = Song.getPreferSong(tmp, preferSong);
                    } catch (Throwable t) {
                        log("songx api failed " + oldSong.id);
                        log(t);
                    }
                }

                // 3rd
                if (preferSong.getPrefer() < expectBr
                        && preferSong.getPrefer() < maxBr) {
                    try {
                        Song tmp = getSongBy3rdApi(oldSong.id, expectBr);
                        preferSong = Song.getPreferSong(tmp, preferSong);
                    } catch (Throwable t) {
                        log("3rd api failed " + oldSong.id);
                        log(t);
                    }
                }


                // detail which br < expect br
                if (detail != null
                        && preferSong.getPrefer() < expectBr
                        && preferSong.getPrefer() < maxBr) {
                    try {
                        int minBr = preferSong.getPrefer();
                        Song tmp = detail.find(2, minBr);
                        preferSong = Song.getPreferSong(tmp, preferSong);
                    } catch (Throwable t) {
                        log("detail api failed " + oldSong.id);
                        log(t);
                    }
                }


                if (preferSong.getPrefer() > oldSong.getPrefer()) {
                    oldSongJson.put("br", preferSong.br)
                            .put("code", 200)
                            .put("flag", 0)
                            .put("gain", 0)
                            .put("md5", preferSong.md5)
                            .put("size", preferSong.size)
                            .put("type", preferSong.type)
                            .put("url", preferSong.url);

                    try {
                        File cacheDir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                        if (preferSong.is3rdPartySong()) {
                            String fileName = String.format("%s-%s-%s.%s.xp!", preferSong.id, preferSong.br, preferSong.md5, preferSong.type);
                            File file = new File(cacheDir, fileName);
                            String str = preferSong.getMatchedJson().toString();
                            Utility.writeFile(file, str);
                        } else {
                            String start = String.format("%s-", preferSong.id);
                            String end = ".xp!";
                            File[] files = Utility.findFiles(cacheDir, start, end, null);
                            Utility.deleteFiles(files);
                        }
                    } catch (Throwable t) {
                        log("read 3rd party tips failed " + oldSong.id);
                        log(t);
                    }
                    return true;
                }
            }
        } catch (Throwable t) {
            log(t);
        }
        return false;
    }

    private static Song getSongByRemoteApi(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
            put("withHQ", "1");
        }};
        String raw = Http.post(XAPI + "song", map, true).getResponseText();
        JSONObject json = new JSONObject(raw);
        if (json.getInt("code") == 200) {
            return Song.parseFromOther(json.getJSONObject("data"));
        }
        return null;
    }

    private static Song getSongByRemoteApiEnhance(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
        }};
        String raw = Http.post(XAPI + "songx", map, true).getResponseText();
        JSONObject json = new JSONObject(raw);
        if (json.getInt("code") == 200) {
            return Song.parseFromOther(json.getJSONObject("data"));
        }
        return null;
    }

    private static Song getSongBy3rdApi(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
        }};
        String raw = Http.post(XAPI + "3rd/match", map, true).getResponseText();
        JSONObject json = new JSONObject(raw);
        if (json.getInt("code") == 200) {
            return Song.parseFromOther(json.getJSONObject("data"));
        }
        return null;
    }

    private static String convertPtoM(String pUrl) {
        if (pUrl != null && pUrl.startsWith("http://p")) {
            return "http://m2" + pUrl.substring(pUrl.indexOf('.'));
        }
        return null;
    }

    private static class DetailApi {
        final long songId;
        final int expectBitrate;

        int maxBr;
        JSONObject songsJson;
        List<String> seqList1;
        List<String> seqList2;


        DetailApi(long songId, int expectBr) throws Throwable {
            this.songId = songId;
            this.expectBitrate = expectBr;

            getDetailSongs();
            if (songsJson != null) {
                getMaxBr();
                arrangeSeq();
            }
        }

        private void arrangeSeq() {
            // find first index that br >= expect br
            int preferIndex = 0;

            List<Integer> values = new ArrayList<>(QUALITY_MAP.values());
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) >= expectBitrate) {
                    preferIndex = i;
                    break;
                }
            }

            List<String> keys = new ArrayList<>(QUALITY_MAP.keySet());

            seqList1 = new ArrayList<>(keys.size() - preferIndex);
            seqList2 = new ArrayList<>(preferIndex);
            for (int i = preferIndex; i < keys.size(); i++) {
                String key = keys.get(i);
                seqList1.add(key);
            }
            for (int i = preferIndex - 1; i >= 0; i--) {
                String key = keys.get(i);
                seqList2.add(key);
            }
        }

        private void getDetailSongs() throws Throwable {
            Map<String, String> map = new HashMap<>();
            JSONArray c = new JSONArray().put(new JSONObject().put("id", songId).put("v", 0));
            map.put("c", c.toString());

            String raw = Http.post(XAPI + "detail", map, true).getResponseText();
            JSONObject json = new JSONObject(raw);
            if (json.getInt("code") == 200) {
                songsJson = json.getJSONArray("songs").getJSONObject(0);
            }
        }

        void getMaxBr() {
            for (Map.Entry<String, Integer> entry : QUALITY_MAP.entrySet()) {
                String quality = entry.getKey();
                if (songsJson.has(quality) && !songsJson.isNull(quality)) {
                    int br = entry.getValue();
                    if (br > maxBr) {
                        maxBr = br;
                    }
                }
            }
        }

        Song find(int seq, int minBr) throws JSONException {
            List<String> seqList = seq == 1 ? seqList1 : seqList2;
            for (String quality : seqList) {
                int br = QUALITY_MAP.get(quality);
                if (br >= minBr && songsJson.has(quality) && !songsJson.isNull(quality)) {
                    Song song = Song.parseFromDetail(songsJson.getJSONObject(quality), songId, br);
                    if (song != null && song.url != null) {
                        String pUrl = song.url;
                        if (song.checkAccessible()) {
                            return song;
                        }

                        if (pUrl.startsWith("http://p")) {
                            song.url = convertPtoM(pUrl);
                            song.accessible = null;
                            if (song.checkAccessible()) {
                                return song;
                            }
                        }
                    }
                }
            }

            return null;
        }
    }
}

