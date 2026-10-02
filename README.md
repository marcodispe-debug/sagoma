# Sagoma — Editor planimetria (Android)

App Android nativa (Kotlin + Jetpack Compose) che implementa l'editor planimetria descritto in
[docs/specifica-editor-planimetria.md](docs/specifica-editor-planimetria.md).

## Build

Requisiti: Android SDK (platform 37) e JDK 17+ (va bene quello incluso in Android Studio).

```bash
./gradlew assembleFreeDebug assembleProDebug   # APK in app/build/outputs/apk/{free,pro}/debug/
./gradlew testFreeDebugUnitTest testProDebugUnitTest
./gradlew installFreeDebug installProDebug
```

Oppure apri la cartella in Android Studio ed esegui la configurazione `app` (variante freeDebug o proDebug).

## Versioni free e pro

Due app dallo stesso codice (flavor `edition`), installabili una accanto all'altra con dati separati:

| | free (`com.sagoma.planimetria`) | pro (`com.sagoma.planimetria.pro`, "Sagoma Pro") |
|---|---|---|
| Colori pareti | tavolozza di 12 colori | tavolozza estesa (~50 colori), colori recenti, selettore libero (tinta/saturazione/luminosità, codice esadecimale) |
| Rivestimenti | — | carta da parati, piastrelle 20×20, diamantate, mattoni, pietra, boiserie, perlinato: colore e altezza da terra (o tutta la parete), su pareti della stanza, singola parete, facciata e muri singoli |
| Motore 3D | OpenGL ES 2 semplice (`PlanRenderer3D`) | Filament 1.77 (Google, Apache 2.0): materiali fisici, sole con ombre, luce ambiente, SSAO, MSAA+FXAA, tone mapping neutro; ripiega su OpenGL se il telefono non ha GLES 3 |
| Arredi | — | catalogo di 892 arredi (834 modelli 3D e 58 tappeti fatti con i materiali) (soggiorno, pranzo, cucina, camera, bagno, studio, luci, elettrodomestici, decori, piante, esterni) con ricerca e miniature; in pianta la vista dall'alto del modello; trascinamento con aggancio a muri e altri mobili (schienale al muro), maniglia di rotazione con scatto a 90°, misure/altezza da terra, duplica; nel 3D si toccano e bloccano chi cammina |

- `Edition.isPro` (in `src/free` e `src/pro`) nasconde le funzioni pro nell'interfaccia; il modello dati è comune, così
  un file salvato dalla pro si apre anche nella free (con i colori di tavolozza, senza rivestimenti e arredi visibili).
- `SceneRenderer` (main) è l'interfaccia del disegno 3D: `GlSceneRenderer` nella free, `FilamentSceneRenderer`
  (src/pro) nella pro. La scena passa a Filament come glTF binario in memoria (`geometry/Glb`): tre oggetti
  (opachi, lampade senza ombreggiatura, vetri trasparenti); tocchi, selezione e camminata restano sulla CPU (`Scene3D`).
