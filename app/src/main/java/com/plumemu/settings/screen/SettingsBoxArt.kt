package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.account.clockTime
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.graph.BoxArtClearResult
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.home.rememberCountFormat
import com.plumemu.library.scrape.AskAgain
import com.plumemu.scraper.AccountLimits
import com.plumemu.scraper.PauseReason
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import kotlin.math.ceil
import kotlin.math.roundToLong

/*
 * Settings › Library › ScreenScraper's box art: the one place a run state
 * becomes words (the panel's status line, the account row's short form), the systems picker and
 * the Re-download confirm. The rows and the account panel are in SettingsAccount.kt; the run
 * itself is the graph's `GraphScrapes`.
 */

/** What Settings asks of the box-art run. Each call returns at once; [onDone] comes on the main thread. */
internal interface SettingsBoxArtActions {
    /** Marks [which] games of [systemIds] (null: every system) to be asked again and starts a run; [onDone] gets whether they were marked. */
    fun askAgain(
        which: AskAgain,
        systemIds: Set<String>?,
        onDone: (Boolean) -> Unit,
    )

    /** Pauses or resumes box art; [onDone] gets whether it was stored. */
    fun setPaused(
        isPaused: Boolean,
        onDone: (Boolean) -> Unit,
    )

    /** Clear box art: deletes the saved pictures and their stored results (the account stays); [onDone] gets how it ended. */
    fun clearBoxArt(onDone: (BoxArtClearResult) -> Unit)

    object None : SettingsBoxArtActions {
        override fun clearBoxArt(onDone: (BoxArtClearResult) -> Unit) = onDone(BoxArtClearResult.Failed)

        override fun askAgain(
            which: AskAgain,
            systemIds: Set<String>?,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)

        override fun setPaused(
            isPaused: Boolean,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)
    }
}

/** Box art's state in the person's terms: the panel's line and the account row's short form both come from it. */
internal sealed interface BoxArtLine {
    data object NotInThisBuild : BoxArtLine

    data object WrongPassword : BoxArtLine

    data object PausedByPerson : BoxArtLine

    data class DailyLimit(val resumeAtMillis: Long) : BoxArtLine

    data class ServerClosed(val resumeAtMillis: Long) : BoxArtLine

    data object WaitingForWifi : BoxArtLine

    data object WaitingForBattery : BoxArtLine

    /** No connection (or the server or the disk failed); [nextAtMillis] when WorkManager tries again, if known. */
    data class Retrying(val nextAtMillis: Long?) : BoxArtLine

    data class Starting(val counts: ScrapeCounts?) : BoxArtLine

    /** A run is going: [counts] for "340 of 2,054" (null: unreadable), [left] once the pace is known. */
    data class Running(val counts: ScrapeCounts?, val left: TimeLeft?) : BoxArtLine

    /** Every listed game was asked: how many have a box front. */
    data class Done(val withBoxFront: Int, val listed: Int) : BoxArtLine

    /** No run, some games not asked yet (Download missing asks them). */
    data class Some(val withBoxFront: Int, val listed: Int) : BoxArtLine

    /** The library has no games: nothing to download. */
    data object EmptyLibrary : BoxArtLine

    /** No run, and the library's numbers could not be read (the graph logged why). */
    data object Unreadable : BoxArtLine
}

/** About how long a run still needs, rounded to what a person reads at a glance. */
internal sealed interface TimeLeft {
    data object UnderAMinute : TimeLeft

    data class Minutes(val count: Long) : TimeLeft

    data class Hours(val count: Long) : TimeLeft

    data class Days(val count: Long) : TimeLeft
}

/**
 * [pending] games at [gamesPerMinute]: under a minute; whole minutes below 10; then to 5 minutes;
 * hours from one hour; days from two days. Null without a pace (the run's first games).
 */
