package com.plumemu.coreui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * What the person picked in Settings › Appearance (and Advanced › Animations). Every token is
 * derived from these. Defaults are the app's defaults: Black, light text, Red, Plum Sans.
 */
@Immutable
data class PlumThemeSettings(
    val background: BackgroundChoice = BackgroundChoice.Preset(BackgroundPreset.Black),
    /**
     * The stored text colour: any colour; [TextMode.Light] and [TextMode.Dark] `.color` are its
     * presets. What is drawn is [textColor], which differs only when this cannot be read on the
     * background (an older version, a restored backup, a hand-edited file).
     */
    val text: Rgb = TextMode.Light.color,
    val accent: AccentChoice = AccentChoice.Preset(AccentPreset.Default),
    val font: PlumFont = PlumFont.Default,
    /** Null until the person picks a size: then [TextSize.Default], Large. */
    val textSize: TextSize? = null,
    /** Settings › Appearance › Text weight: how heavy every text role is drawn. */
    val textWeight: TextWeight = TextWeight.Default,
    val animationsOn: Boolean = true,
    /** Settings › Appearance › Grid size: [PlumLayout.gridSize]. */
    val gridSize: GridSize = GridSize.Default,
    /**
     * The ready-made set whose A/B/X/Y glyph colours are in use: the one the person last picked,
     * whatever the three colours are now. Null: the usual ringed glyphs.
     */
    val buttonSet: ColorSet? = null,
) {
    /** The text and accent that are drawn: the stored ones settled by [Readability], never written back. */
    private val drawn: Readability.Settled by lazy(LazyThreadSafetyMode.NONE) {
        Readability.settle(background.color, text, accent)
    }

    val backgroundColor: Rgb get() = background.color

    /** The text colour drawn: [text], or the nearest readable one when it cannot be read on the background. */
    val textColor: Rgb get() = drawn.text

    /** Light when the drawn text is lighter than the background, else Dark; what the theme branches on. */
    val textMode: TextMode get() = TextMode.of(textColor, backgroundColor)

    /** The accent drawn: a preset follows the text mode, a custom colour does not; one below 3:1 is moved in lightness. */
    val accentColor: Rgb get() = drawn.accent.resolve(textMode)

    /**
     * Fill behind the navigation bar on Android 6.0–7.1 when the text is dark. Those versions
     * cannot draw dark navigation buttons, so the system's white ones need a dark bar behind them;
     * the dark text colour is that bar. Unused with light text (light buttons on the background).
     */
    val legacyNavigationBarScrim: Rgb get() = TextMode.Dark.color

    companion object {
        /** The default look: Black, light text, Red. */
        val Default = PlumThemeSettings()

        /** The dark preset of every screenshot test (no longer the default). */
        val Graphite = PlumThemeSettings(background = BackgroundChoice.Preset(BackgroundPreset.Graphite))

        /** The light preset of every screenshot test. */
        val Paper = PlumThemeSettings(background = BackgroundChoice.Preset(BackgroundPreset.Paper), text = TextMode.Dark.color)
    }
}

private val LocalPlumColors = staticCompositionLocalOf { PlumComposeColors.from(PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light)) }
private val LocalPlumType = staticCompositionLocalOf { PlumTypography(PlumFont.Default, TextSize.Default, TextMode.Light) }
/** Internal so the test fixtures' `ProvideGridSize` can swap the grid size of a subtree. */
internal val LocalPlumLayout = staticCompositionLocalOf { PlumLayout(width = WidthClass.WIDE_MIN_WIDTH, height = 432.dp) }
private val LocalPlumMotion = staticCompositionLocalOf { PlumMotionState(isOn = true) }
private val LocalPlumDimens = staticCompositionLocalOf { PlumDimens }
private val LocalPlumButtons = staticCompositionLocalOf<ButtonColors?> { null }

/**
 * Provides colours, type, dimensions, layout and motion to [content]. Built on Compose
 * foundation only; there is no Material theme in this app (APK size).
 */
