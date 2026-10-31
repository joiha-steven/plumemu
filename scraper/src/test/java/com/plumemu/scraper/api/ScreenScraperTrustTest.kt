package com.plumemu.scraper.api

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathValidatorResult
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The ISRG roots bundled for Android 6.0-7.0 (API 23-24), whose system store lacks ISRG Root X1.
 *
 * The chains are ScreenScraper's real public intermediates (from letsencrypt.org/certs):
 * Let's Encrypt YE2 <- Root YE (cross-signed by ISRG Root X2) <- ISRG Root X2 (cross-signed by
 * ISRG Root X1). No 90-day leaf: every path is validated at [FIXED_DATE], inside all their
 * validity periods, so the tests never expire.
 */
class ScreenScraperTrustTest {

    @Test
    fun `the bundled roots are ISRG Root X1 and X2 with the published SHA-256 fingerprints`() {
        val byName = ScreenScraperTrust.bundledRoots().associate { it.subjectX500Principal.name to sha256(it) }
        assertEquals(
            mapOf(
                "CN=ISRG Root X1,O=Internet Security Research Group,C=US" to ISRG_ROOT_X1_SHA256,
                "CN=ISRG Root X2,O=Internet Security Research Group,C=US" to ISRG_ROOT_X2_SHA256,
            ),
            byName,
        )
    }

    @Test
    fun `the ScreenScraper intermediates validate up to the bundled ISRG Root X1 alone`() {
        val x1 = ScreenScraperTrust.bundledRoots().single { "ISRG Root X1" in it.subjectX500Principal.name }
        val params = PKIXParameters(setOf(TrustAnchor(x1, null))).apply {
            date = FIXED_DATE
            isRevocationEnabled = false
        }
        val path = CertificateFactory.getInstance("X.509").generateCertPath(screenScraperChain().toList())
        val result = CertPathValidator.getInstance("PKIX").validate(path, params) as PKIXCertPathValidatorResult
        assertSame(x1, result.trustAnchor.trustedCert)
    }

    @Test
    fun `below API 25 the chain ending at ISRG Root X1 is accepted though the system store refuses it`() {
        SystemThenBundledTrustManager(AndroidSixStore(), bundledAtFixedDate()).checkServerTrusted(screenScraperChain(), AUTH_TYPE)
    }

    @Test
    fun `below API 25 a chain to an unknown self-signed root is refused`() {
        val trust = SystemThenBundledTrustManager(ScreenScraperTrust.systemTrustManager(), bundledAtFixedDate())
        assertThrows(CertificateException::class.java) {
            trust.checkServerTrusted(arrayOf(testCert("unknown-root.pem")), AUTH_TYPE)
        }
    }

    @Test
    fun `below API 25 a root that only borrows ISRG Root X1's name is refused`() {
        val trust = SystemThenBundledTrustManager(AndroidSixStore(), bundledAtFixedDate())
        val refusal = assertThrows(CertificateException::class.java) {
            trust.checkServerTrusted(arrayOf(testCert("fake-isrg-root-x1.pem")), AUTH_TYPE)
        }
        assertEquals("the system's refusal is the one thrown", AndroidSixStore.MESSAGE, refusal.message)
        assertEquals("the bundled roots' refusal rides along", 1, refusal.suppressed.size)
    }

    @Test
    fun `with Conscrypt as the first provider, as on Android, the bundled roots still load and validate`() = withConscryptFirst {
        val app = ScreenScraperTrust.bundledTrustManager()
        assertTrue("Conscrypt built it: ${app.javaClass.name}", app.javaClass.name.startsWith("org.conscrypt."))
        assertEquals(ScreenScraperTrust.bundledRoots().toSet(), app.acceptedIssuers.toSet())
        SystemThenBundledTrustManager(AndroidSixStore(), bundledAtFixedDate()).checkServerTrusted(screenScraperChain(), AUTH_TYPE)
    }

    @Test
    fun `the system store is asked first and the bundled roots only after it refused`() {
        val bundled = CountingStore(bundledAtFixedDate())
        SystemThenBundledTrustManager(AcceptingStore(), bundled).checkServerTrusted(screenScraperChain(), AUTH_TYPE)
        assertEquals(0, bundled.serverChecks)
        SystemThenBundledTrustManager(AndroidSixStore(), bundled).checkServerTrusted(screenScraperChain(), AUTH_TYPE)
        assertEquals(1, bundled.serverChecks)
    }

