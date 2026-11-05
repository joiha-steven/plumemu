package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.plumemu.coreui.components.PerfSwitches

/**
 * Where the text lines of one menu row sit inside it, measured from the placed texts (a row holds one to three lines of different heights,
 * so its padding alone does not say where a text ends). Put [mark] on the row's texts through `LocalRowTextMark` and [item] on the row's root.
 */
internal class RowTextBands {
    private val texts = HashSet<LayoutCoordinates>()
    var item: LayoutCoordinates? = null

    /** Bumped when a text joins, so a reader of the bands runs again. */
    var seen by mutableIntStateOf(0)
        private set

    val mark: Modifier =
        if (PerfSwitches.noBands) Modifier else Modifier.onGloballyPositioned {
            if (texts.add(it)) seen++
        }

    /** The top and bottom (px) of every placed text, from the row's top. */
    fun bands(): List<Pair<Float, Float>> {
        val root = item?.takeIf { it.isAttached } ?: return emptyList()
        texts.removeAll { !it.isAttached }
        return texts.map { t ->
            val top = root.localPositionOf(t, Offset.Zero).y
            top to top + t.size.height
        }
    }
}
