/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.viewer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.prabincode.vajrafs.util.ImmersiveMode
import com.prabincode.vajrafs.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class VideoAspectMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Zoom / Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIXED_16_9("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
}

enum class OrientationState(val label: String, val icon: ImageVector, val orientation: Int) {
    SENSOR("Auto Rotate", Icons.Default.ScreenRotation, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    LANDSCAPE("Landscape", Icons.Default.StayCurrentLandscape, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    PORTRAIT("Portrait", Icons.Default.StayCurrentPortrait, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
}

enum class RepeatMode(val label: String) {
    OFF("Repeat Off"),
    ALL("Repeat All"),
    ONE("Repeat One")
}

private enum class DragGestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
    IGNORED
}

private data class HorizontalSeekData(
    val targetPosition: Long,
    val deltaMs: Long
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Audio Manager for volume gestures
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Discover videos in current folder for Playlist Next/Prev
    val videoExtensions = remember {
        setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "flv", "ts", "m4v", "wmv")
    }
    val initialFile = remember(filePath) { File(filePath) }
    val folderVideos = remember(filePath) {
        val parent = initialFile.parentFile
        val files = parent?.listFiles { f ->
            f.isFile && !f.name.startsWith(".") && videoExtensions.contains(f.extension.lowercase(Locale.ROOT))
        }?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })?.toList()
        if (!files.isNullOrEmpty()) files else listOf(initialFile)
    }

    var currentVideoIndex by remember(filePath) {
        val idx = folderVideos.indexOfFirst { it.absolutePath == filePath }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }
    val currentFile = folderVideos.getOrElse(currentVideoIndex) { initialFile }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    // Video metadata
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // UI overlays & HUD state
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf(VideoAspectMode.FIT) }
    var orientationState by remember { mutableStateOf(OrientationState.SENSOR) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var isMuted by remember { mutableStateOf(false) }
    var backgroundAudioEnabled by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var showFastForwardHud by remember { mutableStateOf(false) }
    var isCapturingFrame by remember { mutableStateOf(false) }

    // Set the moment the user leaves, so the video surface is dropped before the exit animation starts
    var isClosing by remember { mutableStateOf(false) }

    // Temporary Immersive Full-Screen Mode (hides top status bar and navigation bar)
    ImmersiveMode(enabled = isFullscreen || !showControls || isLocked)

    // Pinch-to-zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Gesture HUDs
    var brightnessHud by remember { mutableStateOf<Float?>(null) } // 0f to 1f
    var volumeHud by remember { mutableStateOf<Int?>(null) } // 0 to maxVolume
    var horizontalSeekData by remember { mutableStateOf<HorizontalSeekData?>(null) }
    var dismissSeekJob by remember { mutableStateOf<Job?>(null) }
    // Double-tap seek streak: while its badge is showing, every further tap on that side adds 10s
    var doubleTapSeekLeft by remember { mutableStateOf(false) }
    var doubleTapSeekRight by remember { mutableStateOf(false) }
    var seekStreakSeconds by remember { mutableIntStateOf(0) }
    var seekStreakTick by remember { mutableIntStateOf(0) }

    // Position the current video resumed from, shown briefly with a "Start over" action
    var resumedFrom by remember { mutableStateOf<Long?>(null) }

    // Sheets & Dialogs
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSpecsDialog by remember { mutableStateOf(false) }

    // Initial brightness extraction
    val initialBrightness = remember {
        val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (windowBrightness in 0f..1f) {
            windowBrightness
        } else {
            // No window override yet: start from the system level so the first swipe doesn't jump.
            // Some OEMs (e.g. Xiaomi) use a range above 255; those fall back to the midpoint.
            val system = try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (_: Exception) {
                -1
            }
            if (system in 1..255) system / 255f else 0.5f
        }
    }
    var currentBrightness by remember { mutableFloatStateOf(initialBrightness) }

    val resumeStore = remember { VideoResumeStore(context) }

    // Starts at the saved position directly, so the video doesn't visibly jump after loading
    fun loadVideo(player: ExoPlayer, file: File) {
        val resumeAt = resumeStore.get(file)
        resumedFrom = resumeAt
        val item = MediaItem.fromUri(Uri.fromFile(file))
        if (resumeAt != null) player.setMediaItem(item, resumeAt) else player.setMediaItem(item)
        player.prepare()
    }

    // ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
            // Pause instead of blasting through the speaker when headphones are unplugged
            setHandleAudioBecomingNoisy(true)
            loadVideo(this, currentFile)
            playWhenReady = true
        }
    }

    // Saves where the given video was left off (cleared when it was near the start or end)
    fun saveResumePosition(file: File) {
        val total = exoPlayer.duration
        if (total > 0) resumeStore.save(file, exoPlayer.currentPosition, total)
    }

    val originalOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Leave in one step: silence audio, drop the SurfaceView (it ignores Compose's fade, so it would
    // otherwise stay on screen for the whole exit animation) and restore orientation before navigating.
    // Also guards against a double tap popping the explorer screen too.
    fun closePlayer() {
        if (isClosing) return
        isClosing = true
        exoPlayer.pause()
        saveResumePosition(currentFile)
        activity?.requestedOrientation = originalOrientation
        onNavigateBack()
    }

    // Back priority: locked -> only reveal the unlock button; fullscreen -> exit fullscreen; otherwise close
    BackHandler(enabled = isLocked) {
        showControls = true
    }
    BackHandler(enabled = !isLocked && isFullscreen) {
        isFullscreen = false
        showControls = true
    }
    BackHandler(enabled = !isLocked && !isFullscreen) {
        closePlayer()
    }

    // Restarts from the beginning when the video already ended, where play() alone does nothing
    fun togglePlayPause() {
        when {
            exoPlayer.isPlaying -> exoPlayer.pause()
            exoPlayer.playbackState == Player.STATE_ENDED -> {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            }
            else -> exoPlayer.play()
        }
    }

    // While scrubbing, jump to the nearest keyframe (fast) so the picture follows the finger; throttled
    // so the decoder isn't flooded. commitSeek() then lands on the exact frame when the finger lifts.
    var lastPreviewSeekAt by remember { mutableLongStateOf(0L) }
    fun previewSeek(positionMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewSeekAt < 100L) return
        lastPreviewSeekAt = now
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        exoPlayer.seekTo(positionMs)
    }
    fun commitSeek(positionMs: Long) {
        exoPlayer.setSeekParameters(SeekParameters.DEFAULT)
        exoPlayer.seekTo(positionMs)
        currentPosition = positionMs
    }

    // Double-tap seek; repeated taps during the streak stack up (-10s, -20s, -30s...)
    fun streakSeek(forward: Boolean) {
        val continuing = if (forward) doubleTapSeekRight else doubleTapSeekLeft
        seekStreakSeconds = if (continuing) seekStreakSeconds + 10 else 10
        doubleTapSeekRight = forward
        doubleTapSeekLeft = !forward
        val newPos = if (forward) {
            (exoPlayer.currentPosition + 10_000L).coerceAtMost(duration)
        } else {
            (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
        }
        commitSeek(newPos)
        seekStreakTick++
    }

    // Switch video track helper
    fun playVideoAtIndex(index: Int) {
        if (index in folderVideos.indices) {
            saveResumePosition(currentFile)
            currentVideoIndex = index
            loadVideo(exoPlayer, folderVideos[index])
            exoPlayer.play()
            zoomScale = 1f
            panOffset = Offset.Zero
        }
    }

    // Capture exact video frame at current position
    fun captureCurrentFrame() {
        if (isCapturingFrame) return
        isCapturingFrame = true
        val currentPosUs = exoPlayer.currentPosition * 1000L
        val activeFile = currentFile

        coroutineScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(activeFile.absolutePath)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        currentPosUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        videoWidth.coerceAtLeast(1),
                        videoHeight.coerceAtLeast(1)
                    ) ?: retriever.getFrameAtTime(currentPosUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } else {
                    retriever.getFrameAtTime(currentPosUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }

                if (bitmap != null) {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val captureDir = File(picturesDir, "VajraFS_Captures")
                    if (!captureDir.exists()) captureDir.mkdirs()
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val destFile = File(captureDir, "FRAME_${timeStamp}_${activeFile.nameWithoutExtension}.jpg")
                    FileOutputStream(destFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Frame saved: Pictures/VajraFS_Captures", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not extract video frame", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Frame capture error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                try { retriever.release() } catch (_: Exception) { }
                isCapturingFrame = false
            }
        }
    }

    // Keep updated state references for listener callbacks
    val currentRepeatMode by rememberUpdatedState(repeatMode)
    val currentIdx by rememberUpdatedState(currentVideoIndex)
    val currentFolderVideos by rememberUpdatedState(folderVideos)
    val latestFile by rememberUpdatedState(currentFile)

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    when (currentRepeatMode) {
                        RepeatMode.ONE -> {
                            exoPlayer.seekTo(0L)
                            exoPlayer.play()
                        }
                        RepeatMode.ALL -> {
                            if (currentFolderVideos.isNotEmpty()) {
                                val nextIndex = (currentIdx + 1) % currentFolderVideos.size
                                playVideoAtIndex(nextIndex)
                            }
                        }
                        RepeatMode.OFF -> {
                            if (currentIdx < currentFolderVideos.size - 1) {
                                playVideoAtIndex(currentIdx + 1)
                            }
                        }
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            if (!isClosing) saveResumePosition(latestFile) // closePlayer() already saved it
            exoPlayer.release()
            activity?.requestedOrientation = originalOrientation
            // Restore window brightness & system bars
            activity?.let { act ->
                val layoutParams = act.window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = layoutParams
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val decor = act.window.decorView
                decor.post { androidx.core.view.ViewCompat.requestApplyInsets(decor) }
                decor.postDelayed({ androidx.core.view.ViewCompat.requestApplyInsets(decor) }, 150)
            }
        }
    }

    // Lifecycle observer for background audio vs pausing
    DisposableEffect(lifecycleOwner, backgroundAudioEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // The process may be killed while in the background; keep the spot safe
                saveResumePosition(latestFile)
                val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    activity?.isInPictureInPictureMode == true
                } else false
                if (!backgroundAudioEnabled && !inPip && exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Periodic position updater
    // Polls only while playing; when paused it syncs once and stops (seek handlers update it directly)
    LaunchedEffect(isPlaying, isSeeking) {
        do {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(300)
        } while (isPlaying)
    }

    // Auto-hide controls after 3.5s of inactivity when playing; the unlock button hides after 2.5s
    LaunchedEffect(showControls, isPlaying, isLocked, isFastForwarding) {
        if (showControls && isLocked) {
            delay(2500)
            showControls = false
        } else if (showControls && isPlaying && !isFastForwarding) {
            delay(3500)
            showControls = false
        }
    }

    // Clear gesture HUDs after inactivity
    LaunchedEffect(brightnessHud) {
        if (brightnessHud != null) {
            delay(1200)
            brightnessHud = null
        }
    }
    LaunchedEffect(volumeHud) {
        if (volumeHud != null) {
            delay(1200)
            volumeHud = null
        }
    }
    // Each streak tap restarts the window; once it lapses the streak ends and the badge hides
    LaunchedEffect(seekStreakTick) {
        if (seekStreakTick > 0) {
            delay(800)
            doubleTapSeekLeft = false
            doubleTapSeekRight = false
        }
    }
    LaunchedEffect(resumedFrom) {
        if (resumedFrom != null) {
            delay(4000)
            resumedFrom = null
        }
    }

    // Auto-hide 2.0x speed HUD badge after 1.2s so it does not obstruct the video while holding
    LaunchedEffect(isFastForwarding) {
        if (isFastForwarding) {
            showFastForwardHud = true
            delay(1200)
            showFastForwardHud = false
        } else {
            showFastForwardHud = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
    ) {
        // ─── 1. ExoPlayer Surface with Pinch-to-Zoom & Pan ───
        if (!isClosing) AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    resizeMode = aspectMode.resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = aspectMode.resizeMode
                // ExoPlayer doesn't hold the screen awake by itself; without this the display times out mid-video
                playerView.keepScreenOn = isPlaying
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        )

        // ─── 2. Gesture Touch Layer (Zoom/Pan, Hold 2x Speed, Seek, Volume/Brightness) ───
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            var dragStartX by remember { mutableFloatStateOf(0f) }
            var dragAccumX by remember { mutableFloatStateOf(0f) }
            var dragAccumY by remember { mutableFloatStateOf(0f) }
            var dragStartPosition by remember { mutableLongStateOf(0L) }
            var dragStartBrightness by remember { mutableFloatStateOf(0f) }
            var dragStartVolume by remember { mutableIntStateOf(0) }
            var activeDragType by remember { mutableStateOf(DragGestureType.NONE) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Pinch-to-zoom & 2-finger pan gesture
                    .pointerInput(isLocked, screenWidth, screenHeight) {
                        if (isLocked) return@pointerInput
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                val downPointers = event.changes.filter { it.pressed }
                                if (downPointers.size >= 2) {
                                    var prevCentroid = (downPointers[0].position + downPointers[1].position) / 2f
                                    var prevDistance = (downPointers[0].position - downPointers[1].position).getDistance()

                                    downPointers.forEach { it.consume() }

                                    while (true) {
                                        val moveEvent = awaitPointerEvent()
                                        val activePointers = moveEvent.changes.filter { it.pressed }
                                        if (activePointers.size < 2) break

                                        val currentCentroid = (activePointers[0].position + activePointers[1].position) / 2f
                                        val currentDistance = (activePointers[0].position - activePointers[1].position).getDistance()

                                        if (prevDistance > 10f) {
                                            val zoomDelta = currentDistance / prevDistance
                                            val panDelta = currentCentroid - prevCentroid

                                            val newZoom = (zoomScale * zoomDelta).coerceIn(1f, 4f)
                                            zoomScale = newZoom

                                            val maxPanX = (screenWidth * (newZoom - 1f)).coerceAtLeast(0f) / 2f
                                            val maxPanY = (screenHeight * (newZoom - 1f)).coerceAtLeast(0f) / 2f

                                            panOffset = if (newZoom > 1f) {
                                                Offset(
                                                    x = (panOffset.x + panDelta.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (panOffset.y + panDelta.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                            } else {
                                                Offset.Zero
                                            }
                                        }

                                        prevCentroid = currentCentroid
                                        prevDistance = currentDistance
                                        activePointers.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    // Tap gestures: single tap (toggle controls), double tap (seek or reset zoom), press-and-hold (2.0x speed)
                    .pointerInput(isLocked, screenWidth, screenHeight) {
                        if (isLocked) {
                            detectTapGestures(
                                onTap = { showControls = !showControls }
                            )
                        } else {
                            // Hand-rolled instead of detectTapGestures, which delays every single tap by the
                            // double-tap timeout (~300ms). Here a single tap toggles the controls at once; if a
                            // second tap follows, that toggle is undone and the double-tap action runs instead.
                            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown()

                                // Press-and-hold: 2x speed until release
                                var speedActivated = false
                                val boostJob = coroutineScope.launch {
                                    delay(350)
                                    speedActivated = true
                                    isFastForwarding = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    exoPlayer.setPlaybackSpeed(2.0f)
                                }
                                val up = waitForUpOrCancellation()
                                boostJob.cancel()
                                if (speedActivated) {
                                    isFastForwarding = false
                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                    return@awaitEachGesture
                                }
                                // Null means a drag or pinch took the pointer over
                                if (up == null) return@awaitEachGesture

                                val tapX = down.position.x
                                val side = when {
                                    tapX < screenWidth * 0.35f -> -1
                                    tapX > screenWidth * 0.65f -> 1
                                    else -> 0
                                }

                                // Mid-streak: each tap on the same side seeks again, no double-tap needed
                                val streakSide = if (doubleTapSeekRight) 1 else if (doubleTapSeekLeft) -1 else 0
                                if (zoomScale <= 1.05f && side != 0 && side == streakSide) {
                                    streakSeek(forward = side > 0)
                                    return@awaitEachGesture
                                }

                                showControls = !showControls

                                val secondDown = withTimeoutOrNull(doubleTapTimeout) { awaitFirstDown() }
                                    ?: return@awaitEachGesture
                                if (waitForUpOrCancellation() == null) return@awaitEachGesture

                                // Double tap: undo the single-tap toggle, then act
                                showControls = !showControls
                                when {
                                    zoomScale > 1.05f -> {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                    secondDown.position.x < screenWidth * 0.35f -> streakSeek(forward = false)
                                    secondDown.position.x > screenWidth * 0.65f -> streakSeek(forward = true)
                                    else -> togglePlayPause()
                                }
                            }
                        }
                    }
                    // Drag Gestures: Horizontal scrub/seek (forward & backward), Left side vertical brightness, Right side vertical volume
                    .pointerInput(isLocked, screenWidth, screenHeight) {
                        if (isLocked) return@pointerInput
                        // Swipes starting this close to the top/bottom edge belong to the system
                        // (notification shade, gesture navigation), not to the player
                        val systemEdgePx = 40.dp.toPx()
                        // Dragging across this much of the screen height sweeps brightness/volume end to end
                        val levelDragSpanPx = screenHeight * 0.6f

                        detectDragGestures(
                            onDragStart = { offset ->
                                dismissSeekJob?.cancel()
                                dragStartX = offset.x
                                dragAccumX = 0f
                                dragAccumY = 0f
                                dragStartPosition = exoPlayer.currentPosition
                                dragStartBrightness = currentBrightness
                                dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                activeDragType = if (offset.y < systemEdgePx || offset.y > screenHeight - systemEdgePx) {
                                    DragGestureType.IGNORED
                                } else {
                                    DragGestureType.NONE
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (activeDragType == DragGestureType.IGNORED) return@detectDragGestures
                                dragAccumX += dragAmount.x
                                dragAccumY -= dragAmount.y // Dragging up increases value

                                if (activeDragType == DragGestureType.NONE) {
                                    val totalAbsX = abs(dragAccumX)
                                    val totalAbsY = abs(dragAccumY)
                                    if (totalAbsX > 18f || totalAbsY > 18f) {
                                        activeDragType = if (totalAbsX > totalAbsY) {
                                            DragGestureType.SEEK
                                        } else {
                                            if (dragStartX < (screenWidth / 2f)) DragGestureType.BRIGHTNESS else DragGestureType.VOLUME
                                        }
                                    }
                                }

                                when (activeDragType) {
                                    DragGestureType.SEEK -> {
                                        change.consume()
                                        // Scrub window: screen width spans from 60s up to 10 mins (proportional to duration)
                                        val scrubWindowMs = if (duration > 0) duration.coerceIn(60_000L, 600_000L) else 60_000L
                                        val deltaMs = ((dragAccumX / screenWidth) * scrubWindowMs).toLong()
                                        val targetPos = (dragStartPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
                                        horizontalSeekData = HorizontalSeekData(
                                            targetPosition = targetPos,
                                            deltaMs = deltaMs
                                        )
                                        previewSeek(targetPos)
                                    }
                                    DragGestureType.BRIGHTNESS -> {
                                        change.consume()
                                        // Absolute from the drag's start point, so speed stays constant however long the drag is
                                        val newBrightness = (dragStartBrightness + dragAccumY / levelDragSpanPx).coerceIn(0.01f, 1f)
                                        currentBrightness = newBrightness
                                        brightnessHud = newBrightness
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            lp.screenBrightness = newBrightness
                                            act.window.attributes = lp
                                        }
                                    }
                                    DragGestureType.VOLUME -> {
                                        change.consume()
                                        // Scaled to the device's step count, so a 15-step and a 160-step phone feel the same
                                        val stepDelta = (dragAccumY / levelDragSpanPx * maxVolume).roundToInt()
                                        val newVol = (dragStartVolume + stepDelta).coerceIn(0, maxVolume)
                                        volumeHud = newVol
                                        if (newVol != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        }
                                        if (isMuted && newVol > 0) {
                                            isMuted = false
                                            exoPlayer.volume = 1f
                                        }
                                    }
                                    DragGestureType.NONE, DragGestureType.IGNORED -> {
                                        // Still determining direction (or a system edge swipe), do not consume
                                    }
                                }
                            },
                            onDragEnd = {
                                if (activeDragType == DragGestureType.SEEK) {
                                    horizontalSeekData?.let { seek -> commitSeek(seek.targetPosition) }
                                    dismissSeekJob = coroutineScope.launch {
                                        delay(500)
                                        horizontalSeekData = null
                                    }
                                }
                                activeDragType = DragGestureType.NONE
                            },
                            onDragCancel = {
                                // Live preview already moved the playhead; put it back where the drag began
                                if (activeDragType == DragGestureType.SEEK) commitSeek(dragStartPosition)
                                horizontalSeekData = null
                                activeDragType = DragGestureType.NONE
                            }
                        )
                    }
            )
        }

        // ─── 3. Gesture HUD Badges ───

        // Horizontal Slide Scrub / Seek HUD (Forward & Backward at Bottom)
        AnimatedVisibility(
            visible = horizontalSeekData != null,
            enter = fadeIn(animationSpec = tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showControls && !isLocked) 84.dp else 24.dp)
        ) {
            horizontalSeekData?.let { seek ->
                val isForward = seek.deltaMs >= 0
                val deltaSecs = abs(seek.deltaMs) / 1000L
                val deltaSign = if (isForward) "+" else "-"
                val deltaText = String.format(Locale.US, "%s%d:%02d", deltaSign, deltaSecs / 60, deltaSecs % 60)
                val accentColor = if (isForward) Color(0xFF00E5FF) else Color(0xFFFF8A80)
                val progressFraction = if (duration > 0) (seek.targetPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Minimal translucent pill with subtle border (does not obstruct video)
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = deltaText,
                                color = accentColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "·",
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${formatDuration(seek.targetPosition)} / ${formatDuration(duration)}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sleek progress bar at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }
            }
        }

        // 2.0X SPEED HOLD HUD
        AnimatedVisibility(
            visible = showFastForwardHud,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "2.0X SPEED",
                        color = Color(0xFFFFD54F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Brightness HUD
        AnimatedVisibility(
            visible = brightnessHud != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            brightnessHud?.let { b ->
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (b > 0.6f) Icons.Default.BrightnessHigh else if (b > 0.2f) Icons.Default.BrightnessMedium else Icons.Default.BrightnessLow,
                            contentDescription = "Brightness",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(b)
                                    .background(Color(0xFFFFD54F))
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${(b * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Volume HUD
        AnimatedVisibility(
            visible = volumeHud != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            volumeHud?.let { v ->
                val fraction = if (maxVolume > 0) v.toFloat() / maxVolume else 0f
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (v == 0) Icons.AutoMirrored.Filled.VolumeMute else if (fraction > 0.5f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Volume",
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(Color(0xFF64B5F6))
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Double-Tap Left Seek Ripple (-10s)
        AnimatedVisibility(
            visible = doubleTapSeekLeft,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "-${seekStreakSeconds}s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Double-Tap Right Seek Ripple (+10s)
        AnimatedVisibility(
            visible = doubleTapSeekRight,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "+${seekStreakSeconds}s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // "Resumed from" pill with a Start over action
        AnimatedVisibility(
            visible = resumedFrom != null && !isLocked,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Resumed from ${formatDuration(resumedFrom ?: 0L)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = {
                        commitSeek(0L)
                        resumedFrom = null
                    }) {
                        Text("Start over", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Floating Reset Zoom Button (appears when zoomed in)
        AnimatedVisibility(
            visible = zoomScale > 1.05f,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls && !isLocked) 110.dp else 32.dp)
        ) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = "Reset Zoom",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Reset Zoom (${String.format(Locale.US, "%.1f", zoomScale)}x)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ─── 4. Screen Lock Indicator ───
        if (isLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        isLocked = false
                        showControls = true
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Unlock Screen", modifier = Modifier.size(28.dp))
                }
            }
        }

        // ─── 5. Main Controls Overlay (Top bar, Center controls, Bottom bar) ───
        AnimatedVisibility(
            visible = showControls && !isLocked && !isFastForwarding,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { closePlayer() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = currentFile.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val playlistInfo = if (folderVideos.size > 1) "Video ${currentVideoIndex + 1} of ${folderVideos.size} • " else ""
                        val resInfo = if (videoWidth > 0 && videoHeight > 0) "${videoWidth}x${videoHeight} • " else ""
                        Text(
                            text = "$playlistInfo$resInfo${formatFileSize(currentFile.length())}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Playback Speed Button
                    TextButton(onClick = { showSpeedDialog = true }) {
                        Text(
                            text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Picture-in-Picture Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = {
                            val rational = if (videoWidth > 0 && videoHeight > 0) {
                                val clampedW = videoWidth.coerceIn((videoHeight * 0.41841).toInt(), (videoHeight * 2.39).toInt())
                                Rational(clampedW, videoHeight)
                            } else Rational(16, 9)

                            try {
                                val pipParams = PictureInPictureParams.Builder()
                                    .setAspectRatio(rational)
                                    .build()
                                activity?.enterPictureInPictureMode(pipParams)
                            } catch (_: Exception) { }
                        }) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color.White
                            )
                        }
                    }

                    // Media Info Specs Button
                    IconButton(onClick = { showSpecsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Media Specs",
                            tint = Color.White
                        )
                    }

                    // Lock Screen Button
                    IconButton(onClick = {
                        isLocked = true
                        showControls = false
                    }) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Lock Controls",
                            tint = Color.White
                        )
                    }
                }

                // Middle Center Play/Pause & Skip Next/Previous Actions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Previous Video
                        val canSkipPrev = currentVideoIndex > 0 || (repeatMode == RepeatMode.ALL && folderVideos.size > 1)
                        IconButton(
                            onClick = {
                                if (currentVideoIndex > 0) {
                                    playVideoAtIndex(currentVideoIndex - 1)
                                } else if (repeatMode == RepeatMode.ALL && folderVideos.isNotEmpty()) {
                                    playVideoAtIndex(folderVideos.size - 1)
                                }
                            },
                            enabled = canSkipPrev,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = if (canSkipPrev) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Rewind 10s
                        IconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Play/Pause Main
                        FilledIconButton(
                            onClick = {
                                togglePlayPause()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.9f),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Skip Next Video
                        val canSkipNext = currentVideoIndex < folderVideos.size - 1 || (repeatMode == RepeatMode.ALL && folderVideos.size > 1)
                        IconButton(
                            onClick = {
                                if (currentVideoIndex < folderVideos.size - 1) {
                                    playVideoAtIndex(currentVideoIndex + 1)
                                } else if (repeatMode == RepeatMode.ALL && folderVideos.isNotEmpty()) {
                                    playVideoAtIndex(0)
                                }
                            },
                            enabled = canSkipNext,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (canSkipNext) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Bottom Timeline & Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Timeline Slider & Duration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatDuration(if (isSeeking) seekPosition.toLong() else currentPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = if (isSeeking) seekPosition else currentPosition.toFloat(),
                            onValueChange = { pos ->
                                isSeeking = true
                                seekPosition = pos
                                previewSeek(pos.toLong())
                            },
                            onValueChangeFinished = {
                                commitSeek(seekPosition.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(
                            text = formatDuration(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Bottom Row Quick Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Group: Aspect Ratio, Repeat Mode, Quick Mute
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Current Aspect Display
                            AssistChip(
                                onClick = {
                                    val modes = VideoAspectMode.values()
                                    val nextIdx = (aspectMode.ordinal + 1) % modes.size
                                    aspectMode = modes[nextIdx]
                                },
                                label = { Text(aspectMode.label, fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.12f)
                                ),
                                border = null
                            )

                            // Repeat Mode Button
                            IconButton(onClick = {
                                repeatMode = when (repeatMode) {
                                    RepeatMode.OFF -> {
                                        Toast.makeText(context, "Repeat All: Loop folder playlist", Toast.LENGTH_SHORT).show()
                                        RepeatMode.ALL
                                    }
                                    RepeatMode.ALL -> {
                                        Toast.makeText(context, "Repeat One: Loop current video", Toast.LENGTH_SHORT).show()
                                        RepeatMode.ONE
                                    }
                                    RepeatMode.ONE -> {
                                        Toast.makeText(context, "Repeat Off", Toast.LENGTH_SHORT).show()
                                        RepeatMode.OFF
                                    }
                                }
                                exoPlayer.repeatMode = if (repeatMode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            }) {
                                Icon(
                                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = repeatMode.label,
                                    tint = if (repeatMode != RepeatMode.OFF) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f)
                                )
                            }

                            // Quick Mute / Unmute Toggle
                            IconButton(onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) Color(0xFFFF5252) else Color.White
                                )
                            }
                        }

                        // Right Group: Frame Capture, Background Audio, Screen Rotation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Frame Capture (Screenshot) Button
                            IconButton(onClick = { captureCurrentFrame() }) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Capture Frame",
                                    tint = if (isCapturingFrame) Color(0xFFFFD54F) else Color.White
                                )
                            }

                            // Background Audio Playback Toggle
                            IconButton(onClick = {
                                backgroundAudioEnabled = !backgroundAudioEnabled
                                Toast.makeText(
                                    context,
                                    if (backgroundAudioEnabled) "Background Audio Enabled" else "Background Audio Disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "Background Audio",
                                    tint = if (backgroundAudioEnabled) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f)
                                )
                            }

                            // Screen Rotation Toggle
                            IconButton(onClick = {
                                val nextState = when (orientationState) {
                                    OrientationState.SENSOR -> OrientationState.LANDSCAPE
                                    OrientationState.LANDSCAPE -> OrientationState.PORTRAIT
                                    OrientationState.PORTRAIT -> OrientationState.SENSOR
                                }
                                orientationState = nextState
                                activity?.requestedOrientation = nextState.orientation
                            }) {
                                Icon(
                                    imageVector = orientationState.icon,
                                    contentDescription = orientationState.label,
                                    tint = Color.White
                                )
                            }

                            // Fullscreen Toggle
                            IconButton(onClick = {
                                isFullscreen = !isFullscreen
                                if (isFullscreen) {
                                    showControls = false
                                }
                            }) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                    tint = if (isFullscreen) Color(0xFF00E5FF) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── 6. Playback Speed Selector Dialog ───
    if (showSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text("Playback Speed", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playbackSpeed == speed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .pointerInput(speed) {
                                    detectTapGestures {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                color = if (playbackSpeed == speed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (playbackSpeed == speed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ─── 7. Media Specs Inspector Dialog ───
    if (showSpecsDialog) {
        val videoFormat = exoPlayer.videoFormat
        val audioFormat = exoPlayer.audioFormat

        AlertDialog(
            onDismissRequest = { showSpecsDialog = false },
            icon = {
                Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text("Media Details", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecItem(label = "Filename", value = currentFile.name)
                    if (folderVideos.size > 1) {
                        SpecItem(label = "Playlist Position", value = "${currentVideoIndex + 1} of ${folderVideos.size}")
                    }
                    SpecItem(label = "Resolution", value = if (videoWidth > 0) "${videoWidth} x ${videoHeight} px" else "Unknown")
                    SpecItem(label = "Duration", value = formatDuration(duration))
                    SpecItem(label = "File Size", value = formatFileSize(currentFile.length()))
                    SpecItem(label = "Video Codec", value = videoFormat?.sampleMimeType ?: "Hardware Decoded")
                    SpecItem(label = "Frame Rate", value = if ((videoFormat?.frameRate ?: 0f) > 0) "${videoFormat?.frameRate?.toInt()} fps" else "Variable")
                    SpecItem(label = "Audio Codec", value = audioFormat?.sampleMimeType ?: "Standard Stream")
                    SpecItem(label = "Audio Channels", value = if ((audioFormat?.channelCount ?: 0) > 0) "${audioFormat?.channelCount} ch (${audioFormat?.sampleRate ?: 0} Hz)" else "Stereo")
                    SpecItem(label = "Storage Path", value = currentFile.absolutePath)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpecsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SpecItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
