plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.plumemu.library"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // ScanTimingTest reads the shipped platforms.json from the test APK. Test APK
        // only: the library itself must not depend on platforms.
        getByName("androidTest").assets.directories.add("$rootDir/platforms/src/main/assets")
    }

    testOptions {
        unitTests {
            // Robolectric runs the scanner against an in-memory Room database.
            isIncludeAndroidResources = true
            all { test ->
                // The corpus harness: -Pcorpus.update=true rewrites
                // src/test/resources/corpus/scores.txt with the current scores.
                test.systemProperty("corpus.update", providers.gradleProperty("corpus.update").getOrElse("false"))
                test.systemProperty("plumemu.root", rootProject.layout.projectDirectory.asFile.absolutePath)
                // Tests read the shipped rules and name packs from the source tree.
                test.inputs.file("../platforms/src/main/assets/platforms.json")
                // The public source copy has no name packs (its tests skip), so the folder may be absent.
                if (file("../identify/src/main/assets/identify").isDirectory) test.inputs.dir("../identify/src/main/assets/identify")
            }
        }
    }
}

dependencies {
    implementation(project(":core-data"))
    // Names games while scanning; identify depends on nothing, never on library.
    implementation(project(":identify"))
    // The ScreenScraper run's door: LibraryScrapeSource implements its ScrapeSource.
    // The app depends on scraper itself, so nothing here needs api.
    implementation(project(":scraper"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // The scan timing test runs the real DocumentsContract walker on an emulator or device.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
