package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FileProcessStatus
import com.example.model.MediaKind
import com.example.model.MediaQueueItem
import com.example.model.TranscriptFormatter
import com.example.model.TranscriptSegment
import com.example.ui.adaptive.FoldableAdaptiveState
import com.example.ui.adaptive.FoldableHingeCreaseHorizontal
import com.example.ui.adaptive.FoldableHingeGutterVertical
import com.example.ui.adaptive.FoldablePosture
import com.example.ui.theme.ElectricAmethyst
import com.example.ui.theme.ElectricAmethystContainer
import com.example.ui.theme.ElectricAmethystLight
import com.example.ui.theme.MagmaCoral
import com.example.ui.theme.SnapdragonCyan
import com.example.ui.theme.StatusCompletedGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Submenu panel listing all finalized files (files with completed or existing in-place `.lrc` / `.srt` outputs).
 * Tapping on any file name opens the Waveform vs. Timestamped Lyrics Sync Studio with playback verification & editing.
 */
@Composable
fun FinalizedFilesSubmenuPanel(
    finalizedItems: List<MediaQueueItem>,
    onOpenSyncStudio: (MediaQueueItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .testTag("finalized_files_submenu"),
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
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Finalized Files",
                        tint = StatusCompletedGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Finalized Files • Wavelength & Lyric Sync",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tap any file name to compare audio wavelength vs. timestamped lyrics, play back & edit sync",
                            style = MaterialTheme.typography.labelSmall,
                            color = SnapdragonCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (finalizedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioSurfaceVariant)
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = "No Finalized Files Yet",
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No finalized .LRC or .SRT files yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap 'Start Transcription' to generate timed files in place, then tap any finalized file name here to inspect its wavelength and verify/edit playback sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    finalizedItems.forEach { item ->
                        FinalizedFileItemCard(
                            item = item,
                            onClick = { onOpenSyncStudio(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalizedFileItemCard(
    item: MediaQueueItem,
    onClick: () -> Unit
) {
    val lineCount = if (item.segments.isNotEmpty()) {
        item.segments.size
    } else {
        TranscriptFormatter.parseTranscriptToSegments(
            item.generatedTranscriptContent,
            item.mediaKind,
            item.durationSec
        ).size
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricAmethyst.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("finalized_item_${item.baseName}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricAmethystContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.mediaKind == MediaKind.AUDIO) {
                                Icons.Filled.AudioFile
                            } else {
                                Icons.Filled.VideoFile
                            },
                            contentDescription = item.mediaKind.label,
                            tint = ElectricAmethystLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Tappable File Name with underline cue
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.filename,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricAmethystLight,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .testTag("finalized_filename_${item.baseName}")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.expectedOutputFilename,
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCompletedGreen,
                                modifier = Modifier
                                    .background(Color(0xFF132E21), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🎵 ${item.metadata.title.ifBlank { item.baseName }} • $lineCount timestamped lines • ${TranscriptFormatter.formatEta(item.durationSec)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.savedOutputPath.isNullOrBlank()) {
                            Text(
                                text = "💾 ${item.savedOutputPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("verify_sync_btn_${item.baseName}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Verify / Edit Sync",
                        tint = SnapdragonCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Verify / Edit Sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Interactive Waveform (Wavelength) vs. Timestamped Lyrics Comparison & Playback Sync Studio Dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaveformLyricSyncStudioDialog(
    item: MediaQueueItem,
    isComputingWaveform: Boolean,
    foldableState: FoldableAdaptiveState? = null,
    onSaveUpdatedSegments: (String, List<TranscriptSegment>) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val totalDurationSec = max(item.durationSec, 10.0)

    // Editable segments state initialized from the finalized item
    var editableSegments by remember(item.id, item.segments) {
        mutableStateOf(
            if (item.segments.isNotEmpty()) {
                item.segments
            } else {
                TranscriptFormatter.parseTranscriptToSegments(
                    item.generatedTranscriptContent,
                    item.mediaKind,
                    totalDurationSec
                )
            }
        )
    }

    var selectedSegmentIndex by remember(item.id) { mutableIntStateOf(0) }
    var playheadSec by remember(item.id) { mutableDoubleStateOf(0.0) }
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var loopActiveSegment by remember(item.id) { mutableStateOf(false) }
    var zoomFactor by remember(item.id) { mutableFloatStateOf(1f) }
    var hasUnsavedChanges by remember(item.id) { mutableStateOf(false) }
    var savedFeedbackBanner by remember(item.id) { mutableStateOf(false) }

    // Real Android MediaPlayer (with automatic fallback timer if audio hardware/codec is unavailable)
    var mediaPlayerRef by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var mediaPlayerReady by remember(item.id) { mutableStateOf(false) }

    DisposableEffect(item.id, item.localFilePath, item.documentUriString) {
        val localPath = item.localFilePath
        val isSyntheticVaultNonWav = !localPath.isNullOrBlank() &&
            localPath.contains("Codex_Local_Media_Vault") &&
            !item.extension.equals("wav", ignoreCase = true)

        val player = MediaPlayer()
        val configured = if (isSyntheticVaultNonWav) {
            false
        } else {
            runCatching {
                when {
                    !localPath.isNullOrBlank() && File(localPath).exists() && File(localPath).length() > 16_384L -> {
                        player.setDataSource(localPath)
                        player.prepare()
                        true
                    }
                    !item.documentUriString.isNullOrBlank() -> {
                        player.setDataSource(context, Uri.parse(item.documentUriString))
                        player.prepare()
                        true
                    }
                    else -> false
                }
            }.getOrDefault(false)
        }

        if (configured) {
            player.setOnCompletionListener {
                isPlaying = false
                playheadSec = 0.0
            }
            mediaPlayerRef = player
            mediaPlayerReady = true
        } else {
            runCatching { player.release() }
            mediaPlayerRef = null
            mediaPlayerReady = false
        }

        onDispose {
            runCatching {
                player.stop()
            }
            runCatching {
                player.release()
            }
            mediaPlayerRef = null
            mediaPlayerReady = false
        }
    }

    val seekPlaybackTo: (Double) -> Unit = { targetSec ->
        val clamped = targetSec.coerceIn(0.0, totalDurationSec)
        playheadSec = clamped
        if (mediaPlayerReady) {
            runCatching {
                mediaPlayerRef?.seekTo((clamped * 1000.0).toInt())
            }
        }
        // Auto-select the lyric segment covering this timestamp if any
        val matchingIdx = editableSegments.indexOfFirst { clamped >= it.startSec && clamped <= it.endSec }
        if (matchingIdx >= 0) {
            selectedSegmentIndex = matchingIdx
        }
    }

    val togglePlayback: () -> Unit = {
        val nextPlaying = !isPlaying
        if (nextPlaying) {
            if (playheadSec >= totalDurationSec - 0.15) {
                seekPlaybackTo(0.0)
            }
            if (mediaPlayerReady) {
                runCatching { mediaPlayerRef?.start() }
            }
            isPlaying = true
        } else {
            if (mediaPlayerReady) {
                runCatching {
                    if (mediaPlayerRef?.isPlaying == true) {
                        mediaPlayerRef?.pause()
                    }
                }
            }
            isPlaying = false
        }
    }

    // Playhead synchronization ticker (~35ms resolution)
    LaunchedEffect(isPlaying, loopActiveSegment, selectedSegmentIndex, mediaPlayerReady) {
        var lastTickMs = System.currentTimeMillis()
        while (isActive && isPlaying) {
            delay(35L)
            val nowMs = System.currentTimeMillis()
            val dtSec = ((nowMs - lastTickMs) / 1000.0).coerceIn(0.01, 0.25)
            lastTickMs = nowMs

            val currentPosSec = if (mediaPlayerReady && mediaPlayerRef?.isPlaying == true) {
                (mediaPlayerRef?.currentPosition ?: 0) / 1000.0
            } else {
                playheadSec + dtSec
            }

            val activeSeg = editableSegments.getOrNull(selectedSegmentIndex)
            if (loopActiveSegment && activeSeg != null) {
                if (currentPosSec > activeSeg.endSec || currentPosSec < activeSeg.startSec - 0.1) {
                    seekPlaybackTo(activeSeg.startSec)
                } else {
                    playheadSec = currentPosSec
                }
            } else {
                if (currentPosSec >= totalDurationSec) {
                    isPlaying = false
                    playheadSec = 0.0
                    if (mediaPlayerReady) {
                        runCatching {
                            mediaPlayerRef?.pause()
                            mediaPlayerRef?.seekTo(0)
                        }
                    }
                } else {
                    playheadSec = currentPosSec
                    // Highlight whichever lyric segment the playhead enters
                    val currentIdx = editableSegments.indexOfFirst {
                        currentPosSec >= it.startSec && currentPosSec <= it.endSec
                    }
                    if (currentIdx >= 0 && currentIdx != selectedSegmentIndex) {
                        selectedSegmentIndex = currentIdx
                    }
                }
            }
        }
    }

    val activeSegment = editableSegments.getOrNull(selectedSegmentIndex)
    val livePlayingSegment = editableSegments.firstOrNull {
        playheadSec >= it.startSec && playheadSec <= it.endSec
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                .testTag("waveform_lyric_sync_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Top Header Bar
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
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Waveform & Lyric Sync Studio",
                            tint = ElectricAmethystLight,
                            modifier = Modifier.size(22.dp)
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
                                text = "Wavelength vs. Timestamped Lyrics (${item.expectedOutputFilename})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnapdragonCyan
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveUpdatedSegments(item.id, editableSegments)
                                hasUnsavedChanges = false
                                savedFeedbackBanner = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasUnsavedChanges) ElectricAmethyst else StatusCompletedGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("save_sync_in_place_button")
                        ) {
                            Icon(
                                imageVector = if (savedFeedbackBanner && !hasUnsavedChanges) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.Save
                                },
                                contentDescription = "Save Sync In-Place",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    hasUnsavedChanges -> "Save Sync (${item.expectedOutputFilename})"
                                    savedFeedbackBanner -> "Saved In-Place!"
                                    else -> "Save In-Place"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_sync_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Sync Studio",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = StudioBorder
                )

                val windowSpanSec = (totalDurationSec / zoomFactor).coerceAtLeast(4.0)
                val windowStartSec = if (zoomFactor <= 1f) {
                    0.0
                } else {
                    (playheadSec - windowSpanSec * 0.5).coerceIn(0.0, max(0.0, totalDurationSec - windowSpanSec))
                }
                val windowEndSec = (windowStartSec + windowSpanSec).coerceAtMost(totalDurationSec)

                val visualizerAndTransportCard: @Composable () -> Unit = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioBackground)
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
                                Text(
                                    text = "🔊 Audio Wavelength & Lyric Timestamp Alignment (Tap/Drag to Scrub)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricAmethystLight
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1f to "1x Full", 2f to "2x Zoom", 4f to "4x Detail").forEach { (factor, label) ->
                                        val selected = zoomFactor == factor
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (selected) ElectricAmethystContainer else StudioSurfaceVariant)
                                                .border(
                                                    1.dp,
                                                    if (selected) ElectricAmethyst else StudioBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { zoomFactor = factor }
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) ElectricAmethystLight else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            if (isComputingWaveform) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = SnapdragonCyan,
                                    trackColor = StudioSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            WavelengthVsLyricsCanvas(
                                waveformPeaks = item.waveformPeaks,
                                segments = editableSegments,
                                selectedSegmentIndex = selectedSegmentIndex,
                                playheadSec = playheadSec,
                                totalDurationSec = totalDurationSec,
                                windowStartSec = windowStartSec,
                                windowEndSec = windowEndSec,
                                onScrubTime = seekPlaybackTo,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp)
                                    .testTag("wavelength_lyrics_canvas")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Live Active Lyric Karaoke Readout Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioSurfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = livePlayingSegment?.let { "🎤 [${TranscriptFormatter.formatShortTime(it.startSec)}] ${it.text}" }
                                            ?: "⏸ Playhead in instrumental / gap (${TranscriptFormatter.formatShortTime(playheadSec)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (livePlayingSegment != null) SnapdragonCyan else TextSecondary,
                                        fontWeight = if (livePlayingSegment != null) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${TranscriptFormatter.formatShortTime(playheadSec)} / ${TranscriptFormatter.formatShortTime(totalDurationSec)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = ElectricAmethystLight
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Playback Transport Controls Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = togglePlayback,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPlaying) MagmaCoral else ElectricAmethyst
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("sync_playback_play_pause_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause Playback" else "Play to Verify Sync",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPlaying) "Pause" else "Play Sync",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (editableSegments.isNotEmpty()) {
                                                val prevIdx = (selectedSegmentIndex - 1).coerceAtLeast(0)
                                                selectedSegmentIndex = prevIdx
                                                seekPlaybackTo(editableSegments[prevIdx].startSec)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipPrevious,
                                            contentDescription = "Previous Lyric Line",
                                            tint = TextPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (editableSegments.isNotEmpty()) {
                                                val nextIdx = (selectedSegmentIndex + 1).coerceAtMost(editableSegments.lastIndex)
                                                selectedSegmentIndex = nextIdx
                                                seekPlaybackTo(editableSegments[nextIdx].startSec)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = "Next Lyric Line",
                                            tint = TextPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    FilterChip(
                                        selected = loopActiveSegment,
                                        onClick = { loopActiveSegment = !loopActiveSegment },
                                        label = {
                                            Text(
                                                text = "Loop Line",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Repeat,
                                                contentDescription = "Loop Active Lyric Line",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricAmethystContainer,
                                            selectedLabelColor = ElectricAmethystLight
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Global Track Offset Shift (-0.1s / +0.1s)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Track Offset:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            editableSegments = editableSegments.map { seg ->
                                                val ns = (seg.startSec - 0.10).coerceAtLeast(0.0)
                                                val ne = (seg.endSec - 0.10).coerceAtLeast(ns + 0.3)
                                                seg.copy(startSec = ns, endSec = ne)
                                            }
                                            hasUnsavedChanges = true
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text("-100ms", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editableSegments = editableSegments.map { seg ->
                                                val ns = (seg.startSec + 0.10).coerceAtMost(totalDurationSec - 0.3)
                                                val ne = (seg.endSec + 0.10).coerceAtMost(totalDurationSec)
                                                seg.copy(startSec = ns, endSec = ne.coerceAtLeast(ns + 0.3))
                                            }
                                            hasUnsavedChanges = true
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text("+100ms", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                val activeSegmentEditorCard: @Composable () -> Unit = {
                    if (activeSegment != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ElectricAmethyst.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
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
                                    Text(
                                        text = "✏️ Edit Selected Line #${activeSegment.index}  [${TranscriptFormatter.formatShortTime(activeSegment.startSec)} → ${TranscriptFormatter.formatShortTime(activeSegment.endSec)}]",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricAmethystLight,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = {
                                                val insertStart = playheadSec.coerceIn(0.0, max(0.0, totalDurationSec - 1.0))
                                                val insertEnd = (insertStart + 2.5).coerceAtMost(totalDurationSec)
                                                val newSeg = TranscriptSegment(
                                                    index = editableSegments.size + 1,
                                                    startSec = insertStart,
                                                    endSec = insertEnd,
                                                    text = "New lyric line"
                                                )
                                                val updated = (editableSegments + newSeg)
                                                    .sortedBy { it.startSec }
                                                    .mapIndexed { i, s -> s.copy(index = i + 1) }
                                                editableSegments = updated
                                                selectedSegmentIndex = updated.indexOfFirst {
                                                    abs(it.startSec - insertStart) < 0.01 && it.text == "New lyric line"
                                                }.coerceAtLeast(0)
                                                hasUnsavedChanges = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Add Lyric at Playhead",
                                                tint = SnapdragonCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("+ Line @ Playhead", style = MaterialTheme.typography.labelSmall, color = SnapdragonCyan)
                                        }

                                        if (editableSegments.isNotEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    val updated = editableSegments
                                                        .filterIndexed { i, _ -> i != selectedSegmentIndex }
                                                        .mapIndexed { i, s -> s.copy(index = i + 1) }
                                                    editableSegments = updated
                                                    selectedSegmentIndex = selectedSegmentIndex.coerceIn(0, max(0, updated.lastIndex))
                                                    hasUnsavedChanges = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Delete Lyric Line",
                                                    tint = StatusErrorRed,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Delete", style = MaterialTheme.typography.labelSmall, color = StatusErrorRed)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = activeSegment.text,
                                    onValueChange = { updatedText ->
                                        editableSegments = editableSegments.mapIndexed { idx, seg ->
                                            if (idx == selectedSegmentIndex) seg.copy(text = updatedText) else seg
                                        }
                                        hasUnsavedChanges = true
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricAmethyst,
                                        unfocusedBorderColor = StudioBorder,
                                        focusedContainerColor = StudioSurface,
                                        unfocusedContainerColor = StudioSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sync_lyric_text_input")
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val newStart = playheadSec.coerceIn(0.0, max(0.0, activeSegment.endSec - 0.25))
                                            editableSegments = editableSegments.mapIndexed { idx, seg ->
                                                if (idx == selectedSegmentIndex) seg.copy(startSec = newStart) else seg
                                            }
                                            hasUnsavedChanges = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethystContainer),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("snap_start_to_playhead_button")
                                    ) {
                                        Text(
                                            text = "[⏱ Set Start = Playhead",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricAmethystLight
                                        )
                                    }

                                    listOf(-0.20 to "Start -0.2s", -0.05 to "Start -0.05s", 0.05 to "Start +0.05s", 0.20 to "Start +0.2s").forEach { (delta, label) ->
                                        OutlinedButton(
                                            onClick = {
                                                val newStart = (activeSegment.startSec + delta).coerceIn(0.0, max(0.0, activeSegment.endSec - 0.25))
                                                editableSegments = editableSegments.mapIndexed { idx, seg ->
                                                    if (idx == selectedSegmentIndex) seg.copy(startSec = newStart) else seg
                                                }
                                                seekPlaybackTo(newStart)
                                                hasUnsavedChanges = true
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val newEnd = playheadSec.coerceIn(activeSegment.startSec + 0.25, totalDurationSec)
                                            editableSegments = editableSegments.mapIndexed { idx, seg ->
                                                if (idx == selectedSegmentIndex) seg.copy(endSec = newEnd) else seg
                                            }
                                            hasUnsavedChanges = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF172D3D)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("snap_end_to_playhead_button")
                                    ) {
                                        Text(
                                            text = "Set End = Playhead ⏱]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SnapdragonCyan
                                        )
                                    }

                                    listOf(-0.10 to "End -0.1s", 0.10 to "End +0.1s").forEach { (delta, label) ->
                                        OutlinedButton(
                                            onClick = {
                                                val newEnd = (activeSegment.endSec + delta).coerceIn(activeSegment.startSec + 0.25, totalDurationSec)
                                                editableSegments = editableSegments.mapIndexed { idx, seg ->
                                                    if (idx == selectedSegmentIndex) seg.copy(endSec = newEnd) else seg
                                                }
                                                hasUnsavedChanges = true
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🛡️ Zero-Hallucination Guard: No Auto-Generated Lyrics",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricAmethystLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scrub the wavelength above and add verified lyric lines at the playhead.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val insertStart = playheadSec.coerceIn(0.0, max(0.0, totalDurationSec - 1.0))
                                        val insertEnd = (insertStart + 2.8).coerceAtMost(totalDurationSec)
                                        editableSegments = listOf(
                                            TranscriptSegment(
                                                index = 1,
                                                startSec = insertStart,
                                                endSec = insertEnd,
                                                text = "First verified lyric line"
                                            )
                                        )
                                        selectedSegmentIndex = 0
                                        hasUnsavedChanges = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmethyst),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("add_first_lyric_line_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add First Lyric Line at Playhead",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Line @ Playhead", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                val timestampedLyricsScrollList: @Composable (Modifier) -> Unit = { listMod ->
                    Column(modifier = listMod.fillMaxWidth()) {
                        Text(
                            text = "📜 Timestamped Lyrics (${editableSegments.size} lines) — Tap any line to jump playhead & verify sync:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioBackground)
                                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                                .padding(6.dp)
                                .testTag("sync_studio_lyrics_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(editableSegments, key = { idx, seg -> "${idx}_${seg.startSec}" }) { idx, seg ->
                                val isSelected = idx == selectedSegmentIndex
                                val isPlayheadInside = playheadSec >= seg.startSec && playheadSec <= seg.endSec

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isPlayheadInside -> Color(0xFF1A3344)
                                                isSelected -> ElectricAmethystContainer.copy(alpha = 0.65f)
                                                else -> StudioSurface
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isPlayheadInside -> SnapdragonCyan
                                                isSelected -> ElectricAmethyst
                                                else -> Color.Transparent
                                            },
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedSegmentIndex = idx
                                            seekPlaybackTo(seg.startSec)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "#%02d", idx + 1),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = if (isPlayheadInside) SnapdragonCyan else TextMuted
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "[${TranscriptFormatter.formatShortTime(seg.startSec)} → ${TranscriptFormatter.formatShortTime(seg.endSec)}]",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = if (isPlayheadInside) SnapdragonCyan else ElectricAmethystLight
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = seg.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            fontWeight = if (isPlayheadInside || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Select to Edit Sync",
                                        tint = if (isSelected) ElectricAmethystLight else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Adaptive Foldable Posture Routing inside Sync Studio
                val activePosture = foldableState?.posture ?: FoldablePosture.COMPACT_COVER
                when (activePosture) {
                    FoldablePosture.BOOK_POSTURE, FoldablePosture.FLAT_DUAL_PANE -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1.08f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visualizerAndTransportCard()
                                activeSegmentEditorCard()
                            }

                            if (activePosture == FoldablePosture.BOOK_POSTURE) {
                                FoldableHingeGutterVertical(
                                    hingeWidthDp = foldableState?.hingeSpanDp ?: 16.dp,
                                    isHalfOpened = foldableState?.isHalfOpened ?: true
                                )
                            }

                            timestampedLyricsScrollList(
                                Modifier
                                    .weight(0.92f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    FoldablePosture.TABLETOP_POSTURE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.46f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                visualizerAndTransportCard()
                            }

                            FoldableHingeCreaseHorizontal(
                                hingeHeightDp = foldableState?.hingeSpanDp ?: 22.dp,
                                postureLabel = "Tabletop Flex Hinge • Top: Wavelength & Transport | Bottom: Line Sync Editor"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.54f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeSegmentEditorCard()
                                timestampedLyricsScrollList(Modifier.weight(1f))
                            }
                        }
                    }

                    FoldablePosture.COMPACT_COVER -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visualizerAndTransportCard()
                            activeSegmentEditorCard()
                            timestampedLyricsScrollList(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Canvas rendering the audio/video amplitude wavelength (waveform bars) directly overlaid
 * with the timestamped lyric segments `[startSec .. endSec]` and a live scrubbable playhead cursor.
 */
@Composable
private fun WavelengthVsLyricsCanvas(
    waveformPeaks: List<Float>,
    segments: List<TranscriptSegment>,
    selectedSegmentIndex: Int,
    playheadSec: Double,
    totalDurationSec: Double,
    windowStartSec: Double,
    windowEndSec: Double,
    onScrubTime: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleSpanSec = (windowEndSec - windowStartSec).coerceAtLeast(0.5)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E1016))
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .pointerInput(windowStartSec, windowEndSec) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    val targetSec = windowStartSec + frac * visibleSpanSec
                    onScrubTime(targetSec)
                }
            }
            .pointerInput(windowStartSec, windowEndSec) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    val targetSec = windowStartSec + frac * visibleSpanSec
                    onScrubTime(targetSec)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.54f

        // 1. Draw Timestamped Lyric Blocks behind/over the wavelength so alignment is immediately visible
        for ((idx, seg) in segments.withIndex()) {
            if (seg.endSec < windowStartSec || seg.startSec > windowEndSec) continue
            val startX = (((seg.startSec - windowStartSec) / visibleSpanSec) * w).toFloat().coerceIn(0f, w)
            val endX = (((seg.endSec - windowStartSec) / visibleSpanSec) * w).toFloat().coerceIn(startX + 3f, w)
            val blockW = (endX - startX).coerceAtLeast(3f)

            val isSelected = idx == selectedSegmentIndex
            val isPlayheadInside = playheadSec >= seg.startSec && playheadSec <= seg.endSec

            val regionColor = when {
                isPlayheadInside -> SnapdragonCyan.copy(alpha = 0.25f)
                isSelected -> ElectricAmethyst.copy(alpha = 0.26f)
                idx % 2 == 0 -> ElectricAmethyst.copy(alpha = 0.11f)
                else -> SnapdragonCyan.copy(alpha = 0.09f)
            }
            val borderColor = when {
                isPlayheadInside -> SnapdragonCyan
                isSelected -> ElectricAmethystLight
                else -> ElectricAmethyst.copy(alpha = 0.40f)
            }

            drawRoundRect(
                color = regionColor,
                topLeft = Offset(startX, 4f),
                size = Size(blockW, h - 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Top segment marker bar
            drawRect(
                color = borderColor,
                topLeft = Offset(startX, 4f),
                size = Size(blockW, if (isSelected || isPlayheadInside) 5f else 3f)
            )
            // Start boundary tick line
            drawLine(
                color = borderColor.copy(alpha = 0.7f),
                start = Offset(startX, 4f),
                end = Offset(startX, h - 4f),
                strokeWidth = if (isSelected || isPlayheadInside) 2f else 1f
            )
        }

        // 2. Center zero-crossing axis line
        drawLine(
            color = StudioBorder.copy(alpha = 0.6f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1f
        )

        // 3. Draw mirrored Audio Wavelength (Waveform Envelope) bars
        val peaks = if (waveformPeaks.isNotEmpty()) {
            waveformPeaks
        } else {
            List(180) { i -> 0.18f + 0.55f * abs(kotlin.math.sin(i * 0.23f)) }
        }

        val numBarsToDraw = 160
        val barSpacing = w / numBarsToDraw
        val barWidth = (barSpacing * 0.68f).coerceAtLeast(1.5f)
        val maxHalfHeight = h * 0.40f

        for (b in 0 until numBarsToDraw) {
            val barTimeSec = windowStartSec + ((b + 0.5) / numBarsToDraw) * visibleSpanSec
            val globalFrac = (barTimeSec / totalDurationSec.coerceAtLeast(0.1)).coerceIn(0.0, 0.9999)
            val peakIdx = (globalFrac * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
            val amp = peaks[peakIdx].coerceIn(0.04f, 1.0f)
            val halfBarH = (amp * maxHalfHeight).coerceAtLeast(2f)
            val x = b * barSpacing + (barSpacing - barWidth) * 0.5f

            val isInsideAnyLyric = segments.any { barTimeSec >= it.startSec && barTimeSec <= it.endSec }
            val isInsideSelected = segments.getOrNull(selectedSegmentIndex)?.let {
                barTimeSec >= it.startSec && barTimeSec <= it.endSec
            } ?: false
            val isBeforePlayhead = barTimeSec <= playheadSec

            val barColor = when {
                isInsideSelected -> SnapdragonCyan
                isInsideAnyLyric && isBeforePlayhead -> ElectricAmethystLight
                isInsideAnyLyric -> ElectricAmethyst.copy(alpha = 0.85f)
                else -> TextMuted.copy(alpha = 0.48f)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - halfBarH),
                size = Size(barWidth, halfBarH * 2f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }

        // 4. Draw Live Playhead Cursor
        if (playheadSec in windowStartSec..windowEndSec) {
            val playheadX = (((playheadSec - windowStartSec) / visibleSpanSec) * w).toFloat().coerceIn(0f, w)
            drawLine(
                color = MagmaCoral,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, h),
                strokeWidth = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
            )
            drawCircle(
                color = MagmaCoral,
                radius = 5.5f,
                center = Offset(playheadX, 8f)
            )
        }
    }
}
