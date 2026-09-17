package com.example.feature.documents

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.util.Formatters

data class FormatStyle(
    val color: Color,
    val icon: ImageVector,
    val label: String
)

fun getDocumentFormatStyle(title: String): FormatStyle {
    val ext = title.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> FormatStyle(VaultColors.AccentCrimson, Icons.Default.PictureAsPdf, "PDF")
        "doc", "docx" -> FormatStyle(Color(0xFF2B579A), Icons.Default.Description, "WORD")
        "txt", "log" -> FormatStyle(VaultColors.AccentCyan, Icons.Default.Description, "TEXT")
        "md", "markdown" -> FormatStyle(Color(0xFF00B0FF), Icons.Default.EditNote, "MD")
        "csv", "xls", "xlsx" -> FormatStyle(VaultColors.AccentEmerald, Icons.Default.TableChart, "CSV")
        "json" -> FormatStyle(VaultColors.AccentAmber, Icons.Default.Code, "JSON")
        "xml" -> FormatStyle(Color(0xFFFF9100), Icons.Default.Code, "XML")
        "rtf" -> FormatStyle(Color(0xFFAB47BC), Icons.Default.Description, "RTF")
        else -> FormatStyle(VaultColors.TextSecondary, Icons.Default.Description, ext.uppercase().take(4))
    }
}

/**
 * Large visual document card for Grid view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentGridCard(
    item: VaultItemEntity,
    repository: VaultRepository,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onTags: () -> Unit,
    onInfo: () -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val formatStyle = remember(item.title) { getDocumentFormatStyle(item.title) }
    val ext = remember(item.title) { item.title.substringAfterLast('.', "").lowercase() }

    // Lazy thumbnail loading for PDF
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id) {
        if (ext == "pdf") {
            val cached = DocumentThumbnailHelper.getCachedThumbnail(item.id)
            if (cached != null) {
                thumbnailBitmap = cached
            } else {
                thumbnailBitmap = DocumentThumbnailHelper.loadPdfThumbnail(item, repository)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.12f) else VaultColors.SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("document_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Visual Preview Box / Header
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(VaultColors.SurfaceGraphite)
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback Format Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(formatStyle.color.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = formatStyle.icon,
                            contentDescription = null,
                            tint = formatStyle.color,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Extension Badge (Top Left)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = formatStyle.color,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = formatStyle.label,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Checkbox or Favorite / More (Top Right)
                if (isMultiSelectActive) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VaultColors.AccentCyan,
                            uncheckedColor = VaultColors.TextSecondary
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = VaultColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DocumentContextMenu(
                            expanded = showMenu,
                            item = item,
                            onDismiss = { showMenu = false },
                            onToggleFavorite = onToggleFavorite,
                            onRename = onRename,
                            onMove = onMove,
                            onCopy = onCopy,
                            onDuplicate = onDuplicate,
                            onTags = onTags,
                            onInfo = onInfo,
                            onExport = onExport,
                            onPrint = onPrint,
                            onDelete = onDelete
                        )
                    }
                }
            }

            // Text Metadata Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VaultColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = DocFormatters.formatBytes(item.sizeBytes),
                        fontSize = 11.sp,
                        color = VaultColors.TextSecondary
                    )
                    Text(
                        text = DocFormatters.formatDate(item.modifiedAt),
                        fontSize = 10.sp,
                        color = VaultColors.TextTertiary
                    )
                }

                if (item.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item.tags.split(",").take(2).forEach { tag ->
                            val cleanTag = tag.trim()
                            if (cleanTag.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = VaultColors.AccentAmber.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, VaultColors.AccentAmber.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "#$cleanTag",
                                        fontSize = 9.sp,
                                        color = VaultColors.AccentAmber,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact professional document row for List view.
 */
@Composable
fun DocumentListRow(
    item: VaultItemEntity,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onTags: () -> Unit,
    onInfo: () -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val formatStyle = remember(item.title) { getDocumentFormatStyle(item.title) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.12f) else VaultColors.SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag("document_row_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (isMultiSelectActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = VaultColors.AccentCyan,
                        uncheckedColor = VaultColors.TextSecondary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(formatStyle.color.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = formatStyle.icon,
                        contentDescription = null,
                        tint = formatStyle.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VaultColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DocFormatters.formatBytes(item.sizeBytes),
                        fontSize = 11.sp,
                        color = VaultColors.TextSecondary
                    )
                    Text(
                        text = " • ",
                        fontSize = 11.sp,
                        color = VaultColors.TextTertiary
                    )
                    Text(
                        text = DocFormatters.formatDate(item.modifiedAt),
                        fontSize = 11.sp,
                        color = VaultColors.TextTertiary
                    )
                    if (item.tags.isNotBlank()) {
                        Text(
                            text = " • #${item.tags.substringBefore(',')}",
                            fontSize = 11.sp,
                            color = VaultColors.AccentAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!isMultiSelectActive) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = VaultColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DocumentContextMenu(
                        expanded = showMenu,
                        item = item,
                        onDismiss = { showMenu = false },
                        onToggleFavorite = onToggleFavorite,
                        onRename = onRename,
                        onMove = onMove,
                        onCopy = onCopy,
                        onDuplicate = onDuplicate,
                        onTags = onTags,
                        onInfo = onInfo,
                        onExport = onExport,
                        onPrint = onPrint,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

/**
 * Universal Context Menu for documents.
 */
@Composable
fun DocumentContextMenu(
    expanded: Boolean,
    item: VaultItemEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onTags: () -> Unit,
    onInfo: () -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(VaultColors.SurfaceElevated)
    ) {
        DropdownMenuItem(
            text = { Text(if (item.isFavorite) "Remove from Favorites" else "Add to Favorites", color = VaultColors.TextPrimary) },
            leadingIcon = {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = VaultColors.AccentAmber
                )
            },
            onClick = {
                onDismiss()
                onToggleFavorite()
            }
        )

        DropdownMenuItem(
            text = { Text("Rename", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = {
                onDismiss()
                onRename()
            }
        )

        DropdownMenuItem(
            text = { Text("Move to Folder", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = {
                onDismiss()
                onMove()
            }
        )

        DropdownMenuItem(
            text = { Text("Duplicate in Vault", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = {
                onDismiss()
                onDuplicate()
            }
        )

        DropdownMenuItem(
            text = { Text("Manage Tags", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = {
                onDismiss()
                onTags()
            }
        )

        DropdownMenuItem(
            text = { Text("Document Info", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = {
                onDismiss()
                onInfo()
            }
        )

        DropdownMenuItem(
            text = { Text("Export Document...", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, tint = VaultColors.AccentAmber) },
            onClick = {
                onDismiss()
                onExport()
            }
        )

        DropdownMenuItem(
            text = { Text("Print Document...", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = VaultColors.TextSecondary) },
            onClick = {
                onDismiss()
                onPrint()
            }
        )

        DropdownMenuItem(
            text = { Text("Move to Trash", color = VaultColors.AccentCrimson) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VaultColors.AccentCrimson) },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

private object DocFormatters {
    private val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy · HH:mm", java.util.Locale.getDefault())

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(java.util.Date(timestamp))
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

