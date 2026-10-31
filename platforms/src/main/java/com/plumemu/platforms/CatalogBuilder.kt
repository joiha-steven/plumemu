package com.plumemu.platforms

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal class RejectedException(val failure: PlatformFailure) : Exception(failure.describe())

internal const val CORE_PLACEHOLDER = "{core}"

private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)*$")
private val COLOR_HEX = Regex("^#[0-9A-Fa-f]{6}$")
private const val HEX_RADIX = 16

/** How long a system code is (`tools/platforms/tables.py` `CODE_LENGTH`). */
private const val CODE_LENGTH = 3
/** The system that also holds installed native port apps. */
private const val PORTS_ID = "ports"

/** The `source` value of a system made of installed apps. */
private const val APPS_SOURCE = "apps"
private val ID = Regex("^[a-z0-9][a-z0-9._-]*$")

/** Checks a decoded file and turns it into models; the first problem found is thrown as [RejectedException]. */
internal class CatalogBuilder(private val source: String) {

    fun build(raw: RawCatalog): PlatformCatalog {
        if (raw.systems.isEmpty()) reject(PlatformFailure.EmptyField(source, "systems", "(file)"))
        if (raw.emulators.isEmpty()) reject(PlatformFailure.EmptyField(source, "emulators", "(file)"))
        val emulators = raw.emulators.map { emulator(it) }
        requireUnique("emulator", emulators.map { it.id })
        val emulatorIds = emulators.map { it.id }.toSet()
        val systems = raw.systems.map { system(it) }
        requireUnique("system", systems.map { it.id })
        val folderOwners = HashMap<String, String>()
        val displayNameOwners = HashMap<String, String>()
        val orderOwners = HashMap<Int, String>()
        for (system in systems) {
            val nameOwner = displayNameOwners.getOrPut(system.displayName) { system.id }
            if (nameOwner != system.id) {
                reject(PlatformFailure.DuplicateDisplayName(source, system.displayName, nameOwner, system.id))
            }
            val orderOwner = orderOwners.getOrPut(system.order) { system.id }
            if (orderOwner != system.id) reject(PlatformFailure.DuplicateOrder(source, system.order, orderOwner, system.id))
            val listed = HashSet<String>()
            for (id in system.emulatorIds) {
                if (id !in emulatorIds) reject(PlatformFailure.DanglingEmulator(source, system.id, id))
                if (!listed.add(id)) reject(PlatformFailure.DuplicateEmulatorRef(source, system.id, id))
            }
            for (folder in system.folders) {
                val owner = folderOwners.getOrPut(folder) { system.id }
                if (owner != system.id) reject(PlatformFailure.DuplicateFolder(source, folder, owner, system.id))
            }
        }
        // An accepted extension must be a file of one of the emulator's systems: otherwise the
        // emulator would be offered for nothing, or for files the scan never gives it.
        for (emulator in emulators) {
            val accepts = emulator.accepts ?: continue
            val extensions = systems.filter { emulator.id in it.emulatorIds }.flatMapTo(HashSet()) { it.extensions }
            accepts.firstOrNull { it !in extensions }?.let { badValue("accepts", emulator.id, it) }
        }
        val systemIds = systems.map { it.id }.toSet()
        for ((packageName, systemId) in raw.knownApps) {
            if (!PACKAGE_NAME.matches(packageName)) reject(PlatformFailure.BadKnownApp(source, packageName, "is not a package name"))
            if (systemId !in systemIds) reject(PlatformFailure.BadKnownApp(source, packageName, "names system '$systemId' which does not exist"))
            // Only an installed-apps system or `ports` can hold an installed app; a file system such as snes cannot.
            val target = systems.first { it.id == systemId }
            if (!target.fromInstalledApps && systemId != PORTS_ID) {
                reject(PlatformFailure.BadKnownApp(source, packageName, "names system '$systemId', which is neither an apps system nor '$PORTS_ID'"))
            }
        }
        return PlatformCatalog(raw.formatVersion, systems, emulators, raw.knownApps)
    }

