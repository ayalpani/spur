package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun TourModeHeader(
    tour: Tour?,
    active: Boolean,
    now: Long,
    visible: Boolean,
    titleEditor: TextFieldValue?,
    titleSaving: Boolean,
    onClose: () -> Unit,
    onOpenMenu: () -> Unit,
    onTitleChange: (TextFieldValue) -> Unit,
    onSaveTitle: () -> Unit,
    onCancelTitleEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayedTour = tour ?: return
    val editingTitle = !active && titleEditor != null
    val focusRequester = remember(displayedTour.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    if (editingTitle) {
        BackHandler(onBack = onCancelTitleEdit)
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        DisposableEffect(Unit) {
            onDispose { keyboardController?.hide() }
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(MotionDurationDefaultMillis),
            initialOffsetY = { -it },
        ) + fadeIn(tween(MotionDurationDefaultMillis)),
        exit = slideOutVertically(
            animationSpec = tween(MotionDurationDefaultMillis),
            targetOffsetY = { -it },
        ) + fadeOut(tween(MotionDurationDefaultMillis)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (active) GameRoadSurface else SheetBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ConnectedNodesIcon(
                            color = Ink,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clickable(
                                onClickLabel = if (editingTitle) {
                                    "Umbenennen abbrechen"
                                } else {
                                    "Tour-Ansicht schließen"
                                },
                                onClick = if (editingTitle) onCancelTitleEdit else onClose,
                            )
                            .semantics {
                                contentDescription = if (editingTitle) {
                                    "Umbenennen abbrechen"
                                } else {
                                    "Tour-Ansicht schließen"
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ChevronLeftIcon()
                    }
                }
                if (editingTitle) {
                    BasicTextField(
                        value = titleEditor!!,
                        onValueChange = { value ->
                            if (value.text.length <= TourTitleMaximumCharacters) {
                                onTitleChange(value)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Tourname" },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSaveTitle() }),
                    )
                    TextButton(
                        onClick = onSaveTitle,
                        enabled = !titleSaving,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            text = "Speichern",
                            color = Ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Text(
                        text = if (active) {
                            "Unterwegs"
                        } else {
                            displayedTour.title ?: "Archiv-Tour"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (active) {
                            formatDuration(now - displayedTour.startedAt)
                        } else {
                            historySectionLabel(displayedTour.startedAt, now)
                        },
                        color = Ink.copy(alpha = 0.62f),
                        style = if (active) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                onClickLabel = "Hauptmenü öffnen",
                                onClick = onOpenMenu,
                            )
                            .semantics {
                                contentDescription = "Hauptmenü öffnen"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        MenuIcon(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = LucideBoldStrokeWidth,
                        )
                    }
                }
            }
            HorizontalDivider(color = Ink.copy(alpha = 0.12f))
        }
    }
}

internal fun isDisplayedActiveTour(tour: Tour?, activeTour: Tour?): Boolean =
    tour != null && activeTour != null && tour.id == activeTour.id && tour.endedAt == null
