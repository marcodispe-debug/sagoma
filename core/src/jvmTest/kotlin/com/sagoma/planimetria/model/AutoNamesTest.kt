package com.sagoma.planimetria.model

import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.en.Inglese
import com.sagoma.planimetria.testi.es.Spagnolo
import com.sagoma.planimetria.testi.fr.Francese
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cambiando lingua i nomi conosciuti di stanze e piani si traducono (automatici e comuni); gli altri no. */
class AutoNamesTest {
    @Test
    fun nomiAutomaticiTradotti() {
        assertEquals("Kitchen", AutoNames.room("Cucina", Inglese))
        assertEquals("Kitchen 2", AutoNames.room("Cucina 2", Inglese))
        assertEquals("Room", AutoNames.room("Stanza", Inglese))
        assertEquals("Camera da letto 3", AutoNames.room("Bedroom 3", Italiano))
        assertEquals("Ground floor", AutoNames.floor("Piano terra", Inglese))
        assertEquals("Floor 7", AutoNames.floor("Piano 7", Inglese))
        assertEquals("Primo piano", AutoNames.floor("First floor", Italiano))
        assertEquals("Shaft", AutoNames.zone("Cavedio", Inglese))
        assertEquals("Project 4", AutoNames.project("Progetto 4", Inglese))
        assertEquals(Inglese.progetti.mioProgetto, AutoNames.project("Il mio progetto", Inglese))
    }

    @Test
    fun nomiDellUtenteRestano() {
        assertEquals("Cucina di nonna", AutoNames.room("Cucina di nonna", Inglese))
        assertEquals("Cucina B", AutoNames.room("Cucina B", Inglese))
        assertEquals("Kitchen", AutoNames.room("Kitchen", Inglese))
        assertEquals("Casa di Marco", AutoNames.floor("Casa di Marco", Inglese))
        // Un nome composto si traduce solo se si conoscono tutte le parti.
        assertEquals("Lavanderia - cucina di nonna", AutoNames.room("Lavanderia - cucina di nonna", Francese))
    }

    @Test
    fun nomiComuniTradotti() {
        assertEquals("Chambre double", AutoNames.room("Camera doppia", Francese))
        assertEquals("Dormitorio doble", AutoNames.room("Camera doppia", Spagnolo))
        assertEquals("Double bedroom", AutoNames.room("Camera doppia", Inglese))
        assertEquals("Camera doppia", AutoNames.room("Chambre double", Italiano))
        assertEquals("Dégagement 2", AutoNames.room("Disimpegno 2", Francese))
        assertEquals("Camera matrimoniale", AutoNames.room("Chambre parentale", Italiano))
        assertEquals("Bagno padronale", AutoNames.room("En-suite bathroom", Italiano))
        assertEquals("Cuisine", AutoNames.room("CUCINA", Francese))
        // Sinonimi e abbreviazioni delle piantine: diventano il nome a cui portano.
        assertEquals("Balcon", AutoNames.room("Poggiolo", Francese))
        assertEquals("Balcón 2", AutoNames.room("Poggiolo 2", Spagnolo))
        assertEquals("Distribuidor", AutoNames.room("Disimp.", Spagnolo))
        assertEquals("Dégagement", AutoNames.room("DIs", Francese))
        assertEquals("Aseo", AutoNames.room("W.C.", Spagnolo))
        assertEquals("Chambre", AutoNames.room("Letto", Francese))
        // Composti: ogni parte, con le sue maiuscole e i suoi separatori.
        assertEquals("Buanderie - repassage", AutoNames.room("Lavanderia - stireria", Francese))
        assertEquals("Lavadero - cuarto de plancha", AutoNames.room("Lavanderia - stireria", Spagnolo))
        assertEquals("Salón-comedor", AutoNames.room("Soggiorno-sala da pranzo", Spagnolo))
        // Piani.
        assertEquals("Demi-sous-sol", AutoNames.floor("Seminterrato", Francese))
        assertEquals("Mansarde", AutoNames.floor("Mansarda", Francese))
        assertEquals("Seminterrato", AutoNames.floor("Semisótano", Italiano))
    }

    /** I nomi della mansarda di Marco (letti dalla sua piantina) cambiano tutti in francese e in spagnolo, e tornano in italiano. */
    @Test
    fun nomiDellaPiantinaDiMarco() {
        val nomi = listOf("Area fitness", "Bagno", "Lavanderia - stireria", "Disimpegno", "Bagno 2", "Camera doppia", "Camera matrimoniale", "Poggiolo")
        for (l in listOf(Inglese, Francese, Spagnolo)) {
            for (n in nomi) {
                val tradotto = AutoNames.room(n, l)
                assertTrue(tradotto != n, "\"$n\" non tradotto in ${l.codice}")
                if (n != "Poggiolo") assertEquals(n, AutoNames.room(tradotto, Italiano), "\"$n\" andata e ritorno da ${l.codice}")
            }
        }
    }

