package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.Tip

/** Suggerimenti al primo utilizzo ([Tip]). */
open class Suggerimenti {
    /** Titolo del suggerimento. */
    open fun titolo(v: Tip): String = when (v) {
        Tip.Welcome -> "Benvenuto"
        Tip.Room -> "Stanza selezionata"
        Tip.Wall -> "Muro"
        Tip.NewRoom -> "Nuova stanza"
        Tip.Opening -> "Porte e finestre"
        Tip.Fixture -> "Impianti"
        Tip.Ruler -> "Metro"
        Tip.WallCut -> "Parete sotto il tetto"
        Tip.View3D -> "Vista 3D"
        Tip.Stair -> "Scala"
        Tip.Outdoor -> "Balcone e terrazza"
        Tip.Structure -> "Colonne e travi"
        Tip.Measure -> "Distanza"
        Tip.FreeWall -> "Muro singolo"
        Tip.Floors -> "Piani"
        Tip.Tracing -> "Ricalca una piantina"
        Tip.Calibrate -> "Taratura"
        Tip.WallDraw -> "Muro per muro"
        Tip.Underlay -> "Piantina e disegno"
        Tip.ExcludedZone -> "Zona non calpestabile"
        Tip.RoomInfo -> "Scheda Info"
        Tip.Snap -> "Agganci"
    }

