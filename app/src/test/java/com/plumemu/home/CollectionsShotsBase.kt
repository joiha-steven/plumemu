package com.plumemu.home

import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction

/** The Collections screens' fixtures: five collections (Recent, Most played, Recently added, "RPG" with games of three systems, an empty "Couch co-op"). */
internal object CollectionsShotsBase {
    private val down = Action.Move(Direction.Down)

    private fun rows(prefix: String, system: String, abbr: String, titles: List<String>) =
        titles.mapIndexed { i, title -> GameRowModel("$prefix$i", title, "USA", systemAbbr = abbr, systemId = system) }

    private val snes = rows("s", "snes", "SNES", listOf("Chrono Trigger", "Secret of Mana", "EarthBound", "Super Metroid"))
    private val gba = rows("g", "gba", "GBA", listOf("Golden Sun", "Advance Wars"))
    private val nes = rows("n", "nes", "NES", listOf("Final Fantasy", "Dragon Quest"))

    fun content(
        recent: Int = 3,
        mostPlayed: Int = 4,
        recentlyAdded: Int = 2,
        rpgOpen: Boolean = true,
    ): HomeContent {
        val mixed = listOf(snes[0], gba[0], nes[0], snes[1], gba[1], nes[1], snes[2], snes[3])
        return HomeContent.of(
            recent = mixed.take(recent),
            favorites = snes.take(2),
            apps = emptyList(),
            systems = listOf(SystemGames("snes", "Super Nintendo", snes, code = "SNES"), SystemGames("gba", "Game Boy Advance", gba, code = "GBA"), SystemGames("nes", "Nintendo Entertainment System", nes, code = "NES")),
            otherFolders =
                listOf(
                    FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, mostPlayed), mixed.take(mostPlayed)),
                    FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, recentlyAdded), mixed.take(recentlyAdded)),
                    FolderRows(Folder.Own(7, "RPG", mixed.size), if (rpgOpen) mixed else emptyList()),
                    FolderRows(Folder.Own(8, "Couch co-op", 0), emptyList()),
                ),
        )
    }

    /** Collections focused in the system column, its folders beside it. */
    val column = listOf(Action.TapItem(Column.Systems, 1))

    /** Inside the folders page, the cursor on the own collection "RPG" (row 3). */
    val folders = listOf(Action.TapItem(Column.Systems, 1), Action.Enter, down, down, down)

    /** "RPG" opened (a grid, or a list when it is not in the grid ids). */
    val rpg = folders + Action.Enter

    /** The empty own collection, opened. */
    val emptyOwn = folders + down + Action.Enter

    /** Most played, opened. */
    val mostPlayedOpen = listOf(Action.TapItem(Column.Systems, 1), Action.Enter, down, Action.Enter)

    val gridIds = setOf("collection:7")
}