    /** Nomi della piantina «Appartamento tre camere»: prima restavano in italiano con l'app in spagnolo. */
    @Test
    fun cameraPadronaleESoggiornoConCucina() {
        assertEquals("Suite principal", AutoNames.room("Camera padronale", Spagnolo))
        assertEquals("Salón con cocina", AutoNames.room("Soggiorno con cucina", Spagnolo))
        assertEquals("Living room with kitchen", AutoNames.room("Soggiorno con cucina", Inglese))
        assertEquals("Suite parentale", AutoNames.room("Camera padronale", Francese))
        for (l in listOf(Inglese, Francese, Spagnolo)) for (n in listOf("Camera padronale", "Soggiorno con cucina")) {
            assertEquals(n, AutoNames.room(AutoNames.room(n, l), Italiano), "\"$n\" andata e ritorno da ${l.codice}")
        }
    }

    @Test
    fun casaIntera() {
        val plan = FloorPlan(rooms = listOf(Room(id = 1, name = "Cucina", type = RoomType.Cucina, points = listOf(Vec2(0.0, 0.0), Vec2(300.0, 0.0), Vec2(300.0, 300.0), Vec2(0.0, 300.0)))))
        val b = Building(listOf(Floor(1, "Piano terra", plan))).withAutoNames(Inglese)
        assertEquals("Ground floor", b.floors[0].name)
        assertEquals("Kitchen", b.floors[0].plan.rooms[0].name)
        assertEquals(b, b.withAutoNames(Inglese))
    }

    @Test
    fun franceseESpagnolo() {
        assertEquals("Cuisine 2", AutoNames.room("Cucina 2", Francese))
        assertEquals("Cocina 2", AutoNames.room("Cuisine 2", Spagnolo))
        assertEquals("Camera da letto", AutoNames.room("Dormitorio", Italiano))
        assertEquals("Rez-de-chaussée", AutoNames.floor("Planta baja", Francese))
        assertEquals("Planta 7", AutoNames.floor("Floor 7", Spagnolo))
        assertEquals("Projet 3", AutoNames.project("Proyecto 3", Francese))
        assertEquals(Spagnolo.progetti.mioProgetto, AutoNames.project(Francese.progetti.mioProgetto, Spagnolo))
        assertEquals(Francese.muroPerMuro.cavedio, AutoNames.zone("Cavedio", Francese))
    }

    /**
     * Ogni nome conosciuto (automatico, comune o sinonimo) deve indicare la stessa stanza in tutte le lingue in cui compare,
     * altrimenti la traduzione dipenderebbe da quale lingua si prova prima.
     */
    @Test
    fun vocabolarioSenzaAmbiguita() {
        for (l in Lingue.tutte) {
            assertEquals(Italiano.nomi.stanzeComuni.size, l.nomi.stanzeComuni.size, "stanze comuni ${l.codice}")
            assertEquals(Italiano.piani.pianiComuni.size, l.piani.pianiComuni.size, "piani comuni ${l.codice}")
            val tutti = l.nomi.stanzeComuni + RoomType.entries.map { l.nomi.roomDefaultName(it) } + l.nomi.sinonimiStanze.keys + l.piani.pianiComuni
            val doppi = tutti.groupBy { AutoNames.normalize(it) }.filter { it.value.size > 1 }.keys
            assertTrue(doppi.isEmpty(), "nomi ripetuti in ${l.codice}: $doppi")
            for ((sinonimo, nome) in l.nomi.sinonimiStanze) {
                assertTrue(nome in l.nomi.stanzeComuni || RoomType.entries.any { l.nomi.roomDefaultName(it) == nome }, "sinonimo $sinonimo → $nome sconosciuto")
            }
        }
        fun id(l: com.sagoma.planimetria.testi.Lingua, nome: String): String {
            val n = l.nomi.sinonimiStanze[nome] ?: nome
            RoomType.entries.firstOrNull { l.nomi.roomDefaultName(it) == n }?.let { return "tipo $it" }
            l.nomi.stanzeComuni.indexOf(n).takeIf { it >= 0 }?.let { return "stanza $it" }
            return "piano ${l.piani.pianiComuni.indexOf(n)}"
        }
        val voci = Lingue.tutte.flatMap { l ->
            (l.nomi.stanzeComuni + RoomType.entries.map { l.nomi.roomDefaultName(it) } + l.nomi.sinonimiStanze.keys + l.piani.pianiComuni)
                .map { AutoNames.normalize(it) to id(l, it) }
        }
        for ((nome, ids) in voci.groupBy({ it.first }, { it.second })) {
            assertTrue(ids.distinct().size == 1, "\"$nome\" indica cose diverse nelle lingue: ${ids.distinct()}")
        }
    }

    /** Un nome automatico di una lingua non deve essere quello di un'altra stanza o di un altro piano in un'altra lingua. */
    @Test
    fun nessunNomeInComuneTraLingue() {
        val stanze = Lingue.tutte.flatMap { l -> RoomType.entries.map { l.nomi.roomDefaultName(it) to it } }
        for ((nome, tipi) in stanze.groupBy({ it.first }, { it.second })) {
            assertTrue(tipi.distinct().size == 1, "\"$nome\" è il nome di più stanze: $tipi")
        }
        val piani = Lingue.tutte.flatMap { l -> (0..30).map { Building.floorName(it, l) to it } }
        for ((nome, numeri) in piani.groupBy({ it.first }, { it.second })) {
            assertTrue(numeri.distinct().size == 1, "\"$nome\" è il nome di più piani: $numeri")
        }
    }
}
