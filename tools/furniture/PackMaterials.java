import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Prepara i materiali fotografici della versione pro di Sagoma (ambientCG, Poly Haven: CC0).
 *
 * Ogni cartella d'ingresso contiene le mappe *_Color.jpg, *_NormalGL.jpg, *_Roughness.jpg. Per ognuna
 * scrive `<id>_color.jpg` (1024 px), `<id>_normal.jpg`, `<id>_orm.jpg` (ruvidità nel verde, metallo nero nel
 * blu, come vuole glTF), `<id>_thumb.jpg` (campione 128 px) e alla fine `materials.json`.
 *
 * Tabella (TSV): cartella <TAB> nome <TAB> categoria <TAB> lato reale in cm <TAB> uso (pavimento, parete, entrambi) <TAB> fonte
 *
 * Uso: java PackMaterials.java <cartella materiali> <tabella.tsv> <uscita>
 */
public class PackMaterials {
    /** Lato delle mappe (px). */
    static final int SIZE = 1024;

    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[2]);
        Files.createDirectories(out);
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            Path dir = in.resolve(c[0].trim());
            BufferedImage color = find(dir, "_Color"), normal = find(dir, "_NormalGL"), rough = find(dir, "_Roughness");
            if (color == null || normal == null || rough == null) { System.err.println("mancano mappe: " + c[0]); continue; }
            String id = c[0].trim().toLowerCase(Locale.ROOT);
            write(resize(color, SIZE), out.resolve(id + "_color.jpg"), 0.88f);
            write(resize(normal, SIZE), out.resolve(id + "_normal.jpg"), 0.92f);
            BufferedImage r = resize(rough, SIZE);
            BufferedImage orm = new BufferedImage(r.getWidth(), r.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < r.getHeight(); y++) for (int x = 0; x < r.getWidth(); x++) {
                int g = (r.getRGB(x, y) >> 8) & 255;
                orm.setRGB(x, y, (255 << 16) | (g << 8));
            }
            write(orm, out.resolve(id + "_orm.jpg"), 0.9f);
            write(resize(color, 128), out.resolve(id + "_thumb.jpg"), 0.85f);
            // Colore medio (sRGB), per la pianta e il disegno senza texture.
            BufferedImage small = resize(color, 32);
            long rs = 0, gs = 0, bs = 0; int n = 0;
            for (int y = 0; y < small.getHeight(); y++) for (int x = 0; x < small.getWidth(); x++) {
                int p = small.getRGB(x, y); rs += (p >> 16) & 255; gs += (p >> 8) & 255; bs += p & 255; n++;
            }
            long argb = 0xFF000000L | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
            if (!first) json.append(",");
            first = false;
            json.append("\n{\"id\":\"").append(id).append("\",\"label\":\"").append(c[1].trim().replace("\"", "\\\""))
                .append("\",\"category\":\"").append(c[2].trim()).append("\",\"size\":").append(Double.parseDouble(c[3].trim()))
                .append(",\"argb\":").append(argb).append(",\"use\":\"").append(c[4].trim())
                .append("\",\"source\":\"").append(c.length > 5 ? c[5].trim() : "").append("\",\"license\":\"CC0\"}");
            System.out.println(id + "  " + c[1]);
        }
        json.append("\n]");
        Files.writeString(out.resolve("materials.json"), json.toString());
    }

    static BufferedImage find(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) return null;
        try (var s = Files.list(dir)) {
            Optional<Path> p = s.filter(f -> f.getFileName().toString().contains(suffix) && f.toString().endsWith(".jpg")).findFirst();
            return p.isPresent() ? ImageIO.read(p.get().toFile()) : null;
        }
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
        File f = p.toFile();
        f.delete();
        try (FileImageOutputStream os = new FileImageOutputStream(f)) {
            w.setOutput(os);
            w.write(null, new IIOImage(img, null, null), prm);
        }
        w.dispose();
    }
}
