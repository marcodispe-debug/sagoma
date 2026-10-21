package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiPiani

object TestiPianiEs : TestiPiani() {
    override val aggiungiPianoSopra: String get() = "+ Añadir una planta encima"
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
}
