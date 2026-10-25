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
 * Piantina inventata per il tutorial (nessuna casa vera): «Bilocale con soggiorno a L e terrazza». Stanze con i muri, una
 * terrazza e un balcone, senza porte né finestre. Ogni stanza si disegna con un modo diverso di sagoma e ha scritte le
 * misure che servono per quel modo. Un pixel dell'immagine è un centimetro; le coordinate sono sul filo interno dei muri
 * (interni 10 cm, esterni 30 cm).
 *
 * - [quadrata]: Studio 330 × 330, «Quadrato».
 * - [rettangolare]: Camera 400 × 330, «Rettangolo».
 * - [aL]: Soggiorno 640 × 270 con 300 × 190 che sporge in basso a destra, «A L».
 * - [storta]: Bagno con i lati AB, BC, CD, DA e la diagonale AC, «Da rilievo».
 * - [seiMuri]: Ingresso a sei muri, «Muro per muro».
 * - Terrazza 640 × 240 a sud, balcone 300 × 140 a nord della camera (parapetti di 10 cm).
 */
object PiantinaDiProva {
    /**
     * Una stanza disegnata: `etichetta` è dove va il nome (null: nel baricentro dei vertici), `lettere` scrive A, B, C, …
     * agli angoli e la diagonale AC tratteggiata.
     */
    class Stanza(
        val nome: String,
        val punti: List<Pair<Float, Float>>,
        val etichetta: Pair<Float, Float>? = null,
        val nomePiccolo: Boolean = false,
        val lettere: Boolean = false,
    )

    val soggiorno = Stanza("Soggiorno", listOf(0f to 0f, 640f to 0f, 640f to 460f, 340f to 460f, 340f to 270f, 0f to 270f), etichetta = 330f to 150f)
    val studio = Stanza("Studio", rett(0f, 280f, 330f, 610f))
    val camera = Stanza("Camera", rett(650f, 0f, 1050f, 330f))
    val ingresso = Stanza("Ingresso", listOf(650f to 340f, 780f to 340f, 780f to 610f, 340f to 610f, 340f to 470f, 650f to 470f), etichetta = 465f to 545f, nomePiccolo = true)
    // Tra camera e bagno il muro è di 15 cm, come quello che sagoma mette da sola: nel video il bagno si posa contro la camera.
    val bagno = Stanza("Bagno", listOf(790f to 345f, 1050f to 345f, 1050f to 565f, 790f to 615f), etichetta = 900f to 475f, lettere = true)
    val stanze = listOf(soggiorno, studio, camera, ingresso, bagno)

    val quadrata get() = studio
    val rettangolare get() = camera
    val aL get() = soggiorno
    val storta get() = bagno
    val seiMuri get() = ingresso

    /** Spazio all'aperto: `punti` è la parte calpestabile; i parapetti girano tutto intorno (contro la casa li copre il muro). */
    val terrazza = Stanza("Terrazza", rett(-20f, 640f, 620f, 880f), nomePiccolo = true)
    val balcone = Stanza("Balcone", rett(700f, -170f, 1000f, -30f), nomePiccolo = true)
    val esterni = listOf(terrazza, balcone)

    /** Misure della stanza storta come si prendono col metro (arrotondate al centimetro). */
    val latiStorta: List<Int> get() = storta.punti.indices.map { i -> lato(storta, i).toInt() }
    val diagonaleStorta: Int get() = hypot(storta.punti[2].first - storta.punti[0].first, storta.punti[2].second - storta.punti[0].second).let { Math.round(it).toInt() }

