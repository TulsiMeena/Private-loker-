package com.example.feature.documents

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExtendedDocMetrics(
    val pageCount: Int? = null,
    val dimensions: String? = null,
    val wordCount: Int? = null,
    val charCount: Int? = null,
    val lineCount: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoSheet(
    item: VaultItemEntity,
    folderName: String,
    repository: VaultRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var metrics by remember { mutableStateOf<ExtendedDocMetrics?>(null) }
    var isLoadingMetrics by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoadingMetrics = true
        val ext = item.title.substringAfterLast('.', "").lowercase()
        val computed = withContext(Dispatchers.IO) {
            when {
                ext == "pdf" -> {
                    var tempFile: File? = null
                    var pfd: ParcelFileDescriptor? = null
                    var renderer: PdfRenderer? = null
                    try {
                        val previewRes = repository.createTransientPreview(item)
                        tempFile = previewRes.getOrNull()
                        if (tempFile != null) {
                            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRenderer(pfd)
                            val count = renderer.pageCount
                            val dims = if (count > 0) {
                                val page = renderer.openPage(0)
                                val w = page.width
                                val h = page.height
                                page.close()
                                "${w} x ${h} pt"
                            } else null
                            ExtendedDocMetrics(pageCount = count, dimensions = dims)
                        } else null
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { renderer?.close() } catch (_: Exception) {}
                        try { pfd?.close() } catch (_: Exception) {}
                        tempFile?.let { com.example.core.storage.FileShredder.shredAndPurge(it) }
                    }
                }
                ext in listOf("txt", "md", "csv", "json", "xml", "rtf", "log") -> {
                    try {
                        val bytesRes = repository.decryptItemBytes(item)
                        val bytes = bytesRes.getOrNull()
                        if (bytes != null) {
                            val text = String(bytes, Charsets.UTF_8)
                            val chars = text.length
                            val lines = text.lines().size
                            val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
                            ExtendedDocMetrics(wordCount = words, charCount = chars, lineCount = lines)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                ext == "docx" -> {
                    try {
                        val bytesRes = repository.decryptItemBytes(item)
                        val bytes = bytesRes.getOrNull()
                        if (bytes != null) {
                            val text = DocxTextExtractor.extractText(bytes)
                            val chars = text.length
                            val lines = text.lines().size
                            val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
                            ExtendedDocMetrics(wordCount = words, charCount = chars, lineCount = lines)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }
        }
        metrics = computed
        isLoadingMetrics = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultColors.SurfaceElevated,
        contentColor = VaultColors.TextPrimary,
        modifier = modifier.testTag("document_info_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Document Properties",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.TextPrimary
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

            // Security Status Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = VaultColors.SurfaceGraphite,
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = VaultColors.AccentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AES-256-GCM Hardware Encrypted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VaultColors.AccentEmerald
                        )
                        Text(
                            text = "Zero-disk residue • Protected enclave",
                            fontSize = 11.sp,
                            color = VaultColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // General metadata section
            Text(
                text = "File Information",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VaultColors.AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            InfoItemRow("Filename", item.title)
            InfoItemRow("Type", item.mimeType.ifBlank { item.category })
            InfoItemRow("Size", Formatters.formatBytes(item.sizeBytes))
            InfoItemRow("Folder", folderName)
            InfoItemRow("Created", Formatters.formatTimestamp(item.createdAt))
            InfoItemRow("Modified", Formatters.formatTimestamp(item.modifiedAt))
            InfoItemRow("Favorite", if (item.isFavorite) "Yes" else "No")
            InfoItemRow("Tags", item.tags.ifBlank { "None" })

            // Content-specific metrics
            if (isLoadingMetrics) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = VaultColors.AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Analyzing encrypted document metrics...",
                        fontSize = 12.sp,
                        color = VaultColors.TextSecondary
                    )
                }
            } else metrics?.let { m ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Document Structure",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VaultColors.AccentCyan
                )
                Spacer(modifier = Modifier.height(8.dp))

                m.pageCount?.let { count ->
                    InfoItemRow("Total Pages", count.toString())
                }
                m.dimensions?.let { dims ->
                    InfoItemRow("Page Dimensions", dims)
                }
                m.wordCount?.let { words ->
                    InfoItemRow("Word Count", "$words words")
                }
                m.charCount?.let { chars ->
                    InfoItemRow("Character Count", "$chars characters")
                }
                m.lineCount?.let { lines ->
                    InfoItemRow("Line Count", "$lines lines")
                }
            }
        }
    }
}

@Composable
private fun InfoItemRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = VaultColors.TextSecondary
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = VaultColors.TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = VaultColors.GlassBorderSubtle)
    }
}
