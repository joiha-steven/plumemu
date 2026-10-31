package com.plumemu.identify.codec.lzma

import java.util.zip.CRC32

/**
 * The fixtures `src/test/resources/lzma/make_fixtures.py` wrote, as listed in its
 * `manifest.txt`: made-up content compressed by liblzma, never real game data.
 */
internal object LzmaFixtures {

    /** One manifest line: the file, how it is stored, and what it must decode to. */
    data class Fixture(
        val file: String,
        val kind: String,
        val size: Int,
        val crc: Long,
        val extra: Map<String, String>,
    ) {
        val bytes: ByteArray by lazy { resource(file) }

        /** Properties of a raw LZMA1 fixture. */
        val props: LzmaProperties
            get() = LzmaProperties.fromByte(extra.getValue("props").toInt(16), extra.getValue("dict").toLong())

        val dictionaryProp: Int get() = extra.getValue("dictprop").toInt()

        val prefix4096: Long get() = extra.getValue("prefix4096").toLong(16)
    }

    /** Most bytes any fixture decodes to is 200 KB; tests that do not care ask for 1 MB. */
    const val ANY_OUTPUT = 1 shl 20

    val all: List<Fixture> by lazy {
        String(resource("manifest.txt"), Charsets.US_ASCII).lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val fields = line.split(" ")
                Fixture(
                    file = fields[0],
                    kind = fields[1],
                    size = fields[2].toInt(),
                    crc = fields[3].toLong(16),
                    extra = fields.drop(4).associate { it.substringBefore("=") to it.substringAfter("=") },
                )
            }
    }

    fun named(file: String): Fixture = all.single { it.file == file }

    fun ofKind(kind: String): List<Fixture> = all.filter { it.kind == kind }

    fun crc(bytes: ByteArray, length: Int = bytes.size): Long =
        CRC32().apply { update(bytes, 0, length) }.value

    /** Decodes [fixture] the way its kind says, from [bytes] (the fixture's own by default). */
    fun decode(fixture: Fixture, bytes: ByteArray = fixture.bytes, input: CompressedInput = ByteArrayInput(bytes)): ByteArray =
        when (fixture.kind) {
            "alone" -> lzmaAloneDecode(input, ANY_OUTPUT)
            "raw-eopm" -> lzmaDecode(input, fixture.props, ANY_OUTPUT, knownSize = false)
            "raw" -> lzmaDecode(input, fixture.props, fixture.size, knownSize = true)
            "lzma2" -> lzma2Decode(input, fixture.dictionaryProp, ANY_OUTPUT)
            else -> error("unknown fixture kind ${fixture.kind}")
        }

    private fun resource(name: String): ByteArray =
        checkNotNull(LzmaFixtures::class.java.getResourceAsStream("/lzma/$name")) { "missing /lzma/$name" }
            .use { it.readBytes() }
}
