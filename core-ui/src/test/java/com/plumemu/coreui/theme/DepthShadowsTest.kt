package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shadows drawn outside the collar and slab recipes turn with [Depth] too. */
class DepthShadowsTest {
    @Test
    fun theKeyboardTrayFollowsTheReach() {
        assertEquals(12f * Depth.SHADOW_REACH, KeyboardDimens.keyboardShadowBlur.value, 0.001f)
        assertEquals(6f * Depth.SHADOW_REACH, KeyboardDimens.keyboardShadowOffset.value, 0.001f)
    }

    @Test
    fun aFocusedAppsGlowFollowsTheDepth() {
        assertEquals(18f * Depth.GLOW_REACH, PlumDimens.coverGlowBlur.value, 0.001f)
        assertEquals(0.5f * Depth.GLOW, PlumDimens.COVER_GLOW_ALPHA_DARK, 0.001f)
    }

    @Test
    fun aFlatKeyAndABadgeFollowTheReach() {
        assertEquals(3f * Depth.SHADOW_REACH, PlumDimens.flatShadow.value, 0.001f)
        assertEquals(1.333f * Depth.SHADOW_REACH, BadgeFrameDimens.drop.value, 0.001f)
    }
}
