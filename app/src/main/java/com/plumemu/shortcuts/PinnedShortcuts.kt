package com.plumemu.shortcuts

import android.content.Intent
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coredata.db.PinnedShortcutDao
import com.plumemu.coredata.db.PinnedShortcutKey
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.platforms.PlatformCatalog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the confirm screen offers for one pin request. */
sealed interface PinOffer {
    /** "Add <label> to <system>?": a shortcut Plumemu can list. */
    data class Ask(
        val request: PinRequest,
        val shortcut: ShortcutFacts,
        /** The name the game gets: the shortcut's short label (its id when the app gave none). */
        val label: String,
        val systemId: String,
        /** The list's name as the main screen shows it ("Steam", "Windows", "Shortcuts"). */
        val systemName: String,
    ) : PinOffer {
        override fun toString(): String = "Ask(${shortcut.packageName}, $systemId)"
    }

    /** Nothing to ask: the screen says why and closes. */
    data class Refused(val reason: PinRefusal) : PinOffer
}

enum class PinRefusal {
    /** A widget: Plumemu holds games and app shortcuts only. */
    Widget,

    /** The request was answered already, or its app went away (or there was none: below API 26, a bad intent). */
    Expired,

    /** The platform data is not read yet (or could not be): nothing can be filed. */
    NotReady,
}

/** What adding did; the screen closes on [Added] and shows a message otherwise. */
sealed interface PinAddOutcome {
    data class Added(val gameId: Long, val isNew: Boolean) : PinAddOutcome

    /** Android did not pin it (the request expired meanwhile); nothing was stored. */
    data object NotAccepted : PinAddOutcome

    /** Pinned, but the library could not store the game; asking again from the app adds it. */
    data object NotSaved : PinAddOutcome
}

/**
 * Pinned shortcuts as games: what a pin request offers, the add (Android's accept,
 * then Plumemu's own record), and the "unavailable" mark that follows the apps' changes. The add
 * runs in [scope], the app's own, so leaving the screen half-way never leaves a pin without its
 * game. Play on a shortcut game (`AppGameActions`) starts it through the same [host].
 */
