# MikroTik hotspot HTML

Pages served by the **router**, from the `HOTSPOT1` html-directory configured in
`/ip hotspot profile`. They are **not** served by Cloudflare Pages.

## Why these are self-contained

A client on the hotspot before authenticating can only reach the router and
whatever the walled garden allows. So these pages must not depend on anything
external:

- no `css/familyhub-mockup.css` (the router does not serve it)
- no web fonts, no CDN, no external images, no analytics

The design tokens are therefore copied inline from `familyhub-mockup.css`. If
that stylesheet's palette changes, update these files to match.

## Files

| File | Replaces | Notes |
|---|---|---|
| `status.html` | the old plain directory-style status page at `bawaxict.edu.net/status` | rebuilt in the mockup design system |

## Installing

WinBox: **Files** → open `HOTSPOT1` → drag `status.html` in, replacing the
existing file. Or over FTP:

```
ftp <router-ip>
cd HOTSPOT1
put status.html
```

Back up the current file first:

```
/file print where name~"HOTSPOT1"
```

## RouterOS variables used

`$(username)`, `$(uptime)`, `$(bytes-in-nice)`, `$(bytes-out-nice)`, `$(ip)`,
`$(link-logout)` — all standard hotspot substitutions. They render literally
if you open the file directly in a browser; that is expected.

## Verify after upload

1. Connect a real handset, log in.
2. Visit `http://bawaxict.edu.net/status`.
3. Confirm the stats populate and **Enter FamilyHub** reaches `/forum`.

Step 3 depends on the walled-garden rules for `bawaxict-familyhub2.pages.dev`
including 443/tcp — see `../SETUP.txt`.
