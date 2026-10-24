import { test } from "node:test";
import assert from "node:assert/strict";
import { DEFAULT_MODEL, readWithCloudflare } from "../src/cloudflare.js";

const empty = { is_floor_plan: true, rooms: [], openings: [], stairs: [], walls: [], exterior_wall_px: null, exterior_outline: [], units: "cm", notes: "" };
const input = { image: "AAAA", mediaType: "image/png", width: 900, height: 700 };
const ai = (answer) => {
  const calls = [];
  return {
    calls,
    async run(model, body) {
      calls.push({ model, body });
      if (answer instanceof Error) throw answer;
      return answer;
    },
  };
};
const reply = (content, finish = "stop") => ({ choices: [{ index: 0, message: { role: "assistant", content }, finish_reason: finish }] });

/** Quello che parte verso l'AI di Cloudflare e come si legge la risposta. */
test("richiesta all'AI di Cloudflare e risposta", async () => {
  const AI = ai(reply("```json\n" + JSON.stringify(empty) + "\n```"));
  const r = await readWithCloudflare({ AI }, input);
  assert.equal(r.kind, "ok");
  const { model, body } = AI.calls[0];
  assert.equal(model, DEFAULT_MODEL);
  assert.equal(body.messages[0].role, "system");
  assert.equal(body.messages[1].content[0].image_url.url, "data:image/png;base64,AAAA");
  assert.match(body.messages[1].content[1].text, /900 pixel/);
  assert.equal(body.response_format.type, "json_schema");
  assert.equal(body.response_format.json_schema.schema.type, "object");
  assert.equal(body.response_format.json_schema.schema.$schema, undefined);
});

test("non una piantina, risposta tagliata o storta, quota finita", async () => {
  assert.equal((await readWithCloudflare({ AI: ai(reply(JSON.stringify({ ...empty, is_floor_plan: false }))) }, input)).kind, "not_a_plan");
  assert.equal((await readWithCloudflare({ AI: ai(reply("{", "length")) }, input)).kind, "max_tokens");
  assert.equal((await readWithCloudflare({ AI: ai(reply("non so")) }, input)).kind, "unparsed");
  assert.equal((await readWithCloudflare({ AI: ai(reply(JSON.stringify({ rooms: "x" }))) }, input)).kind, "unparsed");
  const quota = new Error("4006: you have used up your daily free allocation of 10,000 neurons");
  assert.equal((await readWithCloudflare({ AI: ai(quota) }, input)).kind, "quota");
  assert.equal((await readWithCloudflare({ AI: ai(new Error("3040: capacity")) }, input)).kind, "ai_error");
});
