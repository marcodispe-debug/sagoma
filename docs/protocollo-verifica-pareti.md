# Protocollo di verifica della stima delle pareti

Fissato il 2026-10-12, PRIMA di qualsiasi confronto con misure reali. Le scelte qui sotto non si ritoccano in base ai risultati.

## Scopo

Confrontare, rispetto a misure reali della stanza, le stime delle pareti:

1. metodo attuale (piano verticale di ARCore confermato dall'utente, scansione guidata dallo schizzo);
2. R3 (ricostruzione offline dalla profondità);
3. metodo proposto (seme dal mirino solo per scegliere la parete; piano stimato in modo robusto da tutti i punti di profondità
   compatibili, parete vincolata verticale);
4. schizzo senza e con una misura manuale come vincolo.

Il confronto tra metodi senza misure reali dice solo quanto divergono, non quale sia più vicino alla realtà.

## Regole congelate

- **Posa della profondità grezza:** regola principale = posa del **frame corrente** (quello della depth filtrata dello stesso
  keyframe). La posa del timestamp della profondità grezza si usa SOLO come analisi di sensibilità e si riporta a parte.
  Nota: gli indizi a favore del frame corrente vengono da registrazioni di questa stessa stanza.
- **Registrazioni:** scan4 = caso di sviluppo. Le nuove registrazioni della stessa stanza = verifica di ripetibilità (si applica il
  metodo com'è, senza ritocchi). La generalizzazione richiede stanze diverse (almeno una con pareti lisce e una con mobili).
- **Vincolo manuale:**
  - prova principale: si inserisce nello schizzo la misura reale del **muro più lungo** (lunghezza interna, da angolo ad angolo)
    e si valuta l'accuratezza SOLO sulle altre misure, mai su quel muro;
  - prove di controllo: si ripete il confronto usando a turno ciascuna altra misura reale come vincolo e valutando ogni volta
    solo le misure non date al sistema; servono a vedere se il risultato dipende troppo dalla misura scelta;
  - si riportano TUTTI i risultati, senza selezionare a posteriori la combinazione migliore.
- **Separazione:** una misura usata come vincolo non si usa per valutare l'accuratezza nella stessa prova.
- **Prova senza vincolo:** le ricostruzioni si valutano anche senza alcuna misura manuale, su tutte le misure reali.
- **Invariati:** app, soglie approvate, `:core:realDataQuality` (resta "concordanza con R3, non accuratezza reale").

## Etichette dei muri (stanza di prova: bagno, scan4)

Le etichette sono quelle delle pareti R3 di scan4 (`R3Reference`), legate ai muri reali con una foto di scan4 per ciascuna
(keyframe RGB in cui la camera guarda la parete di fronte), CONFERMATE dall'utente il 2026-10-12 (muri e ordine):

| Etichetta | Lato dello schizzo | Muro reale (dalla foto)               | Foto scan4 |
|-----------|--------------------|---------------------------------------|------------|
| W0        | 0                  | muro con lo specchio e la mensola      | 001016.jpg |
| W4        | 1                  | muro con la finestra e il calorifero   | 000486.jpg |
| W2        | 2                  | muro della doccia                      | 000686.jpg |
| W1        | 3                  | muro con la porta                      | 001148.jpg |

Ordine dei lati: 0 → 1 → 2 → 3 = specchio → finestra → doccia → porta (muri consecutivi lungo il perimetro). Angolo k = tra il
lato k−1 e il lato k (angolo 0 = porta/specchio, 1 = specchio/finestra, 2 = finestra/doccia, 3 = doccia/porta). Diagonali:
angolo 0 – angolo 2 e angolo 1 – angolo 3. Le stesse etichette valgono per tutte le registrazioni di questa stanza.

## Misure di riferimento (indipendenti, col metro o meglio con un misuratore laser)

- Lunghezza interna di ogni muro **da angolo ad angolo**: tutta la lunghezza, senza togliere porte, finestre o mobili.
- Le due diagonali (tra gli angoli indicati sopra).
- Larghezza e profondità della stanza, in due punti ciascuna, a circa 1 m d'altezza.
- Solo nelle nuove registrazioni: 2–3 distanze telefono → parete da un punto segnato a terra, con qualche secondo da fermi.

I valori vanno registrati qui sotto, con data e strumento, prima di eseguire i confronti. Si calcola l'impronta SHA-256 del
file (protocollo + valori) e la si conserva SEPARATAMENTE, insieme a una copia del file, fuori dalla cartella del progetto: così
si può verificare in seguito che protocollo e valori non siano cambiati.

## Metriche

- Errore su: distanze tra pareti opposte, lunghezze dei muri, diagonali, angoli tra pareti.
- Metodo proposto, stabilità: sensibilità al seme (tempo e punto del mirino), frame pari contro dispari, quota di punti accettati,
  residuo per fascia di distanza.
- Ogni risultato riporta la registrazione (sviluppo o verifica) e la regola della posa usata (principale o sensibilità).

## Valori di riferimento

### Serie 1 — 2026-10-12, metro, comunicati dall'utente (prima di qualsiasi confronto con queste misure)

Lunghezza interna da angolo ad angolo:

| Etichetta | Lato | Muro      | Misura   |
|-----------|------|-----------|----------|
| W0        | 0    | specchio  | 215,5 cm |
| W4        | 1    | finestra  | 192,0 cm |
| W2        | 2    | doccia    | 215,5 cm |
| W1        | 3    | porta     | 193,0 cm |

Mancano ancora: le due diagonali e larghezza/profondità a circa 1 m d'altezza. Senza diagonali gli angoli NON sono verificabili:
quattro lati non bastano a fissare la forma di un quadrilatero.

Vincolo principale: specchio e doccia hanno la stessa misura (215,5 cm). Parità risolta con una regola fissata ora, prima dei
risultati con vincolo: si usa il lato con l'indice più basso, cioè il **lato 0 (specchio, W0)**.

Valori ricordati in precedenza (2,16 m e 1,93 m, 90°): NON sono riferimento.

### Serie 2 — 2026-10-12, metro, diagonali comunicate dall'utente (prima di qualsiasi confronto con queste misure)

Come comunicate: "da destra porta a sinistra finestra" 290,0 cm; "altra diagonale" 290,5 cm.

Corrispondenza con gli angoli: la prima interpretazione (290,0 cm = diagonale 1–3) era SBAGLIATA. L'utente ha precisato il
2026-10-12: la diagonale di 290,0 cm va dall'angolo porta/specchio all'angolo finestra/doccia. Valori definitivi:

| Diagonale | Tra gli angoli                          | Misura   |
|-----------|-----------------------------------------|----------|
| 0–2       | porta/specchio – finestra/doccia         | 290,0 cm |
| 1–3       | specchio/finestra – doccia/porta         | 290,5 cm |

Coerenza delle sei misure col metro (5 bastano a fissare la forma; la sesta controlla): con i soli triangoli la somma degli
angoli viene 362,07° invece di 360°; il quadrilatero più vicino alle sei misure (minimi quadrati) ha residui di ±0,4–0,7 cm
(RMS 0,53 cm) e angoli (0, 1, 2, 3) 89,97° / 90,03° / 90,23° / 89,77° con la corrispondenza definitiva delle diagonali. Precisione pratica del riferimento: circa ±0,5 cm sulle misure e ±0,3° sugli angoli.

### Serie 3 — 2026-10-13, metro, due stanze nuove comunicate dall'utente (prima di qualsiasi scansione o confronto)

Lati in ordine orario come comunicati; gli angoli si indicano con i due lati che li formano (C12 = tra lato 1 e lato 2).

**Stanza A — camera da letto** (lati misurati ad altezze da terra variabili, non indicate; NON è l'altezza della stanza)

| Lato | Muro          | Misura   |
|------|---------------|----------|
| 1    | porta         | 519,0 cm |
| 2    | mobile grande | 428,0 cm |
| 3    | balcone       | 518,0 cm |
| 4    | letto         | 427,0 cm |

Diagonale misurata: C34 – C12, "dall'angolo letto/balcone all'angolo porta/armadio grande", **674,0 cm**. Si assume che
"armadio grande" sia il lato 2 ("mobile grande"): è l'unica lettura per cui la diagonale unisce angoli opposti.
Diagonale C23 – C41: NON misurata (impossibile secondo l'utente).

**Stanza B — stanzetta sotto** (lati misurati a circa 1,80 m da terra, altezza media: conferma esplicita dell'utente del 2026-10-13, vedi P0.10; scritto in origine "circa 1.80 cm". NON è l'altezza della stanza né del soffitto)

| Lato | Muro          | Misura   |
|------|---------------|----------|
| 1    | porta         | 329,5 cm |
| 2    | finestra      | 311,5 cm |
| 3    | mobile grande | 320,5 cm |
| 4    | letto         | 309,0 cm |

Diagonale misurata: C34 – C12, "dall'angolo letto/mobile grande all'angolo porta/finestra", **447,5 cm**.
Diagonale C23 – C41: NON misurata.

**Forma ricavata dal metro** (4 lati + 1 diagonale fissano il quadrilatero, ma senza la sesta misura NON c'è controllo degli
errori del metro):

| Stanza | C12 | C23 | C34 | C41 | Diagonale C23–C41 (calcolata, non misurata) |
|---|---|---|---|---|---|
| A camera | 89,53° | 90,36° | 89,78° | 90,33° | 670,0 cm |
| B stanzetta | 89,40° | 90,14° | 91,52° | 88,93° | 451,1 cm |

La camera è in squadra entro mezzo grado. La stanzetta NO (lati opposti diversi di 9 e 2,5 cm, angoli tra 88,9° e 91,5°):
è un buon caso per la scelta "Tieni gli angoli della scansione" contro "Approssima a 90°".

**Regole di confronto fissate ORA (prima di qualsiasi scansione):** le regole congelate della seconda stanza restano invariate
(fascia 0,8–2,3 m, correzione = mediana delle mediane per frame con ≥200 punti e ≥5 frame, posa del frame corrente con la posa
al timestamp grezzo solo come sensibilità, obiettivo 5 cm). Il confronto con l'obiettivo dei 5 cm usa SOLO le cinque misure
del metro (4 lati + la diagonale misurata). La diagonale calcolata e gli angoli ricavati si riportano come informazione, non
contano per l'esito. Se una stanza viene scansionata più volte, si riportano TUTTE le scansioni registrate, nessuna scelta a
posteriori.

### Serie 3 — aggiunta 2026-10-13: pareti coperte da mobili (fissata PRIMA delle scansioni)

Comunicato dall'utente: **stanzetta**, lato 3 (mobile grande): il mobile arriva quasi al soffitto, ma resta più di 1 m di parete
libera accanto. **Camera**, lato 2 (mobile grande): un mobile arriva al soffitto; nello spazio rimanente c'è un altro mobile alto
circa 1,7 m dal pavimento, quindi lì il muro è libero solo sopra 1,7 m.

Istruzioni all'utente: inquadrare solo il muro libero (stanzetta: il tratto accanto al mobile; camera: la fascia sopra 1,7 m,
allontanandosi e inclinando il telefono verso l'alto); non confermare una superficie disegnata sul davanti di un mobile.

Regole di analisi:
- Nessuna parete viene esclusa: l'esito dei 5 cm si calcola come per le altre (è quello che otterrebbe un utente).
- Se la retta confermata per la parete coperta sta più di 20 cm dentro la stanza rispetto alla posizione ricavata dal metro, si
  annota "probabile davanti del mobile" come causa; l'esito resta quello calcolato.
- La correzione con la profondità usa la fascia congelata 0,8–2,3 m anche su queste pareti. SOLO per il lato 2 della camera si
  riporta in più, come sensibilità che NON conta per l'esito, la correzione con la fascia 1,8–2,3 m (sopra il mobile).

---

## P0 — Prova offline del METODO IBRIDO (schizzo + pavimento + soffitto + pareti) — fissato il 2026-10-13, PRIMA di qualsiasi registrazione nuova

Stato: protocollo di prova offline. Nessuna modifica all'app; le registrazioni si fanno con l'app installata il 2026-10-13 alle
04:31 (interfaccia a finestre, filtro dei 10° sulle superfici). Le regole qui sotto NON si ritoccano dopo aver visto i dati;
ogni variante non prevista qui si riporta come tale e non conta per l'esito.

Revisione 1 (2026-10-13, prima di qualsiasi registrazione): lo schizzo diventa anche vincolo geometrico MORBIDO (P0.6); varianti
con e senza schizzo sulle stesse registrazioni e metrica del contributo dello schizzo (P0.8, P0.9); pavimento e soffitto come
vincoli verticali, con estensione distinta dalla precisione del bordo (P0.2); la versione dal vivo è un requisito di prodotto,
non una capacità presente (P0.13). Restano invariate tutte le altre decisioni (riconoscimento dei riferimenti, tolleranze,
registrazioni, confronti B/B'/C, criterio di esito rispetto alla guidata).

Revisione 2 (2026-10-13, prima di qualsiasi registrazione): l'altezza della stanza ha come riferimento iniziale quella dichiarata
nello schizzo; la scansione la stima e la confronta, senza imporla (P0.2, P0.6, P0.9). Le altezze al metro non sono più un
requisito: facoltative e separate dai dati dell'algoritmo (P0.10, P0.14). Distinte l'altezza della stanza e l'altezza a cui sono
stati misurati i lati (P0.10).

Indipendenza: le misure del metro (serie 1, 2, 3) NON entrano mai nell'algoritmo, né direttamente né attraverso lo schizzo; servono
solo a valutare il risultato.

### P0.1 Cosa si può registrare oggi (verificato nel codice, sola lettura)

- Registrazione (`ScanRecorder`), solo build di prova e con consenso, avviabile dalla prima finestra della scansione guidata
  ("● Registra"): posa della camera a OGNI frame (circa 30 Hz, timestamp ARCore); ogni 100 ms piani ARCore (tutti i tipi, compresi
  quelli orizzontali verso il basso) e nuvola di punti; foto 640×480 ogni 200 ms; profondità smussata + grezza 160×90 con
  confidenza ogni 500 ms (stima dal movimento: il telefono, motorola edge 70 fusion, NON ha sensore di profondità). Limiti: 6000
  frame (circa 10 minuti) e 2 GB.
- Registro degli eventi (`session-events.jsonl`), da "Registra" fino alla chiusura della schermata: intestazione con lo SCHIZZO
  (vertici in metri, aperture per lato); eventi START_SIDE, WALL_ASSIGNED (lato, retta, incertezze, punti di sostegno),
  WALL_REMOVED, ANSWER, START_DECLARATION, MANUAL_LENGTH, SKETCH_LENGTH_ACCEPTED, BLOCKING, APPLIED, CANCELLED, ciascuno con
  istante della sessione e timestamp ARCore.
- NON esiste oggi un gesto "conferma riferimento" per pavimento e soffitto, né per una parete senza che il motore la proponga
  ("Conferma parete" richiede la parete pronta). Quindi i riferimenti si riconoscono DALLE POSE: un periodo in cui l'utente tiene
  fermo il telefono puntato sulla superficie (regola P0.4). Le foto servono a controllare dopo cosa era inquadrato.

### P0.2 Metodo da provare (offline, sulle registrazioni)

1. Schizzo: forma, ordine delle pareti, aperture e misure indicative dal registro (intestazione). Ha tre ruoli distinti, ciascuno
   definito in P0.6: indizio di associazione, vincolo geometrico morbido, controllo.
2. Pavimento: riferimento = piano stimato nel periodo fermo puntato in basso; poi tutti i punti di profondità della registrazione
   compatibili con quel riferimento, raggruppati in frammenti e collegati se appartengono allo stesso piano. Uscite:
   - quota (e normale) del pavimento: vincolo VERTICALE (base della stanza, fascia d'altezza dei punti di parete, controllo della
     verticalità delle pareti);
   - ESTENSIONE della superficie ricostruita (area coperta, quota parte della pianta coperta): informazione;
   - PRECISIONE DEL BORDO, separata dall'estensione: distanza del bordo ricostruito dalle rette delle pareti, solo riportata. Il
     bordo NON è una misura dell'incontro con le pareti e NON determina la base dei muri (scan5/scan6: si fermava 6–18 cm prima
     del muro su 3 pareti su 4 e usarlo peggiorava la pianta).
3. Soffitto: come il pavimento, puntato in alto. Uscite: quota (vincolo verticale: limite superiore dei punti di parete), estensione
   e bordo come sopra. Provvisorio: nessun dato finora sulla sua affidabilità.
   Altezza della stanza STIMATA = quota del soffitto − quota del pavimento (mediane dei frammenti accettati, sulla verticale). Si
   confronta con l'altezza DICHIARATA nello schizzo (P0.6) e lo scarto si conserva e si riporta; l'altezza dello schizzo non entra
   nel calcolo della geometria e non sostituisce la stima.
4. Pareti: per ogni lato, riferimento = piano stimato nel periodo fermo davanti a una porzione libera; si accettano solo i
   frammenti sul piano di riferimento (P0.5); le superfici parallele spostate si escludono dall'inizio, non si correggono dopo.
5. Pianta: si basa PRINCIPALMENTE sui piani delle pareti e sulle loro intersezioni. Rette dai soli punti accettati; angoli =
   intersezioni dei lati adiacenti; base dei muri = retta della parete (pareti supposte verticali; lo scostamento dalla verticale
   rispetto alla normale del pavimento si riporta come informazione). Pavimento e soffitto vincolano solo in verticale. Lo schizzo
   entra solo come descritto in P0.6, nelle varianti A1 e A2 (P0.8).
6. Fuori ambito: pavimenti o soffitti inclinati, scale, pareti a inclinazione variabile (nessuna delle tre stanze ne ha).

### P0.3 Punti di profondità usati (come nelle analisi precedenti)

Solo profondità grezza "fresca" (non ripetuta), confidenza ≥ 32/255, distanza 0,3–4,0 m, scarto dei pixel di bordo (salto > 8% con
un vicino). Posa del frame corrente (principale); posa al timestamp della grezza come sensibilità, che non conta per l'esito. Ogni
fotogramma pesa UNA volta nelle stime (peso del punto = 1 / punti validi del suo fotogramma).

### P0.4 Riconoscimento dei riferimenti (dai dati registrati)

- Asse ottico = −Z della posa della camera; inclinazione = angolo dell'asse sull'orizzontale.
- "Fermo": su finestre di 0,5 s, rotazione < 10°/s e spostamento < 0,10 m/s; periodo fermo = almeno 2,5 s di fila.
- Pavimento: primo periodo fermo con inclinazione ≤ −40° dall'inizio della registrazione. Soffitto: primo periodo fermo con
  inclinazione ≥ +40° dopo il riferimento del pavimento.
- Parete del lato k: primo periodo fermo con |inclinazione| ≤ 30° che inizia dopo l'ultimo evento di parete precedente (o dopo il
  riferimento del soffitto, per la prima parete) e finisce prima del WALL_ASSIGNED che alla fine occupa il lato k. Se il lato k non
  ha WALL_ASSIGNED: il primo periodo fermo di parete successivo, nell'ordine della guida (dal lato di partenza, lato successivo =
  k+1). Se non c'è: riferimento mancante per quel lato.
- Piano del riferimento: profondità con timestamp nel periodo fermo (almeno 3 fotogrammi), solo la zona centrale dell'immagine
  (metà larghezza × metà altezza), fit robusto (IRLS di Huber, k = 2, come R3). Valido se: almeno 300 punti; dispersione
  (MAD × 1,4826) ≤ 4 cm per le pareti e ≤ 6 cm per pavimento e soffitto; normale entro 10° dall'orizzontale (pareti) o dalla
  verticale (pavimento sotto la camera, soffitto sopra). Riferimento non valido = mancante.
- Riferimento mancante: il metodo FALLISCE per quell'elemento (si riporta), nessuna sostituzione.

### P0.5 Classificazione dei punti e dei frammenti — tolleranze INIZIALI

| Elemento | Il punto entra se | Frammento accettato se | Dubbio (escluso, riportato) | Motivo / stato |
|---|---|---|---|---|
| Parete | distanza dal piano di riferimento ≤ max(6 cm, 3σ_rif) e quota tra pavimento + 10 cm e soffitto − 10 cm (senza soffitto: fino a pavimento + 2,5 m) | mediana delle distanze ≤ 3 cm; inclinazione sul riferimento ≤ 3° (solo se il frammento misura almeno 30 cm in entrambe le direzioni); almeno 200 punti da almeno 3 fotogrammi | mediana > 3 cm, oppure inclinazione 3–10° | rumore osservato sulle pareti 2–3 cm (bagno piastrellato); 3 cm e 3° sono i minimi già usati da R3 per unire frammenti. PROVVISORIA: pareti lisce mai misurate |
| Pavimento | distanza in quota ≤ max(8 cm, 2σ_rif) | mediana ≤ 3 cm; almeno 200 punti da almeno 3 fotogrammi | mediana > 3 cm | dispersione osservata 4 cm (scan5/scan6). Serve solo per la quota |
| Soffitto | come il pavimento | come il pavimento | come il pavimento | PROVVISORIA: nessun dato sul soffitto |

σ_rif = dispersione del riferimento (MAD × 1,4826). Frammenti = celle di 10 cm nel piano di riferimento con almeno 5 punti,
collegate per lato o per spigolo. Le superfici parallele spostate oltre la tolleranza del punto non entrano mai; quelle tra 3 cm e la
tolleranza diventano frammenti "dubbi". Nessun frammento dubbio entra nella pianta.

Rette dai dati: per ogni lato, retta in pianta (IRLS di Huber) sui punti dei frammenti accettati, peso per fotogramma come in P0.3.
Incertezza della retta (σθ, σρ: direzione e posizione): bootstrap sui FOTOGRAMMI (200 ricampionamenti, seme fisso 2026), non sui
punti; con migliaia di punti l'incertezza per punto sarebbe irrealisticamente piccola e annullerebbe ogni vincolo morbido.
Angoli: intersezioni dei lati adiacenti; se due lati adiacenti formano meno di 20° (soglia già esistente) l'angolo è indefinito e
quella variante fallisce sulla stanza. Messa in squadra rigida: la funzione esistente del nucleo (`SketchSquaring`), invariata.

### P0.6 Lo schizzo nella ricostruzione offline (revisione 1)

Fonte: intestazione del registro (vertici in metri, aperture per lato) ed eventi della guida. Le misure dello schizzo sono
INDICATIVE; in questa prova non ci sono misure dichiarate esatte (se in futuro ci saranno, andranno marcate a parte e non
confuse con queste).

Ogni uso ha UN ruolo: I = indizio di associazione (decide a quale lato va un dato, non sposta geometria); V = vincolo geometrico
morbido (entra nel calcolo con un peso pari alla sua incertezza, non vince sui dati ben misurati); C = controllo (si confronta dopo,
non entra nel calcolo).

| Uso | Ruolo | Come | Incertezza dichiarata |
|---|---|---|---|
| Ordine delle pareti e lato di ogni riferimento | I | lato dal WALL_ASSIGNED della guida o dall'ordine della guida (P0.4) | — |
| Adiacenze | I | quali rette intersecare per gli angoli (lato k con k+1) | — |
| Direzione prevista del lato | I | frammenti oltre 10° dalla direzione prevista (schizzo ruotato come le pareti già stimate, medoide) = estranei, come il filtro dell'app | soglia 10° approvata il 2026-10-13 |
| Angolo tra lati adiacenti | V | termine (Δθ_k − α_k)² / σ_α² | σ_α = 2° (PROVVISORIA, sotto) |
| Lunghezza dei lati | V | termine (L_k − L_schizzo,k)² / (0,15·L_schizzo,k)² | 15% della misura dello schizzo (PROVVISORIA, sotto) |
| Altezza della stanza dichiarata | C | riferimento iniziale: si riporta altezza stimata, altezza dello schizzo e scarto; se il soffitto non ha un riferimento valido, "altezza non stimata" (MAI sostituita da quella dello schizzo). Fonte: il campo "Altezza soffitto" della stanza nel progetto (NON è nel registro della registrazione), letto PRIMA dell'analisi; se era rimasto il valore predefinito dell'app (270 cm) si etichetta "predefinito, non dichiarato" | — |
| Lato non acquisito | V, etichettato | la variante A2 può chiudere il perimetro con i soli termini dello schizzo; il lato è "stimato dallo schizzo", NON misurato | come sopra |
| Forma complessiva | C | lunghezze (oltre 15%), angoli (oltre 10°) e vertici dopo il miglior allineamento rigido (oltre max(0,5 m; 30%)): soglie già esistenti nel nucleo | — |
| Associazioni sospette | C | direzione oltre 10° dalla prevista; ordine dei centri dei riferimenti attorno alla stanza diverso dall'ordine dello schizzo; verifiche delle aperture date nella guida | — |
| Lati mancanti | C | lati dello schizzo senza riferimento valido o senza frammenti accettati | — |

Vincolo morbido (variante A2): si stimano direzione e posizione di tutte le rette insieme minimizzando
Σ_lati [(θ_k − θ̂_k)²/σθ_k² + (ρ_k − ρ̂_k)²/σρ_k²] (dati: stime e incertezze bootstrap di P0.5) + Σ termini V della tabella
(Gauss-Newton, partenza dalle rette dei dati). Nessun termine di posizione assoluta: lo schizzo non sa dove sta la stanza.
Conseguenza voluta: una parete ben misurata (σθ di qualche decimo di grado, σρ di 1–2 cm) resta dove dicono i dati; lo schizzo
pesa solo dove i dati sono deboli.

Motivazione delle incertezze (ipotesi, non misure):
- σ_α = 2°: ordine di grandezza ipotizzato delle tolleranze di costruzione, scelto a priori e NON ricavato dalle misure del metro.
  Dichiarazione: gli angoli del metro di bagno (≤ 0,3°), camera (≤ 0,5°) e stanzetta (fino a 1,5°) sono già noti a chi scrive il
  protocollo, quindi la scelta non è del tutto cieca. La stanzetta (non in squadra) è il caso che mostra se il vincolo spinge
  troppo verso lo schizzo.
- 15% sulle lunghezze: è la soglia già esistente per "diverso dallo schizzo"; con dati su entrambe le pareti di un lato il termine
  è quasi ininfluente (σρ di 1–2 cm contro decine di centimetri).

Conflitti: dopo il calcolo, per ogni termine V lo scarto normalizzato z = scarto / incertezza; per ogni parete lo spostamento
dovuto allo schizzo (A2 − A1) in unità della sua σ dei dati. CONFLITTO se |z| > 2 oppure spostamento > 2σ. Il conflitto si conserva
e si riporta con i dati che lo causano (pareti coinvolte, punti, fotogrammi, σθ, σρ, copertura); le rette solo-dati (A1) si
riportano sempre accanto. Nessun risultato sostituisce in silenzio i dati con lo schizzo.

Indipendenza dello schizzo dal metro: lo schizzo si disegna a occhio, con misure arrotondate a 0,5 m, SENZA usare i valori del
metro. Nel resoconto si riportano le misure dello schizzo accanto a quelle del metro.

Scansione guidata (B): NON si accettano le stime dello schizzo né si inseriscono lunghezze a mano; se succede, la pianta guidata si
riporta con l'etichetta "usa misure dello schizzo o a mano" e non conta come scansione.

### P0.7 Registrazioni previste

| Stanza | Registrazioni | Ruolo |
|---|---|---|
| Camera da letto | 3 | verifica (pareti lisce, mobile sul lato 2, lati da 5,2 m) |
| Stanzetta sotto | 3 | verifica (stanza NON in squadra) |
| Bagno | 1 | controllo (pareti piastrellate, già misurato: serie 1 e 2) |

Tutte le registrazioni fatte si riportano, comprese quelle interrotte; nessuna si sceglie a posteriori. Escluse solo per guasto
tecnico (zip illeggibile, profondità assente), con il motivo.

Deviazione dichiarata dal piano della seconda stanza: queste registrazioni contengono le fasi di pavimento e soffitto prima delle
pareti e sono fatte con l'app del 13/10 ore 04:31. Le regole di quell'analisi (fascia 0,8–2,3 m, correzione, posa, soglie) restano
invariate e si applicano a queste registrazioni come confronto (B'), insieme alle regole della serie 3 sui mobili.

### P0.8 Confronti sulle STESSE registrazioni

- (A) Ibrido, in tre varianti sugli stessi punti accettati:
  - A0, SENZA schizzo: lato di ogni riferimento dall'ordine nel tempo; adiacenze = ordine angolare dei centri dei riferimenti
    attorno al loro baricentro; nessun termine V; nessun filtro di direzione dello schizzo.
  - A1, schizzo come INDIZIO (ruoli I): associazione, adiacenze, filtro di direzione; rette solo dai dati. È la variante usata
    per il criterio di esito contro la guidata (decisione già fissata).
  - A2, schizzo come INDIZIO + VINCOLO MORBIDO (ruoli I e V, P0.6).
  Per A1 si riporta anche la messa in squadra rigida (`SketchSquaring`), da confrontare con il vincolo morbido di A2.
- (B) Scansione guidata attuale: pareti del registro (WALL_ASSIGNED) nel nucleo, con gli angoli rilevati e a 90°.
- (B') Guidata + correzione con la profondità: regole congelate della seconda stanza.
- (C) R3 così com'è. Associazione delle pareti R3 ai lati: per ogni lato, la parete R3 lunga almeno 50 cm, con direzione entro 10°
  dal riferimento del lato e più vicina al centro del riferimento, entro 30 cm; altrimenti lato mancante per R3.

### P0.9 Metriche ed esito

- Per stanza, registrazione e metodo: scarto dei 4 lati e della diagonale MISURATA (obiettivo 5 cm; contano solo queste cinque
  misure); angoli rispetto a quelli ricavati dal metro (informazione); diagonale calcolata (informazione, etichettata
  "calcolata"); con gli angoli rilevati e a 90°.
- Altezza della stanza (ibrido): stimata, dichiarata nello schizzo e loro scarto (sempre); contro il metro SOLO se l'utente fornisce
  una misura indipendente (P0.10). Senza metro lo scarto dallo schizzo NON è un errore di accuratezza: dice solo quanto scansione e
  dichiarazione si discostano. L'altezza non entra nel criterio di esito (le cinque misure in pianta).
- Copertura: per ogni parete punti accettati, dubbi ed esclusi, frammenti, estensione coperta; quota e copertura del pavimento;
  distanza del bordo del pavimento dalle pareti (informazione, non entra nella pianta).
- Ripetibilità: per ogni misura, minimo e massimo tra le 3 registrazioni della stessa stanza.
- Controllo sulle foto (descrittivo): per ogni parete, foto dei frammenti dubbi o esclusi più grandi: muro, mobile, vetro, altro.
- Esito del metodo ibrido (fissato ora): "promettente" se, con gli angoli rilevati, almeno 2 registrazioni su 3 per CIASCUNA delle
  due stanze nuove stanno entro 5 cm su tutte e cinque le misure E la mediana dell'errore peggiore è minore di quella di (B) sulle
  stesse registrazioni. Altrimenti "non promettente così com'è", con le cause osservate. Il bagno è un controllo e non decide.
- Solo con esito "promettente" si propone un prototipo dal vivo sul telefono (con misura del costo); nessuna modifica all'app prima.

Il criterio qui sopra confronta l'ibrido con la guidata: NON misura il contributo dello schizzo (A1 lo usa già come indizio). Per
quello, sulle stesse registrazioni:
- Associazione: verità dalle FOTO al momento di ogni riferimento (quale muro fisico era inquadrato: porta, finestra, letto,
  mobile), indipendente dal metro. Per A0 e A1: riferimenti associati al lato giusto, su quanti.
- Chiusura: registrazioni con perimetro chiuso da sole pareti misurate (A0, A1, A2) e quelle chiuse da A2 solo grazie a lati
  "stimati dallo schizzo" (riportate a parte, non contano come misurate).
- Geometria: per ogni registrazione, differenza accoppiata dell'errore peggiore sulle cinque misure del metro: A1 − A0 e A2 − A1;
  stesse differenze sugli angoli rispetto al metro (informazione).
- Influenza e conflitti: spostamento di ogni parete dovuto allo schizzo (in σ dei dati), numero di conflitti e loro cause.
- Criterio fissato ora: "lo schizzo come vincolo morbido aiuta" se la mediana di (A2 − A1) è negativa E nessuna registrazione
  peggiora di oltre 2 cm; "lo schizzo come indizio aiuta" se A1 associa correttamente più riferimenti o chiude più perimetri di A0
  senza peggiorare la mediana dell'errore peggiore. Con 6–7 registrazioni il risultato è DESCRITTIVO, non una prova statistica.
- Una registrazione in cui l'utente ha chiaramente seguito un ordine diverso da quello dello schizzo si riporta: è il caso in cui
  A0 e A1 devono divergere.

### P0.10 Misure aggiuntive

- Altezza della stanza al metro: NON è un requisito (revisione 2). Il riferimento iniziale è l'altezza dello schizzo, che la
  scansione verifica. Limite da dichiarare: l'altezza dello schizzo non è una misura (può essere il valore predefinito o una
  stima), quindi da sola NON permette di dire quanto è accurata l'altezza stimata. Una misura indipendente (metro o laser, 2 punti
  per stanza, con il luogo) serve SOLO per valutare l'accuratezza dell'altezza; è facoltativa. Se l'utente la fornisce: si
  registra con impronta PRIMA dell'analisi, resta separata e non entra mai nell'algoritmo né nello schizzo.
- Due altezze diverse, da non confondere:
  - ALTEZZA DELLA STANZA (pavimento–soffitto): riferimento = valore dichiarato nel campo "Altezza soffitto" dello schizzo; la
    scansione stima l'altezza dal pavimento al soffitto e la confronta con quel valore, senza forzare la geometria a coincidere.
  - ALTEZZA DA TERRA A CUI SONO STATI MISURATI I LATI col metro:
    - stanzetta sotto: circa 1,80 m da terra (altezza media). CONFERMA ESPLICITA dell'utente, 2026-10-13: "1,80 m è l'altezza
      media da terra a cui ho misurato i lati della stanzetta sotto. Non è l'altezza della stanza né quella del soffitto." La
      scrittura originale "circa 1.80 cm" era un errore di unità nella stessa riga del modulo.
    - stanza da letto: altezze variabili (non indicate).
    È un dato della misura dei lati: NON è l'altezza della stanza né del soffitto e non si usa mai come tale.
- Camera: lati misurati ad altezze diverse. Utilizzabili supponendo pareti a piombo; l'incertezza aggiuntiva non è quantificata e
  si dichiara accanto ai risultati della camera. Nessuna nuova misura di lati o diagonali richiesta.
- Facoltativo, senza metro: per ogni parete, finitura (intonaco liscio, piastrelle, carta, legno) e cosa la copre; serve a leggere
  i rischi, non entra nelle metriche.

### P0.11 Rischi e limiti noti prima della prova

- Pareti lisce dipinte: la profondità stimata dal movimento ha bisogno di disegno; potrebbe dare pochi punti o più rumore. È il
  rischio principale: tutte le evidenze favorevoli vengono dal bagno piastrellato.
- Mobili: camera, lato 2, libera solo sopra 1,7 m (fascia stretta, vista di sbieco); se il riferimento cade sul mobile, il metodo
  misura il mobile. Superfici parallele a meno di 3 cm dal muro (battiscopa, pannelli) non si separano.
- Distanze: la profondità è meno precisa con la distanza; la camera ha lati di 5,2 m (punti oltre 4 m esclusi per regola).
- Vetri e specchi: profondità sbagliata (balcone della camera, finestra della stanzetta).
- Pose: deriva di ARCore lungo la stanza; frammenti lontani della stessa parete possono risultare spostati e finire "dubbi".
- Riferimenti dalle pose: se l'utente sta fermo anche in altri momenti, il periodo scelto può essere sbagliato (controllo sulle
  foto, riportato, nessuna correzione).
- Schizzo: se ordine delle pareti o aperture non corrispondono alla stanza, l'associazione è sbagliata; si riporta come
  discrepanza.
- Vincolo morbido: con σ_α = 2° può spingere verso 90° una stanza che non lo è (stanzetta) o, se le incertezze dei dati sono
  sottostimate, non agire affatto; lo dicono gli spostamenti e i conflitti. Uno schizzo disegnato conoscendo il metro renderebbe
  il confronto non indipendente (per questo l'arrotondamento a 0,5 m e il resoconto delle misure dello schizzo).
- Riconoscere quale muro era inquadrato dalle foto (verità dell'associazione) può essere incerto in stanze con pareti simili:
  i casi incerti si riportano come tali.
- Finora una sola stanza vista e un solo telefono: qualunque esito vale per questo telefono e queste stanze.

### P0.12 Istruzioni di registrazione (per l'utente)

Preparazione: schizzo della stanza a 4 lati con porte e finestre, misure stimate A OCCHIO e arrotondate a mezzo metro (NON quelle
del metro); nel campo "Altezza soffitto" l'altezza che dichiareresti per la stanza (se lasci il valore proposto, dillo); luci
accese; telefono carico; partire dal centro della stanza.

1. Avvia la scansione dallo schizzo. Nella prima finestra premi "● Registra" e accetta.
2. Scegli la parete con la porta, poi "Inizia". Non premere ancora "Conferma parete".
3. Pavimento: punta il telefono in basso su un tratto di pavimento libero a circa 1 m davanti a te e TIENI FERMO 4 secondi (conta
   fino a quattro). Poi cammina piano per tutta la stanza inquadrando il pavimento, circa 45 secondi.
4. Soffitto: punta in alto su un tratto di soffitto libero e TIENI FERMO 4 secondi. Poi cammina piano inquadrando il soffitto,
   circa 45 secondi.
5. Per ogni parete, nell'ordine indicato dall'app (parete gialla):
   a. a 1,5–2 m, punta un tratto di parete LIBERO (niente mobili, vetri, quadri) e TIENI FERMO 4 secondi;
   b. passa lentamente il telefono lungo tutta la parete, prima all'altezza del petto, poi più in alto (camera, parete del mobile:
      solo la fascia sopra 1,7 m);
   c. punta il centro, aspetta il giallo e premi "Conferma parete"; rispondi alle domande; "Avanti".
   Non stare fermo in altri momenti: muoviti piano ma senza fermarti.
6. Alla domanda sugli angoli rispondi come faresti. Non accettare "usa la stima dello schizzo" e non scrivere misure a mano.
7. Menu → Dettagli e strumenti → "■ Ferma" → "Esporta". Nomi: camera1, camera2, camera3, stanzetta1, stanzetta2, stanzetta3,
   bagno1. Resta sotto i 10 minuti.
8. Se qualcosa va storto, esporta comunque e passa alla registrazione successiva: conta anche quella.

### P0.13 Versione dal vivo: REQUISITO DI PRODOTTO, non capacità presente

La prova P0 è offline: dice se lo schizzo migliora associazione, geometria e chiusura SUI DATI REGISTRATI. Non dimostra che
l'interfaccia o il confronto in tempo reale esistano o funzionino.

Requisito per una versione futura: durante la scansione l'app confronta continuamente i dati con lo schizzo, mostra la parete
corrente e quelle ancora da acquisire, segnala subito incoerenze (direzione, lunghezza, ordine) e parti mancanti, e lascia
all'utente il controllo delle discrepanze.

Stato oggi (app del 13/10 ore 04:31), per non confondere:
- ESISTE: schizzo con parete attiva (gialla), fatte (verdi) e mancanti; filtro dei 10° sulla direzione, ma sui piani di ARCore e
  non sulla profondità; controlli di discrepanza con lo schizzo DOPO la conferma delle pareti (lunghezze, angoli, direzioni,
  vertici), sempre sulle pareti dei piani ARCore.
- NON ESISTE: profondità letta dal vivo; riferimenti confermati di pavimento e soffitto; classificazione dei punti rispetto a un
  riferimento; vincolo morbido dello schizzo; confronto continuo della geometria ricostruita con lo schizzo.
Il lavoro dal vivo si progetta solo dopo l'esito di P0 (P0.9) e con la misura del costo sul telefono.

### P0.14 Ipotesi dichiarate dall'utente (2026-10-13, prima di qualsiasi registrazione)

- Pareti A PIOMBO, pavimento e soffitto A BOLLA, in tutte e tre le stanze (ipotesi, non misurata). Conseguenze: le pareti si
  stimano come piani verticali (come già previsto) e la loro distanza non dipende dall'altezza, quindi le misure della camera
  prese ad altezze diverse si confrontano direttamente; pavimento e soffitto valgono solo per la loro quota. Lo scostamento
  misurato dalla verticale e dall'orizzontale si riporta comunque come informazione, senza cambiare il calcolo.
- Altezza della stanza: sostituito dalla revisione 2 (P0.10). Il riferimento è l'altezza dello schizzo; la misura al metro è
  facoltativa e serve solo a valutare l'accuratezza dell'altezza.

### P0.15 Registrazioni ricevute e decisioni PRIMA di aprirle (2026-10-13, ore 17)

Copiate dal telefono (cartella Download) in sola lettura; nessuna registrazione è stata aperta prima di questa sezione.

| File | Ora sul telefono | Stanza | SHA-256 (inizio) |
|---|---|---|---|
| scan1 stanzetta.zip | 15:14 | stanzetta | 72dea29d… |
| scan1 sranzetta.zip | 16:31 | stanzetta (nome con errore di battitura) | 1be5005b… |
| scan2 stanzetta.zip | 16:35 | stanzetta | 98f21126… |
| scan3 stanzetta.zip | 16:41 | stanzetta | 8aa5fbd7… |
| scan1 stanza da letto.zip | 16:49 | camera | e144424b… |

Deviazioni dal piano P0.7: bagno 0 registrazioni (previste 1); camera 1 (previste 3); stanzetta 4 (previste 3). La registrazione
delle 15:14 precede le revisioni 2 e 3 del protocollo, che riguardano schizzo e altezze; riconoscimento dei riferimenti, punti e
tolleranze non sono cambiati da allora: si analizza come le altre. Tutte e 5 si riportano.

Schizzi (progetto "prova1" sul telefono, letto in sola lettura):
- "Altezza soffitto" non impostata in nessuna delle due stanze: vale il predefinito dell'app, 270 cm. Etichetta: "predefinito, non
  dichiarato". Il confronto dell'altezza con lo schizzo è quindi poco informativo; nessuna misura al metro dell'altezza.
- Misure: stanzetta 345 × 325 cm, camera 535 × 443 cm (vertici del progetto; con il muro predefinito di 15 cm l'interno è circa
  330 × 310 e 520 × 428). Sono praticamente le misure del METRO (stanzetta 329,5/311,5/320,5/309; camera 519/428/518/427), non
  misure a occhio arrotondate a 0,5 m come previsto da P0.6. Le misure dello schizzo NON sono indipendenti dal metro.

Conseguenze, fissate ora:
- Regola di contaminazione: se nel registro un lato interno dello schizzo dista meno di 5 cm dalla misura del metro corrispondente,
  il termine LUNGHEZZE del vincolo morbido per quella stanza è "non indipendente".
- A2 principale = solo il termine ANGOLI (angoli dello schizzo a 90°: non ricavati dal metro, che per la stanzetta dà 88,9–91,5°).
  A2 con anche le lunghezze si calcola e si riporta a parte con l'etichetta "NON indipendente: schizzo con misure del metro"; non
  entra in nessun criterio.
- I ruoli di indizio (associazione, adiacenze, filtro dei 10°) non usano misure: invariati. I controlli sulle lunghezze si
  riportano con la stessa etichetta.
- Criterio di esito (P0.9): con UNA sola registrazione della camera la regola "almeno 2 su 3 per ciascuna stanza" non è
  applicabile: l'esito del metodo ibrido è "non valutabile secondo P0.9"; i risultati si riportano in modo descrittivo. Per la
  stanzetta (4 registrazioni) la regola diventa "almeno 3 su 4".
- Il controllo del bagno manca: nessun confronto con la stanza già nota.

### P0.16 Nessuna conferma forzata (2026-10-13, sera)
Decisione dell'utente dopo la rilettura della prima prova del prototipo (scan-20261013-184305: lato 1 confermato con l'avviso
"superficie dietro", lato 3 con "fuori dallo schizzo", lato 2 mai acquisito, scatola incompleta):
- dal vivo nessun avviso si può superare: si conferma solo una superficie PRONTA; con un avviso si inquadra un'altra superficie o si
  rifà il riferimento. "Fuori dallo schizzo" era già stato tolto dopo la prima prova; ora è tolto anche "superficie dietro".
- Le registrazioni vecchie con avvisi superati si rianalizzano come sono (lo stato "associazione incerta" resta solo per loro).
- Rischio dichiarato prima della prova: il controllo "superficie dietro" guarda tutta l'inquadratura e scatta anche vicino a
  finestre e porte (prima prova: sul lato della finestra; terza prova: una volta sul lato della porta). Se una parete con finestra
  non si riesce a confermare inquadrando un tratto libero, lo si riporta; le soglie (10 cm–1 m, 300 punti) non si cambiano senza
  approvazione.

### P0.17 Area larga con soli punti buoni, superfici colorate (2026-10-13, sera)
Decisione dell'utente: preso il riferimento (zona centrale dell'immagine, come prima), pavimento, soffitto e pareti raccolgono
dall'IMMAGINE INTERA ma solo i punti buoni (distanza ≤ 2 m, confidenza ≥ 224, angolo di vista ≤ 30°; soglie invariate). Prima:
così solo il pavimento; soffitto con tutti i punti; pareti con tutti i punti o, a scelta dal menu, punti buoni entro 0,75 dal
centro (scelta tolta). Le superfici viste si colorano dal vivo a celle di 10 cm: verde = frammento accettato (registrato come
buono), arancione = dubbio, bianco tenue = visto ma ancora insufficiente. Il registro dichiara nell'intestazione la scelta dei punti.

