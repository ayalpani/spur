package app.spur

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapMenusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun activeTourActionsAreAvailableDirectlyInTheMainMenu() {
        var deleteClicked = false
        compose.setContent {
            MaterialTheme {
                MainMenu(
                    onOpenSettings = {},
                    onOpenGoogleMaps = {},
                    onRenameTour = null,
                    onOpenAbout = {},
                    onShareTour = {},
                    onStopTour = {},
                    onDeleteTour = { deleteClicked = true },
                )
            }
        }

        assertTextExists("Tour teilen")
        assertTextExists("Tour beenden")
        compose.onNodeWithText("Tour löschen").performClick()
        compose.runOnIdle { assertTrue(deleteClicked) }
    }

    @Test
    fun archivedTourOnlyShowsItsApplicableTourAction() {
        var renameClicked = false
        compose.setContent {
            MaterialTheme {
                MainMenu(
                    onOpenSettings = {},
                    onOpenGoogleMaps = {},
                    onRenameTour = { renameClicked = true },
                    onOpenAbout = {},
                    onShareTour = null,
                    onStopTour = null,
                    onDeleteTour = {},
                )
            }
        }

        assertTextExists("Tour löschen")
        assertTextExists("In G-Maps öffnen")
        assertTextExists("Tour umbenennen")
        assertTextDoesNotExist("Tour teilen")
        assertTextDoesNotExist("Tour beenden")
        assertEquals(
            1,
            compose.onAllNodesWithTag(SheetMenuDividerTestTag).fetchSemanticsNodes().size,
        )
        assertTrue(
            compose.onNodeWithText("Tour umbenennen").fetchSemanticsNode().boundsInRoot.top >
                compose.onNodeWithText("In G-Maps öffnen").fetchSemanticsNode().boundsInRoot.top,
        )
        compose.onNodeWithText("Tour umbenennen").performClick()
        compose.runOnIdle { assertTrue(renameClicked) }
    }

    @Test
    fun settingsNoLongerContainsATourDestination() {
        compose.setContent {
            MaterialTheme {
                SettingsMenu(
                    onOpenHome = {},
                    onOpenHomeAutoStart = {},
                    onOpenBackup = {},
                    onOpenTheme = {},
                    onOpenDirection = {},
                )
            }
        }

        assertTextDoesNotExist("Tour")
        assertTextExists("Backup")
    }

    private fun assertTextExists(text: String) {
        assertTrue(compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertTextDoesNotExist(text: String) {
        assertFalse(compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }
}
