package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.geometry.HousingNorms
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiSchedaStanza

object TestiSchedaStanzaFr : TestiSchedaStanza() {
    override val info: String get() = "Infos"
    override val misure: String get() = "Mesures"
    override val finiture: String get() = "Finitions"
    override val porteImpianti: String get() = "Portes, équip."
    override val progetto: String get() = "Projet"
    override val foto: String get() = "Photos"
    override val schedaAspetto: String get() = "Aspect"
    override val schedaForma: String get() = "Forme"
    override val schedaApertura: String get() = "Ouverture"
    override val schedaGradini: String get() = "Marches"
    override val schedaPianoSopra: String get() = "Étage sup."
    override val schedaCurva: String get() = "Courbe"
    override val schedaFinitura: String get() = "Finition"
    override val eliminaStanza: String get() = "Supprimer la pièce"
    override val distanzeDaiMuri: String get() = "📏 Distances des murs"
    override val nascondiDistanzeDaiMuri: String get() = "📏 Masquer les distances"
    override val muroSinistra: String get() = "← À gauche"
    override val muroDestra: String get() = "À droite →"
    override val muroSopra: String get() = "↑ En haut"
    override val muroSotto: String get() = "↓ En bas"
    override val alCentroDelMuro: String get() = "⟷ Au centre du mur"
    override fun perimetroCm(p1: Any?, p2: Any?): String = "$p1 · périmètre $p2 cm"
    override fun luceAria(fin: String, frazione: String, apribili: Boolean): String =
        "Lumière et air : fenêtres ${if (apribili) "ouvrantes " else ""}$fin m², $frazione du sol"
    override fun luceAriaInsieme(nomi: String): String = " (avec $nomi)"
    override fun luceAriaNessuna(apribili: Boolean): String = "Lumière et air : aucune fenêtre ${if (apribili) "ouvrante " else ""}donnant dehors"
    override fun chiChiede(n: HousingNorms): String = if (n.official) "Règles (${T.controllo.paese(n.country)})" else "Conseil"
    override fun luceAriaMinimo(n: HousingNorms, serve: String?): String {
        val ratio = n.windowRatio
        return if (ratio == null || serve == null) "${chiChiede(n)} : les pièces à vivre demandent au moins une fenêtre ouvrante donnant dehors."
        else "${chiChiede(n)} : les pièces à vivre en demandent au moins ${n.ratioLabel(ratio)} du sol ($serve m²)."
    }
    override fun luceAriaApribile(n: HousingNorms, minimo: String, serve: String): String =
        "${chiChiede(n)} : la partie ouvrante doit faire au moins $minimo du sol ($serve m²)."
    override val allAperto: String get() = " · extérieur"
    override val parapetto: String get() = "Garde-corps"
    override val altezzaParapetto: String get() = "Hauteur du garde-corps"
    override val parapettoSuiLatiVerso: String get() =
        "Le garde-corps longe les côtés vers l'extérieur ; là où le balcon touche la maison, le mur reste. " +
            "Pour y accéder, placez une porte-fenêtre (🧱 Éléments de construction → Portes-fenêtres) sur le mur de la pièce."
    override val altezzaSoffitto: String get() = "Hauteur sous plafond"
    override val spessoreMuri: String get() = "Épaisseur des murs"
    override val spessoreValeTuttiMuri: String get() =
        "L'épaisseur vaut pour tous les murs de la pièce ; un seul mur (par exemple un mur porteur de 40 cm ou une cloison de 10 cm) " +
            "se modifie en le touchant."
    override val muri: String get() = "Murs"
    override fun muro(p1: Any?): String = "Mur $p1"
    override val altezzaPers: String get() = "Hauteur (pers.)"
    override val zoneNonCalpestabili: String get() = "Zones non praticables"
    override val partiNonContanoNei: String get() = "Parties qui ne comptent pas dans les m², par exemple la zone inaccessible de combles ou une gaine : vous les dessinez en touchant les angles."
    override val togli: String get() = "Retirer"
    override fun pavimentoInteroM2Computo(p1: Any?): String = "Sol entier $p1 m² (le métré utilise celui-ci)."
    override val aggiungiZonaNonCalpestabile: String get() = "▧ Ajouter une zone non praticable"
    override val pavimento: String get() = "Sol"
    override fun pavimentoDiPrima(p1: Any?): String = "Ancien sol : $p1"
    override val paretiInterne: String get() = "Murs intérieurs"
    override val paretiEsterneTuttaCasa: String get() = "Murs extérieurs (toute la maison)"
    override val paretiEsterneSoloPiano: String get() = "Murs extérieurs (cet étage seulement)"
    override val soloQuestoPiano: String get() = "Cet étage seulement"
    override fun aperture(size: Any?): String = "Ouvertures ($size)"
    override val nessunaUsaElementiArchitettonici: String get() = "Aucune. Utilisez 🧱 Éléments de construction en bas : Portes, Fenêtres ou Portes-fenêtres."
    override fun muroCm(p1: Any?, p2: Any?, p3: Any?): String = "mur $p1 · $p2×$p3 cm"
    override fun terra(p1: Any?): String = " · depuis le sol $p1"
    override fun impianti(size: Any?): String = "Équipements ($size)"
    override fun impiantoBase(prese: Int, minPrese: Int, luci: Int, minLuci: Int): String =
        "Installation électrique de base (CEI 64-8) : points de prise $prese sur $minPrese, points lumineux $luci sur $minLuci"
    override val nessunoUsaElementiArchitettonici: String get() = "Aucun. Utilisez 🧱 Éléments de construction → Équipements pour radiateurs, prises, interrupteurs, points d'eau et lumières."
    override val muriStanza: String get() = "Murs de la pièce : "
    override val statiDiversiCambianoMuro: String get() = "états différents (modifiés mur par mur)"
    override val tuttiEsistenti: String get() = "Tous existants"
    override val tuttiDemolire: String get() = "Tous à démolir"
    override val tuttiNuovi: String get() = "Tous neufs"
    override val singoloMuroPortaFinestra: String get() = "Un seul mur, une porte ou une fenêtre se modifie en le touchant. Dans le plan comparatif, les murs à démolir sont en jaune, les neufs en rouge."
    override fun muroCmTerra(p1: Any?, p2: Any?, p3: Any?, p4: Any?): String = "mur $p1 · $p2×$p3 cm · depuis le sol $p4"
    override fun muroTerraCm(p1: Any?, p2: Any?): String = "mur $p1 · depuis le sol $p2 cm"
    override fun muroCmTerraCm(p1: Any?, p2: Any?, p3: Any?): String = "mur $p1 · $p2 cm · depuis le sol $p3 cm"
    override fun cm(p1: Any?, p2: Any?): String = "$p1 cm · $p2°"
    override val si: String get() = "🧲 Oui"
    override val no: String get() = "🧲 Non"
    override val agganci: String get() = "🧲 Aimant oui"
    override val agganciNo: String get() = "🧲 Aimant non"
    override val soffitto: String get() = "plafond"
}
