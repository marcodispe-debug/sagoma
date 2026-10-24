import { test } from "node:test";
import assert from "node:assert/strict";
import { DEFAULT_MODEL, readWithGemini } from "../src/gemini.js";

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
  assert.equal(sent.body.generationConfig.responseMimeType, "application/json");
  assert.equal(sent.body.generationConfig.responseJsonSchema.type, "object");
});

test("non una piantina, tagliata, storta, limiti del piano gratuito", async () => {
  const with_ = (res) => async () => res;
  assert.equal((await readWithGemini({}, input, with_(reply(JSON.stringify({ ...empty, is_floor_plan: false }))))).kind, "not_a_plan");
  assert.equal((await readWithGemini({}, input, with_(reply("{", "MAX_TOKENS")))).kind, "max_tokens");
  assert.equal((await readWithGemini({}, input, with_(reply("boh")))).kind, "unparsed");
  const day = Response.json({ error: { code: 429, message: "Quota exceeded for metric generate_content_free_tier_requests, limit: GenerateRequestsPerDayPerProjectPerModel-FreeTier" } }, { status: 429 });
  assert.equal((await readWithGemini({}, input, with_(day))).kind, "quota");
  const minute = Response.json({ error: { code: 429, message: "GenerateRequestsPerMinutePerProjectPerModel-FreeTier" } }, { status: 429 });
  assert.equal((await readWithGemini({}, input, with_(minute))).kind, "busy");
  assert.equal((await readWithGemini({}, input, with_(Response.json({ error: {} }, { status: 400 })))).kind, "ai_error");
  assert.equal((await readWithGemini({}, input, async () => { throw new TypeError("rete"); })).kind, "busy");
});
