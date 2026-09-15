# MERGE_NOTES.md — Mockup-driven frontend rebuild

Branch: `mockup-rebuild` (cut from `main`). Nothing committed to `main`. No push, no deploy.
Spec: `BAWAXICT FamilyHub UI Showcase.png` (8 panels).

---

## A. Mockup panel → page mapping

| # | Panel in image | Target page | Existing repo file | Disposition |
|---|---|---|---|---|
| 1 | Home / Community Feed (desktop) | `site/index.html` | exists (36 KB) | Rebuild to mockup |
| 2 | Hotspot Page (desktop) | `site/status.html` | exists | Rebuild to mockup |
| 3 | Home / Feed (mobile) | responsive state of #1 | — | Same file, breakpoint |
| 4 | Discover / Explore (mobile) | **new** `site/discover.html` | none | New page |
| 5 | Create Post Menu (mobile) | modal inside #1 | — | Component in index |
| 6 | Ticket Page Menu (mobile) | `site/tickets.html` | exists | Rebuild to mockup |
| 7 | Connected / Success (mobile) | `site/login.html` success state | exists | Rebuild success state only |
| 8 | FamilyHub Feed (mobile) | same as #3 | — | Same file |

**Not shown in any panel → untouched** (per your rule 4): `ai.html`, `games.html`,
`institute.html`, `advertise.html`, `radvert.html`, `report.html`, `rewards.html`,
`skills.html`, `skill-chat.html`, `skill-status.html`, `updates.html`, `logout.html`,
`admin/*`, all of `site/games/`, all of `site/mikrotik/`.

**Chat, forum, marketplace, services, profile: NO mockup panel was provided.** Panels
1/2/3/8 show *nav links and feed cards*, not those pages' interiors. Under rule 4 I am
leaving `chat.html`, `private-chat.html`, `community-chat.html`, `forum.html`,
`marketplace.html`, `services.html` **untouched**. This directly contradicts the earlier
brief, which made chat the priority rebuild. See Blocker Q1 below — I need your call.

---

## B. Backend endpoints each rebuilt page will use

All real, all already in `backend/server.py`, all reached via
`BAWAXICT_CONFIG.familyHubApiBase` (`/api/familyhub/`). No mocks, no new contracts.

**Panel 1/3/8 — Feed (`index.html`)**
- `GET /api/session` → auth gate, username, `hotspotUser`, `hidden` (server.py:657)
- `GET /api/community` → feed posts, incl. `like_count`, `comment_count`, `liked` (:887)
- `GET /api/community/following` → "Following" tab (:906)
- `GET /api/community?sort=top` → "Trending" tab (:887)
- `GET /api/community/comments?postId=` → comment counts (:918)
- `POST /api/community` → composer / Create Post (:1433)
- `POST /api/community/like` (:1455), `POST /api/community/comment` (:1470)
- `GET /api/stream` SSE, fallback `GET /api/events?since=` → live feed (:679/:666)
- `GET /api/community-activities` → "While You Were Away" rail (:629)
- `GET /api/unread` → header badge (:955)
- `GET /api/profile` (:860) / `POST /api/profile/photo` (:1340) → avatar + profile modal

**Panel 2/7 — Hotspot + Connected (`status.html`, `login.html`)**
- `GET /api/session`, `POST /api/hotspot/verify` (:1382), `POST /api/heartbeat` (:1399)
- MikroTik `$(link-logout)` flow in `site/mikrotik/*.rsc` — untouched.

**Panel 4 — Discover (`discover.html`, new)**
- `GET /api/search?q=` (:960), `GET /api/members` (:855),
  `GET /api/community?sort=top` for "Popular Posts", `GET /api/community-activities`.

**Panel 6 — Tickets (`tickets.html`)**
- `GET /api/session`, `GET /api/rewards/mine` (:968), `GET /api/notices/public` (:838).

---

## C. Endpoints the mockup implies that DO NOT exist

Grepped `server.py`: zero matches for `learning`, `opportunit`, `pulse`, `trending`,
`discover`, `ticket`.

| Mockup element | Status | Proposed handling |
|---|---|---|
| Sidebar "Learning" | no endpoint | nav link only, routes to existing `institute.html` |
| Sidebar "Opportunities" | no endpoint | needs decision — see Q2 |
| Sidebar "Events" | no endpoint | needs decision — see Q2 |
| "Daily Pulse" poll card | no endpoint | needs additive `/api/pulse` — see Q2 |
| "Trending Topics" + hashtags | no endpoint | can derive from `/api/community?sort=top` |
| "2 new opportunities posted" | no endpoint | depends on Q2 |

Per your constraint ("if a needed endpoint doesn't exist yet, add it additively to
`server.py` rather than faking it"), these need new additive routes. I will not fake
them client-side, and I will not add them until you confirm scope.

---

## D. Pre-existing bug found in `backend/server.py` — NOT touching it without approval

`server.py:794` is a **duplicate, unreachable `if path == '/api/marketplace':` block**,
byte-identical in intent to the one at :759. Worse, at :767 there is
`if self.command == 'POST':` at indent level 8 **inside `do_GET`** — the marketplace
POST/create-listing branch was pasted into the GET handler. `do_POST` (:1054) contains
zero marketplace handling (verified: 0 matches).

Consequence: creating a marketplace listing is handled in `do_GET`, so a real POST to
`/api/marketplace` never reaches it. The file compiles (`py_compile` passes), so this
is silent.

This is out of scope for a mockup rebuild and touching it is a backend logic change,
not "wiring in new calls." **I have left it exactly as-is** and am flagging it per your
"stop and ask" rule. Recommend a separate fix commit, separately reviewed.

---

## E. Stale duplicates (`site/` vs `backend/`) — flagged, not yet touched

`backend/` serves its own copies of frontend files. Diverged pairs:

| File | site/ | backend/ | |
|---|---|---|---|
| `chat.html` | 43,330 B | 57,872 B | DIVERGED |
| `private-chat.html` | 63,776 B | 65,598 B | DIVERGED |
| `community-chat.html` | 3,748 B | 4,199 B | DIVERGED |
| `index.html` | 36,441 B | 44,284 B | **DIVERGED** |
| `marketplace.html`, `skills.html`, `skill-chat.html`, `config.js` | | | identical |

`backend/index.html` is 8 KB larger than `site/index.html`. Before I rebuild index I
must know which one is actually live. See Q3.

---

## F. Blocking questions

**Q1 — Chat/forum/marketplace/services/profile.** No mockup panel shows their interiors.
Rule 4 says leave them; the prior brief says chat is the top-priority rebuild. Which wins?

**Q2 — Learning / Opportunities / Events / Daily Pulse.** Four nav destinations and a
poll widget with no backend. Add additive `server.py` routes + schema tables, or render
nav entries that point at existing pages / are disabled for now?

**Q3 — `site/index.html` vs `backend/index.html`.** Which is served in production? I
rebuild that one and leave the other alone until you say to retire it.

**Q4 — Preview.** I can run `backend/server.py` in this sandbox and give you a live URL,
but its SQLite DB will be empty, so the feed renders as an empty state, not the populated
feed in the mockup. Options: (a) live server, real endpoints, empty data; (b) live server
seeded with a few throwaway rows via the real POST endpoints so panels look like the
mockup; (c) both. I suggest (b) — seed data written through real APIs, never hardcoded
in the page, and the seed script kept out of the commit.

---

## G. Assumptions logged (inferred from image, for your review)

- Palette read off the mockup: dark green `#0B2E23`, accent green `#12B76A`,
  orange CTA `#FF7A2E`, page bg `#F6F8F5`, border `#EAECF0`, text `#101828`,
  muted `#667085`. Typeface is a geometric sans → Plus Jakarta Sans, self-hosted
  (captive portal blocks Google Fonts CDN pre-auth).
- Hover/focus/pressed states are not visible in the mockup; I will derive them
  conservatively (2% darken, existing radius/shadow language).
- Panel 5's "Create Post" sheet is mobile-only in the image; on desktop I will present
  the same actions as the existing centered modal pattern.
- Avatars in the mockup are stock faces. Real data has no avatar URLs for most users;
  I will use the existing `/api/profile/photo` media where present and initial-letter
  avatars otherwise, rather than pulling `i.pravatar.cc` (third-party, blocked pre-auth).

---

## I. UPDATE — findings that invert part of the Q1 correction

The Q1 correction says: "do not fall back to the old GitHub markup as a starting point
for layout or styling." Having now actually read the files, that premise is wrong for
two of the five, and it matters enough to stop.

### I.1 `site/forum.html` is NOT old markup — it is already a mockup rebuild

100,428 bytes. Its own header comment reads:

> "Visual system from the approved mockup (same tokens, rail, hero, composer, cards,
> right rail and mobile tab bar as the approved Home / Community Feed screens).
> Functional layer ... is ported from the live site/forum.html + site/index.html logic."

