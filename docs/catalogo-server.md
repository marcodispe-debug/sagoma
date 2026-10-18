# Catalogo degli arredi sul server

L'app sa scaricare arredi e materiali da un server quando servono (`core/.../assets/remote`). Finché gli indirizzi
(proprietà Gradle `sagoma.catalogo.manifest` e `sagoma.catalogo.blobs`) mancano, il sistema remoto è spento e il catalogo
intero arriva con l'app nell'asset pack `catalogo`.

## Come funziona

- **Nell'app** restano solo elenchi, miniature, viste dall'alto e luci ambiente: download iniziale da Google Play ~46 MB
  invece di ~681 MB (bundletool, 17 ottobre 2026). C'è anche `assets/remote/manifest.json`, l'elenco di tutti i file del
  server con impronta SHA-256 e dimensione, scritto dallo stesso strumento che prepara la cartella per il server.
- **Solo quello che serve**: la vista 3D chiede i modelli e le mappe dei materiali della scena che non sono già sul
  dispositivo, e si ridisegna quando arrivano. Un arredo pesa in media ~0,8 MB (metà sotto 0,53 MB, il 90% sotto 1,9 MB,
  il più grande 7,7 MB); un materiale ~0,5 MB (al massimo 1,7 MB).
- **Cache**: i file scaricati restano sul dispositivo (fino a 512 MB, poi si tolgono i meno usati) e si riusano senza rete.
  Lo stesso file chiesto da più parti si scarica una volta sola.
- **Verifica**: un file entra in cache solo se dimensione e SHA-256 sono quelli del manifest, altrimenti si scarta. I nomi
  dei file sul server sono le loro impronte. Gli indirizzi devono essere `https://` (la build si ferma altrimenti).
- **Versioni**: ogni pubblicazione ha una `catalogVersion` più alta. All'avvio l'app chiede il manifest del server; se è più
  nuovo lo salva e lo usa, se è più vecchio lo rifiuta. Una versione già vista con un contenuto diverso è un errore.
- **Senza rete**: valgono il manifest dell'app (o l'ultimo salvato, se più nuovo) e i file in cache. Il manifest si
  riprova con attese crescenti (da 30 secondi a 15 minuti). Un arredo mai scaricato non compare nel 3D finché non arriva.
- **Nessuna credenziale nell'app**: il bucket si legge pubblicamente; la chiave per caricare i file resta solo sul computer
  di chi pubblica.
- **Non ancora**: la firma del manifest è prevista dal formato ma l'app non la verifica; oggi l'integrità del manifest dalla
  rete si basa su HTTPS (quello dentro l'app arriva firmato con l'app).

## Preparare i file

```
gradlew :catalogo:preparaCatalogoRemoto -Psagoma.catalogo.versione=1
```

Scrive in `catalogo/build/catalogo-remoto` la cartella da caricare: `manifest.json` e `blobs/aa/<impronta>` (~830 MB,
3.337 file). Rilanciato con gli stessi file e la stessa versione dà lo stesso manifest byte per byte; i blob già presenti
non si ricopiano. Lo strumento è `tools/remote/BuildRemoteCatalog.java` (anche da solo, vedi il commento in cima).

Prima, se il catalogo è stato rigenerato con `PackModels`, riapplicare le correzioni a mano:
`java tools/furniture/ApplyCatalogFixes.java tools/furniture/catalog-fixes.tsv catalogo/src/main/assets/furniture/catalog.json`.

## Pubblicare su Cloudflare R2

Serve un account Cloudflare a nome di chi pubblica. Qualsiasi hosting di file statici con HTTPS va bene allo stesso modo.

1. **Bucket**: nella dashboard Cloudflare, R2 → *Create bucket*, per esempio `sagoma-catalogo`.
2. **Lettura pubblica**: nel bucket, *Settings* → *Custom Domains* → collegare un dominio gestito su Cloudflare (per esempio
   `catalogo.tuodominio.it`). Per una prova va bene anche *Public Development URL* (`…r2.dev`), che Cloudflare limita e
   sconsiglia per l'uso vero. Non serve CORS: l'app Android e il computer non sono un browser.
3. **Chiave per caricare** (solo sul computer, mai nel progetto né nell'app): R2 → *Manage API tokens* → *Create API
   token*, permesso *Object Read & Write* solo su quel bucket. Annotare Access Key ID, Secret Access Key e l'endpoint
   `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`.
4. **rclone** (https://rclone.org): `rclone config` → nuovo remote `r2`, tipo `s3`, provider `Cloudflare`, le due chiavi e
   l'endpoint del punto 3. La configurazione resta nel profilo utente, fuori dal progetto.
5. **Caricare**, prima i blob e poi il manifest (così nessuno vede un manifest che punta a file non ancora caricati):

   ```
   rclone copy catalogo/build/catalogo-remoto/blobs r2:sagoma-catalogo/blobs --transfers 8 --header-upload "Cache-Control: public, max-age=31536000, immutable"
   rclone copyto catalogo/build/catalogo-remoto/manifest.json r2:sagoma-catalogo/manifest.json --header-upload "Cache-Control: no-cache"
   rclone check catalogo/build/catalogo-remoto r2:sagoma-catalogo --one-way
   ```

6. **Controllare** da un browser che `https://catalogo.tuodominio.it/manifest.json` si apra.
7. **Indirizzi per la build**, in `gradle.properties` dell'utente (`%USERPROFILE%\.gradle\gradle.properties`, fuori dal
   repository) o con `-P`:

   ```
   sagoma.catalogo.manifest=https://catalogo.tuodominio.it/manifest.json
   sagoma.catalogo.blobs=https://catalogo.tuodominio.it/blobs
   sagoma.catalogo.versione=1
   ```

8. **Costruire l'app**: `gradlew clean :app:bundleRelease` (il `clean` serve quando si passa da un modo all'altro, perché il
   pacchetto di debug può tenere i file del modo precedente).

## Aggiornare il catalogo

1. Aumentare `sagoma.catalogo.versione` (2, 3…).
2. `gradlew :catalogo:preparaCatalogoRemoto`, poi caricare blob e manifest come al punto 5.
3. Le app già installate prendono il manifest nuovo al prossimo avvio e scaricano solo i file cambiati quando servono.
4. Non cancellare i blob vecchi dal bucket: le app che non hanno ancora letto il manifest nuovo (per esempio senza rete) li usano ancora.
