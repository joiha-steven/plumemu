package com.plumemu.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.content.SystemArrangement
import com.plumemu.content.SystemOrder
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreui.components.carriedPill
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.currentLocale
import com.plumemu.home.rememberCountFormat
import java.util.Locale

/*
 * Settings › Library › Systems: every system with games in the main screen's
 * order. A picks the focused one up (the row turns solid accent with an `onAccent` ring), Up / Down carry it (held: as
 * fast as the router repeats), L1 / R1 to the top / bottom, A drops it, B puts it back. Picking up
 * while the order is A–Z or Maker & year switches it to Manual from the order on screen. Y hides
 * or shows the focused system; one always stays shown. Touch: a tap focuses, a long press on the
 * handle drags, a tap on "Hidden" shows it again (and the "Y Hide" hint hides).
 */

/** A system being carried: [id] in [order] (the panel's order while carried), and the order and place before it was picked up. */
internal data class SystemCarry(
    val id: String,
    val order: List<String>,
    val before: List<String>,
    val fromIndex: Int,
)

/**
 * The Systems panel's own state. [held] is the order the panel shows while a system is carried
 * and after it was dropped, until the panel is left: the store answers later than the drop, and
 * the cursor must not land on another system meanwhile.
 */
@Stable
internal class SettingsSystems {
    var carry: SystemCarry? by mutableStateOf(null)
        private set

    private var held: List<String>? by mutableStateOf(null)

    /** The language A–Z follows; the panel sets it from the screen's configuration. */
    var locale: Locale = Locale.getDefault()

    val isCarrying: Boolean get() = carry != null

    /** The rows, top to bottom: the held order, else the stored arrangement ([SystemOrder], as the main screen). */
    fun rows(
        content: SettingsContent,
        settings: PlumSettings,
    ): List<LibrarySystem> {
        val arranged = SystemOrder.arrange(content.librarySystems, SystemArrangement.of(settings.library), locale) { it.facts }
        val order = held ?: return arranged
        val byId = content.librarySystems.associateBy { it.id }
        // A scan may add a system while the panel is open: it joins at the end, as Manual would put it.
        val placed = order.toSet()
        return order.mapNotNull { byId[it] } + arranged.filter { it.id !in placed }
    }

    /** Picks up the row at [index] of [rows]. */
    fun pickUp(
        rows: List<LibrarySystem>,
        index: Int,
    ): SystemCarry? {
        val system = rows.getOrNull(index) ?: return null
        val ids = rows.map { it.id }
        held = ids
        return SystemCarry(system.id, ids, ids, index).also { carry = it }
    }

    /**
     * The library changed while a system is carried (a scan, a folder removed): the carried order
     * and the one B returns to become what is on screen, gone systems dropped and new ones at the
     * end A to Z, so every move and the cursor count rows on screen. Gone systems keep their slots
     * in the stored list anyway ([SystemOrder.manualAfterMove]). Returns the carried row's index.
     * If the carried system itself is gone, nothing is lifted any more: the carry ends unstored.
     */
    fun follow(content: SettingsContent): Int? {
        val now = carry ?: return null
        val present = content.librarySystems.map { it.id }.toSet()
        if (now.id !in present) {
            leave()
            return null
        }
        fun onScreen(order: List<String>): List<String> {
            val kept = order.filter { it in present }
            val placed = kept.toSet()
            val added = content.librarySystems.filter { it.id !in placed }
            return kept + SystemOrder.arrange(added, SystemArrangement(), locale) { it.facts }.map { it.id }
        }
        val order = onScreen(now.order)
        val before = onScreen(now.before)
        held = order
        carry = now.copy(order = order, before = before, fromIndex = before.indexOf(now.id).coerceAtLeast(0))
        return order.indexOf(now.id).takeIf { it >= 0 }
    }

    /** The carried system moved to [index] (clamped); its new index, or null when it did not move. */
    fun carryTo(index: Int): Int? {
        val now = carry ?: return null
        val from = now.order.indexOf(now.id)
        val to = index.coerceIn(0, now.order.lastIndex)
        if (from < 0 || to == from) return null
        val order = now.order.toMutableList().apply { add(to, removeAt(from)) }
        held = order
        carry = now.copy(order = order)
        return to
    }

