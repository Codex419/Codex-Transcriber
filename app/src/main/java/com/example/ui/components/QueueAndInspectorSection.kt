package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FileProcessStatus
import com.example.model.InputScanMode
import com.example.model.MediaKind
import com.example.model.MediaQueueItem
import com.example.model.QueueFilter
import com.example.model.TranscriptFormatter
import com.example.ui.theme.ElectricAmethyst
import com.example.ui.theme.ElectricAmethystContainer
import com.example.ui.theme.ElectricAmethystLight
import com.example.ui.theme.MagmaCoral
import com.example.ui.theme.SnapdragonCyan
import com.example.ui.theme.StatusCompletedGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusProcessingPurple
import com.example.ui.theme.StatusSkippedAmber
import com.example.ui.theme.StopButtonRed
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TranscriberUiState
import com.example.viewmodel.WorkspaceBottomTab
import java.util.Locale

@Composable
fun ActionAndProgressStrip(
    uiState: TranscriberUiState,
    onStartOrStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // File Progress Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.processingActive && uiState.currentFileElapsedSec > 0) {
                                "File Progress (Elapsed: ${TranscriptFormatter.formatEta(uiState.currentFileElapsedSec)})"
                            } else {
                                "File Progress:"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%%", uiState.currentFileProgress),
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricAmethystLight
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (uiState.currentFileProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("file_progress_bar"),
                        color = ElectricAmethyst,
                        trackColor = StudioSurfaceVariant
                    )

                    // Batch Progress & Rolling ETA Row (Visible in Batch Directory Mode)
                    if (uiState.inputMode == InputScanMode.BATCH_DIRECTORY) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val countText = if (uiState.totalBatchFiles > 0) {
                                "(${uiState.processedBatchFiles}/${uiState.totalBatchFiles})"
                            } else ""
                            val etaText = uiState.batchEtaSeconds?.let {
                                " • ETA: ${TranscriptFormatter.formatEta(it)}"
                            } ?: ""
                            Text(
                                text = "Batch Progress $countText$etaText",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f%%", uiState.batchProgress),
                                style = MaterialTheme.typography.labelMedium,
                                color = SnapdragonCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (uiState.batchProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("batch_progress_bar"),
                            color = SnapdragonCyan,
                            trackColor = StudioSurfaceVariant
                        )
                    }
                }

                // Start / Stop Button
                Button(
                    onClick = onStartOrStopClick,
                    enabled = uiState.queueItems.isNotEmpty() && !uiState.isDownloadingModel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.processingActive) StopButtonRed else ElectricAmethyst
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("start_stop_button")
                ) {
                    Icon(
                        imageVector = if (uiState.processingActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.processingActive) "Stop Processing" else "Start Transcription",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !uiState.processingActive -> "Start Transcription"
                            uiState.stopRequestedLevel == 0 -> "Stop Processing"
                            else -> "Force Stop"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun QueueFilterHeader(
    uiState: TranscriberUiState,
    onSelectFilter: (QueueFilter) -> Unit,
    onSelectBottomTab: (WorkspaceBottomTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Segmented Tab Switcher: File Queue vs Finalized Files Submenu vs Log View
        val finalizedCount = uiState.queueItems.count {
            it.status == FileProcessStatus.COMPLETED ||
                (it.existingOutputExists && (it.segments.isNotEmpty() || it.generatedTranscriptContent.isNotBlank()))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isQueueTab = uiState.activeBottomTab == WorkspaceBottomTab.FILE_QUEUE
            val isFinalizedTab = uiState.activeBottomTab == WorkspaceBottomTab.FINALIZED_FILES
            val isLogTab = uiState.activeBottomTab == WorkspaceBottomTab.EXECUTION_LOG

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isQueueTab) ElectricAmethystContainer else StudioSurface)
                    .border(
                        1.dp,
                        if (isQueueTab) ElectricAmethyst else StudioBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectBottomTab(WorkspaceBottomTab.FILE_QUEUE) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("tab_file_queue")
            ) {
                Text(
                    text = "📊 File Queue (${uiState.queueItems.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isQueueTab) ElectricAmethystLight else TextSecondary,
                    fontWeight = if (isQueueTab) FontWeight.Bold else FontWeight.Normal
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFinalizedTab) Color(0xFF132D24) else StudioSurface)
                    .border(
                        1.dp,
                        if (isFinalizedTab) StatusCompletedGreen else StudioBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectBottomTab(WorkspaceBottomTab.FINALIZED_FILES) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("tab_finalized_files")
            ) {
                Text(
                    text = "✅ Finalized Files ($finalizedCount)",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFinalizedTab) StatusCompletedGreen else TextSecondary,
                    fontWeight = if (isFinalizedTab) FontWeight.Bold else FontWeight.Normal
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isLogTab) ElectricAmethystContainer else StudioSurface)
                    .border(
                        1.dp,
                        if (isLogTab) ElectricAmethyst else StudioBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectBottomTab(WorkspaceBottomTab.EXECUTION_LOG) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("tab_execution_log")
            ) {
                Text(
                    text = "📜 Log View (${uiState.logEntries.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isLogTab) ElectricAmethystLight else TextSecondary,
                    fontWeight = if (isLogTab) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        if (uiState.activeBottomTab == WorkspaceBottomTab.FILE_QUEUE) {
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QueueFilter.entries.forEach { filter ->
                    val selected = uiState.activeFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricAmethystContainer,
                            selectedLabelColor = ElectricAmethystLight,
                            containerColor = StudioSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase(Locale.ROOT)}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutionLogPanel(
    uiState: TranscriberUiState,
    fullLogTextProvider: () -> String,
    onPasteToLog: (String) -> Unit,
    onExportLogTxtClick: () -> Unit,
    onClearLogsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var customNoteInput by remember { mutableStateOf("") }
    var copyFeedback by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .testTag("execution_log_panel"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = "Execution Log View",
                        tint = ElectricAmethystLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Execution & In-Place Output Log",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Select text to copy, paste notes, or export as .txt to any folder per export",
                            style = MaterialTheme.typography.labelSmall,
                            color = SnapdragonCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row: Copy All, Paste from Clipboard, Export .TXT to User Location, Clear
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val fullText = fullLogTextProvider()
                        clipboardManager.setText(AnnotatedString(fullText))
                        copyFeedback = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethyst),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("copy_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy Log",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (copyFeedback) "Copied!" else "Copy Log",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clipText = clipboardManager.getText()?.text.orEmpty()
                        if (clipText.isNotBlank()) {
                            onPasteToLog(clipText)
                        } else if (customNoteInput.isNotBlank()) {
                            onPasteToLog(customNoteInput)
                            customNoteInput = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("paste_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = "Paste into Log",
                        tint = ElectricAmethystLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Paste Clipboard",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onExportLogTxtClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("export_log_txt_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SaveAlt,
                        contentDescription = "Export .TXT to User Location",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Export .TXT to Location...",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = {
                        copyFeedback = false
                        onClearLogsClick()
                    },
                    modifier = Modifier.testTag("clear_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear Log",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inline Paste / Append Note Box so user can also paste or type directly via long-press context menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customNoteInput,
                    onValueChange = { customNoteInput = it },
                    placeholder = {
                        Text(
                            text = "Paste or type note/command to append to log before .txt export...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricAmethyst,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = StudioSurfaceVariant,
                        unfocusedContainerColor = StudioSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("log_paste_input_field")
                )
                OutlinedButton(
                    onClick = {
                        if (customNoteInput.isNotBlank()) {
                            onPasteToLog(customNoteInput)
                            customNoteInput = ""
                        }
                    },
                    enabled = customNoteInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("append_note_button")
                ) {
                    Text(
                        text = "Add to Log",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selectable Monospace Log Console
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBackground)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("selectable_log_console")
            ) {
                SelectionContainer {
                    val logContent = if (uiState.logEntries.isEmpty()) {
                        "No log entries yet."
                    } else {
                        uiState.logEntries.joinToString("\n")
                    }
                    Text(
                        text = logContent,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun QueueFileItemRow(
    item: MediaQueueItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (item.status) {
        FileProcessStatus.COMPLETED -> StatusCompletedGreen
        FileProcessStatus.SKIPPED -> StatusSkippedAmber
        FileProcessStatus.ERROR -> StatusErrorRed
        FileProcessStatus.PROCESSING -> StatusProcessingPurple
        FileProcessStatus.PENDING -> TextSecondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (item.status == FileProcessStatus.PROCESSING) ElectricAmethyst else StudioBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .testTag("queue_item_${item.baseName}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.status == FileProcessStatus.PROCESSING) {
                Color(0xFF231C35)
            } else {
                StudioSurface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon + Filename + Subfolder + Metadata Sync Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (item.mediaKind == MediaKind.AUDIO) {
                                ElectricAmethystContainer
                            } else {
                                Color(0xFF172D3D)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaKind == MediaKind.AUDIO) {
                            Icons.Filled.AudioFile
                        } else {
                            Icons.Filled.VideoFile
                        },
                        contentDescription = item.mediaKind.label,
                        tint = if (item.mediaKind == MediaKind.AUDIO) {
                            ElectricAmethystLight
                        } else {
                            SnapdragonCyan
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.filename,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${item.extension.uppercase(Locale.ROOT)} → ${item.mediaKind.targetExtension.uppercase(Locale.ROOT)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.mediaKind == MediaKind.AUDIO) ElectricAmethystLight else SnapdragonCyan,
                            modifier = Modifier
                                .background(StudioSurfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Subfolder & Metadata info line
                    val metaTitle = item.metadata.title.takeIf { it.isNotBlank() } ?: item.baseName
                    val metaArtist = item.metadata.artist.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                    Text(
                        text = "📁 ${item.subfolderName}  |  🎵 $metaTitle$metaArtist",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // In-Place Output & Audio <-> Video Metadata Sync Pill
                    val syncRelationText = when {
                        !item.savedOutputPath.isNullOrBlank() ->
                            "✅ Saved In-Place: ${item.savedOutputPath}"
                        item.mediaKind == MediaKind.VIDEO && item.metadata.syncedFromAudioSource != null ->
                            "🔗 Metadata synced from ${item.metadata.syncedFromAudioSource} → ${item.expectedOutputFilename}"
                        item.mediaKind == MediaKind.AUDIO && item.metadata.syncedToVideoTarget != null ->
                            "🔗 ID3/FLAC Metadata synced to ${item.metadata.syncedToVideoTarget} & ${item.expectedOutputFilename}"
                        else ->
                            "💾 In-place target: ${item.subfolderName.trimEnd('/')}/${item.expectedOutputFilename}"
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = syncRelationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            !item.savedOutputPath.isNullOrBlank() -> StatusCompletedGreen
                            item.metadata.syncedFromAudioSource != null || item.metadata.syncedToVideoTarget != null -> SnapdragonCyan
                            else -> TextMuted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Status Symbol + Status Label + Progress %
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.status.symbol} ${item.status.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "%.1f%%", item.progressPercent),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.End
                )
                Text(
                    text = item.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StopProcessingConfirmDialog(
    stopRequestedLevel: Int,
    onStopAfterCurrentFile: () -> Unit,
    onForceImmediateStop: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (stopRequestedLevel == 0) "Stop Transcription?" else "Force Immediate Stop?",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = if (stopRequestedLevel == 0) {
                    "Choose whether to finish saving the current file's .lrc / .srt output before stopping, or force an immediate halt."
                } else {
                    "Stop after current file is already queued. Force stop immediately? The active file's transcript will be discarded."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            if (stopRequestedLevel == 0) {
                Button(
                    onClick = onStopAfterCurrentFile,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethyst)
                ) {
                    Text("Stop After Current File")
                }
            } else {
                Button(
                    onClick = onForceImmediateStop,
                    colors = ButtonDefaults.buttonColors(containerColor = StopButtonRed)
                ) {
                    Text("Force Immediate Stop")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stopRequestedLevel == 0) {
                    TextButton(onClick = onForceImmediateStop) {
                        Text("Force Immediate", color = StatusErrorRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun QueueItemInspectorDialog(
    item: MediaQueueItem,
    onLoadSpectrogram: (MediaQueueItem) -> Unit,
    onOpenSyncStudio: (MediaQueueItem) -> Unit = {},
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = "Output & Metadata Inspector",
                            tint = ElectricAmethyst,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.filename,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "In-Place Output: ${item.subfolderName.trimEnd('/')}/${item.expectedOutputFilename}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnapdragonCyan
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Inspector",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = StudioBorder
                )

                // Synced Metadata Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = "Synced Metadata",
                                    tint = SnapdragonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Synchronized Audio ↔ Video Metadata",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SnapdragonCyan
                                )
                            }
                            Text(
                                text = item.mediaKind.formatBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricAmethystLight
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Title: ${item.metadata.title.ifBlank { "N/A" }}   |   Artist: ${item.metadata.artist.ifBlank { "N/A" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Album: ${item.metadata.album.ifBlank { "N/A" }}   |   Genre: ${item.metadata.genre.ifBlank { "N/A" }} (${item.metadata.year})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (item.metadata.syncedFromAudioSource != null) {
                            Text(
                                text = "✔ Synced into this video from audio source: ${item.metadata.syncedFromAudioSource}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCompletedGreen
                            )
                        } else if (item.metadata.syncedToVideoTarget != null) {
                            Text(
                                text = "✔ Synced from this audio track to video target: ${item.metadata.syncedToVideoTarget}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCompletedGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Timed File Content (${item.expectedOutputFilename}):",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenSyncStudio(item)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethyst),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("inspector_open_sync_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Wavelength vs Lyrics Sync",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Wavelength & Sync",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onLoadSpectrogram(item)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Plot Mel Spectrogram",
                                tint = ElectricAmethystLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Spectrogram",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp, max = 320.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioBackground)
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val previewText = item.generatedTranscriptContent.ifBlank {
                        "No ${item.expectedOutputFilename} generated yet. Tap 'Start Transcription' to run the Snapdragon NPU pipeline and save ${item.expectedOutputFilename} in place."
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
