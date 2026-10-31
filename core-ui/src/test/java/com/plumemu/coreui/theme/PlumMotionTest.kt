package com.plumemu.coreui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlumMotionTest {
    @Test
    fun motionNeedsBothTheSystemScaleAndTheSetting() {
        assertTrue(PlumMotion.isMotionOn(systemAnimatorScale = 1f, animationsSetting = true))
        assertTrue(PlumMotion.isMotionOn(systemAnimatorScale = 0.5f, animationsSetting = true))
        assertFalse(PlumMotion.isMotionOn(systemAnimatorScale = 0f, animationsSetting = true))
        assertFalse(PlumMotion.isMotionOn(systemAnimatorScale = 1f, animationsSetting = false))
    }

    @Test
    fun slideIs180MsEaseOutOrAJump() {
        val on = PlumMotionState(isOn = true).slideSpec<Float>()
        assertTrue(on is TweenSpec<*>)
        assertEquals(180, (on as TweenSpec<Float>).durationMillis)
        assertEquals(PlumMotion.EaseOut, on.easing)
        assertTrue(PlumMotionState(isOn = false).slideSpec<Float>() is SnapSpec<*>)
    }
}
