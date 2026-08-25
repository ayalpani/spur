package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun ArInventoryRail(
    items: List<OwnedItem>,
    pendingItemIds: Set<String> = emptySet(),
    selectedItemId: String?,
    onSelect: (OwnedItem) -> Unit,
    available: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val hasItems = available && items.isNotEmpty()
    val itemStyle = secondaryMapControlStyle(LocalMapControlColors.current)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (hasItems) Color.Transparent else Color.White,
        shadowElevation = if (hasItems) 0.dp else MapControlElevation,
    ) {
        if (!available || items.isEmpty()) {
            Text(
                text = if (available) {
                    "Dein Inventar ist leer"
                } else {
                    "Das verschlüsselte Inventar ist gerade nicht verfügbar"
                },
                modifier = Modifier.padding(20.dp),
                color = Ink.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    val selected = item.id == selectedItemId
                    val pending = item.id in pendingItemIds
                    val shape = RoundedCornerShape(20.dp)
                    Surface(
                        onClick = { onSelect(item) },
                        enabled = !pending,
                        modifier = Modifier
                            .mapControlShadow(shape)
                            .semantics {
                                contentDescription = if (pending) {
                                    "${item.kind.displayName} ist noch nicht veröffentlicht"
                                } else {
                                    "${item.kind.displayName} im Raum ablegen"
                                }
                            },
                        shape = shape,
                        color = itemStyle.colors.background,
                        contentColor = itemStyle.colors.foreground,
                        border = itemStyle.border,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = painterResource(item.kind.mapImageResource),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = item.kind.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (pending) {
                                Text(
                                    text = "Noch nicht veröffentlicht",
                                    color = itemStyle.colors.foreground.copy(alpha = 0.62f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArClaimPrompt(
    item: PublicItem,
    distanceMeters: Double,
    accuracyMeters: Double,
    claiming: Boolean,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val claimable = nearbyItemPresentation(distanceMeters, accuracyMeters) ==
        NearbyItemPresentation.CLAIMABLE
    ArActionButton(
        label = when {
            claiming -> "Wird aufgenommen …"
            claimable -> "${item.kind.displayName} aufnehmen"
            accuracyMeters > ItemMaximumLocationAccuracyMeters -> "Standort wird genauer …"
            else -> "Noch ${ceil(distanceMeters - ItemClaimRadiusMeters).toInt().coerceAtLeast(1)} m näher"
        },
        enabled = claimable && !claiming,
        onClick = onClaim,
        modifier = modifier,
    )
}

@Composable
internal fun ArActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val style = secondaryMapControlStyle(LocalMapControlColors.current)
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = style.colors.background,
        contentColor = style.colors.foreground,
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(MapControlSize)
            .mapControlShadow(CircleShape),
        shape = CircleShape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = style.border,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun InventoryPage(
    inventory: ItemInventory,
    onBack: () -> Unit,
    available: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var selectedItemId by remember(inventory.items) {
        mutableStateOf(inventory.items.firstOrNull()?.id)
    }
    val selected = inventory.items.firstOrNull { it.id == selectedItemId }
    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 8.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    ChevronLeftIcon()
                }
                Text(
                    text = "Inventar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "Deine Items",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (!available || inventory.items.isEmpty()) {
                    Text(
                        text = if (available) {
                            "Alles liegt gerade draußen in der Welt."
                        } else {
                            "Das verschlüsselte Inventar ist gerade nicht verfügbar."
                        },
                        color = Ink.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                inventory.items.forEach { item ->
                    InventoryItemRow(
                        item = item,
                        selected = item.id == selectedItemId,
                        onClick = { selectedItemId = item.id },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                selected?.let { item ->
                    Spacer(modifier = Modifier.height(16.dp))
                    ItemProvenance(item)
                }
                if (inventory.pendingDrops.isNotEmpty() || inventory.pendingClaims.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = pendingTransferMessage(inventory),
                        color = Ink.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

internal fun pendingTransferMessage(inventory: ItemInventory): String = when {
    inventory.pendingDrops.isNotEmpty() -> {
        val count = inventory.pendingDrops.size
        "$count ${if (count == 1) "Item ist" else "Items sind"} noch nicht veröffentlicht. " +
            "Der Besitz bleibt auf diesem Gerät, bis der Server bestätigt."
    }
    inventory.pendingClaims.isNotEmpty() -> "Eine Aufnahme wird sicher fortgesetzt."
    else -> ""
}

@Composable
private fun InventoryItemRow(item: OwnedItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) NeutralSurface.copy(alpha = 0.16f) else Color.White,
        border = BorderStroke(1.dp, Ink.copy(alpha = if (selected) 0.5f else 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(item.kind.mapImageResource),
                contentDescription = null,
                modifier = Modifier.size(58.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = item.kind.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Generation ${item.generation}",
                    color = Ink.copy(alpha = 0.56f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ItemProvenance(item: OwnedItem) {
    Text(
        text = "Provenance",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (item.provenance.events.isEmpty()) {
        Text(
            text = "Dieses Item wurde noch nie abgelegt.",
            color = Ink.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        item.provenance.events.asReversed().forEach { event ->
            Text(
                text = "${event.dayUtc} · ungefähr ${event.coarseLatitude}, ${event.coarseLongitude}",
                modifier = Modifier.padding(vertical = 5.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun PublicItemDetails(
    item: PublicItem,
    onOpenInAr: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(item.kind.mapImageResource),
            contentDescription = null,
            modifier = Modifier.size(92.dp),
        )
        Text(
            text = item.kind.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Seit ${item.droppedDay} hier · ±${item.location.accuracyMeters.roundToInt()} m",
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            color = Ink.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyLarge,
        )
        val latest = item.publicProvenance.lastOrNull()
        if (latest != null) {
            Text(
                text = "Zuletzt ungefähr ${latest.coarseLatitude}, ${latest.coarseLongitude}",
                modifier = Modifier.padding(bottom = 18.dp),
                color = Ink.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SpurPrimaryButton(label = "In AR ansehen", onClick = onOpenInAr)
    }
}
