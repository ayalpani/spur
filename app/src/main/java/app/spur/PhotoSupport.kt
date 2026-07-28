package app.spur

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun photoCaptureLabel(photo: MapMoment): String {
    val capturedAt = photo.captureTimeMillis()
        ?: File(photo.payload).lastModified().takeIf { it > 0L }
        ?: return "Aufnahmezeit unbekannt"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(capturedAt))
}

@Composable
internal fun PhotoLocationMetadata(
    photo: MapMoment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var place by remember(photo.id) {
        mutableStateOf(context.loadPhotoPlace(photo.id))
    }
    LaunchedEffect(photo.id) {
        if (place != null) return@LaunchedEffect
        context.reverseGeocode(photo.latitude, photo.longitude)?.let { resolved ->
            context.savePhotoPlace(photo.id, resolved)
            place = resolved
        }
    }

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.58f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoMapPreview(photo = photo)
        Column(
            modifier = Modifier
                .widthIn(max = 228.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = photoCaptureLabel(photo),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            place?.let { description ->
                Text(
                    text = description.replaceFirst(", ", "\n"),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
internal fun PhotoMapPreview(
    photo: MapMoment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewPixels = with(LocalDensity.current) {
        PhotoMapPreviewSize.roundToPx()
    }
    var preview by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(photo.id, previewPixels) {
        MapLibre.getInstance(context)
        var disposed = false
        val options = MapSnapshotter.Options(previewPixels, previewPixels)
            .withCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(photo.latitude, photo.longitude))
                    .zoom(PhotoMapPreviewZoom)
                    .build(),
            )
            .withPixelRatio(1f)
            .withLogo(false)
            .withStyleBuilder(Style.Builder().fromUri(StreetMapStyle))
        val snapshotter = MapSnapshotter(context, options)
        snapshotter.start(
            { snapshot ->
                if (!disposed) preview = snapshot.bitmap.asImageBitmap()
            },
            { _ -> },
        )
        onDispose {
            disposed = true
            snapshotter.cancel()
        }
    }

    Box(
        modifier = modifier
            .size(PhotoMapPreviewSize)
            .background(Mist)
            .semantics { contentDescription = "Karte des Aufnahmeorts" },
        contentAlignment = Alignment.Center,
    ) {
        preview?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        FilledMapPinAtCenter(
            color = MapPinRed,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal suspend fun Context.reverseGeocode(
    latitude: Double,
    longitude: Double,
): String? {
    if (
        !Geocoder.isPresent() ||
        latitude !in -90.0..90.0 ||
        longitude !in -180.0..180.0
    ) {
        return null
    }
    val geocoder = Geocoder(applicationContext, Locale.getDefault())
    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses.firstOrNull())
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
    } else {
        @Suppress("DEPRECATION")
        withContext(Dispatchers.IO) {
            runCatching {
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }.getOrNull()
        }
    }
    return address?.let {
        shortPlaceDescription(
            thoroughfare = it.thoroughfare,
            streetNumber = it.subThoroughfare,
            district = it.subLocality,
            locality = it.locality,
            region = it.adminArea,
            featureName = it.featureName,
        )
    }
}

internal fun shortPlaceDescription(
    thoroughfare: String?,
    streetNumber: String?,
    district: String?,
    locality: String?,
    region: String?,
    featureName: String?,
): String? {
    val street = listOfNotNull(thoroughfare, streetNumber)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" ")
        .ifEmpty { null }
    val area = listOf(district, locality, region)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
    val fallback = featureName?.trim()?.takeIf(String::isNotEmpty)
    return listOfNotNull(street ?: fallback, area)
        .distinct()
        .take(2)
        .joinToString(", ")
        .ifEmpty { null }
}

internal fun Context.sharePhoto(source: File): Boolean = runCatching {
    if (!source.isFile) return false
    val uri = FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        source,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Spur Foto", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, "Foto teilen"))
    true
}.getOrDefault(false)

internal fun rotatePhotoLeftAndSave(source: File): Boolean = runCatching {
    if (!source.isFile) return false
    val originalLastModified = source.lastModified()
    val exif = android.media.ExifInterface(source.absolutePath)
    val orientation = exif.getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL,
    )
    exif.setAttribute(
        android.media.ExifInterface.TAG_ORIENTATION,
        exifOrientationAfterLeftRotation(orientation).toString(),
    )
    exif.saveAttributes()
    if (originalLastModified > 0L) source.setLastModified(originalLastModified)
    true
}.getOrDefault(false)

internal fun exifOrientationAfterLeftRotation(orientation: Int): Int =
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_NORMAL ->
            android.media.ExifInterface.ORIENTATION_ROTATE_270
        android.media.ExifInterface.ORIENTATION_ROTATE_270 ->
            android.media.ExifInterface.ORIENTATION_ROTATE_180
        android.media.ExifInterface.ORIENTATION_ROTATE_180 ->
            android.media.ExifInterface.ORIENTATION_ROTATE_90
        android.media.ExifInterface.ORIENTATION_ROTATE_90 ->
            android.media.ExifInterface.ORIENTATION_NORMAL
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            android.media.ExifInterface.ORIENTATION_TRANSPOSE
        android.media.ExifInterface.ORIENTATION_TRANSPOSE ->
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            android.media.ExifInterface.ORIENTATION_TRANSVERSE
        android.media.ExifInterface.ORIENTATION_TRANSVERSE ->
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        else -> android.media.ExifInterface.ORIENTATION_ROTATE_270
    }

internal fun photoAspectRatio(photo: File): Float {
    val options = android.graphics.BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    android.graphics.BitmapFactory.decodeFile(photo.absolutePath, options)
    val orientation = runCatching {
        android.media.ExifInterface(photo.absolutePath).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
    return orientedPhotoAspectRatio(options.outWidth, options.outHeight, orientation)
}

internal fun orientedPhotoAspectRatio(width: Int, height: Int, orientation: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val swapsDimensions = when (orientation) {
        android.media.ExifInterface.ORIENTATION_TRANSPOSE,
        android.media.ExifInterface.ORIENTATION_ROTATE_90,
        android.media.ExifInterface.ORIENTATION_TRANSVERSE,
        android.media.ExifInterface.ORIENTATION_ROTATE_270,
        -> true
        else -> false
    }
    return if (swapsDimensions) {
        height.toFloat() / width
    } else {
        width.toFloat() / height
    }
}

@Suppress("DEPRECATION")
internal fun Context.savePhotoToGallery(source: File): Boolean = runCatching {
    if (!source.isFile) return false
    val resolver = contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Spur",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create gallery entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open gallery entry")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    } else {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Spur",
        ).apply { mkdirs() }
        val destination = File(directory, source.name)
        source.copyTo(destination, overwrite = true)
        resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, destination.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATA, destination.absolutePath)
            },
        )
    }
    true
}.getOrDefault(false)

internal fun shareActiveTour(context: Context) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Meine Tour mit Spur läuft gerade.")
    }
    context.startActivity(Intent.createChooser(share, "Tour teilen"))
}
