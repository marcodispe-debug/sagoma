package com.sagoma.planimetria

import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.Lingua
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.es.Spagnolo
import com.sagoma.planimetria.testi.fr.Francese
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nomi del catalogo in francese e in spagnolo: ogni arredo e ogni materiale ha il suo, i dati restano in italiano. */
class CatalogoFranceseSpagnoloTest {
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

    /** Nomi senza traduzione: quelli che mancano, tolti quelli che nella lingua sono uguali all'italiano. */
    private fun senzaNome(lingua: Lingua, uguali: Set<String>): List<String> {
        carica()
        T.lingua = lingua
        val arredi = FurnitureCatalog.items.filter { it.nome == it.label && !ParametricFurniture.isModel(it.model) }.map { it.label }.distinct()
        val materiali = MaterialCatalog.items.filter { it.nome == it.label }.map { it.label }
        return (arredi + materiali).filterNot { it in uguali }
    }

    @Test
    fun `ogni arredo e ogni materiale del catalogo ha il nome francese`() {
        val uguali = setOf(
            "Apple iMac 2004", "Applique", "Cactus", "Chaise longue", "Ficus", "Futon", "PlayStation", "Pouf", "Pouf capitonné",
            "TV", "TV 16:9", "Moquette beige", "Opus incertum", "Tartan", "Terrazzo multicolore",
        )
        assertEquals(emptyList<String>(), senzaNome(Francese, uguali))
    }

    @Test
    fun `ogni arredo e ogni materiale del catalogo ha il nome spagnolo`() {
        val uguali = setOf(
            "Apple iMac 2004", "Cactus", "Chaise longue", "Enciclopedia", "Ficus", "Lavanda", "PlayStation", "TV", "TV 16:9",
            "Gres beige", "Lino verde", "Opus incertum", "Travertino", "Granito rosa", "Microcemento perla", "Mosaico verde salvia",
            "Travertino oro", "Travertino silver", "Zellige verde",
        )
        assertEquals(emptyList<String>(), senzaNome(Spagnolo, uguali))
    }

    @Test
    fun `i nomi seguono la lingua, i dati no`() {
        carica()
        val divano = FurnitureCatalog.items.first { it.label == "Divano letto" }
        val tappeto = FurnitureCatalog.items.first { it.model == FurnitureCatalog.RUG + MaterialCatalog.items.first { m -> m.label == "Juta" }.id + ":o" }
        val armadio = FurnitureCatalog.items.first { it.model.startsWith(ParametricFurniture.PREFIX + "armadio:") }
        T.lingua = Francese
        assertEquals("Canapé-lit", divano.nome)
        assertEquals("Divano letto", divano.label)
        assertEquals("Tapis rond en jute", tappeto.nome)
        assertEquals("Armoire sur mesure", armadio.nome)
        assertTrue(FurnitureCatalog.search("canapé-lit").any { it.label == "Divano letto" })
        T.lingua = Spagnolo
        assertEquals("Sofá cama", divano.nome)
        assertEquals("Alfombra redonda de yute", tappeto.nome)
        assertEquals("Armario a medida", armadio.nome)
        assertTrue(FurnitureCatalog.search("sofá cama").any { it.label == "Divano letto" })
        assertTrue(FurnitureCatalog.search("divano letto").any { it.label == "Divano letto" })
    }
}
