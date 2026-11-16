package org.sourceowned.n3.runtime.v2;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Binder;

/** Signed provider identity remains checked by CallerPolicy; these are additional live UI gates. */
public final class ForegroundPolicy {
    private final Context context;
    public ForegroundPolicy(Context context) { this.context = context; }
    public boolean allowed(int originalUid) {
        long identity = Binder.clearCallingIdentity();
        try {
            int user = (Integer) ActivityManager.class.getMethod("getCurrentUser").invoke(null);
            ActivityManager am = context.getSystemService(ActivityManager.class);
            android.os.IBinder window = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "window");
            Object wm = Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, window);
            boolean locked = (Boolean) Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm);
            int importance = (Integer) ActivityManager.class.getMethod("getUidImportance", int.class).invoke(am, originalUid);
            boolean allowed = user == 0 && originalUid >= 10000 && originalUid < 100000
                    && importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && !locked;
            if (!allowed) android.util.Log.w("N3Foreground", "user=" + user + " importance=" + importance + " locked=" + locked);
            return allowed;
        } catch (Exception unavailable) { android.util.Log.e("N3Foreground", "Foreground query failed", unavailable); return false; }
        finally { Binder.restoreCallingIdentity(identity); }
    }
}
