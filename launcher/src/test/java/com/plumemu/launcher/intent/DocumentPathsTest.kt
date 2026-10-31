package com.plumemu.launcher.intent

import com.plumemu.launcher.OWNER_STORAGE
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentPathsTest {
    private val storage = DocumentPaths.EXTERNAL_STORAGE_AUTHORITY

    private val cases = listOf(
        // authority, document id, expected path (null: no path, only URI emulators)
        Triple(storage, "primary:ROMs/snes/x.sfc", "/storage/emulated/0/ROMs/snes/x.sfc"),
        Triple(storage, "primary:x.sfc", "/storage/emulated/0/x.sfc"),
        Triple(storage, "primary:", "/storage/emulated/0"),
        Triple(storage, "primary:ROMs/psx/Final Fantasy VII (Disc 1).chd", "/storage/emulated/0/ROMs/psx/Final Fantasy VII (Disc 1).chd"),
        Triple(storage, "primary:ROMs/nes/Game #1 100%.nes", "/storage/emulated/0/ROMs/nes/Game #1 100%.nes"),
        Triple(storage, "primary:/ROMs/x.sfc", "/storage/emulated/0/ROMs/x.sfc"),
        Triple(storage, "1A2B-3C4D:ROMs/gba/y.gba", "/storage/1A2B-3C4D/ROMs/gba/y.gba"),
        Triple(storage, "abcd-ef01:ROMs/y.gba", "/storage/abcd-ef01/ROMs/y.gba"),
        Triple(storage, "0000-0000:", "/storage/0000-0000"),
        // Any hex id of 4+ digits per dash-separated group is a volume id now.
        Triple(storage, "1A2B3C4D:ROMs/y.gba", "/storage/1A2B3C4D/ROMs/y.gba"),
        Triple(storage, "1A2B-3C4DE:ROMs/y.gba", "/storage/1A2B-3C4DE/ROMs/y.gba"),
        // NTFS (16 hex digits) and ext4 (a full UUID) drives are mounted under their id too.
        Triple(storage, "0123456789ABCDEF:ROMs/y.gba", "/storage/0123456789ABCDEF/ROMs/y.gba"),
        Triple(storage, "3e7b2c1a-5d4f-4a8b-9c0d-1e2f3a4b5c6d:ROMs/y.gba", "/storage/3e7b2c1a-5d4f-4a8b-9c0d-1e2f3a4b5c6d/ROMs/y.gba"),
        // The "Documents" shortcut's root: internal storage's Documents folder.
        Triple(storage, "home:ROMs/y.gba", "/storage/emulated/0/Documents/ROMs/y.gba"),
        Triple(storage, "home:", "/storage/emulated/0/Documents"),
        Triple(storage, "home:../Android/x", null),
        Triple(storage, "primary:ROMs/A+B 50% off.sfc", "/storage/emulated/0/ROMs/A+B 50% off.sfc"),
        Triple(storage, "../x:ROMs/y.gba", null),
        Triple(storage, "secondary:ROMs/y.gba", null),
        Triple(storage, "ROMs/y.gba", null),
        Triple(storage, ":ROMs/y.gba", null),
        Triple(storage, "primary:ROMs/../../data/x", null),
        Triple(storage, "primary:./x", null),
        Triple("com.android.providers.downloads.documents", "primary:ROMs/x.sfc", null),
        Triple("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/x.sfc", null),
        Triple("com.example.usbprovider", "1A2B-3C4D:ROMs/x.sfc", null),
        Triple("me.zhanghai.android.files.file_provider", "/storage/emulated/0/x.sfc", null),
        Triple(null, "primary:ROMs/x.sfc", null),
    )

    @Test
    fun everyDocumentIdMapsAsTheTableSays() {
        for ((authority, documentId, expected) in cases) {
            assertEquals("$authority / $documentId", expected, DocumentPaths.realPath(authority, documentId, OWNER_STORAGE))
        }
    }
}
