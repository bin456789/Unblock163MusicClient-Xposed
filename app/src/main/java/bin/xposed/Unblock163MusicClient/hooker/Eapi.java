package bin.xposed.Unblock163MusicClient.hooker;

import android.text.TextUtils;

import org.json.JSONObject;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Handler;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import static de.robv.android.xposed.XposedBridge.hookMethod;

public class Eapi extends Hooker {

    @Override
    protected void howToHook() throws Throwable {

        // request
        for (Member m : CloudMusicPackage.HttpEapi.getConstructorList()) {
            hookMethod(m, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    CloudMusicPackage.HttpEapi httpEapi = new CloudMusicPackage.HttpEapi(param.thisObject);
                    if (param.args[0] instanceof String) {
                        httpEapi.setPath((String) param.args[0]);
                    }
                    for (int i = 0; i < param.args.length && i < 2; i++) {
                        if (param.args[i] instanceof Map) {
                            httpEapi.setRequestForm((Map<String, String>) param.args[i]);
                        }
                    }
                }
            });
        }


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
                    String path = eapi.getPath();
                    String modified = null;

                    if (path.startsWith("song/enhance/player/url")) {
                        modified = Handler.modifyPlayerOrDownloadApi(original, eapi, "player");

                    } else if (path.startsWith("song/enhance/download/url")) {
                        modified = Handler.modifyPlayerOrDownloadApi(original, eapi, "download");

                    } else if (path.startsWith("v1/playlist/manipulate/tracks")) {
                        modified = Handler.modifyPlaylistManipulateApi(original, eapi);

                    } else if (path.startsWith("song/like")) {
                        modified = Handler.modifyLike(original, eapi);

                    } else if (path.startsWith("cloud/pub/v2")) {
                        modified = Handler.modifyPub(original, eapi);

                    } else if (path.contains("batch")
                            || path.contains("album")
                            || path.contains("artist")
                            || path.contains("play")
                            || path.contains("radio")
                            || path.contains("song")
                            || path.contains("search")) {
                        modified = Handler.modifyByRegex(original);
                    }

                    if (modified != null) {
                        param.setResult(param.getResult() instanceof JSONObject ? new JSONObject(modified) : modified);
                    }
                }
            });
        }
    }
}





