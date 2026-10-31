package com.plumemu.content

import com.plumemu.coreui.theme.CoverShape
import com.plumemu.platforms.GameSystem

/**
 * A system's box-art shape on the main screen (docs/design/rules.md › Grid), from
 * data, never guessed: the median of the person's own saved box fronts once there are enough
 * (core-data's `BoxFrontShapes`, which follows the region they scraped in), else the platform
 * data's ratio (measured for the main systems, the class's default for the others). A system the
 * platform data does not know keeps [CoverShape.Landscape], as before.
 */
internal object SystemCoverShapes {
    fun of(
        system: GameSystem?,
        measured: Float?,
    ): CoverShape {
        measured?.takeIf { it > 0f && it.isFinite() }?.let { return CoverShape(it) }
        val ratio = system?.coverRatio?.toFloat()?.takeIf { it > 0f && it.isFinite() } ?: return CoverShape.Landscape
        return CoverShape(ratio)
    }
}
