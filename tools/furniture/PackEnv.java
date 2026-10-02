import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.Random;

/**
 * Luce ambiente per il motore Filament della versione pro di Sagoma, da una foto HDR a 360° (formato
 * Radiance .hdr, equirettangolare, es. da Poly Haven).
 *
 * Scrive un file .ibl con:
 *  - intestazione: "SIBL", versione, lato del cubo, numero di livelli;
 *  - 9 × 3 float: armoniche sferiche dell'irradianza già pronte per IndirectLight.irradiance(3, …);
 *  - per ogni livello e per ogni faccia (+X, −X, +Y, −Y, +Z, −Z) i pixel RGB in half float: la mappa dei
 *    riflessi prefiltrata (livello 0 lucido, gli altri sempre più ruvidi).
 * La luminosità si normalizza: l'irradianza media diventa `target` (così tutte le luci hanno la stessa
 * esposizione).
 *
 * Uso: java PackEnv.java <foto.hdr> <uscita.ibl> [lato del cubo, 256] [irradianza media, 0.5]
 */
public class PackEnv {

    static int W, H;
    static float[] img; // RGB lineare

    public static void main(String[] args) throws Exception {
        readHdr(Paths.get(args[0]));
        int size = args.length > 2 ? Integer.parseInt(args[2]) : 256;
        double target = args.length > 3 ? Double.parseDouble(args[3]) : 0.5;
        double[] sh = sh();
        // sh[0..2] è il termine costante dell'irradianza (già diviso per π): lo portiamo a `target`.
        double lum = 0.2126 * sh[0] + 0.7152 * sh[1] + 0.0722 * sh[2];
        double k = target / lum;
        for (int i = 0; i < sh.length; i++) sh[i] *= k;
        for (int i = 0; i < img.length; i++) img[i] *= k;

        int levels = Integer.numberOfTrailingZeros(size) + 1;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        ByteBuffer head = ByteBuffer.allocate(16 + 27 * 4).order(ByteOrder.LITTLE_ENDIAN);
        head.put("SIBL".getBytes()).putInt(1).putInt(size).putInt(levels);
        for (double v : sh) head.putFloat((float) v);
        out.write(head.array());
        // Si scrive solo il livello nitido: i livelli ruvidi li calcola Filament all'avvio
        // (Texture.generatePrefilterMipmap). Il numero di livelli resta nell'intestazione.
        for (int l = 0; l < 1; l++) {
            int s = size >> l;
            double lod = levels == 1 ? 0 : (double) l / (levels - 1);
            // Come in Filament: lod = r (2 − r) con r ruvidità percepita; α = r².
            double r = 1 - Math.sqrt(1 - lod);
            double alpha = r * r;
            ByteBuffer face = ByteBuffer.allocate(s * s * 6).order(ByteOrder.LITTLE_ENDIAN);
            for (int f = 0; f < 6; f++) {
                face.clear();
                for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
                    double[] d = dir(f, (x + 0.5) / s * 2 - 1, (y + 0.5) / s * 2 - 1);
                    double[] c = l == 0 ? sample(d) : prefilter(d, alpha, s);
                    for (int ch = 0; ch < 3; ch++) face.putShort(half((float) c[ch]));
                }
                out.write(face.array());
            }
            System.out.printf("livello %d: %d px, ruvidità %.2f%n", l, s, r);
        }
        Files.write(Paths.get(args[1]), bos.toByteArray());
        System.out.printf("SH0 = %.3f %.3f %.3f, file %.1f KB%n", sh[0], sh[1], sh[2], bos.size() / 1024.0);
    }

    /** Direzione del pixel (u, v in −1..1, v verso il basso) della faccia f, convenzione OpenGL. */
    static double[] dir(int f, double u, double v) {
        double[] d = switch (f) {
            case 0 -> new double[]{1, -v, -u};
            case 1 -> new double[]{-1, -v, u};
            case 2 -> new double[]{u, 1, v};
            case 3 -> new double[]{u, -1, -v};
            case 4 -> new double[]{u, -v, 1};
            default -> new double[]{-u, -v, -1};
        };
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return new double[]{d[0] / l, d[1] / l, d[2] / l};
    }

    /** Colore della foto nella direzione d (bilineare). +Y in alto. */
    static double[] sample(double[] d) {
        double phi = Math.atan2(d[0], -d[2]);
        double theta = Math.acos(Math.max(-1, Math.min(1, d[1])));
        double fx = (phi / (2 * Math.PI) + 0.5) * W - 0.5;
        double fy = theta / Math.PI * H - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double[] c = new double[3];
        for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
            int x = Math.floorMod(x0 + i, W), y = Math.max(0, Math.min(H - 1, y0 + j));
            double w = (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty);
            for (int ch = 0; ch < 3; ch++) c[ch] += img[(y * W + x) * 3 + ch] * w;
        }
        return c;
    }

    static final Random RND = new Random(1);

    /** Riflesso ruvido: media pesata GGX attorno a d (N = V = R), campionamento per importanza. */
    static double[] prefilter(double[] n, double alpha, int size) {
        int samples = 192;
        double[] up = Math.abs(n[1]) < 0.999 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] tx = norm(cross(up, n));
        double[] ty = cross(n, tx);
        double[] c = new double[3];
        double wsum = 0;
        for (int i = 0; i < samples; i++) {
            // Sequenza di Hammersley.
            double u1 = (i + 0.5) / samples;
            double u2 = Integer.reverse(i) / 4294967296.0 + 0.5;
            double a2 = alpha * alpha;
            double cosT = Math.sqrt((1 - u1) / (1 + (a2 - 1) * u1));
            double sinT = Math.sqrt(1 - cosT * cosT);
            double ph = 2 * Math.PI * u2;
            double[] h = {sinT * Math.cos(ph), sinT * Math.sin(ph), cosT};
            double[] hw = {tx[0] * h[0] + ty[0] * h[1] + n[0] * h[2], tx[1] * h[0] + ty[1] * h[1] + n[1] * h[2], tx[2] * h[0] + ty[2] * h[1] + n[2] * h[2]};
            double vh = dot(n, hw);
            double[] l = {2 * vh * hw[0] - n[0], 2 * vh * hw[1] - n[1], 2 * vh * hw[2] - n[2]};
            double nl = dot(n, l);
            if (nl <= 0) continue;
            double[] s = sample(l);
            for (int ch = 0; ch < 3; ch++) c[ch] += s[ch] * nl;
            wsum += nl;
        }
        for (int ch = 0; ch < 3; ch++) c[ch] /= Math.max(1e-9, wsum);
        return c;
    }

    /**
     * Armoniche sferiche (3 bande) dell'irradianza divisa per π, con le costanti della base già
     * moltiplicate: come le vuole Filament (IndirectLight.irradiance), che le valuta con
     * sh0 + sh1·y + sh2·z + sh3·x + sh4·yx + sh5·yz + sh6·(3z²−1) + sh7·zx + sh8·(x²−y²).
     */
    static double[] sh() {
        double[] L = new double[27];
        double total = 0;
        for (int y = 0; y < H; y++) {
            double theta = (y + 0.5) / H * Math.PI;
            double dw = (2 * Math.PI / W) * (Math.PI / H) * Math.sin(theta);
            for (int x = 0; x < W; x++) {
                double phi = ((x + 0.5) / W - 0.5) * 2 * Math.PI;
                double dx = Math.sin(theta) * Math.sin(phi), dy = Math.cos(theta), dz = -Math.sin(theta) * Math.cos(phi);
                double[] b = basis(dx, dy, dz);
                for (int i = 0; i < 9; i++) for (int ch = 0; ch < 3; ch++) L[i * 3 + ch] += img[(y * W + x) * 3 + ch] * b[i] * dw;
                total += dw;
            }
        }
        // Convoluzione con il coseno (A_l) e divisione per π; poi le costanti della base.
        double[] A = {Math.PI, 2 * Math.PI / 3, 2 * Math.PI / 3, 2 * Math.PI / 3, Math.PI / 4, Math.PI / 4, Math.PI / 4, Math.PI / 4, Math.PI / 4};
        double[] K = {0.282095, 0.488603, 0.488603, 0.488603, 1.092548, 1.092548, 0.315392, 1.092548, 0.546274};
        double[] out = new double[27];
        for (int i = 0; i < 9; i++) for (int ch = 0; ch < 3; ch++) out[i * 3 + ch] = L[i * 3 + ch] * A[i] / Math.PI * K[i];
        return out;
    }

    static double[] basis(double x, double y, double z) {
        return new double[]{0.282095, 0.488603 * y, 0.488603 * z, 0.488603 * x, 1.092548 * x * y, 1.092548 * y * z, 0.315392 * (3 * z * z - 1), 1.092548 * x * z, 0.546274 * (x * x - y * y)};
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    static short half(float f) {
        int bits = Float.floatToIntBits(Math.min(f, 65000f));
        int sign = (bits >>> 16) & 0x8000;
        int exp = ((bits >>> 23) & 0xFF) - 127 + 15;
        int mant = bits & 0x7FFFFF;
        if (exp <= 0) return (short) sign;
        if (exp >= 31) return (short) (sign | 0x7BFF);
        return (short) (sign | (exp << 10) | (mant >>> 13));
    }

    /** Legge un .hdr Radiance (RGBE, righe compresse RLE o no). */
    static void readHdr(Path p) throws IOException {
        byte[] data = Files.readAllBytes(p);
        int pos = 0;
        String line;
        do {
            int e = pos;
            while (data[e] != '\n') e++;
            line = new String(data, pos, e - pos).trim();
            pos = e + 1;
        } while (!line.startsWith("-Y") && !line.startsWith("+Y"));
        String[] t = line.split("\\s+");
        H = Integer.parseInt(t[1]);
        W = Integer.parseInt(t[3]);
        img = new float[W * H * 3];
        byte[] scan = new byte[W * 4];
        for (int y = 0; y < H; y++) {
            if (W >= 8 && W < 32768 && data[pos] == 2 && data[pos + 1] == 2 && (data[pos + 2] & 0x80) == 0) {
                pos += 4;
                for (int ch = 0; ch < 4; ch++) {
                    int x = 0;
                    while (x < W) {
                        int n = data[pos++] & 0xFF;
                        if (n > 128) { n -= 128; byte v = data[pos++]; for (int k = 0; k < n; k++) scan[(x++) * 4 + ch] = v; }
                        else for (int k = 0; k < n; k++) scan[(x++) * 4 + ch] = data[pos++];
                    }
                }
            } else {
                System.arraycopy(data, pos, scan, 0, W * 4);
                pos += W * 4;
            }
            for (int x = 0; x < W; x++) {
                int e = scan[x * 4 + 3] & 0xFF;
                float f = e == 0 ? 0 : (float) Math.scalb(1.0, e - 136);
                for (int ch = 0; ch < 3; ch++) img[(y * W + x) * 3 + ch] = (scan[x * 4 + ch] & 0xFF) * f;
            }
        }
    }
}
