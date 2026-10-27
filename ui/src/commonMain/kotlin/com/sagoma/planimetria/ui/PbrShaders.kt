package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.LightZones

/**
 * Shader "fotorealistico" del computer: materiali fisici (PBR metallo/ruvidità, come glTF e Filament), luce
 * in spazio lineare con esposizione e tone mapping ACES (resa simile a una fotografia), sole con ombre
 * morbide, cielo e terreno che illuminano e si riflettono, luci della casa, luce dalle finestre. Serve la
 * stessa scena (colori dei vertici), i materiali fotografici (colore, rilievo, ruvidità) e i modelli glTF
 * degli arredi.
 */
object PbrShaders {
    const val MAX_LAMPS = LitShaders.MAX_LAMPS
    const val MAX_WINDOWS = LitShaders.MAX_WINDOWS

    fun vertex(header: String) = header + """
        uniform mat4 uViewProj;
        uniform mat4 uModel;
        uniform mat4 uLightMvp;
        uniform mat4 uRoofMvp;
        uniform vec3 uToSun;
        // Lato (cm) di un punto della mappa delle ombre del sole.
        uniform float uShadowTexelCm;
        attribute vec3 aPos;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        attribute vec2 aUv;
        varying vec3 vPos;
        varying vec3 vNormal;
        varying vec4 vColor;
        varying vec2 vUv;
        // "centroid": sui bordi (antialiasing) i punti per il sole restano sulla superficie, non oltre il suo spigolo (dove,
        // sopra il soffitto, il sole c'è).
        centroid varying vec4 vShadow;
        centroid varying vec4 vRoofSun;
        void main() {
            vec4 w = uModel * vec4(aPos, 1.0);
            vPos = w.xyz;
            vNormal = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * aNormal;
            vColor = aColor;
            vUv = aUv;
            // Punto con cui si guarda la mappa delle ombre del sole: spostato lungo la normale, dal lato del sole, di 3,5 punti della
            // mappa (fin dove arrivano i campioni del bordo morbido), di più quanto più la superficie è di sbieco rispetto al sole. Così
            // una superficie non fa ombra a se stessa e basta un margine di profondità minimo (uShadowBias).
            vec3 s = w.xyz;
            vec3 inward = vec3(0.0);
            float ln = length(vNormal);
            if (ln > 0.1) {
                vec3 n = vNormal / ln;
                float c = dot(n, uToSun);
                if (c < 0.0) { n = -n; c = -c; }
                s += n * (3.5 * uShadowTexelCm * sqrt(max(1.0 - c * c, 0.0)));
                inward = vec3(n.x, 0.0, n.z) * 2.0;
            }
            vShadow = uLightMvp * vec4(s, 1.0);
            // Lo stesso punto (2 cm dentro la stanza, per una parete) nella mappa dei soffitti: vedi sunShadow().
            vRoofSun = uRoofMvp * vec4(s + inward, 1.0);
            gl_Position = uViewProj * w;
        }
    """

