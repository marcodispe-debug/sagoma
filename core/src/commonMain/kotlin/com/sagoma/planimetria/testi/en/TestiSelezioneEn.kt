package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiSelezione

object TestiSelezioneEn : TestiSelezione() {
    override val selezionaTrascinaVuotoRettangolo: String get() = "☐ Select: drag on empty space for a rectangle (→ only what is inside, ← also what it touches), tap to add or remove."
    override fun trascinaneSpostarliTutti(n: Any?, p2: Any?): String = "$n $p2 · drag one to move them all"
    override val specchia: String get() = "⇆ Mirror"
    override val specchia2: String get() = "⇅ Mirror"
    override val sposta: String get() = "Move by…"
    override val duplica: String get() = "Duplicate…"
    override val copia: String get() = "⧉ Copy"
    override val serie: String get() = "Array…"
    override val tutto: String get() = "All"
    override val sposta2: String get() = "Move by"
    override val duplicaSpostando: String get() = "Duplicate moving by"
    override val serieCopieFila: String get() = "Array (copies in a row)"
    override val allinea: String get() = "Align ▾"
    override val distribuisciOrizzontale: String get() = "Distribute horizontally"
    override val distribuisciVerticale: String get() = "Distribute vertically"
    override val destraCm: String get() = "→ right cm"
    override val giuCm: String get() = "↓ down cm"
    override val numeroCopie: String get() = "Number of copies"
    override val valoriNegativiVersoSinistra: String get() = "Negative values: to the left or upwards."
    override val oggettoSelezionato: String get() = "object selected"
    override val oggettiSelezionati: String get() = "objects selected"
}
