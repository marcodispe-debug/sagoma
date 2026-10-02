package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.geometry.toRadians
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Luce del giorno all'ora scelta, con gli stessi calcoli del renderer Filament di Android: sole a est alle 6,
 * a sud a mezzogiorno, a ovest alle 18 (nord in alto nella pianta), alto fino a 55°. Ne seguono colore e
 * forza del sole, del cielo, della luce ambiente e di quella che entra dalle finestre; le lampade si
 * accendono quando il sole è basso ("auto"), sempre ("on") o mai ("off").
 */
class Daylight(val hour: Float, lamps: String, val walking: Boolean) {
    val elevation: Double = 55.0 * sin(kotlin.math.PI * (hour - 6.0) / 12.0)
    /** 0 di notte, 1 con il sole alto; morbido attorno all'alba e al tramonto. */
    val day: Float = ((elevation + 6.0) / 30.0).coerceIn(0.0, 1.0).toFloat()
    /** 1 = sole basso, luce calda. */
    val low: Float = (1.0 - (elevation / 35.0).coerceIn(0.0, 1.0)).toFloat()

    /** Direzione verso il sole (unitaria). */
    val toSun: Vec3 = run {
        val az = toRadians(90.0 + (hour - 6.0) * 15.0)
        val el = toRadians(max(elevation, 2.0))
        Vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el))
    }

    /** Colore × intensità del sole (niente sotto l'orizzonte). */
    val sun: FloatArray = if (elevation <= 0) floatArrayOf(0f, 0f, 0f) else {
        val k = 0.80f * day
        floatArrayOf(k, k * (0.97f - 0.33f * low), k * (0.92f - 0.52f * low))
    }

    /** Luce diffusa del cielo; dentro casa entra solo dalle finestre, quindi è molto meno. */
    val ambient: FloatArray = run {
        val k = (0.08f + 0.46f * day) * (if (walking) 0.55f else 1f)
        floatArrayOf(k * (0.92f + 0.08f * low), k * 0.95f, k * (1.02f - 0.12f * low))
    }

    /** Colore del cielo (sfondo): giorno azzurro chiaro, tramonto caldo, notte blu scuro. */
    val sky: FloatArray = run {
        val dayC = floatArrayOf(0.80f, 0.88f, 0.96f)
        val sunset = floatArrayOf(0.96f, 0.76f, 0.60f)
        val night = floatArrayOf(0.035f, 0.05f, 0.10f)
        FloatArray(3) { i ->
            val lit = dayC[i] + (sunset[i] - dayC[i]) * (low * low)
            night[i] + (lit - night[i]) * day
        }
    }

    /** Colore della luce che entra dalle finestre. */
    val windowColor: FloatArray = floatArrayOf(0.9f + 0.1f * low, 0.94f, 1f - 0.2f * low)

    val lampsOn: Boolean = lamps == "on" || (lamps == "auto" && elevation < 8)
}

/**
 * Shader "illuminato" del computer e del browser: sole con ombre (dove il renderer le calcola), luce del
 * cielo, luci della casa e luce dalle finestre, su una scena con i colori dei vertici.
 */
object LitShaders {
    const val MAX_LAMPS = 24
    const val MAX_WINDOWS = 16

    /** Colore del prato attorno alla casa (come nel renderer Filament). */
    val grass = floatArrayOf(0.47f, 0.58f, 0.36f)

