# FamilyHub Test — Android WebView shell (test build)

A native Android `.apk` that wraps the **existing FamilyHub web app** in a
single-Activity WebView. Nothing is rebuilt natively: this folder contains only
a browser shell whose job is to load the hotspot-facing site and behave, towards
that site, exactly like a hotspot browser session.

Status: **test build only.** Placeholder name (`FamilyHub Test`) and placeholder
icon. No final launch branding appears anywhere in this project.

This folder is self-contained. It does not read from, reference or modify
anything in `site/`, `backend/`, `functions/` or `CLOUDFLARE_DEPLOY/`.

---

## 1. Building it

### Where the build has to run

The APK **cannot be compiled from the checked-out workspace itself**: the
Android SDK (`android.jar`, `aapt2`, `apksigner`), the Gradle distribution and
the Android Gradle Plugin are only distributed from `dl.google.com`,
`services.gradle.org` and `repo1.maven.org`. From the environment this project
was written in, those hosts are unreachable (egress is allowlisted to
`github.com`, `registry.npmjs.org` and PyPI), so there is no local toolchain and
no way to obtain one. The build therefore runs on a **GitHub Actions runner**,
which has unrestricted network access. See
`.github/workflows/android-test-apk.yml`.

If you build on your own machine instead (Android Studio, or any machine with a
JDK 17 + Android SDK), the same commands work locally.

### One-click (GitHub Actions)

1. Repository → **Actions** → *Android test APK (FamilyHub Test)* → **Run workflow**.
2. Leave the defaults, or change:
   - `start_url` — the URL the WebView loads (default
     `https://bawaxict-familyhub2.pages.dev/forum`).
   - `app_name` — launcher label (default `FamilyHub Test`).
   - `publish release` — tick to also get a **direct download link** (a GitHub
     Release) that can be shared to phones without a GitHub login.
3. Download `FamilyHubTest-<version>-debug.apk` from the run's **Artifacts**
   (retention 30 days), or from the Release.

The workflow builds with `assembleDebug`: the output is a plain **`.apk`**
signed with Gradle's auto-generated **debug key**. There is no `.aab`, no Play
Store listing and no release signing material anywhere in this project.

It also runs automatically on every push/PR that touches `android-test/`, so the
folder can't rot silently.

### Locally

