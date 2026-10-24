import { test } from "node:test";
import assert from "node:assert/strict";
import { readWithClaude } from "../src/index.js";

/** Quello che parte verso l'API (con una fetch finta) e come si legge la risposta. */
test("richiesta all'API e risposta strutturata", async () => {
  let sent;
  const answer = { is_floor_plan: true, rooms: [], openings: [], stairs: [], walls: [], exterior_wall_px: null, exterior_outline: [], units: "cm", notes: "" };
  const fetch = async (url, init) => {
    sent = { url: String(url), headers: new Headers(init.headers), body: JSON.parse(init.body) };
    return Response.json({
      id: "msg_1", type: "message", role: "assistant", model: "claude-opus-5-5", stop_reason: "end_turn", stop_sequence: null,
      content: [{ type: "text", text: JSON.stringify(answer) }],
      usage: { input_tokens: 10, output_tokens: 10 },
    });
  };
  const r = await readWithClaude({ ANTHROPIC_API_KEY: "k" }, { image: "AAAA", mediaType: "image/png", width: 900, height: 700 }, { fetch, maxRetries: 0 });
  assert.equal(r.kind, "ok");
  assert.equal(new URL(sent.url).pathname, "/v1/messages");
  assert.match(sent.headers.get("anthropic-beta"), /server-side-fallback-2026-07-01/);
  assert.equal(sent.body.model, "claude-opus-5-5");
  assert.equal(sent.body.fallbacks, "default");
  assert.equal(sent.body.output_config.effort, "high");
  assert.equal(sent.body.output_config.format.type, "json_schema");
  assert.equal(sent.body.messages[0].content[0].source.media_type, "image/png");
  assert.match(sent.body.messages[0].content[1].text, /900 pixel/);
});

test("non una piantina", async () => {
  const fetch = async () => Response.json({
    id: "msg_2", type: "message", role: "assistant", model: "m", stop_reason: "end_turn", stop_sequence: null,
    content: [{ type: "text", text: JSON.stringify({ is_floor_plan: false, rooms: [], openings: [], stairs: [], walls: [], exterior_wall_px: null, exterior_outline: [], units: "cm", notes: "" }) }],
    usage: { input_tokens: 1, output_tokens: 1 },
  });
  const r = await readWithClaude({ ANTHROPIC_API_KEY: "k", MODELLO: "claude-sonnet-5-5" }, { image: "AAAA", mediaType: "image/jpeg", width: 10, height: 10 }, { fetch, maxRetries: 0 });
  assert.equal(r.kind, "not_a_plan");
});
