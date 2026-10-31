package com.plumemu.identify.codec.lzma

/**
 * The compressed data is not a valid LZMA or LZMA2 stream, or ends before the output the caller
 * asked for: a bad properties byte, a first range-coder byte that is not 0, a match reaching
 * back beyond the bytes produced, a bad LZMA2 control byte, reading past the end of the input.
 *
 * Deliberately not an [java.io.IOException]: in this module an `IOException` means the storage
 * failed and must reach the caller, while broken data in a file becomes a note.
 */
internal class LzmaException(message: String) : Exception(message)
