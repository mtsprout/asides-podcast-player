package com.asides.app

import com.asides.app.data.Annotation
import com.asides.app.data.BackupAside
import com.asides.app.data.BackupData
import com.asides.app.data.BackupEpisode
import com.asides.app.data.BackupException
import com.asides.app.data.BackupFormat
import com.asides.app.data.BackupMatch
import com.asides.app.data.BackupPodcast
import com.asides.app.data.BackupSettings
import com.asides.app.data.KnownEpisode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {
    private fun sample() = BackupData(
        appVersion = "0.4.0",
        exportedAt = 1_700_000_000_000,
        settings = BackupSettings(autoplay = false, speed = 1.3f, newestFirst = true, hidePlayed = true, listDensity = "large", interests = "1303,1488"),
        podcasts = listOf(
            BackupPodcast("https://a/feed", "Show \"A\" – ünïcode", "https://a/art.jpg", subscribed = true, notifyNew = true),
            BackupPodcast("https://b/feed", "Show B", null, subscribed = false, notifyNew = false),
        ),
        episodes = listOf(
            BackupEpisode("https://a/feed", "g1", "https://a/1.mp3", "Day 1", 1000, 60_000, 12_000, played = false),
            BackupEpisode("https://b/feed", null, "https://b/9.mp3", "Nine", 2000, 0, 0, played = true),
        ),
        asides = listOf(
            BackupAside("https://a/feed", "g1", "https://a/1.mp3", 5_000, 1_700_000_001_000, "hello \"world\"\nline two", "audio/1.wav"),
            BackupAside("https://a/feed", null, "https://a/1.mp3", 9_000, 1_700_000_002_000, null, null),
        ),
    )

    @Test fun roundTripKeepsEverything() {
        assertEquals(sample(), BackupFormat.decode(BackupFormat.encode(sample())))
    }

    @Test fun anEmptyBackupRoundTrips() {
        val empty = sample().copy(podcasts = emptyList(), episodes = emptyList(), asides = emptyList())
        assertEquals(empty, BackupFormat.decode(BackupFormat.encode(empty)))
    }

    @Test fun garbageIsRejectedWithAFriendlyMessage() {
        try { BackupFormat.decode("this is not json"); fail() } catch (e: BackupException) {
            assertTrue(e.message!!.contains("isn't an Asides backup"))
        }
    }

    @Test fun jsonFromSomewhereElseIsRejected() {
        try { BackupFormat.decode("""{"hello":"world"}"""); fail() } catch (e: BackupException) {
            assertTrue(e.message!!.contains("isn't an Asides backup"))
        }
    }

    @Test fun aNewerFormatIsRefusedRatherThanMisread() {
        val newer = JSONObject(BackupFormat.encode(sample())).put("format", BackupFormat.VERSION + 1).toString()
        try { BackupFormat.decode(newer); fail() } catch (e: BackupException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test fun aDamagedBackupIsRejected() {
        val damaged = JSONObject(BackupFormat.encode(sample())).apply { remove("podcasts") }.toString()
        try { BackupFormat.decode(damaged); fail() } catch (e: BackupException) {
            assertTrue(e.message!!.contains("damaged"))
        }
    }

    @Test fun missingOptionalFieldsGetDefaults() {
        val trimmed = JSONObject(BackupFormat.encode(sample())).apply { getJSONObject("settings").remove("speed") }.toString()
        assertEquals(1.0f, BackupFormat.decode(trimmed).settings.speed)
    }

    @Test fun episodesAreMatchedByGuidThenAudioUrl() {
        val known = listOf(
            KnownEpisode(1, "g1", "https://x/1.mp3?t=NEW", "T", 0),
            KnownEpisode(2, null, "https://x/2.mp3", "T", 0),
        )
        assertEquals(1L, BackupMatch.episode(known, "g1", "https://x/1.mp3?t=OLD")?.id) // same guid, URL changed
        assertEquals(2L, BackupMatch.episode(known, null, "https://x/2.mp3")?.id)
        assertNull(BackupMatch.episode(known, "nope", "https://x/zzz.mp3"))
    }

    @Test fun anAsideAlreadyThereIsRecognized() {
        val existing = listOf(Annotation(id = 1, episodeId = 1, positionMs = 5_000, audioPath = "p", createdAt = 111))
        assertTrue(BackupMatch.alreadyHave(existing, 5_000, 111))
        assertFalse(BackupMatch.alreadyHave(existing, 5_000, 222))
        assertFalse(BackupMatch.alreadyHave(existing, 6_000, 111))
    }
}
