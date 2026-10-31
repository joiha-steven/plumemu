package com.plumemu.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** What the update cares about in a package: its name, version and the SHA-256 of each signing certificate. */
data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    val signers: Set<String>,
)

/** Reads [ApkFacts] from a file and from the installed app; tests give a fake. */
interface ApkInspector {
    /** Null when Android cannot read [file] as an APK. */
    fun archive(file: File): ApkFacts?

    /** The running app's own package as installed; null if Android will not say (never expected). */
    fun installed(): ApkFacts?
}

/**
 * The checks after the size and SHA-256: the file is Plumemu
 * ([PACKAGE]), its versionCode is the manifest's, and it is signed by exactly the certificates the
 * installed app is signed with, read at run time: a debug build accepts only debug-signed updates,
 * a release build only release-signed ones. Nothing is hard-coded.
 */
object ApkChecks {
    const val PACKAGE = "com.plumemu"

    /** Null when the file may be installed; else the first check that failed and a line for the log. */
    fun refusal(
        archive: ApkFacts?,
        installed: ApkFacts?,
        manifest: UpdateManifest,
    ): Pair<UpdateFailure, String>? {
        if (archive == null) return UpdateFailure.NotAnApk to "Android could not read the file as an APK"
        if (archive.packageName != PACKAGE) return UpdateFailure.WrongPackage to "package ${archive.packageName}"
        if (archive.versionCode != manifest.versionCode) return UpdateFailure.WrongVersion to "versionCode ${archive.versionCode}, manifest ${manifest.versionCode}"
        if (installed == null || installed.signers.isEmpty()) return UpdateFailure.WrongSigner to "the installed app's certificates could not be read"
        if (archive.signers != installed.signers) return UpdateFailure.WrongSigner to "signed by ${archive.signers.map { it.take(12) }}, installed by ${installed.signers.map { it.take(12) }}"
        return null
    }
}

/** [ApkInspector] on Android's PackageManager. */
class PackageManagerApkInspector(
    private val context: Context,
) : ApkInspector {
    override fun archive(file: File): ApkFacts? =
        try {
            archiveInfo(file)?.let { facts(it) }
        } catch (e: RuntimeException) {
            // A broken file can throw from the parser on some versions instead of returning null.
            null
        }

    override fun installed(): ApkFacts? =
        try {
            facts(installedInfo())
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    @Suppress("DEPRECATION") // The int-flags overloads are deprecated from API 33 but are the only ones below it.
    private fun archiveInfo(file: File): PackageInfo? {
        val pm = context.packageManager
        return when {
            Build.VERSION.SDK_INT >= 33 -> pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            Build.VERSION.SDK_INT >= 28 -> pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            else -> pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
        }
    }

    // GET_SIGNATURES below API 28 reads every signer of the package; Android 4.4's fake-ID bug, which the
    // lint warning is about, is long fixed on every version Plumemu runs on (API 23 and up).
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION") // The int-flags overloads are deprecated from API 33 but are the only ones below it.
    private fun installedInfo(): PackageInfo {
        val pm = context.packageManager
        val name = context.packageName
        return when {
            Build.VERSION.SDK_INT >= 33 -> pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            Build.VERSION.SDK_INT >= 28 -> pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            else -> pm.getPackageInfo(name, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION") // `signatures` and `versionCode` are the only fields below API 28.
    private fun facts(info: PackageInfo): ApkFacts {
        val signatures: Array<Signature>? =
            if (Build.VERSION.SDK_INT >= 28) {
                // The current signers only, not past ones a rotated key may still vouch for.
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return ApkFacts(info.packageName, versionCode, signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}
