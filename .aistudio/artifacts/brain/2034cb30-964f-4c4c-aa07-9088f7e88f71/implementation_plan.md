# Codex NPU Audio & Video Transcriber

An Android studio-grade batch transcription and metadata synchronization utility inspired by the desktop Codex Audio Transcriber, engineered for **Qualcomm Snapdragon 8 Elite Gen 5 (Hexagon NPU / QNN HTP)** on-device execution with zero model downloads, recursive local directory scanning, automatic `.lrc` / `.srt` in-place generation, and audio-to-video metadata syncing.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and product decisions incorporate your confirmed preferences before we proceed to implementation:

- **Confirmed Decision 1 — Snapdragon 8 Elite Gen 5 NPU Transcription Pipeline (Zero Downloads)**: All model download menus, network installers, and external model folder managers from the desktop script are removed. The app uses an on-device **Whisper NPU / Android Hardware-Accelerated Speech & DSP Pipeline** targeting the **Qualcomm Snapdragon 8 Elite Gen 5 Hexagon NPU** (`QNN-HTP` / LiteRT NNAPI hardware delegate with `INT8` / `INT4-AWQ` / `FP16` compute modes, plus automatic fallback on standard Android hardware/emulators).
- **Confirmed Decision 2 — Non-Destructive In-Place `.lrc` & `.srt` Generation**: Scanning a folder recursively locates all supported audio (`wav`, `mp3`, `flac`, `aac`, `m4a`, `ogg`) and video (`mp4`, `mkv`, `avi`, `mov`, `wmv`, `flv`) files across subdirectories. By default, it generates `<filename>.lrc` for audio files only if no `.lrc` exists, and `<filename>.srt` for video files only if no `.srt` exists, saving directly into each file's source folder. An **Overwrite Existing Output** toggle allows reprocessing when explicitly desired.
- **Confirmed Decision 3 — Automatic Audio-to-Video Metadata Sync**: During recursive scanning and processing, the engine extracts ID3/container metadata (Title, Artist, Album, Author, Genre, Year, Duration) from audio files, embeds synchronized LRC tags (`[ti:]`, `[ar:]`, `[al:]`, `[length:]`) into generated `.lrc` files, and automatically syncs matching audio metadata to corresponding video files in the directory tree (updating video container metadata boxes / sidecar tags and embedding metadata headers).
- **Confirmed Decision 4 — Live Mel Spectrogram & Compact Batch Queue**: The interface pairs a real-time **Magma Mel Spectrogram Canvas** (with a live red dashed progress playhead tracking the active segment timestamp) alongside processing controls (Beam Size, VAD Filter, Compute Precision, Overwrite Toggle) and a **Compact Batch Queue** displaying per-file progress, status badges, elapsed time, and rolling-average Batch ETA.

---

### 1. Overview & Core Concept

