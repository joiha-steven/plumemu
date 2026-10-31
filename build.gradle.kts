// Plugins are declared once here so every module uses the same versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}

// Absolute wall-clock budgets in unit tests (speed checks that hold on any machine are ratios, as in TitleSearchTest) are
// checked on the development machine only. verify.sh --ci passes -Pplumemu.timingBudgets=false, because
// shared CI runners are several times slower; the tests then skip just the timing assertion.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("plumemu.timingBudgets", providers.gradleProperty("plumemu.timingBudgets").getOrElse("true"))
    }
}
