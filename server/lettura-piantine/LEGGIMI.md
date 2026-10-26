# Lettura delle piantine

Questo è il piccolo server dietro il pulsante «✨ Leggi la piantina da sola» di Sagoma.

L'app gli manda la foto della piantina. Il server la fa leggere a un'AI e restituisce stanze, porte, finestre, scale e muri singoli. L'app li mette nella pianta, sopra lo sfondo già in scala.

Chi legge si sceglie con `LETTORE` in `wrangler.toml`:
- `gemini` (impostato): Gemini di Google, con il piano gratuito. Serve la chiave gratuita di Google (`GEMINI_API_KEY`).
- `cloudflare`: l'AI di Cloudflare, gratis entro la quota del giorno dell'account. Non serve nessuna chiave, ma legge peggio.
- `claude`: Claude di Anthropic, il più preciso ma a pagamento. Serve la chiave Anthropic (`ANTHROPIC_API_KEY`).

Le chiavi stanno solo tra i segreti di Cloudflare: non vanno mai nell'app né nel codice.

## Quanto costa

- **Cloudflare Workers:** il piano gratuito basta (100.000 richieste al giorno).
- **Gemini:** il piano gratuito ha un tetto di richieste al minuto e al giorno, deciso da Google. Quando finiscono, l'app avvisa di riprovare più tardi. Nel piano gratuito Google può usare le immagini inviate per migliorare i suoi modelli. Il modello si cambia con `MODELLO_GEMINI`. Si usa solo quello: quando Google è pieno il server aspetta il suo turno e lo riprova, per al massimo `ATTESA_GEMINI` secondi (270 se non detto), poi l'app avvisa di riprovare più tardi. Con `MODELLO_GEMINI_RISERVA` (modelli separati da virgole) si possono aggiungere riserve, usate dopo l'attesa: quelle gratuite però leggono peggio. Con `CONTROLLO_GEMINI = "si"` il server controlla la lettura (stanze sovrapposte, metri quadri disegnati lontani da quelli scritti) e, se trova problemi, chiede a Gemini di correggerla una seconda volta, tenendo la lettura migliore. È spento: con i modelli gratuiti (spesso risponde il più debole, perché gli altri sono pieni) la correzione spesso sposta tutta la pianta, e ogni lettura ne usa due. `RAGIONAMENTO_GEMINI` (per esempio "high") dice a Gemini quanto ragionare; senza, decide il modello. Le stanze che si sovrappongono le separa comunque l'app.
- **AI di Cloudflare:** 10.000 «neuroni» gratis al giorno per account, che si azzerano a mezzanotte (ora UTC). Bastano per poche letture al giorno; quando finiscono, l'app avvisa di riprovare domani. Il modello si cambia con `MODELLO_CLOUDFLARE`.
- **Claude:** ogni lettura costa qualche centesimo, di più per le piantine grandi e piene di scritte. Al massimo circa 35 centesimi con `claude-opus-5-5` (immagine ~6.000 token a 4 $ per milione, risposta fino a 16.000 token a 20 $ per milione), circa la metà con Sonnet: con 300 letture al giorno il caso peggiore è circa 100 $ al giorno.
  - Con `claude-sonnet-5-5` (impostazione `MODELLO` in `wrangler.toml`) costa circa la metà.
  - Per stare tranquilli, metti un tetto di spesa mensile nella console di Anthropic, alla voce **Limits**.
- **Limiti già impostati:**
  - 10 letture al giorno per dispositivo;
  - 30 letture al giorno per indirizzo di rete (il codice del dispositivo lo sceglie l'app, e chi vuole imbrogliare ne inventa uno nuovo a ogni richiesta);
  - 300 letture al giorno in tutto: con un'AI a pagamento è il tetto della spesa del giorno.
  - Si cambiano in `wrangler.toml`. Li conta il Durable Object `CONTATORE`, già in `wrangler.toml`: senza contatore il server non legge.
  - Con un'AI a pagamento metti comunque un tetto di spesa anche da Google o Anthropic: è l'unica protezione se qualcosa nel server va storto.

## Metterlo online (una volta sola)

Serve Node.js 20 o più recente. I comandi si lanciano da questa cartella.

1. `npm install`
2. `npx wrangler login`: si apre il browser, entri con l'account Cloudflare (lo stesso di R2) e accetti.
3. (Niente da fare: il contatore `CONTATORE` è già in `wrangler.toml` e nasce da solo al primo deploy.)
4. `npx wrangler deploy`: stampa l'indirizzo del server, per esempio `https://sagoma-lettura-piantine.<nome>.workers.dev`.
5. **La chiave la inserisci tu.** Con Gemini: su aistudio.google.com premi Get API key → Create API key. Con Claude: nella console di Anthropic, API Keys → Create Key. Poi, su Cloudflare:
   1. Workers & Pages → sagoma-lettura-piantine → Settings → Variables and Secrets → Add;
   2. nome `GEMINI_API_KEY` (o `ANTHROPIC_API_KEY`), valore la chiave, spunta **Secret**;
   3. Add variable and deploy.
   Non incollarla in chat e non scriverla in nessun file.
6. Nel file `gradle.properties` della tua cartella utente (lo stesso degli indirizzi del catalogo) aggiungi:

       sagoma.lettura.url=https://sagoma-lettura-piantine.<nome>.workers.dev

   Da quel momento l'app costruita sul PC (computer e telefono) mostra il pulsante.

### Facoltativo: chiave dell'app

Senza questa chiave, chiunque conosca l'indirizzo può usare il server (con i limiti al giorno). La chiave però sta dentro l'app, e chi smonta l'APK la trova: ferma i curiosi, non chi vuole davvero. Per accettare solo l'app:

1. scegli una parola lunga a caso;
2. mettila su Cloudflare come secondo segreto, `CHIAVE_APP`;
3. scrivila in `gradle.properties` come `sagoma.lettura.chiave=...`.

## Per chi sviluppa

- `npm test`: prove del server, senza chiamare Claude.
- `npx wrangler dev`: server in locale, con il suo contatore in locale. La chiave va in un file `.dev.vars`, che non si pubblica.
- **Cosa manda l'app:** `POST /leggi` con un JSON che contiene:
  - `image`, l'immagine in base64, con il lato lungo al massimo di 2560 px;
  - `media_type`;
  - `width` e `height`, in pixel;
  - `device`, un codice casuale del dispositivo.
- **Cosa risponde il server:** la lettura (`PlanReading` in `core/.../reading/PlanReading.kt`) in pixel di quell'immagine. Se qualcosa va storto risponde `{ error, message }`.
