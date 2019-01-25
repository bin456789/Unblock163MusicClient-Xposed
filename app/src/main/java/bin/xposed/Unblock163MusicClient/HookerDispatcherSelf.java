package bin.xposed.Unblock163MusicClient;

import net.androidwing.hotxposed.IHookerDispatcher;

import bin.xposed.Unblock163MusicClient.ui.SettingsActivity;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class HookerDispatcherSelf implements IHookerDispatcher {

    @Override
    public void dispatch(XC_LoadPackage.LoadPackageParam lpparam) {
        findAndHookMethod(SettingsActivity.class.getName(), lpparam.classLoader,
                "isModuleActive", XC_MethodReplacement.returnConstant(true));
    }
}