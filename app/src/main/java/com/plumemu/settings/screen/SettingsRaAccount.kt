package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.account.RaAccountStatus
import com.plumemu.account.SignInKind
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.rememberCountFormat
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import kotlin.math.roundToInt

/*
 * Settings › Accounts › RetroAchievements: the row's panel. Signed out it is the sign-in form shared
 * with ScreenScraper (a user name and the Web API key, masked), with the lines on where the key is and
 * what is sent under the buttons; signed in it says who and how many points, then Sign out. The form's
 * keyboard and touch rules are SignInForm's; the graph's side is GraphAchievements.signIn.
 */

/** What the signed-in page offers, top to bottom. */
internal enum class RaPageItem { SignInAgain, SignOut }

/** After a refusal (the key was reset on the site): Sign in again and Sign out; otherwise Sign out. */
internal fun raPageItems(content: SettingsContent): List<RaPageItem> =
    if ((content.raAccount as? RaAccountStatus.SignedIn)?.isKeyRefused == true) listOf(RaPageItem.SignInAgain, RaPageItem.SignOut) else listOf(RaPageItem.SignOut)

internal fun raIsSignedIn(content: SettingsContent): Boolean = content.raAccount is RaAccountStatus.SignedIn

/** The focused item after the list changed from [before] to [after]: the same item if it is still there, else the nearest row. */
internal fun raIndexAfter(
    before: SettingsContent,
    after: SettingsContent,
    at: Int,
): Int {
    val now = raPageItems(after)
    return raPageItems(before).getOrNull(at)?.let { now.indexOf(it) }?.takeIf { it >= 0 } ?: at.coerceAtMost(now.lastIndex)
}

/**
 * A or a tap on an item of the signed-in page. Sign in again opens the form with the name filled in; Sign out forgets the name and the key
 * at once (the key stays on the site), the panel closing through [closeIfHere] once the account is gone and staying open, with a line, if it failed.
 */
internal fun enterRaItem(
    item: RaPageItem?,
    content: SettingsContent,
    work: SettingsWork,
    activated: () -> Unit,
    signInAgain: (String) -> Unit,
    closeIfHere: () -> Unit,
) {
    when (item) {
        RaPageItem.SignInAgain -> signInAgain((content.raAccount as? RaAccountStatus.SignedIn)?.userName.orEmpty())
        RaPageItem.SignOut -> {
            work.signOutRa(closeIfHere)
            activated()
        }
        null -> closeIfHere()
    }
}

/** The panel of the RetroAchievements row: the form while it is open, else who is signed in and what can be done. */
@Composable
internal fun RaAccountPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val form = model.signIn.form
    val page = model.focus.page
    val title = stringResource(R.string.settings_ra_title)
    if (form != null && form.kind == SignInKind.RetroAchievements && page == PanelPage.ScraperSignIn) {
        SignInPage(title, form, touch) { RaNotes() }
        return
    }
    val account = model.content.raAccount
    val inPanel = model.focus.column == SettingsColumn.Panel && page == PanelPage.RaAccount
    var buttonsTop by remember { mutableIntStateOf(0) }
    AccountPanelScroll(PlumDimens.settingsPanelGap) {
        PanelTitle(title)
        if (account is RaAccountStatus.SignedIn) {
            if (account.isKeyRefused) {
                PanelBody(stringResource(R.string.settings_ra_refused), Modifier.testTag(SettingsPanelTags.STATUS))
            } else {
                BasicText(
                    pluralStringResource(R.plurals.settings_ra_signed_in, account.points, account.userName, rememberCountFormat().format(account.points)),
                    style = PlumTheme.type.meta.copy(color = PlumTheme.colors.text),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.STATUS),
                )
            }
            PanelLine(model, SettingsOption.RetroAchievementsAccount, null)
            PanelBody(stringResource(R.string.settings_ra_privacy))
            Column(
                Modifier.width(IntrinsicSize.Max).onGloballyPositioned { buttonsTop = it.positionInParent().y.roundToInt() },
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
            ) {
                raPageItems(model.content).forEachIndexed { index, item ->
                    val focused = inPanel && model.focus.panelIndex == index
                    SetupButton(
                        text = stringResource(if (item == RaPageItem.SignInAgain) R.string.settings_account_sign_in_again else R.string.settings_account_sign_out),
                        isPrimary = item == RaPageItem.SignInAgain,
                        isFocused = focused,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().bringIntoViewWhen(focused, buttonsTop, PlumDimens.focusRingOutset).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                    )
                }
            }
        } else {
            RaNotes()
        }
    }
}

/** Under the form: where the Web API key is, that it is kept encrypted, and what goes to retroachievements.org. */
@Composable
private fun RaNotes() {
    PanelBody(stringResource(R.string.settings_ra_help_where), Modifier.testTag(SettingsPanelTags.RA_HELP))
    PanelBody(stringResource(R.string.settings_ra_help_key), Modifier.testTag(SettingsPanelTags.RA_HELP))
    PanelFact(stringResource(R.string.settings_ra_privacy), Modifier.testTag(SettingsPanelTags.RA_PRIVACY))
}
