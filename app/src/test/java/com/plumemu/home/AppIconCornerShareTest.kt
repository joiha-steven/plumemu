package com.plumemu.home

import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.launcher.apps.AppIconShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** The focus ring's corner and the icons' mask corner are one number, or a ring would stand off an icon's corner. */
class AppIconCornerShareTest {
    @Test
    fun theRingFollowsTheIconMask() {
        assertEquals(AppIconShape.CORNER_SHARE, AppsGridDimens.APPS_ICON_CORNER_SHARE, 0f)
    }
}