It already implements: the sidebar nav from panel 1 (Home, Marketplace, Services,
Learning, Opportunities), the tab row (For You / Following / Trending), the composer,
the right rail, the mobile tab bar, a `profilePanel`, and SSE via `API + '/stream'`.
It calls 23 real endpoints through `config.js`. Its token set is a *near* match to my
section-G palette but not identical — it uses `--green-900:#123B2C` / `--bg:#F6F4EE`
where I read `#0B2E23` / `#F6F8F5` off the image.

**This is panel 1/3/8 already built.** Someone did this work already. Discarding it and
rebuilding from the image would destroy a working, wired, 100 KB page and re-derive it
worse — I have the image, they evidently had the layered source.

### I.2 `site/private-chat.html` is also already a mockup-era rebuild

63,776 bytes, header: "STANDALONE PAGE: Private Chat (1-to-1, WhatsApp-style)." Wired to
20 real endpoints (`/conversations`, `/private`, `/read`, `/block`, `/start-conversation`,
`/media/upload`, `/stream`...). Already has online/presence (19 refs). Missing only
typing indicators and delivery/seen receipts.

### I.3 The actual architecture is the reverse of what the brief assumes

`site/index.html` (36 KB) is a **static landing/dashboard prototype**, per
`STAGE9_WEB_LANDING_FIX.md`. It makes exactly ONE api call
(`/familyhub/api/community-activities`) and links out to the real pages. The real
FamilyHub feed app is `forum.html`. So the mockup's panel 1 "Home / Community Feed"
corresponds to **`forum.html`**, not `index.html`. My section-A mapping was wrong.

### I.4 `site/services.html` genuinely IS old, and is a different product

2,199 bytes, zero JavaScript, zero fetch calls. Four hardcoded marketing cards
(Printing, ID & Registration, Hotspot & Network, Home Technology) for the *BawaxICT
storefront in Ilorin* — physical-desk services. This is NOT the mockup's "Services —
find trusted local service providers" directory. Meanwhile `server.py` exposes a real
`/api/services` + `/api/services/mine` + `POST /api/services/request` that this page
never calls. This one is a true from-scratch build.

### I.5 Corrected disposition for the five

| Page | Reality | Correct action |
|---|---|---|
| `forum.html` | already mockup-built, 23 endpoints, SSE | **Reconcile tokens to image; do NOT rebuild** |
| `private-chat.html` | already mockup-built, 20 endpoints, presence | **Extend: typing + delivery/seen** |
| `community-chat.html` | 3.7 KB redirect shim → forum/private-chat | Leave or retire as dead weight |
| `chat.html` | 43 KB "approved dashboard mockup", older gen | Retire once forum+private-chat confirmed live |
| `services.html` | genuinely old, static, wrong product | **Build new against `/api/services`** |
| `marketplace.html` | 15 KB, calls `/api/familyhub/marketplace` | Rebuild UI, keep the POST bug untouched |
| Profile panel | exists as `profilePanel` in forum.html | Extend in place, no sidebar link |

### I.6 Additive schema for chat (ready, not yet applied)

Following the existing pattern at `server.py:372-392` (try/except `OperationalError`):
- `ALTER TABLE private_messages ADD COLUMN delivered_at INTEGER NOT NULL DEFAULT 0`
- `ALTER TABLE private_messages ADD COLUMN seen_at INTEGER NOT NULL DEFAULT 0`
- `CREATE TABLE IF NOT EXISTS typing_state (thread_id, user_id, updated_at, UNIQUE(thread_id,user_id))`

No existing column or table is altered or dropped. Typing broadcasts over the existing
`events` table so `/api/stream` carries it with no new transport.

---

## J. Why I stopped instead of building

Three of the five pages you told me to rebuild from scratch are already built to this
mockup and wired to real endpoints. The instruction not to use the old markup was
written on the assumption they were stale — they are not. Rebuilding `forum.html` from
a PNG would be a regression, and it is not reversible once the old file is retired.

Also still unanswered, and now blocking more than before: **Q2** (Learning /
Opportunities / Events / Daily Pulse have no backend — `forum.html` already renders
these nav items, so I need to know what they point at), **Q3** (now partly self-answered:
`site/` is the Cloudflare publish dir, `backend/*.html` is served only by `server.py`,
whose `/` route serves `backend/chat.html`), and **Q4** (preview seeding).

## K. AUDIT — live end-to-end testing of chat + profile

Method: ran `backend/server.py` (port 18080) against a fresh SQLite DB, created two
real users (Alice, Bob) via `POST /api/join`, and exercised every endpoint the chat
and profile UIs depend on with real HTTP calls. Findings are empirical, not read
off the source.

### K.1 THE HEADLINE: image + voice upload are NOT broken in the backend

Both work perfectly. Measured:

