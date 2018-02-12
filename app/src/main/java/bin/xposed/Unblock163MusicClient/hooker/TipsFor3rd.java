package bin.xposed.Unblock163MusicClient.hooker;

import android.text.TextUtils;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class TipsFor3rd extends Hooker {

    @Override
    protected void howToHook() throws Throwable {
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
    }
}






