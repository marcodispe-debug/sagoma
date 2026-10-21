package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.Tip

/** Miniguide del pulsante «❔ Guide»: titoli delle guide e passi scritti apposta (gli altri passi riusano tutorial e suggerimenti). */
open class TestiGuide {
    open val titoloMansarda: String get() = "Mansarda"
    open val titoloRicalco: String get() = "Ricalca una piantina"
    open val titoloStanze: String get() = "Stanze e misure"
    open val titoloZone: String get() = "Zone non calpestabili"
    open val titoloMuri: String get() = "Muri e angoli"
    open val titoloAgganci: String get() = "Agganci"
    open val titoloPorte: String get() = "Porte e finestre"
    open val titoloImpianti: String get() = "Impianti"
    open val titoloPiuStanze: String get() = "Più stanze"
    open val titoloBalconi: String get() = "Balconi e terrazze"
    open val titoloScale: String get() = "Scale e piani"
    open val titoloVista3D: String get() = "Vista 3D"
    open val titoloQuote: String get() = "Quote e testi"

    open fun passoDi(numero: Int, totale: Int): String = "Passo $numero di $totale"
    open val apriVista3D: String get() = "Apri la vista 3D"

    open val mansardaIntro: Passo get() = Passo(
        "Com'è fatta una mansarda",
        "In una mansarda il soffitto scende verso un muro più basso (il \"muretto\" sotto la falda del tetto). " +
            "Le pareti laterali quindi non sono rettangolari: sono alte fino a un certo punto e poi si abbassano in " +
            "diagonale. In questa app si ottiene in due mosse: abbassi il muretto e tagli in diagonale le pareti accanto.",
    )

    open val mansardaStanza: Passo get() = Passo(
        "1 · Disegna la stanza",
        "Crea la stanza con «🧱 Elementi architettonici» → Stanza e sistema le misure dal pannello Info, come per qualsiasi altra stanza. " +
            "L'altezza del soffitto della stanza è quella della parte più alta della mansarda.",
    )

    open val mansardaMuretto: Passo get() = Passo(
        "2 · Abbassa il muretto",
        "Tocca il muro verso cui scende il tetto, apri Info e scrivi la sua altezza reale in \"Altezza muro\" " +
            "(per esempio 120 cm). Sulla pianta compare l'altezza in viola accanto al muro.",
    )

    open val mansardaTaglio: Passo get() = Passo(
        "3 · Taglia la prima parete laterale",
        "Tocca una delle due pareti perpendicolari al muretto e, nel pannello Info, attiva \"Taglio diagonale (sottotetto)\". " +
            "Se il muretto è più basso, la parete ora scende fino alla sua altezza. Se entrambi i muri accanto sono bassi, " +
            "scegli in \"Scende verso\" da che parte.",
    )

    open val mansardaInizio: Passo get() = Passo(
        "4 · Scegli dove inizia a scendere",
        "Misura in casa a che distanza dal muretto il soffitto comincia a inclinarsi e scrivila in \"Inizia a scendere a\". " +
            "In alternativa trascina il pallino arancione sulla pianta: la linea tratteggiata segna l'inizio della discesa.",
    )

    open val mansardaRipeti: Passo get() = Passo(
        "5 · Ripeti sull'altra parete",
        "Fai lo stesso sulla parete di fronte, così il soffitto scende in modo uguale su tutta la stanza. " +
            "Se la parete è in comune con un'altra stanza, viene tagliato solo il lato della mansarda: la stanza accanto resta intera.",
    )

    open val mansardaControllo: Passo get() = Passo(
        "6 · Controlla porte, finestre e impianti",
        "Se una finestra, una porta, un termosifone o una presa finisce più in alto della parete inclinata, compare un " +
            "pallino rosso con \"!\" sulla pianta e un avviso nel pannello Info: abbassa l'oggetto o spostalo lungo il muro " +
            "verso la parte alta.",
    )

    open val mansarda3D: Passo get() = Passo(
        "7 · Guarda il risultato in 3D",
        "Apri la vista 3D: il bordo delle pareti e il soffitto scendono in diagonale e puoi camminare dentro la mansarda " +
            "per controllare che tutto torni. Il volume della stanza nel pannello Info tiene già conto del soffitto inclinato.",
    )

    open val caricaPiantina: Passo get() = Passo(
        "Carica la piantina",
        "In un progetto nuovo scegli «Ricalca una piantina»; in un progetto già iniziato usa ⋮ → «Pianta di sfondo…». " +
            "Poi «Foto / immagine» (o «PDF» sul computer): catasto, progetto o una foto dritta.",
    )

