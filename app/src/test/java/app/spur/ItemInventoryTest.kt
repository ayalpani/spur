package app.spur

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class ItemInventoryTest {
    @Test
    fun `inventory codec and AES-GCM preserve secrets and provenance`() {
        val inventory = ItemInventory(
            items = listOf(testOwnedItem()),
            pendingClaims = listOf(
                PendingItemClaim(
                    itemId = "item-2",
                    idempotencyId = "claim-1",
                    newCapabilitySecret = ByteArray(32) { 7 },
                    location = ItemLocation(52.52, 13.405, 3.0),
                    phase = PendingClaimPhase.REQUESTING,
                ),
            ),
            pendingDrops = listOf(
                PendingItemDrop(
                    itemId = "item-1",
                    idempotencyId = "drop-1",
                    location = ItemLocation(52.521, 13.406, 4.0),
                ),
            ),
        )
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        val encrypted = ItemInventoryCipher.encrypt(ItemInventoryCodec.encode(inventory), key)
        assertFalse(encrypted.containsSequence(inventory.items.single().capabilitySecret))
        val decoded = ItemInventoryCodec.decode(ItemInventoryCipher.decrypt(encrypted, key))

        assertEquals(inventory.items.single().id, decoded.items.single().id)
        assertArrayEquals(inventory.items.single().capabilitySecret, decoded.items.single().capabilitySecret)
        assertEquals(inventory.items.single().provenance, decoded.items.single().provenance)
        assertArrayEquals(
            inventory.pendingClaims.single().newCapabilitySecret,
            decoded.pendingClaims.single().newCapabilitySecret,
        )
        assertEquals(inventory.pendingDrops, decoded.pendingDrops)
    }

    @Test(expected = AEADBadTagException::class)
    fun `tampered inventory fails authenticated decryption`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encrypted = ItemInventoryCipher.encrypt(byteArrayOf(1, 2, 3), key)
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()
        ItemInventoryCipher.decrypt(encrypted, key)
    }

    @Test
    fun `drop retains ownership until success and claim retains recovery until ack`() {
        val item = testOwnedItem()
        val machine = ItemTransferMachine(ItemInventory(items = listOf(item)))
        machine.beginDrop(item.id, "drop-1", ItemLocation(1.0, 2.0, 3.0))
        assertTrue(machine.inventory.items.any { it.id == item.id })
        machine.cancelDrop(item.id)
        assertTrue(machine.inventory.pendingDrops.isEmpty())
        machine.beginDrop(item.id, "drop-2", ItemLocation(1.0, 2.0, 3.0))
        machine.completeDrop(item.id)
        assertFalse(machine.inventory.items.any { it.id == item.id })

        val claim = machine.beginClaim(
            itemId = "found-item",
            idempotencyId = "claim-1",
            secret = ByteArray(32) { 8 },
            location = ItemLocation(1.0, 2.0, 3.0),
        )
        machine.receiveClaim(claim, item.copy(id = "found-item", capabilitySecret = claim.newCapabilitySecret))
        assertEquals(PendingClaimPhase.ACKNOWLEDGING, machine.inventory.pendingClaims.single().phase)
        machine.acknowledgeClaim("found-item")
        assertTrue(machine.inventory.pendingClaims.isEmpty())

        machine.beginClaim(
            itemId = "other-item",
            idempotencyId = "claim-2",
            secret = ByteArray(32) { 9 },
            location = ItemLocation(1.0, 2.0, 3.0),
        )
        machine.cancelClaim("other-item")
        assertTrue(machine.inventory.pendingClaims.isEmpty())
    }

    private fun testOwnedItem() = OwnedItem(
        id = "item-1",
        kind = ItemKind.STRAWBERRY,
        generation = 2,
        capabilitySecret = ByteArray(32) { it.toByte() },
        provenance = ProvenanceCapsule(
            events = listOf(
                ProvenanceEvent(
                    generation = 1,
                    dayUtc = LocalDate.of(2026, 8, 14),
                    coarseLatitude = "52.52",
                    coarseLongitude = "13.40",
                    previousHash = "abc",
                    serverSignature = "signature",
                ),
            ),
        ),
    )
}

private fun ByteArray.containsSequence(candidate: ByteArray): Boolean =
    indices.any { start ->
        start + candidate.size <= size && candidate.indices.all { offset ->
            this[start + offset] == candidate[offset]
        }
    }
