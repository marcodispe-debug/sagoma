package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DFr : TestiVista3D() {
    override val luciAccese: String get() = "💡 Lumières allumées"
    override val luciSpente: String get() = "💡 Lumières éteintes"
    override val luciAuto: String get() = "💡 Lumières : auto"
    override fun luminositaLuci(pct: Int): String = "💡 Luminosité des lumières : $pct %"
    override val vistaDallAlto: String get() = "🏠 Vue de dessus"
    override val cammina: String get() = "🚶 Marcher"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Heure : $h:$p2"
    override val soleSegueBussola: String get() =
        "Le soleil suit la ville, le jour et le nord choisis avec la boussole 🧭 sur le plan ; la lumière entre par les fenêtres. " +
            "Quand il fait nuit, les lumières de la maison s'allument."
    override val nordCitta: String get() = "🧭 Nord et ville"
    override val bussola: String get() = "Boussole : faites-la glisser pour tourner le N vers le vrai nord, touchez-la pour choisir le nord et la ville"
    override val spiegazioneNord: String get() =
        "Tournez la flèche jusqu'à ce que le N indique le vrai nord par rapport à votre plan. Le plan ne bouge pas : en 3D, seul le soleil se déplace. C'est facultatif : sinon, le nord est en haut et la ville est Rome."
    override val nordGradi: String get() = "Nord (degrés dans le sens horaire depuis le haut)"
    override val nordInAlto: String get() = "Nord en haut"
    override val citta: String get() = "Ville"
    override val cercaCitta: String get() = "Chercher la ville"
    override val nessunaCitta: String get() = "Aucune (Rome par défaut)"
    override fun cittaScelta(nome: Any?): String = "Ville : $nome"
    override val giorno: String get() = "Jour"
    override val oggi: String get() = "Aujourd'hui"
    override val marzo21: String get() = "21 mars"
    override val giugno21: String get() = "21 juin"
    override val settembre21: String get() = "21 septembre"
    override val dicembre21: String get() = "21 décembre"
}