@Composable
fun PlumTheme(
    settings: PlumThemeSettings = PlumThemeSettings.Default,
    systemAccent: State<Rgb>? = null,
    colorPreview: State<PlumThemeSettings?>? = null,
    content: @Composable () -> Unit,
) {
    val background = settings.backgroundColor
    val accent = settings.accentColor
    val text = settings.textColor
    // [colorPreview]: the look a finger on a colour slider shows before the store has it. The colour
    // set is built once and reads it as a State, so a move invalidates what reads a colour (a draw
    // lambda: a redraw) and recomposes nothing under the theme. When the person's choice itself
    // changes the keys change and a new set is built, as before.
    // By system: the accent of the system in view ([systemAccent], read only by what draws the
    // accent). A State too, so a system move wakes no reader of any other colour.
    val colors =
        remember(background, text, accent, systemAccent, colorPreview) {
            val tokens =
                derivedStateOf {
                    val shown = colorPreview?.value
                    PlumColors.derive(shown?.backgroundColor ?: background, shown?.textColor ?: text, shown?.accentColor ?: accent)
                }
            val accentLook =
                derivedStateOf {
                    val shown = colorPreview?.value
                    val tokensNow = tokens.value
                    if (systemAccent != null && (shown == null || shown.accent is AccentChoice.BySystem)) {
                        AccentLook.of(PlumColors.derive(tokensNow.bg, tokensNow.text, systemAccent.value))
                    } else {
                        AccentLook.of(tokensNow)
                    }
                }
            PlumComposeColors.following(tokens, accentLook)
        }
    // The A/B/X/Y glyph colours belong to the set the person last picked, not to an exact colour
    // match: fine-tuning a colour keeps them.
    val buttons = settings.buttonSet?.buttons
    // The window's size, not the screen's: the launcher fills its window, and in split screen
    // or a freeform window the width class must follow what is actually drawn.
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val textSize = settings.textSize ?: TextSize.Default
    // The text mode picks the font weight (light text on a dark background is drawn a little lighter). It follows the
    // preview too, but through a derived State read here: it changes only when a slider move flips the mode, so a
    // move that keeps it recomposes nothing, and a flip rebuilds the type once.
    val storedMode = settings.textMode
    val shownMode by remember(storedMode, colorPreview) { derivedStateOf { colorPreview?.value?.textMode ?: storedMode } }
    val type =
        remember(settings.font, textSize, shownMode, settings.textWeight) {
            PlumTypography(settings.font, textSize, shownMode, settings.textWeight)
        }
    val layout =
        remember(containerSize, density, textSize, settings.gridSize) {
            with(density) {
                PlumLayout(
                    width = containerSize.width.toDp(),
                    height = containerSize.height.toDp(),
                    // The peek shows counts only; they grow with the text, so the column does too.
                    // A future peek-width setting must also be multiplied by the factor.
                    peek = PlumDimens.peekWidth * textSize.heightScale,
                    textScale = textSize.heightScale,
                    gridSize = settings.gridSize,
                )
            }
        }
    val animatorScale = rememberSystemAnimatorScale()
    val motion =
        remember(animatorScale, settings.animationsOn) {
            PlumMotionState(PlumMotion.isMotionOn(animatorScale, settings.animationsOn))
        }
    CompositionLocalProvider(
        LocalPlumColors provides colors,
        LocalPlumType provides type,
        LocalPlumLayout provides layout,
        LocalPlumMotion provides motion,
        LocalPlumDimens provides PlumDimens,
        LocalPlumButtons provides buttons,
        content = content,
    )
}

/**
 * Only [settings]' type (font, Text size, text mode) for [content], for something that reads
 * nothing else from the theme and stays up for long: no colours built, no layout, and no observer
 * of Android's animator scale (the second screen's in-game battery part, up all play).
 * The Text size left unpicked is [TextSize.Default] (Large), as in [PlumTheme].
 */
@Composable
fun ProvidePlumType(
    settings: PlumThemeSettings,
    content: @Composable () -> Unit,
) {
    val textSize = settings.textSize ?: TextSize.Default
    val type =
        remember(settings.font, textSize, settings.textMode, settings.textWeight) {
            PlumTypography(settings.font, textSize, settings.textMode, settings.textWeight)
        }
    CompositionLocalProvider(LocalPlumType provides type, content = content)
}

/** Read access to the current theme: `PlumTheme.colors.bg`, `PlumTheme.type.gameTitle`, … */
object PlumTheme {
    val colors: PlumComposeColors
        @Composable @ReadOnlyComposable
        get() = LocalPlumColors.current

    val type: PlumTypography
        @Composable @ReadOnlyComposable
        get() = LocalPlumType.current

    val layout: PlumLayout
        @Composable @ReadOnlyComposable
        get() = LocalPlumLayout.current

    val motion: PlumMotionState
        @Composable @ReadOnlyComposable
        get() = LocalPlumMotion.current

    val dimens: PlumDimens
        @Composable @ReadOnlyComposable
        get() = LocalPlumDimens.current

    /** The current ready-made set's A/B/X/Y glyph colours (Super, Charcoal, Ivory, Banana, Pocket), else null: the usual glyphs. */
    val buttons: ButtonColors?
        @Composable @ReadOnlyComposable
        get() = LocalPlumButtons.current
}

/**
 * The system animator duration scale, kept current while composed. Changing it (Developer
 * options, or accessibility "Remove animations") is not a configuration change and the launcher
 * activity lives for days, so the setting is observed rather than read once. No polling: the
 * observer only runs when the setting changes.
 */
@Composable
private fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    var scale by remember(context) { mutableFloatStateOf(readAnimatorScale(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scale = readAnimatorScale(context)
                }
            }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // It may have changed between the first read and registering.
        scale = readAnimatorScale(context)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
