plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.plumemu.launcher"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            // Robolectric runs PackageManager, intents and an in-memory Room database on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":platforms"))
    implementation(project(":core-data"))
    implementation(project(":library"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // The real launch test starts a fake emulator APK on an emulator (scripts in src/androidTest).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Tests read the shipped platform data and the generated <queries> manifest straight from disk.
tasks.withType<Test>().configureEach {
    inputs.file("../platforms/src/main/assets/platforms.json").withPropertyName("shippedPlatformsJson")
    inputs.file("../platforms/src/main/AndroidManifest.xml").withPropertyName("generatedQueriesManifest")
}
