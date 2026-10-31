plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.plumemu.coreui"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    // The screenshot harness (ReferenceShots) lives in test fixtures so every module's tests
    // can use it with testImplementation(testFixtures(project(":core-ui"))).
    testFixtures {
        enable = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the real resources (fonts) for screenshot tests.
            isIncludeAndroidResources = true
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    // api: Color, TextStyle, FontFamily, Dp and @Composable are part of this module's public surface.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.text)
    api(libs.androidx.compose.foundation)

    testImplementation(libs.junit)

    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testFixturesImplementation(libs.roborazzi)
    testFixturesImplementation(libs.roborazzi.compose)
    testFixturesImplementation(libs.robolectric)
    testFixturesImplementation(libs.junit)

    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.robolectric)
    // Roborazzi's compose capture hosts the content in an activity via setContent.
    testImplementation(libs.androidx.activity.compose)
    // createComposeRule for tests that compose PlumTheme and read what it provides.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
