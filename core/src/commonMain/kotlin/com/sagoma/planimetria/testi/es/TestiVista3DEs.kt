package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DEs : TestiVista3D() {
    override val luciAccese: String get() = "💡 Luces encendidas"
    override val luciSpente: String get() = "💡 Luces apagadas"
    override val luciAuto: String get() = "💡 Luces: auto"
    override val vistaDallAlto: String get() = "🏠 Vista superior"
    override val cammina: String get() = "🚶 Caminar"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Hora del día: $h:$p2"
    override val soleSorgeEstDestra: String get() =
        "El sol sale por el este (a la derecha del plano) y se pone por el oeste; la luz entra por las ventanas. " +
            "Cuando oscurece, se encienden las luces de la casa."
}
