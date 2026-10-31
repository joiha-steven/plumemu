package com.plumemu.home

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.plumemu.coreinput.strip.StripState
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * The Apps entry as a grid of icons: thirty made-up apps with plain
 * generated icons (no real logos), short and very long names, one app without a usable icon.
 */

/** Where Apps sits in the system column (Recent, Favorites, Apps). */
internal const val APPS_ENTRY = 2

/** Made-up apps: one-word names, a 9-letter word, two-line names, a name longer than two lines and one word longer than a tile. */
private val APP_NAMES =
    listOf(
        "Browser", "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Files", "Gallery", "Music", "Settings",
        "Retro Controller Configuration Utility for Handhelds", "Notes", "Maps", "Weather Forecast and Radar", "Podcasts",
        "Photos", "Downloads", "Recorder", "Supercalifragilisticexpialidocious", "Video Player", "Keyboard", "Mail",
        "Messages", "Phone", "Store", "Translate", "Emulator Companion", "Torch", "Voice Search", "System Update",
    )

/** The apps of [AppsGridLibrary], ids `app-0` … `app-29`. */
internal val AppsGridApps: List<GameRowModel> = APP_NAMES.mapIndexed { i, name -> GameRowModel("app-$i", name, region = "") }

/** The app whose icon cannot be had ("Notes", on the second row at 7 columns): its tile shows the stand-in. */
internal const val APP_WITHOUT_ICON = 11

/** The longest name (more than two lines in every tile). */
internal const val APP_LONG_NAME = 10

/** The board's library ([hasBoxArt]: signed in to ScreenScraper or not) with [AppsGridApps] in Apps. */
internal fun appsGridLibrary(hasBoxArt: Boolean = false): HomeContent {
    val board = if (hasBoxArt) FakeHomeContent.withArtwork() else FakeHomeContent.board()
    return board.withEntries(board.entries.map { if (it.kind == EntryKind.Apps) it.copy(rows = AppsGridApps, shownCount = AppsGridApps.size) else it })
}

/**
 * Icons made on the spot: a rounded square in a colour from the row id with a light dot, the same for
 * the same id; rows in [missing] have none. Counts its loads ([loads]); keeps what it made, as the
 * launcher's cache does, and answers [cached] from it.
 */
internal class FakeAppIcons(
    var missing: Set<String> = setOf(AppsGridApps[APP_WITHOUT_ICON].id),
) : HomeAppIcons {
    override val glowDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    val loads = mutableListOf<String>()
    private val memory = HashMap<Pair<String, Int>, ImageBitmap>()
    override val generation = MutableStateFlow(0)

    /** A package changed: what was made is forgotten and the generation goes up, as `AppIcons.invalidate` does. */
    fun invalidate() {
        memory.clear()
        generation.value++
    }

    override fun cached(
        rowId: String,
        sizePx: Int,
    ): ImageBitmap? = memory[rowId to sizePx]

    override suspend fun load(
        rowId: String,
        sizePx: Int,
    ): ImageBitmap? {
        loads += rowId
        if (rowId in missing) return null
        return memory.getOrPut(rowId to sizePx) { plainIcon(rowId, sizePx) }
    }
}

private fun plainIcon(
    rowId: String,
    px: Int,
): ImageBitmap {
    val image = ImageBitmap(px, px)
    val canvas = Canvas(image)
    val paint = Paint().apply { color = Color.hsv((abs(rowId.hashCode()) % 360).toFloat(), 0.5f, 0.8f) }
    val size = px.toFloat()
    canvas.drawRoundRect(0f, 0f, size, size, size * 0.22f, size * 0.22f, paint)
    paint.color = Color.White.copy(alpha = 0.8f)
    canvas.drawCircle(Offset(size / 2f, size / 2f), size * 0.2f, paint)
    return image
}

/** Apps as a grid ([gridIds] holds `apps`), app [focused] focused, the icons from [icons]. */
@Composable
internal fun AppsGridBoard(
    focused: Int = 0,
    hasBoxArt: Boolean = false,
    icons: HomeAppIcons = remember { FakeAppIcons() },
    onState: (StripState) -> Unit = {},
) {
    val content = remember(hasBoxArt) { appsGridLibrary(hasBoxArt) }
    CompositionLocalProvider(LocalHomeAppIcons provides icons) {
        HomeAt(content, gridActions(APPS_ENTRY, focused), gridIds = setOf(HomeContent.APPS_ID), onState = onState)
    }
}
