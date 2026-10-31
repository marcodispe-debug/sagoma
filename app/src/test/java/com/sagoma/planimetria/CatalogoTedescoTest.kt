package com.sagoma.planimetria

import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.de.Tedesco
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nomi del catalogo in tedesco: ogni arredo e ogni materiale ha il suo, i dati restano in italiano. */
class CatalogoTedescoTest {
    @After
    fun italiano() {
        T.lingua = Italiano
    }

    private fun carica() {
        MaterialCatalog.load(java.io.File("../catalogo/src/main/assets/materials/materials.json").readText())
        FurnitureCatalog.load(java.io.File("../catalogo/src/main/assets/furniture/catalog.json").readText())
        FurnitureCatalog.addRugs(MaterialCatalog.items)
        FurnitureCatalog.addParametric()
    }

    @Test
    fun `ogni arredo e ogni materiale del catalogo ha il nome tedesco`() {
        carica()
        T.lingua = Tedesco
        val arredi = FurnitureCatalog.items.filter { it.nome == it.label && !ParametricFurniture.isModel(it.model) }.map { it.label }.distinct()
        val materiali = MaterialCatalog.items.filter { it.nome == it.label }.map { it.label }
        // Nomi che in tedesco sono uguali all'italiano.
        val uguali = setOf("Apple iMac 2004", "Bonsai", "Ficus", "Futon", "PlayStation", "Pouf", "Opus incertum", "Terrazzo beige")
        assertEquals(emptyList<String>(), (arredi + materiali).filterNot { it in uguali })
    }

    @Test
    fun `i nomi seguono la lingua, i dati no`() {
        carica()
        val divano = FurnitureCatalog.items.first { it.label == "Divano letto" }
        val tappeto = FurnitureCatalog.items.first { it.model == FurnitureCatalog.RUG + MaterialCatalog.items.first { m -> m.label == "Juta" }.id + ":o" }
        val armadio = FurnitureCatalog.items.first { it.model.startsWith(ParametricFurniture.PREFIX + "armadio:") }
        T.lingua = Tedesco
        assertEquals("Schlafsofa", divano.nome)
        assertEquals("Divano letto", divano.label)
        assertEquals("Runder Teppich aus Jute", tappeto.nome)
        assertEquals("Kleiderschrank nach Maß", armadio.nome)
        assertTrue(FurnitureCatalog.search("schlafsofa").any { it.label == "Divano letto" })
        assertTrue(FurnitureCatalog.search("divano letto").any { it.label == "Divano letto" })
    }
}
