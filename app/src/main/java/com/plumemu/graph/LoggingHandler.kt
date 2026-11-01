package com.plumemu.graph

import kotlinx.coroutines.CoroutineExceptionHandler

/** Logs an uncaught failure of the app's scope by class instead of crashing. */
internal fun loggingHandler(log: (String) -> Unit): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, e -> log("Background work failed (${e.javaClass.simpleName})") }
