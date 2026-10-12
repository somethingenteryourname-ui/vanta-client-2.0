# VANTA presence server (optional)

Only needed if you want VANTA badges on servers that **don't** run the VantaBridge plugin.
It's a free Cloudflare Worker you host yourself. It keeps, in memory only, which UUIDs have
VANTA open (forgotten 3 minutes after a client stops checking in).

## Deploy (about 5 minutes)

1. Make a free account at https://dash.cloudflare.com.
2. Install Node.js, then in this folder run:
   ```
   npx wrangler login
   npx wrangler deploy
   ```
3. Wrangler prints your URL, like `https://vanta-presence.yourname.workers.dev`.
4. In game: VANTA menu > Modules > Client Badge > Presence server, paste that URL.
   Everyone who should see each other's badge pastes the same URL.

Free plan limits are far above what a group of friends uses (each client sends one small
request per minute).
