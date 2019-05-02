package bin.xposed.Unblock163MusicClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage.HttpEapi;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.E.showToast;
import static bin.xposed.Unblock163MusicClient.Settings.isUpgradeBitrateFrom3rdParty;
import static bin.xposed.Unblock163MusicClient.Utils.log;

public class Handler {
    private static final String XAPI = "https://xmusic.xmusic.top/xapi/v1/";
    private static final Pattern REX_PL = Pattern.compile("\"pl\":(?!999000)\\d+");
    private static final Pattern REX_DL = Pattern.compile("\"dl\":(?!999000)\\d+");
    private static final Pattern REX_SUBP = Pattern.compile("\"subp\":\\d+");
    private static final ExecutorService handlerPool = Executors.newCachedThreadPool();

    public static String modifyByRegex(String originalContent) {
        originalContent = REX_PL.matcher(originalContent).replaceAll("\"pl\":320000");
        originalContent = REX_DL.matcher(originalContent).replaceAll("\"dl\":320000");
        originalContent = REX_SUBP.matcher(originalContent).replaceAll("\"subp\":1");
        return originalContent;
    }

    public static String modifyPlayerOrDownloadApi(String originalContent, HttpEapi eapi, String from) throws JSONException {
        JSONObject originalJson = new JSONObject(originalContent);

        int expectBitrate;
        try {
            expectBitrate = Integer.parseInt(eapi.getRequestData().get("br"));
        } catch (Throwable ignored) {
            expectBitrate = 320000;
        }


        Object data = originalJson.get("data");
        if (data instanceof JSONObject) {
            JSONObject originalSong = (JSONObject) data;
            Process.process(originalSong, expectBitrate, from);
        } else {
            JSONArray originalSongs = (JSONArray) data;
            List<Future> futures = new ArrayList<>();
            final int finalExpectBitrate = expectBitrate;
            for (int i = 0; i < originalSongs.length(); i++) {
                JSONObject songJson = originalSongs.getJSONObject(i);
                futures.add(handlerPool.submit(() -> Process.process(songJson, finalExpectBitrate, from)));
            }
            for (Future future : futures) {
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
            Map<String, String> map = eapi.getRequestData();
            String raw = Http.post(XAPI + "pub", map, true).getResponseText();
            if (Utils.isJSONValid(raw)) {
                return raw;
            }
        }

        return originalContent;
    }

    private static List<Song> getSongByRemoteApi(long songId, int expectBitrate, String apiName) {
        try {
            Map<String, String> map = new LinkedHashMap<String, String>() {{
                put("id", String.valueOf(songId));
                put("br", String.valueOf(expectBitrate));
            }};

            String raw = Http.post(XAPI + apiName, map, false).getResponseText();

            JSONObject json = new JSONObject(raw);
            if (json.getInt("code") == 200) {
                Object obj = json.get("data");
                if (obj instanceof JSONObject) {
                    return Collections.singletonList(Song.parse((JSONObject) obj));
                } else if (obj instanceof JSONArray) {
                    return Song.parse((JSONArray) obj);
                }
            }
        } catch (Throwable t) {
            log(apiName + " api failed " + songId, t);
        }
        return new ArrayList<>();
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

        boolean isNeedToGetP1() {
            return !preferSong.isAccessible() || preferSong.getBr() < expectBr;
        }

        boolean isNeedToEnhance() {
            boolean isNeed = false;

            if (preferSong.getBr() < expectBr && originalSong.isFee() && !originalSong.isPayed()) {
                isNeed = true;
            }

            // 附加，能听就不去服务器取高音质
            if (preferSong.isAccessible()) {
                isNeed = false;
            }

            return isNeed;
        }

        boolean isNeedToGet3rdParty() {
            return !preferSong.isAccessible() || (preferSong.getBr() < expectBr && isUpgradeBitrateFrom3rdParty());
        }

        boolean isNeedToProcess() {
            // 云盘
            if (originalSong.hasUserCloudFile()) {
                return false;
            }


            return !originalSong.isAccessible() // 听不了
                    || originalSong.isFreeTrialFile() // 试听
                    || originalSong.getBr() < expectBr; // 音质不够
        }

        private void addRC(Callable condition, Callable<List<Song>> getSongByApi) {
            try {
                if ((boolean) condition.call()) {
                    List<Song> songList = new ArrayList<>();
                    songList.add(preferSong);
                    songList.addAll(getSongByApi.call());

                    Song tmp = Song.getPreferSong(songList);
                    if (tmp != null) {
                        preferSong = tmp;
                    }
                }
            } catch (Throwable t) {
                log(t);
            }
        }

        void doProcess() throws JSONException {

            originalSong = Song.parse(oldSongJson);

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

            addRC(this::isNeedToGetP1, () -> getSongByRemoteApi(originalSong.getId(), expectBr, "songs"));
            addRC(this::isNeedToEnhance, () -> getSongByRemoteApi(originalSong.getId(), expectBr, "songx"));
            addRC(this::isNeedToGet3rdParty, () -> getSongByRemoteApi(originalSong.getId(), expectBr, "match"));

            if (preferSong != originalSong) {
                oldSongJson.put("br", preferSong.getBr())
                        .put("code", 200)
                        .put("md5", preferSong.getMd5())
                        .put("size", preferSong.getSize())
                        .put("type", preferSong.getType())
                        .put("url", preferSong.getUrl());

                if (!preferSong.isFreeTrialFile()) {
                    oldSongJson.put("freeTrialInfo", null);
                }

                try {
                    File cacheDir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                    if (preferSong.is3rdPartySong()) {
                        String fileName = String.format("%s-%s-%s.%s.xp!", preferSong.getId(), preferSong.getBr(), preferSong.getMd5(), preferSong.getType());
                        File file = new File(cacheDir, fileName);
                        String str = preferSong.getMatchedJson().toString();
                        Utils.writeFile(file, str);
                    } else {
                        String start = String.format("%s-", preferSong.getId());
                        String end = ".xp!";
                        File[] files = Utils.findFiles(cacheDir, start, end, null);
                        Utils.deleteFiles(files);
                    }
                } catch (Throwable t) {
                    log("read 3rd party tips failed " + originalSong.getId(), t);
                }
            }
        }
    }


}

