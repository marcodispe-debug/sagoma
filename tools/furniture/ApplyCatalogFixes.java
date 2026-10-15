import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Applica a catalog.json le correzioni a mano di catalog-fixes.tsv (voci nascoste, misure riscalate, nomi, categorie, quote).
 * Si lancia dopo PackModels; rilanciarlo non cambia niente (le correzioni già fatte restano uguali).
 *
 * Uso (JDK 17+): java ApplyCatalogFixes.java <catalog-fixes.tsv> <catalog.json>
 *
 * Il catalogo ha una voce per riga (come lo scrive PackModels): si cambiano solo i campi corretti, il resto della riga resta
 * com'è. Per le misure si controlla che la scala sia uniforme: ogni misura nuova deve stare entro 1 cm da quella vecchia per
 * lo stesso fattore, altrimenti il modello si deformerebbe e lo strumento si ferma.
 */
public class ApplyCatalogFixes {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.err.println("Uso: java ApplyCatalogFixes.java <catalog-fixes.tsv> <catalog.json>"); System.exit(1); }
        Path catalog = Paths.get(args[1]);
        String text = Files.readString(catalog, StandardCharsets.UTF_8);
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        int changed = 0;
        for (String row : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (row.isBlank() || row.startsWith("#")) continue;
            String[] c = row.split("\t", -1);
            String model = c[0].trim(), fix = c[1].trim(), value = c.length > 2 ? c[2].trim() : "";
            int i = find(lines, model);
            if (i < 0) throw new IllegalStateException("Modello non trovato nel catalogo: " + model);
            String line = lines.get(i);
            String out = switch (fix) {
                case "nascondi" -> line.contains("\"hidden\":true") ? line : line.replaceFirst("}(,?)\\s*$", ",\"hidden\":true}$1");
                case "nome" -> setString(line, "label", value);
                case "categoria" -> setString(line, "category", value);
                case "quota" -> setNumber(line, "elevation", Long.parseLong(value));
                case "misure" -> resize(line, model, value);
                default -> throw new IllegalArgumentException("Correzione sconosciuta: " + fix + " (" + model + ")");
            };
            if (!out.equals(line)) { lines.set(i, out); changed++; }
        }
        Files.writeString(catalog, String.join(nl, lines), StandardCharsets.UTF_8);
        System.out.println("Correzioni applicate: " + changed);
    }

    static int find(List<String> lines, String model) {
        String key = "\"model\":\"" + model + "\"";
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(key)) return i;
        return -1;
    }

    static String setString(String line, String key, String value) {
        Matcher m = Pattern.compile("\"" + key + "\":\"[^\"]*\"").matcher(line);
        if (!m.find()) throw new IllegalStateException("Campo " + key + " mancante: " + line);
        return line.substring(0, m.start()) + "\"" + key + "\":\"" + value.replace("\"", "'") + "\"" + line.substring(m.end());
    }

    static String setNumber(String line, String key, long value) {
        Matcher m = Pattern.compile("\"" + key + "\":-?[0-9.]+").matcher(line);
        if (!m.find()) throw new IllegalStateException("Campo " + key + " mancante: " + line);
        return line.substring(0, m.start()) + "\"" + key + "\":" + value + line.substring(m.end());
    }

    static double number(String line, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":(-?[0-9.]+)").matcher(line);
        if (!m.find()) throw new IllegalStateException("Campo " + key + " mancante: " + line);
        return Double.parseDouble(m.group(1));
    }

    /** Nuove misure "LxPxA", solo se sono le vecchie per uno stesso fattore (entro 1 cm per l'arrotondamento). */
    static String resize(String line, String model, String value) {
        String[] keys = {"width", "depth", "height"};
        String[] v = value.split("x");
        double[] old = new double[3], now = new double[3];
        double so = 0, sn = 0;
        for (int k = 0; k < 3; k++) { old[k] = number(line, keys[k]); now[k] = Double.parseDouble(v[k]); so += old[k]; sn += now[k]; }
        double f = sn / so;
        for (int k = 0; k < 3; k++) {
            if (Math.abs(now[k] - old[k] * f) > 1.0) throw new IllegalStateException(String.format(Locale.ROOT,
                "%s: %s non è una scala uniforme di %.0fx%.0fx%.0f (fattore %.3f)", model, value, old[0], old[1], old[2], f));
        }
        if (Math.abs(f - 1) > 1e-9) System.out.printf(Locale.ROOT, "%-36s %.0fx%.0fx%.0f -> %s  (x%.3f)%n", model, old[0], old[1], old[2], value, f);
        String out = line;
        for (int k = 0; k < 3; k++) out = setNumber(out, keys[k], Math.round(now[k]));
        return out;
    }
}
