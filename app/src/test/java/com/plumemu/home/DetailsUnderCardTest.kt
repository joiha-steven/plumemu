package com.plumemu.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.Beetle
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.appNotAGameConfirmContent
import com.plumemu.game.boxArtStoredContent
import com.plumemu.game.coreMissingMessage
import com.plumemu.game.coreQuestionMessage
import com.plumemu.game.launchMessage
import com.plumemu.game.sampleFacts
import com.plumemu.game.sampleListing
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.choice.CoreToConfirm
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A card open over the wide details column never cuts what the column shows: every text and the cover under the column is either
 * not drawn (and hidden from semantics), clear of the card, or wholly inside it. Before, the card's bottom edge ran through the
 * title's letters with the subtitle showing under it.
 */
abstract class DetailsUnderCardBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")
    private val celeste = GameRowModel(id = "901", title = "Celeste", region = "", systemId = "android", sortName = "celeste")
    private val androidGames
        get() = HomeContent.of(
            recent = emptyList(),
            favorites = emptyList(),
            apps = emptyList(),
            systems = listOf(SystemGames("android", "Android games", listOf(celeste), code = "AND")),
            hasBoxArt = stop.isNotEmpty(),
        )
    private val opened = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter)

    /** Keys after the games are focused: the narrow screen shows its details as a stop of their own. */
    protected open val stop: List<Action> = emptyList()

    /** The narrow screen has the details stop only with box art. */
    protected open val board: HomeContent get() = FakeHomeContent.board()

    private fun show(panel: OpenPanel?, content: HomeContent = board, keys: List<Action> = BoardGamesFocused) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) { HomeAt(content, keys + stop, panel = panel) }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNode.isHiddenFromUser(): Boolean = generateSequence(this) { it.parent }.any { SemanticsProperties.InvisibleToUser in it.config }

    /** Drawn texts and the cover under the details column, as (label, bounds). */
    private fun detailsParts(): List<Pair<String, Rect>> {
        val column = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode()
        val out = mutableListOf<Pair<String, Rect>>()
        fun walk(node: SemanticsNode) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            val label = text ?: tag?.takeIf { it == HomeTags.DETAILS_COVER || it == HomeTags.DETAILS_COVER_SLOT }
            val b = node.boundsInRoot
            if (label != null && node.layoutInfo.isPlaced && b.width > 0f && b.height > 0f && !node.isHiddenFromUser()) out += label to b
            node.children.forEach(::walk)
        }
        walk(column)
        return out
    }

    private fun assertNoneCut(name: String) {
        val card = compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val cut = detailsParts().filter { (_, b) ->
            val inside = b.left >= card.left - 0.5f && b.right <= card.right + 0.5f && b.top >= card.top - 0.5f && b.bottom <= card.bottom + 0.5f
            b.overlaps(card) && !inside
        }
        assertTrue("$name: the card $card cuts details parts: ${cut.take(4)}", cut.isEmpty())
    }

    @Test
    fun controlTheColumnShowsWithNoCard() {
        show(null)
        assertTrue("the details column shows something with no card open", detailsParts().isNotEmpty())
    }

    @Test
    fun coreQuestion() {
        val question = CoreQuestion(1, "snes-6", "Super Metroid", Beetle.id, "RetroArch: Snes9x", CoreToConfirm("com.retroarch", "snes9x_libretro_android.so"), null)
        show(OpenPanel(game, PanelScreen.Message(coreQuestionMessage(question))))
        assertNoneCut("core question")
    }

    @Test
    fun coreMissing() {
        show(OpenPanel(game, PanelScreen.Message(coreMissingMessage(sampleListing().copy(systemName = "Super Nintendo"), Beetle.id, "RetroArch: Snes9x"))))
        assertNoneCut("core missing")
    }

    @Test
    fun chooseEmulator() {
        val offers = listOf(EmulatorOffer("snes9x", "Snes9x EX+", null), EmulatorOffer("retroarch-snes9x", "RetroArch: Snes9x", null))
        val message = launchMessage(LaunchOutcome.EmulatorNotChosen(6, "snes", "Super Nintendo", offers, "snes9x"), sampleListing().copy(systemName = "Super Nintendo"))!!
        show(OpenPanel(game, PanelScreen.Message(message)))
        assertNoneCut("choose emulator")
    }

    @Test
    fun boxArtSaved() {
        show(OpenPanel(game, PanelScreen.Message(boxArtStoredContent(BoxArtPanelFixtures.portrait))))
        assertNoneCut("box art saved")
    }

    @Test
    fun menu() {
        val facts = sampleFacts(canFixBoxArt = true, canSort = true).copy(sort = SortGamesSetting.Name)
        show(OpenPanel(game, PanelScreen.Menu(facts), focus = 3))
        assertNoneCut("menu")
    }

    @Test
    fun notAGameOnAnAppGame() {
        val app = GameRef(celeste.id, celeste.title)
        val facts = sampleFacts(title = "Celeste").copy(systemName = "Android games", isAppGame = true, emulator = null)
        val menu = OpenPanel(app, PanelScreen.Menu(facts), focus = 4)
        show(OpenPanel(app, PanelScreen.Message(appNotAGameConfirmContent(app)), parent = menu), androidGames, opened)
        assertNoneCut("not a game")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = WIDE768)
class DetailsUnderCard768Test : DetailsUnderCardBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class DetailsUnderCard832Test : DetailsUnderCardBase()

/** The narrow details stop (576): the column is a stop after the games, and a card opened there must not cut it either. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = NARROW576)
class DetailsUnderCard576Test : DetailsUnderCardBase() {
    override val stop = listOf(Action.Move(Direction.Right))
    override val board get() = FakeHomeContent.withArtwork()
}
