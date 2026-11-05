package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.home.rememberCountFormat

/** The words of one picking card: the top line and the title. */
internal class CardTexts(
    val top: String,
    val title: String,
    val isFavorite: Boolean,
)

/** Words for [content]; a game, the calm screen, the setup question, the statistics card and the entry cards have none here. */
@Composable
internal fun cardTexts(content: SecondScreenContent): CardTexts? {
    val numbers = rememberCountFormat()
    return when (content) {
        is SecondScreenContent.Apps ->
            CardTexts(
                top = pluralStringResource(R.plurals.home_apps_count, content.appCount, numbers.format(content.appCount)),
                title = stringResource(R.string.home_entry_apps),
                isFavorite = false,
            )
        is SecondScreenContent.App ->
            CardTexts(top = stringResource(R.string.second_screen_android_app), title = content.name, isFavorite = false)
        is SecondScreenContent.Game, SecondScreenContent.Calm, SecondScreenContent.SetupQuestion, is SecondScreenContent.Statistics, is EntryContent -> null
    }
}
