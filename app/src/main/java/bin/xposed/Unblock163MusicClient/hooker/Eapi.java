package bin.xposed.Unblock163MusicClient.hooker;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import bin.xposed.Unblock163MusicClient.BuildConfig;
import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Handler;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import static bin.xposed.Unblock163MusicClient.Utils.log;

public class Eapi extends Hooker {

    @Override
    protected void howToHook() throws Throwable {

        // response
        for (Method m : CloudMusicPackage.HttpEapi.getRawStringMethodList()) {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    if ((!(param.getResult() instanceof String) && !(param.getResult() instanceof JSONObject))) {
                        return;
                    }

                    String original = param.getResult().toString();
                    if (TextUtils.isEmpty(original)) {
                        return;
                    }

                    CloudMusicPackage.HttpEapi eapi = new CloudMusicPackage.HttpEapi(param.thisObject);
                    Uri uri = eapi.getUri();
                    String path = uri.getPath().substring("/eapi/".length());
                    String modified = null;

                    if ("song/enhance/player/url".equals(path) || "song/enhance/player/url/v1".equals(path)) {
                        modified = Handler.modifyPlayerOrDownloadApi(original, eapi, "player");

                    } else if ("song/enhance/download/url".equals(path)) {
                        modified = Handler.modifyPlayerOrDownloadApi(original, eapi, "download");

                    } else if ("v1/playlist/manipulate/tracks".equals(path)) {
                        modified = Handler.modifyPlaylistManipulateApi(original, eapi);

                    } else if ("song/like".equals(path)) {
                        modified = Handler.modifyLike(original, eapi);

                    } else if ("cloud/pub/v2".equals(path)) {
                        modified = Handler.modifyPub(original, eapi);

                    } else {
                        List<String> segments = uri.getPathSegments();
                        if (segments.contains("batch")
                                || segments.contains("album")
                                || segments.contains("artist")
                                || segments.contains("play")
                                || segments.contains("playlist")
                                || segments.contains("radio")
                                || segments.contains("song")
                                || segments.contains("songs")
                                || segments.contains("search")) {
                            modified = Handler.modifyByRegex(original);
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        log("------------------------");
                        log("path: " + path);
                        Map data = new CloudMusicPackage.HttpEapi(param.thisObject).getRequestData();
                        log("data: " + (data != null ? data.toString() : ""));
                        log("original: " + original);
                        boolean isModified = modified != null && !modified.equals(original);
                        log("modified: " + (isModified ? modified : ""));
                        log("------------------------");
                    }

                    if (modified != null) {
                        param.setResult(param.getResult() instanceof JSONObject ? new JSONObject(modified) : modified);
                    }
                }
            });
        }
    }
}





