package com.example.feature.media.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.FileShredder
import com.example.feature.viewer.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Advanced Secure Video Player Dialog with Custom HUD Overlay.
 *
 * Security Protocol:
 * - Decrypts video to private tracked transient file.
 * - On exit, background, or lock, immediately purges and shreds the transient file.
 * - Restores orientation and releases video decoders on dismiss.
 */
@Composable
fun AdvancedVideoPlayerDialog(
    item: VaultItemEntity,
    playlist: List<VaultItemEntity>,
    repository: VaultRepository,
    sessionManager: SessionSecurityManager,
    onDismiss: () -> Unit,
    onShowInfo: (VaultItemEntity) -> Unit
) {
    val context = LocalContext.current
    val lockState by sessionManager.lockState.collectAsState()

    // Lock interruption guard
    LaunchedEffect(lockState) {
        if (lockState !is LockState.Unlocked) {
            onDismiss()
        }
    }

    var currentItem by remember { mutableStateOf(item) }
    var transientFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }

    // Load file and prepare
    LaunchedEffect(currentItem.id) {
        isLoading = true
        errorMessage = null
        isReady = false
        isPlaying = false

        // Clean previous transient file
        transientFile?.let { FileShredder.shredAndPurge(it) }
        transientFile = null

        val result = withContext(Dispatchers.IO) {
            repository.createTransientPreview(currentItem)
        }

        if (result.isSuccess) {
            transientFile = result.getOrNull()
            isLoading = false
            repository.recordFileAccess(currentItem)
            repository.logSecurityEvent(
                action = "MEDIA_PLAYBACK_START",
                details = "Started secure video stream for #${currentItem.id} (${currentItem.title})",
                isSuccess = true
            )
        } else {
            errorMessage = "Failed to decrypt video"
            isLoading = false
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Polling track progress
    LaunchedEffect(isPlaying, isSeeking) {
        while (isActive && isPlaying && !isSeeking) {
            videoViewRef?.let { vv ->
                try {
                    if (vv.isPlaying) {
                        currentPositionMs = vv.currentPosition
                    }
                } catch (_: Exception) {}
            }
            delay(250)
        }
    }

    // Dispose and shred
    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
            transientFile?.let { FileShredder.shredAndPurge(it) }

            // Restore orientation if altered
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun applySpeed(speed: Float) {
        playbackSpeed = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayerRef?.let { mp ->
                try {
                    val params = mp.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    mp.playbackParams = params
                } catch (_: Exception) {}
            }
        }
    }

    fun seekRelative(deltaMs: Int) {
        val vv = videoViewRef ?: return
        val newPos = (vv.currentPosition + deltaMs).coerceIn(0, durationMs)
        vv.seekTo(newPos)
        currentPositionMs = newPos
    }

    val currentIndex = playlist.indexOfFirst { it.id == currentItem.id }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex != -1 && currentIndex + 1 < playlist.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                }
                .testTag("advanced_video_player")
        ) {
            // Video View Container
            if (transientFile != null) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val vv = VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                                setVideoPath(transientFile!!.absolutePath)
                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    durationMs = mp.duration
                                    isReady = true
                                    applySpeed(playbackSpeed)
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPositionMs = durationMs
                                    if (hasNext) {
                                        currentItem = playlist[currentIndex + 1]
                                    }
                                }
                                setOnErrorListener { _, _, _ ->
                                    errorMessage = "Video playback failed"
                                    true
                                }
                            }
                            videoViewRef = vv
                            addView(vv)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = VaultColors.AccentCyan,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = VaultColors.AccentCrimson,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // HUD Controls Overlay
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceElevated.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Video",
                                tint = Color.White
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = currentItem.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Encrypted Stream • AES-256",
                                color = VaultColors.AccentCyan,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = { onShowInfo(currentItem) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceElevated.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Video Information",
                                tint = Color.White
                            )
                        }
                    }

                    // Center Big Play/Pause Button
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                val vv = videoViewRef ?: return@IconButton
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Bottom Control HUD
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Slider & Timestamps
                        val sliderPos = if (isSeeking) {
                            seekFraction
                        } else if (durationMs > 0) {
                            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Slider(
                            value = sliderPos,
                            onValueChange = { frac ->
                                isSeeking = true
                                seekFraction = frac
                            },
                            onValueChangeFinished = {
                                videoViewRef?.let { vv ->
                                    if (durationMs > 0) {
                                        val targetMs = (seekFraction * durationMs).toInt()
                                        vv.seekTo(targetMs)
                                        currentPositionMs = targetMs
                                    }
                                }
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = VaultColors.AccentCyan,
                                activeTrackColor = VaultColors.AccentCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val curMs = if (isSeeking) (seekFraction * durationMs).toInt() else currentPositionMs
                            Text(
                                text = formatDuration(curMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = formatDuration(durationMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Controls Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Speed Dropdown
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = true },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = VaultColors.AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                        DropdownMenuItem(
                                            text = { Text("${spd}x") },
                                            onClick = {
                                                applySpeed(spd)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Center: Navigation and 10s Skips
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasPrev) {
                                    IconButton(
                                        onClick = { currentItem = playlist[currentIndex - 1] },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Video",
                                            tint = Color.White
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { seekRelative(-10_000) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "-10s",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { seekRelative(10_000) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "+10s",
                                        tint = Color.White
                                    )
                                }

                                if (hasNext) {
                                    IconButton(
                                        onClick = { currentItem = playlist[currentIndex + 1] },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Video",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }

                            // Right: Orientation Toggle
                            IconButton(
                                onClick = {
                                    val activity = context as? Activity
                                    if (isLandscape) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        isLandscape = false
                                    } else {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        isLandscape = true
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
