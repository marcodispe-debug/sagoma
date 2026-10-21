package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiDisegniGuide

object TestiDisegniGuideFr : TestiDisegniGuide() {
    override val si: String get() = "🧲 Oui"
    override val no: String get() = "🧲 Non"
    override val camera: String get() = "Chambre"
    override val balcone: String get() = "Balcon"
    override val elementi: String get() = "🧱 Éléments"
    override val rampaDritta: String get() = "Volée droite"
    override val dritta: String get() = "Droit"
    override val chiocciola: String get() = "Colimaçon"
    override val primoPiano: String get() = "🏠 1er étage ▾"
    override val pianoTerra: String get() = "🏠 Rez-de-chaussée ▾"
    override val aggiungiPianoSopra: String get() = "+ Ajouter un étage au-dessus"
    override val soggiorno: String get() = "Séjour"
    override val info: String get() = "Infos ▲"
    override val nomeCamera: String get() = "Nom · Chambre"
    override val tipoCamera: String get() = "Type · Chambre"
    override val muri400300: String get() = "Murs · 400 × 300"
    override val lato1: String get() = "Côté 1"
    override val stanza: String get() = "Pièce"
    override val cucina: String get() = "Cuisine"
    override val bagno: String get() = "Salle de bain"
    override val porta1Anta: String get() = "Porte simple"
    override val presaCorrente: String get() = "Prise électrique"
    override val stanza2: String get() = "Pièce 2"
    override val annulla: String get() = "↶ Annuler"
    override val strumenti: String get() = "🛠 Outils"
    override fun altezzaMuro(value: Any?): String = "Hauteur du mur : $value"
    override val taglioDiagonale: String get() = "Coupe en biais"
    override val muretto: String get() = "muret"
}
