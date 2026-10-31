package com.plumemu.update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Why an update did not reach the installer, or the installer refused it. Each has one plain message. */
enum class UpdateFailure {
    Offline,
    Timeout,
    Http,

    /** The cache could not be written (full, or removed under us). */
    Storage,
    SizeMismatch,
    HashMismatch,

    /** Android could not read the file as an APK. */
    NotAnApk,
    WrongPackage,
    WrongVersion,
    WrongSigner,
    InstallFailed,
}

/** How a download ended. */
sealed interface DownloadResult {
    /** The file has the manifest's size and SHA-256; nothing else is checked yet. */
    data class Done(val file: File) : DownloadResult

    /** The file is already deleted. */
    data class Failed(val failure: UpdateFailure, val detail: String) : DownloadResult
}

/**
 * Downloads a release's APK into [dir] (the app's cache) and checks its size and SHA-256 while it
 * is written. Any failure or a cancel deletes the file; only a checked file is kept, as
 * `plumemu-<versionCode>.apk`. Progress is reported each time the whole percent changes.
 */
class ApkDownloader(
    private val http: UpdateHttp,
    private val dir: File,
    private val io: CoroutineDispatcher,
) {
    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (done: Long, total: Long) -> Unit,
    ): DownloadResult =
        withContext(io) {
            dir.mkdirs()
            val part = File(dir, "plumemu-${manifest.versionCode}.apk.part")
            val done = File(dir, "plumemu-${manifest.versionCode}.apk")
            var kept = false
            try {
                val result = copyAndCheck(manifest, part, onProgress)
                if (result != null) return@withContext result
                if (!part.renameTo(done)) return@withContext DownloadResult.Failed(UpdateFailure.Storage, "could not rename the download")
                kept = true
                DownloadResult.Done(done)
            } finally {
                if (!kept) {
                    part.delete()
                    done.delete()
                }
            }
        }

    /** Null when the file in [part] is whole and matches; a failure otherwise. */
    private suspend fun copyAndCheck(
        manifest: UpdateManifest,
        part: File,
        onProgress: (Long, Long) -> Unit,
    ): DownloadResult.Failed? {
        val digest = MessageDigest.getInstance("SHA-256")
        val count: Long
        try {
            val copied =
                http.open(manifest.apkUrl).use { body ->
                    if (body.length >= 0 && body.length != manifest.size) {
                        return DownloadResult.Failed(UpdateFailure.SizeMismatch, "server says ${body.length} bytes, manifest ${manifest.size}")
                    }
                    // Cancel ends a read stalled on the network at once (`use` closes the body afterwards, here).
                    closeOnCancel(body::cancel) { copy(body, part, manifest.size, digest, onProgress) }
                }
            count = copied ?: return DownloadResult.Failed(UpdateFailure.SizeMismatch, "more than ${manifest.size} bytes")
        } catch (e: UpdateNetException) {
            // A cancelled call fails with an IOException too: a cancel is not a network failure.
            currentCoroutineContext().ensureActive()
            val failure =
                when (e.failure) {
                    NetFailure.Offline -> UpdateFailure.Offline
                    NetFailure.Timeout -> UpdateFailure.Timeout
                    NetFailure.Http -> UpdateFailure.Http
                }
            return DownloadResult.Failed(failure, "download: ${e.message}")
        } catch (e: StorageException) {
            return DownloadResult.Failed(UpdateFailure.Storage, "cache write: ${e.cause?.javaClass?.simpleName}")
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            return DownloadResult.Failed(UpdateFailure.Storage, "cache file: ${e.javaClass.simpleName}")
        }
        if (count != manifest.size) return DownloadResult.Failed(UpdateFailure.SizeMismatch, "got $count bytes, manifest ${manifest.size}")
        val hex = digest.digest().toHex()
        if (hex != manifest.sha256) return DownloadResult.Failed(UpdateFailure.HashMismatch, "sha256 $hex, manifest ${manifest.sha256}")
        return null
    }

    /**
     * Copies [body] into [part], hashing as it goes; the bytes read, or null as soon as there are
     * more than [size] (a wrong file is not read to its end).
     */
    private suspend fun copy(
        body: UpdateBody,
        part: File,
        size: Long,
        digest: MessageDigest,
        onProgress: (Long, Long) -> Unit,
    ): Long? {
        var count = 0L
        part.outputStream().use { out ->
            val buffer = ByteArray(BUFFER_BYTES)
            var lastPercent = -1L
            onProgress(0, size)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = body.stream.read(buffer)
                if (read < 0) break
                count += read
                if (count > size) return null
                digest.update(buffer, 0, read)
                writeOrStorage { out.write(buffer, 0, read) }
                val percent = count * 100 / size
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(count, size)
                }
            }
            writeOrStorage { out.fd.sync() }
        }
        return count
    }

    /** Deletes every file a download left (a process that died mid-download, an install that ended). */
    fun clearLeftovers() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private class StorageException(cause: IOException) : IOException(cause)

    private inline fun writeOrStorage(block: () -> Unit) =
        try {
            block()
        } catch (e: IOException) {
            throw StorageException(e)
        }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

internal fun ByteArray.toHex(): String =
    buildString(size * 2) {
        for (b in this@toHex) {
            val v = b.toInt() and 0xff
            append(HEX_DIGITS[v ushr 4])
            append(HEX_DIGITS[v and 0x0f])
        }
    }
