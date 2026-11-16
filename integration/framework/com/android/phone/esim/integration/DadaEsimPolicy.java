package com.android.phone.esim.integration;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.SystemProperties;
import android.os.UserManager;
import android.telephony.euicc.EuiccManager;
import android.widget.Toast;
import com.android.internal.telephony.euicc.EuiccConnector;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Management policy only: this class has no route, power, mapping or profile setter. */
public final class DadaEsimPolicy {
    static final String FINGERPRINT =
            "OnePlus/PLZ110/OP64DDL1:17/CP2A.260605.016/"
            + "B.2a5e7c5-41b0cd-45d7d3:user/release-keys";
    static final String BASE_FEATURE = "android.hardware.telephony.euicc";
    static final String BIND_PERMISSION = "android.permission.BIND_EUICC_SERVICE";
    static final String WRITE_PERMISSION = "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS";
    static final String MANAGE_ACTION =
            "android.telephony.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS";
    static final String ADD_ACTION =
            "android.telephony.euicc.action.PROVISION_EMBEDDED_SUBSCRIPTION";
    static final String UNAVAILABLE =
            "eSIM action unavailable: verified logical1 ownership and release integration are required.";
    private static final int BUILD_UNAVAILABLE = -1;
    private static final int BUILD_OTHER = 0;
    private static final int BUILD_MATCH = 1;

    private DadaEsimPolicy() {}

    /** Read-only compatibility predicate, not original-caller/Binder authorization. */
    public static boolean isSupportedBuild() {
        return applicability() == BUILD_MATCH;
    }

    /** Unknown is not confirmed out of scope and must not select an unsafe OEM fallback. */
    public static boolean useNativeManagement() {
        return applicability() != BUILD_OTHER;
    }

    private static int applicability() {
        try {
            // Read both: an unreadable/missing fingerprint is not a confirmed mismatch.
            String device = SystemProperties.get("ro.product.device");
            String fingerprint = SystemProperties.get("ro.build.fingerprint");
            if (device == null || device.length() == 0
                    || fingerprint == null || fingerprint.length() == 0) {
                return BUILD_UNAVAILABLE;
            }
            return "dada".equals(device) && FINGERPRINT.equals(fingerprint)
                    ? BUILD_MATCH : BUILD_OTHER;
        } catch (RuntimeException | LinkageError unavailable) {
            return BUILD_UNAVAILABLE;
        }
    }

    /** Every call consults current capability, manager, user, provider and signer. No cache. */
    public static boolean isManagementAvailable(Context context) {
        return isSupportedBuild() && providerAvailable(context, ReleaseContract.identity());
    }

    /** A nonzero management display is not an OEM hardware or conversion support type. */
    public static int managementDisplayType(Context context) {
        return isManagementAvailable(context) ? 1 : 0;
    }

    /** Throw before a legacy task/request/state write, outside its original catch blocks. */
    public static void rejectLegacyMutation() {
        if (applicability() != BUILD_OTHER) {
            throw new UnsupportedOperationException(UNAVAILABLE);
        }
    }

    /** Do not alter ordinary logical0 power actions when guarding the old logical1 delegate. */
    public static void rejectLegacyLogical1Mutation(int logicalSlot) {
        if (logicalSlot != 0) {
            rejectLegacyMutation();
        }
    }

    /** Selective classification only; original native authorization remains mandatory. */
    public static void requireCurrentPhysicalSim1(Context context, int subId, int logicalSlot) {
        int scope = applicability();
        if (scope == BUILD_OTHER) {
            return;
        }
        if (scope != BUILD_MATCH
                || !PhysicalSim1Admission.isCurrent(context, subId, logicalSlot)) {
            throw new UnsupportedOperationException(UNAVAILABLE);
        }
    }

