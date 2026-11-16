package org.sourceowned.n3.runtime.v2;

import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import java.util.ArrayList;
import java.util.List;
import org.sourceowned.n3.runtime.v2.core.Model;
import org.sourceowned.n3.runtime.v2.dada.AndroidDadaMapping;

/** Actual platform GETs, deliberately NOT core Observation/generation/card/profile authority. */
public final class FrameworkDiagnostics {
    private final TelephonyManager telephony;
    private final AndroidDadaMapping mapping;
    public FrameworkDiagnostics(TelephonyManager telephony, AndroidDadaMapping mapping) {
        this.telephony = telephony; this.mapping = mapping;
    }
    public Bundle read() {
        Bundle out = new Bundle();
        out.putString("vendor_route", "SEE_OPLUS_ROUTE_POTENTIALLY_CACHED");
        out.putString("normalized_type", "UNAVAILABLE");
        out.putString("card_identity_authority", "UNAVAILABLE");
        out.putString("enabled_profile", "UNAVAILABLE_NOT_VERIFIED_EMPTY");
        out.putString("generation", "UNAVAILABLE");
        out.putString("completion", "UNAVAILABLE");
        out.putString("evidence_scope", "FRAMEWORK_GETS_ONLY_NOT_ATOMIC_OR_VENDOR_AUTHORITY");
        try {
            Model.FullMap map = mapping.read();
            out.putString("framework_map_status", "COMPLETE_SUPPORTED");
            out.putString("framework_complete_map", map.toString());
        } catch (Exception unavailable) { out.putString("framework_map_status", "UNAVAILABLE_OR_UNSUPPORTED"); }
        try {
            List<UiccCardInfo> cards = telephony.getUiccCardsInfo();
            if (cards == null || cards.size() > 4) throw new IllegalStateException();
            ArrayList<String> rows = new ArrayList<>();
            for (UiccCardInfo card : cards) {
                // Never get EID, ICCID, public cardId or raw profile identifiers for diagnostics.
                for (UiccPortInfo port : card.getPorts()) {
                    if (rows.size() >= 8) throw new IllegalStateException();
                    rows.add("physical=" + card.getPhysicalSlotIndex() + ",port=" + port.getPortIndex()
                            + ",logical=" + port.getLogicalSlotIndex() + ",active=" + port.isActive()
                            + ",euicc=" + card.isEuicc());
                }
            }
            out.putString("framework_card_status", cards.isEmpty() ? "NO_REPORTED_CARDS_NOT_PROVEN_ABSENCE" : "REPORTED");
            out.putStringArrayList("framework_card_ports", rows);
        } catch (Exception unavailable) { out.putString("framework_card_status", "UNAVAILABLE"); }
        return out;
    }
}
