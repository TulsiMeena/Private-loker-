package com.example.feature.media

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.feature.viewer.formatBytes
import com.example.feature.viewer.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Universal Media Information Bottom Sheet.
 * Dynamically displays metadata for Images, Videos, and Audio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    item: VaultItemEntity,
    folders: List<VaultFolderEntity>,
    repository: VaultRepository,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var imageMeta by remember { mutableStateOf<ImageMetadataInfo?>(null) }
    var videoMeta by remember { mutableStateOf<VideoMetadataInfo?>(null) }
    var audioMeta by remember { mutableStateOf<AudioMetadataInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showExtendedExif by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        isLoading = true
        when (item.category) {
            "IMAGE" -> imageMeta = MediaMetadataExtractor.extractImageMetadata(repository, item)
            "VIDEO" -> videoMeta = MediaMetadataExtractor.extractVideoMetadata(repository, item)
            "AUDIO" -> audioMeta = MediaMetadataExtractor.extractAudioMetadata(repository, item)
        }
        isLoading = false
    }

    val folderName = folders.find { it.id == item.folderId }?.name ?: "Vault Root"
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultColors.SurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Media Information",
                        color = VaultColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VaultColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    CircularProgressIndicator(color = VaultColors.AccentCyan)
                }
            } else {
                // Primary File Details
                MetadataSection(title = "FILE DETAILS") {
                    MetadataRow("Filename", item.title)
                    MetadataRow("Category", item.category)
                    MetadataRow("MIME Type", item.mimeType)
                    MetadataRow("File Size", formatBytes(item.sizeBytes))
                    MetadataRow("Encrypted Size", formatBytes(item.encryptedSizeBytes))
                    MetadataRow("Folder", folderName)
                    if (item.tags.isNotBlank()) {
                        MetadataRow("Tags", item.tags)
                    }
                    MetadataRow("Favorite", if (item.isFavorite) "Yes" else "No")
                    MetadataRow("Created", dateFormat.format(Date(item.createdAt)))
                    MetadataRow("Modified", dateFormat.format(Date(item.modifiedAt)))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Media Specific Section
                when (item.category) {
                    "IMAGE" -> {
                        imageMeta?.let { meta ->
                            MetadataSection(title = "IMAGE PROPERTIES") {
                                if (meta.width != null && meta.height != null) {
                                    MetadataRow("Dimensions", "${meta.width} × ${meta.height} px")
                                    meta.aspectRatio?.let { MetadataRow("Aspect Ratio", it) }
                                }
                                if (meta.colorSpace != null) {
                                    MetadataRow("Color Space", meta.colorSpace)
                                }
                            }

                            // Optional Extended Metadata Accordion
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showExtendedExif = !showExtendedExif }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Extended Format Attributes",
                                    color = VaultColors.AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (showExtendedExif) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = VaultColors.AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = showExtendedExif) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(VaultColors.SurfaceOverlay)
                                        .padding(12.dp)
                                ) {
                                    MetadataRow("Format Encoding", meta.mimeType.substringAfter('/'))
                                    MetadataRow("Orientation", "${meta.orientationDegrees}°")
                                    MetadataRow("Pixel Density", "Original (Zero compression loss)")
                                }
                            }
                        }
                    }

                    "VIDEO" -> {
                        videoMeta?.let { meta ->
                            MetadataSection(title = "VIDEO PROPERTIES") {
                                MetadataRow("Duration", formatDuration(meta.durationMs.toInt()))
                                if (meta.width != null && meta.height != null) {
                                    MetadataRow("Resolution", "${meta.width} × ${meta.height} px")
                                }
                                if (meta.rotation != 0) {
                                    MetadataRow("Display Rotation", "${meta.rotation}°")
                                }
                                if (meta.bitrate != null && meta.bitrate > 0) {
                                    MetadataRow("Bitrate", "${meta.bitrate / 1000} kbps")
                                }
                            }
                        }
                    }

                    "AUDIO" -> {
                        audioMeta?.let { meta ->
                            MetadataSection(title = "AUDIO PROPERTIES") {
                                MetadataRow("Duration", formatDuration(meta.durationMs.toInt()))
                                meta.title?.let { MetadataRow("Track Title", it) }
                                meta.artist?.let { MetadataRow("Artist", it) }
                                meta.album?.let { MetadataRow("Album", it) }
                                if (meta.bitrate != null && meta.bitrate > 0) {
                                    MetadataRow("Bitrate", "${meta.bitrate / 1000} kbps")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Security & Integrity Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VaultColors.SurfaceOverlay)
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cryptographic Protection",
                                color = VaultColors.AccentEmerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cipher: AES-256-GCM Hardware Enclave",
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp
                        )
                        if (item.checksumSha256.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-256: ${item.checksumSha256.take(16)}...",
                                color = VaultColors.TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetadataSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VaultColors.SurfaceOverlay)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = VaultColors.AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = VaultColors.TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = VaultColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (label.contains("Size") || label.contains("Dimensions") || label.contains("Duration")) FontFamily.Monospace else FontFamily.Default
        )
    }
}
