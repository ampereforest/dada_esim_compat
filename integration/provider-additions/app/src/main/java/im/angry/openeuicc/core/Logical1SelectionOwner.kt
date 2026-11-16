package im.angry.openeuicc.core

/** Port:physical:logical, deliberately independent of the OEM hidden SIM-type field. */
data class Logical1MapEntry(val port: Int, val physical: Int, val logical: Int)

enum class Logical1Destination { PhysicalSim2, Euicc }

data class Logical1SelectionRequest(
    val operationId: String,
    val destination: Logical1Destination,
    val completeMap: List<Logical1MapEntry>,
) {
    fun validate() {
        require(operationId.isNotBlank())
        val secondPort = if (destination == Logical1Destination.Euicc) 1 else 0
        require(completeMap.size == 2 && completeMap.toSet() == setOf(
            Logical1MapEntry(0, 0, 0), Logical1MapEntry(secondPort, 1, 1))) {
            "Only the two complete logical0-preserving maps are allowed"
        }
    }
}

sealed interface Logical1SelectionOutcome {
    data object Unavailable : Logical1SelectionOutcome
    // No success implementation is supplied: acknowledgement is not authoritative readback.
}

interface Logical1SelectionOwner : ProfileOperationCoordinator {
    /**
     * Main must supply a protected owner with caller authentication, board readiness, intentional
     * hidden SIM-type normalization, route/map/identity readback, bounded pending/unknown results
     * and durable confirmation. A local mutex, Android mapping GET or callback is not that owner.
     * This interface is not an exported Binder endpoint and cannot grant board access.
     */
    suspend fun select(request: Logical1SelectionRequest): Logical1SelectionOutcome
}

class Logical1OwnerUnavailableException : Exception("Protected logical1 owner is unavailable")

/** Production default: no mapping/GPIO/UIM/profile mutation, including purported no-ops. */
object UnavailableLogical1SelectionOwner : Logical1SelectionOwner {
    override suspend fun select(request: Logical1SelectionRequest): Logical1SelectionOutcome {
        request.validate()
        return Logical1SelectionOutcome.Unavailable
    }

    override suspend fun <T> withProfileOperation(context: EuiccOperationContext, operation: ProfileOperation,
                                                block: suspend () -> T): T {
        throw Logical1OwnerUnavailableException()
    }
}
