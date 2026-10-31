package com.plumemu.platforms

// Helpers for the expected launch specs; the data is in ExpectedLaunches*.kt.

internal class ExpectedEntry(val key: String, val spec: LaunchSpec)

internal class ExpectedSystem(val id: String, val path: String, val uri: String, val entries: List<ExpectedEntry>)

internal class SystemBuilder(val id: String, extension: String) {
    val PATH = "/storage/emulated/0/ROMs/$id/Game.$extension"
    val URI = "content://com.android.externalstorage.documents/document/primary%3AROMs%2F$id%2FGame.$extension"
    val entries = mutableListOf<ExpectedEntry>()

    fun standalone(
        key: String,
        packageName: String,
        className: String?,
        action: String?,
        category: String?,
        data: String?,
        mime: String?,
        flags: Set<LaunchFlag>,
        extras: Map<String, ExtraValue>,
    ) {
        entries += ExpectedEntry(key, LaunchSpec(packageName, className, action, category, data, mime, extras, flags))
    }

    /** RetroArch 64-bit; [core] is the libretro file name, which is also the key. */
    fun retroArch(core: String) {
        val pkg = "com.retroarch.aarch64"
        val extras = mapOf(
            "CONFIGFILE" to ExtraValue.Text("/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"),
            "LIBRETRO" to ExtraValue.Text("/data/data/$pkg/cores/$core"),
            "ROM" to ExtraValue.Text(PATH),
        )
        entries += ExpectedEntry(
            core,
            LaunchSpec(
                pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture",
                null, null, null, null, extras, emptySet(),
            ),
        )
    }
}

internal fun system(id: String, extension: String, body: SystemBuilder.() -> Unit): ExpectedSystem {
    val builder = SystemBuilder(id, extension).apply(body)
    return ExpectedSystem(id, builder.PATH, builder.URI, builder.entries)
}
