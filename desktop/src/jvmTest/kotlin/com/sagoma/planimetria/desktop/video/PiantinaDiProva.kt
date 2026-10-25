package com.sagoma.planimetria.desktop.video

import com.sagoma.planimetria.reading.PlanReader
import com.sagoma.planimetria.reading.PlanReaderResult
import com.sagoma.planimetria.reading.PlanReading
import kotlinx.coroutines.delay
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Surface
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Piantina inventata per il tutorial (nessuna casa vera): stanze con i muri, una terrazza e un balcone, senza porte né
 * finestre. Ogni stanza si disegna con un modo diverso di sagoma e ha scritte le misure che servono per quel modo. Un pixel
 * dell'immagine è un centimetro; le coordinate sono sul filo interno dei muri (interni 10 cm, esterni 30 cm).
 *
 * - Camera, quadrata 350 × 350: «Quadrato».
 * - Soggiorno, rettangolare 520 × 410: «Rettangolo».
 * - Cucina a L, 450 × 300 con 200 × 180 che sporge in alto a destra: «A L».
 * - Bagno storto, lati AB 240, BC 229, CD 281, DA 205 e diagonale AC 359: «Da rilievo».
 * - Studio a sei muri: «Muro per muro».
 * - Terrazza 200 × 170 nel vuoto in alto tra soggiorno e cucina, balcone 300 × 140 sotto il soggiorno (parapetti di 10 cm).
 */
object PiantinaDiProva {
    const val WIDTH = 1960
    const val HEIGHT = 900
    /** Dove cade l'angolo interno in alto a sinistra della camera, in pixel. */
    private const val OX = 150f
    private const val OY = 150f

    class Stanza(val nome: String, val punti: List<Pair<Float, Float>>)

    val camera = Stanza("Camera", rett(0f, 0f, 350f, 350f))
    val soggiorno = Stanza("Soggiorno", rett(360f, 0f, 880f, 410f))
    val cucina = Stanza("Cucina", listOf(890f to 180f, 1140f to 180f, 1140f to 0f, 1340f to 0f, 1340f to 480f, 890f to 480f))
    val bagno = Stanza("Bagno", listOf(1350f to 0f, 1590f to 0f, 1630f to 225f, 1350f to 205f))
    val studio = Stanza("Studio", listOf(0f to 360f, 350f to 360f, 350f to 510f, 250f to 510f, 250f to 610f, 0f to 610f))
    val stanze = listOf(camera, soggiorno, cucina, bagno, studio)

    /** Spazio all'aperto: `punti` è la parte calpestabile, `parapetti` i lati (da `punti[i]` a `punti[i + 1]`) senza muro della casa. */
    class Esterno(val nome: String, val punti: List<Pair<Float, Float>>, val parapetti: Set<Int>)

    val terrazza = Esterno("Terrazza", rett(910f, -20f, 1110f, 150f), parapetti = setOf(0))
    val balcone = Esterno("Balcone", rett(470f, 440f, 770f, 580f), parapetti = setOf(1, 2, 3))
    val esterni = listOf(terrazza, balcone)

    /** Misure del bagno storto come si prendono col metro (arrotondate al centimetro). */
    val latiBagno: List<Int> get() = bagno.punti.indices.map { i -> lato(bagno, i).toInt() }
    val diagonaleBagno: Int get() = hypot(bagno.punti[2].first - bagno.punti[0].first, bagno.punti[2].second - bagno.punti[0].second).let { Math.round(it).toInt() }

    private fun rett(x0: Float, y0: Float, x1: Float, y1: Float) = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
    private fun lato(st: Stanza, i: Int): Float {
        val (ax, ay) = st.punti[i]
        val (bx, by) = st.punti[(i + 1) % st.punti.size]
        return Math.round(hypot(bx - ax, by - ay)).toFloat()
    }

    fun px(x: Float) = OX + x
    fun py(y: Float) = OY + y

    private val reg = FontMgr.default.matchFamilyStyle("DejaVu Sans", FontStyle.NORMAL)
    private val bold = FontMgr.default.matchFamilyStyle("DejaVu Sans", FontStyle.BOLD)

