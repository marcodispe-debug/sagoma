package com.sagoma.planimetria.assets.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FetchPolicyTest {
    @Test
    fun `solo gli errori transitori si ritentano`() {
        val p = FetchPolicy()
        for (e in listOf(RemoteError.Timeout, RemoteError.Offline, RemoteError.TooManyRequests(), RemoteError.ServerError(500), RemoteError.ServerError(503))) {
            assertTrue(p.isRetryable(e), e.describe())
        }
        for (e in listOf(
            RemoteError.AccessDenied, RemoteError.NotFound, RemoteError.HashMismatch("a"), RemoteError.SizeMismatch(1, 2),
            RemoteError.ContentLengthMismatch(1, 2), RemoteError.ManifestIncompatible("x"), RemoteError.ManifestInvalid(listOf("x")),
            RemoteError.SignatureInvalid, RemoteError.Cancelled, RemoteError.HttpStatus(418), RemoteError.TooLarge(2, 1),
            RemoteError.CacheUnavailable("x"),
        )) {
            assertFalse(p.isRetryable(e), e.describe())
        }
    }

    @Test
    fun `attesa esponenziale con tetto`() {
        val p = FetchPolicy(baseBackoffMillis = 100, maxBackoffMillis = 1_000)
        assertEquals(listOf(100L, 200L, 400L, 800L, 1000L, 1000L), (1..6).map { p.backoffMillis(it) })
        assertEquals(0L, FetchPolicy(baseBackoffMillis = 0).backoffMillis(5))
    }

    @Test
    fun `Retry-After allunga l'attesa entro il tetto`() {
        val p = FetchPolicy(baseBackoffMillis = 100, maxBackoffMillis = 5_000)
        assertEquals(3_000L, p.backoffMillis(1, RemoteError.TooManyRequests(3)))
        assertEquals(5_000L, p.backoffMillis(1, RemoteError.TooManyRequests(60)))
        assertEquals(100L, p.backoffMillis(1, RemoteError.TooManyRequests(null)))
        assertEquals(100L, p.backoffMillis(1, RemoteError.ServerError(500)))
    }

    @Test
    fun `parametri non validi`() {
        assertFailsWith<IllegalArgumentException> { FetchPolicy(maxConcurrentDownloads = 0) }
        assertFailsWith<IllegalArgumentException> { FetchPolicy(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { FetchPolicy(baseBackoffMillis = -1) }
    }
}
