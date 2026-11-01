package com.plumemu.update

import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.io.InputStream

/** The platform data in use and which version of it that is (Settings › About shows [version]). */
data class LoadedPlatformData(
    val catalog: PlatformCatalog,
    val version: PlatformDataVersion,
)

/**
 * What the app starts with: the downloaded copy when its dataVersion is higher than the bundled one
 * and it loads, else the bundled asset. A downloaded copy that is not newer (an APK update caught
 * up with it) is deleted; one that fails to load costs one log line and the bundled data is used.
 * Null only when the bundled asset itself is broken (the loader has logged why).
 */
fun selectPlatformData(
    bundledVersion: PlatformDataVersion,
    openBundled: () -> InputStream,
    store: PlatformDataStore,
    info: (String) -> Unit,
    log: (String) -> Unit,
): LoadedPlatformData? {
    val stored = store.read()
    if (stored == null && store.readVersion() != null) {
        // A record with data that does not match it (the line was logged by read): forgotten, or that version would never be fetched again.
        info("Stored platform data is damaged; removing it")
        store.delete()
    }
    if (stored != null) {
        if (stored.dataVersion > bundledVersion.dataVersion) {
            val loaded = PlatformLoader.load(stored.json, "downloaded platform data ${stored.dataVersion}")
            if (loaded is PlatformLoadResult.Loaded) {
                return LoadedPlatformData(loaded.catalog, PlatformDataVersion(stored.dataVersion, stored.sha256, stored.date))
            }
            log("Downloaded platform data ${stored.dataVersion} does not load; using the bundled ${bundledVersion.dataVersion}")
            // Forgotten, so the next check may fetch this version again instead of counting it as already held.
            store.delete()
        } else {
            info("Downloaded platform data ${stored.dataVersion} is not newer than the bundled ${bundledVersion.dataVersion}; removing it")
            store.delete()
        }
    }
    val loaded = openBundled().use { PlatformLoader.load(it, "assets/platforms.json") }
    return (loaded as? PlatformLoadResult.Loaded)?.let { LoadedPlatformData(it.catalog, bundledVersion) }
}
