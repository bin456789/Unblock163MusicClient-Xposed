package bin.xposed.Unblock163MusicClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;

public class Main implements IXposedHookLoadPackage {

    @SuppressWarnings("deprecation")
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // make current module activated
            findAndHookMethod("bin.xposed.Unblock163MusicClient.ui.SettingsActivity", lpparam.classLoader,
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }


        if (lpparam.packageName.equals("com.netease.cloudmusic")) {
            CloundMusicPackage.init(lpparam);


            findAndHookMethod(CloundMusicPackage.HttpEapi.CLASS, "a", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String url = new CloundMusicPackage.HttpBase(param.thisObject).getUrl();
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
            hookMethod(findConstructorExact(CloundMusicPackage.HttpEapi.CLASS, String.class, Map.class), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[0].equals("v1/playlist/manipulate/tracks")) {
                        Handler.playlistManipulateDataMap = (Map) param.args[1];
                    }
                }
            });


            // calc md5
            findAndHookMethod(CloundMusicPackage.NeteaseMusicUtils.CLASS, "a", String.class, new XC_MethodHook() {
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
                findAndHookMethod(CloundMusicPackage.org2.apache.http.impl.client.AbstractHttpClient.CLASS,
                        "execute", CloundMusicPackage.org2.apache.http.client.methods.HttpUriRequest.CLASS, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws URISyntaxException {
                                if (CloundMusicPackage.org2.apache.http.client.methods.HttpRequestBase.CLASS.isInstance(param.args[0])) {
                                    Object object = param.args[0];
                                    URI uri = (URI) callMethod(object, "getURI");
                                    String host = uri.getHost();
                                    String path = uri.getPath();
                                    // solve server ip point to 1.1.1.1
                                    if (host.equals("m2.music.126.net")) {
                                        String ip = Utility.getIpByHost(host);
                                        if (ip != null) {
                                            URI newUrl = new URI(String.format("http://%s%s", ip, path));
                                            callMethod(object, "setURI", newUrl);
                                            callMethod(object, "setHeader", "Host", host);
                                        }
                                    }
                                }
                            }
                        }
                );
            }


            // xiami
            findAndHookMethod(CloundMusicPackage.HttpEapi.CLASS.getPackage().getName() + ".a", lpparam.classLoader, "b", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    callMethod(param.getResult(), "addRequestInterceptor", Proxy.newProxyInstance(lpparam.classLoader, new Class[]{CloundMusicPackage.org2.apache.http.HttpRequestInterceptor.CLASS}, new InvocationHandler() {
                                @Override
                                public Object invoke(Object o, Method method, Object[] args) throws Throwable {
                                    if (method.getName().equals("process")) {
                                        Object httpRequest = args[0];

                                        Object originalHttpRequest = callMethod(httpRequest, "getOriginal");
                                        URI originalURI = (URI) callMethod(originalHttpRequest, "getURI");
                                        if (originalURI.getHost().endsWith("xiami.com")) {

                                            // 避免发送网易cookie到虾米
                                            callMethod(httpRequest, "removeHeader", callMethod(httpRequest, "getFirstHeader", "Cookie"));
                                            callMethod(httpRequest, "removeHeaders", "Referer");

                                            // 避免开通联通流量包后听不了
                                            if ((boolean) callMethod(httpRequest, "containsHeader", "Authorization")) {
                                                return callMethod(httpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                            }
                                        }
                                        return null;
                                    }
                                    return method.invoke(o, args);
                                }
                            })
                    );
                }
            });
        }
    }
}


