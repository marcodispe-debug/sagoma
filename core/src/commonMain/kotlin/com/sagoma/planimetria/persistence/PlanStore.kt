package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
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
        rooms = uniqueRoomIds(plan.rooms).map { room ->
            // Porte e impianti su un muro con numero negativo (file rovinato o scritto da altri programmi): non stanno su
            // nessun muro e facevano cadere 3D e quote. Si tolgono.
            val placed = if (room.openings.none { it.wallIndex < 0 } && room.fixtures.none { it.kind.mount == Mount.Wall && it.wallIndex < 0 }) room
            else room.copy(openings = room.openings.filter { it.wallIndex >= 0 }, fixtures = room.fixtures.filter { it.kind.mount != Mount.Wall || it.wallIndex >= 0 })
            // Muri a spessore zero o negativo (stesso caso): il 3D cadeva sugli sguinci delle finestre. Diventano sottili
            // quanto il minimo che l'app permette.
            val r = if (placed.wallThickness >= MIN_WALL && placed.wallThicknesses.values.all { it >= MIN_WALL }) placed
            else placed.copy(wallThickness = placed.wallThickness.coerceAtLeast(MIN_WALL), wallThicknesses = placed.wallThicknesses.mapValues { it.value.coerceAtLeast(MIN_WALL) })
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
 * Stanze con lo stesso numero (file rovinato o scritto da altri programmi): l'app le confondeva tra loro (quote e 3D
 * cadevano, selezionarne una prendeva l'altra). Dalla seconda in poi ricevono un numero nuovo.
 */
private fun uniqueRoomIds(rooms: List<Room>): List<Room> {
    if (rooms.map { it.id }.toSet().size == rooms.size) return rooms
    val seen = HashSet<Long>()
    var next = (rooms.maxOfOrNull { it.id } ?: 0L) + 1
    return rooms.map { r -> if (seen.add(r.id)) r else r.copy(id = next++).also { seen += it.id } }
}

interface PlanStore {
    /** La casa salvata, oppure null se non ce n'è una (o non è leggibile). */
    fun load(): Building?
    fun save(building: Building)
}