    /**
     * Puts the carried system down where it is: the panel's order to store (systems a scan added
     * meanwhile at the end, A to Z), or null when nothing was carried. The panel keeps showing it.
     */
    fun drop(content: SettingsContent): List<String>? {
        val now = carry ?: return null
        carry = null
        val placed = now.order.toSet()
        val added = content.librarySystems.filter { it.id !in placed }
        val order = now.order + SystemOrder.arrange(added, SystemArrangement(), locale) { it.facts }.map { it.id }
        held = order
        return order
    }

    /** B: the carried system goes back where it was; its old index. */
    fun cancel(): Int? {
        val now = carry ?: return null
        carry = null
        held = now.before
        return now.fromIndex
    }

    /** The panel was left: next time it opens on the stored order. */
    fun leave() {
        carry = null
        held = null
    }
}

/** "16 shown" or "14 of 16 shown": the Systems row's value. */
@Composable
internal fun systemsShownText(
    content: SettingsContent,
    settings: PlumSettings,
): String {
    val format = rememberCountFormat()
    val total = content.librarySystems.size
    val shown = content.librarySystems.count { it.id !in settings.library.hiddenSystems }
    // Every system hidden shows them all on the main screen (SystemOrder.split): say what it shows.
    return if (shown == total || shown == 0) {
        pluralStringResource(R.plurals.settings_systems_shown, total, format.format(total))
    } else {
        pluralStringResource(R.plurals.settings_systems_shown_of, total, format.format(shown), format.format(total))
    }
}

/** The order's name for the System order row, its value index [index]. */
internal fun systemOrderName(index: Int): Int =
    when (SystemOrderSetting.entries[index]) {
        SystemOrderSetting.Name -> R.string.settings_system_order_name
        SystemOrderSetting.Maker -> R.string.settings_system_order_maker
        SystemOrderSetting.Manual -> R.string.settings_system_order_manual
    }

/** Library › Systems' panel: the title, a plain line when there is one, then every system with games. */
@Composable
internal fun SystemsPanel(
    settings: PlumSettings,
    model: SettingsModel,
) {
    // The model arranges with the same language as the rows drawn here (a plain field: nothing observes it).
    model.systems.locale = currentLocale()
    val content = model.content
    val rows = model.systems.rows(content, settings)
    val inPage = model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Systems
    val focused = if (inPage) model.focus.panelIndex else -1
    val carriedId = model.systems.carry?.id
    val geometry = settingsGeometry(PlumTheme.layout)
    val latestSettings by rememberUpdatedState(settings)
    val latestGeometry by rememberUpdatedState(geometry)
    Column(Modifier.fillMaxSize().testTag(SettingsTags.SYSTEMS_PANEL), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_systems))
        val notice = model.work.notice?.takeIf { it.first == SettingsOption.Systems }?.second
        if (notice != null) {
            // "Order set to Manual" is quiet (it only says what happened); the refusal is a plain line in the text colour.
            val color = if (notice == SettingsNotice.SystemOrderNowManual) PlumTheme.colors.muted else PlumTheme.colors.text
            BasicText(stringResource(notice.text), style = PlumTheme.type.panelBody.copy(color = color), modifier = Modifier.onRowTextLine().testTag(SettingsTags.NOTICE))
        }
        KeepFocusedColumn(
            { if (model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Systems) model.focus.panelIndex else -1 },
            rows.size,
            PlumDimens.listRowGap,
            Modifier.fillMaxWidth().weight(1f).testTag(SettingsTags.SYSTEMS_LIST),
            // The carried row is drawn solid by itself ([carriedPill]); a gliding pill lagging behind it would show next to it.
            pillIndex = { if (carriedId != null) -1 else if (model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Systems) model.focus.panelIndex else -1 },
        ) { place ->
            rows.forEachIndexed { index, system ->
                SystemArrangeRow(
                    system = system,
                    isFocused = index == focused,
                    isCarried = system.id == carriedId,
                    isHidden = system.id in settings.library.hiddenSystems,
                    modifier =
                        place(index)
                            .tapTarget { model.onTapSystem(index, latestSettings, latestGeometry) }
                            .testTag(SettingsTags.panelItem(index)),
                    onHiddenTap = { model.onTapSystemHidden(index, latestSettings, latestGeometry) },
                    handle = Modifier.systemHandleDrag(index, model, { latestSettings }, { latestGeometry }),
                )
            }
        }
    }
}

/**
 * Long press, then drag: picks the row up and carries it one place each time the finger travels
 * a row's height; lifting the finger drops it, a cancelled gesture puts it back. Keyed by the
 * model only, so the gesture survives its row index showing another system as it moves.
 */
