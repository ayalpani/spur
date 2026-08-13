package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LandmarkSuggestionTest {
    private val origin = SpurCoordinate(latitude = 52.5, longitude = 13.4)

    @Test
    fun `prefers a nearby candidate before falling back to one hundred meters`() {
        val result = bestNearbyLandmarkTitle(
            candidates = listOf(
                candidate("Further important", northMeters = 70.0, rank = 1),
                candidate("Nearby", northMeters = 40.0, rank = 20),
            ),
            origin = origin,
        )

        assertEquals("Nearby", result)
    }

    @Test
    fun `uses map rank then distance inside the same radius band`() {
        val result = bestNearbyLandmarkTitle(
            candidates = listOf(
                candidate("Closer", northMeters = 20.0, rank = 4),
                candidate("More notable", northMeters = 45.0, rank = 1),
            ),
            origin = origin,
        )

        assertEquals("More notable", result)
    }

    @Test
    fun `deduplicates the same name before choosing a title`() {
        val result = bestNearbyLandmarkTitle(
            candidates = listOf(
                candidate("Same place", northMeters = 30.0, rank = 1),
                candidate("Same place", northMeters = 10.0, rank = 1),
            ),
            origin = origin,
        )

        assertEquals("Same place", result)
    }

    @Test
    fun `ignores candidates beyond one hundred meters`() {
        assertNull(
            bestNearbyLandmarkTitle(
                candidates = listOf(candidate("Too far", northMeters = 101.0, rank = 1)),
                origin = origin,
            ),
        )
    }

    private fun candidate(
        title: String,
        northMeters: Double,
        rank: Int,
    ) = NearbyLandmarkCandidate(
        title = title,
        coordinate = origin.copy(latitude = origin.latitude + northMeters / 111_111.0),
        rank = rank,
    )
}