    private fun system(raw: RawSystem): GameSystem {
        requireId("systems.id", raw.id, raw.id)
        requireNotBlank("name", raw.id, raw.name)
        requireNotBlank("shortName", raw.id, raw.shortName)
        requireNotBlank("displayName", raw.id, raw.displayName)
        val fromApps = when (raw.source) {
            null -> false
            APPS_SOURCE -> true
            else -> badValue("source", raw.id, raw.source)
        }
        // Games that are installed apps are not files: no folder to scan, no extension, no emulator to start them.
        if (fromApps) {
            if (raw.folders.isNotEmpty()) badValue("folders", raw.id, "an apps system has none")
            if (raw.extensions.isNotEmpty()) badValue("extensions", raw.id, "an apps system has none")
            if (raw.emulators.isNotEmpty()) badValue("emulators", raw.id, "an apps system has none")
        } else {
            requireNotEmpty("folders", raw.id, raw.folders)
            requireNotEmpty("extensions", raw.id, raw.extensions)
            requireNotEmpty("emulators", raw.id, raw.emulators)
        }
        raw.folders.forEach { if (it.isBlank() || it != it.lowercase()) badValue("folders", raw.id, it) }
        raw.extensions.forEach {
            if (it.isBlank() || it != it.lowercase() || it.startsWith(".")) badValue("extensions", raw.id, it)
        }
        val cover = when (raw.cover) {
            "landscape" -> CoverShape.Landscape
            "portrait" -> CoverShape.Portrait
            else -> badValue("cover", raw.id, raw.cover)
        }
        raw.coverRatio?.let { ratio ->
            // A measured ratio decides the class: the converter writes `cover` from it.
            val landscape = ratio >= CoverShape.LANDSCAPE_MIN_RATIO
            if (ratio !in CoverShape.RATIO_RANGE || landscape != (cover == CoverShape.Landscape)) {
                badValue("coverRatio", raw.id, "$ratio with cover ${raw.cover}")
            }
        }
        val tier = when (raw.tier) {
            "tested" -> Tier.Tested
            "untested" -> Tier.Untested
            else -> badValue("tier", raw.id, raw.tier)
        }
        val kind = raw.kind?.let { SystemKind.fromCode(it) ?: badValue("kind", raw.id, it) }
        // Facts for the home screen's system info: they describe a made thing, so they need a maker and a year.
        if (kind != null && raw.maker.isNullOrBlank()) badValue("kind", raw.id, "${raw.kind} without a maker")
        if (kind != null && raw.year == null) badValue("kind", raw.id, "${raw.kind} without a year")
        raw.endYear?.let { end ->
            val year = raw.year ?: badValue("endYear", raw.id, "$end without a year")
            // No clock check here: a handheld with a dead clock must still load the shipped file (the converter caps the year).
            if (end < year) badValue("endYear", raw.id, "$end with year $year")
        }
        if (raw.inProduction != null) {
            if (raw.inProduction == false) badValue("inProduction", raw.id, "false (leave it out)")
            if (raw.year == null) badValue("inProduction", raw.id, "without a year")
            if (raw.maker.isNullOrBlank() || kind == null) badValue("inProduction", raw.id, "without a maker and a kind")
            if (raw.endYear != null) badValue("inProduction", raw.id, "together with endYear ${raw.endYear}")
        }
        val color = raw.color?.let { hex ->
            if (!COLOR_HEX.matches(hex)) badValue("color", raw.id, hex)
            hex.drop(1).toInt(HEX_RADIX)
        }
        return GameSystem(
            raw.id, raw.name, raw.shortName, raw.displayName, raw.order, raw.folders, raw.extensions, cover, tier, raw.emulators,
            // Optional facts for Settings › Library › System order; a blank maker counts as none.
            maker = raw.maker?.takeIf { it.isNotBlank() },
            year = raw.year,
            kind = kind,
            endYear = raw.endYear,
            inProduction = raw.inProduction == true,
            coverRatio = raw.coverRatio ?: cover.defaultRatio,
            abbr = raw.abbr,
            code = raw.code.ifEmpty { raw.abbr.uppercase().take(CODE_LENGTH) },
            color = color,
            fromInstalledApps = fromApps,
        )
    }

    private fun emulator(raw: RawEmulator): Emulator {
        requireId("emulators.id", raw.id, raw.id)
        requireNotBlank("name", raw.id, raw.name)
        val primary = variant(raw.id, raw.packageName, raw.activity, raw.bits)
        val variants = raw.variants.map { variant(raw.id, it.packageName, it.activity, it.bits) }
        val mode = when (raw.game.mode) {
            "path" -> PassMode.Path
            "uri" -> PassMode.Uri
            "vita_title_id" -> PassMode.VitaTitleId
            "steam_app_id" -> PassMode.SteamAppId
            else -> badValue("game.mode", raw.id, raw.game.mode)
        }
        val target = when (raw.game.target) {
            "data" -> GameTarget.Data
            "extra" -> GameTarget.Extra
            "extra_array" -> GameTarget.ExtraArray
            else -> badValue("game.target", raw.id, raw.game.target)
        }
        if ((target != GameTarget.Data) != (raw.game.key != null)) {
            badValue("game.key", raw.id, raw.game.key ?: "(missing)")
        }
        if (raw.game.prefix.isNotEmpty() && target != GameTarget.ExtraArray) {
            badValue("game.prefix", raw.id, "only for target extra_array")
        }
        raw.game.prefix.forEach { if (it.isBlank()) badValue("game.prefix", raw.id, "(blank)") }
        // A title ID is only ever passed in an array extra (Vita3K's command line), and an array extra
        // only carries a title ID; any other pair has no launch behind it.
        if ((mode == PassMode.VitaTitleId) != (target == GameTarget.ExtraArray)) {
            badValue("game", raw.id, "mode ${raw.game.mode} with target ${raw.game.target}")
        }
        val accepts = raw.accepts?.let { list ->
            if (list.isEmpty()) badValue("accepts", raw.id, "(empty)")
            list.forEach { if (it.isBlank() || it != it.lowercase() || it.startsWith(".")) badValue("accepts", raw.id, it) }
            if (list.toSet().size != list.size) badValue("accepts", raw.id, list.joinToString(","))
            list.toSet()
        }
        val idType = idTypeOf(raw, mode, target)
        val template = templateOf(raw, mode, target)
        val pathExtensions = raw.game.pathExtensions
        if (pathExtensions.isNotEmpty() && mode != PassMode.Uri) badValue("game.pathExtensions", raw.id, "only for mode uri")
        pathExtensions.forEach { if (it.isBlank() || it != it.lowercase() || it.startsWith(".")) badValue("game.pathExtensions", raw.id, it) }
        val flags = raw.flags.map {
            when (it) {
                "clear_task" -> LaunchFlag.ClearTask
                "clear_top" -> LaunchFlag.ClearTop
                "no_history" -> LaunchFlag.NoHistory
                else -> badValue("flags", raw.id, it)
            }
        }.toSet()
        val extras = raw.extras.mapValues { (key, value) -> extraValue(raw.id, key, value) }
        val core = raw.core
        // The core name ends up inside a file path, so it must stay a plain file name.
        if (core != null && (core.isBlank() || '/' in core || ".." in core)) badValue("core", raw.id, core)
        if (core == null && extras.values.any { it is ExtraValue.Text && CORE_PLACEHOLDER in it.value }) {
            badValue("extras", raw.id, "uses $CORE_PLACEHOLDER but the emulator has no core")
        }
        return Emulator(
            raw.id, raw.name, primary, variants, core, raw.action, raw.category, raw.mimeType,
            flags, GamePass(mode, target, raw.game.key, raw.game.prefix, idType, template, pathExtensions.toSet()), extras, raw.legalRisk, accepts,
        )
    }

