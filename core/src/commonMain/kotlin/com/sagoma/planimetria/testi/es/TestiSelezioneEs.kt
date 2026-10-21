package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiSelezione

object TestiSelezioneEs : TestiSelezione() {
    override val selezionaTrascinaVuotoRettangolo: String get() = "☐ Seleccionar: arrastra en un espacio vacío para un rectángulo (→ solo lo que queda dentro, ← también lo que toca), toca para añadir o quitar."
    override fun trascinaneSpostarliTutti(n: Any?, p2: Any?): String = "$n $p2 · arrastra uno para moverlos todos"
    override val specchia: String get() = "⇆ Reflejar"
    override val specchia2: String get() = "⇅ Reflejar"
    override val sposta: String get() = "Mover…"
    override val duplica: String get() = "Duplicar…"
    override val copia: String get() = "⧉ Copiar"
    override val serie: String get() = "Serie…"
    override val tutto: String get() = "Todo"
    override val sposta2: String get() = "Mover"
    override val duplicaSpostando: String get() = "Duplicar moviendo"
    override val serieCopieFila: String get() = "Serie (copias en fila)"
    override val allinea: String get() = "Alinear ▾"
    override val distribuisciOrizzontale: String get() = "Distribuir en horizontal"
    override val distribuisciVerticale: String get() = "Distribuir en vertical"
    override val destraCm: String get() = "→ derecha cm"
    override val giuCm: String get() = "↓ abajo cm"
    override val numeroCopie: String get() = "Número de copias"
    override val valoriNegativiVersoSinistra: String get() = "Valores negativos: hacia la izquierda o hacia arriba."
    override val oggettoSelezionato: String get() = "objeto seleccionado"
    override val oggettiSelezionati: String get() = "objetos seleccionados"
}
