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
    open val vari: TestiVari = TestiVari()
    open val vista3D: TestiVista3D = TestiVista3D()
    open val finiture: TestiFiniture = TestiFiniture()
    open val arredi: TestiArredi = TestiArredi()
    open val piani: TestiPiani = TestiPiani()
    open val selezione: TestiSelezione = TestiSelezione()
    open val computo: TestiComputo = TestiComputo()
    open val progetti: TestiProgetti = TestiProgetti()
    open val note: TestiNote = TestiNote()
    open val sfondo: TestiSfondo = TestiSfondo()
    open val pdf: TestiPdf = TestiPdf()
    open val disegniGuide: TestiDisegniGuide = TestiDisegniGuide()
    open val creazione: TestiCreazione = TestiCreazione()
    open val muroPerMuro: TestiMuroPerMuro = TestiMuroPerMuro()
    open val schedaStanza: TestiSchedaStanza = TestiSchedaStanza()
    open val legenda: TestiLegenda = TestiLegenda()
    open val editor: TestiEditor = TestiEditor()
    open val pannelli: TestiPannelli = TestiPannelli()
    open val foto: TestiFoto = TestiFoto()
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
    val vari: TestiVari get() = lingua.vari
    val vista3D: TestiVista3D get() = lingua.vista3D
    val finiture: TestiFiniture get() = lingua.finiture
    val arredi: TestiArredi get() = lingua.arredi
    val piani: TestiPiani get() = lingua.piani
    val selezione: TestiSelezione get() = lingua.selezione
    val computo: TestiComputo get() = lingua.computo
    val progetti: TestiProgetti get() = lingua.progetti
    val note: TestiNote get() = lingua.note
    val sfondo: TestiSfondo get() = lingua.sfondo
    val pdf: TestiPdf get() = lingua.pdf
    val disegniGuide: TestiDisegniGuide get() = lingua.disegniGuide
    val creazione: TestiCreazione get() = lingua.creazione
    val muroPerMuro: TestiMuroPerMuro get() = lingua.muroPerMuro
    val schedaStanza: TestiSchedaStanza get() = lingua.schedaStanza
    val legenda: TestiLegenda get() = lingua.legenda
    val editor: TestiEditor get() = lingua.editor
    val pannelli: TestiPannelli get() = lingua.pannelli
    val foto: TestiFoto get() = lingua.foto
}

/** Titolo e testo di un passo di una guida. */
data class Passo(val titolo: String, val testo: String)
