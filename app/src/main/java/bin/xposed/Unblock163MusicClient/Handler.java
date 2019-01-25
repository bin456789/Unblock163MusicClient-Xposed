package bin.xposed.Unblock163MusicClient;

import android.content.pm.PackageManager;

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

    public static String modifyPlayerOrDownloadApi(String originalContent, HttpEapi eapi, final String from) throws JSONException, IllegalAccessException, PackageManager.NameNotFoundException {
        JSONObject originalJson = new JSONObject(originalContent);

        int expectBitrate = Integer.parseInt(eapi.getRequestData().get("br"));

        Object data = originalJson.get("data");
        if (data instanceof JSONObject) {
            JSONObject originalSong = (JSONObject) data;
            processSong(originalSong, expectBitrate, from);
        } else {
            JSONArray originalSongs = (JSONArray) data;
            Set<Future> futureSet = new HashSet<>();
            final int finalExpectBitrate = expectBitrate;
            for (int i = 0; i < originalSongs.length(); i++) {
                final JSONObject songJson = originalSongs.getJSONObject(i);
                futureSet.add(handlerPool.submit(() -> processSong(songJson, finalExpectBitrate, from)));
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

    private static void processSong(JSONObject oldSongJson, int expectBr, String from) {
        // 异常在这方法里处理，防止影响下一曲
        if (oldSongJson == null) {
            return;
        }

        try {
            Song oldSong = Song.parseFromOther(oldSongJson);

            // ncm
            oldSongJson.put("flag", 0);


            // 云盘
            if (oldSong.uf != null) {
                return;
            }

            // 原始 mp3 可以连接
            if (oldSong.br > 0 && oldSong.url != null) {
                oldSong.accessible = true;
            }


            // 如果本来可以播放，且没有设置强制320k，直接返回
            if (oldSong.accessible && !Settings.isTryHighBitrate()) {
                return;
            }

            // 忽略无损
            if (expectBr > 320000) {
                expectBr = 320000;
            }

            // 要处理的歌曲
            if (oldSong.url == null
                    || (oldSong.fee != 0 && oldSong.payed == 0 && oldSong.br < expectBr)) {

                Song preferSong = oldSong;
                int maxBr = 320000;


                // enhance
                if (oldSong.url == null
                        && oldSong.fee != 0
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


                if (preferSong.getPrefer() > oldSong.getPrefer()) {
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
                        log("read 3rd party tips failed " + oldSong.id);
                        log(t);
                    }
                }
            }
        } catch (Throwable t) {
            log(t);
        }
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


}

