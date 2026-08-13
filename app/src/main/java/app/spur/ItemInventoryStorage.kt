package app.spur

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object ItemInventoryCodec {
    private const val FormatVersion = 1

    fun encode(inventory: ItemInventory): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(FormatVersion)
            output.writeInt(inventory.items.size)
            inventory.items.forEach { output.writeOwnedItem(it) }
            output.writeInt(inventory.pendingClaims.size)
            inventory.pendingClaims.forEach { claim ->
                output.writeUTF(claim.itemId)
                output.writeUTF(claim.idempotencyId)
                output.writeByteArray(claim.newCapabilitySecret)
                output.writeLocation(claim.location)
                output.writeUTF(claim.phase.name)
            }
            output.writeInt(inventory.pendingDrops.size)
            inventory.pendingDrops.forEach { drop ->
                output.writeUTF(drop.itemId)
                output.writeUTF(drop.idempotencyId)
                output.writeLocation(drop.location)
            }
        }
        bytes.toByteArray()
    }

    fun decode(encoded: ByteArray): ItemInventory = DataInputStream(ByteArrayInputStream(encoded)).use { input ->
        require(input.readInt() == FormatVersion) { "Unbekanntes Inventarformat" }
        val items = List(input.readBoundedCount()) { input.readOwnedItem() }
        val pending = List(input.readBoundedCount()) {
            PendingItemClaim(
                itemId = input.readUTF(),
                idempotencyId = input.readUTF(),
                newCapabilitySecret = input.readByteArray(),
                location = input.readLocation(),
                phase = PendingClaimPhase.valueOf(input.readUTF()),
            )
        }
        val drops = List(input.readBoundedCount()) {
            PendingItemDrop(
                itemId = input.readUTF(),
                idempotencyId = input.readUTF(),
                location = input.readLocation(),
            )
        }
        require(input.available() == 0) { "Unerwartete Inventardaten" }
        ItemInventory(items = items, pendingClaims = pending, pendingDrops = drops)
    }

    private fun DataOutputStream.writeOwnedItem(item: OwnedItem) {
        writeUTF(item.id)
        writeUTF(item.kind.name)
        writeInt(item.generation)
        writeByteArray(item.capabilitySecret)
        writeInt(item.provenance.events.size)
        item.provenance.events.forEach { event ->
            writeInt(event.generation)
            writeUTF(event.dayUtc.toString())
            writeUTF(event.coarseLatitude)
            writeUTF(event.coarseLongitude)
            writeUTF(event.previousHash)
            writeUTF(event.serverSignature)
        }
    }

    private fun DataInputStream.readOwnedItem(): OwnedItem = OwnedItem(
        id = readUTF(),
        kind = ItemKind.valueOf(readUTF()),
        generation = readInt(),
        capabilitySecret = readByteArray().also { require(it.size == 32) },
        provenance = ProvenanceCapsule(
            events = List(readBoundedCount()) {
                ProvenanceEvent(
                    generation = readInt(),
                    dayUtc = LocalDate.parse(readUTF()),
                    coarseLatitude = readUTF(),
                    coarseLongitude = readUTF(),
                    previousHash = readUTF(),
                    serverSignature = readUTF(),
                )
            },
        ),
    )

    private fun DataOutputStream.writeByteArray(value: ByteArray) {
        writeInt(value.size)
        write(value)
    }

    private fun DataOutputStream.writeLocation(location: ItemLocation) {
        writeDouble(location.latitude)
        writeDouble(location.longitude)
        writeDouble(location.accuracyMeters)
    }

    private fun DataInputStream.readLocation() = ItemLocation(
        latitude = readDouble(),
        longitude = readDouble(),
        accuracyMeters = readDouble(),
    )

    private fun DataInputStream.readByteArray(): ByteArray {
        val size = readInt()
        require(size in 1..4096) { "Ungültige Feldlänge" }
        return ByteArray(size).also(::readFully)
    }

    private fun DataInputStream.readBoundedCount(): Int = readInt().also {
        require(it in 0..10_000) { "Ungültige Eintragszahl" }
    }
}

