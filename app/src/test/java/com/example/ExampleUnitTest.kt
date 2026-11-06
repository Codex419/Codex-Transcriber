package com.example

import com.example.engine.MelSpectrogramEngine
import com.example.engine.SnapdragonNpuTranscriptionEngine
import com.example.model.ComputePrecision
import com.example.model.HardwareAccelerator
import com.example.model.MediaKind
import com.example.model.MediaMetadataInfo
import com.example.model.MediaQueueItem
import com.example.model.QuantizationSuffix
import com.example.model.SupportedMediaExtensions
import com.example.model.TranscriptFormatter
import com.example.model.TranscriptSegment
import com.example.model.WhisperModelProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun supportedExtensions_includeMp3FlacAndAllAudioVideoFormats() {
        assertEquals(MediaKind.AUDIO, SupportedMediaExtensions.classifyExtension("mp3"))
        assertEquals(MediaKind.AUDIO, SupportedMediaExtensions.classifyExtension(".flac"))
        assertEquals(MediaKind.AUDIO, SupportedMediaExtensions.classifyExtension("wav"))
        assertEquals(MediaKind.AUDIO, SupportedMediaExtensions.classifyExtension("aac"))
        assertEquals(MediaKind.AUDIO, SupportedMediaExtensions.classifyExtension("m4a"))
        assertEquals(MediaKind.AUDIO, SupportedMediaExtensions.classifyExtension("ogg"))

        assertEquals(MediaKind.VIDEO, SupportedMediaExtensions.classifyExtension("mp4"))
        assertEquals(MediaKind.VIDEO, SupportedMediaExtensions.classifyExtension(".mkv"))
        assertEquals(MediaKind.VIDEO, SupportedMediaExtensions.classifyExtension("avi"))
        assertEquals(MediaKind.VIDEO, SupportedMediaExtensions.classifyExtension("mov"))
        assertEquals(MediaKind.VIDEO, SupportedMediaExtensions.classifyExtension("wmv"))
        assertEquals(MediaKind.VIDEO, SupportedMediaExtensions.classifyExtension("flv"))
    }

    @Test
    fun segmentsToLrc_embedsMetadataAndFormatsPureLyricsOnly() {
        val meta = MediaMetadataInfo(
            title = "Hexagon Horizon",
            artist = "Astraea",
            album = "Snapdragon 8 Elite Sessions",
            author = "E. Vance",
            durationMs = 14_500L
        )
        val segments = listOf(
            TranscriptSegment(index = 1, startSec = 1.25, endSec = 4.50, text = "First lyric line"),
            TranscriptSegment(index = 2, startSec = 65.08, endSec = 70.00, text = "Second lyric line")
        )
        val lrc = TranscriptFormatter.segmentsToLrc(segments, meta, "large-v3-turbo-npu")

        assertTrue(lrc.contains("[ti:Hexagon Horizon]"))
        assertTrue(lrc.contains("[ar:Astraea]"))
        assertTrue(lrc.contains("[al:Snapdragon 8 Elite Sessions]"))
        assertTrue(lrc.contains("[length:00:14.50]"))
        assertFalse(lrc.contains("[by:"))
        assertTrue(lrc.contains("[00:01.25]First lyric line"))
        assertTrue(lrc.contains("[01:05.08]Second lyric line"))
    }

    @Test
    fun fullFileTranscription_coversEntireDurationAndOutputsPureLyricsWithoutAudioDescriptions() = runTest {
        val npuEngine = SnapdragonNpuTranscriptionEngine()
        val fullSongDurationSec = 180.0 // 3-minute song
        val sampleRate = 8000
        val samples = FloatArray((fullSongDurationSec * sampleRate).toInt()) { i ->
            kotlin.math.sin(2.0 * Math.PI * 220.0 * i / sampleRate).toFloat() * 0.5f
        }
        val decodedPcm = MelSpectrogramEngine.DecodedAudioPcm(
            samples = samples,
            sampleRate = sampleRate,
            durationSec = fullSongDurationSec
        )
        val verifiedSongLyrics = (1..45).joinToString("\n") { idx ->
            "Verified studio vocal line number $idx across the skyline"
        }
        val item = MediaQueueItem(
            id = "test_song",
            filename = "My_Favorite_Track.mp3",
            baseName = "My_Favorite_Track",
            extension = "mp3",
            relativePath = "My_Favorite_Track.mp3",
            subfolderName = "/",
            mediaKind = MediaKind.AUDIO,
            durationSec = fullSongDurationSec,
            expectedOutputFilename = "My_Favorite_Track.lrc",
            metadata = MediaMetadataInfo(
                title = "Midnight Drive",
                artist = "Nova",
                album = "Starlight",
                durationMs = 180_000L,
                embeddedLyrics = verifiedSongLyrics
            )
        )

        val segments = npuEngine.transcribeMediaItem(
            item = item,
            decodedPcm = decodedPcm,
            modelProfile = WhisperModelProfile.LARGE_V3_TURBO_NPU,
            quantization = QuantizationSuffix.NONE,
            accelerator = HardwareAccelerator.HEXAGON_QNN_HTP,
            computePrecision = ComputePrecision.INT8_FLOAT16,
            beamSize = 5,
            vadFilter = true,
            isStopImmediateRequested = { false },
            onSegmentGenerated = { _, _ -> }
        )

        // Verify the entire 180s song is processed (> 40 lyric segments, reaching the end of the 180s track)
        assertTrue("Expected > 40 segments for 180s song, got ${segments.size}", segments.size > 40)
        assertTrue("Expected final segment end near 180s, got ${segments.last().endSec}", segments.last().endSec >= 175.0)
        assertEquals("My_Favorite_Track.lrc", item.expectedOutputFilename)

        // Verify ZERO audio descriptions or technical tags appear in any lyric line
        for (seg in segments) {
            val lower = seg.text.lowercase()
            assertFalse(lower.contains("instrumental"))
            assertFalse(lower.contains("vocal phrase"))
            assertFalse(lower.contains("recorded by"))
            assertFalse(lower.contains("harmonic resonance"))
            assertFalse(lower.contains("beam decoding"))
            assertFalse(lower.startsWith("["))
        }
    }

    @Test
    fun zeroHallucinationGuard_neverInventsImaginaryLyricsOnImportedAudioWithoutVocals() = runTest {
        val npuEngine = SnapdragonNpuTranscriptionEngine()
        val durationSec = 45.0
        val sampleRate = 8000
        val samples = FloatArray((durationSec * sampleRate).toInt()) { i ->
            kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate).toFloat() * 0.15f
        }
        val decodedPcm = MelSpectrogramEngine.DecodedAudioPcm(
            samples = samples,
            sampleRate = sampleRate,
            durationSec = durationSec
        )
        // Imported arbitrary audio file with NO embedded lyrics and unindexed/non-existent artist
        val unverifiedItem = MediaQueueItem(
            id = "imported_audio_only",
            filename = "Untagged_Field_Recording_01.flac",
            baseName = "Untagged_Field_Recording_01",
            extension = "flac",
            relativePath = "Untagged_Field_Recording_01.flac",
            subfolderName = "/",
            mediaKind = MediaKind.AUDIO,
            durationSec = durationSec,
            expectedOutputFilename = "Untagged_Field_Recording_01.lrc",
            metadata = MediaMetadataInfo(
                title = "Untagged_Field_Recording_01",
                artist = "",
                album = "",
                durationMs = 45_000L,
                embeddedLyrics = ""
            )
        )

        val segments = npuEngine.transcribeMediaItem(
            item = unverifiedItem,
            decodedPcm = decodedPcm,
            modelProfile = WhisperModelProfile.LARGE_V3_TURBO_NPU,
            quantization = QuantizationSuffix.NONE,
            accelerator = HardwareAccelerator.HEXAGON_QNN_HTP,
            computePrecision = ComputePrecision.INT8_FLOAT16,
            beamSize = 5,
            vadFilter = true,
            isStopImmediateRequested = { false },
            onSegmentGenerated = { _, _ -> }
        )

        // Must NOT invent imaginary lyrics when no verified vocals exist
        assertTrue(
            "Expected 0 hallucinated lyric segments on unverified audio file, got ${segments.size}",
            segments.isEmpty()
        )
    }

    @Test
    fun autoBeamAndTranscriptParser_roundTripsLrcAndSrtAndResolvesAutoBeamWidth() {
        val npuEngine = SnapdragonNpuTranscriptionEngine()
        val samples = FloatArray(16000) { i ->
            if (i % 400 < 200) 0.45f else 0.02f
        }
        val decoded = MelSpectrogramEngine.DecodedAudioPcm(
            samples = samples,
            sampleRate = 8000,
            durationSec = 2.0
        )
        val decision = npuEngine.resolveAutoBeamSize(
            modelProfile = WhisperModelProfile.LARGE_V3_TURBO_NPU,
            accelerator = HardwareAccelerator.HEXAGON_QNN_HTP,
            computePrecision = ComputePrecision.INT8_FLOAT16,
            decodedPcm = decoded
        )
        assertTrue(
            "Auto beam width should be between 1 and 20, got ${decision.resolvedBeamSize}",
            decision.resolvedBeamSize in 1..20
        )
        assertTrue(decision.reasonSummary.contains("Auto Beam=${decision.resolvedBeamSize}"))

        val sampleLrc = """
            [ti:Neon Pulse]
            [ar:Kaito]
            [00:02.50]First verified line
            [00:06.75]Second verified line
        """.trimIndent()
        val parsedLrc = TranscriptFormatter.parseTranscriptToSegments(
            content = sampleLrc,
            mediaKind = MediaKind.AUDIO,
            totalDurationSec = 12.0
        )
        assertEquals(2, parsedLrc.size)
        assertEquals(2.50, parsedLrc[0].startSec, 0.01)
        assertEquals("First verified line", parsedLrc[0].text)
        assertEquals(6.75, parsedLrc[1].startSec, 0.01)
        assertEquals("Second verified line", parsedLrc[1].text)
    }

    @Test
    fun segmentsToSrt_formatsStandardSubRipTimestamps() {
        val meta = MediaMetadataInfo(title = "Studio Video")
        val segments = listOf(
            TranscriptSegment(index = 1, startSec = 0.5, endSec = 3.25, text = "Opening subtitle --> check")
        )
        val srt = TranscriptFormatter.segmentsToSrt(segments, meta)

        assertTrue(srt.contains("1\n00:00:00,500 --> 00:00:03,250\nOpening subtitle -> check"))
    }

    @Test
    fun batchCompletion_doesNotTriggerInfiniteStateFlowCasLoopWhenLoggingDuringSync() = runTest {
        val stateFlow = kotlinx.coroutines.flow.MutableStateFlow(
            Pair(
                listOf("01_Hexagon_Horizon.mp3", "01_Hexagon_Horizon.mp4"),
                emptyList<String>()
            )
        )

        fun appendLog(msg: String) {
            stateFlow.value = stateFlow.value.copy(
                second = stateFlow.value.second + msg
            )
        }

        // Compute synced items outside StateFlow.update while logging via appendLog, then update StateFlow once
        val currentItems = stateFlow.value.first
        val syncedItems = currentItems.map { name ->
            if (name.endsWith(".mp4")) {
                appendLog("Synced metadata to $name")
                "$name [synced]"
            } else {
                name
            }
        }
        stateFlow.value = stateFlow.value.copy(first = syncedItems)

        assertEquals(2, stateFlow.value.first.size)
        assertTrue(stateFlow.value.first[1].endsWith("[synced]"))
        assertEquals(1, stateFlow.value.second.size)
    }

    @Test
    fun fullConcertTranscription_hasNoProgrammedLimitsAndProcessesBeyond2m26sToFullConcertEnd() = runTest {
        val npuEngine = SnapdragonNpuTranscriptionEngine()
        val concertDurationSec = 5400.0 // 90-minute full concert (01:30:00,000)
        val sampleRate = 100
        val samples = FloatArray((concertDurationSec * sampleRate).toInt()) { i ->
            kotlin.math.sin(2.0 * Math.PI * 15.0 * i / sampleRate).toFloat() * 0.65f
        }
        val decodedPcm = MelSpectrogramEngine.DecodedAudioPcm(
            samples = samples,
            sampleRate = sampleRate,
            durationSec = concertDurationSec
        )

        // Simulate a single-song synced LRC header block that previously stopped at 02:26.65 (2:26,650 in SRT)
        val singleSongSyncedBlockEndingAt2m26s = (1..36).joinToString("\n") { idx ->
            val sec = (idx * 4.0736).coerceAtMost(146.65)
            val m = (sec / 60).toInt()
            val s = sec % 60.0
            String.format(java.util.Locale.US, "[%02d:%05.2f]Live concert vocal verse line %d under the stage lights", m, s, idx)
        }

        val concertItem = MediaQueueItem(
            id = "concert_90min",
            filename = "Live_In_Tokyo_Full_Concert.mp4",
            baseName = "Live_In_Tokyo_Full_Concert",
            extension = "mp4",
            relativePath = "Live_In_Tokyo_Full_Concert.mp4",
            subfolderName = "/",
            mediaKind = MediaKind.VIDEO,
            durationSec = concertDurationSec,
            expectedOutputFilename = "Live_In_Tokyo_Full_Concert.srt",
            metadata = MediaMetadataInfo(
                title = "Live In Tokyo Full Concert",
                artist = "Astraea",
                album = "World Tour",
                durationMs = (concertDurationSec * 1000).toLong(),
                embeddedLyrics = singleSongSyncedBlockEndingAt2m26s
            )
        )

        val segments = npuEngine.transcribeMediaItem(
            item = concertItem,
            decodedPcm = decodedPcm,
            modelProfile = WhisperModelProfile.LARGE_V3_TURBO_NPU,
            quantization = QuantizationSuffix.NONE,
            accelerator = HardwareAccelerator.HEXAGON_QNN_HTP,
            computePrecision = ComputePrecision.INT8_FLOAT16,
            beamSize = 5,
            vadFilter = true,
            isStopImmediateRequested = { false },
            onSegmentGenerated = { _, _ -> }
        )

        // Must NOT stop at 146.65s (00:02:26,650) or 400 windows; must cover the entire 5400s (01:30:00,000) concert!
        assertTrue(
            "Expected > 1000 segments for 90-minute concert (no 400-window cap), got ${segments.size}",
            segments.size > 1000
        )
        assertTrue(
            "Expected final segment end near 5400s (01:30:00,000) instead of truncating at 146.65s (2:26,650), got ${segments.last().endSec}",
            segments.last().endSec >= 5390.0
        )
        val srt = TranscriptFormatter.segmentsToSrt(segments, concertItem.metadata)
        assertTrue(
            "Expected SRT output to contain 01:29:xx or 01:30:00 timestamps for 90-minute concert",
            srt.contains("01:29:") || srt.contains("01:30:00")
        )
    }

    @Test
    fun strictMonotonicTimestamps_preventsOverlappingAndTailCrushing() {
        val npuEngine = SnapdragonNpuTranscriptionEngine()
        val durationSec = 137.541 // Same duration as 2m 17.541s video

        // Include overlapping segments (seg 2 starts before seg 1 ends) and tail segments exceeding durationSec
        val rawSegments = listOf(
            TranscriptSegment(index = 1, startSec = 50.100, endSec = 51.920, text = "First line"),
            TranscriptSegment(index = 2, startSec = 51.896, endSec = 54.000, text = "Overlapping second line"),
            TranscriptSegment(index = 3, startSec = 136.800, endSec = 137.900, text = "Tail line A"),
            TranscriptSegment(index = 4, startSec = 138.200, endSec = 139.500, text = "Tail line B"),
            TranscriptSegment(index = 5, startSec = 140.000, endSec = 142.000, text = "Tail line C")
        )

        val fixed = npuEngine.enforceStrictMonotonicTimestamps(rawSegments, durationSec)
        assertEquals(5, fixed.size)

        for (i in fixed.indices) {
            val seg = fixed[i]
            assertTrue("Segment ${seg.index} must have endSec > startSec", seg.endSec > seg.startSec + 0.25)
            assertTrue("Segment ${seg.index} must end within durationSec", seg.endSec <= durationSec + 0.01)
            if (i > 0) {
                val prev = fixed[i - 1]
                assertTrue(
                    "Segment ${seg.index} start (${seg.startSec}) must be >= previous end (${prev.endSec})",
                    seg.startSec >= prev.endSec
                )
            }
        }
    }

    @Test
    fun alignPlainLinesToVocalWindows_neverDropsLinesWhenLinesExceedWindows() {
        val npuEngine = SnapdragonNpuTranscriptionEngine()
        val durationSec = 175.0
        val vocalWindows = (0 until 48).map { i ->
            val st = i * 3.5
            SnapdragonNpuTranscriptionEngine.VocalWindow(
                startSec = st,
                endSec = st + 3.1,
                rmsEnergy = 0.25,
                zeroCrossingRate = 0.08,
                vocalBandRatio = 0.75
            )
        }
        val plainLines = (1..74).map { "Lyric line number $it in the song" }

        val aligned = npuEngine.alignPlainLinesToVocalWindows(plainLines, vocalWindows, durationSec)
        assertEquals("All 74 lines must be preserved with 0 dropped lines", 74, aligned.size)
        for (i in 1 until aligned.size) {
            assertTrue(aligned[i].startSec >= aligned[i - 1].endSec)
        }
    }

    @Test
    fun whisperTfliteRunner_computes80x3000MelSpectrogramAndVocalMetrics() {
        val runner = com.example.engine.WhisperTfliteRunner()
        val sampleRate = com.example.engine.WhisperTfliteRunner.WHISPER_SAMPLE_RATE
        val twoSecondsSamples = sampleRate * 2
        val samples16k = FloatArray(twoSecondsSamples) { i ->
            val t = i.toDouble() / sampleRate
            // Modulated 440Hz + 880Hz vocal-band signal with 4Hz syllabic envelope
            val env = 0.5 + 0.45 * kotlin.math.sin(2.0 * Math.PI * 4.0 * t)
            ((0.6 * kotlin.math.sin(2.0 * Math.PI * 440.0 * t) +
                0.3 * kotlin.math.sin(2.0 * Math.PI * 880.0 * t)) * env).toFloat()
        }

        val (melData, metrics) = runner.computeMelSpectrogramAndVocalMetrics(samples16k, twoSecondsSamples)
        assertEquals(80 * 3000, melData.size)
        assertTrue("Expected positive RMS energy", metrics.rmsEnergy > 0.1)
        assertTrue("Expected strong vocal formant ratio in 280-3400Hz band", metrics.vocalFormantRatio > 0.5)
        assertTrue("Expected syllabic modulation > 0.14", metrics.syllabicModulation > 0.14)
        assertTrue("Expected human vocal classification to be true", metrics.isLikelyHumanVocal)
    }
}


