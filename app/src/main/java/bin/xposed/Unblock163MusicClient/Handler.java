package bin.xposed.Unblock163MusicClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage.HttpEapi;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.E.showToast;
import static bin.xposed.Unblock163MusicClient.Settings.isUpgradeBitrateFrom3rdParty;
import static de.robv.android.xposed.XposedBridge.log;

public class Handler {
    private static final String XAPI = "http://xmusic.xmusic.top/xapi/v1/";
    private static final Date DOMAIN_EXPIRED_DATE = new GregorianCalendar(2019, 10 - 1, 1).getTime();
    private static final Pattern REX_PL = Pattern.compile("\"pl\":(?!999000)\\d+");
    private static final Pattern REX_DL = Pattern.compile("\"dl\":(?!999000)\\d+");
    private static final Pattern REX_SUBP = Pattern.compile("\"subp\":\\d+");
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

    public static String modifyPlayerOrDownloadApi(String originalContent, HttpEapi eapi, final String from) throws JSONException {
        JSONObject originalJson = new JSONObject(originalContent);

        int expectBitrate = 320000;
        try {
            expectBitrate = Integer.parseInt(eapi.getRequestData().get("br"));
        } catch (Throwable ignored) {
        }


        Object data = originalJson.get("data");
        if (data instanceof JSONObject) {
            JSONObject originalSong = (JSONObject) data;
            Process.process(originalSong, expectBitrate, from);
        } else {
            JSONArray originalSongs = (JSONArray) data;
            Set<Future> futureSet = new HashSet<>();
            final int finalExpectBitrate = expectBitrate;
            for (int i = 0; i < originalSongs.length(); i++) {
                final JSONObject songJson = originalSongs.getJSONObject(i);
                futureSet.add(handlerPool.submit(() -> Process.process(songJson, finalExpectBitrate, from)));
            }
            for (Future future : futureSet) {
                try {
                    future.get();
                } catch (Throwable t) {
                    log(t);
                }
            }
        }

        return originalJson.toString();
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
            if (Utils.isJSONValid(raw)) {
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
            if (Utils.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    private static Song getSongByRemoteApi(final long songId, final int expectBitrate) throws Throwable {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("id", String.valueOf(songId));
            put("br", String.valueOf(expectBitrate));
            put("withHQ", "1");
        }};
        String raw = Http.post(XAPI + "song", map, false).getResponseText();
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
        String raw = Http.post(XAPI + "songx", map, false).getResponseText();
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
        String raw = Http.post(XAPI + "match", map, false).getResponseText();
        JSONObject json = new JSONObject(raw);
        if (json.getInt("code") == 200) {
            return Song.parseFromOther(json.getJSONObject("data"));
        }
        return null;
    }

    static class Process {
        final JSONObject oldSongJson;
        final int expectBr;
        final String from;

        Song originalSong;
        Song preferSong;

        private Process(JSONObject oldSongJson, int expectBr, String from) {
            this.oldSongJson = oldSongJson;
            this.expectBr = expectBr > 320000 ? 320000 : expectBr;  // 忽略无损
            this.from = from;
        }

        static void process(JSONObject oldSongJson, int expectBr, String from) {
            // 异常在这方法里处理，防止影响下一曲
            try {
                new Process(oldSongJson, expectBr, from).doProcess();
            } catch (Throwable t) {
                log(t);
            }
        }

        boolean isNeedToEnhance() {
            boolean isNeed = false;

            if (preferSong.br < expectBr && originalSong.isFee() && !originalSong.isPayed()) {
                isNeed = true;
            }

            // 附加，能听就不去服务器取高音质
            if (preferSong.isAccessible()) {
                isNeed = false;
            }

            return isNeed;
        }

        boolean isNeedToGet3rdParty() {
            return !preferSong.isAccessible() || (preferSong.br < expectBr && isUpgradeBitrateFrom3rdParty());
        }

        boolean isNeedToProcess() {
            // 云盘
            if (originalSong.hasUserCloudFile()) {
                return false;
            }


            return !originalSong.isAccessible() // 听不了
                    || originalSong.isFreeTrialFile() // 试听
                    || originalSong.br < expectBr; // 音质不够
        }

        void doProcess() throws JSONException {

            originalSong = Song.parseFromOther(oldSongJson);

            if (originalSong == null) {
                return;
            }


            // ncm
            oldSongJson.put("flag", 0);


            // 排除不需要处理的
            if (!isNeedToProcess()) {
                return;
            }


            preferSong = originalSong;


            // enhance
            if (isNeedToEnhance()) {
                try {
                    Song tmp = getSongByRemoteApiEnhance(originalSong.id, expectBr);
                    preferSong = Song.getPreferSong(tmp, preferSong);
                } catch (Throwable t) {
                    log("songx api failed " + originalSong.id);
                    log(t);
                }
            }

            // 3rd
            if (isNeedToGet3rdParty()) {
                try {
                    Song tmp = getSongBy3rdApi(originalSong.id, expectBr);
                    preferSong = Song.getPreferSong(tmp, preferSong);
                } catch (Throwable t) {
                    log("3rd api failed " + originalSong.id);
                    log(t);
                }
            }


            if (preferSong.getPrefer() > originalSong.getPrefer()) {
                oldSongJson.put("br", preferSong.br)
                        .put("code", 200)
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
                        Utils.writeFile(file, str);
                    } else {
                        String start = String.format("%s-", preferSong.id);
                        String end = ".xp!";
                        File[] files = Utils.findFiles(cacheDir, start, end, null);
                        Utils.deleteFiles(files);
                    }
                } catch (Throwable t) {
                    log("read 3rd party tips failed " + originalSong.id);
                    log(t);
                }
            }
        }
    }


}

