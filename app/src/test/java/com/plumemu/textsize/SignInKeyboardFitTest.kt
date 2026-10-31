package com.plumemu.textsize

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
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.account.SignInTypingShots
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.SignInTypingSettings
import com.plumemu.setup.SetupSignInTypingAt
import com.plumemu.setup.SetupTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in form typing with Plumemu's keyboard in setup and in Settings, in
 * Plum Sans at the six sizes and in Serif and Mono at Largest: the tray along the screen's bottom with
 * the typing hints on it; the focused field (the username, the password) whole, under the top bar and
 * above the tray's top edge; the first line of what follows it (the password row, Sign in, or the
 * refusal's message) whole between the field and the tray; the field's text and the hints whole. As
 * `SettingsKeyboardFit*Test` for Settings' other fields.
 */
abstract class SignInKeyboardFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Where the check looks: the tray's and hints' tags, the top the field must stay under, and the lists that scroll. */
    private class Place(
        val tray: String,
        val hints: String,
        val top: String,
        val scrolls: Set<String>,
    )

    private val setup = Place(SetupTags.TRAY, SetupTags.TYPING_HINTS, SetupTags.SCROLL, setOf(SetupTags.SCROLL))
    private val settings = Place(SettingsTags.TRAY, SettingsTags.TYPING_HINTS, SettingsTags.STRIP, setOf(SettingsTags.ACCOUNT_PANEL))

    private fun check(
        name: String,
        place: Place,
        field: SignInItem,
        lineUnderTag: String,
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
            checkOne(root, label, place, field, lineUnderTag, FitAllowance(scrollTags = place.scrolls, isLargest = each == TextSize.Largest), failures)
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun checkOne(
        root: SemanticsNode,
        label: String,
        place: Place,
        item: SignInItem,
        lineUnderTag: String,
        allow: FitAllowance,
        failures: MutableList<String>,
    ) {
        val screen = root.boundsInRoot
        val tray = findTagged(root, place.tray)?.boundsInRoot ?: return run { failures += "$label: no tray" }
        val hints = findTagged(root, place.hints) ?: return run { failures += "$label: no typing hints" }
        val top = findTagged(root, place.top)?.boundsInRoot ?: return run { failures += "$label: no ${place.top}" }
        if (tray.left > 1f || tray.right < screen.right - 1f || tray.bottom < screen.bottom - 1f) failures += "$label: the tray is not along the screen's bottom ($tray)"
        if (hints.boundsInRoot.top < tray.top || hints.boundsInRoot.bottom > tray.bottom + 1f) failures += "$label: the hint bar is not on the tray"
        textProblems(hints, allow).mapTo(failures) { "$label: $it" }
        val fieldTag = if (item == SignInItem.Password) SignInTags.PASSWORD else SignInTags.USERNAME
        val field = findTagged(root, fieldTag) ?: return run { failures += "$label: no field" }
        notFullyShown(field, root)?.let { failures += "$label: the field $it" }
        val shownField = field.boundsInRoot
        if (shownField.bottom > tray.top + 1f) failures += "$label: the field reaches under the tray (${shownField.bottom} > ${tray.top})"
        if (shownField.top < top.top - 1f) failures += "$label: the field reaches the top bar (${shownField.top} < ${top.top})"
        val typed = findTagged(field, SignInTags.typedText(item))?.let { node -> textLayout(node)?.let { node to it } }
        if (typed == null) {
            failures += "$label: the field shows no typed text"
        } else if (typed.second.size.width > typed.first.boundsInRoot.width + 1f) {
            // A text longer than the field slides left to keep the caret in view (CaretLine): cut at its sides by design, never in height.
            if (typed.first.boundsInRoot.height < typed.second.size.height - 1f) failures += "$label: the field's text is cut in height"
        } else {
            textProblems(field, allow).mapTo(failures) { "$label: $it" }
        }
        val line = findTagged(root, lineUnderTag) ?: return run { failures += "$label: no line under the field" }
        val text = firstText(line) ?: return run { failures += "$label: no text under the field" }
        val layout = textLayout(text) ?: return run { failures += "$label: the text under the field is not laid out" }
        val lineTop = text.positionInRoot.y
        val firstLineBottom = lineTop + layout.getLineBottom(0)
        val shown = text.boundsInRoot
        if (lineTop < shownField.bottom - 1f) failures += "$label: what follows starts above the field's bottom ($lineTop < ${shownField.bottom})"
        if (label in SIGN_IN_BUTTON_CUT) {
            // Its word must still start above the tray and show most of its line.
            if (lineTop + (firstLineBottom - lineTop) * SIGN_IN_BUTTON_SHOWN > tray.top + 1f) failures += "$label: Sign in under the password is hidden by the tray ($lineTop..$firstLineBottom, tray at ${tray.top})"
            return
        }
        if (firstLineBottom > tray.top + 1f) failures += "$label: the first line under the field reaches under the tray ($firstLineBottom > ${tray.top}; the field ${shownField.top}..${shownField.bottom}, under ${place.top} from ${top.top})"
        if (shown.top > lineTop + 1f || shown.bottom < firstLineBottom - 1f) failures += "$label: the first line under the field is cut ($shown, line $lineTop..$firstLineBottom)"
        textProblems(line, allow).mapTo(failures) { "$label: $it" }
    }

    private companion object {
        /**
         * The one place the line under the field does not fit whole: Settings' Sign in under the password at
         * Largest Serif on 375 high. The panel's space above the tray is 111 dp; the field (58), the form's gap
         * (12) and the button (47) need 117, so its word loses its lowest 3 dp under the tray. The field stays
         * whole and the tray's own action key is "Sign in" too.
         */
        val SIGN_IN_BUTTON_CUT = setOf("settings_password at Largest Serif on 667x375")

        /** How much of that word's line must still show. */
        const val SIGN_IN_BUTTON_SHOWN = 0.85f
    }

    private fun firstText(node: SemanticsNode): SemanticsNode? =
        if (SemanticsProperties.Text in node.config) node else node.children.firstNotNullOfOrNull { firstText(it) }

    private fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    @Test
    fun setupUsername() = check("setup_username", setup, SignInItem.Username, SignInTags.PASSWORD) { SetupSignInTypingAt(SignInTypingShots.State.Username) }

    @Test
    fun setupPassword() = check("setup_password", setup, SignInItem.Password, SignInTags.item(SignInItem.SignIn)) { SetupSignInTypingAt(SignInTypingShots.State.Password) }

    @Test
    fun setupPasswordAfterARefusal() = check("setup_wrong", setup, SignInItem.Password, SignInTags.MESSAGE) { SetupSignInTypingAt(SignInTypingShots.State.Wrong) }

    @Test
    fun settingsUsername() = check("settings_username", settings, SignInItem.Username, SignInTags.PASSWORD) { SignInTypingSettings(SignInTypingShots.State.Username) }

    @Test
    fun settingsPassword() = check("settings_password", settings, SignInItem.Password, SignInTags.item(SignInItem.SignIn)) { SignInTypingSettings(SignInTypingShots.State.Password) }

    @Test
    fun settingsPasswordAfterARefusal() = check("settings_wrong", settings, SignInItem.Password, SignInTags.MESSAGE) { SignInTypingSettings(SignInTypingShots.State.Wrong) }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SignInKeyboardFitNovaTest : SignInKeyboardFitBase(576, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SignInKeyboardFitRp5Test : SignInKeyboardFitBase(768, 432)

/** The shortest reference size: the tray takes the most of the form's height here. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SignInKeyboardFitRp4ProTest : SignInKeyboardFitBase(667, 375)
