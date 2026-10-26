package <?package_name?>;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

public class SketchApplication extends Application {

    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable throwable) {
                        String stackTrace = Log.getStackTraceString(throwable);
                        try {
                            Intent logIntent = new Intent("pro.sketchware.ACTION_NEW_DEBUG_LOG");
                            logIntent.putExtra("packageName", getPackageName());
                            logIntent.putExtra("isCrash", true);
                            logIntent.putExtra("log", stackTrace);
                            sendBroadcast(logIntent);
                        } catch (Throwable ignored) {
                        }
                        Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        intent.putExtra("error", stackTrace);
                        startActivity(intent);
                        Process.killProcess(Process.myPid());
                        System.exit(1);
                    }
                });
        super.onCreate();
    }
}
