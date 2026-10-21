package com.sagoma.planimetria.testi

/** Finiture e colori di pareti e pavimenti. */
open class TestiFiniture {
    open val pittura: String get() = "Pittura"
    open val rivestimento: String get() = "Rivestimento"
    open val materiale: String get() = "Materiale"
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
}
