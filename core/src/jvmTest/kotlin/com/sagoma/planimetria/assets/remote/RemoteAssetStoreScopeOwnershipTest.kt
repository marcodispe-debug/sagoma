package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chi possiede lo scope: lo store ha un job figlio proprio; `close()` ferma lui, la cancellazione del genitore scende fino a lui. */
class RemoteAssetStoreScopeOwnershipTest {
    private val dirs = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun cleanup() {
        scopes.forEach { it.coroutineContext[Job]?.cancel() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun cache() = DiskAssetCache(kotlin.io.path.createTempDirectory("sagoma-own-").toFile().also { dirs += it }, 1L shl 24)
    private fun appScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
    private val policy = FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0)
    private val CoroutineScope.job: Job get() = coroutineContext[Job]!!
    private val cancelled = RemoteEnsureResult.Failed(RemoteError.Cancelled)

    @Test
    fun `close ferma lo store ma lascia attivo lo scope del chiamante`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val gate = GateFetcher().add(b)
        val app = appScope()
        val store = RemoteAssetStore(resolverOf(b), cache(), gate, policy, scope = app)
        assertEquals(1, app.job.children.count(), "lo store e un job figlio del chiamante")

        val waiter = async(Dispatchers.Default) { store.ensureDetailed(b.path) }
        waitUntil("download partito") { gate.requests.size == 1 }
        store.close()

        assertEquals(cancelled, withTimeout(3_000) { waiter.await() })
        assertTrue(app.job.isActive, "lo scope del chiamante resta attivo")
        waitUntil("il lavoro dello store e terminato") { app.job.children.count() == 0 }
        // e lo scope e ancora utilizzabile da chi lo possiede
        assertEquals(42, withTimeout(2_000) { app.async { 42 }.await() })
    }

    @Test
    fun `close senza lavori in corso e ripetuto lascia attivo lo scope e senza figli`() = runBlocking<Unit> {
        val app = appScope()
        val store = RemoteAssetStore(BlobResolver.EMPTY, cache(), CountingFetcher(), policy, scope = app)
        assertEquals(1, app.job.children.count())
        store.close(); store.close()
        assertTrue(app.job.isActive)
        waitUntil("figlio terminato") { app.job.children.count() == 0 }
    }

    @Test
    fun `cancellare lo scope del chiamante ferma lo store senza lasciare nessuno in attesa`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val gate = GateFetcher().add(b)
        val app = appScope()
        val store = RemoteAssetStore(resolverOf(b), cache(), gate, policy, scope = app)
        val waiter = async(Dispatchers.Default) { store.ensureDetailed(b.path) }
        waitUntil("download partito") { gate.requests.size == 1 }

        app.job.cancel() // il chiamante chiude la sua parte
        assertEquals(cancelled, withTimeout(3_000) { waiter.await() })
        // uno store il cui genitore e cancellato non resta sospeso: la nuova richiesta termina subito
        assertEquals(cancelled, withTimeout(3_000) { store.ensureDetailed(b.path) })
        assertTrue(app.job.isCancelled)
        waitUntil("figli terminati") { app.job.children.count() == 0 }
        store.close() // ancora ripetibile
    }

    @Test
    fun `due negozi sullo stesso scope - chiudere uno non ferma l altro`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val app = appScope()
        val s1 = RemoteAssetStore(resolverOf(b), cache(), CountingFetcher().add(b), policy, scope = app)
        val s2 = RemoteAssetStore(resolverOf(b), cache(), CountingFetcher().add(b), policy, scope = app)
        s1.close()
        assertEquals(cancelled, withTimeout(2_000) { s1.ensureDetailed(b.path) })
        assertEquals(RemoteEnsureResult.Available, withTimeout(5_000) { s2.ensureDetailed(b.path) })
        s2.close()
    }
}
