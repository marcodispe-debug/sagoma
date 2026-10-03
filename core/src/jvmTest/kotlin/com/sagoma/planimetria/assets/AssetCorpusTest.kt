package com.sagoma.planimetria.assets

import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gli asset di oggi (`app/src/pro/assets`) letti attraverso [DirectoryAssetStore] e [AssetPaths], cioè come li
 * leggono i renderer e l'interfaccia. Se la cartella non c'è la prova FALLISCE (non passa in silenzio):
 * quando gli asset staranno altrove andrà detto a questo test dove cercarli.
 */
class AssetCorpusTest {
    private val root = File("../app/src/pro/assets")
    private val savedFurniture = FurnitureCatalog.items
    private val savedMaterials = MaterialCatalog.items

    @AfterTest
    fun restore() {
        FurnitureCatalog.set(savedFurniture)
        MaterialCatalog.set(savedMaterials)
    }

    private fun store(): AssetStore {
        assertTrue(root.isDirectory, "Cartella degli asset non trovata: ${root.absolutePath}")
        return DirectoryAssetStore(root)
    }

    @Test
    fun `il catalogo degli arredi si legge e ogni modello ha i suoi tre file`() {
        val s = store()
        FurnitureCatalog.load(s.peek(AssetPaths.FURNITURE_CATALOG)!!.decodeToString())
        val items = FurnitureCatalog.items
        assertTrue(items.size >= 100, "catalogo troppo piccolo: ${items.size}")
        val missing = items.flatMap { i ->
            listOf(AssetPaths.furnitureModel(i.model), AssetPaths.furnitureThumbnail(i.model), AssetPaths.furnitureTop(i.model))
                .filter { !s.isAvailable(it) }
        }
        assertTrue(missing.isEmpty(), "File mancanti (${missing.size}): ${missing.take(5)}")
    }

    @Test
    fun `il catalogo dei materiali si legge e ogni materiale ha mappe e campione`() {
        val s = store()
        MaterialCatalog.load(s.peek(AssetPaths.MATERIAL_CATALOG)!!.decodeToString())
        val items = MaterialCatalog.items
        assertTrue(items.size >= 30, "troppo pochi materiali: ${items.size}")
        val missing = items.flatMap { m ->
            listOf("color", "normal", "orm").map { AssetPaths.materialMap(m.id, it) } + AssetPaths.materialThumbnail(m.id)
        }.filter { !s.isAvailable(it) }
        assertTrue(missing.isEmpty(), "File mancanti (${missing.size}): ${missing.take(5)}")
    }

    @Test
    fun `le luci ambiente ci sono e sono nel formato atteso`() {
        val s = store()
        for (name in listOf("giorno", "tramonto", "interno")) {
            val bytes = s.peek(AssetPaths.environment(name))
            assertTrue(bytes != null && bytes.size > 100, "luce $name non trovata")
            // Intestazione "SIBL" che FilamentSceneRenderer controlla prima di usare il file.
            assertEquals("SIBL", bytes.copyOfRange(0, 4).decodeToString(), "intestazione di $name")
        }
    }

    @Test
    fun `lo store legge gli stessi byte del file su disco`() {
        val s = store()
        val path = AssetPaths.FURNITURE_CATALOG
        assertTrue(s.peek(path)!!.contentEquals(File(root, path).readBytes()))
    }
}
