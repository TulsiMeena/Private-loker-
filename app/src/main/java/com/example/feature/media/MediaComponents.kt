package com.example.feature.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.feature.viewer.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3 Primary Category Overview Cards displaying actual metadata item counts and storage.
 */
@Composable
fun MediaOverviewCategoryCards(
    imageCount: Int,
    imageSizeBytes: Long,
    videoCount: Int,
    videoSizeBytes: Long,
    audioCount: Int,
    audioSizeBytes: Long,
    onCategoryClick: (MediaCategoryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MediaCategoryCard(
            title = "Images",
            count = imageCount,
            sizeBytes = imageSizeBytes,
            icon = Icons.Default.Image,
            accentColor = VaultColors.AccentCyan,
            onClick = { onCategoryClick(MediaCategoryTab.IMAGES) },
            modifier = Modifier.weight(1f)
        )

        MediaCategoryCard(
            title = "Videos",
            count = videoCount,
            sizeBytes = videoSizeBytes,
            icon = Icons.Default.Videocam,
            accentColor = Color(0xFF818CF8),
            onClick = { onCategoryClick(MediaCategoryTab.VIDEOS) },
            modifier = Modifier.weight(1f)
        )

        MediaCategoryCard(
            title = "Audio",
            count = audioCount,
            sizeBytes = audioSizeBytes,
            icon = Icons.Default.Audiotrack,
            accentColor = VaultColors.AccentEmerald,
            onClick = { onCategoryClick(MediaCategoryTab.AUDIO) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MediaCategoryCard(
    title: String,
    count: Int,
    sizeBytes: Long,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                color = VaultColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$count ${if (count == 1) "item" else "items"}",
                color = VaultColors.TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formatBytes(sizeBytes),
                color = VaultColors.TextTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Filter and Sorting Controls Bar.
 */
@Composable
fun MediaFilterSortBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onToggleSearch: () -> Unit,
    sortOption: MediaSortOption,
    onSortOptionSelected: (MediaSortOption) -> Unit,
    sizeFilter: MediaSizeFilter,
    onSizeFilterSelected: (MediaSizeFilter) -> Unit,
    dateFilter: MediaDateFilter,
    onDateFilterSelected: (MediaDateFilter) -> Unit,
    statusFilter: MediaStatusFilter,
    onStatusFilterSelected: (MediaStatusFilter) -> Unit,
    selectedTag: String?,
    availableTags: List<String>,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Search Input (Expandable)
        if (isSearching) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search media by name, tag, format...", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VaultColors.AccentCyan
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = VaultColors.TextSecondary
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultColors.AccentCyan,
                    unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                    focusedContainerColor = VaultColors.SurfaceElevated,
                    unfocusedContainerColor = VaultColors.SurfaceElevated,
                    focusedTextColor = VaultColors.TextPrimary,
                    unfocusedTextColor = VaultColors.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("media_search_input")
            )
        }

        // Horizontal Filter Chips & Sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Button
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultColors.SurfaceElevated)
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { showSortMenu = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort Options",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sortOption.displayName,
                        color = VaultColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    MediaSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.displayName,
                                    color = if (option == sortOption) VaultColors.AccentCyan else VaultColors.TextPrimary
                                )
                            },
                            onClick = {
                                onSortOptionSelected(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Status Filter Chips
            MediaStatusFilter.entries.filter { it != MediaStatusFilter.ALL }.forEach { filter ->
                val isSelected = statusFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onStatusFilterSelected(if (isSelected) MediaStatusFilter.ALL else filter)
                    },
                    label = { Text(filter.displayName, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = VaultColors.AccentCyan,
                        containerColor = VaultColors.SurfaceElevated,
                        labelColor = VaultColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                        selectedBorderColor = VaultColors.AccentCyan,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }

            // Size Filter Chips
            MediaSizeFilter.entries.filter { it != MediaSizeFilter.ALL }.forEach { size ->
                val isSelected = sizeFilter == size
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSizeFilterSelected(if (isSelected) MediaSizeFilter.ALL else size)
                    },
                    label = { Text(size.displayName, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultColors.AccentEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = VaultColors.AccentEmerald,
                        containerColor = VaultColors.SurfaceElevated,
                        labelColor = VaultColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) VaultColors.AccentEmerald else VaultColors.GlassBorderSubtle,
                        selectedBorderColor = VaultColors.AccentEmerald,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }

            // Tags Filter Chips
            availableTags.forEach { tag ->
                val isSelected = selectedTag == tag
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onTagSelected(if (isSelected) null else tag)
                    },
                    label = { Text("#$tag", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF818CF8).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF818CF8),
                        containerColor = VaultColors.SurfaceElevated,
                        labelColor = VaultColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) Color(0xFF818CF8) else VaultColors.GlassBorderSubtle,
                        selectedBorderColor = Color(0xFF818CF8),
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }
    }
}

