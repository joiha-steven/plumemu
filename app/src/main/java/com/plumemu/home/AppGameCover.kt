package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.plumemu.coreui.components.CoverShadow
import com.plumemu.coreui.components.coverShadow
import com.plumemu.coreui.components.drawCoverRim
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.POSTER_INK_WHITE
import com.plumemu.coreui.theme.POSTER_SHADOW_COLOR
import com.plumemu.coreui.theme.PosterLook
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * An app game in a grid that also holds file games (Ports, Recent, Favorites): a cover card of its neighbours' shape and
 * size. A gradient from the icon's own colour, the icon centred above the poster's system band. The name is not drawn:
 * the grid's bottom bar shows it, as it does for a cover. Grids made only of app games are Apps tiles (AppsGrid.kt).
 */

/** The icon's side as a share of the card's shorter side. */
internal const val APP_GAME_ICON_SHARE = 0.47f

/** The icon's corner as a share of its side, and the soft shadow under it. */
private const val ICON_CORNER_SHARE = 0.22f
private const val ICON_SHADOW_STEPS = 4

/** Lightness of the card's gradient ends and band, HSL; the white ink then passes 4.5:1 (checked by [appGameLook]). */
private const val LOOK_TOP = 0.42f
private const val LOOK_BOTTOM = 0.30f
private const val LOOK_BAND = 0.22f
private const val INK_MIN_CONTRAST = 4.5f

/** Saturation is kept but lifted to a floor for a coloured icon (grey ones stay grey) and capped so the card is not neon. */
private const val SATURATION_FLOOR = 0.35f
private const val SATURATION_CAP = 0.80f
private const val GREY_UNDER = 0.08f

/** The card's look from an icon's average colour: its hue, a deeper neighbour at the bottom and the band, white ink. */
internal fun appGameLook(average: Color): PosterLook {
    val (hue, saturation, _) = hsl(average)
    val s = if (saturation < GREY_UNDER) saturation else saturation.coerceIn(SATURATION_FLOOR, SATURATION_CAP)
    var top = fromHsl(hue, s, LOOK_TOP)
    var lightness = LOOK_TOP
    // The ink must read on the lightest end of the gradient.
    while (contrastWithWhite(top) < INK_MIN_CONTRAST && lightness > 0.05f) {
        lightness -= 0.02f
        top = fromHsl(hue, s, lightness)
    }
    val scale = lightness / LOOK_TOP
    return PosterLook(top, fromHsl(hue, s, LOOK_BOTTOM * scale), fromHsl(hue, s, LOOK_BAND * scale), POSTER_INK_WHITE)
}

/** The card before its icon's colour is known (or when the icon has none): the neutral surface, the text colour on it. */
internal fun appGameNeutralLook(colors: PlumComposeColors): PosterLook = PosterLook(colors.sel, colors.sel, colors.bg, colors.text2)

/** Test hook: called with the icon key each time a card is drawn. */
internal var appGameCoverDrawHook: ((String) -> Unit)? = null

private val glowByIcons = java.util.WeakHashMap<HomeAppIcons, CoverGlowColors>()

/**
 * The icon colours of every app game card and its halo, one cache per icon source (kept while the source lives, so scrolling
 * away and back, or another grid size, finds the colour again), keyed by icon key (package).
 */
internal fun HomeAppIcons.appGlowColors(): CoverGlowColors = synchronized(glowByIcons) { glowByIcons.getOrPut(this) { CoverGlowColors(glowDispatcher) } }

private fun contrastWithWhite(c: Color): Float = 1.05f / (c.luminance() + 0.05f)

private fun hsl(c: Color): Triple<Float, Float, Float> {
    val hi = max(c.red, max(c.green, c.blue))
    val lo = min(c.red, min(c.green, c.blue))
    val l = (hi + lo) / 2f
    val d = hi - lo
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h =
        when (hi) {
            c.red -> ((c.green - c.blue) / d + 6f) % 6f
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        }
    return Triple(h / 6f, s, l)
}

