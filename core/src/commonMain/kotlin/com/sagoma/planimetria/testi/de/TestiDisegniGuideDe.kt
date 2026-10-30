package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiDisegniGuide

object TestiDisegniGuideDe : TestiDisegniGuide() {
    override val si: String get() = "🧲 Ja"
    override val no: String get() = "🧲 Nein"
    override val camera: String get() = "Zimmer"
    override val balcone: String get() = "Balkon"
    override val elementi: String get() = "🧱 Elemente"
    override val rampaDritta: String get() = "Gerader Lauf"
    override val dritta: String get() = "Gerade"
    override val chiocciola: String get() = "Wendeltreppe"
    override val primoPiano: String get() = "🏠 1. Obergeschoss ▾"
    override val pianoTerra: String get() = "🏠 Erdgeschoss ▾"
    override val aggiungiPianoSopra: String get() = "+ Geschoss darüber hinzufügen"
    override val soggiorno: String get() = "Wohnzimmer"
    override val info: String get() = "Info ▲"
    override val nomeCamera: String get() = "Name · Zimmer"
    override val tipoCamera: String get() = "Typ · Zimmer"
    override val muri400300: String get() = "Wände · 400 × 300"
    override val lato1: String get() = "Seite 1"
    override val stanza: String get() = "Raum"
    override val cucina: String get() = "Küche"
    override val bagno: String get() = "Bad"
    override val porta1Anta: String get() = "Einflügelige Tür"
    override val presaCorrente: String get() = "Steckdose"
    override val stanza2: String get() = "Raum 2"
    override val annulla: String get() = "↶ Rückgängig"
    override val strumenti: String get() = "🛠 Werkzeuge"
    override fun altezzaMuro(value: Any?): String = "Wandhöhe: $value"
    override val taglioDiagonale: String get() = "Schräger Schnitt"
    override val muretto: String get() = "Kniestock"
}
