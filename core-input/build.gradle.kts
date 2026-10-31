plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.plumemu.coreinput"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric reads the merged test manifest (the empty activity createComposeRule hosts).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // api: Modifier, Dp, AnimationSpec and @Composable are part of this module's public surface.
    // Kotlin coroutines come with the Compose runtime (it exposes CoroutineScope in its API).
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // Tests check the strip offsets against the real layout tokens; main code takes sizes as
    // parameters so core-input does not depend on core-ui.
    testImplementation(project(":core-ui"))
    testImplementation(testFixtures(project(":core-ui")))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
