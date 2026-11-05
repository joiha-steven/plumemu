package com.plumemu.settings.screen

/** The games, then the Exact play time switch as the page's last stop (also on a page with no games). */
internal fun statisticsStopsIn(content: SettingsContent): List<SettingsOption> =
    if (content.statistics == StatisticsState.NotRead) {
        emptyList()
    } else {
        List(statisticStopsOf(content.statistics)) { SettingsOption.Statistic } + SettingsOption.ExactPlayTime
    }

/** One of the Statistics page's cursor stops: a most-played game or the Exact play time switch; Up and Down at its ends scroll the page. */
internal val SettingsOption.isOnStatisticsPage: Boolean
    get() = this == SettingsOption.Statistic || this == SettingsOption.ExactPlayTime
