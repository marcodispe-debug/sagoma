package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.Tip
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
        Lesson.Elements -> "Porte, finestre e altro"
        Lesson.OverPlan -> "Sopra la tua planimetria"
    }

    open fun intestazione(n: Int, tot: Int, nome: String): String = "Guida · Lezione $n di $tot · $nome"
    open fun passo(n: Int, tot: Int): String = "Passo $n di $tot"

    /** Titolo e testo delle schede da leggere (passi senza azione). */
    open fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Benvenuto in Sagoma!"
        CourseStep.ElementsDone -> "Ogni elemento, da «${T.schedaStanza.info}»"
        CourseStep.Finished -> "Hai finito la guida!"
        CourseStep.Snap -> "Gli agganci 🧲"
        else -> "Fatto!"
    }

    open fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Ti guido passo passo a disegnare un piccolo appartamento di prova, in 7 lezioni brevi. Le azioni le fai tu: a ogni " +
                "passo ti indico con un riquadro arancione cosa cliccare. È un progetto solo per fare pratica: i tuoi progetti non si toccano."
        CourseStep.SquareDone ->
            "Lo studio quadrato di 3,30 × 3,30 m è sulla pianta, con le misure scritte sui muri. Nella prossima lezione aggiungi una stanza rettangolare."
        CourseStep.RectDone ->
            "Ecco la camera di 4,00 × 3,30 m, accanto allo studio. Nella prossima lezione: un soggiorno a forma di L."
        CourseStep.LDone ->
            "Ecco il soggiorno a L: 6,40 × 2,70 m, con il pezzo di 3,00 × 1,90 m che sporge. Nella prossima lezione: una stanza fuori squadra, dalle misure prese col metro."
        CourseStep.SurveyDone ->
            "Ecco il bagno con la sua forma vera, un po' fuori squadra come nella realtà. Nella prossima lezione disegni una stanza muro per muro."
        CourseStep.WallsDone ->
            "Ecco l'ingresso, con la forma che hai disegnato muro per muro. Hai visto i modi per creare una stanza. Nella prossima lezione: porte, finestre, balcone, impianti e scale."
        CourseStep.ElementsDone ->
            "Nella scheda «${T.schedaStanza.info}» cambi misure, tipo e tutto il resto dell'elemento selezionato; si chiude con «▼». " +
                "Per ogni elemento: lo scegli da «${e.elementiArchitettonici}», lo metti sulla pianta e il resto da «${T.schedaStanza.info}». " +
                "Ultima lezione: costruire le stanze sopra la tua planimetria. Serve un progetto nuovo: lo preparo io."
        CourseStep.Snap ->
            "Il pulsante «${T.schedaStanza.si}», qui in basso, accende e spegne gli agganci. Accesi, quando trascini una stanza vicino a " +
                "un'altra si attacca da sola, angolo su angolo o muro su muro, e il muro diventa in comune. Anche i muri e gli angoli che " +
                "trascini scattano sui muri vicini e sulle direzioni dritte. Spenti («${T.schedaStanza.no}»), tutto resta esattamente dove " +
                "lo lasci. Lasciali accesi: ora servono per attaccare il soggiorno alla camera."
        CourseStep.Finished ->
            "Ecco camera, bagno e soggiorno sopra la piantina, con le misure giuste. Con le altre stanze fai lo stesso: una forma per " +
                "partire, attaccata alle stanze accanto, poi muri e angoli al loro posto e le misure esatte in «${T.schedaStanza.info}». " +
                "Ora puoi disegnare la tua casa! La guida si rifà quando vuoi da «${e.guide}»."
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
    open val voceMenu: String get() = "Guida interattiva (7 lezioni)"
    open val progettoProva: String get() = "Prova della guida"
    open val progettoProvaPlanimetria: String get() = "Prova della guida · planimetria"

    /** Pulsante (solo durante la guida) che carica la piantina inventata al posto di un file. */
    open val usaPiantina: String get() = "🧭 Usa la piantina della guida"

    /** Scritte della piantina della guida. */
    open val ingresso: String get() = "Ingresso"
    open val notaMisure: String get() = "misure in cm, sul filo interno dei muri"

    // ---------- Indicazioni comuni ----------

    open val apriElementi: String get() = "Clicca «${e.elementiArchitettonici}», qui in basso."
    open val apriElementiAltraStanza: String get() = "Per aggiungere un'altra stanza clicca «${e.elementiArchitettonici}», qui in basso."
    open val poiStanza: String get() = "Clicca «${e.stanza}»: torna la finestra con le forme."
    open fun tipo(nome: String): String = "Scegli il tipo: «$nome»."
    open val creaStanza: String get() = "Clicca «${cr.creaStanza}»."

    // ---------- Quadrato ----------

    open val scegliQuadrato: String get() =
        "Quando apri un progetto nuovo, Sagoma ti chiede subito che forma ha la prima stanza. Clicca «${cr.quadrato}»: è la forma per una stanza con i quattro lati uguali."
    open val tipoStudio: String get() = "In «${cr.tipoStanza}» clicca «${com.sagoma.planimetria.model.RoomType.Studio.label}»: Sagoma dà alla stanza il nome e il colore giusti."
    open fun scriviLato(v: Int): String = "Nel campo «${cr.lato2}» scrivi $v. È la misura interna, da muro a muro: quella che prendi col metro."
    open val soffittoECrea: String get() = "L'altezza del soffitto è già scritta (270 cm): cambiala solo se la tua stanza è diversa. Ora clicca «${cr.creaStanza}»."

    // ---------- Rettangolo ----------

    open val scegliRettangolo: String get() = "Clicca «${cr.rettangolo}»: due lati lunghi e due corti."
    open fun scriviLarghezza(v: Int): String = "«${T.comuni.larghezza}» è il lato orizzontale della stanza, da sinistra a destra sulla pianta: scrivi $v."
    open fun scriviProfondita(v: Int): String = "«${T.comuni.profondita}» è l'altro lato, dall'alto in basso: scrivi $v."

    // ---------- Forma a L ----------

    open val scegliL: String get() = "Clicca «${T.comuni.formaL}»: molti soggiorni e molte cucine sono fatti così, con un angolo che rientra."
    open fun sporgeInBassoADestra(lato: String): String =
        "Sagoma chiede da che parte sporge il pezzo in più della L. Guarda la stanza dall'alto, come su una piantina: qui sporge «$lato»."
    open fun larghezzaPrincipale(v: Int): String = "La L è fatta di due pezzi. Il pezzo principale è il rettangolo più grande: in «${cr.larghezzaPrincipale}» scrivi $v."
    open fun profonditaPrincipale(v: Int): String = "In «${cr.profonditaPrincipale}» scrivi $v."
    open fun larghezzaProtuberanza(v: Int): String =
        "La «protuberanza» è il pezzo che sporge. In «${cr.larghezzaProtuberanza}» scrivi $v: deve essere meno della larghezza principale."
    open fun profonditaProtuberanza(v: Int): String = "In «${cr.profonditaProtuberanza}» scrivi di quanto sporge: $v."

    // ---------- Da rilievo ----------

    open val scegliRilievo: String get() = "Clicca «${cr.rilievo}»: serve quando gli angoli non sono proprio a 90° o la stanza ha 5 lati o più."
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
        "Clicca «${cr.muroMuro}»: disegni la stanza un muro alla volta, con le misure interne, come in un programma da architetto."
    open val primoAngolo: String get() = "Clicca sulla pianta dove comincia il primo muro, per esempio qui: è il primo angolo della stanza, all'interno."
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
    open val balconeCrea: String get() = "Clicca «${cr.crea}»: il balcone nasce già appoggiato alla casa (poi lo puoi spostare trascinandolo dal nome)."
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
    open val cliccaStanzaLuce: String get() = "Clicca dentro la stanza, dove va la luce: per esempio qui."
    open val cliccaStudio: String get() = "Ultima, la scala. Prima clicca dentro la stanza dove va, per esempio lo studio, qui."
    open val scalaElementi: String get() = "Poi «${e.elementiArchitettonici}»…"
    open val scale: String get() = "…«${e.scale}»…"
    open val chiocciola: String get() = "…e scegli la forma, per esempio «${StairKind.Spiral.label}»."
    open val info: String get() =
        "La scala è selezionata. Per tutte le altre modifiche clicca «${T.schedaStanza.info}»: vale per ogni elemento, porte, finestre, balconi, impianti e scale."

    /** La mini guida che si apre la prima volta che si mette un elemento. */
    open fun miniGuida(t: Tip): String = when (t) {
        Tip.Opening -> "La prima volta che metti una porta si apre la sua mini guida: come spostarla lungo il muro, e misure e verso da «${T.schedaStanza.info}». Leggila e clicca «${e.hoCapito}»."
        Tip.Outdoor -> "Anche il balcone ha la sua mini guida: al posto dei muri c'è il parapetto, e da «${T.schedaStanza.info}» scegli ringhiera, muretto o vetro. Clicca «${e.hoCapito}»."
        Tip.Fixture -> "Mini guida degli impianti: quelli a muro scorrono lungo il muro, e da «${T.schedaStanza.info}» scegli altezza da terra e misure. Clicca «${e.hoCapito}»."
        Tip.Stair -> "Quando metti una scala si apre la sua mini guida, che spiega tutto: come spostarla e accostarla a un muro, e cosa cambi da «${T.schedaStanza.info}». Leggila e clicca «${e.hoCapito}»."
        else -> "Si è aperta una mini guida: leggila e clicca «${e.hoCapito}»."
    }

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
    open val chiudiPannello: String get() =
        "Qui a destra c'è il pannello «${e.piantinaDisegno}»: rende la piantina più chiara o più scura, oppure la nasconde. Per ora chiudilo con «${e.chiudi}»."
    open val esciMuroPerMuro: String get() =
        "È partito «${cr.muroMuro}» sopra la piantina: potresti cliccare gli angoli delle stanze uno a uno. Qui invece usiamo le forme e le " +
            "aggiustiamo sulla piantina: clicca «${T.comuni.esci}»."
    open val cameraDaQuadrato: String get() =
        "Cominciamo dalla camera, che è un rettangolo: partiamo da un quadrato, per vedere come si allunga. Clicca «${cr.quadrato}»."
    open fun latoCortoCamera(v: Int): String = "Scrivi il lato corto della camera, letto sulla piantina: $v."
    open val spostaCamera: String get() =
        "La camera c'è, ma non ancora al suo posto. Per spostare una stanza premi sul suo nome, tieni premuto e trascinala: lasciala " +
            "quando l'angolo in alto a sinistra è sopra quello della camera disegnata (il contorno tratteggiato)."
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
            "quando il suo angolo in alto a destra arriva vicino a quello della camera, scatta e si attacca da solo."
    open fun muroSullaPiantina(muro: Int): String =
        "Ora porta i muri sopra la piantina. Premi sul muro ${nomeMuroSoggiorno(muro)} del soggiorno e trascinalo fino al segno, sul muro " +
            "disegnato. Basta più o meno: la misura esatta la scrivi dopo."
    open val selezionaSoggiorno: String get() = "Clicca sul nome del soggiorno per selezionarlo."
    open val misureInInfo: String get() =
        "Spostando i muri a mano le misure non vengono mai proprio uguali a quelle della piantina: guarda quelle scritte sui muri del " +
            "soggiorno. Quelle giuste si scrivono in «${T.schedaStanza.info}»: cliccalo."
    open val schedaMisure: String get() = "Apri la scheda «${T.schedaStanza.misure}»."
    open fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "Sotto «${T.schedaStanza.muri}» c'è la lunghezza interna di ogni muro. " else "") +
            "In «${T.schedaStanza.muro(numero)}», il muro ${nomeMuroSoggiorno(muro)}, scrivi $v e premi Invio."

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
