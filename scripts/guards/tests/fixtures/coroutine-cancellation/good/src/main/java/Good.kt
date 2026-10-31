package fixture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun earlierCatch(): Int =
    try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        0
    }

suspend fun earlierCatchHandlesIt(): Int =
    try {
        load()
    } catch (e: java.io.IOException) {
        1
    } catch (e: CancellationException) {
        println("cancelled")
        throw e
    } catch (e: Throwable) {
        2
    }

suspend fun ifIs(): Int =
    try {
        load()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        0
    }

suspend fun ensure(): Int =
    try {
        load()
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        0
    }

suspend fun specificExceptions(): Int =
    try {
        load()
    } catch (e: java.io.IOException) {
        1
    } catch (e: IllegalArgumentException) {
        2
    }

fun plainFunction(): Int =
    try {
        plain()
    } catch (e: Exception) {
        0
    }

suspend fun plainFunctionInside(): Int {
    fun parse(): Int =
        try {
            plain()
        } catch (e: RuntimeException) {
            0
        }
    return parse() + load()
}

fun launchWithRethrow(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println(e)
        }
    }
}

suspend fun inCommentsAndStrings(): Int {
    // catch (e: Exception) { }
    val text = "catch (e: Exception) { }"
    return withContext(Dispatchers.IO) { text.length }
}

suspend fun load(): Int = 1

fun plain(): Int = 1