- Modelli degli arredi, in `app/src/pro/assets/furniture/` (~645 MB, APK pro ~650 MB): per ogni modello `<id>.glb`, `<id>.png`
  (miniatura) e `<id>_top.png` (vista dall'alto), più `catalog.json` con nome, categoria, misure reali, fonte, autore e licenza.
  - **Poly Haven** (polyhaven.com), 130 modelli fotorealistici, licenza **CC0**.
  - **Sweet Home 3D**, librerie 1.9.3 da SourceForge: *BlendSwap-CC-0* (pubblico dominio) e *Scopia* (**CC-BY 3.0**:
    va citato "Space Mushrooms e Scopia Visual Interfaces Systems", lo fa la schermata "Fonti e licenze" del catalogo);
    215 modelli di cucina, bagno, camera, soggiorno, studio e luci, esclusi gli oggetti in `tools/furniture/sh3d-exclude.txt`.
  - **Objaverse** (raccolta di modelli Sketchfab, Allen AI), 489 modelli scelti a mano tra i più apprezzati di 45
    categorie, solo licenza **CC0** o **CC-BY 4.0** (autore e collegamento di ciascuno nei crediti); elenco, nomi, larghezze
    reali e rotazioni in `tools/furniture/objaverse.tsv`, valori tipici per categoria in `objaverse-categories.tsv`,
    metadati (licenza, autore, apprezzamenti) con `tools/furniture/ObjaverseMeta.java`.
  - Preparazione: `tools/furniture/PackModels.java` (JDK 17+, senza dipendenze) converte glTF, GLB e OBJ in GLB compatti
    (texture fino a 1024 px, normali e uv quantizzati con `KHR_mesh_quantization`, normali levigate con angolo di 40° per gli OBJ,
    geometria originale; MAX_TRIANGLES permette di semplificarla con il collasso di spigoli a errore quadrico), disegna miniature e viste dall'alto e scrive il catalogo; nomi e categorie Poly Haven in
    `tools/furniture/polyhaven.tsv`.
    ```bash
    java tools/furniture/PackModels.java polyhaven <cartella modelli> tools/furniture/polyhaven.tsv <uscita>
    java tools/furniture/PackModels.java sh3d <libreria.sh3f> <uscita> "Kitchen|Bathroom|Bedroom|Living room|Office|Lights" tools/furniture/sh3d-exclude.txt
    java tools/furniture/PackModels.java objaverse <cartella dei .glb> tools/furniture/objaverse.tsv <uscita>
    ```
  Ogni modello si adatta a larghezza, profondità e altezza scelte (scala separata sui tre assi, base sul pavimento o
  all'altezza da terra).
- Luce del giorno e luci della casa (pulsanti "☀ ora" e "💡 Luci" nel 3D): il sole si calcola dall'ora scelta (nord in
  alto nella pianta, alba a est, tramonto a ovest, altezza massima 55°) con colore e forza che cambiano; le ombre del sole
  restano anche camminando, perché i soffitti (facce rivolte in basso, materiale `lit_down` a due facce) le proiettano e il
  sole entra solo dalle finestre. Ogni finestra aggiunge una luce morbida verso la stanza (proporzionale alla sua area e
  alla luce del giorno) e dentro casa la luce ambiente diffusa si riduce. Plafoniere, faretti, lampadari, neon, strisce
  LED e le lampade del catalogo sono luci vere (`Scene3D.lights`), accese quando fa buio, sempre o mai.
- Cielo e riflessi: foto a 360° di Poly Haven
  (CC0: *kloofendal_48d_partly_cloudy_puresky*, *lilienstein*, *lebombo*) in `app/src/pro/assets/env/*.ibl`, preparate
  con `tools/furniture/PackEnv.java` (armoniche sferiche per la luce diffusa + cubo per riflessi e cielo; i livelli
  ruvidi dei riflessi li calcola Filament all'avvio con `generatePrefilterMipmap`).
- Materiali fotografici (pavimenti della stanza e rivestimento "Materiale fotografico" di pareti, facciata e muri
  singoli, prato attorno alla casa): 188 materiali di ambientCG e Poly Haven (CC0) in `app/src/pro/assets/materials/`
  (1024 px, 118 MB; i tessuti e le moquette diventano anche tappeti rettangolari e tondi), preparati con `tools/furniture/PackMaterials.java` da `tools/furniture/materials.tsv` (nome, categoria,
  lato reale della foto). Nel 3D sono superfici a parte (`Scene3D.textured`, uv in ripetizioni della foto) disegnate
  da `TexturedSurfaces` con colore, rilievo e ruvidità; il renderer semplice usa il colore medio.

## Moduli (Kotlin Multiplatform)

| Modulo | Piattaforme | Contenuto |
|---|---|---|
| `core` | Android, computer (JVM), browser (Wasm) | modello della casa, geometria (quote, 3D, computo, travi e colonne), editor (`EditorViewModel`), formato dei file, archivio dei progetti (`ProjectRepository`; su file in `jvmShared`) |
| `ui` | Android, computer, browser | tutta l'interfaccia in Compose Multiplatform; ciò che cambia tra piattaforme passa da `Platform` (file, condivisione, immagini, PDF, 3D). `skikoMain`: immagini e PDF con Skia per computer e browser |
| `app` | Android | `MainActivity`, versioni free/pro (`Flavor`), motore 3D Filament (pro) |
| `desktop` | Windows, Mac, Linux | `./gradlew :desktop:run`; installer con `:desktop:packageMsi` |
| `web` | browser | `./gradlew :web:wasmJsBrowserDistribution` → `web/build/dist/wasmJs/productionExecutable` (sito statico) |

Sul computer e nel browser per ora mancano la vista 3D, i PDF come pianta di sfondo e (solo web) il catalogo arredi e l'esportazione `.sagoma` completa. Il PDF delle tavole è a 300 dpi (200 nel browser) invece che vettoriale, sempre in scala esatta.

## Struttura

| Package | Contenuto |
|---|---|
| `model` | `Vec2`, `Room`, `RoomType`, `FloorPlan` — dati immutabili, unità in **cm**, asse y verso il basso |
| `geometry` | Kotlin puro, senza Android: area/contenimento (`Polygon`), creazione forme e nomi automatici (`RoomFactory`), snap angoli/muri/stanze (`Snapping`) |
| `editor` | `EditorViewModel` (tutte le azioni), `EditorUiState`, `History` (annulla/ripeti su snapshot di `FloorPlan`) |
| `ui` | `PlanCanvas` (disegno + gesti), campi lunghezza sui muri, dialoghi di creazione, pannelli |

Convenzioni: i poligoni sono sempre in ordine **orario su schermo**, così `perp()` della direzione di un muro
punta verso l'interno (servirà per "apre verso interno/esterno" delle porte). Il muro `i` va da `points[i]` a `points[i+1]`.

## Stato rispetto alla spec

| § | Funzione | Stato |
|---|---|---|
| 1 | Creazione stanza (quadrato/rettangolo/L con 4 orientamenti, misure, tipo, soffitto, prima apertura obbligatoria, posizionamento accanto + adatta vista) | ✅ |
| 2 | Angoli con snap 22 cm + propagazione, trascinamento muri, lunghezza esatta dalla tendina Info della stanza (sezione Muri; misurata sul lato interno del muro, come perimetro e quote; anche le misure inserite alla creazione sono interne). Sulla pianta le misure sono quote in sola lettura come nelle planimetrie: dentro ogni stanza, per ogni muro, una linea di quota parallela al filo interno, con linee di richiamo, trattini obliqui alle estremità e valore in cm sopra la linea. Il valore evita porte, finestre, impianti e il nome della stanza, altezza per muro (su un muro in comune vale per entrambe le stanze), spostamento stanza con appoggio 25 cm. Aggancio muro-su-muro entro 25 cm: trascinando un muro o spostando una stanza, un muro parallelo di un'altra stanza affiancato (o allineato con estremità vicine) viene raggiunto esattamente, così i due muri si sovrappongono; spostando una stanza anche su due assi | ✅ |
| 2–3 | Caratteristiche della stanza (nome, tipo, altezza soffitto, misure area/perimetro/volume, lunghezza e altezza di ogni muro, elenco aperture e impianti, elimina; il contenuto scorre). Selezionando una stanza (sulla pianta o dalla sua scheda in alto) o un oggetto compare solo una striscia in basso con il nome, "Info" e ▲/▼. La tendina si apre e si chiude con Info, con la freccia o con uno swipe: in su o in giù sulla striscia, oppure in giù sul contenuto quando è già in cima. Non si apre mai da sola. Con la tendina della stanza aperta, la stanza viene centrata nello spazio visibile. Selezionando un'altra cosa sulla pianta la tendina si richiude; dal menu ⋮ "Proprietà di …" si apre già aperta | ✅ |
| 3 | Tipi con colore, rinomina automatica, altezza soffitto, area calpestabile (sul filo interno dei muri: contorno ristretto di metà spessore, 7,5 cm) | ✅ |
| 4 | Stanza a fuoco / nulla selezionato | ✅ |
| 5 | "+ Porte" (1/2 ante, scorrevole, Apertura senza porta ▸ squadrata/ad arco), "+ Infissi" (Finestre ▸ 1/2 ante, scorrevole; Balcone ▸ 1/2 ante, scorrevole: vetrati a terra, con soglia disegnata). Ogni porta/finestra/balcone può essere a battente o scorrevole (simbolo con ante nello spessore del muro e freccia del verso), aperture senza porta (squadrata/ad arco), trascinamento lungo il muro, riquadro con "Rimuovi", varco condiviso tra stanze a contatto. I pulsanti sono sempre attivi: l'apertura va sul muro toccato di qualsiasi stanza, che diventa quella attiva | ✅ |
| — | Impianti ("+ Impianti"): calorifero, presa di corrente, interruttore, punto acqua (altezza da terra, 50 cm di default) sui muri; Luci ▸ faretto, neon lungo, lampadario, plafoniera, striscia LED al soffitto. Si posizionano toccando il muro o il punto della stanza, si trascinano, hanno il riquadro caratteristiche e compaiono nell'elenco della stanza e nel PNG | ✅ |
| 6–7 | Mobili e controllo urti | ⏸ sospesi da spec |
| 8 | Annulla/Ripeti (pulsanti + Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z) | ✅ |
| 9 | Metro: più righelli, tacche 10/50 cm, estremità per allungare, corpo per spostare, rotazione con aggancio a 45° e gradi dal vivo, × per eliminare, maniglie a distanza fissa dallo zoom | ✅ |
| 10 | Riquadro caratteristiche non bloccante (muro, aperture, tendina stanza). Quando un riquadro in basso compare, si apre/chiude o sparisce, la pianta scorre (animata, senza cambiare zoom) e si centra nello spazio visibile sopra; se non ci sta, sfrutta lo spazio vuoto e tiene visibile l'oggetto selezionato | ✅ |
| 11 | Zoom pulsanti/rotellina/pinch, pan, adatta alla vista | ✅ |
| 12 | Esportazione PNG ("Esporta" in alto): immagine pulita su sfondo bianco, nome + area e quote dei muri su tutte le stanze, metri senza maniglie; salvataggio col selettore file di sistema (nessun permesso) | ✅ |
| 13 | Selezione automatica del testo nei campi, zone di presa allargate | ✅ |
| — | Salvataggio automatico (JSON versionato in `filesDir/planimetria.json`, scrittura atomica, 400 ms dopo l'ultima modifica e subito all'uscita in background); all'avvio la pianta viene ricaricata | ✅ |

Vista 3D ("🧊 Vista 3D" in basso a destra, "◱ Pianta 2D" per tornare): scena OpenGL ES 2 generata dalla pianta (`geometry/Scene3D`, Kotlin puro e testato). Contiene muri con le altezze reali e varchi veri per porte e finestre, anche ad arco. I muri in comune si costruiscono una volta sola. Ci sono pavimenti colorati per tipo di stanza, porte aperte come nel simbolo 2D (o scorrevoli), finestre e balconi con telaio e vetro, impianti e luci che si illuminano, e il battiscopa. Dall'alto: un dito ruota, due dita zoomano e spostano, − / + / Adatta in alto agiscono sulla vista 3D. "🚶 Cammina": prima persona ad altezza occhi con soffitti, joystick e sguardo col dito; non si attraversano i muri, dalle porte sì. Un tocco seleziona stanza, muro, apertura o impianto, con la stessa striscia Info della pianta, e posiziona porte, infissi e impianti in attesa. Un oggetto già selezionato si trascina: aperture e impianti a muro lungo il muro, luci sul soffitto, muri avanti e indietro, la stanza intera dal pavimento.

Pareti sotto il tetto (sottotetti): nel riquadro di un muro c'è "Taglio diagonale (sottotetto)", disponibile se uno dei due muri perpendicolari è più basso. La parete resta alta normale fino al punto scelto, poi scende in linea retta fino all'altezza di quel muro basso. Il punto si sceglie scrivendo la distanza dal muro basso oppure trascinando il pallino sul muro selezionato; se entrambi i muri perpendicolari sono più bassi si sceglie verso quale scendere. Anche il soffitto della stanza scende allo stesso modo verso il muro basso (`geometry/Ceilings`): in pianta una linea tratteggiata segna dove inizia la pendenza, in 3D pareti e soffitto sono tagliati e il volume della stanza ne tiene conto. Se un oggetto arriva più in alto della parete (porte, finestre, calorifero, prese, interruttori, punti acqua sotto il taglio o su un muro basso) compare un avviso: un segnale rosso in pianta e una riga rossa con le due altezze nella tendina dell'oggetto e in quella del muro (`geometry/Collisions`). Un muro in comune fra due stanze ha in 3D due facce di mezzo spessore: ognuna segue la parete della propria stanza, quindi se il bagno ha il taglio e il soggiorno no, solo il lato del bagno scende. Per gli avvisi un impianto guarda solo la faccia della sua stanza, mentre una porta o una finestra deve starci sotto entrambe.

Guida mansarda: il pulsante "⌂ Mansarda" in fondo alla barra in basso (e nella colonna a sinistra in orizzontale) apre una guida di 8 passi con un disegno per ognuno. I passi sono: com'è fatta una mansarda, disegnare la stanza, abbassare il muretto ("Altezza muro"), attivare "Taglio diagonale" sulla prima parete laterale, scegliere dove inizia la discesa (campo o pallino da trascinare), ripetere sulla parete di fronte, controllare gli avvisi, vista 3D. L'ultimo passo ha il pulsante "Apri la vista 3D" (`ui/Guides.kt`).

Piani: il pulsante del piano in alto a sinistra ("🏠 Piano terra ▾") mostra il piano su cui si lavora. Dal suo menu si fanno tre cose:
- cambiare piano;
- aggiungere un piano sopra, scegliendo nome, interpiano (default 300 cm) e se partire dai muri del piano più alto (solo i muri) o da un piano vuoto;
- modificare nome e interpiano del piano corrente, o eliminarlo.

Il piano di sotto si vede in grigio tratteggiato. La cronologia vale per tutta la casa: ↶ riporta anche al piano della modifica. Il file salvato passa alla versione 2 (`building` con tutti i piani); i file della versione 1 si aprono come casa a un piano (`model/Building`, `persistence/PlanStore`).

Scale: "+ Scale ▾" nella barra in basso offre rampa dritta, a L con pianerottolo, a U con due rampe e a chiocciola. La scala compare dentro la stanza selezionata o quella al centro della vista. Si trascina, e vicino alla faccia di un muro dritto ci si accosta. Nella tendina Info si cambiano:
- tipo;
- verso (destra o sinistra; per la chiocciola orario o antiorario);
- larghezza e pedata, oppure il diametro per la chiocciola;
- rotazione, anche con "↻ Ruota di 90°".
- pendenza: scelta rapida (Comoda, Normale, Ripida, Molto ripida) che imposta alzate e pedata comoda (2 alzate + 1 pedata = 63 cm), oppure numero di alzate con − e + (più alzate = gradini più bassi e scala più lunga), con altezza del gradino e inclinazione in gradi;
- scale a L e a U: gradini prima del pianerottolo con − e + (almeno uno per rampa), il resto va dopo.

Le alzate sono tutte uguali; se non si sceglie il numero, è il minimo con alzate di al massimo 18 cm. Sopra i 20 cm compare l'avviso di scala molto ripida. Compare un avviso se 2 alzate + 1 pedata escono da 60–66 cm. In pianta la scala ha le pedate, la linea di salita con il pallino alla partenza, la freccia all'arrivo e la scritta "sale". Al piano di sopra compare il vuoto, tratteggiato con la croce e "scende" (`geometry/Stairs`, `ui/StairDrawing`).

In 3D si vede il piano scelto sopra quelli di sotto; i piani più alti sono nascosti. I gradini sono blocchi pieni; nella chiocciola sono lastre attorno al palo. Il pavimento del piano di sopra ha il buco della scala (`geometry/Clip`), e i muri dei piani superiori scendono nello spessore del solaio. Si toccano solo gli oggetti del piano corrente, scale comprese.

Balconi e terrazze: due tipi di stanza all'aperto (`RoomType.outdoor`). Si creano da "+ Balconi e terrazze ▾" (in orizzontale "+ Esterni"), con il tipo già scelto, oppure scegliendo il tipo nella creazione di una stanza. Nascono appoggiati al muro verticale più lungo sul lato destro della casa, con il lato lungo lungo il muro; poi si trascinano dal nome.
- Dove toccano la casa resta il muro della casa, a tutta altezza e da entrambe le parti.
- Sugli altri lati c'è il parapetto (`Room.parapet`, `parapetHeight`, default ringhiera da 100 cm):
  - ringhiera: corrimano, traverso e montanti ogni 12 cm; in pianta, doppia linea sottile;
  - muretto: in pianta, linea piena;
  - vetro: zoccolo, lastra e corrimano; in pianta, linea azzurra.
- Niente soffitto e niente volume; nella tendina Info ci sono parapetto e altezza al posto dell'altezza del soffitto.
- Gli avvisi di collisione usano il parapetto verso l'esterno e il muro della casa dove c'è. Camminando in 3D il parapetto blocca.

Modelli e finiture (`model/Styles.kt`): tutti disegnati dall'app nel 3D, senza file esterni. Nella tendina Info ogni scelta è una fila di schede scorrevoli con l'anteprima disegnata (`ui/ModelPicker.kt`, `ui/ModelPreviews.kt`); i colori si scelgono con pallini.
- Porte:
  - modelli: liscia, a pannelli (riquadri in rilievo), con vetro, a doghe, moderna (fughe orizzontali e maniglione);
  - colori: rovere, bianco, rovere chiaro, noce, grigio, antracite;
  - maniglia, coprifilo sulle due facce e rivestimento del varco.
- Finestre e balconi:
  - modelli: classica, minimal (profili sottili, antracite se non si sceglie il colore), all'inglese (listelli sul vetro);
  - oscuranti: persiane aperte ai lati, con le stecche, oppure tapparella abbassata di un terzo con guide e cassonetto;
  - colore dell'infisso.
- Scale:
  - due tipi in più: dritta con pianerottolo, e a L con tre gradini a ventaglio nell'angolo al posto del pianerottolo;
  - struttura: muratura, a sbalzo, a giorno con cosciali;
  - materiale dei gradini: legno, legno scuro, marmo, pietra, cemento;
  - ringhiera sui lati liberi (non contro i muri, non a partenza e arrivo), con il corrimano che segue la pendenza: metallo, vetro, legno.
- Ringhiera del vano scala al piano di sopra (`Stair.wellRailing`): nessuna, metallo, vetro, legno.
  - Si sceglie toccando il vano al piano di sopra (in pianta o la ringhiera in 3D), oppure dalla tendina della scala al piano di sotto.
  - Corre sul contorno del vuoto, alta 100 cm, tranne l'arrivo e i lati contro i muri del piano di sopra (`Stairs.wellRailingRuns`).
  - In pianta è una doppia linea (azzurra se in vetro); in 3D blocca chi cammina.
- Termosifoni: a piastra, a elementi, scaldasalviette. Scegliendo lo scaldasalviette le misure diventano 50 × 120 cm.
- Lampadari: moderno, classico a sei bracci, a campana, industriale a tre sospensioni.
- Stanze:
  - pavimento: colore della stanza, parquet rovere o noce (doghe sfalsate), gres chiaro o grigio (60 × 60), marmo (90 × 90), cotto;
  - colore delle pareti, per ogni faccia del muro.

Porte, finestre e impianti a muro si spostano lungo tutto il perimetro della stanza. Trascinandoli vanno sul muro più vicino al dito su cui ci stanno, anche girando gli angoli (`Openings.alongPerimeter`). In 3D si trascinano su un piano orizzontale, così fanno lo stesso.

Colonne e travi (`model: Column, Beam`, `geometry/Structure.kt`, `ui/StructureDrawing.kt`), dal pulsante "+ Colonne e travi ▾":
- Colonne quadrate (anche rettangolari e ruotate) o rotonde, dal pavimento al soffitto (anche in mansarda).
  - Nascono al centro della stanza scelta; trascinate vicino a un muro si accostano alla sua faccia e diventano pilastri che sporgono dal muro.
  - In pianta sono piene, come i muri; in 3D bloccano chi cammina.
- Travi a soffitto: nascono attraverso la stanza nel verso corto, da faccia a faccia dei muri.
  - Si spostano trascinandole e si allungano dai pallini alle estremità, che si appoggiano ai muri.
  - Nella tendina Info si cambiano larghezza, quanto sporgono sotto il soffitto e lunghezza.
  - In pianta sono tratteggiate con le misure; in 3D pendono dal soffitto.

Muri in 3D: ogni muro è fatto di due mezze fasce ai lati della mezzeria.
- Fascia interna: colore delle pareti della stanza ("Pareti interne"). Ogni stanza disegna la propria, anche sui muri in comune.
- Fascia esterna: colore della facciata del piano (`FloorPlan.facade`, "Pareti esterne" nella tendina di qualunque stanza), solo sui tratti che danno all'esterno o su un balcone.
- Agli angoli la fascia interna si allunga solo negli angoli rientranti della stanza, quella esterna solo negli spigoli sporgenti della casa, non toccati da altre stanze. Così agli incroci nessun colore sconfina sulla faccia di un'altra stanza.
- Una singola parete può avere un colore diverso dalle altre (`Room.wallPaints`, per muro; `Room.paintOf(i)`). Si sceglie toccando il muro e aprendo Info ("Colore di questa parete"); "Come le altre pareti" toglie il colore proprio.
- Colonne: si può scegliere l'altezza (di base fino al soffitto, mai oltre).
- Travi: spostandole si accostano di fianco ai muri paralleli, e le estremità restano appoggiate ai muri.

Muri singoli (`model: FreeWall`, `FloorPlan.freeWalls`): tramezzi o muretti che non chiudono una stanza. Si aggiungono da "+ Aggiungi stanza" → "Muro singolo" (compare quando c'è già almeno una stanza).
- Nascono attraverso la stanza scelta nel verso corto, da faccia a faccia dei muri; di base sono spessi 10 cm, alti fino al soffitto e bianchi.
- Trascinandoli si spostano; trascinando i pallini alle estremità si allungano e ruotano.
- Aggancio (`Structure.wallLines`, `snapWallEnd`, `snapped`):
  - l'estremità trascinata si raddrizza a 90° se è entro 6°;
  - poi si unisce all'estremità di un altro muro singolo vicino (angolo), oppure si appoggia alla faccia di un muro, della stanza o singolo (a T);
  - il muro spostato intero si accosta di fianco ai muri paralleli, senza storcersi.
- Nella tendina Info si cambiano lunghezza, spessore, altezza (fino al soffitto o più basso) e colore.
- In pianta è pieno come i muri, con la lunghezza; in 3D si tocca e blocca chi cammina; con lo strumento Distanza si misura.
- Anche travi e colonne si agganciano ai muri singoli.

Distanza (`geometry/Distances.kt`, `ui/MeasureDrawing.kt`): il pulsante "📐 Distanza" (accanto a Metro) attiva lo strumento e i due tocchi successivi scelgono gli oggetti.
- Oggetti misurabili: muri, porte e finestre, termosifoni, prese, interruttori, punti acqua, luci, colonne, travi, scale.
- Ogni oggetto conta con la sua forma in pianta: il muro con il suo spessore, il termosifone con la sua sporgenza, la finestra nello spessore del muro, la colonna e la scala con il loro ingombro.
- La distanza è quella tra i bordi più vicini, al decimo di centimetro, con le componenti orizzontale e verticale se è di sbieco.
- Sulla pianta compaiono gli oggetti evidenziati e la quota. Se si spostano gli oggetti la misura si aggiorna; un terzo tocco inizia una nuova misura.

Travi: allungando una trave dalle estremità, se è entro 6° dall'orizzontale o dalla verticale si raddrizza (`Structure.snapAngle`), tenendo la lunghezza, e poi l'estremità si appoggia al muro.

Miniguide animate: il pulsante "❔ Guide" accanto a "Mansarda" apre l'elenco delle guide: Stanze e misure, Muri e angoli, Porte e finestre, Impianti, Più stanze, Scale e piani, Vista 3D, Mansarda e Metro. Ogni passo ha una piccola animazione in loop che mostra il gesto da fare: un dito che tocca, trascina o apre la tendina, e la pianta che cambia di conseguenza. Le stesse animazioni compaiono nelle schede del tutorial e dei suggerimenti al primo utilizzo. Le scene sono disegnate su Canvas in `ui/GuideSketches.kt` (`Sketch`, `AnimatedSketch`): ogni scena è una funzione del tempo 0..1, senza immagini o file esterni.

Legenda: pulsante "Legenda" in basso a destra della pianta (sopra la striscia Info). Apre l'elenco di tutti i simboli (muri, stanza selezionata, altezza muro, quota, porte, infissi, impianti, luci, metro), ciascuno disegnato con lo stesso codice della pianta e con una breve spiegazione.

Aggiunta non in spec: eliminazione di un muro in comune. Nel riquadro di un muro condiviso con un'altra stanza c'è "Elimina muro · unisci con …". Se il muro confina con più stanze c'è un pulsante per ciascuna. Contano i tratti in comune di almeno 20 cm; due stanze che si toccano solo in un angolo non hanno il pulsante. Dopo la conferma le due stanze diventano un unico ambiente, con nome, tipo e altezza soffitto della stanza del muro selezionato. Del muro resta solo il tratto non in comune. Aperture, impianti e altezze personalizzate degli altri muri si conservano, mentre quelli sul tratto eliminato vengono rimossi. Se due muri laterali sono sfalsati di pochi cm, vengono raddrizzati in un muro unico. L'operazione si annulla con ↶.

Tutorial guidato a passi: parte al primo avvio (dopo la prima stanza, se la pianta è vuota) e si può rifare dal menu ⋮ "Rifai il tutorial". Ogni passo chiede di fare davvero l'azione e si completa da solo quando è fatta: aprire Info, cambiare larghezza e profondità dalla sezione Muri, cambiare il tipo di stanza, spostare un muro, spostare un angolo, aggiungere e spostare una porta, aggiungere un impianto, aggiungere una stanza e accostarla, Annulla, metro. Ha "Salta passo" ed "Esci". Finché la scheda è aperta, la pianta si adatta e si centra nello spazio sotto di lei. Durante il tutorial i suggerimenti sono sospesi; finito il tutorial non compaiono più.

Suggerimenti al primo utilizzo: una scheda in alto spiega ogni funzione la prima volta che la si usa (benvenuto, stanza, muro, nuova stanza, porte e finestre, impianti, metro). Ha i pulsanti "Ho capito" e "Salta tutti". I suggerimenti visti sono salvati in `filesDir/suggerimenti-visti.txt` e si possono rivedere dal menu ⋮ "Rivedi i suggerimenti".

Aggiunta non in spec: eliminazione stanza, dal menu ⋮ ("Elimina <stanza>…", con la stanza selezionata) o dalla tendina della stanza. Chiede conferma, è annullabile e non è disponibile se resta una sola stanza.

Orizzontale (altezza < 500 dp): schede e barra degli strumenti su una sola riga, barra di stato nascosta (riappare scorrendo dal bordo), riquadri caratteristiche sul lato destro con la pianta che si sposta a sinistra. Su schermi larghi (≥ 600 dp, anche tablet) i riquadri stanno sempre a destra. Ruotando lo schermo la pianta viene riadattata.
