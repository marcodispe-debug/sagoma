# Specifica — Editor planimetria

Questo documento riassume il comportamento dell'editor planimetria così come definito e testato nel prototipo HTML/SVG. Non descrive il codice del prototipo (che non verrà riusato in app nativa), ma le **decisioni di comportamento e interazione** che restano valide per l'implementazione Android nativa.

---

## 1. Creazione di una stanza

**Flusso**: pulsante → finestra di scelta forma → (per la L, sottomenù orientamento) → finestra misure → stanza creata.

- Alla primissima apertura dell'app (planimetria vuota), la finestra di scelta forma si apre **automaticamente e obbligatoriamente** — nessuna opzione "Annulla", perché deve esistere almeno una stanza.
- Per ogni stanza successiva, il pulsante "+ Aggiungi stanza" apre lo stesso flusso, ma con "Annulla" disponibile.

**Finestra di scelta forma**, tre opzioni con icona:
- **Quadrato**
- **Rettangolo**
- **A L** → apre un sottomenù con 4 orientazioni (in alto a destra / in alto a sinistra / in basso a destra / in basso a sinistra), ciascuna con icona che mostra dove sporge la protuberanza

**Finestra misure**, mostrata dopo la scelta forma, contiene:
- Selettore **tipo di stanza** (vedi §3)
- Campi dimensione specifici per la forma (lato / larghezza+profondità / larghezza+profondità principale + larghezza+profondità protuberanza)
- Campo **altezza soffitto** (default 270 cm)
- Pulsante "Crea stanza"

Una nuova stanza viene posizionata automaticamente accanto alle esistenti (mai sovrapposta), e la vista si adatta per mostrarla per intero.

---

## 2. Geometria e modifica della forma

### Angoli
- Trascinare un angolo ne cambia la posizione liberamente.
- **Snap automatico**: quando l'angolo trascinato si avvicina a una posizione che renderebbe **quel punto perfettamente allineato** con i due muri adiacenti (formando un angolo retto esatto), scatta in quella posizione esatta. La soglia di aggancio è di circa 22 cm (raggio di "richiamo magnetico"); una volta agganciato, l'allineamento è matematicamente esatto, non approssimato.
- **Non è legato alla forma originale**: lo snap riconosce *qualsiasi* rettangolo pulito nelle dimensioni attuali (anche se la stanza è stata allungata rispetto a come è stata creata), non solo le misure di partenza. Vale anche per le stanze a L.
- **Propagazione**: quando un angolo scatta in posizione, il sistema controlla anche gli altri angoli della stessa stanza e raddrizza automaticamente eventuali piccole imprecisioni residue sui muri collegati (più passaggi, per convergere anche su forme complesse come la L).

### Muri
- **Trascinare il muro** (non un angolo): allunga/restringe la stanza lungo quell'asse — entrambi gli angoli del muro si spostano insieme, perpendicolarmente al muro stesso.
- **Toccare il muro senza trascinare** (uno scatto secco): seleziona quel muro specifico per impostargli un'**altezza personalizzata**, diversa dall'altezza soffitto generale della stanza — utile per soffitti inclinati (mansarda). Il muro con altezza personalizzata mostra una piccola etichetta "h.___cm"; è sempre possibile tornare all'altezza standard.
- **Lunghezza esatta**: ogni muro della stanza attiva mostra un campo numerico modificabile con la sua lunghezza in cm, posizionato al centro del muro.

### Stanza intera
- Trascinare l'**etichetta con il nome** sposta l'intera stanza; se un angolo si avvicina (entro 25 cm) a un angolo di un'altra stanza, scatta in appoggio.
- Doppio tocco sulla scheda in alto per rinominare la stanza.

---

## 3. Proprietà della stanza

- **Tipo**: Altro, Soggiorno, Camera da letto, Cucina, Bagno, Cameretta, Studio, Corridoio — ciascuno con un colore identificativo che tinge leggermente il riempimento della stanza sulla pianta (più intenso se la stanza è quella attiva).
- **Cambiare il tipo rinomina automaticamente la stanza** (es. "Cucina", "Cucina 2" se già presente) — impostabile sia alla creazione sia in qualsiasi momento dopo.
- **Altezza soffitto**: valore generale della stanza (cm), usato anche per il controllo urti (§8). Sovrascrivibile per singolo muro (§2).
- **Area**: calcolata automaticamente (formula del poligono, valida anche per forme a L), mostrata in m² accanto al nome.

