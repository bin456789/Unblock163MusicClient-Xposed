package bin.xposed.Unblock163MusicClient.hooker;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.Settings;
import bin.xposed.Unblock163MusicClient.Utility;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Gray extends Hooker {

    @Override
    protected void howToHook() throws Throwable {

        if (Settings.isPreventGrayEnabled()) {

            findAndHookMethod(CloudMusicPackage.MusicInfo.getClazz(), "hasCopyRight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!Utility.isCallFromMyself()) {
                        param.setResult(true);
                    }
                }
            });

        }
    }
}



