package com.sagoma.planimetria.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le luci di «Impianti» → «Luci» che si scelgono tra i modelli del catalogo degli arredi. */
class CatalogLightsTest {
    private fun lamp(label: String, ceiling: Boolean = false, elevation: Double = 0.0, symbol: String = "Lamp") =
        FurnitureCatalog.Item(label, label, "Luci", symbol, 50.0, 50.0, 60.0, elevation = elevation, ceiling = ceiling)

    @Test
    fun `ogni lampada va con la sua luce`() {
        val cases = mapOf(
            lamp("Lampadario classico", ceiling = true) to FixtureKind.Chandelier,
            lamp("Lampadario", elevation = 187.0) to FixtureKind.Chandelier,
            lamp("Lampada sospesa", elevation = 190.0) to FixtureKind.Chandelier,
            lamp("Lanterna di carta", ceiling = true) to FixtureKind.Chandelier,
            lamp("Lampada da esterno", elevation = 191.0) to FixtureKind.Chandelier,
            lamp("Lampada da soffitto", elevation = 245.0) to FixtureKind.CeilingLight,
            lamp("Applique a gabbia", elevation = 180.0) to FixtureKind.WallLight,
            lamp("Lampada a muro", elevation = 140.0) to FixtureKind.WallLight,
            lamp("Faro da esterno", elevation = 220.0) to FixtureKind.WallSpot,
            lamp("Ventilatore a soffitto", ceiling = true) to null,
            lamp("Lampada da terra classica") to null,
            lamp("Lampada con paralume", elevation = 75.0) to null,
            lamp("Lampada da tavolo", symbol = "Table") to null,
        )
        for ((item, kind) in cases) assertEquals(kind, FurnitureCatalog.lightKind(item), item.label)
    }

    @Test
    fun `nel catalogo vero ci sono lampadari e applique da scegliere`() {
        val file = File("../catalogo/src/main/assets/furniture/catalog.json").takeIf { it.isFile } ?: return
        val saved = FurnitureCatalog.items
        try {
            FurnitureCatalog.load(file.readText())
            assertTrue(FurnitureCatalog.lights(FixtureKind.Chandelier).size >= 20)
            assertTrue(FurnitureCatalog.lights(FixtureKind.WallLight).size >= 3)
            // Faretti, neon e strisce LED il catalogo non li ha: si mettono subito come punto luce.
            for (k in listOf(FixtureKind.Spotlight, FixtureKind.Neon, FixtureKind.LedStrip, FixtureKind.WallLedStrip)) {
                assertTrue(FurnitureCatalog.lights(k).isEmpty(), k.name)
            }
            assertTrue(FurnitureCatalog.lights(FixtureKind.Chandelier).none { it.hidden })
        } finally {
            FurnitureCatalog.set(saved)
        }
    }
}
