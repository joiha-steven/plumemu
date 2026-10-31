import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// ScreenScraper developer credentials identify the app, not the player. They come from
// secrets.properties at the repository root (git-ignored), else from environment variables (CI
// secrets), else stay empty: such a build works and its scraper says it is unavailable.
val secretsFile = rootProject.layout.projectDirectory.file("secrets.properties")
val secrets = Properties().apply {
    providers.fileContents(secretsFile).asText.orNull?.let { load(it.reader()) }
}

fun secret(key: String, envVar: String): String =
    secrets.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envVar).orNull
        ?: ""

val developerId = secret("screenscraper.devId", "SCREENSCRAPER_DEV_ID")
val developerPassword = secret("screenscraper.devPassword", "SCREENSCRAPER_DEV_PASSWORD")
val hasDeveloperCredentials = developerId.isNotBlank() && developerPassword.isNotBlank()

fun javaStringLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.plumemu.scraper"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField(
            "String",
            "SCREENSCRAPER_DEV_ID",
            javaStringLiteral(developerId),
        )
        buildConfigField(
            "String",
            "SCREENSCRAPER_DEV_PASSWORD",
            javaStringLiteral(developerPassword),
        )
        buildConfigField("boolean", "HAS_DEVELOPER_CREDENTIALS", hasDeveloperCredentials.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            // Robolectric runs the worker, WorkManager's test database and bitmap decoding.
            isIncludeAndroidResources = true
            // The opt-in smoke test against the real ScreenScraper (live/ScreenScraperLiveSmokeTest):
            // skipped unless asked for with -Pscreenscraper.live=true, so CI and verify.sh never run it.
            all { it.systemProperty("screenscraper.live", providers.gradleProperty("screenscraper.live").getOrElse("false")) }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)

    // The real Android Keystore cipher round trip; runs on an emulator or device only.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// Says only whether the build has both developer credentials, never a value (CI runs it so its log
// shows that the secrets arrived).
tasks.register("screenScraperCredentialsCheck") {
    val line = "ScreenScraper developer credentials: " + if (hasDeveloperCredentials) "present" else "absent"
    doLast { println(line) }
}
