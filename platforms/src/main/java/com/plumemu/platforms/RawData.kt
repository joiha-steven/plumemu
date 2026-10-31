package com.plumemu.platforms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

// The file's shape as written by tools/platforms/convert.py. Values stay strings here so the
// loader can say which field and id are wrong instead of failing inside the decoder.

@Serializable
internal class RawCatalog(
    val formatVersion: Int,
    val systems: List<RawSystem>,
    val emulators: List<RawEmulator>,
    /** Optional: installed app package name to the id of the system it belongs to (`tables.py` `KNOWN_APPS`). */
    val knownApps: Map<String, String> = emptyMap(),
)

@Serializable
internal class RawSystem(
    val id: String,
    val name: String,
    val shortName: String,
    val displayName: String,
    val order: Int,
    val folders: List<String>,
    val extensions: List<String>,
    val cover: String,
    val tier: String,
    val emulators: List<String>,
    /** Optional: who made the system (absent for engines, stores and emulators). */
    val maker: String? = null,
    /** Optional: the year it first went on sale anywhere. */
    val year: Int? = null,
    /** Optional: `console`, `handheld`, `arcade`, `computer`, `add-on` or `os`; only with a maker. */
    val kind: String? = null,
    /** Optional: the year its maker stopped making it; absent while still made or when unknown. */
    val endYear: Int? = null,
    /** Optional: true while its maker still makes it; never together with [endYear]. */
    val inProduction: Boolean? = null,
    /** Optional: a measured box front's width over height (`tools/platforms/tables.py` `COVER_RATIOS`). */
    val coverRatio: Double? = null,
    /** Optional: the system's own colour as `#RRGGBB` (`tables.py` `SYSTEM_COLORS`), for Accent color > By system. */
    val color: String? = null,
    /** The short name for mixed lists ("SNES"); the converter gives every system one. */
    val abbr: String = "",
    /** The three-character code for the home screen's left edge (`tables.py` `CODE`); the converter gives every system one. */
    val code: String = "",
    /** Optional: `apps` marks a system made of installed apps; it alone may have no folders, extensions or emulators. */
    val source: String? = null,
)

@Serializable
internal class RawGame(
    val mode: String,
    val target: String,
    val key: String? = null,
    val prefix: List<String> = emptyList(),
    /** Only for mode `steam_app_id` with target `extra`: `int` or `string`. */
    val type: String? = null,
    /** Only for mode `steam_app_id` with target `data`: the data with `{id}` where the id goes. */
    val template: String? = null,
    /** Only for mode `uri`: lower-case extensions (no dot) the emulator can open only by file path, never by URI. */
    val pathExtensions: List<String> = emptyList(),
)

@Serializable
internal class RawVariant(
    @SerialName("package") val packageName: String,
    val activity: String? = null,
    val bits: Int? = null,
)

@Serializable
internal class RawEmulator(
    val id: String,
    val name: String,
    @SerialName("package") val packageName: String,
    val activity: String? = null,
    val bits: Int? = null,
    val variants: List<RawVariant> = emptyList(),
    val core: String? = null,
    val action: String? = null,
    val category: String? = null,
    val mimeType: String? = null,
    val flags: List<String> = emptyList(),
    val game: RawGame,
    val extras: Map<String, JsonPrimitive> = emptyMap(),
    val legalRisk: Boolean = false,
    /** Optional: the extensions (of its systems) it can open; absent means every extension of its systems. */
    val accepts: List<String>? = null,
)
