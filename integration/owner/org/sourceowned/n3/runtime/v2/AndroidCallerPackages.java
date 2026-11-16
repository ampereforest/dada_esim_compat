package org.sourceowned.n3.runtime.v2;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.security.MessageDigest;
import java.util.Locale;
import android.os.Binder;

/** Each check refreshes installed facts; no certificate history/signature bypass or shared UID. */
public final class AndroidCallerPackages implements CallerPolicy.Packages {
    private final PackageManager pm;
    public AndroidCallerPackages(Context context) { pm = context.getPackageManager(); }
    @Override public CallerPolicy.Facts inspect(int uid) throws Exception {
        // Original UID is already captured by caller policy. Use owner's PM visibility, not
        // provider's package-visibility filters, then restore Binder identity for live checks.
        long identity = Binder.clearCallingIdentity();
        try { return inspectAsOwner(uid); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    private CallerPolicy.Facts inspectAsOwner(int uid) throws Exception {
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null || packages.length != 1 || !CallerPolicy.PACKAGE.equals(packages[0])) return null;
        PackageInfo info = pm.getPackageInfo(CallerPolicy.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
        ApplicationInfo app = info.applicationInfo;
        if (app == null || info.signingInfo == null || info.signingInfo.hasMultipleSigners()) return null;
        Signature[] current = info.signingInfo.getApkContentsSigners();
        if (current == null || current.length != 1) return null;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(current[0].toByteArray());
        StringBuilder cert = new StringBuilder();
        for (byte b : digest) cert.append(String.format(Locale.ROOT, "%02x", b & 255));
        // Legitimate platform private API. If inaccessible on this process/build, authorization denies.
        boolean privileged = (Boolean) ApplicationInfo.class.getMethod("isPrivilegedApp").invoke(app);
        boolean enabled = app.enabled && (app.flags & ApplicationInfo.FLAG_SUSPENDED) == 0;
        boolean permissions = true;
        for (String permission : new String[] {"android.permission.MODIFY_PHONE_STATE",
                "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS"}) {
            permissions &= pm.checkPermission(permission, CallerPolicy.PACKAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return new CallerPolicy.Facts(app.uid, info.packageName, cert.toString(), app.sourceDir,
                true, privileged, enabled, permissions);
    }
}
