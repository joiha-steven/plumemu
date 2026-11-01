package com.plumemu.achievements.api

/**
 * Keeps the Web API key and the user out of log lines. The client logs the endpoint name and the
 * cause; when a URL is written at all it goes through [text] first.
 */
internal object Redact {
    /** Query parameters whose value never reaches a log: the key (`y`) and the user (`u`). */
    val SECRET_PARAMS: Set<String> = setOf("y", "u")

    // A secret parameter at the start of a query or after a separator; its value ends at the next
    // separator, quote, backslash or space.
    private val SECRET_PARAM = Regex("""(?<=[?&])(y|u)=[^&"'\s\\]*""")

    /** Shorter values are too likely to be ordinary words to blank out everywhere. */
    private const val MIN_SECRET_LENGTH = 4

    /** [text] with every `y=` / `u=` value replaced by `***`, and each of [secrets] blanked wherever it appears. */
    fun text(text: String, secrets: Collection<String> = emptyList()): String {
        var out = SECRET_PARAM.replace(text) { "${it.groupValues[1]}=***" }
        // Longest first: a short secret inside a longer one must not leave the rest of it.
        for (secret in secrets.sortedByDescending { it.length }) {
            if (secret.length >= MIN_SECRET_LENGTH) out = out.replace(secret, "***")
        }
        return out
    }
}
