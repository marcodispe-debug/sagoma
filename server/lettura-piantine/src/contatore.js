/**
 * Contatore delle letture del giorno (Durable Object `CONTATORE`, uno solo per tutto il server).
 *
 * Al posto del KV `LIMITI`: un Durable Object risponde a una richiesta alla volta, quindi leggere e aumentare i contatori è
 * un passo unico. Con il KV dieci richieste partite insieme leggono tutte lo stesso numero e passano tutte, anche oltre i
 * limiti. Tiene solo il giorno corrente: al primo conteggio di un giorno nuovo cancella tutto quello di ieri.
 *
 * Richiesta: `POST` con `{ day, checks: [{ key, limit, error }] }`. Risposta: `{ error }` con il primo limite già raggiunto
 * (e allora non conta niente), oppure `{ error: null }` dopo aver contato una lettura su ogni chiave.
 */
export class Contatore {
  constructor(state) {
    this.storage = state.storage;
  }

  async fetch(request) {
    const { day, checks } = await request.json();
    return Response.json({ error: await take(this.storage, day, checks) });
  }
}

/** Il conteggio vero e proprio, separato per le prove. `storage` è quello del Durable Object (get, put, deleteAll). */
export async function take(storage, day, checks) {
  if ((await storage.get("giorno")) !== day) {
    await storage.deleteAll();
    await storage.put("giorno", day);
  }
  const counts = await Promise.all(checks.map((c) => storage.get(`n:${c.key}`)));
  for (const [i, c] of checks.entries()) {
    if ((counts[i] ?? 0) >= c.limit) return c.error;
  }
  await Promise.all(checks.map((c, i) => storage.put(`n:${c.key}`, (counts[i] ?? 0) + 1)));
  return null;
}
