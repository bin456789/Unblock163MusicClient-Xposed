package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.AbstractHttpClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.findMamClass;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;

public class Main implements IXposedHookLoadPackage {

    @SuppressWarnings("deprecation")
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // make current module activated
            findAndHookMethod("bin.xposed.Unblock163MusicClient.ui.SettingsActivity", lpparam.classLoader,
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }


        if (lpparam.packageName.equals("com.netease.cloudmusic")) {
            findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    CloudMusicPackage.init(lpparam);

                    findAndHookMethod(CloudMusicPackage.HttpEapi.CLASS, "a", String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String url = CloudMusicPackage.HttpEapi.getUrl(param.thisObject);
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
                                    String modified = Handler.modifyLike((String) param.getResult());
                                    param.setResult(modified);
                                }
                            }
                        }
                    });


                    // save latest post data
                    hookMethod(findConstructorExact(CloudMusicPackage.HttpEapi.CLASS, String.class, Map.class, String.class, boolean.class), new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String api = (String) param.args[0];
                            if (api.startsWith("v1/playlist/manipulate/tracks")) {
                                Handler.LAST_PLAYLIST_MANIPULATE_MAP = (Map) param.args[1];
                            } else if (api.startsWith("song/like")) {
                                Handler.LAST_LIKE_STRING = (String) param.args[0];
                            }
                        }
                    });


                    // calc md5
                    findAndHookMethod(CloudMusicPackage.NeteaseMusicUtils.CLASS, "a", String.class, new XC_MethodHook() {
                        Pattern REX_MD5 = Pattern.compile("[a-f0-9]{32}", Pattern.CASE_INSENSITIVE);

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String path = (String) param.args[0];
                            Matcher matcher = REX_MD5.matcher(path);
                            if (matcher.find())
                                param.setResult(matcher.group());
                        }
                    });


                    // oversea mode
                    if (Settings.isOverseaModeEnabled()) {
                        findAndHookMethod(findMamClass(AbstractHttpClient.class), "execute", findMamClass(HttpUriRequest.class), new XC_MethodHook() {
                            Class HttpRequestBase = findMamClass(HttpRequestBase.class); // included GET, POST and etc.

                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws URISyntaxException {
                                if (HttpRequestBase.isInstance(param.args[0])) {
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
                        });
                    }


                    // xiami
                    findAndHookMethod(CloudMusicPackage.HttpEapi.CLASS.getPackage().getName() + ".a", lpparam.classLoader, "b", new XC_MethodHook() {
                        Class HttpRequestInterceptor = findMamClass(HttpRequestInterceptor.class);
                        Method addRequestInterceptor = findMethodExact(findMamClass(AbstractHttpClient.class), "addRequestInterceptor", HttpRequestInterceptor);

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            addRequestInterceptor.invoke(param.getResult(), Proxy.newProxyInstance(lpparam.classLoader, new Class[]{HttpRequestInterceptor}, new InvocationHandler() {
                                @Override
                                public Object invoke(Object o, Method method, Object[] args) throws Throwable {
                                    if (method.getName().equals("process")) {
                                        Object httpRequest = args[0];

                                        Object originalHttpRequest = callMethod(httpRequest, "getOriginal");
                                        URI originalURI = (URI) callMethod(originalHttpRequest, "getURI");
                                        if (!(originalURI.getHost().endsWith("126.net") || originalURI.getHost().endsWith("163.com"))) {
                                            // 避免发送网易cookie到第三方
                                            callMethod(httpRequest, "removeHeader", callMethod(httpRequest, "getFirstHeader", "Cookie"));
                                            callMethod(httpRequest, "removeHeaders", "Referer");

                                            if (originalURI.getHost().endsWith("xiami.com")) {
                                                // 避免开通联通流量包后听不了
                                                if ((boolean) callMethod(httpRequest, "containsHeader", "Authorization"))
                                                    return callMethod(httpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                            }
                                        }

                                        return null;
                                    }
                                    return method.invoke(o, args);
                                }
                            }));
                        }
                    });
                }
            });
        }
    }
}


