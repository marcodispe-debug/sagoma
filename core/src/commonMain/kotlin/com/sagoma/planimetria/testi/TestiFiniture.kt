package com.sagoma.planimetria.testi

import com.sagoma.planimetria.model.LayingPattern

/** Finiture e colori di pareti e pavimenti. */
open class TestiFiniture {
    open val pittura: String get() = "Pittura"
    open val rivestimento: String get() = "Rivestimento"
    open val materiale: String get() = "Materiale"
    open fun rivestimentoDiPrima(p1: Any?): String = "Rivestimento di prima: $p1"
    open fun colore(p1: Any?): String = "Colore $p1"
    open val tuttaParete: String get() = "Tutta la parete"
    open fun altoCmTerra(p1: Any?): String = "Alto $p1 cm da terra"
    open val colorePersonalizzato: String get() = "Colore personalizzato"
    open val tinta: String get() = "Tinta"
    open val saturazione: String get() = "Saturazione"
    open val luminosita: String get() = "Luminosità"
    open val codiceEsC9d3bf: String get() = "Codice (es. C9D3BF)"
    open val usaQuestoColore: String get() = "Usa questo colore"
    open val nessuno: String get() = "Nessuno"
    open fun verso(p1: Any?): String = "Verso: $p1°"
    open fun misuraPezzi(p1: Any?): String = "Misura dei pezzi: $p1%"
    open val comeNelCatalogo: String get() = "Come nel catalogo"
    open val posa: String get() = "Posa"
    open val comeNellaFoto: String get() = "Come nella foto"
    open val aPezzi: String get() = "A pezzi"
    open fun schemaPosa(p: LayingPattern): String = when (p) {
        LayingPattern.Dritta -> "Dritta"
        LayingPattern.Sfalsata -> "Sfalsata ½"
        LayingPattern.Terzi -> "Sfalsata ⅓"
        LayingPattern.ACorrere -> "A correre"
        LayingPattern.SpinaDiPesce -> "Spina di pesce"
        LayingPattern.Cesto -> "A cesto"
    }
    open fun larghezzaPezzo(p1: Any?): String = "Larghezza dei pezzi: $p1 cm"
    open fun lunghezzaPezzo(p1: Any?): String = "Lunghezza dei pezzi: $p1 cm"
    open fun fuga(p1: Any?): String = "Fuga: $p1 mm"
    open val coloreFuga: String get() = "Colore della fuga"
    open val partenza: String get() = "Si parte"
    open val dalCentro: String get() = "Dal centro"
    open val daUnAngolo: String get() = "Da un angolo"
    open val fotoGiaPosata: String get() = "Questa foto è già posata (spina di pesce, esagonette…): puoi girarla e cambiarne la misura, non posarla a pezzi."
    open fun pezziServono(tot: Any?, interi: Any?, tagliati: Any?, m2: Any?): String =
        "Servono circa $tot pezzi: $interi interi e $tagliati da tagliare, per $m2 m² (più lo sfrido)."
}