    @Test
    fun `from API 25 on there is no extra trust and the client keeps the platform default`() {
        val untouched = TrustHolder(system = { error("built at API 25") }, bundled = { error("built at API 25") })
        val logs = mutableListOf<String>()
        for (sdk in listOf(25, 34)) {
            val client = ScreenScraperTrust.apply(OkHttpClient.Builder(), sdk, logs::add, untouched).build()
            assertPlatformTrust(client)
        }
        assertPlatformTrust(ScreenScraperApi.defaultHttpClient(logs::add, sdkInt = 25))
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun `below API 25 the client trusts the system store plus the bundled roots and keeps OkHttp's other defaults`() {
        for (sdk in listOf(23, 24)) {
            val client = ScreenScraperApi.defaultHttpClient({ throw AssertionError(it) }, sdkInt = sdk)
            val issuers = client.x509TrustManager!!.acceptedIssuers.toSet()
            assertTrue("API $sdk is the composite", client.x509TrustManager is SystemThenBundledTrustManager)
            assertTrue("API $sdk keeps the system roots", issuers.containsAll(OkHttpClient().x509TrustManager!!.acceptedIssuers.toSet()))
            assertTrue("API $sdk adds the bundled roots", issuers.containsAll(ScreenScraperTrust.bundledRoots()))
            assertSame(OkHttpClient().hostnameVerifier, client.hostnameVerifier)
            assertEquals(OkHttpClient().connectionSpecs, client.connectionSpecs)
        }
    }

    @Test
    fun `below API 25 every client shares the one trust of the process`() {
        val first = ScreenScraperApi.defaultHttpClient({ throw AssertionError(it) }, sdkInt = 23)
        val second = ScreenScraperApi.defaultHttpClient({ throw AssertionError(it) }, sdkInt = 23)
        assertSame(first.x509TrustManager, second.x509TrustManager)
        assertSame(first.sslSocketFactory, second.sslSocketFactory)
    }

    @Test
    fun `a bundled root missing from the build is logged once and the client keeps the system trust`() {
        var builds = 0
        val broken = TrustHolder(bundled = { builds++; ScreenScraperTrust.bundledTrustManager(listOf("no-such-root.pem")) })
        val logs = mutableListOf<String>()
        repeat(2) {
            val client = ScreenScraperTrust.apply(OkHttpClient.Builder(), 23, logs::add, broken).build()
            assertPlatformTrust(client)
        }
        assertEquals(1, builds)
        assertEquals(1, logs.size)
        assertTrue(logs.single(), "CertificateException" in logs.single() && "system trust only" in logs.single())
    }

    /** Android 6's store: does not know ISRG Root X1, so it refuses ScreenScraper's chain. */
    private class AndroidSixStore : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException(MESSAGE)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException(MESSAGE)
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        companion object {
            const val MESSAGE = "Trust anchor for certification path not found."
        }
    }

    private class CountingStore(private val inner: X509TrustManager) : X509TrustManager by inner {
        var serverChecks = 0

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            serverChecks++
            inner.checkServerTrusted(chain, authType)
        }
    }

    private class AcceptingStore : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val AUTH_TYPE = "ECDHE_ECDSA"

        // From crt.sh ids 9314791 and 3335562555, linked from https://letsencrypt.org/certificates/.
        const val ISRG_ROOT_X1_SHA256 = "96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6"
        const val ISRG_ROOT_X2_SHA256 = "69729B8E15A86EFC177A57AFB7171DFC64ADD28C2FCA8CF1507E34453CCB1470"

        /** June 2027 UTC: inside the validity of YE2 and of both cross-signs. */
        val FIXED_DATE: Date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2027, Calendar.JUNE, 1)
        }.time

        fun screenScraperChain(): Array<X509Certificate> =
            arrayOf(testCert("int-ye2.pem"), testCert("root-ye-by-x2.pem"), testCert("root-x2-by-x1.pem"))

        fun testCert(name: String): X509Certificate {
            val stream = requireNotNull(ScreenScraperTrustTest::class.java.getResourceAsStream("/screenscraper/tls/$name")) { name }
            return stream.use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
        }

        /**
         * The bundled roots as the app builds them, but validating at [FIXED_DATE] instead of now.
         * A fixed date needs `CertPathTrustManagerParameters`, which only the JDK's own provider
         * (SunJSSE) takes; Conscrypt refuses it ("ManagerFactoryParameters not supported"), and a
         * Robolectric test earlier in the same JVM leaves Conscrypt first in the provider list. So
         * the provider is named here. The app itself inits its factory with a KeyStore, which every
         * provider takes (Conscrypt on Android included).
         */
        fun bundledAtFixedDate(): X509TrustManager {
            val params = PKIXBuilderParameters(ScreenScraperTrust.bundledKeyStore(), null).apply {
                date = FIXED_DATE
                isRevocationEnabled = false
            }
            val factory = TrustManagerFactory.getInstance("PKIX", JDK_TRUST_PROVIDER)
            factory.init(CertPathTrustManagerParameters(params))
            return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        }

        private const val JDK_TRUST_PROVIDER = "SunJSSE"

        /**
         * Runs [block] with Conscrypt first in the JVM's provider list, as Android has it and as a
         * Robolectric test leaves it. Conscrypt comes with Robolectric at test runtime only, so it
         * is loaded by name.
         */
        fun withConscryptFirst(block: () -> Unit) {
            val provider = Class.forName("org.conscrypt.Conscrypt").getMethod("newProvider").invoke(null) as Provider
            val installed = Security.insertProviderAt(provider, 1) == 1
            try {
                assertEquals("Conscrypt is the first provider", provider.name, Security.getProviders().first().name)
                block()
            } finally {
                if (installed) Security.removeProvider(provider.name)
            }
        }

        fun assertPlatformTrust(client: OkHttpClient) {
            val platform = OkHttpClient().x509TrustManager!!
            assertEquals(platform.javaClass, client.x509TrustManager!!.javaClass)
            assertEquals(platform.acceptedIssuers.toSet(), client.x509TrustManager!!.acceptedIssuers.toSet())
        }

        fun sha256(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }
    }
}
