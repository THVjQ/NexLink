# §20 — Element Web deployment and skinning

> **Confidence:** `REVISABLE`
> The decision to adopt rather than build is `DURABLE`. Configuration specifics
> track Element Web's release cadence.

---

## 20.1 The decision: adopt Element Web

§1.2 requires "a web client for use from a desktop computer". §2.7 left the
choice open between Element Web and something bespoke. It is resolved here:

**Element Web, deployed and configured, not forked and not rebuilt.**

Verified 2026-09-11: `element-hq/element-web v1.12.27`, released 2026-09-01.
Actively developed, and it ships Element Call bundled, which means the web
client is a full participant in the calling stack of §17 with no additional work.

What adopting it provides, at a cost of configuration rather than development:

- A complete, audited E2EE implementation with cross-signing, key backup and
  device verification — the exact machinery §7 and §8 depend on, already built.
- The second device in §11.7's SDK decision procedure and §8.4's verification
  flows, without which those chapters cannot be tested at all.
- Element Call bundled, satisfying §17 on the desktop for free.
- A recovery-key implementation to check the Android client's against (§7.4.3).

**Reversal cost: low.** A bespoke web client later is a product decision, not a
migration — accounts, history and keys are server- and client-side, not tied to
which web client rendered them. This is the cheapest decision in Part VI to
change, which is exactly why it should not be agonised over now.

### 20.1.1 What it costs

Element Web is a general Matrix client. It exposes surfaces this product does
not have — room directories, spaces, federation-shaped assumptions, integration
managers — and §20.3 turns them off. Some cannot be fully hidden by
configuration, and the honest position is that the web client will always look
slightly more like a general Matrix client than the Android app does.

That is acceptable for a desktop companion. It would not be acceptable as the
primary surface, and it is a reason to revisit this in a later phase if the web
client becomes more than a convenience.

---

## 20.2 Deployment

Element Web is static files. No server-side component, no database.

On Willard, as a custom app (§22 covers the pattern in detail):

| | |
|---|---|
| Image | `vectorim/element-web` pinned to an exact tag, never `:latest` |
| Port | `8061` (§21.4) |
| Config | `config.json` bind-mounted from `Pool1-MAIN/social/element-web/` |
| Public URL | `https://nexlink.thvjq.com.au` via the existing cloudflared tunnel |

Pinning the tag is not fussiness. Element Web is a client for an E2EE service;
an unattended `:latest` pull is an unreviewed change to the code that handles
users' keys. Upgrades are deliberate, and §29 gives them a runbook.

### 20.2.1 Why this one is safe to put on Willard

Unlike the SFU (§17.4), Element Web is pure HTTP and is served perfectly well
through an outbound-initiated tunnel. Nothing about CGNAT affects it.

---

## 20.3 Configuration

`config.json` is where this product's shape is imposed. Annotated:

```jsonc
{
  "default_server_config": {
    "m.homeserver": {
      "base_url": "https://nexlink.thvjq.com.au",
      "server_name": "nexlink.thvjq.com.au"
    },
    // §1.3 — no third-party identity server, ever. An EMPTY object, and it must
    // live inside default_server_config: an earlier draft of this chapter had a
    // second top-level "default_server_config" key, which is invalid JSON and
    // silently takes only the last one.
    "m.identity_server": {}
  },

  // §2.6 — no federation, so no other server may be chosen
  "disable_custom_urls": true,

  // §2.5 — registration is invite-only and happens in the app (§9.6)
  "disable_guests": true,
  "disable_3pid_login": true,

  "brand": "NexLink Social",
  "defaultCountryCode": "AU",
  "showLabsSettings": false,

  // §17 — MatrixRTC. The `url` is NOT optional: without it Element Web
  // loads the call widget from call.element.io — a third party serving
  // executable code into the call surface of a privacy product (§17.6.3).
  "features": { "feature_video_rooms": false },
  "element_call": {
    "url": "https://nexlink.thvjq.com.au/call",
    "use_exclusively": true
  },

  // §1.3 — nothing that leaks content or metadata to third parties
  "integrations_ui_url": null,
  "integrations_rest_url": null,
  "integrations_widgets_urls": [],
  "bug_report_endpoint_url": null,
  "map_style_url": null,

  "setting_defaults": {
    "UIFeature.feedback": false,
    "UIFeature.registration": false,
    "UIFeature.identityServer": false,
    "UIFeature.thirdPartyId": false,
    "UIFeature.shareQrCode": false,
    "UIFeature.shareSocial": false,
    "UIFeature.roomHistorySettings": false,
    "UIFeature.advancedEncryption": false,
    "UIFeature.urlPreviews": false
  }
}
```

### 20.4.1 Element Web ships its own Element Call

Measured 2026-09-14: the widget iframe Element Web 1.12.27 actually loads is

```
https://nexlink.thvjq.com.au/widgets/element-call/index.html?widgetId=…
```

— its **own bundled copy**, on this deployment's origin, in preference to the
configured `element_call.url`. §17.6.2's worry that a missing `url` means
`call.element.io` does not hold for this build; no third party was serving code
into the call surface. Set the `url` anyway: it costs nothing and it decides the
question rather than leaving it to what a future release bundles.

### 20.4.2 The config is copied at container start, not read per request

Applied 2026-09-14. The file is bind-mounted at `/app/config.json`, but nginx
serves `/config` from `/tmp/element-web-config`, which the image's entrypoint
populates on startup. **Editing the mounted file changes nothing until the app
is redeployed** — and the symptom is the worst kind: `docker exec … cat
/app/config.json` shows the new content while the site serves the old.

