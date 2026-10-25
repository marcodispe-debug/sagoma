package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.Comuni
import com.sagoma.planimetria.testi.TestiCorso
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

/** Inglese: ogni area è tradotta nel suo oggetto *En. */
object Inglese : Lingua("en") {
    override val sigla: String get() = "ENG"
    // In inglese solo l'iniziale: "single French window", "LED strip" restano scritti giusti.
    override fun minuscola(s: String): String =
        if (s.length > 1 && s[1].isUpperCase()) s else s.replaceFirstChar { it.lowercaseChar() }

    override val virgola: Char get() = '.'
    override val comuni: Comuni = ComuniEn
    override val nomi: Nomi = NomiEn
    override val suggerimenti: Suggerimenti = SuggerimentiEn
    override val tutorial: TestiTutorial = TestiTutorialEn
    override val guide: TestiGuide = TestiGuideEn
    override val vari: TestiVari = TestiVariEn
    override val vista3D: TestiVista3D = TestiVista3DEn
    override val finiture: TestiFiniture = TestiFinitureEn
    override val arredi: TestiArredi = TestiArrediEn
    override val piani: TestiPiani = TestiPianiEn
    override val selezione: TestiSelezione = TestiSelezioneEn
    override val computo: TestiComputo = TestiComputoEn
    override val progetti: TestiProgetti = TestiProgettiEn
    override val note: TestiNote = TestiNoteEn
    override val sfondo: TestiSfondo = TestiSfondoEn
    override val pdf: TestiPdf = TestiPdfEn
    override val disegniGuide: TestiDisegniGuide = TestiDisegniGuideEn
    override val creazione: TestiCreazione = TestiCreazioneEn
    override val muroPerMuro: TestiMuroPerMuro = TestiMuroPerMuroEn
    override val schedaStanza: TestiSchedaStanza = TestiSchedaStanzaEn
    override val legenda: TestiLegenda = TestiLegendaEn
    override val editor: TestiEditor = TestiEditorEn
    override val pannelli: TestiPannelli = TestiPannelliEn
    override val foto: TestiFoto = TestiFotoEn
    override val corso: TestiCorso = TestiCorsoEn
}
