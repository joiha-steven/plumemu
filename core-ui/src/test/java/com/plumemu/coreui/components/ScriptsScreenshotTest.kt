package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Settings rows and the hint bar in each script Plumemu is being translated into
 * (docs/conventions/translation.md › Fonts): Vietnamese in Plum Sans, Serif and Mono (every
 * precomposed letter is in the three fonts; the third row holds the tallest stacked marks), and
 * Chinese, Korean and Japanese, which no Plum font has, drawn by Android's fallback letter by
 * letter. Each script is drawn with its locale as the process default, as `AppLocale.wrap` sets
 * it, because Compose chooses the Chinese or Japanese form of a shared Han character from it.
 * The words are sample translations, not the shipped ones ([load]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ScriptsScreenshotTest {
    private class Sample(val label: String, val value: String, val badge: String?)

    private class Script(val shot: String, val tag: String, val rows: List<Sample>, val hints: List<Pair<String, String>>)

    private val before = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(before)

    @Composable
    private fun Rows(script: Script) {
        Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.fillMaxSize().padding(horizontal = PlumDimens.stripMarginSide, vertical = PlumDimens.stripMarginSide)) {
                script.rows.forEachIndexed { i, row ->
                    OptionRow(
                        label = row.label,
                        look = if (i == 0) OptionRowLook.Focused else OptionRowLook.Normal,
                        modifier = Modifier.fillMaxWidth(),
                        value = row.value,
                        badge = row.badge,
                    )
                }
            }
            HintBar {
                script.hints.forEach { (cap, label) -> Hint(cap, label) }
            }
        }
    }

    private fun capture(
        script: Script,
        fonts: List<PlumFont> = listOf(PlumFont.Sans),
    ) {
        Locale.setDefault(Locale.forLanguageTag(script.tag))
        ReferenceShots.capture(script.shot, fonts) { Rows(script) }
    }

    @Test
    fun vietnamese() = capture(load("script_vietnamese", "vi", "values-vi"), PlumFont.entries)

    @Test
    fun chineseSimplified() = capture(load("script_chinese_simplified", "zh-CN", "values-zh"))

    @Test
    fun chineseTraditional() = capture(load("script_chinese_traditional", "zh-TW", "values-zh-rTW"))

    @Test
    fun korean() = capture(load("script_korean", "ko", "values-ko"))

    @Test
    fun japanese() = capture(load("script_japanese", "ja", "values-ja"))

    /**
     * The sample words live in `src/test/resources/script-samples/res/<folder>/samples.tsv`, the only
     * kind of path where the english-only guard allows other scripts: a `row` line is label, value
     * and badge (empty for none), a `hint` line a key cap and its label, separated by tabs.
     */
    private fun load(
        shot: String,
        tag: String,
        folder: String,
    ): Script {
        val path = "script-samples/res/$folder/samples.tsv"
        val text = javaClass.classLoader?.getResource(path)?.readText() ?: error("no test resource $path")
        val lines = text.lines().filter { it.isNotBlank() }.map { it.split('\t') }
        val rows = lines.filter { it[0] == "row" }.map { Sample(it[1], it[2], it.getOrNull(3)?.ifEmpty { null }) }
        val hints = lines.filter { it[0] == "hint" }.map { it[1] to it[2] }
        return Script(shot, tag, rows, hints)
    }
}
