# Risultati della verifica delle pareti

Riferimento: `protocollo-verifica-pareti.md` (impronta SHA-256 `fcb0df38…17d2`, copie in `C:\Users\marco\sagoma-riferimenti`).
Misure col metro: lati 215,5 / 192,0 / 215,5 / 193,0 cm; diagonali 0–2 = 290,0, 1–3 = 290,5 cm; stanza in squadra entro 0,2°.

## 2026-10-12 — scan4 (registrazione di sviluppo), senza vincolo manuale

Scansione guidata = motore M2.0 con utente SIMULATO (prima proposta compatibile con una parete R3), non un utente vero.
Messa in squadra (prova offline, codice temporaneo non conservato): orientamento comune = media pesata sulla lunghezza vista
(modulo 90°); ogni parete ruotata attorno al centro del tratto visto; angoli dal nucleo (`SketchSolver`).

| Variante                          | Lato peggiore | Diagonale 0–2 | Diagonale 1–3 | Angolo peggiore | Area      |
|-----------------------------------|---------------|---------------|---------------|-----------------|-----------|
| Scansione guidata, angoli scansione | 3,7 cm      | −10,0 cm      | +1,5 cm       | 3,2°            | 4,064 m²  |
| Scansione guidata, in squadra       | 3,6 cm      | −4,9 cm       | −5,4 cm       | 0° (imposto)    | 4,037 m²  |
| R3, angoli di R3                    | 2,4 cm      | −2,3 cm       | −0,8 cm       | 0,95°           | 4,145 m²  |
| R3, in squadra                      | 1,4 cm      | −1,4 cm       | −1,9 cm       | 0° (imposto)    | 4,144 m²  |

Osservazioni:
- La messa in squadra dimezza l'errore peggiore della scansione guidata (diagonale da −10,0 a −5,4 cm). L'errore che resta è
  sistematico: la stanza viene circa 2,5 cm più piccola in ogni direzione (pareti posizionate leggermente all'interno).
- La scansione guidata su scan4 resta NON confermabile dal nucleo: `OBSERVED_BEYOND_CORNER` bloccante (2 lati senza squadra,
  3 con la squadra). Problema separato, non risolto.
- Una sola registrazione, utente simulato: indicazioni, non prova di accuratezza generale.

## 2026-10-12 — diagnosi su scan4 e correzioni (cartella B)

**Blocco `OBSERVED_BEYOND_CORNER`: causa trovata, era un artefatto.** ARCore ricalcola e FONDE i suoi piani nel tempo, anche tra
muri perpendicolari: il piano 15 (muro dello specchio, normale lungo x a 75 s) a 100 s è ruotato di 47° (taglia la stanza in
diagonale: probabilmente il "muro diagonale" delle prime prove) e a 141 s è assorbito dal piano 4 (muro della finestra), il cui
poligono arriva fino a x = −1,18 m, 40 cm oltre il muro dello specchio. La nuvola di punti oltre quell'angolo: 0 punti sul muro
della finestra, 2 sul muro dello specchio (rumore). Correzione: il motore salva alla conferma dove stanno lungo la parete i punti
che la sostengono; il nucleo conta il superamento dell'angolo solo se lo confermano anche almeno 10 punti (mai più blocchi di
prima; senza punti o con meno di 10 il controllo resta quello di prima). Soglia di 5 cm invariata. Su scan4: nessun blocco,
pianta confermabile; lunghezze e angoli invariati.

**Pareti "più all'interno": non è un errore sistematico.** Rispetto a R3: specchio 4,8 cm verso l'interno e ruotata di 2,3°
(confermata quando ARCore vedeva solo la parte bassa, fino alla mensola; i punti erano già 2 cm più indietro del piano), finestra
0,7 cm, doccia −0,2 cm, porta 0,8 cm. L'errore viene quasi tutto da UNA parete: una correzione fissa di posizione sarebbe un
adattamento a scan4 (vietato dal protocollo). Candidato da verificare sulla registrazione nuova: usare lo scarto dei punti per
correggere la posizione della parete (oggi i punti sono solo un controllo).

**Avviso "parete confermata dietro":** scattava anche per il muro opposto, alle spalle del telefono (distanze senza segno).
Corretto: conta solo una parete dalla stessa parte della candidata, più lontana.

