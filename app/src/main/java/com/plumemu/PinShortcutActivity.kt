package com.plumemu

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import com.plumemu.language.LocalizedActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.LocalConfirmCap
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.graph.AppGraph
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.followSettings
import com.plumemu.settings.toInputSettings
import com.plumemu.shortcuts.PinAddOutcome
import com.plumemu.shortcuts.PinConfirmActions
import com.plumemu.shortcuts.PinConfirmScreen
import com.plumemu.shortcuts.PinConfirmModel
import com.plumemu.shortcuts.PinOffer
import com.plumemu.sound.LocalUiSounds
import com.plumemu.sound.UiSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Plumemu's answer to another app's "Add to home screen": Android starts it with `CONFIRM_PIN_SHORTCUT` while Plumemu
 * is the default home app (Android 8.0 and later). It asks "Add <game> to <list>?" in Plumemu's
 * look, over the app that asked, and closes when answered. The add itself runs in the app's scope
 * (`PinnedShortcuts.add`), so closing early never leaves a pin without its game.
 */
open class PinShortcutActivity : LocalizedActivity(followsLanguage = false) {
    private val graph: AppGraph get() = (application as PlumApplication).graph

    private val input: InputRouter by lazy {
        InputRouter(
            CoroutineScope(lifecycleScope.coroutineContext + Dispatchers.Main + AndroidUiFrameClock(Choreographer.getInstance())),
            ControlsSettings().toInputSettings(),
        )
    }

    private val sounds = UiSounds.onDevice { applicationContext }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = input.send(Action.Back)
            },
        )
        // The model outlives a recreated Activity: the request is read once, a running add is
        // answered on the new Activity (PinConfirmModel).
        val request = intent
        val state = ViewModelProvider(this, PinConfirmModel.Factory { pinActions(request) })[PinConfirmModel::class.java].state
        lifecycleScope.launch { state.closedFlow.first { it }.let { finish() } }
        val settingsFlow = graph.settings
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { input.followSettings(settingsFlow.filterNotNull()) } }
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            val stored = settings ?: return@setContent
            SideEffect { sounds.follow(stored.advanced) }
            val letters = ButtonLetters.of(stored.controls.confirmButton)
            SettingsTheme(stored) {
                CompositionLocalProvider(LocalButtonLetters provides letters, LocalConfirmCap provides stringResource(letters.confirm), LocalUiSounds provides sounds, LocalHeldKeys provides input.held) {
                    PinConfirmScreen(state, input, letters)
                }
            }
        }
    }

    /** What the screen asks and adds through: the graph's [com.plumemu.shortcuts.PinnedShortcuts]; tests give their own. */
    internal open fun pinActions(request: Intent): PinConfirmActions {
        val shortcuts = graph.pinnedShortcuts
        return object : PinConfirmActions {
            override suspend fun offer(): PinOffer = shortcuts.offerOf(request)

            override suspend fun add(offer: PinOffer.Ask): PinAddOutcome = shortcuts.add(offer)
        }
    }

    override fun onStart() {
        super.onStart()
        sounds.start()
    }

    // Lint false positive, as in MainActivity: ComponentActivity redeclares this with @RestrictTo.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = input.onKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        input.onGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) input.releaseAll()
    }

    override fun onStop() {
        super.onStop()
        input.releaseAll()
        sounds.stop()
    }
}
