import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Trova i pezzi (doghe, piastrelle, lastre) dentro le foto dei materiali, per la posa a pezzi di Sagoma: ogni pezzo posato
 * nel 3D mostra un pezzo vero della foto, senza le fughe della foto (le fughe le disegna Sagoma, larghe e del colore scelti).
 *
 * Per ogni foto cerca le fughe dritte che attraversano tutta l'immagine (righe di doghe, griglia delle piastrelle) e, dentro
 * ogni riga, le giunte di testa. Le foto si ripetono senza bordi, quindi un pezzo può continuare dall'altra parte.
 * Le foto che sono già una posa (spina di pesce, esagonette, opus…) si riconoscono dal nome e restano come sono.
 *
 * Scrive [MaterialPiecesData.kt] (dati per l'app) e, se si dà una cartella, i fogli di controllo con i pezzi trovati.
 *
 * Uso: java FindMaterialPieces.java <cartella materiali> <materials.tsv> <uscita .kt> [cartella fogli di controllo]
 */
public class FindMaterialPieces {
    /** Lato delle mappe (px), come in PackMaterials. */
    static final int N = 1024;
    static final String DEBUG = System.getenv().getOrDefault("PEZZI_DEBUG", "");

    /** Nomi delle foto che sono già una posa: i pezzi non sono rettangoli in fila. */
    static final String[] PATTERNED = {
        "spina", "intreccio", "cesto", "diagonale", "esagon", "opus", "ventaglio", "sampietrini", "ciottolato", "selciato",
        "gradini", "intars", "versailles", "losanghe", "onde", "rombi", "zig-zag", "formati misti", "tozzetti", "ottagoni",
        "disegni", "fascia", "autobloccanti", "ghiaia", "prato", "erba", "bottoni", "riquadri", "a mosaico", "spacco", "intrecci",
    };

    /**
     * Correzioni a mano, guardando i fogli di controllo: foto che sono un disegno o una posa irregolare (P) anche se il nome
     * non lo dice, o righe che non sono pezzi (U).
     */
    static final Map<String, String> OVERRIDES = Map.ofEntries(
        Map.entry("woodfloor035", "P"), Map.entry("woodfloor036", "P"), Map.entry("woodfloor069", "P"),
        Map.entry("tiles003", "P"), Map.entry("tiles019", "P"), Map.entry("tiles021", "P"), Map.entry("tiles024", "P"),
        Map.entry("tiles045", "P"), Map.entry("tiles046", "P"), Map.entry("tiles051", "P"), Map.entry("tiles056", "P"),
        Map.entry("tiles099", "P"), Map.entry("tiles101", "P"), Map.entry("tiles102", "P"), Map.entry("tiles104", "P"),
        Map.entry("ph_floor_tiles_02", "P"), Map.entry("ph_floor_tiles_04", "P"), Map.entry("ph_blue_floor_tiles_01", "P"),
        Map.entry("carpet002", "P"), Map.entry("carpet003", "P"), Map.entry("carpet004", "P"), Map.entry("carpet006", "P"),
        Map.entry("pavingstones122", "P"), Map.entry("pavingstones138", "P"),
        Map.entry("fabric080", "P"), Map.entry("fabric083", "P"), Map.entry("ph_fabric_pattern_05", "P"),
        Map.entry("ph_fabric_pattern_07", "P"), Map.entry("ph_faux_fur_geometric", "P"),
        Map.entry("carpet014", "U")
    );

    /** Categorie in cui una foto senza pezzi trovati ha comunque i pezzi (mattoni, pietre): resta com'è. */
    static final Set<String> ALWAYS_PIECES = Set.of("Mattoni e pietra");

    /** Una fuga: centro, larghezza (px) e quanto spicca sul fondo. */
    record Seam(double pos, double width, double strength) {}

    record Result(String kind, List<int[]> rects, double joint, int grout, boolean grid, int nx, int ny) {}

    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[2]);
        Path sheets = args.length > 3 ? Paths.get(args[3]) : null;
        if (sheets != null) Files.createDirectories(sheets);
        StringBuilder kt = new StringBuilder();
        kt.append("package com.sagoma.planimetria.model\n\n");
        kt.append("/**\n * Pezzi trovati nelle foto dei materiali (file generato da tools/furniture/FindMaterialPieces.java: non modificarlo a mano).\n");
        kt.append(" * Per ogni materiale: «U» foto senza pezzi (marmo, cemento…), «P» foto che è già una posa (spina di pesce, esagonette…),\n");
        kt.append(" * altrimenti «S fuga colore griglia colonne righe» seguito dai pezzi «x y larghezza altezza» in px su 1024, separati da «|».\n */\n");
        kt.append("internal object MaterialPiecesData {\n    val table: Map<String, String> = mapOf(\n");
        List<BufferedImage> thumbs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int sheet = 0;
        Map<String, Integer> kinds = new TreeMap<>();
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            String id = c[0].trim().toLowerCase(Locale.ROOT);
            String label = c[1].trim();
            File color = in.resolve(id + "_color.jpg").toFile();
            if (!color.isFile()) { System.err.println("manca la foto: " + id); continue; }
            BufferedImage img = ImageIO.read(color);
            BufferedImage nrm = read(in.resolve(id + "_normal.jpg").toFile());
            BufferedImage orm = read(in.resolve(id + "_orm.jpg").toFile());
            Result r = OVERRIDES.containsKey(id) ? new Result(OVERRIDES.get(id), List.of(), 0, 0, false, 0, 0) : analyse(label, img, nrm, orm);
            if (r.kind.equals("U") && ALWAYS_PIECES.contains(c[2].trim()) && !OVERRIDES.containsKey(id)) r = new Result("P", List.of(), 0, 0, false, 0, 0);
            kinds.merge(r.kind.equals("S") ? (r.grid ? "griglia" : "file") : r.kind, 1, Integer::sum);
            kt.append("        \"").append(id).append("\" to \"").append(encode(r)).append("\",\n");
            if (sheets != null) {
                thumbs.add(preview(img, r));
                labels.add(id + " " + label + " " + describe(r, Double.parseDouble(c[3].trim())));
                if (thumbs.size() == 20) { writeSheet(thumbs, labels, sheets.resolve(String.format("pezzi-%02d.png", ++sheet))); thumbs.clear(); labels.clear(); }
            }
        }
        if (sheets != null && !thumbs.isEmpty()) writeSheet(thumbs, labels, sheets.resolve(String.format("pezzi-%02d.png", ++sheet)));
        kt.append("    )\n}\n");
        Files.writeString(out, kt.toString(), StandardCharsets.UTF_8);
        System.out.println(kinds);
    }

    static BufferedImage read(File f) throws Exception { return f.isFile() ? ImageIO.read(f) : null; }

    static String encode(Result r) {
        if (!r.kind.equals("S")) return r.kind;
        StringBuilder s = new StringBuilder("S ").append(String.format(Locale.ROOT, "%.1f", r.joint)).append(' ')
            .append(String.format("%06x", r.grout & 0xFFFFFF)).append(' ').append(r.grid ? 1 : 0).append(' ').append(r.nx).append(' ').append(r.ny);
        for (int[] q : r.rects) s.append('|').append(q[0]).append(' ').append(q[1]).append(' ').append(q[2]).append(' ').append(q[3]);
        return s.toString();
    }

    static String describe(Result r, double size) {
        if (!r.kind.equals("S")) return r.kind.equals("U") ? "(senza pezzi)" : "(posa nella foto)";
        double[] w = new double[r.rects.size()], h = new double[r.rects.size()];
        for (int i = 0; i < w.length; i++) { w[i] = r.rects.get(i)[2] * size / N; h[i] = r.rects.get(i)[3] * size / N; }
        return String.format(Locale.ROOT, "%d pezzi ~%.0fx%.0f cm, fuga %.1f cm", w.length, median(w), median(h), r.joint * size / N);
    }

    // ---------- Analisi ----------

    static Result analyse(String label, BufferedImage img, BufferedImage nrm, BufferedImage orm) {
        String low = label.toLowerCase(Locale.ROOT);
        for (String k : PATTERNED) if (low.contains(k)) return new Result("P", List.of(), 0, 0, false, 0, 0);
        float[] lum = channel(img, -1), rough = orm == null ? null : channel(orm, 1);
        float[] nx = nrm == null ? null : channel(nrm, 0), ny = nrm == null ? null : channel(nrm, 1);
        // Forza di una linea (scura o chiara) o di un gradino, attraverso le righe (fughe orizzontali) e le colonne.
        float[] sy = strength(lum, rough, ny, true), sx = strength(lum, rough, nx, false);
        List<Seam> rows = family(profile(sy, true, 0, N));
        List<Seam> cols = family(profile(sx, false, 0, N));
        if (DEBUG.equals(label)) System.err.println("righe " + rows + "\ncolonne " + cols + "\npicchi " + peaks(profile(sy, true, 0, N), 0.03));
        List<int[]> rects = new ArrayList<>();
        List<double[]> seamPx = new ArrayList<>(); // per il colore della fuga: x0, y0, x1, y1 di ogni fuga
        boolean grid = false;
        int nxCount = 0, nyCount = 0;
        if (rows != null && cols != null) {
            grid = true;
            nxCount = cols.size();
            nyCount = rows.size();
            for (int j = 0; j < rows.size(); j++) for (int i = 0; i < cols.size(); i++) rects.add(cell(cols, i, rows, j));
            for (Seam s : rows) seamPx.add(new double[]{0, s.pos, N, s.pos});
            for (Seam s : cols) seamPx.add(new double[]{s.pos, 0, s.pos, N});
        } else if (rows != null || cols != null) {
            boolean horizontal = rows != null; // file di doghe orizzontali: le giunte di testa sono verticali
            List<Seam> bands = horizontal ? rows : cols;
            for (int j = 0; j < bands.size(); j++) {
                Seam a = bands.get(j), b = bands.get((j + 1) % bands.size());
                double from = a.pos + a.width / 2, to = b.pos - b.width / 2 + (j + 1 == bands.size() ? N : 0);
                int pad = 3;
                List<Seam> joints = joints(horizontal ? sx : sy, !horizontal, (int) Math.ceil(from) + pad, (int) Math.floor(to) - pad);
                if (joints == null || joints.isEmpty()) {
                    rects.add(horizontal ? rect(0, from, N, to - from) : rect(from, 0, to - from, N));
                    continue;
                }
                for (int k = 0; k < joints.size(); k++) {
                    Seam p = joints.get(k), q = joints.get((k + 1) % joints.size());
                    double s0 = p.pos + p.width / 2, s1 = q.pos - q.width / 2 + (k + 1 == joints.size() ? N : 0);
                    if (joints.size() == 1) s1 = p.pos - p.width / 2 + N;
                    rects.add(horizontal ? rect(s0, from, s1 - s0, to - from) : rect(from, s0, to - from, s1 - s0));
                    seamPx.add(horizontal ? new double[]{p.pos, from, p.pos, to} : new double[]{from, p.pos, to, p.pos});
                }
            }
            for (Seam s : bands) seamPx.add(horizontal ? new double[]{0, s.pos, N, s.pos} : new double[]{s.pos, 0, s.pos, N});
        } else {
            return new Result("U", List.of(), 0, 0, false, 0, 0);
        }
        rects.removeIf(q -> q[2] < 6 || q[3] < 6);
        if (rects.isEmpty()) return new Result("U", List.of(), 0, 0, false, 0, 0);
        List<Double> widths = new ArrayList<>();
        if (rows != null) for (Seam s : rows) widths.add(s.width);
        if (cols != null) for (Seam s : cols) widths.add(s.width);
        double joint = median(widths.stream().mapToDouble(Double::doubleValue).toArray());
        return new Result("S", rects, joint, grout(img, seamPx), grid, nxCount, nyCount);
    }

    static int[] rect(double x, double y, double w, double h) {
        return new int[]{(int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h)};
    }

    static int[] cell(List<Seam> cols, int i, List<Seam> rows, int j) {
        Seam a = cols.get(i), b = cols.get((i + 1) % cols.size());
        Seam c = rows.get(j), d = rows.get((j + 1) % rows.size());
        double x0 = a.pos + a.width / 2, x1 = b.pos - b.width / 2 + (i + 1 == cols.size() ? N : 0);
        double y0 = c.pos + c.width / 2, y1 = d.pos - d.width / 2 + (j + 1 == rows.size() ? N : 0);
        return rect(x0, y0, x1 - x0, y1 - y0);
    }

    /** Un canale (0 rosso, 1 verde, 2 blu; -1 luminosità), da 0 a 1. */
    static float[] channel(BufferedImage img, int ch) {
        BufferedImage s = img.getWidth() == N && img.getHeight() == N ? img : resize(img);
        float[] out = new float[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int p = s.getRGB(x, y);
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            out[y * N + x] = (ch < 0 ? 0.299f * r + 0.587f * g + 0.114f * b : ch == 0 ? r : ch == 1 ? g : b) / 255f;
        }
        return out;
    }

    static BufferedImage resize(BufferedImage img) {
        BufferedImage s = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = s.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, N, N, null);
        g.dispose();
        return s;
    }

    static int wrap(int i) { return ((i % N) + N) % N; }

    /** Linea sottile (scura o chiara) o gradino attraverso le righe (`acrossRows`: fughe orizzontali) o le colonne. */
    static float[] strength(float[] lum, float[] rough, float[] normal, boolean acrossRows) {
        float[] out = new float[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float s = line(lum, x, y, acrossRows);
            if (rough != null) s += 0.5f * line(rough, x, y, acrossRows);
            if (normal != null) s += 0.5f * Math.abs(normal[y * N + x] - 0.5f);
            out[y * N + x] = s;
        }
        return out;
    }

    static float line(float[] a, int x, int y, boolean acrossRows) {
        float c = a[y * N + x];
        float m3, p3, m2, p2;
        if (acrossRows) {
            m3 = a[wrap(y - 3) * N + x]; p3 = a[wrap(y + 3) * N + x]; m2 = a[wrap(y - 2) * N + x]; p2 = a[wrap(y + 2) * N + x];
        } else {
            m3 = a[y * N + wrap(x - 3)]; p3 = a[y * N + wrap(x + 3)]; m2 = a[y * N + wrap(x - 2)]; p2 = a[y * N + wrap(x + 2)];
        }
        return Math.max(Math.abs((m3 + p3) / 2 - c), 0.5f * Math.abs(m2 - p2));
    }

    /**
     * Profilo lungo y (`acrossRows`) o x della forza delle linee, tra `from` e `to` nell'altra direzione: per ogni riga il valore
     * superato dal 70% dei punti, così conta solo una linea che attraversa (quasi) tutto il tratto.
     */
    static double[] profile(float[] s, boolean acrossRows, int from, int to) {
        double[] p = new double[N];
        int len = to - from;
        if (len < 4) return null;
        float[] buf = new float[len];
        for (int i = 0; i < N; i++) {
            for (int k = 0; k < len; k++) {
                int o = wrap(from + k);
                buf[k] = acrossRows ? s[i * N + o] : s[o * N + i];
            }
            Arrays.sort(buf);
            p[i] = buf[(int) (len * 0.3)];
        }
        // Un po' smussato (3 px).
        double[] q = new double[N];
        for (int i = 0; i < N; i++) q[i] = (p[wrap(i - 1)] + p[i] + p[wrap(i + 1)]) / 3;
        return q;
    }

    /** Picchi del profilo: posizioni e larghezze delle linee; le due righe di uno smusso vicine diventano una fuga sola. */
    static List<Seam> peaks(double[] p, double minRise) {
        double[] sorted = p.clone();
        Arrays.sort(sorted);
        double base = sorted[N / 2];
        double[] dev = new double[N];
        for (int i = 0; i < N; i++) dev[i] = Math.abs(p[i] - base);
        Arrays.sort(dev);
        double mad = dev[N / 2];
        double thr = base + Math.max(7 * mad, minRise);
        List<Integer> top = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            if (p[i] < thr) continue;
            boolean max = true;
            for (int k = -5; k <= 5 && max; k++) if (k != 0 && p[wrap(i + k)] > p[i]) max = false;
            if (max) top.add(i);
        }
        if (top.isEmpty()) return List.of();
        // Gruppi di picchi vicini (meno di 12 px): una fuga sola.
        List<List<Integer>> groups = new ArrayList<>();
        for (int i : top) {
            if (!groups.isEmpty()) {
                List<Integer> g = groups.get(groups.size() - 1);
                if (i - g.get(g.size() - 1) < 12) { g.add(i); continue; }
            }
            groups.add(new ArrayList<>(List.of(i)));
        }
        if (groups.size() > 1) {
            List<Integer> first = groups.get(0), last = groups.get(groups.size() - 1);
            if (first.get(0) + N - last.get(last.size() - 1) < 12) {
                for (int i : first) last.add(i + N);
                groups.remove(0);
            }
        }
        List<Seam> out = new ArrayList<>();
        for (List<Integer> g : groups) {
            int a = g.get(0), b = g.get(g.size() - 1);
            double peak = 0;
            for (int i : g) peak = Math.max(peak, p[wrap(i)]);
            double half = base + (peak - base) / 2;
            int l = a, r = b;
            while (a - l < 10 && p[wrap(l - 1)] > half) l--;
            while (r - b < 10 && p[wrap(r + 1)] > half) r++;
            double pos = (l + r) / 2.0;
            out.add(new Seam(((pos % N) + N) % N, Math.max(2, Math.min(16, r - l + 1)), peak - base));
        }
        out.sort(Comparator.comparingDouble(Seam::pos));
        return out;
    }

    /**
     * Fughe che attraversano tutta la foto, se sono una famiglia credibile: almeno due, distanze non troppo diverse. Le linee
     * deboli rispetto alle altre e quelle troppo vicine a una più forte (una venatura, un graffio) non contano.
     */
    static List<Seam> family(double[] p) {
        List<Seam> s = new ArrayList<>(peaks(p, 0.03));
        if (s.size() < 2) return null;
        double typical = median(s.stream().mapToDouble(Seam::strength).toArray());
        s.removeIf(a -> a.strength < 0.35 * typical);
        while (s.size() >= 2) {
            int worst = -1;
            double min = Double.MAX_VALUE;
            double[] gaps = gaps(s);
            double med = median(gaps);
            for (int i = 0; i < gaps.length; i++) if (gaps[i] < min) { min = gaps[i]; worst = i; }
            if (min >= Math.max(16, 0.4 * med)) break;
            Seam a = s.get(worst), b = s.get((worst + 1) % s.size());
            s.remove(a.strength < b.strength ? a : b);
        }
        if (s.size() < 2) return null;
        double[] gaps = gaps(s);
        double min = Arrays.stream(gaps).min().getAsDouble(), max = Arrays.stream(gaps).max().getAsDouble();
        if (min < 16 || max > 3.5 * min) return null;
        return s;
    }

    /** Distanze tra fughe consecutive (l'ultima torna alla prima, dall'altra parte della foto). */
    static double[] gaps(List<Seam> s) {
        double[] g = new double[s.size()];
        for (int i = 0; i < s.size(); i++) g[i] = s.get((i + 1) % s.size()).pos - s.get(i).pos + (i + 1 == s.size() ? N : 0);
        return g;
    }

    /** Giunte di testa dentro una fila (tra `from` e `to` nell'altra direzione). */
    static List<Seam> joints(float[] s, boolean acrossRows, int from, int to) {
        double[] p = profile(s, acrossRows, from, to);
        if (p == null) return null;
        List<Seam> j = new ArrayList<>(peaks(p, 0.04));
        if (j.isEmpty()) return j;
        // Le più forti per prime; giunte deboli o troppo vicine a una più forte non sono credibili (venature).
        j.sort(Comparator.comparingDouble(Seam::strength).reversed());
        double strongest = j.get(0).strength;
        List<Seam> out = new ArrayList<>();
        for (Seam a : j) {
            if (a.strength < 0.3 * strongest) continue;
            boolean near = false;
            for (Seam b : out) {
                double d = Math.abs(a.pos - b.pos);
                if (Math.min(d, N - d) < 24) near = true;
            }
            if (!near) out.add(a);
        }
        out.sort(Comparator.comparingDouble(Seam::pos));
        return out;
    }

    /** Colore della fuga: la mediana dei punti sulle fughe trovate. */
    static int grout(BufferedImage img, List<double[]> seams) {
        List<Integer> rs = new ArrayList<>(), gs = new ArrayList<>(), bs = new ArrayList<>();
        for (double[] q : seams) {
            double len = Math.hypot(q[2] - q[0], q[3] - q[1]);
            int n = (int) Math.max(1, len / 2);
            for (int k = 0; k <= n; k++) {
                int x = wrap((int) Math.round(q[0] + (q[2] - q[0]) * k / n)), y = wrap((int) Math.round(q[1] + (q[3] - q[1]) * k / n));
                int p = img.getRGB(x * img.getWidth() / N, y * img.getHeight() / N);
                rs.add((p >> 16) & 255); gs.add((p >> 8) & 255); bs.add(p & 255);
            }
        }
        Collections.sort(rs); Collections.sort(gs); Collections.sort(bs);
        int m = rs.size() / 2;
        return (rs.get(m) << 16) | (gs.get(m) << 8) | bs.get(m);
    }

    static double median(double[] v) {
        if (v.length == 0) return 0;
        double[] s = v.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    // ---------- Fogli di controllo ----------

    static BufferedImage preview(BufferedImage img, Result r) {
        int S = 300;
        BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, S, S, null);
        g.setStroke(new BasicStroke(1.5f));
        Random rnd = new Random(1);
        for (int[] q : r.rects) {
            g.setColor(Color.getHSBColor(rnd.nextFloat(), 1f, 1f));
            double k = S / (double) N;
            for (int dx = -1; dx <= 0; dx++) for (int dy = -1; dy <= 0; dy++)
                g.drawRect((int) ((q[0] + dx * N) * k), (int) ((q[1] + dy * N) * k), (int) (q[2] * k), (int) (q[3] * k));
        }
        if (r.kind.equals("S")) { g.setColor(new Color(r.grout)); g.fillRect(S - 24, 0, 24, 24); g.setColor(Color.WHITE); g.drawRect(S - 24, 0, 23, 23); }
        g.dispose();
        return out;
    }

    static void writeSheet(List<BufferedImage> thumbs, List<String> labels, Path file) throws Exception {
        int cols = 5, S = 300, H = S + 36;
        int rows = (thumbs.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * S, rows * H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i < thumbs.size(); i++) {
            int x = (i % cols) * S, y = (i / cols) * H;
            g.drawImage(thumbs.get(i), x, y, null);
            g.setColor(Color.BLACK);
            String t = labels.get(i);
            int cut = Math.min(t.length(), 48);
            g.drawString(t.substring(0, cut), x + 3, y + S + 14);
            if (cut < t.length()) g.drawString(t.substring(cut), x + 3, y + S + 28);
        }
        g.dispose();
        ImageIO.write(sheet, "png", file.toFile());
    }
}
