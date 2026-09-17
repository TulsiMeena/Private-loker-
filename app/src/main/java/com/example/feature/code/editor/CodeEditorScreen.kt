package com.example.feature.code.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.VaultColors
import com.example.feature.code.CodeLanguage
import com.example.feature.code.SyntaxHighlighter
import com.example.feature.code.preview.CsvPreview
import com.example.feature.code.preview.MarkdownPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    itemId: Long,
    viewModel: CodeEditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(itemId) {
        viewModel.loadFile(itemId)
    }

    val textFieldValue by viewModel.textFieldValue.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val isReadOnly by viewModel.isReadOnly.collectAsStateWithLifecycle()
    val isLargeFile by viewModel.isLargeFile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val indentOption by viewModel.indentOption.collectAsStateWithLifecycle()
    val isWordWrap by viewModel.isWordWrap.collectAsStateWithLifecycle()
    val isPreviewMode by viewModel.isPreviewMode.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val replaceText by viewModel.replaceText.collectAsStateWithLifecycle()
    val matchCase by viewModel.matchCase.collectAsStateWithLifecycle()
    val wholeWord by viewModel.wholeWord.collectAsStateWithLifecycle()
    val useRegex by viewModel.useRegex.collectAsStateWithLifecycle()
    val regexError by viewModel.regexError.collectAsStateWithLifecycle()
    val searchMatches by viewModel.searchMatches.collectAsStateWithLifecycle()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    val tabs by viewModel.tabManager.tabs.collectAsStateWithLifecycle()
    val activeTabIndex by viewModel.tabManager.activeTabIndex.collectAsStateWithLifecycle()
    val currentTab = viewModel.tabManager.currentTab

    // Dialog states
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showReplaceAllDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    val handleBackPress = {
        if (currentTab?.isDirty == true) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(VaultColors.SurfaceDark)) {
                // Top App Bar
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTab?.title ?: "Code Studio",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VaultColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                // Language badge (clickable to override)
                                currentTab?.language?.let { lang ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                                            .clickable { showLanguageDialog = true }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = lang.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VaultColors.AccentCyan
                                        )
                                    }
                                }
                            }

                            // Save status badge
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                val statusColor = when (saveStatus) {
                                    SaveStatus.SAVED -> VaultColors.AccentSuccess
                                    SaveStatus.UNSAVED -> VaultColors.AccentAmber
                                    SaveStatus.SAVING -> VaultColors.AccentCyan
                                    SaveStatus.READ_ONLY -> VaultColors.TextTertiary
                                    SaveStatus.ERROR -> VaultColors.AccentRed
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(statusColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = saveStatus.label,
                                    fontSize = 10.sp,
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBackPress, modifier = Modifier.testTag("code_editor_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VaultColors.TextPrimary)
                        }
                    },
                    actions = {
                        // Search Button
                        IconButton(onClick = { viewModel.toggleSearch() }, modifier = Modifier.testTag("code_editor_search_btn")) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchOpen) VaultColors.AccentCyan else VaultColors.TextSecondary
                            )
                        }

                        // Preview toggle button (if preview supported)
                        if (currentTab?.language?.supportsPreview == true) {
                            IconButton(onClick = { viewModel.togglePreviewMode() }, modifier = Modifier.testTag("code_editor_preview_btn")) {
                                Icon(
                                    if (isPreviewMode) Icons.Default.Edit else Icons.Default.Preview,
                                    contentDescription = if (isPreviewMode) "Switch to Editor" else "Switch to Preview",
                                    tint = if (isPreviewMode) VaultColors.AccentCyan else VaultColors.TextSecondary
                                )
                            }
                        }

                        // Save Button
                        if (currentTab?.isDirty == true && !isReadOnly) {
                            IconButton(onClick = { viewModel.saveContent() }, modifier = Modifier.testTag("code_editor_save_btn")) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = VaultColors.AccentCyan)
                            }
                        }

                        // Overflow Menu
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }, modifier = Modifier.testTag("code_editor_menu_btn")) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = VaultColors.TextSecondary)
                            }

                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false },
                                modifier = Modifier.background(VaultColors.SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Language: ${currentTab?.language?.displayName ?: ""}", color = VaultColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = VaultColors.AccentCyan) },
                                    onClick = {
                                        showSettingsMenu = false
                                        showLanguageDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isWordWrap) "Word Wrap: ON" else "Word Wrap: OFF", color = VaultColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null, tint = VaultColors.TextSecondary) },
                                    onClick = {
                                        showSettingsMenu = false
                                        viewModel.toggleWordWrap()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Font Size: ${fontSize.label}", color = VaultColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null, tint = VaultColors.TextSecondary) },
                                    onClick = {
                                        showSettingsMenu = false
                                        val next = when (fontSize) {
                                            EditorFontSize.SMALL -> EditorFontSize.MEDIUM
                                            EditorFontSize.MEDIUM -> EditorFontSize.LARGE
                                            EditorFontSize.LARGE -> EditorFontSize.EXTRA_LARGE
                                            EditorFontSize.EXTRA_LARGE -> EditorFontSize.SMALL
                                        }
                                        viewModel.setFontSize(next)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Indent: ${indentOption.label}", color = VaultColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = VaultColors.TextSecondary) },
                                    onClick = {
                                        showSettingsMenu = false
                                        val next = when (indentOption) {
                                            IndentOption.TWO_SPACES -> IndentOption.FOUR_SPACES
                                            IndentOption.FOUR_SPACES -> IndentOption.TAB
                                            IndentOption.TAB -> IndentOption.TWO_SPACES
                                        }
                                        viewModel.setIndentOption(next)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isReadOnly) "Disable Read-Only" else "Set Read-Only", color = VaultColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = VaultColors.TextSecondary) },
                                    onClick = {
                                        showSettingsMenu = false
                                        viewModel.toggleReadOnly()
                                    }
                                )

                                // JSON formatting shortcuts
                                if (currentTab?.language == CodeLanguage.JSON) {
                                    HorizontalDivider(color = VaultColors.GlassBorderSubtle)
                                    DropdownMenuItem(
                                        text = { Text("Format JSON", color = VaultColors.AccentCyan) },
                                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = VaultColors.AccentCyan) },
                                        onClick = {
                                            showSettingsMenu = false
                                            viewModel.prettyPrintJson()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Minify JSON", color = VaultColors.TextSecondary) },
                                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = VaultColors.TextSecondary) },
                                        onClick = {
                                            showSettingsMenu = false
                                            viewModel.minifyJson()
                                        }
                                    )
                                }

                                // XML formatting shortcut
                                if (currentTab?.language == CodeLanguage.XML || currentTab?.language == CodeLanguage.HTML) {
                                    HorizontalDivider(color = VaultColors.GlassBorderSubtle)
                                    DropdownMenuItem(
                                        text = { Text("Format XML/HTML", color = VaultColors.AccentCyan) },
                                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = VaultColors.AccentCyan) },
                                        onClick = {
                                            showSettingsMenu = false
                                            viewModel.formatXml()
                                        }
                                    )
                                }

                                HorizontalDivider(color = VaultColors.GlassBorderSubtle)
                                DropdownMenuItem(
                                    text = { Text("Document Statistics", color = VaultColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = VaultColors.TextSecondary) },
                                    onClick = {
                                        showSettingsMenu = false
                                        showStatsDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.SurfaceDark)
                )

                // Multi-tab Strip
                if (tabs.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117))
                            .border(1.dp, VaultColors.GlassBorderSubtle)
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        itemsIndexed(tabs) { index, tab ->
                            val isActive = index == activeTabIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isActive) Color(0xFF1F242C) else Color(0xFF13171E))
                                    .border(
                                        1.dp,
                                        if (isActive) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.tabManager.switchTab(index) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (tab.isDirty) "${tab.title} *" else tab.title,
                                    fontSize = 12.sp,
                                    color = if (isActive) VaultColors.TextPrimary else VaultColors.TextSecondary,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab",
                                    tint = VaultColors.TextTertiary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.tabManager.closeTab(index) }
                                )
                            }
                        }
                    }
                }

                // Animated Search & Replace Panel
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF11151C))
                            .border(1.dp, VaultColors.GlassBorderSubtle)
                            .padding(10.dp)
                    ) {
                        // Search Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Find...", fontSize = 13.sp, color = VaultColors.TextTertiary) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = VaultColors.TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VaultColors.AccentCyan,
                                    unfocusedBorderColor = VaultColors.GlassBorderSubtle
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("code_editor_search_input")
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Match Counter
                            val matchText = if (searchQuery.isBlank()) ""
                            else if (searchMatches.isEmpty()) "0/0"
                            else "${currentMatchIndex + 1}/${searchMatches.size}"

                            Text(
                                text = matchText,
                                fontSize = 11.sp,
                                color = if (searchMatches.isEmpty() && searchQuery.isNotBlank()) VaultColors.AccentRed else VaultColors.AccentCyan,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            IconButton(onClick = { viewModel.prevMatch() }, enabled = searchMatches.isNotEmpty()) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", tint = VaultColors.TextSecondary)
                            }
                            IconButton(onClick = { viewModel.nextMatch() }, enabled = searchMatches.isNotEmpty()) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", tint = VaultColors.TextSecondary)
                            }
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search", tint = VaultColors.TextTertiary)
                            }
                        }

                        // Regex Error if any
                        regexError?.let { err ->
                            Text(
                                text = "Regex Error: $err",
                                color = VaultColors.AccentRed,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        // Replace Row
                        if (!isReadOnly) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = replaceText,
                                    onValueChange = { viewModel.setReplaceText(it) },
                                    placeholder = { Text("Replace with...", fontSize = 13.sp, color = VaultColors.TextTertiary) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = VaultColors.TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VaultColors.AccentCyan,
                                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("code_editor_replace_input")
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                OutlinedButton(
                                    onClick = { viewModel.replaceCurrentMatch() },
                                    enabled = searchMatches.isNotEmpty(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan)
                                ) {
                                    Text("Replace", fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                OutlinedButton(
                                    onClick = { showReplaceAllDialog = true },
                                    enabled = searchMatches.isNotEmpty(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentAmber)
                                ) {
                                    Text("All", fontSize = 11.sp)
                                }
                            }
                        }

                        // Options Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            FilterChip(
                                selected = matchCase,
                                onClick = { viewModel.toggleMatchCase() },
                                label = { Text("Match Case", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = VaultColors.AccentCyan
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            FilterChip(
                                selected = wholeWord,
                                onClick = { viewModel.toggleWholeWord() },
                                label = { Text("Whole Word", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = VaultColors.AccentCyan
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            FilterChip(
                                selected = useRegex,
                                onClick = { viewModel.toggleUseRegex() },
                                label = { Text("Regex", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = VaultColors.AccentCyan
                                )
                            )
                        }
                    }
                }

                // Large File Banner
                if (isLargeFile) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VaultColors.AccentAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = VaultColors.AccentAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Large file (> 1.5MB). Optimized read-only streaming mode enabled.",
                            fontSize = 11.sp,
                            color = VaultColors.AccentAmber
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!isPreviewMode) {
                // Bottom Coder Quick Utility Toolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VaultColors.SurfaceDark)
                        .border(1.dp, VaultColors.GlassBorderSubtle)
                ) {
                    // Symbol quick row
                    val symbols = listOf("{", "}", "(", ")", "[", "]", "\"", "'", "<", ">", ";", "=", ":", ",", ".", "_")
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090B0E))
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        itemsIndexed(symbols) { _, sym ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF161B22))
                                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val closeSym = when (sym) {
                                            "{" -> "}"
                                            "(" -> ")"
                                            "[" -> "]"
                                            "\"" -> "\""
                                            "'" -> "'"
                                            "<" -> ">"
                                            else -> null
                                        }
                                        viewModel.insertSymbolOrPair(sym, closeSym)
                                    }
                            ) {
                                Text(
                                    text = sym,
                                    color = VaultColors.AccentCyan,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Action tools row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Undo / Redo
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) VaultColors.AccentCyan else VaultColors.TextTertiary
                                )
                            }
                            IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) VaultColors.AccentCyan else VaultColors.TextTertiary
                                )
                            }
                        }

                        // Comment toggle & Indent
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            currentTab?.language?.commentPrefix?.let { prefix ->
                                OutlinedButton(
                                    onClick = { viewModel.toggleComment() },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(prefix, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = VaultColors.AccentCyan)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.indentLines(isUnindent = false) },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("Tab", fontSize = 11.sp, color = VaultColors.TextSecondary)
                            }
                            OutlinedButton(
                                onClick = { viewModel.indentLines(isUnindent = true) }
                            ) {
                                Text("Untab", fontSize = 11.sp, color = VaultColors.TextSecondary)
                            }
                        }

                        // Stats Chip
                        Text(
                            text = "${stats.lines}L · ${stats.words}W",
                            fontSize = 11.sp,
                            color = VaultColors.TextTertiary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { showStatsDialog = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        containerColor = VaultColors.Canvas,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VaultColors.AccentCyan)
                }
            } else if (isPreviewMode) {
                // Preview Pane
                when (currentTab?.language) {
                    CodeLanguage.MARKDOWN -> MarkdownPreview(markdownContent = textFieldValue.text)
                    CodeLanguage.CSV -> CsvPreview(csvContent = textFieldValue.text)
                    else -> {
                        // Generic formatted preview
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(VaultColors.Canvas)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = SyntaxHighlighter.highlight(
                                    code = textFieldValue.text,
                                    language = currentTab?.language ?: CodeLanguage.PLAIN_TEXT,
                                    searchQuery = searchQuery,
                                    matchCase = matchCase
                                ),
                                fontSize = fontSize.size,
                                lineHeight = fontSize.lineHeight,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                // Code Editor Workspace with Line Numbers Gutter
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()
                val lineCount = textFieldValue.text.lines().size.coerceAtLeast(1)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VaultColors.Canvas)
                ) {
                    // Line Numbers Gutter
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(46.dp)
                            .background(Color(0xFF090B0E))
                            .border(1.dp, VaultColors.GlassBorderSubtle)
                            .verticalScroll(verticalScrollState)
                            .padding(top = 12.dp, bottom = 40.dp, end = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = i.toString().padStart(3, '0'),
                                color = VaultColors.TextTertiary,
                                fontSize = fontSize.size,
                                lineHeight = fontSize.lineHeight,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Main Editor Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(verticalScrollState)
                            .then(if (!isWordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { viewModel.onTextChanged(it) },
                            readOnly = isReadOnly,
                            textStyle = TextStyle(
                                color = VaultColors.TextPrimary,
                                fontSize = fontSize.size,
                                lineHeight = fontSize.lineHeight,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(VaultColors.AccentCyan),
                            visualTransformation = VisualTransformation.None,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("code_editor_text_field")
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // Unsaved Changes Confirmation Dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes in ${currentTab?.title}. Would you like to save before exiting?", color = VaultColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        viewModel.saveContent(onSuccess = { onNavigateBack() })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                ) {
                    Text("Save & Exit", color = VaultColors.TextInverse)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        onNavigateBack()
                    }) {
                        Text("Discard", color = VaultColors.AccentRed)
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Cancel", color = VaultColors.TextSecondary)
                    }
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }

    // Replace All Confirmation Dialog
    if (showReplaceAllDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceAllDialog = false },
            title = { Text("Replace All Matches", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Replace all ${searchMatches.size} occurrence(s) with \"$replaceText\"?", color = VaultColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceAllDialog = false
                        viewModel.replaceAllMatches()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber)
                ) {
                    Text("Confirm Replace All", color = VaultColors.TextInverse)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceAllDialog = false }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language Mode", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                val languages = CodeLanguage.getCreatableLanguages()
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    languages.forEach { lang ->
                        val isCurrent = lang == currentTab?.language
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = lang.displayName,
                                color = if (isCurrent) VaultColors.AccentCyan else VaultColors.TextPrimary,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = VaultColors.AccentCyan)
                            }
                        }
                        HorizontalDivider(color = VaultColors.GlassBorderSubtle)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }

    // Document Statistics Dialog
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text("Document Statistics", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    StatRow(label = "Total Lines", value = stats.lines.toString())
                    StatRow(label = "Total Words", value = stats.words.toString())
                    StatRow(label = "Total Characters", value = stats.characters.toString())
                    if (stats.selectedChars > 0) {
                        HorizontalDivider(color = VaultColors.GlassBorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                        StatRow(label = "Selected Characters", value = stats.selectedChars.toString())
                    }
                    StatRow(label = "File Language", value = currentTab?.language?.displayName ?: "Unknown")
                    StatRow(label = "Save State", value = saveStatus.label)
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("OK", color = VaultColors.AccentCyan)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }

    // Error Snackbar / Alert
    errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Studio Notice", color = VaultColors.AccentAmber, fontWeight = FontWeight.Bold) },
            text = { Text(err, color = VaultColors.TextPrimary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearErrorMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber)
                ) {
                    Text("Dismiss", color = VaultColors.TextInverse)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = label, color = VaultColors.TextSecondary, fontSize = 13.sp)
        Text(text = value, color = VaultColors.AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
