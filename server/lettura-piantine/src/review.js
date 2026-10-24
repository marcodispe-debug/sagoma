/**
 * Controllo di una lettura prima di mandarla all'app: stanze che si sovrappongono e metri quadri disegnati lontani da quelli
 * scritti. Le coordinate possono essere in pixel o in millesimi: contano solo i rapporti tra le aree.
 */

/** Sovrapposizione oltre questa parte della stanza più piccola: due stanze non possono condividere pavimento. */
const OVERLAP = 0.03;
/** Metri quadri disegnati oltre questo scarto da quelli scritti. */
const AREA = 0.15;
/** Lato della griglia con cui si misurano le sovrapposizioni. */
const GRID = 300;

export function polygonArea(pts) {
  let a = 0;
  for (let i = 0; i < pts.length; i++) {
    const [x1, y1] = pts[i];
    const [x2, y2] = pts[(i + 1) % pts.length];
    a += x1 * y2 - x2 * y1;
  }
  return Math.abs(a) / 2;
}

function inside([x, y], pts) {
  let c = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i];
    const [xj, yj] = pts[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
}

/** Area comune di due poligoni, contando i punti di una griglia sul riquadro che li contiene entrambi. */
export function overlapArea(a, b) {
  const ba = bounds(a);
  const bb = bounds(b);
  // Si contano solo i punti nel riquadro comune; il passo dipende dal riquadro di tutte e due.
  const [x0, x1] = [Math.max(ba.x0, bb.x0), Math.min(ba.x1, bb.x1)];
  const [y0, y1] = [Math.max(ba.y0, bb.y0), Math.min(ba.y1, bb.y1)];
  if (x1 <= x0 || y1 <= y0) return 0;
  const step = Math.max(Math.max(ba.x1, bb.x1) - Math.min(ba.x0, bb.x0), Math.max(ba.y1, bb.y1) - Math.min(ba.y0, bb.y0)) / GRID;
  let n = 0;
  for (let x = x0 + step / 2; x < x1; x += step) {
    for (let y = y0 + step / 2; y < y1; y += step) if (inside([x, y], a) && inside([x, y], b)) n++;
  }
  return n * step * step;
}

function bounds(pts) {
  const xs = pts.map((p) => p[0]);
  const ys = pts.map((p) => p[1]);
  return { x0: Math.min(...xs), x1: Math.max(...xs), y0: Math.min(...ys), y1: Math.max(...ys) };
}

function median(values) {
  const v = [...values].sort((p, q) => p - q);
  return v.length ? (v.length % 2 ? v[(v.length - 1) / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2) : null;
}

/** I problemi trovati, uno per riga, in italiano: vuota se la lettura sembra a posto. */
export function findIssues(reading) {
  // Le zone non calpestabili non contano nei metri quadri scritti.
  const rooms = reading.rooms
    .filter((r) => r.corners.length >= 3)
    .map((r) => ({ ...r, area: polygonArea(r.corners) - (r.excluded ?? []).filter((z) => z.length >= 3).reduce((a, z) => a + polygonArea(z), 0) }));
  const issues = [];
  // Metri quadri per unità d'area dell'immagine, dalle stanze con i metri quadri scritti: la mediana regge qualche errore.
  const written = rooms.filter((r) => r.area_m2 > 0 && r.area > 0);
  const k = written.length >= 3 ? median(written.map((r) => r.area_m2 / r.area)) : null;
  if (k) {
    for (const r of written) {
      const drawn = r.area * k;
      if (Math.abs(drawn - r.area_m2) / r.area_m2 > AREA) {
        issues.push(`«${r.name}»: il contorno disegnato misura circa ${drawn.toFixed(1)} m², ma sulla piantina è scritto ${r.area_m2} m². Controlla dove sono davvero i suoi muri.`);
      }
    }
  }
  for (let i = 0; i < rooms.length; i++) {
    for (let j = i + 1; j < rooms.length; j++) {
      const shared = overlapArea(rooms[i].corners, rooms[j].corners);
      if (shared > OVERLAP * Math.min(rooms[i].area, rooms[j].area)) {
        issues.push(`«${rooms[i].name}» e «${rooms[j].name}» si sovrappongono: tra due stanze c'è sempre un muro, e ognuna finisce sul proprio lato di quel muro.`);
      }
    }
  }
  return issues;
}

/** La richiesta del secondo giro: la prima lettura e i problemi trovati, da correggere guardando di nuovo la piantina. */
export function reviewText(first, issues) {
  return `Questa è la tua prima lettura della piantina:
${JSON.stringify(first)}

Controllandola ho trovato questi problemi:
${issues.map((s) => `- ${s}`).join("\n")}

Guarda di nuovo la piantina e correggi la lettura: sposta i contorni delle stanze sul filo interno dei loro muri, così che nessuna stanza si sovrapponga a un'altra e i metri quadri tornino. Tieni quello che era già giusto. Restituisci la lettura completa, con le coordinate nello stesso formato.`;
}
