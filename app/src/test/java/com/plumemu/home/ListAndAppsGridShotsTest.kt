package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Shots of the two faults (a row added above Recent's window; the Apps grid's preview), at the reference sizes in Graphite and Paper. They are written only
 * when `PLUM_SHOTS_DIR` is set (with `PLUM_SHOTS_LABEL`, "before" or "after"), as `<fault>_<size>_<theme>_<label>.png`; without it the test only checks that the
 * screens build. Not compared with goldens: the files are for a person to look at.
 */
abstract class ListAndAppsGridShotsBase(
    private val size: ReferenceShots.Size,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var preset by mutableStateOf(ReferenceShots.Preset.Graphite)
    private var content by mutableStateOf(FakeHomeContent.board())
    private lateinit var state: com.plumemu.coreinput.strip.StripState

    private fun write(
        fault: String,
        p: ReferenceShots.Preset,
    ) {
        val dir = System.getenv("PLUM_SHOTS_DIR") ?: return
        val label = System.getenv("PLUM_SHOTS_LABEL") ?: "after"
        preset = p
        compose.waitForIdle()
        File(dir).mkdirs()
        File(dir, "${fault}_${size.label}_${p.label}_$label.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun entryRows(id: String) = content.entries.first { it.id == id }.rows

    private fun edit(
        id: String,
        rows: List<GameRowModel>,
    ) {
        compose.runOnIdle { content = content.withEntries(content.entries.map { if (it.id == id) it.copy(rows = rows) else it }) }
        compose.waitForIdle()
    }

    private fun host(
        actions: List<Action>,
        gridIds: Set<String> = emptySet(),
    ) {
        compose.setContent { PlumTheme(preset.settings) { HomeAt(content, actions, gridIds = gridIds, onAction = { state.dispatch(it) }, onState = { state = it }) } }
        compose.waitForIdle()
    }

    /** Recent, the focus on its first row, then the game just played moves to the top. */
    @Test
    fun fault1RowAddedAboveTheWindow() {
        val deep = entryRows("recent")
        edit("recent", deep + deep.map { it.copy(id = it.id + "-b") })
        host(listOf(Action.TapItem(Column.Systems, 0), Action.Enter))
        edit("recent", listOf(entryRows("recent").first().copy(id = "just-played", title = "Super Mario 3D Land")) + entryRows("recent"))
        ReferenceShots.Preset.entries.forEach { write("fault1", it) }
    }

    /** Apps with the systems focused: the grid previews beside the column, with two-line names. */
    @Test
    fun fault2AppsPreview() {
        edit("apps", entryRows("apps").mapIndexed { i, row -> row.copy(title = "Nightly Emulator ${i + 1}") })
        val appsIndex = content.entries.indexOfFirst { it.id == "apps" }
        host(listOf(Action.TapItem(Column.Systems, appsIndex)), gridIds = setOf("apps"))
        ReferenceShots.Preset.entries.forEach { write("fault2", it) }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ListAndAppsGridShots768Test : ListAndAppsGridShotsBase(ReferenceShots.Size.Wide)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class ListAndAppsGridShots576Test : ListAndAppsGridShotsBase(ReferenceShots.Size.Narrow)
