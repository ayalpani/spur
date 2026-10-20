package app.spur

import java.time.LocalDate

internal enum class ItemKind(
    val displayName: String,
    val mapImageResource: Int,
) {
    STRAWBERRY("Erdbeere", R.drawable.item_strawberry),
    PEAR("Birne", R.drawable.item_pear),
    BANANA("Banane", R.drawable.item_banana),
}

internal data class ProvenanceEvent(
    val generation: Int,
    val dayUtc: LocalDate,
    val coarseLatitude: String,
    val coarseLongitude: String,
    val previousHash: String,
    val serverSignature: String,
)

internal data class ProvenanceCapsule(
    val events: List<ProvenanceEvent> = emptyList(),
)

internal data class OwnedItem(
    val id: String,
    val kind: ItemKind,
    val generation: Int,
    val capabilitySecret: ByteArray,
    val provenance: ProvenanceCapsule,
)

internal data class ItemLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
)

internal data class PublicItem(
    val id: String,
    val kind: ItemKind,
    val generation: Int,
    val location: ItemLocation,
    val droppedDay: LocalDate,
    val publicProvenance: List<ProvenanceEvent>,
)

internal data class PublicItemCluster(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
)

internal data class PublicItemsPage(
    val items: List<PublicItem>,
    val clusters: List<PublicItemCluster>,
)

internal data class ItemMapBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

internal enum class PendingClaimPhase {
    REQUESTING,
    ACKNOWLEDGING,
}

internal data class PendingItemClaim(
    val itemId: String,
    val idempotencyId: String,
    val newCapabilitySecret: ByteArray,
    val location: ItemLocation,
    val phase: PendingClaimPhase,
)

internal data class PendingItemDrop(
    val itemId: String,
    val idempotencyId: String,
    val location: ItemLocation,
)

internal data class ItemInventory(
    val items: List<OwnedItem> = emptyList(),
    val pendingClaims: List<PendingItemClaim> = emptyList(),
    val pendingDrops: List<PendingItemDrop> = emptyList(),
)

internal const val ItemDiscoveryRadiusMeters = 50.0
internal const val ItemMaximumLocationAccuracyMeters = 15.0
internal const val ItemMaximumLocationAgeMillis = 10_000L
