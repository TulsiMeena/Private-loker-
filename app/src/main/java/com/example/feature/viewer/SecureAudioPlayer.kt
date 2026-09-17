package com.example.feature.viewer

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors
import com.example.core.storage.FileShredder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

/**
 * Secure Audio Player.
 *
 * Security Protocol:
 * - Plays from a transient decrypted working file.
 * - On exit or lock, releases media buffers and shreds the temporary file.
 */
@Composable
fun SecureAudioPlayer(
    transientAudioFile: File,
    fileName: String,
    modifier: Modifier = Modifier
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }

    // Initialize MediaPlayer
    LaunchedEffect(transientAudioFile) {
        try {
            val player = MediaPlayer()
            player.setDataSource(transientAudioFile.absolutePath)
            player.setOnPreparedListener { mp ->
                durationMs = mp.duration
                isReady = true
            }
            player.setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
            }
            player.prepareAsync()
            mediaPlayer = player
        } catch (_: Exception) {
            isReady = false
        }
    }

    // Polling track progress
    LaunchedEffect(isPlaying, isSeeking) {
        while (isActive && isPlaying && !isSeeking) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPositionMs = player.currentPosition
                }
            }
            delay(250)
        }
    }

    // Shred and clean on disposal
    DisposableEffect(transientAudioFile) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            FileShredder.shredAndPurge(transientAudioFile)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
            .padding(24.dp)
    ) {
        if (!isReady) {
            CircularProgressIndicator(color = VaultColors.AccentCyan)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                // Audio visualizer placeholder circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(VaultColors.SurfaceGraphite)
                        .border(2.dp, VaultColors.AccentCyan.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = fileName,
                    color = VaultColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = "Encrypted Audio Stream",
                    color = VaultColors.AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Slider
                val sliderValue = if (isSeeking) seekPosition else {
                    if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
                }

                Slider(
                    value = sliderValue.coerceIn(0f, 1f),
                    onValueChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekPosition * durationMs).toInt()
                        mediaPlayer?.seekTo(targetMs)
                        currentPositionMs = targetMs
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = VaultColors.AccentCyan,
                        activeTrackColor = VaultColors.AccentCyan,
                        inactiveTrackColor = VaultColors.SurfaceHighlight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Time counters
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatDuration(currentPositionMs),
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatDuration(durationMs),
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playback Control Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Replay 10s
                    IconButton(onClick = {
                        val target = (currentPositionMs - 10000).coerceAtLeast(0)
                        mediaPlayer?.seekTo(target)
                        currentPositionMs = target
                    }) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = VaultColors.TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Play/Pause Big Button
                    IconButton(
                        onClick = {
                            mediaPlayer?.let { player ->
                                if (player.isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(VaultColors.AccentCyan)
                            .testTag("audio_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Speed selector button
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = VaultColors.AccentCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            modifier = Modifier.background(VaultColors.SurfaceElevated)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${speed}x",
                                            color = if (playbackSpeed == speed) VaultColors.AccentCyan else VaultColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        playbackSpeed = speed
                                        showSpeedMenu = false
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            mediaPlayer?.let { mp ->
                                                val params = mp.playbackParams ?: PlaybackParams()
                                                params.speed = speed
                                                mp.playbackParams = params
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
