package com.plumemu.achievements.api

/**
 * What one RetroAchievements call came back with. Every failure is one of the [Failure] cases,
 * mapped in one place ([RaStatus]); none of them carries the key or the server's text.
 */
sealed interface RaResult<out T> {

    /**
     * The answer. [stale] is set when RetroAchievements could not be asked (no connection, busy,
     * rate limited, a bad answer) and an older saved copy is returned instead; only the game list
     * is saved. A refused key ([WrongKey]) is never hidden behind a saved copy.
     */
    data class Ok<out T>(val value: T, val stale: Stale? = null) : RaResult<T>

    /** A call that did not give an answer. */
    sealed interface Failure : RaResult<Nothing>

    /** HTTP 401: the Web API key is wrong or was reset on the site. */
    data object WrongKey : Failure

    /**
     * HTTP 429, still after one wait, or with a Retry-After longer than the client waits.
     * [retryAfterSeconds] is the server's number when it sent one.
     */
    data class RateLimited(val retryAfterSeconds: Long?) : Failure

    /** HTTP 5xx, still after one wait. */
    data class Busy(val code: Int) : Failure

    /** No connection, a timeout, or the connection broke during the answer. */
    data object NoConnection : Failure

    /** RetroAchievements knows no such user (HTTP 404 on a user call, or an empty profile). */
    data object UnknownUser : Failure

    /** An answer that is not what the API documents: malformed JSON, an empty body, another code. */
    data class BadAnswer(val why: String) : Failure
}

/** An older saved copy returned because RetroAchievements failed with [reason]. */
data class Stale(val savedAtMillis: Long, val reason: RaResult.Failure)

/** [RaResult.Ok]'s value with [transform] applied; failures pass through unchanged. */
inline fun <T, R> RaResult<T>.map(transform: (T) -> R): RaResult<R> = when (this) {
    is RaResult.Ok -> RaResult.Ok(transform(value), stale)
    is RaResult.Failure -> this
}

/**
 * The one place where RetroAchievements' HTTP status codes get a meaning.
 *
 * Seen in a real answer: 401 with `{"message":"Unauthenticated.","errors":[{"status":"401",
 * "code":"unauthorized",...}]}` for a wrong key. 404 for an unknown user and 429 / 5xx are from
 * the API's documentation and common HTTP use, not from answers seen.
 */
internal object RaStatus {
    /** Whether [code] is worth one wait and a second try. */
    fun isRetryable(code: Int): Boolean = code == TOO_MANY_REQUESTS || code in SERVER_ERRORS

    /**
     * The failure for a non-200 [code]. [isUserCall]: the call names a user (`u=`), so 404 means
     * that user is unknown; elsewhere a 404 is an unexpected answer.
     */
    fun failure(code: Int, isUserCall: Boolean, retryAfterSeconds: Long?): RaResult.Failure = when {
        code == UNAUTHORIZED -> RaResult.WrongKey
        code == NOT_FOUND && isUserCall -> RaResult.UnknownUser
        code == TOO_MANY_REQUESTS -> RaResult.RateLimited(retryAfterSeconds)
        code in SERVER_ERRORS -> RaResult.Busy(code)
        else -> RaResult.BadAnswer("HTTP $code")
    }

    private const val UNAUTHORIZED = 401
    private const val NOT_FOUND = 404
    private const val TOO_MANY_REQUESTS = 429
    private val SERVER_ERRORS = 500..599
}
