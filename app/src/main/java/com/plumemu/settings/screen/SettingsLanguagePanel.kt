package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.language.AppLanguages
import com.plumemu.language.ownName

/*
 * Advanced › Language's side panel. Item 0 is System default (in the screen's language), then every
 * shipped language in its own name and script, from that language's own `language_name`: never translated, so a person
 * who landed in a language they cannot read still finds theirs. Up and Down only move; A applies; B closes unchanged.
 */

/** The panel item showing [tag]: 0 for System default (null or a tag no longer shipped), else 1 + the language's place in [AppLanguages.all]. */
internal fun languagePanelIndex(tag: String?): Int = AppLanguages.all.indexOf(AppLanguages.byTag(tag)) + 1

/** The tag item [index] stands for; null for System default. */
internal fun languagePanelTag(index: Int): String? = AppLanguages.all.getOrNull(index - 1)?.tag

@Composable
internal fun LanguagePanel(
    model: SettingsModel,
    touch: SettingsTouch,
    settings: PlumSettings,
) {
    val inPanel = model.focus.column == SettingsColumn.Panel
    val focused = if (inPanel) model.focus.panelIndex else -1
    val current = languagePanelIndex(settings.appearance.language)
    val context = LocalContext.current
    val system = stringResource(R.string.settings_language_system)
    val names = remember(context, system) { listOf(system) + AppLanguages.all.map { ownName(context, it) } }
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_language))
        PanelBody(stringResource(R.string.settings_language_body))
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(names, key = { index, _ -> index }, contentType = { _, _ -> "language" }) { index, name ->
                PanelItem(
                    title = name,
                    isFocused = index == focused,
                    isQuiet = !inPanel && index == current,
                    badge = if (index == current) stringResource(R.string.settings_badge_current) else null,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}