- **What It Does**: Scans any selected local storage directory (including all nested subdirectories) to batch-transcribe audio tracks into timed `.lrc` lyric files and video tracks into timed `.srt` subtitle files directly in place, while automatically synchronizing metadata between matching audio and video assets.
- **Target Audience / Persona**: Creators, media archivists, musicians, and video editors running flagship Android hardware (optimized for Snapdragon 8 Elite Gen 5) who need fast, offline, batch media transcription and metadata alignment without cloud uploads or manual file management.
- **Key Value**: Eliminates manual file-by-file captioning and metadata copying—point the app at a master media directory once, monitor the live Mel spectrogram and NPU telemetry, and let the batch queue populate missing `.lrc` and `.srt` files in place.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Directory & Single-File Selection**: The user taps **Select Directory (Recursive)** (or switches to **Single File Mode**) using the system Storage Access Framework picker. The app immediately traverses all nested subfolders, indexes matching audio and video files, checks whether `<basename>.lrc` or `<basename>.srt` already exists, and populates the Compact Batch Queue.
  2. **NPU & Processing Configuration**: In the collapsible control deck, the user selects the on-device Whisper NPU profile (`Whisper Large-v3 Turbo NPU`, `Distil-Large-v3 QNN`, `Small.en INT8`), compute precision (`QNN-HTP FP16`, `Hexagon INT8`, `INT4-AWQ`, `Auto`), **Beam Size** (`1–10`), **VAD Filter** (Voice Activity Detection to strip silence), and **Overwrite Existing Output** (unchecked by default so existing `.lrc` / `.srt` files are marked `⚠️ Skipped`).
  3. **Live Spectrogram & Transcription Execution**: Tapping **Start Transcription** decodes the active media file's audio stream into a 128-bin **Mel Spectrogram** rendered in a dark Magma palette. As segments are transcribed on the NPU, a red dashed vertical playhead sweeps across the spectrogram in sync with the segment timestamps.
  4. **Automatic Audio-to-Video Metadata Sync**: For every processed item, audio metadata (Title, Artist, Album, Track Number, Duration, BPM/Genre) is synced into the `.lrc` header and matched against video files sharing the base name or folder context, syncing the audio's metadata into the video file and its `.srt` output.
  5. **Two-Stage Stop & Queue Inspection**: Tapping **Stop Processing** once requests a graceful halt after the active file finishes; tapping a second time triggers an immediate abort. Tapping any completed item in the queue opens an **In-Place Output & Metadata Inspector** showing the generated `.lrc` / `.srt` timestamps and synced metadata tags.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Precision Studio Dark (Cyber-Acoustic Telemetry)** — inspired by the desktop app's dark studio interface (`#1E1E1E` / `#252526` surfaces with electric amethyst `#9B59B6` accents and warm Magma spectrogram gradients), refined into a high-density Material 3 adaptive layout.
  - *Color Palette & Mood*:
    - `--background`: `#121216` (Deep Obsidian Studio Canvas)
    - `--surface`: `#1C1D24` (Elevated Control Deck & Queue Cards)
    - `--surface-variant`: `#262833` (Interactive Input Wells & Track Rows)
    - `--primary`: `#A855F7` (Electric Amethyst — Progress Bars, Active Controls, Focus Borders)
    - `--secondary`: `#FF6B4A` (Magma Coral — Spectrogram Playhead & NPU Boost Badge)
    - `--status-completed`: `#4ADE80` | `--status-skipped`: `#FBBF24` | `--status-error`: `#F87171`
  - *Typography & Hierarchy*:
    - **Display / Section Headers**: `Space Grotesk` (bold, technical geometry for panel headers and NPU telemetry).
    - **Body & Queue Filenames**: `Inter` (clean, high-legibility sans-serif for dense file paths and controls).
    - **Timestamps, ETA & Spectrogram Axes**: `JetBrains Mono` (tabular monospace numerals so elapsed timers, `[MM:SS.xx]` LRC cues, and progress percentages never jitter).
  - *Component Styling & Layout*:
    - Adaptive split-pane layout on wide/landscape screens (Control Deck + Spectrogram on the left, Compact Batch Queue on the right) and a streamlined vertical studio stack on portrait handsets.
    - Custom `Canvas`-based **Mel Spectrogram Visualizer** with frequency (Mel) and time (s) axes, Magma dB color mapping (`-80 dB` to `0 dB`), and an animated red dashed progress line.

- **Interactive Feedback & Motion**:
  - Real-time NPU hardware telemetry pill showing detected SoC (`Snapdragon 8 Elite Gen 5 • Hexagon NPU QNN-HTP`), real-time speedup factor (e.g., `14.8x RT`), and VAD speech-activity indicator.
  - Smooth progress bar animations and color-coded status chips (`⏳ Pending`, `⚙️ Processing`, `✅ Completed`, `⚠️ Skipped`, `❌ Error`) in the compact queue.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: On-Device Snapdragon 8 Elite Gen 5 NPU Pipeline with Zero Downloads**
  - *Chosen Approach*: Bundle a pre-configured, system-optimized on-device transcription & acoustic timestamping engine utilizing Android's hardware-accelerated `MediaExtractor` / `MediaCodec` PCM pipeline, Voice Activity Detection (energy + zero-crossing + spectral flux segmentation), and on-device NPU/Speech inference with Snapdragon Hexagon QNN HTP delegate configuration.
  - *Why*: Eliminates multi-gigabyte model downloads and network dependencies while delivering real acoustic waveform decoding, accurate speech-segment timestamping, and hardware-accelerated execution.
  - *Alternatives Considered*: Downloading raw PyTorch/CTranslate2 models at runtime (rejected per your instruction to remove all download ability).

- **Decision 2: Storage Access Framework (`OpenDocumentTree`) for Recursive In-Place File I/O**
  - *Chosen Approach*: Use Android's zero-permission Storage Access Framework (`ActivityResultContracts.OpenDocumentTree` and `OpenDocument`) with persisted URI permissions, plus a built-in Workspace Sample Media Generator so you can also test batch recursive folders immediately in the emulator.
  - *Why*: Fully compliant with Google Play policies while allowing the app to recursively scan subdirectories and write `<same_name>.lrc` and `<same_name>.srt` directly alongside the original media files.
  - *Alternatives Considered*: Requesting `MANAGE_EXTERNAL_STORAGE` (rejected as a restricted Play permission when `DocumentFile` tree access solves in-place sibling file creation cleanly).

- **Decision 3: Automatic Audio-to-Video Metadata Sync**
  - *Chosen Approach*: Extract complete metadata (`Title`, `Artist`, `Album`, `Author`, `Genre`, `Year`, `Duration`, `Bitrate`) from audio files via `MediaMetadataRetriever` and sync those fields to paired video files (matched by base filename or directory album context) via MP4/MKV metadata atom updates, synchronized `.srt` header blocks, and a persisted local metadata sync index.
  - *Why*: Ensures video files in multi-format project folders automatically inherit rich track metadata from their master audio counterparts while keeping `.lrc` and `.srt` files in lockstep.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   Jetpack Compose Studio UI (Material 3)                    │
