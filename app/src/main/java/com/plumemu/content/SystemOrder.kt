package com.plumemu.content

import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.SystemOrderSetting
import java.text.Collator
import java.util.Locale

/**
 * What the system order needs to know of one system with games: its id, the name the screen shows,
 * and (from the platform data) who made it and when. [maker] and [year] are null for systems the
 * data gives none (engines, stores, emulators) or does not know.
 */
data class OrderFacts(
    val id: String,
    val name: String,
    val maker: String? = null,
    val year: Int? = null,
)

/**
 * The three stored settings that arrange the system column (`library.system_order`,
 * `library.system_order_manual`, `library.hidden_systems`), apart from the rest of Library so a
 * change to Sort games does not re-arrange the systems.
 */
data class SystemArrangement(
    val order: SystemOrderSetting = SystemOrderSetting.Name,
    val manual: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    companion object {
        fun of(library: LibrarySettings): SystemArrangement = SystemArrangement(library.systemOrder, library.manualSystemOrder, library.hiddenSystems)
    }
}

/**
 * The one place that arranges the systems (Settings › Library › System order):
 * the main screen's system column and Settings' Systems panel both go through it, so they always
 * agree. Recent, Favorites and Apps are not systems and never pass through here.
 *
 * - [SystemOrderSetting.Name]: A to Z by the shown name, as the person's language sorts letters
 *   (case and accents do not split a letter; an O with a macron sits with the O's in English).
 * - [SystemOrderSetting.Maker]: by maker A to Z, then oldest first, then by name; systems without
 *   a maker or a year go last, by name.
 * - [SystemOrderSetting.Manual]: the stored list; systems not in it (a new scan found them) go at
 *   the end, by name. Ids in the list without games now are skipped, not forgotten.
 */
object SystemOrder {
    /** [systems] in the order [arrangement] asks for. Ties always end on the id, so the order never depends on the input's. */
    fun <T> arrange(
        systems: List<T>,
        arrangement: SystemArrangement,
        locale: Locale,
        facts: (T) -> OrderFacts,
    ): List<T> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        val byName = compareBy<T, String>(collator) { facts(it).name }.thenBy { facts(it).id }
        val order =
            when (arrangement.order) {
                SystemOrderSetting.Name -> byName
                SystemOrderSetting.Maker -> {
                    // Without both facts a system goes last, sorted by its name alone.
                    fun known(item: T): OrderFacts? = facts(item).takeIf { it.maker != null && it.year != null }
                    compareBy<T> { known(it) == null }
                        .then(compareBy(collator) { known(it)?.maker.orEmpty() })
                        .thenBy { known(it)?.year ?: 0 }
                        .then(byName)
                }
                SystemOrderSetting.Manual -> {
                    val places = manualPlaces(arrangement.manual)
                    compareBy<T> { places[facts(it).id] ?: Int.MAX_VALUE }.then(byName)
                }
            }
        return systems.sortedWith(order)
    }


    /**
     * [arranged] split into the column's systems and the hidden ones, both kept in order. If every
     * system is hidden (the last shown one lost its games) all of them show: the column never
     * reads as an empty library while there are games.
     */
    fun <T> split(
        arranged: List<T>,
        arrangement: SystemArrangement,
        id: (T) -> String,
    ): Pair<List<T>, List<T>> {
        val (hidden, shown) = arranged.partition { id(it) in arrangement.hidden }
        return if (shown.isEmpty()) arranged to emptyList() else shown to hidden
    }

    /**
     * The manual list to store after the person arranged [arranged] (every system with games, top
     * to bottom). Ids of [stored] that have no games now keep their slots between the others, so a
     * folder that comes back (an SD card) brings its systems back about where they were; systems
     * the stored list did not have yet fill the slots in [arranged]'s order like the others.
     */
    fun manualAfterMove(
        arranged: List<String>,
        stored: List<String>,
    ): List<String> {
        val present = arranged.toSet()
        val next = arranged.iterator()
        val out = ArrayList<String>(arranged.size + stored.size)
        for (id in stored.distinct()) {
            if (id in present) {
                if (next.hasNext()) out += next.next()
            } else {
                out += id
            }
        }
        // Not forEachRemaining / putIfAbsent: both need API 24 (minSdk is 23).
        while (next.hasNext()) out += next.next()
        return out
    }

    private fun manualPlaces(manual: List<String>): Map<String, Int> {
        val places = HashMap<String, Int>(manual.size)
        manual.forEachIndexed { index, id -> if (id !in places) places[id] = index }
        return places
    }
}
