package bin.xposed.Unblock163MusicClient;

import com.annimon.stream.Stream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static bin.xposed.Unblock163MusicClient.Utils.log;
import static bin.xposed.Unblock163MusicClient.Utils.optString;

class Song {
    long id;
    int code;
    int br;
    int fee;
    String md5;
    int payed;
    long size;
    String type;
    JSONObject uf;
    String url;
    JSONObject freeTrialInfo;

    String matchedPlatform;
    String matchedSongName;
    String matchedArtistName;
    boolean matchedDuration;

    private Boolean accessible;

    static Song parseFromOther(JSONObject songJson) {
        if (songJson == null) {
            return null;
        }

        Song song = new Song();
        song.id = songJson.optLong("id");
        song.code = songJson.optInt("code");
        song.br = songJson.optInt("br");
        song.fee = songJson.optInt("fee");
        song.md5 = optString(songJson, "md5");
        song.payed = songJson.optInt("payed");
        song.size = songJson.optLong("size");
        song.type = optString(songJson, "type");
        song.uf = songJson.optJSONObject("uf");
        song.url = optString(songJson, "url");
        song.freeTrialInfo = songJson.optJSONObject("freeTrialInfo");
        song.parseMatchInfo(songJson);
        return song;
    }

    static List<Song> parseFromOther(JSONArray songsJson) {
        if (songsJson == null) {
            return null;
        }
        ArrayList<Song> songs = new ArrayList<>();
        for (int i = 0; i < songsJson.length(); i++) {
            JSONObject obj = songsJson.optJSONObject(i);
            if (obj != null) {
                songs.add(parseFromOther(obj));
            }
        }
        return songs;
    }


    static Song parseFromDetail(JSONObject songJson, long id, int br) {
        Song song = new Song();
        long fid = songJson.optLong("fid");
        if (fid > 0) {
            song.id = id;
            song.br = br;
            song.md5 = String.format(Locale.getDefault(), "%032d", fid);
            song.size = songJson.optLong("size");
            song.type = "mp3";
            song.url = CloudMusicPackage.NeteaseMusicUtils.generateUrl(fid);
            return song;
        }
        return null;
    }

    static Song getPreferSong(Song... songs) {
        return Stream.of(songs).sortBy(s -> -s.getPrefer(false))
                .filter(Song::isAccessible)
                .findFirst().orElse(null);

    }

    void parseMatchInfo(JSONObject songJson) {
        matchedPlatform = optString(songJson, "matchedPlatform");
        matchedSongName = optString(songJson, "matchedSongName");
        matchedArtistName = optString(songJson, "matchedArtistName");
        matchedDuration = songJson.optBoolean("matchedDuration");
    }

    boolean isAccessible() {
        if (accessible != null) {
            return accessible;
        }

        accessible = false;
        if (url != null) {
            try {
                Http h = Http.headByGet(url, false);
                if (h.getResponseCode() == 200 || h.getResponseCode() == 206) {
                    url = h.getFinalLocation();
                    size = h.getContentLength(); // re-calc music size for 3rd party url
                    accessible = true;
                }
            } catch (Throwable t) {
                log(String.format("%s %s %s %s", t.getMessage(), id, br, url), t);
            }
        }
        return accessible;
    }

    boolean is3rdPartySong() {
        return matchedPlatform != null;
    }

    boolean hasUserCloudFile() {
        return uf != null;
    }


    boolean isFreeTrialFile() {
        return freeTrialInfo != null;
    }


    boolean isPayed() {
        return payed != 0;
    }

    boolean isFee() {
        return fee != 0;
    }


    private boolean is3rdMatchedDuration() {
        return matchedDuration;
    }

    JSONObject getMatchedJson() throws JSONException {
        return new JSONObject()
                .put("matchedPlatform", matchedPlatform)
                .put("matchedSongName", matchedSongName)
                .put("matchedArtistName", matchedArtistName);
    }

    int getPrefer() {
        return getPrefer(true);
    }

    int getPrefer(boolean checkAccessible) {
        if (checkAccessible && !isAccessible()) {
            return 0;
        }

        int prefer = br;
        if (is3rdPartySong()) {
            prefer--;
            if (!is3rdMatchedDuration()) {
                prefer = 1;
            }
        }

        return prefer;
    }
}
