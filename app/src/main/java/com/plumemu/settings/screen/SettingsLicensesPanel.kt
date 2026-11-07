package com.plumemu.settings.screen

import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * About › Open-source licenses: the list of what ships in the APK (LicenseNotices) and, for an
 * entry whose text ships in the assets, that text. Read from the APK, off the main thread; never
 * from the network.
 */

@Composable
internal fun LicensesPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    if (focus.column == SettingsColumn.Panel && focus.page == PanelPage.LicenseText) {
        LicenseTextPage(LicenseNotices.all[focus.licenseIndex.coerceIn(LicenseNotices.all.indices)], model.licenseScroll, model)
    } else {
        LicenseListPage(if (focus.column == SettingsColumn.Panel) focus.panelIndex else -1, touch)
    }
}

/** A panel's text that Up and Down scroll, in steps of a few rows ([SettingsPanelModel.licenseScroll]); [key] starts the count anew. */
@Composable
internal fun rememberPressedScroll(
    key: Any?,
    presses: Int,
    model: SettingsModel,
): ScrollState {
    val scroll = rememberScrollState()
    // The model cannot know the page's size: it asks, so a press that cannot scroll stays silent.
    SideEffect { model.licenseCanScroll = { down -> if (down) scroll.canScrollForward else scroll.canScrollBackward } }
    val motion = PlumTheme.motion
    val isMotionOn = motion.isOn
    val stepPx = with(LocalDensity.current) { (PlumTheme.layout.rowHeight * LICENSE_SCROLL_ROWS).toPx() }
    var applied by remember(key) { mutableIntStateOf(presses) }
    LaunchedEffect(presses) {
        val delta = presses - applied
        applied = presses
        val target = (scroll.value + delta * stepPx).toInt().coerceIn(0, scroll.maxValue)
        if (delta != 0) if (isMotionOn) scroll.animateScrollTo(target, motion.glideSpec()) else scroll.scrollTo(target)
    }
    return scroll
}

@Composable
private fun LicenseListPage(
    focusedIndex: Int,
    touch: SettingsTouch,
) {
    PanelListColumn(Modifier.fillMaxHeight(), listKeepsEnd = true) {
        PanelTitle(stringResource(R.string.settings_licenses))
        PanelBody(stringResource(R.string.settings_licenses_body))
        LicenseRows(focusedIndex, touch)
    }
}

/** The entries, 2 apart like the options; scrolls so the focused one stays on screen. */
@Composable
private fun LicenseRows(
    focusedIndex: Int,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val latest by rememberUpdatedState(focusedIndex)
    // The header gives way to a card too tall for what it leaves. It comes back only when the list is left, so the walk does not jump.
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val room = LocalPanelListRoom.current
    LaunchedEffect(room) {
        snapshotFlow { heights[latest] ?: 0 }.collect { height -> room?.tallestRow = if (latest < 0) 0 else maxOf(room?.tallestRow ?: 0, height) }
    }
    KeepFocusedColumn(
        { latest },
        LicenseNotices.all.size,
        PlumDimens.listRowGap,
        Modifier.testTag(SettingsTags.LICENSE_LIST),
        fadeTag = SettingsTags.LICENSE_LIST,
        endRoom = SlabDimens.listEndRoom,
    ) { place ->
        LicenseNotices.all.forEachIndexed { index, notice ->
            val focused = index == focusedIndex
            Row(
                modifier =
                    place(index)
                        .fillMaxWidth()
                        .heightIn(min = PlumTheme.layout.rowHeight)
                        .onSizeChanged { heights[index] = it.height }
                        .focusPill(focused)
                        .tapTarget { touch.onPanelItem(index) }
                        .testTag(SettingsTags.panelItem(index))
                        .padding(horizontal = PlumDimens.settingsPanelRowPadding, vertical = PlumDimens.settingsFolderCardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val nameStyle = if (focused) type.setupRowTitleFocused.copy(color = colors.strong) else type.meta.copy(color = colors.text2)
                    BasicText(stringResource(notice.name), style = nameStyle)
                    BasicText(stringResource(notice.licence), style = type.smallFact.copy(color = if (focused) colors.meta else colors.muted), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun LicenseTextPage(
    notice: LicenseNotice,
    scrollPresses: Int,
    model: SettingsModel,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val context = LocalContext.current
    val text by produceState<LicenseText?>(null, notice) {
        value = withContext(Dispatchers.IO) { readLicenseText(context.assets, notice) { Log.w(TAG, it) } }
    }
    val scroll = rememberPressedScroll(notice, scrollPresses, model)
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
        PanelTitle(stringResource(notice.name))
        BasicText(stringResource(notice.licence), style = type.smallFact.copy(color = colors.meta), maxLines = 1, modifier = Modifier.onRowTextLine())
        val body =
            when (val loaded = text) {
                is LicenseText.Loaded -> loaded.text
                LicenseText.NotShipped -> stringResource(R.string.settings_licence_not_shipped)
                LicenseText.Unreadable -> stringResource(R.string.settings_licence_unreadable)
                null -> ""
            }
        Box(Modifier.padding(top = PlumDimens.settingsPanelListGap)) {
            BasicText(
                body,
                style = type.panelBody.copy(color = colors.text2),
                modifier = Modifier.onRowTextLine().verticalScroll(scroll).testTag(SettingsTags.LICENSE_TEXT),
            )
            ScrollEdgeFades(scroll, SettingsTags.LICENSE_TEXT)
        }
    }
}

/** The scroll position that shows all of [span] (px inside the list) in a viewport of [viewport] at [scroll]. */
internal fun keepVisible(
    scroll: Int,
    viewport: Int,
    span: IntRange,
): Int =
    when {
        span.first < scroll -> span.first
        span.last + 1 > scroll + viewport -> span.last + 1 - viewport
        else -> scroll
    }

/** Up and down move the text by three rows. */
private const val LICENSE_SCROLL_ROWS = 3
private const val TAG = "Settings"
