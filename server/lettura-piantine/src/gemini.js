import { SYSTEM_PROMPT, userText } from "./prompt.js";
import { findIssues, reviewText } from "./review.js";
import { parseJson, readingJsonSchema, readingSchema } from "./schema.js";

/** Modello di Gemini, se MODELLO_GEMINI non dice altro: ha il piano gratuito e guarda le immagini. */
export const DEFAULT_MODEL = "gemini-3.8-flash";
/**
 * Modelli di riserva, in ordine, quando quello principale è sovraccarico, senza quota o non più offerto (MODELLO_GEMINI_RISERVA,
 * separati da virgole; vuoto per non usarli). Tutti con il piano gratuito.
 */
export const BACKUP_MODELS = "gemini-3.6-flash,gemini-3.5-flash-lite";

/**
 * Chiede la lettura a Gemini di Google con la chiave GEMINI_API_KEY (segreto del Worker). Stessi risultati di readWithClaude,
 * più `{ kind: "quota" }` quando il piano gratuito ha finito le richieste del giorno e `{ kind: "busy" }` quando Google è
 * sovraccarico o ha finito quelle del minuto: in questi casi, e se il modello non c'è più, si passa ai modelli di riserva.
 *
 * Con CONTROLLO_GEMINI = "si", se nella lettura ci sono stanze sovrapposte o metri quadri che non tornano, gliela si rimanda
 * con i problemi trovati e si tiene la correzione se non è peggio. Spento se non detto: con i modelli gratuiti, quasi sempre il
 * più debole perché gli altri sono pieni, la correzione spesso sposta tutta la pianta. RAGIONAMENTO_GEMINI (per esempio `high`)
 * chiede a Gemini quanto ragionare; senza, decide il modello. `fetchFn` solo per i test.
 */
export async function readWithGemini(env, input, fetchFn = fetch) {
  const first = await ask(env, input, [], fetchFn);
  if (first.kind !== "ok") return first;
  let reading = first.reading;
  const issues = env.CONTROLLO_GEMINI === "si" ? findIssues(reading) : [];
  if (issues.length) {
    const second = await ask(env, input, [reviewText(reading, issues)], fetchFn);
    const left = second.kind === "ok" ? findIssues(second.reading) : null;
    console.log("Secondo giro:", issues.length, "problemi, dopo", left?.length ?? second.kind);
    if (left && left.length <= issues.length) reading = second.reading;
  }
  return { kind: "ok", reading: fromThousandths(reading, input.width, input.height) };
}

/** Una lettura (in millesimi) dal primo modello che risponde, tra il principale e le riserve. */
async function ask(env, input, extra, fetchFn) {
  const backups = (env.MODELLO_GEMINI_RISERVA ?? BACKUP_MODELS).split(",").map((m) => m.trim()).filter(Boolean);
  let result;
  for (const model of [env.MODELLO_GEMINI || DEFAULT_MODEL, ...backups]) {
    result = await readWith(model, env, input, extra, env.RAGIONAMENTO_GEMINI || null, fetchFn);
    // Un modello che non accetta quel livello di ragionamento: lo stesso modello con il suo livello predefinito.
    if (result.kind === "no_thinking") result = await readWith(model, env, input, extra, null, fetchFn);
    // Le quote del piano gratuito sono per modello: anche a quota finita la riserva può rispondere.
    if (!["busy", "quota", "missing"].includes(result.kind)) return result;
  }
  return result.kind === "missing" ? { kind: "ai_error" } : result;
}

