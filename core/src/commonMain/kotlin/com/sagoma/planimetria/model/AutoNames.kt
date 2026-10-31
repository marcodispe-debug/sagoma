package com.sagoma.planimetria.model

import com.sagoma.planimetria.testi.Lingua
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.T

/**
 * Nomi di stanze e piani portati nella lingua dell'app: cambiando lingua i nomi automatici ("Cucina 2", "Piano terra") e quelli
 * comuni, anche scritti sulle piantine o a mano ("Camera doppia", "Disimpegno", "Seminterrato"), diventano "Kitchen 2", "Ground
 * floor", "Double bedroom"…; così anche le zone non calpestabili proposte e, solo a schermo, i nomi dei progetti dati dall'app.
 * I nomi che l'app non conosce ("Cucina di nonna") restano come sono.
 */
object AutoNames {
    /**
     * Nome del piano nella lingua `lingua`, se `name` è un nome di piano conosciuto di un'altra lingua: automatico ("Piano terra",
     * "Piano 7"), comune ("Seminterrato", [com.sagoma.planimetria.testi.TestiPiani.pianiComuni]) o anche nome di stanza ("Mansarda").
     */
    fun floor(name: String, lingua: Lingua = T.lingua): String {
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            for (i in -MAX_FLOORS..MAX_FLOORS) if (name == Building.floorName(i, altra)) return Building.floorName(i, lingua)
        }
        val key = normalize(name)
        if (lingua.piani.pianiComuni.any { normalize(it) == key }) return name
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            val i = altra.piani.pianiComuni.indexOfFirst { normalize(it) == key }
            if (i >= 0) return lingua.piani.pianiComuni[i]
        }
        return room(name, lingua)
    }

    /**
     * Nome della stanza nella lingua `lingua`, se `name` è un nome di stanza conosciuto di un'altra lingua: automatico ("Cucina",
     * "Cucina 2"), comune ("Camera doppia", "Disimpegno 2", [com.sagoma.planimetria.testi.Nomi.stanzeComuni]) o un suo sinonimo
     * ("Poggiolo", "Disimp."). Anche composto: "Lavanderia - stireria" → "Buanderie - repassage". Gli altri nomi restano come sono.
     */
    fun room(name: String, lingua: Lingua = T.lingua): String {
        roomPart(name, lingua)?.let { return it }
        // Nome composto: si traduce solo se si conoscono tutte le parti, per non mescolare due lingue.
        val separators = Regex("\\s*[-/+]\\s*").findAll(name).toList()
        if (separators.isEmpty()) return name
        val parts = name.split(Regex("\\s*[-/+]\\s*"))
        val translated = parts.mapIndexed { i, part -> roomPart(part, lingua, inner = i > 0) ?: return name }
        return buildString {
            for ((i, part) in translated.withIndex()) {
                append(part)
                if (i < separators.size) append(separators[i].value)
            }
        }
    }

    /**
     * Traduzione di un nome di stanza intero (con l'eventuale numero), lui stesso se è già della lingua giusta; null se sconosciuto.
     * `inner`: parte di un nome composto dopo la prima ("stireria" in "Lavanderia - stireria").
     */
    private fun roomPart(name: String, lingua: Lingua, inner: Boolean = false): String? {
        val trimmed = name.trim()
        val number = Regex(" (\\d+)$").find(trimmed)
        val base = number?.let { trimmed.substring(0, it.range.first) } ?: trimmed
        val suffix = number?.value ?: ""
        val key = normalize(base)
        if (key.isEmpty()) return null
        // Già un nome della lingua giusta ("Studio" è anche inglese… per chi lo scrive): non si tocca.
        if (key in known(lingua)) return name
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            val target = translate(key, altra, lingua) ?: continue
            // "stireria" in un nome composto resta minuscola anche tradotta. Il tedesco ha sempre la maiuscola: tradotto in un'altra
            // lingua, dopo la prima parte torna minuscolo ("Waschküche - Bügelzimmer" → "Lavanderia - stireria").
            val minuscolo = base.first().isLowerCase() || (inner && altra.minuscola(base) == base)
            val cased = if (minuscolo && lingua.minuscola(target) != target) lowerFirst(target) else target
            return cased + suffix
        }
        return null
    }

    /** Prima lettera minuscola, se non è una sigla ("WC" resta così). */
    private fun lowerFirst(s: String): String = if (s.length > 1 && s[1].isUpperCase()) s else s.replaceFirstChar { it.lowercaseChar() }

    /** Nome normalizzato `key` della lingua `da` portato nella lingua `a`; null se in `da` non è un nome conosciuto. */
    private fun translate(key: String, da: Lingua, a: Lingua): String? {
        RoomType.entries.firstOrNull { normalize(da.nomi.roomDefaultName(it)) == key }?.let { return a.nomi.roomDefaultName(it) }
        val i = da.nomi.stanzeComuni.indexOfFirst { normalize(it) == key }
        if (i >= 0) return a.nomi.stanzeComuni.getOrNull(i)
        val synonym = da.nomi.sinonimiStanze.entries.firstOrNull { normalize(it.key) == key } ?: return null
        return translate(normalize(synonym.value), da, a)
    }

    /** Tutti i nomi di stanza conosciuti di una lingua, normalizzati. */
    private fun known(l: Lingua): Set<String> =
        (RoomType.entries.map { l.nomi.roomDefaultName(it) } + l.nomi.stanzeComuni + l.nomi.sinonimiStanze.keys).map(::normalize).toSet()

    /** Per confrontare i nomi: minuscole, senza punti e spazi doppi ("W.C." = "wc", "DIs" = "dis."). */
    internal fun normalize(name: String): String = name.lowercase().replace(".", "").trim().replace(Regex("\\s+"), " ")

    /** Nome di una zona non calpestabile scelto tra quelli proposti ("Cavedio", "Pilastro"…), nella lingua `lingua`. */
    fun zone(name: String, lingua: Lingua = T.lingua): String {
        fun presets(l: Lingua) = with(l.muroPerMuro) { listOf(nonAccessibile, cavedio, pilastro, cannaFumaria) }
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            val i = presets(altra).indexOf(name)
            if (i >= 0) return presets(lingua)[i]
        }
        return name
    }

    /** Nome di un progetto dato dall'app ("Il mio progetto", "Progetto 3", "Progetto importato"), da mostrare nella lingua `lingua`. */
    fun project(name: String, lingua: Lingua = T.lingua): String {
        for (altra in Lingue.tutte) {
            if (altra === lingua) continue
            if (name == altra.progetti.progettoImportato) return lingua.progetti.progettoImportato
            if (name == altra.progetti.mioProgetto) return lingua.progetti.mioProgetto
            val n = name.removePrefix(altra.progetti.progetto("")).takeIf { it.length < name.length }
            if (n != null && n.isNotEmpty() && n.all { it.isDigit() }) return lingua.progetti.progetto(n)
        }
        return name
    }

    private const val MAX_FLOORS = 30
}

/** Stessa casa con i nomi automatici di stanze e piani nella lingua dell'app. */
fun Building.withAutoNames(lingua: Lingua = T.lingua): Building {
    val out = copy(floors = floors.map { f ->
        f.copy(
            name = AutoNames.floor(f.name, lingua),
            plan = f.plan.copy(rooms = f.plan.rooms.map { r ->
                r.copy(
                    name = AutoNames.room(r.name, lingua),
                    excludedZones = r.excludedZones.map { z -> z.copy(name = AutoNames.zone(z.name, lingua)) },
                )
            }),
        )
    })
    return if (out == this) this else out
}
