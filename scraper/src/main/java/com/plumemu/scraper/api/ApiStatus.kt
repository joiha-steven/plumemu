package com.plumemu.scraper.api

import com.plumemu.scraper.UnavailableReason

/** What one ScreenScraper call came back with, before the scraper decides what to do. */
internal sealed interface ApiOutcome {
    data class Ok(val body: String) : ApiOutcome
    data object NotFound : ApiOutcome
    data class Refused(val reason: UnavailableReason) : ApiOutcome
    data object ThreadLimit : ApiOutcome
    data object DailyQuota : ApiOutcome
    data object ServerClosed : ApiOutcome
    data class ServerError(val code: Int) : ApiOutcome
    data object NetworkError : ApiOutcome
}

/**
 * The one place where ScreenScraper's HTTP status codes get a meaning.
 *
 * Seen in real answers: 200, 404 with
 * "Erreur : Rom/Iso/Dossier non trouvee !", 403 with "Erreur de login : Verifier vos identifiants
 * developpeur !" (the app's credentials) and 403 with "Erreur de login : Verifier les identifiants
 * utilisateurs !" (the person's, from `ssuserInfos.php`; the French has accents). A wrong user
 * password on `jeuInfos.php` is NOT a 403: it answers 200 as anonymous (`ssuser.numid` "0"), which
 * the scraper checks itself. 401, 423, 426, 429, 430 and 431 still come from public notes and
 * other clients, not from answers seen.
 */
internal object ApiStatus {
    fun map(code: Int, body: String): ApiOutcome = when (code) {
        200 -> ApiOutcome.Ok(body)
        // "Rom/Iso/Dossier non trouvee": the hash lookup knows no such ROM.
        404 -> ApiOutcome.NotFound
        // Wrong login: the body names which one, "identifiants developpeur" for the app's,
        // "identifiants utilisateurs" for the person's.
        403 -> ApiOutcome.Refused(
            if (mentionsDeveloper(body)) UnavailableReason.BadDeveloperCredentials
            else UnavailableReason.BadUserCredentials,
        )
        // This software (softname) is blacklisted.
        426 -> ApiOutcome.Refused(UnavailableReason.SoftwareBlocked)
        // Too many threads in use, or too many requests this minute: wait and retry.
        429 -> ApiOutcome.ThreadLimit
        // 430: today's request quota is used up; 431: today's quota of unknown ROMs is used up.
        430, 431 -> ApiOutcome.DailyQuota
        // 401: API closed to non-members (server overloaded); 423: API fully closed.
        401, 423 -> ApiOutcome.ServerClosed
        else -> ApiOutcome.ServerError(code)
    }

    // "veloppeur" matches both "developpeur" and the accented French spelling.
    private fun mentionsDeveloper(body: String): Boolean =
        body.contains("veloppeur", ignoreCase = true) || body.contains("developer", ignoreCase = true)
}