private fun fromHsl(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Color {
    fun f(n: Int): Float {
        val k = (n + hue * 12f) % 12f
        return lightness - saturation * min(lightness, 1f - lightness) * max(-1f, min(min(k - 3f, 9f - k), 1f))
    }
    return Color(f(0).coerceIn(0f, 1f), f(8).coerceIn(0f, 1f), f(4).coerceIn(0f, 1f))
}

/** The icon's side in px on a card of [size]: what the cell asks the icons for. */
internal fun appGameIconPx(
    size: DpSize,
    density: Density,
): Int = with(density) { (min(size.width.toPx(), size.height.toPx()) * APP_GAME_ICON_SHARE).roundToInt().coerceAtLeast(1) }

/**
 * The cover card of [row] at [size]: [icon] centred in the room above the system band. [poster] names the system on the
 * band and the game for accessibility. While the icon loads the card is the neutral surface with no icon. Colours and the
 * learned average are read while drawing; a colour that lands redraws this card only.
 */
@Composable
internal fun AppGameCover(
    poster: PosterSpec,
    iconKey: String,
    icon: TileIcon,
    size: DpSize,
    corner: Dp,
    shadow: CoverShadow?,
    shadowAlpha: Float,
    modifier: Modifier,
    inFocusLayer: Modifier,
) {
    val icons = LocalHomeAppIcons.current
    val glow = remember(icons) { icons.appGlowColors() }
    // Seeded from the shared cache, so a card that scrolled back in does not flash neutral; read without observing (the
    // cache's version moves for every app). Only this card's own icon landing changes its state.
    val average = remember(iconKey, glow) { mutableStateOf(Snapshot.withoutReadObservation { glow[iconKey] }) }
    (icon as? TileIcon.Picture)?.let { shown ->
        LaunchedEffect(shown) { glow.learn(iconKey, shown.image, replace = true)?.let { average.value = it } }
    }
    val colors = PlumTheme.colors
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val family = PlumTheme.type.gridFocusTitle.fontFamily
    val rim = shadow?.rim ?: Color.Transparent
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .coverShadow(shadow, corner, shadowAlpha)
            .then(inFocusLayer)
            .clip(shape)
            .semantics { contentDescription = poster.title }
            .drawWithContent {
                drawContent()
                if (rim != Color.Transparent) drawCoverRim(rim, corner.toPx())
            },
    ) {
        Box(
            Modifier.fillMaxSize().testTag(if (icon is TileIcon.Picture) HomeTags.APP_ICON else HomeTags.APP_GAME_CARD).drawWithCache {
                // Read here: the colour landing rebuilds the card (a redraw), nothing recomposes.
                val found = average.value
                val look = if (found != null) appGameLook(found) else appGameNeutralLook(colors)
                val layout = PosterLayout.of(measurer, this, "", poster.system, poster.systemShort, look, family, this.size)
                val side = min(this.size.width, this.size.height) * APP_GAME_ICON_SHARE
                val bandHeight = this.size.height * PosterLayout.BAND_HEIGHT
                val centre = Offset(this.size.width / 2f, (this.size.height - bandHeight) / 2f)
                val topLeft = Offset(centre.x - side / 2f, centre.y - side / 2f)
                val cornerPx = side * ICON_CORNER_SHARE
                val clip = Path().apply { addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + side, topLeft.y + side, CornerRadius(cornerPx))) }
                val image = (icon as? TileIcon.Picture)?.image
                onDrawBehind {
                    appGameCoverDrawHook?.invoke(iconKey)
                    layout.draw(this)
                    if (image == null) return@onDrawBehind
                    // A soft shadow: a few translucent rounded rects growing outward, a little below the icon.
                    for (step in ICON_SHADOW_STEPS downTo 1) {
                        val grow = side * 0.03f * step
                        drawRoundRect(
                            POSTER_SHADOW_COLOR.copy(alpha = 0.07f),
                            Offset(topLeft.x - grow, topLeft.y - grow + side * 0.04f),
                            Size(side + grow * 2, side + grow * 2),
                            CornerRadius(cornerPx + grow),
                        )
                    }
                    clipPath(clip) {
                        drawImage(image, dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = IntSize(side.roundToInt(), side.roundToInt()))
                    }
                }
            },
        )
    }
}

/** The icon [row] asks for on a card of [size], or null when [row] is not an app game (read through [rememberTileIcon]). */
@Composable
internal fun rememberAppGameIcon(
    row: GameRowModel,
    size: DpSize,
): TileIcon? {
    if (row.appPackage == null) return null
    val icons = LocalHomeAppIcons.current
    val generation by icons.generation.collectAsState()
    return rememberTileIcon(row.iconKey, appGameIconPx(size, LocalDensity.current), icons, generation)
}
