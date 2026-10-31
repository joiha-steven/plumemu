package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInBody
import com.plumemu.account.SignInForm
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.keyboard.aboveTray
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import kotlin.math.roundToInt

/*
 * Settings › Library's ScreenScraper rows: the account row and its
 * panel (the sign-in form shared with setup, or the signed-in page: the status line and the box
 * art actions), and the box art region (a plain ‹ › choice in SettingsOptions.kt). The status
 * words, the systems picker and the confirm are in SettingsBoxArt.kt.
 */

/**
 * Library's ScreenScraper rows: without developer credentials only the status line ("Not
 * available in this build"); otherwise the account row (its value says how box art is going)
 * and the box art region.
 */
internal fun accountRowsIn(content: SettingsContent): List<SettingsOption> =
    when (content.account) {
        AccountStatus.NotInThisBuild -> listOf(SettingsOption.ScraperStatus)
        AccountStatus.SignedOut, is AccountStatus.SignedIn -> listOf(SettingsOption.ScraperAccount, SettingsOption.BoxArtRegion)
    }

/** ScreenScraper refused the stored password: Settings offers to sign in again. */
internal fun isRefused(account: AccountStatus): Boolean = (account as? AccountStatus.SignedIn)?.isRefused == true

/** What the signed-in page offers, top to bottom. */
internal enum class AccountPageItem { SignInAgain, DownloadMissing, RedownloadAll, ChooseSystems, ClearBoxArt, PauseOrResume, SignOut }

/**
 * After a refusal: Sign in again and Sign out. An empty library: Clear box art (pictures may be
 * left of games that are gone), Resume while paused, and Sign out. Otherwise Download missing
 * (focused first), Re-download all, Choose systems… (with games in more than one system), Clear
 * box art…, Pause or Resume, Sign out.
 */
internal fun accountPageItems(content: SettingsContent): List<AccountPageItem> =
    when {
        isRefused(content.account) -> listOf(AccountPageItem.SignInAgain, AccountPageItem.SignOut)
        isLibraryEmpty(content) -> listOfNotNull(AccountPageItem.ClearBoxArt, AccountPageItem.PauseOrResume.takeIf { isPausedByPerson(content) }, AccountPageItem.SignOut)
        else ->
            listOfNotNull(
                AccountPageItem.DownloadMissing,
                AccountPageItem.RedownloadAll,
                AccountPageItem.ChooseSystems.takeIf { content.systems.size > 1 },
                AccountPageItem.ClearBoxArt,
                AccountPageItem.PauseOrResume,
                AccountPageItem.SignOut,
            )
    }

/** The sign-in form open in the panel, if any; the model routes every action to it while [PanelPage.ScraperSignIn] has the focus. */
@Stable
internal class SettingsSignIn {
    var form: SignInForm? by mutableStateOf(null)
        private set

    /** Shows [opened] (a fresh form; for Sign in again with the name pre-filled). */
    fun open(opened: SignInForm): SignInForm = opened.also { form = it }

    companion object {
        /**
         * The panel is narrow (248 on wide screens): Sign in over Cancel, so neither is cut at the larger sizes.
         * [keyboard]: Settings › Controls › Keyboard when the form opens (Plumemu's tray, or Android's).
         */
        fun newForm(
            username: String = "",
            keyboard: KeyboardChoice,
        ): SignInForm = SignInForm(initialUsername = username, stackedButtons = true, keyboardKind = keyboard)
    }

    fun close() {
        form = null
    }
}

/**
 * The account row's panel: the form while it is open, the systems picker or the Re-download
 * confirm while one is open, else who is signed in, how box art is going and what can be done.
 */
