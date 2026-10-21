package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DFr : TestiVista3D() {
    override val luciAccese: String get() = "💡 Lumières allumées"
    override val luciSpente: String get() = "💡 Lumières éteintes"
    override val luciAuto: String get() = "💡 Lumières : auto"
    override val vistaDallAlto: String get() = "🏠 Vue de dessus"
    override val cammina: String get() = "🚶 Marcher"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Heure : $h:$p2"
    override val soleSorgeEstDestra: String get() =
        "Le soleil se lève à l'est (à droite du plan) et se couche à l'ouest ; la lumière entre par les fenêtres. " +
            "Quand il fait nuit, les lumières de la maison s'allument."
}
