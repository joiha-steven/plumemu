package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.BoxArtProblem
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixBoxArtTags
import com.plumemu.game.FixNameState
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.SAVE_KEY
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.resultKey
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game panel's fields typing with Plumemu's keyboard at every text size, in Plum
 * Sans at the six sizes and in Serif and Mono at Largest: the tray is along the screen's bottom with the
 * hint bar on it; the field is whole, under the top bar and above the tray's top edge, and the first line
 * of what follows it (Fix name's first result, also one whose title wraps; Use my own name's Save or its
 * refusal; Fix box art's status line, also a long "nothing found" and "sign in again") is whole between
 * the field and the tray (the rest may scroll in its list); the field's text and the hints are whole.
 */
abstract class PanelKeyboardFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")

    private fun check(
        name: String,
        screen: PanelScreen,
        fieldTag: String,
        lineUnderTag: String,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        val panel = OpenPanel(game, screen)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
        }
        val combinations = TextSize.entries.map { PlumFont.Sans to it } + listOf(PlumFont.Serif to TextSize.Largest, PlumFont.Mono to TextSize.Largest)
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allow = FitAllowance(scrollTags = setOf(FixNameTags.RESULTS, FixNameTags.OWN_ROWS, FixBoxArtTags.ROWS), isLargest = each == TextSize.Largest)
            checkOne(root, label, fieldTag, lineUnderTag, allow, failures)
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun checkOne(
        root: SemanticsNode,
        label: String,
        fieldTag: String,
        lineUnderTag: String,
        allow: FitAllowance,
        failures: MutableList<String>,
    ) {
        val screen = root.boundsInRoot
        val tray = findTagged(root, FixNameTags.TRAY)?.boundsInRoot ?: return run { failures += "$label: no tray" }
        val hints = findTagged(root, HomeTags.HINT_BAR) ?: return run { failures += "$label: no hint bar" }
        val topBar = findTagged(root, HomeTags.TOP_BAR)?.boundsInRoot ?: return run { failures += "$label: no top bar" }
        if (tray.left > 1f || tray.right < screen.right - 1f || tray.bottom < screen.bottom - 1f) failures += "$label: the tray is not along the screen's bottom ($tray)"
        if (hints.boundsInRoot.top < tray.top || hints.boundsInRoot.bottom > tray.bottom + 1f) failures += "$label: the hint bar is not on the tray"
        textProblems(hints, allow).mapTo(failures) { "$label: $it" }
        // The field: whole, under the top bar, above the tray.
        val field = findTagged(root, fieldTag) ?: return run { failures += "$label: no field" }
        notFullyShown(field, root)?.let { failures += "$label: the field $it" }
        val shownField = field.boundsInRoot
        if (shownField.bottom > tray.top + 1f) failures += "$label: the field reaches under the tray (${shownField.bottom} > ${tray.top})"
        if (shownField.top < topBar.bottom - 1f) failures += "$label: the field reaches the top bar (${shownField.top} < ${topBar.bottom})"
        val fieldText = (findTagged(field, FixNameTags.typedText(fieldTag)) ?: findTagged(field, FixNameTags.placeholder(fieldTag)) ?: firstText(field))?.let { node -> textLayout(node)?.let { node to it } }
        if (fieldText != null && fieldText.second.size.width > fieldText.first.boundsInRoot.width + 1f) {
            // A name longer than the field slides left to keep the caret in view (CaretLine): cut at its sides by design, never in height.
            if (fieldText.first.boundsInRoot.height < fieldText.second.size.height - 1f) failures += "$label: the field's text is cut in height"
        } else {
            textProblems(field, allow).mapTo(failures) { "$label: $it" }
        }
        // What follows: its first line whole, under the field and above the tray (the rest may scroll).
        val line = findTagged(root, lineUnderTag) ?: return run { failures += "$label: no line under the field" }
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

    private fun firstText(node: SemanticsNode): SemanticsNode? =
        if (SemanticsProperties.Text in node.config) node else node.children.firstNotNullOfOrNull { firstText(it) }

    private fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    private fun typing(screen: PanelScreen): PanelScreen = screen.also { (it as com.plumemu.game.TypingScreen).typing.focusField() }

    @Test
    fun fixName() {
        val finder = TitleFinder(listOf(TitleChoice("Super Metroid", "Japan, USA, Europe", 1994, null), TitleChoice("Super Mario World", "USA", 1990, null)))
        val state = FixNameState("Super Metroid", finder).apply { showResults(query, finder.find(query)) }
        check("fix_name", typing(PanelScreen.FixName(state)), FixNameTags.FIELD, GamePanelTags.row(resultKey(0)))
    }

    @Test
    fun ownName() = check("own_name", typing(PanelScreen.OwnName(OwnNameState("Super Metroid"))), FixNameTags.OWN_FIELD, GamePanelTags.row(SAVE_KEY))

    @Test
    fun ownNameRefused() {
        val state = OwnNameState("Super Metroid").apply { onTextChange("  ") }
        state.onAction(Action.Move(com.plumemu.coreinput.Direction.Down))
        state.submit()
        check("own_name_refused", typing(PanelScreen.OwnName(state)), FixNameTags.OWN_FIELD, FixNameTags.REFUSED)
    }

    @Test
    fun fixNameWithALongFirstTitle() {
        val long = "Super Metroid: The Return of the Space Hunter to the Planet Zebes, Special Edition"
        val finder = TitleFinder(listOf(TitleChoice(long, "Japan, USA, Europe, Korea, Brazil, Australia", 1994, null), TitleChoice("Super Metroid", "USA", 1994, null)))
        val state = FixNameState("Super Metroid", finder).apply { showResults(query, finder.find(query)) }
        check("fix_name_long_title", typing(PanelScreen.FixName(state)), FixNameTags.FIELD, GamePanelTags.row(resultKey(0)))
    }

    @Test
    fun fixBoxArtNothingFoundForALongName() {
        val query = "Super Metroid Redesign Hack Version Two, Extended and Remastered Edition"
        val state =
            FixBoxArtState(query, "Super Nintendo").apply {
                searching(query)
                showSearch(query, BoxArtSearchOutcome.NothingFound)
            }
        check("fix_box_art_nothing_found", typing(PanelScreen.FixBoxArt(state)), FixBoxArtTags.FIELD, FixBoxArtTags.LINE)
    }

    @Test
    fun fixBoxArtSignInAgain() {
        val state =
            FixBoxArtState("Super Metroid", "Super Nintendo").apply {
                searching("Super Metroid")
                showSearch("Super Metroid", BoxArtSearchOutcome.Failed(BoxArtProblem.SignInAgain))
            }
        check("fix_box_art_sign_in_again", typing(PanelScreen.FixBoxArt(state)), FixBoxArtTags.FIELD, FixBoxArtTags.LINE)
    }

    @Test
    fun fixBoxArt() = check("fix_box_art", typing(PanelScreen.FixBoxArt(FixBoxArtState("Super Metroid", "Super Nintendo"))), FixBoxArtTags.FIELD, FixBoxArtTags.LINE)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PanelKeyboardFitNovaTest : PanelKeyboardFitBase(576, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PanelKeyboardFitRp5Test : PanelKeyboardFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class PanelKeyboardFitThorTest : PanelKeyboardFitBase(837, 471)

/** The shortest reference size: the tray takes the most of the panel's height here. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class PanelKeyboardFitRp4ProTest : PanelKeyboardFitBase(667, 375)
