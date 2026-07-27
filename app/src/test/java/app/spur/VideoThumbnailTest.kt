package app.spur

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailTest {
    @Test
    fun detailedFrameOutranksFlatFrame() {
        val flat = IntArray(16) { 0xFF808080.toInt() }
        val detailed = IntArray(16) { index ->
            if ((index + index / 4) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }

        assertTrue(
            frameQualityScore(detailed, width = 4, height = 4) >
                frameQualityScore(flat, width = 4, height = 4),
        )
    }
}
