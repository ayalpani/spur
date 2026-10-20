package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class MapObstructionsTest {
    private val screen = LandmarkIndicatorBounds(0f, 0f, 400f, 800f)

    @Test
    fun tallRailNarrowsFromTheSide() {
        val rail = LandmarkIndicatorBounds(330f, 300f, 400f, 800f)

        val free = screen.avoiding(listOf(rail), gap = 4f, attachDistance = 24f)

        assertEquals(LandmarkIndicatorBounds(0f, 0f, 326f, 800f), free)
    }

    @Test
    fun wideBarsShortenFromTopAndBottom() {
        val header = LandmarkIndicatorBounds(0f, 0f, 400f, 90f)
        val footer = LandmarkIndicatorBounds(0f, 650f, 400f, 800f)

        val free = screen.avoiding(listOf(header, footer), gap = 4f, attachDistance = 24f)

        assertEquals(LandmarkIndicatorBounds(0f, 94f, 400f, 646f), free)
    }

    @Test
    fun cornerButtonBelowHeaderShortensTheTop() {
        val header = LandmarkIndicatorBounds(0f, 0f, 400f, 90f)
        val cornerButton = LandmarkIndicatorBounds(9f, 98f, 69f, 158f)

        val free = screen.avoiding(
            listOf(cornerButton, header),
            gap = 4f,
            attachDistance = 24f,
        )

        assertEquals(LandmarkIndicatorBounds(0f, 162f, 400f, 800f), free)
    }

    @Test
    fun floatingObstructionIsIgnored() {
        val floating = LandmarkIndicatorBounds(150f, 350f, 250f, 450f)

        assertEquals(
            screen,
            screen.avoiding(listOf(floating), gap = 4f, attachDistance = 24f),
        )
    }

    @Test
    fun markerBlockStaysInsideFreeArea() {
        assertEquals(100, 380.clampedInto(0f, 200f, 100))
        assertEquals(10, (-5).clampedInto(10f, 200f, 100))
        assertEquals(50, 50.clampedInto(null, 200f, 100))
    }

    @Test
    fun landmarkOnTopOfOwnLocationIsDropped() {
        val location = LocationEdgeIndicator(
            point = LandmarkScreenPoint(100f, 100f),
            labelPlacement = LandmarkLabelPlacement.BELOW,
            angleDegrees = 0f,
            isOffscreen = true,
        )
        val landmark = Landmark("home", "Zuhause", SpurCoordinate(0.0, 0.0), 1)
        val near = LandmarkEdgeIndicator(
            landmark = landmark,
            point = LandmarkScreenPoint(110f, 100f),
            labelPlacement = LandmarkLabelPlacement.BELOW,
            angleDegrees = 0f,
            isEdgeArrow = true,
        )
        val far = near.copy(point = LandmarkScreenPoint(300f, 100f))

        assertEquals(listOf(far), listOf(near, far).withoutOverlapping(location, 56f))
        assertEquals(
            listOf(near, far),
            listOf(near, far).withoutOverlapping(location.copy(isOffscreen = false), 56f),
        )
    }
}