**Messa in squadra (scelta dell'utente, implementata):** domanda nella revisione quando lo schizzo è ad angoli retti e ogni
angolo misurato è entro 4° dal suo angolo retto (sopra 0,1°): "Tieni gli angoli della scansione" / "Approssima a 90°", con
"Cambia scelta". Senza risposta la pianta non si conferma. Su scan4 (proposta: scarto massimo 3,21°):

| Scelta           | Lati (cm)                  | Diagonali (cm) | Angoli          | Confermabile |
|------------------|----------------------------|----------------|-----------------|--------------|
| Angoli scansione | −3,6 / +0,6 / −1,4 / −3,7  | −10,0 / +1,5   | 87,5°–93,2°     | sì           |
| A 90°            | −2,3 / −2,6 / −2,3 / −3,6  | −4,9 / −5,4    | 90°             | sì           |

## 2026-10-12 (pomeriggio) — punti aperti

### 1. Soglia di 4° e dimensioni della stanza (simulazione sintetica, codice temporaneo non conservato)

Rettangoli da 2×2 a 10×6 m. A = stanza in squadra con pareti scansionate storte (errore della scansione); B = stanza davvero
fuori squadra di α, scansionata senza errori. "Spostamento" = quanto si spostano gli angoli tra le due scelte.

| Caso | 2×2 m | 3×4 m | 5×4 m | 7×5 m | 10×6 m |
|------|-------|-------|-------|-------|--------|
| A, errori ±1° (angolo 2°): diagonale con angoli scansione | 5,0 cm | 8,5 | 11,1 | 14,5 | 18,4 |
| A, errori scan4 (angolo 2,45°): diagonale con angoli scansione | 5,4 cm | 9,2 | 12,0 | 15,7 | 20,0 |
| A, qualsiasi: diagonale con squadra | 0,0 | 0,0 | 0,0 | 0,2 | 0,3 |
| B, α = 1°: lato peggiore con squadra | 1,7 cm | 3,5 | 3,5 | 4,4 | 5,2 |
| B, α = 2°: lato peggiore con squadra | 3,5 cm | 7,0 | 7,0 | 8,7 | 10,5 |
| B, α = 3°: lato peggiore con squadra | 5,3 cm | 10,5 | 10,5 | 13,1 | 15,7 |
| Spostamento tra le scelte, angolo 2° | 2,5–2,8 cm | 4,3–5,2 | 5,6–5,8 | 7,3–7,4 | 9,1–9,4 |

Conclusioni:
- Un limite fisso in gradi NON corrisponde a un errore in cm: lo stesso angolo pesa in proporzione alla dimensione della stanza.
  Con 4° su una stanza di 5×4 m la scelta sbagliata costa 15–20 cm.
- Dagli angoli NON si può distinguere l'errore della scansione (A) da una stanza davvero storta (B): lo sa solo l'utente. Per
  questo la scelta resta sua.
- Lo spostamento tra le due piante cresce con la stanza ed è circa l'errore che si commette con la scelta sbagliata: è la misura
  giusta da mostrare all'utente e su cui decidere se proporre la domanda.
- Il limite di 4° oggi è solo un limite di plausibilità tarato su una registrazione (scan4: errori di direzione fino a 2,3° per
  parete, 3,2° agli angoli). Non validato.

### 2. scan4 e il criterio dei 5 cm (utente simulato, misure col metro, nessun arrotondamento)

| Scelta           | Lati: errore peggiore | Diagonali              | Angoli (metro: 89,77°–90,23°) |
|------------------|-----------------------|------------------------|-------------------------------|
| Angoli scansione | 3,7 cm (entro 5)      | −10,0 / +1,5 cm (NO)   | 87,48°–93,21° (fino a 3,2°)   |
| A 90°            | 3,6 cm (entro 5)      | −4,9 (sì) / −5,4 cm (NO, 0,4 cm oltre) | 90° (fino a 0,23° dal metro) |

Con la squadra scan4 NON rientra del tutto nei 5 cm: una diagonale è a −5,4 cm. Lo scarto di 0,4 cm è dentro l'incertezza del
metro (±0,5 cm), quindi non è dimostrabile né il superamento né il rispetto. Inoltre il criterio "5 cm" non dice ancora a quali
grandezze si applica (solo lati, o anche diagonali).

### 3. `OBSERVED_BEYOND_CORNER`: criterio rivisto

Il primo criterio (almeno 10 punti oltre l'angolo) dipendeva dalla densità: su scan4 le pareti hanno 140–405 punti/m al momento
della conferma (10 punti = 2–7 cm di parete), ma su una parete liscia con 15 punti/m 10 punti sono circa 67 cm, quindi un
superamento vero fino a quella lunghezza sarebbe stato ignorato. Densità e numero di punti dipendono anche da quanto si è
scansionato (i punti si accumulano per tutta la sessione) e dalla lunghezza del muro.

Criterio attuale, relativo: il tratto oltre l'angolo è un artefatto solo se ha meno di un quarto dei punti attesi alla densità
che la stessa parete ha tra gli angoli, e solo se se ne attendono almeno 10; altrimenti conta il poligono come prima (nel dubbio
si blocca; mai più blocchi di prima). Dati scan4 alla conferma:

| Parete   | Densità tra gli angoli | Oltre l'angolo: poligono | Punti oltre / attesi | Esito          |
|----------|------------------------|--------------------------|----------------------|----------------|
| specchio | 244 punti/m            | 8,8 cm                   | 3 / 21,5             | artefatto      |
| finestra | 405 punti/m            | 40,2 cm                  | 0 / 163              | artefatto      |
| doccia   | 315 punti/m            | 4,5 e 4,0 cm             | 0 / 14,2 e 0 / 12,5  | entro tolleranza comunque |
| porta    | 140 punti/m            | 5,3 cm                   | 8 / 7,4              | tratto vero (attesi < 10: conta il poligono), entro tolleranza |

Test sintetici: stesso esito con 1, 2 e 4 volte i punti; parete vera oltre l'angolo vista a metà densità → blocca; parete poco
densa (8 punti/m) → blocca come prima.
Limite: nei dati reali disponibili non c'è nessun blocco LEGITTIMO (scan4 ha solo artefatti), quindi non è dimostrato sul reale
che la correzione non ne elimini; lo è solo sui casi sintetici. Il rapporto "un quarto" è una scelta tecnica da verificare su
altre registrazioni (caso critico: una parete vera che prosegue oltre l'angolo ma è vista molto peggio vicino all'angolo).

### 4. Registrazione reale nuova

- Sul telefono non c'è nessuna registrazione dopo il 10/10 11:18 (cartella `scan-recordings` della Pro e `Download`): la
  scansione guidata fatta dopo l'installazione del 12/10 non è stata registrata. Nel progetto 4 non c'è il risultato.
- Il formato delle registrazioni NON contiene le conferme delle pareti né le risposte (tipi di riga: header, frame, pose, rgb,
  depth, missing, stats, end). Con una registrazione si può rigiocare il motore e simulare l'utente (come per scan4), ma non
  riprodurre le scelte dell'utente vero.

## 2026-10-12 (sera) — decisioni dell'utente e modifiche

**Criterio dei 5 cm (decisione dell'utente):** obiettivo per i lati E per le diagonali. Su scan4 con la squadra: lati entro 5 cm;
diagonali −4,9 cm e −5,4 cm. La diagonale a −5,4 cm è un risultato AL LIMITE E INCONCLUSIVO: supera l'obiettivo di 0,4 cm, meno
dell'incertezza del metro (±0,5 cm); non è dichiarata né superata né rispettata. Senza squadra la diagonale a −10,0 cm non rientra.
Nota: la definizione "lati e diagonali" è stata fissata dopo aver visto i risultati di scan4 (registrazione di sviluppo); vale da
qui in avanti per le registrazioni di verifica.

**Messa in squadra: nessun limite in gradi.** La domanda compare per ogni schizzo ad angoli retti e mostra lo spostamento massimo
degli angoli tra le due piante (scan4: 4,7 cm, mostrato "5 cm", con angoli fino a 3,2° da 90°). Si omette solo se lo spostamento
è sotto 1 cm: soglia di VISUALIZZAZIONE (le misure sono mostrate in centimetri, sotto 1 cm le due piante coincidono), non una
tolleranza di accuratezza. Approvata dall'utente il 2026-10-12 (`SketchThresholds.squareMinShiftM`).

**Criterio relativo di `OBSERVED_BEYOND_CORNER`: PROVVISORIO** finché non è verificato su una scansione reale.

**Registro degli eventi della sessione (file laterale):** `session-events.jsonl` nella cartella della registrazione (e nel suo ZIP),
scritto solo mentre si registra. `recording.jsonl` NON cambia: le registrazioni vecchie restano identiche e leggibili, gli
strumenti esistenti ignorano il file in più (verificato con un test: stessa registrazione letta con e senza file laterale).
Contenuto: intestazione (schizzo, aperture), lato di partenza, pareti confermate (lato, id, istante della sessione e sull'orologio
di ARCore della registrazione, retta, incertezze, punti di sostegno), pareti tolte o spostate, risposte (con i valori mostrati,
per esempio lo spostamento della squadra), dichiarazione della partenza, misure a mano, stime accettate, esito (pianta applicata
con gli angoli, oppure uscita). Dalle pareti registrate il nucleo rifà offline la stessa pianta con le stesse decisioni.

## Prossima prova sul telefono (bagno) — piano concordato il 2026-10-12

Ordine: prima A, poi B. Stanza grande rimandata (non si allarga il test prima di sapere cosa succede nel bagno).

- **A — scansione normale.** Registra (con consenso) PRIMA di iniziare; schizzo rettangolare; 4 pareti confermate; risposte;
  sulla squadra annotare i cm mostrati, provare "Cambia scelta", poi scegliere; applicare; esportare lo ZIP (contiene
  `session-events.jsonl`). Atteso: nessun blocco "parte vista oltre l'angolo" (i superamenti sono artefatti, come su scan4).
- **B — test intenzionale di superamento reale.** Come A, ma si conferma come parete il fronte di un mobile alto appoggiato al muro
  (annotare quale, profondità dal muro e larghezza col metro). Atteso: il blocco compare. SE IL BLOCCO NON COMPARE: non confermare
  né applicare quella pianta; esportare la registrazione e fermarsi (sarebbe un superamento reale accettato: criterio da rivedere).
- Riferimento: misure col metro già registrate nel protocollo (lati 215,5 / 192,0 / 215,5 / 193,0 cm; diagonali 290,0 / 290,5).
- Offline, dal registro: per ogni parete tratto oltre l'angolo, punti trovati/attesi, decisione; pianta rifatta dal nucleo;
  confronto con il metro (lati e diagonali, obiettivo 5 cm) e con le foto della registrazione.

## 2026-10-12 — scan5 (prova A, registrazione di VERIFICA, utente vero)

File: `scan5.zip` (registrazione 78,6 s, 648 frame, 151 depth; `session-events.jsonl` presente). Pianta rifatta offline dal
nucleo con le pareti e le risposte registrate (nessuna simulazione dell'utente).

**Eventi registrati:** partenza lato 3 (porta); pareti confermate nell'ordine porta (13,2 s), specchio (30,3 s), finestra
(43,4 s), doccia (75,2 s), ciascuna con retta, incertezze e punti di sostegno (142 / 482 / 144 / 460); risposte alle aperture
(finestra e porta: "sì, è su questa parete"). **Mancano** la scelta degli angoli e l'esito: la registrazione è stata fermata
(per esportarla) prima di quei passi, e il registro scrive solo mentre si registra. Il progetto "prova1" contiene la stanza
ancora come schizzo da misurare: la pianta della scansione NON è stata applicata.

**Domanda sugli angoli:** proposta, con spostamento 7,2 cm e angoli fino a 5,7° da 90° (con il vecchio limite di 4° non
sarebbe comparsa). Nessun blocco; con una delle due scelte la pianta è confermabile.

| Scelta (metro: 215,5 / 192,0 / 215,5 / 193,0; diagonali 290,0 / 290,5) | Lati: scarto (cm) | Diagonali: scarto (cm) | Angoli |
|---|---|---|---|
| Angoli della scansione | +3,6 / +0,8 / −12,4 / −7,9 | −12,8 / −1,4 | 84,3°–93,8° |
| Approssima a 90° | −3,4 / −3,6 / −3,4 / −4,6 (entro 5) | −6,3 / −6,8 (NON entro 5, oltre il margine del metro) | 90° |

Osservazioni:
- Con la squadra i lati rientrano nei 5 cm, le diagonali no (−6,3 e −6,8 cm: oltre 5 cm di più del margine del metro).
- Come su scan4, la stanza in squadra viene più PICCOLA in entrambe le direzioni (scan4: −2,3…−3,6 cm; scan5: −3,4…−4,6 cm). Su
  due registrazioni lo scarto ha lo stesso segno: la conclusione di scan4 ("non sistematico") va rivista. Da verificare: R3 su
  scan5 (su scan4 R3, stesse pose, non aveva questo scarto) e il confronto parete per parete.
- `OBSERVED_BEYOND_CORNER` (criterio provvisorio). CORRETTO il 2026-10-12: nella prima versione di questa analisi il segno di
  un'estremità era invertito (i valori 20,4 / 46,1 / 9,6 cm erano tratti mancanti PRIMA dell'angolo, non superamenti). Valori
  corretti: supera l'angolo solo la parete della doccia, di 12,4 cm all'angolo 2 (1 punto oltre, attesi 28,0) e di 9,7 cm
  all'angolo 3 (0 punti, attesi 21,9); lo specchio di 0,9 cm (entro tolleranza). I due superamenti della doccia sono trattati
  come artefatti, nessun blocco; con il vecchio controllo la pianta sarebbe stata bloccata.

## 2026-10-12 — scan6 (prova A ripetuta, registrazione di VERIFICA, utente vero)

File: `scan6.zip` (registrazione 203,6 s, 1647 frame). Eventi: partenza lato 3 (porta); porta confermata (21,8 s); risposta
apertura porta; dichiarazione di partenza (porta) e "partenza verificata" (associazione ambigua: 1 alternativa); stime dello
schizzo accettate per i lati 0 e 1; specchio (103,8 s); finestra (140,3 s); risposta apertura finestra; **scelta degli angoli
"Tieni gli angoli della scansione" (155,5 s, con lo spostamento mostrato di 15,3 cm e angoli fino a 5,8°) quando erano
confermate solo 3 pareti**; doccia (193,8 s). Registrazione fermata prima dell'esito.

**La pianta non è stata applicata (la stanza in "prova1" è ancora uno schizzo) perché la pianta finale è BLOCCATA:**
`OBSERVED_BEYOND_CORNER` sullo specchio, 12,4 cm oltre l'angolo 0 (porta/specchio), con 5 punti oltre su 14,6 attesi (34%,
sopra la soglia provvisoria del 25%: giudicato superamento reale). Lo stesso blocco c'è anche con la squadra. Altri due
superamenti (doccia 5,2 cm e porta 7,3 cm all'angolo 3, 0 punti su 12,9 e 13,9 attesi) trattati come artefatti: con il vecchio
controllo avrebbero bloccato anche loro. Probabile causa del superamento dello specchio: angolo 0 misurato 95,4° (la stanza è a
90° entro 0,3°), quindi l'angolo calcolato cade prima di quello vero e la parete vista lo supera davvero. Il blocco segnala
una geometria sbagliata (giusto), ma l'utente non ha modo di capire cosa fare. Non verificato con le foto.

| Scelta (metro: 215,5 / 192,0 / 215,5 / 193,0; diagonali 290,0 / 290,5) | Lati: scarto (cm) | Diagonali: scarto (cm) | Angoli |
|---|---|---|---|
| Angoli della scansione (quella scelta) | −5,6 / +2,6 / −7,6 / −8,0 | −18,6 / +2,2 | 84,2°–95,4° |
| Approssima a 90° | −5,1 / −3,3 / −5,1 / −4,3 | −7,4 / −7,9 | 90° |

Osservazioni:
- Terza registrazione con la stanza più PICCOLA del vero in entrambe le direzioni (con la squadra: scan4 −2,3…−3,6; scan5
  −3,4…−4,6; scan6 −3,3…−5,1 cm). È sistematico: le pareti confermate stanno in media circa 2 cm dentro il muro vero.
- Gli errori d'angolo della scansione guidata reale (5,7° e 5,8°) sono maggiori di quelli di scan4 con l'utente simulato (3,2°).
- La risposta sugli angoli resta valida anche se poi la pianta cambia (qui: data con 3 pareti, poi confermata la quarta): la
  scelta è stata fatta su una pianta diversa da quella finale (spostamento mostrato 15,3 cm, finale 7,4 cm).

## 2026-10-12 — correzioni dopo scan6 (approvate dall'utente)

1. **La scelta degli angoli si azzera** quando cambia la pianta: conferma, rimozione o spostamento di una parete, "Tutte ±1
   lato", misura a mano o stima dello schizzo cambiate (una misura uguale a prima non la azzera; le altre risposte restano).
   La domanda torna aperta e la pianta non si conferma finché l'utente non sceglie di nuovo, sulla pianta aggiornata.
2. **Registro degli eventi fino alla chiusura della schermata**, anche dopo "Ferma registrazione": lo ZIP esportato contiene gli
   eventi fino all'esportazione, la cartella della registrazione sul telefono anche quelli dopo (scelte, blocchi, esito). Nuovo
   evento `BLOCKING`: i blocchi presenti, scritto ogni volta che cambiano. Solo eventi, niente immagini né profondità.
3. **Blocchi con indicazione di cosa fare.** `OBSERVED_BEYOND_CORNER` ora porta l'angolo, l'altro lato di quell'angolo, l'angolo
   misurato e quello dello schizzo; su scan6 il testo sarebbe: "Lato 1: la parete vista prosegue 12 cm oltre l'angolo con lato
   4 (angolo misurato 95,4°, nello schizzo 90,0°): quell'angolo è calcolato male. Togli e riconferma la parete di lato 4,
   inquadrandola al centro e senza mobili davanti; se non basta, riconferma anche quella di lato 1." Indicazioni aggiunte anche
   a: nessuna parete, parete doppia, pareti parallele o quasi, misure incompatibili o diverse dalla scansione, lato nullo o
   rovesciato, angolo a punta, perimetro incrociato, area troppo piccola, parete che guarda fuori.

## Analisi esplorativa "linea di base dal pavimento" su scan5/scan6 — regole fissate PRIMA di eseguirla (2026-10-12)

Esplorativa: scan5 e scan6 non contengono la scansione iniziale del pavimento chiesta dalla procedura proposta. Riferimento per la
pianta: la linea alla BASE delle pareti (decisione dell'utente).

- Profondità: solo grezza "fresca" (`rawStatus` salvata, file presente; le ripetute non sono salvate), confidenza ≥ 32/255
  (valore già usato da R1; ARCore non dà una soglia in cm), distanza 0,3–4 m, scarto dei pixel ai bordi (salto > 8% con un
  vicino, come R1). Posa: quella del FRAME CORRENTE (regola principale del protocollo); posa del timestamp della grezza riportata a
  parte come sensibilità.
- Quota del pavimento: punti entro 15 cm dalla quota ARCore del momento; quota = mediana per finestre di 20 s; dispersione =
  MAD × 1,4826 e percentili 5/95 dei residui; tolleranza T = 3 × dispersione (calcolata, non scelta).
- Campioni di pavimento vicino a una parete: residuo dal pavimento ≤ T, distanza dalla parete confermata tra −10 e +50 cm (verso
  l'interno), lungo la parete tra i suoi angoli calcolati meno 10 cm per parte.
- Campioni della base della parete: altezza dal pavimento tra T e 30 cm, entro 10 cm dalla parete confermata.
- Fine del pavimento: tratti di 10 cm lungo la parete; in ogni tratto con almeno 5 campioni, il 5° percentile della distanza
  (il pavimento più vicino al muro).
- Linea stimabile se almeno 4 tratti con dati coprono almeno 60 cm; retta ai minimi quadrati sui tratti: posizione e direzione
  rispetto alla parete confermata.
- Confronto col metro: pianta con le pareti confermate e pianta con la linea di base al posto della parete dove stimabile (angoli
  della scansione e a 90°); lati e diagonali, obiettivo 5 cm. Nessuna soglia approvata cambia.

### Esito dell'analisi esplorativa (regole sopra, eseguita il 2026-10-12)

| | scan5 | scan6 |
|---|---|---|
| Grezze fresche usate (posa frame corrente) | 122 (29 ripetute) | 164 (225 ripetute) |
| Pavimento: dispersione MAD×1,4826 | 4,0 cm | 4,0 cm |
| Residui p5 / p95 | −7,6 / +9,2 cm | −5,9 / +11,5 cm |
| Tolleranza T = 3 × dispersione | 12,1 cm | 12,1 cm |
| Quota per 20 s | −1,52 … −1,46 m | −1,51 … −1,42 m |

Linea di fine pavimento rispetto alla parete confermata (posa frame corrente; campioni di pavimento vicino: migliaia per parete):

| Parete | scan5: posizione / rotazione / scarto tratti | scan6: posizione / rotazione / scarto tratti |
|---|---|---|
| specchio (lato 0) | 16,6 cm dentro / −3,2° / 16,8 cm | 6,4 cm dentro / −3,8° / 14,8 cm |
| finestra (lato 1) | 15,1 cm dentro / +2,9° / 19,4 cm | 7,9 cm dentro / +15,5° / 7,9 cm |
| doccia (lato 2) | 16,1 cm dentro / +5,6° / 7,7 cm | 18,1 cm dentro / +5,4° / 9,8 cm |
| porta (lato 3) | 0,2 cm dentro / −1,1° / 3,9 cm | 0,5 cm fuori / −3,2° / 1,2 cm |

Sostituendo le pareti con queste linee la pianta PEGGIORA: con la squadra lati −14…−38 cm e diagonali −30…−40 cm (contro
−3…−5 e −6…−8 cm con le pareti confermate). Con la posa del timestamp della grezza le rotazioni cambiano molto (scan6, porta:
−3,2° → −11,1°): stima instabile.

Conclusione (esplorativa, su questi dati): i campioni sono tanti ma la profondità del pavimento ha una dispersione di circa 4 cm
(code fino a 7–11 cm), quindi la tolleranza (12 cm) non separa il pavimento dalla base dei muri, dal battiscopa e dal piatto
doccia; vicino a 3 pareti su 4 il pavimento "finisce" 6–18 cm prima del muro con forte dispersione. Solo la parete della porta
(base libera) dà una linea coerente con la parete confermata. Con questo telefono la linea di base dal pavimento NON è una stima
indipendente utilizzabile; il pavimento resta utile come quota di riferimento e per riconoscere superfici, non per la posizione.

## Diagnosi dello scarto verso l'interno — regole fissate PRIMA di eseguirla (2026-10-12)

Domanda: lo scarto di circa 2 cm verso l'interno delle pareti confermate è coerente tra pareti e registrazioni? Viene dalla
posizione dei piani di ARCore, dalla scala delle pose, o dalla scelta del punto misurato (altezza, battiscopa, mobili)?

- Registrazioni: scan5 e scan6 (pareti dal registro eventi, utente vero), scan4 (pareti dell'utente simulato, come nel test di
  qualità). Nessuna soglia approvata cambia.
- Punti della parete: profondità grezza fresca come nell'analisi precedente (confidenza ≥ 32, 0,3–4 m, scarto dei bordi, posa del
  frame corrente; posa del timestamp della grezza come sensibilità). Punti entro 15 cm dalla parete confermata, lungo la parete
  tra gli angoli calcolati meno 20 cm per parte, divisi in fasce d'altezza dal pavimento ARCore del momento: 0–0,3; 0,3–0,8;
  0,8–1,3; 1,3–1,8; 1,8–2,3 m.
- Misura: distanza con segno dalla parete confermata (positivo = dietro la parete, verso il muro vero); per fascia mediana,
  MAD × 1,4826 e numero di punti. Fasce con meno di 200 punti non si commentano.
- Stessa misura con i punti di ARCore (nuvola) della registrazione, come seconda fonte.
- Lettura: punti sistematicamente dietro la parete confermata (stesso segno su più pareti e registrazioni) → posizione dei piani
  di ARCore; punti sulla parete confermata ma stanza comunque più piccola del metro → scala delle pose; differenze tra fasce →
  mobili, battiscopa, fuori piombo o punto misurato.

### Esito della diagnosi dello scarto verso l'interno (regole sopra)

Distanza mediana dei punti di profondità dalla parete confermata, fascia 0,3–2,3 m dal pavimento (+ = punti DIETRO la parete
confermata, cioè muro vero più esterno). Posa del frame corrente; con la posa del timestamp della grezza i valori cambiano di
al massimo 0,5 cm.

| Registrazione | Specchio | Finestra | Doccia | Porta | Media |
|---|---|---|---|---|---|
| scan5 (utente vero) | +0,7 | +2,6 | +1,7 | +3,2 | +2,1 cm |
| scan6 (utente vero) | +1,4 | +2,0 | +1,7 | +2,5 | +1,9 cm |
| scan4 (utente simulato) | +0,7 | +0,7 | −0,7 | +0,5 | +0,3 cm |

Per fasce d'altezza:
- 0–0,3 m: punti DAVANTI alla parete in modo ripetuto (doccia −10 cm in tutte e tre: probabilmente il piatto doccia; finestra
  −2,5…−3,7 cm: probabilmente il calorifero). Non c'è un segno di battiscopa sulle altre pareti (0…+1 cm).
- 0,8–1,8 m: le fasce più pulite (dispersione 2–3 cm sulle pareti piastrellate): +1…+4,6 cm in scan5/scan6.
- Nessuna inclinazione sistematica con l'altezza (fuori piombo non evidente; unica eccezione la porta in scan6, da +2,0 a +4,2).
- La nuvola di punti di ARCore sta invece quasi SULLA parete confermata (−0,9…+1,4 cm nelle fasce centrali): i piani di ARCore
  seguono i suoi punti; è la profondità a collocare il muro più indietro.

Lettura (esplorativa):
- Nelle due scansioni con utente vero lo scarto è coerente: 8 pareti su 8 con lo stesso segno, +0,7…+3,2 cm, media circa 2 cm,
  indipendente dalla posa scelta. È compatibile con le stanze più piccole del metro (circa 4 cm per dimensione = 2 cm per parete).
- In scan4 (utente simulato, pareti scelte in modo diverso) lo scarto quasi non c'è, eppure anche lì la stanza viene più piccola:
  non è un'unica causa uguale ovunque.
- La causa più probabile è la posizione dei piani di ARCore (che seguono i punti di ARCore), non la scala delle pose (la profondità
  usa le stesse pose e colloca il muro più indietro), non il battiscopa, non il fuori piombo. Quale tra profondità e punti ARCore
  sia più vicina al vero lo dice solo il metro: il segno e l'entità della correzione suggeriscono la profondità.

## Simulazione offline della correzione con la profondità — regole fissate PRIMA del calcolo (2026-10-12)

Prova PRELIMINARE: scan5 e scan6 sono due rilievi della stessa stanza con lo stesso telefono; scan4 (utente simulato) è un
controllo. Il disaccordo di circa 2 cm tra profondità e piani di ARCore, calcolati con le stesse pose, non prova da solo la causa
né esclude errori comuni di posa.

- Fascia d'altezza UNICA per tutte le registrazioni: 0,8–2,3 m dal pavimento ARCore del momento (sopra lavabo, calorifero e piatto
  doccia, sotto il soffitto). Le fasce 0,8–1,8 e 0,3–2,3 del resoconto precedente non si usano.
- Campioni: solo profondità grezza nuova (salvata, non ripetuta), confidenza ≥ 32/255, 0,3–4 m, senza pixel ai bordi; punti entro
  15 cm dalla parete confermata, lungo la parete tra gli angoli calcolati meno 20 cm per parte. Posa del frame corrente (principale);
  posa del timestamp della grezza come sensibilità.
- Un fotogramma pesa UNA volta: per ogni fotogramma e parete, la mediana delle distanze se ha almeno 200 punti; la correzione della
  parete è la mediana delle mediane dei fotogrammi; servono almeno 5 fotogrammi, altrimenti la parete resta com'è.
- Regola di correzione (senza ritocchi): ogni parete confermata si sposta lungo la sua normale della correzione così calcolata
  (verso il muro se positiva, verso l'interno se negativa); direzione invariata; nessun limite, nessun arrotondamento.
- Confronto con il metro, per scan5, scan6 e scan4: pianta originale e corretta, con gli angoli della scansione e a 90° (messa in
  squadra rifatta sulle pareti corrette); lati, diagonali e angoli; obiettivo 5 cm su lati e diagonali; margine del metro ±0,5 cm.
  Nessuna soglia approvata cambia. Se migliora scan5 e scan6 ma non scan4, va spiegato prima di proporre la correzione nell'app.

### Esito della simulazione (regole sopra, posa del frame corrente; tra parentesi la posa del timestamp della grezza)

Correzioni (cm verso il muro; fotogrammi usati): scan5 specchio 1,1 (34), finestra 4,6 (28), doccia 2,3 (41), porta 4,8 (22);
scan6 1,8 (41), 3,9 (45), 2,1 (43), 2,8 (46); scan4 1,5 (147), 1,3 (129), −0,4 (66), 0,8 (73).

Errore peggiore tra lati e diagonali (obiettivo 5 cm):

| | Originale, angoli scansione | Corretta, angoli scansione | Originale, a 90° | Corretta, a 90° |
|---|---|---|---|---|
| scan5 | 12,8 | 13,2 (12,3) | 6,8 | **6,1** (5,2) — lati +6,1/−0,2/+6,1/−1,2, diagonali +3,1/+2,6 |
| scan6 | 18,6 | 11,3 (11,7) | 7,9 | **1,6** (1,2) — lati +1,6/+0,6/+1,6/−0,4, diagonali +0,2/−0,3 |
| scan4 | 10,0 | 7,8 (7,5) | 5,4 | **3,1** (2,9) — lati −0,2/−1,6/−0,2/−2,6, diagonali −2,6/−3,1 |

Lettura (preliminare: una stanza, un telefono, due rilievi veri e un controllo simulato):
- Con la messa in squadra la correzione riduce l'errore peggiore in tutte e tre le registrazioni; scan6 e scan4 rientrano nei 5 cm,
  scan5 no (6,1 cm: la correzione di finestra e porta, 4,6 e 4,8 cm, porta la dimensione specchio–doccia 6 cm oltre il metro;
  con l'altra posa 5,2 cm, comunque oltre).
- Anche scan4 migliora (5,4 → 3,1): non c'è contraddizione con il controllo.
- Con gli angoli della scansione nessuna registrazione rientra: l'errore d'angolo (fino a 5,8°) resta dominante e la correzione di
  posizione non lo cura.
- La correzione non è indipendente da errori comuni di posa (stesse pose), e la scelta della fascia e delle regole è una sola: il
  risultato va confermato su altre stanze o registrazioni prima di qualsiasi uso nell'app.

## Seconda stanza — piano fissato PRIMA dell'acquisizione (2026-10-12)

Scopo: verificare la correzione con la profondità su una stanza diversa, SENZA cambiare niente: fascia 0,8–2,3 m, formula di
correzione (mediana delle mediane dei fotogrammi, ≥ 200 punti per fotogramma, ≥ 5 fotogrammi), posa del frame corrente (quella
del timestamp della grezza come sensibilità), soglie, obiettivo 5 cm su lati e diagonali. Nessun ritocco dopo i risultati.

- Procedura di scansione: quella attuale (nessuna scansione iniziale di pavimento o soffitto), con l'APK installato il 12/10 alle
  16:11 (nessuna installazione nuova).
- Stanza: rettangolare (4 pareti), più grande del bagno, con almeno una parete liscia (intonaco) misurabile.
- Misure reali, registrate nel protocollo con impronta SHA-256 PRIMA di aprire la registrazione: 4 lati da angolo ad angolo a
  circa 1 m d'altezza; 2 diagonali a circa 1 m; larghezza e profondità in due punti ciascuna a circa 1 m. Angoli reali: ricavati
  dalle sei misure (quadrilatero più vicino, residui e somma degli angoli) e, se disponibile, con una squadra o una livella; la
  stanza è "in squadra" se tutti gli angoli stimati stanno entro il margine che risulta dalle misure.
- Confronto: pianta non corretta e corretta, con gli angoli della scansione e a 90°; lati, diagonali, angoli; tutti i risultati.

Stato del piano della seconda stanza (2026-10-12, sera): IN ATTESA, non annullato. L'utente farà misure e scansione appena
possibile; nessuna sostituzione con altre verifiche (per esempio incrociata scan5/scan6). Regole, fascia, formula, posa e soglie
invariate. Misure da registrare nel protocollo (con impronta) prima di aprire la registrazione.

## Nota (2026-10-13): nuova interfaccia guidata della scansione

La scansione dallo schizzo ha una nuova interfaccia a passi (partenza → "Parete N di M" → domande di contesto → revisione con
la scelta degli angoli e "Applica"). Cambiano SOLO presentazione e flusso: algoritmo geometrico, soglie e criteri di conferma sono
invariati. In più, cambiando una parete o una misura si riaprono tutte le risposte che dipendono dalla geometria (non solo la
scelta degli angoli). La prossima registrazione (seconda stanza) avverrà con questa interfaccia: va trattata anche come PROVA DELLA
NUOVA PROCEDURA. Le regole dell'analisi della seconda stanza (fascia, formula, posa, soglie) restano quelle fissate; cambia solo
l'APK (non più quello del 12/10 ore 16:11), che andrà installato solo dopo il via esplicito dell'utente.

## 2026-10-13 — Filtro delle superfici "fuori schizzo" durante la scansione (soglia approvata: 10°)

Richiesta dell'utente: non proporre superfici senza senso rispetto allo schizzo (es. una diagonale che taglia la stanza,
proposta per una parete successiva alla prima). Regola: dalla seconda parete in poi, la direzione della superficie proposta si
confronta con quella che lo schizzo prevede per il lato da scansionare, con lo schizzo ruotato come le pareti già confermate
(medoide circolare). Oltre `candidateDirectionDeg` la superficie non si disegna e non si può confermare
(`SessionRejection.OFF_SKETCH_DIRECTION`). La prima parete non si filtra (manca il riferimento).

Scelta della soglia sui dati (pareti VERE confermate, scarto dalla direzione prevista; il bagno è in squadra entro 0,3°):

| Registrazione | parete 2 | parete 3 | parete 4 |
|---|---|---|---|
| scan5 | 0,9° | 4,8° | 1,0° |
| scan6 | 5,4° | 0,4° | 2,8° |

L'utente proponeva 5° o meno: su scan6 avrebbe nascosto la parete vera dello specchio (5,4°). Approvati 10° (circa il doppio
del massimo osservato). Le superfici parallele allo schizzo ma sul lato sbagliato passano il filtro e restano fermate dal
nucleo (`SKETCH_DIRECTION_CONFLICT`, test della stanza a L). Le soglie esistenti non cambiano.

## 2026-10-13 (sera) — Prova P0 (metodo ibrido): registrazioni di camera e stanzetta

Regole: `protocollo-verifica-pareti.md`, sezione P0 con P0.15 (impronta della copia `…P0rev4…` in `sagoma-riferimenti`).
Strumento: test temporaneo `TmpHybridTest` (non nel repository).

### Esito secondo P0: NON VALUTABILE (acquisizione)

Le registrazioni non contengono i gesti della procedura P0.12: nessun periodo fermo verso il soffitto in nessuna registrazione;
riferimento del pavimento valido in 2 su 5; riferimenti di parete validi 0–2 su 4. Con le regole fissate il metodo ibrido fallisce
per riferimenti mancanti in tutte e 5: è un esito dell'acquisizione, non un'informazione sul metodo.

| Registrazione | Durata | Pavimento | Soffitto | Pareti con riferimento | Pareti guidate |
|---|---|---|---|---|---|
| scan1 stanzetta (15:14) | 353 s | non valido (6,9 cm) | assente | 0/4 | nessun registro eventi |
| scan1 sranzetta (16:31) | 272 s | valido (1,0 cm) | assente | 2/4 | 1 (lato 0) |
| scan2 stanzetta | 242 s | valido (3,1 cm) | assente | 1/4 | 1 (lato 0) |
| scan3 stanzetta | 136 s | assente | assente | 2/4 (probabilmente sbagliati: stessa direzione, primi 12 s) | 3 (lati 0, 1, 3) |
| scan1 camera | 57 s | assente | assente | 0/4 | 4, "Approssima a 90°" |

### Risultati descrittivi (non previsti come esito da P0)

- (B) Scansione guidata, camera (unica completa): lati −74,6 / −11,3 / −61,4 / −3,0 cm, diagonale −60,4 cm (angoli della
  scansione); a 90° −68,4 / −7,3 / −67,4 / −6,3, diagonale −57,5. La foto al momento della conferma del lato 1 (mobile) mostra
  le ANTE DELL'ARMADIO: la parete confermata è il davanti del mobile (~60–70 cm dentro la stanza), come previsto dalla regola
  "probabile davanti del mobile" (serie 3, aggiunta).
- (B') Correzione con la profondità (regole congelate, camera): correzioni +3,0 / +2,5 / +1,3 / −9,9 cm; errore peggiore 82,1 cm
  (angoli scansione) e 75,8 cm (a 90°). Sensibilità fascia 1,8–2,3 m sul lato del mobile: +5,3 cm; nessun cambiamento
  sostanziale. La correzione (finestra ±15 cm) non può recuperare una parete confermata sul mobile. È la "seconda stanza" del
  piano in attesa: esito NEGATIVO per questa registrazione, per una causa a monte (associazione al mobile), non per la correzione.
- (C) R3/R4 così come sono: 8–33 "pareti" per registrazione (molti frammenti, mobili compresi); R4 non chiude mai il perimetro
  (stati UNCERTAIN/PARTIAL, alternative ambigue). Sulle pareti lisce la profondità produce comunque superfici di parete lunghe
  (fino a 3,1 m osservati, rms 2–4 cm, dispersione tra viste 1–2 cm): la profondità "vede" le pareti lisce, ma senza un criterio
  di scelta (riferimento, schizzo) R3 non sa quali formino la stanza. Nessun confronto col metro possibile senza perimetro.
- Pavimento (controllo R1/R2): inclinazione della superficie di pavimento dalla profondità 0,3–2,3° nelle registrazioni delle
  16:31–16:41, 6,6° (rms 3,3 cm) in quella delle 15:14. Nella camera la superficie "pavimento" dalla profondità sta 75 cm sopra
  il pavimento di ARCore: probabilmente il letto; le quote delle pareti R3 della camera sono quindi falsate.

Lettura: i dati confermano le critiche dell'utente al motore attuale (pareti proposte dai piani di ARCore: una parete accettata
su quattro nella stanzetta; mobile accettato come parete nella camera) e mostrano che la sola ricostruzione dai punti (R3/R4)
non basta senza riferimento e schizzo. Il metodo ibrido resta da provare: servono registrazioni con i gesti di P0.12.

## 2026-10-13 (sera) — Prototipo dal vivo del metodo ibrido (implementato, NON verificato)

Nucleo `scan/hybrid` (HybridModels, HybridScan): riferimenti di pavimento, soffitto e pareti stimati dalla profondità GREZZA con
confidenza (zona centrale dell'immagine, fit robusto, controlli di P0.4), classificazione dei punti e dei frammenti con le
tolleranze iniziali di P0.5 (provvisorie), frammenti dubbi mostrati e mai uniti, proposta della parete successiva al cambio di
direzione (con schizzo: direzione prevista entro 10°), avvisi "parallela spostata" e "superficie dietro" (mobile davanti al
muro), angoli dalle intersezioni, scatola incompleta/incerta/chiusa, confronto con lo schizzo (lunghezze 15%, angoli 10°,
pareti mancanti, altezza) mostrato e mai corretto. Schermata separata `RoomHybridScanScreen`, scelta all'avvio accanto alla
scansione guidata (invariata). Registro `hybrid-events.jsonl` con i riferimenti confermati, per la prova offline.
Test sintetici: 8 (logica), nessuna prova su telefono o su dati reali. Soglie NUOVE non tratte da dati: superficie "dietro" tra
10 cm e 1 m con almeno 300 punti (solo avviso superabile dall'utente). Il vincolo morbido dello schizzo (A2) NON è applicato dal
vivo: la geometria viene solo dai dati.

### Prima prova del prototipo sul telefono (stanzetta, registrazione scan-20261013-184305, rianalizzata offline)

Rianalisi: stessa profondità registrata (2 al secondo), stessi riferimenti confermati e "Rifai" dal registro `hybrid-events.jsonl`.
- Sequenza: pavimento confermato (62 s, dispersione 0,8 cm); soffitto confermato (185 s, 1,7 cm); lato 0 confermato, rifatto
  (la foto del primo riferimento mostra il davanti di un mobile di legno), riconfermato; lato 1 confermato, rifatto, riconfermato
  NONOSTANTE l'avviso "superficie dietro"; lato 3 confermato NONOSTANTE l'avviso "direzione diversa di 35° dallo schizzo"; lato 2
  mai acquisito. Registrazione al limite dei 10 minuti di frame; registro senza evento d'uscita.
- Pareti 0 e 1: frammento accettato grande (4,5 × 2,8 m e 3,1 × 2,9 m), scarto dallo schizzo 0,1° e 0,8°; angolo tra le due 89,1°
  contro 89,4° del metro (−0,3°). Lato 1 osservato per 306 cm (metro 311,5): l'avviso "superficie dietro" era un FALSO ALLARME
  (foto: trave/ribassamento del soffitto e libreria). Lato 0 osservato per 442 cm, più del lato vero (329,5): anomalia da capire.
- Lato 3: la foto mostra uno spigolo vicino al letto, non la parete; l'avviso era GIUSTO. Con il lato 2 mancante la scatola resta
  incompleta: nessun confronto delle lunghezze col metro possibile.
- Pavimento ricostruito 11,1 m² (stanza circa 10,2 m²). Soffitto: nessun frammento accettato (uno dubbio: il soffitto ha un
  ribassamento) → altezza NON stimata; i soli riferimenti danno 2,99 m (non verificato; schizzo 2,70 = valore predefinito).
- Usabilità: nella fase pareti il candidato era "non piano" nel 58% dei fotogrammi e "pronto" nel 3%. Diagnosi: con UN fotogramma
  la dispersione dei piani verticali al centro ha mediana 2,4 cm; il candidato, che unisce fino a 6 fotogrammi recenti mentre la
  camera si muove (mediana 8 cm/s), ha mediana 6,5 cm. La causa principale è il modo in cui il prototipo unisce i fotogrammi, non
  solo il rumore delle pareti lisce. (Dal vivo la profondità arriva più spesso che nella registrazione: l'effetto può essere minore.)

Correzioni e diagnosi successive (stessa prova, 2026-10-13 sera):
- Area del pavimento: nella rianalisi 8,3 m² accettati al momento di "Avanti: soffitto" (l'utente ricorda circa 6 m² dal vivo);
  11,1 m² alla FINE della sessione, perché il pavimento continua ad accumulare durante soffitto e pareti. La rianalisi usa solo la
  profondità salvata dalla registrazione (2 al secondo) e le pose registrate: non è identica a ciò che il telefono ha elaborato dal
  vivo, che non è registrato. Da aggiungere al registro del prototipo: aree e stati mostrati all'utente.
- La spiegazione "pavimento oltre la porta aperta" era SBAGLIATA (le porte erano chiuse). Causa trovata: la posizione stimata da
  ARCore SALTA. In 13,5 minuti: 17 salti oltre 10 cm tra due pose consecutive (fino a 2,09 m in 33 ms) e 7 interruzioni del
  tracciamento; concentrati attorno al soffitto (230–233 s), durante la parete della porta (295–334 s), attorno alla finestra
  (566–617 s) e nei tentativi sul lato 3 (644–773 s). Dopo un salto i punti nuovi sono spostati rispetto a quelli già accumulati:
  superfici "raddoppiate", parete della porta lunga 4,4 m, punti fuori stanza. Riguarda qualunque metodo che accumula dati nel
  mondo ARCore, anche la scansione guidata.
- Nota: una prima verifica dei salti era errata (lettura sbagliata dei numeri in notazione esponenziale); quella valida è questa.

### Analisi esplorativa: quali punti di profondità sono più giusti (stessa prova, 2026-10-13 sera)

Scarto dei punti dal piano confermato (pavimento: 1,6 milioni di punti; parete della porta; soffitto), per gruppi. ESPLORATIVA, una
sola registrazione: da verificare su altre prima di usarla nell'app.
- Pavimento, tutti i punti: dispersione 3,2 cm, entro ±2 cm il 48%. Fattore più forte: l'ANGOLO DI VISTA. Visto quasi di fronte
  (0–30° dalla perpendicolare) dispersione 2,5–2,6 cm; 30–45° scarto sistematico +2,3 cm; 45–60° +7,1 cm (dispersione 9,6). Poi la
  POSIZIONE NELL'IMMAGINE (centro 2,2 cm, bordi 3,9 cm), la DISTANZA (1–1,5 m la migliore; 2,5–3 m scarto +5,9 cm), la CONFIDENZA
  (massima 2,9 cm, le altre 3,7–4,2).
- Selezione stretta (distanza ≤ 2 m, angolo ≤ 30°, confidenza ≥ 224, zona centrale): pavimento dispersione 2,2 cm, scarto tipico
  −0,4 cm, entro ±2 cm il 61% (29% dei punti); parete della porta 3,1 cm, scarto +0,8 cm (da 5,9 e +2,2 con tutti i punti).
- Anche i punti migliori hanno circa 2 cm di dispersione ciascuno: con questo telefono la precisione viene dalla mediana di
  moltissimi punti buoni, non dal singolo punto; gli errori sistematici (angolo, distanza, salti di posizione) vanno evitati.
- Soffitto: TUTTI i gruppi stanno 9–16 cm sotto il riferimento, a ogni distanza, angolo e confidenza: non è qualità dei punti. Il
  riferimento e il resto del soffitto sono superfici diverse (ribassamento visibile nelle foto) o separati da un salto di posizione
  (salti a 229,9 s durante la fase soffitto).

### Terza prova del prototipo (stanzetta, scan-20261013-215646, 11 minuti)

- Pavimento: 10,1 m² ricostruiti (stanza circa 10,2 m²). Soffitto confermato e ricostruito (4,2 m² nella rianalisi, 5,4 m²
  dal vivo). Altezza misurata pavimento–soffitto 2,80 m (NON verificata col metro; schizzo 2,70 = valore predefinito).
- Pareti: nessuna chiusa. Due conferme su quattro ignorate in silenzio (pulsante attivo, ma il candidato era diventato
  instabile un istante prima): ERRORE dell'interfaccia del prototipo, da correggere.
- ARCore ha perso il tracciamento nel 15% dei fotogrammi (893 su 5.976; prove precedenti 4% e 9%), quasi sempre per
  INSUFFICIENT_FEATURES (pareti e soffitto lisci e uniformi). Alla ripresa la posizione SALTA (22 salti, fino a 1,5 m) e
  l'orientamento RUOTA (17 rotazioni oltre 10° in 33 ms, fino a 144°). Prova diretta: la parete della porta, confermata due volte
  (345 s e 605 s, foto: stessa parete), ha due direzioni a 70° l'una dall'altra. Dopo le rotazioni la parete giusta del lato 1
  risultava "fuori schizzo" (scarti 18–162°) e veniva ignorata dal filtro dei 10°.
- Conseguenza: in questa stanza il sistema di riferimento di ARCore non resta coerente per tutta la scansione. Qualunque metodo
  che accumula dati nel mondo di ARCore per minuti (anche la scansione guidata) ne è colpito. Serve un riallineamento dopo le
  perdite di tracciamento, oppure una scansione che non lo perda.

### Rianalisi per P0.17: pareti con punti buoni, solo centro o immagine intera (prima prova, scan-20261013-184305)

Stessi eventi registrati, punti buoni su tutte le superfici, salti NON corretti (la prova è precedente al riallineamento). Una sola
registrazione, scansionata senza le istruzioni "telefono di fronte": indicazione, non prova.
- Parete della porta (lato 0): con centro ≤ 0,75 il tratto più grande è DUBBIO (1,4 × 1,5 m, inclinazione 3,7°, oltre il limite di
  3°); con l'immagine intera è ACCETTATO (1,5 × 1,6 m, inclinazione 2,5°, scarto −0,9 cm), dispersione attorno alla retta 3,1 cm
  (2,7 col centro, ma su un tratto accettato di 20 cm).
- Pavimento: 6,0 → 6,3 m² accettati, scarto −0,5 → −0,8 cm. Soffitto: dubbio in entrambi i casi (−5,5/−6,2 cm: il ribassamento,
  non la scelta dei punti). Lati 1 e 3: insufficienti o dubbi in entrambi (rotazioni di ARCore non corrette).

### Prove del 2026-10-13, 23:51–00:01 (stanzetta, punti buoni su tutte le superfici, superfici colorate)

- "Crash" (2 volte, durante il soffitto): NON crash. Il processo è rimasto vivo; Android ha RICREATO la schermata per la rotazione
  dello schermo (configuration_changed: orientamento) quando il telefono puntava in alto, e la scansione è ripartita da capo
  (relaunch alle 23:54:45 e 00:00:54; le registrazioni si fermano lì). Correzione: schermo bloccato in verticale nel prototipo.
- Pavimento arancione: in tutte e tre le prove il pavimento resta accettato per 10–20 s (1,6–3,7 m²), poi l'UNICO frammento
  diventa dubbio: scarto mediano dal riferimento 3,2–4,5 cm (7,5 cm nella seconda), oltre il limite di 3 cm.
- Ipotesi "riferimento sbagliato perché preso di sbieco" SMENTITA: il riferimento ristimato dai soli punti buoni differisce da
  quello registrato di 0,1–1,5 cm, e il pavimento resta dubbio (3,2–7,5 cm).
- Causa osservata: la quota del pavimento vista dal telefono CAMBIA con la posizione e il tempo. Mediana per immagine dei punti
  buoni rispetto al riferimento: −0,4 cm sul posto del riferimento, poi +2,5…+5,8 cm (prova 23:59). Anche nella prova buona delle
  21:56 oscilla da −1,6 a +5,1 cm, ma la mediana complessiva resta 0,5 cm e il pavimento è accettato. Con questo telefono la
  coerenza in quota da un punto all'altro della stanza è dell'ordine di ±3–5 cm: al limite della tolleranza di 3 cm, che vale per
  l'intero frammento collegato (tutto il pavimento insieme).
- Causa del riferimento basso (prova 23:59): la foto al momento della conferma mostra MARMO LUCIDO con il riflesso della lampada al
  centro dell'inquadratura; le foto successive mostrano riflessi di porta e mobili. Sui riflessi la profondità misura l'immagine
  riflessa, "sotto" il pavimento: il riferimento è circa 4 cm più basso del resto. I filtri dei punti buoni non lo escludono.
- Rianalisi con il giudizio per cella (P0.18): pavimento accettato 1,6 / 0,5 / 0,2 m² nelle prove 23:59 / 23:51 / 23:56 (prima
  0 m²); il grosso resta dubbio (3,8 / 4,8 / 7,5 cm) perché è il riferimento a essere spostato. Prova 21:56: pavimento accettato
  6,3 m² (prima 8,2: zone a 3,5–3,8 cm ora escluse); soffitto da accettato (1,0 m², 1,6°) a dubbio (tratto di celle accettate
  inclinato 3,3°, oltre 3°): altezza non stimata in questa rianalisi.
- Aggiunti: istruzione "pavimento senza riflessi" e pulsante "Rifai il riferimento" per pavimento e soffitto.

### Prova del 2026-10-14, 00:33 (stanzetta, 16 minuti, schermo bloccato, giudizio per cella)

- Nessun crash né riavvio della schermata durante la scansione.
- Pavimento: 6,7 m² accettati su 7,5 visti (riferimento preso senza riflessi). Soffitto: 1,7 m² accettati su 4,3 visti, dopo
  3,7 minuti con 8 perdite di posizione.
- Pareti 0, 1, 2 confermate in un minuto (339–398 s). Lato 1 tra i due angoli: 316,6 cm (metro 311,5: +5,1 cm). Riferimenti:
  lati 1–2 a 90,8° tra loro; lati 0–1 a 82,9° (circa 7° da un angolo retto; il metro dà 88,9–91,5°).
- Da 414 s a fine sessione: ARCore ha perso o corretto la posizione 29 volte; il riallineamento è stato chiesto 7 volte e ha
  occupato circa il 74% del tempo (419 s su 564). Correzioni risultanti: −8,1°, −9,6°, 0,0°, −4,4° (plausibili), poi 76,5°,
  54,3°, −116,8° con spostamenti fino a 7,8 m (impossibili in una stanza di 3,3 m): riconferme incoerenti accettate senza
  controllo. Da lì il lato 3 risultava "fuori schizzo" e non è mai stato acquisito; scatola incompleta.
- Posizione persa da ARCore (PAUSED) per fase: pavimento 0%; SOFFITTO 60%; pareti 7% fino al primo salto, 14% dopo. Durante il
  soffitto le riprese hanno ruotato il mondo di 41°, 108°, 119° e 171°: il soffitto, inquadrato con il telefono quasi verticale
  (richiesto dai punti buoni), è la fase che rompe il tracciamento.
- Tentativo di verifica "le pareti acquisite restano al loro posto?" con una fascia di 15 cm attorno al piano, immagine per
  immagine: NON conclusivo (il vetro e il telaio della finestra, arretrati di circa 11 cm, cadono nella fascia; anche a mondo
  fermo lo scarto per immagine è di 3–4 cm). Un primo riassunto di questa verifica era sbagliato (colonne lette male).

### Prove del 2026-10-14, 01:25 e 01:34 (ordine pavimento → pareti → soffitto)

- In entrambe una sola parete confermata (lato 0); scatola mai chiusa. Nessun crash.
- PROFONDITÀ FERMA con posizione tracciata e telefono in movimento: 01:25 da 360 s alla fine (157 s); 01:34 per 125 s da 353 s e
  di nuovo da 655 s alla fine. Anche le registrazioni non hanno immagini di profondità in quei tratti: è ARCore che non ne produce
  di nuove, non la lettura dell'app. Buchi della profondità oltre 10 s anche nelle prove precedenti: 21:56 il 24% del tempo, 00:33
  il 26% (soprattutto dopo le perdite di posizione).
- Perdite di posizione durante le pareti con motivo INSUFFICIENT_FEATURES (pareti uniformi inquadrate di fronte da vicino).
- Con una sola parete acquisita il riallineamento si fa su una parete sola: nessun controllo di coerenza possibile; una correzione
  di 27° (01:34, 560 s) è stata accettata. Stati del candidato nella fase pareti (01:34): fuori schizzo 33, inclinata 26, nessuna
  profondità 22, pochi punti 10, non piana 8, pronta 5: le pareti successive risultavano "fuori schizzo" dopo le rotazioni del mondo.

### Verifica P0.20: angoli misurati da una sola immagine (eseguita 2026-10-14 notte, regole fissate prima)

- 9 registrazioni della stanzetta, 5.211 immagini di profondità: solo 11 immagini con due pareti valide (≥ 1500 punti ciascuna);
  2 gruppi con almeno 5 immagini: 00:33 (878–882 s) angolo mediano 101,9° (dispersione 4,0°); 01:34 (207–209 s) 107,8° (1,8°).
  Angoli del metro: 88,9–91,5°.
- ESITO: INSUFFICIENTE (entrambe le mediane fuori da 86,4–94,0°).
- Limite: le registrazioni non erano fatte per questo (istruzione "telefono di fronte alla parete"), quindi pochissime immagini con
  uno spigolo. Ma i due casi trovati sbagliano di 10–18° nello stesso verso (angolo più aperto), come ci si aspetta dall'errore
  noto della profondità vista di sbieco (pavimento: +2,3 cm tra 30° e 45°, +7 cm oltre 45°): in uno spigolo visto da 1,5–2 m le due
  pareti sono viste a circa 45°.
- Conseguenza: il metodo "un angolo da una sola immagine dello spigolo" NON si costruisce così com'è.

### Registrazione dedicata P0.21 (scan-20261014-020925, 266 s; regole fissate prima)

- Identificazione dalle foto: tratto 62–89 s = spigolo con mensola e peluche; tratto 110–136 s = spigolo accanto al letto (non
  identificabile con certezza tra C34 e C41). Deviazione: confronto con l'intervallo del metro (88,9–91,5°) come in P0.20, non con
  il singolo angolo.
- Errore di sbieco sul pavimento (zero 0–15°): +0,8 cm a 25–30°, +1,5 a 30–35°, +2,9 a 35–40°, +4,1 a 40–45°, +5,8 a 45–50°
  (punti misurati più vicini, quindi più in alto).
- Q1 (una immagine): spigolo mensola 111,2° (8 immagini), spigolo letto 104,8° (41). ESITO: INSUFFICIENTE.
- Q1 corretta con l'errore del pavimento: 112,5° e 102,7°. ESITO: INSUFFICIENTE (la correzione non aiuta: l'errore di sbieco è
  troppo piccolo per spiegare 13–20°).
- Q2 (pareti di fronte a pochi secondi): NON VALUTABILE (posizione persa a 143–161 s e 183–195 s, proprio nei cambi di parete).
- ESPLORATIVA (dichiarata dopo, non è un esito): piani rifatti escludendo i punti vicini allo spigolo. Spigolo letto: 101,9° con
  20 cm esclusi (38 immagini), 90,8° con 40 cm esclusi (8 immagini); spigolo mensola: nessuna immagine con abbastanza punti oltre
  40 cm. Ipotesi: la profondità del telefono, levigata, "arrotonda" lo spigolo e i piani stimati vicino allo spigolo si piegano
  verso l'interno (angolo più aperto). Da verificare con una prova progettata per questo.

### Prova P0.22 "dal centro della stanza" (scan-20261014-022927, 85 s, regole fissate prima)

- Nessuna perdita di posizione, profondità regolare. Ma da circa 2,3 m le pareti uniformi danno pochi punti con confidenza
  ≥ 224: su 126 immagini una parete con ≥ 1500 punti in circa 20, due pareti insieme in 3 (tutte sullo stesso spigolo, 75–77 s).
- ESITO ANGOLI: NON VALUTABILE (nessuno spigolo con almeno 5 immagini). ESITO LUNGHEZZE: NON VALUTABILE.
- ESPLORATIVA (dichiarata dopo): con confidenza ≥ 128 lo stesso spigolo in 6 immagini, 87,6–93,2°; con ≥ 64 in 7 immagini,
  84,4–93,3° (mediana 87,9°); gli altri due spigoli: 120° (2 immagini) e 90,3° (1 immagine). Variazione da un'immagine all'altra
  di ±3–4°: troppo per l'obiettivo di 5 cm.
- Quadro complessivo della notte, con questo telefono (profondità solo dal movimento) in questa stanza (pareti uniformi, marmo
  lucido): di fronte e vicino i punti sono buoni ma ARCore perde la posizione; di sbieco e vicino allo spigolo la profondità
  arrotonda e sbaglia l'angolo di 13–20°; da lontano mancano punti affidabili. Nessuna configurazione provata raggiunge 5 cm.