| Test | Result |
|---|---|
| `POST /media/upload` image/png | `200` → `{"ok":true,"media":{"id":1,"kind":"image"...}}` |
| `POST /media/upload` audio/webm | `200` → `{"ok":true,"media":{"id":3,"kind":"voice"...}}` |
| `POST /private` w/ `attachmentId` (image) | `200` → `{"ok":true,"messageId":1}` |
| `POST /private` w/ `attachmentId` (voice) | `200` → `{"ok":true,"messageId":2}` |
| `GET /private?with=Alice` as Bob | returns both, with full `attachment` objects |
| `GET /media/1` | `200 image/png 73 B` |
| `GET /media/3` | `200 audio/webm 2048 B` |

The backend already supports voice natively: `ALLOWED_VOICE_TYPES` (server.py:42) =
webm/ogg/mp4/mpeg/wav, `MAX_VOICE_BYTES` = 3 MB, `kind='voice'` branch at :1326.
**No new endpoint or column is needed for voice notes.** The brief's assumption that
audio support might be missing is incorrect.

`parse_multipart` (:553) is field-name agnostic — it returns the first part with a
filename — so "wrong field name" is not the cause either. `private-chat.html:974`
sends `fd.append('file', ...)`, which is fine.

### K.2 ROOT CAUSE of "uploads are broken": a 9-second client timeout

`private-chat.html:354` — `x.timeout = 9000` — applies to **every** request including
multipart uploads. Measured on loopback with zero network latency:

| Payload | Time |
|---|---|
| 200 KB | 0.013 s |
| 900 KB | 0.030 s |
| 1.8 MB | **1.055 s** |

1.8 MB costs ~1.06 s on loopback. Over a congested community hotspot at ~150–400 kbps
upstream, a 1.8 MB image (limit is 2 MB) needs **40–100 s** — it will blow the 9 s
timeout every time. A voice note near the 3 MB cap is worse. Small test images on a
fast link succeed, which is exactly why this reads as "randomly broken."

Compounding it: `x.onerror`/`ontimeout` report "server timed out," the partial upload
still lands server-side, and there is no progress indicator, no retry, and no
chunking. Fix = raise/remove the timeout for multipart, add upload progress via
`xhr.upload.onprogress`, and client-side downscale images before send.

### K.3 Chat background — confirmed bad, and heavy

`private-chat.html:62-63`:
```
background-color:#111;
background-image:linear-gradient(rgba(0,0,0,.35),rgba(0,0,0,.45)),url("assets/wallpapers/private-chat-wallpaper.png");
```
The wallpaper file **exists but is 2,234,054 bytes (2.2 MB)** — a full extra megabyte
more than every other asset combined, blocking first paint of the thread on the same
slow hotspot link. It is also darkened by a double black overlay onto `#111`, which is
why it looks muddy. Replacing it with a subtle CSS-only pattern in the mockup palette
removes 2.2 MB from the page *and* fixes the look. Confirmed in scope.

### K.4 Chat is not in the mockup's design system at all

Hardcoded **WhatsApp** colors, not BAWAXICT: `#075e54` ×4, `#d9fdd3` ×2 (sent bubble),
`#25d366` ×2, `#128c7e` ×2, `#667781` ×9. `community-chat.html` has `#075e54` ×4 too.
This is a WhatsApp clone skin, not the mockup's `#0B2E23`/`#12B76A`/`#FF7A2E` system.
Full visual rebuild is justified — this one genuinely is not mockup-based, whatever
its header comment says.

### K.5 Profile: the endpoint already exists; the gap is in ONE page

`GET /api/profile/public?username=` (server.py:864) already works. Verified Bob
fetching Alice: `{"username":"Alice","profilePhotoId":null,"following":false}`.
`GET /api/profile/public/posts?username=` (:874) also works, and respects `hidden`.

`forum.html` **already calls both** (lines 1683, 1701) — viewing another member's
profile from the feed works today. The gap is that **`private-chat.html` never calls
`/profile/public` at all** — grep returns zero hits. So from a chat thread you cannot
open the person you're talking to. That's the actual defect, and it's frontend-only.

**No new endpoint required.** This contradicts the brief's expectation that
`/api/profile/:username` needs adding — correcting that here rather than adding a
redundant duplicate route.

### K.6 `community-chat.html` — verified NOT in use as a chat page

It is a 3.7 KB **username-entry splash**, not a chat UI. It collects a name and
redirects: `location.href='forum.html?preferredName='+...`. So the live chat surfaces
are `forum.html` (community feed) and `private-chat.html` (1:1). Per the instruction to
verify before rebuilding: there is no community-chat interior to redesign. It should be
restyled as a small join screen or folded into the login flow — not rebuilt as chat.

### K.7 Navigation audit

Relative-link sweep across all 22 `site/*.html`: **zero missing targets.**

Four absolute links will 404 unless the site is served from domain root:
`forum.html:300`, `forum.html:444`, `index.html:167`, `index.html:256` — all
`href="/marketplace.html"`. Every other internal link is correctly relative. Under the
`/familyhub/` base path these break. Fix: make them relative.

### K.8 Other findings

- **SSE holds a DB connection for 55 s per client.** `SSE_MAX_SECONDS=55`,
  `SSE_POLL_SECONDS=0.75` (:120-121); `/api/stream` opens a connection and keeps it
  through the whole loop (:679-704). Server is `ThreadingTCPServer` (:1560), so N
  concurrent viewers = N held SQLite connections, each waking 73×/min. Works at
  village scale, will not scale; noted, not in scope.
- **`ONLINE_WINDOW = 45 s`** vs SSE reconnect at 55 s → a user can flicker "offline"
  between reconnects. Relevant to the presence indicator being added; will drive
  presence from heartbeat, not SSE lifetime.
- **Recovery code shown once at join** (`"recoveryCode":"HTZ2-3YC9"`) with no
  re-display path. If the user misses it, identity is unrecoverable for 21 days.
  Flagging, not fixing — not mockup/chat/profile scope.

### K.9 Corrected work plan

| # | Item | Layer | Status |
|---|---|---|---|
| 1 | Rebuild `private-chat.html` visual layer in mockup system | frontend | in scope |
| 2 | Replace 2.2 MB wallpaper with CSS pattern | frontend | in scope |
| 3 | Fix 9 s timeout; add upload progress + image downscale | frontend | in scope |
| 4 | Voice notes — wire UI to existing `/media/upload` | frontend | **no backend work** |
| 5 | Open other users' profiles from chat via `/profile/public` | frontend | **no backend work** |
| 6 | Typing + delivered/seen (section I.6 migrations) | both | in scope, additive |
| 7 | Make 4 `/marketplace.html` links relative | frontend | in scope |
| 8 | `community-chat.html` → restyle as join screen | frontend | in scope |
| 9 | `services.html` from scratch vs `/api/services*` | frontend | in scope |
| 10 | `marketplace.html` rebuild, POST bug untouched | frontend | in scope |

Backend changes reduce to **item 6 only** (two `ALTER TABLE` + one
`CREATE TABLE IF NOT EXISTS`, via the existing try/except pattern at :372-392).
Everything else the brief flagged as "broken backend" is already working.

## L. ENTRY-POINT TRACE + Q2 — reported before any redirect/entry logic is touched

### L.1 Q2 RESOLVED — the fallback is not needed. Everything is already sensible.

Verified `forum.html`'s actual rail nav (lines 296-306), not assumed:

| Nav item | href | Target | Verdict |
|---|---|---|---|
| Home | `index.html` | 36 KB | live |
| Forum | `forum.html` | current | live |
| Chat | `private-chat.html` | 63 KB | live |
| Marketplace | `/marketplace.html` | 15 KB | live but **absolute** (item 7) |
| Services | `services.html` | 2.2 KB | live (being rebuilt, item 9) |
| **Learning** | `institute.html` | 1.8 KB | **already correct — matches proposed fallback exactly** |
| **Opportunities** | `skills.html` | 8.2 KB | **already sensible — do not touch** |
| Entertainment | `games.html` | exists | live |
| Support | `report.html` | exists | live |

- **Learning** already points at `institute.html` — the exact fallback I proposed. No change.
- **Opportunities** points at `skills.html`, which is a real 8 KB page backed by the
  live `/api/skills` endpoints. Better than "coming soon." No change.
- **Events** — does not exist in `forum.html` at all (0 occurrences). It appears only in
  the mockup's sidebar. Nothing to fix; nothing to break.
- **Daily Pulse** — already implemented as an explicit *"Coming Soon"* card
  (line 426) with a `comingSoon('Daily Pulse')` handler (line 1171). Already exactly
  the proposed fallback, done deliberately.
- **Trending Topics** — already live, and better than proposed: `renderTrending()`
  (line 1183) ranks **real `#hashtags` extracted from actual feed posts** via a Unicode
  regex, plus a working Trending tab. Not a stub.

**Net Q2 result: zero changes required.** Per "apply the fallback only where they're
dead links or placeholders" — none are. Reporting as instructed.

### L.2 ENTRY-POINT TRACE — and it's worse than the "chat.html vs forum.html" tension

Traced the full chain. The real front door is the MikroTik router, not any HTML file.

**`site/mikrotik/SETUP.txt`:**
- L6: "FamilyHub server: local LAN server at `http://familyhub.chat:8080/` (192.168.6.191:8080)"
- L5: "Community Chat does not require a hotspot ticket."
- `.rsc` L154: firewall rule *"allow unauthenticated FamilyHub chat"* →
  `dst-address=192.168.6.147 dst-port=8080`

**Contradiction inside the MikroTik config itself:** DNS static entries (`.rsc` L141-142)
map `chat.bawaxict` / `chat.bawaxict.edu.net` → **192.168.6.147**, and the firewall
allow-rule also targets **.147**. But `SETUP.txt` L6-7 says the FamilyHub server is
**192.168.6.191** and that `familyhub.chat` points there. **Two different IPs for the
same service, in the same shipped config.** One of them is wrong. This is exactly the
kind of thing that makes the portal "randomly not load" on the LAN.

**Where users land:** hotspot → `familyhub.chat:8080` → that's `server.py`
(`PORT = 18080`, proxied/NATed to 8080), whose `/` route serves **`backend/chat.html`**
— the 57 KB backend copy, not `site/chat.html` (43 KB). So on the hotspot path, the
Cloudflare `site/` tree is not what's being served at all.

**Answering your two questions directly:**

1. **What does `login.html` redirect to?** → `index.html` (line 168). But see L.3 —
   `login.html` cannot succeed at all, so this redirect never fires.
2. **Does any live path reach `forum.html`/`private-chat.html`?** → **Yes, but only one,
   and it's fragile.** The only route in is `community-chat.html` (line 19):
   `location.href='forum.html?preferredName='+...`. And `index.html`'s sidebar links to
   `community-chat.html`. So: `index.html` → `community-chat.html` → `forum.html` →
   `private-chat.html`. Meanwhile `chat.html` (both copies) is what `server.py` serves at
   `/`, and it *intercepts* clicks on `community-chat.html` links, redirecting to
   `/familyhub/` (site/chat.html:378) — so on the hotspot path users are actively
   steered **away** from `forum.html` and into the old `chat.html`.

**Conclusion: your framing is correct.** Real hotspot users land on `chat.html` and
in practice never see `forum.html`. The task is therefore **"make `forum.html` /
`private-chat.html` the actual destination of the login flow, then retire `chat.html`'s
role"** — not "retire chat.html." Retiring it first would delete the only page the
hotspot actually serves and take the portal down.

### L.3 NEW BLOCKER — `site/login.html` is completely non-functional

Tested live against `server.py`. Three independent breakages, any one fatal:

| # | `login.html` does | Backend reality | Result |
|---|---|---|---|
| 1 | `GET /check-username?name=` (L127) | endpoint does not exist (0 hits in server.py) | **404** |
| 2 | `POST /join {preferredName}` (L157) | handler reads `data.get('username')` (:1107) | **400** "Use 3–24 letters…" |
| 3 | omits `client_id` entirely | required (:1110) | **400** "Missing device identity" |
| 4 | checks `res.ok && data.success` | backend returns `{"ok":true,...}` — no `success` key | would fail even if 1-3 were fixed |

Verified: `POST /join {"preferredName":"Charlie"}` → `400`. Same call with
`{"username":"Charlie","client_id":"..."}` → `200 {"ok":true,...}`.

`login.html` also hotlinks `https://picsum.photos/id/1015/800/600` as its hero — a
third-party image that is **blocked pre-authentication on a captive portal**, so the
hero is blank exactly when it's needed.

This is mockup **panel 7** ("Connected / Success"), so it is in scope. It needs the
join contract fixed (`username` + `client_id`, read `ok` not `success`), the dead
`check-username` call removed or an additive endpoint added, and the hotlinked hero
replaced. **Flagging rather than silently fixing**, since "make login work" enlarges
scope beyond a visual rebuild.

### L.4 `status.html` (panel 2/7) is a 1.2 KB stub

Hardcoded "12 people active in the community right now" — a fabricated number, the kind
of mock data explicitly banned. Real source exists: `/api/members` + `online_for()`.
Its CTA goes to `index.html`. Under the corrected entry model it should go to
`forum.html`. In scope as panel 2/7.

### L.5 Recommended entry-point fix (NOT applied — awaiting your call)

1. `status.html` / `login.html` success CTA → `forum.html` (not `index.html`)
2. Remove `chat.html`'s click-interceptor (site/chat.html:378) that diverts to `/familyhub/`
3. Point `server.py`'s `/` route at the new canonical feed **(backend file change —
   explicitly confirming with you first, per the file-safety rule)**
4. Only then retire `chat.html` + `backend/chat.html`
5. Reconcile the .147 vs .191 MikroTik IP contradiction — **router config, needs you**

Steps 3 and 5 are the ones I will not touch without explicit approval.

---

## M. Known issues — tracked, explicitly NOT in scope

- Recovery code is displayed once at join with no re-display path; miss it and the
  identity is unrecoverable for 21 days (`/api/join` returns `recoveryCode` only on
  first create).
- SSE holds one SQLite connection per client for 55 s, waking 73×/min (K.8).
- `ONLINE_WINDOW` 45 s vs SSE 55 s presence flicker — resolved incidentally by item 6
  (presence driven off heartbeat).
- Marketplace `do_GET`/`do_POST` handler bug (section D) — confirmed out of scope.
- MikroTik `.rsc` DNS/firewall points at 192.168.6.147 while `SETUP.txt` documents
  192.168.6.191 (L.2).

## N. BUILD COMPLETE — what changed, and the `/` routing decision

### N.1 The `/` routing decision (L.5 step 3) and why

`server.py`'s `/` now serves **`forum.html` directly**, not a branded `index.html`.

Reasoning: `index.html` is a static link-out shell (per I.3) that makes one API call.
Routing `/` there would mean a hotspot user pays a full page load to reach a menu whose
only real destination is the feed — two loads before seeing content, on the slowest
link in the system. The mockup's panel 1 *is* the feed, so the feed is the landing
surface. `index.html` is kept and still reachable, it is just no longer in the
critical path.

Legacy paths are preserved rather than 404'd:
- `/chat`, `/chat.html` → **302 → `/`** (bookmarks, printed tickets, walled-garden entries)
- `/private-chat.html`, `/forum.html` → served directly
- `/discover.html`, `/services.html`, `/login.html`, `/status.html`,
  `/community-chat.html`, `/marketplace.html`, `/index.html` → added to the additive
  page whitelist (no directory traversal; same guarded pattern as the old static map)

Verified live: `/` → 200, `/chat.html` → 302 → `/`, all nine pages → 200.

### N.2 Backend changes (all additive)

| Change | Location | Nature |
|---|---|---|
| `delivered_at`, `seen_at` on `private_messages` | migration list | `ALTER TABLE`, try/except |
| `typing_state` table + index | schema block | `CREATE TABLE IF NOT EXISTS` |
| `TYPING_TTL_SECONDS = 8` | constants | new |
| `GET /api/typing`, `GET /api/receipts` | do_GET | new routes |
| `POST /api/typing` | do_POST | new route |
| `POST /api/read` now sets `seen_at` + emits `private.seen` | existing route | additive |
| `GET /api/private` marks `delivered_at` | existing route | additive |
| `GET /api/check-username` | do_GET | **backfills the 404 login.html was already calling** |
| typing sweep | `cleanup()` | one DELETE |
| `/` route + page whitelist | `_do_GET` | routing only |

No table dropped, no column altered, no existing route restructured. `server.py` is
still `server.py`. Marketplace `do_GET`/`do_POST` bug untouched, as agreed.

Verified end-to-end against a live server: sent → `✓`, recipient fetches → `✓✓`
delivered, recipient opens → `✓✓` seen (green), and typing appears/expires on TTL.

### N.3 Pages: full rebuild vs targeted fix

**Full visual rebuild (new file, mockup system):**
`private-chat.html`, `services.html`, `community-chat.html`, `login.html`,
`status.html`, `discover.html` (new).

**Targeted fix only:** `forum.html` (absolute links → relative; "Forum" nav →
"Home"/"Discover"; already mockup-built per I.1), `index.html` (absolute links →
relative), `marketplace.html` (links verified; already calls the real endpoint).

**Retired:** `site/chat.html` + `backend/chat.html` (both copies, verified zero inbound
references from any page), and `assets/wallpapers/private-chat-wallpaper.png` (2.2 MB).

### N.4 Shared design system

New `site/css/familyhub-mockup.css` — one token set and component library for all nine
pages. Font is a **self-hosted system geometric-sans stack, not the Google Fonts CDN**:
the CDN is blocked pre-auth on a captive portal, so every page that linked it was
rendering in a fallback face at exactly the moment it mattered.

### N.5 Chat fixes, specifically

- **Timeout:** was `x.timeout=9000` on every request. Now 12 s for JSON, **180 s for
  multipart**, with `xhr.upload.onprogress` driving a real progress bar.
- **Client-side downscale:** images over 1600 px / 1.2 MB are re-encoded to JPEG q0.82
  before upload. Phone photos are routinely 4–6 MB against a 2 MB server cap.
- **Background:** 2.2 MB PNG replaced with a CSS dot-grid + gradient in palette.
- **Voice notes:** `MediaRecorder` → the *existing* `/media/upload`, which already
  accepts `audio/webm|ogg|mp4`. Zero backend change, as established in K.1.
- **Profile from chat:** tapping the thread header opens the profile panel via
  `/profile/public` — the endpoint `forum.html` already used and `private-chat.html`
  never called.
- **No WhatsApp palette remains:** `#075e54`/`#d9fdd3`/`#25d366` all gone.

### N.6 Link sweep (item 7, expanded as instructed)

Re-ran the absolute-vs-relative sweep across every touched page: **zero absolute
`.html` links remain** anywhere in `site/`. All nav destinations resolve to files that
exist. Out-of-scope pages (`institute.html`, `skills.html`, `games.html`,
`report.html`) are linked correctly and left undesigned, as agreed.

### N.7 MikroTik — documentation corrected, router config untouched

Per your instruction, took `.147` as truth (two structural entries beat one prose line)
and corrected only the `SETUP.txt` prose. `.rsc` DNS/firewall entries untouched.
**Flagging for hardware confirmation.**

### N.8 Bugs found and fixed during preview testing (standing authorization)

1. **`forum.html` sidebar covered the whole screen on mobile — feed unreachable.**
   CSS source-order bug: `@media(max-width:860px){.rail-left{display:none}}` was
   declared at line 49, *before* `.rail-left{display:flex}` at line 52. Equal
   specificity, so the later rule won and the nav rail rendered full-height on every
   phone, hiding the feed entirely. Moved the media query after the base rule.
   Verified: computed `display` is now `none` at 390 px, `flex` at 1440 px.
   This shipped on the live site and nobody had caught it.

2. **Conversation list showed "Conversation started" for media-only messages.**
   A photo/voice message has an empty `message` field, so the placeholder fired.
   Now shows "Attachment".

3. **Composer icons were emoji glyphs.** Replaced with inline SVG. Emoji fonts are not
   guaranteed on low-end Android, which is the actual device profile here — the mic and
   image buttons would have rendered as tofu boxes.

### N.8b FIX — clicking Chat showed the join gate instead of opening chat

**Reported:** clicking Chat re-prompts for a username instead of opening the chat page.

**Reproduced** (headless, two contexts):

| Case | Before | After |
|---|---|---|
| A · live cookie → click Chat | opened chat | opened chat |
| B · cookie gone, **device still bound** | **bounced to `community-chat.html`** | **opens chat, 3 conversations** |
| C · genuinely new device | join screen | join screen → returns to chat |

**Cause was mine.** My rebuilt `private-chat.html` bootstrapped with a single check:

```js
api('/session').then(function(s){
  if(!s.authenticated){ location.href='community-chat.html'; return; }
```

`forum.html` never did that. It falls back to `GET /device?client_id=` and, if the
device is still bound inside its 21-day identity, silently re-joins. So the feed
recognised a returning user while chat sent the same user back to the gate — the
session cookie is `Max-Age`-bound and expires well before the 21-day device identity,
so this hits **every returning user whose cookie lapsed**, which on a shared hotspot
phone is routine.

**Fix** — `private-chat.html` now uses the same three-step chain as the feed:
1. live cookie session → start
2. else `GET /device?client_id=` → if bound, silent `POST /join` with the bound
   username + `client_id` → start
3. only a genuinely unknown device sees the join screen

Two supporting fixes:
- **`clientId()` must use the same `localStorage` key as `forum.html`**
  (`bawaxict_client_id`, `browser-` prefix). A different key would mint a second
  identity per device and silently defeat the binding.
- **Return-to-origin:** the gate is reached as
  `community-chat.html?next=<page>`, and `community-chat.html` now honours it, so
  joining from chat returns to *chat* (deep link `?with=` preserved) rather than
  dumping the user on the feed. `nextTarget()` only accepts relative
  `*.html` targets — absolute URLs are rejected, so `next=` is not an open redirect.
- `community-chat.html` got the same silent re-auth, so a returning user never sees
  the gate at all.
- Transport failures are now tagged `err.netFail` so "server unreachable" shows the
  offline notice instead of being misread as "not signed in" and triggering a redirect.

**Security unchanged — verified, not assumed:** claiming a username bound to a
*different* device still returns `409 recoveryRequired`; `/device` on an unknown
`client_id` returns `{"bound":false}` and leaks nothing. This reuses the existing
device-binding contract rather than loosening it.

### N.8c FIX — "join the community first" when posting, after confirming a username

**Reported:** creating a post keeps saying join community first, even after confirming
the username.

**Cause — a dead end in the existing gate, not in the post handler.** `POST /api/community`
was never the problem; the user was never actually joined.

`doJoin()` handled the "username already taken" case (`409 recoveryRequired`) with a
blocking `window.prompt()`. The gate markup itself contained exactly **one** control —
`gateJoinBtn`. There was no recovery-code input, no close button, no "use another name"
escape. So:

1. User types a name that already exists → backend correctly returns 409.
2. `gateError` shows *"Enter its recovery code to continue"* — but **there is nowhere
   to enter it.** The `prompt()` is dismissed by any user who taps outside it (and is
   suppressed outright by some Android webviews).
3. `joined` stays `false`, and `#gateOverlay` stays `display:grid` over the whole page.
4. Every composer action then hits `if(!joined){ showGate(); return; }` — hence
   "join community first", forever. The overlay also intercepts pointer events, so the
   composer could not even be clicked.

Verified before the fix: gate controls enumerated as `['gateJoinBtn']`, overlay
`display:grid` after a 409, composer unreachable.

**Fix (all in `forum.html`, no backend change):**
- Added an **inline recovery-code field** to the gate card, revealed only on a 409.
- Added a **"choose a different name"** escape link that resets the gate.
- Typing in the name field abandons an in-flight recovery attempt automatically.
- A **wrong** code now reports *"That recovery code is not correct for X"* and keeps the
  field open, instead of silently re-rendering an empty box.
- The join button relabels to *"Continue as <name>"* in recovery mode, and Enter works
  in both fields.
- Removed the `prompt()` entirely.

**Verified end-to-end** (fresh browser contexts, real backend):

| Path | Result |
|---|---|
| Brand-new name → join → post | posts, appears in feed |
| Taken name → correct recovery code → post | joins, posts, appears in feed |
| Taken name → **wrong** code | clear error, field stays open, recoverable |
| Taken name → "choose a different name" → new name → post | escapes cleanly, posts |

Security unchanged: the 409 + recovery-code contract is still enforced server-side.
This only gives the user a way to *satisfy* it.

### N.8d FIX — Create button dead, and "join community first" in the preview iframe

**1. Create button unresponsive — real CSS bug, now fixed.**

`.hero::after` is a 220×220px decorative circle positioned `right:-40px; top:-60px`
— directly on top of the Create button — with no `pointer-events:none`. The
pseudo-element sat above the button in paint order and **swallowed every click**.
Confirmed by the browser itself: `<div class="hero"> intercepts pointer events`.

Fixed in `forum.html` by adding `pointer-events:none` to the pseudo-element and
`position:relative;z-index:1` to `.hero-text,.hero-cta`. Swept for the same pattern
across all pages and found one more latent instance in `index.html` — fixed too.

Verified: Create opens the composer, the post submits, and it appears in the feed.
Also verified the mobile `+` FAB and the inline composer on a 390px viewport.

**2. "Please join the community first" — environment, not application code.**

That string is `server.py:1486`, a **401** returned when `auth()` cannot resolve a
session. `auth()` resolves it from the `fh_session` cookie
(`HttpOnly; SameSite=Lax`).

Reproduced the report exactly by loading the app inside a **sandboxed preview
iframe** (`sandbox="allow-scripts"`, which is how the in-app file viewer renders it):
joining fails with `Failed to fetch`, no cookie is stored, and every subsequent write
returns the 401. Adding `allow-same-origin` to the same iframe makes it work
immediately — gate closes, posting succeeds.

So the app is fine; a cookie-authenticated app simply cannot work inside an
opaque-origin iframe, because the browser treats it as third-party and blocks
cookie storage. **Open the preview in a real browser tab, not the embedded viewer.**
This does not affect production: Cloudflare Pages serves the pages top-level on the
same origin that `_worker.js` proxies `/api/familyhub/*` from, so the cookie is
first-party there.

No code change made for (2) — changing it would mean weakening the cookie
(`SameSite=None`), which the security constraint forbids and which production does
not need.

### N.8e Entertainment zone cleanup (as requested)

- Removed the entire **"Already connected?"** block from `games.html`: *Community
  Chat*, *Internet Tickets* and *Portal Home* — none belong in an arcade page.
- **Internet Tickets** promoted to the main menu, added to `forum.html` (the real
  home), `index.html`, `discover.html` and `services.html` so the nav stays
  consistent across the set.
- Registered `/tickets.html` and `/games.html` in `server.py`'s page whitelist so the
  new nav entry resolves on the Oracle origin too, not only on Pages.
- Verified: `games.html` contains none of the three removed items; the Internet
  Tickets link resolves to the live `tickets.html`.

`tickets.html` itself was not redesigned — it is outside the agreed nine-page set.
Flagging it for a later pass: it is now reachable from the main nav, so its styling
is visibly older than the pages around it.

### N.8f Trending / Live activity / Invite & Earn

**1. Trending topics — confirmed already clickable, no change needed.**
Each row is a real `<button class="trend-row">` wired to `setTagFilter()`.
Verified live: clicking `#techtalk` filtered the feed from 6 posts to its 2,
highlighted the row, and rendered a dismissible "Showing #techtalk ✕" banner.
Clicking the same row again clears the filter. Left exactly as-is.

**2. Live activity now collapses after 3 entries.**
`renderActivities()` previously rendered up to 10 cards flat, which pushed the rest
of the feed far down the page. Now: first 3 render normally, the remainder go into a
hidden `#activityMore` block behind a **"Show N more updates ▾" / "Show less ▴"**
toggle. Nothing is dropped — the extra entries stay in the DOM.

The expanded/collapsed state is held in `activityExpanded`, declared alongside
`lastActivitySig`, **specifically so the 8-second feed refresh does not snap an
expanded list shut under the user.** Verified: expanded to 10 cards, waited through a
full refresh cycle, still expanded; collapse returns to 3.

**3. Invite & Earn — new page `site/invite.html`.**
The card's "Invite Now" pointed at `community-chat.html` (the join gate), which made
no sense for an existing member. It now opens a real share sheet.

- **Share targets are plain web-intent URLs** — no SDKs, no trackers, no CDN scripts.
  That is deliberate: third-party scripts are blocked pre-authentication on a captive
  portal, so an SDK-based share widget would render dead on the very network this
  runs on. WhatsApp uses `https://wa.me/?text=` (opens the app on mobile, WhatsApp Web
  on desktop); also Telegram, Facebook, X, plus `sms:` and `mailto:` which open the
  phone's native apps directly.
- `navigator.share` is offered as **"More apps"** but only rendered when the browser
  actually supports it — on Android that surfaces every installed app, which is the
  best outcome on the phones this is used on.
- The invite URL is derived from `location.origin`, so it stays correct on the hotspot
  LAN and on the public domain without being hardcoded.
- **Reward progress reads the real `/api/rewards/mine`** (`chatSeconds`,
  `thresholdSeconds`, `label`, `eligible`). **No referral count is shown** — the
  backend has no referral table, and displaying an invented "3 friends joined" figure
  would be exactly the fake data the brief forbids. Adding real referral attribution
  would need a new table and an invite-token column; flagged as a possible follow-up,
  not built unprompted.
- Registered in `server.py`'s page whitelist so it resolves on the Oracle origin too.

### N.9 Preview verification

Ten page states captured at 2× DPR via headless Chromium against the running stack.
**Zero failed network requests and zero console errors across all ten.** Confirmed
rendering with real seeded data: delivered/seen ticks, online dots, unread badges,
image + voice attachments in-thread, trending hashtags derived from real posts,
service catalog + "My requests", and the real member/post counts on `status.html`
(5 active, 5 members, 6 posts — no fabricated "12 people").

## H. Status

- [x] Repo cloned, branch `mockup-rebuild` created
- [x] Inventory + endpoint map written (this file)
- [ ] **BLOCKED on Q1–Q4** — no page code written yet, per "do not start building
      until this plan is written" + "stop and ask rather than proceeding"

---

# POST-LAUNCH FIXES (branch `post-launch-fixes`, from `6e75ea1`)

## P1. Site root now serves the feed

`_redirects` added: `/ -> /forum` (302), plus `/home`, `/feed`, and the
retired `/chat` paths.

Chose a redirect over copying `forum.html` into `index.html`: forum.html is
105KB, so duplicating it means two copies of the whole feed app to keep in
sync forever, and ~10 pages still link to `index.html` directly so the file
has to keep working. `_routes.json` scopes the Worker to `/familyhub/*`, so
the root is handled by the static asset layer — exactly what `_redirects`
governs. 302 not 301, because a permanent redirect is cached hard and is
painful to reverse.

## P2. Post-connect redirect

Three independent things were sending users to the wrong page:

1. `tickets.html` is the hotspot landing page and its "Enter FamilyHub Free",
   "Enter Community Free" and "Explore FamilyHub" buttons all pointed at
   `index.html`. Now `/forum`. ("Back to Portal" and the brand logo still go
   to index.html — intended.)
2. `/ip hotspot profile` had **no** `login-page-redirect`, so RouterOS sent
   the user to whatever URL their phone was probing. Now set to
   `https://bawaxict-familyhub2.pages.dev/forum`.
3. The walled garden had **no HTTPS rule at all** — only two 8080/tcp
   entries. The hosted site was unreachable pre-ticket, so the redirect
   would have failed even once configured. Added `dst-host` entries for the
   Pages hostname plus explicit 443/tcp.

**Needs hardware verification.** `login-page-redirect` is RouterOS-version
sensitive; `SETUP.txt` documents an `alogin.html` meta-refresh fallback. The
`HOTSPOT1` html-directory lives on the router and is not in this repo, so if
those login pages hardcode a destination they must be updated there.

## P3. Router status page rebuilt

New `site/mikrotik/hotspot-html/status.html`, matching `site/status.html`.

It is served by the **router**, so it cannot link `css/familyhub-mockup.css`
— the router does not serve that file and a pre-auth client cannot reach a
CDN. Tokens are copied inline; the page is fully self-contained. Uses real
RouterOS substitutions (`$(username)`, `$(uptime)`, `$(bytes-in-nice)`,
`$(bytes-out-nice)`, `$(ip)`, `$(link-logout)`). README covers install and
verification.

## P4. Chat live delivery — root cause and fix

**Not** a URL mismatch. `private-chat.html` connects to `/stream` via
`config.js`, `server.py` serves `/api/stream`, and the worker maps them
correctly. Verified the server pushes a frame ~2s after a message is sent.

The bug: `server.py` closes every SSE stream after `SSE_MAX_SECONDS` (55s)
**by design**, so the client must reconnect for the life of the page. It only
reconnected from `es.onerror`, and a clean server-side close does not
reliably fire an error event. After the first 55s the stream went silent
permanently — matching "only appears after a manual refresh".

Measured before: t=0..52s delivered <1s; **every** message from t=64s onward
not delivered; one `/stream` connection for the session; no reconnect.

Fixed by recycling the stream at 50s (ahead of the server cutoff), adding a
30s idle watchdog (server keepalive is 15s), reconnecting with bounded
backoff, deduping on `lastEventId`, refreshing on tab focus and `online`, and
**always** running a 6s `/events` poll as a safety net.

Measured after: **8/8 within 1s** across 100s with a clean reconnect at 50s.
With `/stream` blocked outright at the network layer, **3/3** still arrive via
the poll in 3–6s.

## P5. Mobile optimization

Audited all 12 rebuilt pages at 360x740 measuring real metrics.

Structural bugs found:
- `index.html` had the same CSS source-order bug already fixed in
  `forum.html` (`@media` hide declared *before* `.rail-left{display:flex}`)
- `.rail-right` had the identical bug in **both** `index.html` and `forum.html`
- `index.html` could be dragged sideways: `.main` keeps 1px borders from the
  desktop 3-column shell, making the body 2px wider than the viewport
- `services.html` / `discover.html` put search + CTA on one row; at 360px the
  input collapsed to ~60px (now 332px, stacked)
