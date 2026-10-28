package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * File DXF in testo (versione R12, AC1009: la più semplice, la leggono AutoCAD, LibreCAD, QCAD, DraftSight, BricsCAD e gli altri)
 * con le misure in centimetri. Si passano i punti della pianta: qui la y si capovolge, perché sulla pianta cresce verso il basso
 * e nel DXF verso l'alto (altrimenti il disegno uscirebbe specchiato).
 */
class DxfWriter {
    /** Un livello: nome, colore (indice AutoCAD 1-255), tipo di linea; `off`: spento all'apertura (nel CAD si riaccende). */
    class Layer(val name: String, val color: Int, val dashed: Boolean = false, val off: Boolean = false)

    private val layers = LinkedHashMap<String, Layer>()
    private val entities = StringBuilder()
    private var minX = Double.MAX_VALUE
    private var minY = Double.MAX_VALUE
    private var maxX = -Double.MAX_VALUE
    private var maxY = -Double.MAX_VALUE

    fun layer(l: Layer) { layers[l.name] = l }

    fun line(layer: Layer, a: Vec2, b: Vec2) {
        if (a.distanceTo(b) < 1e-3) return
        entity("LINE", layer)
        point(10, a)
        point(11, b)
    }

    fun polyline(layer: Layer, pts: List<Vec2>, closed: Boolean) {
        if (pts.size < 2) return
        entity("POLYLINE", layer)
        code(66, "1")
        code(10, "0.0"); code(20, "0.0"); code(30, "0.0")
        code(70, if (closed) "1" else "0")
        for (p in pts) {
            code(0, "VERTEX")
            code(8, layer.name)
            point(10, p)
        }
        code(0, "SEQEND")
        code(8, layer.name)
    }

    fun circle(layer: Layer, center: Vec2, radius: Double) {
        if (radius < 1e-3) return
        entity("CIRCLE", layer)
        point(10, center)
        code(40, num(radius))
        extend(center - Vec2(radius, radius)); extend(center + Vec2(radius, radius))
    }

    /** Arco con centro `center` da `from` a `to` passando dalla parte di `through` (punti della pianta). */
    fun arc(layer: Layer, center: Vec2, from: Vec2, to: Vec2, through: Vec2) {
        val r = center.distanceTo(from)
        if (r < 1e-3) return
        // Angoli nel DXF (y capovolta), in senso antiorario come vuole il formato.
        fun angle(p: Vec2) = norm(atan2(-(p.y - center.y), p.x - center.x) * 180 / PI)
        var a1 = angle(from)
        var a2 = angle(to)
        val am = angle(through)
        if (norm(am - a1) > norm(a2 - a1)) { val t = a1; a1 = a2; a2 = t }
        entity("ARC", layer)
        point(10, center)
        code(40, num(r))
        code(50, num(a1))
        code(51, num(a2))
        extend(from); extend(to); extend(through)
    }

    /**
     * Scritta alta `height` cm, centrata in `at`, girata di `rotation` gradi come sulla pianta (in senso orario sullo schermo,
     * dove la y cresce verso il basso; nel DXF diventa antiorario).
     */
    fun text(layer: Layer, at: Vec2, height: Double, text: String, rotation: Double = 0.0) {
        if (text.isBlank() || !(height > 0)) return
        entity("TEXT", layer)
        point(10, at)
        code(40, num(height))
        code(1, encode(text))
        if (rotation != 0.0) code(50, num(norm(-rotation)))
        code(72, "1") // centrata
        point(11, at)
        code(73, "2") // a metà altezza
        val w = width(height, text) / 2 + height / 2 // anche girata
        extend(at - Vec2(w, w)); extend(at + Vec2(w, w))
    }

    /** Superficie piena di 3 o 4 punti in giro (un pezzo di muro): nel DXF il quarto punto va scritto prima del terzo. */
    fun solid(layer: Layer, pts: List<Vec2>) {
        if (pts.size !in 3..4) return
        entity("SOLID", layer)
        point(10, pts[0])
        point(11, pts[1])
        point(12, pts.last())
        point(13, pts[2])
    }