---

## 4. Stato "a fuoco" — cosa si vede quando

Per evitare confusione visiva con più stanze, le **misure modificabili di un muro sono visibili solo per la stanza attualmente "a fuoco"**.

- Toccare una stanza (il suo contorno, un angolo, un muro, l'etichetta, o la sua scheda in alto) la rende attiva **e a fuoco**: le sue misure appaiono, l'etichetta mostra nome + area, la scheda si evidenzia.
- Toccare in un punto vuoto della pianta, fuori da ogni stanza, **toglie completamente il fuoco**: tutte le misure spariscono, nessuna etichetta mostra l'area, nessuna scheda resta evidenziata — uno stato di "nulla selezionato" pulito e coerente.

---

## 5. Aperture — porte, finestre, aperture senza porta

Tre pulsanti in un'unica sezione: "+ Porta", "+ Finestra" (apre sottomenù 1 anta / 2 ante), "+ Apertura senza porta" (apre sottomenù Squadrata / Ad arco). Dopo la scelta, si tocca un punto qualsiasi su un muro della stanza attiva per posizionare l'apertura lì.

### Porta
- Campi: larghezza (default 80 cm), altezza (default 210 cm), **cardine** (sinistra/destra — quale lato è il perno), **apre verso** (interno/esterno della stanza).
- Disegno: varco nel muro + pannello + arco tratteggiato che mostra il raggio di apertura (simbolo architettonico standard).

### Finestra — 1 anta
- Stessi campi della porta, più **altezza da terra / davanzale** (default 90 cm).
- Stesso disegno di una porta (varco + pannello + arco), colore diverso.

### Finestra — 2 ante
- Come sopra ma **senza campo cardine** (non applicabile): entrambe le ante sono incernierate ciascuna sul proprio lato esterno e si aprono simmetricamente verso il centro.

### Apertura senza porta
- Campi: larghezza, altezza, **stile** (Squadrata / Ad arco) — nessun cardine, nessun verso apertura (non c'è pannello che si muove).
- **Squadrata**: varco nel muro con due tacche verticali sottili ai bordi (indicano gli stipiti).
- **Ad arco**: come sopra, più una linea curva che collega le due tacche.

### Comune a tutte le aperture
- Trascinabili lungo il proprio muro dopo il posizionamento (si afferra la linea dell'apertura, non la casella con la misura del muro che ha la priorità esattamente al centro).
- Selezionare un'apertura apre il riquadro caratteristiche (§10) con tutti i campi pertinenti e un pulsante "Rimuovi".

### Varco condiviso tra stanze
Quando due stanze hanno un muro **geometricamente a contatto** (stessa linea, parallelo, entro ~12 cm) e una delle due ha una **porta o un'apertura senza porta** su quel muro, appare automaticamente un varco corrispondente anche sul muro dell'altra stanza, nello stesso punto — un passaggio continuo tra le due stanze, non una porta contro un muro pieno.

---

## 6. Mobili — funzione sospesa

**Decisione presa**: l'editor planimetria **non permette più di inserire mobili generici**. I mobili reali verranno inseriti in una fase successiva, con un catalogo vero collegato ai prodotti effettivi.

Tutta la logica sottostante (elencata qui sotto) è già scritta e funzionante nel prototipo, semplicemente senza un punto d'accesso per crearla — riattivarla con un catalogo reale richiederà di ricollegare l'origine dei mobili, non di riscrivere il comportamento:

- **Trascinamento** entro i confini della stanza, con **inset dallo spessore del muro** (i mobili si fermano a metà spessore muro dalla linea centrale, non sulla linea).
- **Rotazione**: maniglia dedicata, agganciata a incrementi di 15° (libera tenendo Shift), con lettura dei gradi in tempo reale.
- **Snap alle pareti** quando ci si avvicina (usa il vero ingombro ruotato, non il rettangolo prima della rotazione — dettaglio importante corretto durante lo sviluppo).
- **Posizionamento con precisione**: pannello con le 4 distanze dai muri, tutte modificabili e coerenti tra loro, tiene conto della rotazione.
- **Duplica** (Ctrl+D), **elimina** (pulsante × piccolo sul mobile + tasto Canc), **sposta con le frecce** (1 cm, 10 cm con Shift).
- Riposizionamento automatico entro i nuovi confini se la stanza cambia forma o si rimpicciolisce.

---

## 7. Controllo urti (attivo indipendentemente dai mobili, si riattiverà con essi)

Tre tipi di urto, evidenziati con lo stesso colore rosso acceso dedicato:

1. **Tra due mobili** — sovrapposizione reale dei contorni (ruotati).
2. **Contro il soffitto** — altezza del mobile maggiore dell'altezza soffitto della stanza (o del muro, se personalizzata).
3. **Contro l'apertura di una porta o finestra** — il mobile invade la vera area che l'anta spazza aprendosi (non solo "è vicino al muro"), ed è più alto del davanzale/soglia.

In caso di urto: il mobile diventa rosso, appare un'etichetta "⚠ possibile urto" **sempre visibile sulla pianta** (non solo se selezionato), e il pannello caratteristiche elenca la causa (o le cause) specifica.

---

## 8. Annulla / Ripeti

- Cronologia completa, prima di ogni modifica (trascinamenti, aggiunte, eliminazioni, cambi di valore nei campi).
- Pulsanti dedicati + scorciatoie da tastiera dove disponibili.
- I riferimenti agli elementi grafici non fanno parte della cronologia salvata (irrilevanti per lo stato dei dati).

---

## 9. Metro

- Un tocco sul pulsante crea un righello a schermo, lunghezza di default 100 cm, con tacche ogni 10 cm (più lunghe ogni 50 cm).
- **Si allunga** trascinando un'estremità (segnata da una linea sottile perpendicolare, non un pallino — per indicare con precisione dove inizia/finisce la misura).
- **Si sposta** trascinando il corpo (zona di presa più larga della linea visibile, per essere facile da afferrare col dito).
- **Ruota** trascinando una maniglia dedicata: rotazione libera su tutti i 360°, con aggancio automatico solo quando ci si avvicina a un multiplo di 45° — i gradi correnti sono leggibili in tempo reale durante il trascinamento, ben distanziati dal punto di contatto per non restare coperti dal dito.
- Un piccolo pulsante × (più piccolo delle altre maniglie, ruota insieme al righello) lo elimina.
- Le maniglie (rotazione, eliminazione) mantengono una distanza minima dal corpo del righello **indipendente dallo zoom**, per restare sempre facilmente distinguibili dal corpo stesso.
- Si possono avere più righelli contemporaneamente sulla pianta.

---

## 10. Selezione e riquadro caratteristiche

Selezionare un mobile, un'apertura, o un muro (per l'altezza personalizzata) apre un **riquadro con le caratteristiche modificabili** dell'oggetto.

- Il riquadro **non copre né blocca la pianta**: resta ancorato a un lato (in basso su schermi stretti, in un angolo su schermi larghi), lasciando il resto della pianta visibile e utilizzabile — è possibile continuare a vedere e trascinare l'oggetto selezionato mentre il riquadro mostra i valori aggiornati in tempo reale.
- Pulsante di chiusura dedicato; toccare la pianta fuori dall'oggetto lo chiude.

---

## 11. Zoom e navigazione

- Pulsanti +/−, rotellina del mouse, pinch a due dita su touch.
- Intervallo di zoom ampio: dal dettaglio fine al panoramico su piante con molte stanze.
- Pulsante "adatta alla vista" per centrare tutto il contenuto.
- Trascinare lo sfondo vuoto sposta la vista (pan); un tocco senza trascinamento sullo sfondo vuoto toglie il fuoco (vedi §4).

---

## 12. Esportazione

- Esporta la pianta corrente come immagine PNG, con tutti i colori risolti correttamente (non dipendenti dal contesto della pagina).

---

## 13. Dettagli di interazione generali

- Tutti i campi numerici/testo nei pannelli e nei riquadri **selezionano automaticamente il loro contenuto quando li si tocca**, pronti per essere sovrascritti digitando — esclusi i campi delle misure direttamente sui muri della pianta.
- Zone di presa per angoli, maniglie e pulsanti piccoli sono sempre più larghe (invisibili) dell'elemento visibile, per essere facili da toccare col dito.

---

## Limiti noti / cose non ancora fatte

- Il contenimento dei mobili nei confini della stanza usa il rettangolo che la contiene (bounding box), non la vera forma poligonale — per una stanza a L, un mobile potrebbe tecnicamente finire nell'angolo "tagliato" che non fa parte della stanza vera.
- Lo spessore del muro è fisso (15 cm) per tutte le stanze, non personalizzabile.
- Nessun salvataggio persistente: il prototipo vive solo nella sessione del browser.
