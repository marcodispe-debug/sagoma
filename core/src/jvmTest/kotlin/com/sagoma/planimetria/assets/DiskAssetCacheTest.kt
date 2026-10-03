package com.sagoma.planimetria.assets

import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cache persistente su disco: solo cartelle temporanee (e, in un test, la lettura dei file veri senza modificarli). */
class DiskAssetCacheTest {
    private val dirs = mutableListOf<File>()
    private var clock = 1_000L

    @AfterTest
    fun cleanup() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun newDir(): File = kotlin.io.path.createTempDirectory("sagoma-cache-").toFile().also { dirs += it }
    private fun cache(root: File, max: Long = 1_000L) = DiskAssetCache(root, max) { clock++ }

    /** SHA-256 calcolato qui, indipendente da quello del codice di produzione. */
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun bytes(n: Int, seed: Int = 0) = ByteArray(n) { (it * 31 + seed).toByte() }

    @Test
    fun `un file messo in cache si legge uguale con la sua impronta`() {
        val c = cache(newDir())
        val data = bytes(100, 1)
        assertEquals(AssetPutResult.Stored, c.put("furniture/a.glb", data))
        assertEquals(AssetAvailability.Available, c.availability("furniture/a.glb"))
        assertContentEquals(data, c.peek("furniture/a.glb"))
        val info = c.info("furniture/a.glb")!!
        assertEquals(100L, info.size)
        assertEquals(sha(data), info.sha256)
        assertEquals(100L, c.usedBytes)
        assertNull(c.peek("furniture/altro.glb"))
        assertEquals(AssetAvailability.Unavailable, c.availability("furniture/altro.glb"))
    }

    @Test
    fun `la cache resta tra una apertura e l'altra con ordine impronte e file fissati`() {
        val root = newDir()
        val c1 = cache(root)
        c1.put("a", bytes(10, 1)); c1.put("b", bytes(20, 2)); c1.put("c", bytes(30, 3))
        c1.peek("a") // "a" diventa il più recente
        c1.setPinned("progetto-1", listOf("b", "non-ancora-qui"))
        val before = c1.list()

        val c2 = cache(root) // come dopo un riavvio
        assertEquals(60L, c2.usedBytes)
        assertEquals(before.map { it.path }, c2.list().map { it.path })
        assertEquals(listOf("b", "c", "a"), c2.list().map { it.path })
        assertEquals(before.map { it.sha256 }, c2.list().map { it.sha256 })
        assertContentEquals(bytes(20, 2), c2.peek("b"))
        assertTrue(c2.info("c")!!.pinned.not())
        assertTrue(c2.info("b")!!.pinned)
        // il file fissato che non c'era vale da quando arriva
        c2.put("non-ancora-qui", bytes(5))
        assertTrue(c2.info("non-ancora-qui")!!.pinned)
    }

    @Test
    fun `un contenuto con impronta sbagliata non entra e non lascia tracce`() {
        val root = newDir()
        val c = cache(root)
        val result = c.put("a.bin", bytes(50), sha256 = sha(bytes(50, 9)))
        assertEquals(AssetPutResult.HashMismatch, result)
        assertEquals(AssetAvailability.Unavailable, c.availability("a.bin"))
        assertNull(c.peek("a.bin"))
        assertEquals(0L, c.usedBytes)
        assertEquals(0, File(root, "tmp").listFiles()!!.size)
        assertEquals(0, File(root, "data").walkTopDown().count { it.isFile })
        // l'impronta giusta, anche in maiuscolo, entra
        assertEquals(AssetPutResult.Stored, c.put("a.bin", bytes(50), sha256 = sha(bytes(50)).uppercase()))
    }

    @Test
    fun `sostituire un file aggiorna spazio e impronta e il file vecchio non resta`() {
        val root = newDir()
        val c = cache(root)
        c.put("a", bytes(40, 1))
        val firstSha = c.info("a")!!.sha256
        c.put("a", bytes(10, 2))
        assertEquals(10L, c.usedBytes)
        assertEquals(1, c.list().size)
        assertTrue(c.info("a")!!.sha256 != firstSha)
        assertContentEquals(bytes(10, 2), c.peek("a"))
        assertEquals(1, File(root, "data").walkTopDown().count { it.isFile })
    }

