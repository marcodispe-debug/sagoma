package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.DirectoryAssetStore
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [FurnitureAssets] sopra uno [AssetStore]: cataloghi caricati dallo store, immagini lette una volta sola e
 * cache con un limite. Gli asset sono finti (cartella temporanea), così la prova non dipende da quelli veri.
 * `FurnitureAssets` è un oggetto che si inizializza una volta sola: per questo c'è un'unica prova.
 */
class FurnitureAssetsTest {
    private class FakePlatform(override val assetStore: AssetStore, root: File, private val decode: () -> ImageBitmap) : Platform {
        override val isPro = true
        override val projects = FileProjectRepository(root)
        override val tips = FileTipStore(root)
        override fun toast(message: String) {}
        override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {}
        override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {}
        override fun shareText(subject: String, text: String) {}
        override fun decodeImage(bytes: ByteArray): ImageBitmap = decode()
        override fun encodePng(image: ImageBitmap): ByteArray? = null
        override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage? = null
        override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray? = null
        override fun formatDate(epochMillis: Long, withTime: Boolean): String = ""
        override fun now(): Long = 0
        override fun createSceneRenderer(): SceneRenderer = error("non serve")
    }

    @Test
    fun `cataloghi dallo store e immagini in cache con limite`() {
        val savedFurniture = FurnitureCatalog.items
        val savedMaterials = MaterialCatalog.items
        try {
            val models = 300 // 300 miniature da 256×200 RGBA (circa 61 MB) superano il limite delle miniature
            val root = Files.createTempDirectory("sagoma-furniture-assets").toFile().also { it.deleteOnExit() }
            fun write(path: String, text: String = "x") = File(root, "assets/$path").apply { parentFile.mkdirs(); writeText(text) }
            write(
                "furniture/catalog.json",
                (1..models).joinToString(",", "[", "]") {
                    """{"model":"m$it","label":"M$it","category":"Soggiorno","width":100.0,"depth":50.0,"height":80.0}"""
                },
            )
            write("materials/materials.json", """[{"id":"lana","label":"Lana","category":"Moquette","use":"tappeto"}]""")
            write("materials/lana_thumb.jpg")
            for (i in 1..models) {
                write("furniture/m$i.png")
                write("furniture/m${i}_top.png")
            }
            var decoded = 0
            val platform = FakePlatform(DirectoryAssetStore(File(root, "assets")), root) { decoded++; ImageBitmap(256, 200) }

            FurnitureAssets.init(platform)

            // Cataloghi letti dallo store; i tappeti nascono dai materiali (2 per materiale da tappeto).
            assertEquals(models + 2, FurnitureCatalog.items.size)
            assertEquals(1, MaterialCatalog.items.size)
            assertEquals("M7", FurnitureCatalog.item("m7")!!.label)

            // Un'immagine si decodifica una volta sola finché sta in cache.
            assertNotNull(FurnitureAssets.thumbnail("m1"))
            assertEquals(1, decoded)
            assertNotNull(FurnitureAssets.thumbnail("m1"))
            assertEquals(1, decoded)

            // Miniatura e vista dall'alto sono immagini diverse; un file che non c'è dà null.
            assertNotNull(FurnitureAssets.top("m1"))
            assertEquals(2, decoded)
            assertNull(FurnitureAssets.thumbnail("non-esiste"))
            assertEquals(2, decoded)

            // Tappeto di materiale: usa il campione del materiale, non un file del mobile.
            assertNotNull(FurnitureAssets.thumbnail("tappeto:lana:r"))
            assertNotNull(FurnitureAssets.materialThumb("lana"))
            assertEquals(3, decoded)

            // La cache ha un limite: sfogliando tutto, la prima miniatura è stata buttata via e si decodifica di nuovo...
            for (i in 1..models) assertNotNull(FurnitureAssets.thumbnail("m$i"))
            val afterAll = decoded
            assertNotNull(FurnitureAssets.thumbnail("m1"))
            assertEquals(afterAll + 1, decoded, "la prima miniatura doveva essere stata buttata via")
            // ...mentre l'ultima, usata da poco, è ancora in cache.
            assertNotNull(FurnitureAssets.thumbnail("m$models"))
            assertEquals(afterAll + 1, decoded, "l'ultima miniatura doveva essere ancora in cache")
        } finally {
            FurnitureCatalog.set(savedFurniture)
            MaterialCatalog.set(savedMaterials)
        }
    }
}
