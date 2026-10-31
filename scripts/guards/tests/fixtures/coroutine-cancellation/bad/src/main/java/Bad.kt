package fixture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun inSuspendFun(): Int =
    try {
        load()
    } catch (e: Exception) { // BAD
        0
    }

suspend fun throwableLogged(): Int =
    try {
        load()
    } catch (e: Throwable) { // BAD
        println("failed")
        0
    }

suspend fun runtimeAfterSpecific(): Int {
    try {
        return load()
    } catch (e: java.io.IOException) {
        return 1
    } catch (e: RuntimeException) { // BAD
        return 2
    }
}

suspend fun stateException(): Int =
    try {
        load()
    } catch (_: IllegalStateException) { // BAD
        0
    }

suspend fun inWithContext(): Int =
    withContext(Dispatchers.IO) {
        try {
            plain()
        } catch (e: Exception) { // BAD
            0
        }
    }

fun inLaunch(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        try {
            load()
        } catch (e: kotlin.Exception) { // BAD
            println(e)
        }
    }
}

fun inFlow() =
    flow {
        try {
            emit(load())
        } catch (e: Exception) { // BAD
            emit(0)
        }
    }

suspend fun rethrowsAnotherException(): Int =
    try {
        load()
    } catch (e: Exception) { // BAD
        throw IllegalArgumentException("wrapped", e)
    }

suspend fun cancellationCaughtAfter(): Int =
    try {
        load()
    } catch (e: Exception) { // BAD
        0
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    }

suspend fun load(): Int = 1

fun plain(): Int = 1
