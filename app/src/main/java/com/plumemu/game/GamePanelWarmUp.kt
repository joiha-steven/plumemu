package com.plumemu.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.WarmCardImages
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** The box the card fills, measured with the panel closed: its first opening then finds its shadows built. */
@Composable
internal fun WarmGamePanel(
    isWide: Boolean,
    modifier: Modifier,
) {
    val layout = PlumTheme.layout
    var areaHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier.fillMaxSize().onSizeChanged { areaHeight = with(density) { it.height.toDp() } })
    WarmCardImages(if (isWide) CardDimens.menuWidth else layout.width - PlumDimens.stripMarginSide * 2, areaHeight, layout.rowHeight)
}
