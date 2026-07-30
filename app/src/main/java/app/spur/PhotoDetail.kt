package app.spur

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
@Suppress("DEPRECATION")
internal fun LightSheetNavigationBar(backgroundColor: Color = SheetBackground) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousColor = window?.navigationBarColor
        val previousContrastEnforced =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window?.isNavigationBarContrastEnforced
            } else {
                null
            }
        val insetsController = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        val previousLightIcons = insetsController?.isAppearanceLightNavigationBars

        window?.navigationBarColor = backgroundColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        insetsController?.isAppearanceLightNavigationBars =
            backgroundColor.luminance() > 0.5f

        onDispose {
            if (previousColor != null) {
                window.navigationBarColor = previousColor
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                previousContrastEnforced != null
            ) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousLightIcons != null) {
                insetsController?.isAppearanceLightNavigationBars = previousLightIcons
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
internal fun DarkMediaSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let {
            it.statusBarColor = Color.Black.toArgb()
            it.navigationBarColor = Color.Black.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        onDispose {}
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PhotoDetailPage(
    photos: List<MapMoment>,
    initialPhotoId: String,
    openOrigin: Offset? = null,
    photoRevision: Long = 0L,
    showFeedbackNotice: ShowFeedbackNotice,
    onPhotoChanged: (MapMoment) -> Unit = {},
    onPhotoRotated: () -> Unit = {},
    onPhotoDeleted: (MapMoment) -> Unit,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialPage = remember(photos, initialPhotoId) {
        photos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size },
    )
    val selectedPhoto = photos[pagerState.currentPage.coerceIn(photos.indices)]
    val currentPhoto by rememberUpdatedState(selectedPhoto)
    val currentOnPhotoChanged by rememberUpdatedState(onPhotoChanged)
    val currentOnPhotoRotated by rememberUpdatedState(onPhotoRotated)
    val currentOnPhotoDeleted by rememberUpdatedState(onPhotoDeleted)
    val rotationMutex = remember { Mutex() }
    var imageRevision by remember(photoRevision) { mutableLongStateOf(photoRevision) }
    var hasRotatedPhoto by remember { mutableStateOf(false) }
    val openProgress = remember(openOrigin) {
        Animatable(if (openOrigin == null) 1f else 0f)
    }
    var openingPhotoAspectRatio by remember(openOrigin) { mutableFloatStateOf(1f) }
    var openingThumbnail by remember(openOrigin) { mutableStateOf<ImageBitmap?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var showPhotoActionsSheet by remember { mutableStateOf(false) }
    var showDeletePhotoSheet by remember { mutableStateOf(false) }
    val photoActionsSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val deletePhotoSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var openingAnimationFinished by remember(openOrigin) {
        mutableStateOf(false)
    }
    val resolvedImageKeys = remember { mutableStateMapOf<String, Boolean>() }

    fun dismissAnimated() {
        if (isClosing) return
        isClosing = true
        isVisible = false
        scope.launch {
            delay(MotionDurationDefaultMillis.toLong())
            if (hasRotatedPhoto) currentOnPhotoRotated()
            onDismiss()
        }
    }

    fun deleteAnimated(photo: MapMoment) {
        if (isClosing) return
        isClosing = true
        isVisible = false
        scope.launch {
            deletePhotoSheetState.hide()
            showDeletePhotoSheet = false
            delay(MotionDurationDefaultMillis.toLong())
            currentOnPhotoDeleted(photo)
            if (hasRotatedPhoto) currentOnPhotoRotated()
            onDismiss()
        }
    }

    fun savePhoto() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                context.savePhotoToGallery(File(currentPhoto.payload))
            }
            if (!saved) {
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Foto konnte nicht gespeichert werden.",
                )
            }
        }
    }

    fun sharePhoto() {
        val shared = context.sharePhoto(File(currentPhoto.payload))
        if (!shared) {
            showFeedbackNotice(
                FeedbackNoticeKind.ERROR,
                "Das Foto konnte nicht geteilt werden.",
            )
        }
    }

    fun rotatePhotoLeft() {
        val photo = File(currentPhoto.payload)
        scope.launch {
            val rotated = withContext(Dispatchers.IO) {
                rotationMutex.withLock { rotatePhotoLeftAndSave(photo) }
            }
            if (rotated) {
                imageRevision++
                hasRotatedPhoto = true
            } else {
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Das Foto konnte nicht gedreht werden.",
                )
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            savePhoto()
        } else {
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Zum Speichern braucht Spur Zugriff auf deine Bilder.",
            )
        }
    }
    val requestSavePhoto: () -> Unit = {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            savePhoto()
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
        if (openOrigin == null) {
            delay(MotionDurationDefaultMillis.toLong())
            openingAnimationFinished = true
        }
    }

    LaunchedEffect(openOrigin) {
        if (openOrigin == null) return@LaunchedEffect
        val photo = File(photos[initialPage].payload)
        val (aspectRatio, thumbnail) = withContext(Dispatchers.IO) {
            photoAspectRatio(photo) to decodeMarkerPhoto(photo.absolutePath)?.asImageBitmap()
        }
        openingPhotoAspectRatio = aspectRatio
        openingThumbnail = thumbnail
        openProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MotionDurationDefaultMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        openingAnimationFinished = true
    }

    LaunchedEffect(pagerState, photos) {
        var lastPage = initialPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != lastPage) {
                photos.getOrNull(page)?.let(currentOnPhotoChanged)
                lastPage = page
            }
        }
    }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(
            enabled = !isClosing && !showPhotoActionsSheet && !showDeletePhotoSheet,
            onBack = ::dismissAnimated,
        )
        DarkMediaSystemBars()
        AnimatedVisibility(
            visible = isVisible,
            enter = if (openOrigin == null) {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = MotionDurationDefaultMillis,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = MotionDurationDefaultMillis,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { height -> height / 10 },
                )
            } else {
                EnterTransition.None
            },
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = MotionDurationDefaultMillis,
                ),
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = MotionDurationDefaultMillis,
                    easing = FastOutSlowInEasing,
                ),
                targetOffsetY = { height -> height / 10 },
            ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val progress = openProgress.value
                val detailAlpha = if (openOrigin == null) {
                    1f
                } else {
                    if (progress >= 1f) 1f else 0f
                }
                val selectedImageKey = "${selectedPhoto.id}:$imageRevision"
                val controlsVisible =
                    openingAnimationFinished &&
                        resolvedImageKeys[selectedImageKey] == true &&
                        !isClosing
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = progress)),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = detailAlpha },
                    key = { photos[it].id },
                    beyondViewportPageCount = 1,
                ) { page ->
                    val photo = photos[page]
                    val imageKey = "${photo.id}:$imageRevision"
                    val imageRequest = remember(photo.payload, imageRevision, openOrigin) {
                        ImageRequest.Builder(context)
                            .data(File(photo.payload))
                            .memoryCacheKey("${photo.payload}:$imageRevision")
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .listener(
                                onError = { _, _ -> resolvedImageKeys[imageKey] = true },
                                onSuccess = { _, _ -> resolvedImageKeys[imageKey] = true },
                            )
                            .let { builder ->
                                if (openOrigin == null) {
                                    builder.crossfade(MotionDurationDefaultMillis)
                                } else {
                                    builder
                                }
                            }
                            .build()
                    }
                    ZoomableAsyncImage(
                        model = imageRequest,
                        contentDescription = if (page == pagerState.currentPage) {
                            "Foto ${page + 1} von ${photos.size}"
                        } else {
                            null
                        },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val thumbnail = openingThumbnail
                if (openOrigin != null && thumbnail != null && progress < 1f) {
                    val density = LocalDensity.current
                    val availableWidth = constraints.maxWidth.toFloat()
                    val availableHeight = constraints.maxHeight.toFloat()
                    val targetWidth = minOf(
                        availableWidth,
                        availableHeight * openingPhotoAspectRatio,
                    )
                    val targetHeight = targetWidth / openingPhotoAspectRatio
                    val sourceSize = with(density) { 40.dp.toPx() }
                    val targetLeft = (availableWidth - targetWidth) / 2f
                    val targetTop = (availableHeight - targetHeight) / 2f
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    targetLeft.roundToInt(),
                                    targetTop.roundToInt(),
                                )
                            }
                            .size(
                                with(density) { targetWidth.toDp() },
                                with(density) { targetHeight.toDp() },
                            )
                            .graphicsLayer {
                                scaleX = sourceSize / targetWidth +
                                    (1f - sourceSize / targetWidth) * progress
                                scaleY = sourceSize / targetHeight +
                                    (1f - sourceSize / targetHeight) * progress
                                translationX =
                                    (openOrigin.x - availableWidth / 2f) * (1f - progress)
                                translationY =
                                    (openOrigin.y - availableHeight / 2f) * (1f - progress)
                            }
                            .clip(RoundedCornerShape(7.dp)),
                    )
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier
                        .fillMaxSize(),
                    enter = fadeIn(tween(MotionDurationDefaultMillis)),
                    exit = fadeOut(tween(MotionDurationDefaultMillis / 2)),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PhotoLocationMetadata(
                            photo = selectedPhoto,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(top = 18.dp, end = 18.dp),
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(MapControlGap),
                        ) {
                            PhotoActionButton(
                                contentDescription = "Bildaktionen öffnen",
                                onClick = { showPhotoActionsSheet = true },
                            ) {
                                PhotoMoreIcon()
                            }
                            PhotoActionButton(
                                contentDescription = "Foto 90 Grad nach links drehen",
                                onClick = ::rotatePhotoLeft,
                            ) {
                                PhotoRotateCcwSquareIcon()
                            }
                        }
                        PhotoActionButton(
                            contentDescription = "Foto schließen",
                            onClick = ::dismissAnimated,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(16.dp),
                        ) {
                            PhotoCloseIcon()
                        }
                    }
                }
            }
        }
    }

    if (showPhotoActionsSheet) {
        SpurModalBottomSheet(
            onDismissRequest = { showPhotoActionsSheet = false },
            sheetState = photoActionsSheetState,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                BottomSheetHeader(
                    title = "Bildaktionen",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SheetMenuItem(
                    label = "Teilen",
                    trailing = false,
                    leading = { ShareIcon() },
                    onClick = {
                        scope.launch {
                            photoActionsSheetState.hide()
                            showPhotoActionsSheet = false
                            sharePhoto()
                        }
                    },
                )
                SheetMenuItem(
                    label = "In Galerie speichern",
                    trailing = false,
                    leading = { PhotoDownloadIcon() },
                    onClick = {
                        scope.launch {
                            photoActionsSheetState.hide()
                            showPhotoActionsSheet = false
                            requestSavePhoto()
                        }
                    },
                )
                SheetMenuItem(
                    label = "Bild löschen",
                    destructive = true,
                    trailing = false,
                    leading = { PhotoDeleteIcon(color = StopRed) },
                    onClick = {
                        scope.launch {
                            photoActionsSheetState.hide()
                            showPhotoActionsSheet = false
                            showDeletePhotoSheet = true
                        }
                    },
                )
            }
        }
    }

    if (showDeletePhotoSheet) {
        SpurModalBottomSheet(
            onDismissRequest = { showDeletePhotoSheet = false },
            sheetState = deletePhotoSheetState,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomSheetHeader(title = "Bild löschen")
                Text(
                    text = "Das Bild wird dauerhaft aus Spur und vom Gerät entfernt.",
                    color = Ink.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                SpurPrimaryButton(
                    label = "Bild endgültig löschen",
                    onClick = { deleteAnimated(currentPhoto) },
                    destructive = true,
                )
                SpurSecondaryButton(
                    label = "Abbrechen",
                    onClick = { showDeletePhotoSheet = false },
                )
            }
        }
    }
}

