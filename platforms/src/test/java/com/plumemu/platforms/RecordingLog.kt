package com.plumemu.platforms

internal class RecordingLog : PlatformLog {
    val lines = mutableListOf<String>()
    override fun error(message: String) {
        lines += message
    }
}
