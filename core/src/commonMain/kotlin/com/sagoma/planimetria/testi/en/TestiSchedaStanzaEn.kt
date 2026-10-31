package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.geometry.HousingNorms
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiSchedaStanza

object TestiSchedaStanzaEn : TestiSchedaStanza() {
    override val info: String get() = "Info"
    override val misure: String get() = "Size"
    override val finiture: String get() = "Finishes"
    override val porteImpianti: String get() = "Doors, fixtures"
    override val progetto: String get() = "Project"
    override val foto: String get() = "Photos"
    override val schedaAspetto: String get() = "Look"
    override val schedaForma: String get() = "Shape"
    override val schedaApertura: String get() = "Opening"
    override val schedaGradini: String get() = "Steps"
    override val schedaPianoSopra: String get() = "Floor above"
    override val schedaCurva: String get() = "Curve"
    override val schedaFinitura: String get() = "Finish"
    override val eliminaStanza: String get() = "Delete room"
    override val distanzeDaiMuri: String get() = "📏 Distances from walls"
    override val nascondiDistanzeDaiMuri: String get() = "📏 Hide distances"
    override val muroSinistra: String get() = "← Left"
    override val muroDestra: String get() = "Right →"
    override val muroSopra: String get() = "↑ Above"
    override val muroSotto: String get() = "↓ Below"
    override val alCentroDelMuro: String get() = "⟷ Centre on the wall"
    override fun perimetroCm(p1: Any?, p2: Any?): String = "$p1 · perimeter $p2 cm"
    override fun luceAria(fin: String, frazione: String, apribili: Boolean): String =
        "Light and air: ${if (apribili) "opening windows" else "windows"} $fin m², $frazione of the floor"
    override fun luceAriaInsieme(nomi: String): String = " (with $nomi)"
    override fun luceAriaNessuna(apribili: Boolean): String = "Light and air: no ${if (apribili) "opening " else ""}window facing outside"
    override fun chiChiede(n: HousingNorms): String = if (n.official) "Rules (${T.controllo.paese(n.country)})" else "Advice"
    override fun luceAriaMinimo(n: HousingNorms, serve: String?): String {
        val ratio = n.windowRatio
        return if (ratio == null || serve == null) "${chiChiede(n)}: living spaces need at least one opening window facing outside."
        else "${chiChiede(n)}: living spaces need at least ${n.ratioLabel(ratio)} of the floor ($serve m²)."
    }
    override fun luceAriaApribile(n: HousingNorms, minimo: String, serve: String): String =
        "${chiChiede(n)}: the opening part needs at least $minimo of the floor ($serve m²)."
    override val allAperto: String get() = " · outdoors"
    override val parapetto: String get() = "Parapet"
    override val altezzaParapetto: String get() = "Parapet height"
    override val parapettoSuiLatiVerso: String get() =
        "The parapet runs along the outward sides; where the balcony touches the house the wall stays. " +
            "To step out onto it, put a French window (🧱 Building elements → French windows) on the room wall."
    override val altezzaSoffitto: String get() = "Ceiling height"
    override val spessoreMuri: String get() = "Wall thickness"
    override val spessoreValeTuttiMuri: String get() =
        "The thickness applies to all the walls of the room; a single wall (for example a 40 cm load-bearing wall or a 10 cm partition) " +
            "is changed by tapping it."
    override val muri: String get() = "Walls"
    override fun muro(p1: Any?): String = "Wall $p1"
    override val altezzaPers: String get() = "Height (custom)"
    override val zoneNonCalpestabili: String get() = "Non-walkable areas"
    override val partiNonContanoNei: String get() = "Parts that do not count in the m², for example the inaccessible area of an attic or a shaft: you draw them by tapping the corners."
    override val togli: String get() = "Remove"
    override fun pavimentoInteroM2Computo(p1: Any?): String = "Whole floor $p1 m² (the quantities use this)."
    override val aggiungiZonaNonCalpestabile: String get() = "▧ Add non-walkable area"
    override val pavimento: String get() = "Floor"
    override fun pavimentoDiPrima(p1: Any?): String = "Previous floor: $p1"
    override val paretiInterne: String get() = "Interior walls"
    override val paretiEsterneTuttaCasa: String get() = "Exterior walls (whole house)"
    override val paretiEsterneSoloPiano: String get() = "Exterior walls (this floor only)"
    override val soloQuestoPiano: String get() = "This floor only"
    override fun aperture(size: Any?): String = "Openings ($size)"
    override val nessunaUsaElementiArchitettonici: String get() = "None. Use 🧱 Building elements at the bottom: Doors, Windows or French windows."
    override fun muroCm(p1: Any?, p2: Any?, p3: Any?): String = "wall $p1 · $p2×$p3 cm"
    override fun terra(p1: Any?): String = " · from floor $p1"
    override fun impianti(size: Any?): String = "Fixtures ($size)"
    override fun impiantoBase(prese: Int, minPrese: Int, luci: Int, minLuci: Int): String =
        "Basic electrical system (CEI 64-8): socket outlets $prese of $minPrese, light points $luci of $minLuci"
    override val nessunoUsaElementiArchitettonici: String get() = "None. Use 🧱 Building elements → Fixtures for radiators, sockets, switches, water points and lights."
    override val muriStanza: String get() = "Room walls: "
    override val statiDiversiCambianoMuro: String get() = "mixed states (changed wall by wall)"
    override val tuttiEsistenti: String get() = "All existing"
    override val tuttiDemolire: String get() = "All to demolish"
    override val tuttiNuovi: String get() = "All new"
    override val singoloMuroPortaFinestra: String get() = "A single wall, door or window is changed by tapping it. In the comparison drawing walls to demolish are yellow, new ones red."
    override fun muroCmTerra(p1: Any?, p2: Any?, p3: Any?, p4: Any?): String = "wall $p1 · $p2×$p3 cm · from floor $p4"
    override fun muroTerraCm(p1: Any?, p2: Any?): String = "wall $p1 · from floor $p2 cm"
    override fun muroCmTerraCm(p1: Any?, p2: Any?, p3: Any?): String = "wall $p1 · $p2 cm · from floor $p3 cm"
    override fun cm(p1: Any?, p2: Any?): String = "$p1 cm · $p2°"
    override val si: String get() = "🧲 On"
    override val no: String get() = "🧲 Off"
    override val agganci: String get() = "🧲 Snap on"
    override val agganciNo: String get() = "🧲 Snap off"
    override val soffitto: String get() = "ceiling"
}
