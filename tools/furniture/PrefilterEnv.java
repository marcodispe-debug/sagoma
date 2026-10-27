import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.stream.IntStream;

/**
 * Luce ambiente già pronta per il telefono: da un file .ibl di PackEnv (versione 1, solo il cubo nitido) scrive un .ibl
 * versione 2 con tutti i livelli dei riflessi già prefiltrati, così l'app non li calcola più sul processore all'apertura
 * del 3D (prima lo faceva Filament con Texture.generatePrefilterMipmap, sul thread principale).
 *
 * Il calcolo è quello di Filament (libs/generatePrefilterMipmap e libs/ibl/CubemapIBL.roughnessFilter, versione 1.77):
 * cubo specchiato in x, livello l con ruvidità lineare (l / (livelli − 1))², campionamento per importanza GGX che legge
 * dalla catena di mipmap del cubo nitido (meno rumore a parità di campioni), con i campioni ruotati di un angolo diverso
 * per ogni pixel (così non si vedono le righe della sequenza). Qui i campioni sono molti di più (256 invece di 8): c'è
 * tempo, e i livelli ruvidi vengono lisci.
 *
 * File versione 2: "SIBL", 2, lato del cubo, numero di livelli, 9 × 3 float delle armoniche sferiche (come la versione 1),
 * poi per ogni livello (dal più nitido) e per ogni faccia (+X, −X, +Y, −Y, +Z, −Z) i pixel nel formato della texture
 * (R11F_G11F_B10F, 4 byte a pixel, little endian): si caricano così come sono.
 *
 * Uso: java PrefilterEnv.java <ingresso.ibl> <uscita.ibl> [lato del cubo, 256] [campioni, 256]
 */
public class PrefilterEnv {

    /** Un cubo: 6 facce di dim × dim pixel RGB lineari. */
    record Cube(int dim, float[][] faces) {
        static Cube empty(int dim) { return new Cube(dim, new float[6][dim * dim * 3]); }
    }

