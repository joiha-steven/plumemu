package com.plumemu

import com.plumemu.usage.LocalErrorTracker
import com.plumemu.usage.ControllerWatch
import com.plumemu.graph.trackSettingChanges
import com.plumemu.graph.trackViewChanged
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import com.plumemu.language.LocalizedActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.content.HomeState
import com.plumemu.battery.batteryPaceNow
import com.plumemu.battery.batteryPaceOf
import com.plumemu.content.homePlayFacts
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextMode
import com.plumemu.graph.AppGraph
import com.plumemu.graph.ContentShown
import com.plumemu.graph.StorageMounts
import com.plumemu.graph.GraphAppActions
import com.plumemu.graph.GraphAppIcons
import com.plumemu.graph.doAfterFirstDraw
import com.plumemu.graph.trackUpdateInstalled
import com.plumemu.usage.LocalScreenTracker
import com.plumemu.usage.UsageSession
import com.plumemu.usage.openFromOf
import com.plumemu.game.AppActions
import com.plumemu.game.AppGameActions
import com.plumemu.game.GameActions
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.CoverImages
import com.plumemu.home.HomeAppIcons
import com.plumemu.home.HomeArtSource
import com.plumemu.home.HomeCallbacks
import com.plumemu.graph.homeNotices
import com.plumemu.graph.afterReturn
import com.plumemu.home.HomeNotices
import com.plumemu.home.HomePlayFactsSource
import com.plumemu.launcher.play.battery.BatteryReads
import com.plumemu.secondscreen.AndroidDisplaySource
import com.plumemu.secondscreen.SecondScreenFeed
import com.plumemu.secondscreen.isWindowLikelyShown
import com.plumemu.secondscreen.SecondScreenHost
import com.plumemu.secondscreen.StrayFocusCatcher
import com.plumemu.secondscreen.SecondScreenPlayFacts
import com.plumemu.game.emulatorToStart
import com.plumemu.game.launchedFileName
import com.plumemu.secondscreen.cardResult
import com.plumemu.secondscreen.SecondScreenPresence
import com.plumemu.settings.followSettings
import com.plumemu.settings.versionLine
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.toInputSettings
import com.plumemu.settings.toSetting
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withGridView
import com.plumemu.setup.HomeRoleActions
import com.plumemu.sound.UiSounds
import com.plumemu.setup.OpenRomsFolder
import com.plumemu.setup.PickResult
import com.plumemu.setup.homeRoleIntent
import com.plumemu.setup.homeSettingsIntent
import com.plumemu.graph.addFolderFromHome
import com.plumemu.setup.isHomeApp as isPlumemuHomeApp
import com.plumemu.setup.romsInitialUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : LocalizedActivity(followsLanguage = true) {
    /**
     * Every key, joystick move and touch action goes through here; screens collect its actions.
     * Held directions repeat on the display's frames: the scope carries the Choreographer's frame
     * clock, and runs on the plain main dispatcher so each frame hands the main thread back.
     */
    internal val input: InputRouter by lazy {
        InputRouter(
            CoroutineScope(lifecycleScope.coroutineContext + Dispatchers.Main + AndroidUiFrameClock(Choreographer.getInstance())),
            ControlsSettings().toInputSettings(),
        )
    }

    private val graph: AppGraph get() = (application as PlumApplication).graph

    /** App opens, time in front, screens and input for the statistics; benchmark builds count nothing. */
    private val usage: UsageSession? get() = if (isBenchmarkBuild) null else graph.usage

    /** Benchmark builds show the fake content (board or 10,000 games) and do no startup work. */
    private val isBenchmarkBuild = BuildConfig.BENCHMARK_CONTENT

    /** Benchmarks measure the window on the emulator's overlay display, which is off by default. */
    private val isSecondScreenForced: Boolean
        get() = isBenchmarkBuild && intent?.getBooleanExtra(SecondScreenHost.EXTRA_BENCHMARK_SECOND_SCREEN, false) == true

    /**
     * Games play and have their menu, apps of the Apps entry open (`GraphAppActions`);
     * benchmark builds launch nothing. Settings is opened by MainScreen. Lazy: the application is
     * attached after construction. Internal for `MainActivityAppsTest`.
     */
    internal val homeCallbacks by lazy {
        HomeCallbacks(
            apps = if (isBenchmarkBuild) AppActions.None else GraphAppActions(graph),
            games = if (isBenchmarkBuild) GameActions.None else AppGameActions(graph, this),
            // MainScreen opens Settings instead; START in benchmark builds (no Settings) only logs.
            onSettings = { Log.i(TAG, "Settings: none in benchmark builds") },
            art = if (isBenchmarkBuild) HomeArtSource.None else graph.homeArt,
            playFacts = if (isBenchmarkBuild) HomePlayFactsSource.None else graph.homePlayFacts(),
            appIcons = if (isBenchmarkBuild) HomeAppIcons.None else GraphAppIcons(graph),
            // The battery line's figures again when the battery changes, under the details and in Info (benchmark builds: no line).
            batteryPaceNow = if (isBenchmarkBuild) null else graph.batteryPaceNow(),
            updateFound = if (isBenchmarkBuild) MutableStateFlow(false) else graph.updates.hasUpdate,
            onAddFolder = { if (!isBenchmarkBuild) graph.addFolderFromHome(settingsActivityActions) },
            notices = if (isBenchmarkBuild) HomeNotices() else graph.homeNotices(),
            // A box-art run asks the shown system's games first.
            onSystemShown = { systemId -> if (!isBenchmarkBuild) graph.viewedSystem.value = systemId },
            // L1 / R1 in a grid: the same setting and queue as Settings › Appearance › Grid size.
            onGridSize = { size, onFailed ->
                if (!isBenchmarkBuild) {
                    graph.settingsWriter.submitTold({ it.copy(appearance = it.appearance.copy(gridSize = size.toSetting())) }, onFailed, graph::trackSettingChanges)
                }
            },
            // SELECT: list or grid for one system, in the same queue; benchmark builds keep it in memory.
            onSearchUsed = { length, results, opened -> usage?.onSearchUsed(length, results, opened) },
            onGridView = { systemId, isGrid, onFailed ->
                if (!isBenchmarkBuild) {
                    graph.settingsWriter.submitTold({ it.withGridView(systemId, isGrid) }, onFailed) { _, after -> graph.trackViewChanged(systemId, isGrid, after) }
                }
            },
        )
    }

    /** Interface clicks; the sound pool exists only between onStart and onStop. */
    private val sounds = UiSounds.onDevice { applicationContext }

    private val contentShown = ContentShown(runsStartupWork = !isBenchmarkBuild)

    /** Storage mounted while started (an SD card put in): a change check of the folders. */
    private val storageMounts by lazy { StorageMounts(this) { graph.startup.onStorageMounted() } }

    /** The first content frame of this Activity has asked for the second screen's listener. */
    private var secondScreensRequested = false

    /** This Activity started the second screen's listener, so its onDestroy stops it (starts are counted). */
    private var hasStartedSecondScreens = false

    /**
     * What the second screen's window shows; the main screen's effects tell it (only while a window
     * is up). Play facts come from the play history, read off the main thread after the settle.
     */
    private val secondScreenFeed: SecondScreenFeed by lazy {
        SecondScreenFeed(
            lifecycleScope,
            facts =
                SecondScreenPlayFacts(
                    plays = { graph.database.playDao() },
                    // Benchmark builds show no art (as the home screen's).
                    resultOf = if (isBenchmarkBuild) null else graph.homeArt::cardResult,
                    // The emulator A would start, named on the played line (benchmark builds: none).
                    emulatorOf = if (isBenchmarkBuild) null else { id -> graph.emulatorToStart(id) },
                    // The file Play launches (a chosen version's), as Info's "File" row (benchmark builds: no file line).
                    fileNameOf = if (isBenchmarkBuild) null else { id -> graph.launchedFileName(id) },
                    // The game's usual battery pace for the line under the played line (benchmark builds: none).
                    paceOf = if (isBenchmarkBuild) null else { id -> graph.batteryPaceOf(id) },
                    isArtShown = { secondScreenFeed.hasArtRoom },
                )::fill,
        )
    }

    /**
     * Whether a stop is for a game Plumemu started (the second screen shows the In game setting) or
     * Plumemu was left (its window goes). The launcher tells it through `AppGraph.gameStarts`
     * right before it starts an emulator or an app of the Apps entry (`InstalledApps.launch`), and
     * when that start threw.
     */
    private val secondScreenPresence = SecondScreenPresence()

    /** The second screen's window, made after the first content frame with the listener, gone in onDestroy. */
    private var secondScreenHost: SecondScreenHost? = null

    /** Gives the controller back to this screen when the bottom display kept it (the AYN panel on the Thor). */
    internal val strayFocus = StrayFocusCatcher(this, isResumed = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) })

    /** The cached background, read in onCreate: the second screen's window paints it before its first frame. */
    private var cachedBackgroundArgb = 0

    /**
     * Who opened the folder picker: Settings › Library, or first-run setup. Kept across a recreated
     * Activity (the picker may outlive it), so the answer reaches the screen that asked.
     */
    private var pickerForSettings = false

    /** Android's folder picker; its answer goes to the process's setup model or to Settings. */
    private val folderPicker =
        registerForActivityResult(OpenRomsFolder()) { tree ->
            if (pickerForSettings) {
                graph.settingsLibrary.onPicked(tree?.toString())
            } else {
                graph.setup.onPicked(if (tree == null) PickResult.Cancelled else PickResult.Picked(tree.toString()))
            }
        }

    /** Settings › Advanced › Back up…'s save picker; its answer starts the backup in the graph. */
    private val backupPicker = BackupPicker(this) { graph }

    /** Settings › Advanced › Restore…'s open picker; its answer starts the reading in the graph. */
    private val restorePicker = RestorePicker(this) { graph }

    /** Android's own home-app screen; Plumemu does nothing with its answer (Android applies it). */
    private val homeRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i(TAG, "Home app screen closed (result ${result.resultCode})")
        }

    private val homeRoleActions =
        object : HomeRoleActions {
            override fun isHomeApp(): Boolean = isPlumemuHomeApp(this@MainActivity)

            override fun requestHomeRole(): Boolean {
                val intent = homeRoleIntent(this@MainActivity) ?: return false
                return try {
                    homeRoleRequest.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "Home app screen could not open (${e.javaClass.simpleName})")
                    false
                }
            }

            // In the process's scope: the answer is stored even if the Activity goes away meanwhile.
            override fun markAsked() {
                graph.appScope.launch { graph.settingsStore.markHomeRoleAsked() }
            }
        }

    /** Settings' picker and home-app screens. */
    private val settingsActivityActions =
        object : SettingsActivityActions {
            override fun openFolderPicker(): Boolean =
                try {
                    pickerForSettings = true
                    folderPicker.launch(romsInitialUri())
                    true
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "No folder picker on this device (${e.javaClass.simpleName})")
                    false
                }

            override fun isHomeApp(): Boolean = isPlumemuHomeApp(this@MainActivity)

            override fun openBackupPicker(kind: BackupKind): Boolean = backupPicker.open(kind)

            override fun openRestorePicker(): Boolean = restorePicker.open()

            override fun exportLogs(onDone: (Boolean) -> Unit) = shareLogFile(onDone)

            override fun openHomeSettings(): Boolean {
                // Already the home app: Android's home settings, to switch back (the role request
                // would answer at once and show nothing).
                val intent = if (isHomeApp()) homeSettingsIntent(this@MainActivity) else homeRoleIntent(this@MainActivity)
                if (intent == null) return false
                return try {
                    homeRoleRequest.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "Home app screen could not open (${e.javaClass.simpleName})")
                    false
                }
            }
        }

    /** Settings › …; benchmark builds have none (their START only logs). */
    private val settingsHost: SettingsHost? by lazy {
        if (isBenchmarkBuild) null else graph.settingsHost(versionLine(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()), settingsActivityActions)
    }

    private val setupHost: SetupHost? =
        if (isBenchmarkBuild) null else SetupHost({ graph.setup }, ::openFolderPicker, homeRoleActions)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph.gameStarts.target = secondScreenPresence
        usage?.onActivityCreated(openFromOf(intent), isRecreated = savedInstanceState != null)
        pickerForSettings = savedInstanceState?.getBoolean(STATE_PICKER_FOR_SETTINGS) == true
        backupPicker.restore(savedInstanceState)
        // From Android 13 the Back key and the back gesture may skip dispatchKeyEvent and arrive
        // here instead. Always enabled: Back on the home screen never leaves it.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = input.send(Action.Back)
            },
        )
        // The settings arrive from DataStore shortly after the first frame; until then the window
        // paints the person's background (and the bars their text mode) from a tiny synchronous
        // cache, so neither a cold start nor a recreated Activity flashes another colour.
        val cached = graph.appearanceCache.read()
        cachedBackgroundArgb = cached.backgroundArgb
        window.setBackgroundDrawable(ColorDrawable(cached.backgroundArgb))
        applySystemBarStyle(cached.textMode)
        // Layout C: the window comes after the first frame, so the top screen starts from a guess (cached switch, listed displays); the host corrects it.
        secondScreenFeed.setWindowShown(
            isWindowLikelyShown(graph.secondScreenSwitchCache.read(), AndroidDisplaySource(this).presentationDisplays(), isSecondScreenForced),
        )
        hideSystemBars()
        val settingsFlow = graph.settings
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { input.followSettings(settingsFlow.filterNotNull()) } }
        val homeStates = homeStates()
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            val textColor = settings?.toThemeSettings()?.textMode // drawn mode: a background change can flip it with the stored text unchanged
            LaunchedEffect(textColor) { settings?.let { applySystemBarStyle(it.toThemeSettings().textMode) } }
            val showsSetup = setupHost != null && settings?.flags?.setupCompleted == false
            // The library is read only once setup is done; setup has its own counts.
            val state = if (settings != null && !showsSetup) homeStates.collectAsStateWithLifecycle().value else null
            if (showsSetup) {
                LaunchedEffect(Unit) { onContentComposed(isLibrary = false) }
            } else if (settings != null && state != null) {
                val isLibrary = state is HomeState.Ready
                LaunchedEffect(isLibrary) { onContentComposed(isLibrary) }
            }
            CompositionLocalProvider(LocalScreenTracker provides { usage?.onScreen(it) }, LocalErrorTracker provides { where, code -> usage?.onError(where, code) }, com.plumemu.coreui.components.LocalHeldKeys provides input.held) { // held: pressed hint caps
                MainScreen(
                settings,
                state,
                input,
                homeCallbacks,
                onRetry = graph::retryHomeContent,
                setup = setupHost,
                settingsHost = settingsHost,
                sounds = sounds,
                secondScreen = secondScreenFeed,
                homePresses = homePresses,
                viewedSystem = graph.viewedSystemAccent(),
                waitingAppearance = cached,
                )
            }
        }
    }

    /** `controller_connected` while Plumemu is in front (benchmark builds: none). */
    private val controllers by lazy { ControllerWatch(this, graph.usage::isNewController, graph.usage::onController) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The cold start's app_open carries the time from the process start to this first frame.
        window.decorView.doAfterFirstDraw {
            usage?.onFirstFrame()
            if (!isBenchmarkBuild) graph.trackUpdateInstalled()
        }
    }

    /** Home pressed while Plumemu is in front (see [isHomePressInFront]); the main screen collects it. */
    internal val homePresses = HomePresses()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        usage?.openedBy(openFromOf(intent))
        if (isHomePressInFront(intent, hasWindowFocus())) homePresses.press()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PICKER_FOR_SETTINGS, pickerForSettings)
        backupPicker.save(outState)
    }

    private fun openFolderPicker() {
        pickerForSettings = false
        try {
            folderPicker.launch(romsInitialUri())
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No folder picker on this device (${e.javaClass.simpleName})")
            graph.setup.onPicked(PickResult.NoPicker)
        }
    }

    private fun homeStates(): StateFlow<HomeState?> =
        if (isBenchmarkBuild) {
            val large = intent?.getBooleanExtra(FakeHomeContent.EXTRA_LARGE_LIBRARY, false) == true
            MutableStateFlow(HomeState.Ready(FakeHomeContent.forLaunch(isBenchmarkBuild = true, wantsLargeLibrary = large)))
        } else {
            graph.homeContent
        }

    /**
     * The first frame with real content is what the person waits for: report it as fully drawn
     * (Macrobenchmark's time to full display) and only then start the startup work.
     */
    private fun onContentComposed(isLibrary: Boolean) {
        val actions = contentShown.onShown(isLibrary)
        // Setup asks about the second screen too, so this one is not tied to the library's startup work.
        val startsSecondScreens = !secondScreensRequested
        if (actions.isEmpty && !startsSecondScreens) return
        secondScreensRequested = true
        window.decorView.doAfterFirstDraw {
            if (actions.reportFullyDrawn) reportFullyDrawn()
            if (actions.startStartupWork) graph.startup.onContentShown()
            if (startsSecondScreens) startSecondScreens()
        }
        // The composition may have been drawn already; ask for one more frame so the listener fires.
        window.decorView.invalidate()
    }

    /**
     * Listens for the second screen until onDestroy (main thread); Settings and setup read it. Its
     * window follows from here too: made after the first frame, never in onCreate.
     */
    private fun startSecondScreens() {
        // The posted step may run after the Activity was finished: nothing would stop the listener then.
        if (lifecycle.currentState == Lifecycle.State.DESTROYED || hasStartedSecondScreens) return
        graph.secondScreens.startAndDecideSystemInfo(lifecycleScope, graph.settings, graph.settingsWriter::submit)
        hasStartedSecondScreens = true
        secondScreenHost =
            SecondScreenHost(
                context = this,
                scope = lifecycleScope,
                screens = graph.secondScreens.current,
                settings = graph.settings,
                feed = secondScreenFeed,
                viewedSystem = graph.viewedSystemAccent(),
                presence = secondScreenPresence.current,
                backgroundArgb = cachedBackgroundArgb,
                // Benchmarks measure the window on the emulator's overlay display, which is off by default.
                forcedOn = isSecondScreenForced,
                images = if (isBenchmarkBuild) CoverImages.None else graph.homeArt.images,
                // Benchmarks measure the window's own cost: no battery reads there.
                battery = if (isBenchmarkBuild) BatteryReads.None else graph.playBattery,
                // The picking line's figures again when the battery changes (benchmark builds: no line).
                paceNow = if (isBenchmarkBuild) null else graph.batteryPaceNow(),
                onDriftNoteShown = { graph.settingsWriter.submit { it.copy(flags = it.flags.copy(inGameDriftNoteShown = true)) } },
                onStrayFocus = strayFocus::takeControllerBack,
            ).also {
                strayFocus.host = it
                it.start()
            }
    }

    override fun onStart() {
        super.onStart()
        secondScreenPresence.onStart()
        usage?.run { onStart().also { controllers.start() } }
        // Also in benchmark builds, so start-up and frame measurements include the sounds' cost.
        sounds.start()
        if (isBenchmarkBuild) return
        graph.packageChanges.onStart()
        storageMounts.onStart()
        // Also checks the ROM folders for games copied while Plumemu was in the background.
        graph.startup.onStart()
    }

    override fun onResume() {
        super.onResume()
        secondScreenPresence.onResume()
        usage?.onResume()
        strayFocus.onResume()
        if (isBenchmarkBuild) return
        // Adds the time of a game started from here, once the person is back.
        graph.afterReturn()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        strayFocus.onTopResumedChanged(isTopResumedActivity)
    }

    override fun onPause() {
        super.onPause()
        strayFocus.onPause()
    }

    private fun applySystemBarStyle(textMode: TextMode) {
        val barStyle = systemBarStyleFor(textMode)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
    }

    // Lint false positive: this overrides the public android.app.Activity method, which
    // androidx.core's ComponentActivity redeclares with @RestrictTo; lint then flags every override.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        usage?.onGamepadInput()
        return input.onKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        usage?.onGamepadInput()
        return input.onGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        usage?.onTouchInput()
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A key-up that happens while another window has focus never reaches us.
        if (!hasFocus) input.releaseAll()
        // A dialog, the notification shade or another app may have brought the bars back.
        if (hasFocus) hideSystemBars()
    }

    /**
     * The main screen has no system bars (the board draws none). Immersive: a swipe from the edge
     * shows them for a moment, then they hide again. Works from API 23 (androidx falls back to
     * the system UI visibility flags there).
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        super.onStop()
        secondScreenPresence.onStop()
        usage?.run { onStop(isChangingConfigurations).also { controllers.stop() } }
        input.releaseAll()
        sounds.stop()
        if (!isBenchmarkBuild) {
            graph.packageChanges.onStop()
            storageMounts.onStop()
            graph.startup.onStop()
        }
    }

    override fun onDestroy() {
        // Before super: a window left up would leak the Activity (and sit on that screen with nothing behind it).
        secondScreenHost?.stop()
        secondScreenHost = null
        strayFocus.host = null
        graph.gameStarts.release(secondScreenPresence)
        if (hasStartedSecondScreens) graph.secondScreens.stop()
        hasStartedSecondScreens = false
        super.onDestroy()
    }
}

/**
 * Bar icons follow the text colour: light text gets light icons. Passed explicitly because the
 * no-argument form follows the system's dark mode instead. With dark
 * text, Android 6.0–7.1 can only draw white navigation buttons and androidx paints the bar with
 * the second (dark) scrim; a transparent one would leave white buttons on a light background.
 */
private fun systemBarStyleFor(textMode: TextMode): SystemBarStyle =
    when (textMode) {
        TextMode.Light -> SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        TextMode.Dark ->
            SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = PlumThemeSettings.Default.legacyNavigationBarScrim.toArgb(),
            )
    }

private const val TAG = "MainActivity"
private const val STATE_PICKER_FOR_SETTINGS = "picker_for_settings"
