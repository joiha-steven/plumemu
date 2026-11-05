package com.plumemu.graph

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import com.plumemu.setup.DeviceFacts
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * What setup asks of the device: whether a gamepad is connected (the confirm step exists then) and
 * what it is (the look step's suggestion). Android-only reads, so nothing here is unit tested; the
 * decisions are pure functions in setup/SetupLook.kt.
 */

/** True while an input device with gamepad or joystick buttons is connected (a handheld's own buttons count). */
internal fun gamepadPresence(context: Context): Flow<Boolean> =
    callbackFlow {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val read = {
            val found = manager.inputDeviceIds.any { id -> manager.getInputDevice(id)?.let(::isGamepad) == true }
            trySend(found)
            Unit
        }
        val listener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    read()
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    read()
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    read()
                }
            }
        manager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        read()
        awaitClose { manager.unregisterInputDeviceListener(listener) }
    }.conflate().distinctUntilChanged()

private fun isGamepad(device: InputDevice): Boolean =
    device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

internal fun deviceFactsOf(context: Context): DeviceFacts {
    val metrics = context.resources.displayMetrics
    return DeviceFacts(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        widthDp = metrics.widthPixels / metrics.density,
        heightDp = metrics.heightPixels / metrics.density,
    )
}