/**
 * Grid Presentation for Media item.
 */
@Composable
fun MediaGridTile(
    item: VaultItemEntity,
    repository: VaultRepository,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingThumb by remember { mutableStateOf(true) }

    LaunchedEffect(item.id, item.modifiedAt) {
        isLoadingThumb = true
        if (item.category == "IMAGE" || item.category == "VIDEO") {
            thumbnail = MediaThumbnailHelper.loadThumbnail(item, repository)
        }
        isLoadingThumb = false
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(VaultColors.SurfaceElevated)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("media_grid_item_${item.id}")
    ) {
        // Thumbnail or Icon Fallback
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoadingThumb && (item.category == "IMAGE" || item.category == "VIDEO")) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = VaultColors.AccentCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    val icon = when (item.category) {
                        "IMAGE" -> Icons.Default.Image
                        "VIDEO" -> Icons.Default.Videocam
                        "AUDIO" -> Icons.Default.MusicNote
                        else -> Icons.Default.Image
                    }
                    val iconColor = when (item.category) {
                        "IMAGE" -> VaultColors.AccentCyan
                        "VIDEO" -> Color(0xFF818CF8)
                        "AUDIO" -> VaultColors.AccentEmerald
                        else -> VaultColors.TextSecondary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Dark gradient shade at bottom for title readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Bottom Info Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (item.isFavorite) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = VaultColors.AccentCrimson,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Video Play Overlay Badge
        if (item.category == "VIDEO") {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Multi-select Checkbox
        if (isMultiSelectMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) VaultColors.AccentCyan else Color.Black.copy(alpha = 0.6f))
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = VaultColors.Canvas,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Unselected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * List Presentation for Media item.
 */
@Composable
fun MediaListRow(
    item: VaultItemEntity,
    repository: VaultRepository,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(item.id, item.modifiedAt) {
        if (item.category == "IMAGE" || item.category == "VIDEO") {
            thumbnail = MediaThumbnailHelper.loadThumbnail(item, repository, targetWidth = 120, targetHeight = 120)
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) VaultColors.SurfaceHighlight else VaultColors.SurfaceElevated)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VaultColors.SurfaceOverlay)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val icon = when (item.category) {
                    "IMAGE" -> Icons.Default.Image
                    "VIDEO" -> Icons.Default.Videocam
                    "AUDIO" -> Icons.Default.MusicNote
                    else -> Icons.Default.Image
                }
                val iconColor = when (item.category) {
                    "IMAGE" -> VaultColors.AccentCyan
                    "VIDEO" -> Color(0xFF818CF8)
                    "AUDIO" -> VaultColors.AccentEmerald
                    else -> VaultColors.TextSecondary
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Metadata
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = VaultColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.isFavorite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatBytes(item.sizeBytes),
                    color = VaultColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = " • ",
                    color = VaultColors.TextTertiary,
                    fontSize = 11.sp
                )
                Text(
                    text = dateFormat.format(Date(item.createdAt)),
                    color = VaultColors.TextTertiary,
                    fontSize = 11.sp
                )
                if (item.tags.isNotBlank()) {
                    Text(
                        text = " • #${item.tags.split(',').first().trim()}",
                        color = VaultColors.AccentCyan,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Multi-select Checkbox
        if (isMultiSelectMode) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Unselected",
                tint = if (isSelected) VaultColors.AccentCyan else VaultColors.TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Multi-Select Floating Action Bar.
 */
@Composable
fun MediaMultiSelectActionBar(
    selectedCount: Int,
    onMoveClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddTagClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit,
    onCancelSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VaultColors.SurfaceOverlay)
            .border(1.dp, VaultColors.GlassBorderMedium, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancelSelection,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = VaultColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$selectedCount selected",
                    color = VaultColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Move
                IconButton(onClick = onMoveClick) {
                    Icon(
                        imageVector = Icons.Default.DriveFileMove,
                        contentDescription = "Move",
                        tint = VaultColors.TextPrimary
                    )
                }
                // Favorite
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = VaultColors.AccentCrimson
                    )
                }
                // Tag
                IconButton(onClick = onAddTagClick) {
                    Icon(
                        imageVector = Icons.Default.Label,
                        contentDescription = "Tag",
                        tint = VaultColors.AccentCyan
                    )
                }
                // Export
                IconButton(onClick = onExportClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        tint = VaultColors.AccentEmerald
                    )
                }
                // Delete
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = VaultColors.AccentCrimson
                    )
                }
            }
        }
    }
}

