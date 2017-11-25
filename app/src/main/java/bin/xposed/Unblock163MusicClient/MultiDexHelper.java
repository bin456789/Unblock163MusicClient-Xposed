package bin.xposed.Unblock163MusicClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;

import static de.robv.android.xposed.XposedBridge.log;

/**
 * Created by xudshen@hotmail.com on 14/11/13.
 * http://stackoverflow.com/questions/26623905/android-multidex-list-all-classes
 */

class MultiDexHelper {
    private static final String EXTRACTED_NAME_EXT = ".classes";
    private static final String EXTRACTED_SUFFIX = ".zip";

    private static final String SECONDARY_FOLDER_NAME = "code_cache" + File.separator +
            "secondary-dexes";

    private static final String PREFS_FILE = "multidex.version";
    private static final String KEY_DEX_NUMBER = "dex.number";

    @SuppressWarnings("all")
    private static SharedPreferences getMultiDexPreferences(Context context) {
        return context.getSharedPreferences(PREFS_FILE,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                        ? Context.MODE_PRIVATE
                        : Context.MODE_MULTI_PROCESS);
    }

    /**
     * get all the dex path
     *
     * @param context the application context
     * @return all the dex path
     * @throws PackageManager.NameNotFoundException
     */
    private static List<String> getSourcePaths(Context context) throws PackageManager.NameNotFoundException {
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
        File sourceApk = new File(applicationInfo.sourceDir);
        File dexDir = new File(applicationInfo.dataDir, SECONDARY_FOLDER_NAME);

        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add(applicationInfo.sourceDir); //add the default apk path

        //the prefix of extracted file, ie: test.classes
        String extractedFilePrefix = sourceApk.getName() + EXTRACTED_NAME_EXT;
        //the total dex numbers
        int totalDexNumber = getMultiDexPreferences(context).getInt(KEY_DEX_NUMBER, 1);

        for (int secondaryNumber = 2; secondaryNumber <= totalDexNumber; secondaryNumber++) {
            //for each dex file, ie: test.classes2.zip, test.classes3.zip...
            String fileName = extractedFilePrefix + secondaryNumber + EXTRACTED_SUFFIX;
            File extractedFile = new File(dexDir, fileName);
            if (extractedFile.isFile()) {
                sourcePaths.add(extractedFile.getAbsolutePath());
                //we ignore the verify zip part
            } else {
                log("Missing extracted secondary dex file '" +
                        extractedFile.getPath() + "'");
            }
        }

        return sourcePaths;
    }

    /**
     * get all the classes name in "classes.dex", "classes2.dex", ....
     *
     * @param context the application context
     * @return all the classes name
     * @throws PackageManager.NameNotFoundException
     */
    static List<String> getAllClasses(Context context) throws PackageManager.NameNotFoundException {
        // read class list from cache
        long lastUpdateTime = context.getPackageManager().getPackageInfo(CloudMusicPackage.PACKAGE_NAME, 0).lastUpdateTime;
        File classesFile = new File(context.getCacheDir(), "ClassList.dat");
        if (classesFile.exists() && classesFile.canRead()) {
            try {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(classesFile));
                long lastUpdateTimeFromFile = in.readLong();
                if (lastUpdateTime == lastUpdateTimeFromFile) {
                    //noinspection unchecked
                    return (List<String>) in.readObject();
                }
            } catch (IOException e) {
                log(e);
            } catch (ClassNotFoundException e) {
                log(e);
            }
        }


        List<String> classNames = new ArrayList<>();
        boolean hasException = false;
        for (String path : getSourcePaths(context)) {
            try {
                DexFile dexfile;
                String pathTmp = null;
                if (path.endsWith(EXTRACTED_SUFFIX)) {
                    //NOT use new DexFile(path), because it will throw "permission error in /data/dalvik-cache"
                    pathTmp = path + ".tmp";
                    dexfile = DexFile.loadDex(path, pathTmp, 0);
                } else {
                    dexfile = new DexFile(path);
                }
                Enumeration<String> dexEntries = dexfile.entries();
                while (dexEntries.hasMoreElements()) {
                    classNames.add(dexEntries.nextElement());
                }
                if (pathTmp != null) {
                    Utility.deleteFile(new File(pathTmp));
                }
            } catch (Throwable t) {
                hasException = true;
                log("Error at loading dex file '" +
                        path + "'");
            }
        }

        // write class list cache
        if (!hasException) {
            try {
                ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(classesFile));
                out.writeLong(lastUpdateTime);
                out.writeObject(classNames);
                out.flush();
                out.close();
            } catch (Throwable t) {
                log(t);
            }
        }

        return classNames;
    }
}