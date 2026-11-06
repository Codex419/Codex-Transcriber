package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DevicesFold
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FileProcessStatus
import com.example.model.MediaKind
import com.example.model.QueueFilter
import com.example.ui.adaptive.FoldableAdaptiveState
import com.example.ui.adaptive.FoldableHingeCreaseHorizontal
import com.example.ui.adaptive.FoldableHingeGutterVertical
import com.example.ui.adaptive.FoldablePosture
import com.example.ui.adaptive.StudioNavigationRail
import com.example.ui.adaptive.rememberFoldableAdaptiveState
import com.example.ui.components.ActionAndProgressStrip
import com.example.ui.components.CompactSettingsSummaryBar
import com.example.ui.components.ExecutionLogPanel
import com.example.ui.components.FinalizedFilesSubmenuPanel
import com.example.ui.components.InputSourceCard
import com.example.ui.components.QueueFileItemRow
import com.example.ui.components.QueueFilterHeader
import com.example.ui.components.QueueItemInspectorDialog
import com.example.ui.components.SettingsMenuDialog
import com.example.ui.components.SnapdragonTelemetryBanner
import com.example.ui.components.SpectrogramVisualizerCard
import com.example.ui.components.StopProcessingConfirmDialog
import com.example.ui.components.WaveformLyricSyncStudioDialog
import com.example.ui.theme.ElectricAmethyst
import com.example.ui.theme.ElectricAmethystContainer
import com.example.ui.theme.ElectricAmethystLight
import com.example.ui.theme.SnapdragonCyan
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TranscriberViewModel
import com.example.viewmodel.WorkspaceBottomTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriberStudioScreen(
    viewModel: TranscriberViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var latestFoldableState by remember { mutableStateOf<FoldableAdaptiveState?>(null) }

    // Android Storage Access Framework Directory Picker (scans folder and all subfolders)
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onDirectoryTreePicked(uri)
        }
    }

    // Android Storage Access Framework Single Media File Picker (MP3, FLAC, WAV, AAC, M4A, OGG, MP4, MKV, AVI, MOV)
    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onSingleMediaFilePicked(uri)
        }
    }

    // Per-export user-chosen .TXT save location launcher
    val exportLogTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.exportLogsToUserLocation(targetUri)
        }
    }

    val triggerExportLogTxt = {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        exportLogTxtLauncher.launch("codex_transcriber_log_$stamp.txt")
    }

    val filteredQueueItems by remember(uiState.queueItems, uiState.activeFilter) {
        derivedStateOf {
            when (uiState.activeFilter) {
                QueueFilter.ALL -> uiState.queueItems
                QueueFilter.AUDIO_LRC -> uiState.queueItems.filter { it.mediaKind == MediaKind.AUDIO }
                QueueFilter.VIDEO_SRT -> uiState.queueItems.filter { it.mediaKind == MediaKind.VIDEO }
                QueueFilter.PENDING -> uiState.queueItems.filter { it.status == FileProcessStatus.PENDING }
                QueueFilter.COMPLETED -> uiState.queueItems.filter { it.status == FileProcessStatus.COMPLETED }
                QueueFilter.SKIPPED -> uiState.queueItems.filter { it.status == FileProcessStatus.SKIPPED }
            }
        }
    }

    val finalizedItems by remember(uiState.queueItems) {
        derivedStateOf {
            uiState.queueItems.filter {
                it.status == FileProcessStatus.COMPLETED ||
                    (it.existingOutputExists && (it.segments.isNotEmpty() || it.generatedTranscriptContent.isNotBlank()))
            }
        }
    }

    val launchSingleFilePicker = {
        singleFilePickerLauncher.launch(
            arrayOf(
                "audio/*",
                "audio/mpeg",
                "audio/mp3",
                "audio/flac",
                "audio/x-flac",
                "audio/wav",
                "audio/x-wav",
                "audio/mp4",
                "audio/aac",
                "audio/ogg",
                "video/*",
                "video/mp4",
                "video/x-matroska",
                "video/x-msvideo",
                "video/quicktime"
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StudioBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioBackground,
                    titleContentColor = TextPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(ElectricAmethystContainer)
                                .border(1.dp, ElectricAmethyst, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Codex Transcriber",
                                tint = ElectricAmethystLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Codex Transcriber",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = latestFoldableState?.let {
                                    "Fold-Aware (${it.posture.shortBadge}) • Local LRC/SRT & Metadata Sync"
                                } ?: "Local Recursive LRC/SRT & Audio→Video Metadata Sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnapdragonCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Foldable Posture Quick Switcher / Indicator Button
                    OutlinedButton(
                        onClick = viewModel::cycleFoldablePostureMode,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("top_bar_foldable_posture_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DevicesFold,
                            contentDescription = "Switch Foldable Posture",
                            tint = SnapdragonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = latestFoldableState?.posture?.shortBadge ?: "Fold",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }

                    val isLogActive = uiState.activeBottomTab == WorkspaceBottomTab.EXECUTION_LOG
                    OutlinedButton(
                        onClick = {
                            viewModel.setBottomTab(
                                if (isLogActive) WorkspaceBottomTab.FILE_QUEUE else WorkspaceBottomTab.EXECUTION_LOG
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("top_bar_log_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "Toggle Log View",
                            tint = ElectricAmethystLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLogActive) "Queue" else "Log",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = viewModel::openSettingsMenu,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings Menu",
                            tint = SnapdragonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Studio Status Bar (matching the desktop application's bottom status bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("studio_status_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Status",
                        tint = ElectricAmethystLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = uiState.statusBannerMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val foldableState = rememberFoldableAdaptiveState(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                postureMode = uiState.foldablePostureMode
            )
            latestFoldableState = foldableState

            // Reusable Right/Bottom Queue & Submenu Content Pane
            val queueAndSubmenuPane: @Composable (Modifier) -> Unit = { paneMod ->
                Column(
                    modifier = paneMod,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionAndProgressStrip(
                        uiState = uiState,
                        onStartOrStopClick = viewModel::onPrimaryActionClicked
                    )
                    QueueFilterHeader(
                        uiState = uiState,
                        onSelectFilter = viewModel::setFilter,
                        onSelectBottomTab = viewModel::setBottomTab
                    )
                    when (uiState.activeBottomTab) {
                        WorkspaceBottomTab.EXECUTION_LOG -> {
                            ExecutionLogPanel(
                                uiState = uiState,
                                fullLogTextProvider = viewModel::getFullLogText,
                                onPasteToLog = viewModel::appendPastedTextToLog,
                                onExportLogTxtClick = triggerExportLogTxt,
                                onClearLogsClick = viewModel::clearLogs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                        WorkspaceBottomTab.FINALIZED_FILES -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                item {
                                    FinalizedFilesSubmenuPanel(
                                        finalizedItems = finalizedItems,
                                        onOpenSyncStudio = viewModel::openWaveformLyricSyncStudio
                                    )
                                }
                            }
                        }
                        WorkspaceBottomTab.FILE_QUEUE -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("file_queue_list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filteredQueueItems, key = { it.id }) { item ->
                                    QueueFileItemRow(
                                        item = item,
                                        onClick = { viewModel.inspectQueueItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (foldableState.posture) {
                // 1. BOOK POSTURE (Vertical Hinge Separation) & 2. UNFOLDED DUAL-PANE (Navigation Rail + Two Panes)
                FoldablePosture.BOOK_POSTURE, FoldablePosture.FLAT_DUAL_PANE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("foldable_dual_pane_container")
                    ) {
                        if (foldableState.showNavigationRail) {
                            StudioNavigationRail(
                                activeTab = uiState.activeBottomTab,
                                queueCount = uiState.queueItems.size,
                                finalizedCount = finalizedItems.size,
                                logCount = uiState.logEntries.size,
                                posture = foldableState.posture,
                                onSelectTab = viewModel::setBottomTab,
                                onOpenSettings = viewModel::openSettingsMenu,
                                onCyclePostureMode = viewModel::cycleFoldablePostureMode
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left Book Page / Left Studio Pane
                            Column(
                                modifier = Modifier
                                    .weight(1.03f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SnapdragonTelemetryBanner(
                                    telemetry = uiState.npuTelemetry,
                                    lastSpeedup = uiState.lastRealtimeSpeedup
                                )
                                InputSourceCard(
                                    uiState = uiState,
                                    onModeChange = viewModel::setInputMode,
                                    onBrowseDirectory = { directoryPickerLauncher.launch(null) },
                                    onBrowseSingleFile = launchSingleFilePicker,
                                    onResetDemoVault = viewModel::resetAndScanLocalStudioTree
                                )
                                SpectrogramVisualizerCard(spectrogram = uiState.spectrogram)
                                CompactSettingsSummaryBar(
                                    uiState = uiState,
                                    onOpenSettingsClick = viewModel::openSettingsMenu
                                )
                            }

                            // Vertical Hinge Gutter in Book Posture so no controls sit in the fold crease
                            if (foldableState.posture == FoldablePosture.BOOK_POSTURE) {
                                FoldableHingeGutterVertical(
                                    hingeWidthDp = foldableState.hingeSpanDp,
                                    isHalfOpened = foldableState.isHalfOpened
                                )
                            }

                            // Right Book Page / Right Studio Pane
                            queueAndSubmenuPane(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                // 3. TABLETOP / FLEX POSTURE (Horizontal Hinge Separation)
                FoldablePosture.TABLETOP_POSTURE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("foldable_tabletop_container"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Half (above horizontal hinge): Hands-Free Spectrogram Visualizer & NPU Telemetry
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.45f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SnapdragonTelemetryBanner(
                                telemetry = uiState.npuTelemetry,
                                lastSpeedup = uiState.lastRealtimeSpeedup
                            )
                            SpectrogramVisualizerCard(spectrogram = uiState.spectrogram)
                            CompactSettingsSummaryBar(
                                uiState = uiState,
                                onOpenSettingsClick = viewModel::openSettingsMenu
                            )
                        }

                        // Horizontal Fold Crease Divider (Tabletop Hinge Avoidance)
                        FoldableHingeCreaseHorizontal(
                            hingeHeightDp = foldableState.hingeSpanDp,
                            postureLabel = "Tabletop Flex Hinge • Top: Spectrogram & NPU | Bottom: Controls & Queue"
                        )

                        // Bottom Half (flat on table below horizontal hinge): Interactive Controls & File Queue
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.55f)
                                .testTag("file_queue_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            item {
                                InputSourceCard(
                                    uiState = uiState,
                                    onModeChange = viewModel::setInputMode,
                                    onBrowseDirectory = { directoryPickerLauncher.launch(null) },
                                    onBrowseSingleFile = launchSingleFilePicker,
                                    onResetDemoVault = viewModel::resetAndScanLocalStudioTree
                                )
                            }
                            item {
                                ActionAndProgressStrip(
                                    uiState = uiState,
                                    onStartOrStopClick = viewModel::onPrimaryActionClicked
                                )
                            }
                            item {
                                QueueFilterHeader(
                                    uiState = uiState,
                                    onSelectFilter = viewModel::setFilter,
                                    onSelectBottomTab = viewModel::setBottomTab
                                )
                            }

                            when (uiState.activeBottomTab) {
                                WorkspaceBottomTab.EXECUTION_LOG -> {
                                    item {
                                        ExecutionLogPanel(
                                            uiState = uiState,
                                            fullLogTextProvider = viewModel::getFullLogText,
                                            onPasteToLog = viewModel::appendPastedTextToLog,
                                            onExportLogTxtClick = triggerExportLogTxt,
                                            onClearLogsClick = viewModel::clearLogs
                                        )
                                    }
                                }
                                WorkspaceBottomTab.FINALIZED_FILES -> {
                                    item {
                                        FinalizedFilesSubmenuPanel(
                                            finalizedItems = finalizedItems,
                                            onOpenSyncStudio = viewModel::openWaveformLyricSyncStudio
                                        )
                                    }
                                }
                                WorkspaceBottomTab.FILE_QUEUE -> {
                                    items(filteredQueueItems, key = { it.id }) { item ->
                                        QueueFileItemRow(
                                            item = item,
                                            onClick = { viewModel.inspectQueueItem(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. COMPACT COVER SCREEN / HANDHELD PORTRAIT (< 600.dp)
                FoldablePosture.COMPACT_COVER -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .testTag("file_queue_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp)
                    ) {
                        item {
                            SnapdragonTelemetryBanner(
                                telemetry = uiState.npuTelemetry,
                                lastSpeedup = uiState.lastRealtimeSpeedup
                            )
                        }
                        item {
                            InputSourceCard(
                                uiState = uiState,
                                onModeChange = viewModel::setInputMode,
                                onBrowseDirectory = { directoryPickerLauncher.launch(null) },
                                onBrowseSingleFile = launchSingleFilePicker,
                                onResetDemoVault = viewModel::resetAndScanLocalStudioTree
                            )
                        }
                        item {
                            SpectrogramVisualizerCard(spectrogram = uiState.spectrogram)
                        }
                        item {
                            CompactSettingsSummaryBar(
                                uiState = uiState,
                                onOpenSettingsClick = viewModel::openSettingsMenu
                            )
                        }
                        item {
                            ActionAndProgressStrip(
                                uiState = uiState,
                                onStartOrStopClick = viewModel::onPrimaryActionClicked
                            )
                        }
                        item {
                            QueueFilterHeader(
                                uiState = uiState,
                                onSelectFilter = viewModel::setFilter,
                                onSelectBottomTab = viewModel::setBottomTab
                            )
                        }

                        when (uiState.activeBottomTab) {
                            WorkspaceBottomTab.EXECUTION_LOG -> {
                                item {
                                    ExecutionLogPanel(
                                        uiState = uiState,
                                        fullLogTextProvider = viewModel::getFullLogText,
                                        onPasteToLog = viewModel::appendPastedTextToLog,
                                        onExportLogTxtClick = triggerExportLogTxt,
                                        onClearLogsClick = viewModel::clearLogs
                                    )
                                }
                            }
                            WorkspaceBottomTab.FINALIZED_FILES -> {
                                item {
                                    FinalizedFilesSubmenuPanel(
                                        finalizedItems = finalizedItems,
                                        onOpenSyncStudio = viewModel::openWaveformLyricSyncStudio
                                    )
                                }
                            }
                            WorkspaceBottomTab.FILE_QUEUE -> {
                                if (filteredQueueItems.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(StudioSurface)
                                                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No matching media files in this filter.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredQueueItems, key = { it.id }) { item ->
                                        QueueFileItemRow(
                                            item = item,
                                            onClick = { viewModel.inspectQueueItem(item) }
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

    // Settings Menu Dialog (Model, Quantization, NPU Delegate, Auto Beam, Beam Size, VAD, Overwrite, Metadata Sync, Foldable Posture)
    if (uiState.showSettingsDialog) {
        SettingsMenuDialog(
            uiState = uiState,
            onSelectModel = viewModel::setModelProfile,
            onSelectQuantization = viewModel::setQuantization,
            onSelectAccelerator = viewModel::setHardwareAccelerator,
            onSelectComputePrecision = viewModel::setComputePrecision,
            onAutoBeamChange = viewModel::setAutoBeamSize,
            onBeamSizeChange = viewModel::setBeamSize,
            onVadFilterChange = viewModel::setVadFilter,
            onOverwriteChange = viewModel::setOverwriteExisting,
            onAutoMetadataSyncChange = viewModel::setAutoSyncMetadata,
            onSelectFoldablePostureMode = viewModel::setFoldablePostureMode,
            onDismiss = viewModel::closeSettingsMenu
        )
    }

    // Two-Stage Stop Confirmation Dialog
    if (uiState.showStopConfirmDialog) {
        StopProcessingConfirmDialog(
            stopRequestedLevel = uiState.stopRequestedLevel,
            onStopAfterCurrentFile = viewModel::confirmStopAfterCurrentFile,
            onForceImmediateStop = viewModel::confirmForceImmediateStop,
            onDismiss = viewModel::dismissStopDialog
        )
    }

    // In-Place Output & Metadata Sync Inspector Dialog
    uiState.inspectedItem?.let { inspected ->
        QueueItemInspectorDialog(
            item = inspected,
            onLoadSpectrogram = viewModel::previewItemSpectrogram,
            onOpenSyncStudio = viewModel::openWaveformLyricSyncStudio,
            onDismiss = { viewModel.inspectQueueItem(null) }
        )
    }

    // Finalized File Wavelength vs. Timestamped Lyrics Sync & Playback Verification Studio Dialog (Fold-Aware)
    uiState.syncEditorItem?.let { syncItem ->
        WaveformLyricSyncStudioDialog(
            item = syncItem,
            isComputingWaveform = uiState.isComputingWaveformForSync,
            foldableState = latestFoldableState,
            onSaveUpdatedSegments = viewModel::saveEditedSegmentsInPlace,
            onDismiss = viewModel::closeWaveformLyricSyncStudio
        )
    }
}