    public static void main(String[] args) throws Exception {
        byte[] in = Files.readAllBytes(Paths.get(args[0]));
        int target = args.length > 2 ? Integer.parseInt(args[2]) : 256;
        int samples = args.length > 3 ? Integer.parseInt(args[3]) : 256;
        ByteBuffer bb = ByteBuffer.wrap(in).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.get() != 'S' || bb.get() != 'I' || bb.get() != 'B' || bb.get() != 'L') throw new IOException("non è un file .ibl");
        int version = bb.getInt();
        if (version != 1) throw new IOException("serve un .ibl versione 1 (cubo nitido), questo è versione " + version);
        int size = bb.getInt();
        bb.getInt(); // livelli
        float[] sh = new float[27];
        for (int i = 0; i < 27; i++) sh[i] = bb.getFloat();
        Cube base = Cube.empty(size);
        for (int f = 0; f < 6; f++) for (int i = 0; i < size * size * 3; i++) base.faces[f][i] = halfToFloat(bb.getShort());
        // Lato del cubo per il telefono: si dimezza finché serve (media di 2×2 pixel).
        while (base.dim > target) base = downsample(base);
        byte[] out = prefilter(base, sh, samples);
        Files.write(Paths.get(args[1]), out);
        System.out.printf("%s: cubo %d → %d px, %d livelli, %d campioni, %.1f MB%n", args[1], size, base.dim,
            Integer.numberOfTrailingZeros(base.dim) + 1, samples, out.length / 1048576.0);
    }

    /** Il file versione 2 dal cubo nitido `base` (lato potenza di 2). */
    static byte[] prefilter(Cube base, float[] sh, int samples) {
        int levels = Integer.numberOfTrailingZeros(base.dim) + 1;
        // Catena di mipmap del cubo nitido: i campioni dei livelli ruvidi leggono da quella giusta per la loro ampiezza.
        Cube[] chain = new Cube[levels];
        chain[0] = base;
        for (int l = 1; l < levels; l++) chain[l] = downsample(chain[l - 1]);
        long bytes = 16 + 27 * 4;
        for (int l = 0; l < levels; l++) bytes += 6L * (base.dim >> l) * (base.dim >> l) * 4;
        ByteBuffer out = ByteBuffer.allocate((int) bytes).order(ByteOrder.LITTLE_ENDIAN);
        out.put((byte) 'S').put((byte) 'I').put((byte) 'B').put((byte) 'L').putInt(2).putInt(base.dim).putInt(levels);
        for (float v : sh) out.putFloat(v);
        for (int l = 0; l < levels; l++) {
            float lod = levels == 1 ? 0 : (float) l / (levels - 1);
            Cube c = roughnessFilter(chain, base.dim >> l, lod * lod, samples);
            for (int f = 0; f < 6; f++) {
                float[] px = c.faces[f];
                for (int i = 0; i < px.length; i += 3) out.putInt(packR11G11B10(px[i], px[i + 1], px[i + 2]));
            }
        }
        return out.array();
    }

    /** Come CubemapIBL::roughnessFilter di Filament (con mirror = (−1, 1, 1) e prefilter = true). */
    static Cube roughnessFilter(Cube[] chain, int dim, float linearRoughness, int numSamples) {
        Cube dst = Cube.empty(dim);
        int maxLevel = chain.length - 1;
        int dim0 = chain[0].dim;
        if (linearRoughness == 0) {
            for (int f = 0; f < 6; f++) for (int y = 0; y < dim; y++) for (int x = 0; x < dim; x++) {
                float[] n = direction(f, x + 0.5f, y + 0.5f, dim);
                n[0] = -n[0];
                float[] c = sampleBilinear(chain[0], n);
                System.arraycopy(c, 0, dst.faces[f], (y * dim + x) * 3, 3);
            }
            return dst;
        }
        // Campioni fissi (dipendono solo dal numero del campione): direzione L attorno a N = (0, 0, 1), peso, livello.
        double omegaP = 4 * Math.PI / (6.0 * dim0 * dim0);
        float[][] ls = new float[numSamples][];
        float[] w = new float[numSamples];
        float[] mip = new float[numSamples];
        int count = 0;
        double weight = 0;
        for (int i = 0; i < numSamples; i++) {
            double u1 = (double) i / numSamples;
            double u2 = (Integer.reverse(i) & 0xffffffffL) / 4294967296.0;
            double a = linearRoughness;
            // Importanza GGX (Trowbridge-Reitz), come hemisphereImportanceSampleDggx.
            double phi = 2 * Math.PI * u1;
            double cosT2 = (1 - u2) / (1 + (a * a - 1) * u2);
            double cosT = Math.sqrt(cosT2);
            double sinT = Math.sqrt(1 - cosT2);
            double hx = sinT * Math.cos(phi), hy = sinT * Math.sin(phi), noh = cosT;
            double nol = 2 * noh * noh - 1;
            if (nol <= 0) continue;
            double pdf = distributionGGX(noh, a) / 4;
            double omegaS = 1 / (numSamples * pdf);
            double l = log4(omegaS) - log4(omegaP) + log4(4);
            ls[count] = new float[]{(float) (2 * noh * hx), (float) (2 * noh * hy), (float) nol};
            w[count] = (float) nol;
            mip[count] = (float) Math.max(0, Math.min(maxLevel, l));
            weight += nol;
            count++;
        }
        final int n = count;
        for (int i = 0; i < n; i++) w[i] /= (float) weight;
        IntStream.range(0, 6 * dim).parallel().forEach(row -> {
            int f = row / dim, y = row % dim;
            for (int x = 0; x < dim; x++) {
                float[] nn = direction(f, x + 0.5f, y + 0.5f, dim);
                nn[0] = -nn[0];
                // Base attorno alla normale (come Filament: "up" = z, se no x).
                float[] up = Math.abs(nn[2]) < 0.999f ? new float[]{0, 0, 1} : new float[]{1, 0, 0};
                float[] u0 = normalize(cross(up, nn));
                float[] u1 = cross(nn, u0);
                // Rotazione attorno alla normale, fissa per pixel (come Filament, ma ripetibile: stesso file a ogni build).
                double rot = (hash(f, x, y) & 0xffffff) / (double) 0x1000000 * 2 * Math.PI;
                float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                float[] t0 = {u0[0] * cr + u1[0] * sr, u0[1] * cr + u1[1] * sr, u0[2] * cr + u1[2] * sr};
                float[] t1 = {u1[0] * cr - u0[0] * sr, u1[1] * cr - u0[1] * sr, u1[2] * cr - u0[2] * sr};
                float r = 0, g = 0, b = 0;
                for (int s = 0; s < n; s++) {
                    float[] e = ls[s];
                    float[] d = {t0[0] * e[0] + t1[0] * e[1] + nn[0] * e[2], t0[1] * e[0] + t1[1] * e[1] + nn[1] * e[2], t0[2] * e[0] + t1[2] * e[1] + nn[2] * e[2]};
                    float[] c = sampleTrilinear(chain, mip[s], d);
                    r += c[0] * w[s]; g += c[1] * w[s]; b += c[2] * w[s];
                }
                int k = (y * dim + x) * 3;
                dst.faces[f][k] = r; dst.faces[f][k + 1] = g; dst.faces[f][k + 2] = b;
            }
        });
        return dst;
    }

    static int hash(int f, int x, int y) {
        int h = f * 0x9E3779B1 ^ x * 0x85EBCA77 ^ y * 0xC2B2AE3D;
        h ^= h >>> 16; h *= 0x7FEB352D; h ^= h >>> 15; h *= 0x846CA68B; h ^= h >>> 16;
        return h;
    }

    static double distributionGGX(double noh, double linearRoughness) {
        double a = noh * linearRoughness;
        double k = linearRoughness / (1 - noh * noh + a * a);
        return k * k * (1 / Math.PI);
    }

    static double log4(double x) { return Math.log(x) / Math.log(4); }

    /** Direzione del centro del pixel (x, y) della faccia f: Cubemap::getDirectionFor di Filament (stessa convenzione di PackEnv). */
    static float[] direction(int f, float x, float y, int dim) {
        float cx = x * 2f / dim - 1;
        float cy = 1 - y * 2f / dim;
        float[] d = switch (f) {
            case 0 -> new float[]{1, cy, -cx};
            case 1 -> new float[]{-1, cy, cx};
            case 2 -> new float[]{cx, 1, -cy};
            case 3 -> new float[]{cx, -1, cy};
            case 4 -> new float[]{cx, cy, 1};
            default -> new float[]{-cx, cy, -1};
        };
        return normalize(d);
    }

    /** Faccia e coordinate (0..1) nella direzione r: Cubemap::getAddressFor di Filament. */
    static float[] address(float[] r) {
        float rx = Math.abs(r[0]), ry = Math.abs(r[1]), rz = Math.abs(r[2]);
        int face;
        float sc, tc, ma;
        if (rx >= ry && rx >= rz) {
            ma = 1 / rx;
            if (r[0] >= 0) { face = 0; sc = -r[2]; tc = -r[1]; } else { face = 1; sc = r[2]; tc = -r[1]; }
        } else if (ry >= rx && ry >= rz) {
            ma = 1 / ry;
            if (r[1] >= 0) { face = 2; sc = r[0]; tc = r[2]; } else { face = 3; sc = r[0]; tc = -r[2]; }
        } else {
            ma = 1 / rz;
            if (r[2] >= 0) { face = 4; sc = r[0]; tc = -r[1]; } else { face = 5; sc = -r[0]; tc = -r[1]; }
        }
        return new float[]{face, (sc * ma + 1) * 0.5f, (tc * ma + 1) * 0.5f};
    }

    /** Bilineare dentro la faccia (ai bordi si ferma all'ultimo pixel: la differenza dal cubo "senza cuciture" è minima). */
    static float[] sampleBilinear(Cube c, float[] dir) {
        float[] a = address(dir);
        int f = (int) a[0];
        int d = c.dim;
        float fx = a[1] * d - 0.5f, fy = a[2] * d - 0.5f;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = fx - x0, ty = fy - y0;
        float[] px = c.faces[f];
        float[] out = new float[3];
        for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
            int x = Math.max(0, Math.min(d - 1, x0 + i)), y = Math.max(0, Math.min(d - 1, y0 + j));
            float wt = (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty);
            int k = (y * d + x) * 3;
            out[0] += px[k] * wt; out[1] += px[k + 1] * wt; out[2] += px[k + 2] * wt;
        }
        return out;
    }

    static float[] sampleTrilinear(Cube[] chain, float level, float[] dir) {
        int l0 = (int) level;
        int l1 = Math.min(chain.length - 1, l0 + 1);
        float t = level - l0;
        float[] a = sampleBilinear(chain[l0], dir);
        if (t == 0 || l1 == l0) return a;
        float[] b = sampleBilinear(chain[l1], dir);
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Metà lato: media di 2×2 pixel (come downsampleCubemapLevelBoxFilter). */
    static Cube downsample(Cube c) {
        int d = c.dim / 2;
        Cube o = Cube.empty(d);
        for (int f = 0; f < 6; f++) for (int y = 0; y < d; y++) for (int x = 0; x < d; x++) for (int ch = 0; ch < 3; ch++) {
            float[] p = c.faces[f];
            int w = c.dim;
            o.faces[f][(y * d + x) * 3 + ch] = 0.25f * (p[((2 * y) * w + 2 * x) * 3 + ch] + p[((2 * y) * w + 2 * x + 1) * 3 + ch]
                + p[((2 * y + 1) * w + 2 * x) * 3 + ch] + p[((2 * y + 1) * w + 2 * x + 1) * 3 + ch]);
        }
        return o;
    }

    static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static float[] normalize(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static float halfToFloat(short h) {
        int bits = h & 0xffff;
        int sign = (bits & 0x8000) != 0 ? -1 : 1;
        int exp = (bits >>> 10) & 0x1f;
        int mant = bits & 0x3ff;
        if (exp == 0) return sign * mant * (float) Math.pow(2, -24);
        if (exp == 31) return sign * 65504f;
        return sign * (1 + mant / 1024f) * (float) Math.pow(2, exp - 15);
    }

    /** Un pixel nel formato R11F_G11F_B10F (UINT_10F_11F_11F_REV): rosso nei bit 0–10, verde 11–21, blu 22–31. */
    static int packR11G11B10(float r, float g, float b) {
        return toSmallFloat(r, 6) | (toSmallFloat(g, 6) << 11) | (toSmallFloat(b, 5) << 22);
    }

    /** Float senza segno con 5 bit di esponente (bias 15) e `mantBits` di mantissa, arrotondato al più vicino. */
    static int toSmallFloat(float v, int mantBits) {
        if (!(v > 0)) return 0; // negativi e NaN → 0
        int maxExp = 30;
        float max = (float) ((2 - Math.pow(2, -mantBits)) * Math.pow(2, maxExp - 15));
        if (v >= max) return (maxExp << mantBits) | ((1 << mantBits) - 1);
        int e = Math.getExponent(v); // v = 1.m × 2^e
        if (e < -14) {
            // Denormale: v = m × 2^(−14 − mantBits).
            int m = Math.round(v * (float) Math.pow(2, 14 + mantBits));
            return m; // se arriva a 1 << mantBits è già il primo normale
        }
        int m = Math.round((v / (float) Math.pow(2, e) - 1) * (1 << mantBits));
        int exp = e + 15;
        if (m == (1 << mantBits)) { m = 0; exp++; }
        if (exp > maxExp) return (maxExp << mantBits) | ((1 << mantBits) - 1);
        return (exp << mantBits) | m;
    }
}
