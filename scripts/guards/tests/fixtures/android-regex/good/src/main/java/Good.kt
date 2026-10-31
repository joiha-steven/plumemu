package fixture

import java.util.regex.Pattern

object Good {
    val escaped = Regex("""\(([^()]*)\)|\[([^\[\]]*)\]|\{([^{}]*)\}""")
    val escapedPlain = Regex("\\]|\\}")
    val quantifiers = Regex("""a{2}b{1,}c{2,5}""")
    val property = Regex("""\p{Mn}+\P{L}\x{41}""")
    val insideClass = Regex("[}{]")
    val nestedClass = Regex("[a[b]]x")
    val quoted = Regex("""\Q]}\E""")
    val template = Regex("${prefix}a{3}")
    val compiled = Pattern.compile("\\[x\\]")
    val notAPattern = "a]".length
    // Regex("a]") in a comment is not code.
    val conversion = "[)]".toRegex()
    private const val prefix = "p"
    val joined = Regex(
        """\(([^()]*)\)|""" +
            """\{([^{}]*)\}""",
        RegexOption.IGNORE_CASE,
    )
    private const val COUNT = """\d{2,4}"""
    private const val CLASS_PART = "}"
    val byName = Regex(COUNT)
    val templated = Regex("""^a$COUNT[$CLASS_PART]${'$'}""")
    val dollarEnd = Regex("\\}$")
    val convertedName = COUNT.toRegex()
}
