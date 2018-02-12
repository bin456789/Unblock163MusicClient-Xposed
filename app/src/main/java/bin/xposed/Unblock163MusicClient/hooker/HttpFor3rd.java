package bin.xposed.Unblock163MusicClient.hooker;

import com.annimon.stream.Stream;

import java.lang.reflect.Method;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;

import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.ClassHelper.getFilteredClasses;
import static bin.xposed.Unblock163MusicClient.CloudMusicPackage.Mam.findMamClass;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.newInstance;

public class HttpFor3rd extends Hooker {

    @Override
    protected void howToHook() throws Throwable {

        if (CloudMusicPackage.HttpEapi.isUseOkHttp()) {
            hookMethod(CloudMusicPackage.Okhttp.RequestBuilder.getBuildMethod(), new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            CloudMusicPackage.Okhttp.RequestBuilder requestBuilder = new CloudMusicPackage.Okhttp.RequestBuilder(param.thisObject);
                            String url = requestBuilder.getUrl();

                            CloudMusicPackage.Okhttp.HeaderBuilder headersBuilder = requestBuilder.getHeaderBuilderWrapper();

                            if (url.contains("xiami.net")) {
                                headersBuilder.removeAll("Cookie");
                                headersBuilder.removeAll("Referer");
                                headersBuilder.set("User-Agent", "Android");

                                if (headersBuilder.get("Authorization") != null) {
                                    headersBuilder.set("Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                }


                            } else if (url.contains("musicway.cn")) {
                                headersBuilder.removeAll("Cookie");
                                headersBuilder.removeAll("Referer");
                                headersBuilder.set("User-Agent", "Android");

                                if (headersBuilder.get("Authorization") != null) {
                                    headersBuilder.set("Authorization", "Basic MzAwMDAwNDM0NzpCN0RDRTAxMjVGQzhGQkQwNzAzNUFBODNCMzA0OTZDRg==");
                                }


                            } else if (url.contains("qq.com")) {
                                headersBuilder.removeAll("Cookie");
                                headersBuilder.removeAll("Referer");
                                headersBuilder.set("User-Agent", "Android");

                                if (headersBuilder.get("Authorization") != null) {
                                    headersBuilder.removeAll("Authorization");
                                    String newUrlString = url.replace("http://", "http://gd.unicommusic.gtimg.com:8080/");
                                    requestBuilder.setUrl(newUrlString);
                                }
                            }
                        }
                    }
            );


            // 为qq音乐删除代理
            Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.[a-z]\\.[a-z]\\$[a-z]$");
            List<String> list = getFilteredClasses(pattern);
            Method selectMethod = Stream.of(list)
                    .map(s -> findClass(s, CloudMusicPackage.getClassLoader()))
                    .filter(c -> c.getSuperclass() == ProxySelector.class)
                    .findFirst().get().getDeclaredMethod("select", URI.class);

            hookMethod(selectMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String url = param.args[0].toString();
                    if (url.contains("gtimg.com")) {
                        param.setResult(null);
                    }
                }
            });


        } else {
            hookAllMethods(findClass(CloudMusicPackage.HttpEapi.getClazz().getPackage().getName() + ".a$1", CloudMusicPackage.getClassLoader()),
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

                                URI url = (URI) callMethod(originalHttpRequest, "getURI");
                                String host = url.getHost();

                                if (host.endsWith("xiami.net")) {
                                    callMethod(originalHttpRequest, "removeHeaders", "Cookie");
                                    callMethod(originalHttpRequest, "removeHeaders", "Referer");
                                    callMethod(originalHttpRequest, "setHeader", "User-Agent", "Android");

                                    if (callMethod(resultHttpRoute, "getProxyHost") != null) {
                                        callMethod(originalHttpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDU5MDpGRDYzQTdBNTM0NUMxMzFF");
                                    }

                                } else if (host.endsWith("musicway.cn")) {
                                    callMethod(originalHttpRequest, "removeHeaders", "Cookie");
                                    callMethod(originalHttpRequest, "removeHeaders", "Referer");
                                    callMethod(originalHttpRequest, "setHeader", "User-Agent", "Android");

                                    if (callMethod(resultHttpRoute, "getProxyHost") != null) {
                                        callMethod(originalHttpRequest, "setHeader", "Authorization", "Basic MzAwMDAwNDM0NzpCN0RDRTAxMjVGQzhGQkQwNzAzNUFBODNCMzA0OTZDRg==");
                                    }

                                } else if (host.endsWith("qq.com")) {
                                    callMethod(originalHttpRequest, "removeHeaders", "Cookie");
                                    callMethod(originalHttpRequest, "removeHeaders", "Referer");
                                    callMethod(originalHttpRequest, "setHeader", "User-Agent", "Android");

                                    if (callMethod(resultHttpRoute, "getProxyHost") != null) {
                                        callMethod(originalHttpRequest, "removeHeaders", "Authorization");
                                        callMethod(paramHttpRequest, "setURI", URI.create(url.toString().replace("http:/", "")));
                                        Object newHttpHost = newInstance(HttpHost, "gd.unicommusic.gtimg.com", 8080);
                                        Object newHttpRoute = newInstance(HttpRoute, newHttpHost, null, false);
                                        param.setResult(newHttpRoute);
                                    }
                                }
                            }
                        }
                    });
        }
    }
}






