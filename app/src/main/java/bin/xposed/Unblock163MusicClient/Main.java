package bin.xposed.Unblock163MusicClient;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.ui.SettingsActivity;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.findMamClass;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;

public class Main implements IXposedHookLoadPackage {

    @SuppressWarnings("deprecation")
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // make current module activated
            findAndHookMethod(findClass(SettingsActivity.class.getCanonicalName(), lpparam.classLoader),
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }


        if (lpparam.packageName.equals(CloudMusicPackage.PACKAGE_NAME)) {
            findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    CloudMusicPackage.init(lpparam);


                    // main
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
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String api = (String) param.args[0];
                            if (api.startsWith("v1/playlist/manipulate/tracks")) {
                                Handler.LAST_PLAYLIST_MANIPULATE_MAP = (Map<String, String>) param.args[1];
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


                    // dislike confirm
                    if (Settings.isConfirmDislikeEnabled()) {
                        final Class MusicInfo = findClass("com.netease.cloudmusic.meta.MusicInfo", lpparam.classLoader);
                        final Class UIAA = findClass("com.netease.cloudmusic.ui.a.a", lpparam.classLoader);
                        final Resources resources = Utility.getModuleResources();
                        final String question = resources.getString(R.string.dislike_confirm_question);
                        final String confirm = resources.getString(R.string.confirm);

                        hookMethod(CloudMusicPackage.DislikeConfirm.getOnClickMethod(), new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                                Activity ch = (Activity) getObjectField(param.thisObject, "a");
                                Object musicInfo = CloudMusicPackage.DislikeConfirm.getMusicInfo(ch);
                                long musicId = CloudMusicPackage.DislikeConfirm.getMusicId(ch, musicInfo);
                                boolean isStarred = (boolean) callStaticMethod(MusicInfo, "isStarred", musicId);
                                if (isStarred) {
                                    callStaticMethod(UIAA, "a", ch, question, confirm, new View.OnClickListener() {
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
                    }


                    final Class HttpHost = findMamClass(org.apache.http.HttpHost.class);
                    final Class HttpRequest = findMamClass(org.apache.http.HttpRequest.class);
                    final Class HttpContext = findMamClass(org.apache.http.protocol.HttpContext.class);
                    final Class HttpRequestBase = findMamClass(org.apache.http.client.methods.HttpRequestBase.class);
                    final Class HttpRoute = findMamClass(org.apache.http.conn.routing.HttpRoute.class);

                    // 3rd party
                    findAndHookMethod(CloudMusicPackage.HttpEapi.CLASS.getPackage().getName() + ".a$1", lpparam.classLoader,
                            "determineRoute", HttpHost, HttpRequest, HttpContext, new XC_MethodHook() {
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
                                            if (host.endsWith("music.xposed.tk")) {
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
                                                } else if (host.endsWith("music.qq.com")) {
                                                    callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                                    callMethod(paramHttpRequest, "setURI", new URI(url.toString().replace("http:/", "")));
                                                    Object newHttpHost = newInstance(HttpHost, "gd.unicommusic.gtimg.com", 8080);
                                                    Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                                    param.setResult(newHttpRoute);
                                                } else if (host.endsWith("imusicapp.cn")) {
                                                    // do nothing for now
                                                } else {
                                                    // remove proxy
                                                    callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                                    callMethod(paramHttpRequest, "setURI", new URI(url.getPath()));
                                                    Object newHttpHost = newInstance(HttpHost, host);
                                                    Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                                    param.setResult(newHttpRoute);
                                                }
                                            }
                                        }
                                    }
                                }
                            });


                    // 3rd party source tips
                    hookMethod(CloudMusicPackage.BottomSheetDialog.getSetTitleMethod(), new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            CharSequence title = null;
                            int titleIndex = -1;
                            ArrayList list = null;

                            for (int i = param.args.length - 1; i >= 0; i--) {
                                if (param.args[i] instanceof ArrayList) {
                                    list = (ArrayList) param.args[i];
                                    titleIndex = i - 1;
                                    title = (CharSequence) param.args[titleIndex];
                                    break;
                                }
                            }

                            if (list != null) {
                                Object musicInfo = getObjectField(list.get(0), CloudMusicPackage.BottomSheetDialog.getMusicInfoField());
                                long songId = (long) callMethod(musicInfo, "getId");
                                Song song = Handler.THIRD_PARTY_MUSIC_INFO.get(songId);
                                if (song != null && song.matchedPlatform != null) {
                                    param.args[titleIndex] = String.format("%s (来自%s：%s - %s)",
                                            title == null ? "" : title,
                                            song.matchedPlatform,
                                            song.matchedArtistName,
                                            song.matchedSongName);
                                }
                            }
                        }
                    });


                    // oversea mode
                    if (Settings.isOverseaModeEnabled()) {
                        Class AbstractHttpClient = findMamClass(org.apache.http.impl.client.AbstractHttpClient.class);
                        Class HttpUriRequest = findMamClass(org.apache.http.client.methods.HttpUriRequest.class);

                        findAndHookMethod(AbstractHttpClient, "execute", HttpUriRequest, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws URISyntaxException {
                                if (HttpRequestBase.isInstance(param.args[0])) {
                                    Object httpRequestBase = param.args[0];
                                    URI uri = (URI) callMethod(httpRequestBase, "getURI");
                                    String host = uri.getHost();
                                    String path = uri.getPath();
                                    // solve server ip point to 1.1.1.1
                                    if (host.equals("m2.music.126.net")) {
                                        String ip = Utility.getIpByHost(host);
                                        if (ip != null) {
                                            String newUrl = String.format("http://%s%s", ip, path);
                                            callMethod(httpRequestBase, "setURI", newUrl);
                                            callMethod(httpRequestBase, "setHeader", "Host", host);
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            });
        }
    }
}


