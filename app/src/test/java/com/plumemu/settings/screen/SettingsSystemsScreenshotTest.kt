package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Library › System order and Systems: the
 * System order row with its line, and the Systems panel idle, carrying, with hidden systems, after
 * the switch to Manual and after the refusal to hide the last one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSystemsScreenshotTest {
    @Test
    fun systemOrderRow() = ReferenceShots.capture("settings_system_order") { SystemsShot(SettingsOption.SystemOrder) }

    @Test
    fun systemsIdle() = ReferenceShots.capture("settings_systems_idle") { SystemsShot(SettingsOption.Systems, enter = true) { move(Direction.Down) } }

    @Test
    fun systemsCarrying() =
        ReferenceShots.capture("settings_systems_carrying") {
            SystemsShot(SettingsOption.Systems, enter = true, order = SystemOrderSetting.Manual) {
                move(Direction.Down)
                press(Action.Enter)
                move(Direction.Down)
                move(Direction.Down)
            }
        }

    @Test
    fun systemsHidden() =
        ReferenceShots.capture("settings_systems_hidden") {
            SystemsShot(SettingsOption.Systems, enter = true, hidden = setOf("arcade", "psp")) { move(Direction.Down) }
        }

    @Test
    fun systemsAfterSwitchToManual() =
        ReferenceShots.capture("settings_systems_now_manual") {
            SystemsShot(SettingsOption.Systems, enter = true) {
                move(Direction.Down)
                press(Action.Enter)
                move(Direction.Up)
                press(Action.Enter)
            }
        }

    @Test
    fun systemsLastShown() =
        ReferenceShots.capture("settings_systems_last_shown") {
            SystemsShot(SettingsOption.Systems, enter = true, hidden = SystemsShots.ids.toSet() - "gba") {
                move(Direction.Down) // Game Boy Advance, the only one shown
                press(Action.ToggleFavorite)
            }
        }
}

/** The board's library with its systems, for the Systems shots and tests. */
internal object SystemsShots {
    val systems =
        listOf(
            LibrarySystem("arcade", "Arcade", 312),
            LibrarySystem("gba", "Game Boy Advance", 196, "Nintendo", 2001),
            LibrarySystem("gc", "GameCube", 34, "Nintendo", 2001),
            LibrarySystem("megadrive", "Mega Drive", 210, "Sega", 1988),
            LibrarySystem("nds", "Nintendo DS", 120, "Nintendo", 2004),
            LibrarySystem("psx", "PlayStation", 152, "Sony", 1994),
            LibrarySystem("psp", "PSP", 88, "Sony", 2004),
            LibrarySystem("snes", "Super Nintendo", 172, "Nintendo", 1990),
        )
    val ids = systems.map { it.id }

    val content: SettingsContent = ShotContent.library.copy(librarySystems = systems)
}

/** Writes land at once on the shot's own settings, so the screen shows what a press did. */
private class ShotWrites(
    initial: PlumSettings,
) : SettingsActions by NoActions {
    var settings: PlumSettings by mutableStateOf(initial)

    override fun update(transform: (PlumSettings) -> PlumSettings) {
        settings = transform(settings)
    }
}

/** What a shot does before it is drawn: the presses, with the settings shown at each. */
internal class ShotPresses(
    private val model: SettingsModel,
    private val settings: () -> PlumSettings,
    private val geometry: SettingsGeometry,
) {
    fun press(action: Action) = model.onAction(action, settings(), geometry)

    fun move(direction: Direction) = press(Action.Move(direction))
}

/**
 * Settings on [option] of Library ([enter]: inside its panel), with [order] and [hidden] stored,
 * after [presses]; drawn with no input attached.
 */
@Composable
internal fun SystemsShot(
    option: SettingsOption,
    enter: Boolean = false,
    order: SystemOrderSetting = SystemOrderSetting.Name,
    hidden: Set<String> = emptySet(),
    content: SettingsContent = SystemsShots.content,
    presses: ShotPresses.() -> Unit = {},
) {
    val base = settingsForShot()
    val geometry = settingsGeometry(PlumTheme.layout)
    val writes =
        remember {
            val manual = if (order == SystemOrderSetting.Manual) listOf("snes", "gba", "psx", "megadrive", "nds", "psp", "gc", "arcade") else emptyList()
            ShotWrites(base.copy(library = base.library.copy(systemOrder = order, manualSystemOrder = manual, hiddenSystems = hidden)))
        }
    val model =
        remember {
            val rows = SettingsSection.Library.rowsIn(content)
            val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Library, rememberedOption = mapOf(SettingsSection.Library to rows.indexOf(option)))
            SettingsModel(writes, focus).also { model ->
                model.content = content
                if (enter) model.onAction(Action.Enter, writes.settings, geometry)
                ShotPresses(model, { writes.settings }, geometry).presses()
            }
        }
    SettingsLayout(writes.settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
}
