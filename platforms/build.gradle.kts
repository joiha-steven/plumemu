plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.plumemu.platforms"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// The tests read the shipped file straight from disk, so Gradle must rerun them when it changes.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/assets/platforms.json").withPropertyName("shippedPlatformsJson")
    inputs.file("src/main/AndroidManifest.xml").withPropertyName("generatedQueriesManifest")
    inputs.dir("../tools/platforms").withPropertyName("converterAndSources")
}
