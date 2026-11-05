package com.plumemu.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * What a test compares a lit cover groove with. The Covers board's focused groove is the accent under `inset 0 1px 2px .35`, and beside a
 * picture also under the art's `0 3px 4px .4` drop: at mid height it measures (50,100,183) left and (48,96,176) right of the picture for
 * #3E7BE0, the accent 20 % toward black, while a groove with nothing beside it stays the accent. The midpoint, 10 % toward black, is within
 * 0.12 of both and far from any unlit well.
 */
internal fun litGroove(accent: Color): Color = lerp(accent, Color.Black.copy(alpha = accent.alpha), 0.1f)
