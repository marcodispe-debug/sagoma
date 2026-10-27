package com.sagoma.planimetria.testi

/** Piani dell'edificio. */
open class TestiPiani {
    open val aggiungiPianoSopra: String get() = "+ Aggiungi un piano sopra"
    open val aggiungiPianoSotto: String get() = "+ Aggiungi un piano sotto"
    open fun modifica(name: Any?): String = "✎ Modifica «$name»"
    open fun eliminare(name: Any?): String = "Eliminare $name?"
    open val verrannoTolteTutteStanze: String get() = "Verranno tolte tutte le stanze e le scale di questo piano. Potrai recuperarlo con ↶ Annulla in alto."
    open val nuovoPiano: String get() = "Nuovo piano"
    open val modificaPiano: String get() = "Modifica piano"
    open val interpianoAutomatico: String get() = "Interpiano automatico"
    open fun interpianoCm(p1: Any?): String = "Interpiano: $p1 cm"
    open fun calcolatoAltezzaStanzaPiu(p1: Any?): String = "Calcolato come altezza della stanza più alta del piano più $p1 cm di solaio: si aggiorna se cambi l'altezza delle stanze."
    open val interpiano: String get() = "Interpiano"
    open fun pavimentoQuestoPianoQuello(p1: Any?): String =
        "Dal pavimento di questo piano a quello del piano di sopra: l'altezza del soffitto più lo spessore del solaio (di solito $p1 cm). " +
            "Resta quello che scrivi, anche se cambi l'altezza delle stanze."
    open val scaleQuestoPianoSalgono: String get() = "Le scale di questo piano salgono di questa altezza."
    open fun partiMuriLiModifichi(p1: Any?): String = "Parti dai muri di «$p1» (poi li modifichi)"
    open val eliminaQuestoPiano: String get() = "Elimina questo piano"
    open val aggiungi: String get() = "Aggiungi"
    open val pianoTerra: String get() = "Piano terra"
    open val primoPiano: String get() = "Primo piano"
    open val secondoPiano: String get() = "Secondo piano"
    open val terzoPiano: String get() = "Terzo piano"
    open val quartoPiano: String get() = "Quarto piano"
    open fun piano(index: Any?): String = "Piano $index"
    /** Piano sotto il piano terra: 1 il primo interrato, 2 il secondo… */
    open fun interrato(n: Int): String = if (n == 1) "Piano interrato" else "$n° interrato"

    /**
     * Nomi di piano comuni oltre a quelli automatici ("Seminterrato", "Ammezzato"): cambiando lingua si traducono. Stesso ordine in
     * tutte le lingue. "Mansarda" e "Sottotetto" sono anche nomi di stanza e si traducono da lì.
     */
    open val pianiComuni: List<String> get() = listOf("Piano rialzato", "Seminterrato", "Interrato", "Ammezzato", "Soppalco")

    // Tetto inclinato sopra l'ultimo piano.
    open val tettoMenu: String get() = "⌂ Tetto inclinato…"
    open val tetto: String get() = "Tetto inclinato"
    open fun tettoSopra(name: Any?): String = "Il tetto sta sopra l'ultimo piano («$name») e copre il rettangolo dei suoi muri esterni. Tutto facoltativo: «Piano» lo toglie."
    open val forma: String get() = "Forma"
    open val tettoPiano: String get() = "Piano"
    open val unaFalda: String get() = "Una falda"
    open val dueFalde: String get() = "Due falde"
    open val padiglione: String get() = "Padiglione"
    open val pendenza: String get() = "Pendenza"
    open val sporto: String get() = "Sporto della gronda"
    open val giraColmo: String get() = "↻ Gira il colmo"
    open val giraFalda: String get() = "↻ Gira la falda"
    open val manto: String get() = "Manto"
    open val tegole: String get() = "Tegole"
    open val ardesia: String get() = "Ardesia"
    open val lamiera: String get() = "Lamiera"
    open fun altezzaColmo(cm: Any?): String = "Colmo a $cm cm sopra il solaio dell'ultimo piano."
    open val tettoNel3D: String get() = "Nel 3D dall'alto il tetto si mette e si toglie con «⌂ Tetto», per guardare dentro. In pianta, all'ultimo piano, è tratteggiato."
    open val nessunaStanza: String get() = "Prima disegna le stanze dell'ultimo piano: il tetto le copre."
    open val mostraTetto: String get() = "⌂ Tetto"
    open val togliTetto: String get() = "⌂ Togli tetto"
}
