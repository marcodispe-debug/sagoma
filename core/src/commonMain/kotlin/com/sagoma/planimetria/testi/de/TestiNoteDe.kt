package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiNote

object TestiNoteDe : TestiNote() {
    override val piccolo: String get() = "Klein"
    override val medio: String get() = "Mittel"
    override val grande: String get() = "Groß"
    override val quotaToccaPrimoPunto: String get() = "↔ Maß: tippe auf den ersten Punkt (rastet an Ecken und Wandflächen ein)"
    override val quotaToccaSecondoPunto: String get() = "↔ Maß: tippe auf den zweiten Punkt"
    override val tTestoToccaDove: String get() = "T Text: tippe dorthin, wo er stehen soll"
    override val nuovoTesto: String get() = "Neuer Text"
    override val testoTesto: String get() = "Text"
    override val dimensione: String get() = "Größe"
    override val metti: String get() = "Einfügen"
    override fun misuraCmAggiornaSpostando(p1: Any?): String = "Maß: $p1 cm (passt sich an, wenn du die Enden verschiebst)."
    override val scrittaPostoMisuraVuoto: String get() = "Text statt des Maßes (leer = das Maß)"
    override val distanzaLineaCm: String get() = "Abstand der Linie (cm)"
    override val dallAltraParte: String get() = "⇅ Auf die andere Seite"
    override val trascinaLineaAllontanarlaEstremita: String get() = "Ziehe die Linie, um sie abzurücken, und die Enden, um etwas anderes zu messen: sie rasten an Ecken und Wandflächen ein."
    override val verticale: String get() = "↺ Senkrecht"
    override val orizzontale: String get() = "↻ Waagerecht"
    override val freccia: String get() = "↘ Pfeil"
    override val togliFreccia: String get() = "Pfeil entfernen"
    override val trascinaTestoSpostarlo: String get() = "Ziehe den Text, um ihn zu verschieben"
    override val trascinaPuntaFrecciaIndicare: String get() = "; ziehe die Pfeilspitze, um auf einen Punkt zu zeigen."
    override val demolire: String get() = "Abbruch"
    override val nuovo: String get() = "Neu"
    override val livelli: String get() = "Ebenen"
    override val nascostoNonVedePianta: String get() = "Ausgeblendet: nicht auf dem Grundriss, im PDF oder im Bild zu sehen. Gesperrt: sichtbar, aber nicht veränderbar. Räume, Wände, Türen und Fenster sind immer aktiv."
    override val visibile: String get() = "Sichtbar"
    override val bloccato: String get() = "Gesperrt"
    override val fatto: String get() = "Fertig"
}
