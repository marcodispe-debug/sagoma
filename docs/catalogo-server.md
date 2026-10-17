# Catalogo degli arredi sul server

L'app sa scaricare arredi e materiali da un server (`core/.../assets/remote`) quando servono. Finché gli indirizzi
(proprietà Gradle `sagoma.catalogo.manifest` e `sagoma.catalogo.blobs`) mancano, il sistema remoto è spento e il catalogo
intero arriva con l'app nell'asset pack `catalogo`.

Con gli indirizzi impostati:
- nell'app restano solo la parte leggera del catalogo (elenchi, miniature, viste dall'alto, luci ambiente): download
  iniziale da Google Play ~46 MB invece di ~681 MB (misurato con bundletool il 17 ottobre 2026);
- la vista 3D chiede i modelli e le mappe dei materiali che le mancano e si ridisegna quando arrivano; restano in cache;
- sul computer lo stesso con `-Dsagoma.remote.manifest=` e `-Dsagoma.remote.blobs=`.

## Preparare i file

```
java tools/remote/BuildRemoteCatalog.java catalogo/src/main/assets <cartella di uscita> <catalogVersion> [releaseId]
```

- `catalogVersion` cresce a ogni pubblicazione (1, 2, 3…): l'app rifiuta un manifest più vecchio di uno già visto.
- Nell'uscita: `manifest.json` (l'elenco, con impronta SHA-256 e dimensione di ogni file) e `blobs/aa/<impronta>` (il
  contenuto, una volta sola per file uguali). I blob non cambiano mai nome: si possono tenere in cache per sempre.
- Rilanciato con gli stessi file non ricopia niente; con file cambiati aggiunge solo i blob nuovi.
- Prova del 14 ottobre 2026 sugli asset attuali: 1027 asset (834 arredi, 188 materiali, 3 luci, 2 cataloghi), 3259 file,
  790 MB. Il manifest risultante si legge senza errori.

Prima, se il catalogo è stato rigenerato con `PackModels`, riapplicare le correzioni a mano:
`java tools/furniture/ApplyCatalogFixes.java tools/furniture/catalog-fixes.tsv catalogo/src/main/assets/furniture/catalog.json`.

## Pubblicare (serve un account di hosting, da aprire a tuo nome)

Va bene qualsiasi hosting di file statici con HTTPS. Per esempio Cloudflare R2 (nessun costo per il traffico in uscita):

1. creare un bucket e renderlo leggibile pubblicamente (dominio `r2.dev` o un dominio proprio);
2. caricare il contenuto della cartella di uscita così com'è (`manifest.json` e `blobs/`), per esempio con `rclone copy`;
3. scrivere gli indirizzi in `gradle.properties`: `sagoma.catalogo.manifest=https://…/manifest.json` e
   `sagoma.catalogo.blobs=https://…/blobs` (o passarli con `-P` a Gradle);
4. a ogni aggiornamento: rigenerare con una `catalogVersion` più alta e caricare prima i blob, poi il manifest.

## Cosa non fa ancora

- Tutti gli asset sono pubblici (`access` assente = public): i modelli sono CC0 o CC-BY, quindi non serve proteggerli.
- La firma del manifest è prevista dal formato ma l'app non la verifica ancora.
