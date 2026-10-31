package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiSelezione

object TestiSelezioneDe : TestiSelezione() {
    override val selezionaTrascinaVuotoRettangolo: String get() = "☐ Auswählen: auf einer leeren Stelle ein Rechteck ziehen (→ nur was darin liegt, ← auch was es berührt), tippen zum Hinzufügen oder Entfernen."
    override fun trascinaneSpostarliTutti(n: Any?, p2: Any?): String = "$n $p2 · eines ziehen, um alle zu verschieben"
    override val specchia: String get() = "⇆ Spiegeln"
    override val specchia2: String get() = "⇅ Spiegeln"
    override val sposta: String get() = "Verschieben um…"
    override val duplica: String get() = "Duplizieren…"
    override val copia: String get() = "⧉ Kopieren"
    override val serie: String get() = "Reihe…"
    override val tutto: String get() = "Alles"
    override val sposta2: String get() = "Verschieben um"
    override val duplicaSpostando: String get() = "Duplizieren mit Versatz"
    override val serieCopieFila: String get() = "Reihe (Kopien hintereinander)"
    override val allinea: String get() = "Ausrichten ▾"
    override val distribuisciOrizzontale: String get() = "Waagerecht verteilen"
    override val distribuisciVerticale: String get() = "Senkrecht verteilen"
    override val destraCm: String get() = "→ rechts cm"
    override val giuCm: String get() = "↓ unten cm"
    override val numeroCopie: String get() = "Anzahl der Kopien"
    override val valoriNegativiVersoSinistra: String get() = "Negative Werte: nach links oder nach oben."
    override val oggettoSelezionato: String get() = "Objekt ausgewählt"
    override val oggettiSelezionati: String get() = "Objekte ausgewählt"
}
