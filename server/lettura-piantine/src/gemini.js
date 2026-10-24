import { SYSTEM_PROMPT, userText } from "./prompt.js";
import { parseJson, readingJsonSchema, readingSchema } from "./schema.js";

/** Modello di Gemini, se MODELLO_GEMINI non dice altro: ha il piano gratuito e guarda le immagini. */
export const DEFAULT_MODEL = "gemini-3.8-flash";

/**
 * Chiede la lettura a Gemini di Google con la chiave GEMINI_API_KEY (segreto del Worker). Stessi risultati di readWithClaude,
 * più `{ kind: "quota" }` quando il piano gratuito ha finito le richieste del giorno e `{ kind: "busy" }` per quelle del minuto.
 * `fetchFn` solo per i test.
 */
export async function readWithGemini(env, { image, mediaType, width, height }, fetchFn = fetch) {
  const model = env.MODELLO_GEMINI || DEFAULT_MODEL;
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
            parts: [{ inline_data: { mime_type: mediaType, data: image } }, { text: userText(width, height) }],
          },
        ],
        generationConfig: { responseMimeType: "application/json", responseJsonSchema: readingJsonSchema, maxOutputTokens: 32000 },
      }),
    });
  } catch (e) {
    console.error("Gemini non raggiungibile", String(e?.message ?? e));
    return { kind: "busy" };
  }
  if (!response.ok) {
    const text = await response.text();
    console.error("Errore di Gemini", response.status, text.slice(0, 500));
    // 429: finite le richieste del piano gratuito (del giorno, o del minuto se si riprova subito).
    if (response.status === 429) return { kind: /per ?day|daily|PerDay/i.test(text) ? "quota" : "busy" };
    if (response.status >= 500) return { kind: "busy" };
    return { kind: "ai_error" };
  }
  const answer = await response.json();
  const candidate = answer?.candidates?.[0];
  console.log("Gemini uso", JSON.stringify(answer?.usageMetadata), "fine", candidate?.finishReason);
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
