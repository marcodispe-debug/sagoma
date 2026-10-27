import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Prepara i materiali fotografici più definiti, che stanno solo sul server del catalogo: 2K per il 3D del computer, 4K per
 * i render. Gli stessi materiali di tools/furniture/materials.tsv (quelli 1K dell'app li fa PackMaterials), scaricati di nuovo
 * dalle fonti originali in quella definizione: ambientCG (lo zip <id>_2K-JPG) e Poly Haven (cartella "ph_<id>", dalla sua API).
 * Licenza CC0 per tutti.
 *
 * Scrive `<uscita>/materials/2k/<id>_color.jpg`, `_normal.jpg`, `_orm.jpg` (o `4k/`), fatti come quelli 1K: ruvidità nel
 * verde dell'ORM, occlusione piena nel rosso, metallo nero nel blu. Un materiale già pronto non si riscarica: se si ferma, si
 * rilancia e riprende. Poi `gradlew :catalogo:preparaCatalogoRemoto` li mette nel catalogo del server.
 *
 * Uso (JDK 17+): java PackMaterialsHd.java <materials.tsv> <uscita, per esempio catalogo/hd> <lato: 2048 o 4096> [id...]
 * (con degli id, solo quei materiali).
 */
public class PackMaterialsHd {
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
    static final String[] KINDS = {"color", "normal", "orm"};

    public static void main(String[] args) throws Exception {
        if (args.length < 3) { System.err.println("Uso: java PackMaterialsHd.java <materials.tsv> <uscita> <2048|4096> [id...]"); System.exit(1); }
        int side = Integer.parseInt(args[2]);
        if (side != 2048 && side != 4096) throw new IllegalArgumentException("lato 2048 (2K) o 4096 (4K)");
        String res = side / 1024 + "k";
        Path out = Paths.get(args[1]).resolve("materials").resolve(res);
        Files.createDirectories(out);
        Set<String> only = new HashSet<>(Arrays.asList(args).subList(3, args.length));
        List<String> folders = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String folder = line.split("\t")[0].trim();
            if (only.isEmpty() || only.contains(folder) || only.contains(folder.toLowerCase(Locale.ROOT))) folders.add(folder);
        }
        AtomicInteger done = new AtomicInteger(), skipped = new AtomicInteger();
        List<String> failed = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> jobs = new ArrayList<>();
        for (String folder : folders) jobs.add(pool.submit(() -> {
            String id = folder.toLowerCase(Locale.ROOT);
            if (Arrays.stream(KINDS).allMatch(k -> Files.exists(out.resolve(id + "_" + k + ".jpg")))) { skipped.incrementAndGet(); return; }
            try {
                Map<String, byte[]> maps = folder.startsWith("ph_") ? polyHaven(folder.substring(3), res) : ambientCg(folder, res);
                pack(maps, id, side, out);
                System.out.printf(Locale.ROOT, "[%d/%d] %s%n", done.incrementAndGet() + skipped.get(), folders.size(), id);
            } catch (Exception e) {
                failed.add(folder + ": " + e.getMessage());
                System.err.println("NON FATTO " + folder + ": " + e.getMessage());
            }
        }));
        for (Future<?> j : jobs) j.get();
        pool.shutdown();
        System.out.printf(Locale.ROOT, "Fatti ora: %d, già pronti: %d, non fatti: %d%n", done.get(), skipped.get(), failed.size());
        for (String f : failed) System.out.println("  " + f);
        if (!failed.isEmpty()) System.exit(2);
    }

    /** Le tre mappe d'origine ("color", "normal" OpenGL, "rough") dallo zip di ambientCG. */
    static Map<String, byte[]> ambientCg(String asset, String res) throws Exception {
        byte[] zip = get("https://ambientcg.com/get?file=" + asset + "_" + res.toUpperCase(Locale.ROOT) + "-JPG.zip");
        Map<String, byte[]> maps = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName();
                if (n.endsWith("_Color.jpg")) maps.put("color", z.readAllBytes());
                else if (n.endsWith("_NormalGL.jpg")) maps.put("normal", z.readAllBytes());
                else if (n.endsWith("_Roughness.jpg")) maps.put("rough", z.readAllBytes());
            }
        }
        if (maps.size() < 3) throw new IOException("nello zip mancano mappe (trovate " + maps.keySet() + ")");
        return maps;
    }

    /** Le tre mappe d'origine da Poly Haven: gli indirizzi li dà la sua API (Diffuse, nor_gl, Rough). */
    @SuppressWarnings("unchecked")
    static Map<String, byte[]> polyHaven(String asset, String res) throws Exception {
        Map<String, Object> files = (Map<String, Object>) new Json(new String(get("https://api.polyhaven.com/files/" + asset), StandardCharsets.UTF_8)).value();
        Map<String, byte[]> maps = new HashMap<>();
        String[][] wanted = {{"color", COLOR_VARIANT.getOrDefault(asset, "Diffuse")}, {"normal", "nor_gl"}, {"rough", "Rough"}};
        for (String[] w : wanted) {
            Object url = dig(files, w[1], res, "jpg", "url");
            if (!(url instanceof String u)) throw new IOException("Poly Haven non ha " + w[1] + " " + res);
            maps.put(w[0], get(u));
        }
        return maps;
    }

    /** Tessuti di Poly Haven con più colori e nessun "Diffuse": quello che c'è nell'app (stesse mappe di rilievo e ruvidità). */
    static final Map<String, String> COLOR_VARIANT = Map.of("fabric_pattern_05", "col_01", "fabric_pattern_07", "col_2");

    @SuppressWarnings("unchecked")
    static Object dig(Object o, String... keys) {
        for (String k : keys) {
            if (!(o instanceof Map)) return null;
            o = ((Map<String, Object>) o).get(k);
        }
        return o;
    }

    static byte[] get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Mozilla/5.0 (sagoma PackMaterialsHd)").timeout(Duration.ofMinutes(1)).build();
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            // Attesa massima per tutto il file (anche un download che si pianta a metà), poi si riprova.
            CompletableFuture<HttpResponse<byte[]>> call = HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray());
            try {
                HttpResponse<byte[]> r = call.get(5, TimeUnit.MINUTES);
                if (r.statusCode() == 200) return r.body();
                last = new IOException("HTTP " + r.statusCode() + " da " + url);
                if (r.statusCode() == 404) break;
            } catch (TimeoutException e) {
                call.cancel(true);
                last = new IOException("troppo lento: " + url);
            } catch (ExecutionException e) {
                last = e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            }
            Thread.sleep(2000L << attempt);
        }
        throw last;
    }

    /** Come PackMaterials, al lato `side`: colore, normali, ORM (ruvidità nel verde). */
    static void pack(Map<String, byte[]> maps, String id, int side, Path out) throws IOException {
        BufferedImage color = read(maps.get("color")), normal = read(maps.get("normal")), rough = read(maps.get("rough"));
        BufferedImage r = resize(rough, side);
        BufferedImage orm = new BufferedImage(r.getWidth(), r.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < r.getHeight(); y++) for (int x = 0; x < r.getWidth(); x++) {
            int g = (r.getRGB(x, y) >> 8) & 255;
            orm.setRGB(x, y, (255 << 16) | (g << 8));
        }
        // L'ORM per ultimo: c'è solo se il materiale è finito (vedi il controllo di quelli già pronti).
        write(resize(color, side), out.resolve(id + "_color.jpg"), 0.88f);
        write(resize(normal, side), out.resolve(id + "_normal.jpg"), 0.92f);
        write(orm, out.resolve(id + "_orm.jpg"), 0.9f);
    }

    static BufferedImage read(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("immagine illeggibile");
        return img;
    }

    static BufferedImage resize(BufferedImage src, int max) {
        double k = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * k)), h = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage img, Path p, float quality) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam prm = w.getDefaultWriteParam();
        prm.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        prm.setCompressionQuality(quality);
        // Prima in un file temporaneo, poi al suo posto: un file interrotto a metà non sembra pronto.
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        File f = tmp.toFile();
        f.delete();
        try (FileImageOutputStream os = new FileImageOutputStream(f)) {
            w.setOutput(os);
            w.write(null, new IIOImage(img, null, null), prm);
        }
        w.dispose();
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
    }

    /** JSON minimo (oggetti, liste, stringhe, numeri, true/false/null): basta per l'API di Poly Haven. */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object value() {
            space();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                space();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    space();
                    String k = string();
                    space();
                    expect(':');
                    m.put(k, value());
                    space();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                space();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    space();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            int start = i;
            while (i < s.length() && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) i++;
            String t = s.substring(start, i);
            return switch (t) {
                case "true" -> true;
                case "false" -> false;
                case "null" -> null;
                default -> Double.parseDouble(t);
            };
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            }
        }

        private void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalStateException("JSON: atteso '" + c + "' a " + i);
            i++;
        }

        private void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
