import { test } from "node:test";
import assert from "node:assert/strict";
import { findIssues, overlapArea, polygonArea, reviewText } from "../src/review.js";

const square = (x, y, s) => [[x, y], [x + s, y], [x + s, y + s], [x, y + s]];
const room = (name, corners, area_m2 = null, excluded = []) => ({ name, corners, sides_m: [], area_m2, excluded });

test("aree e sovrapposizioni", () => {
  assert.equal(polygonArea(square(0, 0, 10)), 100);
  assert.equal(overlapArea(square(0, 0, 10), square(20, 0, 10)), 0);
  const shared = overlapArea(square(0, 0, 10), square(5, 0, 10));
  assert.ok(Math.abs(shared - 50) < 3, `${shared}`);
});

test("stanze sovrapposte e metri quadri che non tornano", () => {
  // Scala dalla mediana: 1 m² ogni 100 unità d'area. La Camera è disegnata il doppio di quello che è scritto.
  const reading = {
    rooms: [
      room("Cucina", square(0, 0, 30), 9),
      room("Bagno", square(40, 0, 20), 4),
      room("Studio", square(70, 0, 30), 9),
      room("Camera", square(0, 40, 40), 8),
      room("Disimpegno", square(35, 40, 20)),
    ],
  };
  const issues = findIssues(reading);
  assert.equal(issues.length, 2, issues.join("\n"));
  assert.match(issues[0], /«Camera».*16\.0 m².*8 m²/);
  assert.match(issues[1], /«Camera» e «Disimpegno» si sovrappongono/);
  // Le zone non calpestabili non contano: la Camera con metà zona esclusa torna.
  const fixed = { rooms: [...reading.rooms.slice(0, 3), room("Camera", square(0, 40, 40), 8, [[[0, 40], [40, 40], [40, 60], [0, 60]]])] };
  assert.deepEqual(findIssues(fixed), []);
  assert.match(reviewText(fixed, issues), /prima lettura[\s\S]*- «Camera»/);
});
