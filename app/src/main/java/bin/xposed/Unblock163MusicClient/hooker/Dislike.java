package bin.xposed.Unblock163MusicClient.hooker;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.view.View;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.Settings;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedBridge.invokeOriginalMethod;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class Dislike extends Hooker {

    @Override
    protected void howToHook() throws PackageManager.NameNotFoundException, IllegalAccessException {
        if (Settings.isConfirmDislikeEnabled()) {
            hookMethod(CloudMusicPackage.PlayerActivity.getLikeButtonOnClickMethod(), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    Activity currentActivity = (Activity) getObjectField(param.thisObject, "a");
                    if (CloudMusicPackage.PlayerActivity.getClazz().isInstance(currentActivity)) {
                        Object musicInfo = new CloudMusicPackage.PlayerActivity(currentActivity).getMusicInfo();
                        long musicId = new CloudMusicPackage.MusicInfo(musicInfo).getMatchedMusicId();
                        boolean isStarred = CloudMusicPackage.MusicInfo.isStarred(musicId);
                        if (isStarred) {
                            callStaticMethod(CloudMusicPackage.UIAA.getClazz(), "a", currentActivity, "确定不再收藏此歌曲吗？", "不再收藏", (View.OnClickListener) v -> {
                                try {
                                    invokeOriginalMethod(param.method, param.thisObject, param.args);
                                } catch (Throwable t) {
                                    log(t);
                                }
                            });
                            param.setResult(null);
                        }
                    }
                }
            });
        }
    }
}






