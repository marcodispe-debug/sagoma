package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.FurnitureCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Documento salvato: la versione permette di migrare il formato in futuro.
 * Versione 1: un solo piano in `plan`. Versione 2: tutti i piani in `building`.
 */
@Serializable
private data class SavedDocument(val version: Int, val plan: FloorPlan? = null, val building: Building? = null)

object PlanJson {
    const val VERSION = 2

    /** Spessore minimo di un muro, come nell'editor (cm). */
    private const val MIN_WALL = 3.0

    private val json = Json {
        ignoreUnknownKeys = true // file scritti da versioni più nuove dell'app restano leggibili
        encodeDefaults = false
    }

    fun encode(building: Building): String =
        json.encodeToString(SavedDocument.serializer(), SavedDocument(VERSION, building = building))

    /** Casa a un solo piano. */
    fun encode(plan: FloorPlan): String = encode(Building.single(plan))

    fun decodeBuilding(text: String): Building {
        // Un file scritto da altri programmi può iniziare con il segno BOM: si ignora.
        val doc = json.decodeFromString(SavedDocument.serializer(), text.removePrefix("﻿"))
        val building = doc.building ?: Building.single(doc.plan ?: FloorPlan())
        // Piano corrente fuori misura (file scritto a mano o da altri programmi): si apre il più vicino.
        return building.copy(floors = building.floors.map { it.copy(plan = migrate(it.plan)) }, current = building.current.coerceIn(building.floors.indices))
    }

    /** Pianta del piano corrente. */
    fun decode(text: String): FloorPlan = decodeBuilding(text).floor.plan

    /** Adegua i dati letti da file scritti con versioni precedenti dell'app. */
    private fun migrate(plan: FloorPlan): FloorPlan = plan.copy(
        // Le aperture dei muri singoli stanno tutte sul muro 0: un altro numero (file rovinato) faceva cadere il 3D.
        freeWalls = uniqueFreeWallIds(plan.freeWalls).map { w ->
            if (w.openings.all { it.wallIndex == 0 }) w else w.copy(openings = w.openings.map { it.copy(wallIndex = 0) })
        },
        rooms = uniqueRoomIds(plan.rooms).map { room ->
            // Porte e impianti su un muro con numero negativo (file rovinato o scritto da altri programmi): non stanno su
            // nessun muro e facevano cadere 3D e quote. Si tolgono.
            val placed = if (room.openings.none { it.wallIndex < 0 } && room.fixtures.none { it.kind.mount == Mount.Wall && it.wallIndex < 0 }) room
            else room.copy(openings = room.openings.filter { it.wallIndex >= 0 }, fixtures = room.fixtures.filter { it.kind.mount != Mount.Wall || it.wallIndex >= 0 })
            // Muri eliminati con un numero che non c'è (stesso caso): il 3D cadeva cercando i varchi per la luce. Si tolgono.
            val opened = if (placed.removedWalls.all { it in 0 until placed.wallCount }) placed
            else placed.copy(removedWalls = placed.removedWalls.filter { it in 0 until placed.wallCount }.toSet())
            // Muri a spessore zero o negativo (stesso caso): il 3D cadeva sugli sguinci delle finestre. Diventano sottili
            // quanto il minimo che l'app permette.
            val r = if (opened.wallThickness >= MIN_WALL && opened.wallThicknesses.values.all { it >= MIN_WALL }) opened
            else opened.copy(wallThickness = opened.wallThickness.coerceAtLeast(MIN_WALL), wallThicknesses = opened.wallThicknesses.mapValues { it.value.coerceAtLeast(MIN_WALL) })
            // La porta-finestra non è più un tipo a sé: diventa un balcone ad 1 anta con le stesse misure.
            if (r.openings.none { it.kind == OpeningKind.FrenchDoor }) r
            else r.copy(openings = r.openings.map { if (it.kind == OpeningKind.FrenchDoor) it.copy(kind = OpeningKind.Balcony1) else it })
        },
        // Arredi del primo catalogo (Kenney): diventano il modello equivalente del catalogo attuale,
        // con le stesse misure e la stessa posizione.
        furniture = plan.furniture.map { f -> FurnitureCatalog.RENAMED[f.model]?.let { f.copy(model = it) } ?: f },
    )
}

/**
 * Stanze con lo stesso numero, o con un numero zero o negativo (file rovinato o scritto da altri programmi): l'app le
 * confondeva tra loro (quote e 3D cadevano, selezionarne una prendeva l'altra) e con i muri singoli, che usano i numeri
 * negativi per le loro porte. Ricevono un numero nuovo; la prima di quelle con lo stesso numero tiene il suo.
 */
private fun uniqueRoomIds(rooms: List<Room>): List<Room> =
    renumbered(rooms, { it.id }) { r, id -> r.copy(id = id) }

/** Stesso caso per i muri singoli: il numero della loro porta (negativo) finiva su quello di una stanza. */
private fun uniqueFreeWallIds(walls: List<FreeWall>): List<FreeWall> =
    renumbered(walls, { it.id }) { w, id -> w.copy(id = id) }

private fun <T> renumbered(items: List<T>, id: (T) -> Long, withId: (T, Long) -> T): List<T> {
    if (items.all { id(it) > 0 } && items.map(id).toSet().size == items.size) return items
    val seen = HashSet<Long>()
    var next = (items.maxOfOrNull(id) ?: 0L).coerceAtLeast(0L) + 1
    return items.map { item -> if (id(item) > 0 && seen.add(id(item))) item else withId(item, next++).also { seen += id(it) } }
}

interface PlanStore {
    /** La casa salvata, oppure null se non ce n'è una (o non è leggibile). */
    fun load(): Building?
    fun save(building: Building)
}