    @Test
    fun `quando manca spazio si buttano via i meno usati e leggere aggiorna l'ordine`() {
        val c = cache(newDir(), max = 100)
        c.put("a", bytes(40)); c.put("b", bytes(40))
        c.peek("a") // "b" è ora il meno recente
        assertEquals(AssetPutResult.Stored, c.put("c", bytes(40))) // 120 > 100: via "b"
        assertNull(c.peek("b"))
        assertNotNull(c.peek("a"))
        assertNotNull(c.peek("c"))
        assertEquals(80L, c.usedBytes)
        assertTrue(c.usedBytes <= c.maxBytes)
    }

    @Test
    fun `i file fissati non si buttano via nemmeno oltre il limite e si liberano con setPinned`() {
        val c = cache(newDir(), max = 100)
        c.setPinned("progetto", listOf("p1", "p2")) // si fissa prima: vale da quando i file arrivano
        c.put("p1", bytes(60)); c.put("p2", bytes(60)) // 120 > 100, ma sono fissati
        c.put("x", bytes(10)) // far posto si può solo con ciò che non è fissato: non c'è niente da buttare, e x resta
        assertEquals(AssetAvailability.Available, c.availability("p1"))
        assertEquals(AssetAvailability.Available, c.availability("p2"))
        assertNotNull(c.peek("x"))
        assertTrue(c.info("p1")!!.pinned && c.info("p2")!!.pinned)
        c.setPinned("progetto", emptyList())
        c.put("y", bytes(10)) // ora p1 e p2 si possono buttare via
        assertTrue(c.usedBytes <= c.maxBytes, "usati ${c.usedBytes}")
        assertNotNull(c.peek("y"))
    }

    @Test
    fun `un file piu grande del limite non entra e non tocca il resto`() {
        val c = cache(newDir(), max = 100)
        c.put("a", bytes(60))
        assertEquals(AssetPutResult.TooLarge, c.put("grande", bytes(101)))
        assertEquals(AssetAvailability.Unavailable, c.availability("grande"))
        assertContentEquals(bytes(60), c.peek("a"))
        assertEquals(60L, c.usedBytes)
    }

    @Test
    fun `un file troncato sul disco non si legge e la voce sparisce`() {
        val root = newDir()
        val c = cache(root)
        c.put("furniture/a.glb", bytes(100))
        File(root, "data/furniture/a.glb").writeBytes(bytes(30)) // come dopo uno spegnimento a metà
        assertNull(c.peek("furniture/a.glb"))
        assertNull(c.info("furniture/a.glb"))
        assertEquals(0L, c.usedBytes)
        assertEquals(AssetAvailability.Unavailable, c.availability("furniture/a.glb"))
    }

    @Test
    fun `un file alterato della stessa dimensione lo scopre verify e lo toglie`() {
        val root = newDir()
        val c = cache(root)
        c.put("a", bytes(100, 1)); c.put("b", bytes(100, 2))
        val altered = bytes(100, 1).also { it[50] = (it[50] + 1).toByte() }
        File(root, "data/a").writeBytes(altered)
        assertTrue(c.verify("b"))
        assertFalse(c.verify("a"))
        assertNull(c.peek("a"))
        c.put("a", bytes(100, 1))
        File(root, "data/a").writeBytes(altered)
        assertEquals(listOf("a"), c.verifyAll())
        assertEquals(listOf("b"), c.list().map { it.path })
    }

    @Test
    fun `alla riapertura si cancellano i file orfani e i temporanei`() {
        val root = newDir()
        val c1 = cache(root)
        c1.put("furniture/a.glb", bytes(10))
        File(root, "data/furniture").also { it.mkdirs() }
        File(root, "data/furniture/orfano.glb").writeBytes(bytes(5)) // scritto ma mai entrato nell'indice
        File(root, "tmp/vecchio.part").writeBytes(bytes(5)) // scrittura interrotta
        val c2 = cache(root)
        assertFalse(File(root, "data/furniture/orfano.glb").exists())
        assertEquals(0, File(root, "tmp").listFiles()!!.size)
        assertContentEquals(bytes(10), c2.peek("furniture/a.glb"))
        assertEquals(10L, c2.usedBytes)
    }

