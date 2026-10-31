package com.plumemu.graph

import android.content.Context
import android.util.Log
import com.plumemu.identify.Identifier
import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.android.AssetBundledPacks
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.pack.PackStore
import com.plumemu.library.scan.GameNaming
import java.io.File

/**
 * The process's one [PackStore] and [Identifier] ("Setup"):
 * packs unpack from the APK's assets into no-backup storage the first time a scan needs them
 * (they are rebuilt from the APK, so they need no backup). Building this reads nothing.
 * Lines name system ids and exception classes only.
 */
internal fun gameNamingFor(context: Context): GameNaming {
    val app = context.applicationContext
    val log = IdentifyLog { Log.i(IDENTIFY_TAG, it) }
    val packs = PackStore(File(app.noBackupFilesDir, PACK_DIRECTORY), AssetBundledPacks(app.assets), log)
    return GameNaming(packs, Identifier(packs, FingerprintReader(), log))
}

private const val IDENTIFY_TAG = "PlumIdentify"
private const val PACK_DIRECTORY = "identify-packs"
