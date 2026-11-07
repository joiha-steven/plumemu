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
 * cancelled or failed sign-in leaves the setting as it was). Under the choices, for a person whose
 * ScreenScraper panel does not offer it, the box art work that needs no account.
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

/** The source in force for what the content holds; the store's answer, as [boxArtSourceIn] of the settings. */
internal fun boxArtSourceIn(content: SettingsContent): BoxArtSourceSetting = effectiveBoxArtSource(content.chosenBoxArtSource, isScraperUsable(content.account))

/** An item of Box art's panel, top to bottom: the choices, then the work. */
internal sealed interface BoxArtPanelEntry {
    data class Choice(val source: BoxArtSourceSetting) : BoxArtPanelEntry

    data class Work(val item: AccountPageItem) : BoxArtPanelEntry
}

internal fun boxArtPanelEntries(content: SettingsContent): List<BoxArtPanelEntry> =
    boxArtSourceChoices(content.account).map { BoxArtPanelEntry.Choice(it) } + boxArtPanelWork(content).map { BoxArtPanelEntry.Work(it) }

/**
 * What can be done about box art with no usable account, by the source in force. A usable account and a
 * refused one on ScreenScraper keep all of it in ScreenScraper's panel. Box art only: Download missing,
 * Re-download all and Choose systems… (with more than one system) with games in the library, Clear box art…, Resume while paused (a pause outlives the account, and nothing else
 * lifts it), Box art region (libretro picks by it too). Own covers, and ScreenScraper with nobody signed in:
 * no run can start, so only Clear box art…: saved pictures stay until then.
 */
internal fun boxArtPanelWork(content: SettingsContent): List<AccountPageItem> {
    if (isScraperUsable(content.account)) return emptyList()
    return when (boxArtSourceIn(content)) {
        BoxArtSourceSetting.LibretroOnly ->
            listOfNotNull(
                AccountPageItem.DownloadMissing.takeIf { !isLibraryEmpty(content) },
                AccountPageItem.RedownloadAll.takeIf { !isLibraryEmpty(content) },
                AccountPageItem.ChooseSystems.takeIf { !isLibraryEmpty(content) && content.systems.size > 1 },
                AccountPageItem.ClearBoxArt,
                AccountPageItem.PauseOrResume.takeIf { isPausedByPerson(content) },
                AccountPageItem.BoxArtRegion,
            )
        BoxArtSourceSetting.OwnCovers -> listOf(AccountPageItem.ClearBoxArt)
        BoxArtSourceSetting.ScreenScraper -> listOfNotNull(AccountPageItem.ClearBoxArt.takeIf { content.account !is AccountStatus.SignedIn })
    }
}

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
    val content = model.content
    if (model.focus.page == PanelPage.ScraperSystems) return BoxArtSystemsPage(model, touch, SettingsOption.BoxArtSource)
    if (model.focus.page == PanelPage.ScraperConfirm) return BoxArtConfirmPage(model, touch)
    if (model.focus.page == PanelPage.ScraperClearConfirm) {
        val body = if (boxArtSourceIn(content) == BoxArtSourceSetting.LibretroOnly) R.string.settings_box_art_clear_body_libretro else R.string.settings_box_art_clear_body_own
        return ClearBoxArtConfirmPage(model, touch, body)
    }
    val inPanel = model.focus.column == SettingsColumn.Panel
    val focused = if (inPanel) model.focus.panelIndex else -1
    val entries = boxArtPanelEntries(content)
    val current = boxArtSourceIn(settings, content.account)
    PanelListColumn(Modifier.fillMaxHeight()) {
        PanelTitle(stringResource(R.string.settings_box_art_source))
        PanelBody(stringResource(R.string.settings_box_art_source_body))
        // With the work here, so is how it is going: ScreenScraper's panel says it for everyone else.
        PanelLine(model, SettingsOption.BoxArtSource, boxArtPanelWork(content).takeIf { it.isNotEmpty() }?.let { boxArtLineOf(content.account, content.boxArt) }?.let { boxArtLineText(it) })
        PanelLazyList(focused, Modifier) {
            itemsIndexed(entries, key = { index, _ -> index }, contentType = { _, entry -> entry.javaClass.simpleName }) { index, entry ->
                val modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index))
                when (entry) {
                    is BoxArtPanelEntry.Choice ->
                        PanelItem(
                            title = stringResource(boxArtSourceName(entry.source)),
                            detail = stringResource(boxArtSourceLine(entry.source)),
                            isFocused = index == focused,
                            isQuiet = !inPanel && entry.source == current,
                            padded = true,
                            badge = if (entry.source == current) stringResource(R.string.settings_badge_current) else null,
                            modifier = modifier,
                        )
                    is BoxArtPanelEntry.Work ->
                        PanelItem(
                            title = accountPageItemWords(entry.item, settings, content),
                            isFocused = index == focused,
                            isOutlined = inPanel,
                            modifier = modifier,
                        )
                }
            }
        }
    }
}
