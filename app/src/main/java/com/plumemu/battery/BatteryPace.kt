package com.plumemu.battery

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.launcher.play.battery.TimeLeft
import com.plumemu.launcher.play.battery.UsualPace
import com.plumemu.launcher.play.battery.roundedMinutesLeft
import kotlin.math.roundToInt

/**
 * What the picking line knows of a game's usual battery pace (worked out from the stored
 * plays by `UsualPace`): how long the charge there is now lasts at that pace ([minutesOnCharge]: the
 * charge counter over the usual draw) and the pace as a share of a full battery per hour
 * ([percentPerHour]). Either may be unknown, never both. A game never measured has none: no line.
 *
 * The figures are those of the moment they were read; [usualDrawMicroamps], the draw they came from,
 * lets the line work them out again when the battery's level or charging changes ([BatteryPaceNow]).
 */
@Immutable
internal data class BatteryPace(
    val minutesOnCharge: Double?,
    val percentPerHour: Double?,
    /** The game's usual draw these figures were worked out from; null: the figures are kept as they are (tests' fixed paces). */
    val usualDrawMicroamps: Long? = null,
) {
    companion object {
        /**
         * `UsualPace`'s figures for a game whose usual draw is [usualDrawMicroamps] as a pace, each kept only
         * when it is a usable figure (above zero, a number); null when neither is.
         */
        fun of(
            figures: UsualPace.Figures?,
            usualDrawMicroamps: Long? = null,
        ): BatteryPace? {
            val minutes = figures?.minutesOnCharge?.takeIf(::isUsable)
            val percent = figures?.percentPerHour?.takeIf(::isUsable)
            return if (minutes == null && percent == null) null else BatteryPace(minutes, percent, usualDrawMicroamps)
        }

        private fun isUsable(figure: Double) = figure > 0.0 && figure.isFinite()
    }
}

/**
 * Works a game's pace out again from its usual draw and the battery now (`UsualPace` over the charge
 * counter and level of this moment), off the main thread: the picking line asks it when the level or
 * charging it shows changes, so the time is never the one of a charge long gone. Given to the line as
 * [LocalBatteryPaceNow] by the screens that show it (`MainScreen`, the second screen's host).
 */
internal fun interface BatteryPaceNow {
    /** The pace now of a game whose usual draw is [usualDrawMicroamps]; null when nothing is known of the battery. Throws what the battery reads throw. */
    suspend fun of(usualDrawMicroamps: Long): BatteryPace?
}

/** The screen's [BatteryPaceNow]; null outside one that gives it (tests, benchmark builds): the line keeps the figures it was given. */
internal val LocalBatteryPaceNow = staticCompositionLocalOf<BatteryPaceNow?> { null }

/**
 * What the picking line says: the time on
 * the charge there is now at the usual pace ([onCharge]; null while charging, when only the pace is
 * told, or when not known) and the pace in whole percent of a full battery an hour ([percentPerHour]:
 * [UNDER_ONE_PERCENT] for a pace under 1%, null when not known). Never both null.
 */
internal data class PickingBatteryParts(
    val onCharge: TimeLeft?,
    val percentPerHour: Int?,
)

/** [PickingBatteryParts.percentPerHour] of a pace under 1% an hour: said as "under 1%", its time kept from the real pace. */
internal const val UNDER_ONE_PERCENT = 0

/**
 * The line's figures for [pace], or null when there is nothing to say (charging with no pace known):
 * the time rounded like the in-game time left (5 minutes under an hour, 15 above, "About" where the
 * rounding hid minutes); the pace in whole percent, "under 1%" below 1.
 */
internal fun pickingBatteryParts(
    pace: BatteryPace,
    isCharging: Boolean,
): PickingBatteryParts? {
    val onCharge = if (isCharging) null else pace.minutesOnCharge?.let(::roundedMinutesLeft)
    val percent = pace.percentPerHour?.let { if (it < 1.0) UNDER_ONE_PERCENT else it.roundToInt() }
    return if (onCharge == null && percent == null) null else PickingBatteryParts(onCharge, percent)
}

/**
 * How many of the glyph's [GLYPH_STEPS] bars show for a battery at [percent]: none under 5%, then a
 * bar for each quarter, nearest first (5-37 one, 38-62 two, 63-87 three, 88 and over four).
 */
internal fun glyphSteps(percent: Int): Int =
    if (percent < EMPTY_BELOW) 0 else ((percent + QUARTER / 2) / QUARTER).coerceIn(1, GLYPH_STEPS)

internal const val GLYPH_STEPS = 4
private const val EMPTY_BELOW = 5
private const val QUARTER = 25
