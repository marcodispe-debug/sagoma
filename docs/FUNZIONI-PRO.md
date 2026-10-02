# Funzioni dei programmi professionali → Sagoma

Riferimenti: ArchiCAD, Revit, Vectorworks, AutoCAD Architecture (progettazione architettonica), SketchUp, Chief Architect,
Cedreo, Homestyler, Planner 5D, Roomle/Pytha (interior design e arredo), magicplan / CamToPlan (rilievo).

Legenda: ✅ fatto · 🟡 in parte · ⬜ da fare. **P1** = priorità alta per i professionisti.

## A. Disegno di precisione (CAD)

| Funzione | Stato | Note |
|---|---|---|
| Snap agli oggetti: estremità, punto medio, centro, allineamenti, angolo retto, 0°/45°/90° | ✅ | motore unico (`SnapEngine`) con **guide tratteggiate e nome dell'aggancio**: angoli delle stanze, metro (anche spigoli delle facce dei muri), arredi (allineamento), disegno dei muri; travi e colonne con i loro agganci. Mancano intersezione e perpendicolare |
| Inserimento numerico mentre si disegna (lunghezza, angolo, coordinate, spostamento di x cm) | 🟡 | lunghezza e angolo nel disegno dei muri; manca durante il trascinamento degli oggetti. **P1** |
| Griglia regolabile e snap alla griglia | ⬜ | |
| Muri disegnati a polilinea (muro per muro), muri curvi | 🟡 | "Muro per muro": angoli sulla pianta con agganci, lunghezza e angolo scritti, anteprima col mouse, chiusura sul primo angolo. Mancano i muri curvi |
| Selezione multipla, sposta / ruota / specchia / copia / serie, allinea e distribuisci | ✅ | rettangolo finestra/intersezione, Ctrl+clic, Ctrl+A, Canc, Esc |
| Copia e incolla (impianti, aperture, arredi, strutture, stanze, gruppi; anche su altri piani) | ✅ | ⧉ Copia / 📋 Incolla, Ctrl+C / Ctrl+V (incolla subito sotto il mouse), incolla ripetuto |
| ⧉ Duplica su ogni elemento (scheda Info, Ctrl+D) | ✅ | stessa impostazione, accanto all'originale, subito selezionato |
| Scelta tra oggetti sovrapposti (2D) | ✅ | "Cosa vuoi selezionare?" quando ≥ 2 oggetti nello stesso punto |
| Arredi specchiati (sinistra ↔ destra) | ✅ | 2D, 3D computer e telefono |
| Stanza ricavata dentro un'altra (ripostiglio) contro il muro esterno | ✅ | facciata continua, faccia interna della stanza piccola |
| Prova di resistenza automatica dell'editor | ✅ | EditorStressTest: sessioni di operazioni a caso, salvataggio/rilettura, id unici |
| Blocca oggetti, gruppi | ⬜ | |
| Layer (arredi, impianti, quote, testi, sfondo, strutture) con visibilità e blocco | ✅ | menu ⋮ → Livelli: nascosto (né pianta né PDF/PNG; arredi anche nel 3D) o bloccato |
| Quote automatiche interne ed esterne | ✅ | esterne progressive e totali nel PDF |
| Quote manuali: lineari, allineate, angolari, raggi, catene, quote di livello | 🟡 | allineate (anche diagonali) con aggancio, linea spostabile, scritta personalizzata, nel PDF; mancano angolari, catene, livelli |
| Testi, etichette, frecce, simbolo del nord, nuvole di revisione | 🟡 | testi (3 dimensioni, orizzontali/verticali) con freccia di richiamo, nel PDF; mancano nord e nuvole |
| Metro, distanza tra oggetti | ✅ | |
| Annulla / ripeti | ✅ | |
| Scorciatoie da tastiera e mouse (web, tablet con tastiera) | 🟡 | rotellina per lo zoom |

## B. Architettura

| Funzione | Stato | Note |
|---|---|---|
| Muri con spessore per stanza e per muro | ✅ | |
| Stratigrafia dei muri (intonaco, laterizio, isolante…) e retini di sezione | ⬜ | |
| **Stato di fatto / progetto**: demolizioni in giallo, costruzioni in rosso, tavola di confronto | ⬜ | standard delle pratiche edilizie italiane (CILA/SCIA). **P1** |
| Porte, finestre, balconi, aperture | ✅ | |
| Abaco serramenti (tabella con codice, misure, verso, finitura) | 🟡 | misure nel PDF; manca l'abaco. **P1** |
| Scale (dritte, a L, a U, chiocciola, ventaglio), ringhiere | ✅ | |
| Rampe, gradini singoli, dislivelli tra stanze | ⬜ | |
| Colonne, travi | ✅ | |
| Soffitti inclinati (mansarde) | ✅ | |
| Controsoffitti, ribassi, velette, gole luminose | ⬜ | |
| Tetti (a falde, piani) | ⬜ | |
| Nicchie, cavedi, camini, cappotto | ⬜ | |
| Più piani | ✅ | |
| **Sezioni** verticali e **prospetti** esterni | ⬜ | **P1** |
| Giardino, terreno, recinzioni | 🟡 | prato attorno alla casa |

