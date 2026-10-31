package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Scritta del disegno DXF, in centimetri e con la y della pianta. */
class DxfText(
    /** Punto di aggancio della scritta. */
    val at: Vec2,
    /** Altezza delle lettere maiuscole (cm). */
    val height: Double,
    /** Gradi in senso orario come sulla pianta (y in giù). */
    val rotation: Double,
    /** Righe, dall'alto in basso. */
    val lines: List<String>,
    /** Aggancio orizzontale: 0 a sinistra, 1 al centro, 2 a destra. */
    val align: Int = 0,
    /** Aggancio verticale: 0 sulla linea di base, 1 in basso, 2 a metà, 3 in alto. */
    val valign: Int = 0,
)

/**
 * Disegno letto da un file DXF (per esempio la pianta che il professionista rimanda indietro), da mettere sotto al disegno come
 * sfondo, già in scala: spezzate (archi, cerchi, ellissi e spline in tanti tratti) e scritte, in centimetri con la y della pianta.
 * [minX]…[maxY]: il riquadro del disegno vero (senza i pezzi sparsi lontanissimi che a volte restano nei file CAD).
 */
class DxfDrawing(
    /** Spezzate: x, y, x, y… in cm. */
    val paths: List<DoubleArray>,
    val texts: List<DxfText>,
    /** Centimetri per unità del file. */
    val cmPerUnit: Double,
    /** Il file non diceva l'unità: indovinata dalla grandezza del disegno. */
    val unitGuessed: Boolean,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
    /** Il disegno era troppo grande: una parte delle linee non è stata letta. */
    val truncated: Boolean = false,
    /** Superfici piene (i muri campiti con i SOLID e i retini pieni): per ognuna i contorni chiusi, buchi compresi. */
    val fills: List<List<DoubleArray>> = emptyList(),
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
}

/**
 * Lettore dei file DXF di testo (da R12 alle versioni nuove), come li salvano AutoCAD, LibreCAD, ArchiCAD, Revit e gli altri:
 * linee, polilinee (anche con gli archi), cerchi, archi, ellissi, spline, solidi, quote e blocchi (porte, arredi…) con la loro
 * posizione, scala e rotazione; scritte. Solo lo spazio modello, solo i livelli accesi. Il resto (retini, immagini, 3D) si salta.
 */
object DxfReader {
    sealed class Result {
        class Ok(val drawing: DxfDrawing) : Result()
        /** Un file DWG (il formato chiuso di AutoCAD): va salvato come DXF dal programma CAD. */
        data object Dwg : Result()
        /** DXF binario: va salvato come DXF di testo. */
        data object Binary : Result()
        /** DXF senza niente da disegnare (o con tutti i livelli spenti). */
        data object Empty : Result()
        /** Non è un DXF. */
        data object NotDxf : Result()
    }

    /** Punti al massimo (oltre, il disegno si legge solo in parte): abbastanza per una pianta con tutti gli arredi. */
    const val MAX_POINTS = 2_000_000
    /** Blocchi dentro blocchi: oltre questa profondità ci si ferma (anche contro i blocchi che contengono sé stessi). */
    private const val MAX_DEPTH = 12
    /** Gradi per ogni tratto degli archi. */
    private const val ARC_STEP = 5.0

    fun read(bytes: ByteArray): Result {
        if (bytes.size >= 6 && bytes[0] == 'A'.code.toByte() && bytes[1] == 'C'.code.toByte() && bytes[2] in '0'.code.toByte()..'9'.code.toByte()) return Result.Dwg
        if (bytes.size >= 18 && bytes.copyOfRange(0, 18).decodeToString() == "AutoCAD Binary DXF") return Result.Binary
        val pairs = Pairs(bytes)
        if (!pairs.looksLikeDxf()) return Result.NotDxf
        return Parser(pairs).run()
    }

    /**
     * Coppie codice/valore del file, una alla volta, lette direttamente dai byte (un DXF può essere di decine di MB: niente copie
     * del file intero). Le scritte sono in UTF-8 dalla versione 2007 (AC1021) in poi, prima in ANSI 1252 (paesi occidentali).
     */
    private class Pairs(private val b: ByteArray) {
        private var pos = 0
        private val utf8: Boolean
        var code = -1
            private set
        var value = ""
            private set

        init {
            // Segno UTF-8 all'inizio (lo mettono alcuni programmi).
            if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) {
                pos = 3
                utf8 = true
            } else {
                val head = CharArray(min(b.size, 4096)) { (b[it].toInt() and 0xFF).toChar() }.concatToString()
                val version = Regex("\\\$ACADVER\\s*\\r?\\n\\s*1\\s*\\r?\\n\\s*(AC\\d{4})").find(head)?.groupValues?.get(1)
                utf8 = version != null && version >= "AC1021"
            }
        }

        /** La riga letta per ultima: da `lineStart` a `lineEnd` (escluso), senza il ritorno a capo. */
        private var lineStart = 0
        private var lineEnd = 0

