package com.plumemu.platforms

/** Where a game is. A scan knows the document URI always and the file path when it can work it out. */
sealed interface GameLocation {
    data class Path(val path: String) : GameLocation
    data class Uri(val uri: String) : GameLocation
    data class Both(val path: String, val uri: String) : GameLocation
}

/**
 * Everything needed to start an emulator, as plain data. The launcher turns it into an Android Intent;
 * keeping it free of android.content.Intent lets JVM tests compare it exactly.
 */
data class LaunchSpec(
    val packageName: String,
    val className: String?,
    val action: String?,
    val category: String?,
    /** The game, when the emulator takes it as Intent data (a path or a URI, as text). */
    val data: String?,
    val mimeType: String?,
    val extras: Map<String, ExtraValue>,
    val flags: Set<LaunchFlag>,
)

sealed interface LaunchResult {
    data class Built(val spec: LaunchSpec) : LaunchResult

    /** The emulator wants a file path but the game only has a URI (for example on a USB drive). */
    data class NeedsFilePath(val emulatorId: String) : LaunchResult

    /** The emulator wants a document URI but the game only has a path. */
    data class NeedsDocumentUri(val emulatorId: String) : LaunchResult

    /** The emulator starts a PS Vita title ID and none was given: the launcher reads it from the game file first. */
    data class NeedsVitaTitleId(val emulatorId: String) : LaunchResult

    /** The emulator starts a Steam app id and none was given: the launcher reads it from the game file first. */
    data class NeedsSteamAppId(val emulatorId: String) : LaunchResult

    /** [packageName] is not one of the emulator's packages. */
    data class UnknownPackage(val emulatorId: String, val packageName: String) : LaunchResult
}

private const val PACKAGE_PLACEHOLDER = "{package}"

object LaunchSpecs {
    /**
     * [installedPackage] picks one of the emulator's packages; null uses the preferred one.
     * [vitaTitleId] is the content of the game's `.psvita` file, needed only by [PassMode.VitaTitleId] emulators;
     * [steamAppId] the content of its `.steam` file, needed only by [PassMode.SteamAppId] ones.
     */
    fun build(
        emulator: Emulator,
        location: GameLocation,
        installedPackage: String? = null,
        vitaTitleId: VitaTitleId? = null,
        steamAppId: SteamAppId? = null,
    ): LaunchResult {
        val variant = if (installedPackage == null) emulator.primary else
            emulator.allPackages.firstOrNull { it.packageName == installedPackage }
                ?: return LaunchResult.UnknownPackage(emulator.id, installedPackage)
        val game = when (emulator.game.mode) {
            PassMode.Path -> pathOf(location) ?: return LaunchResult.NeedsFilePath(emulator.id)
            PassMode.Uri -> pathForExtension(emulator.game, location)
                ?: uriOf(location) ?: return LaunchResult.NeedsDocumentUri(emulator.id)
            PassMode.VitaTitleId -> vitaTitleId?.value ?: return LaunchResult.NeedsVitaTitleId(emulator.id)
            PassMode.SteamAppId -> steamAppId?.text ?: return LaunchResult.NeedsSteamAppId(emulator.id)
        }
        val extras = LinkedHashMap<String, ExtraValue>()
        for ((key, value) in emulator.extras) extras[key] = fill(value, variant.packageName, emulator.core)
        var data: String? = null
        when (emulator.game.target) {
            // A template (DroidDeck's droiddeck://game/{id}) takes the id; otherwise the data is the game.
            GameTarget.Data -> data = emulator.game.template?.replace(GamePass.ID_PLACEHOLDER, game) ?: game
            GameTarget.Extra -> extras[emulator.game.extraKey.orEmpty()] = when (emulator.game.idType) {
                // GameNative reads app_id with getIntExtra: a String extra would read as 0.
                IdType.Number -> ExtraValue.Number(steamAppId?.value ?: return LaunchResult.NeedsSteamAppId(emulator.id))
                IdType.Text, null -> ExtraValue.Text(game)
            }
            GameTarget.ExtraArray -> extras[emulator.game.extraKey.orEmpty()] = ExtraValue.TextArray(emulator.game.prefix + game)
        }
        return LaunchResult.Built(
            LaunchSpec(
                variant.packageName, variant.activity, emulator.action, emulator.category, data,
                emulator.mimeType, extras, emulator.flags,
            ),
        )
    }

    private fun pathOf(location: GameLocation): String? = when (location) {
        is GameLocation.Path -> location.path
        is GameLocation.Both -> location.path
        is GameLocation.Uri -> null
    }

    /** The path of a file this URI emulator opens only by path ([GamePass.pathExtensions]); null when it is not one or the path is unknown. */
    private fun pathForExtension(game: GamePass, location: GameLocation): String? {
        val path = pathOf(location) ?: return null
        return path.takeIf { path.substringAfterLast('.', "").lowercase() in game.pathExtensions }
    }

    private fun uriOf(location: GameLocation): String? = when (location) {
        is GameLocation.Uri -> location.uri
        is GameLocation.Both -> location.uri
        is GameLocation.Path -> null
    }

    private fun fill(value: ExtraValue, packageName: String, core: String?): ExtraValue =
        if (value is ExtraValue.Text) {
            val filled = value.value.replace(PACKAGE_PLACEHOLDER, packageName).replace(CORE_PLACEHOLDER, core.orEmpty())
            ExtraValue.Text(filled)
        } else {
            value
        }
}
