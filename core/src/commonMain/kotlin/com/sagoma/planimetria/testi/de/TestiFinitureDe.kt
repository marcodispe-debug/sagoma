package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.testi.TestiFiniture

object TestiFinitureDe : TestiFiniture() {
    override val pittura: String get() = "Anstrich"
    override val rivestimento: String get() = "Belag"
    override val materiale: String get() = "Material"
    override fun rivestimentoDiPrima(p1: Any?): String = "Bisheriger Belag: $p1"
    override fun colore(p1: Any?): String = "Farbe $p1"
    override val tuttaParete: String get() = "Ganze Wand"
    override fun altoCmTerra(p1: Any?): String = "$p1 cm hoch ab Boden"
    override val colorePersonalizzato: String get() = "Eigene Farbe"
    override val tinta: String get() = "Farbton"
    override val saturazione: String get() = "Sättigung"
    override val luminosita: String get() = "Helligkeit"
    override val codiceEsC9d3bf: String get() = "Code (z. B. C9D3BF)"
    override val usaQuestoColore: String get() = "Diese Farbe verwenden"
    override val nessuno: String get() = "Keiner"
    override fun verso(p1: Any?): String = "Richtung: $p1°"
    override fun misuraPezzi(p1: Any?): String = "Größe der Stücke: $p1 %"
    override val comeNelCatalogo: String get() = "Wie im Katalog"
    override val posa: String get() = "Verlegung"
    override val comeNellaFoto: String get() = "Wie auf dem Foto"
    override val aPezzi: String get() = "In Stücken"
    override fun schemaPosa(p: LayingPattern): String = when (p) {
        LayingPattern.Dritta -> "Kreuzfuge"
        LayingPattern.Sfalsata -> "Versetzt ½"
        LayingPattern.Terzi -> "Versetzt ⅓"
        LayingPattern.ACorrere -> "Wilder Verband"
        LayingPattern.SpinaDiPesce -> "Fischgrät"
        LayingPattern.Cesto -> "Flechtverband"
    }
    override fun larghezzaPezzo(p1: Any?): String = "Breite der Stücke: $p1 cm"
    override fun lunghezzaPezzo(p1: Any?): String = "Länge der Stücke: $p1 cm"
    override fun fuga(p1: Any?): String = "Fuge: $p1 mm"
    override val coloreFuga: String get() = "Fugenfarbe"
    override val partenza: String get() = "Beginn"
    override val dalCentro: String get() = "Von der Mitte"
    override val daUnAngolo: String get() = "Von einer Ecke"
    override val fotoGiaPosata: String get() = "Dieses Foto ist schon verlegt (Fischgrät, Sechsecke…): du kannst es drehen und die Größe ändern, aber nicht in Stücken verlegen."
    override fun pezziServono(tot: Any?, interi: Any?, tagliati: Any?, m2: Any?): String =
        "Du brauchst etwa $tot Stücke: $interi ganze und $tagliati zum Zuschneiden, für $m2 m² (plus Verschnitt)."
}
