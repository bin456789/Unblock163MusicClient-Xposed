package bin.xposed.Unblock163MusicClient.hooker;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Download extends Hooker {
    private static final Pattern REX_MD5 = Pattern.compile("[a-f0-9]{32}", Pattern.CASE_INSENSITIVE);

    @Override
    protected void howToHook() throws Throwable {
        XC_MethodHook replaceMd5 = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String path = param.args[0] instanceof File
                        ? ((File) param.args[0]).getPath()
                        : param.args[0].toString();

                Matcher matcher = REX_MD5.matcher(path);
                if (matcher.find()) {
                    param.setResult(matcher.group());
                }
            }
        };


        if (CloudMusicPackage.getVersion().startsWith("3")) {
            findAndHookMethod(CloudMusicPackage.NeteaseMusicUtils.getClazz(), "a", String.class, replaceMd5);
        } else {
            hookMethod(CloudMusicPackage.Transfer.getCalcMd5Method(), replaceMd5);
        }
    }
}




