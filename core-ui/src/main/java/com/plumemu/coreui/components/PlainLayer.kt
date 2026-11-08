package com.plumemu.coreui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.unit.Constraints

/**
 * A graphics layer that is not also a semantics node. Compose's `graphicsLayer` is one (empty, or carrying its shape when it
 * clips), so a layout that has nothing else to say still gets a node, and every node is walked and sorted by the
 * accessibility pass on each change while any accessibility service is on (the handhelds' own game assistant is). This places
 * the content with the same kind of layer, so it draws exactly as `graphicsLayer` would; reads in [block] update the layer only.
 * Use it where the layer is for drawing alone (alpha, translation, scale, a clip): a layer whose shape a test reads keeps
 * `graphicsLayer`.
 */
fun Modifier.plainLayer(block: GraphicsLayerScope.() -> Unit): Modifier = this then PlainLayerElement(block)

private data class PlainLayerElement(
    val block: GraphicsLayerScope.() -> Unit,
) : ModifierNodeElement<PlainLayerNode>() {
    override fun create() = PlainLayerNode(block)

    override fun update(node: PlainLayerNode) {
        node.block = block
        node.invalidatePlacement()
    }
}

private class PlainLayerNode(
    var block: GraphicsLayerScope.() -> Unit,
) : Modifier.Node(),
    LayoutModifierNode {
    override val shouldAutoInvalidate: Boolean get() = false

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = block) }
    }
}
