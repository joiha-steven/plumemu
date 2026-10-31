package com.plumemu.library.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExternalStorageFolderPresenceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var internal: File
    private lateinit var card: File
    private val unlistable = mutableSetOf<File>()

    private val presence by lazy {
        ExternalStorageFolderPresence(
            volumeRoot = { volume ->
                when (volume) {
                    "primary" -> internal
                    "6AD6-712E" -> card
                    else -> null
                }
            },
            listNames = { dir -> if (dir in unlistable) null else dir.list() },
        )
    }

    @Before
    fun setUp() {
        internal = temp.newFolder("emulated0")
        card = temp.newFolder("6AD6-712E")
        File(internal, "ROMs/DroidDeck").mkdirs()
        File(card, "Games/SNES").mkdirs()
    }

    private fun tree(documentId: String, authority: String = ExternalStorageFolderPresence.AUTHORITY) =
        "content://$authority/tree/" + documentId.replace(":", "%3A").replace("/", "%2F").replace(" ", "%20")

    @Test
    fun aFolderThatIsThereIsNotGone() {
        assertFalse(presence.isProvenGone(tree("primary:ROMs/DroidDeck")))
        assertFalse(presence.isProvenGone(tree("6AD6-712E:Games/SNES")))
    }

    @Test
    fun aDeletedLastFolderIsProvenGone() {
        File(internal, "ROMs/DroidDeck").deleteRecursively()
        assertTrue(presence.isProvenGone(tree("primary:ROMs/DroidDeck")))
    }

    @Test
    fun aDeletedMiddleFolderIsProvenGone() {
        File(internal, "ROMs").deleteRecursively()
        assertTrue(presence.isProvenGone(tree("primary:ROMs/DroidDeck")))
    }

    @Test
    fun aNameWithSpacesAndEscapesIsDecoded() {
        File(internal, "My Games+1").mkdirs()
        assertFalse(presence.isProvenGone(tree("primary:My Games%2B1")))
        File(internal, "My Games+1").delete()
        assertTrue(presence.isProvenGone(tree("primary:My Games%2B1")))
    }

    @Test
    fun aCardTakenOutIsNotProof() {
        card.deleteRecursively()
        assertFalse(presence.isProvenGone(tree("6AD6-712E:Games/SNES")))
    }

    @Test
    fun anUnknownVolumeIsNotProof() {
        assertFalse(presence.isProvenGone(tree("1234-ABCD:Games")))
    }

    @Test
    fun aParentThatCannotBeListedIsNotProof() {
        File(internal, "ROMs/DroidDeck").deleteRecursively()
        unlistable += File(internal, "ROMs")
        assertFalse(presence.isProvenGone(tree("primary:ROMs/DroidDeck")))
        unlistable.clear()
        unlistable += internal
        assertFalse(presence.isProvenGone(tree("primary:ROMs/DroidDeck")))
    }

    @Test
    fun anotherProviderIsNotProof() {
        File(internal, "ROMs").deleteRecursively()
        assertFalse(presence.isProvenGone(tree("primary:ROMs/DroidDeck", authority = "com.android.providers.downloads.documents")))
        assertFalse(presence.isProvenGone("content://com.example.cloud/tree/abc"))
    }

    @Test
    fun aWholeVolumeOrAnUnreadableUriIsNotProof() {
        assertFalse(presence.isProvenGone(tree("primary:")))
        assertFalse(presence.isProvenGone(tree("primary")))
        assertFalse(presence.isProvenGone("not a uri at all"))
        assertFalse(presence.isProvenGone("content://${ExternalStorageFolderPresence.AUTHORITY}/document/primary%3AROMs"))
        assertFalse(presence.isProvenGone("content://${ExternalStorageFolderPresence.AUTHORITY}/tree/primary%3AROMs%ZZ"))
    }

    @Test
    fun theDocumentsShortcutAndDotDotAreNeverProof() {
        // `home:` is internal storage's Documents; this proof was reviewed for `primary` and volumes only.
        assertFalse(presence.isProvenGone(tree("home:Gone")))
        assertFalse(presence.isProvenGone(tree("primary:ROMs/../Gone")))
    }

    @Test
    fun theAndroidFolderIsNeverProof() {
        // Android 11+ hides other apps' folders under Android/data from listings: missing there proves nothing.
        File(internal, "Android/data").mkdirs()
        assertFalse(presence.isProvenGone(tree("primary:Android/data/com.example/roms")))
    }

    @Test
    fun aDifferentlyCasedNameThatStillOpensIsNotProof() {
        // FAT and exFAT cards match names without case; a listing in another case is not a deletion.
        val caseInsensitive = ExternalStorageFolderPresence(
            volumeRoot = { card },
            listNames = { dir -> dir.list()?.map { it.uppercase() }?.toTypedArray() },
        )
        assertFalse(caseInsensitive.isProvenGone(tree("6AD6-712E:Games/SNES")))
    }
}