    @Test
    fun `una voce senza file o con file di altra dimensione si scarta alla riapertura`() {
        val root = newDir()
        val c1 = cache(root)
        c1.put("a", bytes(10)); c1.put("b", bytes(20)); c1.put("c", bytes(30))
        File(root, "data/a").delete()
        File(root, "data/b").writeBytes(bytes(7))
        val c2 = cache(root)
        assertEquals(listOf("c"), c2.list().map { it.path })
        assertEquals(30L, c2.usedBytes)
        assertFalse(File(root, "data/b").exists())
    }

    @Test
    fun `un indice illeggibile o di un'altra versione equivale a una cache vuota che poi funziona`() {
        // L'indice di un'altra versione è quello vero (con la sua voce e il suo file) a cui si cambia solo la versione.
        val sameIndexOtherVersion = { real: String -> real.replace("\"version\":1", "\"version\":99") }
        for (corrupt in listOf<(String) -> String>({ "questo non e json" }, sameIndexOtherVersion, { "" }, { it.take(it.length / 2) })) {
            val root = newDir()
            val c1 = cache(root)
            c1.put("furniture/a.glb", bytes(10))
            val real = File(root, "index.json").readText()
            assertTrue(real.contains("furniture/a.glb") && real.contains("\"version\":1"), "indice di partenza: $real")
            val content = corrupt(real)
            File(root, "index.json").writeText(content)
            val c2 = cache(root)
            assertEquals(0, c2.list().size, "indice: '$content'")
            assertEquals(0L, c2.usedBytes)
            assertEquals(0, File(root, "data").walkTopDown().count { it.isFile }) // non si fida di file che non può verificare
            assertEquals(AssetPutResult.Stored, c2.put("furniture/b.glb", bytes(20)))
            assertContentEquals(bytes(20), cache(root).peek("furniture/b.glb"))
        }
    }

    @Test
    fun `se il limite si riduce tra due aperture la cache rientra nel nuovo limite`() {
        val root = newDir()
        val c1 = cache(root, max = 1000)
        c1.put("a", bytes(300)); c1.put("b", bytes(300)); c1.put("c", bytes(300))
        c1.peek("a")
        c1.flush() // l'ordine dato dalla lettura si salva
        val c2 = cache(root, max = 700)
        assertTrue(c2.usedBytes <= 700)
        assertEquals(listOf("c", "a"), c2.list().map { it.path }) // via "b", il meno recente
    }

    @Test
    fun `flush salva l'ordine d'uso delle letture e senza letture non scrive`() {
        val root = newDir()
        val c1 = cache(root)
        c1.put("a", bytes(10)); c1.put("b", bytes(10)); c1.put("c", bytes(10))
        val index = File(root, "index.json")
        val stamp = index.lastModified()
        val content = index.readText()
        c1.flush() // niente di nuovo da salvare
        assertEquals(content, index.readText())
        assertEquals(stamp, index.lastModified())
        c1.peek("a"); c1.peek("b") // ordine ora: c, a, b
        assertEquals(listOf("a", "b", "c"), cache(root).list().map { it.path }) // su disco c'è ancora quello vecchio
        c1.flush()
        assertEquals(listOf("c", "a", "b"), cache(root).list().map { it.path })
    }

    @Test
    fun `percorsi non validi sono rifiutati`() {
        val c = cache(newDir())
        val invalid = listOf(
            "", "/etc/passwd", "../fuori", "furniture/../../fuori", "a\\b", "a//b", "a/", "con:due.puntini",
            "nome?.glb", "CON", "furniture/NUL.png", "lpt1.txt", "finisce.", "finisce ", "a/b*c",
        )
        for (p in invalid) {
            assertEquals(AssetPutResult.InvalidPath, c.put(p, bytes(1)), "'$p'")
            assertNull(c.peek(p), "'$p'")
            assertEquals(AssetAvailability.Unavailable, c.availability(p), "'$p'")
            assertFalse(c.remove(p))
        }
        assertEquals(0L, c.usedBytes)
        // percorsi veri del catalogo
        for (p in listOf("furniture/ph_sofa_01.glb", "furniture/ob_0002e50309b44e40_top.png", "materials/woodfloor040_color.jpg", "env/giorno.ibl")) {
            assertEquals(AssetPutResult.Stored, c.put(p, bytes(1)), p)
        }
    }

