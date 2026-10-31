plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.plumemu.coredata"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        // The exported schemas are read by MigrationTestHelper in the JVM tests.
        getByName("test").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // Robolectric needs the test assets (the schema JSON) on its classpath.
            isIncludeAndroidResources = true
        }
    }
}

room {
    // Every schema version is committed; migration tests compare against these files.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // api: DAOs return Flow and are part of this module's public surface.
    api(libs.androidx.room.runtime)
    api(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room.compiler)
    // Settings (settings/SettingsRepository.kt); the DataStore stays inside this module.
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// KSP exports the schema and the Room plugin's copyRoomSchemas task writes it to schemas/…/<n>.json;
// the unit-test assets must be merged after that, or the first test run after a schema change reads
// the previous JSON and the migration test fails.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("UnitTestAssets") }.configureEach {
    dependsOn(tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") && !it.name.contains("UnitTest") })
    dependsOn(tasks.matching { it.name == "copyRoomSchemas" })
}
