package com.swordfish.lemuroid.lib.saves

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SaveStateBundle {
    const val CURRENT_FORMAT = 1
    const val APP_ID = "lemuroid"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_STATE = "state.bin"
    private const val ENTRY_METADATA = "metadata.json"
    private const val ENTRY_PREVIEW = "preview.jpg"

    private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 1L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 32
    private const val BUFFER_SIZE = 8 * 1024

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Serializable
    data class Manifest(
        val format: Int = CURRENT_FORMAT,
        val app: String = APP_ID,
        val coreName: String,
        val systemId: String,
        val gameFileName: String,
        val slot: Int,
        val exportedAt: Long = System.currentTimeMillis(),
    )

    fun write(
        out: OutputStream,
        manifest: Manifest,
        saveState: SaveState,
        previewJpeg: ByteArray? = null,
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_METADATA))
            zip.write(
                json.encodeToString(SaveState.Metadata.serializer(), saveState.metadata).toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_STATE))
            zip.write(saveState.state)
            zip.closeEntry()

            if (previewJpeg != null && previewJpeg.isNotEmpty()) {
                zip.putNextEntry(ZipEntry(ENTRY_PREVIEW))
                zip.write(previewJpeg)
                zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream): Result<Triple<Manifest, SaveState, ByteArray?>> =
        runCatching {
            var manifest: Manifest? = null
            var metadata: SaveState.Metadata? = null
            var stateBytes: ByteArray? = null
            var previewBytes: ByteArray? = null
            var entryCount = 0

            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        throw IllegalArgumentException("Save state archive contains too many entries")
                    }

                    when (entry.name) {
                        ENTRY_MANIFEST -> {
                            val text = readBoundedBytes(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8)
                            manifest = json.decodeFromString(Manifest.serializer(), text)
                        }
                        ENTRY_METADATA -> {
                            val text = readBoundedBytes(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8)
                            metadata = json.decodeFromString(SaveState.Metadata.serializer(), text)
                        }
                        ENTRY_STATE -> {
                            stateBytes = readBoundedBytes(zip, MAX_ENTRY_BYTES)
                        }
                        ENTRY_PREVIEW -> {
                            previewBytes = readBoundedBytes(zip, MAX_ENTRY_BYTES)
                        }
                    }
                    zip.closeEntry()
                }
            }

            val parsedManifest = manifest ?: throw IllegalArgumentException("Missing manifest.json")
            if (parsedManifest.app != APP_ID ||
                parsedManifest.format != CURRENT_FORMAT ||
                parsedManifest.coreName.isBlank()
            ) {
                throw IllegalArgumentException("Unsupported save state manifest")
            }

            val parsedMetadata = metadata ?: throw IllegalArgumentException("Missing metadata.json")
            val parsedState = stateBytes ?: throw IllegalArgumentException("Missing state.bin")
            if (parsedState.isEmpty()) {
                throw IllegalArgumentException("Empty state.bin")
            }

            Triple(parsedManifest, SaveState(parsedState, parsedMetadata), previewBytes)
        }

    private fun readBoundedBytes(
        input: InputStream,
        maxBytes: Long,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("ZIP entry exceeds maximum allowed size ($maxBytes bytes)")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
