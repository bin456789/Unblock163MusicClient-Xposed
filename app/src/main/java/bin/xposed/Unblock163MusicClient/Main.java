package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import bin.xposed.Unblock163MusicClient.hooker.Dislike;
import bin.xposed.Unblock163MusicClient.hooker.Download;
import bin.xposed.Unblock163MusicClient.hooker.Eapi;
import bin.xposed.Unblock163MusicClient.hooker.Gray;
import bin.xposed.Unblock163MusicClient.hooker.HttpFor3rd;
import bin.xposed.Unblock163MusicClient.hooker.Oversea;
import bin.xposed.Unblock163MusicClient.hooker.QualityBox;
import bin.xposed.Unblock163MusicClient.hooker.TipsFor3rd;
import bin.xposed.Unblock163MusicClient.ui.SettingsActivity;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

@SuppressWarnings("deprecation")
public class Main implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (Handler.isDomainExpired()) {
            return;
        }

        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            findAndHookMethod(findClass(SettingsActivity.class.getName(), lpparam.classLoader),
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }

        if (lpparam.packageName.equals(CloudMusicPackage.PACKAGE_NAME)) {
            findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    CloudMusicPackage.init((Context) param.thisObject);
                    List<Hooker> list = new ArrayList<>();

                    if (Settings.isUnblockEnabled()) {
                        list.add(new Download());
                        list.add(new Eapi());
                        list.add(new HttpFor3rd());
                        list.add(new QualityBox());
                        list.add(new TipsFor3rd());

                        if (Settings.isOverseaModeEnabled()) {
                            list.add(new Oversea());
                        }

                        if (Settings.isPreventGrayEnabled()) {
                            list.add(new Gray());
                        }

                    }

                    if (Settings.isConfirmDislikeEnabled()) {
                        list.add(new Dislike());
                    }


                    for (Hooker hooker : list) {
                        hooker.startToHook();
                    }

                }
            });
        }
    }
}