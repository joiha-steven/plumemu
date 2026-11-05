package com.plumemu.coreui.components

import android.os.Trace

/** Runs [block] inside a trace section [name]: the cost of building a cached image shows in a system trace. */
internal inline fun <T> traceMiss(
    name: String,
    block: () -> T,
): T {
    ImageBuilding.watcher?.invoke(name)
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
