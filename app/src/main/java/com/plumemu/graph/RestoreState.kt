package com.plumemu.graph

import com.plumemu.coredata.account.ScraperAccount
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coredata.backup.RestoreRefusal
import java.io.InputStream

/** Why Restore… did not go ahead; nothing on the device changed. */
internal enum class RestoreProblem {
    NotABackup,
    NewerFormat,
    NewerDatabase,
    Torn,
    BadDatabase,
    BadSettings,
    BadAccount,
    NoSpace,

    /** The picked file could not be opened or read (the provider failed, the file went away). */
    Unreadable,

    /** A backup runs: it reads the library a restore would replace. */
    BackupRunning,

    /** Clear box art or the box-art sweep is deleting pictures a restore would swap. */
    BoxArtBusy,

    /** A scan runs (or waits): it writes the library a restore would replace. */
    ScanRunning,

    /** A Settings file while an Everything way-back set is kept: that set is the person's earlier library. */
    EarlierRestoreKept,

    /** The box-art run or a write did not stop in time before the swap: nothing moved. */
    LibraryBusy,
    ;

    companion object {
        fun of(refusal: RestoreRefusal): RestoreProblem = valueOf(refusal.name)
    }
}

/** What the file does with the ScreenScraper account, as the confirm panel says it. */
internal enum class AccountInFile {
    /** None in the file (or a build without box art): the device's own stays. */
    None,

    /** Signed in with the file's name and password, encrypted with this device's key. */
    SignsIn,

    /** The file has no usable password: signed out, the person signs in again. */
    SignInAgain,
}

/** The confirm panel's facts, from the manifest: what the file holds and when it was made. */
internal data class RestoreSummary(
    val kind: BackupKind,
    val fromMillis: Long,
    val games: Int,
    val pictures: Int,
    val account: AccountInFile,
    /** Box-art entries of the file that were damaged and are left out (BackupUnpack). */
    val damagedMediaFiles: Int = 0,
)

/** The way back kept by the last restore: what it holds, when the restore ran, and the bytes it keeps. */
internal data class WayBackSet(
    val kind: BackupKind,
    val restoredAtMillis: Long,
    val bytes: Long,
)

/** Where Settings › Advanced › Restore… and Undo last restore are. */
internal sealed interface RestoreState {
    data object Idle : RestoreState

    /** The file is being read and checked; [progress] counts box-art files once they start. */
    data class Reading(val progress: BackupProgress? = null) : RestoreState

    /** Read and checked, nothing changed yet: the person confirms or cancels. */
    data class Confirm(val summary: RestoreSummary) : RestoreState

    /** Replacing (a restore, or an Undo); with the library the app restarts at the end. */
    data class Replacing(
        val isUndo: Boolean,
        val withLibrary: Boolean,
    ) : RestoreState

    data class Refused(val problem: RestoreProblem) : RestoreState

    /** [damagedMediaFiles]: pictures of the file that were damaged and left out; the line says how many. */
    data class Done(
        val isUndo: Boolean,
        val kind: BackupKind,
        val fromMillis: Long,
        val damagedMediaFiles: Int = 0,
    ) : RestoreState

    /** Replacing failed; [isPutBack] says whether everything was put back as it was. */
    data class Failed(
        val isUndo: Boolean,
        val isPutBack: Boolean,
    ) : RestoreState

    /** A process death stopped the last one; it was finished at this start, or put back. */
    data class Interrupted(val isFinished: Boolean) : RestoreState
}

/** The file the person picked in Android's open picker. */
internal fun interface RestoreInput {
    /** Throws when the provider cannot give the file. */
    fun open(): InputStream
}

/** The ScreenScraper account as a restore needs it ([GraphScraperAccounts] is the graph's). Never logs a value. */
internal interface RestoreAccounts {
    /** False in a build without box art: no account is read or written. */
    val isAvailable: Boolean

    /** The stored account exactly (its ciphertext); null when signed out. Throws on a read error. */
    suspend fun storedForRestore(): ScraperAccount?

    /** A backup's account encrypted with this device's key (the sign-in's own store path). */
    suspend fun encryptForRestore(account: BackupAccount): ScraperAccount

    /** Stores exactly [account], or signs out for null. Throws when it cannot be written. */
    suspend fun putForRestore(account: ScraperAccount?)
}
