package bin.xposed.Unblock163MusicClient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.AbstractHttpClient;
import org.xbill.DNS.TextParseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Main implements IXposedHookLoadPackage {

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // make current module activated
            findAndHookMethod("bin.xposed.Unblock163MusicClient.ui.SettingsActivity", lpparam.classLoader,
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }


        if (lpparam.packageName.equals("com.netease.cloudmusic")) {
            CloundMusicPackage.init(lpparam);

            findAndHookMethod(CloundMusicPackage.Http.Class, "a", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Exception {
                    String url = (String) CloundMusicPackage.HttpBase.Url.get(param.thisObject);
                    if (url.startsWith("http://music.163.com/eapi/")) {
                        String path = url.replace("http://music.163.com", "");
                        if (path.startsWith("/eapi/batch")
                                || path.startsWith("/eapi/album/privilege")
                                || path.startsWith("/eapi/artist/privilege")
                                || path.startsWith("/eapi/playlist/privilege")
                                || path.startsWith("/eapi/song/enhance/privilege")
                                || path.startsWith("/eapi/v1/artist")
                                || path.startsWith("/eapi/v1/album")
                                || path.startsWith("/eapi/v1/discovery/new/songs")
                                || path.startsWith("/eapi/v1/discovery/recommend/songs")
                                || path.startsWith("/eapi/v1/play/record")
                                || path.startsWith("/eapi/v1/search/get")
                                || path.startsWith("/eapi/v3/playlist/detail")
                                || path.startsWith("/eapi/v3/song/detail")) {
                            String original = (String) param.getResult();
                            String modified = Handler.modifyByRegex(original);
                            param.setResult(modified);

                            if (path.startsWith("/eapi/batch")) {
                                Handler.cacheLikePlaylistId(original);
                            }


                        } else if (path.startsWith("/eapi/song/enhance/player/url")) {
                            String modified = Handler.modifyPlayerOrDownloadApi(path, (String) param.getResult(), "player");
                            param.setResult(modified);

                        } else if (path.startsWith("/eapi/song/enhance/download/url")) {
                            String modified = Handler.modifyPlayerOrDownloadApi(path, (String) param.getResult(), "download");
                            param.setResult(modified);

                        } else if (path.startsWith("/eapi/v1/playlist/manipulate/tracks")) {
                            String modified = Handler.modifyPlaylistManipulateApi((String) param.getResult());
                            param.setResult(modified);

                        } else if (path.startsWith("/eapi/song/like")) {
                            String modified = Handler.modifyLike(path, (String) param.getResult());
                            param.setResult(modified);
                        }
                    }
                }
            });


            // save the post data about adding song to playlist
            hookMethod(CloundMusicPackage.Http.Constructor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[0].equals("v1/playlist/manipulate/tracks")) {
                        Handler.playlistManipulateDataMap = (Map) param.args[1];
                    }
                }
            });


            // calc md5
            findAndHookMethod(CloundMusicPackage.NeteaseMusicUtils.Class, "a", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String path = ((String) param.args[0]);
                    if (path.contains("/netease/cloudmusic/Cache/Download/")) {
                        String md5 = Utility.getLastPartOfString(path, "/");
                        param.setResult(md5);
                    }
                }
            });


            // oversea mode
            if (Settings.isOverseaModeEnabled()) {
                Oversea.init();
                findAndHookMethod("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader,
                        "onCreate", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Context context = (Context) param.thisObject;
                                BroadcastReceiver settingChangedReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        Oversea.setDnsServer(intent.getStringExtra(Settings.DNS_SERVER_KEY));
                                    }
                                };
                                IntentFilter settingChangedFilter = new IntentFilter(Settings.SETTING_CHANGED);
                                context.registerReceiver(settingChangedReceiver, settingChangedFilter);
                            }
                        });


                // noinspection deprecation
                findAndHookMethod(AbstractHttpClient.class, "execute", HttpUriRequest.class, new XC_MethodHook() {
                    @SuppressWarnings("deprecation")
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws TextParseException, URISyntaxException {
                        if (param.args[0] instanceof HttpGet) {
                            HttpGet httpGet = (HttpGet) param.args[0];
                            URI uri = httpGet.getURI();
                            String path = uri.getPath();

                            // only process self-generate url
                            // original music url contains ymusic string, while self-generate url not contain.
                            if (path.endsWith(".mp3") && !path.contains("/ymusic/")) {
                                String host = uri.getHost();
                                String ip = Oversea.getIpByHost(host);
                                if (ip != null) {
                                    URI newUrl = new URI(String.format("http://%s%s", ip, path));
                                    httpGet.setURI(newUrl);
                                    httpGet.setHeader("Host", host);
                                    param.args[0] = httpGet;
                                }
                            }
                        }
                    }
                });
            }
        }
    }
}
