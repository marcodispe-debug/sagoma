package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiPiani

object TestiPianiEs : TestiPiani() {
    override val aggiungiPianoSopra: String get() = "+ Añadir una planta encima"
    override val aggiungiPianoSotto: String get() = "+ Añadir una planta debajo"
    override fun modifica(name: Any?): String = "✎ Editar «$name»"
    override fun eliminare(name: Any?): String = "¿Eliminar $name?"
    override val verrannoTolteTutteStanze: String get() = "Se quitarán todas las habitaciones y escaleras de esta planta. Puedes recuperarla con ↶ Deshacer arriba."
    override val nuovoPiano: String get() = "Nueva planta"
    override val modificaPiano: String get() = "Editar planta"
    override val interpianoAutomatico: String get() = "Altura entre plantas automática"
    override fun interpianoCm(p1: Any?): String = "Altura entre plantas: $p1 cm"
    override fun calcolatoAltezzaStanzaPiu(p1: Any?): String = "Calculada como la altura de la habitación más alta de la planta más $p1 cm de forjado: se actualiza si cambias la altura de las habitaciones."
    override val interpiano: String get() = "Altura entre plantas"
    override fun pavimentoQuestoPianoQuello(p1: Any?): String =
        "Del suelo de esta planta al suelo de la de arriba: la altura del techo más el grosor del forjado (normalmente $p1 cm). " +
            "Se queda como la escribes, aunque cambies la altura de las habitaciones."
    override val scaleQuestoPianoSalgono: String get() = "Las escaleras de esta planta suben esta altura."
    override fun partiMuriLiModifichi(p1: Any?): String = "Partir de las paredes de «$p1» (y luego editarlas)"
    override val eliminaQuestoPiano: String get() = "Eliminar esta planta"
    override val aggiungi: String get() = "Añadir"
    override val pianoTerra: String get() = "Planta baja"
    override val primoPiano: String get() = "Primera planta"
    override val secondoPiano: String get() = "Segunda planta"
    override val terzoPiano: String get() = "Tercera planta"
    override val quartoPiano: String get() = "Cuarta planta"
    override fun piano(index: Any?): String = "Planta $index"
    override fun interrato(n: Int): String = if (n == 1) "Sótano" else "Sótano $n"

    override val pianiComuni: List<String> get() = listOf("Planta baja elevada", "Semisótano", "Sótano", "Entresuelo", "Altillo")
    override val tettoMenu: String get() = "⌂ Tejado inclinado…"
    override val tetto: String get() = "Tejado inclinado"
    override fun tettoSopra(name: Any?): String = "El tejado está sobre la última planta («$name») y sigue la forma de sus muros exteriores (también en L, en T o en U; con muros inclinados cubre el rectángulo que los contiene). Todo opcional: «Plano» lo quita."
    override val forma: String get() = "Forma"
    override val tettoPiano: String get() = "Plano"
    override val unaFalda: String get() = "Una vertiente"
    override val dueFalde: String get() = "Dos aguas"
    override val padiglione: String get() = "Cuatro aguas"
    override val pendenza: String get() = "Pendiente"
    override val sporto: String get() = "Vuelo del alero"
    override val giraColmo: String get() = "↻ Girar la cumbrera"
    override val giraFalda: String get() = "↻ Girar la vertiente"
    override val manto: String get() = "Cubierta"
    override val tegole: String get() = "Tejas"
    override val ardesia: String get() = "Pizarra"
    override val lamiera: String get() = "Chapa"
    override fun altezzaColmo(cm: Any?): String = "Cumbrera a $cm cm sobre el forjado de la última planta."
    override val tettoNel3D: String get() = "En la vista 3D desde arriba, «⌂ Tejado» pone y quita el tejado, para mirar dentro. En el plano de la última planta aparece discontinuo."
    override val nessunaStanza: String get() = "Primero dibuja las habitaciones de la última planta: el tejado las cubre."
    override val mostraTetto: String get() = "⌂ Tejado"
    override val togliTetto: String get() = "⌂ Quitar tejado"
}
