plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    // Applied before the finalizeDsl block below, so the build types it creates exist there.
    alias(libs.plugins.androidx.baselineprofile)
}

/*
 * Where the app checks for updates and sends usage statistics and problem reports. A build from source
 * has neither: both stay empty, and the app then shows no statistics or update rows, asks nothing about
 * them in setup and never connects. The release machine names them in ~/.gradle/gradle.properties, like the release key:
 *   plumemu.updateUrl=<URL of latest.json>
 *   plumemu.statsEndpoint=<base URL of the statistics server>
 * Debug builds fall back to the published addresses below so local work is unchanged; -P on the
 * command line overrides the file for any build.
 */
val PUBLISHED_UPDATE_MANIFEST_URL = "https://plumemu-app.stevensdesk.com/update/latest.json"
val PUBLISHED_STATS_ENDPOINT = "https://plumemu.stevensdesk.com"

val configuredUpdateUrl = providers.gradleProperty("plumemu.updateUrl").getOrElse("").trim()
val configuredStatsEndpoint = providers.gradleProperty("plumemu.statsEndpoint").getOrElse("").trim()

/**
 * -Pplumemu.statsEndpoint=http://10.0.2.2:8787 points a debug build at a local `wrangler dev`
 * (the emulator reaches the Mac at 10.0.2.2). Only then may that build use plain http; the
 * telemetry module still sends plain http only to 10.0.2.2 and localhost.
 */
val debugStatsEndpoint = configuredStatsEndpoint.ifEmpty { PUBLISHED_STATS_ENDPOINT }
val debugStatsCleartext = debugStatsEndpoint.startsWith("http://")
// -Pplumemu.updateUrl=http://10.0.2.2:8000/latest.json: the emulator reaches the Mac at 10.0.2.2.
val debugUpdateUrl = configuredUpdateUrl.ifEmpty { PUBLISHED_UPDATE_MANIFEST_URL }

