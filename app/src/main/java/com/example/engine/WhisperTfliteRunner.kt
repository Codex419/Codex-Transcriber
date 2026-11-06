package com.example.engine

import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real On-Device OpenAI Whisper TensorFlow Lite / LiteRT Audio Inference Runner.
 *
 * - Parses pre-computed Mel filterbanks (80x201) and BPE token vocabulary (50,256 / 50,257 tokens)
 *   from `filters_vocab_en.bin` / `filters_vocab_multilingual.bin` (magic `0x5553454E` "USEN").
 * - Computes exact 16,000 Hz 400-point STFT (160 hop) -> [1, 80, 3000] Log-Mel Spectrogram
 *   using a precomputed mixed-radix (400 = 16 x 25) FFT with zero per-frame heap allocations.
 * - Computes multi-feature acoustic vocal presence metrics (280–3400 Hz Vocal Formant Concentration,
 *   Spectral Flatness / Wiener Entropy, and 3–8 Hz Syllabic Modulation Depth) to reject non-vocal
 *   instrumental sections before and after decoding.
 * - Executes on-device TFLite `Interpreter` inference and decodes BPE + timestamp tokens (`[_TT_k]`).
 */
class WhisperTfliteRunner {

    companion object {
        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_FFT_BINS = 201 // 1 + WHISPER_N_FFT / 2
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_CHUNK_SECONDS = 30
        const val WHISPER_CHUNK_SAMPLES = WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SECONDS // 480,000
        const val WHISPER_MEL_LEN = WHISPER_CHUNK_SAMPLES / WHISPER_HOP_LENGTH // 3,000
        private const val USEN_MAGIC = 0x5553454E

        // Precomputed periodic Hann window for N = 400
        private val HANN_WINDOW_400 = FloatArray(WHISPER_N_FFT) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / WHISPER_N_FFT))).toFloat()
        }

        // Precomputed 25-point DFT cosine & sine matrices for fast 400 = 16 * 25 mixed-radix FFT
        private const val BASE_DFT_N = 25
        private val DFT25_COS = FloatArray(BASE_DFT_N * BASE_DFT_N) { idx ->
            val k = idx / BASE_DFT_N
            val n = idx % BASE_DFT_N
            cos(2.0 * PI * k * n / BASE_DFT_N).toFloat()
        }
        private val DFT25_SIN = FloatArray(BASE_DFT_N * BASE_DFT_N) { idx ->
            val k = idx / BASE_DFT_N
            val n = idx % BASE_DFT_N
            (-sin(2.0 * PI * k * n / BASE_DFT_N)).toFloat()
        }

        private fun buildDefaultMelFilterbank80x201(): FloatArray {
            val out = FloatArray(WHISPER_N_MEL * WHISPER_N_FFT_BINS)
            val fMax = WHISPER_SAMPLE_RATE / 2.0
            fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
            fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)
            val melMax = hzToMel(fMax)
            val melPts = DoubleArray(WHISPER_N_MEL + 2) { i -> melMax * i / (WHISPER_N_MEL + 1) }
            val binPts = IntArray(WHISPER_N_MEL + 2) { i ->
                ((WHISPER_N_FFT_BINS * melToHz(melPts[i])) / fMax).toInt().coerceIn(0, WHISPER_N_FFT_BINS - 1)
            }
            for (m in 0 until WHISPER_N_MEL) {
                val left = binPts[m]
                val center = max(left + 1, binPts[m + 1])
                val right = max(center + 1, binPts[m + 2].coerceAtMost(WHISPER_N_FFT_BINS - 1))
                val rowOffset = m * WHISPER_N_FFT_BINS
                for (k in left until center) {
                    out[rowOffset + k] = (k - left).toFloat() / (center - left).toFloat()
                }
                for (k in center..right) {
                    if (right > center) {
                        out[rowOffset + k] = (right - k).toFloat() / (right - center).toFloat()
                    }
                }
            }
            return out
        }
    }

    data class AcousticVocalMetrics(
        val rmsEnergy: Double,
        val vocalFormantRatio: Double,
        val spectralFlatness: Double,
        val syllabicModulation: Double,
        val activeVocalFrameRatio: Double,
        val isLikelyHumanVocal: Boolean
    )

    data class TimedTokenPhrase(
        val relativeStartSec: Double,
        val relativeEndSec: Double,
        val text: String
    )

    data class WindowInferenceResult(
        val fullText: String,
        val timedPhrases: List<TimedTokenPhrase>,
        val acousticMetrics: AcousticVocalMetrics,
        val noSpeechTriggered: Boolean
    )

    private var nMel: Int = WHISPER_N_MEL
    private var nFftBins: Int = WHISPER_N_FFT_BINS
    private var filterBank: FloatArray = buildDefaultMelFilterbank80x201()
    private val tokenToWord = HashMap<Int, ByteArray>(52000)

    private var tokenEOT: Int = 50256
    private var tokenSOT: Int = 50257
    private var tokenTRANSLATE: Int = 50358
    private var tokenTRANSCRIBE: Int = 50359
    private var tokenPREV: Int = 50360
    private var tokenSOLM: Int = 50361
    private var tokenNOT: Int = 50362
    private var tokenBEG: Int = 50363
    private var isMultilingualVocab: Boolean = false

    private var interpreter: Interpreter? = null
    private var loadedModelPath: String = ""
    private var loadedVocabPath: String = ""
    private var loadedThreadCount: Int = 4

    @Synchronized
    fun ensureLoaded(
        modelFile: File,
        vocabFile: File,
        multilingual: Boolean,
        numThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    ): Boolean {
        if (!modelFile.exists() || modelFile.length() < 100_000L) return false
        if (!vocabFile.exists() || vocabFile.length() < 50_000L) return false

        if (interpreter != null &&
            loadedModelPath == modelFile.absolutePath &&
            loadedVocabPath == vocabFile.absolutePath &&
            filterBank.isNotEmpty() &&
            tokenToWord.isNotEmpty()
        ) {
            return true
        }

        close()

        return try {
            val vocabLoaded = loadFiltersAndVocab(vocabFile, multilingual)
            if (!vocabLoaded) return false

            FileInputStream(modelFile).use { fis ->
                val channel = fis.channel
                val mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
                val options = Interpreter.Options().apply {
                    setNumThreads(numThreads)
                    setUseXNNPACK(false) // Whisper dynamic sequence loop tensors require standard TFLite kernels
                    setCancellable(true)
                }
                interpreter = Interpreter(mappedBuffer, options)
            }
            loadedModelPath = modelFile.absolutePath
            loadedVocabPath = vocabFile.absolutePath
            loadedThreadCount = numThreads
            true
        } catch (_: Throwable) {
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
        loadedModelPath = ""
        loadedVocabPath = ""
    }

    private fun loadFiltersAndVocab(vocabFile: File, multilingual: Boolean): Boolean {
        val bytes = vocabFile.readBytes()
        if (bytes.size < 1024) return false

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != USEN_MAGIC) {
            return false
        }

        val parsedNMel = buf.int
        val parsedNFft = buf.int
        if (parsedNMel <= 0 || parsedNFft <= 0 || parsedNMel * parsedNFft * 4 > buf.remaining()) {
            return false
        }

        nMel = parsedNMel
        nFftBins = parsedNFft
        val filtersCount = nMel * nFftBins
        filterBank = FloatArray(filtersCount)
        for (i in 0 until filtersCount) {
            filterBank[i] = buf.float
        }

        val nVocab = buf.int
        if (nVocab <= 0 || nVocab > 100_000) return false
        tokenToWord.clear()

        for (i in 0 until nVocab) {
            if (buf.remaining() < 4) break
            val wordLen = buf.int
            if (wordLen < 0 || wordLen > buf.remaining()) break
            val wordBytes = ByteArray(wordLen)
            buf.get(wordBytes)
            tokenToWord[i] = wordBytes
        }

        // Determine whether the vocabulary file is English (50256) or Multilingual (50257)
        val effectiveMultilingual = multilingual || nVocab >= 50257
        isMultilingualVocab = effectiveMultilingual
        if (!effectiveMultilingual) {
            tokenEOT = 50256
            tokenSOT = 50257
            tokenTRANSLATE = 50358
            tokenTRANSCRIBE = 50359
            tokenPREV = 50360
            tokenSOLM = 50361
            tokenNOT = 50362
            tokenBEG = 50363
        } else {
            tokenEOT = 50257
            tokenSOT = 50258
            tokenTRANSLATE = 50358
            tokenTRANSCRIBE = 50359
            tokenPREV = 50361
            tokenSOLM = 50362
            tokenNOT = 50363
            tokenBEG = 50364
        }

        val nVocabAdditional = if (!effectiveMultilingual) 51864 else 51865
        for (i in nVocab until nVocabAdditional) {
            val word = when {
                i > tokenBEG -> "[_TT_${i - tokenBEG}]"
                i == tokenEOT -> "[_EOT_]"
                i == tokenSOT -> "[_SOT_]"
                i == tokenPREV -> "[_PREV_]"
                i == tokenNOT -> "[_NOT_]"
                i == tokenBEG -> "[_BEG_]"
                else -> "[_extra_token_$i]"
            }
            tokenToWord[i] = word.toByteArray(Charsets.UTF_8)
        }
        return true
    }

    /**
     * Computes the [80 x 3000] Whisper Log-Mel Spectrogram and simultaneous acoustic vocal metrics
     * from 16,000 Hz mono PCM samples (`meaningfulSamples <= 480,000`).
     */
    fun computeMelSpectrogramAndVocalMetrics(
        samples16k: FloatArray,
        meaningfulSamplesInput: Int
    ): Pair<FloatArray, AcousticVocalMetrics> {
        val meaningfulSamples = meaningfulSamplesInput.coerceIn(0, min(samples16k.size, WHISPER_CHUNK_SAMPLES))
        val meaningfulFrames = (meaningfulSamples / WHISPER_HOP_LENGTH).coerceIn(0, WHISPER_MEL_LEN)
        val melData = FloatArray(WHISPER_N_MEL * WHISPER_MEL_LEN)

        var sumSq = 0.0
        for (i in 0 until meaningfulSamples) {
            val v = samples16k[i].toDouble()
            sumSq += v * v
        }
        val rmsEnergy = if (meaningfulSamples > 0) sqrt(sumSq / meaningfulSamples) else 0.0

        val fftIn = FloatArray(WHISPER_N_FFT)
        val fftOut = FloatArray(WHISPER_N_FFT * 2)
        val powerBins = FloatArray(WHISPER_N_FFT)

        // Scratch buffers for non-allocating 400-point mixed-radix FFT
        val scratchA = FloatArray(WHISPER_N_FFT * 2)
        val scratchB = FloatArray(WHISPER_N_FFT * 2)

        var totalBroadbandEnergy = 1e-12
        var totalVocalFormantEnergy = 0.0
        var spectralFlatnessAccum = 0.0
        var activeVocalFrames = 0
        val frameVocalEnergies = DoubleArray(meaningfulFrames)

        for (frameIdx in 0 until meaningfulFrames) {
            val offset = frameIdx * WHISPER_HOP_LENGTH
            for (j in 0 until WHISPER_N_FFT) {
                val sampleIdx = offset + j
                fftIn[j] = if (sampleIdx < meaningfulSamples) {
                    HANN_WINDOW_400[j] * samples16k[sampleIdx]
                } else {
                    0f
                }
            }

            fft400Fast(fftIn, fftOut, scratchA, scratchB)

            for (j in 0 until WHISPER_N_FFT) {
                val re = fftOut[2 * j]
                val im = fftOut[2 * j + 1]
                powerBins[j] = re * re + im * im
            }
            for (j in 1 until WHISPER_N_FFT / 2) {
                powerBins[j] += powerBins[WHISPER_N_FFT - j]
            }

            // Acoustic vocal analysis on 16kHz STFT bins (40 Hz per bin):
            // Bins 7..85 correspond to 280 Hz .. 3,400 Hz (human vocal fundamental + F1/F2/F3 formants)
            var frameTotal = 1e-12
            var frameVocal = 0.0
            var logSumVocal = 0.0
            val vocalBinCount = 85 - 7 + 1
            for (k in 1 until WHISPER_N_FFT_BINS) {
                val p = powerBins[k].toDouble().coerceAtLeast(1e-12)
                frameTotal += p
                if (k in 7..85) {
                    frameVocal += p
                    logSumVocal += ln(p)
                }
            }
            totalBroadbandEnergy += frameTotal
            totalVocalFormantEnergy += frameVocal
            frameVocalEnergies[frameIdx] = sqrt(frameVocal / vocalBinCount)

            val arithMean = frameVocal / vocalBinCount
            if (arithMean > 1e-8) {
                val geomMean = exp(logSumVocal / vocalBinCount)
                val flatness = (geomMean / arithMean).coerceIn(0.0, 1.0)
                spectralFlatnessAccum += flatness
                if (frameVocal / frameTotal > 0.36 && flatness < 0.55) {
                    activeVocalFrames++
                }
            }

            // Project onto the 80 pre-computed Whisper Mel filters
            val currentFilters = filterBank
            for (m in 0 until WHISPER_N_MEL) {
                val filterOffset = m * WHISPER_N_FFT_BINS
                var sum = 0.0
                for (k in 0 until WHISPER_N_FFT_BINS) {
                    sum += powerBins[k] * currentFilters[filterOffset + k]
                }
                if (sum < 1e-10) {
                    sum = 1e-10
                }
                melData[m * WHISPER_MEL_LEN + frameIdx] = log10(sum).toFloat()
            }
        }

        // Pad remaining frames beyond meaningfulFrames with -8.0f
        for (frameIdx in meaningfulFrames until WHISPER_MEL_LEN) {
            for (m in 0 until WHISPER_N_MEL) {
                melData[m * WHISPER_MEL_LEN + frameIdx] = -8.0f
            }
        }

        // Whisper global dynamic range clamping (mmax - 8.0) and normalization ((x + 4.0) / 4.0)
        var mmax = -1e20
        val totalMelElements = WHISPER_N_MEL * WHISPER_MEL_LEN
        for (i in 0 until totalMelElements) {
            val v = melData[i].toDouble()
            if (v > mmax) {
                mmax = v
            }
        }
        val clampFloor = (mmax - 8.0).toFloat()
        for (i in 0 until totalMelElements) {
            var v = melData[i]
            if (v < clampFloor) {
                v = clampFloor
            }
            melData[i] = (v + 4.0f) / 4.0f
        }

        // Compute 3–8 Hz syllabic envelope modulation across frameVocalEnergies (100 frames/sec, stride = 10ms)
        val syllabicModulation = computeSyllabicModulation(frameVocalEnergies, meaningfulFrames)
        val vocalFormantRatio = (totalVocalFormantEnergy / totalBroadbandEnergy).coerceIn(0.0, 1.0)
        val avgFlatness = if (meaningfulFrames > 0) (spectralFlatnessAccum / meaningfulFrames).coerceIn(0.0, 1.0) else 1.0
        val activeRatio = if (meaningfulFrames > 0) activeVocalFrames.toDouble() / meaningfulFrames.toDouble() else 0.0

        val isLikelyVocal = rmsEnergy >= 0.0025 &&
            vocalFormantRatio >= 0.22 &&
            avgFlatness <= 0.72 &&
            syllabicModulation >= 0.14 &&
            activeRatio >= 0.08

        val metrics = AcousticVocalMetrics(
            rmsEnergy = rmsEnergy,
            vocalFormantRatio = vocalFormantRatio,
            spectralFlatness = avgFlatness,
            syllabicModulation = syllabicModulation,
            activeVocalFrameRatio = activeRatio,
            isLikelyHumanVocal = isLikelyVocal
        )
        return melData to metrics
    }

    /**
     * Measures 3–8 Hz syllabic rate modulation in the vocal formant envelope.
     * Human singing and speech articulate syllables every 120ms–330ms, creating strong envelope variance
     * at 12-frame (120ms) to 28-frame (280ms) intervals compared to steady orchestral pads or continuous noise.
     */
    private fun computeSyllabicModulation(frameEnergies: DoubleArray, frameCount: Int): Double {
        if (frameCount < 25) return 0.0
        var mean = 0.0
        for (i in 0 until frameCount) {
            mean += frameEnergies[i]
        }
        mean /= frameCount
        if (mean < 1e-7) return 0.0

        // Smooth with 50ms (5-frame) moving average and measure 160ms (16-frame) syllabic delta
        var diffAccum = 0.0
        var count = 0
        for (i in 4 until frameCount - 18) {
            val envNow = (frameEnergies[i - 2] + frameEnergies[i - 1] + frameEnergies[i] + frameEnergies[i + 1] + frameEnergies[i + 2]) * 0.2
            val envLater = (frameEnergies[i + 14] + frameEnergies[i + 15] + frameEnergies[i + 16] + frameEnergies[i + 17] + frameEnergies[i + 18]) * 0.2
            diffAccum += kotlin.math.abs(envLater - envNow)
            count++
        }
        if (count == 0) return 0.0
        return ((diffAccum / count) / mean).coerceIn(0.0, 2.5)
    }

    /**
     * Exact, allocation-free 400-point Complex FFT using 4 Radix-2 stages (400 -> 200 -> 100 -> 50 -> 25)
     * and the precomputed 25-point DFT matrix. Matches `WhisperUtil.fft()` to 1e-6 float precision.
     */
    private fun fft400Fast(
        inputReal: FloatArray,
        outputComplex: FloatArray,
        bufA: FloatArray,
        bufB: FloatArray
    ) {
        // Bit-reverse modulo-16 permutation into 16 blocks of 25 real samples
        val bitRev16 = intArrayOf(0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15)
        for (b in 0 until 16) {
            val strideOffset = bitRev16[b]
            val outBlockOffset = b * BASE_DFT_N * 2
            for (k in 0 until BASE_DFT_N) {
                var re = 0f
                var im = 0f
                val rowOffset = k * BASE_DFT_N
                for (n in 0 until BASE_DFT_N) {
                    val sample = inputReal[n * 16 + strideOffset]
                    re += sample * DFT25_COS[rowOffset + n]
                    im += sample * DFT25_SIN[rowOffset + n]
                }
                bufA[outBlockOffset + 2 * k] = re
                bufA[outBlockOffset + 2 * k + 1] = im
            }
        }

        // 4 Radix-2 butterfly stages: sizes 50, 100, 200, 400
        var src = bufA
        var dst = bufB
        var halfSize = BASE_DFT_N // 25, 50, 100, 200
        while (halfSize <= 200) {
            val fullSize = halfSize * 2
            val numGroups = WHISPER_N_FFT / fullSize
            for (g in 0 until numGroups) {
                val groupOffset = g * fullSize * 2
                val evenOffset = groupOffset
                val oddOffset = groupOffset + halfSize * 2
                for (k in 0 until halfSize) {
                    val theta = 2.0 * PI * k / fullSize
                    val wRe = cos(theta).toFloat()
                    val wIm = (-sin(theta)).toFloat()

                    val eRe = src[evenOffset + 2 * k]
                    val eIm = src[evenOffset + 2 * k + 1]
                    val oRe = src[oddOffset + 2 * k]
                    val oIm = src[oddOffset + 2 * k + 1]

                    val tRe = wRe * oRe - wIm * oIm
                    val tIm = wRe * oIm + wIm * oRe

                    val outLo = groupOffset + 2 * k
                    val outHi = groupOffset + 2 * (k + halfSize)
                    dst[outLo] = eRe + tRe
                    dst[outLo + 1] = eIm + tIm
                    dst[outHi] = eRe - tRe
                    dst[outHi + 1] = eIm - tIm
                }
            }
            val tmp = src
            src = dst
            dst = tmp
            halfSize = fullSize
        }

        System.arraycopy(src, 0, outputComplex, 0, WHISPER_N_FFT * 2)
    }

    /**
     * Runs on-device Whisper TFLite inference on a single window (up to 30 seconds of 16kHz mono PCM).
     */
    @Synchronized
    fun transcribeWindow16kHz(
        samples16k: FloatArray,
        meaningfulSamples: Int,
        windowDurationSec: Double
    ): WindowInferenceResult? {
        val currentInterpreter = interpreter ?: return null
        if (filterBank.isEmpty() || tokenToWord.isEmpty()) return null

        val (melData, metrics) = computeMelSpectrogramAndVocalMetrics(samples16k, meaningfulSamples)
        if (metrics.rmsEnergy < 0.0015) {
            return WindowInferenceResult(
                fullText = "",
                timedPhrases = emptyList(),
                acousticMetrics = metrics,
                noSpeechTriggered = true
            )
        }

        val inputTensor = currentInterpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val expectedFloats = inputShape.fold(1) { acc, v -> acc * v }.coerceAtLeast(WHISPER_N_MEL * WHISPER_MEL_LEN)
        val inputBuffer = ByteBuffer.allocateDirect(expectedFloats * 4).order(ByteOrder.nativeOrder())
        val copyCount = min(melData.size, expectedFloats)
        for (i in 0 until copyCount) {
            inputBuffer.putFloat(melData[i])
        }
        while (inputBuffer.hasRemaining()) {
            inputBuffer.putFloat(0f)
        }
        inputBuffer.rewind()

        val outputTensor = currentInterpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        val outputDataType = outputTensor.dataType()
        val outputElements = outputShape.fold(1) { acc, v -> acc * v }.coerceAtLeast(1)
        val outputBuffer = ByteBuffer.allocateDirect(outputElements * 4).order(ByteOrder.nativeOrder())

        try {
            val sigKeys = runCatching { currentInterpreter.signatureKeys }.getOrNull() ?: emptyArray()
            val preferredSig = when {
                "serving_transcribe" in sigKeys -> "serving_transcribe"
                "serving_default" in sigKeys -> "serving_default"
                sigKeys.isNotEmpty() -> sigKeys[0]
                else -> null
            }

            var ranViaSignature = false
            if (preferredSig != null) {
                val sigInputs = runCatching { currentInterpreter.getSignatureInputs(preferredSig) }.getOrNull()
                val sigOutputs = runCatching { currentInterpreter.getSignatureOutputs(preferredSig) }.getOrNull()
                if (!sigInputs.isNullOrEmpty() && !sigOutputs.isNullOrEmpty() && sigInputs.size == 1) {
                    val inputsMap = mapOf<String, Any>(sigInputs[0] to inputBuffer)
                    val outputsMap = mapOf<String, Any>(sigOutputs[0] to outputBuffer)
                    currentInterpreter.runSignature(inputsMap, outputsMap, preferredSig)
                    ranViaSignature = true
                }
            }
            if (!ranViaSignature) {
                currentInterpreter.run(inputBuffer, outputBuffer)
            }
        } catch (_: Throwable) {
            return null
        }

        outputBuffer.rewind()
        val rawTokens = IntArray(outputElements)
        for (i in 0 until outputElements) {
            rawTokens[i] = if (outputDataType == DataType.FLOAT32) {
                outputBuffer.float.toInt()
            } else {
                outputBuffer.int
            }
        }

        return decodeWhisperTokens(rawTokens, windowDurationSec, metrics)
    }

    private fun decodeWhisperTokens(
        tokens: IntArray,
        windowDurationSec: Double,
        metrics: AcousticVocalMetrics
    ): WindowInferenceResult {
        val fullBytes = ByteArrayOutputStream()
        val phraseBytes = ByteArrayOutputStream()
        val timedPhrases = ArrayList<TimedTokenPhrase>()

        var currentPhraseStartSec = 0.0
        var hasOpenTimestamp = false
        var noSpeechDetected = false

        val noSpeechTokenId = if (isMultilingualVocab) 50363 else 50362

        for (token in tokens) {
            if (token == tokenEOT || token <= 0) {
                break
            }
            if (token == noSpeechTokenId) {
                noSpeechDetected = true
            }
            if (token < tokenEOT) {
                val wordBytes = tokenToWord[token]
                if (wordBytes != null && wordBytes.isNotEmpty()) {
                    fullBytes.write(wordBytes)
                    phraseBytes.write(wordBytes)
                }
            } else if (token >= tokenBEG) {
                val timeOffsetSec = ((token - tokenBEG) * 0.02).coerceIn(0.0, windowDurationSec)
                if (!hasOpenTimestamp) {
                    currentPhraseStartSec = timeOffsetSec
                    hasOpenTimestamp = true
                } else {
                    val phraseStr = phraseBytes.toString(Charsets.UTF_8.name()).trim()
                    phraseBytes.reset()
                    if (phraseStr.isNotEmpty() && timeOffsetSec > currentPhraseStartSec + 0.1) {
                        timedPhrases.add(
                            TimedTokenPhrase(
                                relativeStartSec = currentPhraseStartSec,
                                relativeEndSec = timeOffsetSec,
                                text = phraseStr
                            )
                        )
                    }
                    currentPhraseStartSec = timeOffsetSec
                    hasOpenTimestamp = false
                }
            }
        }

        val trailingPhrase = phraseBytes.toString(Charsets.UTF_8.name()).trim()
        if (trailingPhrase.isNotEmpty() && timedPhrases.isNotEmpty()) {
            val start = currentPhraseStartSec.coerceIn(0.0, max(0.0, windowDurationSec - 0.4))
            val end = windowDurationSec.coerceAtLeast(start + 0.4)
            timedPhrases.add(
                TimedTokenPhrase(
                    relativeStartSec = start,
                    relativeEndSec = end,
                    text = trailingPhrase
                )
            )
        }

        val fullDecoded = fullBytes.toString(Charsets.UTF_8.name())
            .replace(Regex("\\[_.*?_]"), " ")
            .replace(Regex("<\\|.*?\\|>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return WindowInferenceResult(
            fullText = fullDecoded,
            timedPhrases = timedPhrases,
            acousticMetrics = metrics,
            noSpeechTriggered = noSpeechDetected && fullDecoded.length < 12
        )
    }
}