class PinnedShortcuts(
    private val dao: () -> PinnedShortcutDao,
    private val catalog: suspend () -> PlatformCatalog?,
    /** Every `LauncherApps` call; also Play's (`AppGameActions`), so the app has one host. */
    internal val host: ShortcutHost,
    /** The "Shortcuts" list's name from the app's strings (the stored fallback and the screen's words). */
    private val shortcutsName: String,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
) {
    private val isListening = AtomicBoolean(false)

    /** Reads the request an intent carries and where its shortcut would be listed. */
    suspend fun offerOf(intent: Intent): PinOffer {
        val request =
            try {
                host.pinRequest(intent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // The activity is exported: whatever an app put in the intent is no request.
                log("Pin request unreadable (${e.javaClass.simpleName})")
                null
            }
        if (request == null || !request.isValid) {
            log("Pin request refused: ${if (request == null) "none in the intent" else "no longer valid"}")
            return PinOffer.Refused(PinRefusal.Expired)
        }
        if (request.kind != PinKind.Shortcut) {
            log("Pin request refused: a widget")
            return PinOffer.Refused(PinRefusal.Widget)
        }
        val shortcut = request.shortcut ?: return PinOffer.Refused(PinRefusal.Expired).also { log("Pin request refused: no shortcut in it") }
        val data = withContext(io) { catalog() }
        if (data == null) {
            // Filing without the platform data would put a Steam game under Shortcuts for good.
            log("Pin request refused: the platform data is not read yet")
            return PinOffer.Refused(PinRefusal.NotReady)
        }
        val systemId = ShortcutFiling(data).systemFor(shortcut.packageName)
        val systemName = if (systemId == ShortcutFiling.SHORTCUTS) shortcutsName else data.system(systemId)?.displayName ?: systemId
        return PinOffer.Ask(request, shortcut, shortcut.shortLabel.trim().ifEmpty { shortcut.id }, systemId, systemName)
    }

    /** Accepts the pin, then lists the game (once per shortcut); never throws. */
    suspend fun add(offer: PinOffer.Ask): PinAddOutcome =
        scope.async(io) {
            if (!acceptOf(offer.request)) {
                log("Pin of ${offer.shortcut.packageName} not accepted by Android")
                return@async PinAddOutcome.NotAccepted
            }
            if (!isPinnedNow(offer.shortcut)) {
                // A request an app made itself (its own binder) "accepts" without Android pinning
                // anything; only a shortcut Android lists as pinned for Plumemu becomes a game.
                log("Pin of ${offer.shortcut.packageName} accepted but not pinned by Android: not stored")
                return@async PinAddOutcome.NotAccepted
            }
            try {
                val added = dao().add(newShortcut(offer))
                log("Pinned shortcut of ${offer.shortcut.packageName} listed as game ${added.gameId} in ${offer.systemId} (new: ${added.isNew})")
                PinAddOutcome.Added(added.gameId, added.isNew)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Pinned shortcut of ${offer.shortcut.packageName} could not be stored (${e.javaClass.simpleName})")
                PinAddOutcome.NotSaved
            }
        }.await()

    /**
     * Marks each stored shortcut available or not from Android's pinned list (after a start or a
     * package change), and starts following the apps' changes. Does nothing while Android does not
     * answer (Plumemu is not the home app); a failure is logged, never thrown.
     */
    suspend fun checkAvailability() {
        if (isListening.compareAndSet(false, true) && !host.listen(::onShortcutsChanged)) isListening.set(false)
        guarded("check pinned shortcuts") {
            val keys = withContext(io) { dao().keys() }
            for ((app, records) in keys.groupBy { it.packageName to it.userSerial }) {
                val pinned = withContext(io) { host.pinnedShortcuts(app.first, app.second) } ?: continue
                apply(records, pinned)
            }
        }
    }

    /** An app's shortcuts changed (or the app was removed: an empty list). */
    internal fun onShortcutsChanged(
        packageName: String,
        userSerial: Long,
        shortcuts: List<ShortcutFacts>,
    ) {
        scope.launch {
            guarded("follow the shortcuts of $packageName") {
                apply(withContext(io) { dao().keysOf(packageName, userSerial) }, shortcuts)
            }
        }
    }

    private suspend fun apply(
        records: List<PinnedShortcutKey>,
        current: List<ShortcutFacts>,
    ) {
        for ((gameId, isAvailable) in availabilityChanges(records, current)) {
            withContext(io) { dao().setAvailable(gameId, isAvailable) }
            log("Shortcut game $gameId is ${if (isAvailable) "available again" else "unavailable"}")
        }
    }

    private fun isPinnedNow(shortcut: ShortcutFacts): Boolean =
        try {
            host.pinnedShortcuts(shortcut.packageName, shortcut.userSerial, listOf(shortcut.id))
                ?.any { it.id == shortcut.id && it.packageName == shortcut.packageName && it.isPinned } == true
        } catch (e: RuntimeException) {
            log("Pinned check failed (${e.javaClass.simpleName})")
            false
        }

    private fun acceptOf(request: PinRequest): Boolean =
        try {
            request.isValid && request.accept()
        } catch (e: RuntimeException) {
            log("Pin request failed (${e.javaClass.simpleName})")
            false
        }

    private fun newShortcut(offer: PinOffer.Ask) =
        NewPinnedShortcut(
            packageName = offer.shortcut.packageName,
            shortcutId = offer.shortcut.id,
            userSerial = offer.shortcut.userSerial,
            label = offer.label,
            sortName = TitleCleaner.sortName(offer.label),
            systemId = offer.systemId,
            systemName = offer.systemName,
            addedAt = clock(),
        )

    private suspend fun guarded(
        what: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Could not $what (${e.javaClass.simpleName})")
        }
    }

    companion object {
        /**
         * Which records change: a shortcut is available while its app still lists it pinned and
         * enabled; a disabled or vanished one is marked unavailable, never deleted, so its favourite
         * and play time stay (the app may enable it again; the person removes it with Hide).
         */
        internal fun availabilityChanges(
            records: List<PinnedShortcutKey>,
            current: List<ShortcutFacts>,
        ): List<Pair<Long, Boolean>> =
            records.mapNotNull { record ->
                val now = current.any { it.id == record.shortcutId && it.packageName == record.packageName && it.isPinned && it.isEnabled }
                if (now == record.isAvailable) null else record.gameId to now
            }
    }
}
