package com.plumemu.update

import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.platforms.PlatformDataVersions
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * The platform data check that rides on the update check ([Updates]): fetches the platform data
 * manifest and, when a newer file is published, downloads, verifies and stores it. The order of the
 * checks is the contract with the publisher. Any failure or skip keeps the current
 * data and logs one line; nothing throws out of [check], and nothing here touches the APK update's
 * record, gate or screen. The new data is used at the next app start ([selectPlatformData]).
 *
 * A blank [manifestUrl] (a build from source) asks nobody.
 */
class PlatformDataUpdater(
    private val http: UpdateHttp,
    private val manifestUrl: String,
    private val installedVersionCode: () -> Long,
    /** Read at the first check, off the main thread. */
    private val bundled: () -> PlatformDataVersion,
    private val store: PlatformDataStore,
    private val verifier: DataSignatureVerifier,
    private val io: CoroutineDispatcher,
    private val info: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    /** [label] is "daily" or "manual", for the log lines. */
    suspend fun check(label: String) {
        if (manifestUrl.isBlank()) return
        try {
            run(label)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateNetException) {
            log("Platform data check ($label) failed: ${e.message}")
        } catch (e: Exception) {
            log("Platform data check ($label) failed unexpectedly (${e.javaClass.simpleName})")
        }
    }

    private suspend fun run(label: String) {
        val text = http.text(manifestUrl, PlatformDataManifests.MAX_MANIFEST_BYTES)
        val manifest =
            when (val read = PlatformDataManifests.read(text, manifestUrl)) {
                is PlatformDataManifests.Result.Rejected -> return log("Platform data check ($label) skipped: ${read.reason}")
                is PlatformDataManifests.Result.Ok -> read.manifest
            }
        val (bundledVersion, storedVersion) = withContext(io) { bundled().dataVersion to store.readVersion() }
        val have = maxOf(bundledVersion, storedVersion ?: 0)
        if (manifest.dataVersion <= have) return info("Platform data check ($label): ${manifest.dataVersion} is not newer than $have")
        if (manifest.formatVersion != PlatformDataVersions.FORMAT_VERSION) {
            return log("Platform data check ($label) skipped: version ${manifest.dataVersion} has format ${manifest.formatVersion}, this app reads ${PlatformDataVersions.FORMAT_VERSION}")
        }
        val installed = withContext(io) { installedVersionCode() }
        if (manifest.minVersionCode > installed) {
            return log("Platform data check ($label) skipped: version ${manifest.dataVersion} needs build ${manifest.minVersionCode}, this is $installed")
        }
        val bytes = download(manifest) ?: return log("Platform data check ($label) failed: the file is not ${manifest.size} bytes")
        val (problem, json) = withContext(io) { unpack(manifest, bytes) }
        if (json == null) return log("Platform data check ($label) failed: $problem")
        // A separate step: a check cancelled (Plumemu left the front) stops here and stores nothing.
        val writeProblem = withContext(io) { write(manifest, json) }
        if (writeProblem != null) return log("Platform data check ($label) failed: $writeProblem")
        info("Platform data ${manifest.dataVersion} stored; it is used from the next start")
    }

    /** The file's bytes, or null when it is not the size the manifest says (read at most one byte past it). */
    private suspend fun download(manifest: PlatformDataManifest): ByteArray? {
        val body = http.open(manifest.fileUrl)
        try {
            // Read and closed on the io thread, as UpdateBody asks.
            return withContext(io) {
                body.use {
                    if (it.length >= 0 && it.length != manifest.size) {
                        null
                    } else {
                        it.stream.readUpTo(manifest.size + 1).takeIf { bytes -> bytes.size.toLong() == manifest.size }
                    }
                }
            }
        } catch (e: CancellationException) {
            body.close()
            throw e
        }
    }

    /** The unpacked, loader-checked JSON text; or null with the reason for the log. Runs the checks after the size, in contract order. */
    private fun unpack(
        manifest: PlatformDataManifest,
        gz: ByteArray,
    ): Pair<String?, String?> {
        if (MessageDigest.getInstance("SHA-256").digest(gz).toHex() != manifest.sha256) return "SHA-256 of the file differs from the manifest" to null
        val signature = CertificateSignatureVerifier.decode(manifest.signature) ?: return "signature is not base64" to null
        if (!verifier.verifies(manifest.signedText.toByteArray(Charsets.UTF_8), signature)) return "signature not made by this app's key" to null
        val json =
            try {
                GZIPInputStream(gz.inputStream()).use { it.readUpTo(PlatformDataManifests.MAX_JSON_BYTES + 1L) }
            } catch (e: IOException) {
                return "file is not valid gzip (${e.javaClass.simpleName})" to null
            }
        if (json.size > PlatformDataManifests.MAX_JSON_BYTES) return "unpacked file is over ${PlatformDataManifests.MAX_JSON_BYTES} bytes" to null
        val text = String(json, Charsets.UTF_8)
        val loaded = PlatformLoader.load(text, "downloaded platform data ${manifest.dataVersion}")
        if (loaded !is PlatformLoadResult.Loaded) return "the platform loader rejected the data (its own line says why)" to null
        return null to text
    }

    /** Null once stored; else the reason. */
    private fun write(
        manifest: PlatformDataManifest,
        text: String,
    ): String? =
        try {
            store.write(StoredPlatformData(manifest.dataVersion, manifest.released, manifest.sha256, text))
            null
        } catch (e: IOException) {
            "could not write it (${e.javaClass.simpleName})"
        }
}

/** Reads at most [limit] bytes: one more than the caller allows says "too long" without reading it all. */
internal fun InputStream.readUpTo(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}
