package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.TutorialStep

/** Tutorial guidato e guida al ricalco ([TutorialStep]). */
open class TestiTutorial {
    /** Titolo del passo. */
    open fun titolo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro -> "Tutorial"
        TutorialStep.OpenInfo -> "Le informazioni della stanza"
        TutorialStep.WallLengths -> "Cambia le misure"
        TutorialStep.ChangeType -> "Tipo di stanza"
        TutorialStep.MoveWall -> "Sposta un muro"
        TutorialStep.MoveCorner -> "Sposta un angolo"
        TutorialStep.AddDoor -> "Aggiungi una porta"
        TutorialStep.MoveDoor -> "Sposta la porta"
        TutorialStep.AddFixture -> "Impianti"
        TutorialStep.AddRoom -> "Aggiungi una stanza"
        TutorialStep.MoveRoom -> "Accosta le stanze"
        TutorialStep.Undo -> "Annulla"
        TutorialStep.Done -> "Fatto!"
        TutorialStep.TraceLoad -> "Carica la piantina"
        TutorialStep.TraceCalibA -> "Taratura: il primo punto"
        TutorialStep.TraceCalibB -> "Taratura: il secondo punto"
        TutorialStep.TraceCalibValue -> "Taratura: la misura vera"
        TutorialStep.TraceFirstCorner -> "Il primo angolo"
        TutorialStep.TraceCorners -> "Gli altri angoli"
        TutorialStep.TraceClose -> "Chiudi la stanza"
        TutorialStep.TraceCheck -> "Controlla le misure"
        TutorialStep.TraceFade -> "Piantina e disegno"
        TutorialStep.TraceNext -> "La stanza accanto"
        TutorialStep.TraceDone -> "Fatto!"
    }

    /** Cosa fare nel passo. */
    open fun testo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro ->
            "Ti guido passo passo nelle funzioni principali: ogni passo ti chiede di provare tu. " +
                "Sono modifiche vere alla pianta, ma alla fine puoi annullarle con ↶."
        TutorialStep.OpenInfo ->
            "Tocca una stanza per selezionarla: in basso compare la striscia Info. Premi Info, la freccia ▲ " +
                "o scorri la striscia verso l'alto per aprire le sue caratteristiche."
        TutorialStep.WallLengths ->
            "Nella tendina, sotto «Muri», c'è la lunghezza di ogni lato: scrivi un nuovo valore e conferma. " +
                "Il lato opposto si adegua da solo. Poi cambia un lato nell'altra direzione. Le quote sulla pianta si aggiornano."
        TutorialStep.ChangeType -> "Sempre nella tendina, scegli un tipo di stanza, ad esempio Cucina: colore e nome cambiano da soli."
        TutorialStep.MoveWall ->
            "Chiudi la tendina (▼ o scorri in giù). Poi tocca un muro per selezionarlo e trascinalo: " +
                "la stanza si allarga o si stringe, e il muro si aggancia a quelli delle stanze vicine."
        TutorialStep.MoveCorner -> "Trascina uno dei pallini blu agli angoli della stanza: puoi anche storcere i muri."
        TutorialStep.AddDoor -> "Premi «🧱 Elementi architettonici» → Porte, scegli un tipo e poi tocca il muro su cui metterla."
        TutorialStep.MoveDoor -> "Trascina la porta lungo il muro. Da Info ne cambi misure, verso di apertura o la rendi scorrevole."
        TutorialStep.AddFixture -> "Premi «🧱 Elementi architettonici» → Impianti e scegli, ad esempio, Presa di corrente, poi tocca un muro. Le luci invece si mettono toccando un punto dentro la stanza."
        TutorialStep.AddRoom -> "Premi «🧱 Elementi architettonici» → Stanza, scegli forma e misure e conferma."
        TutorialStep.MoveRoom -> "Trascina la nuova stanza tenendola per il nome e avvicinala a un'altra: quando i muri sono vicini si agganciano da soli."
        TutorialStep.Undo -> "Hai sbagliato qualcosa? Premi ↶ Annulla in alto per tornare indietro di un passo (↷ Ripeti lo rifà)."
        TutorialStep.Done -> "Ora conosci le funzioni principali. Da «❔ Guide», in alto a destra, rifai il tutorial quando vuoi e trovi una guida per ogni funzione."
        TutorialStep.TraceLoad -> "Premi «Foto / immagine» (o «PDF» sul computer) e scegli la piantina della casa: catasto, progetto o una foto dritta."
        TutorialStep.TraceCalibA ->
            "Cerca sulla piantina una misura scritta, meglio se lunga (ad esempio un muro quotato 4,72). " +
                "Ingrandisci e tocca l'inizio di quella misura: compare il punto A."
        TutorialStep.TraceCalibB -> "Ora tocca la fine della stessa misura: compare il punto B. Se sbagli, un altro tocco ricomincia da A."
        TutorialStep.TraceCalibValue ->
            "Nella scheda in alto scrivi quanto è lunga davvero la misura tra A e B (in cm, come sulla piantina) e conferma: " +
                "la piantina va in scala."
        TutorialStep.TraceFirstCorner ->
            "Ora sei in «Muro per muro». Scegli una stanza e clicca un suo angolo, sul lato interno del muro: " +
                "lo spessore dei muri si aggiunge da solo verso l'esterno."
        TutorialStep.TraceCorners -> "Clicca gli altri angoli uno dopo l'altro, girando intorno alla stanza. Se sbagli, «↶ Ultimo» toglie l'ultimo angolo."
        TutorialStep.TraceClose ->
            "Quando hai cliccato tutti gli angoli, clicca di nuovo il primo (quello cerchiato) o premi «Chiudi stanza»; " +
                "poi scegli il tipo di stanza e conferma."
        TutorialStep.TraceCheck ->
            "Apri Info della stanza (striscia in basso, ▲ o scorri in su): confronta m² e muri con la piantina. " +
                "Se una parte non conta nei m² (mansarda bassa, cavedio) usa «▧ Aggiungi zona non calpestabile» nella scheda Misure."
        TutorialStep.TraceFade ->
            "Chiudi la tendina e premi «◐ Piantina e disegno» in basso a destra: rendi la piantina più chiara o nascondila " +
                "per vedere bene il disegno."
        TutorialStep.TraceNext -> "Premi «🧱 Elementi architettonici» → Stanza, scegli «Muro per muro» e ricalca la stanza accanto, angolo dopo angolo come prima."
        TutorialStep.TraceDone ->
            "Continua così con le altre stanze. Se gli agganci ti spostano i punti, spegnili con 🧲. " +
                "Con «Fine», se non l'hai ancora fatto, parte il tutorial delle altre funzioni (porte, impianti, misure)."
    }

    /** Avanzamento nel passo «Cambia le misure» (lati di direzioni diverse già cambiati). */
    open fun latiCambiati(n: Int): String =
        if (n == 0) "Lati cambiati: 0 di 2" else "Lati cambiati: 1 di 2 · ora un lato nell'altra direzione"
    open fun angoliServono(n: Int): String = "Angoli cliccati: $n · ne servono almeno 3"
    open fun angoliCliccati(n: Int): String = "Angoli cliccati: $n"
}
