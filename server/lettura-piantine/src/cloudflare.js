import * as z from "zod/v4";
import { SYSTEM_PROMPT, userText } from "./prompt.js";
import { readingSchema } from "./schema.js";

/** Modello di Cloudflare Workers AI che guarda le immagini, se MODELLO_CLOUDFLARE non dice altro. */
export const DEFAULT_MODEL = "@cf/qwen/qwen3.8-27b";

const jsonSchema = (() => {
  const { $schema: _, ...schema } = z.toJSONSchema(readingSchema);
  return schema;
})();

/**
 * Chiede la lettura all'AI di Cloudflare (binding AI), gratis entro la quota del giorno dell'account. Stessi risultati di
 * readWithClaude, più `{ kind: "quota" }` quando la quota del giorno è finita.
 */
export async function readWithCloudflare(env, { image, mediaType, width, height }) {
  let answer;
  try {
    answer = await env.AI.run(env.MODELLO_CLOUDFLARE || DEFAULT_MODEL, {
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        {
          role: "user",
          content: [
            { type: "image_url", image_url: { url: `data:${mediaType};base64,${image}`, detail: "high" } },
            { type: "text", text: userText(width, height) },
          ],
        },
      ],
      response_format: { type: "json_schema", json_schema: { name: "lettura", schema: jsonSchema, strict: true } },
      max_completion_tokens: 8000,
      // Senza ragionamento: con il ragionamento la risposta supera i 120 secondi che Workers AI concede. Senza `stream: false`
      // la richiesta scade lo stesso.
      reasoning_effort: "low",
      chat_template_kwargs: { enable_thinking: false },
      stream: false,
    });
  } catch (e) {
    // 4006: finita la quota gratuita del giorno (si azzera a mezzanotte UTC).
    if (/4006|daily free allocation|neurons/i.test(String(e?.message ?? e))) return { kind: "quota" };
    console.error("Errore dell'AI di Cloudflare", String(e?.message ?? e));
    return { kind: "ai_error" };
  }
  const choice = answer?.choices?.[0];
  if (choice?.finish_reason === "length") return { kind: "max_tokens" };
  const parsed = readingSchema.safeParse(parseJson(choice?.message?.content));
  if (!parsed.success) {
    console.error("Lettura non valida", String(choice?.message?.content).slice(0, 500));
    return { kind: "unparsed" };
  }
  if (!parsed.data.is_floor_plan) return { kind: "not_a_plan" };
  return { kind: "ok", reading: parsed.data };
}

/** Il JSON della risposta, anche se il modello lo chiude tra ``` o ci mette parole intorno; `null` se non c'è. */
function parseJson(text) {
  if (typeof text !== "string") return text ?? null;
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
}
