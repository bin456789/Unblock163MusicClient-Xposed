package bin.xposed.Unblock163MusicClient;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;

import org.apache.http.params.CoreConnectionPNames;
import org.json.JSONObject;
import org.xbill.DNS.TextParseException;

import java.io.File;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import static de.robv.android.xposed.XposedBridge.invokeOriginalMethod;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
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
                    for (Method m : CloudMusicPackage.HttpEapi.getRawStringMethods())
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if ((!(param.getResult() instanceof String) && !(param.getResult() instanceof JSONObject)))
                                    return;

                                String original = param.getResult().toString();
                                if (TextUtils.isEmpty(original))
                                    return;

                                String path = new CloudMusicPackage.HttpEapi(param.thisObject).getPath();
                                String modified = null;

                                if (path.startsWith("song/enhance/player/url")) {
                                    modified = Handler.modifyPlayerOrDownloadApi(original, param.thisObject, "player");

                                } else if (path.startsWith("song/enhance/download/url")) {
                                    modified = Handler.modifyPlayerOrDownloadApi(original, param.thisObject, "download");

                                } else if (path.startsWith("v1/playlist/manipulate/tracks")) {
                                    modified = Handler.modifyPlaylistManipulateApi(original, param.thisObject);

                                } else if (path.startsWith("song/like")) {
                                    modified = Handler.modifyLike(original, param.thisObject);

                                } else if (path.startsWith("cloud/pub/v2")) {
                                    modified = Handler.modifyPub(original, param.thisObject);

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


                    // ctmus 如果有 Referer 则返回404
                    if (CloudMusicPackage.HttpEapi.isUseOkHttp()) {
                        Method addHeaderMethod = findMethodExact(CloudMusicPackage.HttpEapi.getClazz(), "a", String.class, String.class);
                        hookMethod(addHeaderMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if ("Referer".equals(param.args[0])) {
                                    String path = new CloudMusicPackage.HttpEapi(param.thisObject).getPath();
                                    String host = URI.create(path).getHost();
                                    if (host.contains("qq")
                                            || host.contains("xiami")
                                            || host.contains("alicdn")
                                            || host.contains("ctmus")) {
                                        param.setResult(param.thisObject);
                                    }
                                }
                            }
                        });
                    }


                    // save latest post data
                    try {
                        for (Member m : CloudMusicPackage.HttpEapi.getConstructor()) {
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
                                            httpEapi.setRequestMap((Map<String, String>) param.args[i]);
                                        }
                                    }
                                }
                            });
                        }
                    } catch (Throwable t) {
                        log(t);
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
                        if (CloudMusicPackage.getVersion().startsWith("3")) {
                            findAndHookMethod(CloudMusicPackage.NeteaseMusicUtils.getClazz(), "a", String.class, replaceMd5);
                        } else {
                            hookMethod(CloudMusicPackage.Transfer.getCalcMd5Method(), replaceMd5);
                        }
                    } catch (Throwable t) {
                        log(t);
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
                                                        invokeOriginalMethod(param.method, param.thisObject, param.args);
                                                    } catch (Throwable t) {
                                                        log(t);
                                                    }
                                                }
                                            });
                                            param.setResult(null);
                                        }
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            log(t);
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
                        log(t);
                    }

                    // 3rd party
                    try {
                        if (!CloudMusicPackage.HttpEapi.isUseOkHttp())
                            hookAllMethods(findClass(CloudMusicPackage.HttpEapi.getClazz().getPackage().getName() + ".a$1", lpparam.classLoader),
                                    "determineRoute", new XC_MethodHook() {
                                        final Class HttpHost = findMamClass(org.apache.http.HttpHost.class);
                                        final Class HttpGet = findMamClass(org.apache.http.client.methods.HttpGet.class);
                                        final Class HttpRoute = findMamClass(org.apache.http.conn.routing.HttpRoute.class);

                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                            Object paramHttpRequest = param.args[1];
                                            Object originalHttpRequest = callMethod(paramHttpRequest, "getOriginal");
                                            Object resultHttpRoute = param.getResult();

                                            if (HttpGet.isInstance(originalHttpRequest)) {
                                                // 防止 SocketTimeoutException 造成假死
                                                Object rangeHeader = callMethod(originalHttpRequest, "getFirstHeader", "Range");
                                                if (rangeHeader != null) {
                                                    Object requestParams = callMethod(callMethod(paramHttpRequest, "getParams"), "getRequestParams");
                                                    callMethod(requestParams, "setParameter", CoreConnectionPNames.SO_TIMEOUT, 5000);
                                                }

                                                URI url = (URI) callMethod(originalHttpRequest, "getURI");
                                                String host = url.getHost();

                                                if (host.endsWith("126.net") || host.endsWith("127.net") || host.endsWith("163.com"))
                                                    return;

                                                // cookie 处理
                                                // 避免发送网易cookie到xiami, qq ...
                                                callMethod(originalHttpRequest, "removeHeaders", "Cookie");
                                                callMethod(originalHttpRequest, "removeHeaders", "Referer");

                                                // 避免开通联通流量包后听不了
                                                if (callMethod(resultHttpRoute, "getProxyHost") != null) {
                                                    if (host.contains("xiami") || host.contains("alicdn")) {
                                                        callMethod(originalHttpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                                    } else if (host.contains("qq")) {
                                                        callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                                        callMethod(paramHttpRequest, "setURI", URI.create(url.toString().replace("http:/", "")));
                                                        Object newHttpHost = newInstance(HttpHost, "gd.unicommusic.gtimg.com", 8080);
                                                        Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                                        param.setResult(newHttpRoute);
                                                    } else if (host.contains("imusicapp")) {
                                                        // do nothing for now
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
                                    });
                    } catch (Throwable t) {
                        log(t);
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
                        log(t);
                    }

                    if (CloudMusicPackage.getVersion().compareTo("3.4") >= 0) {
                        try {
                            findAndHookMethod(CloudMusicPackage.MusicInfo.getClazz(), "getThirdTitle", boolean.class, set3rdStr);
                        } catch (Throwable t) {
                            log(t);
                        }
                    }


                    // oversea mode
                    if (!CloudMusicPackage.HttpEapi.isUseOkHttp() && Settings.isOverseaModeEnabled()) {
                        try {
                            Set<String> prefixes = new HashSet<>();
                            prefixes.add("");
                            prefixes.add(CloudMusicPackage.Mam.getPrefix());

                            for (String prefix : prefixes) {
                                final Class AbstractHttpClient = findClass(prefix + org.apache.http.impl.client.AbstractHttpClient.class.getName(), lpparam.classLoader);
                                final Class HttpUriRequest = findClass(prefix + org.apache.http.client.methods.HttpUriRequest.class.getName(), lpparam.classLoader);
                                final Class HttpRequestBase = findClass(prefix + org.apache.http.client.methods.HttpRequestBase.class.getName(), lpparam.classLoader);

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
                            }
                        } catch (Throwable t) {
                            log(t);
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
                            log(t);
                        }
                    }

                    // toast
                    if (false) {
                        for (Method method : CloudMusicPackage.E.getSuspectedShowToastMethods()) {
                            hookMethod(method, new XC_MethodReplacement() {
                                long lastShowToastTime = 0;

                                @Override
                                protected Object replaceHookedMethod(final MethodHookParam param) throws Throwable {
                                    if ("歌曲已存在".equals(param.args[0]) && !Utility.isCallFromMyself())
                                        return null;

                                    long toastLength = 3500;
                                    long between = System.currentTimeMillis() - lastShowToastTime;
                                    long shouldWait = between > toastLength ? 0 : toastLength - between;
                                    lastShowToastTime = System.currentTimeMillis() + shouldWait;

                                    if (shouldWait > 0) {
                                        Utility.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    invokeOriginalMethod(param.method, param.thisObject, param.args);
                                                } catch (Throwable t) {
                                                    log(t);
                                                }
                                            }
                                        }, shouldWait);
                                    } else {
                                        invokeOriginalMethod(param.method, param.thisObject, param.args);
                                    }
                                    return null;
                                }
                            });
                        }
                    }
                }
            });
        }
    }
}


