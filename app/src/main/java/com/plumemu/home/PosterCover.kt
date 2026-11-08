package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.rememberTextMeasurer
import com.plumemu.coreui.theme.BOX_NEUTRAL_BASE
import com.plumemu.coreui.theme.BOX_YEAR_INK
import com.plumemu.coreui.theme.POSTER_INK_WHITE
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.boxFieldFor
import java.util.Locale

/**
 * The drawn box front of a game without box art ([BoxFrontLayout]): a card in the system's colour (a light to dark diagonal, fine grain, the title's first letter large behind the text) with [systemShort]'s code at the top left
 * ([system] when there is none) and the title at the bottom left. It fills the size the caller gives it; the caller clips
 * and rounds it. The card is drawn once into an image per (title, system, size, colours) and only that image is drawn per
 * frame. [line] is no longer drawn (the card has no small line); the parameter stays so callers need not change. The cell
 * around it carries the title for accessibility, so this adds no semantics.
 */
@Composable
fun PosterCover(
    title: String,
    system: String,
    systemShort: String = system,
    modifier: Modifier = Modifier,
    line: String = "",
) {
    // The grid's shared measurer when there is one: a measurer of its own per card was one more allocation per cover.
    val measurer = LocalTileTitleFit.current?.measurer ?: rememberTextMeasurer(cacheSize = 8)
    val colors = PlumTheme.colors
    val tints = LocalSystemTints.current
    Box(
        modifier.drawWithCache {
            // Colours are read here, so a theme change rebuilds the image without recomposing.
            val base = tints.value[systemTintKey(systemShort.ifBlank { system })] ?: colors.accent
            val look = boxFieldFor(base, title)
            val layout = BoxFrontLayout.of(measurer, this, title, system, systemShort, look, size)
            val image = layout.render(this)
            onDrawBehind {
                if (image != null) {
                    drawImage(image)
                    boxFrontProbe?.let { it.imageDraws++ }
                }
            }
        },
    )
}

/** The key a system's tint is found by: its short code in capitals ("SNES"). */
internal fun systemTintKey(short: String): String = short.uppercase(Locale.ROOT)

/**
 * The ground of the titled tile (a picture still loading, or a game with neither picture nor poster): the card's field and grain
 * ([BoxFrontLayout.fieldOnly]) in the colour of [spec]'s system, its hue turned by the title, with no letter or text. Drawn once
 * into an image per (title, system, size, colours) inside `drawWithCache`; a frame draws that image only. [spec] null (no system
 * known) takes a neutral grey of the same recipe. [titleOf] is the hue's title when the tile shows none.
 */
@Composable
internal fun rememberTileField(
    spec: PosterSpec?,
    titleOf: String?,
): TileField {
    val colors = PlumTheme.colors
    val tints = LocalSystemTints.current
    val title = spec?.title ?: titleOf.orEmpty()
    val key = spec?.let { systemTintKey(it.systemShort.ifBlank { it.system }) }
    val look = remember(title, key, tints, colors.accent) { TileField(title, key, tints, colors.accent) }
    return look
}

/** The titled tile's ground as a modifier ([fill]) and the white ink that reads on it. */
internal class TileField(
    private val title: String,
    private val key: String?,
    private val tints: State<Map<String, Color>>,
    private val accent: Color,
) {
    val fill: Modifier =
        Modifier.drawWithCache {
            // Read here, so a theme or tint change rebuilds the image without recomposing.
            val base = if (key == null) BOX_NEUTRAL_BASE else tints.value[key] ?: accent
            val look = boxFieldFor(base, title)
            val image = BoxFrontLayout.fieldOnly(title, look, size).render(this)
            onDrawBehind {
                if (image != null) {
                    drawImage(image)
                    boxFrontProbe?.let { it.imageDraws++ }
                }
            }
        }

    /** The card's own ink: white title, the year as the card's code (white at 80 %). */
    val titleInk: Color = POSTER_INK_WHITE
    val yearInk: Color = BOX_YEAR_INK
}
