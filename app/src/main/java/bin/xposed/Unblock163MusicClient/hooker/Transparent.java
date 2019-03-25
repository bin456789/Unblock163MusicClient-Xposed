package bin.xposed.Unblock163MusicClient.hooker;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
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
import de.robv.android.xposed.XposedHelpers;

import static bin.xposed.Unblock163MusicClient.Utils.log;
import static com.gyf.barlibrary.ImmersionBar.getNavigationBarHeight;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Transparent extends Hooker {


    private Boolean hasNavigationBar;

    @Override
    protected void howToHook() throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Settings.isTransparentPlayerNavBar()) {
                transparentPlayerNavBar();
            }

            if (Settings.isTransparentBaseNavBar()) {
                transparentBaseNavBar();
            }
        }
    }

    private void transparentPlayerNavBar() {
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


        XC_MethodHook methodHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity playerActivity = (Activity) param.thisObject;

                if (hasNavigationBar(playerActivity)) {
                    ViewGroup rootView = (ViewGroup) ((ViewGroup) playerActivity.findViewById(android.R.id.content)).getChildAt(0);
                    int navigationBarHeight = getNavigationBarHeight(playerActivity);

                    View lyricView = null;

                    for (int i = 0; i < rootView.getChildCount(); i++) {
                        View child = rootView.getChildAt(i);

                        if (lyricView == null) {
                            // 4.x ~ 5.x
                            if (child.getClass().getSimpleName().endsWith("Toolbar")) {
                                lyricView = rootView.getChildAt(i - 1);

                                // 5.x ~ 6.x
                            } else if (child.getClass().getSimpleName().endsWith("ViewContainer")) {
                                lyricView = child;
                            }
                        }


                        if (child.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) child.getLayoutParams();
                            addMarginBottomForRelativeLayoutParams(layoutParams, navigationBarHeight);
                        }
                    }

                    if (lyricView != null) {
                        addPaddingBottom(lyricView, navigationBarHeight);
                        setNavigationTransparent(playerActivity);
                    }

                }
            }
        };

        for (String s : activityMap) {
            try {
                findAndHookMethod("com.netease.cloudmusic.activity." + s, CloudMusicPackage.getClassLoader(),
                        "onCreate", Bundle.class, methodHook);
            } catch (Throwable t) {
                log(t);
            }
        }
    }

    private void transparentBaseNavBar() {
        Class clazz = XposedHelpers.findClass("com.netease.cloudmusic.activity.MainActivity", CloudMusicPackage.getClassLoader());
        while (!clazz.getSuperclass().getSimpleName().equals("AppCompatActivity")) {
            clazz = clazz.getSuperclass();
        }


        XC_MethodHook methodHook = new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (hasNavigationBar(activity) && shouldTransparent(activity)) {
                    setNavigationTransparent(activity);
                }
            }
        };


        try {
            findAndHookMethod(clazz, "onCreate", Bundle.class, methodHook);
        } catch (Throwable t) {
            log(t);
        }
    }

    private boolean hasNavigationBar(Activity activity) {
        if (hasNavigationBar == null) {
            hasNavigationBar = ImmersionBar.hasNavigationBar(activity);
        }
        return hasNavigationBar;
    }

    private boolean shouldTransparent(Activity activity) {
        // fix SharePanelActivity
        return (activity.getIntent().getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) == 0
                || activity.isTaskRoot();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setNavigationTransparent(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void addPaddingBottom(View view, int pixel) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                view.getPaddingBottom() + pixel);
    }

    private void addMarginBottomForRelativeLayoutParams(RelativeLayout.LayoutParams layoutParams, int pixel) {
        if (layoutParams.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM] != 0) {
            layoutParams.bottomMargin += pixel;
        }
    }
}



