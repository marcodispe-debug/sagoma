package com.sagoma.planimetria.testi.es

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

/** Spagnolo: ogni area è tradotta nel suo oggetto *Es. */
object Spagnolo : Lingua("es") {
    override val sigla: String get() = "ESP"
    // Solo l'iniziale: le sigle ("LED", "TV") restano scritte giuste.
    override fun minuscola(s: String): String =
        if (s.length > 1 && s[1].isUpperCase()) s else s.replaceFirstChar { it.lowercaseChar() }

    override val comuni: Comuni = ComuniEs
    override val nomi: Nomi = NomiEs
    override val suggerimenti: Suggerimenti = SuggerimentiEs
    override val tutorial: TestiTutorial = TestiTutorialEs
    override val guide: TestiGuide = TestiGuideEs
    override val vari: TestiVari = TestiVariEs
    override val vista3D: TestiVista3D = TestiVista3DEs
    override val finiture: TestiFiniture = TestiFinitureEs
    override val arredi: TestiArredi = TestiArrediEs
    override val piani: TestiPiani = TestiPianiEs
    override val selezione: TestiSelezione = TestiSelezioneEs
    override val computo: TestiComputo = TestiComputoEs
    override val progetti: TestiProgetti = TestiProgettiEs
    override val note: TestiNote = TestiNoteEs
    override val sfondo: TestiSfondo = TestiSfondoEs
    override val pdf: TestiPdf = TestiPdfEs
    override val disegniGuide: TestiDisegniGuide = TestiDisegniGuideEs
    override val creazione: TestiCreazione = TestiCreazioneEs
    override val muroPerMuro: TestiMuroPerMuro = TestiMuroPerMuroEs
    override val schedaStanza: TestiSchedaStanza = TestiSchedaStanzaEs
    override val legenda: TestiLegenda = TestiLegendaEs
    override val editor: TestiEditor = TestiEditorEs
    override val pannelli: TestiPannelli = TestiPannelliEs
    override val foto: TestiFoto = TestiFotoEs
}