        private fun line(): Boolean {
            if (pos >= b.size) return false
            var end = pos
            while (end < b.size && b[end] != NL) end++
            lineStart = pos
            lineEnd = if (end > pos && b[end - 1] == CR) end - 1 else end
            pos = end + 1
            return true
        }

        /** Il numero della riga (il codice), o null se non è un numero. */
        private fun lineInt(): Int? {
            var i = lineStart
            var j = lineEnd
            while (i < j && b[i] in 0..32) i++
            while (j > i && b[j - 1] in 0..32) j--
            val neg = i < j && b[i] == '-'.code.toByte()
            if (neg) i++
            if (i == j || j - i > 9) return null
            var n = 0
            while (i < j) {
                val d = b[i] - '0'.code.toByte()
                if (d !in 0..9) return null
                n = n * 10 + d
                i++
            }
            return if (neg) -n else n
        }

        private fun lineText(): String =
            if (utf8) b.decodeToString(lineStart, lineEnd)
            else CharArray(lineEnd - lineStart) { DxfWriter.fromCp1252(b[lineStart + it].toInt() and 0xFF) }.concatToString()

        fun next(): Boolean {
            if (!line()) return false
            val c = lineInt() ?: return false
            if (!line()) return false
            code = c
            value = lineText()
            return true
        }

