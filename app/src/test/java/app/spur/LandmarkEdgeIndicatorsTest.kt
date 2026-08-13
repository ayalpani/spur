package app.spur

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkEdgeIndicatorsTest {
    @Test
    fun offscreenPointStaysOnLineFromCenterAndInsideEdge() {
        val result = landmarkEdgeIndicators(
            projected = listOf(projected("tower", priority = 0, x = 200f, y = 75f)),
            bounds = LandmarkIndicatorBounds(10f, 10f, 110f, 110f),
            minimumSeparation = 0f,
            maximumCount = 5,
        ).single()

        assertEquals(110f, result.point.x, 0.001f)
        assertEquals(65.357f, result.point.y, 0.001f)
        assertEquals(LandmarkLabelPlacement.LEFT, result.labelPlacement)
        assertEquals(6.116f, result.angleDegrees, 0.001f)
        assertTrue(result.isEdgeArrow)
    }

    @Test
    fun onscreenPointMovesToItsRealProjectedPosition() {
        val result = landmarkEdgeIndicators(
            projected = listOf(projected("gate", priority = 0, x = 35f, y = 80f)),
            bounds = LandmarkIndicatorBounds(10f, 10f, 110f, 110f),
            minimumSeparation = 0f,
            maximumCount = 5,
        ).single()

        assertEquals(35f, result.point.x)
        assertEquals(80f, result.point.y)
        assertEquals(LandmarkLabelPlacement.RIGHT, result.labelPlacement)
        assertEquals(141.34f, result.angleDegrees, 0.001f)
        assertFalse(result.isEdgeArrow)
    }

    @Test
    fun ownLocationUsesTheSameInvisibleEdgeFrame() {
        val bounds = LandmarkIndicatorBounds(10f, 10f, 110f, 110f)

        val outside = locationEdgeIndicatorFor(
            point = LandmarkScreenPoint(200f, 75f),
            bounds = bounds,
        )!!
        val inside = locationEdgeIndicatorFor(
            point = LandmarkScreenPoint(35f, 80f),
            bounds = bounds,
        )!!

        assertEquals(110f, outside.point.x, 0.001f)
        assertEquals(65.357f, outside.point.y, 0.001f)
        assertEquals(LandmarkLabelPlacement.LEFT, outside.labelPlacement)
        assertTrue(outside.isOffscreen)
        assertEquals(35f, inside.point.x, 0.001f)
        assertEquals(80f, inside.point.y, 0.001f)
        assertEquals(LandmarkLabelPlacement.RIGHT, inside.labelPlacement)
        assertFalse(inside.isOffscreen)
    }

    @Test
    fun closeIndicatorsKeepOnlyTheHigherPriorityLandmark() {
        val result = landmarkEdgeIndicators(
            projected = listOf(
                projected("lower", priority = 2, x = 200f, y = 55f),
                projected("higher", priority = 0, x = 200f, y = 50f),
                projected("top", priority = 1, x = 50f, y = -100f),
            ),
            bounds = LandmarkIndicatorBounds(0f, 0f, 100f, 100f),
            minimumSeparation = 30f,
            maximumCount = 5,
        )

        assertEquals(listOf("higher", "top"), result.map { it.landmark.id })
    }

    @Test
    fun retainedIndicatorsNeverSwapDuringOneGesture() {
        val bounds = LandmarkIndicatorBounds(0f, 0f, 100f, 100f)
        val initial = landmarkEdgeIndicators(
            projected = listOf(
                projected("first", priority = 0, x = 200f, y = 45f),
                projected("second", priority = 1, x = 200f, y = 90f),
            ),
            bounds = bounds,
            minimumSeparation = 30f,
            maximumCount = 1,
        )

        val moved = retainedLandmarkEdgeIndicators(
            projected = listOf(
                projected("first", priority = 0, x = 200f, y = 90f),
                projected("second", priority = 1, x = 200f, y = 45f),
            ),
            bounds = bounds,
            landmarkIds = initial.mapTo(linkedSetOf()) { it.landmark.id },
        )

        assertEquals(listOf("first"), moved.map { it.landmark.id })
    }

    @Test
    fun emptyLandmarkTitlesAreRejected() {
        assertNull(normalizeLandmarkTitle("   "))
        assertEquals("Fernsehturm", normalizeLandmarkTitle("  Fernsehturm  "))
    }

    @Test
    fun landmarkColorsRepeatAcrossTheDarkPalette() {
        assertEquals(
            LandmarkColorPalette.first().toArgb(),
            Landmark("first", "first", SpurCoordinate(0.0, 0.0), 0).colorArgb,
        )
        assertEquals(
            LandmarkColorPalette.first().toArgb(),
            landmarkColor(LandmarkColorPalette.size).toArgb(),
        )
        assertTrue(LandmarkColorPalette.all { it.luminance() < 0.5f })
    }

    private fun projected(
        id: String,
        priority: Int,
        x: Float,
        y: Float,
    ) = ProjectedLandmark(
        landmark = Landmark(
            id = id,
            title = id,
            coordinate = SpurCoordinate(52.5, 13.4),
            priority = priority,
        ),
        point = LandmarkScreenPoint(x, y),
    )
}
