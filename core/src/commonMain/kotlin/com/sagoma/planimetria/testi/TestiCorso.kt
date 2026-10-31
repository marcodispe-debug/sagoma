package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.StairKind

/**
 * Guida interattiva ([com.sagoma.planimetria.editor.Course]): le lezioni, le schede da leggere e le indicazioni passo passo.
 * I nomi di pulsanti e voci di menu si prendono dai loro testi, così restano uguali a quelli dell'app in ogni lingua.
 */
open class TestiCorso {
    protected val e get() = T.editor
    protected val cr get() = T.creazione

    // ---------- Lezioni e schede ----------

    open fun lezione(l: Lesson): String = when (l) {
        Lesson.Square -> cr.quadrato
        Lesson.Rectangle -> cr.rettangolo
        Lesson.LShape -> "Forma a L"
        Lesson.Survey -> cr.rilievo
        Lesson.Walls -> cr.muroMuro
        Lesson.FreeWall -> cr.muroSingolo
        Lesson.Elements -> "Porte, finestre e altro"
        Lesson.Check -> "Controlla la pianta"
        Lesson.Sun -> "Il sole e la bussola"
        Lesson.OverPlan -> "Sopra la tua planimetria"
    }

    open fun intestazione(n: Int, tot: Int, nome: String): String = "Guida · Lezione $n di $tot · $nome"
    open fun passo(n: Int, tot: Int): String = "Passo $n di $tot"

