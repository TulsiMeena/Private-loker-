package com.example.feature.media.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors
import com.example.feature.viewer.formatDuration

/**
 * High-craft Mini Audio Player.
 * Stays visible while navigating Media Center if audio is playing.
 */
@Composable
fun MiniAudioPlayer(
    engine: AudioPlaybackEngine,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by engine.currentTrack.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val isPrepared by engine.isPrepared.collectAsState()
    val currentPositionMs by engine.currentPositionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()

    if (currentTrack == null) return

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VaultColors.SurfaceOverlay)
            .border(1.dp, VaultColors.GlassBorderMedium, RoundedCornerShape(14.dp))
            .clickable(onClick = onExpand)
            .testTag("mini_audio_player")
    ) {
        Column {
            // Linear progress indicator at very top of mini player card
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = VaultColors.AccentCyan,
                trackColor = VaultColors.SurfaceHighlight,
                strokeCap = StrokeCap.Round
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Icon / Art
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultColors.SurfaceHighlight)
                ) {
                    if (isPlaying) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Paused",
                            tint = VaultColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track Info & Timestamps
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentTrack?.title ?: "Secure Track",
                        color = VaultColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                        color = VaultColors.TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause
                    IconButton(
                        onClick = { engine.togglePlayPause() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                            .testTag("mini_player_play_pause")
                    ) {
                        if (!isPrepared && isPlaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = VaultColors.AccentCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Expand to full player
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand Player",
                            tint = VaultColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Dismiss / Stop
                    IconButton(
                        onClick = { engine.dismissMiniPlayer() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Audio",
                            tint = VaultColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