fun javaStringLiteral(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.plumemu"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.plumemu"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // plumemu.versionCode (gradle.properties, or -P) when set: the public source copy has none of this
        // repository's history, so its copy of gradle.properties carries the number. Otherwise the commit
        // count of HEAD, so it grows with every commit and Android accepts each build as an update (test
        // builds are named by it). CI checks out the full history for the same count. A -P value also lets a
        // debug build stand in for another build of one commit to test an update.
        versionCode = providers.gradleProperty("plumemu.versionCode").orNull?.trim()?.toInt()
            ?: providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                .standardOutput.asText.get().trim().toInt()
        versionName = "0.1.0"
        // The 10,000-game benchmark content may be picked by an intent extra only where this is
        // true: the benchmark build types below, never debug or release (MainActivity).
        buildConfigField("boolean", "BENCHMARK_CONTENT", "false")
        // In-app updates and usage statistics (telemetry module): the addresses
        // named by the release machine's Gradle properties, else empty (the feature is absent). Debug builds
        // fall back to the published addresses (below).
        buildConfigField("String", "UPDATE_MANIFEST_URL", javaStringLiteral(configuredUpdateUrl))
        buildConfigField("String", "STATS_ENDPOINT", javaStringLiteral(configuredStatsEndpoint))
        buildConfigField("boolean", "STATS_LOCAL_CLEARTEXT", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The bundled name packs are already LZMA-compressed (identify/PACKS.md): the APK stores them as they are.
    androidResources {
        noCompress += "lzma"
    }

    signingConfigs {
        // The release key and its passwords live outside both repositories (the release
        // key is never committed): ~/.gradle/gradle.properties on the machine that makes releases names them. Where
        // they are absent (CI, other machines) the release build is unsigned.
        val storeFile = providers.gradleProperty("PLUMEMU_RELEASE_STORE_FILE").orNull
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = providers.gradleProperty("PLUMEMU_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("PLUMEMU_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("PLUMEMU_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "UPDATE_MANIFEST_URL", javaStringLiteral(debugUpdateUrl))
            buildConfigField("String", "STATS_ENDPOINT", javaStringLiteral(debugStatsEndpoint))
            buildConfigField("boolean", "STATS_LOCAL_CLEARTEXT", debugStatsCleartext.toString())
        }
        release {
            // R8 as measured: memory with 10,000 games and the APK size stay well inside
            // the performance budgets only with it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The local statistics server is plain http: only a debug build pointed at it (above) gets a
    // manifest that allows it. Every other build keeps Android's own default.
    if (debugStatsCleartext) {
        sourceSets.getByName("debug").manifest.srcFile("local-stats/AndroidManifest.xml")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the real resources (fonts, strings) for screenshot tests.
            isIncludeAndroidResources = true
            // Gradle's default 512 MB ran out once Robolectric held several SDK jars for ~925
            // tests in one JVM (OutOfMemoryError loading android-all 9).
            all {
                it.maxHeapSize = "2g"
                // The opt-in timing run against the real ScreenScraper (ScreenScraperLiveTest):
                // skipped unless asked for with -Pscreenscraper.live.timing=true, so CI and
                // verify.sh never run it.
                it.systemProperty("screenscraper.live.timing", providers.gradleProperty("screenscraper.live.timing").getOrElse("false"))
                // The README's raw screens (ReadmeMediaShots): rendered only with -Dplumemu.readme=true.
                providers.systemProperty("plumemu.readme").orNull?.let { readme -> it.systemProperty("plumemu.readme", readme) }
            }
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

baselineProfile {
    // Generated by hand on an emulator or device, then committed; never during
    // a normal build. Lands in src/main/generated/baselineProfiles/.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
}

// A release signed with the real release key is the one people install and update from. Without the two
// addresses it would never check for updates (nobody could be moved off it), and a leftover
// plumemu.versionCode (update testing) would pin its build number. So that build refuses to be made.
// A build without the key (a source build, CI) is exempt: it must work with none of these properties.
val releaseBuildPropertiesCheck = tasks.register("releaseBuildPropertiesCheck") {
    val isSigned = providers.gradleProperty("PLUMEMU_RELEASE_STORE_FILE").isPresent
    val hasUpdateUrl = configuredUpdateUrl.isNotEmpty()
    val hasStatsEndpoint = configuredStatsEndpoint.isNotEmpty()
    val pinnedVersionCode = providers.gradleProperty("plumemu.versionCode").isPresent
    doLast {
        if (!isSigned) return@doLast
        val problems = buildList {
            if (!hasUpdateUrl) add("plumemu.updateUrl is not set: this release would never check for updates")
            if (!hasStatsEndpoint) add("plumemu.statsEndpoint is not set: this release would have no statistics and no problem reports")
            if (pinnedVersionCode) add("plumemu.versionCode is set (-P or gradle.properties): it would pin this release's build number")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Release build refused:\n  " + problems.joinToString("\n  ") + "\nSet the first two in ~/.gradle/gradle.properties and drop the third.")
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(releaseBuildPropertiesCheck) }

androidComponents {
    // The Baseline Profile plugin creates `benchmarkRelease` (what the benchmarks measure) and
    // `nonMinifiedRelease` (what the profile is generated on) from release. Both are test-only:
    // release-like and not debuggable, signed with the debug key (never the release key), and the
    // only builds where the 10,000-game content can be chosen. benchmarkRelease runs R8 like the
    // release build.
    finalizeDsl { android ->
        val debugKey = android.signingConfigs.getByName("debug")
        android.buildTypes.findByName("benchmarkRelease")?.apply {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(android.getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = debugKey
            buildConfigField("boolean", "BENCHMARK_CONTENT", "true")
        }
        android.buildTypes.findByName("nonMinifiedRelease")?.apply {
            signingConfig = debugKey
            buildConfigField("boolean", "BENCHMARK_CONTENT", "true")
        }
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-input"))
    implementation(project(":core-data"))
    implementation(project(":platforms"))
    implementation(project(":library"))
    // The name packs and the identifier live in the app graph; About lists their licence texts.
    implementation(project(":identify"))
    implementation(project(":launcher"))
    // The ScreenScraper engine; the Application tells it which credentials this build has.
    implementation(project(":scraper"))
    // Usage statistics: the install id, queue, switch and sender (docs/conventions/telemetry.md).
    implementation(project(":telemetry"))
    // Configuration.Provider on the Application (WorkManager starts on demand).
    implementation(libs.androidx.work.runtime)
    // Decodes the saved box art at the drawn size, with a bounded memory cache.
    implementation(libs.coil.base)
    implementation(libs.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    // Installs the committed Baseline Profile on Android 7+ when the app is not installed from Play.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core-ui")))
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.robolectric)
    // Bounds, touch and recomposition tests of the main screen (Compose on Robolectric).
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    // The main screen's content from an in-memory Room database; startup work on virtual time.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // The box-art run's triggers and status against WorkManager's test database.
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)

    // On an emulator: the real folder picker, scan and main screen (LibraryOnDeviceTest).
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
