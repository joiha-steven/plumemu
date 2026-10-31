package com.plumemu.scraper

/**
 * Turns the HTML entities ScreenScraper leaves in its text fields (`&quot;`, `&amp;`, `&#39;`, `&#x27;`,
 * `&eacute;`) into the characters. One pass only: `&amp;quot;` becomes the text `&quot;`, never `"`.
 * Pure Kotlin so it works in the JVM tests. Anything that is not a known or valid entity stays as written.
 */
public object HtmlEntities {
    private val entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,9});")

    private val latin1Names = (
        "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg plusmn sup2 sup3 " +
            "acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest Agrave Aacute Acirc Atilde " +
            "Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml ETH Ntilde Ograve Oacute " +
            "Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig agrave aacute acirc atilde " +
            "auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml eth ntilde ograve oacute " +
            "ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml"
        ).split(' ')

    private val named: Map<String, String> = buildMap {
        put("quot", "\"")
        put("amp", "&")
        put("apos", "'")
        put("lt", "<")
        put("gt", ">")
        latin1Names.forEachIndexed { index, name -> put(name, (LATIN1_START + index).toChar().toString()) }
        put("hellip", "\u2026")
        put("ndash", "\u2013")
        put("mdash", "\u2014")
        put("lsquo", "\u2018")
        put("rsquo", "\u2019")
        put("ldquo", "\u201c")
        put("rdquo", "\u201d")
        put("bull", "\u2022")
        put("euro", "\u20ac")
        put("trade", "\u2122")
    }

    private const val LATIN1_START = 0xA0
    private const val MAX_CODE_POINT = 0x10FFFF

    /** Tab, line feed and carriage return; any other control character stays written as an entity, not made invisible. */
    private val ALLOWED_CONTROLS = setOf(9, 10, 13)

    public fun decode(text: String): String =
        if ('&' !in text) text else entity.replace(text) { match -> replacement(match.groupValues[1]) ?: match.value }

    /**
     * The inverse of [decode]: `decode(encode(x)) == x`. The library stores ScreenScraper's text as an entity-encoded
     * column (as the answer gave it) and decodes it once when it is shown.
     */
    public fun encode(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    public fun decodeOrNull(text: String?): String? = text?.let { decode(it) }

    private fun replacement(body: String): String? {
        if (body[0] != '#') return named[body]
        val hex = body[1] == 'x' || body[1] == 'X'
        val code = body.substring(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10) ?: return null
        val control = code < 0x20 && code !in ALLOWED_CONTROLS
        val valid = code in 1..MAX_CODE_POINT && code !in 0xD800..0xDFFF && !control
        return if (valid) String(Character.toChars(code)) else null
    }
}
