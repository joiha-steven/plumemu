package com.plumemu.graph

import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.scan.ScanEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRulesScanTest {
    private val calls = mutableListOf<String>()
    private val scan: () -> Flow<ScanEvent> = { calls += "scan"; emptyFlow() }
    private val rescanEverything: () -> Flow<ScanEvent> = { calls += "rescanEverything"; emptyFlow() }

    @Test
    fun libraryMatchedByOlderRulesListsEveryFolderAgain() = runTest {
        val recordVersion = scanForFolderRules(FOLDER_MATCH_VERSION - 1, scan, rescanEverything) {}
        assertEquals(listOf("rescanEverything"), calls)
        assertEquals("the caller must record the new version", true, recordVersion)
    }

    @Test
    fun libraryMatchedByCurrentRulesScansNormally() = runTest {
        val recordVersion = scanForFolderRules(FOLDER_MATCH_VERSION, scan, rescanEverything) {}
        assertEquals(listOf("scan"), calls)
        assertEquals(false, recordVersion)
    }
}