    /** Spiegazione. */
    open fun testo(v: Tip): String = when (v) {
        Tip.Welcome ->
            "Tocca una stanza per selezionarla. Con due dita fai zoom, trascinando il fondo sposti la pianta. " +
                "Se sbagli, ↶ Annulla in alto rimette tutto com'era."
        Tip.Room ->
            "Trascina i pallini blu per spostare gli angoli e i muri per allungarla; trascinando il nome sposti tutta la stanza. " +
                "Per nome, tipo e misure esatte dei lati premi Info o scorri in su la striscia in basso. " +
                "Se gli agganci ti tirano il punto dove non vuoi, spegnili con 🧲 sulla striscia."
        Tip.Wall ->
            "Trascinalo per spostarlo: si aggancia ai muri delle stanze vicine. Da Info cambi l'altezza e, se è in comune " +
                "con un'altra stanza, puoi eliminarlo per unire le due stanze in un unico ambiente."
        Tip.NewRoom ->
            "La stanza nuova nasce attaccata a quella aperta, con il muro in comune. Se va da un'altra parte trascinala dal nome: " +
                "si aggancia ai muri delle stanze vicine."
        Tip.Opening -> "Trascinala lungo il muro per spostarla. Da Info cambi misure, battente o scorrevole, lato del cardine e verso di apertura, oppure la rimuovi."
        Tip.Fixture ->
            "Gli impianti a muro scorrono lungo il muro, le luci si trascinano dove vuoi nella stanza. " +
                "Da Info imposti altezza da terra, misure e rotazione, oppure lo rimuovi."
        Tip.Ruler -> "Trascina le estremità per allungarlo e il centro per spostarlo. Con la maniglia tonda lo ruoti (si aggancia ogni 45°), con × lo elimini."
        Tip.WallCut ->
            "La parete scende in diagonale fino all'altezza del muro basso. Trascina il pallino sul muro, o scrivi la distanza, " +
                "per scegliere da dove inizia a scendere. Il tratteggio mostra dove comincia la pendenza del soffitto; in 3D si vede tutto."
        Tip.View3D ->
            "Trascina per girare intorno alla casa; con due dita (o la rotellina) zoomi. Tocca un muro o un oggetto per selezionarlo. " +
                "Con 🚶 Cammina entri nelle stanze. Tutti i comandi sono in ❔ Guide → Vista 3D."
        Tip.Stair ->
            "Trascinala per spostarla: vicino a un muro ci si accosta da sola. Da Info scegli tipo, larghezza, pedata e da che parte gira, " +
                "e la ruoti di 90°. Numero e altezza dei gradini si calcolano dall'interpiano; al piano di sopra compare il vuoto della scala."
        Tip.Outdoor ->
            "È uno spazio all'aperto: al posto dei muri c'è il parapetto, contro la casa resta il muro della casa. Accostalo a un muro " +
                "e mettici una porta-finestra (Elementi architettonici → Porte-finestre) sul muro della stanza. Da Info scegli ringhiera, muretto o vetro e l'altezza."
        Tip.Structure ->
            "Trascina una colonna per spostarla: vicino a un muro ci si accosta e diventa un pilastro che sporge. Una trave si sposta " +
                "trascinandola e si allunga dai pallini alle estremità, che si appoggiano ai muri. Da Info cambi forma e misure."
        Tip.Measure ->
            "Tocca il primo oggetto e poi il secondo: muri, porte e finestre, termosifoni, prese, luci, colonne, travi, scale. " +
                "Compare la distanza tra i loro bordi più vicini, con la quota sulla pianta; se sposti gli oggetti si aggiorna."
        Tip.FreeWall ->
            "Un tramezzo che non chiude una stanza. Trascinalo per spostarlo e trascina i pallini alle estremità per allungarlo o girarlo: " +
                "le estremità si agganciano alle facce degli altri muri e alle estremità di altri muri singoli, e il muro si raddrizza a 90°. " +
                "Da Info cambi spessore, altezza e colore."
        Tip.Floors ->
            "Con il pulsante del piano, in alto a sinistra, passi da un piano all'altro e ne aggiungi di nuovi. Il piano di sotto si vede " +
                "in grigio per allineare i muri, e le sue scale compaiono come vuoto nel pavimento. In 3D vedi il piano scelto sopra quelli di sotto."
        Tip.Tracing ->
            "Carica la foto o il PDF della piantina, poi tarala su una misura che conosci: dopo clicchi gli angoli delle stanze " +
                "e le misure vengono da sole, come quelle scritte sulla piantina."
        Tip.Calibrate ->
            "Tocca i due estremi di una misura scritta sulla piantina (meglio la più lunga, per esempio una parete intera) e scrivi " +
                "quanti cm sono. Se poi le misure non tornano, \"📏 Rifai taratura\" la corregge anche a metà stanza."
        Tip.WallDraw ->
            "Clicca gli angoli uno dopo l'altro; per finire clicca il primo angolo (cerchiato) o \"Chiudi stanza\". Sopra la piantina " +
                "clicca gli angoli interni dei muri: lo spessore si aggiunge verso l'esterno. ↶ Ultimo toglie l'ultimo angolo, " +
                "\"Scrivi le misure\" fa scegliere direzione e lunghezza di ogni muro."
        Tip.Underlay ->
            "Con il pannello \"◐ Piantina e disegno\", in basso a destra, mostri o nascondi la piantina e il tuo disegno e ne regoli " +
                "la trasparenza, per vedere bene cosa stai ricalcando. Il disegno torna pieno quando riapri il progetto."
        Tip.ExcludedZone ->
            "Clicca gli angoli della parte che non conta nei m², come la zona bassa di una mansarda o un cavedio: gli angoli si " +
                "agganciano a quelli interni dei muri. Alla fine scegli il nome; i m² della stanza scendono, il pavimento resta intero."
        Tip.RoomInfo ->
            "In alto ci sono Duplica, Elimina, nome, tipo e misure principali. Sotto, le schede: Misure (altezze, spessori, muri e zone " +
                "non calpestabili), Finiture (pavimento e pareti), Porte e impianti, Progetto (muri esistenti, da demolire o nuovi). " +
                "La barra a destra fa scorrere il contenuto."
        Tip.Snap ->
            "Con 🧲 Sì angoli, muri e oggetti scattano su angoli, allineamenti, direzioni dritte e muri vicini. Con 🧲 No vanno " +
                "esattamente dove li porti; la stanza si chiude comunque cliccando il primo angolo."
    }
}
