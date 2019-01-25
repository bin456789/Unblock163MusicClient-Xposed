package bin.xposed.Unblock163MusicClient.hooker;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.Settings;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Transparent extends Hooker {

    private static int getNavigationBarHeight(Context context) {
        Resources resources = context.getResources();
        int id = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0) {
            return resources.getDimensionPixelSize(id);
        }
        return 0;
    }

    public static Point getNavigationBarSize(Context context) {
        Point appUsableSize = getAppUsableScreenSize(context);
        Point realScreenSize = getRealScreenSize(context);

        // navigation bar on the side
        if (appUsableSize.x < realScreenSize.x) {
            return new Point(realScreenSize.x - appUsableSize.x, appUsableSize.y);
        }

        // navigation bar at the bottom
        if (appUsableSize.y < realScreenSize.y) {
            return new Point(appUsableSize.x, realScreenSize.y - appUsableSize.y);
        }

        // navigation bar is not present
        return new Point();
    }

    public static Point getAppUsableScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public static Point getRealScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();

        if (Build.VERSION.SDK_INT >= 17) {
            display.getRealSize(size);
        } else {
            try {
                size.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                size.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            } catch (Exception ignored) {
            }
        }

        return size;
    }

    @Override
    protected void howToHook() throws Throwable {

        if (Settings.isTransparentNavBar()) {

            findAndHookMethod("com.netease.cloudmusic.activity.PlayerActivity", CloudMusicPackage.getClassLoader(),
                    "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity playerActivity = (Activity) param.thisObject;

                            int navigationBarHeight = getNavigationBarSize(playerActivity).y;
                            if (navigationBarHeight > 0) {
                                ViewGroup rootView = (ViewGroup) ((ViewGroup) playerActivity.findViewById(android.R.id.content)).getChildAt(0);
                                if (rootView instanceof RelativeLayout) {
                                    LinearLayout linearLayout = null;

                                    for (int i = rootView.getChildCount() - 1; i >= 0; i--) {
                                        View child = rootView.getChildAt(i);
                                        if (child instanceof LinearLayout) {
                                            linearLayout = (LinearLayout) child;
                                            break;
                                        }
                                    }

                                    if (linearLayout != null) {
                                        linearLayout.setPadding(linearLayout.getLeft(), linearLayout.getTop(), linearLayout.getRight(),
                                                navigationBarHeight);
                                        playerActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                                    }
                                }
                            }
                        }
                    });

        }
    }

}



