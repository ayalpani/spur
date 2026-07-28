package app.spur

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import java.util.Locale
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private class SpurRecentEmojiProvider(context: Context) : RecentEmojiProvider {
    private val preferences = context.getSharedPreferences(
        EmojiPreferences,
        Context.MODE_PRIVATE,
    )

    override suspend fun getRecentEmojiList(): List<String> =
        synchronized(preferences) {
            preferences.getString(RecentEmojiPreference, null)
                ?.split(EmojiPreferenceSeparator)
                ?.filter(String::isNotBlank)
                ?.take(MaxRecentEmojis)
                ?.ifEmpty { DefaultProminentEmojis }
                ?: DefaultProminentEmojis
        }

    override fun recordSelection(emoji: String) {
        synchronized(preferences) {
            val recent = preferences.getString(RecentEmojiPreference, null)
                ?.split(EmojiPreferenceSeparator)
                ?.filter(String::isNotBlank)
                .orEmpty()
            val updated = (listOf(emoji) + recent)
                .distinct()
                .take(MaxRecentEmojis)
            preferences.edit()
                .putString(
                    RecentEmojiPreference,
                    updated.joinToString(EmojiPreferenceSeparator),
                )
                .apply()
        }
    }
}

@Composable
internal fun EmojiPickerBottomSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onEmojiPicked: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LightSheetNavigationBar()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            val sheetHeight = constraints.maxHeight.toFloat()
            val partialOffset = sheetHeight / 2f
            var sheetOffset by remember(sheetHeight) {
                mutableFloatStateOf(partialOffset)
            }
            var animationJob by remember {
                mutableStateOf<kotlinx.coroutines.Job?>(null)
            }

            val animateTo: (Float, () -> Unit) -> Unit = { target, onFinished ->
                animationJob?.cancel()
                animationJob = scope.launch {
                    animate(
                        initialValue = sheetOffset,
                        targetValue = target,
                        animationSpec = tween(MotionDurationDefaultMillis),
                    ) { value, _ ->
                        sheetOffset = value
                    }
                    onFinished()
                }
            }
            val settleSheet: () -> Unit = {
                val target = listOf(0f, partialOffset, sheetHeight)
                    .minBy { abs(it - sheetOffset) }
                animateTo(target) {
                    if (target == sheetHeight) onDismiss()
                }
            }
            val hideTo: (() -> Unit) -> Unit = { onHidden ->
                animateTo(sheetHeight, onHidden)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { sheetOffset.toDp() })
                    .semantics {
                        contentDescription = "Emoji-Auswahl schließen"
                    }
                    .clickable { hideTo(onDismiss) },
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(
                        with(density) {
                            (sheetHeight - sheetOffset).coerceAtLeast(0f).toDp()
                        },
                    ),
                color = SheetBackground,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                EmojiPickerSheet(
                    headerModifier = Modifier.pointerInput(sheetHeight) {
                        detectVerticalDragGestures(
                            onDragStart = { animationJob?.cancel() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                sheetOffset =
                                    (sheetOffset + dragAmount).coerceIn(0f, sheetHeight)
                            },
                            onDragEnd = settleSheet,
                            onDragCancel = settleSheet,
                        )
                    },
                    onBack = { hideTo(onBack) },
                    onEmojiPicked = { emoji ->
                        hideTo { onEmojiPicked(emoji) }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SpurModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color = SheetBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
    ) {
        LightSheetNavigationBar(containerColor)
        content()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmojiPickerSheet(
    headerModifier: Modifier,
    onBack: () -> Unit,
    onEmojiPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    val recentEmojiProvider = remember(context) {
        SpurRecentEmojiProvider(context.applicationContext)
    }
    val currentOnEmojiPicked by rememberUpdatedState(onEmojiPicked)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = headerModifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
            BottomSheetHeader(
                title = "Emoji wählen",
                onBack = onBack,
            )
        }
        AndroidView(
            factory = { viewContext ->
                val configuration = Configuration(viewContext.resources.configuration)
                    .apply { setLocale(Locale.GERMAN) }
                val localizedContext = viewContext.createConfigurationContext(configuration)
                val pickerContext = ContextThemeWrapper(
                    localizedContext,
                    R.style.Theme_Spur,
                )
                EmojiPickerView(pickerContext).apply {
                    emojiGridColumns = EmojiPickerColumns
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setRecentEmojiProvider(recentEmojiProvider)
                    setOnEmojiPickedListener { item ->
                        currentOnEmojiPicked(item.emoji)
                    }
                }
            },
            update = { picker ->
                picker.setOnEmojiPickedListener { item ->
                    currentOnEmojiPicked(item.emoji)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

internal data class FeedbackNotice(
    val id: Long,
    val kind: FeedbackNoticeKind,
    val message: String,
)

@Composable
internal fun FeedbackNoticeHost(
    notice: FeedbackNotice?,
    modifier: Modifier = Modifier,
) {
    var displayedNotice by remember { mutableStateOf(notice) }
    LaunchedEffect(notice) {
        if (notice != null) displayedNotice = notice
    }

    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(MotionDurationDefaultMillis),
        ) + fadeIn(tween(MotionDurationDefaultMillis)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(MotionDurationDefaultMillis),
        ) + fadeOut(tween(MotionDurationDefaultMillis)),
    ) {
        displayedNotice?.let { current ->
            Surface(
                color = current.kind.background,
                contentColor = current.kind.foreground,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                shadowElevation = MapControlElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = current.message,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

internal fun Modifier.mapControlShadow(shape: Shape): Modifier {
    var result = this
    repeat(MapControlShadowLayers) {
        result = result.shadow(
            elevation = MapControlElevation,
            shape = shape,
            ambientColor = MapControlShadowColor,
            spotColor = Color.Transparent,
        )
    }
    return result
}
