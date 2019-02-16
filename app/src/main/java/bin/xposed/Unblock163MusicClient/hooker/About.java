package bin.xposed.Unblock163MusicClient.hooker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import bin.xposed.Unblock163MusicClient.BuildConfig;
import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import bin.xposed.Unblock163MusicClient.R;
import bin.xposed.Unblock163MusicClient.Utils;
import bin.xposed.Unblock163MusicClient.ui.SettingsActivity;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class About extends Hooker {

    @Override
    protected void howToHook() throws Throwable {
        findAndHookMethod("com.netease.cloudmusic.activity.AboutActivity", CloudMusicPackage.getClassLoader(),
                "onCreate", Bundle.class, new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity aboutActivity = (Activity) param.thisObject;

                        ViewGroup vg = (ViewGroup) ((ViewGroup) aboutActivity.findViewById(android.R.id.content)).getChildAt(0);
                        vg = (ViewGroup) vg.getChildAt(2);
                        vg = (ViewGroup) vg.getChildAt(0);

                        int pos = 3;

                        if (vg instanceof LinearLayout && vg.getChildAt(pos) instanceof TextView) {
                            View checkUpdate = vg.getChildAt(pos - 1);
                            TextView rateUs = (TextView) vg.getChildAt(pos);

                            TextView module = new TextView(aboutActivity);
                            module.setText(Utils.getModuleResources().getText(R.string.app_name));
                            module.setOnClickListener(v -> {
                                Intent intent = new Intent();
                                intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, SettingsActivity.class.getName()));
                                aboutActivity.startActivity(intent);
                            });

                            Utils.copyTextViewStyle(rateUs, module);
                            Utils.copyBackground(checkUpdate, rateUs);
                            vg.addView(module, pos + 1);
                        }

                    }
                });
    }
}
