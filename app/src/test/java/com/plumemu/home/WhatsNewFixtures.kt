package com.plumemu.home

import com.plumemu.update.NoteSection
import com.plumemu.update.SectionKind
import com.plumemu.update.UpdateManifest

/** Releases for the update notice's tests and shots: all four groups, and notes only (an old manifest). */
internal object WhatsNewFixtures {
    private fun release(
        notes: List<String>,
        sections: List<NoteSection>,
    ) = UpdateManifest(
        versionCode = 12,
        versionName = "0.1.1",
        apkUrl = "https://plumemu-app.stevensdesk.com/update/plumemu-0.1.1-12.apk",
        size = 9_800_000,
        sha256 = "ab".repeat(32),
        minSdk = 23,
        released = "2026-11-01",
        notes = notes,
        notesUrl = null,
        sections = sections,
    )

    val grouped =
        release(
            notes = listOf("Update notice on the main screen.", "Faster scans.", "Box art no longer flickers.", "Some themes clip long names."),
            sections =
                listOf(
                    NoteSection(SectionKind.New, listOf("A notice on the main screen when an update is waiting.", "What's new after an update.")),
                    NoteSection(SectionKind.Improved, listOf("Scans of large folders are faster.")),
                    NoteSection(SectionKind.Fixed, listOf("Box art no longer flickers while scrolling a long list in a very large text size.")),
                    NoteSection(SectionKind.Known, listOf("A few themes clip very long system names.")),
                ),
        )

    val notesOnly = release(notes = listOf("Update notice on the main screen.", "Faster scans.", "Box art no longer flickers."), sections = emptyList())
}
