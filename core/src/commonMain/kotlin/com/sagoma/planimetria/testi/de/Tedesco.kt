package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.Comuni
import com.sagoma.planimetria.testi.TestiCorso
import com.sagoma.planimetria.testi.TestiControllo
import com.sagoma.planimetria.testi.Lingua
import com.sagoma.planimetria.testi.Nomi
import com.sagoma.planimetria.testi.Suggerimenti
import com.sagoma.planimetria.testi.TestiArredi
import com.sagoma.planimetria.testi.TestiComputo
import com.sagoma.planimetria.testi.TestiCreazione
import com.sagoma.planimetria.testi.TestiDisegniGuide
import com.sagoma.planimetria.testi.TestiEditor
import com.sagoma.planimetria.testi.TestiFiniture
import com.sagoma.planimetria.testi.TestiFoto
import com.sagoma.planimetria.testi.TestiGuide
import com.sagoma.planimetria.testi.TestiLegenda
import com.sagoma.planimetria.testi.TestiMuroPerMuro
import com.sagoma.planimetria.testi.TestiNote
import com.sagoma.planimetria.testi.TestiPannelli
import com.sagoma.planimetria.testi.TestiPdf
import com.sagoma.planimetria.testi.TestiPiani
import com.sagoma.planimetria.testi.TestiProgetti
import com.sagoma.planimetria.testi.TestiSchedaStanza
import com.sagoma.planimetria.testi.TestiSelezione
import com.sagoma.planimetria.testi.TestiSfondo
import com.sagoma.planimetria.testi.TestiTutorial
import com.sagoma.planimetria.testi.TestiVari
import com.sagoma.planimetria.testi.TestiVista3D

/** Tedesco: ogni area è tradotta nel suo oggetto *De. Si dà del «du», come l'italiano dà del tu. */
object Tedesco : Lingua("de") {
    override val sigla: String get() = "DEU"
    // In tedesco i nomi hanno sempre la maiuscola, anche dentro una frase ("Tippe auf die Wand für: Schiebetür").
    override fun minuscola(s: String): String = s

    override val comuni: Comuni = ComuniDe
    override val nomi: Nomi = NomiDe
    override val suggerimenti: Suggerimenti = SuggerimentiDe
    override val tutorial: TestiTutorial = TestiTutorialDe
    override val guide: TestiGuide = TestiGuideDe
    override val vari: TestiVari = TestiVariDe
    override val vista3D: TestiVista3D = TestiVista3DDe
    override val finiture: TestiFiniture = TestiFinitureDe
    override val arredi: TestiArredi = TestiArrediDe
    override val piani: TestiPiani = TestiPianiDe
    override val selezione: TestiSelezione = TestiSelezioneDe
    override val computo: TestiComputo = TestiComputoDe
    override val progetti: TestiProgetti = TestiProgettiDe
    override val note: TestiNote = TestiNoteDe
    override val sfondo: TestiSfondo = TestiSfondoDe
    override val pdf: TestiPdf = TestiPdfDe
    override val disegniGuide: TestiDisegniGuide = TestiDisegniGuideDe
    override val creazione: TestiCreazione = TestiCreazioneDe
    override val muroPerMuro: TestiMuroPerMuro = TestiMuroPerMuroDe
    override val schedaStanza: TestiSchedaStanza = TestiSchedaStanzaDe
    override val legenda: TestiLegenda = TestiLegendaDe
    override val editor: TestiEditor = TestiEditorDe
    override val pannelli: TestiPannelli = TestiPannelliDe
    override val foto: TestiFoto = TestiFotoDe
    override val corso: TestiCorso = TestiCorsoDe
    override val controllo: TestiControllo = TestiControlloDe
}
