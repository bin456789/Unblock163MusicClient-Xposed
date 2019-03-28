package bin.xposed.Unblock163MusicClient;

import android.content.Context;

import net.androidwing.hotxposed.IHookerDispatcher;

import java.util.ArrayList;
import java.util.List;

import bin.xposed.Unblock163MusicClient.hooker.About;
import bin.xposed.Unblock163MusicClient.hooker.Dislike;
import bin.xposed.Unblock163MusicClient.hooker.DnsMod;
import bin.xposed.Unblock163MusicClient.hooker.Download;
import bin.xposed.Unblock163MusicClient.hooker.Eapi;
import bin.xposed.Unblock163MusicClient.hooker.Gray;
import bin.xposed.Unblock163MusicClient.hooker.HttpMod;
import bin.xposed.Unblock163MusicClient.hooker.MagiskFix;
import bin.xposed.Unblock163MusicClient.hooker.QualityBox;
import bin.xposed.Unblock163MusicClient.hooker.TipsFor3rd;
import bin.xposed.Unblock163MusicClient.hooker.Transparent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class HookerDispatcher implements IHookerDispatcher {


    @Override
    public void dispatch(XC_LoadPackage.LoadPackageParam lpparam) {
        findAndHookMethod(findClass("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader),
                "attachBaseContext", Context.class, new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                        Context context = (Context) param.thisObject;
                        String processName = Utils.getCurrentProcessName(context);
                        List<Hooker> hookers = null;

                        if (processName.equals(CloudMusicPackage.PACKAGE_NAME)) {
                            hookers = getMainProcessHookers();

                        } else if (processName.equals(CloudMusicPackage.PACKAGE_NAME + ":play")) {
                            hookers = getPlayProcessHookers();
                        }

                        if (hookers != null && hookers.size() > 0) {
                            CloudMusicPackage.init(context);
                            for (Hooker hooker : hookers) {
                                hooker.startToHook();
                            }
                        }
                    }
                });
    }

    private List<Hooker> getMainProcessHookers() {
        List<Hooker> list = new ArrayList<>();
        list.add(new About());
        if (Settings.isUnblockEnabled()) {
            list.add(new Eapi());
            list.add(new Download());
            list.add(new HttpMod());
            list.add(new QualityBox());
            list.add(new TipsFor3rd());
            list.add(new DnsMod());
            if (Settings.isPreventGrayEnabled()) {
                list.add(new Gray());
            }
        }
        if (Settings.isDislikeConfirmEnabled()) {
            list.add(new Dislike());
        }
        if (Settings.isTransparentPlayerNavBar() || Settings.isTransparentBaseNavBar()) {
            list.add(new Transparent());
        }
        if (Settings.isMagiskFixEnabled()) {
            list.add(new MagiskFix());
        }
        return list;
    }

    private List<Hooker> getPlayProcessHookers() {
        List<Hooker> list = new ArrayList<>();
        if (Settings.isUnblockEnabled()) {
            list.add(new Eapi());
            list.add(new HttpMod());
            list.add(new DnsMod());
        }
        return list;
    }


}