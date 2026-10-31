package com.plumemu.coredata.settings

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Settings › Library › Sort games › Year is stored by name and read back after a restart (plain JVM). */
class SortGamesYearTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    private fun open(job: Job) = SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), SettingsLog { })

    @Test
    fun sortByYearIsKeptAcrossARestart() = runBlocking {
        val first = Job()
        open(first).update { it.copy(library = it.library.copy(sortGames = SortGamesSetting.Year)) }
        first.cancelAndJoin()
        val second = Job()
        assertEquals(SortGamesSetting.Year, open(second).settings.first().library.sortGames)
        second.cancelAndJoin()
    }
}
