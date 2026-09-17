package com.example.feature.media.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.feature.media.AudioMetadataInfo
import com.example.feature.media.MediaMetadataExtractor
import com.example.feature.viewer.formatBytes
import com.example.feature.viewer.formatDuration

/**
 * Fullscreen Modal Audio Player with Live Waveform, Timeline Scrubbing,
 * Speed Controls, and Playlist Queue Management.
 */
@Composable
fun AdvancedAudioPlayerDialog(
    engine: AudioPlaybackEngine,
    repository: VaultRepository,
    onDismiss: () -> Unit
) {
    val currentTrack by engine.currentTrack.collectAsState()
    val queue by engine.queue.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val isPrepared by engine.isPrepared.collectAsState()
    val currentPositionMs by engine.currentPositionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()
    val playbackSpeed by engine.playbackSpeed.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderPos by remember { mutableFloatStateOf(0f) }

    var metadata by remember { mutableStateOf<AudioMetadataInfo?>(null) }

    LaunchedEffect(currentTrack?.id) {
        val track = currentTrack
        if (track != null) {
            metadata = MediaMetadataExtractor.extractAudioMetadata(repository, track)
        } else {
            metadata = null
        }
    }

    if (currentTrack == null) {
        onDismiss()
        return
    }

    val track = currentTrack!!
    val progressFraction = if (isSeeking) {
        seekSliderPos
    } else if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val currentIndex = queue.indexOfFirst { it.id == track.id }
    val nextTrack = if (currentIndex != -1 && currentIndex + 1 < queue.size) queue[currentIndex + 1] else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VaultColors.Canvas)
                .testTag("advanced_audio_player")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(VaultColors.SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            tint = VaultColors.TextSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SECURE AUDIO PLAYER",
                            color = VaultColors.AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (queue.size > 1) "Track ${currentIndex + 1} of ${queue.size}" else "Single Track",
                            color = VaultColors.TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    // Queue Toggle Button
                    IconButton(
                        onClick = { showQueueSheet = !showQueueSheet },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (showQueueSheet) VaultColors.AccentCyan.copy(alpha = 0.2f) else VaultColors.SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Playback Queue",
                            tint = if (showQueueSheet) VaultColors.AccentCyan else VaultColors.TextSecondary
                        )
                    }
                }

                if (showQueueSheet) {
                    // Queue Management View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(VaultColors.SurfaceElevated)
                            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Playback Queue (${queue.size})",
                                color = VaultColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (queue.size > 1) {
                                TextButton(onClick = { engine.clearQueue() }) {
                                    Text("Clear Others", color = VaultColors.AccentCrimson, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(queue, key = { _, it -> "q_${it.id}" }) { idx, item ->
                                val isCurrent = item.id == track.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) VaultColors.SurfaceHighlight else VaultColors.SurfaceOverlay)
                                        .clickable { engine.playTrack(item, queue) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "${idx + 1}.",
                                            color = if (isCurrent) VaultColors.AccentCyan else VaultColors.TextTertiary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = item.title,
                                                color = if (isCurrent) VaultColors.AccentCyan else VaultColors.TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatBytes(item.sizeBytes),
                                                color = VaultColors.TextTertiary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    if (!isCurrent) {
                                        IconButton(
                                            onClick = { engine.removeFromQueue(item.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = VaultColors.TextTertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Active",
                                            tint = VaultColors.AccentCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Center Art & Track Metadata
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Ambient Art / Waveform Card
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(VaultColors.SurfaceElevated)
                                .border(1.dp, VaultColors.GlassBorderMedium, RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                contentDescription = "Audio Art",
                                tint = if (isPlaying) VaultColors.AccentCyan else VaultColors.TextSecondary,
                                modifier = Modifier.size(80.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Title & Embedded Tags
                        Text(
                            text = metadata?.title ?: track.title,
                            color = VaultColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val artistAlbum = listOfNotNull(metadata?.artist, metadata?.album).joinToString(" • ")
                        Text(
                            text = if (artistAlbum.isNotBlank()) artistAlbum else formatBytes(track.sizeBytes),
                            color = VaultColors.TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (nextTrack != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VaultColors.SurfaceHighlight)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "NEXT: ",
                                    color = VaultColors.AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = nextTrack.title,
                                    color = VaultColors.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Playback Scrubbing & Controls Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    // Timeline Slider
                    Slider(
                        value = progressFraction,
                        onValueChange = { frac ->
                            isSeeking = true
                            seekSliderPos = frac
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0) {
                                engine.seekTo((seekSliderPos * durationMs).toInt())
                            }
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = VaultColors.AccentCyan,
                            activeTrackColor = VaultColors.AccentCyan,
                            inactiveTrackColor = VaultColors.SurfaceHighlight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Timestamps & Format
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentDisplayMs = if (isSeeking) (seekSliderPos * durationMs).toInt() else currentPositionMs
                        Text(
                            text = formatDuration(currentDisplayMs),
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = formatDuration(durationMs),
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Control Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playback Speed Selector
                        Box {
                            TextButton(
                                onClick = { showSpeedMenu = true },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VaultColors.SurfaceElevated)
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
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x", color = if (playbackSpeed == speed) VaultColors.AccentCyan else VaultColors.TextPrimary) },
                                        onClick = {
                                            engine.setPlaybackSpeed(speed)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Skip -10s
                        IconButton(
                            onClick = { engine.seekRelative(-10_000) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Seek Back 10s",
                                tint = VaultColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Previous Track
                        IconButton(
                            onClick = { engine.skipToPrevious() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = VaultColors.TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Master Play / Pause Button
                        IconButton(
                            onClick = { engine.togglePlayPause() },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(VaultColors.AccentCyan)
                                .testTag("audio_dialog_play_pause")
                        ) {
                            if (!isPrepared && isPlaying) {
                                CircularProgressIndicator(
                                    color = VaultColors.Canvas,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = VaultColors.Canvas,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        // Next Track
                        IconButton(
                            onClick = { engine.skipToNext() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = VaultColors.TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Skip +10s
                        IconButton(
                            onClick = { engine.seekRelative(10_000) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Seek Forward 10s",
                                tint = VaultColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
