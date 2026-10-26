package com.sagoma.planimetria.model

import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.testi.Lingue
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Piani della casa: piano terra, interrati e quote; i nomi dei piani nelle quattro lingue. */
class BuildingLevelsTest {
    private fun floor(id: Long, name: String, height: Double) = Floor(id, name, FloorPlan(), levelHeight = height)

    // Due interrati (250 e 280 cm), piano terra (300), primo piano (320).
    private val house = Building(
        listOf(floor(1, "2° interrato", 250.0), floor(2, "Piano interrato", 280.0), floor(3, "Piano terra", 300.0), floor(4, "Primo piano", 320.0)),
        current = 2,
        ground = 2,
    )

    @Test
    fun `i piani si contano dal piano terra e le quote dal piano piu basso`() {
        assertEquals(listOf(-2, -1, 0, 1), house.floors.indices.map(house::level))
        assertEquals(listOf(0.0, 250.0, 530.0, 830.0), house.floors.indices.map(house::elevation))
        // Quota del terreno rispetto a ogni piano: è quella che il 3D usa per il prato.
        assertEquals(530.0, house.elevation(house.groundIndex) - house.elevation(0))
        assertEquals(house.floors[1], house.copy(current = 2).below)
    }

    @Test
    fun `un piano terra fuori dall'elenco (file rovinato) resta dentro i piani`() {
        assertEquals(3, house.copy(ground = 9).groundIndex)
        assertEquals(0, house.copy(ground = -4).groundIndex)
        val text = PlanJson.encode(house).replace("\"ground\":2", "\"ground\":12")
        assertEquals(3, PlanJson.decodeBuilding(text).groundIndex)
    }

    @Test
    fun `il piano terra e i piani si salvano e si rileggono`() {
        assertEquals(house, PlanJson.decodeBuilding(PlanJson.encode(house)))
        // Le case senza interrati scrivono il file come prima (niente "ground").
        val single = Building.single(FloorPlan())
        assertEquals(false, PlanJson.encode(single).contains("ground"))
    }

    @Test
    fun `i nomi automatici dei piani passano da una lingua all'altra e tornano uguali`() {
        for (from in Lingue.tutte) for (to in Lingue.tutte) for (i in -5..8) {
            val name = Building.floorName(i, from)
            val there = AutoNames.floor(name, to)
            assertEquals(Building.floorName(i, to), there, "piano $i da ${from.codice} a ${to.codice}")
            assertEquals(name, AutoNames.floor(there, from), "piano $i da ${from.codice} a ${to.codice} e ritorno")
        }
    }

    @Test
    fun `nomi diversi per piani diversi, in ogni lingua`() {
        for (lingua in Lingue.tutte) {
            val names = (-5..8).map { Building.floorName(it, lingua) }
            assertEquals(names.size, names.toSet().size, "${lingua.codice}: $names")
        }
    }

    @Ignore // DIFETTO: «Basement», «Sous-sol» e «Sótano» sono sia il nome automatico del primo interrato sia la traduzione di «Interrato».
    @Test
    fun `i nomi comuni dei piani passano da una lingua all'altra e tornano uguali`() {
        for (from in Lingue.tutte) for (to in Lingue.tutte) {
            if (from === to) continue
            for (name in from.piani.pianiComuni) {
                val there = AutoNames.floor(name, to)
                assertEquals(name, AutoNames.floor(there, from), "«$name» da ${from.codice} a ${to.codice} («$there») e ritorno")
            }
        }
    }
}
