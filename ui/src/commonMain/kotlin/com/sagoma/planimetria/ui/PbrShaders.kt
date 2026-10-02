package com.sagoma.planimetria.ui

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
        attribute vec3 aPos;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        attribute vec2 aUv;
        varying vec3 vPos;
        varying vec3 vNormal;
        varying vec4 vColor;
        varying vec2 vUv;
        varying vec4 vShadow;
        void main() {
            vec4 w = uModel * vec4(aPos, 1.0);
            vPos = w.xyz;
            vNormal = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * aNormal;
            vColor = aColor;
            vUv = aUv;
            vShadow = uLightMvp * w;
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
        uniform float uShadowTexel;
        uniform float uExposure;
        uniform int uLampCount;
        uniform vec4 uLamp[$MAX_LAMPS];
        uniform vec3 uLampColor[$MAX_LAMPS];
        uniform vec4 uLampBox[$MAX_LAMPS];
        uniform int uWinCount;
        uniform vec4 uWin[$MAX_WINDOWS];
        uniform vec4 uWinDir[$MAX_WINDOWS];
        uniform vec3 uWinColor;

        varying vec3 vPos;
        varying vec3 vNormal;
        varying vec4 vColor;
        varying vec2 vUv;
        varying vec4 vShadow;

        const float PI = 3.14159265;

        vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }

        // Rumore morbido (value noise) per le variazioni a grande scala del prato.
        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        float noise(vec2 p) {
            vec2 i = floor(p); vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
        }

        float sunShadow(float ndl) {
            vec3 p = vShadow.xyz / vShadow.w * 0.5 + 0.5;
            if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
            float bias = 0.0008 + 0.003 * (1.0 - ndl);
            float lit = 0.0;
            // Bordo morbido: 16 campioni su un disco.
            for (int x = -2; x <= 1; x++) for (int y = -2; y <= 1; y++) {
                vec2 o = (vec2(float(x), float(y)) + 0.5) * 1.3 * uShadowTexel;
                lit += (p.z - bias <= texture2D(uShadowMap, p.xy + o).r) ? 1.0 : 0.0;
            }
            return lit / 16.0;
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

        vec3 aces(vec3 x) {
            return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
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

            vec3 color = vec3(0.0);
            float inside = indoor();
            // Occlusione ambientale: toglie luce diffusa (cielo, finestre, un poco le lampade) negli angoli.
            // (Calcolata in un passaggio a parte e sfumata, senza granulosità.)
            float ssao = uUseAo > 0.5 ? texture2D(uAoTex, gl_FragCoord.xy / uViewport).r : 1.0;
            ao *= ssao;
            float ndlSun = max(dot(N, uToSun), 0.0);
            if (ndlSun > 0.0) color += shade(N, V, uToSun, uSun, albedo, rough, metal) * sunShadow(ndlSun);
            for (int i = 0; i < $MAX_LAMPS; i++) {
                if (i >= uLampCount) break;
                // La lampada illumina solo la sua stanza (niente luce attraverso i muri, né sul prato).
                vec4 box = uLampBox[i];
                if (vPos.x < box.x || vPos.x > box.z || vPos.z < box.y || vPos.z > box.w) continue;
                vec3 d = uLamp[i].xyz - vPos;
                float dist2 = max(dot(d, d), 400.0);
                // Intensità che cala col quadrato della distanza, con un raggio di influenza di ~6 m.
                float fall = clamp(1.0 - dist2 / 360000.0, 0.0, 1.0);
                vec3 rad = uLampColor[i] * uLamp[i].w * 40000.0 / dist2 * fall * fall;
                color += shade(N, V, normalize(d), rad, albedo, rough, metal) * mix(1.0, ao, 0.6);
                // Luce rimbalzata su pareti e soffitto: schiarisce un poco tutta la stanza, anche dove la lampada non arriva diretta.
                color += albedo * (1.0 - metal) * uLampColor[i] * uLamp[i].w * 0.045 / (1.0 + dist2 / 250000.0) * ao;
            }
            for (int i = 0; i < $MAX_WINDOWS; i++) {
                if (i >= uWinCount) break;
                vec3 d = uWin[i].xyz - vPos;
                float dist2 = max(dot(d, d), 900.0);
                vec3 inward = normalize(vec3(uWinDir[i].x, 0.0, uWinDir[i].z));
                vec3 toPoint = normalize(vec3(-d.x, 0.0, -d.z) + vec3(0.0001, 0.0, 0.0));
                float front = clamp(dot(toPoint, inward) * 1.2 + 0.55, 0.0, 1.0);
                // Solo dentro casa: la luce della finestra non esce sul prato.
                vec3 rad = uWinColor * uWinDir[i].w * front * 60000.0 / (dist2 + 30000.0) * inside;
                // Luce morbida (cielo che entra): anche le superfici di lato la ricevono un poco.
                vec3 L = normalize(d);
                color += (shade(N, V, L, rad, albedo, max(rough, 0.5), metal) + albedo * (1.0 - metal) * rad * 0.08) * ao;
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

            vec3 mapped = aces(color * uExposure);
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
        uniform float uNear;
        uniform float uFar;
        uniform float uDoubleSided;
        varying vec3 vPos;
        varying vec3 vNormal;
        varying vec4 vColor;
        varying vec2 vUv;
        varying vec4 vShadow;

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
                float sceneZ = linearDepth(texture2D(uDepthTex, uv).r);
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

    /** Sfumatura 4×4 dell'occlusione che rispetta i bordi (non passa tra oggetti a profondità diverse). */
    fun blurFragment(header: String) = header + """
        uniform sampler2D uAoRaw;
        uniform sampler2D uDepthTex;
        uniform vec2 uViewport;
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
                vec2 o = vec2(float(x), float(y)) * px;
                float z = linearDepth(texture2D(uDepthTex, uv + o).r);
                float w = 1.0 / (1.0 + abs(z - z0) / (1.0 + z0 * 0.01));
                sum += texture2D(uAoRaw, uv + o).r * w;
                wsum += w;
            }
            float ao = sum / max(wsum, 0.0001);
            gl_FragColor = vec4(ao, ao, ao, 1.0);
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
