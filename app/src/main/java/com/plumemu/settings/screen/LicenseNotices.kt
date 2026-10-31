package com.plumemu.settings.screen

import android.content.res.AssetManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.plumemu.R
import java.io.IOException

/**
 * Settings › About › Open-source licenses: what ships inside the APK, from the third-party credits list (rows
 * "In use" that end up in the app; test-only and build-time tools are left out). [asset] is the
 * licence text shipped in the APK's assets, when there is one; read from there, never the network.
 */
@Immutable
internal data class LicenseNotice(
    @StringRes val name: Int,
    @StringRes val licence: Int,
    val asset: String?,
)

internal object LicenseNotices {
    /** The Apache License 2.0 text as androidx ships it (where each text comes from: the third-party credits list). */
    private const val APACHE = "licenses/apache-2.0.txt"

    val all: List<LicenseNotice> =
        listOf(
            LicenseNotice(R.string.settings_license_plum_sans, R.string.settings_licence_ofl, "licenses/plum-sans-OFL.txt"),
            LicenseNotice(R.string.settings_license_plum_serif, R.string.settings_licence_ofl, "licenses/plum-serif-OFL.txt"),
            LicenseNotice(R.string.settings_license_plum_mono, R.string.settings_licence_ofl, "licenses/plum-mono-OFL.txt"),
            LicenseNotice(R.string.settings_license_daijisho, R.string.settings_licence_mit, "licenses/daijisho-MIT.txt"),
            LicenseNotice(R.string.settings_license_esde, R.string.settings_licence_mit, "licenses/esde-MIT.txt"),
            LicenseNotice(R.string.settings_license_androidx, R.string.settings_licence_apache, APACHE),
            LicenseNotice(R.string.settings_license_kotlin, R.string.settings_licence_apache, APACHE),
            LicenseNotice(R.string.settings_license_room, R.string.settings_licence_apache, APACHE),
            // Box art decoding and what it brings outside androidx: OkHttp and Okio.
            LicenseNotice(R.string.settings_license_coil, R.string.settings_licence_apache, APACHE),
            LicenseNotice(R.string.settings_license_okhttp, R.string.settings_licence_apache, APACHE),
            LicenseNotice(R.string.settings_license_protobuf, R.string.settings_licence_bsd3, "licenses/protobuf-LICENSE.txt"),
            // The identify module's name packs; the texts ship in identify's assets (third-party credits list).
            LicenseNotice(R.string.settings_license_mame_arcade, R.string.settings_licence_bsd3, "licenses/mame-BSD-3-Clause.txt"),
            LicenseNotice(R.string.settings_license_mame_lists, R.string.settings_licence_cc0, "licenses/mame-CC0.txt"),
            LicenseNotice(R.string.settings_license_titledb, R.string.settings_licence_mit, "licenses/titledb-MIT.txt"),
            // identify's LZMA decoder is ported from it; public domain has no text to ship.
            LicenseNotice(R.string.settings_license_lzma_sdk, R.string.settings_licence_public_domain, null),
        )
}

/** A licence text, or why there is none to show. */
internal sealed interface LicenseText {
    data class Loaded(val text: String) : LicenseText

    /** No text ships in the APK for this entry; the panel names the licence instead. */
    data object NotShipped : LicenseText

    data object Unreadable : LicenseText
}

/** Reads [notice]'s text from the APK's assets; call off the main thread. Logs the class only. */
internal fun readLicenseText(
    assets: AssetManager,
    notice: LicenseNotice,
    log: (String) -> Unit,
): LicenseText {
    val path = notice.asset ?: return LicenseText.NotShipped
    return try {
        LicenseText.Loaded(assets.open(path).bufferedReader().use { it.readText() })
    } catch (e: IOException) {
        log("Licence text could not be read (${e.javaClass.simpleName})")
        LicenseText.Unreadable
    }
}
