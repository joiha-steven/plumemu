package com.plumemu.scraper

import java.io.File
import java.io.InputStream

/** A ROM that is a plain file. */
class FileRomSource(private val file: File) : RomSource {
    override val key: String get() = file.absolutePath
    override val fileName: String get() = file.name
    override val sizeBytes: Long get() = file.length()
    override val lastModifiedMillis: Long get() = file.lastModified()
    override fun open(): InputStream = file.inputStream()
}
