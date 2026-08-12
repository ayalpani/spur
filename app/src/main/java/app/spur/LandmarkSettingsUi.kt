package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LandmarkCreateBottomSheet(
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val normalizedTitle = normalizeLandmarkTitle(title)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        BottomSheetHeader(title = "Landmark hinzufügen")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            color = Color.Transparent,
            shape = CircleShape,
            border = BorderStroke(1.dp, Ink.copy(alpha = 0.5f)),
        ) {
            BasicTextField(
                value = title,
                onValueChange = {
                    if (it.length <= LandmarkTitleMaximumCharacters) title = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Name der Landmark" },
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                ),
                singleLine = true,
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { normalizedTitle?.let(onSave) },
                ),
                decorationBox = { field ->
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (title.isEmpty()) {
                            Text(
                                text = "Name",
                                color = Ink.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        field()
                    }
                },
            )
        }
        SpurPrimaryButton(
            label = "Landmark hinzufügen",
            enabled = normalizedTitle != null,
            modifier = Modifier.padding(top = 16.dp),
            onClick = { normalizedTitle?.let(onSave) },
        )
        SpurSecondaryButton(
            label = "Zurück",
            modifier = Modifier.padding(top = 10.dp),
            onClick = onBack,
        )
    }
}

@Composable
internal fun LandmarkSettingsBottomSheet(
    landmarks: List<Landmark>,
    onRename: (Landmark, String) -> Unit,
    onDelete: (Landmark) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun cancelEditing() {
        editingId = null
        draft = ""
        keyboard?.hide()
    }

    BackHandler(enabled = editingId != null, onBack = ::cancelEditing)
    LaunchedEffect(editingId) {
        if (editingId != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        BottomSheetHeader(
            title = "Orte",
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text = "Diese Orte erscheinen während einer Kartenbewegung am Rand.",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = Ink.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (landmarks.isEmpty()) {
            Text(
                text = "Keine Orte gespeichert.",
                modifier = Modifier.padding(24.dp),
                color = Ink.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(landmarks, key = Landmark::id) { landmark ->
                    LandmarkSettingsRow(
                        landmark = landmark,
                        editing = editingId == landmark.id,
                        draft = draft,
                        focusRequester = focusRequester,
                        onDraftChanged = { draft = it },
                        onStartEditing = {
                            editingId = landmark.id
                            draft = landmark.title
                        },
                        onSave = {
                            val title = normalizeLandmarkTitle(draft)
                            if (title != null) {
                                onRename(landmark, title)
                                cancelEditing()
                            }
                        },
                        onCancel = ::cancelEditing,
                        onDelete = { onDelete(landmark) },
                    )
                    HorizontalDivider(color = Ink.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun LandmarkSettingsRow(
    landmark: Landmark,
    editing: Boolean,
    draft: String,
    focusRequester: FocusRequester,
    onDraftChanged: (String) -> Unit,
    onStartEditing: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        if (it.length <= LandmarkTitleMaximumCharacters) onDraftChanged(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "Titel von ${landmark.title}" },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = Ink,
                        fontWeight = FontWeight.Medium,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                )
                LandmarkRowIconButton(
                    contentDescription = "Titel speichern",
                    onClick = onSave,
                ) {
                    LucideIcon(
                        paths = listOf("m20 6-11 11-5-5"),
                        color = Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                LandmarkRowIconButton(
                    contentDescription = "Umbenennen abbrechen",
                    onClick = onCancel,
                ) {
                    LucideIcon(
                        paths = listOf("M18 6 6 18", "m6 6 12 12"),
                        color = Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Text(
                    text = landmark.title,
                    modifier = Modifier.weight(1f),
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LandmarkRowIconButton(
                    contentDescription = "${landmark.title} umbenennen",
                    onClick = onStartEditing,
                ) {
                    PencilIcon(modifier = Modifier.size(22.dp))
                }
                LandmarkRowIconButton(
                    contentDescription = "${landmark.title} löschen",
                    onClick = onDelete,
                ) {
                    PhotoDeleteIcon(
                        color = StopRed,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LandmarkRowIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
        content = content,
    )
}
