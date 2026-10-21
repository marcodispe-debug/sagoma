package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiFiniture

object TestiFinitureEn : TestiFiniture() {
    override val pittura: String get() = "Paint"
    override val rivestimento: String get() = "Covering"
    override val materiale: String get() = "Material"
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
}
