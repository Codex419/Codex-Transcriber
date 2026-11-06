package com.example.model

import java.util.Locale
import kotlin.math.floor

enum class InputScanMode(val id: String, val label: String) {
    SINGLE_FILE("single", "Single File"),
    BATCH_DIRECTORY("batch", "Batch Directory (incl. Subfolders)")
}

enum class MediaKind(val label: String, val targetExtension: String, val formatBadge: String) {
    AUDIO("Audio", ".lrc", "LRC"),
    VIDEO("Video", ".srt", "SRT")
}

enum class FileProcessStatus(val symbol: String, val label: String) {
    PENDING("⏳", "Pending"),
    PROCESSING("⚙️", "Processing"),
    COMPLETED("✅", "Completed"),
    SKIPPED("⚠️", "Skipped (Exists)"),
    ERROR("❌", "Error")
}

enum class WhisperModelProfile(
    val id: String,
    val displayName: String,
    val parameterCount: String,
    val description: String,
    val relativeSpeedMultiplier: Float
) {
    LARGE_V3_TURBO_NPU(
        id = "large-v3-turbo-npu",
        displayName = "Whisper Large-v3 Turbo (NPU)",
        parameterCount = "809M",
        description = "Snapdragon 8 Elite Gen 5 Hexagon HTP fused graph. Best speed-to-accuracy ratio for multi-folder batching.",
        relativeSpeedMultiplier = 16.4f
    ),
    LARGE_V3_QNN(
        id = "large-v3",
        displayName = "Whisper Large-v3 (QNN)",
        parameterCount = "1.55B",
        description = "Full 32-layer encoder/decoder on Hexagon Tensor Processor. Maximum multilingual & lyric accuracy.",
        relativeSpeedMultiplier = 9.8f
    ),
    LARGE_V2_QNN(
        id = "large-v2",
        displayName = "Whisper Large-v2",
        parameterCount = "1.55B",
        description = "Classic studio reference model with stable word-level timestamp alignment for LRC & SRT.",
        relativeSpeedMultiplier = 9.5f
    ),
    DISTIL_LARGE_V3(
        id = "distil-large-v3",
        displayName = "Distil-Large-v3",
        parameterCount = "756M",
        description = "Distilled 2-decoder-layer architecture optimized for low-latency NPU burst inference.",
        relativeSpeedMultiplier = 19.2f
    ),
    MEDIUM_EN(
        id = "medium.en",
        displayName = "Whisper Medium.en",
        parameterCount = "769M",
        description = "High-precision English-only acoustic model with low hallucination rate on music vocals.",
        relativeSpeedMultiplier = 13.5f
    ),
    SMALL_EN(
        id = "small.en",
        displayName = "Whisper Small.en",
        parameterCount = "244M",
        description = "Lightweight English model with ultra-low thermal footprint for massive directory scans.",
        relativeSpeedMultiplier = 24.0f
    ),
    BASE(
        id = "base",
        displayName = "Whisper Base",
        parameterCount = "74M",
        description = "Compact balanced model for ultra-fast draft captioning on any device.",
        relativeSpeedMultiplier = 32.0f
    )
}

enum class HardwareAccelerator(
    val id: String,
    val shortName: String,
    val fullTitle: String,
    val defaultCompute: ComputePrecision
) {
    HEXAGON_QNN_HTP(
        id = "qnn_htp",
        shortName = "Hexagon NPU (QNN-HTP)",
        fullTitle = "Qualcomm Snapdragon 8 Elite Gen 5 • Hexagon NPU (QNN HTP v79)",
        defaultCompute = ComputePrecision.INT8_FLOAT16
    ),
    ANDROID_NNAPI_LITERT(
        id = "nnapi_litert",
        shortName = "LiteRT NNAPI",
        fullTitle = "Android LiteRT CompiledModel Hardware Delegate (NPU / GPU)",
        defaultCompute = ComputePrecision.FLOAT16
    ),
    CPU_ARM_SVE2(
        id = "cpu_sve2",
        shortName = "CPU (ARMv9 NEON)",
        fullTitle = "Oryon / ARMv9 Multi-Core CPU with SVE2 Vector Extensions",
        defaultCompute = ComputePrecision.INT8
    )
}

