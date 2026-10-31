package com.plumemu.scraper.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Keeps credentials out of anything stored or logged.
 *
 * ScreenScraper echoes credentials back: media URLs and the header's `commandRequested` carry
 * devid, devpassword, ssid, sspassword and softname as query parameters.
 */
internal object Redact {
    val CREDENTIAL_PARAMS: Set<String> = setOf("devid", "devpassword", "ssid", "sspassword", "softname")

    // A parameter name not glued to a longer word (or right after a JSON-escaped &, \u0026); its
    // value ends at the next separator, quote, backslash or space.
    private val CREDENTIAL_PARAM = Regex(
        """(?:(?<![A-Za-z0-9_])|(?<=\\u0026))(devid|devpassword|ssid|sspassword|softname)=[^&"'\s\\]*""",
        RegexOption.IGNORE_CASE,
    )

    // The person's username comes back as the id of the answer's "ssuser" object.
    private val ACCOUNT_ID = Regex("""("ssuser"\s*:\s*\{[^{}]*?"id"\s*:\s*")[^"]*(")""")

    /** Shorter values are too likely to be ordinary words to blank out everywhere. */
    private const val MIN_SECRET_LENGTH = 4

    /**
     * [text] with the value of every credential parameter and the `ssuser` id replaced by `***`,
     * and every one of
     * [secrets] (passwords, ids) blanked wherever it appears, for text the server may have
     * echoed them into.
     */
    fun text(text: String, secrets: Collection<String> = emptyList()): String {
        var out = CREDENTIAL_PARAM.replace(text) { "${it.groupValues[1]}=***" }
        out = ACCOUNT_ID.replace(out) { "${it.groupValues[1]}***${it.groupValues[2]}" }
        // Longest first: a username inside a password must not leave the rest of the password.
        for (secret in secrets.sortedByDescending { it.length }) {
            if (secret.length >= MIN_SECRET_LENGTH) out = out.replace(secret, "***")
        }
        return out
    }

    /** [url] with every credential parameter (and empty leftovers) removed, for storing. */
    fun stripCredentials(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return text(url)
        val kept = parsed.encodedQuery?.split('&')?.filter { part ->
            val name = part.substringBefore('=')
            name.isNotEmpty() && name.lowercase() !in CREDENTIAL_PARAMS
        }
        val query = kept?.takeIf { it.isNotEmpty() }?.joinToString("&")
        return parsed.newBuilder().encodedQuery(query).build().toString()
    }
}
