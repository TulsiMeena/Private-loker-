package com.example.feature.organization

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.storage.VaultCategory

enum class SmartCollectionType(
    val displayName: String,
    val description: String
) {
    RECENTLY_ADDED("Recently Added", "Files imported or created recently"),
    RECENTLY_OPENED("Recently Opened", "Files opened or previewed in this vault"),
    FAVORITES("Favorites", "Starred items for rapid access"),
    LARGE_FILES("Large Files", "Files consuming significant storage"),
    UNTAGGED("Untagged Files", "Files missing categorization tags"),
    UNFILED("Unfiled Files", "Loose files in vault root without a folder"),
    DOCUMENTS("Documents", "PDFs, spreadsheets, and word documents"),
    IMAGES("Images & Photos", "Encrypted photo and visual library"),
    VIDEOS("Videos", "Protected video recordings"),
    AUDIO("Audio & Recordings", "Encrypted voice memos and audio files"),
    ARCHIVES("ZIP Archives", "Encrypted compressed archives"),
    CODE("Code Studio", "Source code files and scripts"),
    NOTES("Notes & Text", "Private text notes and markdown files");

    fun getIcon(): ImageVector = when (this) {
        RECENTLY_ADDED -> Icons.Default.Schedule
        RECENTLY_OPENED -> Icons.Default.History
        FAVORITES -> Icons.Default.Star
        LARGE_FILES -> Icons.Default.DataUsage
        UNTAGGED -> Icons.Default.LabelOff
        UNFILED -> Icons.Default.FolderOpen
        DOCUMENTS -> Icons.Default.Description
        IMAGES -> Icons.Default.Image
        VIDEOS -> Icons.Default.VideoFile
        AUDIO -> Icons.Default.AudioFile
        ARCHIVES -> Icons.Default.Archive
        CODE -> Icons.Default.Code
        NOTES -> Icons.Default.NoteAdd
    }
}

enum class InsightType {
    UNTAGGED,
    UNFILED,
    LARGE_FILES,
    DUPLICATES,
    RECENT_IMPORT
}

data class OrganizationInsight(
    val type: InsightType,
    val title: String,
    val count: Int,
    val description: String,
    val actionLabel: String
)

data class DuplicateFileGroup(
    val checksumSha256: String,
    val sizeBytes: Long,
    val items: List<VaultItemEntity>
)

data class ProposedMove(
    val item: VaultItemEntity,
    val currentFolderName: String,
    val targetFolderName: String,
    val targetParentId: Long?
)

data class OrganizationProposal(
    val title: String,
    val description: String,
    val moves: List<ProposedMove>
)

data class BatchOperationUndo(
    val description: String,
    val moves: List<Pair<Long, Long?>>, // itemId to previous folderId
    val timestamp: Long = System.currentTimeMillis()
)

data class TagSummary(
    val tag: String,
    val count: Int,
    val colorIndex: Int = 0
)

data class FolderTreeNode(
    val folder: VaultFolderEntity,
    val level: Int,
    val children: List<FolderTreeNode> = emptyList(),
    val directItemCount: Int = 0,
    val directSizeBytes: Long = 0L,
    val totalItemCount: Int = 0,
    val totalSizeBytes: Long = 0L
)
