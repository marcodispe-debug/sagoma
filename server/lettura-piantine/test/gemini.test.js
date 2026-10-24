import { test } from "node:test";
import assert from "node:assert/strict";
import { BACKUP_MODEL, DEFAULT_MODEL, fromThousandths, readWithGemini } from "../src/gemini.js";

const empty = { is_floor_plan: true, rooms: [], openings: [], stairs: [], walls: [], exterior_wall_px: null, exterior_outline: [], units: "cm", notes: "" };
const input = { image: "AAAA", mediaType: "image/png", width: 900, height: 700 };
const reply = (text, finishReason = "STOP") => Response.json({ candidates: [{ content: { role: "model", parts: [{ text }] }, finishReason }] });

/** Quello che parte verso Gemini e come si legge la risposta. */
test("richiesta a Gemini e risposta", async () => {
  let sent;
  const fetch = async (url, init) => {
    sent = { url, headers: new Headers(init.headers), body: JSON.parse(init.body) };
    return reply(JSON.stringify(empty));
  };
  const r = await readWithGemini({ GEMINI_API_KEY: "k" }, input, fetch);
  assert.equal(r.kind, "ok");
  assert.equal(sent.url, `https://generativelanguage.googleapis.com/v1beta/models/${DEFAULT_MODEL}:generateContent`);
  assert.equal(sent.headers.get("x-goog-api-key"), "k");
  assert.deepEqual(sent.body.contents[0].parts[0], { inline_data: { mime_type: "image/png", data: "AAAA" } });
  assert.match(sent.body.contents[0].parts[1].text, /900 pixel/);
  assert.match(sent.body.contents[0].parts[1].text, /millesimi/);
  assert.equal(sent.body.generationConfig.responseMimeType, "application/json");
  assert.equal(sent.body.generationConfig.responseJsonSchema.type, "object");
});

test("non una piantina, tagliata, storta, limiti del piano gratuito", async () => {
  const with_ = (res) => async () => res.clone();
  assert.equal((await readWithGemini({}, input, with_(reply(JSON.stringify({ ...empty, is_floor_plan: false }))))).kind, "not_a_plan");
  assert.equal((await readWithGemini({}, input, with_(reply("{", "MAX_TOKENS")))).kind, "max_tokens");
  assert.equal((await readWithGemini({}, input, with_(reply("boh")))).kind, "unparsed");
  const day = Response.json({ error: { code: 429, message: "Quota exceeded for metric generate_content_free_tier_requests, limit: GenerateRequestsPerDayPerProjectPerModel-FreeTier" } }, { status: 429 });
  assert.equal((await readWithGemini({ MODELLO_GEMINI_RISERVA: "" }, input, with_(day))).kind, "quota");
  const minute = Response.json({ error: { code: 429, message: "GenerateRequestsPerMinutePerProjectPerModel-FreeTier" } }, { status: 429 });
  assert.equal((await readWithGemini({ MODELLO_GEMINI_RISERVA: "" }, input, with_(minute))).kind, "busy");
  assert.equal((await readWithGemini({}, input, with_(Response.json({ error: {} }, { status: 400 })))).kind, "ai_error");
  assert.equal((await readWithGemini({}, input, async () => { throw new TypeError("rete"); })).kind, "busy");
});

test("Google sovraccarico: si riprova una volta con il modello di riserva", async () => {
  const urls = [];
  const busyThenOk = async (url) => {
    urls.push(url);
    return urls.length === 1 ? Response.json({ error: { code: 503, status: "UNAVAILABLE" } }, { status: 503 }) : reply(JSON.stringify(empty));
  };
  assert.equal((await readWithGemini({}, input, busyThenOk)).kind, "ok");
  assert.deepEqual(urls.map((u) => u.split("/models/")[1]), [`${DEFAULT_MODEL}:generateContent`, `${BACKUP_MODEL}:generateContent`]);
  let calls = 0;
  const busy = async () => { calls++; return Response.json({ error: {} }, { status: 503 }); };
  assert.equal((await readWithGemini({ MODELLO_GEMINI_RISERVA: "" }, input, busy)).kind, "busy");
  assert.equal(calls, 1);
});

test("coordinate in millesimi riportate nei pixel dell'immagine", () => {
  const door = { kind: "door", ends: [[500, 500], [600, 500]], width_cm: null, height_cm: null, sill_cm: null, entrance: false, hinge: [500, 500], opens_toward: null, sliding: false };
  const reading = {
    ...empty,
    rooms: [{ name: "Cucina", corners: [[0, 0], [1000, 0], [1000, 500], [0, 500]], sides_m: [], area_m2: null, excluded: [[[100, 100], [200, 200]]] }],
    openings: [door],
    stairs: [{ from: [10, 20], to: [10, 80], width_cm: null, width_px: 10, treads: null }],
    walls: [{ from: [0, 0], to: [0, 1000], thickness_px: 10, openings: [door] }],
    exterior_wall_px: 20,
    exterior_outline: [[0, 0], [1000, 1000]],
  };
  const r = fromThousandths(reading, 2000, 500);
  assert.deepEqual(r.rooms[0].corners, [[0, 0], [2000, 0], [2000, 250], [0, 250]]);
  assert.deepEqual(r.rooms[0].excluded, [[[200, 50], [400, 100]]]);
  assert.deepEqual(r.openings[0].ends, [[1000, 250], [1200, 250]]);
  assert.deepEqual(r.openings[0].hinge, [1000, 250]);
  assert.equal(r.openings[0].opens_toward, null);
  assert.deepEqual(r.walls[0].openings[0].ends, [[1000, 250], [1200, 250]]);
  assert.deepEqual(r.stairs[0].to, [20, 40]);
  assert.equal(r.stairs[0].width_px, 10);
  assert.equal(r.walls[0].thickness_px, 10);
  assert.equal(r.exterior_wall_px, 20);
  assert.deepEqual(r.exterior_outline[1], [2000, 500]);
  // Già in pixel (oltre 1000): resta com'è.
  const pixels = { ...reading, exterior_outline: [[0, 0], [1800, 450]] };
  assert.equal(fromThousandths(pixels, 2000, 500), pixels);
});
