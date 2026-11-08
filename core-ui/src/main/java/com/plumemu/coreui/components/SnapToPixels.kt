package com.plumemu.coreui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import kotlin.math.round

/**
 * Moves what follows it by up to half a pixel so its top-left corner lies on a whole pixel of the window, while [active] is true.
 * The home strip rests at a fractional x, so the lists on it sit between pixels; an offscreen layer used to be drawn on whole pixels, which kept covers
 * sharp. The edge-fade masks no longer use that layer, so this does it. The shift is a layer translation read in the layer block: it follows
 * movement of any ancestor (strip slide, list glide) without redrawing the content. Anything not under this modifier stays where it was.
 */
internal fun Modifier.snapToPixels(active: () -> Boolean = { true }): Modifier = this then SnapToPixelsElement(active)

private class SnapToPixelsElement(
    val active: () -> Boolean,
) : ModifierNodeElement<SnapToPixelsNode>() {
    override fun create() = SnapToPixelsNode(active)

    override fun update(node: SnapToPixelsNode) {
        node.active = active
    }

    override fun equals(other: Any?) = other is SnapToPixelsElement && other.active == active

    override fun hashCode() = active.hashCode()
}

private class SnapToPixelsNode(
    var active: () -> Boolean,
) : Modifier.Node(),
    LayoutModifierNode,
    GlobalPositionAwareModifierNode {
    private var dx by mutableFloatStateOf(0f)
    private var dy by mutableFloatStateOf(0f)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                if (active()) {
                    translationX = dx
                    translationY = dy
                }
            }
        }
    }

    // The node's own coordinates do not include the layer it places its content in, so the offset cannot feed back into itself.
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        val nx = round(p.x) - p.x
        val ny = round(p.y) - p.y
        if (nx != dx) dx = nx
        if (ny != dy) dy = ny
    }
}
