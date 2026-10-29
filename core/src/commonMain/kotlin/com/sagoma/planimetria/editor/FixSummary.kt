package com.sagoma.planimetria.editor

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiControllo
import kotlin.math.abs

/**
 * Cosa ha cambiato «Correggi» (o «Correggi tutti») nella casa, da dire a chi disegna: quante porte, finestre, impianti, mobili
 * e stanze sono stati aggiunti, tolti (o chiusi, con stato di fatto e di progetto), spostati o cambiati.
 */
object FixSummary {
    /** Le cose della casa, nell'ordine in cui si elencano. */
    enum class Thing { Door, Window, BalconyDoor, Passage, Niche, Radiator, WaterPoint, Outlet, Switch, Light, Furniture, Room }

    /** Quante cose di ogni tipo: aggiunte, tolte (o chiuse), spostate, cambiate (misure, verso dell'anta, forma). */
    data class Summary(
        val added: Map<Thing, Int> = emptyMap(),
        val removed: Map<Thing, Int> = emptyMap(),
        val moved: Map<Thing, Int> = emptyMap(),
        val changed: Map<Thing, Int> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && moved.isEmpty() && changed.isEmpty()
    }

    fun thing(k: OpeningKind): Thing = when (k) {
        OpeningKind.Door, OpeningKind.Door2 -> Thing.Door
        OpeningKind.Window1, OpeningKind.Window2 -> Thing.Window
        OpeningKind.Balcony1, OpeningKind.Balcony2, OpeningKind.FrenchDoor -> Thing.BalconyDoor
        OpeningKind.Passage -> Thing.Passage
        OpeningKind.Niche -> Thing.Niche
    }

    fun thing(k: FixtureKind): Thing = when (k) {
        FixtureKind.Radiator -> Thing.Radiator
        FixtureKind.WaterPoint -> Thing.WaterPoint
        FixtureKind.Outlet -> Thing.Outlet
        FixtureKind.Switch -> Thing.Switch
        else -> Thing.Light
    }

    /** Cosa cambia da `before` a `after` (le cose si riconoscono dal piano e dal loro id). */
    fun of(before: Building, after: Building): Summary {
        val added = mutableMapOf<Thing, Int>()
        val removed = mutableMapOf<Thing, Int>()
        val moved = mutableMapOf<Thing, Int>()
        val changed = mutableMapOf<Thing, Int>()
        fun MutableMap<Thing, Int>.count(t: Thing, n: Int = 1) {
            this[t] = (this[t] ?: 0) + n
        }
        for (f in 0 until maxOf(before.floors.size, after.floors.size)) {
            val p = before.floors.getOrNull(f)?.plan
            val q = after.floors.getOrNull(f)?.plan
            // Porte e finestre (anche dei muri singoli; l'id è unico nel piano): una che si chiude è tolta, una su un altro muro o
            // in un altro punto è spostata. Tolta una e messa un'altra dello stesso tipo (la porta del bagno su un altro muro): spostata.
            val was = p?.openingHosts.orEmpty().flatMap { h -> h.openings.map { it.id to (h.id to it) } }.toMap()
            val now = q?.openingHosts.orEmpty().flatMap { h -> h.openings.map { it.id to (h.id to it) } }.toMap()
            val openingsAdded = mutableMapOf<Thing, Int>()
            val openingsRemoved = mutableMapOf<Thing, Int>()
            for ((id, hosted) in now) {
                val (host, o) = hosted
                val prev = was[id]
                if (prev == null) {
                    openingsAdded.count(thing(o.kind))
                    continue
                }
                val (oldHost, old) = prev
                when {
                    old.phase != Phase.Demolish && o.phase == Phase.Demolish -> openingsRemoved.count(thing(old.kind))
                    oldHost != host || old.wallIndex != o.wallIndex || abs(old.position - o.position) > 1e-6 -> moved.count(thing(o.kind))
                    old != o -> changed.count(thing(o.kind))
                }
            }
            for ((id, hosted) in was) if (id !in now) openingsRemoved.count(thing(hosted.second.kind))
            for (t in openingsAdded.keys + openingsRemoved.keys) {
                val a = openingsAdded[t] ?: 0
                val r = openingsRemoved[t] ?: 0
                val both = minOf(a, r)
                if (both > 0) moved.count(t, both)
                if (a > both) added.count(t, a - both)
                if (r > both) removed.count(t, r - both)
            }
            // Impianti.
            val fixturesBefore = p?.rooms.orEmpty().flatMap { r -> r.fixtures.map { (r.id to it.id) to it } }.toMap()
            val fixturesAfter = q?.rooms.orEmpty().flatMap { r -> r.fixtures.map { (r.id to it.id) to it } }.toMap()
            for ((key, x) in fixturesAfter) {
                val old = fixturesBefore[key]
                if (old == null) added.count(thing(x.kind)) else if (old != x) changed.count(thing(x.kind))
            }
            for ((key, x) in fixturesBefore) if (key !in fixturesAfter) removed.count(thing(x.kind))
            // Mobili.
            val furnitureBefore = p?.furniture.orEmpty().associateBy { it.id }
            val furnitureAfter = q?.furniture.orEmpty().associateBy { it.id }
            for ((id, m) in furnitureAfter) {
                val old = furnitureBefore[id]
                when {
                    old == null -> added.count(Thing.Furniture)
                    old.center != m.center || old.rotation != m.rotation -> moved.count(Thing.Furniture)
                    old != m -> changed.count(Thing.Furniture)
                }
            }
            for (id in furnitureBefore.keys) if (id !in furnitureAfter) removed.count(Thing.Furniture)
            // Stanze: spostata se tutti gli angoli si muovono insieme, se no cambiata (un muro spostato, un angolo tolto).
            val roomsBefore = p?.rooms.orEmpty().associateBy { it.id }
            val roomsAfter = q?.rooms.orEmpty().associateBy { it.id }
            for ((id, r) in roomsAfter) {
                val old = roomsBefore[id]
                when {
                    old == null -> added.count(Thing.Room)
                    old.points == r.points -> Unit
                    old.points.size == r.points.size && r.points.indices.all { k ->
                        val d = r.points[k] - old.points[k]
                        val d0 = r.points[0] - old.points[0]
                        abs(d.x - d0.x) < 1e-6 && abs(d.y - d0.y) < 1e-6
                    } -> moved.count(Thing.Room)
                    else -> changed.count(Thing.Room)
                }
            }
            for (id in roomsBefore.keys) if (id !in roomsAfter) removed.count(Thing.Room)
        }
        return Summary(added, removed, moved, changed)
    }

    /** Il riassunto a parole, nella lingua dei testi: «aggiunto: 2 porte e 3 prese; tolto: 1 porta»; null se non è cambiato niente. */
    fun describe(s: Summary, t: TestiControllo = T.controllo): String? {
        if (s.isEmpty) return null
        fun list(m: Map<Thing, Int>) = m.entries.sortedBy { it.key.ordinal }.map { (k, n) -> t.quante(k, n) }.takeIf { it.isNotEmpty() }?.let(t::elenco)
        return t.fatto(list(s.added), list(s.removed), list(s.moved), list(s.changed))
    }

    /** Il messaggio dopo `n` correzioni da `before` a `after`: quante e cosa è cambiato. */
    fun message(n: Int, before: Building, after: Building, t: TestiControllo = T.controllo): String =
        describe(of(before, after), t)?.let { t.corretti(n, it) } ?: t.corretti(n)
}
