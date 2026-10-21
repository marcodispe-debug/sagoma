package com.sagoma.planimetria.testi

import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.testi.en.Inglese
import com.sagoma.planimetria.testi.es.Spagnolo
import com.sagoma.planimetria.testi.fr.Francese

/**
 * Testi dell'app, divisi per argomento. L'italiano è scritto qui come base; una traduzione estende le parti
 * ([Nomi], [Suggerimenti], …) e cambia solo i testi che traduce: quelli che mancano restano in italiano.
 *
 * Per aggiungere una lingua: un oggetto come [com.sagoma.planimetria.testi.en.Inglese], con le sue classi dei testi,
 * e una voce in [LanguageChoice] e in [Lingue.scegli].
 */
open class Lingua(val codice: String) {
    /** Sigla sul pulsante della lingua, accanto alla bandiera. */
    open val sigla: String get() = "ITA"

    /** Nome in minuscolo dentro una frase ("Tocca il muro su cui posizionare: porta ad 1 anta"). */
    open fun minuscola(s: String): String = s.lowercase()

    /** Separatore dei decimali nei numeri mostrati (20,50 m² in italiano). */
    open val virgola: Char get() = ','
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

/** Le lingue dell'app e quale usare per la scelta dell'utente. */
object Lingue {
    /** Tutte le lingue dell'app. */
    val tutte: List<Lingua> get() = listOf(Italiano, Inglese, Francese, Spagnolo)

    /**
     * Lingua per la scelta: con "come il sistema" quella del sistema se l'app la conosce, altrimenti l'inglese
     * (capito da più persone dell'italiano). `sistema`: codice della lingua del sistema ("it", "en-US", "fr-CA"…).
     */
    fun scegli(choice: LanguageChoice, sistema: String): Lingua = when (choice) {
        LanguageChoice.Italian -> Italiano
        LanguageChoice.English -> Inglese
        LanguageChoice.French -> Francese
        LanguageChoice.Spanish -> Spagnolo
        LanguageChoice.System -> sistema.lowercase().let { s -> tutte.firstOrNull { s.startsWith(it.codice) } ?: Inglese }
    }
}

/** Testi della lingua scelta: `T.nomi.roomType(…)`, `T.suggerimenti.testo(…)`. */
object T {
    /** Lingua dei testi. Le schermate non se ne accorgono da sole: quando cambia, SagomaApp le ridisegna da capo. */
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