### P0.18 Giudizio per cella (2026-10-14, notte)
Decisione dell'utente dopo le prove delle 23:51–00:01 (la quota del pavimento vista dal telefono cambia di 3–5 cm da un punto
all'altro: giudicato come un unico frammento, il pavimento diventava tutto dubbio dopo 10–20 s). Su pavimento, soffitto e pareti:
- ogni cella di 10 cm con almeno 20 punti da almeno 3 immagini (soglia NUOVA, provvisoria) è accettata se la mediana dei suoi punti
  sta entro 3 cm dal riferimento (limite invariato), dubbia se oltre; con meno punti resta da completare;
- i frammenti sono celle collegate con lo STESSO giudizio; un frammento di celle accettate resta accettato con almeno 200 punti e 3
  immagini e, se esteso almeno 30 cm nelle due direzioni, inclinazione entro 3° (limiti invariati);
- aree, quote e rette delle pareti usano solo i frammenti accettati, come prima.
Cambia rispetto a P0 la regola dei frammenti (prima: tutte le celle collegate giudicate insieme sulla mediana). Le rianalisi
precedenti restano valide per la regola di allora.

### P0.19 Ordine pavimento → pareti → soffitto; controlli del riallineamento (2026-10-14, notte)
Decisione dell'utente dopo la prova delle 00:33 (posizione persa dal 60% del tempo durante il soffitto, rotazioni del mondo fino a
171°; poi riallineamenti per il 74% del tempo delle pareti, con correzioni impossibili accettate):
- fasi: pavimento → pareti → soffitto → riepilogo. Confermate tutte le pareti si resta nella fase pareti (per percorrerle) fino a
  "Avanti: soffitto". Fuori dalla fase pareti le pareti non raccolgono punti e un salto non chiede il riallineamento (le immagini
  si controllano come dopo un salto). Dal riepilogo si torna alle pareti se ne mancano, altrimenti al soffitto.
