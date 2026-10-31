package com.plumemu.secondscreen

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import java.lang.reflect.Method

/**
 * [DisplaySource] over Android's `DisplayManager`: the presentation displays, each with its hidden
 * type, and a listener on the main thread. A failure listing the displays or registering the
 * listener is logged by class and reads as no second screen, never thrown: Plumemu may be the home
 * screen, and a crash there loops.
 */
internal class AndroidDisplaySource(
    private val service: DisplayService?,
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) : DisplaySource {
    constructor(
        context: Context,
        warn: (String) -> Unit = { Log.w(TAG, it) },
    ) : this(context.applicationContext.getSystemService(DisplayManager::class.java)?.let(::AndroidDisplayService), warn)

    private val handler = Handler(Looper.getMainLooper())
    private val types = HiddenDisplayType(warn)
    private var listener: DisplayManager.DisplayListener? = null

    override fun presentationDisplays(): List<DisplayFacts> =
        try {
            service?.presentationDisplays().orEmpty().map { display ->
                DisplayFacts(
                    id = display.displayId,
                    name = display.name.orEmpty(),
                    isPresentation = display.flags and Display.FLAG_PRESENTATION != 0,
                    type = types.of(display),
                )
            }
        } catch (e: RuntimeException) {
            warn("Displays could not be listed (${e.javaClass.simpleName}); no second screen")
            emptyList()
        }

    override fun register(onChange: () -> Unit): Boolean {
        val service = service ?: return false.also { warn("No display service; no second screen") }
        val next =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = onChange()

                override fun onDisplayRemoved(displayId: Int) = onChange()

                override fun onDisplayChanged(displayId: Int) = onChange()
            }
        unregister()
        return try {
            service.registerListener(next, handler)
            listener = next
            true
        } catch (e: RuntimeException) {
            warn("Display listener could not be registered (${e.javaClass.simpleName}); no second screen")
            false
        }
    }

    override fun unregister() {
        val current = listener ?: return
        listener = null
        try {
            service?.unregisterListener(current)
        } catch (e: RuntimeException) {
            warn("Display listener could not be removed (${e.javaClass.simpleName})")
        }
    }

    private companion object {
        const val TAG = "PlumSecondScreen"
    }
}

/** The three `DisplayManager` calls Plumemu makes; a fake in tests can fail them. */
internal interface DisplayService {
    fun presentationDisplays(): Array<Display>

    fun registerListener(
        listener: DisplayManager.DisplayListener,
        handler: Handler,
    )

    fun unregisterListener(listener: DisplayManager.DisplayListener)
}

internal class AndroidDisplayService(private val manager: DisplayManager) : DisplayService {
    override fun presentationDisplays(): Array<Display> = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

    override fun registerListener(
        listener: DisplayManager.DisplayListener,
        handler: Handler,
    ) = manager.registerDisplayListener(listener, handler)

    override fun unregisterListener(listener: DisplayManager.DisplayListener) = manager.unregisterDisplayListener(listener)
}

/**
 * The hidden `Display.getType()`, by reflection: it tells the Thor's built-in screen from HDMI. Android may block hidden calls; then every display
 * reads null (Unsure) and one warning is logged, not one per read.
 */
internal class HiddenDisplayType(
    private val warn: (String) -> Unit,
    private val lookUp: () -> Method = { Display::class.java.getMethod("getType") },
) {
    private var method: Method? = null
    private var hasFailed = false

    fun of(display: Display): Int? = read { method().invoke(display) }

    /** [call]'s answer as a type, or null (and one warning) when it throws or answers something else. */
    fun read(call: () -> Any?): Int? {
        if (hasFailed) return null
        return try {
            call() as? Int ?: fail("not a number")
        } catch (e: Throwable) {
            // Throwable on purpose: a blocked hidden call can surface as an Error (NoSuchMethodError,
            // LinkageError) as well as an exception, and either must read as "unknown".
            fail(e.javaClass.simpleName)
        }
    }

    private fun method(): Method = method ?: lookUp().also { method = it }

    private fun fail(why: String): Int? {
        hasFailed = true
        warn("Display type could not be read ($why); second screens count as unsure")
        return null
    }
}
