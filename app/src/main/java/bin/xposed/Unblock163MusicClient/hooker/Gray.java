package bin.xposed.Unblock163MusicClient.hooker;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodReplacement;

import static de.robv.android.xposed.XposedBridge.hookMethod;

public class Gray extends Hooker {

    @Override
    protected void howToHook() throws Throwable {

        // 或者 canHighLightMusic
        hookMethod(CloudMusicPackage.MusicInfo.getHasCopyRightMethod(), XC_MethodReplacement.returnConstant(true));

    }
}



