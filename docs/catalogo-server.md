# Catalogo degli arredi sul server

L'app sa già scaricare arredi, materiali e luci da un server (`core/.../assets/remote`). Finché gli indirizzi in
`app/src/main/res/values/remote_assets.xml` sono vuoti il sistema remoto è spento e l'app usa solo i file dentro l'APK.

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
3. scrivere gli indirizzi in `remote_assets.xml`: `remote_manifest_url` = `https://…/manifest.json`,
   `remote_blob_base_url` = `https://…/blobs`;
4. a ogni aggiornamento: rigenerare con una `catalogVersion` più alta e caricare prima i blob, poi il manifest.

## Cosa non fa ancora

- Gli asset arrivano anche con l'app, nell'asset pack `catalogo` (circa 670 MB scaricati all'installazione). Toglierli per scaricarli solo quando servono è un passo a parte.
- Tutti gli asset sono pubblici (`access` assente = public): i modelli sono CC0 o CC-BY, quindi non serve proteggerli.
- La firma del manifest è prevista dal formato ma l'app non la verifica ancora.