    fun build(): String = buildString {
        fun c(code: Int, value: String) { append(code).append('\n').append(value).append('\n') }
        val empty = minX > maxX
        c(0, "SECTION"); c(2, "HEADER")
        c(9, "\$ACADVER"); c(1, "AC1009")
        c(9, "\$DWGCODEPAGE"); c(3, "ANSI_1252")
        c(9, "\$INSBASE"); c(10, "0.0"); c(20, "0.0"); c(30, "0.0")
        c(9, "\$EXTMIN"); c(10, num(if (empty) 0.0 else minX)); c(20, num(if (empty) 0.0 else minY)); c(30, "0.0")
        c(9, "\$EXTMAX"); c(10, num(if (empty) 0.0 else maxX)); c(20, num(if (empty) 0.0 else maxY)); c(30, "0.0")
        // Centimetri (lo leggono i programmi recenti; per gli altri l'unità è scritta nel nome del file e nelle istruzioni).
        c(9, "\$INSUNITS"); c(70, "5")
        c(9, "\$MEASUREMENT"); c(70, "1")
        c(9, "\$LTSCALE"); c(40, "1.0")
        c(0, "ENDSEC")
        c(0, "SECTION"); c(2, "TABLES")
        c(0, "TABLE"); c(2, "LTYPE"); c(70, "2")
        c(0, "LTYPE"); c(2, "CONTINUOUS"); c(70, "0"); c(3, "Solid line"); c(72, "65"); c(73, "0"); c(40, "0.0")
        // Tratteggio da 15 cm (10 pieni e 5 vuoti), che in pianta a 1:100 è di 1,5 mm.
        c(0, "LTYPE"); c(2, "DASHED"); c(70, "0"); c(3, "__ __ __"); c(72, "65"); c(73, "2"); c(40, "15.0"); c(49, "10.0"); c(49, "-5.0")
        c(0, "ENDTAB")
        c(0, "TABLE"); c(2, "LAYER"); c(70, (layers.size + 1).toString())
        c(0, "LAYER"); c(2, "0"); c(70, "0"); c(62, "7"); c(6, "CONTINUOUS")
        for (l in layers.values) {
            // Colore negativo: livello spento.
            c(0, "LAYER"); c(2, l.name); c(70, "0"); c(62, (if (l.off) -l.color else l.color).toString()); c(6, if (l.dashed) "DASHED" else "CONTINUOUS")
        }
        c(0, "ENDTAB")
        c(0, "TABLE"); c(2, "STYLE"); c(70, "1")
        c(0, "STYLE"); c(2, "STANDARD"); c(70, "0"); c(40, "0.0"); c(41, "1.0"); c(50, "0.0"); c(71, "0"); c(42, "2.5"); c(3, "txt"); c(4, "")
        c(0, "ENDTAB")
        c(0, "ENDSEC")
        c(0, "SECTION"); c(2, "BLOCKS"); c(0, "ENDSEC")
        c(0, "SECTION"); c(2, "ENTITIES")
        append(entities)
        c(0, "ENDSEC")
        c(0, "EOF")
    }

    private fun entity(type: String, layer: Layer) {
        layer(layer)
        code(0, type)
        code(8, layer.name)
    }

    private fun code(code: Int, value: String) {
        entities.append(code).append('\n').append(value).append('\n')
    }

    /** Punto della pianta nei codici `base`, `base + 10`, `base + 20` (x, y capovolta, z = 0). */
    private fun point(base: Int, p: Vec2) {
        code(base, num(p.x))
        code(base + 10, num(-p.y))
        code(base + 20, "0.0")
        extend(p)
    }

    private fun extend(p: Vec2) {
        minX = min(minX, p.x); maxX = max(maxX, p.x)
        minY = min(minY, -p.y); maxY = max(maxY, -p.y)
    }

    companion object {
        /** Larghezza circa di una scritta alta `height` (lettere di larghezza media, un po' abbondante: i caratteri dei CAD variano). */
        fun width(height: Double, text: String) = height * 0.7 * text.length

        /** Numero con il punto e fino a 4 decimali, mai in notazione esponenziale. */
        fun num(v: Double): String {
            if (!v.isFinite()) return "0.0"
            val scaled = floor(abs(v) * 10_000 + 0.5).toLong()
            val sign = if (v < 0 && scaled != 0L) "-" else ""
            val frac = (scaled % 10_000).toString().padStart(4, '0').trimEnd('0').ifEmpty { "0" }
            return "$sign${scaled / 10_000}.$frac"
        }

        /**
         * Testo per il file: le lettere della codifica del file (ANSI 1252: à, è, ², € …) restano come sono, le altre diventano
         * \U+XXXX, che i programmi CAD capiscono comunque.
         */
        fun encode(s: String): String = buildString {
            for (ch in s) {
                when {
                    ch == '\n' || ch == '\r' -> append(' ')
                    ch.code < 32 || ch.code == 127 || ch.isLowSurrogate() -> Unit
                    ch.isHighSurrogate() -> append('?') // fuori dal piano base (emoji): \U+ ha solo 4 cifre
                    cp1252(ch) != null -> append(ch)
                    else -> append("\\U+").append(ch.code.toString(16).uppercase().padStart(4, '0'))
                }
            }
        }

        /** Il file in byte, nella codifica scritta nell'intestazione (ANSI 1252, quella dei DXF R12 nei paesi occidentali). */
        fun bytes(dxf: String): ByteArray = ByteArray(dxf.length) { (cp1252(dxf[it]) ?: '?'.code).toByte() }

        /** Le lettere in più della codifica ANSI 1252 rispetto al Latin-1 (da 0x80 a 0x9F). */
        private const val EXTRA = "€\u0000‚ƒ„…†‡ˆ‰Š‹Œ\u0000Ž\u0000\u0000‘’“”•–—˜™š›œ\u0000žŸ"

        /** Lettera del byte `b` (0–255) nella codifica ANSI 1252 (i pochi byte senza lettera restano come nel Latin-1). */
        fun fromCp1252(b: Int): Char = if (b in 0x80..0x9F) EXTRA[b - 0x80].takeIf { it != '\u0000' } ?: b.toChar() else b.toChar()

        /** Byte della lettera nella codifica ANSI 1252, o null se non c'è. */
        private fun cp1252(ch: Char): Int? = when {
            ch.code < 128 -> ch.code
            ch.code in 0xA0..0xFF -> ch.code
            else -> EXTRA.indexOf(ch).takeIf { it >= 0 && ch != '\u0000' }?.let { 0x80 + it }
        }

        private fun norm(deg: Double): Double {
            var d = deg % 360.0
            if (d < 0) d += 360.0
            return d
        }
    }
}
