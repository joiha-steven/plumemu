package com.plumemu.coreui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A visual check of the theme: every colour token as a labelled swatch (with the
 * accent presets and the custom palette), and every text style in each Plum font. Two sheets
 * because both do not fit legibly on one 576×432 screen. Labels are test-only text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class TokenSheetScreenshotTest {
    @Test
    fun colourSheet() = ReferenceShots.capture("tokens_colors") { ColourSheet() }

    @Test
    fun typeSheet() = ReferenceShots.capture("tokens_type") { TypeSheet() }

    // The Settings panels' own roles: a second sheet, the first is full at 432 dp.
    @Test
    fun settingsTypeSheet() = ReferenceShots.capture("tokens_type_settings") { SettingsTypeSheet() }
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@Composable
private fun Sheet(
    title: String,
    content: @Composable () -> Unit,
) {
    val d = PlumTheme.dimens
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PlumTheme.colors.bg)
                .padding(start = d.stripMarginSide, end = d.stripMarginSide, top = d.stripMarginTop, bottom = d.stripMarginBottom),
        verticalArrangement = Arrangement.spacedBy(d.barGap),
    ) {
        BasicText(title, style = PlumTheme.type.topBar.copy(color = PlumTheme.colors.text2))
        content()
    }
}

@Composable
private fun ColourSheet() {
    val c = PlumTheme.colors
    val tokens =
        listOf(
            "bg" to c.bg, "text" to c.text, "strong" to c.strong, "sel" to c.sel, "sel2" to c.sel2,
            "text2" to c.text2, "meta" to c.meta, "muted" to c.muted, "chip" to c.chip,
            "accent" to c.accent, "onAccent" to c.onAccent,
        )
    Sheet("Colour tokens") {
        tokens.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, color) -> TokenSwatch(name, color) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PlumTheme.dimens.columnGap)) {
            Palette()
            AccentPresets()
        }
    }
}

@Composable
private fun TokenSwatch(
    name: String,
    color: Color,
) {
    val c = PlumTheme.colors
    Column(modifier = Modifier.width(80.dp)) {
        Box(
            Modifier
                .size(80.dp, 28.dp)
                .background(color, RoundedCornerShape(6.dp))
                .border(1.dp, c.chip, RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.size(4.dp))
        BasicText(name, style = PlumTheme.type.smallFact.copy(color = c.text))
        BasicText(color.hex(), style = PlumTheme.type.smallFact.copy(color = c.muted))
    }
}

@Composable
private fun Palette() {
    val d = PlumTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(d.swatchGapWide)) {
        CustomPalette.backgroundSwatches.chunked(CustomPalette.hueCount).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(d.swatchGapWide)) {
                row.forEach { rgb ->
                    Box(
                        Modifier
                            .size(d.swatchWide)
                            .background(rgb.toComposeColor(), RoundedCornerShape(6.dp))
                            .border(1.dp, PlumTheme.colors.chip, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentPresets() {
    val c = PlumTheme.colors
    val textMode = PlumTheme.type.textMode
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AccentPreset.entries.forEach { preset ->
            val color = preset.colorFor(textMode).toComposeColor()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(18.dp).background(color, RoundedCornerShape(9.dp)))
                BasicText("${preset.name} ${color.hex()}", style = PlumTheme.type.smallFact.copy(color = c.text2))
            }
        }
    }
}

private val labelWidth = 130.dp

@Composable
private fun TypeSheet() {
    val widthClass = PlumTheme.layout.widthClass
    val roles =
        listOf(
            "game" to TypeRole.GameTitle,
            "game focus" to TypeRole.GameTitleFocused,
            "game current" to TypeRole.GameTitleCurrent,
            "message" to TypeRole.MessageTitle,
            "font sample" to TypeRole.FontSample,
            "system" to TypeRole.SystemName,
            "sys. current" to TypeRole.SystemNameCurrent,
            "sys. focus" to TypeRole.SystemNameFocused,
            "option" to TypeRole.SettingsOption,
            "option focus" to TypeRole.SettingsOptionFocused,
            "details" to if (widthClass == WidthClass.Wide) TypeRole.DetailsTitleWide else TypeRole.DetailsTitleNarrow,
            "top bar" to TypeRole.TopBar,
            "meta" to TypeRole.Meta,
            "fact" to TypeRole.SmallFact,
            "hint" to TypeRole.HintLabel,
            "key cap" to TypeRole.KeyCap,
            "setup" to if (widthClass == WidthClass.Wide) TypeRole.SetupTitleWide else TypeRole.SetupTitleNarrow,
            "badge" to TypeRole.Badge,
        )
    TypeTable("Type at Medium; Serif and Mono scaled to x-height 0.53", roles)
}

@Composable
private fun SettingsTypeSheet() {
    val roles =
        listOf(
            "aa sample" to TypeRole.SettingsInkSample,
            "swatch +" to TypeRole.SettingsSwatchGlyph,
            "about" to TypeRole.SettingsAboutTitle,
            "panel meta" to TypeRole.SettingsPanelMeta,
            "panel item focus" to TypeRole.SettingsPanelItemFocused,
            "panel action" to TypeRole.SettingsPanelAction,
            "colors tile" to TypeRole.ColorTileName,
            "colors tile current" to TypeRole.ColorTileNameCurrent,
        )
    TypeTable("Settings panel type at Medium", roles)
}

@Composable
private fun TypeTable(
    title: String,
    roles: List<Pair<String, TypeRole>>,
) {
    val c = PlumTheme.colors
    val textMode = PlumTheme.type.textMode
    val typeByFont = PlumFont.entries.map { PlumTypography(it, TextSize.Medium, textMode) }
    Sheet(title) {
        Column {
            TypeRow(label = "") { i ->
                BasicText("Plum ${PlumFont.entries[i].name}", style = PlumTheme.type.smallFact.copy(color = c.muted))
            }
            roles.forEach { (name, role) ->
                TypeRow(label = "$name ${role.sizeSp.toInt()}/${role.weight}") { i ->
                    Sample(role, typeByFont[i][role])
                }
            }
        }
    }
}

@Composable
private fun TypeRow(
    label: String,
    cell: @Composable (fontIndex: Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, modifier = Modifier.width(labelWidth), style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.muted))
        for (i in PlumFont.entries.indices) {
            Box(Modifier.weight(1f)) { cell(i) }
        }
    }
}

@Composable
private fun Sample(
    role: TypeRole,
    style: TextStyle,
) {
    val c = PlumTheme.colors
    if (role == TypeRole.Badge) {
        BasicText(
            "Current",
            modifier = Modifier.background(c.accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
            style = style.copy(color = c.onAccent),
        )
    } else {
        BasicText("Metroid", style = style.copy(color = c.text), maxLines = 1, overflow = TextOverflow.Clip)
    }
}