    /** Titolo e testo delle schede da leggere (passi senza azione). */
    open fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Benvenuto in Sagoma!"
        CourseStep.ElementsDone -> "Ogni elemento, da «${T.schedaStanza.info}»"
        CourseStep.Finished -> "Hai finito la guida!"
        CourseStep.Snap -> "Spostare le stanze e il gancio 🧲"
        CourseStep.InnerFace -> "Il lato interno dei muri"
        CourseStep.SunDone -> "Il sole della tua casa"
        else -> "Fatto!"
    }

    open fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Ti guido passo passo a disegnare la casa di questa piantina inventata, in ${Lesson.entries.count { it.inCourse }} lezioni brevi: una stanza alla volta, " +
                "ognuna al suo posto come nel disegno. Le azioni le fai tu: a ogni passo ti indico con un riquadro arancione cosa " +
                "cliccare. È un progetto solo per fare pratica: i tuoi progetti non si toccano."
        CourseStep.SquareDone ->
            "Lo studio è pronto: 3,30 × 3,30 m. Hai visto tutti i modi per cambiare forma a una stanza: le misure quando la crei, i " +
                "muri e gli angoli trascinati, le misure esatte in «${T.schedaStanza.info}» e «${e.annulla}» per tornare indietro. " +
                "Nella prossima lezione: il soggiorno a L, sopra lo studio."
        CourseStep.Snap ->
            "Il soggiorno è nato staccato dalla casa. Una stanza si sposta trascinandola dal suo nome. Il gancio «${T.schedaStanza.si}», " +
                "qui in basso, è acceso: quando porti la stanza vicino a un'altra, scatta e si attacca da sola, angolo su angolo, e il " +
                "muro tra le due diventa uno solo. Spento («${T.schedaStanza.no}»), la stanza resta esattamente dove la lasci. Lascialo acceso."
        CourseStep.LDone ->
            "Il soggiorno è attaccato sopra lo studio, come sulla piantina: il muro tra le due stanze ora è uno solo, in comune. " +
                "Nella prossima lezione: la camera, un rettangolo a destra del soggiorno."
        CourseStep.RectDone ->
            "La camera è attaccata a destra del soggiorno. Nella prossima lezione: il bagno, sotto la camera, che è un po' fuori squadra."
        CourseStep.SurveyDone ->
            "Il bagno è sotto la camera, con la sua forma vera: un po' fuori squadra, come nella realtà. Nella prossima lezione: " +
                "l'ingresso, disegnato muro per muro nello spazio che resta tra le stanze."
        CourseStep.WallsDone ->
            "L'ingresso chiude la casa: ora tutte le stanze sono collegate, come sulla piantina. Hai visto tutti i modi per creare " +
                "una stanza. Nella prossima lezione: il muro singolo, un muro da solo che non chiude una stanza."
        CourseStep.FreeWallDone ->
            "La camera ora ha la sua cabina armadio, divisa da un tramezzo con il passaggio in fondo. Nella scheda «${T.schedaStanza.info}» " +
                "del muro singolo scegli anche l'altezza (fino al soffitto o più bassa, come un muretto), il colore e la curva; e anche " +
                "ai muri singoli puoi aggiungere porte e finestre. Nella prossima lezione: porte, finestre, balcone, impianti e scale."
        CourseStep.ElementsDone ->
            "Nella scheda «${T.schedaStanza.info}» cambi misure, tipo e tutto il resto dell'elemento selezionato; si chiude con «▼». " +
                "Per ogni elemento: lo scegli da «${e.elementiArchitettonici}», lo metti sulla pianta e il resto da «${T.schedaStanza.info}». " +
                "Nella prossima lezione: il controllo della pianta, che trova da solo gli errori di disegno e molti li sistema."
        CourseStep.CheckDone ->
            "Ora sai usare il controllo della pianta: l'elenco completo dal menu «⋮», «✓ ${T.controllo.correggi}» e «✓ Correggi tutti» che " +
                "sistemano da soli («${e.annulla}» torna indietro), i «!» sulla pianta per vedere cosa c'è in un punto e «${T.controllo.togliDallaPianta}» " +
                "per quelli che vanno bene così (dall'elenco si rimettono). Prima di esportare, Sagoma ti avvisa se ci sono ancora errori di disegno. " +
                "Ultima lezione: costruire le stanze sopra la tua planimetria. Serve un progetto nuovo: lo preparo io."
        CourseStep.Finished ->
            "Ecco camera, bagno e soggiorno sopra la piantina, con le misure giuste. Con le altre stanze fai lo stesso: una forma per " +
                "partire, attaccata alle stanze accanto, poi muri e angoli al loro posto e le misure esatte in «${T.schedaStanza.info}». " +
                "Ora puoi disegnare la tua casa! La guida si rifà quando vuoi da «${e.guide}»."
        CourseStep.SunDone ->
            "Fatto. Il nord e la città restano nel progetto e li cambi quando vuoi dalla bussola o dal menu ⋮; giorno e ora servono " +
                "solo per guardare. Niente di tutto questo è obbligatorio: quello che non metti resta com'era (nord in alto, Roma, oggi)."
        CourseStep.InnerFace ->
            "Prima di spostare la camera, una cosa importante. Sagoma misura le stanze dal lato interno dei muri, come le misure " +
                "scritte sulla piantina: lo spessore del muro va verso l'esterno. Quando metti una stanza sopra la piantina, fai " +
                "combaciare il più possibile il lato interno dei muri disegnati con il lato interno dei muri della piantina: guarda la " +
                "linea interna del muro, non il centro né il lato esterno. Così misure, metri quadri e stanze accanto vengono giusti."
        else -> ""
    }

    open val avanti: String get() = "Avanti"
    open val apriProgettoNuovo: String get() = "Apri il progetto nuovo"
    open val bastaCosi: String get() = "Basta così"
    open val vaiAiProgetti: String get() = "Vai ai tuoi progetti"

    /** Nessuna indicazione da mostrare sullo schermo (per esempio una finestra chiusa a metà). */
    open val nienteDaIndicare: String get() = "Fai l'azione di questo passo, oppure clicca «${e.saltaPasso}»."

    /** La finestra aperta è di un'altra forma: si torna alla scelta delle forme. */
    open val tornaIndietro: String get() = "Questa non è la forma di questo passo: clicca «${T.comuni.indietro}» e scegli quella giusta."

    /** Al benvenuto: andare dritti a una lezione. */
    open val vaiAllaLezione: String get() = "Oppure vai alla lezione:"

    /** Voce di «❔ Guide» che fa ripartire la guida. */
    open val voceMenu: String get() = "Guida interattiva (${Lesson.entries.count { it.inCourse }} lezioni)"
    open val progettoProva: String get() = "Prova della guida"
    open val progettoProvaPlanimetria: String get() = "Prova della guida · planimetria"

    /** Pulsante (solo durante la guida) che carica la piantina inventata al posto di un file. */
    open val usaPiantina: String get() = "🧭 Usa la piantina della guida"

    /** Scritte della piantina della guida. */
    open val ingresso: String get() = "Ingresso"
    open val cabinaArmadio: String get() = "cabina armadio"
    open val notaMisure: String get() = "misure in cm, sul filo interno dei muri"

    // ---------- Indicazioni comuni ----------

    open val apriElementi: String get() = "Clicca «${e.elementiArchitettonici}», qui in basso."
    open val apriElementiAltraStanza: String get() = "Per aggiungere un'altra stanza clicca «${e.elementiArchitettonici}», qui in basso."
    open val poiStanza: String get() = "Clicca «${e.stanza}»: torna la finestra con le forme."
    open fun tipo(nome: String): String = "Scegli il tipo: «$nome»."
    open val creaStanza: String get() = "Clicca «${cr.creaStanza}»."
    open fun selezionaStanza(nome: String): String = "Clicca sul nome della stanza, «$nome», per selezionarla."
    open val accendiAgganci: String get() = "Il gancio è spento: clicca «${T.schedaStanza.no}», qui in basso, per accenderlo."
    open val schedaMisure: String get() = "Apri la scheda «${T.schedaStanza.misure}»."

    /** Da che parte è un muro: in alto, a destra, in basso, a sinistra (verso dove guarda, fuori dalla stanza). */
    open fun latoMuro(a: WallArrow): String = when (a) {
        WallArrow.UP -> "in alto"
        WallArrow.RIGHT -> "di destra"
        WallArrow.DOWN -> "in basso"
        WallArrow.LEFT -> "di sinistra"
    }

    // ---------- Quadrato ----------

    open val scegliQuadrato: String get() =
        "Quando apri un progetto nuovo, Sagoma ti chiede subito che forma ha la prima stanza. Cominciamo dallo studio, la stanza " +
            "quadrata in basso a sinistra nella piantina: clicca «${cr.quadrato}»."
    open val tipoStudio: String get() = "In «${cr.tipoStanza}» clicca «${com.sagoma.planimetria.model.RoomType.Studio.label}»: Sagoma dà alla stanza il nome e il colore giusti."
    open fun scriviLato(v: Int): String =
        "Nel campo «${cr.lato2}» scrivi $v: è il primo modo di dare la forma a una stanza, con le misure quando la crei. È la misura " +
            "interna, da muro a muro: quella che prendi col metro."
    open val soffittoECrea: String get() = "L'altezza del soffitto è già scritta (270 cm): cambiala solo se la tua stanza è diversa. Ora clicca «${cr.creaStanza}»."
    open val trascinaMuroStudio: String get() =
        "Secondo modo: trascinare i muri. Premi sul muro di destra dello studio, tieni premuto e trascinalo fino al segno: la stanza " +
            "si allarga e la misura scritta sul muro cambia mentre lo sposti."
    open fun misureEsatte(v: Int): String =
        "Trascinando a mano la misura non viene mai precisa. Terzo modo, il più preciso: scrivere le misure. Riportiamo lo studio a " +
            "$v cm: clicca «${T.schedaStanza.info}»."
    open fun muroStudio(numero: Int, lato: WallArrow, v: Int): String =
        "Sotto «${T.schedaStanza.muri}» c'è la lunghezza interna di ogni muro. In «${T.schedaStanza.muro(numero)}», il muro " +
            "${latoMuro(lato)}, scrivi $v e premi Invio: il muro dopo si sposta e lo studio torna quadrato."
    open val trascinaAngoloStudio: String get() =
        "Quarto modo, per le stanze fuori squadro: trascinare un angolo. Premi sul pallino blu dell'angolo in basso a destra e " +
            "trascinalo fino al segno."
    open val annullaAngolo: String get() =
        "Lo studio però ha gli angoli dritti. Quando sbagli, «${e.annulla}», qui in alto, toglie l'ultima modifica: cliccalo."

    // ---------- Rettangolo ----------

    open val scegliRettangolo: String get() = "Clicca «${cr.rettangolo}»: è la camera, a destra del soggiorno, con due lati lunghi e due corti."
    open val spostaCameraAccanto: String get() =
        "Anche la camera nasce staccata: trascinala dal nome fino al contorno tratteggiato, a destra del soggiorno. Con il gancio " +
            "acceso si attacca da sola."
    open fun scriviLarghezza(v: Int): String = "«${T.comuni.larghezza}» è il lato orizzontale della stanza, da sinistra a destra sulla pianta: scrivi $v."
    open fun scriviProfondita(v: Int): String = "«${T.comuni.profondita}» è l'altro lato, dall'alto in basso: scrivi $v."

    // ---------- Forma a L ----------

    open val scegliL: String get() =
        "Clicca «${T.comuni.formaL}»: è il soggiorno, che nella piantina sta sopra lo studio. Molti soggiorni e cucine sono fatti " +
            "così, con un angolo che rientra."
    open val spostaSoggiorno: String get() =
        "Trascina il soggiorno dal suo nome fino al contorno tratteggiato, sopra lo studio: quando ci arriva vicino scatta e si attacca da solo."
    open fun sporgeInBassoADestra(lato: String): String =
        "Sagoma chiede da che parte sporge il pezzo in più della L. Guarda la stanza dall'alto, come su una piantina: qui sporge «$lato»."
    open fun larghezzaPrincipale(v: Int): String = "La L è fatta di due pezzi. Il pezzo principale è il rettangolo più grande: in «${cr.larghezzaPrincipale}» scrivi $v."
    open fun profonditaPrincipale(v: Int): String = "In «${cr.profonditaPrincipale}» scrivi $v."
    open fun larghezzaProtuberanza(v: Int): String =
        "La «protuberanza» è il pezzo che sporge. In «${cr.larghezzaProtuberanza}» scrivi $v: deve essere meno della larghezza principale."
    open fun profonditaProtuberanza(v: Int): String = "In «${cr.profonditaProtuberanza}» scrivi di quanto sporge: $v."

    // ---------- Da rilievo ----------

    open val scegliRilievo: String get() =
        "Clicca «${cr.rilievo}»: il bagno, sotto la camera, ha gli angoli non proprio a 90°. Serve anche per le stanze con 5 lati o più."
    open val spostaBagnoSotto: String get() = "Trascina il bagno dal nome fino al contorno tratteggiato, sotto la camera: si attacca da solo."
    open val tipoBagnoQuattroLati: String get() =
        "Scegli il tipo «${com.sagoma.planimetria.model.RoomType.Bagno.label}». In «${cr.numeroLati}» c'è già 4, come i lati del nostro bagno."
    open fun primoLatoRilievo(v: Int): String =
        "Chiama A un angolo della stanza, poi B, C e D girando in senso orario. Misura i lati sul muro, all'interno. In «${cr.lato('A', 'B')}» scrivi $v."
    open fun lato(nome: String, v: Int): String = "In «${cr.lato(nome[0], nome[1])}» scrivi $v."
    open fun diagonaleAC(v: Int): String =
        "Poi una diagonale: da A all'angolo opposto C (AC) oppure da B a D (BD), quella che hai misurato. Qui è AC, già scelta: scrivi $v. " +
            "È questa misura che dice a Sagoma quanto sono storti gli angoli."
    open val anteprimaECrea: String get() = "Più in basso c'è l'anteprima della forma vera. Clicca «${cr.creaStanza}»."

    // ---------- Muro per muro ----------

    open val scegliMuroPerMuro: String get() =
        "Clicca «${cr.muroMuro}»: l'ingresso riempie lo spazio che resta tra le stanze, e lo disegni un muro alla volta, con le " +
            "misure interne, come in un programma da architetto."
    open val primoAngolo: String get() =
        "Clicca qui, sotto la camera e accanto al soggiorno: è il primo angolo dell'ingresso, all'interno. Sagoma lo aggancia " +
            "all'angolo giusto tra i muri."
    open fun verso(n: Int, a: WallArrow): String = "Muro $n: scegli il verso, «${freccia(a)}»."
    open fun lunghezzaMuro(v: Int): String = "In «${T.muroPerMuro.lunghezzaCm}» scrivi $v."
    open val aggiungi: String get() = "Clicca «${T.muroPerMuro.aggiungi}»."
    open fun muriFatti(n: Int, tot: Int): String = "Muri fatti: $n di $tot"
    open val chiudiStanza: String get() =
        "Manca solo l'ultimo muro, che torna al primo angolo: clicca «${T.muroPerMuro.chiudiStanza}» e Sagoma lo disegna da sola."
    open fun tipoIngresso(nome: String): String = "Scegli il tipo: per l'ingresso va bene «$nome». Soffitto e spessore dei muri sono già scritti."

    protected fun freccia(a: WallArrow): String = when (a) {
        WallArrow.RIGHT -> T.muroPerMuro.destra
        WallArrow.DOWN -> T.muroPerMuro.giu
        WallArrow.LEFT -> T.muroPerMuro.sinistra
        WallArrow.UP -> T.muroPerMuro.su
    }

    // ---------- Muro singolo ----------

    open fun selezionaCameraTramezzo(nome: String): String =
        "Un muro singolo è un muro da solo, che non chiude una stanza: per esempio il tramezzo della cabina armadio nella camera, " +
            "come sulla piantina. Il muro nasce nella stanza selezionata: clicca sul nome della camera, «$nome»."
    open val apriElementiMuroSingolo: String get() = "Ora clicca «${e.elementiArchitettonici}», qui in basso."
    open val scegliMuroSingolo: String get() =
        "In fondo alla finestra c'è «${cr.muroSingolo}»: compare solo quando c'è già almeno una stanza. Cliccalo."
    open val spostaTramezzo: String get() =
        "Il muro nasce dritto attraverso la camera, da un muro all'altro, ed è già selezionato. Si sposta trascinandolo: premi sul " +
            "muro, tieni premuto e portalo fino alla linea tratteggiata, dove va il tramezzo. Le due estremità restano attaccate ai muri."
    open val selezionaMuroSingolo: String get() = "Clicca sul muro singolo per selezionarlo: compaiono i pallini alle due estremità."
    open val accorciaTramezzo: String get() =
        "Il tramezzo non arriva fino in fondo: lascia il passaggio per entrare nella cabina armadio. Premi sul pallino blu in basso, " +
            "alla fine del muro, e trascinalo in su fino al segno: il muro si accorcia."
    open val infoTramezzo: String get() = "Come per le stanze, le misure esatte si scrivono in «${T.schedaStanza.info}»: cliccalo."
    open fun lunghezzaTramezzo(v: Int): String =
        "In «${T.comuni.lunghezza}» scrivi $v: il muro si allunga o si accorcia dalla fine, la partenza in alto resta ferma."
    open fun spessoreTramezzo(v: Int): String = "Un tramezzo in cartongesso è sottile: in «${T.comuni.spessore}» scrivi $v."

    // ---------- Porte, finestre e altro ----------

    open val portaElementi: String get() = "Porte, finestre e tutto il resto si aggiungono da «${e.elementiArchitettonici}». Cliccalo."
    open val porte: String get() = "Clicca «${e.porte}»."
    open val portaUnaAnta: String get() = "Scegli il tipo, per esempio «${e.portaAd1Anta}»."
    open val cliccaMuroPorta: String get() = "Ora clicca sul muro, nel punto dove va la porta: per esempio qui."
    open val finestraElementi: String get() = "Una finestra si mette allo stesso modo: «${e.elementiArchitettonici}»…"
    open val finestre: String get() = "…«${e.finestre}»…"
    open val finestraDueAnte: String get() = "…«${e.finestra2Ante}»."
    open val cliccaMuroFinestra: String get() = "Clicca su un muro esterno, dove va la finestra: per esempio qui."
    open val balconeElementi: String get() = "Ora un balcone: «${e.elementiArchitettonici}»…"
    open val balconiTerrazze: String get() = "…«${e.balconiTerrazze}»…"
    open val balcone: String get() = "…«${com.sagoma.planimetria.model.RoomType.Balcone.label}»."
    open val balconeRettangolo: String get() = "La forma si sceglie come per le stanze: il balcone è un «${cr.rettangolo}»."
    open fun balconeLarghezza(v: Int): String = "Larghezza $v…"
    open fun balconeProfondita(v: Int): String = "…e profondità $v."
    open val balconeCrea: String get() =
        "Clicca «${cr.crea}»: il balcone nasce al suo posto, sopra la camera, già appoggiato alla casa (poi lo puoi spostare trascinandolo dal nome)."
    open val portaFinestraElementi: String get() = "Per uscire sul balcone serve una porta-finestra: «${e.elementiArchitettonici}»…"
    open val porteFinestre: String get() = "…«${e.porteFinestre}»…"
    open val balconeDueAnte: String get() = "…«${e.balcone2Ante}»."
    open val cliccaMuroBalcone: String get() = "Clicca sul muro della casa dove si appoggia il balcone, qui."
    open val impiantiElementi: String get() = "Ora gli impianti: termosifoni, prese, interruttori, punti acqua e luci. «${e.elementiArchitettonici}»…"
    open val impianti: String get() = "…«${e.impianti}»…"
    open val calorifero: String get() = "…«${e.calorifero}»."
    open val cliccaMuroCalorifero: String get() = "Termosifoni, prese, interruttori e punti acqua si mettono cliccando sul muro: per esempio qui."
    open val luciElementi: String get() = "Le luci invece vanno dentro la stanza. «${e.elementiArchitettonici}»…"
    open val luci: String get() = "…«${e.luci}»…"
    open val lampadario: String get() = "…per esempio «${FixtureKind.Chandelier.label}»."
    open val luceSemplice: String get() = "…e qui «${T.arredi.puntoLuceSemplice}»: si mette cliccando nella stanza."
    open val cliccaStanzaLuce: String get() = "Clicca dentro la stanza, dove va la luce: per esempio qui."
    open val cliccaStudio: String get() = "Ultima, la scala. Prima clicca dentro la stanza dove va, per esempio lo studio, qui."
    open val scalaElementi: String get() = "Poi «${e.elementiArchitettonici}»…"
    open val scale: String get() = "…«${e.scale}»…"
    open val chiocciola: String get() = "…e scegli la forma, per esempio «${StairKind.Spiral.label}»."
    open val info: String get() =
        "La scala è selezionata. Per tutte le altre modifiche clicca «${T.schedaStanza.info}»: vale per ogni elemento, porte, finestre, balconi, impianti e scale."

    open val controlloMenu: String get() = "Un aiuto da usare spesso: il controllo della pianta, che cerca gli errori di disegno che di solito sfuggono. Clicca il menu «⋮» qui in alto."
    open val controlloVoce: String get() = "…e «${T.controllo.voceMenu}»."
    open val controlloRiapri: String get() = "Riapri il controllo: il menu «⋮» qui in alto…"
    open val controlloCorreggiTutti: String get() =
        "Ecco l'elenco: ⚠ cosa sistemare, ? cosa controllare, 📏 i minimi delle norme per la casa. Molti problemi si sistemano da soli: " +
            "«✓ Correggi tutti» corregge tutto un gruppo insieme (per esempio mette le porte che mancano) e un solo «${e.annulla}» lo disfa. Cliccalo."
    open val controlloCorreggi: String get() =
        "Ecco l'elenco: ⚠ cosa sistemare, ? cosa controllare, 📏 i minimi delle norme per la casa. Molti problemi si sistemano da soli: " +
            "clicca «✓ ${T.controllo.correggi}» (con «${e.annulla}» si torna indietro)."
    open val controlloChiudi: String get() =
        "Fatto: l'elenco si aggiorna da solo. «${T.controllo.mostra}» porta al punto giusto e lo seleziona, «${T.controllo.condividi}» manda " +
            "l'elenco a chi ti aiuta. Ora clicca «${T.controllo.chiudi}»."
    open val controlloSegno: String get() =
        "Il controllo lavora anche da solo, mentre disegni: il segnale in alto a sinistra sulla pianta dice quanti problemi ci sono (toccalo " +
            "per riaprire l'elenco) e un «!» segna il punto di ognuno, rosso per gli errori, giallo per le cose da controllare, blu per le norme. Clicca questo «!»."
    open val controlloTogli: String get() =
        "La scheda dice cosa c'è in questo punto, con «✓ ${T.controllo.correggi}» (dove si può) e «${T.controllo.mostra}». Se per te va bene " +
            "così, «${T.controllo.togliDallaPianta}» toglie il «!»: il problema resta nell'elenco e da lì si rimette. Clicca «${T.controllo.togliDallaPianta}»."
    open val controlloSegnoDiNuovo: String get() = "Clicca un «!» sulla pianta: si apre la scheda di quel punto."

    // ---------- Sopra la tua planimetria ----------

    open val ricalca: String get() =
        "Hai già una planimetria, del catasto, del progetto o anche solo una foto? Nella scelta della prima stanza di un progetto clicca «${cr.ricalcaPiantina}»."
    open val usaPiantinaGuida: String get() =
        "Di solito qui clicchi «${T.sfondo.fotoImmagine}» (o «${T.sfondo.pdf}») e scegli il file della tua planimetria. Per provare usa la piantina della guida: clicca «$usaPiantina»."
    open fun taraturaA(cm: Int): String =
        "La piantina è sotto il disegno, ma Sagoma non sa ancora quanto è grande: va messa in scala con una misura scritta sopra. " +
            "Usiamo il muro in alto del soggiorno, $cm cm: clicca qui, sull'angolo interno in alto a sinistra del soggiorno."
    open val taraturaB: String get() = "Ora clicca dove finisce la misura: l'angolo interno in alto a destra del soggiorno, qui."
    open fun misuraReale(cm: Int): String = "Scrivi la misura vera tra i due punti, quella scritta sulla piantina: $cm."
    open val tara: String get() = "Clicca «${T.sfondo.tara2}»: ora la piantina è in scala, ogni centimetro è giusto."
    open val sceltaDopoTaratura: String get() =
        "La taratura è registrata: la piantina ora è in scala. Adesso scegli come disegnare la prima stanza: con «${cr.muroMuro}» " +
            "clicchi i suoi angoli sulla piantina, con una forma la crei e poi la porti sopra la piantina. Qui usiamo le forme. " +
            "Cominciamo dalla camera, che è un rettangolo: partiamo da un quadrato, per vedere come si allunga. Clicca «${cr.quadrato}»."
    open val cameraDaQuadrato: String get() =
        "Cominciamo dalla camera, che è un rettangolo: partiamo da un quadrato, per vedere come si allunga. Clicca «${cr.quadrato}»."
    open fun latoCortoCamera(v: Int): String = "Scrivi il lato corto della camera, letto sulla piantina: $v."
    open val spostaCamera: String get() =
        "La camera c'è, ma non ancora al suo posto. Per spostare una stanza premi sul suo nome, tieni premuto e trascinala: lasciala " +
            "quando l'angolo interno in alto a sinistra è sopra quello interno della camera disegnata (il contorno tratteggiato)."
    open fun allungaCamera(v: Int): String =
        "La camera della piantina è più larga: $v. Basta spostare il muro di destra: premi sul muro, tieni premuto e trascinalo fino al segno. " +
            "Mentre lo sposti la misura cambia: lasciala a $v."
    open val bagnoElementi: String get() = "Adesso il bagno, che è storto: anche qui partiamo da un quadrato. «${e.elementiArchitettonici}»…"
    open val bagnoQuadrato: String get() = "…«${cr.quadrato}»…"
    open fun latoBagno(v: Int): String = "…lato $v, il muro in alto del bagno…"
    open val spostaBagno: String get() = "Trascina il bagno dal suo nome sopra il bagno della piantina, con l'angolo in alto a sinistra al suo posto."
    open val angoloC: String get() =
        "Gli angoli del bagno non sono a 90°. Premi sul pallino blu dell'angolo in basso a destra e trascinalo fino al segno, dove combacia con la piantina."
    open val angoloD: String get() = "Poi l'angolo in basso a sinistra: basta un po' più giù. Le misure dei muri si aggiornano mentre lo sposti."

    // ---------- Il soggiorno a L, attaccato alla camera ----------

    open val soggiornoElementi: String get() = "Ultima stanza: il soggiorno, che è a L. «${e.elementiArchitettonici}»…"
    open val soggiornoL: String get() = "…«${T.comuni.formaL}»."
    open val soggiornoMisureDopo: String get() =
        "Scegli il tipo «${com.sagoma.planimetria.model.RoomType.Soggiorno.label}». Le misure lasciale come sono: il soggiorno lo metti a misura dopo, sopra la piantina."
    open val attaccaSoggiorno: String get() =
        "Sulla piantina il soggiorno è attaccato alla camera, alla sua destra. Trascina il soggiorno dal nome fino al contorno tratteggiato: " +
            "con il gancio «${T.schedaStanza.si}» acceso, quando il suo angolo in alto a destra arriva vicino a quello della camera, scatta e si attacca da solo."
    open fun muroSullaPiantina(muro: Int): String =
        "Ora porta i muri sopra la piantina. Premi sul muro ${nomeMuroSoggiorno(muro)} del soggiorno e trascinalo fino al segno, sul muro " +
            "disegnato. Basta più o meno: la misura esatta la scrivi dopo."
    open val misureInInfo: String get() =
        "Spostando i muri a mano le misure non vengono mai proprio uguali a quelle della piantina: guarda quelle scritte sui muri del " +
            "soggiorno. Quelle giuste si scrivono in «${T.schedaStanza.info}»: cliccalo."
    open fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "Sotto «${T.schedaStanza.muri}» c'è la lunghezza interna di ogni muro. " else "") +
            "In «${T.schedaStanza.muro(numero)}», il muro ${nomeMuroSoggiorno(muro)}, scrivi $v e premi Invio."

    // ---------- Piantina più chiara o più scura ----------

    open val apriPannello: String get() =
        "Ultima cosa: per vedere meglio il disegno sopra la piantina, clicca «${e.piantinaDisegno}», qui in basso a destra."
    open val trasparenzaPiantina: String get() =
        "Qui regoli la piantina: trascinando il cursore diventa più chiara o più scura, con l'interruttore la nascondi. Sotto c'è " +
            "lo stesso per il disegno. Quando hai finito, chiudi il pannello con «${T.comuni.chiudi}». Prova: trascina il cursore della piantina."

    // ---------- Il sole e la bussola (al primo tocco sulla bussola, sul progetto dell'utente) ----------

    open fun intestazioneSola(nome: String): String = "Guida · $nome"
    open val giraBussola: String get() =
        "La bussola 🧭 dice dove è il nord rispetto alla tua pianta: serve solo a far entrare il sole dalla parte giusta nel 3D. " +
            "Non sei obbligato a mettere tutte queste informazioni: puoi saltare ogni passo con «${e.saltaPasso}», o uscire. Se sai " +
            "dov'è il nord, premi sulla bussola e trascinala in giro finché la N punta lì. La pianta non si gira mai."
    open fun nordOra(gradi: Int): String = "Nord: $gradi°"
    open val toccaBussola: String get() =
        "Se vuoi, scegli anche la città della casa: decide quanto sale il sole e a che ora sorge e tramonta. Tocca la bussola, " +
            "senza trascinarla. Anche questo è facoltativo: senza città vale Roma."
    open val sceltaCitta: String get() =
        "Qui puoi scrivere anche i gradi del nord. Per la città scrivila in «${T.vista3D.cercaCitta}» e cliccala nell'elenco. Poi " +
            "clicca «${T.comuni.chiudi}». Se non ti interessa, chiudi e basta."
    open val apriVista3d: String get() = "Per vedere il sole clicca «${e.vista3d}», qui in basso a destra. Se non ti serve, esci pure."
    open val tornaAllaPianta: String get() = "La bussola è sulla pianta: clicca «${e.pianta2d}» per tornarci."
    open val tornaAl3d: String get() = "Il sole si guarda nel 3D: clicca «${e.vista3d}»."
    open val apriOra: String get() = "Il sole segue l'ora del giorno: clicca l'ora con il sole ☀, qui in basso a destra. Si apre il cursore dell'ora."
    open val riapriOra: String get() = "Riapri il cursore dell'ora: clicca l'ora con il sole ☀, qui in basso a destra."
    open val trascinaOra: String get() =
        "Trascina il cursore: il sole si muove con l'ora e la luce entra dalle finestre. L'ora non resta nel progetto: serve solo per guardare."
    open val apriGiorno: String get() = "Il sole cambia anche con le stagioni. Accanto al cursore clicca «📅»: di solito è oggi."
    open val scegliGiorno: String get() =
        "Scegli un giorno: d'inverno il sole è basso ed entra più in fondo nelle stanze, d'estate è alto. Anche il giorno serve solo per guardare."

    /** Muri del soggiorno disegnato: 0 in alto, poi in senso orario. */
    protected open fun nomeMuroSoggiorno(muro: Int): String = when (muro) {
        0 -> "in alto"
        1 -> "di destra"
        2 -> "in basso a destra"
        3 -> "corto in mezzo"
        4 -> "in basso a sinistra"
        else -> "di sinistra"
    }
}