```bash
sudo midclt call -j app.redeploy nexlink-social-web
```

`config.json` is served `Cache-Control: no-cache` and comes back
`cf-cache-status: DYNAMIC`, so unlike §17.6.3's assets there is no edge cache
to wait out. Verify at the origin *and* through the edge; they answered
identically here.

Three of these are load-bearing rather than cosmetic and should be treated as
invariants alongside §2.8:

- **`disable_custom_urls: true`** — without it the login screen offers a server
  picker, and a user who types `matrix.org` into it gets a confusing failure at
  best. The service is one homeserver (§2.6).
- **`bug_report_endpoint_url: null`** — Element Web's rageshake uploads logs.
  §2.8's last invariant forbids logs carrying room IDs, user IDs or key
  material, and rageshakes carry all three. Off.
- **`integrations_*: null`** — the integration manager is a third-party service
  with room access. There is no version of this product that wants one.

`UIFeature.urlPreviews: false` is worth a note: URL previews are generated
**server-side** by the homeserver fetching the link, which means the server
learns which links appear in E2EE conversations. §25.4 disables it at the server
too. Both, because either alone is a single point of failure for a metadata leak
the product has told users it does not create (§9.6.1).

---

## 20.4 Skinning

Element Web's theming is CSS custom properties plus a small set of branding
assets. What is in scope:

| | |
|---|---|
| Colours | NexLink's palette, shared from `:shared` per §10.7 |
| Logo, favicon, app name | Replaced |
| Login copy | "NexLink Social" throughout |
| Welcome page | Replaced with a minimal page saying accounts are created in the app (§9.6) and pointing at the Play listing |

What is **out** of scope: restructuring navigation, rewriting components,
maintaining a patch set against upstream. The moment skinning requires a fork,
it has exceeded its budget — every patched file is a merge conflict on every
upgrade, forever, and §20.1's whole argument was that this client is
maintenance the project does not have to do.

**The rule: configuration and theming only. If it needs a code change, it does
not ship.**

---

## 20.5 The web client is a device

This is the part with real consequences, and §8.7 already established the shape.

A browser session is a Matrix device like any other. It gets a device ID, it
must be verified by an existing device before it can read encrypted history, and
it appears in the user's device list (§8.6).

| | |
|---|---|
| Verification | Emoji SAS or QR from the phone (§8.4). There is no other path in. |
| History | Only what key backup restores after verification (§8.5) |
| Keys at rest | IndexedDB, in the browser profile |
| Logout | Must clear the local store. A "remember me" that survives logout is a key-material leak. |

### 20.5.1 The disclosure this requires

Browser storage is not a phone's Keystore. It is readable by anyone with the
unlocked computer and by browser extensions with sufficient permissions. A user
signing in on a shared or work computer is making a materially worse security
decision than installing the app, and §3.7's disclosure obligation applies.

**Requirement:** a one-time interstitial before first web sign-in, in the
register of §9.6.1 — plain, specific, not a legal notice:

> Signing in here stores your keys in this browser. Anyone who can use this
> computer can read your messages. Don't do this on a shared or work computer.

And: **sign-out clears everything**, with no option to keep the session. §8.6's
remote device revocation is the recovery path when someone ignores the warning,
which is precisely why that surface has to be good.

---

## 20.6 Element Call on the web

Bundled with Element Web from v1.11.86 onward, which removes the separate
deployment §17 would otherwise need on the desktop side.

Requirements for it to work:

1. `.well-known/matrix/client` advertises the MatrixRTC backend (§26.3).
2. `homeserver.yaml` carries the `matrix_rtc` block and the experimental
   features from §17.3.1.
3. lk-jwt-service and the SFU are reachable (§24).

Consequence worth stating plainly: **the web client is the reference
implementation for the calling stack.** §17.6's decision procedure gates on
Android-to-web interoperability, so if calls do not work on the web, there is
nothing to interoperate with and the phase 4 spike cannot start. Standing the
web client up is therefore a prerequisite of phase 4, not a parallel task —
which is why §33 sequences it in phase 3.

---

## 20.7 Upgrades

| | |
|---|---|
| Cadence | Follow upstream within a few weeks. Do not drift by releases. |
| Security releases | Promptly. Element publishes advisories; subscribe. |
| Procedure | Runbook in §29. Pull the pinned tag, diff `config.json` against the new default, deploy, verify login + verification + a call. |
| Rollback | Repoint the tag and redeploy. Nothing is migrated, so rollback is genuinely free. |

The `config.json` diff is the step that gets skipped and the one that matters:
new releases add settings, and a setting that defaults to on is a surface this
chapter turned off arriving back through the side door.

---

## 20.8 Open questions

- **Does Element Web's recovery-key format round-trip with the Android client's
  in both representations?** §5.9 flags this and §7.4.3 depends on it. It is
  cheap to test as soon as both exist and expensive to discover late.
- **Should the web client be reachable at all before phase 5?** A deployed
  client is a login form on the public internet. Until invites exist (§9), it
  could sit behind Cloudflare Access — the same control §"Exposing the UI
  publicly" applies to Crafty — and be opened when the service is.
  **Leaning: Access-gated until phase 5.**
- **Is a mobile browser session worth blocking?** Element Web on a phone browser
  works and is a worse experience than the app, with the §20.5.1 storage
  properties. Leaning: allow, do not promote.
