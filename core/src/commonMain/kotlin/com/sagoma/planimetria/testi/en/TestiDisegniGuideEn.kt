package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiDisegniGuide

object TestiDisegniGuideEn : TestiDisegniGuide() {
    override val si: String get() = "🧲 On"
    override val no: String get() = "🧲 Off"
    override val camera: String get() = "Bedroom"
    override val balcone: String get() = "Balcony"
    override val elementi: String get() = "🧱 Elements"
    override val rampaDritta: String get() = "Straight flight"
    override val dritta: String get() = "Straight"
    override val chiocciola: String get() = "Spiral"
    override val primoPiano: String get() = "🏠 First floor ▾"
    override val pianoTerra: String get() = "🏠 Ground floor ▾"
    override val aggiungiPianoSopra: String get() = "+ Add a floor above"
    override val soggiorno: String get() = "Living room"
    override val info: String get() = "Info ▲"
    override val nomeCamera: String get() = "Name · Bedroom"
    override val tipoCamera: String get() = "Type · Bedroom"
    override val muri400300: String get() = "Walls · 400 × 300"
    override val lato1: String get() = "Side 1"
    override val stanza: String get() = "Room"
    override val cucina: String get() = "Kitchen"
    override val bagno: String get() = "Bathroom"
    override val porta1Anta: String get() = "Single door"
    override val presaCorrente: String get() = "Power socket"
    override val stanza2: String get() = "Room 2"
    override val annulla: String get() = "↶ Undo"
    override val strumenti: String get() = "🛠 Tools"
    override fun altezzaMuro(value: Any?): String = "Wall height: $value"
    override val taglioDiagonale: String get() = "Diagonal cut"
    override val muretto: String get() = "low wall"
}