- riallineamento: le pareti riconfermate devono dare la stessa rotazione entro 5° (soglia NUOVA, provvisoria) e lo spostamento
  deve stare entro due diagonali dello schizzo; altrimenti la riconferma è rifiutata e si ricomincia dalla prima parete.
- registro: ogni perdita e ripresa della posizione di ARCore, con il motivo.

### P0.20 Verifica offline: angoli misurati localmente (fissata PRIMA di eseguirla, 2026-10-14 notte)
Domanda: l'angolo tra due pareti adiacenti, preso da UNA sola immagine di profondità in cui si vedono entrambe, è giusto?
(Base del metodo "misure locali": non dipende dalla coerenza di ARCore nel tempo.)
- Dati: TUTTE le registrazioni della stanzetta con profondità grezza disponibili sul computer (18:43, 21:20, 21:56, 23:51,
  23:56, 23:59, 00:33, 01:25, 01:34). Nessuna esclusione a posteriori.
- Punti: confidenza ≥ 224, distanza ≤ 3 m. Per ogni immagine: ricerca sequenziale (RANSAC, 400 tentativi, tolleranza 2 cm) di
  due piani verticali (normale entro 10° dall'orizzontale dopo il raffinamento), ciascuno con almeno 1500 punti; angolo tra le
  normali tra 60° e 120°. Angolo interno = 180° − angolo tra le normali rivolte verso la camera.
- Gruppi: nella stessa registrazione, immagini consecutive con l'intersezione dei due piani (in pianta) entro 50 cm e senza
  perdita di posizione in mezzo formano un "angolo osservato"; si tengono i gruppi con almeno 5 immagini. Per ciascuno: mediana e
  scarto interquartile dell'angolo.
- Riferimento: gli angoli del metro sono tutti tra 88,9° e 91,5° (C12 89,40, C23 90,14, C34 91,52, C41 88,93). L'identità
  dell'angolo non è nota offline: si confronta con l'intervallo.
- Esito: PROMETTENTE se tutte le mediane dei gruppi stanno tra 87,9° e 92,5° (intervallo del metro ± 1°); INSUFFICIENTE se almeno
  una sta fuori da 86,4°–94,0° (± 2,5°); altrimenti INCERTO. Si riportano anche numero di gruppi e dispersione.
- Dettaglio aggiunto prima dell'esecuzione: per non scambiare pavimento e soffitto per pareti si usano solo i punti tra 1,0 m
  sotto e 0,9 m sopra la camera; la ricerca dei piani usa un sottoinsieme fisso di 1000 punti per immagine (il conteggio finale
  dei punti del piano usa tutti i punti). Interruzione del gruppo: posizione persa o salto oltre 10 cm tra due pose.

### P0.21 Registrazione dedicata "spigoli e pareti di fronte" (regole fissate PRIMA della registrazione, 2026-10-14 notte)
Sequenza chiesta all'utente (stanzetta, una registrazione): spigoli porta/finestra (C12), mobile grande/letto (C34), letto/porta
(C41) — C23 mobile grande/finestra SALTATO (mobile davanti, comunicato dall'utente); per ognuno: 2 m, spigolo al centro, 5 s fermo,
poi arco laterale ±1 m. Poi pareti di fronte a 1,5 m, 5 s ferme, nell'ordine porta, finestra, mobile grande (tratto libero), letto,
girandosi subito dall'una all'altra. Poi pavimento vicino ai piedi e a circa 2 m. Riferimenti del metro: C12 89,40°, C23 90,14°,
C34 91,52°, C41 88,93°.
- Q1 (spigolo da una sola immagine): come P0.20 (stessi filtri e soglie). Per ogni spigolo: mediana degli angoli e confronto con
  il suo angolo del metro; si riporta anche l'errore in funzione dell'angolo di vista delle due pareti. Correzione dell'errore di
  sbieco (seconda analisi, dichiarata ora): dal pavimento, mediana della quota per fasce di 5° di angolo di vista, con zero nella
  fascia 0–15°; l'errore in quota diventa errore in distanza (Δd = Δh / cos θ) e si toglie ai punti delle pareti secondo il loro
  angolo di vista; poi si rifà Q1.
- Q2 (pareti di fronte, pochi secondi tra l'una e l'altra): per ogni parete, piano dai punti buoni (≤ 2 m, confidenza ≥ 224,
  ≤ 30°) dei tratti fermi (camera sotto 5 cm/s per almeno 3 s); angolo tra pareti adiacenti solo se tra i due tratti non c'è
  perdita di posizione né salto oltre 10 cm; confronto con il metro (C12, C23, C34, C41).
- Esito, separato per Q1, Q1 corretta e Q2: PROMETTENTE se tutti gli angoli disponibili stanno entro ±1° dal metro; INSUFFICIENTE
  se almeno uno è oltre ±2,5°; altrimenti INCERTO. Spigoli o coppie senza dati: "non valutabile", non contano.

### P0.22 Prova "dal centro della stanza" (regole fissate PRIMA della registrazione, 2026-10-14 notte)
Nata dall'analisi ESPLORATIVA di P0.21 (spigolo letto: 90,8° escludendo i punti entro 40 cm dallo spigolo). Registrazione: utente
al centro della stanzetta, inquadra in ordine gli spigoli porta/finestra (C12), mobile grande/letto (C34), letto/porta (C41), da
circa 2,3 m, 5 s ciascuno con piccolo ondeggiamento laterale, girandosi sul posto tra uno e l'altro. C23 saltato (mobile).
- Piani: per ogni immagine con due pareti (filtri P0.20: confidenza ≥ 224, distanza ≤ 3 m, fascia −1,0/+0,9 m dalla camera,
  RANSAC 400 su 1000 punti, tolleranza 2 cm, ≥ 1500 punti, verticali entro 10°, angolo tra normali 60–120°), poi piani rifatti
  SOLO con i punti a più di 40 cm (in pianta) dall'intersezione, almeno 800 per parete. Angolo interno e spigolo (intersezione).
- Spigolo: mediana degli angoli e mediana della posizione dello spigolo sulle immagini del suo tratto (identificato dall'ordine e
  dalle foto); tratto valido solo con almeno 5 immagini.
- Lunghezze: SOLO tra spigoli dello stesso tratto senza perdita di posizione né salto oltre 10 cm tra di loro: C41–C12 = lato
  porta (metro 329,5 cm), C34–C41 = lato letto (309,0 cm), C12–C34 = diagonale misurata (447,5 cm).
- Esito angoli: PROMETTENTE se ogni angolo valido sta entro ±1° dal suo valore del metro (C12 89,40, C34 91,52, C41 88,93);
  INSUFFICIENTE se uno è oltre ±2,5°; altrimenti INCERTO. Se lo spigolo del letto non è identificabile tra C34 e C41 si confronta
  con entrambi e si riporta il peggiore.
- Esito lunghezze: PROMETTENTE se ogni lunghezza valida sta entro 5 cm dal metro; INSUFFICIENTE se una è oltre 10 cm; altrimenti
  INCERTO. Senza coppie valide: non valutabile.
