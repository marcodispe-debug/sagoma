package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiPiani

object TestiPianiFr : TestiPiani() {
    override val aggiungiPianoSopra: String get() = "+ Ajouter un étage au-dessus"
    override fun modifica(name: Any?): String = "✎ Modifier « $name »"
    override fun eliminare(name: Any?): String = "Supprimer $name ?"
    override val verrannoTolteTutteStanze: String get() = "Toutes les pièces et les escaliers de cet étage seront supprimés. Vous pouvez les récupérer avec ↶ Annuler en haut."
    override val nuovoPiano: String get() = "Nouvel étage"
    override val modificaPiano: String get() = "Modifier l'étage"
    override val interpianoAutomatico: String get() = "Hauteur d'étage automatique"
    override fun interpianoCm(p1: Any?): String = "Hauteur d'étage : $p1 cm"
    override fun calcolatoAltezzaStanzaPiu(p1: Any?): String = "Calculée comme la hauteur de la pièce la plus haute de l'étage plus $p1 cm de plancher : elle se met à jour si vous changez la hauteur des pièces."
    override val interpiano: String get() = "Hauteur d'étage"
    override fun pavimentoQuestoPianoQuello(p1: Any?): String =
        "Du sol de cet étage au sol de l'étage au-dessus : la hauteur sous plafond plus l'épaisseur du plancher (souvent $p1 cm). " +
            "Elle reste telle que vous l'écrivez, même si vous changez la hauteur des pièces."
    override val scaleQuestoPianoSalgono: String get() = "Les escaliers de cet étage montent de cette hauteur."
    override fun partiMuriLiModifichi(p1: Any?): String = "Partir des murs de « $p1 » (puis les modifier)"
    override val eliminaQuestoPiano: String get() = "Supprimer cet étage"
    override val aggiungi: String get() = "Ajouter"
    override val pianoTerra: String get() = "Rez-de-chaussée"
    override val primoPiano: String get() = "1er étage"
    override val secondoPiano: String get() = "2e étage"
    override val terzoPiano: String get() = "3e étage"
    override val quartoPiano: String get() = "4e étage"
    override fun piano(index: Any?): String = "Étage $index"

    override val pianiComuni: List<String> get() = listOf("Rez-de-chaussée surélevé", "Demi-sous-sol", "Sous-sol", "Entresol", "Mezzanine")
}
