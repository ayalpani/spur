package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapMomentTest {
    @Test
    fun mapMomentRoundTripsThroughLocalStorageFormat() {
        MomentType.entries.forEach { type ->
            val moment = MapMoment(
                id = "moment-${type.name}",
                type = type,
                latitude = 52.52,
                longitude = 13.405,
                payload = "/data/moment",
            )
            assertEquals(moment, decodeMapMoment(encodeMapMoment(moment)))
        }
        assertNull(decodeMapMoment("broken"))
    }
}
