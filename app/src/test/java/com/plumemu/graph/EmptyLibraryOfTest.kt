package com.plumemu.graph

import com.plumemu.home.EmptyLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

/** Why the home is empty: the folder with no files to report is told apart, so the message and its buttons are true. */
class EmptyLibraryOfTest {
    @Test
    fun noFolderIsNoFolderWhateverElseIsTrue() = assertEquals(EmptyLibrary.NoFolder, emptyLibraryOf(hasFolders = false, isScanning = true, hasFilesNotAdded = true))

    @Test
    fun aRunningScanIsScanning() = assertEquals(EmptyLibrary.Scanning, emptyLibraryOf(hasFolders = true, isScanning = true, hasFilesNotAdded = false))

    @Test
    fun filesLeftOutKeepTheMessageThatPointsToThem() = assertEquals(EmptyLibrary.NoGames, emptyLibraryOf(hasFolders = true, isScanning = false, hasFilesNotAdded = true))

    @Test
    fun nothingLeftOutIsNoFiles() = assertEquals(EmptyLibrary.NoFiles, emptyLibraryOf(hasFolders = true, isScanning = false, hasFilesNotAdded = false))
}
