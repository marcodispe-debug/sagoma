package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloEs : TestiControllo() {
    override val voceMenu: String get() = "🔍 Revisar el plano…"
    override val titolo: String get() = "Revisar el plano"
    override val nessunProblema: String get() = "No se encontraron problemas."
    override val cosaControlla: String get() =
        "Busca habitaciones superpuestas o casi pegadas, paredes que se cruzan, puertas y ventanas duplicadas, más anchas o más altas que la pared, " +
            "habitaciones sin puerta, la puerta de entrada y las escaleras."
    override val spiega: String get() = "Pueden estropear superficies, mediciones y 3D. «Mostrar» lleva allí y lo selecciona."
    override val daCorreggere: String get() = "Para corregir"
    override val daControllare: String get() = "Para revisar"
    override val mostra: String get() = "Mostrar"
    override val chiudi: String get() = "Cerrar"
    override fun troppi(n: Int): String = "Solo los primeros $n: corrígelos y revisa de nuevo."
    override fun muriIncrociati(a: String): String = "$a: las paredes se cruzan y el contorno se corta a sí mismo, así que la superficie y el 3D salen mal. Mueve las esquinas."
    override fun sovrapposte(a: String, b: String, m2: String): String = "$a y $b se superponen $m2 m²: esa parte cuenta dos veces. Mueve una pared o una esquina."
    override fun fessura(a: String, b: String, cm: String): String =
        "$a y $b están casi pegadas: entre las paredes queda una rendija de $cm cm. Normalmente la pared es compartida: arrastra esta pared sobre la de la otra habitación."
    override fun accavallati(a: String, b: String, cm: String): String =
        "$a y $b tienen dos paredes que se solapan $cm cm en lugar de una pared compartida: arrastra esta pared sobre la de la otra habitación."
    override fun aperturaDoppia(cosa: String, stanze: String): String = "Dos aberturas en el mismo punto ($cosa, $stanze): quizá puesta dos veces. Quita una."
    override fun muroZero(a: String, unisci: String): String = "$a: dos esquinas una encima de otra (una pared de 0 cm). Con la pared seleccionada, «$unisci» la quita."
    override fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa de $cm cm en una pared de $muro cm, no cabe."
    override fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa llega a $cm cm del suelo, pero allí la pared mide $muro cm."
    override fun suMuroTolto(a: String, cosa: String): String = "$a: $cosa en una pared quitada (lado abierto), que no existe."
    override fun senzaPorta(a: String): String = "$a no tiene puertas ni pasos: ¿por dónde se entra?"
    override val nessunaPorta: String get() = "No hay ninguna puerta en esta planta: añade puertas y pasos entre las habitaciones."
    override val senzaIngresso: String get() = "Ninguna puerta da al exterior: ¿falta la puerta de entrada?"
    override fun finestraInterna(a: String, b: String): String = "$a: la ventana da a $b, no al exterior. ¿Es intencionado?"
    override fun piccolissima(a: String, m2: String): String = "$a es diminuta ($m2 m²): ¿es intencionado o un trozo olvidado?"
    override val scalaSenzaPiano: String get() = "La escalera sube, pero arriba no hay ninguna planta: añade la planta de arriba o quita la escalera."
    override fun scalaFuori(piano: String): String = "La escalera llega fuera de las habitaciones de la planta de arriba ($piano)."
}
