package com.plumemu.secondscreen

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Audit frames of the Thor's bottom-screen card (538x468 dp): Recent, Favorites, a system, a game and the empty states, in Plumemu
 * and Daylight, with a dump of every text's bounds and first baseline (px). Written only when `PLUM_SHOTS_DIR` is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class PolishAuditCardShotsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var set by mutableStateOf(ColorSet.Plumemu)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Suppress("DEPRECATION")
    @Before
    fun sendTheBattery() {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100))
    }

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.Text in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun write(name: String) {
        val dir = System.getenv("PLUM_SHOTS_DIR") ?: return
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val lines =
            texts(root).filter { it.boundsInRoot.width > 0f }.map { n ->
                val b = n.boundsInRoot
                val layouts = mutableListOf<TextLayoutResult>()
                if (SemanticsActions.GetTextLayoutResult in n.config) n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val l = layouts.firstOrNull()
                val ell = l?.let { r -> (0 until r.lineCount).any { r.isLineEllipsized(it) } } ?: false
                "${b.left}\t${b.top}\t${b.right}\t${b.bottom}\t${l?.let { b.top + it.firstBaseline } ?: ""}\t${l?.lineCount ?: 0}\t$ell\t\t${n.config[SemanticsProperties.Text].joinToString().replace('\n', ' ')}"
            }
        File(dir, "$name.tsv").writeText("density\t${compose.density.density}\n" + lines.joinToString("\n"))
    }

    @Test
    fun theCards() {
        compose.setContent {
            val settings = PlumSettings(appearance = AppearanceSettings().withColors(set), flags = SetupFlags(setupCompleted = true)).toThemeSettings()
            PlumTheme(settings) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(content) } }
        }
        val cases =
            listOf(
                "recent" to BOARD_RECENT,
                "recent_real" to realRecent(),
                "recent_empty" to RecentEntry("Recent", 0, LOOKS),
                "favorites" to BOARD_FAVORITES,
                "favorites_few" to BOARD_FAVORITES.copy(gameCount = 2, systemCount = 2, games = BOARD_FAVORITES.games.take(2)),
                "favorites_empty" to FavoritesEntry("Favorites", 0, 0, emptyList(), LOOKS),
                "system" to BOARD_SYSTEM,
                "game" to BOARD_GAME,
                "game_no_ra" to BOARD_GAME_NO_RA,
                "game_long_title" to BOARD_GAME.copy(title = "The Legend of Zelda: A Link to the Past Four Swords Anniversary Edition"),
                "game_no_description" to BOARD_GAME.copy(details = null),
                "game_never_played" to BOARD_GAME_NO_RA.copy(lastPlayedDaysAgo = null, weeks = PlayWeeks(List(8) { 0L }, 0, 0)),
                "calm" to SecondScreenContent.Calm,
            )
        for ((setName, s) in listOf("plumemu" to ColorSet.Plumemu, "daylight" to ColorSet.Daylight)) {
            for ((case, each) in cases) {
                // Calm first, as the window shows it, so each card enters as it does on the device.
                compose.runOnIdle {
                    set = s
                    content = SecondScreenContent.Calm
                }
                compose.waitForIdle()
                compose.runOnIdle { content = each }
                compose.waitForIdle()
                write("card_${case}_538x468_$setName")
            }
        }
    }
}
