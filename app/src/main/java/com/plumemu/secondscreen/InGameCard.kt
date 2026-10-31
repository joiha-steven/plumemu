package com.plumemu.secondscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.min
import com.plumemu.R
import com.plumemu.coreui.components.WifiGlyph
import com.plumemu.coreui.theme.InGameColors
import com.plumemu.coreui.theme.InGameDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BatteryReading
import com.plumemu.home.currentLocale
import com.plumemu.home.formatBattery
import com.plumemu.home.formatClock
import com.plumemu.home.rememberBatteryReading
import com.plumemu.home.rememberClockMillis
import com.plumemu.home.rememberWifiConnected
import com.plumemu.launcher.play.battery.BatteryReads
import kotlinx.coroutines.delay

/**
 * The second screen while a game runs, Clock and battery: the time and the battery on black, dim, moved along [PixelShift] once a
 * minute. Listens to the minute tick, the battery and Wi-Fi only while composed, which is only while
 * this card is shown. The Wi-Fi glyph rides on the battery line like the top bar's
 * rides with the battery: no battery line, no glyph. [showBattery] is Settings › Home screen › Show
 * battery, as the top bar reads it: off, the card is the time alone and neither the battery nor
 * Wi-Fi is listened to; a change shows without reopening the window. [clock] is the system clock;
 * a test passes its own.
 */
@Composable
internal fun InGameClockCard(
    clock: () -> Long = System::currentTimeMillis,
    showBattery: Boolean = true,
    /** The play's battery meter: read while shown with Show battery on; its figures make the battery part. */
    battery: BatteryReads = BatteryReads.None,
    /** The once-ever note about the drift has not been shown yet (`flags.in_game_drift_note_shown`). */
    driftNoteDue: Boolean = false,
    /** The note has started showing: stored, so it never shows again. */
    onDriftNoteShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val millis by rememberClockMillis(clock)
    // Called only while shown, so switching it off drops both listeners (the effects are disposed).
    val reading = if (showBattery) rememberBatteryReading().value else null
    val wifi = if (showBattery) rememberWifiConnected().value else false
    // Read on each tick: a 12 / 24 h change shows on the next minute at the latest, like the top bar.
    val time = formatClock(context, millis)
    val minute = PixelShift.epochMinuteOf(millis)
    // No meter (benchmark builds, tests without one): no battery part and no note, the card without those parts.
    val feed = if (showBattery && battery !== BatteryReads.None) remember(battery) { InGameBatteryFeed(battery, clock) } else null
    if (feed != null) {
        // The note is decided once, when the card first shows with the battery: storing it must not hide it.
        val noteDue = remember { driftNoteDue }
        var noteShowing by remember { mutableStateOf(false) }
        if (noteDue) {
            LaunchedEffect(Unit) {
                noteShowing = true
                onDriftNoteShown()
                delay(DRIFT_NOTE_MS)
                noteShowing = false
            }
        }
        LaunchedEffect(feed, millis, reading, noteShowing) { feed.update(reading, noteShowing) }
        // Measured only while the battery is shown: off, nothing is read.
        MeasureBatteryWhileShown(battery, onRead = feed::afterRead, onScreen = feed::onScreen)
    }
    InGameCard(
        time = time,
        battery = reading,
        wifi = wifi,
        shift = { PixelShift.offsetAt(minute) },
        part = { feed?.shown?.value },
        formatTime = { formatClock(context, it) },
    )
}

/** How long the drift note shows, the first time ever. */
internal const val DRIFT_NOTE_MS = 60_000L

/**
 * The card itself, from plain values: the shots and the burn-in test give it fixed ones. [shift]
 * is read while drawing (a translation of the block), so a new position alone redraws without a
 * new layout. No battery line until the first battery broadcast. [wifi] (connected through Wi-Fi)
 * puts the Wi-Fi glyph before the battery glyph, in the line's colour; gone when not connected.
 *
 * [part] is the battery part, read in its own scope so a new figure redraws the part
 * and not the clock: with figures the clock card goes to the top and the part fills the rest of
 * the frame; without, the card stays centred as it always was, with the part's note (if any) at the
 * bottom. Everything moves together by [shift].
 */
