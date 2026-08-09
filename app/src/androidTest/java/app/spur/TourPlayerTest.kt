package app.spur

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class TourPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun focusedWaypointKeepsTheRunningToursStopControl() {
        val tour = Tour(
            id = 7L,
            startedAt = 1_000L,
            endedAt = null,
            distanceMeters = 1_000.0,
            pointCount = 2,
        )
        val focusedWaypoint = EditorLocation(
            point = TrackPoint(
                id = 2L,
                latitude = 52.0,
                longitude = 13.0,
                recordedAt = 126_000L,
            ),
            routeIndex = 1,
            distanceFromStartMeters = 420.0,
            elapsedMillis = 125_000L,
            moments = emptyList(),
        )

        compose.setContent {
            MaterialTheme {
                TourPlayer(
                    tour = tour,
                    routePoints = listOf(focusedWaypoint.point),
                    onStop = {},
                    focusedWaypoint = focusedWaypoint,
                )
            }
        }

        compose.onNodeWithText("420 m").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Tour beenden").fetchSemanticsNode()
    }
}
