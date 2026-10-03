package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManifestRepositoryTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() = dirs.forEach { it.deleteRecursively() }

    private fun dir(): File = kotlin.io.path.createTempDirectory("sagoma-mrepo-").toFile().also { dirs += it }

    /** Manifest sintetico: i file `paths` con impronta derivata da (versione, indice). */
    private fun json(version: Long, paths: List<String> = listOf("furniture/a.glb"), release: String = "r$version", extra: String = ""): String {
        val files = paths.mapIndexed { i, p -> """{"role":"model","path":"$p","sha256":"${"%064x".format(version * 1000 + i)}","size":${100 + i}}""" }
        val assets = files.mapIndexed { i, f -> """{"id":"a$i","kind":"furniture","files":[$f]}""" }.joinToString(",")
        return """{"schema":1,"catalog":"test","catalogVersion":$version,"releaseId":"$release","blobs":{"layout":"flat"},"assets":[$assets]$extra}"""
    }

    private class MemPersistence(var text: String? = null) : ManifestPersistence {
        var saves = 0
        var failSaves = false
        var throwOnSave = false
        override fun load(): String? = text
        override fun save(text: String): Boolean {
            if (throwOnSave) error("disco in fiamme")
            saves++
            if (failSaves) return false
            this.text = text
            return true
        }
    }

    private class FakeFetcher(var result: suspend () -> ManifestFetchResult) : ManifestFetcher {
        val calls = AtomicInteger()
        override suspend fun fetch(): ManifestFetchResult { calls.incrementAndGet(); return result() }
    }

    // ---- avvio ----

    @Test
    fun `al primo avvio si parte dal manifest iniziale`() {
        val repo = ManifestRepository(MemPersistence(), bundledManifest = json(3))
        val s = assertNotNull(repo.current)
        assertEquals(ManifestOrigin.Bundled, s.origin)
        assertEquals(3L, s.catalogVersion)
        assertEquals("r3", s.releaseId)
        assertSame(s.resolver, repo.resolver)
        assertSame(s.manifest, s.resolver.manifest) // il resolver e dello stesso manifest
        assertNotNull(s.resolver.entryForPath("furniture/a.glb"))
    }

    @Test
    fun `si carica l'ultimo manifest salvato`() {
        val repo = ManifestRepository(MemPersistence(json(7)))
        val s = assertNotNull(repo.current)
        assertEquals(ManifestOrigin.Persisted, s.origin)
        assertEquals(7L, s.catalogVersion)
    }

    @Test
    fun `senza manifest iniziale ne salvato non c'e istantanea e il primo aggiornamento la crea`() = runBlocking<Unit> {
        val repo = ManifestRepository(MemPersistence())
        assertNull(repo.current)
        assertNull(repo.resolver)
        val r = repo.replace(json(1))
        assertIs<ManifestRefreshResult.Updated>(r)
        assertEquals(1L, repo.current!!.catalogVersion)
    }

    @Test
    fun `senza persistenza il repository funziona solo in memoria`() = runBlocking<Unit> {
        val repo = ManifestRepository(null, bundledManifest = json(1))
        assertIs<ManifestRefreshResult.Updated>(repo.replace(json(2)))
        assertEquals(2L, repo.current!!.catalogVersion)
    }

    @Test
    fun `tra salvato e iniziale vince il piu recente e a parita quello dell'app`() {
        assertEquals(ManifestOrigin.Persisted, ManifestRepository(MemPersistence(json(9)), json(5)).current!!.origin)
        assertEquals(ManifestOrigin.Bundled, ManifestRepository(MemPersistence(json(4)), json(5)).current!!.origin)
        // stessa versione e contenuto diverso (app aggiornata con un seme diverso): vince quello dell'app
        val tie = ManifestRepository(MemPersistence(json(5, release = "salvato")), json(5, release = "app")).current!!
        assertEquals(ManifestOrigin.Bundled, tie.origin)
        assertEquals("app", tie.releaseId)
    }

    // ---- manifest salvato non valido ----

    private val sha = "a".repeat(64)
    private fun withFile(path: String = "furniture/a.glb", sha: String = this.sha, size: String = "10", version: String = "5", schema: String = "1", extra: String = "") =
        """{"schema":$schema,"catalog":"c","catalogVersion":$version,"releaseId":"r","blobs":{"layout":"flat"},"assets":[{"id":"a","kind":"k","files":[{"role":"m","path":"$path","sha256":"$sha","size":$size}]$extra}]}"""

    private val corrupted: List<Pair<String, String>> = listOf(
        "JSON invalido" to "{",
        "file vuoto" to "",
        "solo spazi" to "   \n ",
        "non e un oggetto" to "[]",
        "troncato a meta" to json(5).let { it.substring(0, it.length / 2) },
        "schema non supportato" to withFile(schema = "99"),
        "SHA non valido" to withFile(sha = "zz"),
        "SHA troppo corto" to withFile(sha = "a".repeat(63)),
        "percorso non valido" to withFile(path = "../fuori.glb"),
        "percorso assoluto" to withFile(path = "/abs.glb"),
        "catalogVersion non valido" to withFile(version = "0"),
        "catalogVersion negativo" to withFile(version = "-3"),
        "dimensione negativa" to withFile(size = "-1"),
        "semanticamente incoerente (replacedBy sconosciuto)" to """{"schema":1,"catalog":"c","catalogVersion":5,"releaseId":"r","blobs":{"layout":"flat"},"assets":[{"id":"a","kind":"k","status":"deprecated","replacedBy":"zzz","files":[{"role":"m","path":"a.glb","sha256":"$sha","size":1}]}]}""",
        "layout sconosciuto" to withFile().replace("flat", "quantico"),
    )

    @Test
    fun `un manifest salvato non valido non si usa e si ripiega sul manifest iniziale`() {
        for ((label, text) in corrupted) {
            val repo = ManifestRepository(MemPersistence(text), bundledManifest = json(2))
            val s = repo.current
            assertNotNull(s, label)
            assertEquals(ManifestOrigin.Bundled, s.origin, label)
            assertEquals(2L, s.catalogVersion, label)
        }
    }

    @Test
    fun `un manifest salvato non valido senza manifest iniziale lascia il repository senza istantanea`() {
        for ((label, text) in corrupted) {
            val repo = ManifestRepository(MemPersistence(text))
            assertNull(repo.current, label)
            assertNull(repo.resolver, label)
        }
    }

    @Test
    fun `dopo un salvato corrotto si puo ancora installare un manifest buono`() = runBlocking<Unit> {
        val p = MemPersistence("{")
        val repo = ManifestRepository(p, bundledManifest = json(2))
        assertIs<ManifestRefreshResult.Updated>(repo.replace(json(3)))
        assertEquals(json(3), p.text) // il file corrotto e sostituito da quello buono
        assertEquals(3L, ManifestRepository(p).current!!.catalogVersion)
    }

    @Test
    fun `un errore di lettura della persistenza non impedisce l'avvio`() {
        val p = object : ManifestPersistence {
            override fun load(): String? = error("non leggibile")
            override fun save(text: String) = true
        }
        assertEquals(ManifestOrigin.Bundled, ManifestRepository(p, json(1)).current!!.origin)
        assertNull(ManifestRepository(p).current)
    }

    // ---- V1 -> V2 e versioni ----

    @Test
    fun `da V1 a V2 il nuovo manifest si salva e diventa corrente`() = runBlocking<Unit> {
        val p = MemPersistence()
        val repo = ManifestRepository(p, bundledManifest = json(1, listOf("furniture/a.glb")))
        val r = repo.replace(json(2, listOf("furniture/a.glb", "furniture/b.glb")))
        val s = assertIs<ManifestRefreshResult.Updated>(r).snapshot
        assertSame(s, repo.current)
        assertEquals(ManifestOrigin.Updated, s.origin)
        assertEquals(2L, s.catalogVersion)
        assertNotNull(repo.resolver!!.entryForPath("furniture/b.glb"))
        assertEquals(json(2, listOf("furniture/a.glb", "furniture/b.glb")), p.text)
        assertEquals(1, p.saves)
        // riavvio: si riparte dal V2 salvato
        assertEquals(2L, ManifestRepository(p, bundledManifest = json(1)).current!!.catalogVersion)
    }

    @Test
    fun `una versione piu bassa e un tentativo di tornare indietro e si rifiuta`() = runBlocking<Unit> {
        val p = MemPersistence()
        val repo = ManifestRepository(p, bundledManifest = json(10))
        for (v in listOf(9L, 1L)) {
            val r = repo.replace(json(v))
            val e = assertIs<RemoteError.ManifestIncompatible>(assertIs<ManifestRefreshResult.Failed>(r).error)
            assertTrue("older" in e.reason, e.reason)
        }
        assertEquals(10L, repo.current!!.catalogVersion)
        assertEquals(0, p.saves)
        assertNull(p.text)
    }

    @Test
    fun `la stessa versione con lo stesso contenuto e un no-op`() = runBlocking<Unit> {
        val p = MemPersistence()
        val repo = ManifestRepository(p, bundledManifest = json(10))
        val before = repo.current
        val r = repo.replace(json(10))
        assertSame(before, assertIs<ManifestRefreshResult.Unchanged>(r).snapshot)
        assertSame(before, repo.current) // nessuna nuova istantanea
        assertEquals(0, p.saves)
    }

    @Test
    fun `la stessa versione con contenuto diverso si rifiuta`() = runBlocking<Unit> {
        val p = MemPersistence()
        val repo = ManifestRepository(p, bundledManifest = json(10, listOf("furniture/a.glb")))
        val r = repo.replace(json(10, listOf("furniture/a.glb", "furniture/b.glb")))
        val e = assertIs<RemoteError.ManifestIncompatible>(assertIs<ManifestRefreshResult.Failed>(r).error)
        assertTrue("already used" in e.reason, e.reason)
        assertNull(repo.resolver!!.entryForPath("furniture/b.glb"))
        assertEquals(0, p.saves)
        // anche con un altro releaseId a parita di versione
        assertIs<ManifestRefreshResult.Failed>(repo.replace(json(10, release = "altro")))
    }

    @Test
    fun `la versione minima e quella del manifest corrente, anche se piu alto del salvato`() = runBlocking<Unit> {
        val repo = ManifestRepository(MemPersistence(json(3)), bundledManifest = json(5)) // vince l'iniziale (5)
        assertIs<ManifestRefreshResult.Failed>(repo.replace(json(4)))
        assertIs<ManifestRefreshResult.Updated>(repo.replace(json(6)))
        assertIs<ManifestRefreshResult.Failed>(repo.replace(json(5)))
    }

    @Test
    fun `un manifest che richiede un client piu nuovo si rifiuta e il corrente resta`() = runBlocking<Unit> {
        val repo = ManifestRepository(null, json(1), clientVersion = 2)
        val r = repo.replace(json(2).replace(""""blobs"""", """"minClient":3,"blobs""""))
        assertIs<RemoteError.ManifestIncompatible>(assertIs<ManifestRefreshResult.Failed>(r).error)
        assertEquals(1L, repo.current!!.catalogVersion)
    }

    @Test
    fun `un manifest non valido o di schema sconosciuto non cambia niente`() = runBlocking<Unit> {
        val p = MemPersistence()
        val repo = ManifestRepository(p, bundledManifest = json(1))
        val before = repo.current
        for ((label, text) in corrupted) {
            val r = repo.replace(text)
            assertIs<ManifestRefreshResult.Failed>(r, label)
            assertSame(before, repo.current, label)
        }
        assertEquals(0, p.saves)
    }

    // ---- salvataggio ----

    @Test
    fun `se il salvataggio fallisce il manifest precedente resta corrente e usabile`() = runBlocking<Unit> {
        val p = MemPersistence(json(1))
        val repo = ManifestRepository(p)
        val v1 = repo.current!!
        p.failSaves = true
        val r = repo.replace(json(2))
        assertIs<RemoteError.CacheUnavailable>(assertIs<ManifestRefreshResult.Failed>(r).error)
        assertSame(v1, repo.current) // V2 non e adottato: memoria e disco dicono la stessa cosa
        assertEquals(json(1), p.text)
        assertNotNull(repo.resolver!!.entryForPath("furniture/a.glb"))
        // quando il disco torna a funzionare lo stesso V2 si installa
        p.failSaves = false
        assertIs<ManifestRefreshResult.Updated>(repo.replace(json(2)))
        assertEquals(2L, repo.current!!.catalogVersion)
    }

    @Test
    fun `un'eccezione della persistenza e un fallimento di salvataggio e non rompe il repository`() = runBlocking<Unit> {
        val p = MemPersistence(json(1))
        val repo = ManifestRepository(p)
        p.throwOnSave = true
        assertIs<ManifestRefreshResult.Failed>(repo.replace(json(2)))
        assertEquals(1L, repo.current!!.catalogVersion)
    }

    @Test
    fun `salvataggio su disco e riavvio`() = runBlocking<Unit> {
        val d = dir()
        val repo = ManifestRepository(DiskManifestPersistence(d), bundledManifest = json(1))
        assertIs<ManifestRefreshResult.Updated>(repo.replace(json(2)))
        assertEquals(json(2), File(d, "manifest.json").readText())
        val again = ManifestRepository(DiskManifestPersistence(d), bundledManifest = json(1)).current!!
        assertEquals(ManifestOrigin.Persisted, again.origin)
        assertEquals(2L, again.catalogVersion)
        // il file salvato che si corrompe: si ripiega sull'iniziale senza eccezioni
        File(d, "manifest.json").writeText(json(2).take(40))
        val broken = ManifestRepository(DiskManifestPersistence(d), bundledManifest = json(1)).current!!
        assertEquals(ManifestOrigin.Bundled, broken.origin)
    }

    // ---- aggiornamento remoto ----

    @Test
    fun `refresh con un manifest nuovo`() = runBlocking<Unit> {
        val p = MemPersistence()
        val f = FakeFetcher { ManifestFetchResult.Text(json(2)) }
        val repo = ManifestRepository(p, json(1), f)
        assertIs<ManifestRefreshResult.Updated>(repo.refresh())
        assertEquals(2L, repo.current!!.catalogVersion)
        assertEquals(1, f.calls.get())
        assertEquals(json(2), p.text)
        // il server dà lo stesso: nessun cambiamento
        assertIs<ManifestRefreshResult.Unchanged>(repo.refresh())
    }

    @Test
    fun `refresh offline non toglie l'ultimo manifest valido`() = runBlocking<Unit> {
        val p = MemPersistence(json(4))
        val f = FakeFetcher { ManifestFetchResult.Error(RemoteError.Offline) }
        val repo = ManifestRepository(p, json(1), f)
        val before = repo.current!!
        repeat(3) { assertEquals(ManifestRefreshResult.Failed(RemoteError.Offline), repo.refresh()) }
        assertSame(before, repo.current)
        assertEquals(4L, repo.current!!.catalogVersion)
        assertEquals(json(4), p.text) // il salvato non si tocca
        assertNotNull(repo.resolver!!.entryForPath("furniture/a.glb"))
        // ... e al riavvio offline si riparte dallo stesso
        assertEquals(4L, ManifestRepository(p, json(1)).current!!.catalogVersion)
    }

    @Test
    fun `refresh con un manifest non valido mantiene il corrente`() = runBlocking<Unit> {
        val p = MemPersistence()
        for ((label, text) in corrupted) {
            val f = FakeFetcher { ManifestFetchResult.Text(text) }
            val repo = ManifestRepository(p, json(1), f)
            val before = repo.current
            val r = repo.refresh()
            val e = assertIs<ManifestRefreshResult.Failed>(r, label).error
            assertTrue(e is RemoteError.ManifestInvalid || e is RemoteError.ManifestIncompatible, "$label: $e")
            assertSame(before, repo.current, label)
        }
        assertEquals(0, p.saves)
        assertNull(p.text)
    }

    @Test
    fun `refresh con un fetcher che fallisce in ogni modo`() = runBlocking<Unit> {
        val errors = listOf(RemoteError.Timeout, RemoteError.NotFound, RemoteError.ServerError(503), RemoteError.AccessDenied, RemoteError.SignatureInvalid)
        for (e in errors) {
            val repo = ManifestRepository(null, json(1), FakeFetcher { ManifestFetchResult.Error(e) })
            assertEquals(ManifestRefreshResult.Failed(e), repo.refresh())
            assertEquals(1L, repo.current!!.catalogVersion)
        }
        // eccezione del fetcher
        val repo = ManifestRepository(null, json(1), FakeFetcher { error("boom") })
        val r = repo.refresh()
        val err = assertIs<RemoteError.CacheUnavailable>(assertIs<ManifestRefreshResult.Failed>(r).error)
        assertFalse("boom" in err.describe(), "il messaggio dell'eccezione non finisce nell'errore")
        assertEquals(1L, repo.current!!.catalogVersion)
        // senza fetcher
        assertIs<ManifestRefreshResult.Failed>(ManifestRepository(null, json(1)).refresh())
    }

    @Test
    fun `dopo un refresh fallito il manifest precedente e ancora utilizzabile da un RemoteAssetStore`() = runBlocking<Unit> {
        val repo = ManifestRepository(null, json(1), FakeFetcher { ManifestFetchResult.Error(RemoteError.Offline) })
        repo.refresh()
        val cache = com.sagoma.planimetria.assets.DiskAssetCache(dir(), 1L shl 20)
        val store = RemoteAssetStore(repo.resolver!!, cache, CountingFetcher())
        assertEquals(com.sagoma.planimetria.assets.AssetAvailability.Remote, store.availability("furniture/a.glb"))
        store.close()
    }

    // ---- immutabilita e concorrenza ----

    @Test
    fun `un'istantanea gia in uso non cambia quando arriva la successiva`() = runBlocking<Unit> {
        val repo = ManifestRepository(null, json(1, listOf("furniture/a.glb")))
        val a = repo.current!! // operazione A
        val resolverA = a.resolver
        val manifestA = a.manifest
        assertIs<ManifestRefreshResult.Updated>(repo.replace(json(2, listOf("furniture/b.glb"))))
        val b = repo.current!! // operazione B
        // A continua con V1
        assertSame(resolverA, a.resolver)
        assertEquals(1L, a.catalogVersion)
        assertEquals(manifestA, a.manifest)
        assertNotNull(resolverA.entryForPath("furniture/a.glb"))
        assertNull(resolverA.entryForPath("furniture/b.glb"))
        // B vede V2
        assertEquals(2L, b.catalogVersion)
        assertNotNull(b.resolver.entryForPath("furniture/b.glb"))
        assertNull(b.resolver.entryForPath("furniture/a.glb"))
        assertTrue(a !== b)
    }

    @Test
    fun `leggere l'istantanea durante un refresh non aspetta e vede sempre un'istantanea coerente`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val f = FakeFetcher { gate.await(); ManifestFetchResult.Text(json(2)) }
        val repo = ManifestRepository(null, json(1), f)
        val refreshing = async(Dispatchers.Default) { repo.refresh() } // fermo nella "rete"
        while (f.calls.get() == 0) kotlinx.coroutines.delay(5)
        val during = repo.current!! // non aspetta il fetcher
        assertEquals(1L, during.catalogVersion)
        gate.complete(Unit)
        assertIs<ManifestRefreshResult.Updated>(refreshing.await())
        assertEquals(1L, during.catalogVersion) // la vecchia in mano resta V1
        assertEquals(2L, repo.current!!.catalogVersion)
    }

    @Test
    fun `lettori concorrenti durante molti aggiornamenti vedono solo istantanee coerenti e mai all'indietro`() = runBlocking<Unit> {
        val repo = ManifestRepository(null, json(1))
        val stop = AtomicBoolean(false)
        val problems = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val readers = (1..4).map {
            thread {
                var last = 0L
                while (!stop.get()) {
                    val s = repo.current ?: continue
                    if (s.resolver.manifest !== s.manifest) problems += "resolver di un altro manifest"
                    if (s.catalogVersion < last) problems += "indietro: $last -> ${s.catalogVersion}"
                    if (s.resolver.entryForPath("furniture/a.glb") == null) problems += "percorso mancante in V${s.catalogVersion}"
                    last = s.catalogVersion
                }
            }
        }
        // piu aggiornamenti in parallelo, in ordine sparso: solo quelli piu nuovi del corrente passano
        val versions = (2L..60L).shuffled()
        versions.map { v -> async(Dispatchers.Default) { repo.replace(json(v)) } }.forEach { it.await() }
        stop.set(true)
        readers.forEach { it.join() }
        assertTrue(problems.isEmpty(), problems.take(5).toString())
        assertEquals(60L, repo.current!!.catalogVersion) // alla fine vince la piu alta, qualunque sia l'ordine di arrivo
    }
}
