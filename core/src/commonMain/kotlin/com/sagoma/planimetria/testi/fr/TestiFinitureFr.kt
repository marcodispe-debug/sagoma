package com.sagoma.planimetria.testi.fr

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
}
