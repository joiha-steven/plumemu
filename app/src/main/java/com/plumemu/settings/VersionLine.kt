package com.plumemu.settings

/**
 * What Settings › About shows: the version name and the build number, so a report names the exact
 * build ("0.0.3-beta (496)": the build number in brackets). The build number is the commit count (app/build.gradle.kts),
 * which also names the test builds.
 */
fun versionLine(versionName: String, buildNumber: Long): String = "$versionName ($buildNumber)"
