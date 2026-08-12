package app.spur

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LandmarkCreateBottomSheet(
    loadSuggestedTitle: suspend () -> String?,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val normalizedTitle = normalizeLandmarkTitle(title)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        title = runCatching { loadSuggestedTitle() }.getOrNull().orEmpty()
        loading = false
    }
    LaunchedEffect(loading) {
        if (loading) return@LaunchedEffect
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
        BottomSheetHeader(title = "Landmark")
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LandmarkCreateLoadingHeight),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Landmark-Name wird geladen" },
                    color = Ink,
                )
            }
        } else {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= LandmarkTitleMaximumCharacters) title = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { normalizedTitle?.let(onSave) },
                ),
            )
            SpurPrimaryButton(
                label = "Hinzufügen",
                enabled = normalizedTitle != null,
                modifier = Modifier.padding(top = 16.dp),
                onClick = { normalizedTitle?.let(onSave) },
            )
        }
        SpurSecondaryButton(
            label = "Abbrechen",
            modifier = Modifier.padding(top = 10.dp),
            onClick = onCancel,
        )
    }
}

private val LandmarkCreateLoadingHeight = 135.dp

@Composable
internal fun LandmarkSettingsBottomSheet(
    landmarks: List<Landmark>,
    onRename: (Landmark, String) -> Unit,
    onDelete: (Landmark) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    fun cancelEditing() {
        editingId = null
        draft = TextFieldValue()
        keyboard?.hide()
    }

    BackHandler(enabled = editingId != null, onBack = ::cancelEditing)
    LaunchedEffect(editingId, landmarks) {
        val index = landmarks.indexOfFirst { it.id == editingId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
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
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
            ) {
                items(landmarks, key = Landmark::id) { landmark ->
                    val editing = editingId == landmark.id
                    LandmarkSettingsRow(
                        landmark = landmark,
                        editing = editing,
                        draft = draft,
                        focusRequester = focusRequester,
                        onDraftChanged = { draft = it },
                        onStartEditing = {
                            editingId = landmark.id
                            draft = TextFieldValue(
                                text = landmark.title,
                                selection = TextRange(landmark.title.length),
                            )
                        },
                        onSave = {
                            val title = normalizeLandmarkTitle(draft.text)
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
                if (editingId != null) {
                    item(key = "edit-scroll-reserve") {
                        Spacer(modifier = Modifier.fillParentMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun LandmarkSettingsRow(
    landmark: Landmark,
    editing: Boolean,
    draft: TextFieldValue,
    focusRequester: FocusRequester,
    onDraftChanged: (TextFieldValue) -> Unit,
    onStartEditing: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = Ink,
        fontSize = SheetMenuTextSize,
        fontWeight = FontWeight.SemiBold,
    )
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
                        if (it.text.length <= LandmarkTitleMaximumCharacters) onDraftChanged(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "Titel von ${landmark.title}" },
                    textStyle = textStyle,
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
                        modifier = Modifier.size(SheetMenuIconSize),
                    )
                }
                LandmarkRowIconButton(
                    contentDescription = "Umbenennen abbrechen",
                    onClick = onCancel,
                ) {
                    LucideIcon(
                        paths = listOf("M18 6 6 18", "m6 6 12 12"),
                        color = Ink,
                        modifier = Modifier.size(SheetMenuIconSize),
                    )
                }
            } else {
                Text(
                    text = landmark.title,
                    modifier = Modifier.weight(1f),
                    color = Ink,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LandmarkRowIconButton(
                    contentDescription = "${landmark.title} umbenennen",
                    onClick = onStartEditing,
                ) {
                    PencilIcon(modifier = Modifier.size(SheetMenuIconSize))
                }
                LandmarkRowIconButton(
                    contentDescription = "${landmark.title} löschen",
                    onClick = onDelete,
                ) {
                    PhotoDeleteIcon(
                        color = Ink,
                        modifier = Modifier.size(SheetMenuIconSize),
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
