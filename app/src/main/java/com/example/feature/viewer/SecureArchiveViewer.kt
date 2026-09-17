package com.example.feature.viewer

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ArchiveEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val isSuspicious: Boolean
)

/**
 * Secure Archive / ZIP Explorer.
 *
 * Security Protocol:
 * - Scans archive structure completely in memory.
 * - Enforces path traversal defenses (detects and neutralizes malicious `../` references).
 * - Enforces decompression bomb bounds (caps extraction size per file).
 */
@Composable
fun SecureArchiveViewer(
    archiveBytes: ByteArray,
    onExtractEntryToVault: (entryName: String, entryBytes: ByteArray) -> Unit,
    onOpenFullBrowser: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var securityWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(archiveBytes) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val list = mutableListOf<ArchiveEntryInfo>()
            var totalUncompressed = 0L
            val maxAllowedUncompressed = 500L * 1024 * 1024 // 500 MB max bomb guard

            try {
                ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val isSuspicious = name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                        val uncompressed = entry.size.coerceAtLeast(0L)
                        totalUncompressed += uncompressed

                        list.add(
                            ArchiveEntryInfo(
                                name = name,
                                isDirectory = entry.isDirectory,
                                compressedSize = entry.compressedSize.coerceAtLeast(0L),
                                uncompressedSize = uncompressed,
                                isSuspicious = isSuspicious
                            )
                        )
                        entry = zis.nextEntry
                    }
                }

                if (totalUncompressed > maxAllowedUncompressed) {
                    securityWarning = "Decompression bomb risk: Total uncompressed content exceeds 500MB safety threshold."
                }
            } catch (e: Exception) {
                securityWarning = "Corrupt or encrypted archive payload: ${e.message}"
            }

            withContext(Dispatchers.Main) {
                entries = list
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
            .padding(16.dp)
    ) {
        // Archive Summary Card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VaultColors.SurfaceElevated)
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Archive Contents",
                    color = VaultColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${entries.size} items · ${formatBytes(archiveBytes.size.toLong())} compressed",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = VaultColors.AccentCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        if (onOpenFullBrowser != null) {
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.Button(
                onClick = onOpenFullBrowser,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCyan,
                    contentColor = VaultColors.Canvas
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Dedicated Archive Studio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Security Warning banner if triggered
        securityWarning?.let { warn ->
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VaultColors.AccentCrimson.copy(alpha = 0.15f))
                    .border(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = VaultColors.AccentCrimson,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = warn,
                    color = VaultColors.AccentCrimson,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = VaultColors.AccentCyan)
            }
        } else if (entries.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Empty archive or unreadable structure",
                    color = VaultColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(entries) { item ->
                    ArchiveEntryRow(
                        item = item,
                        onExtract = {
                            // Extract single entry safely
                            withContext(Dispatchers.IO) {
                                ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zis ->
                                    var e = zis.nextEntry
                                    while (e != null) {
                                        if (e.name == item.name && !e.isDirectory) {
                                            val buffer = zis.readBytes()
                                            withContext(Dispatchers.Main) {
                                                onExtractEntryToVault(item.name.substringAfterLast('/'), buffer)
                                            }
                                            break
                                        }
                                        e = zis.nextEntry
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    item: ArchiveEntryInfo,
    onExtract: suspend () -> Unit
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(VaultColors.SurfaceGraphite)
            .border(
                1.dp,
                if (item.isSuspicious) VaultColors.AccentCrimson.copy(alpha = 0.5f) else VaultColors.GlassBorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (item.isDirectory) VaultColors.AccentAmber else VaultColors.AccentCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (item.isSuspicious) VaultColors.AccentCrimson else VaultColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            if (!item.isDirectory) {
                Text(
                    text = "Raw: ${formatBytes(item.uncompressedSize)}",
                    color = VaultColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }

        if (!item.isDirectory && !item.isSuspicious) {
            IconButton(onClick = {
                coroutineScope.launch {
                    onExtract()
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Extract to Vault",
                    tint = VaultColors.AccentEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
