package im.angry.openeuicc.n3

import android.os.Bundle
import org.json.JSONObject
import org.json.JSONArray

/** Explicit field/value allowlist: never serialize an owner Bundle, token, error or identifiers. */
internal object N3Report {
    private val statuses = setOf(
        "COMMITTED", "PENDING_ROUTE_READBACK", "FAILED_NOT_STARTED", "ESIM_ROUTE_REQUIRED",
        "REAL_READBACK_SUBSET", "CONFIRM_EXPLICIT_ROUTE_IN_FOREGROUND_UI",
        "MAP_MATCH_NO_WRITES_ROUTE_TYPE_NOT_VERIFIED", "OWNER_BUSY_RECONFIRM_REQUIRED",
        "STALE_MAP_RECONFIRM_REQUIRED", "DURABLE_PENDING_REQUIRES_RECONCILIATION",
        "CONSENT_REVOKED_OR_CALL_SIM_UNSAFE", "DURABILITY_FAILED_NO_ADMISSION",
        "REVOKED_OR_CHANGED_DURABLE_PENDING", "PENDING_MODEM_NOT_PROVEN_COMPLETE",
        "REVOKED_OR_UNSAFE_AFTER_MODEM_PENDING", "CHANGED_BEFORE_MAP_PENDING",
        "PENDING_AUTHORITATIVE_GENERATION_PROFILE_QUIESCENCE", "UNKNOWN_AFTER_PREPARED_INTENT",
        "GET_UNAVAILABLE_NO_ADMISSION", "UNKNOWN_AFTER_ADMISSION",
        "ABORTED_ORIGINAL_ROUTE", "ABORT_FAILED_DURABLE_PENDING")
    fun status(bundle: Bundle): String = (bundle.get("status") as? String)
        ?.takeIf { it in statuses } ?: "UNRECOGNIZED_NOT_COMMITTED"
    fun valid(bundle: Bundle) = bundle.get("protocol") == 2 && bundle.get("lpa_leases_supported") is Boolean
    fun pending(bundle: Bundle) = bundle.get("durable_pending") == true

    fun json(bundle: Bundle): String {
        if (!valid(bundle)) return unavailable("InvalidProtocol")
        val out = JSONObject().put("schema", 2).put("protocol", 2).put("status", status(bundle))
            .put("lpa_leases_supported", bundle.getBoolean("lpa_leases_supported"))
            .put("route_committed", status(bundle) == "COMMITTED" && !pending(bundle))
        for (key in listOf("durable_pending", "existing_pending", "admitted")) {
            (bundle.get(key) as? Boolean)?.let { out.put(key, it) }
        }
        for (key in listOf("modem_ack", "map_ack")) {
            val value = bundle.get(key) as? String
            if (value in setOf("NOT_STARTED", "ACCEPTED", "AMBIGUOUS", "MATCH_NO_WRITE")) out.put(key, value)
        }
        enumField(bundle, out, "framework_map_status", setOf("COMPLETE_SUPPORTED", "UNAVAILABLE_OR_UNSUPPORTED"))
        val map = bundle.get("framework_complete_map") as? String
        // FullMap.Row is port:physical:logical; only the two supported complete maps leave here.
        if (map in setOf("[0:0:0, 0:1:1]", "[0:0:0, 1:1:1]")) out.put("framework_complete_map", map)
        enumField(bundle, out, "framework_card_status", setOf("REPORTED", "UNAVAILABLE", "NO_REPORTED_CARDS_NOT_PROVEN_ABSENCE"))
        val ports = bundle.get("framework_card_ports") as? ArrayList<*>
        val row = Regex("physical=[01],port=[01],logical=(-1|[01]),active=(true|false),euicc=(true|false)")
        if (ports != null && ports.size <= 8 && ports.all { it is String && row.matches(it) }) {
            out.put("framework_card_ports", JSONArray(ports))
        }
        out.put("authority", "FRAMEWORK_MAP_AND_ACTIVE_PORT")
        out.put("normalized_type", "UNAVAILABLE").put("generation", "UNAVAILABLE")
        out.put("enabled_profile", "UNAVAILABLE_NOT_VERIFIED_EMPTY")
        val oplus = bundle.get("oplus_route") as? Bundle
        if (oplus != null) {
            val nested = JSONObject().put("fresh_type_callback", false)
            enumField(oplus, nested, "status", setOf("REPORTED_MAY_BE_CACHED", "UNAVAILABLE_VALUE", "UNAVAILABLE_OR_TIMED_OUT"))
            (oplus.get("gpio_status") as? Int)?.takeIf { it in 0..1 }?.let { nested.put("gpio_status", it) }
            out.put("oplus_route", nested)
        }
        val phone = bundle.get("phone_type") as? Bundle
        if (phone != null) {
            val nested = JSONObject()
            enumField(phone, nested, "status", setOf("PHONE_SOURCE_INTEGRATION_REQUIRED", "PHONE_BRIDGE_UNAVAILABLE_OR_TIMED_OUT"))
            // Fresh phone evidence is not integrated here; never turn reported type into authority.
            nested.put("fresh_type_callback", false)
            out.put("phone_type", nested)
        }
        val slots = bundle.get("framework_slots") as? Bundle
        if (slots != null) {
            val nested = JSONObject()
            for (i in 0..1) {
                (slots.get("slot${i}_card_state") as? Int)?.takeIf { it in 0..3 }?.let { nested.put("slot${i}_card_state", it) }
                (slots.get("sim_state$i") as? Int)?.takeIf { it in 0..11 }?.let { nested.put("sim_state$i", it) }
                for (suffix in listOf("euicc", "active")) (slots.get("slot${i}_$suffix") as? Boolean)?.let { nested.put("slot${i}_$suffix", it) }
            }
            out.put("framework_slots", nested)
        }
        return out.toString()
    }
    private fun enumField(bundle: Bundle, out: JSONObject, key: String, allowed: Set<String>) {
        val value = bundle.get(key) as? String
        if (value in allowed) out.put(key, value)
    }
    fun unavailable(error: String = "UnavailableOrTimedOut"): String = JSONObject()
        .put("schema", 2).put("status", "unavailable").put("error_class", error)
        .put("authoritative_committed", false).put("lpa_leases_supported", false).toString()
}