enum class ComputePrecision(
    val id: String,
    val label: String,
    val supportedAccelerators: Set<String>
) {
    AUTO("auto", "auto (Hardware Optimal)", setOf("qnn_htp", "nnapi_litert", "cpu_sve2")),
    INT4_AWQ("int4_awq", "int4_awq (Hexagon Native)", setOf("qnn_htp")),
    INT8_FLOAT16("int8_float16", "int8_float16 (Mixed NPU)", setOf("qnn_htp", "nnapi_litert")),
    FLOAT16("float16", "float16 (FP16 Tensor)", setOf("qnn_htp", "nnapi_litert")),
    BFLOAT16("bfloat16", "bfloat16 (Brain Float)", setOf("qnn_htp", "nnapi_litert")),
    INT8("int8", "int8 (Quantized Fast)", setOf("qnn_htp", "nnapi_litert", "cpu_sve2")),
    INT16("int16", "int16 (High Precision Int)", setOf("qnn_htp", "cpu_sve2")),
    FLOAT32("float32", "float32 (Full Precision)", setOf("cpu_sve2"))
}

enum class QuantizationSuffix(val suffix: String, val displayLabel: String) {
    NONE("", "Default (None)"),
    INT8("int8", "int8"),
    INT8_FLOAT16("int8_float16", "int8_float16"),
    INT8_BFLOAT16("int8_bfloat16", "int8_bfloat16"),
    INT16("int16", "int16"),
    FLOAT16("float16", "float16")
}

enum class QueueFilter(val label: String) {
    ALL("All Files"),
    AUDIO_LRC("Audio (.lrc)"),
    VIDEO_SRT("Video (.srt)"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    SKIPPED("Skipped")
}

data class MediaMetadataInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val author: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val durationMs: Long = 0L,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 44100,
    val embeddedLyrics: String = "",
    val syncedFromAudioSource: String? = null,
    val syncedToVideoTarget: String? = null
)

data class TranscriptSegment(
    val index: Int,
    val startSec: Double,
    val endSec: Double,
    val text: String
)

data class MediaQueueItem(
    val id: String,
    val filename: String,
    val baseName: String,
    val extension: String,
    val relativePath: String,
    val subfolderName: String,
    val mediaKind: MediaKind,
    val localFilePath: String? = null,
    val documentUriString: String? = null,
    val parentTreeUriString: String? = null,
    val status: FileProcessStatus = FileProcessStatus.PENDING,
    val statusMessage: String = "Queued",
    val progressPercent: Float = 0f,
    val durationSec: Double = 0.0,
    val processingTimeSec: Double = 0.0,
    val speedupRealtime: Double = 0.0,
    val existingOutputExists: Boolean = false,
    val expectedOutputFilename: String = "$baseName${mediaKind.targetExtension}",
    val savedOutputPath: String? = null,
    val metadata: MediaMetadataInfo = MediaMetadataInfo(),
    val segments: List<TranscriptSegment> = emptyList(),
    val generatedTranscriptContent: String = "",
    val waveformPeaks: List<Float> = emptyList()
)

data class SpectrogramSnapshot(
    val fileName: String = "",
    val sampleRate: Int = 16000,
    val durationSec: Double = 0.0,
    val hopLength: Int = 512,
    val nFft: Int = 2048,
    val nMels: Int = 128,
    val columns: List<FloatArray> = emptyList(), // Each FloatArray has size nMels (0f..1f dB scaled)
    val playheadTimeSec: Double = 0.0,
    val showPlayhead: Boolean = false,
    val isComputing: Boolean = false
)

object SupportedMediaExtensions {
    val AUDIO_EXTENSIONS = setOf("wav", "mp3", "flac", "aac", "m4a", "ogg")
    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv")
    val ALL_EXTENSIONS = AUDIO_EXTENSIONS + VIDEO_EXTENSIONS

