package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInKind
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.effectiveBoxArtSource
import com.plumemu.coreui.theme.PlumDimens

/*
 * Settings › Accounts › Box art: where box art comes from. The row's panel lists the choices, each
 * with one line on what it brings; A applies. ScreenScraper while no usable account is signed in
 * opens the sign-in form first, and the choice is stored only once ScreenScraper accepted it (a
 * cancelled or failed sign-in leaves the setting as it was).
 */

/** A ScreenScraper account is signed in and ScreenScraper has not refused it. */
internal fun isScraperUsable(account: AccountStatus): Boolean = account is AccountStatus.SignedIn && !account.isRefused

/** The choices, in order: without ScreenScraper in this build, only the two that need no account. */
internal fun boxArtSourceChoices(account: AccountStatus): List<BoxArtSourceSetting> =
    if (account == AccountStatus.NotInThisBuild) {
        listOf(BoxArtSourceSetting.LibretroOnly, BoxArtSourceSetting.OwnCovers)
    } else {
        BoxArtSourceSetting.entries
    }

/** The source in force for the stored [settings] and [account]. */
internal fun boxArtSourceIn(
    settings: PlumSettings,
    account: AccountStatus,
): BoxArtSourceSetting = settings.library.effectiveBoxArtSource(isScraperUsable(account))

internal fun boxArtSourceName(source: BoxArtSourceSetting): Int =
    when (source) {
        BoxArtSourceSetting.ScreenScraper -> R.string.settings_box_art_source_screenscraper
        BoxArtSourceSetting.LibretroOnly -> R.string.settings_box_art_source_libretro
        BoxArtSourceSetting.OwnCovers -> R.string.settings_box_art_source_own
    }

private fun boxArtSourceLine(source: BoxArtSourceSetting): Int =
    when (source) {
        BoxArtSourceSetting.ScreenScraper -> R.string.settings_box_art_source_screenscraper_line
        BoxArtSourceSetting.LibretroOnly -> R.string.settings_box_art_source_libretro_line
        BoxArtSourceSetting.OwnCovers -> R.string.settings_box_art_source_own_line
    }

@Composable
internal fun BoxArtSourcePanel(
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val form = model.signIn.form
    if (form != null && form.kind == SignInKind.ScreenScraper && model.focus.page == PanelPage.ScraperSignIn) {
        SignInPage(stringResource(R.string.settings_account_title), form, touch)
        return
    }
    val inPanel = model.focus.column == SettingsColumn.Panel
    val focused = if (inPanel) model.focus.panelIndex else -1
    val choices = boxArtSourceChoices(model.content.account)
    val current = boxArtSourceIn(settings, model.content.account)
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_box_art_source))
        PanelBody(stringResource(R.string.settings_box_art_source_body))
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(choices, key = { index, _ -> index }, contentType = { _, _ -> "box-art-source" }) { index, source ->
                PanelItem(
                    title = stringResource(boxArtSourceName(source)),
                    detail = stringResource(boxArtSourceLine(source)),
                    isFocused = index == focused,
                    isQuiet = !inPanel && source == current,
                    padded = true,
                    badge = if (source == current) stringResource(R.string.settings_badge_current) else null,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}
