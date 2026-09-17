package com.example.feature.code.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors

enum class CsvDelimiter(val label: String, val char: Char) {
    COMMA("Comma (,)", ','),
    SEMICOLON("Semicolon (;)", ';'),
    TAB("Tab (\\t)", '\t')
}

@Composable
fun CsvPreview(
    csvContent: String,
    modifier: Modifier = Modifier
) {
    var selectedDelimiter by remember { mutableStateOf(detectDelimiter(csvContent)) }
    val horizontalScrollState = rememberScrollState()

    val parsedTable = remember(csvContent, selectedDelimiter) {
        parseCsv(csvContent, selectedDelimiter.char)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
            .padding(12.dp)
    ) {
        // Delimiter and summary bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "${parsedTable.size} rows",
                color = VaultColors.AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp)
            )

            CsvDelimiter.values().forEach { delim ->
                FilterChip(
                    selected = selectedDelimiter == delim,
                    onClick = { selectedDelimiter = delim },
                    label = { Text(delim.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = VaultColors.AccentCyan,
                        containerColor = VaultColors.SurfaceOverlay,
                        labelColor = VaultColors.TextSecondary
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        if (parsedTable.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("CSV file is empty", color = VaultColors.TextTertiary, fontSize = 13.sp)
            }
            return@Column
        }

        // Table container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D1117))
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
        ) {
            val headers = parsedTable.firstOrNull() ?: emptyList()
            val rows = parsedTable.drop(1)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(Color(0xFF161B22))
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.width(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("#", color = VaultColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    headers.forEach { col ->
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = col,
                                color = VaultColors.AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Data Rows
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(rows) { index, row ->
                        val isEven = index % 2 == 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(if (isEven) Color(0xFF090B0E) else Color(0xFF0D1117))
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        ) {
                            // Row Number
                            Box(
                                modifier = Modifier.width(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = VaultColors.TextTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Columns
                            for (c in headers.indices) {
                                val cellVal = row.getOrNull(c) ?: ""
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = cellVal,
                                        color = VaultColors.TextPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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
 * Autodetects the most likely CSV delimiter by frequency in the first non-empty lines.
 */
private fun detectDelimiter(content: String): CsvDelimiter {
    val sample = content.lines().take(5).joinToString("\n")
    val commas = sample.count { it == ',' }
    val semicolons = sample.count { it == ';' }
    val tabs = sample.count { it == '\t' }

    return when {
        semicolons > commas && semicolons > tabs -> CsvDelimiter.SEMICOLON
        tabs > commas && tabs > semicolons -> CsvDelimiter.TAB
        else -> CsvDelimiter.COMMA
    }
}

/**
 * Robust CSV line parsing taking quotes into account.
 */
private fun parseCsv(content: String, delimiter: Char): List<List<String>> {
    val result = mutableListOf<List<String>>()
    val lines = content.lines()

    for (line in lines) {
        if (line.isBlank()) continue
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == delimiter && !inQuotes) {
                cells.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        cells.add(sb.toString().trim())
        result.add(cells)
    }

    return result
}
