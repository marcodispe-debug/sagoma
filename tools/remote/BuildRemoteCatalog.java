import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * Prepara il catalogo remoto degli asset: la cartella da caricare così com'è sul server (per esempio un bucket Cloudflare R2
 * o qualsiasi hosting di file statici). Di solito si lancia da Gradle: `gradlew :catalogo:preparaCatalogoRemoto`.
 *
 * Uso (JDK 17+): java BuildRemoteCatalog.java [--solo-manifest] <cartella degli asset> <cartella di uscita> <catalogVersion> [releaseId]
 *   <cartella degli asset>  per esempio catalogo/src/main/assets (furniture/, materials/, env/)
 *   <catalogVersion>        intero che cresce a ogni pubblicazione (l'app rifiuta un manifest più vecchio di uno già visto)
 *   --solo-manifest         scrive solo manifest.json, senza blob (è il manifest che l'app porta con sé)
 *
 * Scrive nell'uscita:
 *   manifest.json           l'elenco (schema 1, vedi core/.../assets/remote/ManifestParser.kt): ogni asset con i suoi file,
 *                           percorso logico, impronta SHA-256 e dimensione;
 *   blobs/<aa>/<sha256>     il contenuto di ogni file, una volta sola per contenuto (disposizione "fanout2").
 * Sul server: manifest all'indirizzo `remote_manifest_url`, la cartella blobs/ a `remote_blob_base_url`
 * (proprietà Gradle sagoma.catalogo.manifest e sagoma.catalogo.blobs). I blob non cambiano mai nome: si possono mettere in cache per sempre.
 *
 * Un asset per arredo (modello 3D, miniatura, vista dall'alto), per materiale (colore, normali, ORM, campione), per luce
 * ambiente, più i due cataloghi. Gli id sono stabili ("furniture:<modello>", "material:<id>"…): il contenuto può cambiare.
 * Il manifest dipende solo dai file, dalla versione e dal releaseId (niente data): rilanciato con gli stessi dà lo stesso
 * manifest byte per byte, e quello che l'app porta con sé coincide con quello sul server. I blob già presenti non si ricopiano.
 */
public class BuildRemoteCatalog {
    record FileEntry(String role, String path, String sha256, long size, String mime) {}
    record Asset(String id, String kind, List<FileEntry> files) {}

    public static void main(String[] args) throws Exception {
        boolean onlyManifest = Arrays.asList(args).contains("--solo-manifest");
        args = Arrays.stream(args).filter(a -> !a.equals("--solo-manifest")).toArray(String[]::new);
        if (args.length < 3) { System.err.println("Uso: java BuildRemoteCatalog.java [--solo-manifest] <asset> <uscita> <catalogVersion> [releaseId]"); System.exit(1); }
        Path in = Paths.get(args[0]).toAbsolutePath().normalize();
        Path out = Paths.get(args[1]).toAbsolutePath().normalize();
        long version = Long.parseLong(args[2]);
        if (version < 1) throw new IllegalArgumentException("catalogVersion deve essere almeno 1");
        String release = args.length > 3 ? args[3] : "v" + version;
        if (!release.matches("[A-Za-z0-9_\\-][A-Za-z0-9_.\\-]{0,127}")) throw new IllegalArgumentException("releaseId non valido: " + release);

        List<Path> all;
        try (Stream<Path> s = Files.walk(in)) { all = s.filter(Files::isRegularFile).sorted().toList(); }
        Map<String, Asset> assets = new TreeMap<>();
        long bytes = 0;
        Set<String> blobs = new HashSet<>();
        int copied = 0;
        for (Path p : all) {
            String rel = in.relativize(p).toString().replace('\\', '/');
            if (!portable(rel)) throw new IllegalStateException("Percorso non adatto al catalogo remoto: " + rel);
            String[] g = group(rel);
            String sha = sha256(p);
            long size = Files.size(p);
            assets.computeIfAbsent(g[0], k -> new Asset(k, g[1], new ArrayList<>())).files().add(new FileEntry(g[2], rel, sha, size, mime(rel)));
            bytes += size;
            if (blobs.add(sha) && !onlyManifest) {
                Path blob = out.resolve("blobs").resolve(sha.substring(0, 2)).resolve(sha);
                if (!Files.exists(blob) || Files.size(blob) != size) {
                    Files.createDirectories(blob.getParent());
                    Path tmp = blob.resolveSibling(sha + ".tmp");
                    Files.copy(p, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, blob, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    copied++;
                }
            }
        }
        for (Asset a : assets.values()) {
            Set<String> roles = new HashSet<>();
            for (FileEntry f : a.files()) if (!roles.add(f.role())) throw new IllegalStateException(a.id() + ": due file con il ruolo " + f.role());
        }

        StringBuilder j = new StringBuilder();
        j.append("{\n  \"schema\": 1,\n  \"catalog\": \"sagoma\",\n  \"catalogVersion\": ").append(version)
            .append(",\n  \"releaseId\": ").append(q(release))
            .append(",\n  \"minClient\": 0,\n  \"blobs\": {\"layout\": \"fanout2\"},\n  \"assets\": [\n");
        int i = 0;
        for (Asset a : assets.values()) {
            j.append("    {\"id\": ").append(q(a.id())).append(", \"kind\": ").append(q(a.kind())).append(", \"files\": [");
            int k = 0;
            for (FileEntry f : a.files()) {
                j.append(k++ == 0 ? "" : ", ").append("{\"role\": ").append(q(f.role())).append(", \"path\": ").append(q(f.path()))
                    .append(", \"sha256\": ").append(q(f.sha256())).append(", \"size\": ").append(f.size()).append(", \"mime\": ").append(q(f.mime())).append("}");
            }
            j.append("]}").append(++i < assets.size() ? ",\n" : "\n");
        }
        j.append("  ]\n}\n");
        Files.createDirectories(out);
        Files.writeString(out.resolve("manifest.json"), j.toString(), StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "Asset: %d, file: %d (%.1f MB), blob diversi: %d, copiati ora: %d%n", assets.size(), all.size(), bytes / 1048576.0, blobs.size(), copied);
    }

    /** Asset, tipo e ruolo di un file, dal suo percorso logico. */
    static String[] group(String rel) {
        String dir = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : "";
        String name = rel.substring(rel.indexOf('/') + 1);
        if (rel.equals("furniture/catalog.json")) return new String[]{"catalog:furniture", "catalog", "catalog"};
        if (rel.equals("materials/materials.json")) return new String[]{"catalog:materials", "catalog", "catalog"};
        if (dir.equals("furniture") && !name.contains("/")) {
            if (name.endsWith("_top.png")) return new String[]{"furniture:" + cut(name, "_top.png"), "furniture", "top"};
            if (name.endsWith(".png")) return new String[]{"furniture:" + cut(name, ".png"), "furniture", "thumbnail"};
            if (name.endsWith(".glb")) return new String[]{"furniture:" + cut(name, ".glb"), "furniture", "model"};
        }
        if (dir.equals("materials") && !name.contains("/")) {
            for (String kind : new String[]{"color", "normal", "orm", "thumb"}) {
                if (name.endsWith("_" + kind + ".jpg")) return new String[]{"material:" + cut(name, "_" + kind + ".jpg"), "material", kind.equals("thumb") ? "thumbnail" : kind};
            }
        }
        if (dir.equals("env") && name.endsWith(".ibl") && !name.contains("/")) return new String[]{"environment:" + cut(name, ".ibl"), "environment", "ibl"};
        // Qualsiasi altro file: un asset da solo (nessun file resta fuori dal catalogo).
        return new String[]{"file:" + rel, "file", "data"};
    }

    static String cut(String s, String suffix) { return s.substring(0, s.length() - suffix.length()); }

    static String mime(String rel) {
        if (rel.endsWith(".glb")) return "model/gltf-binary";
        if (rel.endsWith(".png")) return "image/png";
        if (rel.endsWith(".jpg") || rel.endsWith(".jpeg")) return "image/jpeg";
        if (rel.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    /** Come isPortableAssetPath dell'app: relativo, con "/" e nomi semplici. */
    static boolean portable(String rel) {
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("\\") || rel.contains("//")) return false;
        for (String part : rel.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
            for (char c : part.toCharArray()) if (!(Character.isLetterOrDigit(c) && c < 128) && c != '_' && c != '-' && c != '.') return false;
        }
        return true;
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream s = Files.newInputStream(p)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = s.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        StringBuilder h = new StringBuilder();
        for (byte b : md.digest()) h.append(String.format("%02x", b));
        return h.toString();
    }

    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
