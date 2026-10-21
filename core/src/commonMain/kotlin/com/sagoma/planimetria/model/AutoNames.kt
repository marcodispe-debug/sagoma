package com.sagoma.planimetria.model

import com.sagoma.planimetria.testi.Lingua
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.T

/**
 * Nomi automatici di stanze e piani ("Cucina 2", "Piano terra") portati nella lingua dell'app: cambiando lingua
 * diventano "Kitchen 2", "Ground floor". I nomi scritti dall'utente restano come sono.
 */
object AutoNames {
    /** Nome del piano nella lingua `lingua`, se `name` è un nome automatico di un'altra lingua; altrimenti `name`. */
    fun floor(name: String, lingua: Lingua = T.lingua): String {
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            for (i in 0..MAX_FLOORS) if (name == Building.floorName(i, altra)) return Building.floorName(i, lingua)
        }
        return name
    }

    /** Nome della stanza nella lingua `lingua`, se `name` è un nome automatico ("Cucina", "Cucina 2") di un'altra lingua. */
    fun room(name: String, lingua: Lingua = T.lingua): String {
        // Un nome che è già automatico nella lingua giusta non si tocca ("Studio" è anche inglese… per chi lo scrive).
        if (RoomType.entries.any { matchesAuto(name, lingua.nomi.roomDefaultName(it)) != null }) return name
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            for (type in RoomType.entries) {
                val suffix = matchesAuto(name, altra.nomi.roomDefaultName(type)) ?: continue
                return lingua.nomi.roomDefaultName(type) + suffix
            }
        }
        return name
    }

    /** "" se `name` è `base`, " 3" se è "`base` 3"; null se non è un nome automatico di `base`. */
    private fun matchesAuto(name: String, base: String): String? {
        if (name == base) return ""
        val rest = name.removePrefix("$base ").takeIf { it.length < name.length } ?: return null
        return if (rest.isNotEmpty() && rest.all { it.isDigit() }) " $rest" else null
    }

    private const val MAX_FLOORS = 30
}

/** Stessa casa con i nomi automatici di stanze e piani nella lingua dell'app. */
fun Building.withAutoNames(lingua: Lingua = T.lingua): Building {
    val out = copy(floors = floors.map { f ->
        f.copy(
            name = AutoNames.floor(f.name, lingua),
            plan = f.plan.copy(rooms = f.plan.rooms.map { r -> r.copy(name = AutoNames.room(r.name, lingua)) }),
        )
    })
    return if (out == this) this else out
}
