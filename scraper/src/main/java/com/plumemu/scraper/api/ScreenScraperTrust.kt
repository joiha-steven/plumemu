package com.plumemu.scraper.api

import okhttp3.OkHttpClient
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Which certificates the ScreenScraper client trusts.
 *
 * ScreenScraper's chain (measured on api. and neoclone.screenscraper.fr, TLS 1.2,
 * ECDHE-ECDSA-AES128-GCM-SHA256): `*.screenscraper.fr` <- Let's Encrypt YE2 <- Root YE
 * (cross-signed by ISRG Root X2) <- ISRG Root X2 (cross-signed by ISRG Root X1); the server sends
 * all four. ISRG Root X1 is in Android's system store only from 7.1.1 (API 25), so on Android
 * 6.0-7.0 the handshake failed with `SSLHandshakeException` and the sign-in said "No connection".
 *
 * Below API 25 the client trusts the system store first and then two bundled roots, the public
 * certificates from https://letsencrypt.org/certs/ (SHA-256 checked against crt.sh, as linked
 * from https://letsencrypt.org/certificates/):
 * - `isrgrootx1.pem`, ISRG Root X1:
 *   96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6
 * - `isrg-root-x2.pem`, ISRG Root X2:
 *   69:72:9B:8E:15:A8:6E:FC:17:7A:57:AF:B7:17:1D:FC:64:AD:D2:8C:2F:CA:8C:F1:50:7E:34:45:3C:CB:14:70
 *
 * From API 25 on nothing changes: OkHttp's platform trust. Hostname verification, TLS versions
 * and cipher suites stay OkHttp's defaults at every level.
 *
 * Below API 25 the trust manager and its socket factory are built once per process
 * ([processWide]): `OkHttpClient.Builder.sslSocketFactory` reads `acceptedIssuers` (OkHttp's
 * certificate chain cleaner), which loads the bundled roots and reads the whole system store
 * (AndroidCAStore), and every `Scraper.create` makes a client. The first client pays that once,
 * on the thread that creates it.
 */
internal object ScreenScraperTrust {
    /** Android 7.1.1, the first release whose system store holds ISRG Root X1. */
    const val FIRST_SDK_TRUSTING_ISRG_ROOT_X1 = 25

    /** Java resources (not `res/raw`): the client has no Context, and JVM tests read them too. */
    private const val RESOURCE_DIR = "com/plumemu/scraper/certs/"
    val BUNDLED_ROOT_FILES = listOf("isrgrootx1.pem", "isrg-root-x2.pem")

    /** The one trust of this process below API 25, built by the first client that needs it. */
    val processWide = TrustHolder()

    /**
     * [builder] with the system store plus the bundled roots below API 25; unchanged from API 25
     * on, and unchanged (system trust only, logged) when the bundled roots cannot be read. Never
     * throws for the trust: a client is always made.
     */
    fun apply(
        builder: OkHttpClient.Builder,
        sdkInt: Int,
        log: (String) -> Unit,
        holder: TrustHolder = processWide,
    ): OkHttpClient.Builder {
        if (sdkInt >= FIRST_SDK_TRUSTING_ISRG_ROOT_X1) return builder
        val trust = holder.get(log) ?: return builder
        return builder.sslSocketFactory(trust.socketFactory, trust.trustManager)
    }

    /** The roots in [files], read from the module's Java resources; throws if one is missing. */
    fun bundledRoots(files: List<String> = BUNDLED_ROOT_FILES): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val loader = ScreenScraperTrust::class.java.classLoader
            ?: throw CertificateException("no class loader to read the bundled roots")
        return files.map { name ->
            val stream = loader.getResourceAsStream(RESOURCE_DIR + name)
                ?: throw CertificateException("bundled root $name is missing from the build")
            stream.use { factory.generateCertificate(it) as X509Certificate }
        }
    }

    /** A key store holding only the roots in [files], as trust anchors. */
    fun bundledKeyStore(files: List<String> = BUNDLED_ROOT_FILES): KeyStore =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            bundledRoots(files).forEachIndexed { index, root -> setCertificateEntry("isrg-root-$index", root) }
        }

    fun systemTrustManager(): X509TrustManager = trustManagerOf(null)

    fun bundledTrustManager(files: List<String> = BUNDLED_ROOT_FILES): X509TrustManager =
        trustManagerOf(bundledKeyStore(files))

    /** The platform's PKIX trust manager over [anchors], or over the system store when null. */
    private fun trustManagerOf(anchors: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(anchors)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }
}

/**
 * Builds the below-API-25 trust once and keeps it, or keeps its absence: a bundled root that
 * cannot be read is a broken build and does not mend itself, so it is logged once and every
 * client then uses the system trust (on Android 6.0-7.0 the person gets the TLS failure as before
 * this trust existed).
 */
internal class TrustHolder(
    private val system: () -> X509TrustManager = ScreenScraperTrust::systemTrustManager,
    private val bundled: () -> X509TrustManager = { ScreenScraperTrust.bundledTrustManager() },
) {
    class Trust(val trustManager: X509TrustManager, val socketFactory: SSLSocketFactory)

    private var built = false
    private var trust: Trust? = null

    fun get(log: (String) -> Unit): Trust? = synchronized(this) {
        if (!built) {
            trust = build(log)
            built = true
        }
        trust
    }

    private fun build(log: (String) -> Unit): Trust? = try {
        val manager = SystemThenBundledTrustManager(system(), bundled())
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(manager), null)
        Trust(manager, context.socketFactory)
    } catch (e: GeneralSecurityException) {
        log(unusable(e))
        null
    } catch (e: IOException) {
        log(unusable(e))
        null
    }

    private fun unusable(e: Exception): String =
        "TLS: the bundled ISRG roots could not be loaded (${e.javaClass.simpleName}); using the system " +
            "trust only, so ScreenScraper fails on Android 6.0-7.0"
}

/**
 * Accepts a chain the [system] store accepts, else one the [bundled] roots accept; the bundled
 * roots are asked only after the system store refused. A chain both refuse fails with the
 * system's exception, the bundled refusal attached as suppressed. A plain [X509TrustManager]:
 * `X509ExtendedTrustManager` is API 24, and this runs on API 23.
 */
internal class SystemThenBundledTrustManager(
    private val system: X509TrustManager,
    private val bundled: X509TrustManager,
) : X509TrustManager {
    /** Read once: on Android the system store builds this array from AndroidCAStore on every call. */
    private val issuers: Array<X509Certificate> by lazy { system.acceptedIssuers + bundled.acceptedIssuers }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            system.checkServerTrusted(chain, authType)
        } catch (systemRefusal: CertificateException) {
            try {
                bundled.checkServerTrusted(chain, authType)
            } catch (bundledRefusal: CertificateException) {
                systemRefusal.addSuppressed(bundledRefusal)
                throw systemRefusal
            }
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = issuers.clone()
}
