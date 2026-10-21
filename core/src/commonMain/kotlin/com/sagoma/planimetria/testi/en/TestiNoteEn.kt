package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiNote

object TestiNoteEn : TestiNote() {
    override val piccolo: String get() = "Small"
    override val medio: String get() = "Medium"
    override val grande: String get() = "Large"
    override val quotaToccaPrimoPunto: String get() = "↔ Dimension: tap the first point (it snaps to corners and wall faces)"
    override val quotaToccaSecondoPunto: String get() = "↔ Dimension: tap the second point"
    override val tTestoToccaDove: String get() = "T Text: tap where to write it"
    override val nuovoTesto: String get() = "New text"
    override val testoTesto: String get() = "Text"
    override val dimensione: String get() = "Size"
    override val metti: String get() = "Place"
    override fun misuraCmAggiornaSpostando(p1: Any?): String = "Measurement: $p1 cm (it updates when you move the ends)."
    override val scrittaPostoMisuraVuoto: String get() = "Label instead of the measurement (empty = the measurement)"
    override val distanzaLineaCm: String get() = "Line offset (cm)"
    override val dallAltraParte: String get() = "⇅ Other side"
    override val trascinaLineaAllontanarlaEstremita: String get() = "Drag the line to move it away, the ends to measure something else: they snap to corners and wall faces."
    override val verticale: String get() = "↺ Vertical"
    override val orizzontale: String get() = "↻ Horizontal"
    override val freccia: String get() = "↘ Arrow"
    override val togliFreccia: String get() = "Remove arrow"
    override val trascinaTestoSpostarlo: String get() = "Drag the text to move it"
    override val trascinaPuntaFrecciaIndicare: String get() = "; drag the arrow tip to point at a spot."
    override val demolire: String get() = "To demolish"
    override val nuovo: String get() = "New"
    override val livelli: String get() = "Layers"
    override val nascostoNonVedePianta: String get() = "Hidden: not shown on the plan, in the PDF or in the image. Locked: shown but cannot be touched. Rooms, walls, doors and windows are always active."
    override val visibile: String get() = "Visible"
    override val bloccato: String get() = "Locked"
    override val fatto: String get() = "Done"
}
