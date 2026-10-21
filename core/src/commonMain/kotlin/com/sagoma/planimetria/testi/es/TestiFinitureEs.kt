package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiFiniture

object TestiFinitureEs : TestiFiniture() {
    override val pittura: String get() = "Pintura"
    override val rivestimento: String get() = "Revestimiento"
    override val materiale: String get() = "Material"
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
}
