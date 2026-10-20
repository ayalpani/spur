package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapObstructionsTest {
    private val screen = LandmarkIndicatorBounds(0f, 0f, 400f, 800f)

    @Test
    fun freeBlockStaysWhereItIs() {
        val block = resolveIndicatorBlock(
            100f,
            300f,
            80f,
            30f,
            screen,
            emptyList(),
            gap = 4f,
            alongHorizontalEdge = true,
        )

        assertEquals(LandmarkIndicatorBounds(100f, 300f, 180f, 330f), block)
    }

    @Test
    fun blockSlidesAlongTopEdgePastCornerButton() {
        val header = LandmarkIndicatorBounds(0f, 0f, 400f, 90f)
        val homeButton = LandmarkIndicatorBounds(9f, 98f, 69f, 158f)

        val block = resolveIndicatorBlock(
            left = 10f,
            top = 100f,
            width = 80f,
            height = 30f,
            area = screen,
            obstacles = listOf(header, homeButton),
            gap = 4f,
            alongHorizontalEdge = true,
        )

        assertEquals(LandmarkIndicatorBounds(73f, 100f, 153f, 130f), block)
    }

    @Test
    fun blockLeavesTheRightRailSideways() {
        val rail = LandmarkIndicatorBounds(331f, 300f, 400f, 800f)

        val block = resolveIndicatorBlock(
            340f,
            500f,
            50f,
            30f,
            screen,
            listOf(rail),
            gap = 4f,
            alongHorizontalEdge = false,
        )

        assertEquals(LandmarkIndicatorBounds(277f, 500f, 327f, 530f), block)
    }

    @Test
    fun blockInBottomCornerClearsBarAndButton() {
        val footer = LandmarkIndicatorBounds(0f, 700f, 400f, 800f)
        val styleButton = LandmarkIndicatorBounds(9f, 630f, 69f, 690f)

        val block = resolveIndicatorBlock(
            left = 0f,
            top = 760f,
            width = 80f,
            height = 30f,
            area = screen,
            obstacles = listOf(footer, styleButton),
            gap = 4f,
            alongHorizontalEdge = true,
        )

        assertFalse(listOf(footer, styleButton).any { it.intersects(block) })
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

private fun LandmarkIndicatorBounds.intersects(other: LandmarkIndicatorBounds): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom
