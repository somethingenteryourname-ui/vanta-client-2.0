# VANTA Client — Stage 4

A customizable client experience inside one Fabric mod for **Minecraft Java 1.21.11**:
a launcher-style home screen, a RIGHT SHIFT client menu with search, a module system,
draggable HUD widgets, a theme system shared by every screen, and live performance stats.

## Versions (checked against Fabric's official 1.21.11 template)

| Thing | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.141.6+1.21.11 (required) |
| Java to play | 21 or newer |
| Mappings | Mojang official (same as Fabric API 1.21.11) |
| Build | Fabric Loom 1.18 (`fabric-loom-remap`), Gradle 9.7.1, JDK 25 in CI |

## Getting the .jar (GitHub builds it for you)

1. Create a new GitHub repository (e.g. `vanta-client`).
2. Upload **everything in this folder**, including the hidden `.github` folder,
   `gradle/`, `gradlew` and `gradlew.bat`. (Easiest: unzip, then drag the
   contents into the repo's "Add file → Upload files" page. If `.github`
   doesn't upload on your system, create `.github/workflows/build.yml` by hand
   and paste in the file's contents.)
3. Open the **Actions** tab. The `build` workflow runs automatically on each push
   (you can also start it with "Run workflow").
4. When it finishes with a green check, open the run and download
   **VANTA-Client-1.21.11** under *Artifacts*. Unzip it to get
   `VANTA-Client-1.21.11.jar`.
5. If the run is red, open the failed **build** step, copy the error lines, and
   send them back to get them fixed.

## Installing

1. Install Fabric Loader for 1.21.11 (fabricmc.net/use).
2. Put `VANTA-Client-1.21.11.jar` **and** Fabric API (1.21.11 build) in your `mods` folder.
3. Launch the Fabric 1.21.11 profile. The VANTA home screen appears instead of the title screen.

## Controls

| Key | Action |
|---|---|
| Right Shift | Open / close the VANTA menu (rebind in Options → Controls → Key Binds → VANTA Client) |
| (unbound) | Open the HUD editor directly — set a key in the same place |
| B | Add a waypoint where you stand (Waypoints module) |
| (unbound) | Open the waypoint list |
| 1 - 9 | On the home screen: join favorite server 1-9 |
| Tab | Focus search in the menu |
| ↑ / ↓ / Enter | Pick a search result |
| Esc | Clear search → leave module page → close |

HUD editor: drag to move, scroll to resize, right-click to show/hide, arrows nudge,
R resets the selected widget, double-click opens its settings.

## What's in this build (stage 2)

**33 modules**, all toggleable with their own settings:

- **Performance:** Particles (density + hide explosions, crits, enchant hits, damage hearts, sweep, totem, block break, rain splash), Entity Culling (distance limits for items, XP orbs, armor stands/frames, mobs, projectiles, chests/signs/banners; players and end crystals always shown), Background FPS (caps FPS while tabbed out).
- **Visuals:** Zoom (C; smooth, scroll to adjust, lower sensitivity, cinematic), Freelook (Left Alt), Fullbright, Custom Crosshair, Block Outline (color/chroma/thickness), Screen Effects (low or hidden fire, totem pop size, no pumpkin/powder snow/portal overlay), Hitboxes.
- **PvP:** Toggle Sprint/Sneak with status, Keystrokes, CPS, Reach Display, Combo Display, Armor Status, Combat Counter (totems, crystals, obsidian, anchors, glowstone, pearls, gapples, XP).
- **HUD:** FPS, Frame Time, Coordinates, Direction, Ping, Memory, Potion Effects, Clock, Speed, Server Address.
- **Interface / QoL:** Home Screen, HUD Tweaks (boss bar, scoreboard, title size; hide vanilla effect icons), Shulker Preview, Death Info, Auto Perspective, Auto Text (3 hotkeys).

**FPS boost system (Performance page):**
- Five presets (Max FPS, Competitive, Balanced, Recording, Potato). Each shows exactly what it changes (current -> new) before you apply it, and Undo restores everything.
- VANTA boosters (particles, culling, background FPS).
- Minecraft's most important video settings gathered in one place.
- Recommended-mod checker (Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast, More Culling) - these give the biggest gains and run alongside VANTA.

**Customization:**
- 6 complete styles, 9 palettes, accent modes (solid, gradient, chroma rainbow with speed/saturation), every surface color, secondary text color, per-category colors.
- Card styles (filled, outlined, minimal, glass), border width, shadows + strength, hover strength, corner radius, panel opacity, toggle styles (switch, checkbox, pill, dot), text shadow, uppercase headings.
- Layout: interface scale, heading scale, grid/list, density, card width, menu position/width/height, sidebar side/width/labels/icons/item height, selection indicator (bar, pill, underline, glow, none), page subtitles, footer hints.
- Backdrop (plain, grid, dots, gradient, vignette), open animation (slide up, fade, zoom, slide in, none), speed, intensity, reduced motion.
- Saved themes you can share (config/vanta/themes).
- Global HUD style: text format ("FPS 120", "[FPS: 120]", "120 FPS"...), label color mode, chroma values, background color, padding, accent outline/side bar.
- Crosshair editor with live preview: 8 shapes, size, thickness, gap, dot, circle, color, outline, chroma, target color, movement spread, attack cooldown bar, Crystal/Mace/Minimal/Classic presets.

**Profiles:** Default, Max FPS, Crystal PvP, Mace PvP, Recording, Balanced built in; create, load, copy, rename, delete, share files (config/vanta/profiles). Imported files are validated.

**Left out on purpose:** removing the end crystal / respawn anchor place-and-break delay. That's what anticheats flag as FastPlace/AutoCrystal and can get you banned; VANTA only changes visuals and performance.

**Not included yet:** voice chat, cosmetics store, motion blur, loot beams, Discord status, Tier Tagger (these need online services, shaders or deeper rendering changes).

## New in stage 3

- **Ping with region:** the Ping widget, pause menu and F3 panel show your region next to the ping, like `53 ms (NA Central)`. It's read from the server address first (na., nae., dal., eu., ...); otherwise the server's IP is looked up once at ipwho.is. Servers behind DDoS protection show the protection network's nearest location. Turn the online lookup off in Settings > Network.
- **Home screen:** custom background images and GIFs (put them in `config/vanta/backgrounds`), a "last world" background (a small picture of where you last played, saved on your PC), a Continue card, favorite servers with live ping and player count (keys 1-9 join them), recent servers and recent worlds, all one click to join.
- **Waypoints:** saved per world/server and dimension, on-screen markers with distance, automatic death point, rename / recolor / hide / delete in the module page. **Minimap:** terrain only (no caves, mobs or players), zoom, rotate, waypoint dots, north marker.
- **Interface:** Clean F3 panel (replaces the debug screen with a tidy themed one), Scoreboard restyle (hide the red numbers, move it), Tab List (ping numbers, theme colors), Auto Reconnect (button + countdown, never retries a ban), Nick Hider (your name and skin on your screen only).
- **PvP / visuals:** Totem Pop Counter, Item Cooldowns widget, Damage Indicators, Nametags (background opacity, size, own nametag, health and pops), Hit Color, Item Physics, Time & Sky (time changer, sky color), Weather (hide rain and storms).

## New in stage 4

- **Works next to other Fabric mods:** runs on Fabric Loader 0.16 and newer (it used to demand 0.19.5). When another mod already does the same job (Zoomify, Xaero's Minimap, JourneyMap, BetterF3, Dynamic FPS, ItemPhysic, AutoReconnect, Shulker Box Tooltip...) VANTA turns its own copy off once; FancyMenu keeps its title screen. Both are switches in Settings > Compatibility. Other mods' F3 lines (Sodium etc.) show in Clean F3 under Mods.
- **Region finder:** tries four lookup services in turn, retries failures (5 s, 20 s, 1 min, 5 min), and saves answers in `config/vanta/regions.json`, so a server you've been on shows its region instantly. A broad address hint (na., eu.) shows while the lookup refines it. DDoS-protected servers are marked as a proxy in F3.
- **Ping:** VANTA now measures your real round trip (a tiny status ping every 10 s, like the server list) instead of the server-reported number, which updates slowly and includes server lag. The last-world picture is only taken when you pause, so it never causes a hitch while playing. Settings > Network.
- **Crosshair:** Overall size (50-400%), bigger ranges for every part, and a Vanilla look shape so you can resize Minecraft's own crosshair.
- **Clean F3:** redesigned as cards: big FPS with a frame-time graph, memory bar, position, world, target, server (region, measured vs. server ping), a **frame-time pie chart**, and other mods' info.
- **Client Badge:** your name with the VANTA logo above your head in F5 and freelook. Other VANTA players get the logo (in your theme color) in nametags and the tab list when the server runs the **VantaBridge** plugin (`vanta-bridge/`, built by GitHub Actions) or when you all use the same optional **presence server** (`presence-server/`, a free Cloudflare Worker you host).
- **Totem pops in the tab list:** `-5` next to each player's ping, in your theme color (yours too).



Tested here:
- Every Minecraft class, method and mixin target was checked against a decompiled copy of the real 1.21.11 client source.
- All code compiles against stubs of those exact signatures.
- 39 automated logic tests pass (plus 27 new stage 3 tests: region detection, GIF decoding, capture sizes, image formats, quick-play helpers): save/load, broken-file recovery, profiles (create, switch, rename, copy, delete, import validation, broken files), saved themes, style presets, chroma, crosshair presets, preset previews, particle and culling logic, FPS math, mod detection.

Not tested yet:
- The real Gradle build (GitHub Actions does it).
- In-game behavior of the new modules and mixins. Feature mixins are marked optional, so if one doesn't attach it logs a warning and that one feature does nothing - the game still starts.

## Privacy and fair play

VANTA needs no account, sends no telemetry, and stores settings only in your config folder.
The only network request is the optional region lookup (it sends the server's IP to ipwho.is,
once per server; switch it off in Settings > Network). It changes visuals and menus only: Keystrokes and CPS display input;
nothing clicks, aims, or changes reach or hitboxes.

## Project layout

```
src/main/java/vanta/
  client/       entry point, version info
  branding/     Theme (shared look for every screen)
  config/       settings framework + ConfigManager (load/save/backup/migrate)
  modules/      Module base, registry, categories, home-screen module
  hud/          HUD widgets, manager, drag-and-drop editor
  gui/          client menu, dialogs, menu items (cards, rows, buttons)
  menu/         VANTA home screen + title-screen hook
  metrics/      frame-time and click statistics
  input/        key bindings
  compat/       installed-mod detection
  ui/           drawing primitives, icons, colors, animation
  mixin/        core mixin + optional feature mixins (each failing alone)
  waypoints/    waypoints, markers, world-to-screen projection
  net/          region lookup
  combat/       reach/combo stats, totem pop tracker
```
