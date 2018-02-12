package bin.xposed.Unblock163MusicClient;

import de.robv.android.xposed.XposedBridge;

public abstract class Hooker {

    protected abstract void howToHook() throws Throwable;

    void startToHook() {
        try {
            howToHook();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
