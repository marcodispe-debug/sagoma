package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiPiani

object TestiPianiEn : TestiPiani() {
    override val aggiungiPianoSopra: String get() = "+ Add a floor above"
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
}