@Composable
internal fun InGameCard(
    time: String,
    battery: BatteryReading?,
    shift: () -> DpOffset,
    modifier: Modifier = Modifier,
    wifi: Boolean = false,
    part: () -> InGameBattery? = { null },
    formatTime: (Long) -> String = { "" },
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(InGameColors.background),
        contentAlignment = Alignment.Center,
    ) {
        val dimens = InGameDimens.forShorterSide(min(maxWidth, maxHeight))
        val density = LocalDensity.current
        val shifted =
            Modifier.graphicsLayer {
                val offset = shift()
                translationX = offset.x.toPx()
                translationY = offset.y.toPx()
            }
        val clock =
            @Composable { blockModifier: Modifier ->
                Column(modifier = blockModifier.testTag(InGameTags.BLOCK), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(text = time, style = dimens.timeStyle(density).copy(color = InGameColors.time), maxLines = 1, softWrap = false)
                    if (battery != null) {
                        Spacer(Modifier.height(InGameDimens.timeToBatteryGap))
                        BatteryLine(battery, wifi, dimens, dimens.batteryStyle(density))
                    }
                }
            }
        val hasFigures by remember(part) { derivedStateOf { part()?.figures != null } }
        val frame =
            Modifier
                .fillMaxSize()
                .padding(start = dimens.partInsetSide, top = dimens.partInsetTop, end = dimens.partInsetSide, bottom = dimens.partInsetBottom)
        if (hasFigures) {
            Column(frame.then(shifted).testTag(InGameBatteryTags.FRAME), horizontalAlignment = Alignment.CenterHorizontally) {
                clock(Modifier)
                Spacer(Modifier.height(dimens.cardToPartGap))
                PartFigures(part, dimens, formatTime, Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            // The layer on the block itself, as before the battery part: the shots and burn-in numbers stay as measured.
            clock(shifted)
            PartNote(part, dimens, frame.then(shifted))
        }
    }
}

/** The figures, read in this scope of their own: a new figure recomposes the part, not the clock above it. */
@Composable
private fun PartFigures(
    part: () -> InGameBattery?,
    dimens: InGameDimens,
    formatTime: (Long) -> String,
    modifier: Modifier,
) {
    val figures = part()?.figures ?: return
    InGameBatteryPart(figures, dimens, formatTime, modifier)
}

/** The clock card alone: the part's note, if any, at the bottom of the frame. */
@Composable
private fun PartNote(
    part: () -> InGameBattery?,
    dimens: InGameDimens,
    modifier: Modifier,
) {
    val note = part()?.note ?: return
    val type = PlumTheme.type
    val style = dimens.partStyle(InGameDimens.lineRole, type.font, type.textSize, LocalDensity.current).copy(color = InGameColors.partText)
    val text = stringResource(if (note == InGameNote.Drift) R.string.in_game_drift_note else R.string.in_game_battery_measuring)
    Box(modifier, contentAlignment = Alignment.BottomCenter) { Note(text, style) }
}

/** In game, Black: the window stays up and lights nothing; no listener, no frame after the first. */
@Composable
internal fun InGameBlack(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(InGameColors.background).testTag(InGameTags.BLACK))
}

@Composable
private fun BatteryLine(
    battery: BatteryReading,
    wifi: Boolean,
    dimens: InGameDimens,
    style: TextStyle,
) {
    val color = if (battery.isLow) InGameColors.batteryLow else InGameColors.battery
    val percent = formatBattery(battery.percent, currentLocale())
    val text = if (battery.isCharging) stringResource(R.string.second_screen_charging, percent) else percent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        if (wifi) {
            WifiGlyph(style, color, stringResource(R.string.wifi_connected), Modifier.testTag(InGameTags.WIFI))
            Spacer(Modifier.width(InGameDimens.wifiGap))
        }
        BatteryGlyph(battery.percent, color, dimens.glyphScale)
        Spacer(Modifier.width(InGameDimens.glyphGap))
        BasicText(text = text, style = style.copy(color = color), maxLines = 1, softWrap = false)
    }
}

/** Low: at [LOW_BATTERY_PERCENT] and below, not charging. */
internal val BatteryReading.isLow: Boolean get() = !isCharging && percent <= LOW_BATTERY_PERCENT

internal const val LOW_BATTERY_PERCENT = 15

/** An outline battery with its nub on the right, filled from the left by [percent]. */
@Composable
private fun BatteryGlyph(
    percent: Int,
    color: Color,
    scale: Float,
) {
    val bodyWidth = InGameDimens.glyphBodyWidth * scale
    val bodyHeight = InGameDimens.glyphBodyHeight * scale
    val nubWidth = InGameDimens.glyphNubWidth * scale
    Canvas(Modifier.size(bodyWidth + nubWidth, bodyHeight).testTag(InGameTags.BATTERY)) {
        val stroke = InGameDimens.glyphStroke.toPx() * scale
        val corner = InGameDimens.glyphCorner.toPx() * scale
        val inset = InGameDimens.glyphFillInset.toPx() * scale
        val body = Size(bodyWidth.toPx(), bodyHeight.toPx())
        // The stroke is centred on its path: half inside, so the outline stays within the body.
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(body.width - stroke, body.height - stroke),
            cornerRadius = CornerRadius(corner - stroke / 2),
            style = Stroke(width = stroke),
        )
        val nub = Size(nubWidth.toPx(), InGameDimens.glyphNubHeight.toPx() * scale)
        drawRect(color = color, topLeft = Offset(body.width, (body.height - nub.height) / 2), size = nub)
        val fillLeft = stroke + inset
        val fullWidth = body.width - 2 * fillLeft
        val fillWidth = fullWidth * percent.coerceIn(0, FULL_PERCENT) / FULL_PERCENT
        if (fillWidth > 0f) {
            drawRect(color = color, topLeft = Offset(fillLeft, fillLeft), size = Size(fillWidth, body.height - 2 * fillLeft))
        }
    }
}

private const val FULL_PERCENT = 100

internal object InGameTags {
    /** The time and battery block, the part that shifts. */
    const val BLOCK = "second_screen_in_game_block"
    const val WIFI = "second_screen_in_game_wifi"
    const val BATTERY = "second_screen_in_game_battery"
    const val BLACK = "second_screen_in_game_black"
}
