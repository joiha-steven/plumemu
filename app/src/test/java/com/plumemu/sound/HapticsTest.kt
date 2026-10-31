package com.plumemu.sound

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which way a vibrator is driven: the best one the Android version and the motor allow (minSdk 23). */
internal class HapticsTest {
    @Test
    fun primitivesNeedAndroid11AndAMotorThatReportsThem() {
        assertEquals(HapticTechnique.Primitives, hapticTechnique(Build.VERSION_CODES.R, primitivesSupported = true))
        assertEquals(HapticTechnique.Primitives, hapticTechnique(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, primitivesSupported = true))
    }

    @Test
    fun withoutPrimitivesAndroid10UsesThePredefinedTickAndClick() {
        assertEquals(HapticTechnique.Predefined, hapticTechnique(Build.VERSION_CODES.R, primitivesSupported = false))
        assertEquals(HapticTechnique.Predefined, hapticTechnique(Build.VERSION_CODES.Q, primitivesSupported = false))
    }

    @Test
    fun android8And9UseAShortOneShotAtALowAmplitude() {
        assertEquals(HapticTechnique.OneShot, hapticTechnique(Build.VERSION_CODES.P, primitivesSupported = false))
        assertEquals(HapticTechnique.OneShot, hapticTechnique(Build.VERSION_CODES.O, primitivesSupported = false))
    }

    @Test
    fun android6And7FallBackToAPlainVibrateForSomeMilliseconds() {
        assertEquals(HapticTechnique.Plain, hapticTechnique(Build.VERSION_CODES.N_MR1, primitivesSupported = false))
        assertEquals(HapticTechnique.Plain, hapticTechnique(Build.VERSION_CODES.M, primitivesSupported = false))
    }

    @Test
    fun primitivesReportedBeforeAndroid11AreNotTrusted() {
        assertEquals(HapticTechnique.Predefined, hapticTechnique(Build.VERSION_CODES.Q, primitivesSupported = true))
    }
}
