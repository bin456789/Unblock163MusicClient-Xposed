package bin.xposed.Unblock163MusicClient.hooker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.Settings;
import bin.xposed.Unblock163MusicClient.Utils;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Oversea extends Hooker {
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    @Override
    protected void howToHook() throws Throwable {
        if (Settings.isOverseaModeEnabled()) {
            String[] methods = {"getByName", "getAllByName"};
            for (String method : methods) {
                findAndHookMethod(java.net.InetAddress.class, method, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String host = (String) param.args[0];
                        if (host.endsWith("music.126.net")) {
                            InetAddress[] ips = Utils.getIpByHostViaHttpDns(host);
                            param.setResult("getAllByName".equals(param.method.getName()) ? ips : ips[0]);
                        }
                    }
                });
            }


            findAndHookMethod("com.netease.hearttouch.hthttpdns.model.DNSEntity", CloudMusicPackage.getClassLoader(), "fromJsonObject", JSONObject.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            JSONObject json = (JSONObject) param.args[0];
                            String host = json.optString("host");
                            if (host.endsWith("music.126.net")) {
                                Future future = pool.submit(() -> Utils.getIpByHostViaHttpDns(host));
                                InetAddress ips[] = (InetAddress[]) future.get();

                                if (ips.length > 0) {
                                    JSONArray array = new JSONArray();
                                    for (InetAddress ip : ips) {
                                        array.put(ip.getHostAddress());
                                    }
                                    json.put("ips", array);
                                }
                            }
                        }
                    }
            );
        }
    }
}