    fun fragment(header: String) = header + """
        uniform sampler2D uAlbedoTex;
        uniform sampler2D uNormalTex;
        uniform sampler2D uOrmTex;
        uniform sampler2D uEmissiveTex;
        uniform sampler2D uShadowMap;
        uniform sampler2D uAoTex;
        uniform float uUseAo;
        uniform vec2 uViewport;
        uniform float uHasAlbedo;
        uniform float uHasNormal;
        uniform float uHasOrm;
        uniform float uOrmHasAo;
        uniform float uHasEmissiveTex;
        uniform vec4 uBaseColor;
        uniform float uRoughness;
        uniform float uMetallic;
        uniform vec3 uEmissive;
        uniform float uMask;
        uniform float uDoubleSided;
        uniform float uUseVertexColor;
        uniform float uMacro;

        uniform vec3 uEye;
        uniform vec3 uToSun;
        uniform vec3 uSun;
        uniform vec3 uSky;
        uniform vec3 uGround;
        // Dentro casa (sotto un soffitto): luce diffusa rimbalzata sulle pareti invece di cielo e prato.
        uniform vec3 uInSky;
        uniform vec3 uInGround;
        uniform sampler2D uRoofMap;
        uniform mat4 uRoofMvp;
        uniform float uRoofTexel;
        // Centimetri tra le quote 0 e 1 della mappa dei soffitti.
        uniform float uRoofDepthCm;
        uniform float uShadowTexel;
        uniform float uShadowBias;
        uniform float uShadowSoft;
        uniform float uExposure;
        // Esposizione dentro casa: come una foto d'interni ben fatta, dentro e fuori si vedono bene tutti e due anche se fuori c'è
        // molta più luce.
        uniform float uExposureIn;
        uniform int uLampCount;
        uniform vec4 uLamp[$MAX_LAMPS];
        uniform vec3 uLampColor[$MAX_LAMPS];
        uniform vec4 uLampBox[${MAX_LAMPS * LightZones.MAX}];
        uniform vec4 uLampAxis[$MAX_LAMPS];
        // Finestre: rettangoli di cielo che illuminano la loro stanza, e il sole che entra (vedi LitShaders.Lights.skyWindows).
        uniform int uWinCount;
        uniform vec4 uWin[$MAX_WINDOWS];
        uniform vec4 uWinAxis[$MAX_WINDOWS];
        uniform vec4 uWinRoom[$MAX_WINDOWS];
        uniform vec4 uWinBox[${MAX_WINDOWS * LightZones.MAX}];
        // Luce del cielo che si vede da una finestra (colore × forza).
        uniform vec3 uWinSky;
        uniform mat4 uLightMvp;
        uniform vec4 uSunPatch[$MAX_WINDOWS];
        uniform vec4 uSunPatchN[$MAX_WINDOWS];
        uniform vec4 uSunGate[$MAX_WINDOWS];
        // Luce rimbalzata dalla macchia di sole (sole × colore del pavimento).
        uniform vec3 uBounce;
        // Vetri (finestre, porte, parapetti): quasi trasparenti, si vede soprattutto il riflesso. Chi guarda è dentro casa?
        uniform float uGlass;
        uniform float uEyeInside;

        varying vec3 vPos;
        varying vec3 vNormal;
        varying vec4 vColor;
        varying vec2 vUv;
        centroid varying vec4 vShadow;
        centroid varying vec4 vRoofSun;

        const float PI = 3.14159265;

        vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }

        // Rumore morbido (value noise) per le variazioni a grande scala del prato.
        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        float noise(vec2 p) {
            vec2 i = floor(p); vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
        }

        float sunShadow() {
            vec3 p = vShadow.xyz / vShadow.w * 0.5 + 0.5;
            if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
            // Bordo morbido quanto è lontano ciò che fa ombra, come la penombra vera: a contatto (gli ultimi centimetri di una
            // parete sotto il soffitto, i piedi di un mobile) quasi netto, così il sole non entra "di lato" dove non arriva.
            float blocker = p.z - uShadowBias - texture2D(uShadowMap, p.xy).r;
            float spread = blocker > 0.0 ? clamp(blocker * uShadowSoft, 0.1, 1.0) : 1.0;
            float lit = 0.0;
            // 16 campioni su un disco.
            for (int x = -2; x <= 1; x++) for (int y = -2; y <= 1; y++) {
                vec2 o = (vec2(float(x), float(y)) + 0.5) * 1.3 * spread * uShadowTexel;
                lit += (p.z - uShadowBias <= texture2D(uShadowMap, p.xy + o).r) ? 1.0 : 0.0;
            }
            lit /= 16.0;
            // Negli ultimi 2 cm sotto un soffitto il sole non arriva (entra solo da finestre e porte, più in basso): lì la mappa delle
            // ombre, a punti di quasi un centimetro, non distingue la parete dal soffitto che la tocca, e ne usciva un filo chiaro.
            vec3 q = vRoofSun.xyz / vRoofSun.w * 0.5 + 0.5;
            if (q.x > 0.0 && q.x < 1.0 && q.y > 0.0 && q.y < 1.0) {
                float below = (q.z - texture2D(uRoofMap, q.xy).r) * uRoofDepthCm;
                if (below > 0.0) lit *= smoothstep(0.5, 2.5, below);
            }
            return lit;
        }

        /**
         * Un lato del rettangolo di una finestra visto dal punto (direzioni unitarie verso i due vertici): la sua parte della luce che
         * arriva da tutto il rettangolo (legge di Lambert per un poligono, con θ/sin θ approssimato come in "Real-Time Polygonal-Light
         * Shading with Linearly Transformed Cosines", Heitz e altri, 2016).
         */
        vec3 edgeLight(vec3 a, vec3 b) {
            float x = dot(a, b);
            float y = abs(x);
            float v = (0.8543985 + (0.4965155 + 0.0145206 * y) * y) / (3.4175940 + (4.1616724 + y) * y);
            float k = x > 0.0 ? v : 0.5 * inversesqrt(max(1.0 - x * x, 1e-7)) - v;
            return cross(a, b) * k;
        }

        /** Il sole arriva nel punto `p` (niente ombra nella mappa del sole)? */
        bool sunReaches(vec3 p) {
            vec4 c = uLightMvp * vec4(p, 1.0);
            vec3 q = c.xyz / c.w * 0.5 + 0.5;
            if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0 || q.z > 1.0) return true;
            return q.z - uShadowBias <= texture2D(uShadowMap, q.xy).r;
        }

        /**
         * Quanto il punto è dentro casa (0–1): c'è un soffitto sopra di lui nella mappa dei soffitti vista
         * dall'alto. Bordo morbido su 4 campioni. Vale anche nella vista dall'alto, dove il tetto non si disegna.
         */
        float indoor() {
            vec4 c = uRoofMvp * vec4(vPos, 1.0);
            vec3 p = c.xyz / c.w * 0.5 + 0.5;
            if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) return 0.0;
            float k = 0.0;
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
                vec2 o = (vec2(float(x), float(y)) - 0.5) * uRoofTexel;
                // Anche il soffitto stesso (fino a 2 cm sopra) conta come dentro.
                k += (p.z > texture2D(uRoofMap, p.xy + o).r - 0.0002) ? 1.0 : 0.0;
            }
            return k / 4.0;
        }

        // Forma della luce di ogni tipo di lampada (generata da LightProfile).
        ${com.sagoma.planimetria.geometry.LightProfile.glslFunction()}
        /** Luce riflessa da una sorgente in direzione L con intensità `radiance` (modello di Filament/UE4). */
        vec3 shade(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, float rough, float metal) {
            float ndl = max(dot(N, L), 0.0);
            if (ndl <= 0.0) return vec3(0.0);
            vec3 H = normalize(V + L);
            float ndv = max(dot(N, V), 0.001);
            float ndh = max(dot(N, H), 0.0);
            float vdh = max(dot(V, H), 0.0);
            float a = rough * rough;
            float a2 = a * a;
            float d = ndh * ndh * (a2 - 1.0) + 1.0;
            float D = a2 / (PI * d * d);
            float k = (rough + 1.0) * (rough + 1.0) / 8.0;
            float G = (ndv / (ndv * (1.0 - k) + k)) * (ndl / (ndl * (1.0 - k) + k));
            vec3 F0 = mix(vec3(0.04), albedo, metal);
            vec3 F = F0 + (1.0 - F0) * pow(1.0 - vdh, 5.0);
            vec3 spec = D * G * F / (4.0 * ndv * ndl + 0.0001);
            vec3 diff = (1.0 - F) * (1.0 - metal) * albedo / PI;
            return (diff + spec) * radiance * ndl;
        }

        /** Solo il riflesso lucido di [shade] (per le finestre: la loro luce diffusa si calcola a parte). */
        vec3 glossy(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, float rough, float metal) {
            float ndl = max(dot(N, L), 0.0);
            if (ndl <= 0.0) return vec3(0.0);
            vec3 H = normalize(V + L);
            float ndv = max(dot(N, V), 0.001);
            float ndh = max(dot(N, H), 0.0);
            float a = rough * rough;
            float a2 = a * a;
            float d = ndh * ndh * (a2 - 1.0) + 1.0;
            float D = a2 / (PI * d * d);
            float k = (rough + 1.0) * (rough + 1.0) / 8.0;
            float G = (ndv / (ndv * (1.0 - k) + k)) * (ndl / (ndl * (1.0 - k) + k));
            vec3 F0 = mix(vec3(0.04), albedo, metal);
            vec3 F = F0 + (1.0 - F0) * pow(1.0 - max(dot(V, H), 0.0), 5.0);
            return D * G * F / (4.0 * ndv * ndl + 0.0001) * radiance * ndl;
        }

        vec3 aces(vec3 x) {
            return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
        }

        /**
         * Da luce a colore dello schermo, come una pellicola (ACES): contrasto morbido, le luci forti si comprimono invece di bruciarsi.
         * Le luci molto forti (sole che entra in casa, vicino alle lampade) tengono un po' del loro colore, come in una foto, invece di
         * diventare macchie bianche piatte.
         */
        vec3 toneMap(vec3 x) {
            vec3 a = aces(x);
            float m = max(max(x.r, x.g), x.b);
            vec3 h = x * (aces(vec3(m)).r / max(m, 1e-4));
            return mix(a, h, smoothstep(0.6, 2.5, m) * 0.45);
        }

        void main() {
            vec4 base = uBaseColor;
            if (uUseVertexColor > 0.5) base *= vec4(toLinear(vColor.rgb), vColor.a);
            if (uHasAlbedo > 0.5) {
                vec4 t = texture2D(uAlbedoTex, vUv);
                base *= vec4(toLinear(t.rgb), t.a);
            }
            if (uMacro > 0.5) {
                // Prato: chiazze più chiare/secche e più scure/verdi di qualche metro, così da lontano la foto
                // ripetuta non diventa una tinta unita.
                float n = noise(vPos.xz / 900.0) * 0.65 + noise(vPos.xz / 230.0) * 0.35;
                base.rgb *= mix(0.72, 1.18, n);
                base.rgb = mix(base.rgb, vec3(dot(base.rgb, vec3(0.3, 0.55, 0.15))) * vec3(1.05, 1.0, 0.8), 0.25 + 0.25 * n);
            }
            if (uMask > 0.5 && base.a < 0.5) discard;
            // Superfici che emettono luce (lampade della scena): il loro colore.
            if (uUseVertexColor > 0.5 && length(vNormal) < 0.1) { gl_FragColor = vColor; return; }

            vec3 N = normalize(vNormal);
            if (uDoubleSided > 0.5 && !gl_FrontFacing) N = -N;
            if (uHasNormal > 0.5) {
                // Rilievo: sistema di riferimento della texture ricavato dalle derivate (niente tangenti nei dati).
                vec3 dp1 = dFdx(vPos); vec3 dp2 = dFdy(vPos);
                vec2 du1 = dFdx(vUv); vec2 du2 = dFdy(vUv);
                vec3 dp2perp = cross(dp2, N); vec3 dp1perp = cross(N, dp1);
                vec3 T = dp2perp * du1.x + dp1perp * du2.x;
                vec3 B = dp2perp * du1.y + dp1perp * du2.y;
                float inv = inversesqrt(max(dot(T, T), dot(B, B)) + 1e-12);
                vec3 m = texture2D(uNormalTex, vUv).xyz * 2.0 - 1.0;
                vec3 bent = normalize(T * inv * m.x + B * inv * m.y + N * m.z);
                N = normalize(mix(N, bent, 0.9));
            }
            float rough = uRoughness;
            float metal = uMetallic;
            float ao = 1.0;
            if (uHasOrm > 0.5) {
                vec3 o = texture2D(uOrmTex, vUv).rgb;
                rough *= o.g;
                metal *= o.b;
                if (uOrmHasAo > 0.5) ao = o.r;
            }
            rough = clamp(rough, 0.06, 1.0);
            vec3 albedo = base.rgb;
            vec3 V = normalize(uEye - vPos);
            if (uGlass > 0.5) {
                // Ogni lastra ha due facce sovrapposte, una per lato: si disegna solo quella verso chi guarda (con due strati il vetro
                // sembrava lattiginoso). Il colore del vetro è solo una velatura.
                if (dot(N, V) < 0.0) discard;
                albedo *= 0.08;
                rough = 0.05;
            }

            vec3 color = vec3(0.0);
            // Un vetro sta sul confine: dentro o fuori a seconda di dove è chi guarda (riflette la stanza o il cielo).
            float inside = uGlass > 0.5 ? uEyeInside : indoor();
            // Occlusione ambientale: toglie luce diffusa (cielo, finestre, un poco le lampade) negli angoli.
            // (Calcolata in un passaggio a parte e sfumata, senza granulosità.)
            float ssao = uUseAo > 0.5 ? texture2D(uAoTex, gl_FragCoord.xy / uViewport).r : 1.0;
            ao *= ssao;
            float ndlSun = max(dot(N, uToSun), 0.0);
            if (ndlSun > 0.0) color += shade(N, V, uToSun, uSun, albedo, rough, metal) * sunShadow();
            for (int i = 0; i < $MAX_LAMPS; i++) {
                if (i >= uLampCount) break;
                // La lampada illumina solo la sua stanza, cioè i suoi rettangoli (niente luce attraverso i muri, né sul prato).
                bool inRoom = false;
                for (int z = 0; z < ${LightZones.MAX}; z++) {
                    vec4 box = uLampBox[i * ${LightZones.MAX} + z];
                    if (vPos.x >= box.x && vPos.x <= box.z && vPos.z >= box.y && vPos.z <= box.w) inRoom = true;
                }
                if (!inRoom) continue;
                vec3 d = uLamp[i].xyz - vPos;
                // Sotto i 50 cm la luce non cresce più: niente macchie bruciate vicino alla lampada (le lampade vere hanno un
                // diffusore, non sono un punto).
                float dist2 = max(dot(d, d), 2500.0);
                // Intensità che cala col quadrato della distanza, con un raggio di influenza di ~6 m.
                float fall = clamp(1.0 - dist2 / 360000.0, 0.0, 1.0);
                // Forma della luce della lampada (faretto, plafoniera, paralume…), come nei profili IES.
                float beam = lightBeam(uLampAxis[i].w, dot(-normalize(d), uLampAxis[i].xyz));
                vec3 rad = uLampColor[i] * uLamp[i].w * 28000.0 / dist2 * fall * fall * beam;
                color += shade(N, V, normalize(d), rad, albedo, rough, metal) * mix(1.0, ao, 0.6);
                // Luce rimbalzata su pareti, pavimento e soffitto: quasi uguale in tutta la stanza, così la luce è diffusa e
                // arriva anche dove la lampada non illumina diretta (in una stanza vera è una buona parte di quella che si vede).
                color += albedo * (1.0 - metal) * uLampColor[i] * uLamp[i].w * 0.065 / (1.0 + dist2 / 900000.0) * ao;
            }
            for (int i = 0; i < $MAX_WINDOWS; i++) {
                if (i >= uWinCount) break;
                // Solo nella stanza della finestra (né oltre i muri, né ai piani sopra e sotto).
                vec4 room = uWinRoom[i];
                if (vPos.y < room.x || vPos.y > room.y) continue;
                bool inRoom = false;
                for (int z = 0; z < ${LightZones.MAX}; z++) {
                    vec4 box = uWinBox[i * ${LightZones.MAX} + z];
                    if (vPos.x >= box.x && vPos.x <= box.z && vPos.z >= box.y && vPos.z <= box.w) inRoom = true;
                }
                if (!inRoom) continue;
                // Luce del cielo entrata e rimbalzata: uguale in tutta la stanza.
                vec3 diffuse = uWinSky * room.z;
                vec3 c = uWin[i].xyz;
                vec3 inward = uWinAxis[i].xyz;
                if (dot(vPos - c, inward) > 0.5) {
                    // Cielo visto dalla finestra: un rettangolo luminoso, quindi molta luce vicino e poca lontano, e di sbieco meno.
                    vec3 side = vec3(-inward.z, 0.0, inward.x) * (uWin[i].w * 0.5);
                    vec3 up = vec3(0.0, uWinAxis[i].w * 0.5, 0.0);
                    vec3 p0 = normalize(c - side - up - vPos);
                    vec3 p1 = normalize(c + side - up - vPos);
                    vec3 p2 = normalize(c + side + up - vPos);
                    vec3 p3 = normalize(c - side + up - vPos);
                    vec3 f = edgeLight(p0, p1) + edgeLight(p1, p2) + edgeLight(p2, p3) + edgeLight(p3, p0);
                    if (dot(f, c - vPos) < 0.0) f = -f;
                    float ff = max(dot(f, N), 0.0) / (2.0 * PI);
                    diffuse += uWinSky * ff;
                    // Riflesso della finestra sulle superfici lisce (pavimenti lucidi, vetri, metalli).
                    vec3 L = normalize(c - vPos);
                    float ndl = max(dot(N, L), 0.0);
                    if (ndl > 0.0) color += glossy(N, V, L, uWinSky * PI * ff / max(ndl, 0.05), albedo, max(rough, 0.3), metal) * ao;
                }
                color += albedo * (1.0 - metal) * diffuse * ao;
                // Sole che entra dalla finestra, se fuori c'è sole: la macchia chiara è come una lampada larga e piatta che guarda nella
                // stanza (più morbida quanto è grande), poi la sua luce si sparpaglia.
                if (uSunPatch[i].w > 0.0 && sunReaches(uSunGate[i].xyz)) {
                    vec3 d = uSunPatch[i].xyz - vPos;
                    float dist2 = dot(d, d) + uSunGate[i].w;
                    vec3 Ls = d * inversesqrt(dist2);
                    float near = uSunPatch[i].w * max(-dot(Ls, uSunPatchN[i].xyz), 0.0) * max(dot(N, Ls), 0.0) / (PI * dist2);
                    color += albedo * (1.0 - metal) / PI * uBounce * (near * mix(1.0, ao, 0.6) + uSunPatchN[i].w * ao);
                }
            }
            // Cielo e terreno: luce diffusa dall'alto e dal basso, e riflessi (più nitidi sulle superfici lisce).
            float up = N.y * 0.5 + 0.5;
            vec3 skyC = mix(uSky, uInSky, inside);
            vec3 groundC = mix(uGround, uInGround, inside);
            vec3 irr = mix(groundC, skyC, up);
            vec3 F0 = mix(vec3(0.04), albedo, metal);
            float ndv = max(dot(N, V), 0.0);
            vec3 Fr = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - ndv, 5.0);
            vec3 R = reflect(-V, N);
            vec3 env = mix(groundC, skyC, clamp(R.y * 0.5 + 0.5, 0.0, 1.0));
            color += ((1.0 - Fr) * (1.0 - metal) * albedo * irr + Fr * env * (1.0 - rough * 0.6)) * ao;

            vec3 emissive = uEmissive;
            if (uHasEmissiveTex > 0.5) emissive *= toLinear(texture2D(uEmissiveTex, vUv).rgb);
            color += emissive;

            vec3 mapped = toneMap(color * mix(uExposure, uExposureIn, inside));
            if (uGlass > 0.5) {
                // Vetro: riflette poco guardando dritto e molto di sbieco (Fresnel), il resto lascia passare quello che c'è dietro
                // (colore già moltiplicato per la copertura).
                float fres = 0.04 + 0.96 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
                gl_FragColor = vec4(pow(mapped, vec3(1.0 / 2.2)), clamp(fres + 0.08, 0.0, 1.0));
                return;
            }
            gl_FragColor = vec4(pow(mapped, vec3(1.0 / 2.2)), base.a);
        }
    """

