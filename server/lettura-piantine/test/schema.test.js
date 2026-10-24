import { test } from "node:test";
import assert from "node:assert/strict";
import * as z from "zod/v4";
import { readingSchema } from "../src/schema.js";

/** Lo schema mandato all'API: ogni oggetto chiuso (additionalProperties false) e con tutti i campi obbligatori. */
test("schema adatto alle uscite strutturate", () => {
  const json = z.toJSONSchema(readingSchema);
  const walk = (s) => {
    if (s.type === "object") {
      assert.equal(s.additionalProperties, false);
      assert.deepEqual([...s.required].sort(), Object.keys(s.properties).sort());
      Object.values(s.properties).forEach(walk);
    }
    if (s.items) walk(s.items);
    for (const k of ["anyOf", "oneOf"]) (s[k] ?? []).forEach(walk);
  };
  walk(json);
});
