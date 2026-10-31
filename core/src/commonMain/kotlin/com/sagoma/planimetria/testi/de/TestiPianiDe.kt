package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiPiani

object TestiPianiDe : TestiPiani() {
    override val aggiungiPianoSopra: String get() = "+ Geschoss darüber hinzufügen"
    override val aggiungiPianoSotto: String get() = "+ Geschoss darunter hinzufügen"
    override fun modifica(name: Any?): String = "✎ „$name“ bearbeiten"
    override fun eliminare(name: Any?): String = "$name löschen?"
    override val verrannoTolteTutteStanze: String get() = "Alle Räume und Treppen dieses Geschosses werden entfernt. Mit ↶ Rückgängig oben kannst du es zurückholen."
    override val nuovoPiano: String get() = "Neues Geschoss"
    override val modificaPiano: String get() = "Geschoss bearbeiten"
    override val interpianoAutomatico: String get() = "Geschosshöhe automatisch"
    override fun interpianoCm(p1: Any?): String = "Geschosshöhe: $p1 cm"
    override fun calcolatoAltezzaStanzaPiu(p1: Any?): String = "Berechnet als Höhe des höchsten Raums im Geschoss plus $p1 cm Decke: passt sich an, wenn du die Raumhöhe änderst."
    override val interpiano: String get() = "Geschosshöhe"
    override fun pavimentoQuestoPianoQuello(p1: Any?): String =
        "Vom Boden dieses Geschosses bis zum Boden des Geschosses darüber: Raumhöhe plus Deckenstärke (meist $p1 cm). " +
            "Der eingegebene Wert bleibt, auch wenn du die Raumhöhe änderst."
    override val scaleQuestoPianoSalgono: String get() = "Die Treppen dieses Geschosses steigen um diese Höhe."
    override fun partiMuriLiModifichi(p1: Any?): String = "Mit den Wänden von „$p1“ beginnen (danach änderst du sie)"
    override val eliminaQuestoPiano: String get() = "Dieses Geschoss löschen"
    override val aggiungi: String get() = "Hinzufügen"
    override val pianoTerra: String get() = "Erdgeschoss"
    override val primoPiano: String get() = "1. Obergeschoss"
    override val secondoPiano: String get() = "2. Obergeschoss"
    override val terzoPiano: String get() = "3. Obergeschoss"
    override val quartoPiano: String get() = "4. Obergeschoss"
    override fun piano(index: Any?): String = "$index. Obergeschoss"
    override fun interrato(n: Int): String = if (n == 1) "Untergeschoss" else "$n. Untergeschoss"

    override val pianiComuni: List<String> get() = listOf("Hochparterre", "Souterrain", "Kellergeschoss", "Zwischengeschoss", "Galerie")
}
