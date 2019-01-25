package bin.xposed.Unblock163MusicClient.hooker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedBridge.hookMethod;

public class Download extends Hooker {
    private static final Pattern REX_MD5 = Pattern.compile("[a-f0-9]{32}", Pattern.CASE_INSENSITIVE);

    @Override
    protected void howToHook() throws Throwable {
        hookMethod(CloudMusicPackage.Transfer.getCalcMd5Method(), new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String path = param.args[0].toString();

                Matcher matcher = REX_MD5.matcher(path);
                if (matcher.find()) {
                    param.setResult(matcher.group());
                }
            }
        });
    }
}