    /** `shadows`: il renderer passa la mappa delle ombre del sole (computer). */
    fun vertex(header: String, shadows: Boolean) = header + (if (shadows) "#define SHADOWS\n" else "") + """
        uniform mat4 uMvp;
        #ifdef SHADOWS
        uniform mat4 uLightMvp;
        varying vec4 vShadow;
        #endif
        attribute vec3 aPos;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        varying vec4 vColor;
        varying vec3 vNormal;
        varying vec3 vPos;
        void main() {
            vColor = aColor;
            vNormal = aNormal;
            vPos = aPos;
            #ifdef SHADOWS
            vShadow = uLightMvp * vec4(aPos, 1.0);
            #endif
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    fun fragment(header: String, shadows: Boolean) = header + (if (shadows) "#define SHADOWS\n" else "") + """
        uniform vec3 uToSun;
        uniform vec3 uSun;
        uniform vec3 uAmbient;
        uniform float uSunIndoor;
        uniform int uLampCount;
        uniform vec4 uLamp[$MAX_LAMPS];
        uniform vec3 uLampColor[$MAX_LAMPS];
        uniform int uWinCount;
        uniform vec4 uWin[$MAX_WINDOWS];
        uniform vec4 uWinDir[$MAX_WINDOWS];
        uniform vec3 uWinColor;
        varying vec4 vColor;
        varying vec3 vNormal;
        varying vec3 vPos;
        #ifdef SHADOWS
        uniform sampler2D uShadowMap;
        uniform float uShadowTexel;
        varying vec4 vShadow;
        float sunShadow(float ndl) {
            vec3 p = vShadow.xyz / vShadow.w * 0.5 + 0.5;
            if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
            float bias = 0.0012 + 0.004 * (1.0 - ndl);
            float lit = 0.0;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                float d = texture2D(uShadowMap, p.xy + vec2(float(x), float(y)) * uShadowTexel).r;
                lit += (p.z - bias <= d) ? 1.0 : 0.0;
            }
            return lit / 9.0;
        }
        #endif
        vec3 tone(vec3 c) {
            // Sopra 0,8 i colori si ammorbidiscono invece di bruciarsi (luci forti, sole sul bianco).
            vec3 over = max(c - 0.8, 0.0);
            return min(c, 0.8) + 0.2 * (1.0 - exp(-over / 0.2));
        }
        void main() {
            float len = length(vNormal);
            // Superfici che emettono luce (lampade): il loro colore, senza ombreggiatura.
            if (len < 0.1) { gl_FragColor = vColor; return; }
            vec3 n = vNormal / len;
            float ndl = max(dot(n, uToSun), 0.0);
            float sh = 1.0;
            #ifdef SHADOWS
            if (ndl > 0.0) sh = sunShadow(ndl);
            #endif
            // Cielo: più luce sulle superfici rivolte in alto; sui muri un po' più scuro in basso.
            vec3 light = uAmbient * (0.72 + 0.28 * n.y) * (abs(n.y) < 0.5 ? mix(0.85, 1.0, clamp(vPos.y / 240.0, 0.0, 1.0)) : 1.0);
            light += uSun * ndl * sh * uSunIndoor;
            for (int i = 0; i < $MAX_LAMPS; i++) {
                if (i >= uLampCount) break;
                vec3 d = uLamp[i].xyz - vPos;
                float dist = length(d);
                float att = uLamp[i].w / (1.0 + dist * dist / 48400.0);
                light += uLampColor[i] * att * (0.3 + 0.7 * max(dot(n, d / max(dist, 1.0)), 0.0));
            }
            for (int i = 0; i < $MAX_WINDOWS; i++) {
                if (i >= uWinCount) break;
                vec3 d = uWin[i].xyz - vPos;
                float dist = length(d);
                vec3 l = d / max(dist, 1.0);
                // Solo davanti alla finestra, verso l'interno della stanza.
                // Pieno davanti, sfumato ai lati (i muri vicini alla finestra), nullo dietro (fuori casa).
                // (Si guarda solo in orizzontale: la luce scende un po', ma non deve uscire sotto la finestra.)
                vec3 inward = normalize(vec3(uWinDir[i].x, 0.0, uWinDir[i].z));
                vec3 toPoint = normalize(vec3(-d.x, 0.0, -d.z) + vec3(0.0001, 0.0, 0.0));
                float front = clamp(dot(toPoint, inward) * 1.2 + 0.55, 0.0, 1.0);
                float att = uWinDir[i].w * front / (1.0 + dist * dist / 90000.0);
                light += uWinColor * att * (0.35 + 0.65 * max(dot(n, l), 0.0));
            }
            gl_FragColor = vec4(tone(vColor.rgb * light), vColor.a);
        }
    """

    /** Shader delle ombre: solo la profondità vista dal sole. */
    fun depthVertex(header: String) = header + """
        uniform mat4 uLightMvp;
        attribute vec3 aPos;
        void main() { gl_Position = uLightMvp * vec4(aPos, 1.0); }
    """

    fun depthFragment(header: String) = header + "void main() { gl_FragColor = vec4(1.0); }\n"

    /**
     * Prato: un grande quadrato poco sotto il pavimento più basso della scena (se si guarda un piano di sopra,
     * sotto quelli di sotto: altrimenti il prato li taglierebbe e li coprirebbe), nel formato dei vertici della scena.
     */
    fun ground(scene: Scene3D): FloatArray {
        val b = scene.bounds ?: return FloatArray(0)
        val m = 4000.0
        val x0 = (b.minX - m).toFloat(); val x1 = (b.maxX + m).toFloat()
        val z0 = (b.minY - m).toFloat(); val z1 = (b.maxY + m).toFloat()
        var lowest = 0f
        var k = 1
        while (k < scene.opaque.size) { lowest = minOf(lowest, scene.opaque[k]); k += Scene3D.FLOATS_PER_VERTEX }
        val y = lowest - 0.5f
        val g = grass
        val corners = listOf(x0 to z0, x0 to z1, x1 to z1, x0 to z0, x1 to z1, x1 to z0)
        val out = FloatArray(corners.size * Scene3D.FLOATS_PER_VERTEX)
        corners.forEachIndexed { i, (x, z) ->
            val o = i * Scene3D.FLOATS_PER_VERTEX
            out[o] = x; out[o + 1] = y; out[o + 2] = z
            out[o + 3] = 0f; out[o + 4] = 1f; out[o + 5] = 0f
            out[o + 6] = g[0]; out[o + 7] = g[1]; out[o + 8] = g[2]; out[o + 9] = 1f
        }
        return out
    }

    /**
     * Uniform delle luci per la scena e l'ora: lampade (le più vicine a chi guarda, fino a [MAX_LAMPS]) e
     * finestre (fino a [MAX_WINDOWS]).
     */
    class Lights(scene: Scene3D?, daylight: Daylight, viewer: Vec3, windowsAlways: Boolean = false) {
        val lamps = FloatArray(MAX_LAMPS * 4)
        val lampColors = FloatArray(MAX_LAMPS * 3)
        /** Stanza di ogni lampada in pianta (minX, minZ, maxX, maxZ): la luce non passa i muri. */
        val lampBoxes = FloatArray(MAX_LAMPS * 4)
        var lampCount = 0
        val windows = FloatArray(MAX_WINDOWS * 4)
        val windowDirs = FloatArray(MAX_WINDOWS * 4)
        var windowCount = 0

        init {
            if (scene != null && daylight.lampsOn) {
                for (l in scene.lights.sortedBy { (it.position - viewer).length }.take(MAX_LAMPS)) {
                    val i = lampCount++
                    // Una plafoniera da ~1000 lumen illumina bene una stanza a 2 m.
                    val s = (l.lumens / 1000.0 * 0.9).toFloat()
                    // I faretti puntano in basso: la luce si mette poco sotto, così arriva sul pavimento.
                    val p = l.direction?.let { l.position + it * 30.0 } ?: l.position
                    lamps[i * 4] = p.x.toFloat(); lamps[i * 4 + 1] = p.y.toFloat(); lamps[i * 4 + 2] = p.z.toFloat(); lamps[i * 4 + 3] = s
                    val c = if (l.warm) floatArrayOf(1f, 0.80f, 0.58f) else floatArrayOf(0.95f, 0.97f, 1f)
                    lampColors[i * 3] = c[0]; lampColors[i * 3 + 1] = c[1]; lampColors[i * 3 + 2] = c[2]
                    // Gli angoli della stanza sono in mezzeria dei muri: poco oltre ci sono le facce interne (illuminate),
                    // mentre le facce esterne restano fuori (buie).
                    val b = l.room
                    val m = 3.0
                    lampBoxes[i * 4] = (b?.minX?.minus(m) ?: -1e7).toFloat(); lampBoxes[i * 4 + 1] = (b?.minY?.minus(m) ?: -1e7).toFloat()
                    lampBoxes[i * 4 + 2] = (b?.maxX?.plus(m) ?: 1e7).toFloat(); lampBoxes[i * 4 + 3] = (b?.maxY?.plus(m) ?: 1e7).toFloat()
                }
            }
            // Luce dalle finestre solo camminando: vista dall'alto la stanza è già aperta sul cielo, e la luce
            // uscirebbe dalla casa sul prato. `windowsAlways`: il renderer sa tenerla dentro casa (mappa dei soffitti).
            if (scene != null && daylight.day > 0.02f && (daylight.walking || windowsAlways)) {
                for (w in scene.windows.sortedBy { (it.center - viewer).length }.take(MAX_WINDOWS)) {
                    val i = windowCount++
                    val c = w.center + w.inward * 20.0
                    val area = (w.width * w.height / 10000.0).toFloat()
                    windows[i * 4] = c.x.toFloat(); windows[i * 4 + 1] = c.y.toFloat(); windows[i * 4 + 2] = c.z.toFloat()
                    val d = (w.inward + Vec3(0.0, -0.35, 0.0)).normalized()
                    windowDirs[i * 4] = d.x.toFloat(); windowDirs[i * 4 + 1] = d.y.toFloat(); windowDirs[i * 4 + 2] = d.z.toFloat()
                    windowDirs[i * 4 + 3] = 1.3f * area * daylight.day
                }
            }
        }
    }
}
