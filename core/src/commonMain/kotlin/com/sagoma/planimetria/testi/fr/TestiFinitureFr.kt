package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.testi.TestiFiniture

object TestiFinitureFr : TestiFiniture() {
    override val pittura: String get() = "Peinture"
    override val rivestimento: String get() = "Revêtement"
    override val materiale: String get() = "Matériau"
    override fun rivestimentoDiPrima(p1: Any?): String = "Ancien revêtement : $p1"
    override fun colore(p1: Any?): String = "Couleur ($p1)"
    override val tuttaParete: String get() = "Tout le mur"
    override fun altoCmTerra(p1: Any?): String = "Jusqu'à $p1 cm du sol"
    override val colorePersonalizzato: String get() = "Couleur personnalisée"
    override val tinta: String get() = "Teinte"
    override val saturazione: String get() = "Saturation"
    override val luminosita: String get() = "Luminosité"
    override val codiceEsC9d3bf: String get() = "Code (ex. C9D3BF)"
    override val usaQuestoColore: String get() = "Utiliser cette couleur"
    override val nessuno: String get() = "Aucun"
    override fun verso(p1: Any?): String = "Sens : $p1°"
    override fun misuraPezzi(p1: Any?): String = "Taille des pièces : $p1 %"
    override val comeNelCatalogo: String get() = "Comme dans le catalogue"
    override val posa: String get() = "Pose"
    override val comeNellaFoto: String get() = "Comme sur la photo"
    override val aPezzi: String get() = "Pièce par pièce"
    override fun schemaPosa(p: LayingPattern): String = when (p) {
        LayingPattern.Dritta -> "Droite"
        LayingPattern.Sfalsata -> "Décalée ½"
        LayingPattern.Terzi -> "Décalée ⅓"
        LayingPattern.ACorrere -> "À l'anglaise"
        LayingPattern.SpinaDiPesce -> "Bâtons rompus"
        LayingPattern.Cesto -> "Vannerie"
    }
    override fun larghezzaPezzo(p1: Any?): String = "Largeur des pièces : $p1 cm"
    override fun lunghezzaPezzo(p1: Any?): String = "Longueur des pièces : $p1 cm"
    override fun fuga(p1: Any?): String = "Joint : $p1 mm"
    override val coloreFuga: String get() = "Couleur du joint"
    override val partenza: String get() = "Départ"
    override val dalCentro: String get() = "Du centre"
    override val daUnAngolo: String get() = "D'un angle"
    override val fotoGiaPosata: String get() = "Cette photo est déjà posée (bâtons rompus, tomettes…) : vous pouvez la tourner et changer sa taille, pas la poser pièce par pièce."
    override fun pezziServono(tot: Any?, interi: Any?, tagliati: Any?, m2: Any?): String =
        "Il faut environ $tot pièces : $interi entières et $tagliati à couper, pour $m2 m² (plus les chutes)."
}
