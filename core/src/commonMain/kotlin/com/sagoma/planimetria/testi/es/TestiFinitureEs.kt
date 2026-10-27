package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.testi.TestiFiniture

object TestiFinitureEs : TestiFiniture() {
    override val pittura: String get() = "Pintura"
    override val rivestimento: String get() = "Revestimiento"
    override val materiale: String get() = "Material"
    override fun rivestimentoDiPrima(p1: Any?): String = "Revestimiento anterior: $p1"
    override fun colore(p1: Any?): String = "Color ($p1)"
    override val tuttaParete: String get() = "Toda la pared"
    override fun altoCmTerra(p1: Any?): String = "Hasta $p1 cm del suelo"
    override val colorePersonalizzato: String get() = "Color personalizado"
    override val tinta: String get() = "Tono"
    override val saturazione: String get() = "Saturación"
    override val luminosita: String get() = "Brillo"
    override val codiceEsC9d3bf: String get() = "Código (ej. C9D3BF)"
    override val usaQuestoColore: String get() = "Usar este color"
    override val nessuno: String get() = "Ninguno"
    override fun verso(p1: Any?): String = "Dirección: $p1°"
    override fun misuraPezzi(p1: Any?): String = "Tamaño de las piezas: $p1 %"
    override val comeNelCatalogo: String get() = "Como en el catálogo"
    override val posa: String get() = "Colocación"
    override val comeNellaFoto: String get() = "Como en la foto"
    override val aPezzi: String get() = "Pieza a pieza"
    override fun schemaPosa(p: LayingPattern): String = when (p) {
        LayingPattern.Dritta -> "Recta"
        LayingPattern.Sfalsata -> "Trabada ½"
        LayingPattern.Terzi -> "Trabada ⅓"
        LayingPattern.ACorrere -> "A la inglesa"
        LayingPattern.SpinaDiPesce -> "Espiga"
        LayingPattern.Cesto -> "Cestería"
    }
    override fun larghezzaPezzo(p1: Any?): String = "Ancho de las piezas: $p1 cm"
    override fun lunghezzaPezzo(p1: Any?): String = "Largo de las piezas: $p1 cm"
    override fun fuga(p1: Any?): String = "Junta: $p1 mm"
    override val coloreFuga: String get() = "Color de la junta"
    override val partenza: String get() = "Inicio"
    override val dalCentro: String get() = "Desde el centro"
    override val daUnAngolo: String get() = "Desde una esquina"
    override val fotoGiaPosata: String get() = "Esta foto ya está colocada (espiga, hexágonos…): puedes girarla y cambiar su tamaño, no colocarla pieza a pieza."
    override fun pezziServono(tot: Any?, interi: Any?, tagliati: Any?, m2: Any?): String =
        "Hacen falta unas $tot piezas: $interi enteras y $tagliati para cortar, para $m2 m² (más los recortes)."
}
