package com.sagoma.planimetria

import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.en.Inglese
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nomi del catalogo in inglese: ogni arredo e ogni materiale ha il suo, i dati restano in italiano. */
class CatalogoIngleseTest {
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
    fun `ogni arredo e ogni materiale del catalogo ha il nome inglese`() {
        carica()
        T.lingua = Inglese
        val arredi = FurnitureCatalog.items.filter { it.nome == it.label && !ParametricFurniture.isModel(it.model) }.map { it.label }.distinct()
        val materiali = MaterialCatalog.items.filter { it.nome == it.label }.map { it.label }
        // Nomi che in inglese sono uguali all'italiano.
        val uguali = setOf("Apple iMac 2004", "Bonsai", "Buddha", "Cactus", "Chaise longue", "Ficus", "Futon", "PlayStation", "Pouf", "TV", "Opus incertum", "Tartan")
        assertEquals(emptyList<String>(), arredi.filterNot { it in uguali })
        assertEquals(emptyList<String>(), materiali.filterNot { it in uguali })
    }

    @Test
    fun `i nomi seguono la lingua, i dati no`() {
        carica()
        val divano = FurnitureCatalog.items.first { it.label == "Divano letto" }
        val tappeto = FurnitureCatalog.items.first { it.model == FurnitureCatalog.RUG + MaterialCatalog.items.first { m -> m.label == "Juta" }.id + ":o" }
        val armadio = FurnitureCatalog.items.first { it.model.startsWith(ParametricFurniture.PREFIX + "armadio:") }
        assertEquals("Divano letto", divano.nome)
        assertEquals("Tappeto tondo in juta", tappeto.nome)
        assertEquals("Armadio su misura", armadio.nome)
        T.lingua = Inglese
        assertEquals("Sofa bed", divano.nome)
        assertEquals("Divano letto", divano.label)
        assertEquals("Round jute rug", tappeto.nome)
        assertEquals("Custom wardrobe", armadio.nome)
        assertTrue(FurnitureCatalog.search("sofa bed").any { it.label == "Divano letto" })
        assertTrue(FurnitureCatalog.search("divano letto").any { it.label == "Divano letto" })
    }
}
