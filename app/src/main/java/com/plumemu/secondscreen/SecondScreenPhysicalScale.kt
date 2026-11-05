package com.plumemu.secondscreen

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.runtime.Composable
import com.plumemu.coreui.theme.PanelFacts
import com.plumemu.coreui.theme.ProvidePhysicalTextScale
import com.plumemu.coreui.theme.ProvideSecondScreenText
import com.plumemu.coreui.theme.SecondScreenPhysical
import com.plumemu.coreui.theme.ThorPanels
import kotlin.math.hypot

/**
 * The second screen's text, scaled so a style is at least as big to the eye as on the main screen ([SecondScreenPhysical]): every
 * size of the type under it is multiplied by [scale], the Text size setting applying on top as everywhere else.
 */
@Composable
internal fun WithPhysicalTextScale(
    scale: Float,
    content: @Composable () -> Unit,
) {
    if (scale == 1f) ProvideSecondScreenText(content) else ProvidePhysicalTextScale(scale, content)
}

/** The scale for text on [second] next to the default display's, 1 where either panel's size cannot be told. */
internal fun physicalTextScaleOf(
    context: Context,
    second: Display,
): Float {
    val main = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return 1f
    val secondFacts = panelFactsOf(second) ?: return 1f
    val mainFacts = panelFactsOf(main) ?: return 1f
    return SecondScreenPhysical.scale(secondFacts, mainFacts)
}

/** A Thor panel by its pixel size, any other display by the dpi Android reports for it; null when that is missing. */
internal fun panelFactsOf(display: Display): PanelFacts? {
    val metrics = DisplayMetrics().also { display.getMetrics(it) }
    return panelFactsOf(metrics)
}

internal fun panelFactsOf(metrics: DisplayMetrics): PanelFacts? {
    val long = maxOf(metrics.widthPixels, metrics.heightPixels)
    val short = minOf(metrics.widthPixels, metrics.heightPixels)
    val diagonal =
        ThorPanels.diagonalOf(long, short)
            ?: if (metrics.xdpi > 0f && metrics.ydpi > 0f) hypot(long / metrics.xdpi.toDouble(), short / metrics.ydpi.toDouble()) else null
    return diagonal?.let { PanelFacts(long, short, metrics.density, it) }
}
