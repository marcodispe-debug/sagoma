package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dotazione minima dell'impianto elettrico di una casa, livello 1 (base) della norma CEI 64-8 (parte 3, capitolo 37): punti presa e
 * punti luce per stanza, secondo il tipo e la superficie calpestabile. Si guarda solo quello che si disegna in sagoma: non le prese
 * TV e telefono, i circuiti, le luci di sicurezza. Un punto presa è una scatola con una o più prese; un punto luce una luce a
 * soffitto o a parete.
 */
object ElectricRules {
    /** Punti presa e punti luce. */
    data class Points(val outlets: Int, val lights: Int)

    /** Corridoio lungo (cm): oltre servono due punti presa e due punti luce. */
    const val LONG_CORRIDOR_CM = 500.0

    /**
     * Minimi della stanza, null se la norma non chiede niente (stanze da 8 m² o meno, balconi sotto 10 m², stanze «Altro»):
     * - camera, soggiorno, studio: 4 punti presa e 1 punto luce fino a 12 m², 5 e 1 fino a 20, 6 e 2 oltre; nelle camere da letto un
     *   punto presa in meno;
     * - cucina 5 e 1; bagno 2 e 2 (un punto presa se non c'è la lavatrice), WC senza doccia né vasca 1 e 1;
     * - corridoio 1 e 1, 2 e 2 se è lungo più di 5 m; balcone o terrazza da 10 m² 1 e 1.
     */
    fun minimum(plan: FloorPlan, room: Room): Points? {
        val inside = room.interior()
        if (inside.size < 3) return null
        // Metri quadri al centesimo, come si leggono: «8,00 m²» non è sopra 8.
        val a = (room.usableArea() / 100).roundToInt() / 100.0
        return when (room.type) {
            RoomType.Soggiorno, RoomType.Camera, RoomType.Cameretta, RoomType.Studio -> {
                val less = if (room.type == RoomType.Camera || room.type == RoomType.Cameretta) 1 else 0
                when {
                    a <= 8.0 -> null
                    a <= 12.0 -> Points(4 - less, 1)
                    a <= 20.0 -> Points(5 - less, 1)
                    else -> Points(6 - less, 2)
                }
            }
            RoomType.Cucina -> Points(5, 1)
            RoomType.Bagno -> {
                val sanitaries = HousingRules.sanitaries(plan, room)
                val toilet = wc(room.name) || (sanitaries.isNotEmpty() && HousingRules.Sanitary.BathOrShower !in sanitaries)
                val washer = plan.furniture.any { f ->
                    FurnitureCatalog.item(f.model)?.label?.startsWith("Lavatrice") == true && Polygon.contains(inside, f.center)
                }
                when {
                    toilet && !washer -> Points(1, 1)
                    else -> Points(if (washer) 2 else 1, 2)
                }
            }
            RoomType.Corridoio -> {
                val b = Polygon.bounds(inside)
                if (max(b.width, b.height) > LONG_CORRIDOR_CM) Points(2, 2) else Points(1, 1)
            }
            RoomType.Balcone, RoomType.Terrazza -> if (a >= 10.0) Points(1, 1) else null
            RoomType.Altro -> null
        }
    }

    /** Bagno di servizio dal nome: «WC», «W.c.», «Servizio». */
    private fun wc(name: String): Boolean {
        val n = name.trim().lowercase()
        return n.startsWith("wc") || n.startsWith("w.c") || n.startsWith("servizi") || n.contains("bagno di servizio")
    }

    /** Punti presa e punti luce messi nella stanza. */
    fun points(room: Room): Points =
        Points(room.fixtures.count { it.kind == FixtureKind.Outlet }, room.fixtures.count { it.kind.isLight })

    /** Nella casa si sta disegnando l'impianto elettrico: c'è almeno una presa. */
    fun wired(rooms: List<Room>): Boolean = rooms.any { r -> r.fixtures.any { it.kind == FixtureKind.Outlet } }
}
