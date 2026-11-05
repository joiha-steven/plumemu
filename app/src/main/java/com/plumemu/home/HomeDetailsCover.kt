package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.components.coverBoxShadow
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumDimens
import kotlin.math.abs

/**
 * The wide details column's cover as the Covers board draws a box: whole, in a thin collar ([CollarSize.Cover]) with the
 * box's small drop shadow, as a grid cell's. It stands in the middle of the column's [size] (a portrait box has room on both
 * sides), on the slot's bottom edge so the title under it never moves. The box is the picture's shape fitted in what the collar
 * leaves of [size]; given less room (large text) the collar and the box shrink together. While the result is being read the box
 * already has its final shape ([coverBoxAspect]), so it does not shrink when the picture lands.
 */
@Composable
internal fun CollaredDetailsCover(
    slot: ArtSlot,
    size: DpSize,
    poster: PosterSpec?,
    posterAspect: Float?,
    appPackage: String?,
    storedAspect: Float? = null,
) {
    val tagged = Modifier.testTag(HomeTags.DETAILS_COVER)
    if (appPackage != null) {
        // An installed app listed as a game: its Android icon, never a poster.
        AppGameIcon(appPackage, poster?.title.orEmpty(), size, tagged)
        return
    }
    val art = (slot as? ArtSlot.Found)?.art
    val picture = art?.detailsPicture()
    // As DetailsCover: the poster stands in only once the picture is known to be missing.
    val shownPoster = if (slot is ArtSlot.Unknown) null else poster
    val inner = coverInner(size)
    val aspect = coverBoxAspect(slot, picture, storedAspect, posterAspect) ?: posterAspect.takeIf { shownPoster != null }
    val box = if (aspect != null) fitInside(aspect, inner) else inner
    val corner = CollarDimens.coverBoxCorner
    val whole = DpSize(box.width + CollarSize.Cover.sidePadding * 2, box.height + CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding)
    Box(tagged.size(size), contentAlignment = Alignment.BottomStart) {
        Box(Modifier.shrinkInto(whole)) {
            Box(Modifier.collar(CollarSize.Cover, corner, halo = false) { 0f }) {
                CoverArt(
                    title = null,
                    picture = picture,
                    size = box,
                    alignment = Alignment.Center,
                    corner = corner,
                    titlePadding = PlumDimens.coverPaddingDetails,
                    modifier = Modifier.testTag(HomeTags.DETAILS_COVER_BOX).coverBoxShadow(corner),
                    poster = shownPoster,
                    posterAspect = posterAspect,
                )
            }
        }
    }
}

/**
 * The shape of a cover's box, the grid's and the details column's alike. With a [picture] in hand, its shape: it beats a stored
 * value the pass has not caught up with. A [storedAspect] within [SAME_SHAPE] of it stands, so the details column's large file does
 * not nudge a box stored from the grid file by a pixel. While the slot is [ArtSlot.Unknown]: [storedAspect], else the system's
 * ([posterAspect]). Null for a game known to have no picture: a stored value left from removed art must not shape its poster.
 */
internal fun coverBoxAspect(
    slot: ArtSlot,
    picture: ArtPicture?,
    storedAspect: Float?,
    posterAspect: Float?,
): Float? =
    when {
        picture != null -> storedAspect?.takeIf { abs(it - picture.aspect) <= picture.aspect * SAME_SHAPE } ?: picture.aspect
        slot is ArtSlot.Unknown -> storedAspect ?: posterAspect
        else -> null
    }

/** Two shapes this close, as a share of the picture's, are one shape: the grid file and the large file of one cover round differently. */
private const val SAME_SHAPE = 0.02f

/**
 * The saved box front the details column draws: the larger of the two saved files, so a column that is taller than the grid file
 * never stretches the grid-size one (either stands in for the other when only one is saved).
 */
internal fun GameArt.detailsPicture(): ArtPicture? {
    val big = large ?: return grid
    val small = grid ?: return big
    return if (small.height > big.height) small else big
}
