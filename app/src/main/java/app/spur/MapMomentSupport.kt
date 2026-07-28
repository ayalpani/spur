package app.spur

import android.content.Context
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.textSize
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

internal const val MapMomentPreferences = "map-moments"
internal const val MapMomentEntries = "entries"
internal const val PhotoPlacePreferences = "photo-places"

internal fun Context.loadMapMoments(): List<MapMoment> =
    getSharedPreferences(MapMomentPreferences, Context.MODE_PRIVATE)
        .getStringSet(MapMomentEntries, emptySet())
        .orEmpty()
        .mapNotNull(::decodeMapMoment)
        .filter { it.type == MomentType.EMOJI || File(it.payload).isFile }

internal fun Context.saveMapMoments(moments: List<MapMoment>) {
    getSharedPreferences(MapMomentPreferences, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(MapMomentEntries, moments.map(::encodeMapMoment).toSet())
        .apply()
}

internal suspend fun Context.deleteMapMoment(
    moment: MapMoment,
    moments: List<MapMoment>,
): List<MapMoment>? = withContext(Dispatchers.IO) {
    val allowedDirectory = when (moment.type) {
        MomentType.PHOTO -> "photos"
        MomentType.VIDEO -> "videos"
        MomentType.VOICE -> "voice"
        MomentType.EMOJI -> null
    }?.let { File(filesDir, "moments/$it").canonicalFile }
    if (allowedDirectory != null) {
        val mediaFile = File(moment.payload).canonicalFile
        if (mediaFile.parentFile != allowedDirectory) return@withContext null
        if (mediaFile.exists() && !mediaFile.delete()) return@withContext null
        if (moment.type == MomentType.VIDEO) {
            videoThumbnailFile(mediaFile).takeIf(File::exists)?.delete()
        }
    }
    val updatedMoments = moments.filterNot { it.id == moment.id }
    saveMapMoments(updatedMoments)
    getSharedPreferences(PhotoPlacePreferences, Context.MODE_PRIVATE)
        .edit()
        .remove(moment.id)
        .apply()
    updatedMoments
}

internal suspend fun Context.deleteStoredTour(
    store: TourStore,
    tourId: Long,
): Boolean = withContext(Dispatchers.IO) {
    val tour = store.tour(tourId) ?: return@withContext true
    var remainingMoments = loadMapMoments()
    for (moment in mapMomentsForTour(remainingMoments, tour)) {
        remainingMoments = deleteMapMoment(moment, remainingMoments)
            ?: return@withContext false
    }
    store.deleteTour(tourId)
    true
}

internal fun Context.loadPhotoPlace(photoId: String): String? =
    getSharedPreferences(PhotoPlacePreferences, Context.MODE_PRIVATE)
        .getString(photoId, null)

internal fun Context.savePhotoPlace(photoId: String, place: String) {
    getSharedPreferences(PhotoPlacePreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(photoId, place)
        .apply()
}

internal fun createMomentMarkerBitmap(
    context: Context,
    moment: MapMoment,
    selected: Boolean,
    voiceProgress: Float? = null,
) =
    android.graphics.Bitmap.createBitmap(
            (MomentMarkerWidth * context.resources.displayMetrics.density).toInt(),
            (MomentMarkerHeight * context.resources.displayMetrics.density).toInt(),
            android.graphics.Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            val scale = context.resources.displayMetrics.density
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = momentMarkerColor(moment.type).toArgb()
            paint.style = android.graphics.Paint.Style.FILL

            if (selected) {
                canvas.drawRoundRect(
                    3 * scale,
                    0f,
                    59 * scale,
                    57 * scale,
                    12 * scale,
                    12 * scale,
                    paint,
                )
            }

            val flag = android.graphics.RectF(
                6 * scale,
                2 * scale,
                56 * scale,
                52 * scale,
            )
            canvas.drawRoundRect(flag, 10 * scale, 10 * scale, paint)
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(26 * scale, 50 * scale)
                    lineTo(36 * scale, 50 * scale)
                    lineTo(31 * scale, 57 * scale)
                    close()
                },
                paint,
            )
            val content = android.graphics.RectF(flag).apply {
                inset(MomentMarkerStroke * scale, MomentMarkerStroke * scale)
            }
            if (moment.type == MomentType.VOICE && voiceProgress != null) {
                canvas.save()
                canvas.clipPath(
                    android.graphics.Path().apply {
                        addRoundRect(
                            flag,
                            10 * scale,
                            10 * scale,
                            android.graphics.Path.Direction.CW,
                        )
                    },
                )
                paint.color = Color.White.copy(alpha = 0.24f).toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawRect(
                    flag.left,
                    flag.top,
                    flag.left + flag.width() * voiceProgress.coerceIn(0f, 1f),
                    flag.bottom,
                    paint,
                )
                canvas.restore()
            }

            val preview = when (moment.type) {
                MomentType.PHOTO -> decodeMarkerPhoto(moment.payload)
                MomentType.VIDEO -> ensureVideoThumbnail(File(moment.payload))
                    ?.let { decodeMarkerPhoto(it.absolutePath) }
                else -> null
            }
            if (preview != null) {
                val photoSide = (40 * scale).roundToInt().toFloat()
                val photoContentLeft =
                    (flag.centerX() - photoSide / 2f).roundToInt().toFloat()
                val photoContentTop =
                    (flag.centerY() - photoSide / 2f).roundToInt().toFloat()
                val photoContent = android.graphics.RectF(
                    photoContentLeft,
                    photoContentTop,
                    photoContentLeft + photoSide,
                    photoContentTop + photoSide,
                )
                drawMarkerPhoto(canvas, paint, photoContent, preview, scale)
                preview.recycle()
                if (moment.type == MomentType.VIDEO) {
                    drawVideoPlayOverlay(canvas, paint, photoContent, scale)
                }
            } else if (moment.type == MomentType.EMOJI) {
                paint.color = momentMarkerContentColor(moment.type).toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.textSize = 28 * scale
                canvas.drawText(moment.payload.ifBlank { "🙂" }, 31 * scale, 38 * scale, paint)
            } else {
                paint.color = momentMarkerContentColor(moment.type).toArgb()
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2 * scale
                drawMomentGlyph(canvas, paint, content, scale, moment.type)
            }

            val edge = createMomentMarkerEdgeBitmap(
                bitmap,
                MomentMarkerEdgeWidth * scale,
            )
            canvas.drawBitmap(edge, 0f, 0f, null)
            edge.recycle()
        }

