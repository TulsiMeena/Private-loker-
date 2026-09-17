package com.example.feature.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentFilterBottomSheet(
    currentFilter: DocumentFilterState,
    availableTags: List<String>,
    onApplyFilters: (DocumentFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var typeFilter by remember { mutableStateOf(currentFilter.typeFilter) }
    var statusFilter by remember { mutableStateOf(currentFilter.statusFilter) }
    var sizeFilter by remember { mutableStateOf(currentFilter.sizeFilter) }
    var dateFilter by remember { mutableStateOf(currentFilter.dateFilter) }
    var selectedTag by remember { mutableStateOf(currentFilter.selectedTag) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultColors.SurfaceElevated,
        contentColor = VaultColors.TextPrimary,
        modifier = modifier.testTag("document_filter_sheet")
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
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Document Filters",
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

            // 1. Document Format / Type
            Text(
                text = "DOCUMENT FORMAT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VaultColors.AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DocumentTypeFilter.values().forEach { type ->
                    val selected = type == typeFilter
                    FilterChip(
                        selected = selected,
                        onClick = { typeFilter = type },
                        label = { Text(type.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VaultColors.AccentCyan,
                            containerColor = VaultColors.SurfaceGraphite,
                            labelColor = VaultColors.TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                            enabled = true,
                            selected = selected
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = VaultColors.GlassBorderSubtle)
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Status Filter
            Text(
                text = "STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VaultColors.AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DocumentStatusFilter.values().forEach { status ->
                    val selected = status == statusFilter
                    FilterChip(
                        selected = selected,
                        onClick = { statusFilter = status },
                        label = { Text(status.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VaultColors.AccentCyan,
                            containerColor = VaultColors.SurfaceGraphite,
                            labelColor = VaultColors.TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                            enabled = true,
                            selected = selected
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = VaultColors.GlassBorderSubtle)
            Spacer(modifier = Modifier.height(16.dp))

            // 3. Size Filter
            Text(
                text = "DOCUMENT SIZE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VaultColors.AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DocumentSizeFilter.values().forEach { size ->
                    val selected = size == sizeFilter
                    FilterChip(
                        selected = selected,
                        onClick = { sizeFilter = size },
                        label = { Text(size.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VaultColors.AccentCyan,
                            containerColor = VaultColors.SurfaceGraphite,
                            labelColor = VaultColors.TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                            enabled = true,
                            selected = selected
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = VaultColors.GlassBorderSubtle)
            Spacer(modifier = Modifier.height(16.dp))

            // 4. Date Filter
            Text(
                text = "DATE ADDED / MODIFIED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VaultColors.AccentCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DocumentDateFilter.values().forEach { date ->
                    val selected = date == dateFilter
                    FilterChip(
                        selected = selected,
                        onClick = { dateFilter = date },
                        label = { Text(date.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VaultColors.AccentCyan,
                            containerColor = VaultColors.SurfaceGraphite,
                            labelColor = VaultColors.TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                            enabled = true,
                            selected = selected
                        )
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = VaultColors.GlassBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // 5. Tags
                Text(
                    text = "TAGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.AccentCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTags.forEach { tag ->
                        val selected = tag.equals(selectedTag, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedTag = if (selected) null else tag
                            },
                            label = { Text("#$tag", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultColors.AccentAmber.copy(alpha = 0.2f),
                                selectedLabelColor = VaultColors.AccentAmber,
                                containerColor = VaultColors.SurfaceGraphite,
                                labelColor = VaultColors.TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (selected) VaultColors.AccentAmber else VaultColors.GlassBorderSubtle,
                                enabled = true,
                                selected = selected
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        typeFilter = DocumentTypeFilter.ALL
                        statusFilter = DocumentStatusFilter.ALL
                        sizeFilter = DocumentSizeFilter.ALL
                        dateFilter = DocumentDateFilter.ALL
                        selectedTag = null
                        onResetFilters()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }

                Button(
                    onClick = {
                        onApplyFilters(
                            DocumentFilterState(
                                typeFilter = typeFilter,
                                statusFilter = statusFilter,
                                sizeFilter = sizeFilter,
                                dateFilter = dateFilter,
                                selectedTag = selectedTag
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