internal fun timeLeftOf(
    pending: Int,
    gamesPerMinute: Double?,
): TimeLeft? {
    if (gamesPerMinute == null || gamesPerMinute <= 0.0 || pending <= 0) return null
    val minutes = pending / gamesPerMinute
    return when {
        minutes < 1 -> TimeLeft.UnderAMinute
        minutes < 10 -> TimeLeft.Minutes(ceil(minutes).toLong())
        minutes < 60 -> TimeLeft.Minutes(((minutes / 5).roundToLong() * 5).coerceIn(10, 55))
        minutes < 48 * 60 -> TimeLeft.Hours((minutes / 60).roundToLong().coerceAtLeast(1))
        else -> TimeLeft.Days((minutes / (24 * 60)).roundToLong())
    }
}

/**
 * The line for [account] and the run's [status] ([com.plumemu.graph.scrapeStatusOf]); null when
 * signed out (nothing to say). The account decides first: the two arrive separately, and a
 * refusal or a build without credentials is known from the account alone.
 */
internal fun boxArtLineOf(
    account: AccountStatus,
    status: ScrapeStatus,
): BoxArtLine? {
    when (account) {
        AccountStatus.NotInThisBuild -> return BoxArtLine.NotInThisBuild
        AccountStatus.SignedOut -> return null
        is AccountStatus.SignedIn -> if (account.isRefused) return BoxArtLine.WrongPassword
    }
    val counts = countsOf(status)
    return when (status) {
        // The run's state has not arrived yet (or is a moment behind): the account's own pause, else nothing.
        ScrapeStatus.NotInThisBuild, ScrapeStatus.SignedOut, ScrapeStatus.Refused -> account.dailyLimitUntilMillis?.let { BoxArtLine.DailyLimit(it) }
        is ScrapeStatus.PausedByPerson -> BoxArtLine.PausedByPerson
        is ScrapeStatus.Paused ->
            if (status.reason == PauseReason.DailyQuota) BoxArtLine.DailyLimit(status.untilMillis) else BoxArtLine.ServerClosed(status.untilMillis)
        else ->
            if (counts != null && counts.listed == 0) {
                BoxArtLine.EmptyLibrary
            } else {
                runLine(status, counts)
            }
    }
}

private fun runLine(
    status: ScrapeStatus,
    counts: ScrapeCounts?,
): BoxArtLine? =
    when (status) {
        is ScrapeStatus.Running -> BoxArtLine.Running(counts, counts?.let { timeLeftOf(it.pending, status.gamesPerMinute) })
        is ScrapeStatus.Starting -> BoxArtLine.Starting(counts)
        is ScrapeStatus.WaitingForWifi -> BoxArtLine.WaitingForWifi
        is ScrapeStatus.WaitingForBattery -> BoxArtLine.WaitingForBattery
        is ScrapeStatus.Retrying -> BoxArtLine.Retrying(status.nextAtMillis)
        is ScrapeStatus.Idle ->
            when {
                counts == null -> BoxArtLine.Unreadable
                counts.pending == 0 -> BoxArtLine.Done(counts.withBoxFront, counts.listed)
                else -> BoxArtLine.Some(counts.withBoxFront, counts.listed)
            }
        else -> null
    }

/** The library's numbers a state carries; null for the account's states and when they could not be read. */
internal fun countsOf(status: ScrapeStatus): ScrapeCounts? =
    when (status) {
        ScrapeStatus.NotInThisBuild, ScrapeStatus.SignedOut, ScrapeStatus.Refused -> null
        is ScrapeStatus.PausedByPerson -> status.counts
        is ScrapeStatus.Idle -> status.counts
        is ScrapeStatus.Running -> status.counts
        is ScrapeStatus.Starting -> status.counts
        is ScrapeStatus.WaitingForWifi -> status.counts
        is ScrapeStatus.WaitingForBattery -> status.counts
        is ScrapeStatus.Paused -> status.counts
        is ScrapeStatus.Retrying -> status.counts
    }

