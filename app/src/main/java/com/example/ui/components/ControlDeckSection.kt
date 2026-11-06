package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.SnapdragonNpuTranscriptionEngine
import com.example.model.ComputePrecision
import com.example.model.HardwareAccelerator
import com.example.model.InputScanMode
import com.example.model.QuantizationSuffix
import com.example.model.WhisperModelProfile
import com.example.ui.adaptive.FoldablePostureMode
import com.example.ui.theme.ElectricAmethyst
import com.example.ui.theme.ElectricAmethystContainer
import com.example.ui.theme.ElectricAmethystLight
import com.example.ui.theme.MagmaCoral
import com.example.ui.theme.SnapdragonCyan
import com.example.ui.theme.StatusCompletedGreen
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TranscriberUiState
import java.util.Locale

@Composable
fun SnapdragonTelemetryBanner(
    telemetry: SnapdragonNpuTranscriptionEngine.NpuHardwareTelemetry,
    lastSpeedup: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1F1638),
                        Color(0xFF182232),
                        Color(0xFF1C1D24)
                    )
                )
            )
            .border(1.dp, ElectricAmethyst.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("npu_telemetry_banner")
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricAmethystContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = "Snapdragon Hexagon NPU",
                        tint = ElectricAmethystLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = telemetry.socName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${telemetry.npuArch} • ${telemetry.qnnHtpVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SnapdragonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = telemetry.activeDelegateBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusCompletedGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF132E21), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (lastSpeedup > 0.0) {
                        String.format("%.1fx RT • %s", lastSpeedup, telemetry.topsRating)
                    } else {
                        "${telemetry.topsRating} • Local On-Device"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputSourceCard(
    uiState: TranscriberUiState,
    onModeChange: (InputScanMode) -> Unit,
    onBrowseDirectory: () -> Unit,
    onBrowseSingleFile: () -> Unit,
    onResetDemoVault: () -> Unit,
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "Input Source",
                        tint = ElectricAmethyst,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Input Source (Local & Subfolders)",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricAmethyst
                    )
                }
                Text(
                    text = "IN-PLACE .LRC / .SRT SAVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusCompletedGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mode Radio Buttons matching desktop Single File vs Batch Directory (incl. Subfolders)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !uiState.processingActive) {
                            onModeChange(InputScanMode.SINGLE_FILE)
                        }
                        .padding(end = 12.dp)
                        .testTag("mode_single_radio")
                ) {
                    RadioButton(
                        selected = uiState.inputMode == InputScanMode.SINGLE_FILE,
                        onClick = { onModeChange(InputScanMode.SINGLE_FILE) },
                        enabled = !uiState.processingActive,
                        colors = RadioButtonDefaults.colors(selectedColor = ElectricAmethyst)
                    )
                    Text(
                        text = "Single File",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !uiState.processingActive) {
                            onModeChange(InputScanMode.BATCH_DIRECTORY)
                        }
                        .padding(end = 8.dp)
                        .testTag("mode_batch_radio")
                ) {
                    RadioButton(
                        selected = uiState.inputMode == InputScanMode.BATCH_DIRECTORY,
                        onClick = { onModeChange(InputScanMode.BATCH_DIRECTORY) },
                        enabled = !uiState.processingActive,
                        colors = RadioButtonDefaults.colors(selectedColor = ElectricAmethyst)
                    )
                    Text(
                        text = "Batch Directory (incl. Subfolders)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }

            // Current Selected Path Well
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSurfaceVariant)
                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("input_path_display")
            ) {
                Column {
                    Text(
                        text = if (uiState.inputMode == InputScanMode.SINGLE_FILE) "Selected File (In-Place Target):" else "Scanned Root Directory (In-Place Target):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = uiState.inputPathDisplay.ifBlank { "Tap Browse to select a local folder or file..." },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Supported Formats Pill Summary
            Text(
                text = "Audio (.mp3, .flac, .wav, .aac, .m4a, .ogg) → .lrc   |   Video (.mp4, .mkv, .avi, .mov, .wmv, .flv) → .srt",
                style = MaterialTheme.typography.labelSmall,
                color = SnapdragonCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Browse & Local Studio Tree Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState.inputMode == InputScanMode.SINGLE_FILE) {
                            onBrowseSingleFile()
                        } else {
                            onBrowseDirectory()
                        }
                    },
                    enabled = !uiState.processingActive,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethyst),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("browse_input_button")
                ) {
                    Icon(
                        imageVector = if (uiState.inputMode == InputScanMode.SINGLE_FILE) {
                            Icons.Filled.AudioFile
                        } else {
                            Icons.Filled.FolderOpen
                        },
                        contentDescription = "Browse Input",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.inputMode == InputScanMode.SINGLE_FILE) {
                            "Browse File..."
                        } else {
                            "Scan Directory..."
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onResetDemoVault,
                    enabled = !uiState.processingActive,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("reload_studio_tree_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = "Reload Local Studio Tree",
                        tint = ElectricAmethystLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scan Local Studio Vault (MP3/FLAC/WAV/MP4)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Compact 1-line summary bar on the main workspace that displays the active Whisper model,
 * Auto Beam / Manual Beam status, NPU precision, and opens the dedicated Settings Menu.
 */
@Composable
fun CompactSettingsSummaryBar(
    uiState: TranscriberUiState,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenSettingsClick)
            .testTag("compact_settings_summary_bar"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Active Settings Summary",
                    tint = ElectricAmethystLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    val beamBadge = if (uiState.autoBeamSize) {
                        "Auto Beam (Dynamic)"
                    } else {
                        "Beam=${uiState.beamSize}"
                    }
                    Text(
                        text = "${uiState.modelProfile.displayName} • $beamBadge • ${uiState.computePrecision.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (uiState.isDownloadingModel) {
                            "⬇️ Auto-Downloading Model (${String.format(Locale.US, "%.0f%%", uiState.modelDownloadProgress)})..."
                        } else {
                            "🛡️ Zero-Hallucination Guard Active • VAD=${if (uiState.vadFilter) "ON" else "OFF"} • Sync=${if (uiState.autoSyncAudioMetadataToVideo) "ON" else "OFF"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isDownloadingModel) ElectricAmethystLight else SnapdragonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onOpenSettingsClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("open_settings_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Open Settings Menu",
                    tint = ElectricAmethystLight,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Dedicated Settings Menu modal containing all Whisper Model, Quantization, NPU Delegate,
 * Compute Precision, Auto Beam / Manual Beam Size, VAD Filter, Overwrite, and Metadata Sync settings.
 */
@Composable
fun SettingsMenuDialog(
    uiState: TranscriberUiState,
    onSelectModel: (WhisperModelProfile) -> Unit,
    onSelectQuantization: (QuantizationSuffix) -> Unit,
    onSelectAccelerator: (HardwareAccelerator) -> Unit,
    onSelectComputePrecision: (ComputePrecision) -> Unit,
    onAutoBeamChange: (Boolean) -> Unit,
    onBeamSizeChange: (Int) -> Unit,
    onVadFilterChange: (Boolean) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onAutoMetadataSyncChange: (Boolean) -> Unit,
    onSelectFoldablePostureMode: (FoldablePostureMode) -> Unit = {},
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
                .heightIn(max = 680.dp)
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                .testTag("settings_menu_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings Menu",
                            tint = ElectricAmethystLight,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Transcriber & NPU Settings",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Whisper Model, Auto Beam, Snapdragon NPU & Metadata Sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnapdragonCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_settings_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Settings Menu",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = StudioBorder
                )

                // Row 1: Model Size & Quantization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioDropdownSelector(
                        label = "Whisper Model (Auto-Downloads)",
                        selectedText = uiState.modelProfile.displayName,
                        enabled = !uiState.processingActive && !uiState.isDownloadingModel,
                        options = WhisperModelProfile.entries,
                        optionLabel = { "${it.displayName} (${it.parameterCount})" },
                        onOptionSelected = onSelectModel,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("model_profile_selector")
                    )

                    StudioDropdownSelector(
                        label = "Quantization",
                        selectedText = uiState.quantization.displayLabel,
                        enabled = !uiState.processingActive && !uiState.isDownloadingModel,
                        options = QuantizationSuffix.entries,
                        optionLabel = { it.displayLabel },
                        onOptionSelected = onSelectQuantization,
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("quantization_selector")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-Download & Local Model Cache Status Strip
                val fullModelId = if (uiState.quantization.suffix.isEmpty()) {
                    uiState.modelProfile.id
                } else {
                    "${uiState.modelProfile.id}-${uiState.quantization.suffix}"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioSurfaceVariant)
                        .border(
                            1.dp,
                            if (uiState.isDownloadingModel) ElectricAmethyst else StudioBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("model_cache_status_strip")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.isDownloadingModel) {
                                    Icons.Filled.CloudDownload
                                } else {
                                    Icons.Filled.CheckCircle
                                },
                                contentDescription = "Model Cache Status",
                                tint = if (uiState.isDownloadingModel) {
                                    ElectricAmethystLight
                                } else {
                                    StatusCompletedGreen
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isDownloadingModel) {
                                    "Auto-Downloading Model '$fullModelId' (${String.format(Locale.US, "%.0f%%", uiState.modelDownloadProgress)})..."
                                } else if (uiState.isCurrentModelCached) {
                                    "Model Ready (Auto-Downloaded): Audio Models/$fullModelId"
                                } else {
                                    "Model Queued for Auto-Download: Audio Models/$fullModelId"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isDownloadingModel) ElectricAmethystLight else StatusCompletedGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (uiState.isDownloadingModel) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (uiState.modelDownloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ElectricAmethyst,
                                trackColor = StudioSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Hardware Delegate & Compute Precision
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioDropdownSelector(
                        label = "Device Delegate",
                        selectedText = uiState.accelerator.shortName,
                        enabled = !uiState.processingActive,
                        options = HardwareAccelerator.entries,
                        optionLabel = { it.shortName },
                        onOptionSelected = onSelectAccelerator,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("accelerator_selector")
                    )

                    StudioDropdownSelector(
                        label = "Compute Type",
                        selectedText = uiState.computePrecision.id,
                        enabled = !uiState.processingActive,
                        options = uiState.availableComputeTypes,
                        optionLabel = { it.label },
                        onOptionSelected = onSelectComputePrecision,
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("compute_type_selector")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = uiState.modelProfile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = StudioBorder.copy(alpha = 0.6f)
                )

                // Row 3: Auto Beam Option + Manual Beam Size Stepper
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !uiState.processingActive) {
                                        onAutoBeamChange(!uiState.autoBeamSize)
                                    }
                                    .testTag("auto_beam_checkbox")
                            ) {
                                Checkbox(
                                    checked = uiState.autoBeamSize,
                                    onCheckedChange = onAutoBeamChange,
                                    enabled = !uiState.processingActive,
                                    colors = CheckboxDefaults.colors(checkedColor = ElectricAmethyst)
                                )
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "Auto Beam",
                                            tint = SnapdragonCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Auto Beam (Dynamic NPU Optimal)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = if (uiState.autoBeamSize) {
                                            uiState.lastAutoBeamSummary
                                        } else {
                                            "Manual beam width active below"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SnapdragonCyan
                                    )
                                }
                            }

                            // Manual Beam Size Stepper (adjusting automatically switches to manual or sets fallback)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioSurface)
                                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            ) {
                                IconButton(
                                    onClick = { onBeamSizeChange(uiState.beamSize - 1) },
                                    enabled = !uiState.processingActive && uiState.beamSize > 1,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("beam_decrease_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "Decrease Beam Size",
                                        tint = if (uiState.autoBeamSize) TextMuted else TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (uiState.autoBeamSize) "AUTO" else uiState.beamSize.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (uiState.autoBeamSize) SnapdragonCyan else ElectricAmethystLight,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { onBeamSizeChange(uiState.beamSize + 1) },
                                    enabled = !uiState.processingActive && uiState.beamSize < 20,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("beam_increase_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Increase Beam Size",
                                        tint = if (uiState.autoBeamSize) TextMuted else TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 4: VAD Filter & Overwrite Existing & Automatic Audio->Video Metadata Sync
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !uiState.processingActive) {
                                onVadFilterChange(!uiState.vadFilter)
                            }
                            .testTag("vad_filter_checkbox")
                    ) {
                        Checkbox(
                            checked = uiState.vadFilter,
                            onCheckedChange = onVadFilterChange,
                            enabled = !uiState.processingActive,
                            colors = CheckboxDefaults.colors(checkedColor = ElectricAmethyst)
                        )
                        Column {
                            Text(
                                text = "VAD Silence & Anti-Hallucination Filter",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Trims non-vocal silence & blocks imaginary lyric hallucinations",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !uiState.processingActive) {
                                onOverwriteChange(!uiState.overwriteExisting)
                            }
                            .testTag("overwrite_output_checkbox")
                    ) {
                        Checkbox(
                            checked = uiState.overwriteExisting,
                            onCheckedChange = onOverwriteChange,
                            enabled = !uiState.processingActive,
                            colors = CheckboxDefaults.colors(checkedColor = ElectricAmethyst)
                        )
                        Column {
                            Text(
                                text = "Overwrite Existing .LRC / .SRT Files",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Unchecked = create in-place only if not already present",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !uiState.processingActive) {
                                onAutoMetadataSyncChange(!uiState.autoSyncAudioMetadataToVideo)
                            }
                            .testTag("metadata_sync_checkbox")
                    ) {
                        Checkbox(
                            checked = uiState.autoSyncAudioMetadataToVideo,
                            onCheckedChange = onAutoMetadataSyncChange,
                            enabled = !uiState.processingActive,
                            colors = CheckboxDefaults.colors(checkedColor = ElectricAmethyst)
                        )
                        Column {
                            Text(
                                text = "Sync Audio → Video Metadata",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Extracts ID3v2/FLAC tags & syncs metadata to companion video files",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnapdragonCyan
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = StudioBorder.copy(alpha = 0.6f)
                )

                // Row 5: Foldable Phone Posture & Hinge Layout (Jetpack WindowManager FoldingFeature)
                StudioDropdownSelector(
                    label = "Foldable Phone Posture & Hinge Layout (WindowManager)",
                    selectedText = uiState.foldablePostureMode.label,
                    enabled = true,
                    options = FoldablePostureMode.entries,
                    optionLabel = { "${it.label} — ${it.subtitle}" },
                    onOptionSelected = onSelectFoldablePostureMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("foldable_posture_mode_selector")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethyst),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("done_settings_button")
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun <T> StudioDropdownSelector(
    label: String,
    selectedText: String,
    enabled: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSurfaceVariant)
                    .border(
                        1.dp,
                        if (expanded) ElectricAmethyst else StudioBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = label,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(StudioSurfaceElevated)
            ) {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        },
                        onClick = {
                            onOptionSelected(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
