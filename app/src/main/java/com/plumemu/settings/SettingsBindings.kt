package com.plumemu.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ProvidePlumType
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.SystemAccents
import com.plumemu.coreui.theme.accentFor
import com.plumemu.settings.screen.ColorPreview
import com.plumemu.settings.screen.LocalColorPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps [router]'s Controls settings equal to the stored ones until cancelled. The router reads
 * its settings on every press, so a change applies from the next press.
 */
suspend fun InputRouter.followSettings(settings: Flow<PlumSettings>) {
    settings.map { it.controls.toInputSettings() }.distinctUntilChanged().collect { this.settings = it }
}

/**
 * The system the person last looked at on the home screen ([system]: its id, null on Favorites, Recent,
 * Apps or before any) and where to read the colour of each system from ([colors]; loaded once, off the
 * main thread). With Accent color › By system, [SettingsTheme] draws this system's colour as the accent.
 */
internal class ViewedSystem(
    val system: StateFlow<String?>,
    private val loadColors: suspend () -> Map<String, Rgb>?,
) {
    /** The colours once [colors] has run, so a theme made later (the second screen's window) starts with them. */
    @Volatile
    var loadedColors: Map<String, Rgb>? = null
        private set

    /** Loaded once; later calls answer at once. Data that could not be read is not kept, so a later call reads again. */
    suspend fun colors(): Map<String, Rgb> = loadedColors ?: loadColors()?.also { loadedColors = it }.orEmpty()
}

/**
 * [PlumTheme] with the stored appearance and Advanced › Animations. With the accent By system and a
 * [viewed] system, the accent follows it: through one [State] that only the readers of the
 * accent read, so a system move recomposes nothing else (`HomeAccentBySystemTest`). Without [viewed]
 * (setup before the home screen, a pinned shortcut) the fallback colour shows.
 */
@Composable
fun SettingsTheme(
    settings: PlumSettings,
    content: @Composable () -> Unit,
) = SettingsTheme(settings, null, content)

@Composable
internal fun SettingsTheme(
    settings: PlumSettings,
    viewed: ViewedSystem?,
    content: @Composable () -> Unit,
) = SettingsTheme(settings, viewed, null, content)

/**
 * [preview]: the colours a finger on a colour slider shows. They reach the theme as a State
 * ([PlumTheme]'s `colorPreview`), so a move redraws what shows a colour and recomposes nothing; the
 * [settings] given stay the stored ones, so nothing else sees a changed value.
 */
@Composable
internal fun SettingsTheme(
    settings: PlumSettings,
    viewed: ViewedSystem?,
    preview: ColorPreview?,
    content: @Composable () -> Unit,
) {
    val theme = remember(settings.appearance, settings.advanced.animations) { settings.toThemeSettings() }
    val isBySystem = viewed != null && theme.accent is AccentChoice.BySystem
    val previewTheme =
        if (preview == null) {
            null
        } else {
            val stored = rememberUpdatedState(settings)
            remember(preview) { derivedStateOf { preview.appearance?.let { preview.over(stored.value).toThemeSettings() } } }
        }
    CompositionLocalProvider(LocalColorPreview provides (preview ?: ColorPreview.None)) {
        PlumTheme(theme, if (isBySystem) rememberSystemAccent(theme, viewed, previewTheme) else null, previewTheme, content)
    }
}

/**
 * The accent for the system in view, as a State. Nothing here is read in composition: the colours
 * and the viewed id arrive in States read inside [derivedStateOf], so only a reader of the result
 * runs again when the system changes.
 */
@Composable
private fun rememberSystemAccent(
    theme: PlumThemeSettings,
    viewed: ViewedSystem?,
    preview: State<PlumThemeSettings?>?,
): State<Rgb> {
    val system = remember(viewed) { mutableStateOf(viewed?.system?.value) }
    val colors = remember(viewed) { mutableStateOf(viewed?.loadedColors.orEmpty()) }
    LaunchedEffect(viewed) {
        if (viewed == null) return@LaunchedEffect
        launch { viewed.system.collect { system.value = it } }
        colors.value = viewed.colors()
    }
    // Settled on the background in view: the previewed one while a colour slider moves (read as State, never in composition).
    val accents = remember(theme.backgroundColor, colors, preview) { derivedStateOf { SystemAccents(preview?.value?.backgroundColor ?: theme.backgroundColor, colors.value) } }
    return remember(theme, accents, system) { derivedStateOf { theme.accentFor(system.value, accents.value) } }
}

/** Only the stored font and Text size ([ProvidePlumType]): for what reads nothing else of the theme. */
@Composable
fun SettingsType(
    settings: PlumSettings,
    content: @Composable () -> Unit,
) {
    val theme = remember(settings.appearance) { settings.toThemeSettings() }
    ProvidePlumType(theme, content)
}
