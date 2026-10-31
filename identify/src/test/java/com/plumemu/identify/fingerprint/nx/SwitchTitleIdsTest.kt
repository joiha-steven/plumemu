package com.plumemu.identify.fingerprint.nx

import com.plumemu.identify.fingerprint.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The file-name door the library's catalogue groups Switch files by; made-up ids. */
class SwitchTitleIdsTest {
    @Test fun baseUpdateAndDlcNamesGiveOneBaseId() {
        assertEquals(
            SwitchTitleIds.Id("0100ABCD00020000", "0100ABCD00020000", TitleKind.BASE),
            SwitchTitleIds.fromFileName("Zork Saga [0100ABCD00020000][v0].xci"),
        )
        assertEquals(
            SwitchTitleIds.Id("0100ABCD00020800", "0100ABCD00020000", TitleKind.UPDATE),
            SwitchTitleIds.fromFileName("0100abcd00020800.nsp"),
        )
        assertEquals(
            SwitchTitleIds.Id("0100ABCD00021003", "0100ABCD00020000", TitleKind.DLC),
            SwitchTitleIds.fromFileName("Zork Saga Extra Pack [0100ABCD00021003][DLC].nsp"),
        )
    }

    @Test fun namesWithoutAnIdOrWithHexThatIsNoIdGiveNoneOrOther() {
        assertNull(SwitchTitleIds.fromFileName("Zork Saga.nsp"))
        assertNull(SwitchTitleIds.fromFileName("Zork Saga [0004000000ABCD00].3ds"))
        assertNull(SwitchTitleIds.fromFileName("deadbeef0100ABCD00020800.nca"))
        assertEquals(TitleKind.OTHER, SwitchTitleIds.fromFileName("Kit [0123456789AB0123].nsp")?.kind)
    }

    @Test fun onlyTheSwitchSystemIsSwitch() {
        assertTrue(SwitchTitleIds.isSwitch("switch"))
        assertTrue(SwitchTitleIds.isSwitch("NSW"))
        assertFalse(SwitchTitleIds.isSwitch("n3ds"))
        assertFalse(SwitchTitleIds.isSwitch("gba"))
    }
}
