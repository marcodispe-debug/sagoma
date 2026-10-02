import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Metadati Objaverse (licenza, autore, apprezzamenti, numero di facce) dei soli modelli candidati.
 * Scarica un blocco di metadati alla volta da Hugging Face (allenai/objaverse) e tiene solo le righe utili.
 *
 * Uso: java ObjaverseMeta.java <paths.tsv (uid, percorso glb)> <candidates.tsv (categoria, uid)> <uscita.tsv>
 */
public class ObjaverseMeta {
    public static void main(String[] args) throws Exception {
        Map<String, String> category = new HashMap<>();
        for (String l : Files.readAllLines(Paths.get(args[1]))) { String[] c = l.split("\t"); category.put(c[1], c[0]); }
        Map<String, List<String>> byChunk = new TreeMap<>();
        for (String l : Files.readAllLines(Paths.get(args[0]))) {
            String[] c = l.split("\t");
            byChunk.computeIfAbsent(c[1].split("/")[1], k -> new ArrayList<>()).add(c[0]);
        }
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[2]), StandardCharsets.UTF_8))) {
            for (var e : byChunk.entrySet()) {
                String url = "https://huggingface.co/datasets/allenai/objaverse/resolve/main/metadata/" + e.getKey() + ".json.gz";
                HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
                String json;
                try (InputStream in = new GZIPInputStream(r.body())) { json = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
                @SuppressWarnings("unchecked") Map<String, Object> all = (Map<String, Object>) Json.parse(json);
                for (String uid : e.getValue()) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) all.get(uid);
                    if (m == null) continue;
                    @SuppressWarnings("unchecked") Map<String, Object> user = (Map<String, Object>) m.getOrDefault("user", Map.of());
                    out.println(String.join("\t", category.get(uid), uid, str(m.get("license")), str(m.get("likeCount")), str(m.get("viewCount")),
                        str(m.get("faceCount")), clean(str(m.get("name"))), clean(str(user.get("displayName"))), clean(str(user.get("username")))));
                }
                out.flush();
                System.out.println(e.getKey() + ": " + e.getValue().size());
            }
        }
    }

    static String str(Object o) { return o == null ? "" : o instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : o.toString(); }
    static String clean(String s) { return s.replaceAll("[\\t\\r\\n]", " "); }

    /** JSON minimo (come in PackModels). */
    static final class Json {
        static Object parse(String s) { int[] p = {0}; return value(s, p); }
        static void ws(String s, int[] p) { while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++; }
        static Object value(String s, int[] p) {
            ws(s, p);
            char c = s.charAt(p[0]);
            if (c == '{') {
                Map<String, Object> m = new HashMap<>();
                p[0]++; ws(s, p);
                if (s.charAt(p[0]) == '}') { p[0]++; return m; }
                while (true) {
                    ws(s, p); String k = (String) value(s, p); ws(s, p); p[0]++;
                    m.put(k, value(s, p)); ws(s, p);
                    if (s.charAt(p[0]++) == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                p[0]++; ws(s, p);
                if (s.charAt(p[0]) == ']') { p[0]++; return l; }
                while (true) { l.add(value(s, p)); ws(s, p); if (s.charAt(p[0]++) == ']') return l; }
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
            return Double.parseDouble(s.substring(st, p[0]));
        }
    }
}