    fun classifyExtension(ext: String): MediaKind? {
        val clean = ext.lowercase(Locale.ROOT).removePrefix(".")
        return when {
            clean in AUDIO_EXTENSIONS -> MediaKind.AUDIO
            clean in VIDEO_EXTENSIONS -> MediaKind.VIDEO
            else -> null
        }
    }
}

object TranscriptFormatter {
    /**
     * Formats timestamp in SRT/VTT format: HH:MM:SS,mmm or HH:MM:SS.mmm
     */
    fun formatTimestamp(
        seconds: Double,
        alwaysIncludeHours: Boolean = true,
        decimalMarker: Char = ','
    ): String {
        val safeSec = seconds.coerceAtLeast(0.0)
        val totalMs = kotlin.math.round(safeSec * 1000.0).toLong()
        val hours = totalMs / 3_600_000L
        val remAfterHours = totalMs % 3_600_000L
        val minutes = remAfterHours / 60_000L
        val remAfterMinutes = remAfterHours % 60_000L
        val secs = remAfterMinutes / 1000L
        val millis = remAfterMinutes % 1000L

        return if (alwaysIncludeHours || hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d%c%03d", hours, minutes, secs, decimalMarker, millis)
        } else {
            String.format(Locale.US, "%02d:%02d%c%03d", minutes, secs, decimalMarker, millis)
        }
    }