internal object ItemInventoryCipher {
    private const val NonceBytes = 12

    fun encrypt(cleartext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Android Keystore keys require the provider to generate the GCM IV. Supplying even a
        // cryptographically random caller IV is rejected when randomized encryption is required.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv.also { require(it.size == NonceBytes) }
        return nonce + cipher.doFinal(cleartext)
    }

    fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray {
        require(encrypted.size > NonceBytes) { "Inventardatei ist unvollständig" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(0, NonceBytes)))
        return cipher.doFinal(encrypted.copyOfRange(NonceBytes, encrypted.size))
    }
}

internal class ItemInventoryStore(private val context: Context) {
    private val file = AtomicFile(context.filesDir.resolve("item-inventory.bin"))
    private val preferences = context.getSharedPreferences("item-inventory", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): ItemInventory {
        if (!file.baseFile.exists()) {
            if (preferences.getBoolean("initialized", false)) return ItemInventory()
            return starterInventory().also(::save)
        }
        val encrypted = file.openRead().use { it.readBytes() }
        return ItemInventoryCodec.decode(ItemInventoryCipher.decrypt(encrypted, inventoryKey()))
    }

    @Synchronized
    fun save(inventory: ItemInventory) {
        val encrypted = ItemInventoryCipher.encrypt(ItemInventoryCodec.encode(inventory), inventoryKey())
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
            preferences.edit().putBoolean("initialized", true).commit()
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    private fun inventoryKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun starterInventory(): ItemInventory = ItemInventory(
        items = ItemKind.entries.map { kind ->
            OwnedItem(
                id = UUID.randomUUID().toString(),
                kind = kind,
                generation = 0,
                capabilitySecret = ByteArray(32).also(SecureRandom()::nextBytes),
                provenance = ProvenanceCapsule(),
            )
        },
    )

    private companion object {
        const val KeyAlias = "spur-item-inventory-v1"
    }
}

internal class ItemTransferMachine(initial: ItemInventory) {
    var inventory: ItemInventory = initial
        private set

    fun beginClaim(
        itemId: String,
        idempotencyId: String,
        secret: ByteArray,
        location: ItemLocation,
    ): PendingItemClaim {
        require(inventory.pendingClaims.none { it.itemId == itemId })
        val claim = PendingItemClaim(
            itemId,
            idempotencyId,
            secret,
            location,
            PendingClaimPhase.REQUESTING,
        )
        inventory = inventory.copy(pendingClaims = inventory.pendingClaims + claim)
        return claim
    }

    fun receiveClaim(claim: PendingItemClaim, item: OwnedItem) {
        require(item.id == claim.itemId)
        inventory = inventory.copy(
            items = inventory.items.filterNot { it.id == item.id } + item,
            pendingClaims = inventory.pendingClaims.map {
                if (it.itemId == claim.itemId) it.copy(phase = PendingClaimPhase.ACKNOWLEDGING) else it
            },
        )
    }

    fun acknowledgeClaim(itemId: String) {
        inventory = inventory.copy(pendingClaims = inventory.pendingClaims.filterNot { it.itemId == itemId })
    }

    fun cancelClaim(itemId: String) {
        inventory = inventory.copy(pendingClaims = inventory.pendingClaims.filterNot { it.itemId == itemId })
    }

    fun completeDrop(itemId: String) {
        inventory = inventory.copy(
            items = inventory.items.filterNot { it.id == itemId },
            pendingDrops = inventory.pendingDrops.filterNot { it.itemId == itemId },
        )
    }

    fun beginDrop(itemId: String, idempotencyId: String, location: ItemLocation): PendingItemDrop {
        require(inventory.items.any { it.id == itemId })
        val existing = inventory.pendingDrops.firstOrNull { it.itemId == itemId }
        if (existing != null) return existing
        val drop = PendingItemDrop(itemId, idempotencyId, location)
        inventory = inventory.copy(pendingDrops = inventory.pendingDrops + drop)
        return drop
    }

    fun cancelDrop(itemId: String) {
        inventory = inventory.copy(pendingDrops = inventory.pendingDrops.filterNot { it.itemId == itemId })
    }
}
