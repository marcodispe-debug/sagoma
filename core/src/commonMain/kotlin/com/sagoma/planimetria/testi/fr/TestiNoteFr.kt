package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiNote

object TestiNoteFr : TestiNote() {
    override val piccolo: String get() = "Petit"
    override val medio: String get() = "Moyen"
    override val grande: String get() = "Grand"
    override val quotaToccaPrimoPunto: String get() = "↔ Cote : touchez le premier point (il s'aimante aux angles et aux faces des murs)"
    override val quotaToccaSecondoPunto: String get() = "↔ Cote : touchez le second point"
    override val tTestoToccaDove: String get() = "T Texte : touchez l'endroit où l'écrire"
    override val nuovoTesto: String get() = "Nouveau texte"
    override val testoTesto: String get() = "Texte"
    override val dimensione: String get() = "Taille"
    override val metti: String get() = "Placer"
    override fun misuraCmAggiornaSpostando(p1: Any?): String = "Mesure : $p1 cm (elle se met à jour quand vous déplacez les extrémités)."
    override val scrittaPostoMisuraVuoto: String get() = "Texte à la place de la mesure (vide = la mesure)"
    override val distanzaLineaCm: String get() = "Distance de la ligne (cm)"
    override val dallAltraParte: String get() = "⇅ De l'autre côté"
    override val trascinaLineaAllontanarlaEstremita: String get() = "Faites glisser la ligne pour l'éloigner, les extrémités pour mesurer autre chose : elles s'aimantent aux angles et aux faces des murs."
    override val verticale: String get() = "↺ À la verticale"
    override val orizzontale: String get() = "↻ À l'horizontale"
    override val freccia: String get() = "↘ Flèche"
    override val togliFreccia: String get() = "Retirer la flèche"
    override val trascinaTestoSpostarlo: String get() = "Faites glisser le texte pour le déplacer"
    override val trascinaPuntaFrecciaIndicare: String get() = " ; faites glisser la pointe de la flèche pour indiquer un endroit."
    override val demolire: String get() = "À démolir"
    override val nuovo: String get() = "Nouveau"
    override val livelli: String get() = "Calques"
    override val nascostoNonVedePianta: String get() = "Masqué : n'apparaît ni sur le plan, ni dans le PDF, ni dans l'image. Verrouillé : visible mais non modifiable. Les pièces, les murs, les portes et les fenêtres sont toujours actifs."
    override val visibile: String get() = "Visible"
    override val bloccato: String get() = "Verrouillé"
    override val fatto: String get() = "Terminé"
}
