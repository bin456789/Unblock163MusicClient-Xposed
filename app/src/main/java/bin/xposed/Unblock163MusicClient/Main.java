package bin.xposed.Unblock163MusicClient;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Main implements IXposedHookLoadPackage {

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals("com.netease.cloudmusic")) {
            Utility.Init(lpparam.classLoader);

            findAndHookMethod("com.netease.cloudmusic.utils.u", lpparam.classLoader,
                    "i", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Exception {
                            String full_url = (String) Utility.FIELD_utils_c.get(param.thisObject);
                            String url = full_url.replace("http://music.163.com", "");
                            if (url.startsWith("/eapi/batch")
                                    || url.startsWith("/eapi/cloudsearch/pc")
                                    || url.startsWith("/eapi/v1/artist")
                                    || url.startsWith("/eapi/v1/album")
                                    || url.startsWith("/eapi/v1/play/record")
                                    || url.startsWith("/eapi/v1/search/get")
                                    || url.startsWith("/eapi/v3/playlist/detail")
                                    || url.startsWith("/eapi/v3/song/detail")
                                    || url.startsWith("/eapi/v3/song/enhance/privilege")) {
                                String modified = Utility.ModifyDetailApi((String) param.getResult());
                                param.setResult(modified);
                            } else if (url.startsWith("/eapi/song/enhance/player/url")) {
                                String modified = Utility.ModifyPlayerApi(url, (String) param.getResult());
                                param.setResult(modified);
                            }
                        }
                    });
        }
    }
}
