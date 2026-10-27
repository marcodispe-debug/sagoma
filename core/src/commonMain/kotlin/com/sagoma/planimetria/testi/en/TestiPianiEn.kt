package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiPiani

object TestiPianiEn : TestiPiani() {
    override val aggiungiPianoSopra: String get() = "+ Add a floor above"
    override val aggiungiPianoSotto: String get() = "+ Add a floor below"
    override fun modifica(name: Any?): String = "✎ Edit «$name»"
    override fun eliminare(name: Any?): String = "Delete $name?"
    override val verrannoTolteTutteStanze: String get() = "All the rooms and stairs on this floor will be removed. You can get it back with ↶ Undo at the top."
    override val nuovoPiano: String get() = "New floor"
    override val modificaPiano: String get() = "Edit floor"
    override val interpianoAutomatico: String get() = "Automatic floor-to-floor height"
    override fun interpianoCm(p1: Any?): String = "Floor-to-floor height: $p1 cm"
    override fun calcolatoAltezzaStanzaPiu(p1: Any?): String = "Calculated as the height of the tallest room on the floor plus $p1 cm of slab: it updates if you change the room heights."
    override val interpiano: String get() = "Floor-to-floor height"
    override fun pavimentoQuestoPianoQuello(p1: Any?): String =
        "From the floor of this level to the floor of the one above: the ceiling height plus the slab thickness (usually $p1 cm). " +
            "It stays as you write it, even if you change the room heights."
    override val scaleQuestoPianoSalgono: String get() = "The stairs on this floor rise by this height."
    override fun partiMuriLiModifichi(p1: Any?): String = "Start from the walls of «$p1» (then edit them)"
    override val eliminaQuestoPiano: String get() = "Delete this floor"
    override val aggiungi: String get() = "Add"
    override val pianoTerra: String get() = "Ground floor"
    override val primoPiano: String get() = "First floor"
    override val secondoPiano: String get() = "Second floor"
    override val terzoPiano: String get() = "Third floor"
    override val quartoPiano: String get() = "Fourth floor"
    override fun piano(index: Any?): String = "Floor $index"
    override fun interrato(n: Int): String = if (n == 1) "Basement" else "Basement $n"

    override val pianiComuni: List<String> get() = listOf("Raised ground floor", "Lower ground floor", "Basement", "Entresol", "Mezzanine")
    override val tettoMenu: String get() = "⌂ Pitched roof…"
    override val tetto: String get() = "Pitched roof"
    override fun tettoSopra(name: Any?): String = "The roof sits on the top floor («$name») and covers the rectangle of its outer walls. All optional: «Flat» removes it."
    override val forma: String get() = "Shape"
    override val tettoPiano: String get() = "Flat"
    override val unaFalda: String get() = "Single slope"
    override val dueFalde: String get() = "Gable"
    override val padiglione: String get() = "Hip"
    override val pendenza: String get() = "Pitch"
    override val sporto: String get() = "Eaves overhang"
    override val giraColmo: String get() = "↻ Turn the ridge"
    override val giraFalda: String get() = "↻ Turn the slope"
    override val manto: String get() = "Covering"
    override val tegole: String get() = "Tiles"
    override val ardesia: String get() = "Slate"
    override val lamiera: String get() = "Metal"
    override fun altezzaColmo(cm: Any?): String = "Ridge $cm cm above the top floor slab."
    override val tettoNel3D: String get() = "In the 3D view from above, «⌂ Roof» puts the roof on and takes it off, to look inside. On the plan of the top floor it is dashed."
    override val nessunaStanza: String get() = "First draw the rooms of the top floor: the roof covers them."
    override val mostraTetto: String get() = "⌂ Roof"
    override val togliTetto: String get() = "⌂ Remove roof"
}
