package bin.xposed.Unblock163MusicClient.hooker;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.gyf.barlibrary.ImmersionBar;

import java.util.ArrayList;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import static com.gyf.barlibrary.ImmersionBar.getNavigationBarHeight;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Transparent extends Hooker {


    @Override
    protected void howToHook() throws Throwable {
        ArrayList<String> activityMap = new ArrayList<String>() {
            {
                add("PlayerActivity");
                add("PlayerMSActivity");
                add("PlayerChildActivity");
                add("PlayerRadioActivity");
                add("PlayerProgramActivity");
                add("PlayerSportRadioActivity");
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Settings.isTransparentNavBar()) {

            XC_MethodHook methodHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity playerActivity = (Activity) param.thisObject;

                    if (ImmersionBar.hasNavigationBar(playerActivity)) {
                        ViewGroup rootView = (ViewGroup) ((ViewGroup) playerActivity.findViewById(android.R.id.content)).getChildAt(0);
                        int navigationBarHeight = getNavigationBarHeight(playerActivity);

                        for (int i = rootView.getChildCount() - 1; i >= 0; i--) {
                            View child = rootView.getChildAt(i);

                            if (child.getClass().getSimpleName().endsWith("ViewContainer")) {
                                addPaddingBottom(child, navigationBarHeight);

                            } else if (child.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) child.getLayoutParams();
                                if (layoutParams.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM] != 0) {
                                    layoutParams.bottomMargin += navigationBarHeight;
                                }
                            }
                        }


                        View decorView = playerActivity.getWindow().getDecorView();
                        decorView.setSystemUiVisibility(decorView.getSystemUiVisibility()
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                        playerActivity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
                    }
                }
            };

            for (String s : activityMap) {
                try {
                    findAndHookMethod("com.netease.cloudmusic.activity." + s, CloudMusicPackage.getClassLoader(),
                            "onCreate", Bundle.class, methodHook);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }

        }
    }

    private void addPaddingBottom(View view, int pixel) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                view.getPaddingBottom() + pixel);
    }

}