@Composable
private fun Modifier.systemHandleDrag(
    index: Int,
    model: SettingsModel,
    settings: () -> PlumSettings,
    geometry: () -> SettingsGeometry,
): Modifier {
    val latestIndex by rememberUpdatedState(index)
    val step = with(LocalDensity.current) { (PlumTheme.layout.rowHeight + PlumDimens.listRowGap).toPx() }
    val latestStep by rememberUpdatedState(step)
    val travelled = remember { floatArrayOf(0f) }
    return pointerInput(model) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                travelled[0] = 0f
                model.onSystemDragStart(latestIndex, settings(), geometry())
            },
            onDrag = { change, amount: Offset ->
                change.consume()
                travelled[0] += amount.y
                while (travelled[0] >= latestStep) {
                    travelled[0] -= latestStep
                    model.onSystemDragStep(+1)
                }
                while (travelled[0] <= -latestStep) {
                    travelled[0] += latestStep
                    model.onSystemDragStep(-1)
                }
            },
            onDragEnd = { model.onSystemDragEnd() },
            onDragCancel = { model.onSystemDragCancel() },
        )
    }
}

/**
 * One system: its name over its game count, "Hidden" when hidden, and the drag handle. Focused: the
 * one focus language (the accent pill and ring, `strong` text); carried, the pill is solid accent with an
 * `onAccent` ring and `onAccent` text, so it is told from merely focused (the ring stays inside the edge:
 * one outside would be cut by the panel's scroll).
 */
@Composable
private fun SystemArrangeRow(
    system: LibrarySystem,
    isFocused: Boolean,
    isCarried: Boolean,
    isHidden: Boolean,
    modifier: Modifier,
    onHiddenTap: () -> Unit,
    handle: Modifier,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val onAccent = isCarried
    Row(
        // As high as its text needs (at least a list row), so the handle's touch column fills it.
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PlumTheme.layout.rowHeight)
                .height(IntrinsicSize.Min)
                .then(if (isCarried) Modifier.carriedPill() else Modifier.focusPill(isFocused))
                .padding(start = PlumDimens.settingsPanelRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = PlumDimens.settingsSystemRowPadding)) {
            val nameStyle =
                when {
                    onAccent -> type.settingsPanelItemFocused.copy(color = colors.onAccent)
                    isFocused -> type.settingsPanelItemFocused.copy(color = colors.strong)
                    isHidden -> type.meta.copy(color = colors.muted)
                    else -> type.meta.copy(color = colors.text)
                }
            BasicText(system.name, style = nameStyle, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(SettingsTags.SYSTEM_NAME))
            BasicText(
                pluralStringResource(R.plurals.settings_systems_games, system.gameCount, rememberCountFormat().format(system.gameCount)),
                style = type.smallFact.copy(color = if (onAccent) colors.onAccent else if (isFocused) colors.meta else colors.muted),
                modifier = Modifier.testTag(SettingsTags.SYSTEM_COUNT),
            )
        }
        if (isHidden) {
            Spacer(Modifier.width(PlumDimens.rowTrailingGap))
            BasicText(
                stringResource(R.string.settings_systems_hidden),
                style = type.smallFact.copy(color = if (onAccent) colors.onAccent else if (isFocused) colors.meta else colors.muted),
                modifier = Modifier.tapTarget(onHiddenTap).testTag(SettingsTags.SYSTEM_HIDDEN),
            )
        }
        val description = stringResource(R.string.settings_systems_handle, system.name)
        DragHandle(
            handle
                .width(PlumDimens.settingsHandleWidth)
                .fillMaxHeight()
                .semantics { contentDescription = description }
                .testTag(SettingsTags.SYSTEM_HANDLE),
            isFocused,
            onAccent,
        )
    }
}

/** Three short bars, centred in the handle's touch column: the usual "drag me" mark. */
@Composable
private fun DragHandle(
    modifier: Modifier,
    isFocused: Boolean,
    onAccent: Boolean,
) {
    val color = if (onAccent) PlumTheme.colors.onAccent else if (isFocused) PlumTheme.colors.meta else PlumTheme.colors.chip
    Canvas(modifier) {
        val bar = PlumDimens.settingsHandleBar.toPx()
        val thickness = PlumDimens.settingsHandleBarThickness.toPx()
        val gap = PlumDimens.settingsHandleBarGap.toPx()
        val left = (size.width - bar) / 2f
        for (i in -1..1) {
            val y = size.height / 2f + i * gap
            drawLine(color, Offset(left, y), Offset(left + bar, y), strokeWidth = thickness)
        }
    }
}