/** The panel's full line: "Downloading box art 340 of 2,054 · about 5 h left", "Paused", "Done: 2,012 of 2,054 games have box art". */
@Composable
internal fun boxArtLineText(line: BoxArtLine): String {
    val format = rememberCountFormat()
    return when (line) {
        BoxArtLine.NotInThisBuild -> stringResource(R.string.settings_account_not_available)
        BoxArtLine.WrongPassword -> stringResource(R.string.settings_account_refused)
        BoxArtLine.PausedByPerson -> stringResource(R.string.settings_box_art_paused)
        is BoxArtLine.DailyLimit -> stringResource(R.string.settings_account_daily_limit, clockTime(line.resumeAtMillis))
        is BoxArtLine.ServerClosed -> stringResource(R.string.settings_box_art_server_closed, clockTime(line.resumeAtMillis))
        BoxArtLine.WaitingForWifi -> stringResource(R.string.settings_box_art_waiting_wifi)
        BoxArtLine.WaitingForBattery -> stringResource(R.string.settings_box_art_waiting_battery)
        is BoxArtLine.Retrying ->
            line.nextAtMillis?.let { stringResource(R.string.settings_box_art_retrying_at, clockTime(it)) }
                ?: stringResource(R.string.settings_box_art_retrying)
        is BoxArtLine.Starting -> stringResource(R.string.settings_box_art_starting)
        is BoxArtLine.Running -> {
            val counts = line.counts ?: return stringResource(R.string.settings_box_art_running_no_counts)
            val asked = format.format(counts.asked)
            val listed = format.format(counts.listed)
            val left = line.left?.let { timeLeftText(it) }
            if (left == null) stringResource(R.string.settings_box_art_running, asked, listed) else stringResource(R.string.settings_box_art_running_left, asked, listed, left)
        }
        is BoxArtLine.Done -> stringResource(R.string.settings_box_art_done, format.format(line.withBoxFront), format.format(line.listed))
        is BoxArtLine.Some -> stringResource(R.string.settings_box_art_some, format.format(line.withBoxFront), format.format(line.listed))
        BoxArtLine.EmptyLibrary -> stringResource(R.string.settings_box_art_empty)
        BoxArtLine.Unreadable -> stringResource(R.string.settings_box_art_unreadable)
    }
}

@Composable
private fun timeLeftText(left: TimeLeft): String {
    val format = rememberCountFormat()
    return when (left) {
        TimeLeft.UnderAMinute -> stringResource(R.string.settings_box_art_left_under_a_minute)
        is TimeLeft.Minutes -> stringResource(R.string.settings_box_art_left_minutes, format.format(left.count))
        is TimeLeft.Hours -> stringResource(R.string.settings_box_art_left_hours, format.format(left.count))
        is TimeLeft.Days -> pluralStringResource(R.plurals.settings_box_art_left_days, left.count.toInt(), format.format(left.count))
    }
}

/**
 * The account row's value: "340 of 2,054", "Paused", "Waiting for Wi-Fi", "Daily limit", "Sign in
 * again", "Done"; null when there is nothing short to say (the row shows the name then).
 */
@Composable
internal fun boxArtShortText(line: BoxArtLine?): String? {
    val format = rememberCountFormat()
    return when (line) {
        null, BoxArtLine.NotInThisBuild, is BoxArtLine.Some, BoxArtLine.EmptyLibrary, BoxArtLine.Unreadable -> null
        BoxArtLine.WrongPassword -> stringResource(R.string.settings_account_sign_in_again)
        BoxArtLine.PausedByPerson -> stringResource(R.string.settings_box_art_paused)
        is BoxArtLine.DailyLimit -> stringResource(R.string.settings_box_art_short_daily_limit)
        is BoxArtLine.ServerClosed -> stringResource(R.string.settings_box_art_short_server_closed)
        BoxArtLine.WaitingForWifi -> stringResource(R.string.settings_box_art_waiting_wifi)
        BoxArtLine.WaitingForBattery -> stringResource(R.string.settings_box_art_short_battery)
        is BoxArtLine.Retrying -> stringResource(R.string.settings_box_art_short_offline)
        is BoxArtLine.Starting -> stringResource(R.string.settings_box_art_starting)
        is BoxArtLine.Running ->
            line.counts?.let { stringResource(R.string.settings_box_art_short_progress, format.format(it.asked), format.format(it.listed)) }
                ?: stringResource(R.string.settings_box_art_short_running)
        is BoxArtLine.Done -> stringResource(R.string.settings_box_art_short_done)
    }
}

