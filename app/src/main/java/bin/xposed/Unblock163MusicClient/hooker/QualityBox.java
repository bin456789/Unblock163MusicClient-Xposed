package bin.xposed.Unblock163MusicClient.hooker;

import android.text.SpannableString;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.CloudMusicPackage.PlayerActivity;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;

public class QualityBox extends Hooker {

    @Override
    protected void howToHook() throws Throwable {
        hookMethod(CloudMusicPackage.UIAA.getQualityBoxMethod(), new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object currentActivity = param.args[0];
                        if (PlayerActivity.getClazz().isInstance(currentActivity)) {
                            Object musicInfo = new PlayerActivity(currentActivity).getMusicInfo();
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
                }
        );
    }
}






