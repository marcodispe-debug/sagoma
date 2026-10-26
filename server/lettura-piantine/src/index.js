import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { readWithCloudflare } from "./cloudflare.js";
import { readWithGemini } from "./gemini.js";
export { Contatore } from "./contatore.js";
import { SYSTEM_PROMPT, userText } from "./prompt.js";
import { readingSchema, withoutNulls } from "./schema.js";

/**
 * Lettura delle piantine per Sagoma (Cloudflare Worker).
 *
 * L'app manda `POST /leggi` con l'immagine della piantina; qui la legge Gemini o l'AI di Cloudflare (gratis entro le quote
 * del giorno) oppure Claude. Le chiavi restano nei segreti del Worker e non arrivano mai all'app. La risposta è la lettura
 * (PlanReading in core/.../reading/PlanReading.kt) in pixel dell'immagine inviata.
 *
 * Richiesta (JSON): `image` (base64), `media_type` (image/jpeg, image/png, image/webp), `width`, `height` (pixel),
 * `device` (codice casuale del dispositivo, per il limite al giorno). Intestazione `X-Sagoma-App` se è impostata CHIAVE_APP.
 * Errori: JSON `{ error, message }` con `error` tra quelli di [ERRORI].
 *
 * Impostazioni (wrangler.toml o pannello di Cloudflare):
 * - LETTORE: chi legge, `gemini` (predefinito), `cloudflare` (binding AI) o `claude`.
 * - GEMINI_API_KEY (segreto): la chiave di Google, con LETTORE = gemini. MODELLO_GEMINI facoltativo.
 * - AI (binding Workers AI): serve con LETTORE = cloudflare. MODELLO_CLOUDFLARE facoltativo.
 * - ANTHROPIC_API_KEY (segreto): la chiave Anthropic, solo con LETTORE = claude.
 * - CHIAVE_APP (segreto, facoltativo): la stessa scritta nell'app; senza, chiunque conosca l'indirizzo può usare il server.
 * - MODELLO (facoltativo): il modello di Claude, `claude-opus-5-5` se non c'è.
 * - LIMITE_DISPOSITIVO, LIMITE_IP, LIMITE_GIORNO (facoltativi): letture al giorno per dispositivo, per indirizzo di rete e
 *   in tutto (10, 30 e 300).
 * - CONTATORE (Durable Object, in wrangler.toml): dove si contano le letture del giorno. Al suo posto va ancora il vecchio
 *   KV LIMITI. Senza nessuno dei due il server non legge, a meno di SENZA_LIMITI = "si" (solo per le prove in locale).
 */

const ERRORI = {
  non_trovato: [404, "Indirizzo sconosciuto."],
  non_autorizzato: [401, "App non riconosciuta."],
  richiesta_non_valida: [400, "Richiesta non valida."],
  immagine_troppo_grande: [413, "L'immagine è troppo grande."],
  troppe_letture: [429, "Hai già letto molte piantine oggi: riprova domani."],
  servizio_pieno: [503, "Oggi il servizio ha già letto molte piantine: riprova domani."],
  non_una_piantina: [422, "L'immagine non sembra una piantina."],
  occupato: [503, "Il servizio è occupato: riprova tra un minuto."],
  non_configurato: [503, "Il servizio non è disponibile: riprova più tardi."],
  errore_lettura: [502, "Non sono riuscito a leggere la piantina: riprova."],
};

const MEDIA_TYPES = ["image/jpeg", "image/png", "image/webp"];
/** Base64 di 5 MB (il massimo per un'immagine dell'API) più un margine. */
const MAX_IMAGE_BASE64 = 7 * 1024 * 1024;
/** Il corpo intero: l'immagine più gli altri campi. */
const MAX_BODY = MAX_IMAGE_BASE64 + 64 * 1024;
/** Lato lungo massimo che Claude guarda alla risoluzione piena: oltre, le coordinate non sarebbero più nei pixel inviati. */
const MAX_SIDE = 2576;

export default {
  async fetch(request, env) {
    const read = { claude: readWithClaude, cloudflare: readWithCloudflare }[env.LETTORE] ?? readWithGemini;
    return handle(request, env, { read: (input) => read(env, input), today: () => new Date().toISOString().slice(0, 10) });
  },
};

