package com.plumemu.settings.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
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

@Composable
private fun LicenseListPage(
    focusedIndex: Int,
    touch: SettingsTouch,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
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
    val scroll = rememberScrollState()
    val motion = PlumTheme.motion
    val isMotionOn = motion.isOn
    // Each row's top and bottom inside the list, to keep the focused one on screen.
    val spans = remember { HashMap<Int, IntRange>() }
    LaunchedEffect(focusedIndex, isMotionOn) {
        val span = spans[focusedIndex] ?: return@LaunchedEffect
        val target = keepVisible(scroll.value, scroll.viewportSize, span)
        // Transform-free scrolling still moves things: a jump when motion is off.
        if (target != scroll.value) if (isMotionOn) scroll.animateScrollTo(target, motion.glideSpec()) else scroll.scrollTo(target)
    }
    Column(
        Modifier.verticalScroll(scroll).testTag(SettingsTags.LICENSE_LIST),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
    ) {
        LicenseNotices.all.forEachIndexed { index, notice ->
            val focused = index == focusedIndex
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PlumTheme.layout.rowHeight)
                        .onPlaced { spans[index] = it.positionInParent().y.toInt() until it.positionInParent().y.toInt() + it.size.height }
                        .focusPill(focused)
                        .tapTarget { touch.onPanelItem(index) }
                        .testTag(SettingsTags.panelItem(index))
                        .padding(horizontal = PlumDimens.settingsPanelRowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val nameStyle = if (focused) type.setupRowTitleFocused.copy(color = colors.strong) else type.meta.copy(color = colors.text2)
                    BasicText(stringResource(notice.name), style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(stringResource(notice.licence), style = type.smallFact.copy(color = if (focused) colors.meta else colors.muted), maxLines = 1)
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
    val scroll = rememberScrollState()
    // The model cannot know the page's size: it asks, so a press that cannot scroll stays silent.
    SideEffect { model.licenseCanScroll = { down -> if (down) scroll.canScrollForward else scroll.canScrollBackward } }
    val motion = PlumTheme.motion
    val isMotionOn = motion.isOn
    val stepPx = with(LocalDensity.current) { (PlumTheme.layout.rowHeight * LICENSE_SCROLL_ROWS).toPx() }
    var applied by remember(notice) { mutableIntStateOf(scrollPresses) }
    LaunchedEffect(scrollPresses) {
        val delta = scrollPresses - applied
        applied = scrollPresses
        val target = (scroll.value + delta * stepPx).toInt().coerceIn(0, scroll.maxValue)
        if (delta != 0) if (isMotionOn) scroll.animateScrollTo(target, motion.glideSpec()) else scroll.scrollTo(target)
    }
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
        BasicText(
            body,
            style = type.panelBody.copy(color = colors.text2),
            modifier = Modifier.padding(top = PlumDimens.settingsPanelListGap).onRowTextLine().verticalScroll(scroll).testTag(SettingsTags.LICENSE_TEXT),
        )
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
