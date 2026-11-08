package com.plumemu.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.components.coverBoxShadow
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.settings.screen.ColorTileLook
import com.plumemu.settings.screen.colorSetName
import com.plumemu.settings.screen.tinyHome
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.WidthClass

/**
 * "Pick a look": [SetupLooks.sets] as the Settings colours panel draws them (a tiny home screen in each set's colours,
 * its name under it), two rows of [SetupLooks.COLUMNS]. A or a tap picks one and goes on; the tile the device suggests is
 * focused first. Nothing applies while the cursor moves: the tiles are the preview.
 */
@Composable
internal fun ColumnScope.LookStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    val letters = LocalButtonLetters.current
    val confirm = stringResource(letters.confirm)
    val back = stringResource(letters.back)
    val measurer = rememberTextMeasurer()
    val keyCap = PlumTheme.type.keyCap
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val shape = RoundedCornerShape(PlumDimens.settingsPaletteCorner)
    // Stacked under the title two rows of Settings' tiles run past a 4:3 screen by less than the title is tall, which then scrolled away.
    val tileAspect = if (PlumTheme.layout.widthClass == WidthClass.Narrow) PlumDimens.SETUP_LOOK_TILE_ASPECT_NARROW else PlumDimens.SETTINGS_COLOR_TILE_ASPECT
    SetupLooks.sets.chunked(SetupLooks.COLUMNS).forEachIndexed { row, sets ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsColorTileGap)) {
            sets.forEachIndexed { column, set ->
                val control = SetupControl.lookTile(row * SetupLooks.COLUMNS + column) ?: return@forEachIndexed
                val isFocused = state.focus == control
                val page = state.deviceLook.pageOf(set).color
                val look = remember(set, page) { ColorTileLook(PlumColors.derive(page, set.textMode, set.accentColor), set.buttons) }
                Column(
                    Modifier.weight(1f).setupControl(state, control, onAction).testTag(SetupTags.LOOK_TILE),
                    verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsColorTileNameGap),
                ) {
                    // A thin collar like a cover's; focus lights its groove.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .collar(CollarSize.Cover, focused = isFocused, radius = PlumDimens.settingsPaletteCorner)
                            .aspectRatio(tileAspect)
                            .coverBoxShadow(PlumDimens.settingsPaletteCorner)
                            .drawWithCache { tinyHome(look, confirm, back, measurer, keyCap) }
                            .border(PlumDimens.settingsSwatchEdge, colors.chip, shape),
                    )
                    BasicText(
                        text = stringResource(colorSetName(set)),
                        style = if (isFocused) type.colorTileNameCurrent.copy(color = colors.accent) else type.colorTileName.copy(color = colors.text2),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // A short last row keeps the tiles' width.
            repeat(SetupLooks.COLUMNS - sets.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (state.message != null) StepNote(state.message, R.string.setup_folder_note)
}
