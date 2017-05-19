package bin.xposed.Unblock163MusicClient;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;

import org.xbill.DNS.TextParseException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.ui.SettingsActivity;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.Mam.findMamClass;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;

@SuppressWarnings("deprecation")
public class Main implements IXposedHookLoadPackage {

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (Handler.isDomainExpired())
            return;

        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // make current module activated
            findAndHookMethod(findClass(SettingsActivity.class.getName(), lpparam.classLoader),
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }


        if (lpparam.packageName.equals(CloudMusicPackage.PACKAGE_NAME)) {
            findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    CloudMusicPackage.init((Context) param.thisObject);

                    // main
                    findAndHookMethod(CloudMusicPackage.HttpEapi.getClazz(), "a", String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String original = (String) param.getResult();
                            if (TextUtils.isEmpty(original))
                                return;

                            String path = new CloudMusicPackage.HttpEapi(param.thisObject).getPath();
                            String modified = null;
                            if (path.startsWith("batch")
                                    || path.startsWith("album/privilege")
                                    || path.startsWith("artist/privilege")
                                    || path.startsWith("playlist/privilege")
                                    || path.startsWith("song/enhance/privilege")
                                    || path.startsWith("v1/artist")
                                    || path.startsWith("v1/album")
                                    || path.startsWith("v1/discovery/new/songs")
                                    || path.startsWith("v1/discovery/recommend/songs")
                                    || path.startsWith("v1/play/record")
                                    || path.startsWith("v1/search/get")
                                    || path.startsWith("v3/playlist/detail")
                                    || path.startsWith("v3/song/detail")) {
                                modified = Handler.modifyByRegex(original);

                                if (path.startsWith("batch")) {
                                    Handler.cacheLikePlaylistId(original, param.thisObject);
                                }

                            } else if (path.startsWith("song/enhance/player/url")) {
                                modified = Handler.modifyPlayerOrDownloadApi(original, param.thisObject, "player");

                            } else if (path.startsWith("song/enhance/download/url")) {
                                modified = Handler.modifyPlayerOrDownloadApi(original, param.thisObject, "download");

                            } else if (path.startsWith("v1/playlist/manipulate/tracks")) {
                                modified = Handler.modifyPlaylistManipulateApi(original, param.thisObject);

                            } else if (path.startsWith("song/like")) {
                                modified = Handler.modifyLike(original, param.thisObject);

                            } else if (path.startsWith("cloud/pub/v2")) {
                                modified = Handler.modifyPub(original, param.thisObject);

                            }

                            if (modified != null) {
                                param.setResult(modified);
                            }
                        }
                    });


                    // save latest post data
                    try {
                        findAndHookConstructor(CloudMusicPackage.HttpEapi.getClazz(), String.class, Map.class, String.class, boolean.class, new XC_MethodHook() {
                            @SuppressWarnings("unchecked")
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                CloudMusicPackage.HttpEapi httpEapi = new CloudMusicPackage.HttpEapi(param.thisObject);
                                httpEapi.setPath((String) param.args[0]);
                                httpEapi.setRequestMap((Map<String, String>) param.args[1]);
                            }
                        });
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }


                    // replace md5
                    XC_MethodHook replaceMd5 = new XC_MethodHook() {
                        final Pattern REX_MD5 = Pattern.compile("[a-f0-9]{32}", Pattern.CASE_INSENSITIVE);

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String path = param.args[0] instanceof File
                                    ? ((File) param.args[0]).getPath()
                                    : param.args[0].toString();

                            Matcher matcher = REX_MD5.matcher(path);
                            if (matcher.find())
                                param.setResult(matcher.group());
                        }
                    };

                    try {
                        if (CloudMusicPackage.version.startsWith("3")) {
                            findAndHookMethod(CloudMusicPackage.NeteaseMusicUtils.getClazz(), "a", String.class, replaceMd5);
                        } else {
                            hookMethod(CloudMusicPackage.Transfer.getCalcMd5Method(), replaceMd5);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }

                    // dislike confirm
                    if (Settings.isConfirmDislikeEnabled()) {
                        try {
                            hookMethod(CloudMusicPackage.PlayerActivity.getLikeButtonOnClickMethod(), new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                                    Activity currentActivity = (Activity) getObjectField(param.thisObject, "a");
                                    if (CloudMusicPackage.PlayerActivity.getClazz().isInstance(currentActivity)) {
                                        Object musicInfo = new CloudMusicPackage.PlayerActivity(currentActivity).getMusicInfo();
                                        long musicId = new CloudMusicPackage.MusicInfo(musicInfo).getMatchedMusicId();
                                        boolean isStarred = CloudMusicPackage.MusicInfo.isStarred(musicId);
                                        if (isStarred) {
                                            callStaticMethod(CloudMusicPackage.UIAA.getClazz(), "a", currentActivity, "确定不再收藏此歌曲吗？", "不再收藏", new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    try {
                                                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                                                    } catch (Throwable t) {
                                                        XposedBridge.log(t);
                                                    }
                                                }
                                            });
                                            param.setResult(null);
                                        }
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }

                    // off-shelf tips in quality-selected window
                    try {
                        hookMethod(CloudMusicPackage.UIAA.getQualityBoxMethod(), new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object currentActivity = param.args[0];
                                if (CloudMusicPackage.PlayerActivity.getClazz().isInstance(currentActivity)) {
                                    Object musicInfo = new CloudMusicPackage.PlayerActivity(currentActivity).getMusicInfo();
                                    if (!(boolean) callMethod(musicInfo, "hasCopyRight")) {
                                        SpannableString ssOld = (SpannableString) param.args[1];
                                        SpannableString ssNew = new SpannableString(ssOld.toString().replace("付费独享", "下架歌曲"));
                                        Object[] spans = ssOld.getSpans(0, ssOld.length(), Object.class);
                                        for (Object span : spans) {
                                            ssNew.setSpan(span, ssOld.getSpanStart(span), ssOld.getSpanEnd(span), ssOld.getSpanFlags(span));
                                        }
                                        param.args[1] = ssNew;
                                    }
                                }
                            }
                        });
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }

                    // 3rd party
                    try {
                        hookAllMethods(findClass(CloudMusicPackage.HttpEapi.getClazz().getPackage().getName() + ".a$1", lpparam.classLoader),
                                "determineRoute", new XC_MethodHook() {
                                    final Class HttpHost = findMamClass(org.apache.http.HttpHost.class);
                                    final Class HttpRequestBase = findMamClass(org.apache.http.client.methods.HttpRequestBase.class);
                                    final Class HttpRoute = findMamClass(org.apache.http.conn.routing.HttpRoute.class);
                                    final String xapiHost = URI.create(Handler.XAPI).getHost();

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        Object paramHttpRequest = param.args[1];
                                        Object originalHttpRequest = callMethod(paramHttpRequest, "getOriginal");
                                        Object resultHttpRoute = param.getResult();

                                        if (HttpRequestBase.isInstance(originalHttpRequest)) {
                                            URI url = (URI) callMethod(originalHttpRequest, "getURI");
                                            String host = url.getHost();


                                            if (!(host.endsWith("126.net") || host.endsWith("163.com"))) {

                                                // 需要发送cookie到自己的服务器
                                                if (host.endsWith(xapiHost)) {
                                                    //client.getParams().setParameter(HttpMethodParams.SINGLE_COOKIE_HEADER, true);
                                                    String cookieString = String.format("modver=%s;%s", BuildConfig.VERSION_NAME, CloudMusicPackage.HttpEapi.getDefaultCookie());
                                                    callMethod(originalHttpRequest, "setHeader", "Cookie", cookieString);
                                                } else {
                                                    // 避免发送网易cookie到xiami, qq ...
                                                    callMethod(originalHttpRequest, "removeHeaders", "Cookie");
                                                    callMethod(originalHttpRequest, "removeHeaders", "Referer");
                                                }

                                                // 避免开通联通流量包后听不了
                                                if (callMethod(resultHttpRoute, "getProxyHost") != null) {
                                                    if (host.endsWith("xiami.com") || (host.endsWith("alicdn.com"))) {
                                                        callMethod(originalHttpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                                    } else if (host.endsWith("qq.com")) {
                                                        callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                                        callMethod(paramHttpRequest, "setURI", URI.create(url.toString().replace("http:/", "")));
                                                        Object newHttpHost = newInstance(HttpHost, "gd.unicommusic.gtimg.com", 8080);
                                                        Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                                        param.setResult(newHttpRoute);
                                                    } else if (host.endsWith("imusicapp.cn")) {
                                                        // imusicapp.cn 可能会引起 SocketTimeoutException 假死
                                                        Object requestParams = callMethod(callMethod(paramHttpRequest, "getParams"), "getRequestParams");
                                                        callMethod(requestParams, "setParameter", "http.socket.timeout", 3000);
                                                    } else {
                                                        // remove proxy
                                                        callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                                        callMethod(paramHttpRequest, "setURI", URI.create(url.getPath()));
                                                        Object newHttpHost = newInstance(HttpHost, host);
                                                        Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                                        param.setResult(newHttpRoute);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                });
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }

                    // 3rd party source tips
                    XC_MethodHook set3rdStr = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object musicInfo = param.thisObject;
                            String thirdTitle = new CloudMusicPackage.MusicInfo(musicInfo).get3rdSourceString();
                            if (thirdTitle != null) {
                                String original = (String) param.getResult();
                                boolean needToAppendSpace = "getThirdTitle".equals(param.method.getName());
                                if (needToAppendSpace) {
                                    thirdTitle = " " + thirdTitle;
                                }
                                String modified = TextUtils.isEmpty(original) ? thirdTitle : original + thirdTitle;
                                param.setResult(modified);
                            }
                        }
                    };


                    try {
                        findAndHookMethod(CloudMusicPackage.MusicInfo.getClazz(), "getAppendCopyRight", set3rdStr);
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }

                    if (CloudMusicPackage.version.compareTo("3.4") >= 0) {
                        try {
                            findAndHookMethod(CloudMusicPackage.MusicInfo.getClazz(), "getThirdTitle", boolean.class, set3rdStr);
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }


                    // oversea mode
                    if (Settings.isOverseaModeEnabled()) {
                        try {
                            final Class AbstractHttpClient = findMamClass(org.apache.http.impl.client.AbstractHttpClient.class);
                            final Class HttpUriRequest = findMamClass(org.apache.http.client.methods.HttpUriRequest.class);
                            final Class HttpRequestBase = findMamClass(org.apache.http.client.methods.HttpRequestBase.class);

                            findAndHookMethod(AbstractHttpClient, "execute", HttpUriRequest, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws URISyntaxException, TextParseException, UnknownHostException {
                                    if (HttpRequestBase.isInstance(param.args[0])) {
                                        Object httpRequestBase = param.args[0];
                                        URI uri = (URI) callMethod(httpRequestBase, "getURI");
                                        String host = uri.getHost();
                                        // solve server ip point to 1.1.1.1
                                        if ("m2.music.126.net".equals(host)) {
                                            String ip = Utility.getIpByHost(host);
                                            URI newUrl = new URI(uri.getScheme(), uri.getUserInfo(), ip, uri.getPort(), uri.getRawPath(), uri.getRawQuery(), uri.getRawFragment());
                                            callMethod(httpRequestBase, "setURI", newUrl);
                                            callMethod(httpRequestBase, "setHeader", "Host", host);
                                        }
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }

                    if (Settings.isPreventGray()) {
                        try {
                            findAndHookMethod(CloudMusicPackage.MusicInfo.getClazz(), "hasCopyRight", new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!Utility.isCallFromMyself()) {
                                        param.setResult(true);
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                }
            });
        }
    }
}


