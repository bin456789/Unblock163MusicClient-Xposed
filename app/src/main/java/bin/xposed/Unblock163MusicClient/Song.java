package bin.xposed.Unblock163MusicClient;

import org.json.JSONException;
import org.json.JSONObject;

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

    Song(JSONObject songJson) {
        if (songJson != null) {
            id = songJson.optLong("id");
            code = songJson.optInt("code");
            br = songJson.optInt("br");
            fee = songJson.optInt("fee");
            gain = (float) songJson.optDouble("gain");
            md5 = optString(songJson, "md5");
            payed = songJson.optInt("payed");
            size = songJson.optLong("size");
            type = optString(songJson, "type");
            uf = songJson.optJSONObject("uf");
            url = optString(songJson, "url");
            matchedPlatform = optString(songJson, "matchedPlatform");
            matchedSongName = optString(songJson, "matchedSongName");
            matchedArtistName = optString(songJson, "matchedArtistName");
        }
    }


    boolean checkAccessable() {
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
                t.printStackTrace();
                return false;
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
