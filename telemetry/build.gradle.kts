plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.plumemu.telemetry"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    testOptions {
        unitTests {
            // Robolectric runs the common fields (Build, displays, memory) and the send worker.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // api: the events take the platform data's systems and emulators (TelemetryIds.kt), so an id
    // cannot be a title or a file name.
    api(project(":platforms"))
    // The sender (HTTPS), the JSON of the API, the on/off state and install id, the send worker.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    // A local fake server: nothing in a test reaches the network.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
