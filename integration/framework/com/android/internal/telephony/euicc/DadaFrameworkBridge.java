package com.android.internal.telephony.euicc;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.UiccSlotMapping;
import java.util.Collection;
import java.util.List;
import com.android.phone.esim.integration.DadaEsimPolicy;
import com.android.phone.esim.integration.ReleaseContract;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Restricts existing framework admission. Does not replace its permission checks. */
public final class DadaFrameworkBridge {
    private static final String PACKAGE = "im.angry.openeuicc";
    private static final String BIND = "android.permission.BIND_EUICC_SERVICE";
    private static final String WRITE = "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS";

    private DadaFrameworkBridge() {}

    /** Called only after the original isValidEuiccComponent has returned true. */
    public static boolean allowCandidate(PackageManager pm, ResolveInfo resolved) {
        if (!DadaEsimPolicy.useNativeManagement()) {
            return true; // confirmed other builds retain their original selector
        }
        if (!DadaEsimPolicy.isSupportedBuild() || resolved == null) {
            return false;
        }
        ComponentInfo component = resolved.serviceInfo != null
                ? resolved.serviceInfo : resolved.activityInfo;
        return validComponent(pm, component);
    }

    /** Revalidate installed component and current signer immediately before binding. */
    public static boolean allowBinding(Context context, ServiceInfo selected) {
        if (!DadaEsimPolicy.useNativeManagement()) {
            return true;
        }
        try {
            if (!DadaEsimPolicy.isSupportedBuild() || context == null || selected == null) {
                return false;
            }
            Object user = context.getSystemService("user");
            return user instanceof UserManager && ((UserManager) user).isSystemUser()
                    && ReleaseContract.SERVICE_CLASS.equals(selected.name)
                    && validComponent(context.getPackageManager(), selected);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * Permit supported provider commands on an already-active matching eUICC path.
     * This is not route selection or an LPA mutation lease. The provider must keep
     * its independent operation owner around channel work. Never change routing
     * to satisfy a READ request, accept unknown cardId, or fake an inventory result.
     */
    public static boolean allowCommand(Context context, ServiceInfo selected, int cardId, int what) {
        if (!DadaEsimPolicy.useNativeManagement()) {
            return true;
        }
        return isSupportedProviderCommand(what) && allowBinding(context, selected)
                && currentEmbeddedLogical1(context, cardId);
    }

    public static boolean isSupportedProviderCommand(int what) {
        // Exact OEM ConnectedState protocol. The provider owns consent, card/EID
        // validation and N3 Binder-lifetime LPA leases; this is only admission.
        // Metadata101, download102, switch107, nickname108. Delete106, erase109/113,
        // retain110 and OTA112 remain denied; unknown values fail closed.
        return isReadCommand(what) || what == 101 || what == 102 || what == 107 || what == 108;
    }

    public static boolean isReadCommand(int what) {
        // Exact original ConnectedState switch: EID100/profile-list103/info105/memory115.
        return what == 100 || what == 103 || what == 105 || what == 115;
    }

    private static boolean currentEmbeddedLogical1(Context context, int cardId) {
        try {
            if (cardId < 0 || context == null) { return false; }
            Object service = context.getSystemService("phone");
            if (!(service instanceof TelephonyManager)) { return false; }
            TelephonyManager tm = (TelephonyManager) service;
            // Repeated framework observations are conservative vetoes, NOT vendor
            // type/route generation, continuity proof or serialized ownership.
            return matchingMap(tm.getSimSlotMapping()) && matchingCard(tm.getUiccCardsInfo(), cardId)
                    && matchingMap(tm.getSimSlotMapping()) && matchingCard(tm.getUiccCardsInfo(), cardId);
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    private static boolean matchingMap(Collection<UiccSlotMapping> map) {
        if (map == null || map.size() != 2) { return false; }
        boolean logical0 = false, logical1 = false;
        for (UiccSlotMapping slot : map) {
            if (slot == null) { return false; }
            if (slot.getLogicalSlotIndex() == 0 && slot.getPhysicalSlotIndex() == 0
                    && slot.getPortIndex() == 0 && !logical0) { logical0 = true; }
            else if (slot.getLogicalSlotIndex() == 1 && slot.getPhysicalSlotIndex() == 1
                    && slot.getPortIndex() == 1 && !logical1) { logical1 = true; }
            else { return false; }
        }
        return logical0 && logical1;
    }

    private static boolean matchingCard(List<UiccCardInfo> cards, int cardId) {
        if (cards == null || cards.isEmpty() || cards.size() > 2) { return false; }
        boolean target = false;
        for (UiccCardInfo card : cards) {
            if (card == null) { return false; }
            if (card.getCardId() == cardId) {
                if (target || card.getPhysicalSlotIndex() != 1 || !card.isEuicc()) { return false; }
                target = true;
                Collection<UiccPortInfo> ports = card.getPorts();
                if (ports == null) { return false; }
                boolean active = false;
                for (UiccPortInfo port : ports) {
                    if (port == null) { return false; }
                    if (port.isActive()) {
                        if (active || port.getPortIndex() != 1 || port.getLogicalSlotIndex() != 1) { return false; }
                        active = true;
                    }
                }
                if (!active) { return false; }
            }
        }
        return target;
    }

    private static boolean validComponent(PackageManager pm, ComponentInfo stale) {
        try {
            if (pm == null || stale == null || !PACKAGE.equals(stale.packageName)) {
                return false;
            }
            String permission;
            if (stale instanceof ServiceInfo) {
                permission = ((ServiceInfo) stale).permission;
                if (!ReleaseContract.SERVICE_CLASS.equals(stale.name)) {
                    return false;
                }
            } else if (stale instanceof ActivityInfo) {
                permission = ((ActivityInfo) stale).permission;
                if (!ReleaseContract.LUI_CLASS.equals(stale.name)
                        && !ReleaseContract.MANAGE_CLASS.equals(stale.name)
                        && !ReleaseContract.RESOLUTION_CLASS.equals(stale.name)) {
                    return false;
                }
            } else {
                return false;
            }
            if (!stale.enabled || !stale.exported || !BIND.equals(permission)
                    || !validApplication(stale.applicationInfo)
                    || pm.checkPermission(WRITE, PACKAGE) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            // Read installed package anew; do not trust a retained ResolveInfo certificate.
            PackageInfo installed = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
            if (installed == null || !validApplication(installed.applicationInfo)
                    || installed.signingInfo == null) {
                return false;
            }
            Signature[] signers = installed.signingInfo.getApkContentsSigners();
            return signers != null && signers.length == 1 && signers[0] != null
                    && ReleaseContract.CURRENT_SIGNER_SHA256.equals(hex(MessageDigest
                            .getInstance("SHA-256").digest(signers[0].toByteArray())));
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException
                | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static boolean validApplication(ApplicationInfo app) {
        return app != null && PACKAGE.equals(app.packageName) && app.enabled
                && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0;
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        String digits = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            chars[2 * i] = digits.charAt((bytes[i] & 255) >>> 4);
            chars[2 * i + 1] = digits.charAt(bytes[i] & 15);
        }
        return new String(chars);
    }
}
