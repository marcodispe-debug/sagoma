package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.Comuni
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

/** Francese: ogni area è tradotta nel suo oggetto *Fr. */
object Francese : Lingua("fr") {
    override val sigla: String get() = "FRA"
    // Solo l'iniziale: le sigle ("LED", "TV") restano scritte giuste.
    override fun minuscola(s: String): String =
        if (s.length > 1 && s[1].isUpperCase()) s else s.replaceFirstChar { it.lowercaseChar() }

    override val comuni: Comuni = ComuniFr
    override val nomi: Nomi = NomiFr
    override val suggerimenti: Suggerimenti = SuggerimentiFr
    override val tutorial: TestiTutorial = TestiTutorialFr
    override val guide: TestiGuide = TestiGuideFr
    override val vari: TestiVari = TestiVariFr
    override val vista3D: TestiVista3D = TestiVista3DFr
    override val finiture: TestiFiniture = TestiFinitureFr
    override val arredi: TestiArredi = TestiArrediFr
    override val piani: TestiPiani = TestiPianiFr
    override val selezione: TestiSelezione = TestiSelezioneFr
    override val computo: TestiComputo = TestiComputoFr
    override val progetti: TestiProgetti = TestiProgettiFr
    override val note: TestiNote = TestiNoteFr
    override val sfondo: TestiSfondo = TestiSfondoFr
    override val pdf: TestiPdf = TestiPdfFr
    override val disegniGuide: TestiDisegniGuide = TestiDisegniGuideFr
    override val creazione: TestiCreazione = TestiCreazioneFr
    override val muroPerMuro: TestiMuroPerMuro = TestiMuroPerMuroFr
    override val schedaStanza: TestiSchedaStanza = TestiSchedaStanzaFr
    override val legenda: TestiLegenda = TestiLegendaFr
    override val editor: TestiEditor = TestiEditorFr
    override val pannelli: TestiPannelli = TestiPannelliFr
    override val foto: TestiFoto = TestiFotoFr
}
