package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DEs : TestiVista3D() {
    override val luciAccese: String get() = "💡 Luces encendidas"
    override val luciSpente: String get() = "💡 Luces apagadas"
    override val luciAuto: String get() = "💡 Luces: auto"
    override fun luminositaLuci(pct: Int): String = "💡 Brillo de las luces: $pct%"
    override val vistaDallAlto: String get() = "🏠 Vista superior"
    override val cammina: String get() = "🚶 Caminar"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Hora del día: $h:$p2"
    override val soleSegueBussola: String get() =
        "El sol sigue la ciudad, el día y el norte elegidos con la brújula 🧭 en el plano; la luz entra por las ventanas. " +
            "Cuando oscurece, se encienden las luces de la casa."
    override val nordCitta: String get() = "🧭 Norte y ciudad"
    override val bussola: String get() = "Brújula: arrástrala para girar la N hacia el norte real, tócala para elegir norte y ciudad"
    override val spiegazioneNord: String get() =
        "Gira la flecha hasta que la N apunte al norte real respecto a tu plano. El plano no se mueve: en 3D solo se mueve el sol. Es opcional: si no, el norte está arriba y la ciudad es Roma."
    override val nordGradi: String get() = "Norte (grados en sentido horario desde arriba)"
    override val nordInAlto: String get() = "Norte arriba"
    override val citta: String get() = "Ciudad"
    override val cercaCitta: String get() = "Buscar la ciudad"
    override val nessunaCitta: String get() = "Ninguna (se usa Roma)"
    override fun cittaScelta(nome: Any?): String = "Ciudad: $nome"
    override val giorno: String get() = "Día"
    override val oggi: String get() = "Hoy"
    override val marzo21: String get() = "21 de marzo"
    override val giugno21: String get() = "21 de junio"
    override val settembre21: String get() = "21 de septiembre"
    override val dicembre21: String get() = "21 de diciembre"
}