- `services.html` filter chips had `overflow-x:auto` but shrinking children,
  so the last chip spilled the page instead of scrolling
- `status.html` 3-column grids could not hold readable label text at 360px

Systemic, in `css/familyhub-mockup.css`: all form controls 16px on small
screens (under 16px iOS Safari zooms the page on focus and the zoom persists
after blur), 44x44 minimum touch targets, scrollable filter strips,
`overflow-wrap:anywhere`, and `prefers-reduced-motion` support.

Result: **0px overflow and no sideways drag on all 12 pages**, no JS errors.
No page locks zoom (`user-scalable=no` appears nowhere).

## Still open

- MikroTik `.147` vs `.191` — confirm on hardware.
- `tickets.html` is in the main nav but not redesigned; its layout is older
  than the pages around it (mobile text/tap sizing was fixed here).
- Marketplace `do_GET`/`do_POST` bug — still deliberately untouched.

---

# DEVICE-TESTING FIXES (branch `device-fixes`, from `840a4fe`)

## D1. Mobile navigation — full menu now reachable

The bottom bar has five slots; the desktop rail has ten destinations. Measured
on a 360x740 device: only Home, Chat, +, Market and Me existed, leaving
**Discover, Services, Learning, Opportunities, Entertainment, Internet Tickets
and Support unreachable on a phone entirely**.

Replaced the Market slot with **More**, opening a bottom-sheet drawer with all
ten rail destinations plus Invite & Earn and Rewards. Marketplace is still one
tap, now inside the drawer.

Drawer icons are inline SVG, not the rail's decorative glyphs: those
characters have no glyph in common Android fonts and rendered as tofu on the
test device — Entertainment literally displayed as `SOH`, Marketplace as a
club suit.

Verified: all 10 items present, every href resolves (no 404s), nothing under
44x44, 0px overflow, drawer suppressed above 860px.

## D2. Logout — was silently re-authenticating the same account

`POST /api/logout` was fine; the client was the problem. It cleared the
session cookie and three localStorage keys but **not `bawaxict_client_id`**.
Bootstrap then falls back to `GET /device?client_id=...` and silently re-joins
the same account.

Measured before: tap Logout → still Amaka; the gate never appeared; typing a
different name was impossible; reload → still Amaka. Logout was completely
non-functional for its actual purpose (handing the phone over / switching
ticket).

Fixed by also releasing the device binding, confirming first (the account
needs its recovery code to come back), and `location.replace()` to the join
screen so Back cannot restore the authenticated view. `community-chat.html`
was hardened too: it called `clientId()`, which *mints and stores* a new id as
a side effect, so it could re-create a binding for a logged-out device.

Measured after: A → logout → join as B → `/api/session` reports **B**. The
old account is untouched and keeps its 21-day reservation.

## D3. Clicking a name — already worked; no change needed

`openAuthor()` is already wired to author names and avatars in the feed,
comments and search, already calls `/profile/public` and
`/profile/public/posts`, and the Follow button already posts to
`/api/follow` / `/api/unfollow` (both exist server-side).

Verified on device rather than assumed: tapping "Ngozi" opens the modal with
avatar, online state, public details, recent posts and a Message button;
Follow flips to "✓ Following" and `/profile/public` then reports
`following: true`; tapping again unfollows and the server reports `false`.

**Reporting this as working rather than inventing a change.** If the intent is
a full-page profile rather than a modal, that is a design decision worth
deciding explicitly — say the word and I'll build it.

## D4. Profile picture beside the greeting

Added a 52px (46px on mobile) avatar button next to "Good morning, [name]".
Uses the `profilePhotoId` that `/api/profile` already returns — no new
endpoint. That field is nulled server-side when the user has hidden their
photo, so the privacy setting is respected automatically. Falls back to the
initial-letter avatar used elsewhere, and tapping it opens the existing
profile panel.

Verified with a real uploaded PNG (renders the photo) and with no photo
(renders the initial).

## Regression

All 12 pages at 360x740 **and** 1440x950: 0px overflow, no sideways drag, no
JS errors. Marketplace still returns 502 from the known out-of-scope
`do_GET`/`do_POST` bug.

---

# FULL PROFILE PAGE (branch `profile-page`, from `device-fixes`)

Replaces the author modal with a real page: **`profile.html?user=<username>`**,
plus a **`/u/<username>`** short form on both deployments.

## Why a query parameter rather than a path segment

Cloudflare Pages serves static files, so a true `/u/<name>` route would need
either a Function or a Worker route, and `_routes.json` deliberately scopes the
Worker to `/familyhub/*` and `/api/familyhub/*`. `profile.html?user=` needs
neither, works identically on Pages and on the Oracle origin, and matches the
flat-file convention every other page already uses.

`/u/<name>` is layered on top as a 302 for nicer sharing — a Pages `_redirects`
placeholder rule and an equivalent handler in `server.py`. The query form stays
canonical so nothing breaks if the short link is ever dropped.

## Data

No new endpoints. The page uses what already existed:

| Endpoint | Used for |
|---|---|
| `GET /profile/public?username=` | avatar, location, following flag |
| `GET /profile/public/posts?username=` | recent posts; honours `hidden` |
| `GET /profile` | identifies "me" |
| `GET /members` | live online state |
| `POST /follow` / `/unfollow` | follow button |

## States handled

- **Own profile** — `profile.html` with no `?user` shows you, with *Edit
  profile* / *Invite friends* instead of Follow / Message.
- **Unknown user** — proper "Member not found" state, not a stuck spinner.
- **Hidden profile** — locked state, honouring the server's `hidden` flag.
- **Signed out** — a shared public profile still renders; the posts section
  prompts to join, because `/profile/public/posts` requires a session.
- `?user=`, `?username=` and `?u=` all accepted so hand-written links resolve.

## Entry points rewired

`openAuthor()` now navigates instead of opening an overlay, and remains the
single call site for feed posts, post avatars, comment authors and search
results. Also rewired: the mobile **Me** tab (previously a small floating
settings panel) and the **private-chat thread header**. Editing still lives in
the header-avatar panel, which the profile page links to.

The modal was **removed**, not left dormant — markup, styles and ~90 lines of
JS. Leaving a second, diverging implementation of the same feature in place is
how the two drift apart.

## Verification

360x740 (touch, Android UA) and 1440x950:

- clicking a name in the feed navigates to a real URL, not an overlay
- follow → `✓ Following` and `/profile/public` reports `following: true`;
  unfollow reverses both
- direct URL loads cold; `/u/<name>` 302s correctly on Pages *and* origin
- Me tab → own profile; chat header → peer profile; Message → the thread
- all 14 page states: 0px overflow, no sideways drag, no JS errors, no
  sub-44px tap targets

## Note on the backend copies

`backend/forum.html` and `backend/private-chat.html` had to be re-synced: an
earlier commit in this branch staged a stale `backend/forum.html` still
containing the removed modal, which would have left LAN hotspot users on the
old overlay while Pages users got the new page. Both now match `site/` exactly.