        /** Il file comincia come un DXF (0 SECTION, magari dopo un commento 999). */
        fun looksLikeDxf(): Boolean {
            val start = pos
            var ok = false
            for (k in 0 until 4) {
                if (!next()) break
                if (code == 999) continue
                ok = code == 0 && value.trim() == "SECTION"
                break
            }
            pos = start
            return ok
        }
    }

    /** Un'entità del file: tipo e coppie codice/valore. */
    private class Ent(val type: String, val codes: IntArray, val values: Array<String>) {
        fun str(code: Int): String? { for (i in codes.indices) if (codes[i] == code) return values[i]; return null }
        fun num(code: Int, default: Double = 0.0): Double = str(code)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
        fun int(code: Int, default: Int = 0): Int = str(code)?.trim()?.toIntOrNull() ?: num(code, default.toDouble()).toInt()
        val layer: String get() = str(8)?.trim() ?: "0"
        /** Il piano dell'entità è girato (estrusione verso il basso): nel disegno la x va specchiata. */
        val mirrored: Boolean get() = num(230, 1.0) < 0
    }

    /** Trasformazione del piano: x' = a·x + c·y + e, y' = b·x + d·y + f. */
    private class Tf(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
        fun x(x: Double, y: Double) = a * x + c * y + e
        fun y(x: Double, y: Double) = b * x + d * y + f
        /** Prima `o`, poi questa. */
        operator fun times(o: Tf) = Tf(
            a * o.a + c * o.b, b * o.a + d * o.b,
            a * o.c + c * o.d, b * o.c + d * o.d,
            a * o.e + c * o.f + e, b * o.e + d * o.f + f,
        )
        val scale: Double get() = sqrt(abs(a * d - b * c))

        companion object {
            val ID = Tf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
            val MIRROR = Tf(-1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
            fun move(x: Double, y: Double) = Tf(1.0, 0.0, 0.0, 1.0, x, y)
            fun rotate(deg: Double): Tf { val r = deg * PI / 180; return Tf(cos(r), sin(r), -sin(r), cos(r), 0.0, 0.0) }
            fun scale(sx: Double, sy: Double) = Tf(sx, 0.0, 0.0, sy, 0.0, 0.0)
        }
    }

    private class Block(val name: String, val baseX: Double, val baseY: Double, val entities: List<Ent>)

    /** Testo come esce dal file (unità del file, angoli antiorari): si converte alla fine. */
    private class RawText(val x: Double, val y: Double, val height: Double, val angle: Double, val lines: List<String>, val align: Int, val valign: Int)

    private class Parser(private val p: Pairs) {
        private var units = 0
        private val off = mutableSetOf<String>()
        private val blocks = HashMap<String, Block>()
        private val paths = mutableListOf<DoubleArray>()
        private val fills = mutableListOf<List<DoubleArray>>()
        private val texts = mutableListOf<RawText>()
        private var points = 0
        private var truncated = false
        /** Coppia già letta e non ancora usata (l'inizio dell'entità dopo). */
        private var pending = false

        private fun next(): Boolean {
            if (pending) { pending = false; return true }
            return p.next()
        }

        fun run(): Result {
            while (next()) {
                if (p.code != 0) continue
                when (p.value.trim()) {
                    "SECTION" -> {
                        if (!next()) break
                        when (if (p.code == 2) p.value.trim() else "") {
                            "HEADER" -> header()
                            "TABLES" -> tables()
                            "BLOCKS" -> blocks()
                            "ENTITIES" -> {
                                val list = section(paperSpace = false)
                                draw(list, Tf.ID, null, 0)
                            }
                        }
                    }
                    "EOF" -> break
                }
            }
            return finish()
        }

        private fun header() {
            var variable = ""
            while (next()) {
                if (p.code == 0) {
                    if (p.value.trim() == "ENDSEC") return
                    continue
                }
                if (p.code == 9) variable = p.value.trim()
                else if (variable == "\$INSUNITS" && p.code == 70) units = p.value.trim().toIntOrNull() ?: 0
            }
        }

        /** Livelli spenti (colore negativo) o congelati. */
        private fun tables() {
            var inLayer = false
            var name = ""
            var color = 7
            var flags = 0
            fun close() {
                if (inLayer && (color < 0 || flags and 1 != 0)) off += name
                inLayer = false
            }
            while (next()) {
                if (p.code == 0) {
                    close()
                    when (p.value.trim()) {
                        "ENDSEC" -> return
                        "LAYER" -> { inLayer = true; name = ""; color = 7; flags = 0 }
                    }
                    continue
                }
                if (!inLayer) continue
                when (p.code) {
                    2 -> name = p.value.trim()
                    62 -> color = p.value.trim().toIntOrNull() ?: 7
                    70 -> flags = p.value.trim().toIntOrNull() ?: 0
                }
            }
        }

        private fun blocks() {
            while (next()) {
                if (p.code != 0) continue
                when (p.value.trim()) {
                    "ENDSEC" -> return
                    "BLOCK" -> {
                        val head = entity("BLOCK")
                        val content = section(paperSpace = true, end = "ENDBLK")
                        val name = head.str(2)?.trim() ?: continue
                        // Riferimenti esterni (xref): il loro contenuto non è nel file.
                        if (head.int(70) and 4 != 0) continue
                        blocks[name.uppercase()] = Block(name, head.num(10), head.num(20), content)
                    }
                }
            }
        }

        /** Le coppie dell'entità che comincia ora (il "0 TIPO" appena letto), fino al prossimo codice 0. */
        private fun entity(type: String): Ent {
            val codes = ArrayList<Int>()
            val values = ArrayList<String>()
            while (next()) {
                if (p.code == 0) { pending = true; break }
                codes += p.code
                values += p.value
            }
            return Ent(type, codes.toIntArray(), values.toTypedArray())
        }

        /** Entità fino a ENDSEC (o a `end`); `paperSpace` = false salta quelle dello spazio carta. */
        private fun section(paperSpace: Boolean, end: String = "ENDSEC"): List<Ent> {
            val out = mutableListOf<Ent>()
            while (next()) {
                if (p.code != 0) continue
                val type = p.value.trim()
                if (type == end || type == "ENDSEC") {
                    if (type == "ENDSEC" && end != "ENDSEC") pending = true
                    // Le coppie di ENDBLK (livello, maniglia…) non servono.
                    if (type == end && end != "ENDSEC") entity(type)
                    break
                }
                val e = entity(type)
                if (!paperSpace && e.int(67) == 1) continue
                out += e
            }
            return out
        }

        private fun visible(e: Ent, inherited: String?): Boolean {
            if (e.int(60) == 1) return false
            val layer = e.layer.let { if (it == "0" && inherited != null) inherited else it }
            return layer !in off
        }

        /** Disegna le entità con la trasformazione `tf`; `inherited`: livello del blocco (per le entità sul livello 0). */
        private fun draw(list: List<Ent>, tf: Tf, inherited: String?, depth: Int) {
            var i = 0
            while (i < list.size) {
                val e = list[i]
                i++
                when (e.type) {
                    "POLYLINE" -> {
                        // I vertici seguono, fino a SEQEND.
                        val vertices = mutableListOf<Ent>()
                        while (i < list.size && list[i].type == "VERTEX") { vertices += list[i]; i++ }
                        if (i < list.size && list[i].type == "SEQEND") i++
                        if (visible(e, inherited)) polyline(e, vertices, tf)
                    }
                    "INSERT" -> {
                        val attribs = mutableListOf<Ent>()
                        if (e.int(66) == 1) {
                            while (i < list.size && list[i].type == "ATTRIB") { attribs += list[i]; i++ }
                            if (i < list.size && list[i].type == "SEQEND") i++
                        }
                        if (!visible(e, inherited)) continue
                        insert(e, tf, inherited, depth)
                        val layer = e.layer.let { if (it == "0" && inherited != null) inherited else it }
                        for (a in attribs) if (visible(a, layer) && a.int(70) and 1 == 0) text(a, tf)
                    }
                    else -> if (visible(e, inherited)) simple(e, tf, inherited, depth)
                }
                if (points >= MAX_POINTS) { truncated = true; return }
            }
        }

        private fun simple(e: Ent, tf0: Tf, inherited: String?, depth: Int) {
            val tf = if (e.mirrored && e.type in OCS) tf0 * Tf.MIRROR else tf0
            when (e.type) {
                "LINE" -> path(tf, doubleArrayOf(e.num(10), e.num(20), e.num(11), e.num(21)))
                "LWPOLYLINE" -> lwpolyline(e, tf)
                "CIRCLE" -> arc(tf, e.num(10), e.num(20), e.num(40), 0.0, 360.0)
                "ARC" -> arc(tf, e.num(10), e.num(20), e.num(40), e.num(50), e.num(51))
                "ELLIPSE" -> ellipse(e, tf0)
                "SPLINE" -> spline(e, tf0)
                "SOLID", "TRACE" -> {
                    // Il quarto punto prima del terzo; piene.
                    val pts = listOf(10, 11, 13, 12).map { e.num(it) to e.num(it + 10) }
                    fill(tf, listOf(DoubleArray(8) { k -> pts[k / 2].let { if (k % 2 == 0) it.first else it.second } }))
                }
                "3DFACE" -> {
                    val pts = listOf(10, 11, 12, 13).map { e.num(it) to e.num(it + 10) }
                    path(tf, DoubleArray(10) { k -> pts[k / 2 % 4].let { if (k % 2 == 0) it.first else it.second } })
                }
                "LEADER" -> {
                    val xs = e.all(10)
                    val ys = e.all(20)
                    val n = min(xs.size, ys.size)
                    if (n >= 2) path(tf, DoubleArray(2 * n) { k -> if (k % 2 == 0) xs[k / 2] else ys[k / 2] })
                }
                "HATCH" -> hatch(e, tf0)
                "TEXT" -> text(e, tf)
                "MTEXT" -> mtext(e, tf)
                "DIMENSION" -> {
                    // Il disegno della quota è in un blocco a parte (*D…), già al suo posto.
                    val b = e.str(2)?.trim()?.uppercase()?.let { blocks[it] } ?: return
                    if (depth < MAX_DEPTH) draw(b.entities, tf0, inherited ?: e.layer, depth + 1)
                }
            }
        }

        private fun Ent.all(code: Int): List<Double> = codes.indices.filter { codes[it] == code }.map { values[it].trim().toDoubleOrNull()?.takeIf { v -> v.isFinite() } ?: 0.0 }

        private fun insert(e: Ent, tf0: Tf, inherited: String?, depth: Int) {
            if (depth >= MAX_DEPTH) return
            val b = e.str(2)?.trim()?.uppercase()?.let { blocks[it] } ?: return
            val sx = e.num(41, 1.0)
            val sy = e.num(42, 1.0)
            if (sx == 0.0 || sy == 0.0) return
            val cols = e.int(70, 1).coerceIn(1, 100)
            val rows = e.int(71, 1).coerceIn(1, 100)
            val layer = e.layer.let { if (it == "0" && inherited != null) inherited else it }
            val base = if (e.mirrored) tf0 * Tf.MIRROR else tf0
            for (r in 0 until rows) for (c in 0 until cols) {
                // Griglia (MINSERT): le distanze sono nel verso del blocco girato.
                val grid = Tf.move(c * e.num(44), r * e.num(45))
                val tf = base * Tf.move(e.num(10), e.num(20)) * Tf.rotate(e.num(50)) * grid * Tf.scale(sx, sy) * Tf.move(-b.baseX, -b.baseY)
                draw(b.entities, tf, layer, depth + 1)
                if (points >= MAX_POINTS) return
            }
        }

        private fun moved(tf: Tf, raw: DoubleArray): DoubleArray {
            val out = DoubleArray(raw.size)
            for (k in 0 until raw.size / 2) {
                val x = raw[2 * k]
                val y = raw[2 * k + 1]
                out[2 * k] = tf.x(x, y)
                out[2 * k + 1] = tf.y(x, y)
            }
            points += raw.size / 2
            return out
        }

        private fun path(tf: Tf, raw: DoubleArray) {
            if (raw.size >= 4) paths += moved(tf, raw)
        }

        /** Superficie piena con i suoi contorni (il primo fuori, gli altri anche buchi). */
        private fun fill(tf: Tf, loops: List<DoubleArray>) {
            val ok = loops.filter { it.size >= 6 }
            if (ok.isNotEmpty()) fills += ok.map { moved(tf, it) }
        }

        /**
         * Retino pieno (i muri campiti dei CAD nuovi): i suoi contorni, buchi compresi (si riempie a pari e dispari). I retini a
         * righe o a disegni si saltano: le linee dei muri ci sono già.
         */
        private fun hatch(e: Ent, tf0: Tf) {
            if (e.int(70) != 1) return
            val tf = if (e.mirrored) tf0 * Tf.MIRROR else tf0
            val c = e.codes
            var i = 0
            fun d(k: Int): Double = if (k < 0) 0.0 else e.values[k].trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            fun n(k: Int): Int = if (k < 0) 0 else e.values[k].trim().toIntOrNull() ?: 0
            // Fino al prossimo `code` (non oltre un nuovo contorno, 92, o la fine dei contorni, 75): l'indice, o −1.
            fun seek(code: Int): Int {
                while (i < c.size) {
                    if (c[i] == code) return i++
                    if (c[i] == 92 || c[i] == 75) return -1
                    i++
                }
                return -1
            }
            while (i < c.size && c[i] != 91) i++
            if (i >= c.size) return
            val count = n(i++).coerceIn(0, 10_000)
            val loops = mutableListOf<DoubleArray>()
            for (loop in 0 until count) {
                while (i < c.size && c[i] != 92) i++
                if (i >= c.size) break
                val flags = n(i++)
                val out = mutableListOf<Double>()
                fun point(x: Double, y: Double) {
                    if (out.size >= 2 && abs(out[out.size - 2] - x) < 1e-9 && abs(out[out.size - 1] - y) < 1e-9) return
                    out += x; out += y
                }
                if (flags and 2 != 0) {
                    // Contorno a polilinea: 72 con i bulge, 73 chiusa, 93 vertici; per ogni vertice 10, 20 (e 42).
                    val bulges = n(seek(72)) != 0
                    val nv = n(seek(93)).coerceIn(0, 100_000)
                    val xs = mutableListOf<Double>()
                    val ys = mutableListOf<Double>()
                    val bs = mutableListOf<Double>()
                    for (k in 0 until nv) {
                        val ix = seek(10)
                        if (ix < 0) break
                        xs += d(ix); ys += d(seek(20))
                        bs += if (bulges && i < c.size && c[i] == 42) d(i++) else 0.0
                    }
                    if (xs.size >= 3) {
                        val pts = mutableListOf(xs[0], ys[0])
                        for (k in xs.indices) bulge(pts, xs[k], ys[k], xs[(k + 1) % xs.size], ys[(k + 1) % xs.size], bs[k])
                        out += pts
                    }
                } else {
                    // Contorno a lati: 93 lati; ognuno col suo tipo (72): 1 linea, 2 arco, 3 ellisse, 4 spline.
                    val edges = n(seek(93)).coerceIn(0, 100_000)
                    for (k in 0 until edges) {
                        val type = n(seek(72))
                        when (type) {
                            1 -> {
                                val x1 = d(seek(10)); val y1 = d(seek(20)); val x2 = d(seek(11)); val y2 = d(seek(21))
                                point(x1, y1); point(x2, y2)
                            }
                            2, 3 -> {
                                val cx = d(seek(10)); val cy = d(seek(20))
                                var mx = 0.0; var my = 0.0; var ratio = 1.0; var r = 0.0
                                if (type == 2) r = d(seek(40)) else { mx = d(seek(11)); my = d(seek(21)); ratio = d(seek(40)) }
                                val a1 = d(seek(50)); val a2 = d(seek(51))
                                val ccw = n(seek(73)) != 0
                                // In senso orario gli angoli sono scritti girati: si percorre all'indietro.
                                val s = if (ccw) a1 else -a1
                                var sweep = if (ccw) a2 - a1 else -(a2 - a1)
                                if (ccw) { while (sweep <= 0) sweep += 360.0 } else { while (sweep >= 0) sweep -= 360.0 }
                                if (abs(sweep) > 360) sweep = 360.0 * (if (sweep > 0) 1 else -1)
                                val steps = ceil(abs(sweep) / ARC_STEP).toInt().coerceIn(2, 72)
                                for (q in 0..steps) {
                                    val a = (s + sweep * q / steps) * PI / 180
                                    if (type == 2) point(cx + r * cos(a), cy + r * sin(a))
                                    else point(cx + mx * cos(a) - my * ratio * sin(a), cy + my * cos(a) + mx * ratio * sin(a))
                                }
                            }
                            4 -> {
                                val degree = n(seek(94))
                                val nk = n(seek(95)).coerceIn(0, 10_000)
                                val nc = n(seek(96)).coerceIn(0, 10_000)
                                val knots = (0 until nk).map { d(seek(40)) }
                                val cx = mutableListOf<Double>()
                                val cy = mutableListOf<Double>()
                                val w = mutableListOf<Double>()
                                for (q in 0 until nc) {
                                    cx += d(seek(10)); cy += d(seek(20))
                                    w += if (i < c.size && c[i] == 42) d(i++) else 1.0
                                }
                                if (nc >= 2 && degree in 1..10 && knots.size == nc + degree + 1 && knots[nc] > knots[degree]) {
                                    val steps = (nc * 8).coerceIn(16, 500)
                                    for (q in 0..steps) {
                                        val t = knots[degree] + (knots[nc] - knots[degree]) * q / steps
                                        point(deBoor(t, degree, knots, cx, cy, w, true), deBoor(t, degree, knots, cx, cy, w, false))
                                    }
                                } else for (q in 0 until nc) point(cx[q], cy[q])
                            }
                            else -> break
                        }
                    }
                }
                if (out.size >= 6) loops += out.toDoubleArray()
            }
            if (loops.isNotEmpty()) fill(tf, loops)
        }

        /** Arco con centro (cx, cy) da `a1` a `a2` gradi, in senso antiorario. */
        private fun arc(tf: Tf, cx: Double, cy: Double, r: Double, a1: Double, a2: Double) {
            if (!(r > 0)) return
            var sweep = a2 - a1
            while (sweep <= 0) sweep += 360.0
            if (sweep > 360.0) sweep = 360.0
            val n = ceil(sweep / ARC_STEP).toInt().coerceIn(2, 72)
            val pts = DoubleArray(2 * (n + 1))
            for (k in 0..n) {
                val a = (a1 + sweep * k / n) * PI / 180
                pts[2 * k] = cx + r * cos(a)
                pts[2 * k + 1] = cy + r * sin(a)
            }
            path(tf, pts)
        }

        /** Punti di un tratto con il "bulge" (tangente di un quarto dell'angolo dell'arco), senza il primo punto. */
        private fun bulge(out: MutableList<Double>, x1: Double, y1: Double, x2: Double, y2: Double, b: Double) {
            val d = hypot(x2 - x1, y2 - y1)
            if (b == 0.0 || d < 1e-12 || !b.isFinite()) { out += x2; out += y2; return }
            val theta = 4 * atan(b)
            val mx = (x1 + x2) / 2
            val my = (y1 + y2) / 2
            val h = d / 2 / tan(theta / 2)
            val cx = mx - (y2 - y1) / d * h
            val cy = my + (x2 - x1) / d * h
            val r = hypot(x1 - cx, y1 - cy)
            val a1 = atan2(y1 - cy, x1 - cx)
            val n = ceil(abs(theta) * 180 / PI / ARC_STEP).toInt().coerceIn(1, 72)
            for (k in 1 until n) {
                val a = a1 + theta * k / n
                out += cx + r * cos(a); out += cy + r * sin(a)
            }
            out += x2; out += y2
        }

        private fun lwpolyline(e: Ent, tf: Tf) {
            val xs = mutableListOf<Double>()
            val ys = mutableListOf<Double>()
            val bs = mutableListOf<Double>()
            for (k in e.codes.indices) {
                val v = e.values[k].trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
                when (e.codes[k]) {
                    10 -> { xs += v; ys += 0.0; bs += 0.0 }
                    20 -> if (ys.isNotEmpty()) ys[ys.size - 1] = v
                    42 -> if (bs.isNotEmpty()) bs[bs.size - 1] = v
                }
            }
            vertices(tf, xs, ys, bs, closed = e.int(70) and 1 != 0)
        }

        private fun polyline(e: Ent, vertices: List<Ent>, tf0: Tf) {
            val flags = e.int(70)
            if (flags and (16 or 64) != 0) return // maglie 3D
            val tf = if (e.mirrored) tf0 * Tf.MIRROR else tf0
            val vs = vertices.filter { it.int(70) and 16 == 0 } // i punti di controllo delle spline non sono sulla linea
            vertices(tf, vs.map { it.num(10) }, vs.map { it.num(20) }, vs.map { it.num(42) }, closed = flags and 1 != 0)
        }

        private fun vertices(tf: Tf, xs: List<Double>, ys: List<Double>, bs: List<Double>, closed: Boolean) {
            val n = xs.size
            if (n < 2) return
            val out = mutableListOf(xs[0], ys[0])
            for (k in 0 until n - 1) bulge(out, xs[k], ys[k], xs[k + 1], ys[k + 1], bs[k])
            if (closed) bulge(out, xs[n - 1], ys[n - 1], xs[0], ys[0], bs[n - 1])
            path(tf, out.toDoubleArray())
        }

        private fun ellipse(e: Ent, tf: Tf) {
            val cx = e.num(10)
            val cy = e.num(20)
            val mx = e.num(11)
            val my = e.num(21)
            val ratio = e.num(40, 1.0)
            if (hypot(mx, my) < 1e-12) return
            // Asse minore: l'asse maggiore girato di 90° attorno alla normale del piano dell'ellisse.
            val s = if (e.mirrored) -1.0 else 1.0
            val nx = -my * ratio * s
            val ny = mx * ratio * s
            val t1 = e.num(41, 0.0)
            var sweep = e.num(42, 2 * PI) - t1
            while (sweep <= 0) sweep += 2 * PI
            if (sweep > 2 * PI) sweep = 2 * PI
            val n = ceil(sweep * 180 / PI / ARC_STEP).toInt().coerceIn(2, 72)
            path(tf, DoubleArray(2 * (n + 1)) { k ->
                val t = t1 + sweep * (k / 2) / n
                if (k % 2 == 0) cx + mx * cos(t) + nx * sin(t) else cy + my * cos(t) + ny * sin(t)
            })
        }

        private fun spline(e: Ent, tf: Tf) {
            val degree = e.int(71, 3)
            val knots = e.all(40)
            val weights = e.all(41)
            val cx = e.all(10)
            val cy = e.all(20)
            val n = min(cx.size, cy.size)
            if (n >= 2 && degree in 1..10 && knots.size == n + degree + 1) {
                val w = if (weights.size == n) weights else List(n) { 1.0 }
                val lo = knots[degree]
                val hi = knots[n]
                if (hi > lo) {
                    val steps = (n * 8).coerceIn(16, 1000)
                    path(tf, DoubleArray(2 * (steps + 1)) { k ->
                        val t = lo + (hi - lo) * (k / 2) / steps
                        deBoor(t, degree, knots, cx, cy, w, k % 2 == 0)
                    })
                    return
                }
            }
            // Senza nodi validi: la spezzata dei punti per cui passa, o di quelli di controllo.
            val fx = e.all(11)
            val fy = e.all(21)
            val m = min(fx.size, fy.size)
            if (m >= 2) path(tf, through(fx.take(m), fy.take(m)))
            else if (n >= 2) path(tf, DoubleArray(2 * n) { k -> if (k % 2 == 0) cx[k / 2] else cy[k / 2] })
        }

        /** Curva morbida che passa per i punti (Catmull-Rom), come la disegnano i CAD dai punti di passaggio. */
        private fun through(xs: List<Double>, ys: List<Double>): DoubleArray {
            val n = min(xs.size, 10_000)
            if (n < 3) return DoubleArray(2 * n) { k -> if (k % 2 == 0) xs[k / 2] else ys[k / 2] }
            val steps = 8
            val out = DoubleArray(2 * ((n - 1) * steps + 1))
            fun at(list: List<Double>, i: Int): Double = when {
                i < 0 -> 2 * list[0] - list[1]
                i >= n -> 2 * list[n - 1] - list[n - 2]
                else -> list[i]
            }
            var k = 0
            for (i in 0 until n - 1) for (s in 0 until steps) {
                val t = s.toDouble() / steps
                for (list in listOf(xs, ys)) {
                    val p0 = at(list, i - 1); val p1 = at(list, i); val p2 = at(list, i + 1); val p3 = at(list, i + 2)
                    out[k++] = 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t)
                }
            }
            out[k++] = xs[n - 1]
            out[k] = ys[n - 1]
            return out
        }

        /** Punto della spline razionale al parametro `t` (algoritmo di de Boor): la x se `wantX`, altrimenti la y. */
        private fun deBoor(t: Double, p: Int, u: List<Double>, xs: List<Double>, ys: List<Double>, w: List<Double>, wantX: Boolean): Double {
            val n = xs.size
            var k = p
            while (k < n - 1 && t >= u[k + 1]) k++
            val dx = DoubleArray(p + 1) { j -> xs[j + k - p] * w[j + k - p] }
            val dy = DoubleArray(p + 1) { j -> ys[j + k - p] * w[j + k - p] }
            val dw = DoubleArray(p + 1) { j -> w[j + k - p] }
            for (r in 1..p) for (j in p downTo r) {
                val den = u[j + 1 + k - r] - u[j + k - p]
                val a = if (den == 0.0) 0.0 else (t - u[j + k - p]) / den
                dx[j] = (1 - a) * dx[j - 1] + a * dx[j]
                dy[j] = (1 - a) * dy[j - 1] + a * dy[j]
                dw[j] = (1 - a) * dw[j - 1] + a * dw[j]
            }
            val ww = if (dw[p] == 0.0) 1.0 else dw[p]
            return if (wantX) dx[p] / ww else dy[p] / ww
        }

        private fun text(e: Ent, tf0: Tf) {
            val s = e.str(1) ?: return
            val clean = specials(s)
            if (clean.isBlank()) return
            val tf = if (e.mirrored) tf0 * Tf.MIRROR else tf0
            val h = e.int(72)
            val v = e.int(73)
            // Allineato solo a sinistra sulla linea di base: conta il primo punto; altrimenti il secondo.
            val second = (h != 0 || v != 0) && h != 3 && h != 5 && e.str(11) != null
            val x = if (second) e.num(11) else e.num(10)
            val y = if (second) e.num(21) else e.num(20)
            val align = when (h) { 1, 4 -> 1; 2 -> 2; else -> 0 }
            val valign = when { h == 4 -> 2; second -> v.coerceIn(0, 3); else -> 0 }
            add(tf, x, y, e.num(40, 1.0), e.num(50), listOf(clean), align, valign)
        }

        private fun mtext(e: Ent, tf0: Tf) {
            val raw = buildString {
                for (k in e.codes.indices) if (e.codes[k] == 3) append(e.values[k])
                append(e.str(1) ?: "")
            }
            val lines = mtextLines(raw)
            if (lines.all { it.isBlank() }) return
            // MTEXT è già nelle coordinate del disegno: niente da specchiare.
            val tf = tf0
            val angle = if (e.str(11) != null) atan2(e.num(21), e.num(11)) * 180 / PI else e.num(50)
            val attach = e.int(71, 1).coerceIn(1, 9)
            val align = (attach - 1) % 3
            val valign = when ((attach - 1) / 3) { 0 -> 3; 1 -> 2; else -> 1 }
            add(tf, e.num(10), e.num(20), e.num(40, 1.0), angle, lines, align, valign)
        }

        private fun add(tf: Tf, x: Double, y: Double, height: Double, angle: Double, lines: List<String>, align: Int, valign: Int) {
            if (!(height > 0)) return
            val a = angle * PI / 180
            val dx = tf.x(cos(a), sin(a)) - tf.x(0.0, 0.0)
            val dy = tf.y(cos(a), sin(a)) - tf.y(0.0, 0.0)
            texts += RawText(tf.x(x, y), tf.y(x, y), height * tf.scale, atan2(dy, dx) * 180 / PI, lines, align, valign)
        }

        private fun finish(): Result {
            if (paths.isEmpty() && fills.isEmpty() && texts.isEmpty()) return Result.Empty
            // Riquadro del disegno vero: senza i pezzi sparsi lontani (lo 0,5% più fuori di ogni lato, se stanno molto più in là).
            // Su un campione dei punti, per non occupare troppa memoria.
            val stride = max(1, (points + texts.size) / SAMPLE)
            val xs = DoubleArray((points + texts.size) / stride + 2)
            val ys = DoubleArray(xs.size)
            var k = 0
            var seen = 0
            for (pth in paths + fills.flatten()) for (j in 0 until pth.size / 2) {
                if (seen++ % stride == 0 && k < xs.size) { xs[k] = pth[2 * j]; ys[k] = pth[2 * j + 1]; k++ }
            }
            for (t in texts) if (seen++ % stride == 0 && k < xs.size) { xs[k] = t.x; ys[k] = t.y; k++ }
            val (x0, x1) = robust(xs.copyOf(k))
            val (y0, y1) = robust(ys.copyOf(k))
            val size = max(x1 - x0, y1 - y0)
            val factor = UNITS[units] ?: when {
                size <= 0.0 -> 1.0
                size <= 200 -> 100.0 // metri: una casa fino a 200 m
                size <= 5000 -> 1.0 // centimetri: fino a 50 m
                else -> 0.1 // millimetri
            }
            val guessed = UNITS[units] == null
            // Nella pianta la y cresce verso il basso (le spezzate si cambiano sul posto: possono essere tante).
            for (pth in paths + fills.flatten()) for (j in pth.indices) pth[j] = if (j % 2 == 0) pth[j] * factor else -pth[j] * factor
            val outTexts = texts.map { t ->
                DxfText(Vec2(t.x * factor, -t.y * factor), t.height * factor, -t.angle, t.lines, t.align, t.valign)
            }
            return Result.Ok(
                DxfDrawing(paths, outTexts, factor, guessed, x0 * factor, -y1 * factor, x1 * factor, -y0 * factor, truncated, fills),
            )
        }
    }

    /** Entità scritte nel loro piano (OCS): con l'estrusione verso il basso la x va specchiata. */
    private val OCS = setOf("LWPOLYLINE", "CIRCLE", "ARC", "SOLID", "TRACE")

    private const val NL: Byte = 10
    private const val CR: Byte = 13

    /** Punti usati per trovare il riquadro del disegno. */
    private const val SAMPLE = 200_000

    /** Centimetri per unità, dal codice \$INSUNITS. */
    private val UNITS = mapOf(1 to 2.54, 2 to 30.48, 4 to 0.1, 5 to 1.0, 6 to 100.0, 10 to 91.44, 14 to 10.0)

    /** Minimo e massimo senza i pochi punti molto lontani dagli altri. */
    private fun robust(v: DoubleArray): Pair<Double, Double> {
        if (v.isEmpty()) return 0.0 to 0.0
        v.sort()
        val lo = v[(v.size * 0.005).toInt()]
        val hi = v[min(v.size - 1, (v.size * 0.995).toInt())]
        val span = max(hi - lo, 1e-9)
        val min = if (lo - v.first() > span) lo else v.first()
        val max = if (v.last() - hi > span) hi else v.last()
        return min to max
    }

    /** Codici speciali dei testi DXF: %%d gradi, %%c diametro, %%p più o meno, %%u e %%o (sottolineato) via, \U+XXXX. */
    internal fun specials(s: String): String {
        var out = s.replace("%%d", "°", ignoreCase = true).replace("%%c", "Ø", ignoreCase = true).replace("%%p", "±", ignoreCase = true)
            .replace("%%u", "", ignoreCase = true).replace("%%o", "", ignoreCase = true).replace("%%%", "%")
        out = Regex("%%(\\d{3})").replace(out) { it.groupValues[1].toInt().toChar().toString() }
        return unicode(out)
    }

    private fun unicode(s: String): String = Regex("\\\\U\\+([0-9A-Fa-f]{4})").replace(s) { it.groupValues[1].toInt(16).toChar().toString() }

    /** Righe di un MTEXT senza i codici di formato (\P a capo, {\f…; …}, \H…;, \S…^…; e gli altri). */
    internal fun mtextLines(raw: String): List<String> {
        val s = unicode(raw)
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '\\' && i + 1 < s.length -> {
                    val c = s[i + 1]
                    when (c) {
                        'P' -> { lines += cur.toString(); cur.clear(); i += 2 }
                        '~' -> { cur.append(' '); i += 2 }
                        '\\', '{', '}' -> { cur.append(c); i += 2 }
                        'L', 'l', 'O', 'o', 'K', 'k', 'N' -> i += 2
                        'S' -> {
                            // Frazione o apici: "1^2" → "1/2".
                            val end = s.indexOf(';', i + 2).let { if (it < 0) s.length else it }
                            cur.append(s.substring(i + 2, end).replace('^', '/').replace('#', '/'))
                            i = end + 1
                        }
                        else -> {
                            // \f…; \H…; \W…; \Q…; \T…; \C…; \A…; \p…; e simili: fino al punto e virgola.
                            val end = s.indexOf(';', i + 2)
                            i = if (end < 0) s.length else end + 1
                        }
                    }
                }
                ch == '{' || ch == '}' -> i++
                else -> { cur.append(ch); i++ }
            }
        }
        lines += cur.toString()
        return lines.map { specials(it).trimEnd() }
    }
}
