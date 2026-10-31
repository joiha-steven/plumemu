package com.plumemu.sound

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** What [UiSounds] feels with: a light tick for a cursor move, a stronger click for a confirm. Must never block. */
interface Haptics {
    fun tick()

    fun click()

    object None : Haptics {
        override fun tick() = Unit

        override fun click() = Unit
    }
}

/** [make] runs at the first tick or click, not at construction: an Activity has no context while its fields are made. */
internal fun lazyHaptics(make: () -> Haptics): Haptics {
    val real by lazy(make)
    return object : Haptics {
        override fun tick() = real.tick()

        override fun click() = real.click()
    }
}

/** How a vibrator is driven, best first; [hapticTechnique] picks one from the Android version and what the motor supports. */
internal enum class HapticTechnique {
    /** `VibrationEffect.Composition` primitives: LOW_TICK / TICK for a move, CLICK for a confirm (Android 11+, motors that report them). */
    Primitives,

    /** `VibrationEffect.createPredefined(EFFECT_TICK / EFFECT_CLICK)` (Android 10+). */
    Predefined,

    /** A short one-shot at a low amplitude for a move, a longer and stronger one for a confirm (Android 8+). */
    OneShot,

    /** `vibrate(ms)` (Android 6 and 7, which have no amplitude). */
    Plain,
}

internal fun hapticTechnique(
    sdk: Int,
    primitivesSupported: Boolean,
): HapticTechnique =
    when {
        sdk >= Build.VERSION_CODES.R && primitivesSupported -> HapticTechnique.Primitives
        sdk >= Build.VERSION_CODES.Q -> HapticTechnique.Predefined
        sdk >= Build.VERSION_CODES.O -> HapticTechnique.OneShot
        else -> HapticTechnique.Plain
    }

/**
 * The device's vibrator. A device without one does nothing and says so once in the log; a failing
 * vibrator call is never fatal (input must go on) and is logged once too.
 */
internal class VibratorHaptics(
    context: Context,
    private val sdk: Int = Build.VERSION.SDK_INT,
) : Haptics {
    private val vibrator: Vibrator? = findVibrator(context.applicationContext)
    private val technique: HapticTechnique? = vibrator?.let { hapticTechnique(sdk, supportsPrimitives(it)) }

    /** Decided once here: the motor's capabilities do not change, so no per-tick support query. */
    private val hasLowTick: Boolean = vibrator != null && technique == HapticTechnique.Primitives && supportsLowTick(vibrator)

    /** One thread for every vibrator call, so a slow binder call never blocks input handling (like SoundPoolPlayer's thread). */
    private val thread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "plumemu-haptics").apply { isDaemon = true } }
    init {
        if (vibrator == null) Log.i(TAG, "Vibration does nothing: this device has no vibrator")
    }

    override fun tick() = vibrate(isClick = false)

    override fun click() = vibrate(isClick = true)

    private fun vibrate(isClick: Boolean) {
        val device = vibrator ?: return
        try {
            thread.execute { vibrateNow(device, isClick) }
        } catch (e: RuntimeException) {
            logFailureOnce(e)
        }
    }

    // NewApi: hapticTechnique(sdk, ...) picks the call by API level (tested in HapticsTest), so each branch only runs where it exists.
    @SuppressLint("NewApi")
    private fun vibrateNow(
        device: Vibrator,
        isClick: Boolean,
    ) {
        try {
            when (technique) {
                HapticTechnique.Primitives -> device.vibrate(primitive(isClick))
                HapticTechnique.Predefined ->
                    device.vibrate(VibrationEffect.createPredefined(if (isClick) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK))
                HapticTechnique.OneShot ->
                    device.vibrate(VibrationEffect.createOneShot(if (isClick) CLICK_MS else TICK_MS, if (isClick) CLICK_AMPLITUDE else TICK_AMPLITUDE))
                @Suppress("DEPRECATION") // vibrate(long) is the only call before API 26.
                HapticTechnique.Plain -> device.vibrate(if (isClick) CLICK_MS else TICK_MS)
                null -> Unit
            }
        } catch (e: RuntimeException) {
            logFailureOnce(e)
        }
    }

    @Volatile private var hasLoggedFailure = false

    private fun logFailureOnce(e: RuntimeException) {
        if (hasLoggedFailure) return
        hasLoggedFailure = true
        Log.w(TAG, "Vibration failed (${e.javaClass.simpleName}); further failures are not logged")
    }

    @SuppressLint("NewApi") // Only reached for HapticTechnique.Primitives (API 30+).
    private fun supportsLowTick(device: Vibrator): Boolean = device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

    @SuppressLint("NewApi") // Only reached for HapticTechnique.Primitives (API 30+).
    private fun primitive(isClick: Boolean): VibrationEffect {
        val id =
            when {
                isClick -> VibrationEffect.Composition.PRIMITIVE_CLICK
                hasLowTick -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                else -> VibrationEffect.Composition.PRIMITIVE_TICK
            }
        return VibrationEffect.startComposition().addPrimitive(id, if (isClick) CLICK_SCALE else TICK_SCALE).compose()
    }

    private companion object {
        const val TAG = "Haptics"
        const val TICK_MS = 10L
        const val CLICK_MS = 18L
        const val TICK_AMPLITUDE = 40
        const val CLICK_AMPLITUDE = 120
        const val TICK_SCALE = 0.5f
        const val CLICK_SCALE = 0.8f

        fun findVibrator(context: Context): Vibrator? {
            val found =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION") // VibratorManager exists only from API 31.
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            return found?.takeIf { it.hasVibrator() }
        }

        /** Needs every primitive it may play: CLICK, and a tick (LOW_TICK or TICK). */
        fun supportsPrimitives(device: Vibrator): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) &&
                (
                    device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) ||
                        device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
                )
    }
}
