package app.spur

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocationOnboardingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun initialStateExplainsPrivacyAndRequestsLocation() {
        var requested = false
        compose.setContent {
            MaterialTheme {
                LocationOnboarding(
                    permissionRequested = false,
                    onRequestLocation = { requested = true },
                )
            }
        }

        compose.onNodeWithText("Deine Spur beginnt dort, wo du bist.").assertIsDisplayed()
        compose.onNodeWithText("Deine Standortdaten bleiben auf diesem Gerät.")
            .assertIsDisplayed()
        compose.onNodeWithText("Standort erlauben").performClick()

        compose.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun requestedStateOffersRetry() {
        compose.setContent {
            MaterialTheme {
                LocationOnboarding(
                    permissionRequested = true,
                    onRequestLocation = {},
                )
            }
        }

        compose.onNodeWithText("Ohne Standort fehlt deine Spur.").assertIsDisplayed()
        compose.onNodeWithText("Erneut erlauben").assertIsDisplayed()
    }
}
