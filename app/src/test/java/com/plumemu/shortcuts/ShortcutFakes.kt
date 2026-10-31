package com.plumemu.shortcuts

import android.content.Intent
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** The shipped platform data, read from the source tree as `PlayWithFileTypeTest` does. */
internal val shippedCatalog: PlatformCatalog by lazy {
    (PlatformLoader.load(File("../platforms/src/main/assets/platforms.json").readText(), "shipped") as PlatformLoadResult.Loaded).catalog
}

/** A pin request whose answer the test sets; counts its accepts. */
internal class FakePinRequest(
    override val shortcut: ShortcutFacts?,
    override val kind: PinKind = PinKind.Shortcut,
    var valid: Boolean = true,
    var accepts: Boolean = true,
    /** Where Android records the pin on accept; null for a forged request that pins nothing. */
    private val pinsInto: MutableList<ShortcutFacts>? = null,
) : PinRequest {
    val acceptCalls = AtomicInteger()

    override val isValid: Boolean get() = valid

    override fun accept(): Boolean {
        acceptCalls.incrementAndGet()
        if (!valid) return false
        valid = false
        if (accepts) shortcut?.let { pinsInto?.add(it) }
        return accepts
    }
}

/**
 * A host that hands out [request], answers [pinned] per package (null: "not the home app"), or throws
 * [failure]; a start answers [startAnswer] and is noted in [starts].
 */
internal class FakeShortcutHost(
    var request: PinRequest? = null,
    val pinned: MutableMap<String, List<ShortcutFacts>?> = mutableMapOf(),
    var failure: RuntimeException? = null,
) : ShortcutHost {
    var listener: ((String, Long, List<ShortcutFacts>) -> Unit)? = null
    val listenCalls = AtomicInteger()

    override fun pinRequest(intent: Intent): PinRequest? {
        failure?.let { throw it }
        return request
    }

    override fun pinnedShortcuts(packageName: String, userSerial: Long, ids: List<String>?): List<ShortcutFacts>? {
        failure?.let { throw it }
        val listed = if (packageName in pinned) pinned[packageName] else acceptedPins.filter { it.packageName == packageName }
        return listed?.filter { ids == null || it.id in ids }
    }

    /** What Android pinned through a real-looking request: [FakePinRequest] adds its shortcut here on accept. */
    val acceptedPins = java.util.concurrent.CopyOnWriteArrayList<ShortcutFacts>()

    var startAnswer: ShortcutStart = ShortcutStart.Started
    val starts = java.util.concurrent.CopyOnWriteArrayList<Triple<String, String, Long>>()

    override fun startShortcut(packageName: String, id: String, userSerial: Long): ShortcutStart {
        failure?.let { throw it }
        starts.add(Triple(packageName, id, userSerial))
        return startAnswer
    }

    override fun listen(onChanged: (String, Long, List<ShortcutFacts>) -> Unit): Boolean {
        listenCalls.incrementAndGet()
        listener = onChanged
        return true
    }
}

internal fun gameNativeShortcut(
    id: String = "game_274190",
    label: String = "Broforce",
    packageName: String = "app.gamenative",
) = ShortcutFacts(packageName = packageName, id = id, shortLabel = label, userSerial = 0)
