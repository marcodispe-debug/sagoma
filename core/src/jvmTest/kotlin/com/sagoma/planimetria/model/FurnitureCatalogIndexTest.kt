package com.sagoma.planimetria.model

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** `FurnitureCatalog.item` usa un indice: stesso risultato della ricerca in elenco, anche dopo i cambi del catalogo. */
class FurnitureCatalogIndexTest {
    private val saved = FurnitureCatalog.items

    @AfterTest
    fun restore() = FurnitureCatalog.set(saved)

    private fun item(model: String, label: String = model) =
        FurnitureCatalog.Item(model, label, "Soggiorno", width = 100.0, depth = 50.0, height = 80.0)

    @Test
    fun `trova ogni voce e null per quelle che non ci sono`() {
        val list = (1..50).map { item("m$it") }
        FurnitureCatalog.set(list)
        for (i in list) assertSame(i, FurnitureCatalog.item(i.model))
        assertNull(FurnitureCatalog.item("assente"))
        assertNull(FurnitureCatalog.item(""))
    }

    @Test
    fun `se un modello e ripetuto vale il primo`() {
        val first = item("dup", "primo")
        FurnitureCatalog.set(listOf(first, item("dup", "secondo"), item("altro")))
        assertSame(first, FurnitureCatalog.item("dup"))
        assertEquals("primo", FurnitureCatalog.item("dup")!!.label)
    }

    @Test
    fun `l'indice segue i cambi del catalogo`() {
        FurnitureCatalog.set(listOf(item("a")))
        assertSame(FurnitureCatalog.items[0], FurnitureCatalog.item("a"))
        FurnitureCatalog.set(listOf(item("b")))
        assertNull(FurnitureCatalog.item("a"))
        assertSame(FurnitureCatalog.items[0], FurnitureCatalog.item("b"))

        FurnitureCatalog.load("""[{"model":"c","label":"C","category":"Soggiorno","width":1.0,"depth":1.0,"height":1.0}]""")
        assertNull(FurnitureCatalog.item("b"))
        assertEquals("C", FurnitureCatalog.item("c")!!.label)
    }

    @Test
    fun `le voci nascoste non si scelgono ma i progetti le trovano ancora`() {
        val hidden = item("rotto", "Lavastoviglie").copy(category = "Cucina", hidden = true)
        FurnitureCatalog.set(listOf(item("a", "Divano"), hidden, item("b", "Lavello").copy(category = "Bagno")))
        assertSame(hidden, FurnitureCatalog.item("rotto"))
        assertEquals(listOf("a", "b"), FurnitureCatalog.visible.map { it.model })
        assertEquals(listOf("Soggiorno", "Bagno"), FurnitureCatalog.categories)
        assertEquals(emptyList(), FurnitureCatalog.search("lavastoviglie"))
        assertEquals(listOf("a", "b"), FurnitureCatalog.search("").map { it.model })
        // Dal file: il campo manca nelle voci normali.
        FurnitureCatalog.load("""[{"model":"c","label":"C","category":"Cucina","width":1.0,"depth":1.0,"height":1.0,"hidden":true},{"model":"d","label":"D","category":"Cucina","width":1.0,"depth":1.0,"height":1.0}]""")
        assertEquals(true, FurnitureCatalog.item("c")!!.hidden)
        assertEquals(listOf("d"), FurnitureCatalog.visible.map { it.model })
    }

    @Test
    fun `i tappeti aggiunti si trovano`() {
        FurnitureCatalog.set(listOf(item("a")))
        FurnitureCatalog.item("a") // costruisce l'indice
        FurnitureCatalog.addRugs(listOf(MaterialCatalog.Item("lana", "Lana", "Moquette", use = "tappeto")))
        val rect = FurnitureCatalog.item("${FurnitureCatalog.RUG}lana:r")
        val round = FurnitureCatalog.item("${FurnitureCatalog.RUG}lana:o")
        assertEquals("Tappeto in lana", rect!!.label)
        assertEquals("Tappeto tondo in lana", round!!.label)
        assertEquals("a", FurnitureCatalog.item("a")!!.model)
    }
}