    /**
     * Formats timestamp in compact MM:SS.cc centisecond LRC style for sync verification UI.
     */
    fun formatShortTime(seconds: Double): String {
        val safeSec = seconds.coerceAtLeast(0.0)
        val totalCs = kotlin.math.round(safeSec * 100.0).toLong()
        val minutes = (totalCs / 6000L).toInt()
        val secs = ((totalCs % 6000L) / 100L).toInt()
        val cs = (totalCs % 100L).toInt()
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, secs, cs)
    }

    /**
     * Generates .lrc content for audio files, automatically embedding synchronized ID3 metadata tags
     * at the top of the file followed by [MM:SS.xx] timed lines.
     */
    fun segmentsToLrc(
        segments: List<TranscriptSegment>,
        metadata: MediaMetadataInfo,
        modelName: String
    ): String {
        val sb = StringBuilder()
        if (metadata.title.isNotBlank()) sb.appendLine("[ti:${metadata.title}]")
        if (metadata.artist.isNotBlank()) sb.appendLine("[ar:${metadata.artist}]")
        if (metadata.album.isNotBlank()) sb.appendLine("[al:${metadata.album}]")
        if (metadata.author.isNotBlank()) sb.appendLine("[au:${metadata.author}]")
        if (metadata.durationMs > 0) {
            val totalCs = kotlin.math.round(metadata.durationMs / 10.0).toLong()
            val mm = (totalCs / 6000L).toInt()
            val ss = ((totalCs % 6000L) / 100L).toInt()
            val cs = (totalCs % 100L).toInt()
            sb.appendLine(String.format(Locale.US, "[length:%02d:%02d.%02d]", mm, ss, cs))
        }
        sb.appendLine()

        for (segment in segments) {
            val totalCs = kotlin.math.round(segment.startSec.coerceAtLeast(0.0) * 100.0).toLong()
            val minutes = (totalCs / 6000L).toInt()
            val seconds = ((totalCs % 6000L) / 100L).toInt()
            val centiseconds = (totalCs % 100L).toInt()
            val lrcTimestamp = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centiseconds)
            val cleanText = segment.text.trim()
            if (cleanText.isNotEmpty()) {
                sb.appendLine("$lrcTimestamp$cleanText")
            }
        }
        return sb.toString()
    }

    /**
     * Generates .srt content for video files.
     */
    fun segmentsToSrt(
        segments: List<TranscriptSegment>,
        metadata: MediaMetadataInfo
    ): String {
        val sb = StringBuilder()
        for ((idx, segment) in segments.withIndex()) {
            val startTime = formatTimestamp(segment.startSec, alwaysIncludeHours = true, decimalMarker = ',')
            val endTime = formatTimestamp(segment.endSec, alwaysIncludeHours = true, decimalMarker = ',')
            val cleanText = segment.text.trim().replace("-->", "->")
            sb.appendLine(idx + 1)
            sb.appendLine("$startTime --> $endTime")
            sb.appendLine(cleanText)
            sb.appendLine()
        }
        return sb.toString()
    }

    /**
     * Parses existing .lrc or .srt text content into a list of timed [TranscriptSegment]s
     * so finalized files can be loaded into the Waveform & Lyric Sync Studio for playback and editing.
     */
    fun parseTranscriptToSegments(
        content: String,
        mediaKind: MediaKind,
        totalDurationSec: Double
    ): List<TranscriptSegment> {
        if (content.isBlank()) return emptyList()
        return if (mediaKind == MediaKind.AUDIO || content.contains(Regex("""\[\d{2}:\d{2}\.\d{2,3}]"""))) {
            parseLrcToSegments(content, totalDurationSec)
        } else {
            parseSrtToSegments(content)
        }
    }

    private fun parseLrcToSegments(content: String, totalDurationSec: Double): List<TranscriptSegment> {
        val regex = Regex("""^\[(?:(\d+):)?(\d+):(\d{2})(?:[.,](\d{1,3}))?](.*)$""")
        val rawStarts = mutableListOf<Pair<Double, String>>()
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            val match = regex.find(line) ?: continue
            val hours = match.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
            val mm = match.groupValues[2].toLongOrNull() ?: continue
            val ss = match.groupValues[3].toLongOrNull() ?: continue
            val fracStr = match.groupValues[4]
            val fracSec = when (fracStr.length) {
                0 -> 0.0
                1 -> (fracStr.toIntOrNull() ?: 0) / 10.0
                2 -> (fracStr.toIntOrNull() ?: 0) / 100.0
                else -> (fracStr.take(3).toIntOrNull() ?: 0) / 1000.0
            }
            val lyricText = match.groupValues[5].trim()
            if (lyricText.isEmpty()) continue
            val startSec = hours * 3600.0 + mm * 60.0 + ss + fracSec
            rawStarts.add(startSec to lyricText)
        }
        if (rawStarts.isEmpty()) return emptyList()
        val sorted = rawStarts.sortedBy { it.first }
        return sorted.mapIndexed { idx, (startSec, text) ->
            val nextStart = sorted.getOrNull(idx + 1)?.first
            val endSec = when {
                nextStart != null -> (nextStart - 0.12).coerceAtLeast(startSec + 0.5)
                totalDurationSec > startSec -> totalDurationSec.coerceAtMost(startSec + 5.0)
                else -> startSec + 3.5
            }
            TranscriptSegment(
                index = idx + 1,
                startSec = startSec,
                endSec = endSec,
                text = text
            )
        }
    }

    private fun parseSrtToSegments(content: String): List<TranscriptSegment> {
        val blocks = content.trim().split(Regex("""\r?\n\r?\n+"""))
        val timeRegex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
        val segments = mutableListOf<TranscriptSegment>()
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val timeLineIdx = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeLineIdx == -1) continue
            val match = timeRegex.find(lines[timeLineIdx]) ?: continue
            val startSec = match.groupValues[1].toInt() * 3600.0 +
                match.groupValues[2].toInt() * 60.0 +
                match.groupValues[3].toInt() +
                match.groupValues[4].toInt() / 1000.0
            val endSec = match.groupValues[5].toInt() * 3600.0 +
                match.groupValues[6].toInt() * 60.0 +
                match.groupValues[7].toInt() +
                match.groupValues[8].toInt() / 1000.0
            val text = lines.drop(timeLineIdx + 1).joinToString(" ").trim()
            if (text.isNotEmpty()) {
                segments.add(
                    TranscriptSegment(
                        index = segments.size + 1,
                        startSec = startSec,
                        endSec = endSec.coerceAtLeast(startSec + 0.3),
                        text = text
                    )
                )
            }
        }
        return segments
    }

    fun formatEta(seconds: Double): String {
        if (seconds < 0 || !seconds.isFinite()) return "--:--"
        val total = seconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, secs)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, secs)
            else -> String.format(Locale.US, "%ds", secs)
        }
    }
}
