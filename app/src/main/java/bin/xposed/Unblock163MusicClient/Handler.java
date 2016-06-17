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
import java.util.LinkedList;
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

    protected static Map playlistManipulateDataMap;
    protected static long likePlaylistId = 0;
    protected static List<Long> processingList = new LinkedList<>();


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
        return originalContent;
    }

    protected static String modifyPlaylistManipulateApi(String originalContent) throws JSONException, IOException {
        JSONObject originalJson = new JSONObject(originalContent);
        int code = originalJson.getInt("code");

        if (code != 200) {
            @SuppressWarnings("unchecked")
            String postData = Utility.serialData(playlistManipulateDataMap);
            return Http.post("http://music.xposed.ml/xapi/v1/manipulate", postData, true);
        }
        return originalContent;
    }

    protected static String modifyLike(String path, String originalContent) throws JSONException, IOException, URISyntaxException {
        if (likePlaylistId != 0) {
            JSONObject originalJson = new JSONObject(originalContent);
            int code = originalJson.getInt("code");

            if (code != 200) {
                String postData = new URI(path).getQuery() + "&playlistId=" + likePlaylistId;
                return Http.post("http://music.xposed.ml/xapi/v1/like", postData, true);
            }
        }
        return originalContent;
    }

    protected static boolean processSong(JSONObject originalSong, int expectBitrate, String from) {
        int originalCode = originalSong.optInt("code");
        int originalBr = originalSong.optInt("br");
        String originalUrl = originalSong.optString("url");
        if (originalUrl.equals("null")) originalUrl = null;

        if (originalCode != 200
                || originalBr != expectBitrate
                || originalUrl == null) {
            long songId = originalSong.optLong("id");

            // 避免 stackoverflow
            if (!processingList.contains(songId)) {
                processingList.add(songId);

                JSONObject newSong = null;
                String newSongFrom = null;

                // remote api
                {
                    JSONObject tmp = Handler.getSongByRemoteApi(songId, expectBitrate);
                    // 加try，假如remote挂了也可以继续往下走
                    try {
                        if (tmp != null
                                && tmp.getInt("code") == 200
                                && Http.head(tmp.getString("url"), false) == 200) {
                            newSong = tmp;
                            newSongFrom = "remote";
                        }
                    } catch (Exception ignored) {
                    }
                }


                // 只用作remote的补充（即remote获取不到才用）
                if (newSong == null) {
                    JSONObject tmp = getSongByDetailApi(songId, expectBitrate);
                    try {
                        if (tmp != null
                                && tmp.getLong("fid") != 0
                                && Http.head(generateUrl(tmp.getLong("fid")), false) == 200) {
                            newSong = tmp;
                            newSongFrom = "detail";
                        }
                    } catch (Exception ignored) {
                    }
                }

                // player download 互换，不用测试head
                // 这两个 api 不能获得付费歌曲，下架只能获得128k。所以如果原来有url的（即音质不同或者是电台）就不需要
                // 可能引起stackoverflow, 用processingList解决
                if (newSong == null && originalUrl == null) {
                    if (from.equals("player")) {
                        JSONObject tmp = Handler.getSongByDownloadApi(songId, expectBitrate);
                        if (tmp != null) {
                            newSong = tmp;
                            newSongFrom = "download";
                        }
                    } else {
                        JSONObject tmp = Handler.getSongByPlayerApi(songId, expectBitrate);
                        if (tmp != null) {
                            newSong = tmp;
                            newSongFrom = "player";
                        }
                    }
                }

                if (newSong != null) {
                    try {
                        if ("detail".equals(newSongFrom)) {
                            long fid = newSong.getLong("fid");
                            String url = generateUrl(newSong.getLong("fid"));
                            originalSong.put("br", newSong.getInt("br"))
                                    .put("code", 200)
                                    .put("gain", newSong.getDouble("vd"))
                                    .put("md5", String.format("%32s", fid).replace(" ", "0")) // 使用fid避免重复
                                    .put("size", newSong.getLong("size"))
                                    .put("type", Utility.getLastPartOfString(url, "."))
                                    .put("url", url);
                        } else {
                            originalSong.put("br", newSong.getInt("br"))
                                    .put("code", 200)
                                    .put("gain", newSong.getDouble("gain"))
                                    .put("md5", newSong.getString("md5"))
                                    .put("size", newSong.getLong("size"))
                                    .put("type", newSong.getString("type"))
                                    .put("url", newSong.getString("url"));
                        }
                        processingList.remove(songId);
                        return true;
                    } catch (JSONException e) {
                        processingList.remove(songId);
                        return false;
                    }
                }
            }
        }
        return false;
    }

    protected static JSONObject getSongByRemoteApi(long songId, int expectBitrate) {
        try {
            String raw = Http.post("http://music.xposed.ml/xapi/v1/song", String.format("id=%s&br=%s", songId, expectBitrate), true);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    protected static JSONObject getSongByPlayerApi(long songId, int expectBitrate) {
        try {
            String ids = URLEncoder.encode(String.format("[\"%s_0\"]", songId), "UTF-8");
            String url = String.format("song/enhance/player/url?br=%s&ids=%s", expectBitrate, ids);
            String raw = CloundMusicPackage.postEapi(url, null);
            return new JSONObject(raw).getJSONArray("data").getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    protected static JSONObject getSongByDownloadApi(long songId, int expectBitrate) {
        String url = String.format("song/enhance/download/url?br=%s&id=%s_0", expectBitrate, songId);
        try {
            String raw = CloundMusicPackage.postEapi(url, null);
            return new JSONObject(raw).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    protected static void cacheLikePlaylistId(String originalContent) throws JSONException {
        if (likePlaylistId == 0 && originalContent.contains("\"/api/user/playlist\"")) {
            likePlaylistId = new JSONObject(originalContent)
                    .getJSONObject("/api/user/playlist")
                    .getJSONArray("playlist")
                    .getJSONObject(0).getLong("id");
        }
    }

    protected static String generateUrl(long fid) {
        return (String) XposedHelpers.callStaticMethod(CloundMusicPackage.NeteaseMusicUtils.Class, "a", fid);
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
        String page = CloundMusicPackage.postEapi("v3/song/detail", map);
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