    @Test
    fun `due percorsi che differiscono solo per le maiuscole non convivono`() {
        val c = cache(newDir())
        assertEquals(AssetPutResult.Stored, c.put("furniture/Sofa.glb", bytes(10, 1)))
        assertEquals(AssetPutResult.InvalidPath, c.put("furniture/sofa.glb", bytes(10, 2)))
        assertEquals(AssetPutResult.Stored, c.put("furniture/Sofa.glb", bytes(10, 3))) // lo stesso percorso si sostituisce
        assertContentEquals(bytes(10, 3), c.peek("furniture/Sofa.glb"))
    }

    @Test
    fun `un errore del disco lascia intatto il resto e non lascia temporanei`() {
        val root = newDir()
        val c = cache(root)
        c.put("a", bytes(10, 1))
        // "a" è un file: "a/b" non si può creare
        assertEquals(AssetPutResult.IoError, c.put("a/b", bytes(10, 2)))
        assertContentEquals(bytes(10, 1), c.peek("a"))
        assertEquals(10L, c.usedBytes)
        assertEquals(listOf("a"), c.list().map { it.path })
        assertEquals(0, File(root, "tmp").listFiles()!!.size)
        // e viceversa: "d/e" è un file, "d" è una cartella
        c.put("d/e", bytes(10, 3))
        assertEquals(AssetPutResult.IoError, c.put("d", bytes(10, 4)))
        assertContentEquals(bytes(10, 3), c.peek("d/e"))
        assertEquals(0, File(root, "tmp").listFiles()!!.size)
    }

    @Test
    fun `remove e clear`() {
        val root = newDir()
        val c = cache(root)
        c.put("a", bytes(10)); c.put("b", bytes(10)); c.setPinned("p", listOf("b"))
        assertTrue(c.remove("a"))
        assertFalse(c.remove("a"))
        assertEquals(10L, c.usedBytes)
        assertFalse(File(root, "data/a").exists())
        c.clear()
        assertEquals(0, c.list().size)
        assertEquals(0L, c.usedBytes)
        assertEquals(0, File(root, "data").walkTopDown().count { it.isFile })
        c.put("b", bytes(10))
        assertFalse(c.info("b")!!.pinned) // clear toglie anche i fissati
        assertEquals(listOf("b"), cache(root).list().map { it.path }) // e la riapertura vede solo il file messo dopo
    }

