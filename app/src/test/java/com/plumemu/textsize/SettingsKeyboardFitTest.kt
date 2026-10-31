package com.plumemu.textsize

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.SAVE_KEY
import com.plumemu.game.resultKey
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.HexTypingSettings
import com.plumemu.settings.screen.NameFixTypingSettings
import com.plumemu.settings.screen.SettingsTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' fields typing with Plumemu's keyboard at every text size, in Plum Sans
 * at the six sizes and in Serif and Mono at Largest: the tray is along the screen's bottom with the
 * typing hints on it; the field (the hex row with its colour dot, Fix name…'s and Use my own name's)
 * is whole, inside the strip and above the tray's top edge, and the first line of what follows it (the
 * wrong-code line, Fix name…'s first result, Save or the blank-name refusal) is whole between the
 * field and the tray; the field's text and the hints are whole. As `PanelKeyboardFit*Test` for the
 * game panel.
 */
abstract class SettingsKeyboardFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(
        name: String,
        fieldTag: String,
        lineUnderTag: String?,
        screen: @Composable () -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { screen() } }
        val combinations = TextSize.entries.map { PlumFont.Sans to it } + listOf(PlumFont.Serif to TextSize.Largest, PlumFont.Mono to TextSize.Largest)
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allow = FitAllowance(scrollTags = setOf(FixNameTags.RESULTS, FixNameTags.OWN_ROWS, SettingsTags.HEX_UNDER), isLargest = each == TextSize.Largest)
            checkOne(root, label, fieldTag, lineUnderTag, allow, failures)
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun checkOne(
        root: SemanticsNode,
        label: String,
        fieldTag: String,
        lineUnderTag: String?,
        allow: FitAllowance,
        failures: MutableList<String>,
    ) {
        val screen = root.boundsInRoot
        val tray = findTagged(root, SettingsTags.TRAY)?.boundsInRoot ?: return run { failures += "$label: no tray" }
        val hints = findTagged(root, SettingsTags.TYPING_HINTS) ?: return run { failures += "$label: no typing hints" }
        val strip = findTagged(root, SettingsTags.STRIP)?.boundsInRoot ?: return run { failures += "$label: no strip" }
        if (tray.left > 1f || tray.right < screen.right - 1f || tray.bottom < screen.bottom - 1f) failures += "$label: the tray is not along the screen's bottom ($tray)"
        if (hints.boundsInRoot.top < tray.top || hints.boundsInRoot.bottom > tray.bottom + 1f) failures += "$label: the hint bar is not on the tray"
        textProblems(hints, allow).mapTo(failures) { "$label: $it" }
        // The field: whole, inside the strip (under the top bar), above the tray.
        val field = findTagged(root, fieldTag) ?: return run { failures += "$label: no field" }
        notFullyShown(field, root)?.let { failures += "$label: the field $it" }
        val shownField = field.boundsInRoot
        if (shownField.bottom > tray.top + 1f) failures += "$label: the field reaches under the tray (${shownField.bottom} > ${tray.top})"
        if (shownField.top < strip.top - 1f) failures += "$label: the field reaches the top bar (${shownField.top} < ${strip.top})"
        val typed = findTagged(field, typedTag(fieldTag))?.let { node -> textLayout(node)?.let { node to it } }
        if (typed != null && typed.second.size.width > typed.first.boundsInRoot.width + 1f) {
            // A name longer than the field slides left to keep the caret in view (CaretLine): cut at its sides by design, never in height.
            if (typed.first.boundsInRoot.height < typed.second.size.height - 1f) failures += "$label: the field's text is cut in height"
        } else {
            textProblems(field, allow).mapTo(failures) { "$label: $it" }
        }
        // What follows: its first line whole, under the field and above the tray (the rest may scroll).
        val tag = lineUnderTag ?: return
        val line = findTagged(root, tag) ?: return run { failures += "$label: no line under the field" }
        val text = firstText(line) ?: return run { failures += "$label: no text under the field" }
        val layout = textLayout(text) ?: return run { failures += "$label: the text under the field is not laid out" }
        val top = text.positionInRoot.y
        val firstLineBottom = top + layout.getLineBottom(0)
        val shown = text.boundsInRoot
        if (top < shownField.bottom - 1f) failures += "$label: what follows starts above the field's bottom ($top < ${shownField.bottom})"
        if (firstLineBottom > tray.top + 1f) failures += "$label: the first line under the field reaches under the tray ($firstLineBottom > ${tray.top})"
        if (shown.top > top + 1f || shown.bottom < firstLineBottom - 1f) failures += "$label: the first line under the field is cut ($shown, line ${top}..$firstLineBottom)"
        textProblems(line, allow).mapTo(failures) { "$label: $it" }
    }

    private fun typedTag(fieldTag: String): String = if (fieldTag == SettingsTags.HEX_ROW) SettingsTags.HEX_TEXT else FixNameTags.typedText(fieldTag)

    private fun firstText(node: SemanticsNode): SemanticsNode? =
        if (SemanticsProperties.Text in node.config) node else node.children.firstNotNullOfOrNull { firstText(it) }

    private fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    /**
     * Android's keyboard (no tray): Use my own name with the line saying why there is no list keeps
     * the Settings panel's plain column, its title whole at the strip's top at every size (the
     * tray's layout gives the header way from the top, which only Plumemu's tray may do).
     */
    @Test
    fun ownNameOnAndroidsPathKeepsItsTitle() {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { NameFixTypingSettings(own = true, android = true) } }
        val title = ApplicationProvider.getApplicationContext<Context>().getString(R.string.game_own_name_title)
        val failures = mutableListOf<String>()
        for ((eachFont, each) in TextSize.entries.map { PlumFont.Sans to it } + listOf(PlumFont.Serif to TextSize.Largest, PlumFont.Mono to TextSize.Largest)) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "own_name_android at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val strip = findTagged(root, SettingsTags.STRIP)?.boundsInRoot ?: return run { failures += "$label: no strip" }
            val node = findText(root, title) ?: return run { failures += "$label: no title" }
            notFullyShown(node, root)?.let { failures += "$label: the title $it" }
            if (node.boundsInRoot.top < strip.top - 1f) failures += "$label: the title is above the strip (${node.boundsInRoot.top} < ${strip.top})"
            if (findTagged(root, SettingsTags.TRAY) != null) failures += "$label: a tray on Android's path"
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun findText(
        node: SemanticsNode,
        text: String,
    ): SemanticsNode? =
        if (node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.any { it.text == text } == true) node else node.children.firstNotNullOfOrNull { findText(it, text) }

    @Test
    fun hexField() = check("hex", SettingsTags.HEX_ROW, lineUnderTag = null) { HexTypingSettings() }

    @Test
    fun hexFieldEmpty() = check("hex_empty", SettingsTags.HEX_ROW, lineUnderTag = null) { HexTypingSettings(typed = "") }

    @Test
    fun hexFieldWithAWrongCode() = check("hex_wrong", SettingsTags.HEX_ROW, SettingsTags.HEX_ERROR) { HexTypingSettings(typed = "zz", tried = true) }

    @Test
    fun fixName() = check("fix_name", FixNameTags.FIELD, GamePanelTags.row(resultKey(0))) { NameFixTypingSettings() }

    @Test
    fun ownName() = check("own_name", FixNameTags.OWN_FIELD, GamePanelTags.row(SAVE_KEY)) { NameFixTypingSettings(own = true) }

    @Test
    fun ownNameRefused() = check("own_name_refused", FixNameTags.OWN_FIELD, FixNameTags.REFUSED) { NameFixTypingSettings(own = true, refused = true) }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsKeyboardFitNovaTest : SettingsKeyboardFitBase(576, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsKeyboardFitRp5Test : SettingsKeyboardFitBase(768, 432)

/** The shortest reference size: the tray takes the most of the panel's height here. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsKeyboardFitRp4ProTest : SettingsKeyboardFitBase(667, 375)
