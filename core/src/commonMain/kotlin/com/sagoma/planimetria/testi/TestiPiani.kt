package com.sagoma.planimetria.testi

/** Piani dell'edificio. */
open class TestiPiani {
    open val aggiungiPianoSopra: String get() = "+ Aggiungi un piano sopra"
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
}