    fun png(): ByteArray {
        val s = Surface.makeRasterN32Premul(WIDTH, HEIGHT)
        val c = s.canvas
        c.clear(0xFFFFFFFF.toInt())
        val inkColor = 0xFF1E1E1E.toInt()
        val ink = Paint().apply { color = inkColor; isAntiAlias = true }
        fun path(st: Stanza) = PathBuilder(PathFillMode.WINDING).apply {
            st.punti.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(px(x), py(y)) else lineTo(px(x), py(y)) }
            closePath()
        }.detach()
        fun path(pts: List<Pair<Float, Float>>) = path(Stanza("", pts))
        // Parapetti di terrazza e balcone: 10 cm tutto intorno; contro la casa li copre il muro, disegnato dopo.
        val parapet = Paint().apply { color = 0xFF8A8A8A.toInt(); mode = PaintMode.STROKE; strokeWidth = 20f; strokeJoin = PaintStrokeJoin.MITER; isAntiAlias = true }
        for (e in esterni) c.drawPath(path(e.punti), parapet)
        // I muri: ogni stanza contornata da 30 cm verso fuori; dove due stanze sono a 10 cm, il muro resta di 10.
        val wall = Paint().apply { color = inkColor; mode = PaintMode.STROKE; strokeWidth = 60f; strokeJoin = PaintStrokeJoin.MITER; strokeMiter = 10f; isAntiAlias = true }
        for (st in stanze) c.drawPath(path(st), wall)
        val white = Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
        for (st in stanze) c.drawPath(path(st), white)
        // Il pavimento all'aperto un po' grigio, per distinguerlo dalle stanze.
        val outdoorFloor = Paint().apply { color = 0xFFF1F1F1.toInt(); isAntiAlias = true }
        for (e in esterni) c.drawPath(path(e.punti), outdoorFloor)
        val gray = Paint().apply { color = 0xFF5A5A5A.toInt(); isAntiAlias = true }
        fun center(text: String, x: Float, y: Float, font: Font, p: Paint) = c.drawString(text, x - font.measureTextWidth(text) / 2, y, font, p)
        // Nome, superficie e misura di ogni lato (scritta dentro la stanza, vicino al muro).
        val small = Font(reg, 19f)
        for (st in stanze + esterni.map { Stanza(it.nome, it.punti) }) {
            val n = st.punti.size
            val cx = st.punti.sumOf { it.first.toDouble() }.toFloat() / n
            val cy = st.punti.sumOf { it.second.toDouble() }.toFloat() / n
            val (lx, ly) = when (st) {
                cucina -> 1115f to 345f
                studio -> 130f to 470f
                bagno -> 1432f to 148f
                else -> cx to cy
            }
            // Terrazza e balcone sono piccoli: nome più piccolo, che non tocchi le misure dei lati.
            val nameSize = if (esterni.any { it.nome == st.nome }) 20f else 24f
            center(st.nome.uppercase(), px(lx), py(ly) - 6f, Font(bold, nameSize), ink)
            center("${fmt(area(st))} m²", px(lx), py(ly) + 22f, Font(reg, 19f), gray)
            for (i in 0 until n) {
                val (ax, ay) = st.punti[i]
                val (bx, by) = st.punti[(i + 1) % n]
                val mx = (ax + bx) / 2
                val my = (ay + by) / 2
                // Verso l'interno: le stanze sono girate in senso orario sullo schermo, l'interno è a destra del lato.
                val len = hypot(bx - ax, by - ay)
                val nx = -(by - ay) / len
                val ny = (bx - ax) / len
                val label = if (st == bagno) "${"ABCD"[i]}${"ABCD"[(i + 1) % 4]} ${lato(st, i).toInt()}" else lato(st, i).toInt().toString()
                val tx = px(mx + nx * 24f)
                val ty = py(my + ny * 24f)
                c.save()
                c.translate(tx, ty)
                var ang = Math.toDegrees(atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
                if (ang > 90f) ang -= 180f
                if (ang < -90f) ang += 180f
                c.rotate(ang)
                center(label, 0f, 7f, small, gray)
                c.restore()
            }
        }
        // Bagno: lettere agli angoli e diagonale AC tratteggiata.
        val (a0, a1) = bagno.punti[0]
        val (c0, c1) = bagno.punti[2]
        val dash = Paint().apply { color = 0xFF8A8A8A.toInt(); mode = PaintMode.STROKE; strokeWidth = 2f; pathEffect = PathEffect.makeDash(floatArrayOf(10f, 7f), 0f); isAntiAlias = true }
        c.drawLine(px(a0), py(a1), px(c0), py(c1), dash)
        c.save()
        c.translate(px(a0 + (c0 - a0) * 0.62f + 8f), py(a1 + (c1 - a1) * 0.62f - 10f))
        c.rotate(Math.toDegrees(atan2((c1 - a1).toDouble(), (c0 - a0).toDouble())).toFloat())
        center("AC $diagonaleBagno", 0f, 0f, small, gray)
        c.restore()
        val letters = listOf(10f to 22f, -14f to 22f, -18f to -10f, 10f to -10f)
        bagno.punti.forEachIndexed { i, (x, y) -> center("ABCD"[i].toString(), px(x + letters[i].first), py(y + letters[i].second), Font(bold, 20f), ink) }
        c.drawString("misure in cm, sul filo interno dei muri", 40f, HEIGHT - 20f, Font(reg, 18f), gray)
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    fun area(st: Stanza): Double {
        var a = 0.0
        val p = st.punti
        for (i in p.indices) {
            val (x0, y0) = p[i]
            val (x1, y1) = p[(i + 1) % p.size]
            a += x0.toDouble() * y1 - x1.toDouble() * y0
        }
        return abs(a) / 2 / 10000
    }

    private fun fmt(v: Double) = String.format(Locale.ITALY, "%.2f", v)

    /** La lettura che darebbe il servizio vero su questa piantina, nei pixel di un'immagine larga `width`. */
    fun lettura(width: Int, height: Int): PlanReading {
        val k = width.toDouble() / WIDTH
        fun p(x: Float, y: Float) = listOf(px(x) * k, py(y) * k)
        val rooms = (stanze + esterni.filter { conEsterni }.map { Stanza(it.nome, it.punti) }).map { st ->
            PlanReading.ReadRoom(
                name = st.nome,
                corners = st.punti.map { (x, y) -> p(x, y) },
                sidesM = st.punti.indices.map { i -> lato(st, i) / 100.0 },
                areaM2 = area(st),
            )
        }
        return PlanReading(image = PlanReading.ImageSize(width, height), rooms = rooms, openings = emptyList(), exteriorWallPx = 30.0 * k)
    }

    /** Nella lettura finta ci sono anche terrazza e balcone (false: solo le stanze, per metterli a mano nel video). */
    var conEsterni = true

    /** Il servizio di lettura finto: risponde dopo qualche secondo con [lettura]. */
    val lettore = object : PlanReader {
        override suspend fun read(image: ByteArray, mediaType: String, width: Int, height: Int): PlanReaderResult {
            delay(3500)
            return PlanReaderResult.Ok(lettura(width, height))
        }
    }
}
