package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.MelSpectrogramEngine
import com.example.engine.MetadataSyncAndStorageEngine
import com.example.engine.SnapdragonNpuTranscriptionEngine
import com.example.model.ComputePrecision
import com.example.model.FileProcessStatus
import com.example.model.HardwareAccelerator
import com.example.model.InputScanMode
import com.example.model.MediaKind
import com.example.model.MediaQueueItem
import com.example.model.QuantizationSuffix
import com.example.model.QueueFilter
import com.example.model.SpectrogramSnapshot
import com.example.model.TranscriptFormatter
import com.example.model.TranscriptSegment
import com.example.model.WhisperModelProfile
import com.example.ui.adaptive.FoldablePostureMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WorkspaceBottomTab(val label: String) {
    FILE_QUEUE("File Queue"),
    FINALIZED_FILES("Finalized Files"),
    EXECUTION_LOG("Log View")
}

data class TranscriberUiState(
    val inputMode: InputScanMode = InputScanMode.BATCH_DIRECTORY,
    val inputPathDisplay: String = "",
    val modelProfile: WhisperModelProfile = WhisperModelProfile.LARGE_V3_TURBO_NPU,
    val quantization: QuantizationSuffix = QuantizationSuffix.NONE,
    val accelerator: HardwareAccelerator = HardwareAccelerator.HEXAGON_QNN_HTP,
    val availableComputeTypes: List<ComputePrecision> = ComputePrecision.entries.filter {
        "qnn_htp" in it.supportedAccelerators
    },
    val computePrecision: ComputePrecision = ComputePrecision.INT8_FLOAT16,
    val autoBeamSize: Boolean = true,
    val beamSize: Int = 5,
    val lastAutoBeamSummary: String = "Auto Beam (Dynamic NPU Optimal)",
    val vadFilter: Boolean = true,
    val overwriteExisting: Boolean = false,
    val autoSyncAudioMetadataToVideo: Boolean = true,
    val showSettingsDialog: Boolean = false,
    val foldablePostureMode: FoldablePostureMode = FoldablePostureMode.AUTO_HARDWARE,
    val npuTelemetry: SnapdragonNpuTranscriptionEngine.NpuHardwareTelemetry =
        SnapdragonNpuTranscriptionEngine().detectDeviceNpuTelemetry(HardwareAccelerator.HEXAGON_QNN_HTP),
    val isCurrentModelCached: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val modelDownloadProgress: Float = 0f,
    val modelCacheDirDisplay: String = "",
    val queueItems: List<MediaQueueItem> = emptyList(),
    val activeFilter: QueueFilter = QueueFilter.ALL,
    val activeBottomTab: WorkspaceBottomTab = WorkspaceBottomTab.FILE_QUEUE,
    val logEntries: List<String> = emptyList(),
    val searchQuery: String = "",
    val isScanning: Boolean = false,
    val processingActive: Boolean = false,
    val stopRequestedLevel: Int = 0,
    val showStopConfirmDialog: Boolean = false,
    val currentFileProgress: Float = 0f,
    val currentFileElapsedSec: Double = 0.0,
    val batchProgress: Float = 0f,
    val processedBatchFiles: Int = 0,
    val totalBatchFiles: Int = 0,
    val batchEtaSeconds: Double? = null,
    val activeFileName: String = "",
    val lastRealtimeSpeedup: Double = 0.0,
    val spectrogram: SpectrogramSnapshot = SpectrogramSnapshot(),
    val inspectedItem: MediaQueueItem? = null,
    val syncEditorItem: MediaQueueItem? = null,
    val isComputingWaveformForSync: Boolean = false,
    val statusBannerMessage: String = "Snapdragon 8 Elite Gen 5 Hexagon NPU ready • Local recursive scanner active."
)

class TranscriberViewModel(application: Application) : AndroidViewModel(application) {

    private val melEngine = MelSpectrogramEngine(application.applicationContext)
    private val storageEngine = MetadataSyncAndStorageEngine(application.applicationContext)
    private val npuEngine = SnapdragonNpuTranscriptionEngine()

    private val _uiState = MutableStateFlow(TranscriberUiState())
    val uiState: StateFlow<TranscriberUiState> = _uiState.asStateFlow()

    private var processingJob: Job? = null
    private var elapsedTickerJob: Job? = null
    private var activeFileStartTimeMs: Long? = null
    private var currentLocalRootDir: File? = null
    private var currentTreeUri: Uri? = null