    /**
     * Occlusione ambientale (SSAO), disegnando la scena una volta in più: per ogni punto, campioni attorno
     * alla superficie nella metà verso l'esterno; quelli che finiscono dietro ad altro (angoli, sotto i
     * mobili, pieghe) tolgono luce diffusa. Il risultato (granuloso) si sfuma poi con [blurFragment].
     */
    fun aoFragment(header: String) = header + """
        uniform sampler2D uDepthTex;
        uniform mat4 uViewProjAo;
        uniform vec2 uUvScale;
        uniform float uNear;
        uniform float uFar;
        uniform float uDoubleSided;
        varying vec3 vPos;
        varying vec3 vNormal;
        varying vec4 vColor;
        varying vec2 vUv;
        centroid varying vec4 vShadow;

        float linearDepth(float z) { float n = z * 2.0 - 1.0; return 2.0 * uNear * uFar / (uFar + uNear - n * (uFar - uNear)); }

        void main() {
            vec3 N = normalize(vNormal);
            if (uDoubleSided > 0.5 && !gl_FrontFacing) N = -N;
            vec3 P = vPos;
            vec3 t = normalize(abs(N.y) < 0.9 ? cross(N, vec3(0.0, 1.0, 0.0)) : cross(N, vec3(1.0, 0.0, 0.0)));
            vec3 b = cross(N, t);
            float occ = 0.0;
            // Rotazione diversa per ogni pixel in un motivo 4×4: la sfumatura successiva la cancella.
            vec2 cell = mod(gl_FragCoord.xy, 4.0);
            float a0 = (cell.x * 4.0 + cell.y) / 16.0 * 6.2831;
            for (int i = 0; i < 16; i++) {
                float fi = float(i);
                float ang = a0 + fi * 2.39996;
                float r = (fi + 0.5) / 16.0;
                float h = sqrt(1.0 - r * r);
                vec3 dir = t * (cos(ang) * r) + b * (sin(ang) * r) + N * h;
                float s = (fi + 1.0) / 16.0;
                float len = mix(3.0, 50.0, s * s);
                vec4 c = uViewProjAo * vec4(P + dir * len, 1.0);
                vec2 uv = c.xy / c.w * 0.5 + 0.5;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) continue;
                float sceneZ = linearDepth(texture2D(uDepthTex, uv * uUvScale).r);
                float sampleZ = c.w;
                float range = smoothstep(0.0, 1.0, len * 1.5 / max(abs(sampleZ - sceneZ), 0.001));
                occ += (sceneZ < sampleZ - 1.0 - sampleZ * 0.002 ? 1.0 : 0.0) * range;
            }
            float ao = clamp(1.0 - occ / 16.0 * 1.4, 0.0, 1.0);
            gl_FragColor = vec4(ao, ao, ao, 1.0);
        }
    """

