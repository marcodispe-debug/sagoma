package com.sagoma.planimetria.reading

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lettura di una piantina (foto, scansione, PDF): cosa c'è scritto e dove stanno le stanze, in pixel dell'immagine (origine in
 * alto a sinistra, y verso il basso). La scrive chi legge la piantina (un'AI, o a mano per le prove) e la trasforma in stanze
 * [PlanReadingImport]. Le misure sono quelle stampate o scritte sulla piantina, mai stimate: dove non c'è niente il campo manca.
 */
@Serializable
data class PlanReading(
    val version: Int = VERSION,
    val image: ImageSize,
    val rooms: List<ReadRoom>,
    /** Spessore dei muri esterni sulla piantina, in pixel (null = non stimato: si usa uno spessore tipico). */
    @SerialName("exterior_wall_px") val exteriorWallPx: Double? = null,
    /** Cosa ha notato chi ha letto (misure dubbie, parti illeggibili): solo da mostrare. */
    val notes: String = "",
) {
    @Serializable
    data class ImageSize(val width: Int, val height: Int)

    /**
     * Una stanza: [corners] sul filo interno dei muri, in ordine lungo il contorno; [sidesM] le misure dei lati scritte sulla
     * piantina (di solito due, larghezza e lunghezza), [areaM2] i metri quadri scritti.
     */
    @Serializable
    data class ReadRoom(
        val name: String,
        val corners: List<List<Double>>,
        @SerialName("sides_m") val sidesM: List<Double> = emptyList(),
        @SerialName("area_m2") val areaM2: Double? = null,
    )

    companion object {
        const val VERSION = 1

        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun decode(text: String): PlanReading = json.decodeFromString(serializer(), text.removePrefix("﻿"))
        fun encode(r: PlanReading): String = json.encodeToString(serializer(), r)
    }
}