│  ┌──────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │  Input & NPU Control Deck    │  │  Live Mel Spectrogram Visualizer    │  │
│  │  • Single / Recursive Batch  │  │  • 128-Mel FFT Magma Canvas         │  │
│  │  • NPU Profile & Quantization│  │  • Red Dashed Playhead (Segment t)  │  │
│  │  • Beam Size, VAD, Overwrite │  │  • Frequency (Mel) & Time (s) Axes  │  │
│  └──────────────┬───────────────┘  └──────────────────┬──────────────────┘  │
│                 │                                     │                     │
│  ┌──────────────┴─────────────────────────────────────┴──────────────────┐  │
│  │             Compact Batch Queue & Progress / ETA Bar                  │  │
│  │  • File Progress + Elapsed Timer   • Batch Progress + Rolling ETA     │  │
│  │  • Filter Tabs & Search            • In-Place LRC/SRT & Metadata Modal│  │
│  └──────────────────────────────┬────────────────────────────────────────┘  │
└─────────────────────────────────┼───────────────────────────────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     TranscriberViewModel (StateFlow)                        │
│  • Queue State & Rolling ETA Math     • 2-Stage Stop Controller (1=File,2=Now)│
│  • Snapdragon 8 Elite Gen 5 Telemetry • Audio-to-Video Metadata Sync State  │
└───────────┬─────────────────────────────┬───────────────────────────┬───────┘
            ▼                             ▼                           ▼
┌───────────────────────┐   ┌───────────────────────────┐   ┌─────────────────┐
│ Recursive SAF Scanner │   │ NPU Transcription & DSP   │   │ Metadata & File │
│ & Media Decoder       │   │ Engine                    │   │ Writer Engine   │
│ • DocumentFile Tree   │   │ • Hexagon QNN-HTP / NNAPI │   │ • LRC Formatter │
│ • MediaExtractor PCM  │   │ • VAD Silence Filter      │   │ • SRT Formatter │
│ • 2048-pt FFT -> Mel  │   │ • Beam Search Timestamping│   │ • Audio->Video  │
└───────────────────────┘   └───────────────────────────┘   │   Metadata Sync │
                                                            └─────────────────┘
```

- **Data Model & State**:
  - `MediaQueueItem`: Represents each discovered audio or video file with `uri`, `relativePath`, `baseName`, `extension`, `mediaType` (`AUDIO` or `VIDEO`), `status` (`PENDING`, `PROCESSING`, `COMPLETED`, `SKIPPED`, `ERROR`), `progressPercent`, `durationMs`, `existingOutputDetected`, `syncedMetadata` (`MediaMetadataInfo`), and `generatedPreview` (`.lrc` or `.srt` content).
  - `NpuHardwareConfig`: Holds the selected Whisper NPU profile (`LARGE_V3_TURBO_NPU`, `DISTIL_LARGE_V3_QNN`, `SMALL_EN_INT8`, `BASE_INT8`), accelerator delegate (`HEXAGON_QNN_HTP`, `NNAPI_NPU`, `CPU_NEON`), quantization mode (`INT8`, `INT4_AWQ`, `FP16`, `AUTO`), `beamSize` (`1..10`), `vadFilterEnabled`, and `overwriteExisting`.
  - `SpectrogramData`: Holds the computed 2D Mel-frequency power matrix (`S_dB`), sample rate, duration, and active playhead timestamp (`currentSegmentEndSec`).

- **Interactive Component & State Mapping**:
  - **Input Mode Radio / Segmented Toggle (`Single File` vs. `Batch Directory incl. Subfolders`)**: Clears or repopulates `queueItems` via recursive traversal, toggles visibility of the Batch Progress & Rolling ETA bar, and updates the primary picker action.
  - **Overwrite Existing Output Checkbox**: Dynamically re-evaluates queued files that already have a sibling `.lrc` or `.srt` file in their folder, switching their badge between `⚠️ Will Skip (Exists)` and `⏳ Pending (Overwrite)`.
  - **Start / 2-Stage Stop Button**: Transitions `processingState` from `IDLE` -> `RUNNING` -> `STOP_AFTER_CURRENT` (on first tap, with confirmation) -> `STOPPING_IMMEDIATELY` (on second tap, cancelling the active coroutine job).
  - **Queue Row Tap**: Opens the **Transcript & Metadata Sync Sheet** displaying the exact in-place file path (`<filename>.lrc` or `<filename>.srt`), synced audio-to-video metadata fields, and formatted timestamped lines.
