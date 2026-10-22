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
    /** Porte, finestre, porte-finestre e passaggi visti sulla piantina. */
    val openings: List<ReadOpening> = emptyList(),
    /** Scale (per ora solo dritte). */
    val stairs: List<ReadStair> = emptyList(),
    /** Spessore dei muri esterni sulla piantina, in pixel (null = non stimato: si usa uno spessore tipico). */
    @SerialName("exterior_wall_px") val exteriorWallPx: Double? = null,
    /** Unità delle misure scritte sulla piantina: "m" (5.03) o "cm" (503); il progetto le mostra allo stesso modo. */
    val units: String = "cm",
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
        /** Zone non calpestabili (fascia "non accessibile" sotto il tetto basso, cavedi): poligoni in pixel. */
        val excluded: List<List<List<Double>>> = emptyList(),
    )

    /**
     * Un'apertura: [kind] uno tra `door`, `double_door`, `window`, `double_window`, `balcony` (porta-finestra), `double_balcony`,
     * `passage`; [ends] i due estremi in pixel (gli stipiti), da qualunque parte dello spessore del muro. Le misure sono quelle
     * scritte accanto (larghezza, altezza, davanzale), in cm; se mancano la larghezza viene dai pixel e il resto è quello tipico.
     */
    /**
     * Scala dritta: [from] il centro del bordo del primo gradino in basso, [to] il centro dell'arrivo in alto (la freccia della
     * piantina va da [from] a [to]), in pixel; [widthCm] la larghezza scritta, altrimenti [widthPx] misurata; [treads] le pedate
     * contate sul disegno.
     */
    @Serializable
    data class ReadStair(
        val from: List<Double>,
        val to: List<Double>,
        @SerialName("width_cm") val widthCm: Double? = null,
        @SerialName("width_px") val widthPx: Double? = null,
        val treads: Int? = null,
    )

    @Serializable
    data class ReadOpening(
        val kind: String,
        val ends: List<List<Double>>,
        @SerialName("width_cm") val widthCm: Double? = null,
        @SerialName("height_cm") val heightCm: Double? = null,
        @SerialName("sill_cm") val sillCm: Double? = null,
        /** Finestre e balconi: sguincio per lato in cm (lati del vano inclinati verso l'interno). */
        @SerialName("splay_cm") val splayCm: Double? = null,
        /** Finestre e balconi: infisso "outside" (verso l'esterno), "inside" o "center". */
        val frame: String? = null,
        /** Porte: è l'ingresso di casa. */
        val entrance: Boolean = false,
    )

    companion object {
        const val VERSION = 1

        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun decode(text: String): PlanReading = json.decodeFromString(serializer(), text.removePrefix("﻿"))
        fun encode(r: PlanReading): String = json.encodeToString(serializer(), r)
    }
}