/** Tutta la logica del Worker; `deps.read` legge la piantina (Claude, o una finta nei test), `deps.today` dà la data. */
export async function handle(request, env, deps) {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/leggi") return error("non_trovato");
  if (env.CHIAVE_APP && request.headers.get("X-Sagoma-App") !== env.CHIAVE_APP) return error("non_autorizzato");
  // Prima di leggere il corpo: chi manda centinaia di MB si ferma qui.
  if (Number(request.headers.get("Content-Length") ?? 0) > MAX_BODY) return error("immagine_troppo_grande");

  let body;
  try {
    body = await request.json();
  } catch {
    return error("richiesta_non_valida");
  }
  const { image, media_type: mediaType, width, height, device } = body ?? {};
  if (typeof image !== "string" || !MEDIA_TYPES.includes(mediaType)) return error("richiesta_non_valida");
  if (!isSide(width) || !isSide(height)) return error("richiesta_non_valida");
  if (typeof device !== "string" || !/^[A-Za-z0-9-]{8,64}$/.test(device)) return error("richiesta_non_valida");
  if (image.length > MAX_IMAGE_BASE64) return error("immagine_troppo_grande");

  const limit = await takeReading(env, deps.today(), device, request.headers.get("CF-Connecting-IP"));
  if (limit) return error(limit);

  let result;
  try {
    result = await deps.read({ image, mediaType, width, height });
  } catch (e) {
    if (e instanceof Anthropic.RateLimitError || e instanceof Anthropic.InternalServerError) return error("occupato");
    if (e instanceof Anthropic.APIError) {
      console.error("Errore dell'API", e.status, e.message);
      return error("errore_lettura");
    }
    throw e;
  }
  if (result.kind === "not_a_plan") return error("non_una_piantina");
  if (result.kind === "quota") return error("servizio_pieno");
  if (result.kind === "busy") return error("occupato");
  if (result.kind !== "ok") {
    console.error("Lettura non riuscita", result.kind);
    return error("errore_lettura");
  }
  const { is_floor_plan: _, ...reading } = withoutNulls(result.reading);
  return Response.json({ version: 1, image: { width, height }, ...reading });
}

function isSide(v) {
  return Number.isInteger(v) && v > 0 && v <= MAX_SIDE;
}

function error(code) {
  const [status, message] = ERRORI[code];
  return Response.json({ error: code, message }, { status });
}

/**
 * Conta una lettura per il dispositivo, per l'indirizzo di rete e per il giorno; il codice dell'errore se un limite è già
 * raggiunto. Si conta prima di chiamare l'AI, così anche le letture che falliscono pesano (e chi riprova di continuo si
 * ferma). Il codice del dispositivo lo sceglie l'app, quindi chi vuole ne inventa uno nuovo a ogni richiesta: il limite per
 * indirizzo di rete ferma anche lui. L'indirizzo non si scrive mai: solo un'impronta, diversa ogni giorno e cancellata il giorno dopo.
 */
async function takeReading(env, day, device, ip) {
  const checks = [
    { key: `dispositivo:${device}`, limit: Number(env.LIMITE_DISPOSITIVO ?? 10), error: "troppe_letture" },
    ...(ip ? [{ key: `ip:${await fingerprint(day, ip)}`, limit: Number(env.LIMITE_IP ?? 30), error: "troppe_letture" }] : []),
    { key: "giorno", limit: Number(env.LIMITE_GIORNO ?? 300), error: "servizio_pieno" },
  ];
  if (env.CONTATORE) {
    const counter = env.CONTATORE.get(env.CONTATORE.idFromName("letture"));
    const res = await counter.fetch("https://contatore/conta", { method: "POST", body: JSON.stringify({ day, checks }) });
    return (await res.json()).error;
  }
  if (env.LIMITI) return takeReadingKv(env.LIMITI, day, checks);
  // Senza contatore nessun limite, e con un'AI a pagamento ogni richiesta costa: meglio non leggere.
  if (env.SENZA_LIMITI === "si") return null;
  console.error("Manca il contatore CONTATORE (o LIMITI): letture ferme");
  return "non_configurato";
}

/** Come prima, sul KV LIMITI: non è un passo unico, quindi richieste partite insieme possono superare un po' i limiti. */
async function takeReadingKv(kv, day, checks) {
  const keys = checks.map((c) => `${day}:${c.key}`);
  const counts = await Promise.all(keys.map((k) => kv.get(k)));
  for (const [i, c] of checks.entries()) if (Number(counts[i] ?? 0) >= c.limit) return c.error;
  // Due giorni di vita: il contatore di ieri sparisce da solo.
  const ttl = { expirationTtl: 2 * 24 * 3600 };
  await Promise.all(keys.map((k, i) => kv.put(k, String(Number(counts[i] ?? 0) + 1), ttl)));
  return null;
}

/** Impronta dell'indirizzo di rete, diversa ogni giorno: basta a contare. */
async function fingerprint(day, ip) {
  const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${day}|${ip}`));
  return [...new Uint8Array(hash).slice(0, 12)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Chiede a Claude la lettura: `{ kind: "ok", reading }`, `{ kind: "not_a_plan" }` o un altro `kind` se qualcosa va storto.
 * `clientOptions` solo per i test (una `fetch` finta).
 */
export async function readWithClaude(env, { image, mediaType, width, height }, clientOptions = {}) {
  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, ...clientOptions });
  const message = await client.beta.messages.parse({
    model: env.MODELLO || "claude-opus-5-5",
    max_tokens: 16000,
    // Se un controllo di sicurezza rifiuta la richiesta, l'API la ripete da sola su un altro modello.
    betas: ["server-side-fallback-2026-07-01"],
    fallbacks: "default",
    output_config: { effort: "high", format: betaZodOutputFormat(readingSchema) },
    system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
    messages: [
      {
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mediaType, data: image } },
          { type: "text", text: userText(width, height) },
        ],
      },
    ],
  });
  if (message.stop_reason === "refusal") return { kind: "refusal" };
  if (message.stop_reason === "max_tokens") return { kind: "max_tokens" };
  const reading = message.parsed_output;
  if (!reading) return { kind: "unparsed" };
  if (!reading.is_floor_plan) return { kind: "not_a_plan" };
  return { kind: "ok", reading };
}
