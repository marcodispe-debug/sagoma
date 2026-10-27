package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.testi.TestiFiniture

object TestiFinitureEn : TestiFiniture() {
    override val pittura: String get() = "Paint"
    override val rivestimento: String get() = "Covering"
    override val materiale: String get() = "Material"
    override fun rivestimentoDiPrima(p1: Any?): String = "Previous covering: $p1"
    override fun colore(p1: Any?): String = "Colour of $p1"
    override val tuttaParete: String get() = "Whole wall"
    override fun altoCmTerra(p1: Any?): String = "$p1 cm high from the floor"
    override val colorePersonalizzato: String get() = "Custom colour"
    override val tinta: String get() = "Hue"
    override val saturazione: String get() = "Saturation"
    override val luminosita: String get() = "Brightness"
    override val codiceEsC9d3bf: String get() = "Code (e.g. C9D3BF)"
    override val usaQuestoColore: String get() = "Use this colour"
    override val nessuno: String get() = "None"
    override fun verso(p1: Any?): String = "Direction: $p1°"
    override fun misuraPezzi(p1: Any?): String = "Piece size: $p1%"
    override val comeNelCatalogo: String get() = "As in the catalogue"
    override val posa: String get() = "Laying"
    override val comeNellaFoto: String get() = "As in the photo"
    override val aPezzi: String get() = "Piece by piece"
    override fun schemaPosa(p: LayingPattern): String = when (p) {
        LayingPattern.Dritta -> "Straight"
        LayingPattern.Sfalsata -> "Offset ½"
        LayingPattern.Terzi -> "Offset ⅓"
        LayingPattern.ACorrere -> "Random"
        LayingPattern.SpinaDiPesce -> "Herringbone"
        LayingPattern.Cesto -> "Basket weave"
    }
    override fun larghezzaPezzo(p1: Any?): String = "Piece width: $p1 cm"
    override fun lunghezzaPezzo(p1: Any?): String = "Piece length: $p1 cm"
    override fun fuga(p1: Any?): String = "Joint: $p1 mm"
    override val coloreFuga: String get() = "Joint colour"
    override val partenza: String get() = "Start"
    override val dalCentro: String get() = "From the centre"
    override val daUnAngolo: String get() = "From a corner"
    override val fotoGiaPosata: String get() = "This photo is already laid (herringbone, hexagons…): you can turn it and change its size, not lay it piece by piece."
    override fun pezziServono(tot: Any?, interi: Any?, tagliati: Any?, m2: Any?): String =
        "About $tot pieces needed: $interi whole and $tagliati to cut, for $m2 m² (plus waste)."
}
