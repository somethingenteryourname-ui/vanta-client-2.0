// VANTA presence server (optional). A tiny Cloudflare Worker + Durable Object that only
// remembers which Minecraft UUIDs have VANTA open right now. Nothing is written to disk:
// entries vanish 3 minutes after a client stops checking in.
//
// API: POST /v1/presence  {"self": "<uuid or omitted>", "check": ["<uuid>", ...],
//                          "cosmetics": {"cape": "<id>", "trail": "<id>"}}   (cosmetics optional)
//      -> {"users": ["<uuid>", ...],                       (the checked UUIDs that are using VANTA)
//          "cosmetics": {"<uuid>": {"cape": "<id>", "trail": "<id>"}, ...}}

const TTL_MS = 3 * 60 * 1000;
const UUID_RE = /^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$/;
const norm = (u) => u.toLowerCase().replace(/-/g, "");
const ID_RE = /^[a-z0-9_]{1,32}$/;
const cleanId = (v) => (typeof v === "string" && ID_RE.test(v) ? v : "none");

export class Presence {
  constructor(state, env) {
    this.online = new Map(); // normalized uuid -> last seen (ms)
    this.looks = new Map(); // normalized uuid -> {cape, trail}
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
    for (const [id, seen] of this.online) {
      if (now - seen > TTL_MS) {
        this.online.delete(id);
        this.looks.delete(id);
      }
    }
    if (typeof body.self === "string" && UUID_RE.test(body.self)) {
      this.online.set(norm(body.self), now);
      const c = body.cosmetics;
      if (c && typeof c === "object") this.looks.set(norm(body.self), { cape: cleanId(c.cape), trail: cleanId(c.trail) });
    }
    const check = Array.isArray(body.check) ? body.check.slice(0, 500) : [];
    const users = check.filter((u) => typeof u === "string" && UUID_RE.test(u) && this.online.has(norm(u)));
    const cosmetics = {};
    for (const u of users) {
      const look = this.looks.get(norm(u));
      if (look) cosmetics[u] = look;
    }
    return Response.json({ users, cosmetics });
  }
}

export default {
  async fetch(request, env) {
    const id = env.PRESENCE.idFromName("global");
    return env.PRESENCE.get(id).fetch(request);
  },
};
