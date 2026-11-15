package com.plumemu.game

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.plumemu.coredata.settings.AppsScreen
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** The store apps' line in the app menu is one line at 576x432, default text size, in every translation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class StoreHintFitTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun SemanticsNode.find(text: String): SemanticsNode? =
        if (config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } == text) this else children.firstNotNullOfOrNull { it.find(text) }

    private fun oneLine(tag: String, hint: StoreHint) {
        val locale = Locale.forLanguageTag(tag)
        val panel = OpenPanel(GameRef("${hint.packageName}/.Main", "App"), PanelScreen.AppMenu(canUninstall = true, canChooseKind = true, otherScreen = AppsScreen.Bottom, storeHint = hint))
        compose.setContent {
            val context = LocalContext.current
            val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration) {
                PlumTheme(PlumThemeSettings.Graphite) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
            }
        }
        compose.waitForIdle()
        val text = RuntimeEnvironment.getApplication().createConfigurationContext(android.content.res.Configuration().apply { setLocale(locale) }).getString(hint.message)
        val node = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().find(text)
        assertNotNull("$tag ${hint.name}: no line '$text'", node)
        val results = mutableListOf<TextLayoutResult>()
        node!!.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertEquals("$tag ${hint.name}: '$text' wraps", 1, results.single().lineCount)
    }

    @Test
    fun gameNativeIn_en() = oneLine("en", StoreHint.GameNative)

    @Test
    fun droidDeckIn_en() = oneLine("en", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_es() = oneLine("es", StoreHint.GameNative)

    @Test
    fun droidDeckIn_es() = oneLine("es", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_fr() = oneLine("fr", StoreHint.GameNative)

    @Test
    fun droidDeckIn_fr() = oneLine("fr", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_ja() = oneLine("ja", StoreHint.GameNative)

    @Test
    fun droidDeckIn_ja() = oneLine("ja", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_ko() = oneLine("ko", StoreHint.GameNative)

    @Test
    fun droidDeckIn_ko() = oneLine("ko", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_vi() = oneLine("vi", StoreHint.GameNative)

    @Test
    fun droidDeckIn_vi() = oneLine("vi", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_zh() = oneLine("zh", StoreHint.GameNative)

    @Test
    fun droidDeckIn_zh() = oneLine("zh", StoreHint.DroidDeck)

    @Test
    fun gameNativeIn_zhTW() = oneLine("zh-TW", StoreHint.GameNative)

    @Test
    fun droidDeckIn_zhTW() = oneLine("zh-TW", StoreHint.DroidDeck)
}
