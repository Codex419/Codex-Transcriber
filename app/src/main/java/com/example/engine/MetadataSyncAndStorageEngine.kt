package com.example.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.model.FileProcessStatus
import com.example.model.MediaKind
import com.example.model.MediaMetadataInfo
import com.example.model.MediaQueueItem
import com.example.model.SupportedMediaExtensions
import com.example.model.TranscriptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Handles:
 * 1. Recursive directory & subdirectory scanning (both local filesystem and Android SAF DocumentFile trees)
 *    with full support for Audio (.mp3, .flac, .wav, .aac, .m4a, .ogg) and Video (.mp4, .mkv, .avi, .mov, .wmv, .flv).
 * 2. Guaranteed in-place saving of `.lrc` (for audio) and `.srt` (for video) in the exact source directory
 *    with strict `.lrc` / `.srt` extensions and detailed diagnostic logging.
 * 3. Extracting native MP3 ID3v2 tags (including USLT lyrics), FLAC Vorbis Comments (including LYRICS),
 *    RIFF LIST-INFO, and MediaMetadataRetriever metadata, and syncing metadata from audio files to video files.
 */
class MetadataSyncAndStorageEngine(private val context: Context) {

    suspend fun scanLocalDirectoryRecursive(
        rootDir: File,
        overwriteExisting: Boolean,
        autoSyncMetadata: Boolean,
        onLog: (String) -> Unit = {}
    ): List<MediaQueueItem> = withContext(Dispatchers.IO) {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            onLog("❌ Local directory does not exist: ${rootDir.absolutePath}")
            return@withContext emptyList()
        }
        onLog("🔍 Scanning local directory (incl. subfolders): ${rootDir.absolutePath}")

        val discoveredFiles = mutableListOf<File>()
        fun walkDir(dir: File) {
            val children = dir.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: return
            for (child in children) {
                if (child.isDirectory) {
                    walkDir(child)
                } else if (child.isFile) {
                    val lowerName = child.name.lowercase(Locale.ROOT)
                    if (lowerName.endsWith(".lrc.txt") || lowerName.endsWith(".srt.txt")) {
                        runCatching { child.delete() }
                        continue
                    }
                    val ext = child.extension.lowercase(Locale.ROOT)
                    if (ext in SupportedMediaExtensions.ALL_EXTENSIONS) {
                        discoveredFiles.add(child)
                    }
                }
            }
        }
        walkDir(rootDir)
        onLog("   Found ${discoveredFiles.size} supported audio/video file(s).")

        val rawItems = discoveredFiles.mapNotNull { file ->
            val ext = file.extension.lowercase(Locale.ROOT)
            val kind = SupportedMediaExtensions.classifyExtension(ext) ?: return@mapNotNull null
            val baseName = file.nameWithoutExtension
            val expectedOutFile = File(file.parentFile, "$baseName${kind.targetExtension}")
            val exists = expectedOutFile.exists() && expectedOutFile.length() > 0L
            val relPath = file.relativeToOrSelf(rootDir).path
            val subfolder = file.parentFile?.relativeToOrSelf(rootDir)?.path?.ifEmpty { "/" } ?: "/"

            val extractedMeta = extractMetadataFromFile(file, kind)
            val existingContent = if (exists) {
                runCatching { expectedOutFile.readText(Charsets.UTF_8) }.getOrDefault("")
            } else ""

            if (exists && !overwriteExisting) {
                onLog("   ⚠️ Skipping existing output: ${expectedOutFile.absolutePath}")
            }

            val durSec = (extractedMeta.durationMs / 1000.0).coerceAtLeast(10.0)
            val parsedSegments = if (existingContent.isNotBlank()) {
                TranscriptFormatter.parseTranscriptToSegments(existingContent, kind, durSec)
            } else emptyList()

            MediaQueueItem(
                id = file.absolutePath,
                filename = file.name,
                baseName = baseName,
                extension = ext,
                relativePath = relPath,
                subfolderName = subfolder,
                mediaKind = kind,
                localFilePath = file.absolutePath,
                status = if (exists && !overwriteExisting) FileProcessStatus.SKIPPED else FileProcessStatus.PENDING,
                statusMessage = if (exists && !overwriteExisting) {
                    "Output Exists (${expectedOutFile.name})"
                } else {
                    "Ready for ${kind.formatBadge}"
                },
                progressPercent = if (exists && !overwriteExisting) 100f else 0f,
                durationSec = durSec,
                existingOutputExists = exists,
                expectedOutputFilename = expectedOutFile.name,
                savedOutputPath = if (exists) expectedOutFile.absolutePath else null,
                metadata = extractedMeta,
                segments = parsedSegments,
                generatedTranscriptContent = existingContent
            )
        }

