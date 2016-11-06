package bin.xposed.Unblock163MusicClient;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableString;
import android.text.style.TextAppearanceSpan;
import android.view.View;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.ui.SettingsActivity;
import dalvik.system.DexFile;
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
            if (false) {
                // 另一种方法，但一直占用内存，所以不用
                final List<String> allClasses = new ArrayList<>();
                findAndHookMethod(findClass("android.support.multidex.MultiDex", lpparam.classLoader),
                        "install", Context.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Application app = (Application) param.args[0];
                                DexFile dexfile = new DexFile(app.getApplicationInfo().sourceDir);
                                allClasses.addAll(Collections.list(dexfile.entries()));
                                CloudMusicPackage.version = app.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
                            }
                        });

                findAndHookMethod(findClass("android.support.multidex.MultiDex", lpparam.classLoader),
                        "installSecondaryDexes", ClassLoader.class, File.class, List.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                @SuppressWarnings("unchecked")
                                List<File> list = (List<File>) param.args[2];
                                for (File file : list) {
                                    String path = file.getAbsolutePath();
                                    DexFile dexfile = DexFile.loadDex(path, path + ".tmp", Context.MODE_PRIVATE);
                                    allClasses.addAll(Collections.list(dexfile.entries()));
                                }
                            }
                        });
            }

            findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    CloudMusicPackage.init(lpparam, (Application) param.thisObject);

                    // main
                    findAndHookMethod(CloudMusicPackage.HttpEapi.CLASS, "a", String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = CloudMusicPackage.HttpEapi.getPath(param.thisObject);
                            String original = (String) param.getResult();
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

                            }

                            if (modified != null) {
                                param.setResult(modified);
                            }
                        }
                    });


                    // save latest post data
                    try {
                        findAndHookConstructor(CloudMusicPackage.HttpEapi.CLASS, String.class, Map.class, String.class, boolean.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                CloudMusicPackage.HttpEapi.setArgs(param.thisObject, param.args);
                            }
                        });
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }


                    // calc md5
                    try {
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
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    // dislike confirm
                    if (Settings.isConfirmDislikeEnabled()) {
                        try {
                            hookMethod(CloudMusicPackage.PlayerActivity.getLikeBottomOnClickMethod(), new XC_MethodHook() {
                                final Resources resources = Utility.getModuleResources();
                                final String question = resources.getString(R.string.dislike_confirm_question);
                                final String confirm = resources.getString(R.string.confirm);

                                @Override
                                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                                    Activity playerActivity = (Activity) getObjectField(param.thisObject, "a");
                                    Object musicInfo = CloudMusicPackage.PlayerActivity.getMusicInfo(playerActivity);
                                    long musicId = (long) callMethod(musicInfo, "getMatchedMusicId");
                                    boolean isStarred = (boolean) callStaticMethod(CloudMusicPackage.MusicInfo.CLASS, "isStarred", musicId);
                                    if (isStarred) {
                                        callStaticMethod(CloudMusicPackage.UIAA.CLASS, "a", playerActivity, question, confirm, new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                try {
                                                    XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        });
                                        param.setResult(null);
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }

                    // off-shelf tips in quality-selected window
                    try {
                        hookMethod(CloudMusicPackage.UIAA.getQualityBoxMethod(), new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object playerActivity = param.args[0];
                                Object musicInfo = CloudMusicPackage.PlayerActivity.getMusicInfo(playerActivity);
                                if (!(boolean) callMethod(musicInfo, "hasCopyRight")) { // 需要打开滑稽模式，即不替换st
                                    SpannableString ssOld = (SpannableString) param.args[1];
                                    SpannableString ssNew = new SpannableString(ssOld.toString().replace("付费独享", "下架歌曲"));
                                    TextAppearanceSpan[] textAppearanceSpen = ssOld.getSpans(0, ssOld.length(), TextAppearanceSpan.class);
                                    for (TextAppearanceSpan span : textAppearanceSpen) {
                                        ssNew.setSpan(span, ssOld.getSpanStart(span), ssOld.getSpanEnd(span), ssOld.getSpanFlags(span));
                                    }
                                    param.args[1] = ssNew;
                                }
                            }
                        });
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    // 3rd party
                    try {
                        hookAllMethods(findClass(CloudMusicPackage.HttpEapi.CLASS.getPackage().getName() + ".a$1", lpparam.classLoader),
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
                                                    if (host.endsWith("xiami.com")) {
                                                        callMethod(originalHttpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                                    } else if (host.endsWith("qq.com")) {
                                                        callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                                        callMethod(paramHttpRequest, "setURI", URI.create(url.toString().replace("http:/", "")));
                                                        Object newHttpHost = newInstance(HttpHost, "gd.unicommusic.gtimg.com", 8080);
                                                        Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                                        param.setResult(newHttpRoute);
                                                    } else if (host.endsWith("imusicapp.cn")) {
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
                                    }
                                });
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    // 3rd party source tips
                    XC_MethodHook set3rdStr = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object musicInfo = param.thisObject;
                            String s = CloudMusicPackage.MusicInfo.get3rdSourceString(musicInfo);
                            if (s != null) {
                                param.setResult(CloudMusicPackage.MusicInfo.get3rdSourceString(musicInfo));
                            }
                        }
                    };


                    try {
                        // getThirdTitle 3.4+ 才有
                        findAndHookMethod(CloudMusicPackage.MusicInfo.CLASS, "getThirdTitle", boolean.class, set3rdStr);
                    } catch (Throwable ignored) {
                    }
                    try {
                        findAndHookMethod(CloudMusicPackage.MusicInfo.CLASS, "getAppendCopyRight", set3rdStr);
                    } catch (Throwable ignored) {
                    }

                    // oversea mode
                    if (Settings.isOverseaModeEnabled()) {
                        try {
                            final Class AbstractHttpClient = findMamClass(org.apache.http.impl.client.AbstractHttpClient.class);
                            final Class HttpUriRequest = findMamClass(org.apache.http.client.methods.HttpUriRequest.class);
                            final Class HttpRequestBase = findMamClass(org.apache.http.client.methods.HttpRequestBase.class);

                            findAndHookMethod(AbstractHttpClient, "execute", HttpUriRequest, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws URISyntaxException {
                                    if (HttpRequestBase.isInstance(param.args[0])) {
                                        Object httpRequestBase = param.args[0];
                                        URI uri = (URI) callMethod(httpRequestBase, "getURI");
                                        String host = uri.getHost();
                                        // solve server ip point to 1.1.1.1
                                        if ("m2.music.126.net".equals(host)) {
                                            String ip = Utility.getIpByHost(host);
                                            if (ip != null) {
                                                URI newUrl = new URI(uri.getScheme(), uri.getUserInfo(), ip, uri.getPort(), uri.getRawPath(), uri.getRawQuery(), uri.getRawFragment());
                                                callMethod(httpRequestBase, "setURI", newUrl);
                                                callMethod(httpRequestBase, "setHeader", "Host", host);
                                            }
                                        }
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            });
        }
    }
}


