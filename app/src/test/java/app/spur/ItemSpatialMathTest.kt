package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSpatialMathTest {
    @Test
    fun `AR offset is projected using north-aligned device bearing`() {
        val origin = ItemLocation(52.52, 13.405, 4.0)
        val destination = projectArOffset(
            origin = origin,
            northBearingDegrees = 90.0,
            offset = LocalArOffset(rightMeters = 0.0, forwardMeters = 10.0),
        )

        assertEquals(10.0, distanceMeters(origin, destination), 0.05)
        assertEquals(90.0, bearingDegrees(origin, destination), 0.1)
    }

    @Test
    fun `discovery spatial and claim thresholds are exact`() {
        assertEquals(NearbyItemPresentation.HIDDEN, nearbyItemPresentation(50.01, 1.0))
        assertEquals(NearbyItemPresentation.DIRECTION_GUIDE, nearbyItemPresentation(50.0, 1.0))
        assertEquals(NearbyItemPresentation.SPATIAL, nearbyItemPresentation(15.0, 16.0))
        assertEquals(NearbyItemPresentation.SPATIAL, nearbyItemPresentation(10.01, 1.0))
        assertEquals(NearbyItemPresentation.CLAIMABLE, nearbyItemPresentation(10.0, 15.0))
    }

    @Test
    fun `local item anchor is capped at eight meters while preserving bearing`() {
        val device = ItemLocation(52.52, 13.405, 2.0)
        val farNorth = projectArOffset(device, 0.0, LocalArOffset(0.0, 40.0))
        val offset = localAnchorOffset(device, farNorth, deviceNorthBearingDegrees = 0.0)

        assertEquals(0.0, offset.rightMeters, 0.01)
        assertEquals(8.0, offset.forwardMeters, 0.01)
    }

    @Test
    fun `bearing smoothing takes shortest path around north`() {
        assertEquals(1.0, smoothBearingDegrees(359.0, 1.0, 1.0), 0.001)
        assertEquals(359.36, smoothBearingDegrees(359.0, 1.0), 0.001)
    }

    @Test
    fun `location must be fresh and at most fifteen meters accurate`() {
        assertTrue(isPublishableLocation(ItemLocationFixQuality(15.0, 10_000)))
        assertEquals(false, isPublishableLocation(ItemLocationFixQuality(15.01, 9_000)))
        assertEquals(false, isPublishableLocation(ItemLocationFixQuality(4.0, 10_001)))
    }

    @Test
    fun `back closes AR and rapid reopen is ignored until session teardown finishes`() {
        var mode = openArMode(WorldMode.MAP, arClosing = false)
        assertEquals(WorldMode.AR, mode)

        mode = closeArMode(mode)
        assertEquals(WorldMode.MAP, mode)
        assertEquals(WorldMode.MAP, openArMode(mode, arClosing = true))
        assertEquals(WorldMode.AR, openArMode(mode, arClosing = false))
    }

    @Test
    fun `public anchors stay attached when the same items remain visible`() {
        val plan = planPublicAnchors(
            existingIds = setOf("strawberry", "pear"),
            visibleIds = setOf("strawberry", "pear"),
            sessionChanged = false,
        )

        assertEquals(emptySet<String>(), plan.idsToRemove)
        assertEquals(emptySet<String>(), plan.idsToCreate)
    }

    @Test
    fun `public anchors are replaced only with a new session or changed item set`() {
        assertEquals(
            PublicAnchorPlan(
                idsToRemove = setOf("strawberry"),
                idsToCreate = setOf("strawberry", "banana"),
            ),
            planPublicAnchors(
                existingIds = setOf("strawberry"),
                visibleIds = setOf("strawberry", "banana"),
                sessionChanged = true,
            ),
        )
        assertEquals(
            PublicAnchorPlan(
                idsToRemove = setOf("pear"),
                idsToCreate = setOf("banana"),
            ),
            planPublicAnchors(
                existingIds = setOf("strawberry", "pear"),
                visibleIds = setOf("strawberry", "banana"),
                sessionChanged = false,
            ),
        )
    }
}
