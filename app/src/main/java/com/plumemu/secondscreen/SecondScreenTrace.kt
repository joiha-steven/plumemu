package com.plumemu.secondscreen

import android.os.Trace

/** Names the work of a second-screen card in a system trace (section "SS:<part>"), so a slow frame says which part cost it. */
internal inline fun <T> cardTrace(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
