package com.plumemu.secondscreen

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.plumemu.coreui.theme.InGameColors
import com.plumemu.PlumApplication
import com.plumemu.language.AppLocale
import com.plumemu.language.LocalizedActivity
import kotlinx.coroutines.launch

/**
 * The second screen's card: Plumemu's own activity on the second display, and that display's home
 * while Plumemu is the default home (the `SecondScreenHome` alias, [SecondScreenHome]). An app
 * opened on the bottom screen sits above it; when that app closes or Home is pressed there,
 * Android brings the card back.
 *
 * It draws what the host that holds it shows ([SecondScreenHost.cardHolder], one per process), so
 * a card Android makes (Home, a boot) shows the right content; with no host it draws nothing of
 * Plumemu's (black). It takes no touch and no key does anything on it: a key it receives
 * before the controller is given back goes to the top screen ([ControllerHandBack]).
 *
 * Launch mode `standard` (manifest): AOSP refuses a `singleTask` or `singleInstance` secondary home
 * and falls back to its own. So instances can pile up; [CardInstances] keeps one.
 */
class SecondScreenCardActivity : LocalizedActivity(followsLanguage = false) {
    private val handBack = ControllerHandBack(displayId = ::displayIdNow)

    /** The host whose content is composed now; null: blank. */
    private var composedFor: SecondScreenHost? = null

    /** Set when a newer card replaced this one: it goes quietly. */
    private var replaced = false

    internal fun displayIdNow(): Int = windowDisplay()?.displayId ?: Display.INVALID_DISPLAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = displayIdNow()
        if (id == Display.DEFAULT_DISPLAY) {
            // Android put it on the top screen (the bottom display refused or went): never cover the person's screen.
            Log.w(SecondScreenHost.TAG, "Second screen card: opened on the top screen; closed")
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val secondScreen = (application as PlumApplication).graph.secondScreens.current.value?.displayId
        if (!CardInstances.isSecondScreen(id, secondScreen)) {
            // Another display with system decorations: Android may make the secondary home on each one while the alias is
            // on. It stays blank, registers nothing and closes nothing, so it can never replace the bottom screen's card.
            Log.i(SecondScreenHost.TAG, "Second screen card: display $id is not the second screen; blank")
            CardInstances.stray(this)
            compose(null)
            return
        }
        Log.i(SecondScreenHost.TAG, "Second screen card: on display $id")
        CardInstances.created(this)
        lifecycleScope.launch { SecondScreenHost.cardHolder.collect(::compose) }
        // Always focusable, also with a game in front: Android makes the card the focused display's top when an app above
        // it closes, and a non-focusable activity there waits for a focus that never comes (an ANR). A tap on it during a
        // game takes the controller, which stays there (ControllerHandBack never moves Plumemu over a game).
    }

    /** Draws [host]'s content, or nothing of Plumemu's; a new host (a recreated `MainActivity`) builds it again, in the language now stored. */
    private fun compose(host: SecondScreenHost?) {
        if (host === composedFor && window.decorView.isAttachedToWindow) return
        composedFor = host
        val paint = host?.firstPaint() ?: InGameColors.background.toArgb()
        window.setBackgroundDrawable(ColorDrawable(paint))
        if (host == null) {
            setContentView(View(this).apply { setBackgroundColor(paint) })
            return
        }
        val display = window.decorView.display ?: windowDisplay()
        val scale = display?.let { physicalTextScaleOf(this, it) } ?: 1f
        // The stored language again: a card kept across a language change was made in the old one (Android 12 and older).
        val view =
            ComposeView(AppLocale.localized(this)).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                setContent { WithPhysicalTextScale(scale) { host.CardContent() } }
            }
        setContentView(view)
    }

    /** This activity's display; `getDisplay` throws for a context with none, never expected of an activity, so the window manager's then. */
    @Suppress("DEPRECATION")
    private fun windowDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                display
            } catch (e: UnsupportedOperationException) {
                windowManager.defaultDisplay
            }
        } else {
            windowManager.defaultDisplay
        }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Android 10+, the only versions the card is started on ([SecondScreenHost]). A blank card on another display hands nothing back. */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (CardInstances.live === this) handBack.onCardTop(isTopResumedActivity)
    }

    internal val isFocusableNow: Boolean get() = window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0

    internal val holdsController: Boolean get() = handBack.holds

    // Lint false positive: this overrides the public android.app.Activity method, which
    // androidx.core's ComponentActivity redeclares with @RestrictTo; lint then flags every override.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = handBack.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = handBack.onMotion(event)

    /** Nothing on the bottom screen reacts to a tap. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = true

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** A newer card was made ([CardInstances.created]): this one ends. */
    internal fun finishAsOlder() {
        replaced = true
        Log.i(SecondScreenHost.TAG, "Second screen card: a newer one is up; this one closes")
        finish()
    }

    override fun onDestroy() {
        handBack.stop()
        CardInstances.destroyed(this, isChangingConfigurations || replaced)
        super.onDestroy()
    }
}
