package fixture

import java.util.regex.Pattern

object Bad {
    // The pattern that broke every scan on Android 6 (TitleCleaner before aecc40d).
    val bracketGroup = Regex("""\(([^()]*)\)|\[([^\[\]]*)]|\{([^{}]*)}""") // BAD
    val closingSquare = Regex("""\[(x)]""") // WARN
    val closingBrace = Regex("a}") // BAD
    val escapedThenStray = Regex("\\]]") // WARN
    val named = Regex(pattern = "b]", option = RegexOption.IGNORE_CASE) // WARN
    val compiled = Pattern.compile("x]") // WARN
    val converted = """\{([^{}]*)}""".toRegex() // BAD
    val convertedPlain = "y}".toPattern() // BAD
    val afterClass = Regex("[a-z]+]") // WARN
    val notAQuantifier = Regex("""a{x}""") // BAD
    val multiLine = Regex(
        """(\d+)]""", // WARN
    )
    val joined = Regex(
        """\(([^()]*)\)|""" +
            """\{([^{}]*)}""", // BAD
        RegexOption.IGNORE_CASE,
    )
    private const val GROUP = """\{([^{}]*)}""" // BAD
    val byName = Regex(GROUP)
    private const val TAIL = "z}" // BAD
    val templated = Regex("""^a$TAIL${'$'}""")
    private val SUFFIX = "w}" // BAD
    val convertedName = SUFFIX.toRegex()
}
