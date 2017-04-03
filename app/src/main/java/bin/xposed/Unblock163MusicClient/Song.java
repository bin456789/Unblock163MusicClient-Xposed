package bin.xposed.Unblock163MusicClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

import static bin.xposed.Unblock163MusicClient.Utility.optString;

class Song {
    long id;
    int code;
    int br;
    int fee;
    float gain;
    String md5;
    int payed;
    long size;
    String type;
    JSONObject uf;
    String url;

    String matchedPlatform;
    String matchedSongName;
    String matchedArtistName;
    boolean matchedDuration;

    static Song parseFromOther(JSONObject songJson) {
        Song song = new Song();
        song.id = songJson.optLong("id");
        song.code = songJson.optInt("code");
        song.br = songJson.optInt("br");
        song.fee = songJson.optInt("fee");
        song.gain = (float) songJson.optDouble("gain");
        song.md5 = optString(songJson, "md5");
        song.payed = songJson.optInt("payed");
        song.size = songJson.optLong("size");
        song.type = optString(songJson, "type");
        song.uf = songJson.optJSONObject("uf");
        song.url = optString(songJson, "url");
        song.parseMatchInfo(songJson);
        return song;
    }

    static Song parseFromDetail(JSONObject songJson, int br) {
        Song song = new Song();
        long fid = songJson.optLong("fid");
        if (fid > 0) {
            song.br = br;
            song.gain = (float) songJson.optDouble("vd");
            song.md5 = String.format(Locale.getDefault(), "%032d", fid);
            song.size = songJson.optLong("size");
            song.type = "mp3";
            song.url = CloudMusicPackage.NeteaseMusicUtils.generateUrl(fid);
            return song;
        } else
            throw new RuntimeException("fid invalid");
    }

    void parseMatchInfo(JSONObject songJson) {
        matchedPlatform = optString(songJson, "matchedPlatform");
        matchedSongName = optString(songJson, "matchedSongName");
        matchedArtistName = optString(songJson, "matchedArtistName");
        matchedDuration = songJson.optBoolean("matchedDuration");
    }

    boolean checkAccessible() {
        String tmpUrl = url;
        if (tmpUrl != null) {
            try {
                Http http = Http.headByGet(tmpUrl);
                int responseCode = http.getResponseCode();
                // manually handle redirection
                while (responseCode == 301 || responseCode == 302) {
                    tmpUrl = http.getRedirectLocation();
                    http = Http.headByGet(tmpUrl);
                    responseCode = http.getResponseCode();
                }
                if (responseCode >= 200 && responseCode < 400) {
                    size = http.getFileSize(); // re-calc music size for 3rd party url
                    url = tmpUrl;
                    return true;
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
        return false;
    }

    boolean isMatchedSong() {
        return matchedPlatform != null;
    }

    JSONObject getMatchedJson() throws JSONException {
        return new JSONObject()
                .put("matchedPlatform", matchedPlatform)
                .put("matchedSongName", matchedSongName)
                .put("matchedArtistName", matchedArtistName);
    }
}
