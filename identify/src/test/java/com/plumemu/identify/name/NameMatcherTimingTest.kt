package com.plumemu.identify.name

import com.plumemu.identify.pack.TestPacks
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * Measures the matcher on a generated 5,000-title pack: index memory and time per lookup over
 * 10,000 names (a third exact, a third with one typo, a third made up). Prints the numbers for
 * the work log; fails only far above the 1 ms target, so a busy machine does not fail it.
 */
class NameMatcherTimingTest {
    @Test fun fiveThousandTitlesTenThousandLookups() {
        val (bytes, names) = TestPacks.many(5_000)
        val pack = TestPacks.open(bytes)
        val random = Random(5)
        val queries = List(10_000) { i ->
            val name = names[random.nextInt(names.size)]
            when (i % 3) {
                0 -> name
                1 -> withTypo(name, random)
                else -> TestPacks.madeUpTitle(random)
            }
        }

        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val matcher = NameMatcher(pack)
        val buildStart = System.nanoTime()
        matcher.match("warm up the index")
        val buildMs = (System.nanoTime() - buildStart) / 1e6
        System.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()

        repeat(2_000) { matcher.match(queries[it]) } // JIT warm-up
        var found = 0
        val start = System.nanoTime()
        for (q in queries) if (matcher.match(q) != null) found++
        val perLookupMs = (System.nanoTime() - start) / 1e6 / queries.size

        println(
            String.format(
                Locale.ROOT,
                "NameMatcher 5,000 titles: index arrays %d KB, heap delta %d KB, build %.1f ms, %.4f ms per lookup, %d of %d matched",
                matcher.indexBytes() / 1024, (after - before) / 1024, buildMs, perLookupMs, found, queries.size,
            ),
        )
        assertTrue("$perLookupMs ms per lookup", perLookupMs < 2.0)
        assertTrue("exact names and one-typo names should mostly match: $found", found > queries.size / 2)
    }

    private fun withTypo(name: String, random: Random): String {
        val letters = name.indices.filter { name[it].isLetter() && it > 0 }
        val at = letters[random.nextInt(letters.size)]
        return name.substring(0, at) + name.substring(at + 1)
    }
}