    /**
     * A Steam app id goes in an extra of a declared type or into the data through a template; no
     * other pair has a launch behind it, and no other mode takes a type or a template.
     */
    private fun idTypeOf(raw: RawEmulator, mode: PassMode, target: GameTarget): IdType? {
        val type = raw.game.type
        val wanted = mode == PassMode.SteamAppId && target == GameTarget.Extra
        if (!wanted) {
            if (type != null) badValue("game.type", raw.id, "only for mode steam_app_id with target extra")
            if (mode == PassMode.SteamAppId && target != GameTarget.Data) {
                badValue("game", raw.id, "mode ${raw.game.mode} with target ${raw.game.target}")
            }
            return null
        }
        return when (type) {
            "int" -> IdType.Number
            "string" -> IdType.Text
            else -> badValue("game.type", raw.id, type ?: "(missing)")
        }
    }

    private fun templateOf(raw: RawEmulator, mode: PassMode, target: GameTarget): String? {
        val template = raw.game.template
        if (mode != PassMode.SteamAppId || target != GameTarget.Data) {
            if (template != null) badValue("game.template", raw.id, "only for mode steam_app_id with target data")
            return null
        }
        // One place for the id, and nothing else to fill: a template the launcher cannot complete
        // would start the app with a broken link.
        if (template == null || template.isBlank() || template.split(GamePass.ID_PLACEHOLDER).size != 2) {
            badValue("game.template", raw.id, template ?: "(missing)")
        }
        return template
    }

    private fun variant(emulatorId: String, packageName: String, activity: String?, bits: Int?): PackageVariant {
        if (!PACKAGE_NAME.matches(packageName)) badValue("package", emulatorId, packageName)
        if (activity != null && !CLASS_NAME.matches(activity)) badValue("activity", emulatorId, activity)
        if (bits != null && bits != 32 && bits != 64) badValue("bits", emulatorId, bits.toString())
        return PackageVariant(packageName, activity, bits)
    }

    private fun extraValue(emulatorId: String, key: String, value: JsonPrimitive): ExtraValue = when {
        value.isString -> ExtraValue.Text(value.content)
        value.booleanOrNull != null -> ExtraValue.Flag(value.booleanOrNull == true)
        value.intOrNull != null -> ExtraValue.Number(value.intOrNull ?: 0)
        else -> badValue("extras.$key", emulatorId, value.content)
    }

    private fun requireId(field: String, id: String, value: String) {
        if (!ID.matches(value)) badValue(field, id, value)
    }

    private fun requireNotBlank(field: String, id: String, value: String) {
        if (value.isBlank()) reject(PlatformFailure.EmptyField(source, field, id))
    }

    private fun requireNotEmpty(field: String, id: String, values: List<String>) {
        if (values.isEmpty()) reject(PlatformFailure.EmptyField(source, field, id))
    }

    private fun requireUnique(kind: String, ids: List<String>) {
        val seen = HashSet<String>()
        for (id in ids) if (!seen.add(id)) reject(PlatformFailure.DuplicateId(source, kind, id))
    }

    private fun badValue(field: String, id: String, value: String): Nothing =
        reject(PlatformFailure.BadValue(source, field, id, value))

    private fun reject(failure: PlatformFailure): Nothing = throw RejectedException(failure)
}