    /** UI feedback is not an operation result or a native task completion callback. */
    public static void showUnavailable(Context context) {
        if (context != null) {
            Toast.makeText(context, UNAVAILABLE, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Called only after the native Add security/consent gates. The ownership adapter is
     * deliberately unavailable until N3 is integrated; no old COS or routing fallback.
     * Returning to the UI does not report download/activation/route success.
     */
    public static void dispatchNative(Activity activity, Intent incoming) {
        try {
            if (activity == null || incoming == null || !isManagementAvailable(activity)) {
                throw new UnsupportedOperationException(UNAVAILABLE);
            }
            String action = incoming.getAction();
            if (!ADD_ACTION.equals(action) && !MANAGE_ACTION.equals(action)) {
                throw new UnsupportedOperationException(UNAVAILABLE);
            }
            Intent forwarded = nativeContext(activity, incoming);
            if (!activityAvailable(activity, forwarded, ReleaseContract.identity())) {
                throw new UnsupportedOperationException(UNAVAILABLE);
            }
            // Must be replaced by an authenticated, serialized, readback-backed N3 lease.
            // Never mint an unauthenticated UUID/PendingIntent and call it ownership.
            NativeLogical1Gate.acquireForManagement(activity, forwarded);
        } catch (RuntimeException | LinkageError unavailable) {
            showUnavailable(activity);
        }
    }

    static Intent nativeContext(Activity activity, Intent incoming) {
        Intent forwarded = new Intent(incoming);
        Intent original = activity.getIntent();
        if (original != null) {
            forwarded.putExtras(original);
            // Preserve deliberate request extras, including the native consent result.
            forwarded.putExtras(incoming);
        }
        // OEM LPA-return ICCID is not activation/route confirmation or new Add authority.
        forwarded.removeExtra("installed_profile_id");
        return forwarded;
    }

    static boolean providerAvailable(Context context, ProviderIdentity identity) {
        try {
            if (context == null || identity == null || !identity.isConfigured()) {
                return false;
            }
            Object user = context.getSystemService("user");
            if (!(user instanceof UserManager) || !((UserManager) user).isSystemUser()) {
                return false;
            }
            PackageManager pm = context.getPackageManager();
            if (pm == null || !pm.hasSystemFeature(BASE_FEATURE)) {
                return false;
            }
            Object manager = context.getSystemService("euicc");
            if (!(manager instanceof EuiccManager) || !((EuiccManager) manager).isEnabled()) {
                return false;
            }
            ComponentInfo selected = EuiccConnector.findBestComponent(pm);
            if (!(selected instanceof ServiceInfo)) {
                return false;
            }
            ServiceInfo service = (ServiceInfo) selected;
            return identity.serviceClass.equals(service.name)
                    && validComponent(pm, service, service.permission, identity);
        } catch (RuntimeException | LinkageError | PackageManager.NameNotFoundException
                 | NoSuchAlgorithmException unavailable) {
            return false;
        }
    }

    /** Reuses the target selector for the translated service action; no independent priority rule. */
    static boolean activityAvailable(Context context, Intent publicIntent, ProviderIdentity identity) {
        try {
            if (!providerAvailable(context, identity) || publicIntent == null) {
                return false;
            }
            String action = publicIntent.getAction();
            String serviceAction;
            if (ADD_ACTION.equals(action)) {
                serviceAction = "android.service.euicc.action.PROVISION_EMBEDDED_SUBSCRIPTION";
            } else if (MANAGE_ACTION.equals(action)) {
                serviceAction = "android.service.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS";
            } else {
                return false;
            }
            Intent query = new Intent(publicIntent);
            query.setAction(serviceAction);
            query.setComponent(null);
            query.setPackage(null);
            ActivityInfo selected = EuiccConnector.findBestActivity(context.getPackageManager(), query);
            String expectedClass = ADD_ACTION.equals(action) ? identity.luiClass : identity.manageClass;
            return selected != null && expectedClass.equals(selected.name)
                    && validComponent(context.getPackageManager(), selected, selected.permission, identity);
        } catch (RuntimeException | LinkageError | PackageManager.NameNotFoundException
                 | NoSuchAlgorithmException unavailable) {
            return false;
        }
    }

    private static boolean validComponent(PackageManager pm, ComponentInfo component,
            String permission, ProviderIdentity identity)
            throws PackageManager.NameNotFoundException, NoSuchAlgorithmException {
        ApplicationInfo application = component.applicationInfo;
        if (!identity.packageName.equals(component.packageName) || !component.enabled
                || !component.exported || !BIND_PERMISSION.equals(permission)
                || application == null || !application.enabled
                || !identity.packageName.equals(application.packageName)
                || (application.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                || (application.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                || pm.checkPermission(WRITE_PERMISSION, identity.packageName)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        PackageInfo installed = pm.getPackageInfo(identity.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (installed == null || installed.signingInfo == null) {
            return false;
        }
        Signature[] signers = installed.signingInfo.getApkContentsSigners();
        // Only the single explicitly approved current release signer is admitted.
        // History/lineage or a same-package debug certificate is not sufficient.
        return signers != null && signers.length == 1 && signers[0] != null
                && MessageDigest.isEqual(identity.currentSignerSha256,
                        MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()));
    }

    static final class ProviderIdentity {
        final String packageName;
        final String serviceClass;
        final String luiClass;
        final String manageClass;
        final byte[] currentSignerSha256;

        ProviderIdentity(String packageName, String serviceClass, String luiClass, String pin) {
            this(packageName, serviceClass, luiClass, luiClass, pin);
        }

        ProviderIdentity(String packageName, String serviceClass, String luiClass,
                String manageClass, String pin) {
            this.packageName = packageName;
            this.serviceClass = serviceClass;
            this.luiClass = luiClass;
            this.manageClass = manageClass;
            this.currentSignerSha256 = decodePin(pin);
        }

        boolean isConfigured() {
            return ReleaseContract.PACKAGE_NAME.equals(packageName)
                    && serviceClass != null && serviceClass.startsWith(packageName + ".")
                    && luiClass != null && luiClass.startsWith(packageName + ".")
                    && manageClass != null && manageClass.startsWith(packageName + ".")
                    && currentSignerSha256 != null;
        }

        private static byte[] decodePin(String pin) {
            if (pin == null || pin.length() != 64) {
                return null;
            }
            byte[] result = new byte[32];
            boolean nonzero = false;
            for (int i = 0; i < result.length; i++) {
                int high = Character.digit(pin.charAt(2 * i), 16);
                int low = Character.digit(pin.charAt(2 * i + 1), 16);
                if (high < 0 || low < 0) {
                    return null;
                }
                result[i] = (byte) ((high << 4) | low);
                nonzero |= result[i] != 0;
            }
            return nonzero ? result : null;
        }
    }
}