async function readWith(model, env, { image, mediaType, width, height }, extra, thinking, fetchFn) {
  let response;
  try {
    response = await fetchFn(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY ?? "" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [
          {
            role: "user",
            parts: [{ inline_data: { mime_type: mediaType, data: image } }, { text: geminiText(width, height) }, ...extra.map((text) => ({ text }))],
          },
        ],
        generationConfig: {
          responseMimeType: "application/json",
          responseJsonSchema: readingJsonSchema,
          maxOutputTokens: 32000,
          ...(thinking ? { thinkingConfig: { thinkingLevel: thinking } } : {}),
        },
      }),
    });
  } catch (e) {
    console.error("Gemini non raggiungibile", model, String(e?.message ?? e));
    return { kind: "busy" };
  }
  if (!response.ok) {
    const text = await response.text();
    console.error("Errore di Gemini", model, response.status, text.slice(0, 500));
    // 429: finite le richieste del piano gratuito (del giorno, o del minuto se si riprova subito).
    if (response.status === 429) return { kind: /per ?day|daily|PerDay/i.test(text) ? "quota" : "busy" };
    if (response.status >= 500) return { kind: "busy" };
    if (response.status === 404) return { kind: "missing" };
    if (response.status === 400 && thinking && /thinking/i.test(text)) return { kind: "no_thinking" };
    return { kind: "ai_error" };
  }
  const answer = await response.json();
  const candidate = answer?.candidates?.[0];
  console.log("Gemini uso", model, JSON.stringify(answer?.usageMetadata), "fine", candidate?.finishReason);
  if (!candidate) return { kind: "refusal" };
  if (candidate.finishReason === "MAX_TOKENS") return { kind: "max_tokens" };
  const text = (candidate.content?.parts ?? []).filter((p) => !p.thought && typeof p.text === "string").map((p) => p.text).join("");
  const parsed = readingSchema.safeParse(parseJson(text));
  if (!parsed.success) {
    console.error("Lettura non valida", text.slice(0, 500));
    return { kind: "unparsed" };
  }
  if (!parsed.data.is_floor_plan) return { kind: "not_a_plan" };
  return { kind: "ok", reading: parsed.data };
}

/**
 * Gemini misura le immagini in millesimi (0-1000 sui due lati, qualunque sia la loro misura): glielo si chiede apertamente e
 * le coordinate si riportano poi nei pixel dell'immagine inviata.
 */
function geminiText(width, height) {
  return `${userText(width, height)}
Attenzione: scrivi invece TUTTE le coordinate [x, y] in millesimi dell'immagine, da 0 a 1000 su entrambi i lati (0,0 in alto a sinistra, 1000,1000 in basso a destra), e anche gli spessori e le larghezze in pixel (thickness_px, exterior_wall_px, width_px) in millesimi del lato.`;
}

/** Da millesimi a pixel. Se qualche coordinata supera 1000 il modello ha già scritto pixel, e la lettura resta com'è. */
export function fromThousandths(reading, width, height) {
  const points = [
    ...reading.rooms.flatMap((r) => [...r.corners, ...r.excluded.flat()]),
    ...reading.openings.flatMap((o) => o.ends),
    ...reading.stairs.flatMap((s) => [s.from, s.to]),
    ...reading.walls.flatMap((w) => [w.from, w.to]),
    ...reading.exterior_outline,
  ];
  if (points.some(([x, y]) => x > 1000 || y > 1000)) return reading;
  const point = (p) => (p ? [(p[0] * width) / 1000, (p[1] * height) / 1000] : p);
  const length = (v) => (v == null ? v : (v * Math.sqrt(width * height)) / 1000);
  const opening = (o) => ({ ...o, ends: o.ends.map(point), hinge: point(o.hinge), opens_toward: point(o.opens_toward) });
  return {
    ...reading,
    rooms: reading.rooms.map((r) => ({ ...r, corners: r.corners.map(point), excluded: r.excluded.map((z) => z.map(point)) })),
    openings: reading.openings.map(opening),
    stairs: reading.stairs.map((s) => ({ ...s, from: point(s.from), to: point(s.to), width_px: length(s.width_px) })),
    walls: reading.walls.map((w) => ({ ...w, from: point(w.from), to: point(w.to), thickness_px: length(w.thickness_px), openings: w.openings.map(opening) })),
    exterior_wall_px: length(reading.exterior_wall_px),
    exterior_outline: reading.exterior_outline.map(point),
  };
}