/**
 * One line of the facts under the status line: what the account may do, so a slow
 * run can be explained. Numbers only; each is hidden when ScreenScraper did not say it.
 */
internal sealed interface AccountFact {
    /** "Requests today: 1,234 of 10,000"; [limit] null when the daily budget is not known; [usedUp] when [today] reached it. */
    data class Requests(val today: Int, val limit: Int?, val usedUp: Boolean) : AccountFact

    data class ParallelDownloads(val count: Int) : AccountFact

    data class DownloadSpeed(val kilobytesPerSecond: Int) : AccountFact
}

/**
 * The facts [limits] hold, in the order the panel shows them. Empty when unknown. A zero or
 * negative speed or thread count is not a limit a person can read, so it is left out.
 * `maxrequestspermin` is not shown: the gate's spacing already keeps to it and it is not what
 * slows a run down (the person can ask for it).
 */
internal fun accountFactsOf(limits: AccountLimits?): List<AccountFact> {
    if (limits == null) return emptyList()
    return listOfNotNull(
        limits.requestsToday?.takeIf { it >= 0 }?.let { today ->
            val limit = limits.maxRequestsPerDay?.takeIf { it > 0 }
            AccountFact.Requests(today, limit, usedUp = limit != null && today >= limit)
        },
        limits.maxThreads?.takeIf { it > 0 }?.let { AccountFact.ParallelDownloads(it) },
        limits.maxDownloadKilobytesPerSecond?.takeIf { it > 0 }?.let { AccountFact.DownloadSpeed(it) },
    )
}

/**
 * The facts block under the status line: muted fact lines, not focusable; nothing at all while
 * unknown. [boxArtBytes] adds "Box art: 312 MB", the space the saved pictures use (null while it is
 * being measured: no line, so no "0 MB" is shown for a moment).
 */
@Composable
internal fun AccountFacts(
    limits: AccountLimits?,
    boxArtBytes: Long? = null,
    givenUp: Int = 0,
) {
    val facts = accountFactsOf(limits)
    if (facts.isEmpty() && boxArtBytes == null && givenUp == 0) return
    val format = rememberCountFormat()
    Column(Modifier.testTag(SettingsPanelTags.FACTS), verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
        if (boxArtBytes != null) {
            PanelFact(
                stringResource(R.string.settings_box_art_size, Formatter.formatShortFileSize(LocalContext.current, boxArtBytes)),
                Modifier.testTag(SettingsPanelTags.BOX_ART_SIZE),
            )
        }
        if (givenUp > 0) {
            PanelFact(
                pluralStringResource(R.plurals.settings_box_art_given_up, givenUp, format.format(givenUp)),
                Modifier.testTag(SettingsPanelTags.BOX_ART_GIVEN_UP),
            )
        }
        facts.forEach { fact ->
            PanelFact(
                when (fact) {
                    is AccountFact.Requests ->
                        when {
                            fact.limit == null -> stringResource(R.string.settings_box_art_fact_requests_only, format.format(fact.today))
                            fact.usedUp -> stringResource(R.string.settings_box_art_fact_requests_used_up, format.format(fact.today), format.format(fact.limit))
                            else -> stringResource(R.string.settings_box_art_fact_requests, format.format(fact.today), format.format(fact.limit))
                        }
                    is AccountFact.ParallelDownloads -> stringResource(R.string.settings_box_art_fact_parallel, format.format(fact.count))
                    is AccountFact.DownloadSpeed -> stringResource(R.string.settings_box_art_fact_speed, format.format(fact.kilobytesPerSecond))
                },
            )
        }
    }
}

