package app.spur

import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.io.File

internal val Sand = Color(0xFFF7F5F0)
internal val Ink = Color(0xFF18201C)
internal val Moss = Color(0xFF23614A)
private val FollowGreen = Color(0xFF43A873)
internal val StopRed = Color(0xFFE53935)
internal val MapPinRed = Color(0xFFEA4335)
private val MomentMarkerGreen = Color(0xFF43A047)
internal val TourMomentSelectionYellow = Color(0xFFCCCC00)
internal val Mist = Color(0xFFE8EEE9)
internal val SheetBackground = Color.White
internal val ImageDetailControlBackground = Color.White.copy(alpha = 0.1f)
internal val ImageDetailControlForeground = Color.White
internal const val DefaultMapZoom = 17.5
internal const val MapControlGapDp = 10
internal const val MapControlSizeDp = 60
internal const val MapControlHorizontalPaddingDp = 18
private const val MapControlVerticalPaddingDp = 16
internal const val MapPlayerMinimumWidthDp = 180
internal val MapControlGap = MapControlGapDp.dp
internal val MapControlSize = MapControlSizeDp.dp
internal val MapControlHorizontalPadding = MapControlHorizontalPaddingDp.dp
internal val MapControlVerticalPadding = MapControlVerticalPaddingDp.dp
internal val MomentSheetHeaderGap = 24.dp
internal val MomentSheetGridGap = 10.dp
internal val SheetMenuTextSize = 24.sp
internal val StopSwipeHandleSize = 52.dp
internal val MapRotationOptionGap = 16.dp
internal val FilterChipVisualInset = 8.dp
internal const val MotionDurationDefaultMillis = 200
internal const val EditorPointTransitionDurationMillis = 50
internal const val FeedbackNoticeDurationMillis = 2_500L
internal const val PendingPhotoRevealDelayMillis = 1_000L
internal const val MinimumSystemSplashDurationMillis = 3_000L
internal const val MinimumMapLoadingDurationMillis = 3_000L
internal const val InitialManualLocationHoldDurationMillis = 5_000L
internal const val ActiveManualLocationHoldDurationMillis = 1_000L
internal const val PanelMotionDurationMillis = 300
internal const val MapRotationAnimationMillis = 350L
internal const val AsteriskRotationDurationMillis = 900
internal const val LoaderAsteriskAccelerationDurationMillis = 1_000
internal const val LoaderAsteriskAccelerationDegrees =
    180f * LoaderAsteriskAccelerationDurationMillis / AsteriskRotationDurationMillis
internal val PhotoMapPreviewSize = 96.dp
internal val LoaderAsteriskSize = 128.dp
internal val LoaderTextGap = 20.dp
internal val EditorLocationRailHeight = 96.dp
internal const val PhotoMapPreviewZoom = 17.5
internal const val TourRouteWidthPixels = 6f
internal const val TourRouteBorderPerSidePixels = 4f
internal const val TourRouteBorderWidthPixels =
    TourRouteWidthPixels + TourRouteBorderPerSidePixels * 2f
internal const val TourWaypointRadiusPixels = 2f
internal const val TrailStrokeAlpha = 0.5f
internal const val LocationPulseAlpha = 0.48f
internal const val LocationSignalPeriodMillis = 3_000
internal const val LocationSignalIconMinimumAlpha = 0.5f
internal const val LocationPulseMaxRadius = 35f
internal const val MapPinTipY = 21.799f
internal val MapPinIconPaths = listOf(
    "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
    "M15 10a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
)
internal const val EmojiPickerColumns = 8
internal const val MaxRecentEmojis = 18
internal const val EmojiPreferences = "emoji-picker"
internal const val RecentEmojiPreference = "recent-emojis"
internal const val EmojiPreferenceSeparator = "\n"
internal val DefaultProminentEmojis = listOf(
    "❤️",
    "😂",
    "🔥",
    "✨",
    "👍",
    "😍",
    "🎉",
    "😎",
    "🙏",
)
internal const val LucideBoldStrokeWidth = 3f
internal const val LucideRegularStrokeWidth = 2f
internal const val ArashLinkedInUrl = "https://www.linkedin.com/in/arash-yalpani-3367258"
internal val MapControlElevation = 16.dp
internal val MapControlShadowColor = Color.Black
internal const val MapControlShadowLayers = 3

