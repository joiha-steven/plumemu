package com.plumemu.identify.fingerprint

/**
 * The CHD hunks and 7z archives `src/test/resources/containers/make_fixtures.py` wrote, as listed
 * in its `manifest.txt`: made-up content only, never real game data.
 */
internal object ContainerFixtures {

    /** One file of the manifest: its kind, its `key=value` fields and, for a 7z, its entries. */
    data class Fixture(val file: String, val kind: String, val fields: Map<String, String>, val entries: List<Entry>) {
        val bytes: ByteArray by lazy { resource(file) }

        fun int(key: String): Int = fields.getValue(key).toInt()

        fun crc(key: String = "crc"): Long = fields.getValue(key).toLong(16)

        /** The note an unread header must give, if the script says there is one. */
        val note: String? get() = fields["note"]?.replace('_', ' ')
    }

    /** One file or directory the script put in a 7z. */
    data class Entry(val name: String, val size: Long, val crc: Long, val isDirectory: Boolean)

    val all: List<Fixture> by lazy {
        val fixtures = ArrayList<Fixture>()
        for (line in String(resource("manifest.txt"), Charsets.US_ASCII).lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            if (line.startsWith("entry ")) {
                val (name, size, crc, kind) = line.removePrefix("entry ").split("|")
                val last = fixtures.removeAt(fixtures.size - 1)
                fixtures += last.copy(entries = last.entries + Entry(unescape(name), size.toLong(), crc.toLong(16), kind == "dir"))
                continue
            }
            val fields = line.split(" ")
            val values = fields.drop(2).associate { it.substringBefore("=") to it.substringAfter("=") }
            fixtures += Fixture(fields[0], fields[1], values, emptyList())
        }
        fixtures
    }

    fun named(file: String): Fixture = all.single { it.file == file }

    fun ofKind(kind: String): List<Fixture> = all.filter { it.kind == kind }

    /** The manifest writes a letter outside ASCII as `\uXXXX` (the english-only guard reads it). */
    private fun unescape(text: String): String =
        Regex("""\\u([0-9a-f]{4})""").replace(text) { it.groupValues[1].toInt(16).toChar().toString() }

    private fun resource(name: String): ByteArray =
        checkNotNull(ContainerFixtures::class.java.getResourceAsStream("/containers/$name")) { "missing /containers/$name" }
            .use { it.readBytes() }
}
