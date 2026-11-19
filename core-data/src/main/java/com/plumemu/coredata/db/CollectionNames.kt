package com.plumemu.coredata.db

/** The name rule of a collection, shared by [CollectionDao] and the screens that check a name before it is saved. */
object CollectionNames {
    /** The longest name, in characters (code points). */
    const val MAX_LENGTH = 40

    /** Whether [name], trimmed, is a name [checked] accepts. */
    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.codePointCount(0, trimmed.length) <= MAX_LENGTH
    }

    /** [name] trimmed. @throws IllegalArgumentException when blank or longer than [MAX_LENGTH]. */
    fun checked(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "a collection name is not blank" }
        require(trimmed.codePointCount(0, trimmed.length) <= MAX_LENGTH) { "a collection name holds at most $MAX_LENGTH characters" }
        return trimmed
    }

    /** [wanted] when no name in [used] equals it (ignoring case); else " 2", " 3"… added, the base cut so the whole fits. */
    fun unique(wanted: String, used: List<String>): String {
        fun taken(candidate: String) = used.any { it.equals(candidate, ignoreCase = true) }
        if (!taken(wanted)) return wanted
        var n = 2
        while (true) {
            val suffix = " $n"
            val base = takeCodePoints(wanted, MAX_LENGTH - suffix.length).trimEnd()
            val candidate = base + suffix
            if (!taken(candidate)) return candidate
            n++
        }
    }

    private fun takeCodePoints(text: String, count: Int): String =
        if (text.codePointCount(0, text.length) <= count) text else text.substring(0, text.offsetByCodePoints(0, count))
}
