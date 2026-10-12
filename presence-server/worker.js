// VANTA presence server (optional). A tiny Cloudflare Worker + Durable Object that only
// remembers which Minecraft UUIDs have VANTA open right now. Nothing is written to disk:
// entries vanish 3 minutes after a client stops checking in.
//
// API: POST /v1/presence  {"self": "<uuid or omitted>", "check": ["<uuid>", ...]}
//      -> {"users": ["<uuid>", ...]}   (the checked UUIDs that are using VANTA)

const TTL_MS = 3 * 60 * 1000;
const UUID_RE = /^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$/;
const norm = (u) => u.toLowerCase().replace(/-/g, "");

export class Presence {
  constructor(state, env) {
    this.online = new Map(); // normalized uuid -> last seen (ms)
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/v1/presence") {
      return new Response("VANTA presence server is running.", { status: 200 });
    }
    let body;
    try {
      body = await request.json();
    } catch {
      return new Response("Bad JSON", { status: 400 });
    }
    const now = Date.now();
    for (const [id, seen] of this.online) if (now - seen > TTL_MS) this.online.delete(id);
    if (typeof body.self === "string" && UUID_RE.test(body.self)) this.online.set(norm(body.self), now);
    const check = Array.isArray(body.check) ? body.check.slice(0, 500) : [];
    const users = check.filter((u) => typeof u === "string" && UUID_RE.test(u) && this.online.has(norm(u)));
    return Response.json({ users });
  }
}

export default {
  async fetch(request, env) {
    const id = env.PRESENCE.idFromName("global");
    return env.PRESENCE.get(id).fetch(request);
  },
};