/** The person paused box art: the panel's Pause reads Resume. */
internal fun isPausedByPerson(content: SettingsContent): Boolean = content.boxArt is ScrapeStatus.PausedByPerson

/** The library has no games (known only once the counts were read): the panel offers no download. */
internal fun isLibraryEmpty(content: SettingsContent): Boolean = countsOf(content.boxArt)?.listed == 0

/** The systems picker's actions, after its systems; listed once a system is chosen. */
internal enum class SystemsPickAction { DownloadMissing, Redownload }

/** The picker's focusable items: each system with games, then its actions once one is chosen. */
internal fun systemsPickCount(
    content: SettingsContent,
    focus: SettingsFocus,
): Int = content.systems.size + if (chosenSystemsIn(content, focus).isEmpty()) 0 else SystemsPickAction.entries.size

/** The chosen systems that still have games (a scan may remove one while the picker is open). */
internal fun chosenSystemsIn(
    content: SettingsContent,
    focus: SettingsFocus,
): Set<String> = focus.chosenSystems.filterTo(LinkedHashSet()) { id -> content.systems.any { it.systemId == id } }

/** The picker's action at [index], or null for a system row. */
internal fun systemsPickActionAt(
    content: SettingsContent,
    index: Int,
): SystemsPickAction? = if (index < content.systems.size) null else SystemsPickAction.entries.getOrNull(index - content.systems.size)

/** The confirm's buttons, top to bottom (Re-download over Cancel, so neither is cut in the narrow panel); Cancel has the focus first. */
internal enum class BoxArtConfirmItem { Redownload, Cancel }

/** Clear box art's confirm buttons, top to bottom; Cancel has the focus first. */
internal enum class ClearConfirmItem { Clear, Cancel }

/** Sign out's confirm buttons, top to bottom; Cancel has the focus first. */
internal enum class SignOutConfirmItem { SignOut, Cancel }

/**
 * Library › ScreenScraper › Sign out, asked first: it says what happens to box art (the covers and the grid stop
 * showing and downloads stop; the pictures already saved stay on the device for the next sign-in), then Sign out and Cancel.
 */
@Composable
internal fun SignOutConfirmPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val focused = if (focus.column == SettingsColumn.Panel) focus.panelIndex else -1
    AccountPanelScroll(PlumDimens.settingsPanelGap) {
        PanelTitle(stringResource(R.string.settings_sign_out_confirm_title))
        BasicText(
            stringResource(R.string.settings_sign_out_confirm_body),
            style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
            modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.STATUS),
        )
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
            SignOutConfirmItem.entries.forEach { item ->
                val isFocused = focused == item.ordinal
                SetupButton(
                    text = stringResource(if (item == SignOutConfirmItem.SignOut) R.string.settings_account_sign_out else R.string.settings_cancel),
                    isPrimary = item == SignOutConfirmItem.SignOut,
                    isFocused = isFocused,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().bringIntoViewWhen(isFocused).tapTarget { touch.onPanelItem(item.ordinal) }.testTag(SettingsTags.panelItem(item.ordinal)),
                )
            }
        }
    }
}

/**
 * Library › ScreenScraper › Clear box art…, asked first: the covers disappear until Download missing
 * fetches them again, the person's own picks and the sign-in stay; then Clear box art and Cancel.
 */
@Composable
internal fun ClearBoxArtConfirmPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val focused = if (focus.column == SettingsColumn.Panel) focus.panelIndex else -1
    AccountPanelScroll(PlumDimens.settingsPanelGap) {
        PanelTitle(stringResource(R.string.settings_box_art_clear_title))
        BasicText(
            stringResource(R.string.settings_box_art_clear_body),
            style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
            modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.STATUS),
        )
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
            ClearConfirmItem.entries.forEach { item ->
                val isFocused = focused == item.ordinal
                SetupButton(
                    text = stringResource(if (item == ClearConfirmItem.Clear) R.string.settings_box_art_clear_confirm else R.string.settings_cancel),
                    isPrimary = item == ClearConfirmItem.Clear,
                    isFocused = isFocused,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().bringIntoViewWhen(isFocused).tapTarget { touch.onPanelItem(item.ordinal) }.testTag(SettingsTags.panelItem(item.ordinal)),
                )
            }
        }
    }
}

