package com.sagoma.planimetria.testi

/**
 * Testi dell'app, divisi per argomento. L'italiano è scritto qui come base; una traduzione estende le parti
 * ([Nomi], [Suggerimenti], …) e cambia solo i testi che traduce: quelli che mancano restano in italiano.
 *
 * Per aggiungere una lingua:
 * ```
 * object Inglese : Lingua("en") {
 *     override val nomi = object : Nomi() { override fun roomType(v: RoomType) = when (v) { … } }
 * }
 * ```
 * e la si sceglie all'avvio con `T.lingua = Inglese`, prima di mostrare qualcosa.
 */
open class Lingua(val codice: String) {
    open val comuni: Comuni = Comuni()
    open val nomi: Nomi = Nomi()
    open val suggerimenti: Suggerimenti = Suggerimenti()
    open val tutorial: TestiTutorial = TestiTutorial()
    open val guide: TestiGuide = TestiGuide()
}

object Italiano : Lingua("it")

/** Testi della lingua scelta: `T.nomi.roomType(…)`, `T.suggerimenti.testo(…)`. */
object T {
    /** Lingua dei testi: si sceglie all'avvio (cambiarla con l'app aperta non aggiorna le schermate già mostrate). */
    var lingua: Lingua = Italiano

    val comuni: Comuni get() = lingua.comuni
    val nomi: Nomi get() = lingua.nomi
    val suggerimenti: Suggerimenti get() = lingua.suggerimenti
    val tutorial: TestiTutorial get() = lingua.tutorial
    val guide: TestiGuide get() = lingua.guide
}

/** Titolo e testo di un passo di una guida. */
data class Passo(val titolo: String, val testo: String)
