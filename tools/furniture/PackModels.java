import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prepara i modelli degli arredi della versione pro di Sagoma.
 *
 * Per ogni modello scrive nella cartella di uscita:
 *  - <id>.glb      modello compatto (texture ridotte a 512 px, tutto in un file);
 *  - <id>.png      miniatura in prospettiva per il catalogo;
 *  - <id>_top.png  vista dall'alto per la pianta 2D;
 * e alla fine catalog.json con misure reali, nomi, categorie, fonte e licenza.
 *
 * Uso (JDK 17+):
 *   java PackModels.java polyhaven <cartella con una sottocartella per modello (.gltf)> <tabella.tsv> <uscita>
 *   java PackModels.java sh3d <file .sh3f o .zip> <uscita> [categorie ammesse separate da |] [file dei modelli esclusi]
 * Più esecuzioni sulla stessa uscita aggiungono modelli al catalogo.
 */
public class PackModels {

    static final int TEXTURE_MAX = 1024;
    /** Triangoli al più per modello: oltre, la mesh si semplifica (a schermo sul telefono non si vede). */
    static final int MAX_TRIANGLES = Integer.MAX_VALUE;
    /** Modelli da non includere (file passato come quinto argomento in modalità sh3d). */
    static final Set<String> EXCLUDED = new HashSet<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 3) { System.err.println("Uso: vedi l'intestazione del file"); System.exit(1); }
        switch (args[0]) {
            case "polyhaven" -> polyHaven(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
            case "objaverse" -> objaverse(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
            // Miniatura di un GLB già preparato (per controllare a occhio la semplificazione).
            case "preview" -> Render.thumbnail(Gltf.load(Paths.get(args[1])).toModel(), Paths.get(args[2]));
            case "sh3d" -> {
                if (args.length > 4) for (String l : Files.readAllLines(Paths.get(args[4]))) if (!l.isBlank() && !l.startsWith("#")) EXCLUDED.add(l.trim());
                sh3d(Paths.get(args[1]), Paths.get(args[2]), args.length > 3 ? Set.of(args[3].split("\\|")) : null);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    // =====================================================================================
    // Catalogo
    // =====================================================================================

    static Map<String, Object> loadCatalog(Path out) throws IOException {
        Path f = out.resolve("catalog.json");
        Map<String, Object> byId = new LinkedHashMap<>();
        if (Files.exists(f)) {
            for (Object o : (List<?>) Json.parse(Files.readString(f))) byId.put((String) ((Map<?, ?>) o).get("model"), o);
        }
        return byId;
    }

    static void saveCatalog(Path out, Map<String, Object> byId) throws IOException {
        Files.writeString(out.resolve("catalog.json"), Json.write(new ArrayList<>(byId.values()), 0));
    }

    static long round(double v) { return Math.round(v); }
    /** Misure: mai sotto 1 cm (un piano a induzione è spesso pochi millimetri). */
    static long size(double v) { return Math.max(1, Math.round(v)); }

    // =====================================================================================
    // Poly Haven: glTF con file esterni → GLB compatto
    // =====================================================================================

    /** Tabella: id <TAB> nome italiano <TAB> categoria <TAB> simbolo <TAB> quota da terra (cm, "soffitto" = appeso). */
    static void polyHaven(Path in, Path table, Path out) throws Exception {
        Files.createDirectories(out);
        Map<String, Object> catalog = loadCatalog(out);
        for (String line : Files.readAllLines(table, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            String id = c[0].trim();
            Path dir = in.resolve(id);
            Path gltf = dir.resolve(id + ".gltf");
            if (!Files.exists(gltf)) { System.err.println("manca " + id); continue; }
            Gltf g = Gltf.load(gltf);
            Model m = g.toModel();
            byte[] glb = g.repack(TEXTURE_MAX);
            String model = "ph_" + id.toLowerCase(Locale.ROOT);
            Files.write(out.resolve(model + ".glb"), glb);
            Render.thumbnail(m, out.resolve(model + ".png"));
            Render.top(m, out.resolve(model + "_top.png"));
            double[] size = m.size();
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("model", model);
            e.put("label", c[1].trim());
            e.put("category", c[2].trim());
            e.put("symbol", c[3].trim());
            e.put("width", size(size[0] * 100));
            e.put("depth", size(size[2] * 100));
            e.put("height", size(size[1] * 100));
            String elev = c.length > 4 ? c[4].trim() : "0";
            if (elev.equals("soffitto")) { e.put("elevation", 0L); e.put("ceiling", true); }
            else { e.put("elevation", Long.parseLong(elev.isEmpty() ? "0" : elev)); e.put("ceiling", false); }
            e.put("source", "Poly Haven (polyhaven.com/a/" + id + ")");
            e.put("author", "Poly Haven");
            e.put("license", "CC0");
            catalog.put(model, e);
            System.out.printf("%-34s %4d×%4d×%4d cm  %6.0f KB%n", model, (long) e.get("width"), (long) e.get("depth"), (long) e.get("height"), glb.length / 1024.0);
        }
        saveCatalog(out, catalog);
    }

    // =====================================================================================
    // Objaverse (modelli di Sketchfab con licenza CC0 o CC-BY): GLB → GLB compatto
    // =====================================================================================

    /**
     * Tabella: uid <TAB> nome <TAB> categoria <TAB> simbolo <TAB> larghezza reale (cm) <TAB> rotazione (gradi, per
     * girare il davanti verso +z) <TAB> quota da terra ("soffitto" = appeso) <TAB> autore <TAB> licenza.
     * I modelli di Sketchfab non sono in scala: si scalano (uguale sui tre assi) alla larghezza indicata.
     */
    static void objaverse(Path in, Path table, Path out) throws Exception {
        Files.createDirectories(out);
        Map<String, Object> catalog = loadCatalog(out);
        for (String line : Files.readAllLines(table, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            String uid = c[0].trim();
            Path glbIn = in.resolve(uid + ".glb");
            if (!Files.exists(glbIn)) { System.err.println("manca " + uid); continue; }
            try {
                Gltf g = Gltf.load(glbIn);
                g.rotateY(Double.parseDouble(c[5].trim()));
                Model m = g.toModel();
                byte[] glb = g.repack(TEXTURE_MAX);
                String model = "ob_" + uid.substring(0, Math.min(16, uid.length()));
                Files.write(out.resolve(model + ".glb"), glb);
                Render.thumbnail(m, out.resolve(model + ".png"));
                Render.top(m, out.resolve(model + "_top.png"));
                double[] s = m.size();
                // La misura tipica è quella del lato orizzontale più lungo (un letto o una vasca possono essere girati).
                double k = Double.parseDouble(c[4].trim()) / Math.max(1e-9, Math.max(s[0], s[2]));
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("model", model);
                e.put("label", c[1].trim());
                e.put("category", c[2].trim());
                e.put("symbol", c[3].trim());
                e.put("width", size(s[0] * k));
                e.put("depth", size(s[2] * k));
                e.put("height", size(s[1] * k));
                String elev = c[6].trim();
                if (elev.equals("soffitto")) { e.put("elevation", 0L); e.put("ceiling", true); }
                else { e.put("elevation", Long.parseLong(elev.isEmpty() ? "0" : elev)); e.put("ceiling", false); }
                e.put("source", "Sketchfab (sketchfab.com/3d-models/" + uid + ")");
                e.put("author", c.length > 7 ? c[7].trim() : "");
                e.put("license", c.length > 8 ? c[8].trim() : "");
                catalog.put(model, e);
                System.out.printf("%-22s %-28s %4d×%4d×%4d cm %6.0f KB%n", model, c[1], (long) e.get("width"), (long) e.get("depth"), (long) e.get("height"), glb.length / 1024.0);
            } catch (Exception ex) {
                System.err.println("saltato " + uid + ": " + ex);
            }
        }
        saveCatalog(out, catalog);
    }

    // =====================================================================================
    // Sweet Home 3D: libreria .sh3f (zip con PluginFurnitureCatalog.properties e modelli OBJ)
    // =====================================================================================

    static void sh3d(Path file, Path out, Set<String> onlyCategories) throws Exception {
        Files.createDirectories(out);
        Map<String, Object> catalog = loadCatalog(out);
        List<Path> libs = new ArrayList<>();
        if (file.toString().endsWith(".zip")) {
            // Archivio di più .sh3f: si estraggono in una cartella temporanea.
            Path tmp = Files.createTempDirectory("sh3d");
            try (ZipFile z = new ZipFile(file.toFile())) {
                for (ZipEntry en : Collections.list(z.entries())) {
                    if (!en.getName().endsWith(".sh3f")) continue;
                    Path p = tmp.resolve(Paths.get(en.getName()).getFileName().toString());
                    try (InputStream is = z.getInputStream(en)) { Files.copy(is, p, StandardCopyOption.REPLACE_EXISTING); }
                    libs.add(p);
                }
            }
        } else libs.add(file);
        for (Path lib : libs) sh3dLibrary(lib, out, catalog, onlyCategories);
        saveCatalog(out, catalog);
    }

    static Properties props(ZipFile z, String name) throws IOException {
        ZipEntry e = z.getEntry(name);
        Properties p = new Properties();
        if (e != null) try (InputStream is = z.getInputStream(e)) { p.load(new InputStreamReader(is, StandardCharsets.ISO_8859_1)); }
        return p;
    }

    static void sh3dLibrary(Path lib, Path out, Map<String, Object> catalog, Set<String> onlyCategories) throws Exception {
        try (ZipFile z = new ZipFile(lib.toFile())) {
            Properties base = props(z, "PluginFurnitureCatalog.properties");
            Properties it = props(z, "PluginFurnitureCatalog_it.properties");
            String libName = base.getProperty("name", lib.getFileName().toString());
            String libLicense = base.getProperty("license", "");
            String libCreator = base.getProperty("creator", "");
            for (int i = 1; ; i++) {
                String id = base.getProperty("id#" + i);
                String modelPath = base.getProperty("model#" + i);
                if (modelPath == null) { if (i > 5000) break; if (base.getProperty("name#" + i) == null && base.getProperty("name#" + (i + 1)) == null) break; continue; }
                if ("true".equals(base.getProperty("doorOrWindow#" + i))) continue;
                String catEn = base.getProperty("category#" + i, "");
                if (onlyCategories != null && !onlyCategories.contains(catEn)) continue;
                String cat = CATEGORIES.getOrDefault(catEn, it.getProperty("category#" + i, catEn));
                String name = it.getProperty("name#" + i, base.getProperty("name#" + i, "Arredo"));
                double w = Double.parseDouble(base.getProperty("width#" + i));
                double d = Double.parseDouble(base.getProperty("depth#" + i));
                double h = Double.parseDouble(base.getProperty("height#" + i));
                double elev = Double.parseDouble(base.getProperty("elevation#" + i, "0"));
                String creator = base.getProperty("creator#" + i, libCreator);
                String license = base.getProperty("license#" + i, libLicense);
                String rot = base.getProperty("modelRotation#" + i);
                String key = "sh_" + (id != null ? id : libName + "_" + i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
                if (EXCLUDED.contains(key)) continue;
                try {
                    Model m = Obj.load(z, modelPath);
                    if (rot != null) m.rotate(Arrays.stream(rot.trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray());
                    // Come in Sweet Home 3D: il modello si adatta alle misure del catalogo (in cm).
                    m.fitTo(w / 100, h / 100, d / 100);
                    byte[] glb = m.toGlb(TEXTURE_MAX);
                    Files.write(out.resolve(key + ".glb"), glb);
                    Render.thumbnail(m, out.resolve(key + ".png"));
                    Render.top(m, out.resolve(key + "_top.png"));
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("model", key);
                    e.put("label", name);
                    e.put("category", cat);
                    e.put("symbol", guessSymbol(catEn + " " + base.getProperty("name#" + i, "")));
                    e.put("width", size(w));
                    e.put("depth", size(d));
                    e.put("height", size(h));
                    e.put("elevation", round(elev));
                    e.put("ceiling", false);
                    e.put("source", "Sweet Home 3D, libreria " + libName);
                    e.put("author", creator);
                    e.put("license", license);
                    catalog.put(key, e);
                    System.out.printf("%-40s %-22s %4.0f×%4.0f×%4.0f  %6.0f KB  %s%n", key, cat, w, d, h, glb.length / 1024.0, license);
                } catch (Exception ex) {
                    System.err.println("saltato " + key + ": " + ex);
                }
            }
        }
    }

    /** Categorie di Sweet Home 3D → categorie del catalogo di Sagoma. */
    static final Map<String, String> CATEGORIES = Map.of(
        "Kitchen", "Cucina", "Bathroom", "Bagno", "Bedroom", "Camera", "Living room", "Soggiorno",
        "Office", "Studio", "Lights", "Luci", "Exterior", "Esterni", "Miscellaneous", "Decori");

    static String guessSymbol(String s) {
        s = s.toLowerCase(Locale.ROOT);
        if (s.matches(".*(toilet|wc|bidet).*")) return "Toilet";
        if (s.matches(".*(bath ?tub|bathtub).*")) return "Tub";
        if (s.contains("shower")) return "Shower";
        if (s.matches(".*(sink|basin|washbasin).*")) return "Sink";
        if (s.matches(".*(cooker|hob|stove|oven).*")) return "Hob";
        if (s.matches(".*(fridge|refrigerator|freezer|washing|dryer|dishwasher|machine|microwave).*")) return "Appliance";
        if (s.contains("bed")) return "Bed";
        if (s.matches(".*(sofa|couch).*")) return "Sofa";
        if (s.matches(".*(armchair|chair|stool|seat).*")) return "Chair";
        if (s.matches(".*(table|desk).*")) return "Table";
        if (s.matches(".*(lamp|light).*")) return "Lamp";
        if (s.matches(".*(plant|flower|tree).*")) return "Plant";
        if (s.contains("rug") || s.contains("carpet")) return "Rug";
        if (s.matches(".*(tv|television|screen).*")) return "Tv";
        return "Cabinet";
    }

    // =====================================================================================
    // Modello in memoria (per miniature e per scrivere il GLB degli OBJ)
    // =====================================================================================

    static final class Material {
        float[] color = {0.8f, 0.8f, 0.8f, 1f};
        BufferedImage texture;
        boolean blend;
        float roughness = 0.8f;
        float metallic = 0f;
        /** Colore medio visto nelle miniature (colore × media della texture). */
        float[] shade() {
            float[] c = color.clone();
            if (texture != null) {
                double r = 0, g = 0, b = 0; int n = 0;
                int sx = Math.max(1, texture.getWidth() / 32), sy = Math.max(1, texture.getHeight() / 32);
                for (int y = 0; y < texture.getHeight(); y += sy) for (int x = 0; x < texture.getWidth(); x += sx) {
                    int p = texture.getRGB(x, y);
                    r += ((p >> 16) & 255) / 255.0; g += ((p >> 8) & 255) / 255.0; b += (p & 255) / 255.0; n++;
                }
                c[0] *= r / n; c[1] *= g / n; c[2] *= b / n;
            }
            return c;
        }
    }

    static final class Prim {
        float[] pos; float[] nor; float[] uv; int[] idx;
        Material mat;
    }

    static final class Model {
        final List<Prim> prims = new ArrayList<>();

        double[] bounds() {
            double[] b = {1e30, 1e30, 1e30, -1e30, -1e30, -1e30};
            for (Prim p : prims) for (int i = 0; i < p.pos.length; i += 3) for (int k = 0; k < 3; k++) {
                b[k] = Math.min(b[k], p.pos[i + k]); b[k + 3] = Math.max(b[k + 3], p.pos[i + k]);
            }
            return b;
        }

        double[] size() { double[] b = bounds(); return new double[]{b[3] - b[0], b[4] - b[1], b[5] - b[2]}; }

        /** Matrice 3×3 (per righe, come modelRotation di Sweet Home 3D). */
        void rotate(double[] m) {
            for (Prim p : prims) {
                for (float[] a : new float[][]{p.pos, p.nor}) if (a != null) for (int i = 0; i < a.length; i += 3) {
                    double x = a[i], y = a[i + 1], z = a[i + 2];
                    a[i] = (float) (m[0] * x + m[1] * y + m[2] * z);
                    a[i + 1] = (float) (m[3] * x + m[4] * y + m[5] * z);
                    a[i + 2] = (float) (m[6] * x + m[7] * y + m[8] * z);
                }
            }
        }

        /** Scala e sposta: ingombro w × h × d metri, base al centro nell'origine. */
        void fitTo(double w, double h, double d) {
            double[] b = bounds();
            double sx = w / Math.max(1e-6, b[3] - b[0]), sy = h / Math.max(1e-6, b[4] - b[1]), sz = d / Math.max(1e-6, b[5] - b[2]);
            double cx = (b[0] + b[3]) / 2, cz = (b[2] + b[5]) / 2;
            for (Prim p : prims) {
                for (int i = 0; i < p.pos.length; i += 3) {
                    p.pos[i] = (float) ((p.pos[i] - cx) * sx);
                    p.pos[i + 1] = (float) ((p.pos[i + 1] - b[1]) * sy);
                    p.pos[i + 2] = (float) ((p.pos[i + 2] - cz) * sz);
                }
                if (p.nor != null) for (int i = 0; i < p.nor.length; i += 3) {
                    double x = p.nor[i] / sx, y = p.nor[i + 1] / sy, z = p.nor[i + 2] / sz;
                    double l = Math.sqrt(x * x + y * y + z * z);
                    if (l > 0) { p.nor[i] = (float) (x / l); p.nor[i + 1] = (float) (y / l); p.nor[i + 2] = (float) (z / l); }
                }
            }
        }

        /** Semplifica le parti troppo dense (al più [MAX_TRIANGLES] triangoli in tutto) e toglie i vertici inutili. */
        void simplify() {
            long total = 0;
            for (Prim p : prims) total += p.idx.length / 3;
            if (total <= MAX_TRIANGLES) return;
            double ratio = (double) MAX_TRIANGLES / total;
            for (Prim p : prims) {
                int[] idx = Simplify.run(p.pos, p.idx, Math.max(12, (int) Math.round(p.idx.length / 3 * ratio)));
                int nv = p.pos.length / 3;
                int[] remap = new int[nv];
                Arrays.fill(remap, -1);
                int used = 0;
                for (int k = 0; k < idx.length; k++) { if (remap[idx[k]] < 0) remap[idx[k]] = used++; idx[k] = remap[idx[k]]; }
                p.pos = compact(p.pos, 3, remap, used);
                if (p.nor != null) p.nor = compact(p.nor, 3, remap, used);
                if (p.uv != null) p.uv = compact(p.uv, 2, remap, used);
                p.idx = idx;
            }
        }

        static float[] compact(float[] a, int n, int[] remap, int used) {
            float[] out = new float[used * n];
            for (int v = 0; v < remap.length; v++) if (remap[v] >= 0) System.arraycopy(a, v * n, out, remap[v] * n, n);
            return out;
        }

        /** GLB con un materiale PBR per primitiva (colore, texture del colore, trasparenza). */
        byte[] toGlb(int maxTex) throws IOException {
            simplify();
            GlbWriter w = new GlbWriter();
            List<Object> meshesPrims = new ArrayList<>();
            List<Object> materials = new ArrayList<>();
            List<Object> textures = new ArrayList<>();
            List<Object> images = new ArrayList<>();
            Map<BufferedImage, Integer> texIndex = new IdentityHashMap<>();
            Map<Material, Integer> matIndex = new IdentityHashMap<>();
            for (Prim p : prims) {
                if (p.idx.length == 0) continue;
                Integer mi = matIndex.get(p.mat);
                if (mi == null) {
                    Map<String, Object> pbr = new LinkedHashMap<>();
                    pbr.put("baseColorFactor", List.of((double) p.mat.color[0], (double) p.mat.color[1], (double) p.mat.color[2], (double) p.mat.color[3]));
                    pbr.put("metallicFactor", (double) p.mat.metallic);
                    pbr.put("roughnessFactor", (double) p.mat.roughness);
                    if (p.mat.texture != null && p.uv != null) {
                        Integer ti = texIndex.get(p.mat.texture);
                        if (ti == null) {
                            byte[] data = Images.encode(Images.resize(p.mat.texture, maxTex));
                            int view = w.view(data, -1);
                            images.add(Map.of("bufferView", (long) view, "mimeType", Images.hasAlpha(p.mat.texture) ? "image/png" : "image/jpeg"));
                            textures.add(Map.of("source", (long) (images.size() - 1)));
                            ti = textures.size() - 1;
                            texIndex.put(p.mat.texture, ti);
                        }
                        pbr.put("baseColorTexture", Map.of("index", (long) ti));
                    }
                    Map<String, Object> mat = new LinkedHashMap<>();
                    mat.put("pbrMetallicRoughness", pbr);
                    if (p.mat.blend || p.mat.color[3] < 0.99f) mat.put("alphaMode", "BLEND");
                    mat.put("doubleSided", true);
                    materials.add(mat);
                    mi = materials.size() - 1;
                    matIndex.put(p.mat, mi);
                }
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("POSITION", (long) w.accessorF(p.pos, 3, "VEC3", true));
                if (p.nor != null) attrs.put("NORMAL", (long) w.accessorNormal(p.nor));
                if (p.uv != null && p.mat.texture != null) attrs.put("TEXCOORD_0", (long) w.accessorUv(p.uv));
                Map<String, Object> prim = new LinkedHashMap<>();
                prim.put("attributes", attrs);
                prim.put("indices", (long) w.accessorIdx(p.idx));
                prim.put("material", (long) mi);
                meshesPrims.add(prim);
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("asset", Map.of("version", "2.0", "generator", "Sagoma PackModels"));
            json.put("scene", 0L);
            json.put("scenes", List.of(Map.of("nodes", List.of(0L))));
            json.put("nodes", List.of(Map.of("mesh", 0L)));
            json.put("meshes", List.of(Map.of("primitives", meshesPrims)));
            json.put("materials", materials);
            if (!textures.isEmpty()) {
                json.put("textures", textures);
                json.put("images", images);
            }
            return w.finish(json);
        }
    }

    // =====================================================================================
    // Scrittura GLB
    // =====================================================================================

    static final class GlbWriter {
        final ByteArrayOutputStream bin = new ByteArrayOutputStream();
        final List<Object> views = new ArrayList<>();
        final List<Object> accessors = new ArrayList<>();

        int view(byte[] data, int target) {
            while (bin.size() % 4 != 0) bin.write(0);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("buffer", 0L);
            v.put("byteOffset", (long) bin.size());
            v.put("byteLength", (long) data.length);
            if (target > 0) v.put("target", (long) target);
            bin.writeBytes(data);
            views.add(v);
            return views.size() - 1;
        }

        int accessorF(float[] a, int n, String type, boolean bounds) {
            ByteBuffer bb = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : a) bb.putFloat(f);
            int v = view(bb.array(), 34962);
            Map<String, Object> acc = new LinkedHashMap<>();
            acc.put("bufferView", (long) v);
            acc.put("componentType", 5126L);
            acc.put("count", (long) (a.length / n));
            acc.put("type", type);
            if (bounds) {
                List<Object> min = new ArrayList<>(), max = new ArrayList<>();
                for (int k = 0; k < n; k++) {
                    double lo = 1e30, hi = -1e30;
                    for (int i = k; i < a.length; i += n) { lo = Math.min(lo, a[i]); hi = Math.max(hi, a[i]); }
                    min.add(lo); max.add(hi);
                }
                acc.put("min", min); acc.put("max", max);
            }
            accessors.add(acc);
            return accessors.size() - 1;
        }

        /** Normali in 3 byte con segno (normalizzati), passo 4 (KHR_mesh_quantization). */
        int accessorNormal(float[] n) {
            int count = n.length / 3;
            ByteBuffer bb = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) {
                for (int k = 0; k < 3; k++) bb.put((byte) Math.max(-127, Math.min(127, Math.round(n[i * 3 + k] * 127))));
                bb.put((byte) 0);
            }
            int v = view(bb.array(), 34962);
            ((Map<String, Object>) views.get(v)).put("byteStride", 4L);
            Map<String, Object> acc = new LinkedHashMap<>();
            acc.put("bufferView", (long) v);
            acc.put("componentType", 5120L);
            acc.put("normalized", true);
            acc.put("count", (long) count);
            acc.put("type", "VEC3");
            accessors.add(acc);
            quantized = true;
            return accessors.size() - 1;
        }

        /** Coordinate delle texture: 16 bit normalizzati se stanno in [0, 1], altrimenti float. */
        int accessorUv(float[] uv) {
            for (float f : uv) if (f < 0 || f > 1) return accessorF(uv, 2, "VEC2", false);
            ByteBuffer bb = ByteBuffer.allocate(uv.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : uv) bb.putShort((short) Math.round(f * 65535));
            int v = view(bb.array(), 34962);
            Map<String, Object> acc = new LinkedHashMap<>();
            acc.put("bufferView", (long) v);
            acc.put("componentType", 5123L);
            acc.put("normalized", true);
            acc.put("count", (long) (uv.length / 2));
            acc.put("type", "VEC2");
            accessors.add(acc);
            quantized = true;
            return accessors.size() - 1;
        }

        boolean quantized;

        int accessorIdx(int[] idx) {
            int max = 0; for (int i : idx) max = Math.max(max, i);
            boolean small = max < 65535;
            ByteBuffer bb = ByteBuffer.allocate(idx.length * (small ? 2 : 4)).order(ByteOrder.LITTLE_ENDIAN);
            for (int i : idx) { if (small) bb.putShort((short) i); else bb.putInt(i); }
            int v = view(bb.array(), 34963);
            accessors.add(Map.of("bufferView", (long) v, "componentType", small ? 5123L : 5125L, "count", (long) idx.length, "type", "SCALAR"));
            return accessors.size() - 1;
        }

        @SuppressWarnings("unchecked")
        byte[] finish(Map<String, Object> json) throws IOException {
            while (bin.size() % 4 != 0) bin.write(0);
            if (quantized) {
                for (String k : List.of("extensionsUsed", "extensionsRequired")) {
                    List<Object> l = new ArrayList<>((List<Object>) json.getOrDefault(k, new ArrayList<>()));
                    if (!l.contains("KHR_mesh_quantization")) l.add("KHR_mesh_quantization");
                    json.put(k, l);
                }
            }
            json.put("bufferViews", views);
            json.put("accessors", accessors);
            json.put("buffers", List.of(Map.of("byteLength", (long) bin.size())));
            return pack(Json.write(json, -1).getBytes(StandardCharsets.UTF_8), bin.toByteArray());
        }

        static byte[] pack(byte[] json, byte[] bin) {
            int jp = (4 - json.length % 4) % 4, bp = (4 - bin.length % 4) % 4;
            int total = 12 + 8 + json.length + jp + (bin.length > 0 ? 8 + bin.length + bp : 0);
            ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(0x46546C67).putInt(2).putInt(total);
            out.putInt(json.length + jp).putInt(0x4E4F534A).put(json);
            for (int i = 0; i < jp; i++) out.put((byte) ' ');
            if (bin.length > 0) {
                out.putInt(bin.length + bp).putInt(0x004E4942).put(bin);
                for (int i = 0; i < bp; i++) out.put((byte) 0);
            }
            return out.array();
        }
    }

    // =====================================================================================
    // Lettura glTF (Poly Haven)
    // =====================================================================================

    static final class Gltf {
        Map<String, Object> json;
        List<byte[]> buffers = new ArrayList<>();
        Path dir;

        static Gltf load(Path file) throws IOException {
            if (file.toString().toLowerCase(Locale.ROOT).endsWith(".glb")) return loadGlb(file);
            Gltf g = new Gltf();
            g.dir = file.getParent();
            @SuppressWarnings("unchecked") Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(file));
            g.json = j;
            for (Object b : list(j, "buffers")) g.buffers.add(Files.readAllBytes(g.dir.resolve(decode((String) ((Map<?, ?>) b).get("uri")))));
            return g;
        }

        /** GLB: blocco JSON e blocco binario (il buffer 0 senza uri). */
        @SuppressWarnings("unchecked")
        static Gltf loadGlb(Path file) throws IOException {
            byte[] b = Files.readAllBytes(file);
            ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.getInt(0) != 0x46546C67) throw new IOException("non è un GLB");
            Gltf g = new Gltf();
            g.dir = file.getParent();
            int pos = 12;
            byte[] bin = new byte[0];
            while (pos + 8 <= b.length) {
                int len = bb.getInt(pos), type = bb.getInt(pos + 4);
                if (type == 0x4E4F534A) g.json = (Map<String, Object>) Json.parse(new String(b, pos + 8, len, StandardCharsets.UTF_8));
                else if (type == 0x004E4942) bin = Arrays.copyOfRange(b, pos + 8, pos + 8 + len);
                pos += 8 + len;
            }
            for (Object o : list(g.json, "buffers")) {
                Map<String, Object> buf = map(o);
                if (buf.containsKey("uri")) {
                    String uri = (String) buf.get("uri");
                    if (uri.startsWith("data:")) g.buffers.add(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)));
                    else g.buffers.add(Files.readAllBytes(g.dir.resolve(decode(uri))));
                } else g.buffers.add(bin);
            }
            return g;
        }

        /** Gira tutta la scena attorno alla verticale (gradi): un nuovo nodo radice con la rotazione. */
        void rotateY(double degrees) {
            if (Math.abs(degrees) < 1e-6) return;
            double h = Math.toRadians(degrees) / 2;
            Map<String, Object> scene = map(list(json, "scenes").get(json.containsKey("scene") ? i(json.get("scene")) : 0));
            List<Object> nodes = list(json, "nodes");
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("name", "sagoma_rotation");
            root.put("rotation", List.of(0.0, Math.sin(h), 0.0, Math.cos(h)));
            root.put("children", new ArrayList<>(list(scene, "nodes")));
            nodes.add(root);
            json.put("nodes", nodes);
            scene.put("nodes", new ArrayList<>(List.of((long) (nodes.size() - 1))));
        }

        /** Immagine da un file vicino al modello o incorporata ("data:image/png;base64,…"). */
        BufferedImage readUri(String uri) throws IOException {
            if (uri.startsWith("data:")) return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
            return ImageIO.read(dir.resolve(decode(uri)).toFile());
        }

        static String decode(String uri) { return java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8); }

        @SuppressWarnings("unchecked") static List<Object> list(Map<String, Object> m, String k) { Object o = m.get(k); return o == null ? new ArrayList<>() : (List<Object>) o; }
        @SuppressWarnings("unchecked") static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }
        static int i(Object o) { return ((Number) o).intValue(); }

        ByteBuffer viewData(int view) {
            Map<String, Object> v = map(list(json, "bufferViews").get(view));
            byte[] b = buffers.get(i(v.get("buffer")));
            int off = v.containsKey("byteOffset") ? i(v.get("byteOffset")) : 0;
            return ByteBuffer.wrap(b, off, i(v.get("byteLength"))).slice().order(ByteOrder.LITTLE_ENDIAN);
        }

        /** Valori di un accessor come float (anche interi normalizzati) o interi per gli indici. */
        float[] readFloats(int acc) {
            Map<String, Object> a = map(list(json, "accessors").get(acc));
            int count = i(a.get("count"));
            int n = switch ((String) a.get("type")) { case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; default -> 16; };
            int ct = i(a.get("componentType"));
            int cs = ct == 5126 || ct == 5125 ? 4 : ct == 5123 || ct == 5122 ? 2 : 1;
            Map<String, Object> v = map(list(json, "bufferViews").get(i(a.get("bufferView"))));
            int stride = v.containsKey("byteStride") ? i(v.get("byteStride")) : n * cs;
            ByteBuffer bb = viewData(i(a.get("bufferView")));
            int base = a.containsKey("byteOffset") ? i(a.get("byteOffset")) : 0;
            boolean norm = Boolean.TRUE.equals(a.get("normalized"));
            float[] out = new float[count * n];
            for (int e = 0; e < count; e++) for (int k = 0; k < n; k++) {
                int p = base + e * stride + k * cs;
                float val = switch (ct) {
                    case 5126 -> bb.getFloat(p);
                    case 5125 -> (float) Integer.toUnsignedLong(bb.getInt(p));
                    case 5123 -> (bb.getShort(p) & 0xFFFF) / (norm ? 65535f : 1f);
                    case 5122 -> bb.getShort(p) / (norm ? 32767f : 1f);
                    case 5121 -> (bb.get(p) & 0xFF) / (norm ? 255f : 1f);
                    default -> bb.get(p) / (norm ? 127f : 1f);
                };
                out[e * n + k] = val;
            }
            return out;
        }

        BufferedImage image(int tex) throws IOException {
            Map<String, Object> t = map(list(json, "textures").get(tex));
            Map<String, Object> img = map(list(json, "images").get(i(t.get("source"))));
            if (img.containsKey("uri")) return readUri((String) img.get("uri"));
            ByteBuffer bb = viewData(i(img.get("bufferView")));
            byte[] data = new byte[bb.remaining()]; bb.get(data);
            return ImageIO.read(new ByteArrayInputStream(data));
        }

        /** Geometria di tutta la scena, con le trasformazioni dei nodi applicate (per le miniature). */
        Model toModel() throws IOException {
            Model m = new Model();
            Map<Integer, Material> mats = new HashMap<>();
            Map<String, Object> scene = map(list(json, "scenes").get(json.containsKey("scene") ? i(json.get("scene")) : 0));
            for (Object n : list(scene, "nodes")) walk(i(n), Mat.identity(), m, mats);
            return m;
        }

        void walk(int nodeIdx, double[] parent, Model m, Map<Integer, Material> mats) throws IOException {
            Map<String, Object> node = map(list(json, "nodes").get(nodeIdx));
            double[] local = Mat.fromNode(node);
            double[] world = Mat.mul(parent, local);
            if (node.containsKey("mesh")) {
                Map<String, Object> mesh = map(list(json, "meshes").get(i(node.get("mesh"))));
                for (Object po : list(mesh, "primitives")) {
                    Map<String, Object> pr = map(po);
                    Map<String, Object> at = map(pr.get("attributes"));
                    Prim p = new Prim();
                    p.pos = readFloats(i(at.get("POSITION")));
                    p.nor = at.containsKey("NORMAL") ? readFloats(i(at.get("NORMAL"))) : null;
                    Mat.transform(world, p.pos, p.nor);
                    if (pr.containsKey("indices")) {
                        float[] f = readFloats(i(pr.get("indices")));
                        p.idx = new int[f.length];
                        for (int k = 0; k < f.length; k++) p.idx[k] = (int) f[k];
                    } else {
                        p.idx = new int[p.pos.length / 3];
                        for (int k = 0; k < p.idx.length; k++) p.idx[k] = k;
                    }
                    int mi = pr.containsKey("material") ? i(pr.get("material")) : -1;
                    p.mat = mats.computeIfAbsent(mi, k -> material(k));
                    m.prims.add(p);
                }
            }
            for (Object c : list(node, "children")) walk(i(c), world, m, mats);
        }

        Material material(int idx) {
            Material mat = new Material();
            if (idx < 0) return mat;
            Map<String, Object> mj = map(list(json, "materials").get(idx));
            Map<String, Object> pbr = mj.containsKey("pbrMetallicRoughness") ? map(mj.get("pbrMetallicRoughness")) : Map.of();
            if (pbr.containsKey("baseColorFactor")) {
                List<?> c = (List<?>) pbr.get("baseColorFactor");
                for (int k = 0; k < 4; k++) mat.color[k] = ((Number) c.get(k)).floatValue();
            } else mat.color = new float[]{1, 1, 1, 1};
            if (pbr.containsKey("baseColorTexture")) {
                try { mat.texture = image(i(map(pbr.get("baseColorTexture")).get("index"))); } catch (IOException e) { /* senza texture */ }
            }
            mat.blend = "BLEND".equals(mj.get("alphaMode"));
            return mat;
        }

        /** Triangoli di una primitiva (dal numero di indici o di vertici). */
        long triangles(Map<String, Object> pr) {
            if ((pr.containsKey("mode") ? i(pr.get("mode")) : 4) != 4) return 0;
            Map<String, Object> at = map(pr.get("attributes"));
            int acc = pr.containsKey("indices") ? i(pr.get("indices")) : at.containsKey("POSITION") ? i(at.get("POSITION")) : -1;
            return acc < 0 ? 0 : i(map(list(json, "accessors").get(acc)).get("count")) / 3;
        }

        /** Tutto in un GLB: stesso JSON, un solo buffer, immagini ridotte e incorporate. */
        byte[] repack(int maxTex) throws IOException {
            GlbWriter w = new GlbWriter();
            // Geometria riscritta in forma compatta: posizioni float, normali in byte, uv a 16 bit;
            // le tangenti si tolgono (Filament le ricalcola), come ossa e animazioni. Le mesh troppo
            // dense si semplificano: al più MAX_TRIANGLES triangoli per modello, divisi tra le parti.
            long total = 0;
            for (Object mo : list(json, "meshes")) for (Object po : list(map(mo), "primitives")) total += triangles(map(po));
            double keepRatio = total > MAX_TRIANGLES ? (double) MAX_TRIANGLES / total : 1.0;
            List<Object> meshes = new ArrayList<>();
            for (Object mo : list(json, "meshes")) {
                Map<String, Object> mesh = new LinkedHashMap<>(map(mo));
                List<Object> prims = new ArrayList<>();
                for (Object po : list(mesh, "primitives")) {
                    Map<String, Object> pr = new LinkedHashMap<>(map(po));
                    pr.remove("targets");
                    Map<String, Object> at = map(pr.get("attributes"));
                    if (!at.containsKey("POSITION")) continue;
                    float[] posData = readFloats(i(at.get("POSITION")));
                    int nv = posData.length / 3;
                    int[] idx;
                    if (pr.containsKey("indices")) {
                        float[] f = readFloats(i(pr.get("indices")));
                        idx = new int[f.length];
                        for (int k = 0; k < f.length; k++) idx[k] = (int) f[k];
                    } else {
                        idx = new int[nv];
                        for (int k = 0; k < nv; k++) idx[k] = k;
                    }
                    int mode = pr.containsKey("mode") ? i(pr.get("mode")) : 4;
                    if (mode == 4 && keepRatio < 1) idx = Simplify.run(posData, idx, Math.max(12, (int) Math.round(idx.length / 3 * keepRatio)));
                    if (idx.length == 0) continue;
                    // Solo i vertici ancora usati, rinumerati.
                    int[] remap = new int[nv];
                    Arrays.fill(remap, -1);
                    int used = 0;
                    for (int k = 0; k < idx.length; k++) { if (remap[idx[k]] < 0) remap[idx[k]] = used++; idx[k] = remap[idx[k]]; }
                    Map<String, Object> attrs = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> a : at.entrySet()) {
                        String sem = a.getKey();
                        // I colori dei vertici di Poly Haven sono maschere per Blender: il motore li
                        // moltiplicherebbe per il colore e scurirebbe il modello.
                        if (sem.startsWith("TANGENT") || sem.startsWith("JOINTS") || sem.startsWith("WEIGHTS") || sem.startsWith("COLOR")) continue;
                        int src = i(a.getValue());
                        float[] data = sem.equals("POSITION") ? posData : readFloats(src);
                        int n = data.length / nv;
                        float[] compact = new float[used * n];
                        for (int v = 0; v < nv; v++) if (remap[v] >= 0) System.arraycopy(data, v * n, compact, remap[v] * n, n);
                        int dst;
                        if (sem.equals("POSITION")) dst = w.accessorF(compact, 3, "VEC3", true);
                        else if (sem.equals("NORMAL")) dst = w.accessorNormal(compact);
                        else if (sem.startsWith("TEXCOORD")) dst = w.accessorUv(compact);
                        else dst = w.accessorF(compact, n, (String) map(list(json, "accessors").get(src)).get("type"), false);
                        attrs.put(sem, (long) dst);
                    }
                    pr.put("attributes", attrs);
                    pr.put("indices", (long) w.accessorIdx(idx));
                    prims.add(pr);
                }
                mesh.put("primitives", prims);
                meshes.add(mesh);
            }
            for (Object io : list(json, "images")) {
                Map<String, Object> img = map(io);
                BufferedImage src;
                if (img.containsKey("uri")) src = readUri((String) img.remove("uri"));
                else { ByteBuffer bb = viewData(i(img.get("bufferView"))); byte[] d = new byte[bb.remaining()]; bb.get(d); src = ImageIO.read(new ByteArrayInputStream(d)); }
                boolean alpha = Images.hasAlpha(src);
                BufferedImage small = Images.resize(src, maxTex);
                byte[] data = alpha ? Images.png(small) : Images.encode(small);
                img.put("bufferView", (long) w.view(data, -1));
                img.put("mimeType", alpha ? "image/png" : "image/jpeg");
            }
            Map<String, Object> out = new LinkedHashMap<>(json);
            out.put("meshes", meshes);
            out.remove("skins");
            out.remove("animations");
            out.remove("bufferViews");
            out.remove("accessors");
            List<Object> nodes = new ArrayList<>();
            for (Object n : list(json, "nodes")) { Map<String, Object> nn = new LinkedHashMap<>(map(n)); nn.remove("skin"); nn.remove("weights"); nodes.add(nn); }
            out.put("nodes", nodes);
            return w.finish(out);
        }
    }

    // =====================================================================================
    // Lettura OBJ/MTL (Sweet Home 3D)
    // =====================================================================================

    static final class Obj {
        static Model load(ZipFile z, String path) throws IOException {
            // Il modello può essere un file dentro la libreria o dentro uno zip annidato ("x.zip!/y.obj").
            String p = path.startsWith("/") ? path.substring(1) : path;
            ZipFile inner = z;
            String objName = p;
            Map<String, byte[]> files = new HashMap<>();
            if (p.contains("!/")) {
                String zipName = p.substring(0, p.indexOf("!/"));
                objName = p.substring(p.indexOf("!/") + 2);
                ZipEntry ze = z.getEntry(zipName);
                files = unzipAll(z, ze);
            } else {
                // Il modello e le sue texture stanno nella stessa cartella della libreria.
                String folder = p.contains("/") ? p.substring(0, p.lastIndexOf('/') + 1) : "";
                for (ZipEntry e : Collections.list(z.entries())) {
                    if (!e.isDirectory() && e.getName().startsWith(folder)) try (InputStream is = z.getInputStream(e)) { files.put(e.getName().substring(folder.length()), is.readAllBytes()); }
                }
                objName = p.substring(folder.length());
            }
            byte[] objData = files.get(objName);
            if (objData == null) {
                // Una sola voce nella cartella del modello: il nome può essere diverso.
                objName = files.keySet().stream().filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".obj")).findFirst().orElseThrow(() -> new IOException("OBJ non trovato: " + path));
                objData = files.get(objName);
            }
            if (!objName.toLowerCase(Locale.ROOT).endsWith(".obj")) throw new IOException("formato non gestito: " + objName);
            String dirOfObj = objName.contains("/") ? objName.substring(0, objName.lastIndexOf('/') + 1) : "";
            return parse(new String(objData, StandardCharsets.UTF_8), files, dirOfObj);
        }

        static Map<String, byte[]> unzipAll(ZipFile z, ZipEntry ze) throws IOException {
            Map<String, byte[]> files = new HashMap<>();
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(z.getInputStream(ze))) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) if (!e.isDirectory()) files.put(e.getName(), zis.readAllBytes());
            }
            return files;
        }

        static Model parse(String text, Map<String, byte[]> files, String dir) throws IOException {
            List<float[]> v = new ArrayList<>(), vn = new ArrayList<>(), vt = new ArrayList<>();
            Map<String, Material> mtl = new HashMap<>();
            Map<Material, Builder> builders = new LinkedHashMap<>();
            Material current = new Material();
            for (String raw : text.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v" -> v.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    case "vn" -> vn.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    case "vt" -> vt.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0});
                    case "mtllib" -> {
                        String name = line.substring(7).trim();
                        byte[] d = files.get(dir + name);
                        if (d == null) d = files.get(name);
                        if (d != null) mtl.putAll(parseMtl(new String(d, StandardCharsets.UTF_8), files, dir));
                    }
                    case "usemtl" -> current = mtl.getOrDefault(line.substring(6).trim(), current);
                    case "f" -> {
                        Builder b = builders.computeIfAbsent(current, Builder::new);
                        int[][] corners = new int[t.length - 1][];
                        for (int k = 1; k < t.length; k++) {
                            String[] s = t[k].split("/", -1);
                            int pi = ref(s[0], v.size());
                            int ti = s.length > 1 && !s[1].isEmpty() ? ref(s[1], vt.size()) : -1;
                            int ni = s.length > 2 && !s[2].isEmpty() ? ref(s[2], vn.size()) : -1;
                            corners[k - 1] = new int[]{pi, ti, ni};
                        }
                        for (int k = 1; k + 1 < corners.length; k++) b.tri(corners[0], corners[k], corners[k + 1], v, vt, vn);
                    }
                    default -> { }
                }
            }
            Model m = new Model();
            for (Builder b : builders.values()) m.prims.add(b.build());
            return m;
        }

        static int ref(String s, int size) { int i = Integer.parseInt(s); return i < 0 ? size + i : i - 1; }
        static float f(String s) { return Float.parseFloat(s); }

        static Map<String, Material> parseMtl(String text, Map<String, byte[]> files, String dir) throws IOException {
            Map<String, Material> out = new HashMap<>();
            Material cur = null;
            for (String raw : text.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "newmtl" -> { cur = new Material(); out.put(line.substring(6).trim(), cur); }
                    case "Kd" -> { if (cur != null) { cur.color[0] = f(t[1]); cur.color[1] = f(t[2]); cur.color[2] = f(t[3]); } }
                    case "d" -> { if (cur != null) cur.color[3] = f(t[1]); }
                    case "Tr" -> { if (cur != null) cur.color[3] = 1 - f(t[1]); }
                    case "Ns" -> { if (cur != null) cur.roughness = (float) Math.max(0.05, Math.min(1, 1 - Math.sqrt(f(t[1]) / 1000.0))); }
                    case "map_Kd" -> {
                        if (cur == null) break;
                        String name = t[t.length - 1];
                        byte[] d = files.get(dir + name);
                        if (d == null) d = files.get(name);
                        if (d != null) {
                            cur.texture = ImageIO.read(new ByteArrayInputStream(d));
                            // Con una texture il colore diffuso di solito non la deve scurire.
                            if (cur.texture != null) { cur.color[0] = 1; cur.color[1] = 1; cur.color[2] = 1; }
                        }
                    }
                    default -> { }
                }
            }
            return out;
        }

        /** Angolo (gradi) oltre il quale uno spigolo resta vivo: sotto, la superficie si ammorbidisce. */
        static final double CREASE = 40;

        /**
         * Triangoli di un materiale. Le normali mancanti si calcolano come media delle facce vicine che
         * formano un angolo minore di [CREASE] (così i vertici si condividono e gli spigoli restano netti);
         * i vertici uguali (posizione, uv, normale) si scrivono una volta sola.
         */
        static final class Builder {
            final Material mat;
            /** Per ogni triangolo: 3 × (posizione, uv, normale del file) e la normale della faccia (pesata con l'area). */
            final List<int[]> corners = new ArrayList<>();
            final List<float[]> faceNormals = new ArrayList<>();
            boolean hasUv = true;
            List<float[]> v, vt, vn;
            Builder(Material m) { mat = m; }

            void tri(int[] a, int[] b, int[] c, List<float[]> v, List<float[]> vt, List<float[]> vn) {
                this.v = v; this.vt = vt; this.vn = vn;
                float[] pa = v.get(a[0]), pb = v.get(b[0]), pc = v.get(c[0]);
                float ux = pb[0] - pa[0], uy = pb[1] - pa[1], uz = pb[2] - pa[2];
                float wx = pc[0] - pa[0], wy = pc[1] - pa[1], wz = pc[2] - pa[2];
                float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
                if (nx * nx + ny * ny + nz * nz < 1e-24f) return;
                if (a[1] < 0 || b[1] < 0 || c[1] < 0) hasUv = false;
                corners.add(new int[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]});
                faceNormals.add(new float[]{nx, ny, nz});
            }

            Prim build() {
                // Facce attorno a ogni posizione, per le normali mancanti.
                Map<Integer, List<Integer>> around = new HashMap<>();
                for (int f = 0; f < corners.size(); f++) for (int k = 0; k < 3; k++) around.computeIfAbsent(corners.get(f)[k * 3], x -> new ArrayList<>()).add(f);
                double cos = Math.cos(Math.toRadians(CREASE));
                Map<String, Integer> index = new HashMap<>();
                List<Float> pos = new ArrayList<>(), nor = new ArrayList<>(), uv = new ArrayList<>();
                List<Integer> idx = new ArrayList<>();
                for (int f = 0; f < corners.size(); f++) {
                    int[] c = corners.get(f);
                    float[] fn = unit(faceNormals.get(f));
                    for (int k = 0; k < 3; k++) {
                        int pi = c[k * 3], ti = c[k * 3 + 1], ni = c[k * 3 + 2];
                        float[] n;
                        if (ni >= 0) n = vn.get(ni);
                        else {
                            float sx = 0, sy = 0, sz = 0;
                            for (int g : around.get(pi)) {
                                float[] gn = faceNormals.get(g);
                                float[] gu = unit(gn);
                                if (gu[0] * fn[0] + gu[1] * fn[1] + gu[2] * fn[2] >= cos) { sx += gn[0]; sy += gn[1]; sz += gn[2]; }
                            }
                            n = unit(new float[]{sx, sy, sz});
                        }
                        // Normale arrotondata nella chiave: vertici quasi uguali si fondono.
                        String key = pi + "/" + (hasUv ? ti : -1) + "/" + Math.round(n[0] * 127) + "," + Math.round(n[1] * 127) + "," + Math.round(n[2] * 127);
                        Integer i = index.get(key);
                        if (i == null) {
                            i = pos.size() / 3;
                            float[] p = v.get(pi);
                            pos.add(p[0]); pos.add(p[1]); pos.add(p[2]);
                            nor.add(n[0]); nor.add(n[1]); nor.add(n[2]);
                            float[] t = hasUv && ti >= 0 ? vt.get(ti) : null;
                            uv.add(t != null ? t[0] : 0f); uv.add(t != null ? 1 - t[1] : 0f);
                            index.put(key, i);
                        }
                        idx.add(i);
                    }
                }
                Prim p = new Prim();
                p.mat = mat;
                p.pos = toArray(pos); p.nor = toArray(nor); p.uv = hasUv ? toArray(uv) : null;
                p.idx = idx.stream().mapToInt(Integer::intValue).toArray();
                return p;
            }

            static float[] unit(float[] a) {
                float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
                return l < 1e-20f ? new float[]{0, 1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l};
            }

            static float[] toArray(List<Float> l) { float[] a = new float[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }
        }
    }

    // =====================================================================================
    // Semplificazione delle mesh (collasso di spigoli con errore quadrico, Garland-Heckbert)
    // =====================================================================================

    static final class Simplify {
        /**
         * Nuovi indici con al più `target` triangoli. I vertici non si spostano: uno spigolo collassa su uno
         * dei suoi due estremi (così uv e normali restano quelle originali). I bordi (compresi i tagli delle
         * texture, dove i vertici sono doppi) si conservano, e si rifiutano i collassi che capovolgono facce.
         */
        static int[] run(float[] pos, int[] idx, int target) {
            int nt = idx.length / 3;
            if (nt <= target) return idx;
            int nv = pos.length / 3;
            double[][] q = new double[nv][10];
            int[] tri = idx.clone();
            boolean[] dead = new boolean[nt];
            List<List<Integer>> vt = new ArrayList<>(nv);
            for (int i = 0; i < nv; i++) vt.add(new ArrayList<>(6));
            for (int t = 0; t < nt; t++) for (int k = 0; k < 3; k++) vt.get(tri[t * 3 + k]).add(t);
            // Quadriche dei piani delle facce, pesate con l'area.
            for (int t = 0; t < nt; t++) {
                double[] n = normal(pos, tri, t);
                double area = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                if (area < 1e-20) continue;
                double a = n[0] / area, b = n[1] / area, c = n[2] / area;
                int v0 = tri[t * 3];
                double d = -(a * pos[v0 * 3] + b * pos[v0 * 3 + 1] + c * pos[v0 * 3 + 2]);
                for (int k = 0; k < 3; k++) addPlane(q[tri[t * 3 + k]], a, b, c, d, area);
            }
            // Bordi: un piano perpendicolare alla faccia lungo lo spigolo, con peso alto, li tiene fermi.
            Map<Long, Integer> edgeCount = new HashMap<>();
            for (int t = 0; t < nt; t++) for (int k = 0; k < 3; k++) {
                int a = tri[t * 3 + k], b = tri[t * 3 + (k + 1) % 3];
                edgeCount.merge(key(a, b), 1, Integer::sum);
            }
            for (int t = 0; t < nt; t++) for (int k = 0; k < 3; k++) {
                int a = tri[t * 3 + k], b = tri[t * 3 + (k + 1) % 3];
                if (edgeCount.get(key(a, b)) != 1) continue;
                double[] n = normal(pos, tri, t);
                double[] e = {pos[b * 3] - pos[a * 3], pos[b * 3 + 1] - pos[a * 3 + 1], pos[b * 3 + 2] - pos[a * 3 + 2]};
                double[] p = {e[1] * n[2] - e[2] * n[1], e[2] * n[0] - e[0] * n[2], e[0] * n[1] - e[1] * n[0]};
                double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                if (l < 1e-20) continue;
                double d = -(p[0] * pos[a * 3] + p[1] * pos[a * 3 + 1] + p[2] * pos[a * 3 + 2]) / l;
                double w = 1000 * (e[0] * e[0] + e[1] * e[1] + e[2] * e[2]);
                addPlane(q[a], p[0] / l, p[1] / l, p[2] / l, d, w);
                addPlane(q[b], p[0] / l, p[1] / l, p[2] / l, d, w);
            }
            PriorityQueue<double[]> heap = new PriorityQueue<>(Comparator.comparingDouble(x -> x[0]));
            for (long e : edgeCount.keySet()) pushEdge(heap, q, pos, (int) (e >>> 32), (int) e);
            int[] version = new int[nv];
            boolean[] gone = new boolean[nv];
            int alive = nt;
            while (alive > target && !heap.isEmpty()) {
                double[] h = heap.poll();
                int a = (int) h[1], b = (int) h[2], keep = (int) h[3];
                if (gone[a] || gone[b] || h[4] != version[a] || h[5] != version[b]) continue;
                int drop = keep == a ? b : a;
                // Il collasso non deve capovolgere nessuna faccia attorno al vertice che sparisce.
                if (flips(pos, tri, dead, vt.get(drop), drop, keep)) continue;
                for (int t : vt.get(drop)) {
                    if (dead[t]) continue;
                    boolean hasKeep = false;
                    for (int k = 0; k < 3; k++) if (tri[t * 3 + k] == keep) hasKeep = true;
                    if (hasKeep) { dead[t] = true; alive--; continue; }
                    for (int k = 0; k < 3; k++) if (tri[t * 3 + k] == drop) tri[t * 3 + k] = keep;
                    vt.get(keep).add(t);
                }
                gone[drop] = true;
                for (int k = 0; k < 10; k++) q[keep][k] += q[drop][k];
                version[keep]++;
                Set<Integer> nb = new HashSet<>();
                for (int t : vt.get(keep)) if (!dead[t]) for (int k = 0; k < 3; k++) nb.add(tri[t * 3 + k]);
                nb.remove(keep);
                for (int o : nb) pushEdgeV(heap, q, pos, keep, o, version);
            }
            int[] out = new int[alive * 3];
            int j = 0;
            for (int t = 0; t < nt; t++) if (!dead[t]) { out[j++] = tri[t * 3]; out[j++] = tri[t * 3 + 1]; out[j++] = tri[t * 3 + 2]; }
            return j == out.length ? out : Arrays.copyOf(out, j);
        }

        static long key(int a, int b) { int lo = Math.min(a, b), hi = Math.max(a, b); return ((long) lo << 32) | (hi & 0xFFFFFFFFL); }

        static double[] normal(float[] p, int[] tri, int t) {
            int a = tri[t * 3], b = tri[t * 3 + 1], c = tri[t * 3 + 2];
            double ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
            double vx = p[c * 3] - p[a * 3], vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
            return new double[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        }

        static void addPlane(double[] q, double a, double b, double c, double d, double w) {
            q[0] += w * a * a; q[1] += w * a * b; q[2] += w * a * c; q[3] += w * a * d;
            q[4] += w * b * b; q[5] += w * b * c; q[6] += w * b * d;
            q[7] += w * c * c; q[8] += w * c * d; q[9] += w * d * d;
        }

        static double err(double[] q, float[] p, int v) {
            double x = p[v * 3], y = p[v * 3 + 1], z = p[v * 3 + 2];
            return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x
                + q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y + q[7] * z * z + 2 * q[8] * z + q[9];
        }

        static void pushEdge(PriorityQueue<double[]> heap, double[][] q, float[] pos, int a, int b) {
            double[] s = new double[10];
            for (int k = 0; k < 10; k++) s[k] = q[a][k] + q[b][k];
            double ea = err(s, pos, a), eb = err(s, pos, b);
            heap.add(new double[]{Math.min(ea, eb), a, b, ea <= eb ? a : b, 0, 0});
        }

        static void pushEdgeV(PriorityQueue<double[]> heap, double[][] q, float[] pos, int a, int b, int[] version) {
            double[] s = new double[10];
            for (int k = 0; k < 10; k++) s[k] = q[a][k] + q[b][k];
            double ea = err(s, pos, a), eb = err(s, pos, b);
            heap.add(new double[]{Math.min(ea, eb), a, b, ea <= eb ? a : b, version[a], version[b]});
        }

        /** Spostare `drop` su `keep` capovolgerebbe (o schiaccerebbe) una delle sue facce? */
        static boolean flips(float[] pos, int[] tri, boolean[] dead, List<Integer> faces, int drop, int keep) {
            for (int t : faces) {
                if (dead[t]) continue;
                boolean hasKeep = false;
                for (int k = 0; k < 3; k++) if (tri[t * 3 + k] == keep) hasKeep = true;
                if (hasKeep) continue;
                double[] before = normal(pos, tri, t);
                int[] moved = {tri[t * 3], tri[t * 3 + 1], tri[t * 3 + 2]};
                for (int k = 0; k < 3; k++) if (moved[k] == drop) moved[k] = keep;
                double[] after = normal(pos, moved, 0);
                double dot = before[0] * after[0] + before[1] * after[1] + before[2] * after[2];
                double la = Math.sqrt(after[0] * after[0] + after[1] * after[1] + after[2] * after[2]);
                double lb = Math.sqrt(before[0] * before[0] + before[1] * before[1] + before[2] * before[2]);
                if (la < 1e-20 || dot < 0.2 * la * lb) return true;
            }
            return false;
        }
    }

    // =====================================================================================
    // Matrici (colonne, come glTF)
    // =====================================================================================

    static final class Mat {
        static double[] identity() { return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }

        static double[] mul(double[] a, double[] b) {
            double[] r = new double[16];
            for (int c = 0; c < 4; c++) for (int rw = 0; rw < 4; rw++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rw] * b[c * 4 + k];
                r[c * 4 + rw] = s;
            }
            return r;
        }

        static double[] fromNode(Map<String, Object> n) {
            if (n.containsKey("matrix")) {
                List<?> l = (List<?>) n.get("matrix");
                double[] m = new double[16];
                for (int i = 0; i < 16; i++) m[i] = ((Number) l.get(i)).doubleValue();
                return m;
            }
            double[] t = vec(n.get("translation"), new double[]{0, 0, 0});
            double[] q = vec(n.get("rotation"), new double[]{0, 0, 0, 1});
            double[] s = vec(n.get("scale"), new double[]{1, 1, 1});
            double x = q[0], y = q[1], z = q[2], w = q[3];
            double[] r = {
                1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
                2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
                2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0,
                0, 0, 0, 1,
            };
            for (int c = 0; c < 3; c++) for (int k = 0; k < 3; k++) r[c * 4 + k] *= s[c];
            r[12] = t[0]; r[13] = t[1]; r[14] = t[2];
            return r;
        }

        static double[] vec(Object o, double[] def) {
            if (o == null) return def;
            List<?> l = (List<?>) o;
            double[] v = new double[l.size()];
            for (int i = 0; i < v.length; i++) v[i] = ((Number) l.get(i)).doubleValue();
            return v;
        }

        static void transform(double[] m, float[] pos, float[] nor) {
            for (int i = 0; i < pos.length; i += 3) {
                double x = pos[i], y = pos[i + 1], z = pos[i + 2];
                pos[i] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12]);
                pos[i + 1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13]);
                pos[i + 2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14]);
            }
            if (nor != null) for (int i = 0; i < nor.length; i += 3) {
                double x = nor[i], y = nor[i + 1], z = nor[i + 2];
                double nx = m[0] * x + m[4] * y + m[8] * z, ny = m[1] * x + m[5] * y + m[9] * z, nz = m[2] * x + m[6] * y + m[10] * z;
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l > 0) { nor[i] = (float) (nx / l); nor[i + 1] = (float) (ny / l); nor[i + 2] = (float) (nz / l); }
            }
        }
    }

    // =====================================================================================
    // Immagini
    // =====================================================================================

    static final class Images {
        static boolean hasAlpha(BufferedImage img) { return img.getColorModel().hasAlpha(); }

        static BufferedImage resize(BufferedImage src, int max) {
            int w = src.getWidth(), h = src.getHeight();
            double k = Math.min(1.0, (double) max / Math.max(w, h));
            int nw = Math.max(1, (int) Math.round(w * k)), nh = Math.max(1, (int) Math.round(h * k));
            boolean alpha = hasAlpha(src);
            BufferedImage out = new BufferedImage(nw, nh, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            return out;
        }

        /** JPEG di qualità 0,85 (o PNG se c'è trasparenza). */
        static byte[] encode(BufferedImage img) throws IOException {
            if (hasAlpha(img)) return png(img);
            ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.85f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream os = new MemoryCacheImageOutputStream(bos)) {
                w.setOutput(os);
                w.write(null, new IIOImage(img, null, null), p);
            }
            w.dispose();
            return bos.toByteArray();
        }

        static byte[] png(BufferedImage img) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        }
    }

    // =====================================================================================
    // Miniature: piccolo rasterizzatore con z-buffer e luce diffusa
    // =====================================================================================

    static final class Render {
        /** Tre quarti dal davanti a destra, dall'alto (proiezione ortogonale). */
        static void thumbnail(Model m, Path out) throws IOException {
            double yaw = Math.toRadians(-32), pitch = Math.toRadians(24);
            double[] right = {Math.cos(yaw), 0, -Math.sin(yaw)};
            double[] fwd = {Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
            fwd = new double[]{-fwd[0], fwd[1], -fwd[2]};
            double[] up = cross(right, fwd);
            draw(m, out, 256, 200, right, up, fwd, new double[]{0.35, 0.8, 0.5}, 0.9);
        }

        /** Dall'alto: x a destra, il davanti (+z) in basso, come in pianta. */
        static void top(Model m, Path out) throws IOException {
            double[] s = m.size();
            int w, h;
            if (s[0] >= s[2]) { w = 256; h = (int) Math.max(8, Math.round(256 * s[2] / Math.max(1e-6, s[0]))); }
            else { h = 256; w = (int) Math.max(8, Math.round(256 * s[0] / Math.max(1e-6, s[2]))); }
            draw(m, out, w, h, new double[]{1, 0, 0}, new double[]{0, 0, -1}, new double[]{0, -1, 0}, new double[]{0.3, 1, 0.45}, 1.0);
        }

        static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
        static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

        /**
         * Proietta sui due assi `right`/`up` (profondità lungo `fwd`, che va dall'osservatore verso la scena);
         * l'immagine si adatta all'ingombro proiettato con un margine (`fill` = quanta parte occupa).
         */
        static void draw(Model m, Path out, int W, int H, double[] right, double[] up, double[] fwd, double[] light, double fill) throws IOException {
            double ll = Math.sqrt(dot(light, light));
            double[] L = {light[0] / ll, light[1] / ll, light[2] / ll};
            double minX = 1e30, maxX = -1e30, minY = 1e30, maxY = -1e30;
            for (Prim p : m.prims) for (int i = 0; i < p.pos.length; i += 3) {
                double[] v = {p.pos[i], p.pos[i + 1], p.pos[i + 2]};
                double x = dot(v, right), y = dot(v, up);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            double k = fill * Math.min(W / Math.max(1e-6, maxX - minX), H / Math.max(1e-6, maxY - minY));
            double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            float[] zbuf = new float[W * H];
            Arrays.fill(zbuf, Float.MAX_VALUE);
            // Prima gli opachi, poi i trasparenti (senza scrivere la profondità).
            for (int pass = 0; pass < 2; pass++) for (Prim p : m.prims) {
                boolean transparent = p.mat.blend || p.mat.color[3] < 0.99f;
                if ((pass == 1) != transparent) continue;
                float[] col = p.mat.shade();
                for (int t = 0; t + 2 < p.idx.length; t += 3) {
                    double[][] sv = new double[3][];
                    double[][] wv = new double[3][];
                    for (int c = 0; c < 3; c++) {
                        int vi = p.idx[t + c] * 3;
                        double[] v = {p.pos[vi], p.pos[vi + 1], p.pos[vi + 2]};
                        wv[c] = v;
                        sv[c] = new double[]{W / 2.0 + (dot(v, right) - cx) * k, H / 2.0 - (dot(v, up) - cy) * k, dot(v, fwd)};
                    }
                    double[] e1 = {wv[1][0] - wv[0][0], wv[1][1] - wv[0][1], wv[1][2] - wv[0][2]};
                    double[] e2 = {wv[2][0] - wv[0][0], wv[2][1] - wv[0][1], wv[2][2] - wv[0][2]};
                    double[] n = cross(e1, e2);
                    double nl = Math.sqrt(dot(n, n));
                    if (nl < 1e-12) continue;
                    n = new double[]{n[0] / nl, n[1] / nl, n[2] / nl};
                    // Faccia vista dal dietro: si illumina come se fosse girata verso l'osservatore.
                    if (dot(n, fwd) > 0) n = new double[]{-n[0], -n[1], -n[2]};
                    double lum = 0.42 + 0.62 * Math.max(0, dot(n, L));
                    int r = clamp(Math.pow(col[0], 1 / 2.2) * lum), g = clamp(Math.pow(col[1], 1 / 2.2) * lum), b = clamp(Math.pow(col[2], 1 / 2.2) * lum);
                    int a = transparent ? Math.max(60, Math.round(col[3] * 255)) : 255;
                    raster(sv, W, H, zbuf, img, (a << 24) | (r << 16) | (g << 8) | b, !transparent);
                }
            }
            ImageIO.write(img, "png", out.toFile());
        }

        static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v * 255))); }

        static void raster(double[][] v, int W, int H, float[] zbuf, BufferedImage img, int argb, boolean writeZ) {
            int x0 = (int) Math.max(0, Math.floor(Math.min(v[0][0], Math.min(v[1][0], v[2][0]))));
            int x1 = (int) Math.min(W - 1, Math.ceil(Math.max(v[0][0], Math.max(v[1][0], v[2][0]))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(v[0][1], Math.min(v[1][1], v[2][1]))));
            int y1 = (int) Math.min(H - 1, Math.ceil(Math.max(v[0][1], Math.max(v[1][1], v[2][1]))));
            double area = (v[1][0] - v[0][0]) * (v[2][1] - v[0][1]) - (v[2][0] - v[0][0]) * (v[1][1] - v[0][1]);
            if (Math.abs(area) < 1e-9) return;
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((v[1][0] - px) * (v[2][1] - py) - (v[2][0] - px) * (v[1][1] - py)) / area;
                double w1 = ((v[2][0] - px) * (v[0][1] - py) - (v[0][0] - px) * (v[2][1] - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = (float) (w0 * v[0][2] + w1 * v[1][2] + w2 * v[2][2]);
                int i = y * W + x;
                if (z >= zbuf[i]) continue;
                if (writeZ) { zbuf[i] = z; img.setRGB(x, y, argb); }
                else {
                    // Trasparente: si mescola con quello che c'è sotto.
                    int under = img.getRGB(x, y);
                    double a = (argb >>> 24) / 255.0;
                    int ua = under >>> 24;
                    int r = (int) (((argb >> 16) & 255) * a + ((under >> 16) & 255) * (1 - a) * (ua / 255.0));
                    int g = (int) (((argb >> 8) & 255) * a + ((under >> 8) & 255) * (1 - a) * (ua / 255.0));
                    int b = (int) ((argb & 255) * a + (under & 255) * (1 - a) * (ua / 255.0));
                    int na = Math.max(ua, argb >>> 24);
                    img.setRGB(x, y, (na << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b));
                }
            }
        }
    }

    // =====================================================================================
    // JSON minimo
    // =====================================================================================

    static final class Json {
        static Object parse(String s) { int[] p = {0}; Object o = value(s, p); return o; }

        static void ws(String s, int[] p) { while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++; }

        static Object value(String s, int[] p) {
            ws(s, p);
            char c = s.charAt(p[0]);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                p[0]++; ws(s, p);
                if (s.charAt(p[0]) == '}') { p[0]++; return m; }
                while (true) {
                    ws(s, p); String k = (String) value(s, p); ws(s, p); p[0]++; // ':'
                    m.put(k, value(s, p)); ws(s, p);
                    if (s.charAt(p[0]++) == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                p[0]++; ws(s, p);
                if (s.charAt(p[0]) == ']') { p[0]++; return l; }
                while (true) {
                    l.add(value(s, p)); ws(s, p);
                    if (s.charAt(p[0]++) == ']') return l;
                }
            }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                p[0]++;
                while (true) {
                    char ch = s.charAt(p[0]++);
                    if (ch == '"') return sb.toString();
                    if (ch == '\\') {
                        char e = s.charAt(p[0]++);
                        switch (e) {
                            case 'n' -> sb.append('\n'); case 't' -> sb.append('\t'); case 'r' -> sb.append('\r');
                            case 'b' -> sb.append('\b'); case 'f' -> sb.append('\f');
                            case 'u' -> { sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16)); p[0] += 4; }
                            default -> sb.append(e);
                        }
                    } else sb.append(ch);
                }
            }
            if (s.startsWith("true", p[0])) { p[0] += 4; return Boolean.TRUE; }
            if (s.startsWith("false", p[0])) { p[0] += 5; return Boolean.FALSE; }
            if (s.startsWith("null", p[0])) { p[0] += 4; return null; }
            int st = p[0];
            while (p[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(p[0])) >= 0) p[0]++;
            String num = s.substring(st, p[0]);
            if (num.matches("-?\\d+")) return Long.parseLong(num);
            return Double.parseDouble(num);
        }

        /** `indent` < 0: tutto su una riga. */
        static String write(Object o, int indent) { StringBuilder sb = new StringBuilder(); write(o, sb, indent, 0); return sb.toString(); }

        static void write(Object o, StringBuilder sb, int indent, int level) {
            String nl = indent >= 0 ? "\n" + "  ".repeat(level + 1) : "";
            String end = indent >= 0 ? "\n" + "  ".repeat(level) : "";
            if (o == null) sb.append("null");
            else if (o instanceof String s) {
                sb.append('"');
                for (char c : s.toCharArray()) {
                    if (c == '"' || c == '\\') sb.append('\\').append(c);
                    else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
                sb.append('"');
            } else if (o instanceof Map<?, ?> m) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    // Nel catalogo una voce per riga; dentro le voci tutto compatto.
                    if (level == 0 && indent >= 0 && false) sb.append(nl);
                    write(e.getKey().toString(), sb, -1, level + 1);
                    sb.append(':');
                    write(e.getValue(), sb, -1, level + 1);
                }
                sb.append('}');
            } else if (o instanceof List<?> l) {
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(',');
                    if (level == 0 && indent >= 0) sb.append(nl);
                    write(l.get(i), sb, level == 0 ? indent : -1, level + 1);
                }
                if (level == 0 && indent >= 0) sb.append(end);
                sb.append(']');
            } else if (o instanceof Double d) {
                if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) (double) d); else sb.append(d);
            } else sb.append(o);
        }
    }
}
