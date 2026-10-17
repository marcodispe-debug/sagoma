package com.sagoma.planimetria.assets

import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Negozio d'asset con cache persistente davanti a una sorgente locale. Le sorgenti sono finte (con un contatore
 * delle letture), tranne nella prova sui file veri, che si leggono soltanto. Le scritture in cache girano subito
 * (esecutore diretto) per avere prove deterministiche; una prova usa l'esecutore di sfondo vero.
 */
class CachedAssetStoreTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun newDir(): File = kotlin.io.path.createTempDirectory("sagoma-cached-").toFile().also { dirs += it }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun bytes(n: Int, seed: Int = 0) = ByteArray(n) { (it * 31 + seed).toByte() }
    private fun cache(dir: File = newDir(), max: Long = 10_000_000) = DiskAssetCache(dir, max)

    /** Sorgente che conta quante volte viene letta. */
    private class CountingSource(private val inner: AssetStore) : AssetStore {
        constructor(files: Map<String, ByteArray>) : this(MapStore(files.toMutableMap()))

        var peeks = 0
        override fun availability(path: String) = inner.availability(path)
        override fun peek(path: String): ByteArray? { peeks++; return inner.peek(path) }
    }

    class MapStore(val files: MutableMap<String, ByteArray>) : AssetStore {
        override fun availability(path: String) = if (path in files) AssetAvailability.Available else AssetAvailability.Unavailable
        override fun peek(path: String): ByteArray? = files[path]
    }

    @Test
    fun `cache hit - il backend locale non viene letto`() = runBlocking {
        val c = cache()
        val cached = bytes(100, 1)
        c.put("furniture/a.glb", cached)
        val source = CountingSource(mapOf("furniture/a.glb" to bytes(100, 2))) // contenuto diverso: si vede da dove arrivano i byte
        val store = cachedAssetStore(source, c)
        assertContentEquals(cached, store.peek("furniture/a.glb"))
        assertContentEquals(cached, store.read("furniture/a.glb"))
        assertEquals(AssetAvailability.Available, store.availability("furniture/a.glb"))
        assertEquals(0, source.peeks)
    }

    @Test
    fun `cache miss - si legge il backend, si popola la cache e la seconda lettura e dalla cache`() {
        val data = bytes(200, 3)
        val c = cache()
        val source = CountingSource(mapOf("furniture/a.glb" to data))
        val store = cachedAssetStore(source, c)
        assertEquals(AssetAvailability.Unavailable, c.availability("furniture/a.glb"))
        assertContentEquals(data, store.peek("furniture/a.glb"))
        assertEquals(1, source.peeks)
        assertContentEquals(data, c.peek("furniture/a.glb")) // ora la cache ha il file
        assertEquals(sha(data), c.info("furniture/a.glb")!!.sha256)
        assertContentEquals(data, store.peek("furniture/a.glb"))
        assertEquals(1, source.peeks) // la seconda lettura non ha toccato la sorgente
    }

    @Test
    fun `persistenza - una seconda istanza, come un secondo processo, legge dalla cache`() {
        val base = newDir()
        val files = mapOf("furniture/a.glb" to bytes(300, 1), "env/giorno.ibl" to bytes(500, 2), "materials/m_color.jpg" to bytes(50, 3))
        val first = CountingSource(files)
        val a = buildCachedAssetStore(first, base, "stamp-1", 10_000_000, execute = { it() })
        for ((p, d) in files) assertContentEquals(d, a.peek(p))
        assertEquals(3, first.peeks)
        // "processo terminato": nuovi oggetti, stessa cartella, stessa versione della sorgente
        val second = CountingSource(files)
        val b = buildCachedAssetStore(second, base, "stamp-1", 10_000_000, execute = { it() })
        for ((p, d) in files) assertContentEquals(d, b.peek(p))
        assertEquals(0, second.peeks)
    }

    @Test
    fun `cache troncata - diventa miss, il backend da il file giusto e la cache si ripristina`() {
        val root = newDir()
        val data = bytes(400, 5)
        val c = cache(root)
        val source = CountingSource(mapOf("furniture/a.glb" to data))
        val store = cachedAssetStore(source, c)
        store.peek("furniture/a.glb")
        assertEquals(1, source.peeks)
        File(root, "data/furniture/a.glb").writeBytes(data.copyOf(100)) // file troncato in cache
        assertContentEquals(data, store.peek("furniture/a.glb")) // risposta giusta, dal backend
        assertEquals(2, source.peeks)
        assertContentEquals(data, File(root, "data/furniture/a.glb").readBytes()) // cache ripristinata
        assertContentEquals(data, store.peek("furniture/a.glb"))
        assertEquals(2, source.peeks) // e di nuovo dalla cache
    }

    @Test
    fun `cache alterata della stessa dimensione - la scopre verifyAll e poi il backend la ripristina`() {
        val root = newDir()
        val data = bytes(400, 6)
        val c = cache(root)
        val source = CountingSource(mapOf("a.bin" to data))
        val store = cachedAssetStore(source, c)
        store.peek("a.bin")
        File(root, "data/a.bin").writeBytes(data.copyOf().also { it[10] = (it[10] + 1).toByte() })
        // peek controlla la dimensione, non l'impronta (costerebbe un calcolo a ogni lettura): non se ne accorge
        assertTrue(!data.contentEquals(store.peek("a.bin")))
        assertEquals(listOf("a.bin"), c.verifyAll()) // il controllo d'integrità esplicito sì, e toglie la voce
        assertContentEquals(data, store.peek("a.bin")) // ora miss: file giusto dal backend
        assertContentEquals(data, File(root, "data/a.bin").readBytes())
    }

    @Test
    fun `scrittura in cache fallita - il chiamante riceve comunque i byte`() {
        val data = bytes(100, 7)
        // (a) errore vero del disco: al posto della cartella dei dati c'e' un file
        val root = newDir()
        val c = cache(root)
        File(root, "data").deleteRecursively()
        File(root, "data").writeText("non sono una cartella")
        val source = CountingSource(mapOf("furniture/a.glb" to data))
        val store = cachedAssetStore(source, c)
        assertContentEquals(data, store.peek("furniture/a.glb"))
        assertContentEquals(data, runBlocking { store.read("furniture/a.glb") })
        // (b) la cache solleva un'eccezione qualsiasi
        val exploding = cachedAssetStore(CountingSource(mapOf("x" to data)), FailingCache(throwOnPut = true))
        assertContentEquals(data, exploding.peek("x"))
        // (c) la cartella di base della cache e' un file: la cache non si apre, l'app legge dalla sorgente
        val notADir = File(newDir(), "file").apply { writeText("x") }
        val noCache = buildCachedAssetStore(CountingSource(mapOf("x" to data)), notADir, "s", 1_000_000, execute = { it() })
        assertContentEquals(data, noCache.peek("x"))
    }

    /** Cache finta che non riesce mai a scrivere (o solleva eccezioni). */
    private class FailingCache(private val throwOnPut: Boolean) : AssetCache {
        var puts = 0
        override val maxBytes = 1_000_000L
        override val usedBytes = 0L
        override fun availability(path: String) = AssetAvailability.Unavailable
        override fun peek(path: String): ByteArray? = null
        override fun list() = emptyList<CachedAssetInfo>()
        override fun info(path: String): CachedAssetInfo? = null
        override fun put(path: String, bytes: ByteArray, sha256: String?): AssetPutResult {
            puts++
            if (throwOnPut) throw IllegalStateException("disco rotto")
            return AssetPutResult.IoError
        }
        override fun remove(path: String) = false
        override fun setPinned(owner: String, paths: Collection<String>) {}
        override fun verify(path: String) = false
        override fun verifyAll() = emptyList<String>()
        override fun flush() {}
        override fun clear() {}
    }

    @Test
    fun `dopo alcuni errori di scrittura di fila non si riprova piu e si legge sempre dalla sorgente`() {
        val data = bytes(10)
        val failing = FailingCache(throwOnPut = false)
        val files = (0 until 20).associate { "f$it" to data }
        val source = CountingSource(files)
        val store = cachedAssetStore(source, failing)
        for (p in files.keys) assertContentEquals(data, store.peek(p))
        assertEquals(5, failing.puts) // dopo 5 errori di fila smette
        assertEquals(20, source.peeks)
    }

    @Test
    fun `un errore della cache non arriva mai all'esecutore e non lascia il percorso bloccato`() {
        val data = bytes(10)
        val failing = FailingCache(throwOnPut = true)
        val leaked = mutableListOf<Throwable>()
        // come un thread di sfondo: se il lavoro solleva un'eccezione, nessuno la nasconde
        val store = cachedAssetStore(CountingSource(mapOf("a" to data)), failing, execute = { task ->
            try { task() } catch (e: Throwable) { leaked += e }
        })
        assertContentEquals(data, store.peek("a"))
        assertTrue(leaked.isEmpty(), "eccezioni uscite dal lavoro di scrittura: $leaked")
        assertContentEquals(data, store.peek("a"))
        assertEquals(2, failing.puts, "dopo l'errore il percorso doveva tornare libero per un nuovo tentativo")
    }

    @Test
    fun `asset inesistente - stesso risultato della sola sorgente`() = runBlocking {
        val source = CountingSource(mapOf("a" to bytes(10)))
        val plain = CountingSource(mapOf("a" to bytes(10)))
        val store = cachedAssetStore(source, cache())
        for (p in listOf("manca", "furniture/manca.glb", "", "../fuori", "con:due.puntini")) {
            assertEquals(plain.peek(p), store.peek(p), "peek '$p'")
            assertEquals(plain.availability(p), store.availability(p), "availability '$p'")
            assertEquals(plain.read(p), store.read(p), "read '$p'")
            assertEquals(plain.ensure(p), store.ensure(p), "ensure '$p'")
        }
        assertNull(store.peek("manca"))
    }

    @Test
    fun `la cache ha la precedenza sulla sorgente anche se questa e cambiata - per questo serve lo stamp`() {
        val c = cache()
        c.put("a.bin", bytes(10, 1))
        val store = cachedAssetStore(CountingSource(mapOf("a.bin" to bytes(10, 2))), c)
        assertContentEquals(bytes(10, 1), store.peek("a.bin"))
    }

    @Test
    fun `impronta attesa diversa - la voce in cache non si usa e viene sostituita dal contenuto nuovo`() {
        val old = bytes(50, 1)
        val new = bytes(60, 2)
        val c = cache()
        val files = mutableMapOf("furniture/x.glb" to old)
        var expected: String? = null // il catalogo futuro dira' l'impronta di ogni percorso
        val source = CountingSource(MapStore(files))
        val store = cachedAssetStore(source, c, expectedSha256 = { expected })
        // path X, impronta A
        expected = sha(old)
        assertContentEquals(old, store.peek("furniture/x.glb"))
        assertContentEquals(old, store.peek("furniture/x.glb"))
        assertEquals(1, source.peeks)
        assertEquals(sha(old), c.info("furniture/x.glb")!!.sha256)
        // la sorgente ora ha un altro contenuto e il catalogo dichiara l'impronta B per lo stesso percorso
        files["furniture/x.glb"] = new
        expected = sha(new)
        assertContentEquals(new, store.peek("furniture/x.glb")) // non il vecchio contenuto
        assertEquals(2, source.peeks)
        assertEquals(sha(new), c.info("furniture/x.glb")!!.sha256) // la cache ora ha il nuovo
        assertContentEquals(new, store.peek("furniture/x.glb"))
        assertEquals(2, source.peeks)
        // senza impronta dichiarata la cache vale com'e'
        expected = null
        assertContentEquals(new, store.peek("furniture/x.glb"))
        assertEquals(2, source.peeks)
    }

    @Test
    fun `stamp diverso - la cache vecchia non si usa e viene cancellata`() {
        val base = newDir()
        val oldSource = CountingSource(mapOf("a.bin" to bytes(10, 1)))
        buildCachedAssetStore(oldSource, base, "app-v1", 1_000_000, execute = { it() }).peek("a.bin")
        val versions1 = base.listFiles()!!.map { it.name }
        assertEquals(1, versions1.size)
        // l'app si aggiorna: la sorgente ha contenuti nuovi per lo stesso percorso
        val newSource = CountingSource(mapOf("a.bin" to bytes(10, 2)))
        val store = buildCachedAssetStore(newSource, base, "app-v2", 1_000_000, execute = { it() })
        assertContentEquals(bytes(10, 2), store.peek("a.bin")) // il contenuto nuovo, non quello vecchio
        assertEquals(1, newSource.peeks)
        val versions2 = base.listFiles()!!.map { it.name }
        assertEquals(1, versions2.size)
        assertTrue(versions1 != versions2, "la cartella della versione vecchia doveva sparire")
        // lo stesso stamp riusa la cache
        val again = CountingSource(mapOf("a.bin" to bytes(10, 9)))
        assertContentEquals(bytes(10, 2), buildCachedAssetStore(again, base, "app-v2", 1_000_000, execute = { it() }).peek("a.bin"))
        assertEquals(0, again.peeks)
    }

    @Test
    fun `la pulizia delle cache vecchie non tocca cartelle che non sono nostre`() {
        val base = newDir()
        File(base, "documenti").apply { mkdirs(); File(this, "importante.txt").writeText("x") }
        File(base, "v-non-una-nostra").apply { mkdirs(); File(this, "dati.txt").writeText("x") }
        File(base, "v-0123456789abcdef").apply { mkdirs(); File(this, "estranea.txt").writeText("x") } // nome giusto, contenuto non nostro
        buildCachedAssetStore(CountingSource(mapOf("a" to bytes(5))), base, "s", 1_000_000, execute = { it() }).peek("a")
        assertTrue(File(base, "documenti/importante.txt").isFile)
        assertTrue(File(base, "v-non-una-nostra/dati.txt").isFile)
        assertTrue(File(base, "v-0123456789abcdef/estranea.txt").isFile)
    }

    @Test
    fun `la cache non puo stare dentro la cartella degli asset ne contenerla e la cartella sorgente non cambia`() {
        val assets = newDir().also { File(it, "furniture").mkdirs(); File(it, "furniture/a.glb").writeBytes(bytes(30)) }
        val before = directoryFingerprint(assets)
        val data = bytes(30)
        val source = CountingSource(DirectoryAssetStore(assets))
        // dentro la cartella degli asset: niente cache, e niente file creati
        val inside = buildCachedAssetStore(source, File(assets, "cache"), "s", 1_000_000, protectedDirs = listOf(assets), execute = { it() })
        assertContentEquals(data, inside.peek("furniture/a.glb"))
        assertEquals(before, directoryFingerprint(assets))
        assertTrue(!File(assets, "cache").exists())
        // la cartella degli asset dentro la cache
        val outer = newDir()
        val nested = File(outer, "assets").also { File(it, "furniture").mkdirs(); File(it, "furniture/a.glb").writeBytes(data) }
        val contains = buildCachedAssetStore(CountingSource(DirectoryAssetStore(nested)), outer, "s", 1_000_000, protectedDirs = listOf(nested), execute = { it() })
        assertContentEquals(data, contains.peek("furniture/a.glb"))
        assertEquals(listOf("assets"), outer.listFiles()!!.map { it.name })
        // una cartella separata funziona e la sorgente resta intatta
        val separate = buildCachedAssetStore(source, newDir(), "s", 1_000_000, protectedDirs = listOf(assets), execute = { it() })
        assertContentEquals(data, separate.peek("furniture/a.glb"))
        assertEquals(before, directoryFingerprint(assets))
    }

    @Test
    fun `senza cartella o senza stamp non c'e cache e il comportamento e quello della sorgente`() {
        val source = CountingSource(mapOf("a" to bytes(5)))
        assertTrue(buildCachedAssetStore(source, null, "s", 1_000) === source)
        assertTrue(buildCachedAssetStore(source, newDir(), null, 1_000) === source)
    }

    @Test
    fun `con l'esecutore di sfondo vero la prima lettura non aspetta la scrittura e poi la cache si riempie`() {
        val base = newDir()
        val data = bytes(1000, 4)
        val source = CountingSource(mapOf("furniture/a.glb" to data))
        val store = buildCachedAssetStore(source, base, "bg", 10_000_000) // esecutore di sfondo predefinito
        assertContentEquals(data, store.peek("furniture/a.glb"))
        val deadline = System.currentTimeMillis() + 10_000
        var cached = false
        while (System.currentTimeMillis() < deadline && !cached) {
            cached = base.walkTopDown().any { it.isFile && it.name == "a.glb" }
            if (!cached) Thread.sleep(20)
        }
        assertTrue(cached, "la scrittura in cache non e' avvenuta")
        val second = CountingSource(mapOf("furniture/a.glb" to bytes(1000, 9)))
        assertContentEquals(data, buildCachedAssetStore(second, base, "bg", 10_000_000).peek("furniture/a.glb"))
        assertEquals(0, second.peeks)
    }

    @Test
    fun `una sola scrittura per percorso in attesa e un limite ai byte in attesa`() {
        val queue = ArrayDeque<() -> Unit>()
        val c = cache()
        val data = bytes(100)
        val source = CountingSource(mapOf("a" to data, "b" to data, "grande" to bytes(5000)))
        val store = cachedAssetStore(source, c, execute = { queue.addLast(it) }, maxPendingBytes = 1000)
        assertContentEquals(data, store.peek("a"))
        assertContentEquals(data, store.peek("a")) // ancora in attesa: non si accoda due volte
        assertEquals(1, queue.size)
        assertContentEquals(bytes(5000), store.peek("grande")) // oltre il limite in attesa: restituito, non messo in cache
        assertEquals(1, queue.size)
        assertEquals(AssetAvailability.Unavailable, c.availability("a")) // finche' non gira il lavoro, la cache e' vuota
        queue.removeFirst()()
        assertEquals(AssetAvailability.Available, c.availability("a"))
        assertContentEquals(data, store.peek("b"))
        queue.removeFirst()()
        assertEquals(listOf("a", "b"), c.list().map { it.path }.sorted())
    }

    @Test
    fun `un esecutore che rifiuta il lavoro non rompe la lettura`() {
        val data = bytes(10)
        val store = cachedAssetStore(CountingSource(mapOf("a" to data)), cache(), execute = { throw java.util.concurrent.RejectedExecutionException("chiuso") })
        assertContentEquals(data, store.peek("a"))
        assertContentEquals(data, store.peek("a"))
    }

    @Test
    fun `i file veri passano dallo store integrato byte per byte e la cartella degli asset non cambia`() {
        val root = File("../app/src/pro/assets")
        assertTrue(root.isDirectory, "Cartella degli asset non trovata: ${root.absolutePath}")
        val fingerprintBefore = directoryFingerprint(root)
        val savedF = FurnitureCatalog.items
        val savedM = MaterialCatalog.items
        try {
            val source = CountingSource(DirectoryAssetStore(root))
            val base = newDir()
            val store = buildCachedAssetStore(source, base, "reali", 200L * 1024 * 1024, protectedDirs = listOf(root), execute = { it() })

            // primo avvio, cache vuota: il catalogo c'e' comunque (lo da la sorgente locale)
            val catalogBytes = store.peek(AssetPaths.FURNITURE_CATALOG)
            assertNotNull(catalogBytes)
            FurnitureCatalog.load(catalogBytes.decodeToString())
            MaterialCatalog.load(store.peek(AssetPaths.MATERIAL_CATALOG)!!.decodeToString())
            assertTrue(FurnitureCatalog.items.size >= 100 && MaterialCatalog.items.size >= 30)

            val model = FurnitureCatalog.items.first { !it.model.startsWith(FurnitureCatalog.RUG) }.model
            val material = MaterialCatalog.items.first().id
            val paths = listOf(
                AssetPaths.FURNITURE_CATALOG, AssetPaths.MATERIAL_CATALOG,
                AssetPaths.furnitureThumbnail(model), AssetPaths.furnitureTop(model), AssetPaths.furnitureModel(model),
                AssetPaths.materialMap(material, "color"), AssetPaths.materialMap(material, "normal"), AssetPaths.materialMap(material, "orm"),
                AssetPaths.materialThumbnail(material), AssetPaths.environment("giorno"),
            )
            for (p in paths) assertContentEquals(File(root, p).readBytes(), store.peek(p), "primo passaggio $p")
            val readsFirst = source.peeks
            assertTrue(readsFirst >= paths.size)
            // popolata: ogni file e' in cache con la sua impronta, uguale a quella del file originale
            val cache = openVersionedAssetCache(base, "reali", 200L * 1024 * 1024)!!
            for (p in paths) assertEquals(sha(File(root, p).readBytes()), cache.info(p)?.sha256, "cache $p")
            // secondo passaggio: tutto dalla cache, stessi byte, la sorgente non si legge
            for (p in paths) assertContentEquals(File(root, p).readBytes(), store.peek(p), "secondo passaggio $p")
            assertEquals(readsFirst, source.peeks)
            // asset veri che non esistono: come prima
            assertNull(store.peek(AssetPaths.furnitureModel("non-esiste")))
        } finally {
            FurnitureCatalog.set(savedF)
            MaterialCatalog.set(savedM)
        }
        assertEquals(fingerprintBefore, directoryFingerprint(root), "la cartella degli asset e' cambiata")
    }
}
