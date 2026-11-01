package com.plumemu.settings.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.THUMBNAIL_SLOT
import com.plumemu.home.ArtPicture
import com.plumemu.home.HomeTags
import com.plumemu.home.LocalHomeArt
import com.plumemu.home.PosterCover
import com.plumemu.home.fitInside
import com.plumemu.home.playTime
import com.plumemu.home.rememberCountFormat
import java.util.concurrent.TimeUnit

/*
 * Figures and covers shared by Statistics' page (SettingsStatisticsPage.kt) and the second screen's
 * statistics card: play times, "38 of 412", a game's small cover.
 */

/** "38 of 412", both numbers in the locale's format (also the second screen's statistics card). */
@Composable
internal fun countOf(
    count: Int,
    of: Int,
): String {
    val numbers = rememberCountFormat()
    return stringResource(R.string.settings_statistics_count_of, numbers.format(count), numbers.format(of))
}

/** A play time in the Info panel's words ("12 h 40 min"); under a minute "Less than a minute", never "0 min" (also the second screen's statistics card). */
@Composable
internal fun statisticTime(seconds: Long): String {
    val minutes = minutesOf(seconds)
    return if (minutes == 0) stringResource(R.string.settings_statistics_under_a_minute) else playTime(minutes)
}

/** Whole minutes, as the game's Info panel and the second screen count them. */
internal fun minutesOf(seconds: Long): Int = TimeUnit.SECONDS.toMinutes(seconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/**
 * A most-played game's cover in the thumbnail slot Fix box art uses: the home grid's saved box
 * front fitted inside (never cropped), decoded at that size by the home screen's loader; until it
 * is decoded the plain stand-in tile, so nothing moves when it lands. A game without a box front
 * wears its poster cover, as on the home screen.
 */
@Composable
internal fun StatisticCover(
    picture: ArtPicture?,
    index: Int,
    title: String,
    system: String,
) = FittedCover(picture, DpSize(THUMBNAIL_SLOT, THUMBNAIL_SLOT), SettingsTags.statisticCover(index), title, system)

/**
 * [picture] fitted inside [slot] (never cropped) and centred in it, or the landscape stand-in tile
 * until it is decoded; without a picture the poster cover of [title] on [system] in that landscape shape.
 */
@Composable
internal fun FittedCover(
    picture: ArtPicture?,
    slot: DpSize,
    tag: String,
    title: String,
    system: String,
) {
    val images = LocalHomeArt.current.images
    val density = LocalDensity.current
    val aspect = picture?.aspect ?: STATISTICS_STAND_IN_ASPECT
    val drawn = fitInside(aspect, slot)
    val widthPx = with(density) { drawn.width.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { drawn.height.roundToPx() }.coerceAtLeast(1)
    val bitmap by produceState(picture?.let { images.cached(it, widthPx, heightPx) }, picture, widthPx, heightPx) {
        // Null: no file, or it cannot be read (the loader logs why); the stand-in stays.
        value = if (picture == null) null else images.cached(picture, widthPx, heightPx) ?: images.load(picture, widthPx, heightPx)
    }
    val corner = if (aspect >= 1f) PlumDimens.cornerCoverLandscape else PlumDimens.cornerCoverPortrait
    Box(Modifier.size(slot).testTag(tag), contentAlignment = Alignment.Center) {
        val image = bitmap
        val tile = Modifier.size(drawn).clip(RoundedCornerShape(corner))
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = tile)
        } else if (picture == null) {
            PosterCover(title, system, modifier = tile.testTag(HomeTags.POSTER_COVER))
        } else {
            // chip alone is a grey slab on Paper; the hairline in muted (4.6:1 and more against the background) keeps its edge visible on both.
            Box(tile.background(PlumTheme.colors.chip).border(PlumDimens.coverEdge, PlumTheme.colors.muted, RoundedCornerShape(corner)))
        }
    }
}

/** An unknown folder's name: three lines before its "…" (two did not hold a 39-letter name at Largest in Mono). */
internal const val GAME_TITLE_LINES = 3

/** The second screen's statistics card's stand-in tile shape: a portrait box, as Fix box art's. This page uses [STATISTICS_STAND_IN_ASPECT]. */
internal const val STAND_IN_ASPECT = 0.7f

/**
 * The stand-in tile's shape in this page's cover slot: the landscape box front real covers have here
 * (560 x 400), so a game without art does not change the slot's shape. The second screen's card keeps
 * [STAND_IN_ASPECT].
 */
internal const val STATISTICS_STAND_IN_ASPECT = 1.4f