## C. Interior design

| Funzione | Stato | Note |
|---|---|---|
| Catalogo arredi 3D | ✅ | 892 generici (CC0) |
| Prodotti reali dei brand (prezzo, varianti, link) | ⬜ | affiliazioni. **P1** (piattaforma) |
| Materiali fotografici per pavimenti e pareti | ✅ | 188 |
| **Posa delle piastrelle**: formato, fuga (larghezza e colore), dritta / sfalsata / diagonale / spina di pesce / a correre, punto di partenza, conteggio pezzi e tagli | ⬜ | **P1** |
| Rivestimenti a parete fino a un'altezza, boiserie, carta da parati | ✅ | |
| **Viste di parete quotate** (prospetto interno di una parete: cucina, bagno, armadi) | ⬜ | **P1** |
| Arredi su misura parametrici: cucine componibili, armadi, librerie, top | ⬜ | **P1** |
| Illuminazione: lampade reali con luce | ✅ | |
| Schema elettrico (prese, interruttori, punti luce, collegamenti) e legenda | 🟡 | simboli sì, collegamenti no |
| Calcolo illuminotecnico (lux) | ⬜ | |
| Moodboard, palette colori del progetto | ⬜ | |
| Tende, tessili, quadri, specchi, decori | 🟡 | nel catalogo |
| Varianti (proposta A / B) e confronto affiancato | 🟡 | duplica progetto; manca il confronto |

## D. Visualizzazione

| Funzione | Stato | Note |
|---|---|---|
| 3D in tempo reale, camminata, sole secondo l'ora, luci | ✅ | |
| Viste salvate (inquadrature) | ⬜ | **P1** |
| Sezione 3D (taglio orizzontale/verticale), vista dall'alto ortogonale, assonometria | ⬜ | |
| Render fotorealistici | ⬜ | su server, a pagamento |
| Panorami 360°, video della camminata | ⬜ | a pagamento |
| Link 3D interattivo per il cliente (nel browser) | ⬜ | **P1** (piattaforma) |
| Realtà virtuale / aumentata (arredo nella stanza vera) | ⬜ | |

## E. Documentazione ed esportazione

| Funzione | Stato | Note |
|---|---|---|
| Tavole PDF in scala con cartiglio, scala grafica, più piani | ✅ | |
| Impaginazione libera delle tavole (pianta + prospetti + 3D + legenda sullo stesso foglio) | ⬜ | |
| **DXF / DWG** (esportare e importare, per lavorare con AutoCAD e gli altri) | ⬜ | **P1** |
| IFC (BIM) | ⬜ | |
| Modello 3D (GLB, OBJ) | 🟡 | GLB interno per il 3D |
| PNG | ✅ | |
| Computo metrico (pavimenti, pareti, battiscopa), lista arredi, CSV | ✅ | |
| Computo con prezzi (prezziario, voci personalizzate), preventivo per il cliente | ⬜ | **P1** |
| Abachi (serramenti, arredi, finiture) | 🟡 | |

## F. Rilievo

| Funzione | Stato | Note |
|---|---|---|
| Stanza da lati e diagonali | ✅ | |
| Pianta esistente di sfondo tarata (foto, PDF) | ✅ | |
| **Metro laser Bluetooth** (Leica DISTO, Bosch GLM): la misura va nel campo | ⬜ | come magicplan. **P1** |
| Scansione AR della stanza (Android, ARCore) | ⬜ | |
| LiDAR (iPhone Pro, RoomPlan) | ⬜ | serve Mac/iPhone |
| Riconoscimento automatico dei muri da foto o PDF | ⬜ | |
| Foto e note di sopralluogo appuntate sulla pianta | ⬜ | |

## G. Studio e collaborazione

| Funzione | Stato | Note |
|---|---|---|
| Più progetti, dati del cliente, varianti, esporta/importa | ✅ | |
| Cloud, sincronizzazione tra dispositivi | ⬜ | Fase 1 |
| Condivisione col cliente (vede, commenta, approva) | ⬜ | |
| Commenti appuntati su un punto della pianta | ⬜ | |
| Cronologia delle versioni | ⬜ | |
| Studio con più persone (ruoli, permessi) | ⬜ | |
| Tariffario, preventivi, pagamenti, recensioni (marketplace) | ⬜ | Fase 3 |
| Versione web / desktop per lavorare al computer | 🟡 | in corso (Kotlin Multiplatform) |
