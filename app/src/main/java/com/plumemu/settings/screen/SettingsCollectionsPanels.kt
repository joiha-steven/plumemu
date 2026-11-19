package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.TypingPanelBody
import com.plumemu.game.TypingPanelLayout
import com.plumemu.home.rememberCountFormat
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen

/** Library › Collections' row value: how many collections the person made (the row is shown only while there is one). */
@Composable
internal fun collectionsValue(count: Int): String = pluralStringResource(R.plurals.collections_count, count, rememberCountFormat().format(count))

/** Library › Collections: the panel of the row, its page, Rename's keyboard or the Delete confirm, by the page the focus is on. */
@Composable
internal fun CollectionsPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val inPanel = model.focus.column == SettingsColumn.Panel
    val rename = model.collectionRename.open
    when {
        inPanel && model.focus.page == PanelPage.CollectionRename && rename != null -> CollectionRenamePage(rename, touch.onHint)
        inPanel && model.focus.page == PanelPage.CollectionDelete -> CollectionDeletePage(model, touch)
        inPanel && model.focus.page == PanelPage.Collection -> CollectionPage(model, touch)
        else -> CollectionsListPanel(model, touch)
    }
}

/** The own collections in order, each with its games. */
@Composable
private fun CollectionsListPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val inPanel = focus.column == SettingsColumn.Panel
    val focused = if (inPanel) focus.panelIndex else -1
    val collections = model.content.collections
    val count = rememberCountFormat()
    PanelListColumn(Modifier.fillMaxHeight().testTag(SettingsPanelTags.COLLECTIONS_PANEL)) {
        PanelTitle(stringResource(R.string.collections_entry))
        PanelBody(stringResource(R.string.collections_settings_body))
        PanelLine(model, SettingsOption.Collections, null)
        PanelLazyList(focused, Modifier) {
            itemsIndexed(collections, key = { _, collection -> collection.id }, contentType = { _, _ -> "collection" }) { index, collection ->
                PanelItem(
                    title = collection.name,
                    detail = pluralStringResource(R.plurals.home_games_count, collection.gameCount, count.format(collection.gameCount)),
                    isFocused = index == focused,
                    isQuiet = !inPanel,
                    isOutlined = inPanel,
                    padded = true,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

/** A collection's page: its name and games, then Rename, Move up, Move down and Delete…. */
@Composable
private fun CollectionPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val collection = content.collections.firstOrNull { it.id == model.focus.collectionId } ?: return
    val items = collectionPageItems(content, collection.id)
    val focused = model.focus.panelIndex.coerceAtMost(items.lastIndex)
    PanelListColumn(Modifier.fillMaxHeight().testTag(SettingsPanelTags.COLLECTION_PAGE)) {
        PanelTitle(collection.name)
        PanelBody(pluralStringResource(R.plurals.home_games_count, collection.gameCount, rememberCountFormat().format(collection.gameCount)))
        PanelLine(model, SettingsOption.Collections, null)
        PanelLazyList(focused, Modifier) {
            itemsIndexed(items, key = { _, item -> item.name }, contentType = { _, _ -> "collection_item" }) { index, item ->
                PanelItem(
                    title =
                        stringResource(
                            when (item) {
                                CollectionPageItem.Rename -> R.string.collections_rename
                                CollectionPageItem.MoveUp -> R.string.collections_move_up
                                CollectionPageItem.MoveDown -> R.string.collections_move_down
                                CollectionPageItem.Delete -> R.string.collections_delete
                            },
                        ),
                    titleStyle = PlumTheme.type.settingsPanelAction.copy(color = PlumTheme.colors.accent),
                    isFocused = index == focused,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

/** Delete's confirm: which collection, that the games stay, then Cancel (focused first) and Delete. */
@Composable
private fun CollectionDeletePage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val collection = model.content.collections.firstOrNull { it.id == model.focus.collectionId }
    // Scrolls when the text is large, keeping the focused button in view.
    PanelScrollColumn(PlumDimens.settingsPanelGap) {
        PanelTitle(stringResource(R.string.collections_delete_title, collection?.name.orEmpty()))
        PanelBody(stringResource(R.string.collections_delete_body))
        ButtonsRow(Modifier.ringRoomAboveAndBelow().testTag(SettingsPanelTags.COLLECTION_DELETE)) {
            val focused = model.focus.panelIndex
            SetupButton(
                text = stringResource(R.string.settings_cancel),
                isPrimary = false,
                isFocused = focused == SettingsModel.CONFIRM_CANCEL,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_CANCEL, ring = FlatPartDimens.focusRimRoom)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_CANCEL) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL)),
            )
            SetupButton(
                text = stringResource(R.string.collections_delete_confirm),
                isPrimary = true,
                isFocused = focused == SettingsModel.CONFIRM_RESET,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_RESET, ring = FlatPartDimens.focusRimRoom)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_RESET) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET)),
            )
        }
    }
}

/**
 * Rename's page: the title and the name field with Plumemu's keyboard laid out as the game panel's New collection… is; with Android's
 * keyboard the panel's plain column. The page's actions come back through the router like the hints' ([SettingsTouch.onHint]).
 */
@Composable
private fun CollectionRenamePage(
    page: OpenCollectionRename,
    onAction: (Action) -> Unit,
) {
    if (page.typing.keyboardKind != KeyboardChoice.Plumemu) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
            PanelTitle(stringResource(R.string.collections_name_title))
            TypingPanelBody(page.screen, onAction)
        }
        return
    }
    TypingPanelLayout(
        page.typing,
        keyboardCovers = settingsKeyboardCovers(),
        modifier = Modifier.fillMaxHeight().testTag(SettingsPanelTags.COLLECTION_RENAME),
        header = {
            PanelTitle(stringResource(R.string.collections_name_title))
            Spacer(Modifier.height(PlumDimens.settingsPanelListGap))
        },
    ) {
        TypingPanelBody(page.screen, onAction)
    }
}
