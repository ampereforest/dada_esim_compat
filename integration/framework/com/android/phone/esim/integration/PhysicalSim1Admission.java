package com.android.phone.esim.integration;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.UiccSlotMapping;
import java.util.Collection;
import java.util.List;

/**
 * Read-only, conservative admission veto for the original physical0/port0/logical0
 * UICC-app branch. Fresh platform queries are not authoritative modem readback,
 * original-caller authorization, serialization, continuity or a logical1 lease.
 * The real target API/access/runtime review remains mandatory before release.
 */
final class PhysicalSim1Admission {
    private PhysicalSim1Admission() {}

    static boolean isCurrent(Context context, int subId, int logicalSlot) {
        if (context == null || subId < 0 || logicalSlot != 0) {
            return false;
        }
        try {
            Object subscriptions = context.getSystemService("telephony_subscription_service");
            Object telephony = context.getSystemService("phone");
            if (!(subscriptions instanceof SubscriptionManager)
                    || !(telephony instanceof TelephonyManager)) {
                return false;
            }
            SubscriptionManager sm = (SubscriptionManager) subscriptions;
            TelephonyManager tm = (TelephonyManager) telephony;
            int before = mapKind(tm.getSimSlotMapping());
            if (before == 0) {
                return false;
            }
            String first = currentTarget(sm, tm, subId);
            if (first == null || mapKind(tm.getSimSlotMapping()) != before) {
                return false;
            }
            String second = currentTarget(sm, tm, subId);
            return first.equals(second) && mapKind(tm.getSimSlotMapping()) == before;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** Both approved maps; order-independent, exact two logical entries, no fallback. */
    private static int mapKind(Collection<UiccSlotMapping> mapping) {
        if (mapping == null || mapping.size() != 2) {
            return 0;
        }
        boolean physical0 = false;
        int logical1Port = -1;
        for (UiccSlotMapping entry : mapping) {
            if (entry == null) {
                return 0;
            }
            int logical = entry.getLogicalSlotIndex();
            int physical = entry.getPhysicalSlotIndex();
            int port = entry.getPortIndex();
            if (logical == 0 && physical == 0 && port == 0 && !physical0) {
                physical0 = true;
            } else if (logical == 1 && physical == 1
                    && (port == 0 || port == 1) && logical1Port == -1) {
                logical1Port = port;
            } else {
                return 0;
            }
        }
        return physical0 && logical1Port != -1 ? logical1Port + 1 : 0;
    }

    private static String currentTarget(SubscriptionManager sm, TelephonyManager tm, int subId) {
        // Available-by-ID, not an active-only or OEM cached-list classification.
        List<SubscriptionInfo> available = sm.getAvailableSubscriptionInfoList();
        if (available == null || available.size() == 0 || available.size() > 64) {
            return null;
        }
        SubscriptionInfo target = null;
        for (SubscriptionInfo info : available) {
            if (info == null) {
                return null;
            }
            if (info.getSubscriptionId() == subId) {
                if (target != null) {
                    return null;
                }
                target = info;
            }
        }
        if (target == null || target.isEmbedded()
                || target.getSubscriptionType() != SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL
                || target.getCardId() < 0 || target.getPortIndex() != 0) {
            return null;
        }
        int slot = target.getSimSlotIndex();
        if (slot != 0 && slot != -1) {
            return null;
        }
        String iccId = target.getIccId();
        if (!validIccId(iccId)) {
            return null;
        }
        for (SubscriptionInfo info : available) {
            if (info != target && (info.getSubscriptionId() == subId
                    || info.getCardId() == target.getCardId()
                    || iccId.equals(info.getIccId())
                    || info.getSimSlotIndex() == 0)) {
                return null;
            }
        }
        List<UiccCardInfo> cards = tm.getUiccCardsInfo();
        if (cards == null || cards.size() == 0 || cards.size() > 2) {
            return null;
        }
        UiccCardInfo physical0 = null;
        boolean physical1 = false;
        for (UiccCardInfo card : cards) {
            if (card == null) {
                return null;
            }
            int physical = card.getPhysicalSlotIndex();
            if (physical == 0 && physical0 == null) {
                physical0 = card;
            } else if (physical == 1 && !physical1) {
                physical1 = true;
            } else {
                return null;
            }
            if (physical != 0 && card.getCardId() == target.getCardId()) {
                return null;
            }
        }
        if (physical0 == null || physical0.getCardId() != target.getCardId()
                || physical0.isEuicc() || !physical0.isRemovable()
                || physical0.isMultipleEnabledProfilesSupported()) {
            return null;
        }
        Collection<UiccPortInfo> ports = physical0.getPorts();
        if (ports == null || ports.size() != 1) {
            return null;
        }
        UiccPortInfo port = ports.iterator().next();
        if (port == null || !port.isActive() || port.getPortIndex() != 0
                || port.getLogicalSlotIndex() != 0 || !iccId.equals(port.getIccId())) {
            return null;
        }
        // Private transient comparison only; never logged, stored or returned to callers.
        return subId + ":" + target.getCardId() + ":" + slot + ":" + iccId;
    }

    private static boolean validIccId(String value) {
        if (value == null || value.length() == 0 || value.length() > 22) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }
}
