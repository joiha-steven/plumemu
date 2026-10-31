package com.plumemu.usage

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import com.plumemu.telemetry.ControllerKind

/**
 * `controller_connected`: each gamepad present when Plumemu comes to the front, and each one plugged
 * in while it is in front, once per process per device ([UsageSession.isNewController]). Listens only
 * between [start] and [stop] (the Activity's start and stop); reads the device's vendor id and
 * whether it is built in, never its name.
 */
internal class ControllerWatch(
    context: Context,
    private val isNew: (descriptor: String) -> Boolean,
    private val onConnected: (ControllerKind) -> Unit,
) {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

    private val listener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = report(deviceId)

            override fun onInputDeviceRemoved(deviceId: Int) = Unit

            override fun onInputDeviceChanged(deviceId: Int) = Unit
        }

    fun start() {
        val manager = inputManager ?: return
        manager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        manager.inputDeviceIds.forEach(::report)
    }

    fun stop() {
        inputManager?.unregisterInputDeviceListener(listener)
    }

    private fun report(deviceId: Int) {
        val device = inputManager?.getInputDevice(deviceId) ?: return
        if (!device.isGamepad()) return
        if (!isNew(device.descriptor ?: deviceId.toString())) return
        val isExternal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) device.isExternal else true
        onConnected(controllerKindOf(device.vendorId, isExternal))
    }
}

private fun InputDevice.isGamepad(): Boolean =
    sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

/**
 * The kind of a gamepad by its USB vendor id; a built-in one ([isExternal] false, known from
 * Android 10) is `built_in`. Android 6 to 9 cannot tell, so a handheld's own buttons count by vendor there.
 */
internal fun controllerKindOf(
    vendorId: Int,
    isExternal: Boolean,
): ControllerKind =
    when {
        !isExternal -> ControllerKind.BuiltIn
        vendorId == VENDOR_MICROSOFT -> ControllerKind.Xbox
        vendorId == VENDOR_SONY -> ControllerKind.PlayStation
        vendorId == VENDOR_NINTENDO -> ControllerKind.Nintendo
        else -> ControllerKind.Other
    }

private const val VENDOR_MICROSOFT = 0x045E
private const val VENDOR_SONY = 0x054C
private const val VENDOR_NINTENDO = 0x057E
