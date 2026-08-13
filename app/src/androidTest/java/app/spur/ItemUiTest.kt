package app.spur

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun worldToggleUsesMapAndArLabelsAndInvokesAction() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                WorldModeToggle(mode = WorldMode.MAP, onClick = { clicks++ })
            }
        }

        compose.onNodeWithContentDescription("AR öffnen").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun inventoryRailStartsPlacementAndGuidanceExplainsMissingFloor() {
        val item = OwnedItem(
            id = "item-1",
            kind = ItemKind.STRAWBERRY,
            generation = 0,
            capabilitySecret = ByteArray(32),
            provenance = ProvenanceCapsule(),
        )
        var selected: OwnedItem? = null
        compose.setContent {
            MaterialTheme {
                ArInventoryRail(items = listOf(item), selectedItemId = null, onSelect = { selected = it })
                PlacementGuidance(
                    validGroundHit = false,
                    waitingForLocation = false,
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        compose.onNodeWithContentDescription("Erdbeere im Raum ablegen").performClick()
        compose.onNodeWithText("Bewege dein Handy und visiere die Stelle am Boden an").assertIsDisplayed()
        compose.runOnIdle { assertEquals(item.id, selected?.id) }
    }
}
