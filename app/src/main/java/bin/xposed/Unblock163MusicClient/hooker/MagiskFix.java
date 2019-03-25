package bin.xposed.Unblock163MusicClient.hooker;

import android.os.Environment;

import com.annimon.stream.Stream;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import bin.xposed.Unblock163MusicClient.CloudMusicPackage;
import bin.xposed.Unblock163MusicClient.Hooker;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MagiskFix extends Hooker {

    @Override
    protected void howToHook() throws Throwable {
        Method[] methods = XposedHelpers.findMethodsByExactParameters(CloudMusicPackage.NeteaseMusicUtils.getClazz(), List.class, boolean.class);
        Method method = Stream.of(methods).sortBy(Method::getName).findFirst().get();
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                List<String> list = new ArrayList<>();
                list.add(Environment.getExternalStorageDirectory().getAbsolutePath());
                param.setResult(list);
            }
        });
    }
}