```bash
cd android-test
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Point the WebView at a different origin without editing any file:

```bash
./gradlew assembleDebug -PstartUrl="http://chat.bawaxict.edu.net:8080/"
./gradlew assembleDebug -PappName="FamilyHub Test v2"
```

Requires JDK 17 and an Android SDK with platform 35 / build-tools 35.0.0.
`local.properties` (`sdk.dir=...`) or `ANDROID_HOME` must be set; neither is
committed.

### Sideload

Share the `.apk` (WhatsApp, Bluetooth, USB). On the phone, opening it prompts
"install unknown apps" — allow it once for the file manager/browser that opened
it, then install. No Play Store involved.

> **Installing over a previous build:** the default debug key is generated per
> build machine, so an APK built by Actions will not upgrade one signed by a
> different key. Uninstall the old "FamilyHub Test" first, or build every round
> with one key via `-PuseInternalTestKey=true` + a local (gitignored)
> `keystore.properties`. See `app/build.gradle`.

---

## 2. Hotspot-session parity (why the shell looks the way it does)

The wrapped app must behave like a hotspot browser session, so the same
cookies/session the browser holds are in play. Each requirement and where it is
handled:

| Behaviour | Where |
|---|---|
| Loads the hotspot-facing origin | `app/build.gradle` (`START_URL`), default = the URL the production MikroTik config redirects authenticated hotspot users to (`login-page-redirect="https://bawaxict-familyhub2.pages.dev/forum"`, also in its walled garden) |
| Persistent cookies/session | `MainActivity.configureCookies()` + `CookieManager.flush()` on page finish / pause / destroy |
| `localStorage` / `sessionStorage` (site uses both) | `WebSettings.setDomStorageEnabled(true)` |
| Looks like a browser, not a WebView | `UserAgents.asBrowser()` strips the `; wv` marker and `Version/4.0` token |
| No injected headers | nothing is added to requests anywhere — an injected `X-Hotspot-*` header would make the app a *different* client than a browser |
| Plain-HTTP hotspot portal + on-LAN server | `res/xml/network_security_config.xml` opens cleartext for exactly the hosts/IPs in `site/mikrotik/*.rsc`, keeps HTTPS strict elsewhere |
| `http://bawaxict.edu.net/status` links inside HTTPS pages | `MIXED_CONTENT_COMPATIBILITY_MODE` (the older `CLOUDFLARE_DEPLOY` skeleton used `NEVER_ALLOW`, which breaks exactly those links) |
| Photo posts / marketplace / profile photo uploads | `ShellChromeClient.onShowFileChooser` + FileProvider |
| `capture="environment"` camera inputs | `MediaStore.ACTION_IMAGE_CAPTURE` via the chooser (no CAMERA permission declared — it delegates to the camera app) |
| Voice notes in private chat | `onPermissionRequest` + runtime RECORD_AUDIO, granted only to the origin `START_URL` points at |
| `window.open` / `target="_blank"` | `setSupportMultipleWindows(false)`: they load in place instead of being dropped |
| Rotating the phone mid-conversation | `android:configChanges` on the Activity + `saveState`/`restoreState`: no reload |
| Back button | walks the site's history, like a browser tab |
| Busy-link failures | main-frame-only error screen; DNS failure on a hotspot-only host explains "join the BAWAXICT WiFi" (`Origins.isHotspotOnly`) |
| TLS problems | never proceed past an SSL error; explain instead |
| Voucher/ticket downloads | handed to the platform via ACTION_VIEW |

### Known router-side prerequisite

`site/mikrotik/BAWAXICT_MikroTik_FamilyHub_PRODUCTION_V2_FIXED.rsc` still has no
DNS/walled-garden entries for `familyhub.chat` (its DNS entries and firewall
rules point at `chat.bawaxict.edu.net` → `192.168.6.147:8080`), even though some
HTML now calls `familyhub.chat`. That is documented in
`site/HOTSPOT_WEBSITE_PARITY_FIX.txt` and is **not** something this project
changes. Practical consequence: builds pointed at `familyhub.chat` will not
resolve on the hotspot until the router config is updated; builds pointed at the
Pages URL or at `chat.bawaxict.edu.net:8080` match what the router actually
allows today.

### ⚠️ What could not be found: the "WiFi — Free" hotspot-origin detection

Before writing this, every branch in the repository was searched (including
`origin/main`, `origin/hotspot-labels-badges` and the older Arena branch) for
hotspot-origin detection or a `WiFi — Free` flag. **No such code exists in the
repo.** What does exist, and how the app relates to it:

- **`POST /api/hotspot/verify`** (`backend/server.py`, guarded by
  `BAWAXICT_HOTSPOT_SHARED_SECRET`, default empty → 403). It sets
  `users.hotspot_user`, surfaced as `hotspotUser` by `/api/session`. Per
  `MERGE_NOTES.md` round 6, no page in `site/` calls it, so `hotspotUser` is
  false for everyone today — including genuine hotspot users.
- **Round 6 hotspot labelling** (`hs-tag` "Hotspot" chips, the `tickets.html`
  banner saying plans are for BAWAXICT WiFi users and that FamilyHub itself is
  free). These are **static labels**, deliberately *not* conditional on origin —
  that decision is recorded in `MERGE_NOTES.md` ("chose labels, not conditional
  hiding").
- **The wifi pill** (`wifiDot`/`wifiTxt` on discover/invite/profile/services)
  shows session state from `/api/session` ("Connected · <name>", "Not signed
  in", "Offline"), not origin.

So the shell preserves *every mechanism that exists* — same origin, same cookie
jar, same storage, same user agent, no extra headers — and therefore also
preserves whatever an origin-based detection added later will key on. But if you
expect an existing `WiFi — Free` indicator to appear in the app, point me at the
file or branch that contains it; it is not in this repository.

---

## 3. Files

```
android-test/
├── settings.gradle / build.gradle / gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/     Gradle 8.7 wrapper (jar from the
│                                             gradle/gradle v8.7.0 tag)
├── tools/make_icons.py                       regenerates the placeholder icon
└── app/
    ├── build.gradle                          START_URL / appName as -P props;
    │                                         optional internal-test keystore
    └── src/main/
        ├── AndroidManifest.xml               permissions, with reasons
        ├── java/net/bawaxict/familyhubtest/
        │   ├── MainActivity.java             the WebView shell
        │   ├── UserAgents.java               browser-parity UA
        │   └── Origins.java                  hotspot-only host recognition
        └── res/
            ├── layout/activity_main.xml      WebView + progress + error panel
            ├── xml/network_security_config.xml
            ├── xml/file_paths.xml            FileProvider target
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap-anydpi-v26/ic_launcher.xml
            └── mipmap-*/ic_launcher.png      placeholder icon (API 24-25)
```

`.github/workflows/android-test-apk.yml` (repo root) is the CI build; it is the
only file this work adds outside `android-test/`.

## 4. Deliberate non-goals

- No native UI, no rebuild of FamilyHub screens.
- No Play Store listing, no `.aab`, no release signing.
- No final launch branding (name, icon, package identity) — this round is a test
  build; branding belongs to the official launch build.
- No changes to `site/`, `backend/`, `functions/`, router config, or the merged
  hotspot labelling work.