    open val ricalcaAngoli: Passo get() = Passo(
        "Ricalca gli angoli",
        "Si apre «Muro per muro». " + Tip.WallDraw.text + " Per la stanza dopo: «🧱 Elementi architettonici» → Stanza → «Muro per muro».",
    )

    open val controllaMisure: Passo get() = Passo(
        "Controlla le misure",
        "Apri Info della stanza e confronta m² e muri con quelli scritti sulla piantina. Se una parte non conta nei m² " +
            "(mansarda bassa, cavedio) aggiungi una zona non calpestabile.",
    )

    open val muoversi: Passo get() = Passo(
        "Muoversi nella pianta",
        Tip.Welcome.text,
    )

    open val aggiungiZona: Passo get() = Passo(
        "Aggiungi una zona",
        "Seleziona la stanza, apri Info e, nella scheda Misure, premi «▧ Aggiungi zona non calpestabile».",
    )

    open val agganci: Passo get() = Passo(
        "Accendi e spegni gli agganci",
        "Il pulsante 🧲 sta sulla striscia Info della stanza e nella scheda di «Muro per muro». " + Tip.Snap.text,
    )

    open val aggiungiBalcone: Passo get() = Passo(
        "Aggiungi un balcone o una terrazza",
        "Premi «🧱 Elementi architettonici» → Balconi e terrazze e scegli il tipo, poi forma e misure come per una stanza. Nasce già appoggiato alla casa: " +
            "trascinalo dal nome lungo il muro fino al punto giusto, si aggancia da solo. Sui lati verso l'esterno c'è il parapetto, " +
            "contro la casa resta il muro.",
    )

    open val parapetto: Passo get() = Passo(
        "Parapetto e porta-finestra",
        Tip.Outdoor.text,
    )

    open val aggiungiScala: Passo get() = Passo(
        "Aggiungi una scala",
        "Premi «🧱 Elementi architettonici» → Scale e scegli il tipo: rampa dritta, a L con pianerottolo, a U con due rampe o a chiocciola. " +
            "La scala compare al centro della vista (o della stanza selezionata): trascinala dove serve, vicino a un muro ci si accosta da sola.",
    )

    open val opzioniScala: Passo get() = Passo(
        "Tipo, misure e verso",
        "Tocca la scala e apri Info: cambi tipo, larghezza, pedata (o diametro della chiocciola), da che parte gira e la rotazione, " +
            "oppure la giri di 90° con ↻. Numero e altezza dei gradini si calcolano dall'interpiano; se i gradini sono scomodi compare un avviso.",
    )

    open val aggiungiPiano: Passo get() = Passo(
        "Aggiungi un piano",
        "Tocca il pulsante del piano in alto a sinistra e scegli «Aggiungi un piano sopra». Puoi partire dai muri del piano di sotto " +
            "o da un piano vuoto. Il piano di sotto resta visibile in grigio e le sue scale compaiono come vuoto nel pavimento.",
    )

    open val piani3D: Passo get() = Passo(
        "I piani in 3D",
        "In 3D vedi il piano scelto sopra quelli di sotto, collegati dalle scale; i piani più alti si nascondono, come se si " +
            "togliesse il tetto. Dallo stesso menu del piano cambi nome e interpiano, o lo elimini.",
    )

    open val comandi3D: Passo get() = Passo(
        "Tutti i comandi",
        "Telefono: un dito ruota, due dita zoomano e spostano; un muro, una porta o un impianto selezionato si trascina per " +
            "spostarlo. In 🚶 Cammina ti muovi col joystick in basso a sinistra. Computer: ↑ ↓ (o W S) avanti e indietro, ← → per " +
            "girare, A D per spostarti di lato, PagSu/PagGiù per alzare o abbassare lo sguardo, Maiusc per andare più veloce; " +
            "rotellina per lo zoom, tasto destro trascinato per spostare la vista.",
    )

    open val quota: Passo get() = Passo(
        "Aggiungi una quota",
        "Premi «🛠 Strumenti» → Quota e tocca i due estremi: i punti si agganciano ad angoli e facce dei muri. La quota resta " +
            "sulla pianta ed esce nelle tavole; toccala per spostarla o eliminarla.",
    )

    open val testo: Passo get() = Passo(
        "Scrivi un testo",
        "Premi «🛠 Strumenti» → Testo, tocca dove scriverlo, poi scrivi e scegli la grandezza.",
    )
}
