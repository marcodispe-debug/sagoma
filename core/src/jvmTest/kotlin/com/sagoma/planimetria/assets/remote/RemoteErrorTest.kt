package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteErrorTest {
    private val all = listOf<RemoteError>(
        RemoteError.Offline, RemoteError.Timeout, RemoteError.HttpStatus(418), RemoteError.NotFound, RemoteError.AccessDenied,
        RemoteError.TooManyRequests(30), RemoteError.ServerError(503), RemoteError.TooLarge(10, 5),
        RemoteError.ContentLengthMismatch(1, 2), RemoteError.SizeMismatch(1, 2), RemoteError.HashMismatch("aa", "bb"),
        RemoteError.CacheUnavailable("disk full"), RemoteError.ManifestInvalid(listOf("x")), RemoteError.ManifestIncompatible("old"),
        RemoteError.SignatureInvalid, RemoteError.Cancelled,
    )

    @Test
    fun `ogni errore ha una descrizione leggibile e senza url o credenziali`() {
        for (e in all) {
            val d = e.describe()
            assertTrue(d.isNotBlank(), e.toString())
            assertFalse("http://" in d || "https://" in d || "Authorization" in d || "token" in d, d)
        }
        assertEquals("too many requests (retry after 30s)", RemoteError.TooManyRequests(30).describe())
        assertEquals("too many requests", RemoteError.TooManyRequests().describe())
        assertEquals("http status 418", RemoteError.HttpStatus(418).describe())
    }

    @Test
    fun `errori transitori`() {
        val transient = all.filter { it.isTransient }.map { it::class.simpleName }.toSet()
        assertEquals(setOf("Offline", "Timeout", "TooManyRequests", "ServerError"), transient)
    }

    @Test
    fun `ManifestInvalid tronca l'elenco nei log`() {
        val d = RemoteError.ManifestInvalid((1..20).map { "issue$it" }).describe()
        assertTrue("issue5" in d && "issue6" !in d && d.endsWith("…"))
    }

    @Test
    fun `AssetState e la sua proiezione su AssetAvailability`() {
        assertEquals(AssetAvailability.Available, AssetState.Available.availability())
        assertEquals(AssetAvailability.Incompatible, AssetState.Incompatible.availability())
        assertEquals(AssetAvailability.Remote, AssetState.Remote.availability())
        assertEquals(AssetAvailability.Remote, AssetState.Downloading(0.5f).availability())
        assertEquals(AssetAvailability.Remote, AssetState.Failed(RemoteError.Offline).availability())
        assertFailsWith<IllegalArgumentException> { AssetState.Downloading(1.5f) }
        assertFailsWith<IllegalArgumentException> { AssetState.Downloading(-0.1f) }
        assertEquals(AssetState.Failed(RemoteError.Timeout), AssetState.Failed(RemoteError.Timeout))
    }
}
