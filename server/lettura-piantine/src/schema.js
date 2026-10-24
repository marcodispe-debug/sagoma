import * as z from "zod/v4";

/**
 * Forma della risposta di Claude: la stessa lettura che l'app conosce (core/.../reading/PlanReading.kt), in pixel
 * dell'immagine inviata. Con le uscite strutturate ogni campo deve esserci: quelli che sulla piantina mancano sono `null`
 * o liste vuote, e prima di rispondere all'app i `null` si tolgono (l'app usa i suoi valori predefiniti).
 */
const point = z.array(z.number()).describe("[x, y] in pixel, origine in alto a sinistra, y verso il basso");

const opening = z.object({
  kind: z.enum(["door", "double_door", "window", "double_window", "balcony", "double_balcony", "passage"]),
  ends: z.array(point).describe("I due stipiti, da qualunque lato dello spessore del muro"),
  width_cm: z.number().nullable().describe("Larghezza scritta sulla piantina, altrimenti null"),
  height_cm: z.number().nullable().describe("Altezza scritta, altrimenti null"),
  sill_cm: z.number().nullable().describe("Altezza del davanzale scritta (finestre), altrimenti null"),
  entrance: z.boolean().describe("Porta d'ingresso di casa"),
  hinge: point.nullable().describe("Anta singola: lo stipite con i cardini (uno dei due ends); se scorrevole, quello verso cui scorre"),
  opens_toward: point.nullable().describe("Un punto dalla parte verso cui si aprono le ante, per esempio dentro l'arco disegnato"),
  sliding: z.boolean().describe("Ante scorrevoli: parallele al muro, senza arco"),
});

export const readingSchema = z.object({
  is_floor_plan: z.boolean().describe("false se l'immagine non è una piantina di un'abitazione o di un locale"),
  rooms: z.array(
    z.object({
      name: z.string().describe("Nome come scritto sulla piantina (in italiano se è italiana), altrimenti un nome adatto"),
      corners: z.array(point).describe("Angoli sul filo interno dei muri, in ordine lungo il contorno"),
      sides_m: z.array(z.number()).describe("Misure dei lati scritte nella stanza, in metri (di solito larghezza e lunghezza); vuota se non scritte"),
      area_m2: z.number().nullable().describe("Metri quadri scritti, altrimenti null"),
      excluded: z.array(z.array(point)).describe("Zone non calpestabili disegnate (cavedi, fasce sotto il tetto basso)"),
    }),
  ),
  openings: z.array(opening),
  stairs: z.array(
    z.object({
      from: point.describe("Centro del bordo del primo gradino in basso"),
      to: point.describe("Centro dell'arrivo in alto (la freccia va da from a to)"),
      width_cm: z.number().nullable(),
      width_px: z.number().nullable(),
      treads: z.number().nullable().describe("Pedate contate sul disegno"),
    }),
  ),
  walls: z.array(
    z.object({
      from: point.describe("Estremo della mezzeria"),
      to: point.describe("Altro estremo della mezzeria"),
      thickness_px: z.number().nullable(),
      openings: z.array(opening),
    }),
  ).describe("Muri singoli che non chiudono una stanza: muretti, penisola della cucina, fianchi degli armadi a muro"),
  exterior_wall_px: z.number().nullable().describe("Spessore dei muri esterni in pixel"),
  exterior_outline: z.array(point).describe("Filo esterno dell'edificio, in ordine; vuoto se non si vede"),
  units: z.enum(["m", "cm"]).describe("Come sono scritte le misure sulla piantina: 5.03 (m) o 503 (cm)"),
  notes: z.string().describe("Cosa hai notato: misure dubbie, parti illeggibili. In italiano, breve"),
});

/** Toglie i `null` (anche dentro le liste di oggetti), così l'app usa i suoi valori predefiniti. */
export function withoutNulls(value) {
  if (Array.isArray(value)) return value.map(withoutNulls);
  if (value && typeof value === "object") {
    const out = {};
    for (const [k, v] of Object.entries(value)) if (v !== null) out[k] = withoutNulls(v);
    return out;
  }
  return value;
}