    /** Triangolo che copre tutto lo schermo (per le sfumature). */
    fun screenVertex(header: String) = header + """
        attribute vec2 aPos;
        void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
    """

    /**
     * Alone delle luci forti (finestre di giorno viste da dentro, sole che entra, lampade), come in una foto: le parti quasi bianche
     * dell'immagine rimpicciolita, sfumate in orizzontale (`uDir` = (1/larghezza, 0), con `uThreshold` = 1 prende solo le parti
     * chiare) e poi in verticale.
     */
    fun bloomFragment(header: String) = header + """
        uniform sampler2D uSrc;
        uniform vec2 uViewport;
        uniform vec2 uDir;
        uniform float uThreshold;
        vec3 bright(vec3 c) {
            float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
            return uThreshold > 0.5 ? c * smoothstep(0.86, 1.0, l) : c;
        }
        void main() {
            vec2 uv = gl_FragCoord.xy / uViewport;
            vec3 sum = bright(texture2D(uSrc, uv).rgb) * 0.2;
            sum += (bright(texture2D(uSrc, uv + uDir * 1.5).rgb) + bright(texture2D(uSrc, uv - uDir * 1.5).rgb)) * 0.18;
            sum += (bright(texture2D(uSrc, uv + uDir * 3.5).rgb) + bright(texture2D(uSrc, uv - uDir * 3.5).rgb)) * 0.13;
            sum += (bright(texture2D(uSrc, uv + uDir * 5.5).rgb) + bright(texture2D(uSrc, uv - uDir * 5.5).rgb)) * 0.07;
            sum += (bright(texture2D(uSrc, uv + uDir * 7.5).rgb) + bright(texture2D(uSrc, uv - uDir * 7.5).rgb)) * 0.02;
            gl_FragColor = vec4(sum, 1.0);
        }
    """

