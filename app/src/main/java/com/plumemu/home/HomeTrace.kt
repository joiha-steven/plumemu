package com.plumemu.home

import android.os.Trace

/** Runs [block] inside a trace section [name] ("Plum:..."), so a system trace says which home work a slow frame or message held. */
internal inline fun <T> homeTrace(
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