    private const val MARGINE = 150f
    private val tutti get() = (stanze + esterni).flatMap { it.punti }
    /** Dove cade lo zero della piantina, in pixel. */
    private val OX = MARGINE - tutti.minOf { it.first }
    private val OY = MARGINE - tutti.minOf { it.second }
    val WIDTH = (tutti.maxOf { it.first } + OX + MARGINE).toInt()
    val HEIGHT = (tutti.maxOf { it.second } + OY + MARGINE).toInt()

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
        // Parapetti di terrazza e balcone: 10 cm tutto intorno; contro la casa li copre il muro, disegnato dopo.
        val parapet = Paint().apply { color = 0xFF8A8A8A.toInt(); mode = PaintMode.STROKE; strokeWidth = 20f; strokeJoin = PaintStrokeJoin.MITER; isAntiAlias = true }
        for (e in esterni) c.drawPath(path(e), parapet)
        // I muri: ogni stanza contornata da 30 cm verso fuori; dove due stanze sono a 10 cm, il muro resta di 10.
        val wall = Paint().apply { color = inkColor; mode = PaintMode.STROKE; strokeWidth = 60f; strokeJoin = PaintStrokeJoin.MITER; strokeMiter = 10f; isAntiAlias = true }
        for (st in stanze) c.drawPath(path(st), wall)
        val white = Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
        for (st in stanze) c.drawPath(path(st), white)
        // Il pavimento all'aperto un po' grigio, per distinguerlo dalle stanze.
        val outdoorFloor = Paint().apply { color = 0xFFF1F1F1.toInt(); isAntiAlias = true }
        for (e in esterni) c.drawPath(path(e), outdoorFloor)
        val gray = Paint().apply { color = 0xFF5A5A5A.toInt(); isAntiAlias = true }
        fun center(text: String, x: Float, y: Float, font: Font, p: Paint) = c.drawString(text, x - font.measureTextWidth(text) / 2, y, font, p)
        fun ruotato(text: String, x: Float, y: Float, gradi: Float) {
            c.save()
            c.translate(x, y)
            c.rotate(gradi)
            center(text, 0f, 7f, Font(reg, 19f), gray)
            c.restore()
        }
        // Nome, superficie e misura di ogni lato (scritta dentro la stanza, vicino al muro).
        for (st in stanze + esterni) {
            val n = st.punti.size
            val (lx, ly) = st.etichetta ?: (st.punti.sumOf { it.first.toDouble() }.toFloat() / n to st.punti.sumOf { it.second.toDouble() }.toFloat() / n)
            center(st.nome.uppercase(), px(lx), py(ly) - 6f, Font(bold, if (st.nomePiccolo) 20f else 24f), ink)
            center("${fmt(area(st))} m²", px(lx), py(ly) + 22f, Font(reg, 19f), gray)
            for (i in 0 until n) {
                val (ax, ay) = st.punti[i]
                val (bx, by) = st.punti[(i + 1) % n]
                // Verso l'interno: le stanze sono girate in senso orario sullo schermo, l'interno è a destra del lato.
                val len = hypot(bx - ax, by - ay)
                val nx = -(by - ay) / len
                val ny = (bx - ax) / len
                val label = if (st.lettere) "${LETTERE[i]}${LETTERE[(i + 1) % n]} ${lato(st, i).toInt()}" else lato(st, i).toInt().toString()
                var ang = Math.toDegrees(atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
                if (ang > 90f) ang -= 180f
                if (ang < -90f) ang += 180f
                ruotato(label, px((ax + bx) / 2 + nx * 24f), py((ay + by) / 2 + ny * 24f), ang)
            }
        }
        // Stanza storta: lettere agli angoli e diagonale AC tratteggiata, con la misura vicino a C.
        for (st in stanze.filter { it.lettere }) {
            val (a0, a1) = st.punti[0]
            val (c0, c1) = st.punti[2]
            val dash = Paint().apply { color = 0xFF8A8A8A.toInt(); mode = PaintMode.STROKE; strokeWidth = 2f; pathEffect = PathEffect.makeDash(floatArrayOf(10f, 7f), 0f); isAntiAlias = true }
            c.drawLine(px(a0), py(a1), px(c0), py(c1), dash)
            val l = hypot(c0 - a0, c1 - a1)
            ruotato(
                "AC ${Math.round(l)}",
                px(a0 + (c0 - a0) * 0.8f - (c1 - a1) / l * 22f),
                py(a1 + (c1 - a1) * 0.8f + (c0 - a0) / l * 22f),
                Math.toDegrees(atan2((c1 - a1).toDouble(), (c0 - a0).toDouble())).toFloat(),
            )
            // Le lettere appena dentro gli angoli: A in alto a sinistra, poi in senso orario.
            val spinta = listOf(12f to 24f, -14f to 24f, -14f to -10f, 12f to -10f)
            st.punti.forEachIndexed { i, (x, y) -> center(LETTERE[i].toString(), px(x + spinta[i % 4].first), py(y + spinta[i % 4].second), Font(bold, 20f), ink) }
        }
        c.drawString("misure in cm, sul filo interno dei muri", 40f, HEIGHT - 20f, Font(reg, 18f), gray)
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private const val LETTERE = "ABCDEFGHIJKL"

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
        val rooms = (stanze + esterni.filter { conEsterni }).map { st ->
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