internal data class MapControlColors(
    val background: Color,
    val foreground: Color,
) {
    val inverted: MapControlColors
        get() = MapControlColors(
            background = foreground,
            foreground = background,
        )
}

internal enum class MapControlColor(
    val label: String,
    val color: Color,
) {
    RED("Rot", Color(0xFFE53935)),
    ORANGE("Orange", Color(0xFFF97316)),
    AMBER("Bernstein", Color(0xFFF59E0B)),
    YELLOW("Gelb", Color(0xFFF4C430)),
    LIME("Limette", Color(0xFF84CC16)),
    GREEN("Grün", Color(0xFF23614A)),
    TEAL("Türkisgrün", Color(0xFF0D9488)),
    CYAN("Cyan", Color(0xFF06B6D4)),
    BLUE("Blau", Color(0xFF2563EB)),
    INDIGO("Indigo", Color(0xFF4F46E5)),
    VIOLET("Violett", Color(0xFF7C3AED)),
    PINK("Pink", Color(0xFFDB2777)),
    BROWN("Braun", Color(0xFF795548)),
    GRAY("Grau", Color(0xFF64748B)),
    BLACK("Schwarz", Color.Black),
    WHITE("Weiß", Color.White),
    ;

    val contrastColor: Color
        get() = if (color.luminance() > 0.3f) Ink else Color.White
}

internal fun momentMarkerColor(type: MomentType): Color = when (type) {
    MomentType.PHOTO -> MomentMarkerGreen
    MomentType.VIDEO -> MapControlColor.BLUE.color
    MomentType.VOICE -> MapControlColor.ORANGE.color
    MomentType.EMOJI -> Ink
}

internal fun momentMarkerContentColor(type: MomentType): Color =
    if (
        type == MomentType.VOICE ||
        momentMarkerColor(type).luminance() <= 0.3f
    ) {
        Color.White
    } else {
        Ink
    }

internal val MomentPhotoIconPaths = listOf(
    "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
    "M15 13a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
)
internal val MomentVideoIconPaths = listOf(
    "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
    "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2",
)
internal val MomentVoicePlaybackIconPaths = listOf(
    "M11 5 6 9H2v6h4l5 4z",
    "M15.54 8.46a5 5 0 0 1 0 7.07",
    "M19.07 4.93a10 10 0 0 1 0 14.14",
)

internal enum class FeedbackNoticeKind(
    val background: Color,
    val foreground: Color,
) {
    PERMISSION(FollowGreen, Ink),
    ERROR(StopRed, Color.White),
    PLACEHOLDER(Mist, Ink),
}

internal typealias ShowFeedbackNotice = (FeedbackNoticeKind, String) -> Unit

internal data class TrailColors(
    val fill: Color,
    val stroke: Color,
)

internal val LocalMapControlColors = staticCompositionLocalOf {
    MapControlColors(
        background = MapControlColor.BLACK.color,
        foreground = MapControlColor.WHITE.color,
    )
}
internal val LocalTrailColors = staticCompositionLocalOf {
    TrailColors(
        fill = MapControlColor.YELLOW.color,
        stroke = MapControlColor.BLACK.color.copy(alpha = TrailStrokeAlpha),
    )
}
internal val LocalLucideStrokeWidth = staticCompositionLocalOf { LucideRegularStrokeWidth }

internal data class PendingMapMoment(
    val type: MomentType,
    val id: String,
    val payload: String,
) {
    constructor(type: MomentType, file: File) : this(
        type = type,
        id = file.nameWithoutExtension,
        payload = file.absolutePath,
    )

    fun deletePayload() {
        if (type != MomentType.EMOJI) {
            File(payload).delete()
        }
    }

    companion object {
        fun emoji(emoji: String) = PendingMapMoment(
            type = MomentType.EMOJI,
            id = "emoji-${System.currentTimeMillis()}",
            payload = emoji,
        )
    }
}

internal sealed interface MomentPlacementTarget {
    data object CurrentLocation : MomentPlacementTarget

    data class RecordedLocation(
        val tourId: Long,
        val trackPointId: Long,
        val coordinate: SpurCoordinate,
    ) : MomentPlacementTarget
}