/**
 * Library › ScreenScraper › Choose systems…: every system with games, a checkmark on each chosen
 * one (A or a tap), then Download missing and Re-download for the chosen ones.
 */
@Composable
internal fun BoxArtSystemsPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val focus = model.focus
    val chosen = chosenSystemsIn(content, focus)
    val focused = if (focus.column == SettingsColumn.Panel) focus.panelIndex else -1
    // One scrolling column like the account page (a library has a few dozen systems at most, so
    // composing them all is cheap): the focused row or button is kept in view at every text size.
    AccountPanelScroll(PlumDimens.settingsPanelListGap) {
        PanelTitle(stringResource(R.string.settings_box_art_systems_title))
        PanelBody(
            if (chosen.isEmpty()) {
                stringResource(R.string.settings_box_art_systems_body)
            } else {
                pluralStringResource(R.plurals.settings_box_art_systems_chosen, chosen.size, rememberCountFormat().format(chosen.size))
            },
        )
        PanelLine(model, SettingsOption.ScraperAccount, null)
        Column(Modifier.testTag(SettingsTags.BOX_ART_SYSTEMS), verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
            content.systems.forEachIndexed { index, system ->
                PanelItem(
                    title = system.name,
                    isFocused = index == focused,
                    isChecked = system.systemId in chosen,
                    modifier = Modifier.bringIntoViewWhen(index == focused).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
        if (chosen.isNotEmpty()) {
            Column(Modifier.padding(top = PlumDimens.focusRingOutset), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
                SystemsPickAction.entries.forEach { action ->
                    val index = content.systems.size + action.ordinal
                    SetupButton(
                        text = stringResource(if (action == SystemsPickAction.DownloadMissing) R.string.settings_box_art_download_missing else R.string.settings_box_art_redownload),
                        isPrimary = action == SystemsPickAction.DownloadMissing,
                        isFocused = index == focused,
                        modifier = Modifier.fillMaxWidth().bringIntoViewWhen(index == focused).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** "Re-download box art for all 2,054 games? Your own choices are kept.", then Re-download and Cancel. */
@Composable
internal fun BoxArtConfirmPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val focus = model.focus
    val systems = focus.redownloadSystems
    val format = rememberCountFormat()
    val question =
        when {
            systems == null ->
                countsOf(content.boxArt)?.listed?.let { pluralStringResource(R.plurals.settings_box_art_confirm_all, it, format.format(it)) }
                    ?: stringResource(R.string.settings_box_art_confirm_all_unknown)
            systems.size == 1 -> stringResource(R.string.settings_box_art_confirm_system, content.system(systems.single())?.name.orEmpty())
            else -> pluralStringResource(R.plurals.settings_box_art_confirm_systems, systems.size, format.format(systems.size))
        }
    val focused = if (focus.column == SettingsColumn.Panel) focus.panelIndex else -1
    AccountPanelScroll(PlumDimens.settingsPanelGap) {
        PanelTitle(stringResource(R.string.settings_box_art_confirm_title))
        BasicText(question, style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text), modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.STATUS))
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
            BoxArtConfirmItem.entries.forEach { item ->
                val isFocused = focused == item.ordinal
                SetupButton(
                    text = stringResource(if (item == BoxArtConfirmItem.Redownload) R.string.settings_box_art_redownload else R.string.settings_cancel),
                    isPrimary = item == BoxArtConfirmItem.Redownload,
                    isFocused = isFocused,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().bringIntoViewWhen(isFocused).tapTarget { touch.onPanelItem(item.ordinal) }.testTag(SettingsTags.panelItem(item.ordinal)),
                )
            }
        }
    }
}
