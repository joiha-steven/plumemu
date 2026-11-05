package com.plumemu.identify.roundtrip

import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.IdentifyRequest
import com.plumemu.identify.Identifier
import com.plumemu.identify.Method
import com.plumemu.identify.name.MatchKey
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.identify.pack.NamePack
import java.util.Locale

/** How one variant file name came out. */
internal enum class Verdict { RIGHT, WRONG, UNKNOWN, AMBIGUOUS }

internal class Failure(val system: String, val kind: String, val fileName: String, val expected: String, val shown: String, val verdict: Verdict)

/** Counts for one system and kind. [unknown] is what is left of [total]. */
internal class Tally {
    var total = 0
    var right = 0
    var wrong = 0
    var ambiguous = 0
    val unknown get() = total - right - wrong - ambiguous

    fun add(other: Tally) {
        total += other.total
        right += other.right
        wrong += other.wrong
        ambiguous += other.ambiguous
    }

    fun percent(): String = if (total == 0) "-" else String.format(Locale.ROOT, "%.1f%%", 100.0 * right / total)
}

internal class SystemResult(val system: String, val titles: Int, val sampled: Int, val byKind: Map<String, Tally>, val failures: List<Failure>)

/**
 * The name-matching path of a scan for a file with no fingerprint: [TitleCleaner.clean] of the base
 * name, then [Identifier.identify] with that title and its regions and no readable content, which is
 * `NameCandidates` and `NameMatcher` on the system's pack (what library's identify pass sends).
 */
internal object RoundTrip {
    fun run(system: String, pack: NamePack, identifier: Identifier, cap: Int): SystemResult {
        val all = pack.allTitles().toList()
        val distinct = all.distinctBy { it.name }
        // How many titles of the whole pack share each key: a name cannot tell those apart.
        val keyCount = HashMap<String, Int>()
        for (t in distinct) keyCount.merge(keyOf(t.name), 1, Int::plus)
        val step = if (distinct.size <= cap) 1 else (distinct.size + cap - 1) / cap
        val sample = distinct.filterIndexed { i, _ -> i % step == 0 }
        val perTitle = sample.parallelStream().map { title ->
            val region = title.regions.firstOrNull() ?: "USA"
            NameVariants.of(title.name, region).map { v -> judge(system, identifier, title.name, v, keyCount) }
        }.toList()
        val byKind = LinkedHashMap<String, Tally>()
        val failures = ArrayList<Failure>()
        for (outcomes in perTitle) for (failure in outcomes) {
            val t = byKind.getOrPut(failure.kind) { Tally() }
            t.total++
            when (failure.verdict) {
                Verdict.RIGHT -> t.right++
                Verdict.WRONG -> t.wrong++
                Verdict.AMBIGUOUS -> t.ambiguous++
                Verdict.UNKNOWN -> Unit
            }
            if (failure.verdict != Verdict.RIGHT) failures += failure
        }
        return SystemResult(system, distinct.size, sample.size, byKind, failures)
    }

    private fun judge(system: String, identifier: Identifier, original: String, v: Variant, keyCount: Map<String, Int>): Failure {
        val base = v.fileName.substringBeforeLast('.')
        val cleaned = TitleCleaner.clean(base)
        val id = identifier.identify(IdentifyRequest(system, v.fileName, cleaned.title, cleaned.regions.map { it.noIntroName }))
        val shown = if (id.method == Method.NAME_MATCH) id.title else ""
        val verdict = when {
            shown.isEmpty() -> Verdict.UNKNOWN
            shown == original -> Verdict.RIGHT
            keyOf(shown) == keyOf(original) -> if ((keyCount[keyOf(original)] ?: 1) > 1) Verdict.AMBIGUOUS else Verdict.RIGHT
            else -> Verdict.WRONG
        }
        return Failure(system, v.kind, v.fileName, original, shown, verdict)
    }

    private fun keyOf(title: String): String = MatchKey.of(title).ifEmpty { title.lowercase(Locale.ROOT) }

    /** A silent log: a pack that fails to open is reported by the test, not by stderr noise. */
    val quietLog = IdentifyLog { }
}
