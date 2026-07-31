package app.spur

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaypointRailTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingEndEndpointSelectsLastWaypoint() {
        val locations = testLocations()
        var selectedPointId = locations.first().point.id
        compose.setContent {
            MaterialTheme {
                WaypointRail(
                    locations = locations,
                    selectedPointId = selectedPointId,
                    onSelected = { selectedPointId = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Ende der Tour").performClick()

        compose.runOnIdle {
            assertEquals(locations.last().point.id, selectedPointId)
        }
    }

    @Test
    fun draggingStartEndpointScrollsInsteadOfSelectingStart() {
        val locations = testLocations()
        var selectedPointId = locations.first().point.id
        compose.setContent {
            MaterialTheme {
                WaypointRail(
                    locations = locations,
                    selectedPointId = selectedPointId,
                    onSelected = { selectedPointId = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Start der Tour")
            .performTouchInput { swipeLeft(durationMillis = 300) }

        compose.runOnIdle {
            assertNotEquals(locations.first().point.id, selectedPointId)
        }
    }

    @Test
    fun followingKeepsGrowingEndEndpointAtTheCenter() {
        var locations by mutableStateOf(testLocations())
        compose.setContent {
            MaterialTheme {
                WaypointRail(
                    locations = locations,
                    selectedPointId = locations.last().point.id,
                    followLatest = true,
                    onSelected = {},
                )
            }
        }

        compose.runOnIdle {
            locations = locations + testLocations(count = 20, startIndex = locations.size)
        }
        compose.waitForIdle()

        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val endBounds = compose.onNodeWithContentDescription("Ende der Tour")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(kotlin.math.abs(endBounds.left - rootBounds.center.x) < 20f)
    }

    private fun testLocations(
        count: Int = 12,
        startIndex: Int = 0,
    ): List<EditorLocation> = List(count) { offset ->
        val index = startIndex + offset
        EditorLocation(
            point = TrackPoint(
                id = (index + 1).toLong(),
                latitude = 52.0 + index / 10_000.0,
                longitude = 13.0 + index / 10_000.0,
                recordedAt = 1_700_000_000_000L + index * 1_000L,
            ),
            routeIndex = index,
            distanceFromStartMeters = index * 10.0,
            elapsedMillis = index * 1_000L,
            moments = emptyList(),
        )
    }
}