    @Test
    fun `molti thread insieme non rompono ne i file ne la contabilita`() {
        val root = newDir()
        val c = cache(root, max = 400)
        val keys = (0 until 15).map { "furniture/k$it.glb" }
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = (0 until 8).map { t ->
            Thread {
                try {
                    val rnd = Random(t)
                    repeat(500) {
                        val k = keys[rnd.nextInt(keys.size)]
                        when (rnd.nextInt(10)) {
                            in 0..3 -> c.put(k, bytes(rnd.nextInt(1, 90), rnd.nextInt(5)))
                            in 4..7 -> c.peek(k)
                            8 -> c.remove(k)
                            else -> c.setPinned("t$t", listOf(k))
                        }
                    }
                } catch (e: Throwable) {
                    errors += e
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        assertTrue(errors.isEmpty(), "errori nei thread: $errors")
        val list = c.list()
        assertEquals(list.sumOf { it.size }, c.usedBytes)
        assertTrue(c.usedBytes <= 400 || list.all { it.pinned }, "usati ${c.usedBytes}")
        assertEquals(emptyList(), c.verifyAll(), "ogni voce deve corrispondere esattamente al suo file")
        assertEquals(0, File(root, "tmp").listFiles()!!.size)
        // e dopo un riavvio è lo stesso
        val reopened = cache(root, max = 400)
        assertEquals(list.map { it.path }.toSet(), reopened.list().map { it.path }.toSet())
    }

    /** Come sarà un negozio remoto: scarica (qui: copia da una mappa) nella cache e poi risponde dalla cache. */
    private class FakeRemote(
        private val cache: AssetCache,
        private val files: Map<String, ByteArray>,
        private val expectedSha: Map<String, String>,
    ) : AssetStore {
        var downloads = 0
        override fun availability(path: String) = cache.availability(path)
        override fun peek(path: String): ByteArray? = cache.peek(path)
        override suspend fun ensure(path: String): AssetAvailability {
            if (cache.availability(path) == AssetAvailability.Available) return AssetAvailability.Available
            val data = files[path] ?: return AssetAvailability.Unavailable
            downloads++
            return if (cache.put(path, data, expectedSha[path]) == AssetPutResult.Stored) AssetAvailability.Available else AssetAvailability.Unavailable
        }
    }

    @Test
    fun `la cache e pronta a ricevere i file da un negozio remoto`() = runBlocking {
        val root = newDir()
        val good = bytes(80, 1)
        val files = mapOf("furniture/a.glb" to good, "furniture/rotto.glb" to bytes(80, 2))
        val shas = mapOf("furniture/a.glb" to sha(good), "furniture/rotto.glb" to sha(bytes(80, 99))) // impronta che non corrisponde
        val remote = FakeRemote(cache(root), files, shas)
        val store = CompositeAssetStore(DirectoryAssetStore(File(newDir(), "vuoto")), remote)

        assertNull(store.peek("furniture/a.glb")) // niente di scaricato: peek non fa rete
        assertContentEquals(good, store.read("furniture/a.glb")) // read scarica nella cache
        assertEquals(1, remote.downloads)
        assertContentEquals(good, store.peek("furniture/a.glb")) // ora anche peek
        store.read("furniture/a.glb")
        assertEquals(1, remote.downloads) // già in cache: niente da scaricare

        assertNull(store.read("furniture/rotto.glb")) // impronta sbagliata: non entra
        assertEquals(AssetAvailability.Unavailable, store.availability("furniture/rotto.glb"))
        assertNull(store.read("furniture/non-esiste.glb"))

        // senza rete (il remoto non ha più niente) e dopo un riavvio, ciò che è già in cache funziona
        val offline = FakeRemote(cache(root), emptyMap(), emptyMap())
        assertContentEquals(good, offline.read("furniture/a.glb"))
        assertEquals(0, offline.downloads)
        // e chi scarica può sapere se la copia in cache è quella attesa
        assertEquals(sha(good), cache(root).info("furniture/a.glb")!!.sha256)
    }

    @Test
    fun `i file veri passano dalla cache uguali e rientrano nel limite`() {
        val dir = File("../app/src/pro/assets/furniture")
        assertTrue(dir.isDirectory, "Cartella degli asset non trovata: ${dir.absolutePath}")
        val models = dir.listFiles { f -> f.name.endsWith(".glb") }!!.sortedBy { it.name }.take(30)
        assertEquals(30, models.size)
        val total = models.sumOf { it.length() }
        val c = cache(newDir(), max = maxOf(total / 2, models.maxOf { it.length() }))
        for (f in models) {
            val data = f.readBytes()
            assertEquals(AssetPutResult.Stored, c.put("furniture/${f.name}", data, sha(data)), f.name)
            assertTrue(c.usedBytes <= c.maxBytes)
        }
        // l'ultimo c'è, il primo è stato buttato via, e tutto ciò che resta è identico al file originale
        assertNotNull(c.peek("furniture/${models.last().name}"))
        assertNull(c.peek("furniture/${models.first().name}"))
        for (info in c.list()) {
            val original = File(dir, info.path.removePrefix("furniture/")).readBytes()
            assertContentEquals(original, c.peek(info.path), info.path)
            assertEquals(sha(original), info.sha256)
        }
        assertEquals(emptyList(), c.verifyAll())
    }
}
