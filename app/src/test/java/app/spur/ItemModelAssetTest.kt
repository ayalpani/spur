package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ItemModelAssetTest {
    @Test
    fun `fruit models bundle their textured materials`() {
        ItemKind.entries.forEach { kind ->
            val bytes = assetFile(kind.modelAsset).readBytes()
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            assertEquals("${kind.name} is a GLB", 0x46546C67, header.getInt(0))
            assertEquals("${kind.name} uses glTF 2", 2, header.getInt(4))
            assertEquals("${kind.name} declares its complete size", bytes.size, header.getInt(8))

            val jsonLength = header.getInt(12)
            assertEquals("${kind.name} starts with a JSON chunk", 0x4E4F534A, header.getInt(16))
            val json = bytes.copyOfRange(20, 20 + jsonLength).decodeToString()

            assertTrue("${kind.name} has UV coordinates", json.contains("\"TEXCOORD_0\""))
            assertTrue("${kind.name} uses a base-color texture", json.contains("\"baseColorTexture\""))
            assertTrue("${kind.name} embeds a PNG", json.contains("\"mimeType\":\"image/png\""))
            assertTrue("${kind.name} stores the image in a GLB buffer", json.contains("\"bufferView\""))
            assertFalse("${kind.name} has no external texture URI", json.contains("\"uri\""))
            assertTrue("${kind.name} contains PNG image data", bytes.contains(PngSignature))
        }
    }

    private fun assetFile(path: String): File =
        sequenceOf(File("src/main/assets", path), File("app/src/main/assets", path))
            .first { it.isFile }

    private fun ByteArray.contains(candidate: ByteArray): Boolean =
        indices.any { start ->
            start + candidate.size <= size && candidate.indices.all { offset ->
                this[start + offset] == candidate[offset]
            }
        }

    private companion object {
        val PngSignature = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