    /**
     * Ultimo passaggio sull'immagine finita (con la fusione GL_ONE, GL_SRC_ALPHA): aggiunge l'alone delle luci forti e scurisce appena
     * gli angoli, come l'obiettivo di una macchina fotografica. `uBloomScale` porta un pixel dell'immagine nella texture dell'alone.
     */
    fun finishFragment(header: String) = header + """
        uniform sampler2D uBloom;
        uniform vec2 uViewport;
        uniform vec2 uBloomScale;
        uniform float uBloomStrength;
        void main() {
            vec2 uv = gl_FragCoord.xy / uViewport;
            vec2 c = (uv - 0.5) * vec2(uViewport.x / uViewport.y, 1.0);
            float vignette = 1.0 - 0.10 * smoothstep(0.35, 1.0, length(c));
            gl_FragColor = vec4(texture2D(uBloom, gl_FragCoord.xy * uBloomScale).rgb * uBloomStrength, vignette);
        }
    """

    /**
     * Sfumatura 4×4 dell'occlusione che rispetta i bordi (non passa tra oggetti a profondità diverse). `uUvMax`: fin dove arriva
     * l'immagine nella texture (oltre restano pezzi di immagini più grandi di prima).
     */
    fun blurFragment(header: String) = header + """
        uniform sampler2D uAoRaw;
        uniform sampler2D uDepthTex;
        uniform vec2 uViewport;
        uniform vec2 uUvMax;
        uniform float uNear;
        uniform float uFar;
        float linearDepth(float z) { float n = z * 2.0 - 1.0; return 2.0 * uNear * uFar / (uFar + uNear - n * (uFar - uNear)); }
        void main() {
            vec2 px = 1.0 / uViewport;
            vec2 uv = gl_FragCoord.xy * px;
            float z0 = linearDepth(texture2D(uDepthTex, uv).r);
            float sum = 0.0;
            float wsum = 0.0;
            for (int x = -2; x <= 1; x++) for (int y = -2; y <= 1; y++) {
                vec2 q = min(uv + vec2(float(x), float(y)) * px, uUvMax);
                float z = linearDepth(texture2D(uDepthTex, q).r);
                float w = 1.0 / (1.0 + abs(z - z0) / (1.0 + z0 * 0.01));
                sum += texture2D(uAoRaw, q).r * w;
                wsum += w;
            }
            float ao = sum / max(wsum, 0.0001);
            gl_FragColor = vec4(ao, ao, ao, 1.0);
        }
    """

