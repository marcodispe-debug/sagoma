package com.sagoma.planimetria.model

import kotlin.math.roundToInt

/**
 * Che cosa c'è nelle foto dei materiali ([MaterialPiecesData], trovato da tools/furniture/FindMaterialPieces.java): nella posa a
 * pezzi ([Laying]) ogni piastrella o doga posata mostra un pezzo vero della foto, senza le fughe della foto.
 */
object MaterialPieces {
    sealed interface Photo

    /** Nessun pezzo nella foto (marmo, cemento, moquette…): ogni pezzo posato ne prende un ritaglio. */
    data object Plain : Photo

    /** La foto è già una posa (spina di pesce, esagonette, mosaici…): non si posa a pezzi. */
    data object Patterned : Photo

    /**
     * Pezzi trovati nella foto: rettangoli in unità della foto (1 = tutta la foto; possono passare il bordo, la foto si
     * ripete). `joint` è la fuga della foto (stesse unità) e `groutArgb` il suo colore. In una griglia (`grid`) i pezzi sono
     * per righe, `columns` per riga.
     */
    class Pieces(val rects: List<Rect>, val joint: Double, val groutArgb: Long, val grid: Boolean, val columns: Int, val rows: Int) : Photo

    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) {
        val long: Double get() = maxOf(w, h)
        val short: Double get() = minOf(w, h)
    }

    private val photos: Map<String, Photo> by lazy { MaterialPiecesData.table.mapValues { parse(it.value) } }

    fun of(id: String?): Photo = id?.let { photos[it] } ?: Plain

    /** Il materiale si può posare a pezzi (la foto non è già una posa). */
    fun canLay(id: String?): Boolean = id != null && of(id) !is Patterned

    internal fun parse(s: String): Photo {
        if (s == "P") return Patterned
        if (!s.startsWith("S ")) return Plain
        val parts = s.split('|')
        val head = parts[0].split(' ')
        val px = 1.0 / 1024
        val rects = parts.drop(1).map { p ->
            val (x, y, w, h) = p.split(' ').map { it.toDouble() * px }
            Rect(x, y, w, h)
        }
        if (rects.isEmpty()) return Plain
        return Pieces(rects, head[1].toDouble() * px, 0xFF000000 or head[2].toLong(16), head[3] == "1", head[4].toInt(), head[5].toInt())
    }

    /**
     * Posa di partenza per un materiale: pezzi e fughe come nella foto quando ci sono, altrimenti le misure solite per quel
     * genere di materiale.
     */
    fun defaultLaying(item: MaterialCatalog.Item): Laying {
        val wood = item.category == "Parquet e legno"
        val photo = of(item.id)
        if (photo is Pieces) {
            val long = median(photo.rects.map { it.long }) * item.size
            val short = median(photo.rects.map { it.short }) * item.size
            val pattern = when {
                photo.grid -> LayingPattern.Dritta
                wood -> LayingPattern.ACorrere
                else -> LayingPattern.Sfalsata
            }
            // Nel legno la «fuga» della foto è lo smusso tra le doghe: nel 3D basta una riga sottile.
            val joint = if (wood) 0.1 else roundTenth((photo.joint * item.size).coerceIn(0.2, 1.0))
            return Laying(pattern, roundSide(short), roundSide(maxOf(long, short)), joint, photo.groutArgb)
        }
        fun darker(k: Double) = darken(item.argb, k)
        return when (item.category) {
            "Parquet e legno" -> Laying(LayingPattern.ACorrere, 20.0, 120.0, 0.1, darker(0.45))
            "Marmo e pietra" -> Laying(LayingPattern.Dritta, 60.0, 60.0, 0.2, darker(0.15))
            "Moquette" -> Laying(LayingPattern.Dritta, 50.0, 50.0, 0.1, darker(0.3))
            "Esterni" -> Laying(LayingPattern.Dritta, 40.0, 40.0, 0.8, 0xFF8E8A82)
            else -> Laying(LayingPattern.Dritta, 60.0, 60.0, 0.3, 0xFFB9B4AA)
        }
    }

    /**
     * Posa quando il materiale passa da `from` a `to`: con lo stesso genere di materiale (un parquet al posto di un altro)
     * resta com'era, altrimenti riparte da quella del nuovo; con una foto già posata si torna alla foto.
     */
    fun relay(layout: MaterialLayout, from: String?, to: String?): MaterialLayout {
        if (layout.laying == null) return layout
        val item = MaterialCatalog.item(to) ?: return layout
        if (!canLay(item.id)) return layout.copy(laying = null)
        if (MaterialCatalog.item(from)?.category == item.category) return layout
        return layout.copy(laying = defaultLaying(item))
    }

    /** Il colore `argb` più scuro di `k` (0 = uguale, 1 = nero). */
    fun darken(argb: Long, k: Double): Long {
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) * (1 - k)).roundToInt().toLong().coerceIn(0, 255) shl shift
        return 0xFF000000 or ch(16) or ch(8) or ch(0)
    }

    private fun median(v: List<Double>) = v.sorted()[v.size / 2]

    private fun roundTenth(v: Double) = (v * 10).roundToInt() / 10.0

    /** Lato di un pezzo arrotondato al centimetro (al mezzo centimetro sotto i 10 cm). */
    private fun roundSide(v: Double): Double {
        val r = if (v < 10) (v * 2).roundToInt() / 2.0 else v.roundToInt().toDouble()
        return r.coerceIn(Laying.MIN_SIDE, Laying.MAX_SIDE)
    }
}
