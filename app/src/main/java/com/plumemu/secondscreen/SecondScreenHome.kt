package com.plumemu.secondscreen

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.plumemu.setup.isHomeApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whether the card is Android's home on the second display: the manifest's `SecondScreenHome`
 * alias (the card with `MAIN` + `SECONDARY_HOME` + `DEFAULT`, disabled in the manifest) is enabled
 * only while Plumemu is the default home app and a second screen is found and switched on.
 * Otherwise Android's own secondary home is used, as without the card, and nothing of Plumemu's
 * registers as a home.
 *
 * Asks are answered in order on [io] (the home check and the write are binder calls); Android's
 * setting is written only when it changes, always with `DONT_KILL_APP` (toggling must never kill
 * or restart Plumemu). One per process ([of]).
 */
internal class SecondScreenHome(
    scope: CoroutineScope,
    private val isDefaultHome: () -> Boolean,
    private val readEnabled: () -> Boolean,
    private val writeEnabled: (Boolean) -> Unit,
    io: CoroutineDispatcher = Dispatchers.IO,
    /** On the main thread after the alias was disabled: a card no host shows is ended there. */
    private val afterDisabled: () -> Unit = {},
    private val main: CoroutineDispatcher = Dispatchers.Main,
    private val info: (String) -> Unit = { Log.i(SecondScreenHost.TAG, it) },
    private val warn: (String) -> Unit = { Log.w(SecondScreenHost.TAG, it) },
) {
    private val asks = Channel<Boolean>(Channel.CONFLATED)

    /** What Android has, once read; written only from [io]. */
    @Volatile
    private var enabled: Boolean? = null

    /** Whether the card is the bottom display's home now (last known). */
    val isOn: Boolean get() = enabled == true

    init {
        scope.launch(io) { for (screenOn in asks) apply(screenOn) }
    }

    /** The second screen is found and switched on ([screenOn]), or not; the default home is asked again each time. */
    fun update(screenOn: Boolean) {
        asks.trySend(screenOn)
    }

    private suspend fun apply(screenOn: Boolean) {
        val want =
            try {
                // Not asked on a device without a second screen: no binder call there.
                screenOn && isDefaultHome()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                warn("Second screen card: could not tell whether Plumemu is the home app (${e.javaClass.simpleName})")
                false
            }
        try {
            val now = enabled ?: readEnabled().also { enabled = it }
            if (now == want) return
            writeEnabled(want)
            enabled = want
            info("Second screen card: ${if (want) "now the bottom screen's home" else "no longer the bottom screen's home"}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            warn("Second screen card: could not ${if (want) "enable" else "disable"} the bottom screen's home (${e.javaClass.simpleName})")
            return
        }
        if (!want) withContext(main) { afterDisabled() }
    }

    companion object {
        /** The alias in the manifest; its target is [SecondScreenCardActivity]. */
        const val ALIAS = "com.plumemu.secondscreen.SecondScreenHome"

        private var process: Pair<Context, SecondScreenHome>? = null

        /** The one of this process (main thread); tests get one per application. */
        fun of(context: Context): SecondScreenHome {
            val app = context.applicationContext
            process?.let { (madeFor, home) -> if (madeFor === app) return home }
            return run {
                val alias = ComponentName(app.packageName, ALIAS)
                val packages = app.packageManager
                SecondScreenHome(
                    CoroutineScope(SupervisorJob()),
                    isDefaultHome = { isHomeApp(app) },
                    readEnabled = { packages.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED },
                    writeEnabled = { on -> writeAlias(packages, alias, on) },
                    afterDisabled = ::endUnshownCard,
                ).also { process = app to it }
            }
        }

        /** Enables or disables the alias; `DONT_KILL_APP` always. */
        fun writeAlias(
            packages: PackageManager,
            alias: ComponentName,
            on: Boolean,
        ) {
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            packages.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
        }

        /**
         * Android may have made the card as the bottom display's home while the alias was still on
         * (a finish just before the write): with no host showing it, it is ended now.
         */
        fun endUnshownCard() {
            CardInstances.finishStrays()
            if (SecondScreenHost.cardHolder.value != null) return
            if (CardInstances.finishLive() != null) Log.i(SecondScreenHost.TAG, "Second screen card: closed, Android's own bottom-screen home is back")
        }
    }
}
