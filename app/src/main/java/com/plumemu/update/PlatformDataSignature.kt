package com.plumemu.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.cert.CertificateFactory

/** Says whether [signature] is a valid signature of [message] by a key the app trusts; tests give a fake or their own keys. */
fun interface DataSignatureVerifier {
    fun verifies(
        message: ByteArray,
        signature: ByteArray,
    ): Boolean
}

/**
 * `SHA256withRSA` against the public key of each of the app's own signing certificates; any one
 * verifying is enough. A certificate whose key is not RSA never verifies. [keys] is read at each
 * check, never cached: nothing is hard-coded, so a debug build trusts the debug key and a release
 * build the release key.
 */
class CertificateSignatureVerifier(
    private val keys: () -> List<PublicKey>,
) : DataSignatureVerifier {
    override fun verifies(
        message: ByteArray,
        signature: ByteArray,
    ): Boolean =
        keys().any { key ->
            try {
                java.security.Signature.getInstance("SHA256withRSA").run {
                    initVerify(key)
                    update(message)
                    verify(signature)
                }
            } catch (e: GeneralSecurityException) {
                // Not an RSA key, or a signature that is not well formed: this key does not vouch.
                false
            }
        }

    companion object {
        /** The standard base64 of a manifest's `signature`, or null when it is not base64. */
        fun decode(base64: String): ByteArray? =
            try {
                Base64.decode(base64, Base64.NO_WRAP).takeIf { it.isNotEmpty() }
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

/** The public keys inside X.509 certificates, as `PackageManager` hands them out (DER bytes); a certificate that cannot be read is left out. */
internal fun publicKeysOf(certificates: List<ByteArray>): List<PublicKey> {
    val factory = CertificateFactory.getInstance("X.509")
    return certificates.mapNotNull { der ->
        try {
            factory.generateCertificate(ByteArrayInputStream(der)).publicKey
        } catch (e: GeneralSecurityException) {
            null
        }
    }
}

/** The running app's own signing certificates' keys, read like [PackageManagerApkInspector] reads them (GET_SIGNATURES below API 28, GET_SIGNING_CERTIFICATES from 28). */
class OwnSigningKeys(
    private val context: Context,
) : () -> List<PublicKey> {
    override fun invoke(): List<PublicKey> =
        try {
            publicKeysOf(signatures().map { it.toByteArray() })
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }

    // Below API 28 GET_SIGNATURES is the only way; the Android 4.4 fake-ID bug the lint warning is about is long fixed on the versions Plumemu runs on.
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signatures(): List<Signature> {
        val pm = context.packageManager
        val name = context.packageName
        return if (Build.VERSION.SDK_INT >= 28) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getPackageInfo(name, flags)
            info.signingInfo?.apkContentsSigners.orEmpty().toList()
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures.orEmpty().toList()
        }
    }
}
