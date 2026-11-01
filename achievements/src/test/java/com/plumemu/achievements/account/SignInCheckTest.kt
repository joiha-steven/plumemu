package com.plumemu.achievements.account

import com.plumemu.achievements.api.RaFixture
import com.plumemu.achievements.api.RaFixture.Companion.KEY
import com.plumemu.achievements.api.RaFixture.Companion.ULID
import com.plumemu.achievements.api.RaProfile
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.achievements.api.WebApiKey
import com.plumemu.achievements.api.fixture
import com.plumemu.achievements.api.ok
import com.plumemu.achievements.api.status
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The sign-in check: one profile call with the typed name and key. */
class SignInCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fx by lazy { RaFixture(tmp.newFolder()) }
    private val keysUsed = mutableListOf<WebApiKey>()
    private val check by lazy {
        SignInCheck { key ->
            keysUsed += key
            RetroAchievementsClient(key, tmp.newFolder(), log = {}, httpClient = lazy { OkHttpClient() }, baseUrl = fx.server.url("/API/").toString())
        }
    }

    @After fun tearDown() = fx.close()

    @Test fun `a good name and key give the profile with the ULID`() = runBlocking {
        fx.enqueue(ok(fixture("profile.json")))

        val result = check.check("  plumtester ", " $KEY ")

        assertEquals(RaResult.Ok(RaProfile("PlumTester", ULID, "https://media.retroachievements.org/UserPic/PlumTester.png", 1234, 56)), result)
        val url = fx.takeRequest().url
        assertEquals("plumtester", url.queryParameter("u"))
        assertEquals("the typed key, trimmed", KEY, url.queryParameter("y"))
    }

    @Test fun `a wrong key and an unknown user come back as such`() = runBlocking {
        fx.enqueue(status(401, fixture("unauthorized.json")))
        assertEquals(RaResult.WrongKey, check.check("plumtester", "wrong-key-value"))

        fx.enqueue(status(404))
        assertEquals(RaResult.UnknownUser, check.check("nobody", KEY))
    }

    @Test fun `a blank name or key asks nothing`() = runBlocking {
        assertEquals(RaResult.WrongKey, check.check("plumtester", "   "))
        assertEquals(RaResult.UnknownUser, check.check("", KEY))
        assertTrue(keysUsed.isEmpty())
        assertEquals(0, fx.server.requestCount)
    }
}
