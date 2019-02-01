package bin.xposed.Unblock163MusicClient.hooker;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.gyf.barlibrary.ImmersionBar;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.Settings;
import de.robv.android.xposed.XC_MethodHook;

import static com.gyf.barlibrary.ImmersionBar.getNavigationBarHeight;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Transparent extends Hooker {


    @Override
    protected void howToHook() throws Throwable {

        if (Settings.isTransparentNavBar()) {

            findAndHookMethod("com.netease.cloudmusic.activity.PlayerActivity", CloudMusicPackage.getClassLoader(),
                    "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity playerActivity = (Activity) param.thisObject;

                            if (ImmersionBar.hasNavigationBar(playerActivity)) {
                                ViewGroup rootView = (ViewGroup) ((ViewGroup) playerActivity.findViewById(android.R.id.content)).getChildAt(0);
                                LinearLayout linearLayout = null;
                                View lyricViewContainer = null;

                                for (int i = rootView.getChildCount() - 1; i >= 0; i--) {
                                    View child = rootView.getChildAt(i);
                                    if (linearLayout == null && child instanceof LinearLayout) {
                                        linearLayout = (LinearLayout) child;
                                    }
                                    if (lyricViewContainer == null && child.getClass().getName().endsWith("LyricViewContainer")) {
                                        lyricViewContainer = child;
                                    }
                                }


                                if (linearLayout != null && lyricViewContainer != null) {
                                    int navigationBarHeight = getNavigationBarHeight(playerActivity);
                                    addPaddingBottom(linearLayout, navigationBarHeight);
                                    addPaddingBottom(lyricViewContainer, navigationBarHeight);
                                    playerActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                                }
                            }
                        }
                    });

        }
    }

    private void addPaddingBottom(View view, int bottom) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                view.getPaddingBottom() + bottom);
    }

}



