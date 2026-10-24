import { test } from "node:test";
import assert from "node:assert/strict";
import { handle } from "../src/index.js";

/** KV finto, in memoria. */
function kv() {
  const m = new Map();
  return { get: async (k) => m.get(k) ?? null, put: async (k, v) => void m.set(k, v), m };
}

const reading = {
  is_floor_plan: true,
  rooms: [{ name: "Cucina", corners: [[10, 10], [110, 10], [110, 90], [10, 90]], sides_m: [3.5, 2.8], area_m2: null, excluded: [] }],
  openings: [{ kind: "door", ends: [[40, 90], [70, 90]], width_cm: null, height_cm: null, sill_cm: null, entrance: true, hinge: [40, 90], opens_toward: [50, 100], sliding: false }],
  stairs: [],
  walls: [],
  exterior_wall_px: null,
  exterior_outline: [],
  units: "cm",
  notes: "",
};

function request(body, headers = {}) {
  return new Request("https://lettura.example/leggi", { method: "POST", headers: { "Content-Type": "application/json", ...headers }, body: JSON.stringify(body) });
}

const ok = { image: "AAAA", media_type: "image/jpeg", width: 1200, height: 800, device: "dispositivo-1" };
const deps = (result = { kind: "ok", reading }) => ({ read: async () => result, today: () => "2026-10-24" });

test("lettura senza null, con le misure dell'immagine inviata", async () => {
  const res = await handle(request(ok), {}, deps());
  assert.equal(res.status, 200);
  const r = await res.json();
  assert.deepEqual(r.image, { width: 1200, height: 800 });
  assert.equal(r.version, 1);
  assert.equal(r.is_floor_plan, undefined);
  assert.equal("area_m2" in r.rooms[0], false);
  assert.equal("width_cm" in r.openings[0], false);
  assert.deepEqual(r.openings[0].hinge, [40, 90]);
});

test("chiave dell'app richiesta se impostata", async () => {
  const env = { CHIAVE_APP: "segreta" };
  assert.equal((await handle(request(ok), env, deps())).status, 401);
  assert.equal((await handle(request(ok, { "X-Sagoma-App": "segreta" }), env, deps())).status, 200);
});

test("richieste non valide", async () => {
  for (const bad of [{ ...ok, media_type: "image/gif" }, { ...ok, width: 3000 }, { ...ok, device: "x" }, { ...ok, image: 3 }]) {
    assert.equal((await handle(request(bad), {}, deps())).status, 400);
  }
  const get = new Request("https://lettura.example/leggi");
  assert.equal((await handle(get, {}, deps())).status, 404);
});

test("limite per dispositivo e per giorno", async () => {
  const env = { LIMITI: kv(), LIMITE_DISPOSITIVO: "2", LIMITE_GIORNO: "3" };
  assert.equal((await handle(request(ok), env, deps())).status, 200);
  assert.equal((await handle(request(ok), env, deps())).status, 200);
  const third = await handle(request(ok), env, deps());
  assert.equal(third.status, 429);
  assert.equal((await third.json()).error, "troppe_letture");
  assert.equal((await handle(request({ ...ok, device: "dispositivo-2" }), env, deps())).status, 200);
  const full = await handle(request({ ...ok, device: "dispositivo-3" }), env, deps());
  assert.equal(full.status, 503);
  assert.equal((await full.json()).error, "servizio_pieno");
});

test("non una piantina e lettura fallita", async () => {
  assert.equal((await handle(request(ok), {}, deps({ kind: "not_a_plan" }))).status, 422);
  assert.equal((await handle(request(ok), {}, deps({ kind: "refusal" }))).status, 502);
});
