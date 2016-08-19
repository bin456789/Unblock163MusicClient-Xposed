package bin.xposed.Unblock163MusicClient;

import org.json.JSONObject;

public class Song {

    public long id;
    public int code;
    public int br;
    public int fee;
    public float gain;
    public String md5;
    public int payed;
    public long size;
    public String type;
    public JSONObject uf;
    public String url;


    public Song(JSONObject songJson) {
        if (songJson != null) {
            id = songJson.optLong("id");
            code = songJson.optInt("code");
            br = songJson.optInt("br");
            fee = songJson.optInt("fee");
            gain = (float) songJson.optDouble("gain");
            md5 = fixNull(songJson.optString("md5"));
            payed = songJson.optInt("payed");
            size = songJson.optLong("size");
            type = fixNull(songJson.optString("type"));
            uf = songJson.optJSONObject("uf");
            url = fixNull(songJson.optString("url"));
        }
    }

    public String fixNull(String s) {
        return "null".equals(s) ? null : s;
    }

    public boolean checkAccessable() {
        if (url != null) {
            try {
                Http h = Http.headByGet(url, false);
                if (h.responseCode >= 200 && h.responseCode < 400) {
                    size = h.contentLength; // fix music length for xiami
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