    private val logTimeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        val modelsRoot = npuEngine.getModelRootDirectory(application.applicationContext)
        _uiState.update { it.copy(modelCacheDirDisplay = modelsRoot.absolutePath) }
        appendLog("🚀 Codex Transcriber initialized (Snapdragon 8 Elite Gen 5 Hexagon NPU QNN-HTP).")
        appendLog("📁 Models directory: ${modelsRoot.absolutePath}")
        initializeDefaultStudioVaultAndAutoDownloadModel()
    }

    fun appendLog(message: String) {
        val timestamp = synchronized(logTimeFormatter) {
            logTimeFormatter.format(Date())
        }
        val formattedLine = "[$timestamp] $message"
        _uiState.update { state ->
            val updatedLogs = (state.logEntries + formattedLine).takeLast(600)
            state.copy(logEntries = updatedLogs)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logEntries = emptyList()) }
        appendLog("🧹 Log view cleared.")
    }

    fun appendPastedTextToLog(clipboardText: String) {
        val clean = clipboardText.trim()
        if (clean.isEmpty()) return
        appendLog("📋 Pasted note into log:\n$clean")
        _uiState.update {
            it.copy(statusBannerMessage = "Pasted clipboard text into Log View.")
        }
    }

    fun getFullLogText(): String {
        val state = _uiState.value
        val header = buildString {
            appendLine("=== CODEX TRANSCRIBER EXECUTION LOG ===")
            appendLine("Device SoC: ${state.npuTelemetry.socName}")
            appendLine("NPU Delegate: ${state.accelerator.fullTitle} (${state.computePrecision.id})")
            appendLine("Whisper Model: ${npuEngine.buildFullModelName(state.modelProfile, state.quantization)}")
            appendLine("Models Root: ${state.modelCacheDirDisplay}")
            appendLine("Scanned Path: ${state.inputPathDisplay}")
            appendLine("=======================================")
        }
        return header + "\n" + state.logEntries.joinToString("\n")
    }

    /**
     * Exports the current execution log as a .txt file to the exact Uri chosen by the user
     * via Android's system CreateDocument picker.
     */
    fun exportLogsToUserLocation(targetUri: Uri) {
        val appContext = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val fullText = getFullLogText()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val outStream = runCatching {
                        appContext.contentResolver.openOutputStream(targetUri, "w")
                    }.getOrNull() ?: appContext.contentResolver.openOutputStream(targetUri)

                    if (outStream == null) {
                        throw IllegalStateException("Could not open output stream for $targetUri")
                    }
                    outStream.use { out ->
                        out.write(fullText.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    storageEngine.resolveRealPathFromSafUri(targetUri) ?: targetUri.toString()
                }
            }
            result.fold(
                onSuccess = { savedLocation ->
                    appendLog("💾 Exported execution log (.txt) to user location: $savedLocation")
                    _uiState.update {
                        it.copy(statusBannerMessage = "✅ Log exported to: $savedLocation")
                    }
                },
                onFailure = { err ->
                    appendLog("❌ Failed to export log (.txt): ${err.localizedMessage ?: err}")
                    _uiState.update {
                        it.copy(statusBannerMessage = "❌ Failed to export log: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun setBottomTab(tab: WorkspaceBottomTab) {
        _uiState.update { it.copy(activeBottomTab = tab) }
    }

    private fun initializeDefaultStudioVaultAndAutoDownloadModel() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    statusBannerMessage = "Initializing local media vault & verifying Whisper model cache..."
                )
            }
            // Auto-download default selected model on startup so it is ready immediately
            triggerModelAutoDownloadIfNeeded(
                profile = _uiState.value.modelProfile,
                quantization = _uiState.value.quantization
            )

            val vaultDir = storageEngine.createOrResetSampleMediaTree(onLog = ::appendLog)
            currentLocalRootDir = vaultDir
            scanLocalDirectoryInternal(vaultDir)
        }
    }

    private suspend fun triggerModelAutoDownloadIfNeeded(
        profile: WhisperModelProfile,
        quantization: QuantizationSuffix
    ): File {
        val context = getApplication<Application>().applicationContext
        val state = _uiState.value
        val alreadyCached = npuEngine.isModelCachedLocally(context, profile, quantization)
        if (alreadyCached) {
            val fullModelName = npuEngine.buildFullModelName(profile, quantization)
            val dir = File(npuEngine.getModelRootDirectory(context), fullModelName)
            _uiState.update {
                it.copy(
                    isCurrentModelCached = true,
                    isDownloadingModel = false,
                    modelDownloadProgress = 100f
                )
            }
            return dir
        }

        _uiState.update {
            it.copy(
                isCurrentModelCached = false,
                isDownloadingModel = true,
                modelDownloadProgress = 5f
            )
        }
        val modelDir = npuEngine.ensureModelAutoDownloaded(
            context = context,
            modelProfile = profile,
            quantization = quantization,
            accelerator = state.accelerator,
            computePrecision = state.computePrecision,
            onProgress = { pct, statusText ->
                _uiState.update {
                    it.copy(
                        isDownloadingModel = pct < 100f,
                        modelDownloadProgress = pct,
                        isCurrentModelCached = pct >= 100f,
                        statusBannerMessage = statusText
                    )
                }
            },
            onLog = ::appendLog
        )
        _uiState.update {
            it.copy(
                isCurrentModelCached = true,
                isDownloadingModel = false,
                modelDownloadProgress = 100f
            )
        }
        return modelDir
    }

    fun resetAndScanLocalStudioTree() {
        if (_uiState.value.processingActive) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    inputMode = InputScanMode.BATCH_DIRECTORY,
                    isScanning = true,
                    statusBannerMessage = "Rebuilding local studio tree with MP3, FLAC, WAV, MP4 & MKV subfolders..."
                )
            }
            val vaultDir = storageEngine.createOrResetSampleMediaTree(onLog = ::appendLog)
            currentTreeUri = null
            currentLocalRootDir = vaultDir
            scanLocalDirectoryInternal(vaultDir)
        }
    }

    private suspend fun scanLocalDirectoryInternal(rootDir: File) {
        val state = _uiState.value
        val items = storageEngine.scanLocalDirectoryRecursive(
            rootDir = rootDir,
            overwriteExisting = state.overwriteExisting,
            autoSyncMetadata = state.autoSyncAudioMetadataToVideo,
            onLog = ::appendLog
        )
        val syncedVideosCount = items.count { it.mediaKind == MediaKind.VIDEO && it.metadata.syncedFromAudioSource != null }
        val skippedExistingCount = items.count { it.status == FileProcessStatus.SKIPPED }

        _uiState.update {
            it.copy(
                inputPathDisplay = rootDir.absolutePath,
                queueItems = items,
                totalBatchFiles = items.size,
                processedBatchFiles = 0,
                currentFileProgress = 0f,
                batchProgress = 0f,
                batchEtaSeconds = null,
                isScanning = false,
                statusBannerMessage = "Scanned ${items.size} files across subfolders • $syncedVideosCount video(s) synced • $skippedExistingCount existing."
            )
        }

        items.firstOrNull()?.let { firstItem ->
            previewItemSpectrogram(firstItem)
        }
    }

    fun setInputMode(mode: InputScanMode) {
        if (_uiState.value.processingActive) return
        appendLog("🔄 Switched input mode to: ${mode.label}")
        _uiState.update {
            it.copy(
                inputMode = mode,
                currentFileProgress = 0f,
                batchProgress = 0f,
                batchEtaSeconds = null,
                statusBannerMessage = if (mode == InputScanMode.SINGLE_FILE) {
                    "Single File mode active • Select an audio (.mp3, .flac, .wav, .m4a, .aac, .ogg) or video (.mp4, .mkv, .avi, .mov) file."
                } else {
                    "Batch Directory mode active • Scans directory and all nested subfolders recursively."
                }
            )
        }
    }

    fun onDirectoryTreePicked(treeUri: Uri) {
        if (_uiState.value.processingActive) return
        val context = getApplication<Application>().applicationContext
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            appendLog("🔐 Granted persistent read/write permission for directory tree: $treeUri")
        }.onFailure { err ->
            appendLog("⚠️ Could not persist URI permission: ${err.localizedMessage}")
        }
        currentTreeUri = treeUri
        currentLocalRootDir = null

        viewModelScope.launch {
            val resolvedRootPath = storageEngine.resolveRealPathFromSafUri(treeUri) ?: (treeUri.path ?: treeUri.toString())
            _uiState.update {
                it.copy(
                    inputMode = InputScanMode.BATCH_DIRECTORY,
                    inputPathDisplay = resolvedRootPath,
                    overwriteExisting = true,
                    isScanning = true,
                    statusBannerMessage = "Scanning local directory and subfolders recursively..."
                )
            }
            val state = _uiState.value
            val items = storageEngine.scanDocumentTreeRecursive(
                treeUri = treeUri,
                overwriteExisting = state.overwriteExisting,
                autoSyncMetadata = state.autoSyncAudioMetadataToVideo,
                onLog = ::appendLog
            )
            val syncedCount = items.count { it.mediaKind == MediaKind.VIDEO && it.metadata.syncedFromAudioSource != null }
            _uiState.update {
                it.copy(
                    queueItems = items,
                    totalBatchFiles = items.size,
                    processedBatchFiles = 0,
                    currentFileProgress = 0f,
                    batchProgress = 0f,
                    batchEtaSeconds = null,
                    isScanning = false,
                    statusBannerMessage = if (items.isEmpty()) {
                        "No supported audio/video files found in selected directory."
                    } else {
                        "Found ${items.size} file(s) • Synced metadata to $syncedCount video file(s)."
                    }
                )
            }
            items.firstOrNull()?.let { previewItemSpectrogram(it) }
        }
    }

    fun onSingleMediaFilePicked(fileUri: Uri) {
        if (_uiState.value.processingActive) return
        val context = getApplication<Application>().applicationContext
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                fileUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        currentLocalRootDir = null

        viewModelScope.launch {
            val resolvedFilePath = storageEngine.resolveRealPathFromSafUri(fileUri) ?: (fileUri.path ?: fileUri.toString())
            _uiState.update {
                it.copy(
                    inputMode = InputScanMode.SINGLE_FILE,
                    inputPathDisplay = resolvedFilePath,
                    overwriteExisting = true,
                    isScanning = true,
                    statusBannerMessage = "Inspecting selected media file & extracting metadata..."
                )
            }
            val item = storageEngine.inspectSingleMediaUri(
                uri = fileUri,
                overwriteExisting = true,
                fallbackTreeUri = currentTreeUri,
                onLog = ::appendLog
            )
            val list = listOfNotNull(item)
            _uiState.update {
                it.copy(
                    queueItems = list,
                    totalBatchFiles = list.size,
                    processedBatchFiles = 0,
                    currentFileProgress = 0f,
                    batchProgress = 0f,
                    batchEtaSeconds = null,
                    isScanning = false,
                    statusBannerMessage = if (item != null) {
                        "Loaded ${item.filename} (${item.mediaKind.label} → ${item.expectedOutputFilename})"
                    } else {
                        "Unsupported media file selected."
                    }
                )
            }
            if (item != null) {
                previewItemSpectrogram(item)
            }
        }
    }

    fun previewItemSpectrogram(item: MediaQueueItem) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    spectrogram = it.spectrogram.copy(
                        fileName = item.filename,
                        isComputing = true,
                        showPlayhead = false
                    )
                )
            }
            val (snapshot, _) = melEngine.computeMelSpectrogram(
                fileName = item.filename,
                localFilePath = item.localFilePath,
                documentUriString = item.documentUriString,
                expectedDurationSec = item.durationSec
            )
            _uiState.update { it.copy(spectrogram = snapshot) }
        }
    }

    fun setModelProfile(profile: WhisperModelProfile) {
        if (_uiState.value.processingActive) return
        val context = getApplication<Application>().applicationContext
        val cached = npuEngine.isModelCachedLocally(context, profile, _uiState.value.quantization)
        appendLog("🧠 Selected Whisper model profile: ${profile.displayName} (${profile.parameterCount})")
        _uiState.update {
            it.copy(
                modelProfile = profile,
                isCurrentModelCached = cached,
                statusBannerMessage = "Selected ${profile.displayName} (${profile.parameterCount}) • Auto-downloads if needed."
            )
        }
        viewModelScope.launch {
            triggerModelAutoDownloadIfNeeded(profile, _uiState.value.quantization)
        }
    }

    fun setQuantization(quantization: QuantizationSuffix) {
        if (_uiState.value.processingActive) return
        val context = getApplication<Application>().applicationContext
        val cached = npuEngine.isModelCachedLocally(context, _uiState.value.modelProfile, quantization)
        appendLog("⚙️ Selected quantization suffix: ${quantization.displayLabel}")
        _uiState.update {
            it.copy(
                quantization = quantization,
                isCurrentModelCached = cached,
                statusBannerMessage = "Quantization suffix set to ${quantization.displayLabel}"
            )
        }
        viewModelScope.launch {
            triggerModelAutoDownloadIfNeeded(_uiState.value.modelProfile, quantization)
        }
    }

    fun setHardwareAccelerator(accelerator: HardwareAccelerator) {
        if (_uiState.value.processingActive) return
        val validComputes = ComputePrecision.entries.filter { accelerator.id in it.supportedAccelerators }
        val currentCompute = _uiState.value.computePrecision
        val resolvedCompute = if (currentCompute in validComputes) currentCompute else accelerator.defaultCompute
        val telemetry = npuEngine.detectDeviceNpuTelemetry(accelerator)
        appendLog("⚡ Switched hardware delegate: ${accelerator.fullTitle} (Compute: ${resolvedCompute.id})")

        _uiState.update {
            it.copy(
                accelerator = accelerator,
                availableComputeTypes = validComputes,
                computePrecision = resolvedCompute,
                npuTelemetry = telemetry,
                statusBannerMessage = "${accelerator.fullTitle} • Compute: ${resolvedCompute.id}"
            )
        }
    }

    fun setComputePrecision(precision: ComputePrecision) {
        if (_uiState.value.processingActive) return
        appendLog("🔢 Set compute precision: ${precision.label}")
        _uiState.update {
            it.copy(
                computePrecision = precision,
                statusBannerMessage = "Compute precision updated to ${precision.label}"
            )
        }
    }

    fun openSettingsMenu() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettingsMenu() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun setAutoBeamSize(enabled: Boolean) {
        if (_uiState.value.processingActive) return
        appendLog("🎯 Auto Beam mode: ${if (enabled) "ENABLED (Dynamic NPU Optimal)" else "DISABLED (Manual Beam=${_uiState.value.beamSize})"}")
        _uiState.update {
            it.copy(
                autoBeamSize = enabled,
                statusBannerMessage = if (enabled) {
                    "Auto Beam enabled • Optimal beam width selected dynamically per track."
                } else {
                    "Manual Beam mode • Beam search decoding width set to ${it.beamSize}"
                }
            )
        }
    }

    fun setBeamSize(beam: Int) {
        if (_uiState.value.processingActive) return
        val clamped = beam.coerceIn(1, 20)
        appendLog("🎯 Manual Beam size updated to: $clamped")
        _uiState.update {
            it.copy(
                autoBeamSize = false,
                beamSize = clamped,
                statusBannerMessage = "Beam search decoding width set to $clamped"
            )
        }
    }

    fun setVadFilter(enabled: Boolean) {
        if (_uiState.value.processingActive) return
        appendLog("🎙️ VAD Silence Filter: ${if (enabled) "ENABLED" else "DISABLED"}")
        _uiState.update {
            it.copy(
                vadFilter = enabled,
                statusBannerMessage = if (enabled) {
                    "VAD Filter enabled • Non-speech silence regions will be trimmed automatically."
                } else {
                    "VAD Filter disabled • Full continuous audio frames will be transcribed."
                }
            )
        }
    }

    fun setOverwriteExisting(overwrite: Boolean) {
        if (_uiState.value.processingActive) return
        appendLog("📝 Overwrite Existing Output: ${if (overwrite) "ENABLED" else "DISABLED (Skip existing .lrc/.srt)"}")
        _uiState.update { state ->
            val updatedItems = state.queueItems.map { item ->
                if (item.existingOutputExists) {
                    if (overwrite) {
                        item.copy(
                            status = FileProcessStatus.PENDING,
                            statusMessage = "Overwrite ${item.expectedOutputFilename}",
                            progressPercent = 0f
                        )
                    } else {
                        item.copy(
                            status = FileProcessStatus.SKIPPED,
                            statusMessage = "Output Exists (${item.expectedOutputFilename})",
                            progressPercent = 100f
                        )
                    }
                } else {
                    item
                }
            }
            state.copy(
                overwriteExisting = overwrite,
                queueItems = updatedItems,
                statusBannerMessage = if (overwrite) {
                    "Overwrite enabled • Existing .lrc and .srt files will be regenerated in place."
                } else {
                    "Safe in-place mode • Creates .lrc (audio) and .srt (video) only if not already present."
                }
            )
        }
    }

    fun setAutoSyncMetadata(enabled: Boolean) {
        if (_uiState.value.processingActive) return
        appendLog("🔗 Automatic Audio→Video Metadata Sync: ${if (enabled) "ENABLED" else "DISABLED"}")
        _uiState.update { state ->
            state.copy(
                autoSyncAudioMetadataToVideo = enabled,
                statusBannerMessage = if (enabled) {
                    "Automatic Audio-to-Video metadata synchronization enabled."
                } else {
                    "Audio-to-Video metadata sync paused."
                }
            )
        }
        if (enabled) {
            viewModelScope.launch {
                val currentItems = _uiState.value.queueItems
                val syncedItems = withContext(Dispatchers.IO) {
                    storageEngine.synchronizeAudioMetadataToVideos(currentItems, ::appendLog)
                }
                _uiState.update { state ->
                    state.copy(queueItems = syncedItems)
                }
            }
        }
    }

    fun setFilter(filter: QueueFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun inspectQueueItem(item: MediaQueueItem?) {
        _uiState.update { it.copy(inspectedItem = item) }
    }

    fun setFoldablePostureMode(mode: FoldablePostureMode) {
        appendLog("📱 Foldable layout mode set to: ${mode.label}")
        _uiState.update {
            it.copy(
                foldablePostureMode = mode,
                statusBannerMessage = "Foldable posture mode: ${mode.label} • ${mode.subtitle}"
            )
        }
    }

    fun cycleFoldablePostureMode() {
        val next = _uiState.value.foldablePostureMode.nextCycledMode()
        setFoldablePostureMode(next)
    }

    /**
     * Opens the Waveform (Wavelength) vs. Timestamped Lyrics Sync Studio for a finalized file,
     * decoding the audio/video waveform envelope on demand if not yet cached.
     */
    fun openWaveformLyricSyncStudio(item: MediaQueueItem) {
        val parsedSegments = if (item.segments.isNotEmpty()) {
            item.segments
        } else if (item.generatedTranscriptContent.isNotBlank()) {
            TranscriptFormatter.parseTranscriptToSegments(
                content = item.generatedTranscriptContent,
                mediaKind = item.mediaKind,
                totalDurationSec = item.durationSec
            )
        } else {
            emptyList()
        }

        val hydratedItem = item.copy(segments = parsedSegments)
        _uiState.update {
            it.copy(
                syncEditorItem = hydratedItem,
                inspectedItem = null,
                isComputingWaveformForSync = hydratedItem.waveformPeaks.isEmpty()
            )
        }

        if (hydratedItem.waveformPeaks.isEmpty()) {
            viewModelScope.launch {
                val (peaks, actualDurationSec) = melEngine.computeWaveformEnvelopeForItem(
                    fileName = hydratedItem.filename,
                    localFilePath = hydratedItem.localFilePath,
                    documentUriString = hydratedItem.documentUriString,
                    expectedDurationSec = hydratedItem.durationSec
                )
                updateQueueItem(hydratedItem.id) { current ->
                    current.copy(
                        waveformPeaks = peaks,
                        durationSec = if (actualDurationSec > 0.0) actualDurationSec else current.durationSec,
                        segments = if (current.segments.isNotEmpty()) current.segments else parsedSegments
                    )
                }
                _uiState.update { state ->
                    val updatedSyncItem = state.syncEditorItem?.let { syncItem ->
                        if (syncItem.id == hydratedItem.id) {
                            syncItem.copy(
                                waveformPeaks = peaks,
                                durationSec = if (actualDurationSec > 0.0) actualDurationSec else syncItem.durationSec
                            )
                        } else syncItem
                    }
                    state.copy(
                        syncEditorItem = updatedSyncItem,
                        isComputingWaveformForSync = false
                    )
                }
            }
        }
    }

    fun closeWaveformLyricSyncStudio() {
        _uiState.update {
            it.copy(
                syncEditorItem = null,
                isComputingWaveformForSync = false
            )
        }
    }

    /**
     * Saves user-edited lyric timestamps and text from the Waveform & Lyric Sync Studio
     * directly IN PLACE into the file's `.lrc` or `.srt` file.
     */
    fun saveEditedSegmentsInPlace(itemId: String, updatedSegments: List<TranscriptSegment>) {
        viewModelScope.launch {
            val state = _uiState.value
            val targetItem = state.queueItems.find { it.id == itemId } ?: state.syncEditorItem ?: return@launch
            val sortedIndexed = updatedSegments
                .sortedBy { it.startSec }
                .mapIndexed { idx, seg ->
                    seg.copy(
                        index = idx + 1,
                        startSec = seg.startSec.coerceAtLeast(0.0),
                        endSec = seg.endSec.coerceAtLeast(seg.startSec + 0.25)
                    )
                }

            val fullModelTag = npuEngine.buildFullModelName(state.modelProfile, state.quantization)
            val formattedContent = when (targetItem.mediaKind) {
                MediaKind.AUDIO -> TranscriptFormatter.segmentsToLrc(
                    segments = sortedIndexed,
                    metadata = targetItem.metadata,
                    modelName = fullModelTag
                )
                MediaKind.VIDEO -> TranscriptFormatter.segmentsToSrt(
                    segments = sortedIndexed,
                    metadata = targetItem.metadata
                )
            }

            val saveResult = storageEngine.saveTimedOutputInPlace(
                item = targetItem,
                content = formattedContent,
                overwriteExisting = true,
                onLog = ::appendLog
            )

            saveResult.fold(
                onSuccess = { savedPath ->
                    appendLog("✅ Updated sync timestamps & saved in-place (${sortedIndexed.size} lines): $savedPath")
                    updateQueueItem(targetItem.id) { current ->
                        current.copy(
                            status = FileProcessStatus.COMPLETED,
                            statusMessage = "Synced & Saved (${current.expectedOutputFilename})",
                            existingOutputExists = true,
                            savedOutputPath = savedPath,
                            segments = sortedIndexed,
                            generatedTranscriptContent = formattedContent
                        )
                    }
                    _uiState.update { s ->
                        val updatedEditor = s.syncEditorItem?.takeIf { it.id == targetItem.id }?.copy(
                            status = FileProcessStatus.COMPLETED,
                            existingOutputExists = true,
                            savedOutputPath = savedPath,
                            segments = sortedIndexed,
                            generatedTranscriptContent = formattedContent
                        ) ?: s.syncEditorItem
                        s.copy(
                            syncEditorItem = updatedEditor,
                            statusBannerMessage = "✅ Sync verified & saved in place: $savedPath"
                        )
                    }
                },
                onFailure = { err ->
                    appendLog("❌ Failed to save edited sync for ${targetItem.expectedOutputFilename}: ${err.localizedMessage ?: err}")
                    _uiState.update {
                        it.copy(statusBannerMessage = "❌ Failed to save sync: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun dismissStopDialog() {
        _uiState.update { it.copy(showStopConfirmDialog = false) }
    }

    fun onPrimaryActionClicked() {
        val state = _uiState.value
        if (!state.processingActive) {
            startBatchTranscription()
        } else {
            _uiState.update { it.copy(showStopConfirmDialog = true) }
        }
    }

    fun confirmStopAfterCurrentFile() {
        appendLog("ℹ️ Stop requested by user: will halt cleanly after the current file finishes.")
        _uiState.update {
            it.copy(
                stopRequestedLevel = 1,
                showStopConfirmDialog = false,
                statusBannerMessage = "ℹ️ Stop requested • Will halt cleanly after the current file finishes."
            )
        }
    }

    fun confirmForceImmediateStop() {
        appendLog("🛑 IMMEDIATE STOP requested by user! Halting active transcription...")
        _uiState.update {
            it.copy(
                stopRequestedLevel = 2,
                showStopConfirmDialog = false,
                statusBannerMessage = "🛑 Immediate stop requested • Halting NPU pipeline..."
            )
        }
        processingJob?.cancel()
        elapsedTickerJob?.cancel()
    }

    private fun startBatchTranscription() {
        val initialState = _uiState.value
        if (initialState.processingActive || initialState.queueItems.isEmpty()) return

        val preparedItems = initialState.queueItems.map { item ->
            if (item.existingOutputExists && !initialState.overwriteExisting) {
                item.copy(
                    status = FileProcessStatus.SKIPPED,
                    statusMessage = "Output Exists (${item.expectedOutputFilename})",
                    progressPercent = 100f
                )
            } else {
                item.copy(
                    status = FileProcessStatus.PENDING,
                    statusMessage = "Queued for ${item.mediaKind.formatBadge}",
                    progressPercent = 0f
                )
            }
        }

        val fullModelTag = npuEngine.buildFullModelName(initialState.modelProfile, initialState.quantization)

        _uiState.update {
            it.copy(
                queueItems = preparedItems,
                processingActive = true,
                stopRequestedLevel = 0,
                currentFileProgress = 0f,
                currentFileElapsedSec = 0.0,
                batchProgress = 0f,
                processedBatchFiles = 0,
                totalBatchFiles = preparedItems.size,
                batchEtaSeconds = null,
                statusBannerMessage = "🚀 Preparing model '$fullModelTag' on ${initialState.accelerator.shortName}..."
            )
        }

        appendLog("🚀 Starting transcription batch for ${preparedItems.size} file(s)...")
        startElapsedTimerTicker()

        processingJob = viewModelScope.launch {
            val completedFileTimesSec = mutableListOf<Double>()
            val totalFiles = preparedItems.size

            try {
                // Step 0: Ensure selected Whisper model is auto-downloaded to 'Audio Models/<fullModelTag>'
                val modelDir = triggerModelAutoDownloadIfNeeded(
                    profile = _uiState.value.modelProfile,
                    quantization = _uiState.value.quantization
                )
                appendLog("🧠 Model ready at: ${modelDir.absolutePath}")

                for ((index, currentItem) in preparedItems.withIndex()) {
                    if (_uiState.value.stopRequestedLevel >= 2 || !isActive) {
                        throw InterruptedException("Immediate stop requested.")
                    }
                    if (_uiState.value.stopRequestedLevel == 1 && index > 0) {
                        appendLog("🛑 Halted batch after completing $index file(s) per user request.")
                        _uiState.update {
                            it.copy(
                                statusBannerMessage = "🛑 Stopped cleanly after completing $index file(s)."
                            )
                        }
                        break
                    }

                    val processedCount = index + 1
                    _uiState.update {
                        it.copy(
                            processedBatchFiles = processedCount,
                            activeFileName = currentItem.filename,
                            currentFileProgress = 0f
                        )
                    }

                    if (currentItem.existingOutputExists && !_uiState.value.overwriteExisting) {
                        appendLog("⚠️ [${processedCount}/${totalFiles}] Skipping '${currentItem.filename}' — '${currentItem.expectedOutputFilename}' already exists in place.")
                        updateQueueItem(currentItem.id) { item ->
                            item.copy(
                                status = FileProcessStatus.SKIPPED,
                                statusMessage = "Skipped • ${item.expectedOutputFilename} exists",
                                progressPercent = 100f
                            )
                        }
                        completedFileTimesSec.add(0.05)
                        updateBatchProgressAndEta(processedCount, totalFiles, completedFileTimesSec)
                        delay(100)
                        continue
                    }

                    activeFileStartTimeMs = System.currentTimeMillis()
                    appendLog("⚙️ [${processedCount}/${totalFiles}] Processing '${currentItem.filename}' (${currentItem.mediaKind.label} → ${currentItem.expectedOutputFilename})...")
                    updateQueueItem(currentItem.id) { item ->
                        item.copy(
                            status = FileProcessStatus.PROCESSING,
                            statusMessage = "Decoding full audio & spectrogram...",
                            progressPercent = 2f
                        )
                    }

                    try {
                        val (specSnapshot, decodedPcm) = melEngine.computeMelSpectrogram(
                            fileName = currentItem.filename,
                            localFilePath = currentItem.localFilePath,
                            documentUriString = currentItem.documentUriString,
                            expectedDurationSec = currentItem.durationSec
                        )
                        appendLog("   🔊 Mel spectrogram generated (Duration: ${TranscriptFormatter.formatEta(decodedPcm.durationSec)}, SR: ${decodedPcm.sampleRate}Hz, Samples: ${decodedPcm.samples.size})")

                        _uiState.update {
                            it.copy(
                                spectrogram = specSnapshot.copy(
                                    playheadTimeSec = 0.0,
                                    showPlayhead = true
                                )
                            )
                        }

                        val stateSnapshot = _uiState.value
                        val effectiveBeamSize = if (stateSnapshot.autoBeamSize) {
                            val decision = npuEngine.resolveAutoBeamSize(
                                modelProfile = stateSnapshot.modelProfile,
                                accelerator = stateSnapshot.accelerator,
                                computePrecision = stateSnapshot.computePrecision,
                                decodedPcm = decodedPcm
                            )
                            appendLog("   🎯 ${decision.reasonSummary}")
                            _uiState.update { it.copy(lastAutoBeamSummary = decision.reasonSummary) }
                            decision.resolvedBeamSize
                        } else {
                            stateSnapshot.beamSize
                        }

                        val segments = npuEngine.transcribeMediaItem(
                            context = getApplication<Application>().applicationContext,
                            melEngine = melEngine,
                            item = currentItem,
                            decodedPcm = decodedPcm,
                            modelProfile = stateSnapshot.modelProfile,
                            quantization = stateSnapshot.quantization,
                            accelerator = stateSnapshot.accelerator,
                            computePrecision = stateSnapshot.computePrecision,
                            beamSize = effectiveBeamSize,
                            vadFilter = stateSnapshot.vadFilter,
                            isStopImmediateRequested = { _uiState.value.stopRequestedLevel >= 2 || !isActive },
                            onLog = ::appendLog,
                            onSegmentGenerated = { segment, progressPercent ->
                                _uiState.update { s ->
                                    s.copy(
                                        currentFileProgress = progressPercent,
                                        spectrogram = s.spectrogram.copy(
                                            playheadTimeSec = segment.endSec,
                                            showPlayhead = true
                                        )
                                    )
                                }
                                updateQueueItem(currentItem.id) { item ->
                                    item.copy(
                                        progressPercent = progressPercent,
                                        statusMessage = "Line ${segment.index} • ${TranscriptFormatter.formatEta(segment.endSec)} / ${TranscriptFormatter.formatEta(decodedPcm.durationSec)}"
                                    )
                                }
                            }
                        )

                        val elapsedSec = ((System.currentTimeMillis() - (activeFileStartTimeMs ?: System.currentTimeMillis())) / 1000.0)
                            .coerceAtLeast(0.1)
                        completedFileTimesSec.add(elapsedSec)
                        val speedup = decodedPcm.durationSec / elapsedSec

                        if (segments.isEmpty()) {
                            appendLog("   🛡️ [${processedCount}/${totalFiles}] No verified vocals/lyrics in '${currentItem.filename}' — skipped saving to prevent imaginary lyric hallucinations.")
                            updateQueueItem(currentItem.id) { item ->
                                item.copy(
                                    status = FileProcessStatus.SKIPPED,
                                    statusMessage = "No verified vocals (0 hallucinations)",
                                    progressPercent = 100f,
                                    durationSec = decodedPcm.durationSec,
                                    processingTimeSec = elapsedSec,
                                    speedupRealtime = speedup,
                                    waveformPeaks = decodedPcm.waveformPeaks
                                )
                            }
                            activeFileStartTimeMs = null
                            _uiState.update {
                                it.copy(
                                    currentFileProgress = 100f,
                                    spectrogram = it.spectrogram.copy(showPlayhead = false),
                                    statusBannerMessage = "🛡️ Anti-Hallucination Guard: Skipped '${currentItem.filename}' (no verified vocals)."
                                )
                            }
                            updateBatchProgressAndEta(processedCount, totalFiles, completedFileTimesSec)
                            continue
                        }

                        appendLog(
                            String.format(
                                Locale.US,
                                "   📊 Summary for %s: Segments=%d, Beam=%d, Dur=%s, Time=%s, Speedup=%.2fx",
                                currentItem.filename,
                                segments.size,
                                effectiveBeamSize,
                                TranscriptFormatter.formatEta(decodedPcm.durationSec),
                                TranscriptFormatter.formatEta(elapsedSec),
                                speedup
                            )
                        )

                        val latestItem = _uiState.value.queueItems.find { it.id == currentItem.id } ?: currentItem
                        val formattedOutput = when (latestItem.mediaKind) {
                            MediaKind.AUDIO -> TranscriptFormatter.segmentsToLrc(
                                segments = segments,
                                metadata = latestItem.metadata,
                                modelName = fullModelTag
                            )
                            MediaKind.VIDEO -> TranscriptFormatter.segmentsToSrt(
                                segments = segments,
                                metadata = latestItem.metadata
                            )
                        }

                        val saveResult = storageEngine.saveTimedOutputInPlace(
                            item = latestItem,
                            content = formattedOutput,
                            overwriteExisting = _uiState.value.overwriteExisting || !latestItem.existingOutputExists,
                            onLog = ::appendLog
                        )

                        saveResult.fold(
                            onSuccess = { savedPath ->
                                updateQueueItem(currentItem.id) { item ->
                                    item.copy(
                                        status = FileProcessStatus.COMPLETED,
                                        statusMessage = "Saved in place: ${item.expectedOutputFilename}",
                                        progressPercent = 100f,
                                        durationSec = decodedPcm.durationSec,
                                        processingTimeSec = elapsedSec,
                                        speedupRealtime = speedup,
                                        existingOutputExists = true,
                                        savedOutputPath = savedPath,
                                        segments = segments,
                                        generatedTranscriptContent = formattedOutput,
                                        waveformPeaks = decodedPcm.waveformPeaks
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        currentFileProgress = 100f,
                                        lastRealtimeSpeedup = speedup,
                                        statusBannerMessage = "✅ Saved in place: $savedPath (${String.format(Locale.US, "%.1fx", speedup)} RT)"
                                    )
                                }
                            },
                            onFailure = { err ->
                                appendLog("   ❌ Error saving ${latestItem.expectedOutputFilename} in place: ${err.localizedMessage ?: err}")
                                updateQueueItem(currentItem.id) { item ->
                                    item.copy(
                                        status = FileProcessStatus.ERROR,
                                        statusMessage = "Save Error: ${err.localizedMessage ?: "Failed"}",
                                        progressPercent = 0f,
                                        segments = segments,
                                        generatedTranscriptContent = formattedOutput,
                                        waveformPeaks = decodedPcm.waveformPeaks
                                    )
                                }
                            }
                        )
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (ie: InterruptedException) {
                        throw ie
                    } catch (fileErr: Throwable) {
                        appendLog("   ❌ Error processing '${currentItem.filename}': ${fileErr.localizedMessage ?: fileErr.javaClass.simpleName}")
                        updateQueueItem(currentItem.id) { item ->
                            item.copy(
                                status = FileProcessStatus.ERROR,
                                statusMessage = "Error: ${fileErr.localizedMessage ?: "Processing failed"}",
                                progressPercent = 0f
                            )
                        }
                        completedFileTimesSec.add(0.1)
                    }

                    activeFileStartTimeMs = null
                    _uiState.update {
                        it.copy(
                            spectrogram = it.spectrogram.copy(showPlayhead = false)
                        )
                    }
                    updateBatchProgressAndEta(processedCount, totalFiles, completedFileTimesSec)
                }

                // Execute audio-to-video metadata synchronization OUTSIDE _uiState.update on Dispatchers.IO
                // so ::appendLog never mutates _uiState inside a StateFlow CAS loop!
                if (_uiState.value.autoSyncAudioMetadataToVideo) {
                    val snapshotQueue = _uiState.value.queueItems
                    val syncedQueue = withContext(Dispatchers.IO) {
                        storageEngine.synchronizeAudioMetadataToVideos(snapshotQueue, ::appendLog)
                    }
                    _uiState.update { state ->
                        state.copy(queueItems = syncedQueue)
                    }
                }

                val completedTotal = _uiState.value.queueItems.count { it.status == FileProcessStatus.COMPLETED }
                val skippedTotal = _uiState.value.queueItems.count { it.status == FileProcessStatus.SKIPPED }
                val errorTotal = _uiState.value.queueItems.count { it.status == FileProcessStatus.ERROR }
                if (_uiState.value.stopRequestedLevel == 0) {
                    appendLog("✅ Batch processing finished: $completedTotal saved in place, $skippedTotal skipped, $errorTotal error(s).")
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "✅ Batch complete • $completedTotal saved in place (.lrc/.srt), $skippedTotal skipped."
                        )
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                appendLog("🛑 Processing cancelled immediately.")
                _uiState.update { state ->
                    val updated = state.queueItems.map { item ->
                        if (item.status == FileProcessStatus.PROCESSING) {
                            item.copy(
                                status = FileProcessStatus.ERROR,
                                statusMessage = "Interrupted by user"
                            )
                        } else item
                    }
                    state.copy(
                        queueItems = updated,
                        statusBannerMessage = "🛑 Processing halted immediately by user."
                    )
                }
            } catch (_: InterruptedException) {
                appendLog("🛑 Processing interrupted immediately.")
                _uiState.update { state ->
                    val updated = state.queueItems.map { item ->
                        if (item.status == FileProcessStatus.PROCESSING) {
                            item.copy(
                                status = FileProcessStatus.ERROR,
                                statusMessage = "Interrupted by user"
                            )
                        } else item
                    }
                    state.copy(
                        queueItems = updated,
                        statusBannerMessage = "🛑 Processing halted immediately by user."
                    )
                }
            } catch (unexpected: Throwable) {
                appendLog("❌ Batch error: ${unexpected.localizedMessage ?: unexpected.javaClass.simpleName}")
                _uiState.update { state ->
                    state.copy(
                        statusBannerMessage = "⚠️ Batch encountered an error: ${unexpected.localizedMessage ?: "Recovered safely"}"
                    )
                }
            } finally {
                activeFileStartTimeMs = null
                elapsedTickerJob?.cancel()
                _uiState.update {
                    it.copy(
                        processingActive = false,
                        stopRequestedLevel = 0,
                        showStopConfirmDialog = false,
                        currentFileElapsedSec = 0.0,
                        spectrogram = it.spectrogram.copy(showPlayhead = false)
                    )
                }
            }
        }
    }

    private fun startElapsedTimerTicker() {
        elapsedTickerJob?.cancel()
        elapsedTickerJob = viewModelScope.launch {
            while (isActive && _uiState.value.processingActive) {
                val startMs = activeFileStartTimeMs
                if (startMs != null) {
                    val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
                    _uiState.update { it.copy(currentFileElapsedSec = elapsed) }
                }
                delay(250)
            }
        }
    }

    private fun updateQueueItem(id: String, transform: (MediaQueueItem) -> MediaQueueItem) {
        _uiState.update { state ->
            val updatedList = state.queueItems.map { item ->
                if (item.id == id) transform(item) else item
            }
            val updatedInspected = if (state.inspectedItem?.id == id) {
                updatedList.find { it.id == id }
            } else {
                state.inspectedItem
            }
            val updatedSyncEditor = if (state.syncEditorItem?.id == id) {
                updatedList.find { it.id == id }
            } else {
                state.syncEditorItem
            }
            state.copy(
                queueItems = updatedList,
                inspectedItem = updatedInspected,
                syncEditorItem = updatedSyncEditor
            )
        }
    }

    private fun updateBatchProgressAndEta(
        processedCount: Int,
        totalFiles: Int,
        completedFileTimesSec: List<Double>
    ) {
        val batchPct = if (totalFiles > 0) (processedCount.toFloat() / totalFiles.toFloat()) * 100f else 0f
        val validTimes = completedFileTimesSec.filter { it >= 0.0 }
        val etaSec = if (validTimes.isNotEmpty() && totalFiles > processedCount) {
            val avg = validTimes.average()
            avg * (totalFiles - processedCount)
        } else {
            0.0
        }
        _uiState.update {
            it.copy(
                batchProgress = batchPct,
                batchEtaSeconds = etaSec
            )
        }
    }
}
