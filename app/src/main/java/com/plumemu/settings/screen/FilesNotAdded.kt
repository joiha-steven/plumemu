package com.plumemu.settings.screen

import androidx.compose.runtime.Immutable
import com.plumemu.coredata.db.LeftOutFileEntity
import com.plumemu.library.catalog.LeftOutReason

/*
 * Settings › Library › Files not added: what the scans left out, grouped by reason. Built off the
 * main thread by the graph (`GraphSettingsLibrary`) from the stored rows; the page only draws it.
 */

/** The groups, in the order the page lists them. */
internal enum class NotAddedGroup {
    /** Files several systems open, outside a system's folder; folders Plumemu does not know come first as one line each. */
    NoSystem,

    /** Folders the person already answered, one line each, so an answer can be changed. */
    AnsweredFolders,

    /** Files no emulator of their folder's system opens (one group per system; no system: nothing opens them). */
    Unmatched,
    Bios,
    Saves,
    Junk,
}

/** One line of the page; every line is a stop for the gamepad. */
@Immutable
internal sealed interface NotAddedItem {
    val key: String

    /** A group's heading; [systemId] names the system for [NotAddedGroup.Unmatched]. */
    data class Heading(val group: NotAddedGroup, val systemId: String?) : NotAddedItem {
        override val key: String get() = "h:$group:$systemId"
    }

    /** A file: its [name], and the [directoryPath] it is in below picked folder [folderId] (`""` for the folder itself). */
    data class File(val folderId: Long, val directoryPath: String, val name: String) : NotAddedItem {
        override val key: String get() = "f:$folderId:$directoryPath/$name"
    }

    /** "and 340 more files": the group's files past what is stored. */
    data class More(val group: NotAddedGroup, val systemId: String?, val count: Int) : NotAddedItem {
        override val key: String get() = "m:$group:$systemId"
    }

    /** An unknown folder ([SettingsContent.unknownFolders]) with the [files] in it the scan left out and its [answer]; A opens its page. */
    data class Folder(val folder: FolderKey, val files: Int, val answer: FolderAnswer = FolderAnswer.Unanswered) : NotAddedItem {
        override val key: String get() = "d:${folder.folderId}:${folder.relativePath}"
    }
}

/**
 * The page's lines from the stored [rows]: per group a heading, then its files by folder and path,
 * then one "more" line. Every unknown folder has its line and its page is reached only from here: the files
 * of an unanswered one are one line for the folder, leading the [NotAddedGroup.NoSystem] group, and an
 * answered one has its line in [NotAddedGroup.AnsweredFolders]. Rows of a reason this build does not know are skipped.
 */
internal fun notAddedItems(
    rows: List<LeftOutFileEntity>,
    unknownFolders: List<SettingsFolderRow>,
): List<NotAddedItem> {
    val (unanswered, answered) = unknownFolders.partition { it.answer == FolderAnswer.Unanswered }
    val waiting = unanswered.map { it.key }
    fun folderOf(row: LeftOutFileEntity): FolderKey? =
        waiting.firstOrNull { it.folderId == row.folderId && (row.directoryPath == it.relativePath || row.directoryPath.startsWith(it.relativePath + "/")) }
    val folderFiles = LinkedHashMap<FolderKey, Int>()
    val grouped = LinkedHashMap<Pair<NotAddedGroup, String?>, MutableList<LeftOutFileEntity>>()
    for (row in rows) {
        val group = groupOf(LeftOutReason.of(row.reason) ?: continue)
        val folder = if (group == NotAddedGroup.NoSystem) folderOf(row) else null
        if (folder != null) {
            folderFiles[folder] = (folderFiles[folder] ?: 0) + filesOf(row)
            continue
        }
        val system = if (group == NotAddedGroup.Unmatched) row.systemId else null
        grouped.getOrPut(group to system) { ArrayList() } += row
    }
    val folders = unanswered.map { NotAddedItem.Folder(it.key, folderFiles[it.key] ?: it.files) }
    val answeredLines = answered.map { NotAddedItem.Folder(it.key, 0, it.answer) }
    val keys = (grouped.keys + folders.map { NotAddedGroup.NoSystem to null } + answeredLines.map { NotAddedGroup.AnsweredFolders to null }).distinct()
        .sortedWith(compareBy<Pair<NotAddedGroup, String?>> { it.first.ordinal }.thenBy { it.second == null }.thenBy { it.second })
    return keys.flatMap { key ->
        val (group, system) = key
        val inGroup = grouped[key].orEmpty()
        val files = inGroup.filter { it.relativePath != null }
            .sortedWith(compareBy<LeftOutFileEntity> { it.folderId }.thenBy { it.relativePath })
            .map { NotAddedItem.File(it.folderId, it.directoryPath, it.relativePath.orEmpty().substringAfterLast('/')) }
        val more = inGroup.sumOf { if (it.relativePath == null) it.more else 0 }
        listOf(NotAddedItem.Heading(group, system)) +
            (if (group == NotAddedGroup.NoSystem) folders else if (group == NotAddedGroup.AnsweredFolders) answeredLines else emptyList()) +
            files +
            listOfNotNull(NotAddedItem.More(group, system, more).takeIf { more > 0 })
    }
}

/** How many files the page tells about: each file line, each "more" count and each folder's files. */
internal fun notAddedCount(items: List<NotAddedItem>): Int =
    items.sumOf {
        when (it) {
            is NotAddedItem.File -> 1
            is NotAddedItem.More -> it.count
            is NotAddedItem.Folder -> it.files
            is NotAddedItem.Heading -> 0
        }
    }

private fun filesOf(row: LeftOutFileEntity): Int = if (row.relativePath == null) row.more else 1

private fun groupOf(reason: LeftOutReason): NotAddedGroup =
    when (reason) {
        LeftOutReason.NO_SYSTEM -> NotAddedGroup.NoSystem
        LeftOutReason.UNMATCHED -> NotAddedGroup.Unmatched
        LeftOutReason.BIOS -> NotAddedGroup.Bios
        LeftOutReason.SAVE -> NotAddedGroup.Saves
        LeftOutReason.JUNK -> NotAddedGroup.Junk
    }
