package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView

/** A library of every kind of row the panels hold at Largest: a folder to sort and one answered, a system with two emulators, an account with points. */
internal val largestContent: SettingsContent = run {
    val duck = EmulatorEntry(id = "duckstation", name = "DuckStation", isLegalRisk = false)
    val swan = EmulatorEntry(id = "retroarch-swanstation", name = "SwanStation", isLegalRisk = true)
    val folders = listOf(SettingsFolderRow(FolderKey(1, "Misc ROMs"), 12, FolderAnswer.Unanswered), SettingsFolderRow(FolderKey(2, "Old"), 0, FolderAnswer.Ignored))
    val release =
        UpdateManifest(
            versionCode = 41,
            versionName = "0.0.3-alpha",
            apkUrl = "https://example.test/plumemu.apk",
            size = 9_800_000,
            sha256 = "ab".repeat(32),
            minSdk = 23,
            released = "2026-10-23",
            notes = listOf("Updates install from Settings.", "Fixes the grid's first row being cut."),
            notesUrl = null,
        )
    SettingsContent(
        gameCount = 1284,
        systems =
            listOf(
                SystemEmulators("snes", "Super Nintendo", automatic = duck, chosen = null, installed = listOf(duck)),
                SystemEmulators("gba", "Game Boy Advance", automatic = duck, chosen = null, installed = listOf(duck)),
                SystemEmulators("psx", "PlayStation", automatic = duck, chosen = null, installed = listOf(duck, swan)),
            ),
        unknownFolders = folders,
        notAdded = notAddedItems(emptyList(), folders),
        account = AccountStatus.SignedIn("plumtester"),
        raAccount = RaAccountStatus.SignedIn("plumtester", 1234),
        hasUpdates = true,
        update = UpdateView(lastCheck = LastCheck(1_768_471_200_000L, CheckOutcome.Found), found = release),
    )
}