internal fun createMomentClusterBitmap(
    context: Context,
    marker: android.graphics.Bitmap,
    stackSize: Int,
): android.graphics.Bitmap {
    val scale = context.resources.displayMetrics.density
    return android.graphics.Bitmap.createBitmap(
        (70 * scale).toInt(),
        (66 * scale).toInt(),
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        clusterStackOffsets(stackSize).forEach { offset ->
            canvas.drawBitmap(marker, offset * scale, offset * scale, paint)
        }
    }
}

internal fun createMomentMarkerEdgeBitmap(
    marker: android.graphics.Bitmap,
    edgeWidth: Float,
): android.graphics.Bitmap =
    android.graphics.Bitmap.createBitmap(
        marker.width,
        marker.height,
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { edge ->
        val canvas = android.graphics.Canvas(edge)
        val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(
                Color.White.toArgb(),
                android.graphics.PorterDuff.Mode.SRC_IN,
            )
        }
        val erasePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        }
        for (horizontalDirection in -1..1) {
            for (verticalDirection in -1..1) {
                if (horizontalDirection != 0 || verticalDirection != 0) {
                    canvas.drawBitmap(
                        marker,
                        horizontalDirection * edgeWidth,
                        verticalDirection * edgeWidth,
                        whitePaint,
                    )
                }
            }
        }
        canvas.drawBitmap(marker, 0f, 0f, erasePaint)
    }

internal fun decodeMarkerPhoto(path: String): android.graphics.Bitmap? =
    runCatching {
        val file = File(path)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(file),
            ) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val side = minOf(info.size.width, info.size.height)
                val scale = minOf(1f, 240f / side)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
            }
        } else {
            android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
            )
        }
    }.getOrNull()

internal fun drawMarkerPhoto(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    destination: android.graphics.RectF,
    photo: android.graphics.Bitmap,
    scale: Float,
) {
    val side = minOf(photo.width, photo.height)
    val source = android.graphics.Rect(
        (photo.width - side) / 2,
        (photo.height - side) / 2,
        (photo.width + side) / 2,
        (photo.height + side) / 2,
    )
    val clip = android.graphics.Path().apply {
        addRoundRect(destination, 5 * scale, 5 * scale, android.graphics.Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clip)
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawBitmap(photo, source, destination, paint)
    canvas.restore()
}

internal fun drawVideoPlayOverlay(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    destination: android.graphics.RectF,
    scale: Float,
) {
    val centerX = destination.centerX()
    val centerY = destination.centerY()
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = Ink.copy(alpha = 0.62f).toArgb()
    canvas.drawCircle(centerX, centerY, 11 * scale, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(
        android.graphics.Path().apply {
            moveTo(centerX - 3.5f * scale, centerY - 6f * scale)
            lineTo(centerX + 6f * scale, centerY)
            lineTo(centerX - 3.5f * scale, centerY + 6f * scale)
            close()
        },
        paint,
    )
}

internal fun drawMomentGlyph(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    destination: android.graphics.RectF,
    scale: Float,
    type: MomentType,
) {
    val paths = when (type) {
        MomentType.PHOTO -> MomentPhotoIconPaths
        MomentType.VIDEO -> MomentVideoIconPaths
        MomentType.VOICE -> MomentVoicePlaybackIconPaths
        MomentType.EMOJI -> emptyList()
    }

    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeJoin = android.graphics.Paint.Join.ROUND
    val glyphScale = 1.17f * scale
    canvas.save()
    canvas.translate(
        destination.centerX() - 12f * glyphScale,
        destination.centerY() - 12f * glyphScale,
    )
    canvas.scale(glyphScale, glyphScale)
    paths.forEach { pathData ->
        androidx.core.graphics.PathParser.createPathFromPathData(pathData)?.let {
            canvas.drawPath(it, paint)
        }
    }
    canvas.restore()
}
