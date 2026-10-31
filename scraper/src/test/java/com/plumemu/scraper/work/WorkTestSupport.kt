package com.plumemu.scraper.work

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.hash.toLowerHex
import com.plumemu.scraper.media.SavedMedia
import mockwebserver3.MockResponse
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/** The Application of worker tests: a [ScraperHost] whose answer each test sets. */
class TestHostApp : Application(), ScraperHost {
    @Volatile var work: ScraperWork? = null

    override suspend fun scraperWork(): ScraperWork? = work
}

/**
 * What the library is to the run: the requests not yet delivered by the main pass, in a stable
 * order, and, like the library's disc pass, the matched ones whose disc or cartridge is still to
 * fetch (a support ref and no support picture delivered, or a fresh request), each once a run.
 */
internal class FakeScrapeSource(private val requests: List<ScrapeRequest>) : ScrapeSource {
    class Delivery(val request: ScrapeRequest, val result: ScrapeResult, val media: List<SavedMedia>, val pass: ScrapePass)

    val deliveries = CopyOnWriteArrayList<Delivery>()
    val deliveredKeys: List<String> get() = deliveries.map { it.request.rom.key }

    /** The main pass's deliveries only (lookup and box front). */
    val mainDeliveries: List<Delivery> get() = deliveries.filter { it.pass == ScrapePass.Main }

    /** The disc pass's deliveries only. */
    val supportDeliveries: List<Delivery> get() = deliveries.filter { it.pass == ScrapePass.Support }

    /** Called before each answer of [pending], for tests that add games mid-run. */
    @Volatile var onPending: () -> Unit = {}

    /** Extra games [pending] offers after [requests] (a scan adding games during the run). */
    val added = CopyOnWriteArrayList<ScrapeRequest>()

    override suspend fun pending(limit: Int): List<ScrapeRequest> {
        onPending()
        val done = mainDeliveries.map { it.request.rom.key }.toSet()
        return (requests + added).filter { it.rom.key !in done }.take(limit)
    }

    override suspend fun pendingSupport(limit: Int): List<ScrapeRequest> {
        val tried = supportDeliveries.map { it.request.rom.key }.toSet()
        return mainDeliveries
            .filter { it.request.rom.key !in tried && it.discStillToFetch() }
            .map { it.request }
            .sortedBy { request -> (requests + added).indexOfFirst { it.rom.key == request.rom.key } }
            .take(limit)
    }

    private fun Delivery.discStillToFetch(): Boolean {
        val matched = result as? ScrapeResult.Matched ?: return false
        if (matched.media.none { it.kind == MediaKind.Support }) return false
        return request.fresh || media.none { it.kind == MediaKind.Support }
    }

    override suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass) {
        deliveries += Delivery(request, result, media, pass)
    }

    /** The rom keys of the games the run called [failedAsking] for (a server error or a timeout), in order. */
    val failedKeys = CopyOnWriteArrayList<String>()

    override suspend fun failedAsking(request: ScrapeRequest) {
        failedKeys += request.rom.key
    }
}

/** An opaque PNG (no alpha, so a low-RAM decode can be RGB_565); needs native graphics. */
internal fun pngBytes(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    bitmap.eraseColor(Color.rgb(51, 102, 153))
    val out = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
    bitmap.recycle()
    return out.toByteArray()
}

internal fun md5Of(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).toLowerHex()

/** A plain JPEG, as ScreenScraper answers `outputformat=jpg`. */
internal fun jpegBytes(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    bitmap.eraseColor(Color.rgb(51, 102, 153))
    val out = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) { "JPEG encoding failed" }
    bitmap.recycle()
    return out.toByteArray()
}

internal fun imageResponse(bytes: ByteArray): MockResponse = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "image/png")
    .body(Buffer().write(bytes))
    .build()
