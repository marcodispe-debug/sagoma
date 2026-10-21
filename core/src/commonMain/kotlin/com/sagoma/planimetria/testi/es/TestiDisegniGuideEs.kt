package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiDisegniGuide

object TestiDisegniGuideEs : TestiDisegniGuide() {
    override val si: String get() = "🧲 Sí"
    override val no: String get() = "🧲 No"
    override val camera: String get() = "Dormitorio"
    override val balcone: String get() = "Balcón"
    override val elementi: String get() = "🧱 Elementos"
    override val rampaDritta: String get() = "Tramo recto"
    override val dritta: String get() = "Recta"
    override val chiocciola: String get() = "Caracol"
    override val primoPiano: String get() = "🏠 Primera planta ▾"
    override val pianoTerra: String get() = "🏠 Planta baja ▾"
    override val aggiungiPianoSopra: String get() = "+ Añadir una planta encima"
    override val soggiorno: String get() = "Salón"
    override val info: String get() = "Info ▲"
    override val nomeCamera: String get() = "Nombre · Dormitorio"
    override val tipoCamera: String get() = "Tipo · Dormitorio"
    override val muri400300: String get() = "Paredes · 400 × 300"
    override val lato1: String get() = "Lado 1"
    override val stanza: String get() = "Habitación"
    override val cucina: String get() = "Cocina"
    override val bagno: String get() = "Baño"
    override val porta1Anta: String get() = "Puerta de una hoja"
    override val presaCorrente: String get() = "Enchufe"
    override val stanza2: String get() = "Habitación 2"
    override val annulla: String get() = "↶ Deshacer"
    override val strumenti: String get() = "🛠 Herramientas"
    override fun altezzaMuro(value: Any?): String = "Altura de la pared: $value"
    override val taglioDiagonale: String get() = "Corte diagonal"
    override val muretto: String get() = "murete"
}