@Composable
private fun PhotoActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = ImageDetailControlBackground,
            contentColor = ImageDetailControlForeground,
        ),
        content = {
            CompositionLocalProvider(
                LocalLucideStrokeWidth provides LucideBoldStrokeWidth,
                content = content,
            )
        },
    )
}

@Composable
private fun PhotoDownloadIcon() = LucideIcon(
    paths = listOf(
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
        "M12 15V3",
    ),
)

@Composable
private fun PhotoMoreIcon() = LucideIcon(
    paths = listOf(
        "M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
        "M19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
        "M5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
    ),
)

@Composable
internal fun PhotoDeleteIcon(color: Color = LocalContentColor.current) = LucideIcon(
    paths = listOf(
        "M3 6h18",
        "M8 6V4h8v2",
        "M19 6l-1 14H6L5 6",
        "M10 11v5",
        "M14 11v5",
    ),
    color = color,
)

@Composable
private fun PhotoRotateCcwSquareIcon() = LucideIcon(
    paths = listOf(
        "M20 9V7a2 2 0 0 0-2-2h-6",
        "m15 2-3 3 3 3",
        "M20 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2",
    ),
)

@Composable
internal fun PhotoCloseIcon() = LucideIcon(
    paths = listOf("M18 6 6 18", "m6 6 12 12"),
)
