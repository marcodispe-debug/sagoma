package com.sagoma.planimetria.testi

/** Selezione di più oggetti: sposta, duplica, serie, allinea. */
open class TestiSelezione {
    open val selezionaTrascinaVuotoRettangolo: String get() = "☐ Seleziona: trascina sul vuoto per un rettangolo (→ solo ciò che è dentro, ← anche ciò che tocca), tocca per aggiungere o togliere."
    open fun trascinaneSpostarliTutti(n: Any?, p2: Any?): String = "$n $p2 · trascinane uno per spostarli tutti"
    open val specchia: String get() = "⇆ Specchia"
    open val specchia2: String get() = "⇅ Specchia"
    open val sposta: String get() = "Sposta di…"
    open val duplica: String get() = "Duplica…"
    open val copia: String get() = "⧉ Copia"
    open val serie: String get() = "Serie…"
    open val tutto: String get() = "Tutto"
    open val sposta2: String get() = "Sposta di"
    open val duplicaSpostando: String get() = "Duplica spostando di"
    open val serieCopieFila: String get() = "Serie (copie in fila)"
    open val allinea: String get() = "Allinea ▾"
    open val distribuisciOrizzontale: String get() = "Distribuisci in orizzontale"
    open val distribuisciVerticale: String get() = "Distribuisci in verticale"
    open val destraCm: String get() = "→ destra cm"
    open val giuCm: String get() = "↓ giù cm"
    open val numeroCopie: String get() = "Numero di copie"
    open val valoriNegativiVersoSinistra: String get() = "Valori negativi: verso sinistra o verso l'alto."
    open val oggettoSelezionato: String get() = "oggetto selezionato"
    open val oggettiSelezionati: String get() = "oggetti selezionati"
}
