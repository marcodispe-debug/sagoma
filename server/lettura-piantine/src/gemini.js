import { SYSTEM_PROMPT, userText } from "./prompt.js";
import { parseJson, readingJsonSchema, readingSchema } from "./schema.js";

/** Modello di Gemini, se MODELLO_GEMINI non dice altro: ha il piano gratuito e guarda le immagini. */
export const DEFAULT_MODEL = "gemini-3.8-flash";
/** Modello di riserva quando quello principale è sovraccarico o senza quota (MODELLO_GEMINI_RISERVA; vuoto per non usarlo). */
export const BACKUP_MODEL = "gemini-2.5-flash";

/**
 * Chiede la lettura a Gemini di Google con la chiave GEMINI_API_KEY (segreto del Worker). Stessi risultati di readWithClaude,
 * più `{ kind: "quota" }` quando il piano gratuito ha finito le richieste del giorno e `{ kind: "busy" }` quando Google è
 * sovraccarico o ha finito quelle del minuto: in entrambi i casi si riprova una volta con il modello di riserva. `fetchFn` solo
 * per i test.
 */
export async function readWithGemini(env, input, fetchFn = fetch) {
  const backup = env.MODELLO_GEMINI_RISERVA ?? BACKUP_MODEL;
  const first = await readWith(env.MODELLO_GEMINI || DEFAULT_MODEL, env, input, fetchFn);
  // Le quote del piano gratuito sono per modello: anche a quota finita la riserva può rispondere.
  if (!["busy", "quota"].includes(first.kind) || !backup) return first;
  return readWith(backup, env, input, fetchFn);
}

async function readWith(model, env, { image, mediaType, width, height }, fetchFn) {
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
            parts: [{ inline_data: { mime_type: mediaType, data: image } }, { text: geminiText(width, height) }],
          },
        ],
        generationConfig: { responseMimeType: "application/json", responseJsonSchema: readingJsonSchema, maxOutputTokens: 32000 },
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
  return { kind: "ok", reading: fromThousandths(parsed.data, width, height) };
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
