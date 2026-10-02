package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.OpeningKind
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
        return building.copy(floors = building.floors.map { it.copy(plan = migrate(it.plan)) })
    }

    /** Pianta del piano corrente. */
    fun decode(text: String): FloorPlan = decodeBuilding(text).floor.plan

    /** Adegua i dati letti da file scritti con versioni precedenti dell'app. */
    private fun migrate(plan: FloorPlan): FloorPlan = plan.copy(
        rooms = plan.rooms.map { room ->
            // La porta-finestra non è più un tipo a sé: diventa un balcone ad 1 anta con le stesse misure.
            if (room.openings.none { it.kind == OpeningKind.FrenchDoor }) room
            else room.copy(openings = room.openings.map { if (it.kind == OpeningKind.FrenchDoor) it.copy(kind = OpeningKind.Balcony1) else it })
        },
        // Arredi del primo catalogo (Kenney): diventano il modello equivalente del catalogo attuale,
        // con le stesse misure e la stessa posizione.
        furniture = plan.furniture.map { f -> FurnitureCatalog.RENAMED[f.model]?.let { f.copy(model = it) } ?: f },
    )
}

interface PlanStore {
    /** La casa salvata, oppure null se non ce n'è una (o non è leggibile). */
    fun load(): Building?
    fun save(building: Building)
}
