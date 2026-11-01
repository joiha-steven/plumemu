package com.plumemu.update

import com.plumemu.platforms.PlatformDataVersion
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.zip.GZIPOutputStream

/** Shared pieces of the platform data tests: a real data file, two signing keys, a signed manifest. */
internal object PlatformDataFixtures {
    const val MANIFEST_URL = "https://plumemu-app.stevensdesk.com/data/platforms/beta.json"
    const val FILE_URL = "https://plumemu-app.stevensdesk.com/data/platforms/platforms-6.json.gz"
    const val INSTALLED = 900L

    val bundled = PlatformDataVersion(1, "00".repeat(32), "2026-11-01")

    /** The shipped data file: unit tests of `app` run with the module folder as working directory. */
    val json: String by lazy { File("../platforms/src/main/assets/platforms.json").readText() }

    /** The app's own key, and another one (the debug key against the release key). */
    val appKey: KeyPair by lazy { newKey() }
    val otherKey: KeyPair by lazy { newKey() }

    private fun newKey(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    fun sign(
        text: String,
        key: KeyPair = appKey,
    ): String =
        Base64.getEncoder().encodeToString(
            java.security.Signature.getInstance("SHA256withRSA").run {
                initSign(key.private)
                update(text.toByteArray(Charsets.UTF_8))
                sign()
            },
        )

    /** What the publisher signs; the app must rebuild exactly this text. */
    fun signedText(
        dataVersion: Int,
        formatVersion: Int,
        minVersionCode: Long,
        sha256: String,
    ) = "plumemu-platforms-data\ndataVersion=$dataVersion\nformatVersion=$formatVersion\nminVersionCode=$minVersionCode\nsha256=$sha256\n"

    /** A published version 6: the file, and its manifest signed with [signWith]; change the manifest with [edit] after signing. */
    class Published(
        val gz: ByteArray,
        val manifest: String,
    )

    fun published(
        dataVersion: Int = 6,
        formatVersion: Int = 2,
        minVersionCode: Long = 900,
        json: ByteArray = this.json.toByteArray(Charsets.UTF_8),
        gz: ByteArray = gzip(json),
        signWith: KeyPair = appKey,
        edit: JSONObject.() -> Unit = {},
    ): Published {
        val sha = UpdateFixtures.sha256(gz)
        val manifest =
            JSONObject()
                .put("schema", 1)
                .put("dataVersion", dataVersion)
                .put("formatVersion", formatVersion)
                .put("minVersionCode", minVersionCode)
                .put("file", "platforms-$dataVersion.json.gz")
                .put("size", gz.size)
                .put("sha256", sha)
                .put("signature", sign(signedText(dataVersion, formatVersion, minVersionCode, sha), signWith))
                .put("released", "2026-11-01")
                .apply(edit)
        return Published(gz, manifest.toString())
    }

    /** The server with [p] published at [FILE_URL]. */
    fun serve(
        http: FakeUpdateHttp,
        p: Published,
    ) {
        http.texts[MANIFEST_URL] = p.manifest
        http.files[FILE_URL] = p.gz
    }

    /** A real X.509 certificate (DER, base64), made once with openssl, for [publicKeysOf]. */
    const val CERTIFICATE_BASE64 = "MIIC8DCCAdigAwIBAgIUCJ7r16fYImg18Sfo7Qwg4Ycdgs0wDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UEAwwMUGx1bWVtdSB0ZXN0MCAXDTI2MTAzMTE5MzYzN1oYDzIxMjYxMDA3MTkzNjM3WjAXMRUwEwYDVQQDDAxQbHVtZW11IHRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDOy/bpiHKmKDCkH8XOLtmylVzJwA0aAk3E7JZ2PhoxaxySxi8t1jR2pxpL4Z26TVT40e27Wgfwg1IQ9z+C884S2w+0indrf22nP0rXp0OtXWXVaHnCGVWuIkAxWhxRTR0b0nAsCasefQiR3WSL8Fnv3NhfbF3N2xq4hEPEyWeYvZN4yvWtxYme+dyCjY16aC2PGGKDFYJuqeecekrrIxCUULvEFssdcFBCylNBC5udf3lZ6ucVojRNgLyBwd/XG4P3a3gEZfG6FKwfJej0e/wOIn3QU0YOcT2/SvL91E7NXnAooPdhlTFqQEIzo6/DXxt4YoNm/KlRpoLd7qKN5UmfAgMBAAGjMjAwMB0GA1UdDgQWBBT0hB9nvP9dSrOqWIbRg0ARTjBySTAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQAsRtlf3tlmVKnSqlcfTBaE0llxwdrclLo/dK5u5IZwwM8+pNo5XUWgA6SWjJjHIogEpH8XY8i4wVFjiPnnjj625T+MB+RTiz3/CxITmTgkE+Q075/BGYMKrs/YLRlxpEGnmtPFfuleW1wXFv+2LHioyEbWuo8wBLtG6fJNX3UJVLji/eefoct8FYpFFqYUPMIBae1nOcGta9+FaAhnuhSXg9askpByD4VWSzcfb/Ek49rzuyCVGhskGy69ouZ6sCWaxfhrvcGJBXtG9j9LGS1dtBg7JSvG7fkSdyHymlY+p6tjPFG9GZ+PcGv9DPnwHD1o/eIN7+by6I9PyTqlMoGA"
}
