package com.plumemu.platforms

/** Why a platform data file was rejected. [describe] is what goes into the log line. */
sealed class PlatformFailure(val source: String) {
    abstract fun describe(): String

    class NotJson(source: String, val detail: String) : PlatformFailure(source) {
        override fun describe() = "$source is not valid JSON: $detail"
    }

    /** [path] is the JSON path such as `$.systems[12]`; [id] is the system or emulator there, when known. */
    class MissingField(source: String, val field: String, val path: String, val id: String?) :
        PlatformFailure(source) {
        override fun describe() = "$source is missing field '$field' at $path${idPart(id)}"
    }

    /** A value of the wrong kind, for example a string where a list belongs. */
    class WrongType(source: String, val path: String, val id: String?, val detail: String) : PlatformFailure(source) {
        override fun describe() = "$source has a value of the wrong type at $path${idPart(id)}: $detail"
    }

    class DuplicateEmulatorRef(source: String, val systemId: String, val emulatorId: String) :
        PlatformFailure(source) {
        override fun describe() = "$source: system '$systemId' lists emulator '$emulatorId' twice"
    }

    class UnsupportedVersion(source: String, val found: Int?) : PlatformFailure(source) {
        override fun describe() = "$source has formatVersion $found, this app reads $SUPPORTED_FORMAT_VERSION"
    }

    class DuplicateId(source: String, val kind: String, val id: String) : PlatformFailure(source) {
        override fun describe() = "$source has two ${kind}s with id '$id'"
    }

    class DanglingEmulator(source: String, val systemId: String, val emulatorId: String) : PlatformFailure(source) {
        override fun describe() = "$source: system '$systemId' lists emulator '$emulatorId' which is not defined"
    }

    class EmptyField(source: String, val field: String, val id: String) : PlatformFailure(source) {
        override fun describe() = "$source: '$field' of '$id' is empty"
    }

    class BadValue(source: String, val field: String, val id: String, val value: String) : PlatformFailure(source) {
        override fun describe() = "$source: '$field' of '$id' has invalid value '$value'"
    }

    class DuplicateDisplayName(source: String, val displayName: String, val firstSystemId: String, val secondSystemId: String) :
        PlatformFailure(source) {
        override fun describe() =
            "$source: displayName '$displayName' belongs to both '$firstSystemId' and '$secondSystemId'"
    }

    class DuplicateOrder(source: String, val order: Int, val firstSystemId: String, val secondSystemId: String) :
        PlatformFailure(source) {
        override fun describe() = "$source: order $order belongs to both '$firstSystemId' and '$secondSystemId'"
    }

    /** A `knownApps` entry the data cannot stand behind; [packageName] is the key, [detail] says why. */
    class BadKnownApp(source: String, val packageName: String, val detail: String) : PlatformFailure(source) {
        override fun describe() = "$source: knownApps entry '$packageName' $detail"
    }

    class DuplicateFolder(source: String, val folder: String, val firstSystemId: String, val secondSystemId: String) :
        PlatformFailure(source) {
        override fun describe() = "$source: folder '$folder' belongs to both '$firstSystemId' and '$secondSystemId'"
    }
}

internal const val SUPPORTED_FORMAT_VERSION = 2

private fun idPart(id: String?) = if (id == null) "" else " (id '$id')"

sealed interface PlatformLoadResult {
    data class Loaded(val catalog: PlatformCatalog) : PlatformLoadResult
    data class Failed(val failure: PlatformFailure) : PlatformLoadResult
}
