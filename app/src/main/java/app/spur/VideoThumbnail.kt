package app.spur

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

private const val ThumbnailSidePixels = 480
private const val ThumbnailJpegQuality = 88
private val EarlyFrameTimesMillis = listOf(0L, 400L, 900L, 1_500L)

internal fun videoThumbnailFile(video: File): File =
    File(video.parentFile, "${video.nameWithoutExtension}.thumb.jpg")

internal fun ensureVideoThumbnail(video: File): File? {
    if (!video.isFile) return null
    val thumbnail = videoThumbnailFile(video)
    if (thumbnail.isFile && thumbnail.length() > 0L) return thumbnail

    val retriever = MediaMetadataRetriever()
    var bestFrame: Bitmap? = null
    var bestScore = Double.NEGATIVE_INFINITY
    return try {
        retriever.setDataSource(video.absolutePath)
        val durationMillis = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
            ?: 1L
        EarlyFrameTimesMillis
            .map { it.coerceAtMost(durationMillis - 1L) }
            .distinct()
            .forEach { timeMillis ->
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeMillis * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        ThumbnailSidePixels,
                        ThumbnailSidePixels,
                    )
                } else {
                    retriever.getFrameAtTime(
                        timeMillis * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )
                } ?: return@forEach
                val score = frameQualityScore(frame)
                if (score > bestScore) {
                    bestFrame?.recycle()
                    bestFrame = frame
                    bestScore = score
                } else {
                    frame.recycle()
                }
            }
        val frame = bestFrame ?: return null
        val temporary = File(thumbnail.parentFile, "${thumbnail.name}.tmp")
        val written = FileOutputStream(temporary).use {
            frame.compress(Bitmap.CompressFormat.JPEG, ThumbnailJpegQuality, it)
        }
        if (!written) {
            temporary.delete()
            return null
        }
        if (thumbnail.exists()) thumbnail.delete()
        if (!temporary.renameTo(thumbnail)) {
            temporary.delete()
            return null
        }
        thumbnail
    } catch (_: Exception) {
        null
    } finally {
        bestFrame?.recycle()
        runCatching { retriever.release() }
    }
}

private fun frameQualityScore(bitmap: Bitmap): Double {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 2 || height < 2) return 0.0
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return frameQualityScore(pixels, width, height)
}

internal fun frameQualityScore(
    pixels: IntArray,
    width: Int,
    height: Int,
): Double {
    if (width < 2 || height < 2 || pixels.size < width * height) return 0.0
    val step = (minOf(width, height) / 64).coerceAtLeast(1)
    var edgeTotal = 0.0
    var luminanceTotal = 0.0
    var count = 0
    var y = 0
    while (y + step < height) {
        var x = 0
        while (x + step < width) {
            val center = pixelLuminance(pixels[y * width + x])
            edgeTotal += abs(center - pixelLuminance(pixels[y * width + x + step]))
            edgeTotal += abs(center - pixelLuminance(pixels[(y + step) * width + x]))
            luminanceTotal += center
            count++
            x += step
        }
        y += step
    }
    if (count == 0) return 0.0
    val averageLuminance = luminanceTotal / count
    val exposureFactor = when {
        averageLuminance < 16.0 || averageLuminance > 239.0 -> 0.1
        averageLuminance < 32.0 || averageLuminance > 223.0 -> 0.5
        else -> 1.0
    }
    return edgeTotal / count * exposureFactor
}

private fun pixelLuminance(pixel: Int): Double {
    val red = pixel shr 16 and 0xFF
    val green = pixel shr 8 and 0xFF
    val blue = pixel and 0xFF
    return red * 0.2126 + green * 0.7152 + blue * 0.0722
}
