package com.plumemu.content

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.EntryKind
import com.plumemu.home.FIRST_SYSTEM_INDEX
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main screen's system column follows Settings › Library › System order and Systems at once:
 * the library's content, re-arranged, drawn by the home screen; the focus
 * stays on the system it was on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSystemOrderScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).allowMainThreadQueries().build()
    private val arrangement = MutableStateFlow(SystemArrangement())
    private val source =
        LibraryHomeContent(
            db.gameDao(),
            { LibraryHomeContentTest.catalog },
            MutableStateFlow(emptyList<LaunchableApp>()),
            MutableStateFlow(true),
            Dispatchers.Default,
            arrangement = arrangement,
        )

    @After
    fun tearDown() = db.close()

    private fun next(predicate: (HomeContent) -> Boolean): HomeContent = runBlocking { withTimeout(TIMEOUT_MS) { source.content.first(predicate) } }

    private fun HomeContent.systemIds() = entries.filter { it.kind == EntryKind.System }.map { it.id }

    private fun assertColumn(vararg names: String) {
        names.forEachIndexed { i, name ->
            compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(HomeTags.system(FIRST_SYSTEM_INDEX + i))), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun theColumnIsReArrangedAndHiddenSystemsLeaveItWhileTheFocusStaysPut() {
        runBlocking {
            val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
            db.scanDao().applyBatch(
                ScanBatch(
                    folder,
                    systems = listOf("snes", "psx", "megadrive").map { SystemEntity(it, it) },
                    newGames = listOf(game(folder, "snes/a.sfc", "snes"), game(folder, "psx/b.cue", "psx"), game(folder, "megadrive/c.md", "megadrive")),
                ),
            )
        }
        val shown = mutableStateOf(next { it.systemCount == 3 })
        lateinit var strip: StripState
        // The cursor on PlayStation, the second system A to Z.
        compose.setContent { PlumTheme { HomeAt(shown.value, listOf(Action.TapItem(Column.Systems, FIRST_SYSTEM_INDEX + 1)), onState = { strip = it }) } }
        assertColumn("Mega Drive", "PlayStation", "Super Nintendo")
        compose.runOnIdle { assertEquals("psx", strip.focus.systemId) }

        arrangement.value = SystemArrangement(SystemOrderSetting.Maker)
        shown.value = next { it.systemIds() == listOf("snes", "megadrive", "psx") }
        compose.waitForIdle()
        assertColumn("Super Nintendo", "Mega Drive", "PlayStation")
        compose.runOnIdle { assertEquals("the focus follows its system", "psx" to FIRST_SYSTEM_INDEX + 2, strip.focus.systemId to strip.focus.systemIndex) }

        arrangement.value = SystemArrangement(SystemOrderSetting.Maker, hidden = setOf("megadrive"))
        shown.value = next { "megadrive" !in it.systemIds() }
        compose.waitForIdle()
        assertColumn("Super Nintendo", "PlayStation")
        compose.onNode(hasText("Mega Drive"), useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals("psx" to FIRST_SYSTEM_INDEX + 1, strip.focus.systemId to strip.focus.systemIndex) }
    }

    private fun game(
        folder: Long,
        path: String,
        system: String,
    ) = GameEntity(
        folderId = folder,
        documentId = "root/$path",
        parentDocumentId = "root/${path.substringBefore('/')}",
        relativePath = path,
        fileName = path.substringAfter('/'),
        sizeBytes = 1,
        modifiedAt = 1,
        title = path.substringAfter('/'),
        sortName = path.substringAfter('/'),
        regions = "USA",
        discGroup = null,
        discCount = 1,
        systemId = system,
        isPrimaryCopy = true,
    )

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
