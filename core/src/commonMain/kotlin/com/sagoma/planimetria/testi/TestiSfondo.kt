package com.sagoma.planimetria.testi

/** Pianta di sfondo e taratura. */
open class TestiSfondo {
    open val impossibileLeggereFile: String get() = "Impossibile leggere il file"
    open val nonSpazioSalvareSfondo: String get() = "Non c'è spazio per salvare lo sfondo: prova con un'immagine più piccola"
    open val ricalcaPiantina: String get() = "Ricalca una piantina"
    open val piantaSfondo: String get() = "Pianta di sfondo"
    open fun guidaRicalcoPasso(number: Any?, total: Any?): String = "Guida al ricalco · passo $number di $total"
    open val caricaPiantinaHaiFoto: String get() = "Carica la piantina che hai (foto, scansione o PDF del catasto o del progetto)."
    open val caricaPiantinaHaiImmagine: String get() = "Carica la piantina che hai come immagine (foto, scansione, JPG o PNG). Per un PDF salvane la pagina come immagine."
    open val tocchiDueEstremiMisura: String get() =
        "Poi tocchi i due estremi di una misura che conosci e scrivi quanto è lunga: la piantina va in scala. " +
            "Infine ripassi i muri con \"Muro per muro\", angolo dopo angolo."
    open val mettiSottoDisegnoPianta: String get() =
        "Metti sotto al disegno una pianta esistente (foto, scansione o PDF del catasto o del progetto), " +
            "tarala su una misura che conosci e ricalca le stanze sopra."
    open val fotoImmagine: String get() = "Foto / immagine"
    open val altraImmagine: String get() = "Altra immagine"
    open val pdf: String get() = "PDF"
    open val caricamento: String get() = "Caricamento…"
    open val visibile: String get() = "Visibile"
    open fun trasparenza(p1: Any?): String = "Trasparenza: $p1%"
    open fun scalaAttuale1Px(p1: Any?, p2: Any?, p3: Any?): String = "Scala attuale: 1 px = $p1 cm · immagine $p2 × $p3 m"
    open val tara: String get() = "📏 Tara"
    open val sposta: String get() = "✋ Sposta"
    open val togliSfondo: String get() = "Togli lo sfondo"
    open val trascinaDitoSpostarePianta: String get() = "Trascina con un dito per spostare la pianta di sfondo sotto al disegno."
    open val taraturaToccaInizioMisura: String get() = "Taratura: tocca l'inizio di una misura che conosci (es. un muro quotato)."
    open val oraToccaFineMisura: String get() = "Ora tocca la fine della misura. Ingrandisci con due dita per essere preciso."
    open val scriviMisuraRealeB: String get() = "Scrivi la misura reale tra A e B."
    open val misuraReale: String get() = "Misura reale"
    open val quantoLungaDavveroDistanza: String get() = "Quanto è lunga davvero la distanza tra A e B?"
    open val piuMisuraLungaPiu: String get() = "Più la misura è lunga, più la taratura è precisa."
    open val tara2: String get() = "Tara"
    open val rifai: String get() = "Rifai"
    open val leggiDaSola: String get() = "Leggi la piantina da sola"
    open val leggiDaSolaSpiega: String get() = "Novità, gratis: carica la piantina e Sagoma disegna da sola stanze, porte e finestre. Poi controlli e correggi."
    open val letturaInCorso: String get() = "Sto leggendo la piantina… può volerci un minuto."
    open fun piantinaLetta(stanze: Int): String = "Piantina letta: $stanze stanze. Controlla le misure e correggi dove serve."
    open val senzaMisureScritte: String get() = "Sulla piantina non ho trovato misure scritte: la scala è solo indicativa, controllala."
    open val letturaNonRiuscita: String get() = "Non sono riuscito a leggere la piantina: ricalcala a mano."
    open val letturaNonRaggiungibile: String get() = "Il servizio di lettura non risponde (serve internet): ricalca la piantina a mano."
    open val troppeLetture: String get() = "Oggi hai già letto molte piantine: riprova domani o ricalcala a mano."
    open val nonUnaPiantina: String get() = "L'immagine non sembra una piantina: ricalcala a mano o prova un'altra immagine."
}