        if (autoSyncMetadata) {
            synchronizeAudioMetadataToVideos(rawItems, onLog)
        } else {
            rawItems
        }
    }

    suspend fun scanDocumentTreeRecursive(
        treeUri: Uri,
        overwriteExisting: Boolean,
        autoSyncMetadata: Boolean,
        onLog: (String) -> Unit = {}
    ): List<MediaQueueItem> = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDoc == null) {
            onLog("❌ Could not open SAF DocumentTree URI: $treeUri")
            return@withContext emptyList()
        }
        val rootRealPath = resolveRealPathFromSafUri(rootDoc.uri)
        onLog("🔍 Scanning SAF directory tree (incl. subfolders): ${rootRealPath ?: treeUri}")

        val rawItems = mutableListOf<MediaQueueItem>()

        fun traverse(folder: DocumentFile, relativeFolder: String) {
            val initialFiles = folder.listFiles()
            for (doc in initialFiles) {
                val n = doc.name ?: continue
                val lower = n.lowercase(Locale.ROOT)
                if (lower.endsWith(".lrc.txt") || lower.endsWith(".srt.txt")) {
                    onLog("   🧹 Removing stale double-extension file: $n")
                    runCatching { doc.delete() }
                }
            }

            val files = folder.listFiles()
            val childNameMap = files.associateBy { it.name ?: "" }

            for (doc in files.sortedBy { it.name?.lowercase(Locale.ROOT) ?: "" }) {
                val docName = doc.name ?: continue
                if (doc.isDirectory) {
                    val nextRel = if (relativeFolder == "/") docName else "$relativeFolder/$docName"
                    traverse(doc, nextRel)
                } else if (doc.isFile) {
                    val dotIdx = docName.lastIndexOf('.')
                    if (dotIdx <= 0) continue
                    val baseName = docName.substring(0, dotIdx)
                    val ext = docName.substring(dotIdx + 1).lowercase(Locale.ROOT)
                    val kind = SupportedMediaExtensions.classifyExtension(ext) ?: continue
                    val expectedOutName = "$baseName${kind.targetExtension}"
                    val siblingOutDoc = childNameMap[expectedOutName]
                    val realFilePath = resolveRealPathFromSafUri(doc.uri)
                    val localSiblingExists = realFilePath?.let {
                        val f = File(File(it).parentFile, expectedOutName)
                        f.exists() && f.length() > 0L
                    } ?: false
                    val exists = (siblingOutDoc != null && siblingOutDoc.exists() && siblingOutDoc.length() > 0L) || localSiblingExists

                    val relPath = if (relativeFolder == "/") docName else "$relativeFolder/$docName"
                    val extractedMeta = extractMetadataFromUri(doc.uri, docName, kind, doc.length())
                    val existingContent = if (exists && siblingOutDoc != null) {
                        runCatching {
                            context.contentResolver.openInputStream(siblingOutDoc.uri)?.use {
                                it.bufferedReader(Charsets.UTF_8).readText()
                            } ?: ""
                        }.getOrDefault("")
                    } else if (localSiblingExists && realFilePath != null) {
                        runCatching {
                            File(File(realFilePath).parentFile, expectedOutName).readText(Charsets.UTF_8)
                        }.getOrDefault("")
                    } else ""

                    val inPlaceDisplayPath = if (realFilePath != null) {
                        File(File(realFilePath).parentFile, expectedOutName).absolutePath
                    } else {
                        "${relativeFolder.trimEnd('/')}/$expectedOutName"
                    }

                    val durSec = (extractedMeta.durationMs / 1000.0).coerceAtLeast(10.0)
                    val parsedSegments = if (existingContent.isNotBlank()) {
                        TranscriptFormatter.parseTranscriptToSegments(existingContent, kind, durSec)
                    } else emptyList()

                    rawItems.add(
                        MediaQueueItem(
                            id = doc.uri.toString(),
                            filename = docName,
                            baseName = baseName,
                            extension = ext,
                            relativePath = relPath,
                            subfolderName = relativeFolder,
                            mediaKind = kind,
                            localFilePath = realFilePath,
                            documentUriString = doc.uri.toString(),
                            parentTreeUriString = folder.uri.toString(),
                            status = if (exists && !overwriteExisting) FileProcessStatus.SKIPPED else FileProcessStatus.PENDING,
                            statusMessage = if (exists && !overwriteExisting) {
                                "Output Exists ($expectedOutName)"
                            } else {
                                "Ready for ${kind.formatBadge}"
                            },
                            progressPercent = if (exists && !overwriteExisting) 100f else 0f,
                            durationSec = durSec,
                            existingOutputExists = exists,
                            expectedOutputFilename = expectedOutName,
                            savedOutputPath = if (exists) inPlaceDisplayPath else null,
                            metadata = extractedMeta,
                            segments = parsedSegments,
                            generatedTranscriptContent = existingContent
                        )
                    )
                }
            }
        }

        traverse(rootDoc, "/")
        onLog("   Found ${rawItems.size} media file(s) across directory tree.")
        if (autoSyncMetadata) {
            synchronizeAudioMetadataToVideos(rawItems, onLog)
        } else {
            rawItems
        }
    }

    suspend fun inspectSingleMediaUri(
        uri: Uri,
        overwriteExisting: Boolean,
        fallbackTreeUri: Uri? = null,
        onLog: (String) -> Unit = {}
    ): MediaQueueItem? = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "selected_media.mp3"
        val dotIdx = displayName.lastIndexOf('.')
        val baseName = if (dotIdx > 0) displayName.substring(0, dotIdx) else displayName
        val ext = if (dotIdx > 0) displayName.substring(dotIdx + 1).lowercase(Locale.ROOT) else "mp3"
        val kind = SupportedMediaExtensions.classifyExtension(ext) ?: MediaKind.AUDIO
        val expectedOutName = "$baseName${kind.targetExtension}"

        val realFilePath = resolveRealPathFromSafUri(uri)
        val matchedParentTreeUri = findMatchingParentTreeUriForSingleFile(uri, fallbackTreeUri)

        val localOutFile = realFilePath?.let { File(File(it).parentFile, expectedOutName) }
        val safOutDoc = matchedParentTreeUri?.let { pUri ->
            DocumentFile.fromTreeUri(context, pUri)?.findFile(expectedOutName)
        }
        val exists = (localOutFile != null && localOutFile.exists() && localOutFile.length() > 0L) ||
            (safOutDoc != null && safOutDoc.exists() && safOutDoc.length() > 0L)

        val meta = extractMetadataFromUri(uri, displayName, kind, 0L)
        val parentFolderDisplay = realFilePath?.let { File(it).parentFile?.absolutePath } ?: "/"
        val existingContent = when {
            localOutFile != null && localOutFile.exists() -> runCatching { localOutFile.readText(Charsets.UTF_8) }.getOrDefault("")
            safOutDoc != null && safOutDoc.exists() -> runCatching {
                context.contentResolver.openInputStream(safOutDoc.uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: ""
            }.getOrDefault("")
            else -> ""
        }
        val durSec = (meta.durationMs / 1000.0).coerceAtLeast(10.0)
        val parsedSegments = if (existingContent.isNotBlank()) {
            TranscriptFormatter.parseTranscriptToSegments(existingContent, kind, durSec)
        } else emptyList()

        onLog("📄 Loaded single file: $displayName (In-place folder: $parentFolderDisplay)")

        MediaQueueItem(
            id = uri.toString(),
            filename = displayName,
            baseName = baseName,
            extension = ext,
            relativePath = displayName,
            subfolderName = parentFolderDisplay,
            mediaKind = kind,
            localFilePath = realFilePath,
            documentUriString = uri.toString(),
            parentTreeUriString = matchedParentTreeUri?.toString(),
            status = if (exists && !overwriteExisting) FileProcessStatus.SKIPPED else FileProcessStatus.PENDING,
            statusMessage = if (exists && !overwriteExisting) "Output Exists ($expectedOutName)" else "Ready for ${kind.formatBadge}",
            progressPercent = if (exists && !overwriteExisting) 100f else 0f,
            durationSec = durSec,
            existingOutputExists = exists,
            expectedOutputFilename = expectedOutName,
            savedOutputPath = if (exists) (localOutFile?.absolutePath ?: "$parentFolderDisplay/$expectedOutName") else null,
            metadata = meta,
            segments = parsedSegments,
            generatedTranscriptContent = existingContent
        )
    }

    /**
     * Resolves a SAF document/tree Uri (such as `primary:Music/Song.mp3`) to an actual `/storage/emulated/0/...`
     * filesystem path whenever backed by local shared storage.
     */
    fun resolveRealPathFromSafUri(uri: Uri): String? {
        return runCatching {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                return@runCatching uri.path
            }
            val docId = when {
                DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.getDocumentId(uri)
                DocumentsContract.isTreeUri(uri) -> DocumentsContract.getTreeDocumentId(uri)
                else -> null
            } ?: return@runCatching null

            when {
                docId.startsWith("raw:") -> docId.removePrefix("raw:")
                docId.startsWith("primary:") -> {
                    val rel = docId.removePrefix("primary:").trimStart('/')
                    val base = Environment.getExternalStorageDirectory().absolutePath
                    if (rel.isEmpty()) base else "$base/$rel"
                }
                docId.contains(":") -> {
                    val parts = docId.split(":", limit = 2)
                    val vol = parts[0]
                    val rel = parts.getOrElse(1) { "" }.trimStart('/')
                    val candidate = File("/storage/$vol/$rel")
                    if (candidate.exists() || candidate.parentFile?.exists() == true) {
                        candidate.absolutePath
                    } else null
                }
                else -> null
            }
        }.getOrNull()
    }

    /**
     * Finds a persisted SAF tree permission that covers the given single-file Uri so in-place
     * file creation works seamlessly even when a file was selected via Single File mode.
     */
    private fun findMatchingParentTreeUriForSingleFile(fileUri: Uri, activeTreeUri: Uri?): Uri? {
        return runCatching {
            val fileDocId = if (DocumentsContract.isDocumentUri(context, fileUri)) {
                DocumentsContract.getDocumentId(fileUri)
            } else return@runCatching activeTreeUri

            val parentDocId = if (fileDocId.contains("/")) {
                fileDocId.substringBeforeLast('/')
            } else if (fileDocId.contains(":")) {
                fileDocId.substringBeforeLast(':') + ":"
            } else return@runCatching activeTreeUri

            val candidateTrees = buildList {
                if (activeTreeUri != null) add(activeTreeUri)
                addAll(
                    context.contentResolver.persistedUriPermissions
                        .filter { it.isWritePermission }
                        .map { it.uri }
                )
            }

            for (treeUri in candidateTrees) {
                val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: continue
                if (parentDocId.startsWith(treeDocId)) {
                    return@runCatching DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                }
            }
            activeTreeUri
        }.getOrNull()
    }

    fun synchronizeAudioMetadataToVideos(
        items: List<MediaQueueItem>,
        onLog: (String) -> Unit = {}
    ): List<MediaQueueItem> {
        val audioItems = items.filter { it.mediaKind == MediaKind.AUDIO }
        if (audioItems.isEmpty()) return items

        val audioToVideoTargetMap = mutableMapOf<String, String>()

        val firstPass = items.map { item ->
            if (item.mediaKind == MediaKind.VIDEO) {
                val normStem = normalizeStem(item.baseName)
                val isVaultFile = item.localFilePath?.contains("Codex_Local_Media_Vault") == true
                // STRICT MATCH ONLY: Only sync an audio file to a video file if they are in the exact same subfolder,
                // share the exact same normalized base filename stem, and have compatible duration (never match
                // a random song in the same folder or a 2:26 studio song onto a different video/concert!).
                val matchedAudio = if (normStem.length >= 2) {
                    audioItems.firstOrNull { audio ->
                        audio.subfolderName == item.subfolderName &&
                            normalizeStem(audio.baseName) == normStem &&
                            (isVaultFile || kotlin.math.abs(audio.durationSec - item.durationSec) <= 20.0)
                    }
                } else null

                if (matchedAudio != null) {
                    audioToVideoTargetMap[matchedAudio.id] = item.filename
                    val aMeta = matchedAudio.metadata
                    val syncedMeta = item.metadata.copy(
                        title = aMeta.title.ifBlank { item.metadata.title.ifBlank { item.baseName } },
                        artist = aMeta.artist.ifBlank { item.metadata.artist },
                        album = aMeta.album.ifBlank { item.metadata.album },
                        author = aMeta.author.ifBlank { item.metadata.author },
                        genre = aMeta.genre.ifBlank { item.metadata.genre },
                        year = aMeta.year.ifBlank { item.metadata.year },
                        trackNumber = aMeta.trackNumber.ifBlank { item.metadata.trackNumber },
                        durationMs = if (item.metadata.durationMs > 0L) item.metadata.durationMs else aMeta.durationMs,
                        sampleRateHz = aMeta.sampleRateHz,
                        embeddedLyrics = aMeta.embeddedLyrics.ifBlank { item.metadata.embeddedLyrics },
                        syncedFromAudioSource = matchedAudio.filename
                    )
                    val changed = item.metadata.syncedFromAudioSource != matchedAudio.filename ||
                        item.metadata.title != syncedMeta.title ||
                        item.metadata.artist != syncedMeta.artist
                    if (changed) {
                        item.localFilePath?.let { path ->
                            writeSyncedMetadataBoxToVideoFile(File(path), syncedMeta)
                        }
                        onLog("   🔗 Synced exact-stem audio metadata: ${matchedAudio.filename} → ${item.filename} (Title='${syncedMeta.title}', Artist='${syncedMeta.artist}')")
                    }
                    item.copy(metadata = syncedMeta)
                } else {
                    // Ensure no stale syncedFromAudioSource from an unrelated audio track remains on this video
                    if (item.metadata.syncedFromAudioSource != null && !isVaultFile) {
                        item.copy(
                            metadata = item.metadata.copy(
                                title = item.baseName.replace('_', ' '),
                                artist = "",
                                embeddedLyrics = "",
                                syncedFromAudioSource = null
                            )
                        )
                    } else {
                        item
                    }
                }
            } else {
                item
            }
        }

        return firstPass.map { item ->
            if (item.mediaKind == MediaKind.AUDIO && item.id in audioToVideoTargetMap) {
                item.copy(
                    metadata = item.metadata.copy(
                        syncedToVideoTarget = audioToVideoTargetMap[item.id]
                    )
                )
            } else {
                item
            }
        }
    }

    /**
     * Saves the timed output (.lrc for Audio, .srt for Video) directly IN PLACE alongside the source file
     * with the exact same base filename and target extension (.lrc or .srt).
     */
    suspend fun saveTimedOutputInPlace(
        item: MediaQueueItem,
        content: String,
        overwriteExisting: Boolean,
        onLog: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFileName = "${item.baseName}${item.mediaKind.targetExtension}"
            val contentBytes = content.toByteArray(Charsets.UTF_8)

            // 1. Try Android SAF parent tree first whenever parentTreeUriString is available
            val parentUriStr = item.parentTreeUriString
                ?: item.documentUriString?.let { docUriStr ->
                    findMatchingParentTreeUriForSingleFile(Uri.parse(docUriStr), null)?.toString()
                }

            if (!parentUriStr.isNullOrBlank()) {
                val parentUri = Uri.parse(parentUriStr)
                val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
                if (parentDoc != null && parentDoc.canWrite()) {
                    // Clean up any stale .lrc.txt or .srt.txt files
                    parentDoc.findFile("$targetFileName.txt")?.delete()
                    parentDoc.findFile("$targetFileName.bin")?.delete()

                    val existingDoc = parentDoc.findFile(targetFileName)
                    if (existingDoc != null && existingDoc.exists() && existingDoc.length() > 0L && !overwriteExisting) {
                        val existingPath = resolveRealPathFromSafUri(existingDoc.uri)
                            ?: "${item.subfolderName.trimEnd('/')}/$targetFileName"
                        onLog("   ⚠️ Skipping save (already exists in place): $existingPath")
                        return@runCatching existingPath
                    }
                    existingDoc?.delete()

                    // Create in-place using DocumentsContract with "application/octet-stream"
                    // In Android's AOSP FileUtils.buildUniqueFile, "application/octet-stream" is the exact MIME
                    // type that preserves the caller's custom extension (.lrc / .srt) without appending .txt!
                    var createdUri: Uri? = runCatching {
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            parentDoc.uri,
                            "application/octet-stream",
                            targetFileName
                        )
                    }.getOrNull()

                    if (createdUri == null) {
                        createdUri = parentDoc.createFile("application/octet-stream", targetFileName)?.uri
                    }

                    val initialUri = createdUri
                    if (initialUri != null) {
                        var finalUri: Uri = initialUri
                        // Write content using standard "w" mode (not "wt" which can fail on ExternalStorageProvider)
                        val outStream = runCatching {
                            context.contentResolver.openOutputStream(finalUri, "w")
                        }.getOrNull() ?: context.contentResolver.openOutputStream(finalUri)

                        if (outStream != null) {
                            outStream.use { out ->
                                out.write(contentBytes)
                                out.flush()
                            }
                        }

                        // Verify exact filename on the provider (if provider altered extension, rename to exact targetFileName)
                        val actualName = resolveDisplayName(finalUri) ?: targetFileName
                        if (!actualName.equals(targetFileName, ignoreCase = true)) {
                            onLog("   🔧 Renaming '$actualName' → '$targetFileName'")
                            runCatching {
                                DocumentsContract.renameDocument(
                                    context.contentResolver,
                                    finalUri,
                                    targetFileName
                                )?.let { renamed -> finalUri = renamed }
                            }
                        }

                        val resolvedInPlacePath = resolveRealPathFromSafUri(finalUri)
                            ?: "${item.subfolderName.trimEnd('/')}/$targetFileName"
                        onLog("   💾 Saved in-place (${contentBytes.size} bytes): $resolvedInPlacePath")
                        return@runCatching resolvedInPlacePath
                    } else {
                        onLog("   ⚠️ SAF createDocument returned null for $targetFileName, trying direct file path...")
                    }
                }
            }

            // 2. Direct Local Filesystem Path (for app vault or direct /storage/emulated/0/... paths)
            val resolvedLocalPath = item.localFilePath
                ?: item.documentUriString?.let { resolveRealPathFromSafUri(Uri.parse(it)) }

            if (!resolvedLocalPath.isNullOrBlank()) {
                val sourceFile = File(resolvedLocalPath)
                val parentDir = sourceFile.parentFile
                if (parentDir != null && (parentDir.exists() || parentDir.mkdirs())) {
                    File(parentDir, "$targetFileName.txt").takeIf { it.exists() }?.delete()
                    val targetFile = File(parentDir, targetFileName)
                    if (targetFile.exists() && targetFile.length() > 0L && !overwriteExisting) {
                        onLog("   ⚠️ Skipping save (already exists in place): ${targetFile.absolutePath}")
                        return@runCatching targetFile.absolutePath
                    }
                    FileOutputStream(targetFile, false).use { fos ->
                        fos.write(contentBytes)
                        fos.flush()
                        runCatching { fos.fd.sync() }
                    }
                    if (item.mediaKind == MediaKind.VIDEO && item.metadata.syncedFromAudioSource != null) {
                        writeSyncedMetadataBoxToVideoFile(sourceFile, item.metadata)
                    }
                    onLog("   💾 Saved in-place (${contentBytes.size} bytes): ${targetFile.absolutePath}")
                    return@runCatching targetFile.absolutePath
                }
            }

            throw IllegalStateException(
                "No write access to source folder for '${item.filename}'. Please use 'Scan Directory...' to grant folder write access so '$targetFileName' can be saved in place."
            )
        }
    }

    private fun extractMetadataFromFile(file: File, kind: MediaKind): MediaMetadataInfo {
        val ext = file.extension.lowercase(Locale.ROOT)
        val nativeAudioMeta = when (ext) {
            "mp3" -> parseMp3Id3v2Metadata(file) ?: readEmbeddedMetadataTag(file)
            "flac" -> parseFlacVorbisCommentMetadata(file) ?: readEmbeddedMetadataTag(file)
            else -> readEmbeddedMetadataTag(file)
        }

        // Skip native NuMediaExtractor / MediaMetadataRetriever on synthetic vault containers or when native tags are complete
        if (file.absolutePath.contains("Codex_Local_Media_Vault") ||
            file.length() < 16_384L ||
            (nativeAudioMeta != null && nativeAudioMeta.title.isNotBlank() && nativeAudioMeta.durationMs > 0L)
        ) {
            return nativeAudioMeta ?: MediaMetadataInfo(
                title = file.nameWithoutExtension.replace('_', ' '),
                artist = "",
                album = file.parentFile?.name?.replace('_', ' ') ?: "",
                author = "",
                genre = if (kind == MediaKind.VIDEO) "Video" else "Music",
                year = "2026",
                durationMs = 24_000L,
                bitrateKbps = 320
            )
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.title?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension.replace('_', ' ')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.artist?.takeIf { it.isNotBlank() }
                ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.album?.takeIf { it.isNotBlank() }
                ?: file.parentFile?.name?.replace('_', ' ') ?: ""
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.author?.takeIf { it.isNotBlank() }
                ?: artist
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.genre?.takeIf { it.isNotBlank() }
                ?: "Music"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.year?.takeIf { it.isNotBlank() }
                ?: "2026"
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.takeIf { it.isNotBlank() }
                ?: nativeAudioMeta?.trackNumber ?: ""
            val bitrate = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 320_000) / 1000
            val estimatedDurFromSizeMs = if (file.length() > 100_000L && bitrate > 0) {
                (file.length() * 8L) / bitrate
            } else 0L
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: nativeAudioMeta?.durationMs?.takeIf { it > 0L }
                ?: estimatedDurFromSizeMs.takeIf { it > 0L }
                ?: 180_000L

            MediaMetadataInfo(
                title = title,
                artist = artist,
                album = album,
                author = author,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                durationMs = durationMs,
                bitrateKbps = bitrate,
                sampleRateHz = nativeAudioMeta?.sampleRateHz ?: 44100,
                embeddedLyrics = nativeAudioMeta?.embeddedLyrics ?: "",
                syncedFromAudioSource = nativeAudioMeta?.syncedFromAudioSource
            )
        } catch (_: Exception) {
            nativeAudioMeta ?: MediaMetadataInfo(
                title = file.nameWithoutExtension.replace('_', ' '),
                artist = "",
                album = file.parentFile?.name?.replace('_', ' ') ?: "",
                author = "",
                genre = "Music",
                year = "2026",
                durationMs = 180_000L,
                bitrateKbps = 320
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun parseMp3Id3v2Metadata(file: File): MediaMetadataInfo? {
        if (!file.exists() || file.length() < 10) return null
        return runCatching {
            val maxRead = file.length().coerceAtMost(512 * 1024L).toInt()
            val bytes = ByteArray(maxRead)
            file.inputStream().use { it.read(bytes) }
            parseId3v2Bytes(bytes, file.length())
        }.getOrNull()
    }

    private fun parseId3v2Bytes(bytes: ByteArray, totalFileLength: Long): MediaMetadataInfo? {
        if (bytes.size < 10) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)

        var pos = 10
        val limit = (10 + tagSize).coerceAtMost(bytes.size)
        val tags = mutableMapOf<String, String>()
        var usltLyrics = ""

        while (pos + 10 <= limit) {
            val frameId = String(bytes, pos, 4, Charsets.US_ASCII)
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val frameSize = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (frameSize <= 1 || pos + 10 + frameSize > limit) break

            if (frameId == "USLT" && frameSize > 5) {
                val rawBytes = bytes.copyOfRange(pos + 14, pos + 10 + frameSize)
                usltLyrics = String(rawBytes, Charsets.UTF_8)
                    .substringAfter('\u0000')
                    .trim { it <= ' ' || it == '\u0000' }
            } else {
                val textBytes = bytes.copyOfRange(pos + 11, pos + 10 + frameSize)
                val value = String(textBytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                tags[frameId] = value
            }
            pos += 10 + frameSize
        }
        if (tags.isEmpty() && usltLyrics.isEmpty()) return null
        val estimatedDurMs = if (totalFileLength > 100_000L) (totalFileLength * 8L) / 320L else 28_000L
        return MediaMetadataInfo(
            title = tags["TIT2"] ?: "",
            artist = tags["TPE1"] ?: "",
            album = tags["TALB"] ?: "",
            author = tags["TCOM"] ?: tags["TPE1"] ?: "",
            genre = tags["TCON"] ?: "Electronic",
            year = tags["TYER"] ?: tags["TDRC"] ?: "2026",
            trackNumber = tags["TRCK"] ?: "01",
            durationMs = tags["TLEN"]?.toLongOrNull() ?: estimatedDurMs,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            embeddedLyrics = usltLyrics
        )
    }

    private fun parseFlacVorbisCommentMetadata(file: File): MediaMetadataInfo? {
        if (!file.exists() || file.length() < 8) return null
        return runCatching {
            val maxRead = file.length().coerceAtMost(512 * 1024L).toInt()
            val bytes = ByteArray(maxRead)
            file.inputStream().use { it.read(bytes) }
            parseFlacBytes(bytes)
        }.getOrNull()
    }

    private fun parseFlacBytes(bytes: ByteArray): MediaMetadataInfo? {
        if (bytes.size < 8 || String(bytes, 0, 4, Charsets.US_ASCII) != "fLaC") return null
        var pos = 4
        val tags = mutableMapOf<String, String>()
        var streamDurationMs = 0L
        var sampleRate = 48000

        while (pos + 4 <= bytes.size) {
            val headerByte = bytes[pos].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val blockLen = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + blockLen > bytes.size) break

            if (blockType == 0 && blockLen >= 18) {
                val sr = ((bytes[pos + 10].toInt() and 0xFF) shl 12) or
                    ((bytes[pos + 11].toInt() and 0xFF) shl 4) or
                    ((bytes[pos + 12].toInt() and 0xF0) shr 4)
                val totalSamples = (((bytes[pos + 13].toLong() and 0x0F) shl 32) or
                    ((bytes[pos + 14].toLong() and 0xFF) shl 24) or
                    ((bytes[pos + 15].toLong() and 0xFF) shl 16) or
                    ((bytes[pos + 16].toLong() and 0xFF) shl 8) or
                    (bytes[pos + 17].toLong() and 0xFF))
                if (sr > 0 && totalSamples > 0) {
                    sampleRate = sr
                    streamDurationMs = (totalSamples * 1000L) / sr
                }
            } else if (blockType == 4) {
                val bb = ByteBuffer.wrap(bytes, pos, blockLen).order(ByteOrder.LITTLE_ENDIAN)
                val vendorLen = bb.int
                bb.position(bb.position() + vendorLen)
                val commentCount = bb.int
                for (c in 0 until commentCount) {
                    val cLen = bb.int
                    val strBytes = ByteArray(cLen)
                    bb.get(strBytes)
                    val comment = String(strBytes, Charsets.UTF_8)
                    val eq = comment.indexOf('=')
                    if (eq > 0) {
                        tags[comment.substring(0, eq).uppercase(Locale.ROOT)] = comment.substring(eq + 1)
                    }
                }
            }
            pos += blockLen
            if (isLast) break
        }
        if (tags.isEmpty() && streamDurationMs == 0L) return null
        return MediaMetadataInfo(
            title = tags["TITLE"] ?: "",
            artist = tags["ARTIST"] ?: "",
            album = tags["ALBUM"] ?: "",
            author = tags["COMPOSER"] ?: tags["ARTIST"] ?: "",
            genre = tags["GENRE"] ?: "Lossless Studio",
            year = tags["DATE"] ?: "2026",
            trackNumber = tags["TRACKNUMBER"] ?: "01",
            durationMs = tags["DURATION_MS"]?.toLongOrNull()
                ?: streamDurationMs.takeIf { it > 0L }
                ?: 28_000L,
            bitrateKbps = 1411,
            sampleRateHz = sampleRate,
            embeddedLyrics = tags["LYRICS"] ?: tags["UNSYNCEDLYRICS"] ?: ""
        )
    }

    private fun extractMetadataFromUri(
        uri: Uri,
        fallbackName: String,
        kind: MediaKind,
        fileLengthBytes: Long
    ): MediaMetadataInfo {
        val baseTitle = fallbackName.substringBeforeLast('.').replace('_', ' ')
        val ext = fallbackName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        val headerMeta = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(256 * 1024)
                val read = stream.read(buf)
                if (read > 16) {
                    val slice = buf.copyOf(read)
                    when (ext) {
                        "mp3" -> parseId3v2Bytes(slice, fileLengthBytes)
                        "flac" -> parseFlacBytes(slice)
                        else -> null
                    }
                } else null
            }
        }.getOrNull()

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: headerMeta?.title?.takeIf { it.isNotBlank() }
                ?: baseTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: headerMeta?.artist?.takeIf { it.isNotBlank() }
                ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
                ?: headerMeta?.album?.takeIf { it.isNotBlank() }
                ?: ""
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?.takeIf { it.isNotBlank() }
                ?: headerMeta?.author?.takeIf { it.isNotBlank() }
                ?: artist
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }
                ?: headerMeta?.genre?.takeIf { it.isNotBlank() }
                ?: "Music"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it.isNotBlank() }
                ?: headerMeta?.year?.takeIf { it.isNotBlank() }
                ?: "2026"
            val bitrate = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 320_000) / 1000
            val estimatedDurFromSizeMs = if (fileLengthBytes > 50_000L && bitrate > 0) {
                (fileLengthBytes * 8L) / bitrate
            } else 0L
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
                ?: headerMeta?.durationMs?.takeIf { it > 0L }
                ?: estimatedDurFromSizeMs.takeIf { it > 0L }
                ?: 180_000L

            MediaMetadataInfo(
                title = title,
                artist = artist,
                album = album,
                author = author,
                genre = genre,
                year = year,
                durationMs = durationMs,
                bitrateKbps = bitrate,
                embeddedLyrics = headerMeta?.embeddedLyrics ?: ""
            )
        } catch (_: Exception) {
            headerMeta ?: MediaMetadataInfo(
                title = baseTitle,
                artist = "",
                album = "",
                author = "",
                year = "2026",
                durationMs = if (fileLengthBytes > 50_000L) (fileLengthBytes * 8L) / 320L else 180_000L,
                bitrateKbps = 320
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun normalizeStem(stem: String): String {
        return stem.lowercase(Locale.ROOT)
            .replace("_video", "")
            .replace("_audio", "")
            .replace("-video", "")
            .replace("-audio", "")
            .replace("_master", "")
            .replace(" ", "_")
            .trim()
    }

    private fun writeSyncedMetadataBoxToVideoFile(videoFile: File, meta: MediaMetadataInfo) {
        if (!videoFile.exists() || !videoFile.canWrite()) return
        // Only write synthetic trailer box to small synthetic studio vault containers (<= 64 KB);
        // never load large user video files into memory with readBytes().
        if (!videoFile.absolutePath.contains("Codex_Local_Media_Vault") || videoFile.length() > 65_536L) {
            return
        }
        runCatching {
            val bytes = videoFile.readBytes()
            val marker = "CDXMETA1".toByteArray(Charsets.US_ASCII)
            var cleanLength = bytes.size
            for (i in max(0, bytes.size - 512) until max(0, bytes.size - marker.size)) {
                var match = true
                for (j in marker.indices) {
                    if (bytes[i + j] != marker[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    cleanLength = i
                    break
                }
            }

            val payload = buildString {
                append("CDXMETA1|")
                append("title=${meta.title}|")
                append("artist=${meta.artist}|")
                append("album=${meta.album}|")
                append("author=${meta.author}|")
                append("genre=${meta.genre}|")
                append("year=${meta.year}|")
                append("track=${meta.trackNumber}|")
                append("durationMs=${meta.durationMs}|")
                append("lyrics=${meta.embeddedLyrics.replace('\n', ';')}|")
                append("syncedFrom=${meta.syncedFromAudioSource ?: ""}")
            }.toByteArray(Charsets.UTF_8)

            FileOutputStream(videoFile, false).use { fos ->
                fos.write(bytes, 0, cleanLength)
                fos.write(payload)
                fos.flush()
            }
        }
    }

    private fun readEmbeddedMetadataTag(file: File): MediaMetadataInfo? {
        if (!file.exists()) return null
        val isVaultFile = file.absolutePath.contains("Codex_Local_Media_Vault")
        return runCatching {
            val fileLen = file.length()
            if (fileLen <= 0L) return@runCatching null
            val readLen = fileLen.coerceAtMost(4096L).toInt()
            val tailBytes = ByteArray(readLen)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(max(0L, fileLen - readLen))
                raf.readFully(tailBytes)
            }
            val marker = "CDXMETA1|".toByteArray(Charsets.UTF_8)
            var markerOffsetInTail = -1
            for (i in 0..(tailBytes.size - marker.size)) {
                var match = true
                for (j in marker.indices) {
                    if (tailBytes[i + j] != marker[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    markerOffsetInTail = i
                    break
                }
            }
            if (markerOffsetInTail < 0) return@runCatching null

            // CRITICAL FIX: If a real user video/audio file (outside Codex_Local_Media_Vault) ever had a
            // CDXMETA1 trailer appended by an earlier run, strip that trailer off the file in-place and ignore it
            // so the video NEVER contains another song's metadata or lyrics!
            if (!isVaultFile) {
                val cleanLength = max(0L, fileLen - readLen) + markerOffsetInTail
                if (cleanLength > 0L && file.canWrite()) {
                    runCatching {
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.setLength(cleanLength)
                        }
                    }
                }
                return@runCatching null
            }

            val tailStr = String(tailBytes, markerOffsetInTail, tailBytes.size - markerOffsetInTail, Charsets.UTF_8)
            val fields = tailStr.split('|')
            val map = mutableMapOf<String, String>()
            for (part in fields.drop(1)) {
                val eq = part.indexOf('=')
                if (eq > 0) {
                    map[part.substring(0, eq)] = part.substring(eq + 1)
                }
            }
            MediaMetadataInfo(
                title = map["title"] ?: "",
                artist = map["artist"] ?: "",
                album = map["album"] ?: "",
                author = map["author"] ?: "",
                genre = map["genre"] ?: "",
                year = map["year"] ?: "2026",
                trackNumber = map["track"] ?: "",
                durationMs = map["durationMs"]?.toLongOrNull() ?: 24_000L,
                sampleRateHz = 16000,
                embeddedLyrics = map["lyrics"]?.replace(';', '\n') ?: "",
                syncedFromAudioSource = map["syncedFrom"]?.takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    suspend fun createOrResetSampleMediaTree(onLog: (String) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val rootDir = File(context.filesDir, "Codex_Local_Media_Vault")
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
        rootDir.mkdirs()

        val folder1 = File(rootDir, "Album_01_Snapdragon_Acoustic").apply { mkdirs() }
        writeMp3WithId3v2Metadata(
            targetFile = File(folder1, "01_Hexagon_Horizon.mp3"),
            meta = MediaMetadataInfo(
                title = "Hexagon Horizon",
                artist = "Astraea",
                album = "Snapdragon 8 Elite Sessions",
                author = "E. Vance",
                genre = "Synthwave",
                year = "2026",
                trackNumber = "01",
                durationMs = 24_000L,
                bitrateKbps = 320,
                sampleRateHz = 44100,
                embeddedLyrics = """
                    Chasing the neon horizon across the midnight sky
                    Signals rising through the silence where the stars align
                    Every pulse of light is calling us to carry on
                    Holding fast until the shadows fade into the dawn
                    Standing on the edge of tomorrow with an open heart
                    Nothing in the universe can tear this dream apart
                    Racing through the static til the frequency is clear
                """.trimIndent()
            )
        )
        writeMinimalMp4Container(
            targetFile = File(folder1, "01_Hexagon_Horizon.mp4"),
            durationMs = 24_000L
        )

        val track2Audio = File(folder1, "02_Velvet_Telemetry.wav")
        writeSynthesizedWavWithMetadata(
            targetFile = track2Audio,
            durationSec = 20.0,
            fundamentalHz = 210.0,
            meta = MediaMetadataInfo(
                title = "Velvet Telemetry",
                artist = "Astraea",
                album = "Snapdragon 8 Elite Sessions",
                author = "E. Vance",
                genre = "Ambient Pop",
                year = "2026",
                trackNumber = "02",
                durationMs = 20_000L,
                sampleRateHz = 16000,
                embeddedLyrics = """
                    Shadows fall across the obsidian glass
                    Every heartbeat echoes in the dark
                    Hold my hand before the moment fades
                    We are weightless underneath the stars
                    Drifting home into the quiet night
                """.trimIndent()
            )
        )
        File(folder1, "02_Velvet_Telemetry.lrc").writeText(
            """
            [ti:Velvet Telemetry]
            [ar:Astraea]
            [al:Snapdragon 8 Elite Sessions]
            [length:00:20.00]

            [00:00.40]Shadows fall across the obsidian glass
            [00:04.25]Every heartbeat echoes in the dark
            [00:08.50]Hold my hand before the moment fades
            [00:12.80]We are weightless underneath the stars
            [00:16.40]Drifting home into the quiet night
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        val folder2 = File(rootDir, "Project_02_Lossless_Masters/Studio_FLAC").apply { mkdirs() }
        writeFlacWithVorbisComments(
            targetFile = File(folder2, "03_Silicon_Pulse_Session.flac"),
            meta = MediaMetadataInfo(
                title = "Silicon Pulse",
                artist = "Marcus Chen",
                album = "Acoustic Horizon",
                author = "Marcus Chen",
                genre = "Acoustic",
                year = "2026",
                trackNumber = "03",
                durationMs = 26_000L,
                bitrateKbps = 1411,
                sampleRateHz = 48000,
                embeddedLyrics = """
                    Listen to the river running underneath the bridge
                    Golden light is breaking over every distant ridge
                    Every word we spoke is written in the morning air
                    Follow where the wind is leading, meet me over there
                    Time is only water flowing gently to the sea
                    Nothing left between the horizon and thetwo of us to be
                    Stay until the evening lanterns shimmer on the shore
                """.trimIndent()
            )
        )
        writeMinimalMp4Container(
            targetFile = File(folder2, "03_Silicon_Pulse_Session.mkv"),
            durationMs = 26_000L
        )

        val folder3 = File(rootDir, "Project_03_Tour_Archive/Night_04_Tokyo").apply { mkdirs() }
        writeSynthesizedWavWithMetadata(
            targetFile = File(folder3, "04_Midnight_Shibuya_Encore.wav"),
            durationSec = 24.0,
            fundamentalHz = 188.0,
            meta = MediaMetadataInfo(
                title = "Midnight Shibuya",
                artist = "Kaito & Neon Resonance",
                album = "Live in Tokyo 2026",
                author = "Kaito Takahashi",
                genre = "Electronic Rock",
                year = "2026",
                trackNumber = "04",
                durationMs = 24_000L,
                sampleRateHz = 16000,
                embeddedLyrics = """
                    Raindrops on the crossing under neon Tokyo skies
                    Looking for a reflection in a thousand passing eyes
                    Turn the amplifier up until the city shakes
                    We are wide awake before the early morning breaks
                    Sing it from the rooftop till the echoes fill the street
                    Moving together to the rhythm of a single beat
                    Never gonna let the midnight fade away
                """.trimIndent()
            )
        )
        writeMinimalMp4Container(
            targetFile = File(folder3, "04_Midnight_Shibuya_Encore.mp4"),
            durationMs = 24_000L
        )

        onLog("📁 Initialized local studio media tree at: ${rootDir.absolutePath}")
        rootDir
    }

    private fun writeMp3WithId3v2Metadata(targetFile: File, meta: MediaMetadataInfo) {
        val frames = listOf(
            "TIT2" to meta.title,
            "TPE1" to meta.artist,
            "TALB" to meta.album,
            "TCOM" to meta.author,
            "TCON" to meta.genre,
            "TYER" to meta.year,
            "TRCK" to meta.trackNumber,
            "TLEN" to meta.durationMs.toString()
        )
        val frameBos = ByteArrayOutputStream()
        for ((id, text) in frames) {
            val payload = text.toByteArray(Charsets.UTF_8)
            val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
            header.put(id.toByteArray(Charsets.US_ASCII))
            header.putInt(payload.size + 1)
            header.putShort(0)
            frameBos.write(header.array())
            frameBos.write(0)
            frameBos.write(payload)
        }
        if (meta.embeddedLyrics.isNotBlank()) {
            val langAndDesc = byteArrayOf(0x03, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00)
            val lyricBytes = meta.embeddedLyrics.toByteArray(Charsets.UTF_8)
            val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
            header.put("USLT".toByteArray(Charsets.US_ASCII))
            header.putInt(langAndDesc.size + lyricBytes.size)
            header.putShort(0)
            frameBos.write(header.array())
            frameBos.write(langAndDesc)
            frameBos.write(lyricBytes)
        }
        val frameBytes = frameBos.toByteArray()
        val size = frameBytes.size
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x03, 0x00, 0x00,
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte()
        )
        val bos = ByteArrayOutputStream()
        bos.write(id3Header)
        bos.write(frameBytes)
        repeat(64) {
            bos.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte()))
            bos.write(ByteArray(100))
        }
        targetFile.writeBytes(bos.toByteArray())
    }

    private fun writeFlacWithVorbisComments(targetFile: File, meta: MediaMetadataInfo) {
        val comments = buildList {
            add("TITLE=${meta.title}")
            add("ARTIST=${meta.artist}")
            add("ALBUM=${meta.album}")
            add("COMPOSER=${meta.author}")
            add("GENRE=${meta.genre}")
            add("DATE=${meta.year}")
            add("TRACKNUMBER=${meta.trackNumber}")
            add("DURATION_MS=${meta.durationMs}")
            if (meta.embeddedLyrics.isNotBlank()) {
                add("LYRICS=${meta.embeddedLyrics}")
            }
        }
        val vendor = "CodexStudioFLAC".toByteArray(Charsets.UTF_8)
        val commentBos = ByteArrayOutputStream()
        val leInt = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        leInt.clear(); leInt.putInt(vendor.size); commentBos.write(leInt.array())
        commentBos.write(vendor)
        leInt.clear(); leInt.putInt(comments.size); commentBos.write(leInt.array())
        for (c in comments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            leInt.clear(); leInt.putInt(cBytes.size); commentBos.write(leInt.array())
            commentBos.write(cBytes)
        }
        val blockBytes = commentBos.toByteArray()
        val bLen = blockBytes.size
        val bos = ByteArrayOutputStream()
        bos.write("fLaC".toByteArray(Charsets.US_ASCII))
        bos.write(
            byteArrayOf(
                0x84.toByte(),
                ((bLen shr 16) and 0xFF).toByte(),
                ((bLen shr 8) and 0xFF).toByte(),
                (bLen and 0xFF).toByte()
            )
        )
        bos.write(blockBytes)
        targetFile.writeBytes(bos.toByteArray())
    }

    private fun writeSynthesizedWavWithMetadata(
        targetFile: File,
        durationSec: Double,
        fundamentalHz: Double,
        meta: MediaMetadataInfo
    ) {
        val sampleRate = 16000
        val numSamples = (sampleRate * durationSec).toInt()
        val dataBytes = numSamples * 2
        val riffSize = 36 + dataBytes

        val bos = ByteArrayOutputStream(riffSize + 512)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(riffSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes)
        bos.write(header.array())

        val pcmBuf = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val phraseGate = sin(2.0 * PI * 0.35 * t)
            val syllableEnv = if (phraseGate > -0.28) {
                (0.55 + 0.45 * sin(2.0 * PI * 3.4 * t)).coerceAtLeast(0.05)
            } else {
                0.02
            }
            val vibrato = 1.0 + 0.03 * sin(2.0 * PI * 5.5 * t)
            val f0 = fundamentalHz * vibrato * (1.0 + 0.12 * cos(2.0 * PI * 0.6 * t))
            val f1 = f0 * 2.0
            val f2 = f0 * 3.5 + 220.0 * sin(2.0 * PI * 1.1 * t)
            val f3 = 1850.0 + 400.0 * cos(2.0 * PI * 1.9 * t)

            val sample = syllableEnv * (
                0.52 * sin(2.0 * PI * f0 * t) +
                    0.26 * sin(2.0 * PI * f1 * t) +
                    0.14 * sin(2.0 * PI * f2 * t) +
                    0.08 * sin(2.0 * PI * f3 * t)
                )
            val pcm16 = (sample.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
            pcmBuf.putShort(pcm16)
        }
        bos.write(pcmBuf.array())

        val metaTrailer = buildString {
            append("CDXMETA1|")
            append("title=${meta.title}|")
            append("artist=${meta.artist}|")
            append("album=${meta.album}|")
            append("author=${meta.author}|")
            append("genre=${meta.genre}|")
            append("year=${meta.year}|")
            append("track=${meta.trackNumber}|")
            append("durationMs=${meta.durationMs}|")
            append("lyrics=${meta.embeddedLyrics.replace('\n', ';')}|")
            append("syncedFrom=")
        }.toByteArray(Charsets.UTF_8)
        bos.write(metaTrailer)

        targetFile.writeBytes(bos.toByteArray())
    }

    private fun writeMinimalMp4Container(targetFile: File, durationMs: Long) {
        val ftyp = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
        )
        val trailer = "CDXMETA1|title=|artist=|album=|author=|genre=Video|year=2026|track=|durationMs=$durationMs|syncedFrom="
            .toByteArray(Charsets.UTF_8)
        FileOutputStream(targetFile).use {
            it.write(ftyp)
            it.write(trailer)
        }
    }
}