    /**
     * Cielo dietro la casa (al posto di un colore unico): più azzurro in alto e più chiaro e velato all'orizzonte, il bagliore
     * attorno al sole, qualche nuvola leggera e, in fondo, una fila di alberi e colline lontane sopra il prato. Così dalle
     * finestre si vede un paesaggio. Si disegna dopo il pieno, solo dove non c'è altro davanti, in colori già pronti (sRGB).
     */
    fun skyVertex(header: String) = header + """
        uniform vec3 uRight;
        uniform vec3 uUp;
        uniform vec3 uForward;
        attribute vec2 aPos;
        varying vec3 vDir;
        void main() {
            // Direzione di vista di ogni angolo dello schermo (l'immagine è capovolta: in alto sullo schermo è y = -1).
            vDir = uForward + uRight * aPos.x - uUp * aPos.y;
            gl_Position = vec4(aPos, 0.9999, 1.0);
        }
    """

    fun skyFragment(header: String) = header + """
        uniform vec3 uHorizon;
        uniform vec3 uZenith;
        uniform vec3 uToSun;
        uniform vec3 uSunGlow;
        uniform vec3 uTrees;
        uniform vec3 uFarGround;
        uniform float uClouds;
        varying vec3 vDir;

        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        float noise(vec2 p) {
            vec2 i = floor(p); vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
        }
        float fbm(vec2 p) {
            float s = 0.0; float a = 0.5;
            for (int i = 0; i < 5; i++) { s += noise(p) * a; p = p * 2.03 + 17.0; a *= 0.5; }
            return s;
        }

        void main() {
            vec3 d = normalize(vDir);
            float e = d.y;
            float az = atan(d.z, d.x);
            // Cielo: dall'orizzonte velato all'azzurro più pieno in alto.
            vec3 c = mix(uHorizon, uZenith, pow(clamp(e, 0.0, 1.0), 0.55));
            // Nuvole leggere su un piano alto, più rade verso l'orizzonte.
            if (e > 0.02) {
                vec2 p = d.xz / e * 0.35;
                float n = fbm(p + vec2(3.1, 7.7));
                float k = smoothstep(0.52, 0.78, n) * smoothstep(0.02, 0.25, e) * uClouds;
                vec3 cloud = mix(uHorizon, vec3(1.0), 0.55) + uSunGlow * 0.25;
                c = mix(c, cloud, k * 0.75);
            }
            // Bagliore attorno al sole.
            float s = max(dot(d, uToSun), 0.0);
            c += uSunGlow * (pow(s, 800.0) * 1.2 + pow(s, 24.0) * 0.25 + pow(s, 4.0) * 0.08);
            // Colline e alberi lontani: una fila irregolare appena sopra l'orizzonte, velata dalla foschia.
            float hills = 0.012 + 0.018 * fbm(vec2(az * 2.2, 1.3));
            float trees = hills + 0.010 * fbm(vec2(az * 60.0, 4.1)) + 0.006 * noise(vec2(az * 260.0, 9.0));
            if (e < trees) {
                float haze = 0.35 + 0.25 * smoothstep(0.0, trees, e);
                c = mix(uTrees * (0.8 + 0.4 * noise(vec2(az * 140.0, 2.0))), uHorizon, haze);
            }
            // Sotto l'orizzonte, oltre il prato: campagna lontana, velata.
            if (e < 0.0) c = mix(uFarGround, uHorizon, 0.45 * exp(e * 40.0));
            gl_FragColor = vec4(c, 1.0);
        }
    """

    fun depthVertex(header: String) = header + """
        uniform mat4 uLightMvp;
        uniform mat4 uModel;
        attribute vec3 aPos;
        void main() { gl_Position = uLightMvp * (uModel * vec4(aPos, 1.0)); }
    """

    fun depthFragment(header: String) = header + "void main() { gl_FragColor = vec4(1.0); }\n"
}
