package im.angry.openeuicc.util

import android.content.Intent
import android.service.euicc.EuiccService
import android.telephony.TelephonyManager
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.core.EuiccOperationContext
import im.angry.openeuicc.core.withValidatedEuiccChannel

/** Explicit protected native-UI contract. A real cardId is never a slot/synthetic ID. */
object EuiccUiContextResolver {
    const val EXTRA_CARD_ID = "im.angry.openeuicc.extra.CARD_ID"
    const val EXTRA_PHYSICAL_SLOT_ID = "im.angry.openeuicc.extra.PHYSICAL_SLOT_ID"
    const val EXTRA_PORT_ID = "im.angry.openeuicc.extra.PORT_ID"
    const val EXTRA_SECURE_ELEMENT_ID = "im.angry.openeuicc.extra.SECURE_ELEMENT_ID"
    const val EXTRA_PROFILE_ICCID = "im.angry.openeuicc.extra.PROFILE_ICCID"

    suspend fun resolve(intent: Intent, manager: EuiccChannelManager, tm: TelephonyManager,
                        restored: EuiccOperationContext? = null): EuiccOperationContext {
        val cards = tm.uiccCardsInfoCompat
        val provided = intent.getBundleExtra(EuiccOperationContext.EXTRA)?.let(EuiccOperationContext::fromBundle)
        val expected = restored ?: provided
        if (restored != null && provided != null) check(restored == provided)
        val cardKey = when {
            intent.hasExtra(EXTRA_CARD_ID) -> EXTRA_CARD_ID
            intent.hasExtra(EuiccService.EXTRA_RESOLUTION_CARD_ID) -> EuiccService.EXTRA_RESOLUTION_CARD_ID
            else -> null
        }
        val requestedCard = cardKey?.let { intent.getIntExtra(it, -1).also { id -> require(id >= 0) } }
            ?: expected?.cardId
        val card = checkNotNull(cards.singleOrNull { it.isEuicc && (requestedCard == null || it.cardId == requestedCard) })
        check(card.cardId >= 0 && cards.count { it.cardId == card.cardId } == 1 &&
            cards.count { it.physicalSlotIndex == card.physicalSlotIndex } == 1)
        if (intent.hasExtra(EXTRA_PHYSICAL_SLOT_ID)) check(intent.getIntExtra(EXTRA_PHYSICAL_SLOT_ID, -1) == card.physicalSlotIndex)
        val portKey = when {
            intent.hasExtra(EXTRA_PORT_ID) -> EXTRA_PORT_ID
            intent.hasExtra(EuiccService.EXTRA_RESOLUTION_PORT_INDEX) -> EuiccService.EXTRA_RESOLUTION_PORT_INDEX
            else -> null
        }
        val requestedPort = portKey?.let { intent.getIntExtra(it, -1) } ?: expected?.portId
        if (requestedPort != null && requestedPort < 0) {
            // Only the platform's NO_PRIVILEGES action allows a separately resolved port.
            check(requestedPort == -1 && intent.action == EuiccService.ACTION_RESOLVE_NO_PRIVILEGES)
        }
        val port = checkNotNull(card.ports.singleOrNull {
            it.logicalSlotIndex >= 0 && (requestedPort == null || requestedPort == -1 || it.portIndex == requestedPort)
        })
        val se = intent.getIntExtra(EXTRA_SECURE_ELEMENT_ID, expected?.secureElementId ?: 0)
        val profile = intent.getStringExtra(EXTRA_PROFILE_ICCID) ?: expected?.profileIccid
        val context = expected ?: manager.withEuiccChannel(card.physicalSlotIndex, port.portIndex,
            EuiccChannel.SecureElementId.createFromInt(se)) { channel ->
            EuiccOperationContext.capture(channel, intent.action, profileIccid = profile)
        }
        check(context.cardId == card.cardId && context.physicalSlotId == card.physicalSlotIndex &&
            context.portId == port.portIndex && context.logicalSlotId == port.logicalSlotIndex &&
            context.secureElementId == se && context.profileIccid == profile &&
            (intent.action in setOf(EuiccService.ACTION_RESOLVE_NO_PRIVILEGES, EuiccService.ACTION_RESOLVE_DEACTIVATE_SIM,
                EuiccService.ACTION_RESOLVE_RESOLVABLE_ERRORS) || context.incomingAction == intent.action))
        manager.withValidatedEuiccChannel(context) { channel ->
            if (profile != null) check(channel.lpa.profiles.count { it.iccid == profile } == 1)
            context.requireMatches(channel)
        }
        val current = checkNotNull(tm.uiccCardsInfoCompat.singleOrNull { it.cardId == card.cardId && it.isEuicc })
        check(current.physicalSlotIndex == context.physicalSlotId &&
            current.ports.singleOrNull { it.portIndex == context.portId }?.logicalSlotIndex == context.logicalSlotId)
        current.eid?.takeIf { it.isNotBlank() }?.let { check(EuiccOperationContext.bindEid(it) == context.eidBinding) }
        return context
    }
}
