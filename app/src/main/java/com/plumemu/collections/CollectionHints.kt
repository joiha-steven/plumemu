package com.plumemu.collections

/** The hints of the folders page; the labels come from strings later. */
enum class FolderHint { Open, NewCollection, Search, SwitchEntry, Back }

/**
 * What the hint bar shows with [focused] on the folders page: only what acts on a folder row.
 * New collection takes Open's place on its row; never Favorite, Sort or the grid switch.
 * [hasOtherEntries]: the L2/R2 switch has somewhere to go.
 */
fun hintsFor(focused: Folder?, hasOtherEntries: Boolean): List<FolderHint> =
    buildList {
        add(if (focused is Folder.NewCollection) FolderHint.NewCollection else FolderHint.Open)
        add(FolderHint.Search)
        if (hasOtherEntries) add(FolderHint.SwitchEntry)
        add(FolderHint.Back)
    }
