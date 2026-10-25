package com.sagoma.planimetria.editor

import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Piantina inventata della guida interattiva (nessuna casa vera): «Bilocale con soggiorno a L», con le stanze che le lezioni
 * fanno disegnare e le loro misure. L'immagine la disegna l'app (vedi `GuidePlanImage` nell'interfaccia), così è nella lingua
 * scelta. Un pixel dell'immagine è un centimetro; le coordinate sono sul filo interno dei muri (interni 15 cm, come quelli che
 * Sagoma mette da sola, così le stanze attaccate con gli agganci vengono proprio come nel disegno; esterni 30 cm).
 */
object GuidePlan {
    /**
     * Una stanza disegnata: `label` è dove va il nome (null: nel baricentro dei vertici), `letters` scrive A, B, C, … agli
     * angoli e la diagonale AC tratteggiata.
     */
    class Room(
        val name: () -> String,
        val type: RoomType,
        val points: List<Vec2>,
        val label: Vec2? = null,
        val smallName: Boolean = false,
        val letters: Boolean = false,
    )

    private fun p(x: Int, y: Int) = Vec2(x.toDouble(), y.toDouble())
    private fun rect(x0: Int, y0: Int, x1: Int, y1: Int) = listOf(p(x0, y0), p(x1, y0), p(x1, y1), p(x0, y1))

    /** Soggiorno a L (640 × 270 con 295 × 190 che sporge in basso a destra): «A L», attaccato sopra lo studio. */
    val soggiorno = Room({ RoomType.Soggiorno.label }, RoomType.Soggiorno, listOf(p(0, 0), p(640, 0), p(640, 460), p(345, 460), p(345, 270), p(0, 270)), label = p(330, 150))

    /** Studio 330 × 330: «Quadrato», la prima stanza. */
    val studio = Room({ RoomType.Studio.label }, RoomType.Studio, rect(0, 285, 330, 615))

    /** Camera 400 × 330: «Rettangolo», a destra del soggiorno; nella lezione sulla planimetria un quadrato da allungare. */
    val camera = Room({ RoomType.Camera.label }, RoomType.Camera, rect(655, 0, 1055, 330))

    /** Ingresso a sei muri, nello spazio che resta tra le altre stanze: «Muro per muro». */
    val ingresso = Room(
        { com.sagoma.planimetria.testi.T.corso.ingresso },
        RoomType.Corridoio,
        listOf(p(655, 345), p(780, 345), p(780, 615), p(345, 615), p(345, 475), p(655, 475)),
        label = p(560, 545),
        smallName = true,
    )

    /** Bagno fuori squadra sotto la camera, con i lati AB, BC, CD, DA e la diagonale AC: «Da rilievo». */
    val bagno = Room({ RoomType.Bagno.label }, RoomType.Bagno, listOf(p(795, 345), p(1055, 345), p(1055, 565), p(795, 615)), label = p(905, 475), letters = true)

    val rooms = listOf(soggiorno, studio, camera, ingresso, bagno)

    /** Spazi all'aperto: `points` è la parte calpestabile; i parapetti (10 cm) girano tutto intorno. */
    val terrazza = Room({ RoomType.Terrazza.label }, RoomType.Terrazza, rect(-20, 645, 620, 885), smallName = true)
    val balcone = Room({ RoomType.Balcone.label }, RoomType.Balcone, rect(705, -170, 1005, -30), smallName = true)
    val outdoor = listOf(terrazza, balcone)

    /** Lunghezza del lato `i` (da angolo ad angolo, al centimetro). */
    fun side(r: Room, i: Int): Int = r.points[i].distanceTo(r.points[(i + 1) % r.points.size]).roundToInt()

    /** Lati del bagno come si misurano col metro. */
    val bathSides: List<Int> get() = bagno.points.indices.map { side(bagno, it) }

    /** Diagonale AC del bagno. */
    val bathDiagonal: Int get() = bagno.points[0].distanceTo(bagno.points[2]).roundToInt()

    /** Muri dell'ingresso uno dopo l'altro dal primo angolo, in senso orario: verso e lunghezza (l'ultimo lo chiude sagoma). */
    val entranceWalls: List<Pair<WallArrow, Int>>
        get() = (0 until ingresso.points.size - 1).map { i ->
            val d = ingresso.points[i + 1] - ingresso.points[i]
            val arrow = when {
                d.x > 0.5 -> WallArrow.RIGHT
                d.x < -0.5 -> WallArrow.LEFT
                d.y > 0.5 -> WallArrow.DOWN
                else -> WallArrow.UP
            }
            arrow to hypot(d.x, d.y).roundToInt()
        }

    /** La misura scritta sul muro in alto del soggiorno, usata per la taratura: da A a B. */
    val calibrationA: Vec2 get() = soggiorno.points[0]
    val calibrationB: Vec2 get() = soggiorno.points[1]
    val calibrationCm: Int get() = side(soggiorno, 0)

    private const val MARGIN = 150.0
    private val all get() = (rooms + outdoor).flatMap { it.points }

    /** Pixel dell'immagine in cui cade lo zero della piantina. */
    val origin: Vec2 get() = Vec2(MARGIN - all.minOf { it.x }, MARGIN - all.minOf { it.y })
    val width: Int get() = (all.maxOf { it.x } + origin.x + MARGIN).roundToInt()
    val height: Int get() = (all.maxOf { it.y } + origin.y + MARGIN).roundToInt()

    /** Pixel dell'immagine di un punto della piantina. */
    fun pixel(p: Vec2): Vec2 = p + origin

    /** Dove cade sulla pianta un punto della piantina, con l'immagine caricata come sfondo `u` (tarato o no). */
    fun onUnderlay(u: Underlay, p: Vec2): Vec2 = u.origin + pixel(p) * (u.cmPerPx * u.widthPx / width)
}
