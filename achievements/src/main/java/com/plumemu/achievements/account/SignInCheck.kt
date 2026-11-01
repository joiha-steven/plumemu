package com.plumemu.achievements.account

import com.plumemu.achievements.api.RaProfile
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.achievements.api.WebApiKey

/**
 * Checks a user name and Web API key the person typed: one profile call. On [RaResult.Ok] the
 * profile holds the name as RetroAchievements writes it, the ULID every later call uses, the
 * points and the picture URL; the caller then encrypts the key ([ApiKeyCipher]) and stores the
 * account. Any failure is returned as it is ([RaResult.WrongKey], [RaResult.UnknownUser], …).
 *
 * [clientFor] builds a client for the typed key; nothing is built before [check].
 */
class SignInCheck(private val clientFor: (WebApiKey) -> RetroAchievementsClient) {

    /** A blank key is [RaResult.WrongKey] and a blank name [RaResult.UnknownUser], without a request. */
    suspend fun check(userName: String, apiKey: String): RaResult<RaProfile> {
        val name = userName.trim()
        val key = apiKey.trim()
        if (key.isEmpty()) return RaResult.WrongKey
        if (name.isEmpty()) return RaResult.UnknownUser
        return clientFor(WebApiKey(key)).profile(name)
    }
}
