plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.plumemu.identify"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    testOptions {
        unitTests.all { test ->
            // The 10,000-file measurement runs only with
            // -Pidentify.measure=true; otherwise IdentifyMeasurementTest is skipped at once.
            test.systemProperty("identify.measure", providers.gradleProperty("identify.measure").getOrElse("false"))
            // Some tests read the bundled packs from the source tree, so a change to them must rerun the
            // tests (and a build cache hit must not stand in for a run without them). The public source
            // copy has no packs: then the tests skip and there is nothing to track.
            if (file("src/main/assets/identify").isDirectory) test.inputs.dir("src/main/assets/identify")
        }
    }
}

dependencies {
    // Pure Kotlin/JDK code: no project dependencies, no Android classes in main sources (yet).
    testImplementation(libs.junit)
}