@Composable
internal fun ScraperAccountPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val form = model.signIn.form
    val page = model.focus.page
    if (form != null && page == PanelPage.ScraperSignIn) {
        SignInPage(form, touch)
        return
    }
    if (page == PanelPage.ScraperSystems) return BoxArtSystemsPage(model, touch)
    if (page == PanelPage.ScraperConfirm) return BoxArtConfirmPage(model, touch)
    if (page == PanelPage.ScraperSignOutConfirm) return SignOutConfirmPage(model, touch)
    if (page == PanelPage.ScraperClearConfirm) return ClearBoxArtConfirmPage(model, touch)
    val content = model.content
    val account = content.account
    val inPanel = model.focus.column == SettingsColumn.Panel && page == PanelPage.ScraperAccount
    // Where the buttons start: it moves when the facts appear or the status line gains or loses a line, and
    // only then is the focused one brought into view again (a changing count in the same lines moves nothing,
    // so a panel the person scrolled by touch is left alone).
    var buttonsTop by remember { mutableIntStateOf(0) }
    AccountPanelScroll(PlumDimens.settingsPanelGap) {
        PanelTitle(stringResource(R.string.settings_account_title))
        if (account is AccountStatus.SignedIn) {
            BasicText(
                stringResource(R.string.account_signed_in_as, account.username),
                style = PlumTheme.type.meta.copy(color = PlumTheme.colors.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.onRowTextLine(),
            )
            PanelLine(model, SettingsOption.ScraperAccount, boxArtLineOf(account, content.boxArt)?.let { boxArtLineText(it) })
            if (!account.isRefused) AccountFacts(content.accountLimits, content.boxArtBytes, content.boxArtGivenUp)
            // As wide as the widest of them, all of them.
            Column(
                Modifier.width(IntrinsicSize.Max).onGloballyPositioned { buttonsTop = it.positionInParent().y.roundToInt() },
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
            ) {
                accountPageItems(content).forEachIndexed { index, item ->
                    val focused = inPanel && model.focus.panelIndex == index
                    SetupButton(
                        text = stringResource(accountPageItemText(item, isPausedByPerson(content))),
                        isPrimary = item == AccountPageItem.SignInAgain || item == AccountPageItem.DownloadMissing,
                        isFocused = focused,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().bringIntoViewWhen(focused, buttonsTop, PlumDimens.focusRingOutset).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                    )
                }
            }
        } else {
            PanelBody(stringResource(R.string.settings_account_body))
        }
    }
}

private fun accountPageItemText(
    item: AccountPageItem,
    isPaused: Boolean,
): Int =
    when (item) {
        AccountPageItem.SignInAgain -> R.string.settings_account_sign_in_again
        AccountPageItem.DownloadMissing -> R.string.settings_box_art_download_missing
        AccountPageItem.RedownloadAll -> R.string.settings_box_art_redownload_all
        AccountPageItem.ChooseSystems -> R.string.settings_box_art_choose_systems
        AccountPageItem.ClearBoxArt -> R.string.settings_box_art_clear
        AccountPageItem.PauseOrResume -> if (isPaused) R.string.settings_box_art_resume else R.string.settings_box_art_pause
        AccountPageItem.SignOut -> R.string.settings_account_sign_out
    }

/**
 * The form under its title; scrolls when the text is large, keeping the focused item in view. While
 * Plumemu's tray is up the scroll ends at the tray's top (the title scrolls away first) and the
 * focused field shows with the line under it.
 */
@Composable
private fun SignInPage(
    form: SignInForm,
    touch: SettingsTouch,
) {
    val covers = settingsKeyboardCovers()
    // Setup's gap, the same with the keyboard tray up or down so the form never jumps when it opens: over the
    // tray at Largest in Mono on 375 high the password, the gap and Sign in need 1 dp more than the panel's
    // 10 left once Sign in's label went from 15 to 16.
    val gap = PlumDimens.setupContentGap
    AccountPanelScroll(gap, Modifier.aboveTray({ form.isTrayUp }, covers)) {
        PanelTitle(stringResource(R.string.settings_account_title))
        SignInBody(form, R.string.settings_cancel, onTap = touch.onPanelItem, onAction = touch.onHint, gap = gap)
    }
}

/**
 * The account panel's column: scrolls when the text is large, with room for focus rings at its
 * sides. Not past its top and bottom: the strip clips there, and a focused item scrolled to the
 * edge would be cut.
 */
@Composable
internal fun AccountPanelScroll(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .bleedSideways(PlumDimens.focusRingOutset)
            .verticalScroll(rememberScrollState())
            // No room above the title: it starts where every other panel's does (the first item is never focused).
            .padding(start = PlumDimens.focusRingOutset, end = PlumDimens.focusRingOutset, bottom = PlumDimens.focusRingOutset)
            .testTag(SettingsTags.ACCOUNT_PANEL),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Like setup's `bleed`, on the left and right only. */
private fun Modifier.bleedSideways(by: Dp): Modifier =
    layout { measurable, constraints ->
        val px = by.roundToPx()
        val placeable = measurable.measure(constraints.offset(px * 2, 0))
        layout(placeable.width - px * 2, placeable.height) { placeable.place(-px, 0) }
    }

/** The status row's panel (a build without credentials only): the line again and what it means (narrow screens open it as a stop). */
@Composable
internal fun ScraperStatusPanel(model: SettingsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_account_title))
        boxArtLineOf(model.content.account, model.content.boxArt)?.let { PanelFact(boxArtLineText(it), Modifier.testTag(SettingsPanelTags.STATUS)) }
        PanelBody(stringResource(R.string.settings_account_not_available_body))
    }
}
