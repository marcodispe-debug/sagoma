package com.sagoma.planimetria.assets

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Negozi di asset: nessun file reale, solo cartelle temporanee, così le prove non dipendono dagli asset. */
class AssetStoreTest {
    private fun tempAssets(vararg files: Pair<String, String>): File {
        val dir = kotlin.io.path.createTempDirectory("sagoma-assets-").toFile()
        dir.deleteOnExit()
        for ((path, text) in files) File(dir, path).apply { parentFile.mkdirs(); writeText(text); deleteOnExit() }
        return dir
    }

    @Test
    fun `lo store vuoto non ha niente`() = runBlocking {
        assertEquals(AssetAvailability.Unavailable, EmptyAssetStore.availability("furniture/catalog.json"))
        assertNull(EmptyAssetStore.peek("furniture/catalog.json"))
        assertEquals(AssetAvailability.Unavailable, EmptyAssetStore.ensure("furniture/catalog.json"))
        assertNull(EmptyAssetStore.read("furniture/catalog.json"))
        assertFalse(EmptyAssetStore.isAvailable("x"))
    }

    @Test
    fun `lo store su cartella legge i file che ci sono`() = runBlocking {
        val store = DirectoryAssetStore(tempAssets("furniture/a.glb" to "glb-a", "env/giorno.ibl" to "ibl"))
        assertEquals(AssetAvailability.Available, store.availability("furniture/a.glb"))
        assertTrue(store.isAvailable("env/giorno.ibl"))
        assertContentEquals("glb-a".encodeToByteArray(), store.peek("furniture/a.glb"))
        // La richiesta che può aspettare dà lo stesso risultato, subito.
        assertEquals(AssetAvailability.Available, store.ensure("furniture/a.glb"))
        assertContentEquals("ibl".encodeToByteArray(), store.read("env/giorno.ibl"))
    }

    @Test
    fun `un file mancante o una cartella non sono disponibili`() = runBlocking {
        val store = DirectoryAssetStore(tempAssets("furniture/a.glb" to "x"))
        assertEquals(AssetAvailability.Unavailable, store.availability("furniture/b.glb"))
        assertNull(store.peek("furniture/b.glb"))
        assertNull(store.peek("furniture"))
        assertEquals(AssetAvailability.Unavailable, store.ensure("furniture/b.glb"))
        assertNull(store.read("furniture/b.glb"))
    }

    @Test
    fun `i percorsi non escono dalla cartella`() {
        val dir = tempAssets("furniture/a.glb" to "x")
        File(dir.parentFile, "segreto-${dir.name}.txt").apply { writeText("segreto"); deleteOnExit() }
        val store = DirectoryAssetStore(dir)
        assertNull(store.peek("../segreto-${dir.name}.txt"))
        assertNull(store.peek("furniture/../../segreto-${dir.name}.txt"))
        assertNull(store.peek("furniture\\..\\..\\segreto-${dir.name}.txt"))
        assertNull(store.peek("/etc/passwd"))
        assertNull(store.peek(""))
        assertEquals(AssetAvailability.Unavailable, store.availability("../segreto-${dir.name}.txt"))
    }

    @Test
    fun `lo store composto usa il primo che ha il file`() = runBlocking {
        val first = DirectoryAssetStore(tempAssets("furniture/a.glb" to "prima", "solo-prima.txt" to "p"))
        val second = DirectoryAssetStore(tempAssets("furniture/a.glb" to "seconda", "solo-seconda.txt" to "s"))
        val both = CompositeAssetStore(first, second)
        assertContentEquals("prima".encodeToByteArray(), both.peek("furniture/a.glb"))
        assertContentEquals("p".encodeToByteArray(), both.peek("solo-prima.txt"))
        assertContentEquals("s".encodeToByteArray(), both.peek("solo-seconda.txt"))
        assertEquals(AssetAvailability.Available, both.availability("solo-seconda.txt"))
        assertEquals(AssetAvailability.Available, both.ensure("solo-seconda.txt"))
        assertContentEquals("s".encodeToByteArray(), both.read("solo-seconda.txt"))
        assertNull(both.peek("manca"))
        assertEquals(AssetAvailability.Unavailable, both.ensure("manca"))
        assertNull(CompositeAssetStore().peek("x"))
    }

