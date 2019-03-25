package bin.xposed.Unblock163MusicClient;

import static bin.xposed.Unblock163MusicClient.Utils.log;

public abstract class Hooker {

    protected abstract void howToHook() throws Throwable;

    void startToHook() {
        try {
            howToHook();
        } catch (Throwable t) {
            log(t);
        }
    }
}