/**
 * Secure Export Confirmation Dialog with Privacy Warning.
 */
@Composable
fun MediaExportConfirmDialog(
    itemTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VaultColors.AccentAmber
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Export Unencrypted Copy?", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "This creates an unencrypted copy outside PrivateVault. Other apps and cloud backups will be able to access this media file.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "File: $itemTitle",
                    color = VaultColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = VaultColors.AccentAmber)
            ) {
                Text("Continue Export", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        },
        containerColor = VaultColors.SurfaceElevated
    )
}

/**
 * Bulk Import Progress Dialog with Cancel Option.
 */
@Composable
fun BulkMediaImportProgressDialog(
    status: BulkImportStatus,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    if (status is BulkImportStatus.Idle) return

    AlertDialog(
        onDismissRequest = {
            if (status is BulkImportStatus.Completed || status is BulkImportStatus.Failed || status is BulkImportStatus.Cancelled) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = when (status) {
                    is BulkImportStatus.Preparing -> "Preparing Media Import..."
                    is BulkImportStatus.Importing -> "Importing into PrivateVault..."
                    is BulkImportStatus.Completed -> "Media Import Complete"
                    is BulkImportStatus.Failed -> "Media Import Failed"
                    BulkImportStatus.Cancelled -> "Import Cancelled"
                    BulkImportStatus.Idle -> ""
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = VaultColors.TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (status) {
                    is BulkImportStatus.Preparing -> {
                        Text("Reading ${status.total} media items...", color = VaultColors.TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = VaultColors.AccentCyan,
                            trackColor = VaultColors.SurfaceHighlight
                        )
                    }
                    is BulkImportStatus.Importing -> {
                        Text(
                            text = "Item ${status.current} of ${status.total}: ${status.currentFileName}",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = if (status.total > 0) status.current.toFloat() / status.total.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = VaultColors.AccentCyan,
                            trackColor = VaultColors.SurfaceHighlight
                        )
                    }
                    is BulkImportStatus.Completed -> {
                        Text(
                            text = "Successfully encrypted and imported ${status.importedCount} items into your vault.",
                            color = VaultColors.AccentEmerald,
                            fontSize = 13.sp
                        )
                        if (status.failedCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${status.failedCount} items could not be imported.",
                                color = VaultColors.AccentCrimson,
                                fontSize = 12.sp
                            )
                        }
                    }
                    is BulkImportStatus.Failed -> {
                        Text(status.reason, color = VaultColors.AccentCrimson, fontSize = 13.sp)
                    }
                    BulkImportStatus.Cancelled -> {
                        Text("Import operation was stopped by user.", color = VaultColors.TextSecondary, fontSize = 13.sp)
                    }
                    BulkImportStatus.Idle -> {}
                }
            }
        },
        confirmButton = {
            if (status is BulkImportStatus.Importing || status is BulkImportStatus.Preparing) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = VaultColors.AccentCrimson)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = VaultColors.AccentCyan)
                }
            }
        },
        containerColor = VaultColors.SurfaceElevated
    )
}

/**
 * Duplicate Media Resolution Dialog (Keep Both, Replace, Skip).
 */
@Composable
fun DuplicateMediaResolutionDialog(
    duplicateName: String,
    onKeepBoth: () -> Unit,
    onReplace: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = {
            Text("Duplicate File Detected", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "A file named \"$duplicateName\" already exists in this destination folder.",
                color = VaultColors.TextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = VaultColors.TextSecondary)
                }
                TextButton(onClick = onReplace) {
                    Text("Replace", color = VaultColors.AccentCrimson)
                }
                TextButton(onClick = onKeepBoth) {
                    Text("Keep Both", color = VaultColors.AccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = VaultColors.SurfaceElevated
    )
}