    /** Store finto: dice di avere il file ma non sempre riesce a leggerlo; può dare i byte solo dopo `ensure` (come uno remoto). */
    private class FakeStore(
        private val available: Boolean,
        private val content: ByteArray?,
        private val needsEnsure: Boolean = false,
    ) : AssetStore {
        var ensureCalls = 0
        private var ensured = false
        override fun availability(path: String) = if (available) AssetAvailability.Available else AssetAvailability.Unavailable
        override fun peek(path: String): ByteArray? = if (needsEnsure && !ensured) null else content
        override suspend fun ensure(path: String): AssetAvailability {
            ensureCalls++
            ensured = available
            return availability(path)
        }
    }

    @Test
    fun `composto - il primo dichiara il file ma la lettura fallisce, si prova il secondo`() = runBlocking {
        val unreadable = FakeStore(available = true, content = null)
        val second = DirectoryAssetStore(tempAssets("a.bin" to "dal-secondo"))
        val both = CompositeAssetStore(unreadable, second)
        assertContentEquals("dal-secondo".encodeToByteArray(), both.peek("a.bin"))
        assertContentEquals("dal-secondo".encodeToByteArray(), both.read("a.bin"))
    }

    @Test
    fun `composto - il secondo store puo aver bisogno di ensure prima di dare i byte`() = runBlocking {
        // Il primo dice di avere il file ma non lo legge; il secondo (come uno remoto) lo dà solo dopo ensure.
        val unreadable = FakeStore(available = true, content = null)
        val lazy = FakeStore(available = true, content = "scaricato".encodeToByteArray(), needsEnsure = true)
        val both = CompositeAssetStore(unreadable, lazy)
        assertNull(both.peek("a.bin")) // senza richiesta non c'è niente di pronto
        assertContentEquals("scaricato".encodeToByteArray(), both.read("a.bin"))
        assertTrue(lazy.ensureCalls >= 1)
    }

    @Test
    fun `composto - read non chiede agli store dopo il primo che ha i byte`() = runBlocking {
        val first = FakeStore(available = true, content = "primo".encodeToByteArray())
        val second = FakeStore(available = true, content = "secondo".encodeToByteArray())
        assertContentEquals("primo".encodeToByteArray(), CompositeAssetStore(first, second).read("a.bin"))
        assertEquals(0, second.ensureCalls)
    }

    @Test
    fun `composto - un file che nessuno ha non e disponibile e read da null`() = runBlocking {
        val both = CompositeAssetStore(FakeStore(false, null), FakeStore(false, null))
        assertEquals(AssetAvailability.Unavailable, both.availability("x"))
        assertEquals(AssetAvailability.Unavailable, both.ensure("x"))
        assertNull(both.peek("x"))
        assertNull(both.read("x"))
    }

    @Test
    fun `lo store sul classpath trova le risorse del programma`() {
        val loader = ClassLoader.getSystemClassLoader()
        val store = ClasspathAssetStore(loader)
        // Una risorsa che esiste di sicuro nel classpath di prova: la classe stessa.
        val own = "com/sagoma/planimetria/assets/AssetStoreTest.class"
        assertEquals(AssetAvailability.Available, store.availability(own))
        assertTrue(store.peek(own)!!.isNotEmpty())
        assertNull(store.peek("furniture/non-esiste.glb"))
        assertNull(store.peek("../com/sagoma/planimetria/assets/AssetStoreTest.class"))
    }

    @Test
    fun `i percorsi logici sono quelli di sempre`() {
        assertEquals("furniture/catalog.json", AssetPaths.FURNITURE_CATALOG)
        assertEquals("materials/materials.json", AssetPaths.MATERIAL_CATALOG)
        assertEquals("furniture/ph_sofa_01.glb", AssetPaths.furnitureModel("ph_sofa_01"))
        assertEquals("furniture/ph_sofa_01.png", AssetPaths.furnitureThumbnail("ph_sofa_01"))
        assertEquals("furniture/ph_sofa_01_top.png", AssetPaths.furnitureTop("ph_sofa_01"))
        assertEquals("materials/woodfloor040_color.jpg", AssetPaths.materialMap("woodfloor040", "color"))
        assertEquals("materials/woodfloor040_normal.jpg", AssetPaths.materialMap("woodfloor040", "normal"))
        assertEquals("materials/woodfloor040_orm.jpg", AssetPaths.materialMap("woodfloor040", "orm"))
        assertEquals("materials/woodfloor040_thumb.jpg", AssetPaths.materialThumbnail("woodfloor040"))
        assertEquals("env/giorno.ibl", AssetPaths.environment("giorno"))
    }
}
