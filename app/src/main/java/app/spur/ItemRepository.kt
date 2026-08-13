package app.spur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

internal class ItemRepository(
    private val store: ItemInventoryStore,
    private val api: ItemsApi,
) {
    private val mutableInventory = MutableStateFlow(store.load())
    val inventory: StateFlow<ItemInventory> = mutableInventory

    suspend fun drop(item: OwnedItem, location: ItemLocation): PublicItem {
        val machine = ItemTransferMachine(mutableInventory.value)
        val pending = machine.beginDrop(item.id, UUID.randomUUID().toString(), location)
        persist(machine.inventory)
        val publicItem = try {
            api.drop(item, pending)
        } catch (failure: Throwable) {
            if (failure.isDefinitiveClientFailure()) {
                machine.cancelDrop(item.id)
                persist(machine.inventory)
            }
            throw failure
        }
        machine.completeDrop(item.id)
        persist(machine.inventory)
        return publicItem
    }

    suspend fun retryDrop(itemId: String): PublicItem? {
        val inventory = mutableInventory.value
        val pending = inventory.pendingDrops.firstOrNull { it.itemId == itemId } ?: return null
        val item = inventory.items.firstOrNull { it.id == itemId } ?: return null
        val publicItem = api.drop(item, pending)
        val machine = ItemTransferMachine(inventory)
        machine.completeDrop(item.id)
        persist(machine.inventory)
        return publicItem
    }

    suspend fun claim(item: PublicItem, location: ItemLocation): OwnedItem {
        val machine = ItemTransferMachine(mutableInventory.value)
        val pending = machine.beginClaim(
            itemId = item.id,
            idempotencyId = UUID.randomUUID().toString(),
            secret = ByteArray(32).also(SecureRandom()::nextBytes),
            location = location,
        )
        persist(machine.inventory)
        val owned = try {
            api.claim(item, pending)
        } catch (failure: Throwable) {
            if (failure.isDefinitiveClientFailure()) {
                machine.cancelClaim(item.id)
                persist(machine.inventory)
            }
            throw failure
        }
        machine.receiveClaim(pending, owned)
        persist(machine.inventory)
        runCatching {
            acknowledge(machine, pending.copy(phase = PendingClaimPhase.ACKNOWLEDGING))
        }
        return owned
    }

    suspend fun resumeTransfers() {
        mutableInventory.value.pendingDrops.toList().forEach { pending ->
            runCatching { retryDrop(pending.itemId) }.onFailure { failure ->
                if (failure.isDefinitiveClientFailure()) {
                    val machine = ItemTransferMachine(mutableInventory.value)
                    machine.cancelDrop(pending.itemId)
                    persist(machine.inventory)
                }
            }
        }
        mutableInventory.value.pendingClaims.toList().forEach { pending ->
            runCatching { resumeClaim(pending) }.onFailure { failure ->
                if (failure.isDefinitiveClientFailure()) {
                    val machine = ItemTransferMachine(mutableInventory.value)
                    machine.cancelClaim(pending.itemId)
                    persist(machine.inventory)
                }
            }
        }
    }

    private suspend fun resumeClaim(pending: PendingItemClaim) {
        val machine = ItemTransferMachine(mutableInventory.value)
        val owned = if (pending.phase == PendingClaimPhase.ACKNOWLEDGING) {
            mutableInventory.value.items.firstOrNull { it.id == pending.itemId }
        } else {
            runCatching { api.recover(pending) }.getOrElse { failure ->
                if (failure is ItemsApiException && failure.status in listOf(401, 404)) {
                    val public = api.detail(pending.itemId)
                    api.claim(public, pending)
                } else {
                    throw failure
                }
            }
        }
        if (owned != null && pending.phase == PendingClaimPhase.REQUESTING) {
            machine.receiveClaim(pending, owned)
            persist(machine.inventory)
        }
        acknowledge(machine, pending.copy(phase = PendingClaimPhase.ACKNOWLEDGING))
    }

    private suspend fun acknowledge(machine: ItemTransferMachine, pending: PendingItemClaim) {
        api.acknowledge(pending)
        machine.acknowledgeClaim(pending.itemId)
        persist(machine.inventory)
    }

    private suspend fun persist(value: ItemInventory) = withContext(Dispatchers.IO) {
        store.save(value)
        mutableInventory.value = value
    }
}

private fun Throwable.isDefinitiveClientFailure(): Boolean =
    this is ItemsApiException && status in 400..499
