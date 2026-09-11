# NexLink Social — status

Last updated 2026-09-11, end of session.

## What works right now

**You can install the app on a phone, sign in, open a conversation and send an
encrypted message.** Verified on a Samsung SM-G990E (Android 16) against the
live homeserver, not in a simulator.

The verification that matters: a message typed on the phone appears on the
server as `m.room.encrypted` with no readable body, and grepping the whole
server-side response for the plaintext returns **zero** occurrences.

| | Status |
|---|---|
| **Specification** | Complete — 38 chapters |
| **Phase 0** — Android foundations | Done, verified on hardware |
| **Phase 1** — homeserver | Live at `https://nexlink.thvjq.com.au` |
| **Phase 2** — SDK decision | Gate passed. Only step 5 (QR) outstanding |
| **Phase 3** — messaging | Core loop working. Much of §12–§14 still to do |
| Phases 4–7 | Not started |

Five commits on branch `feat/social-foundations`. Nothing pushed. Your own
uncommitted work in `app/` and `shared/` is untouched throughout.

---

## THINGS YOU NEED TO DO

### 1. Revert a phone setting I changed  ← do this one

To install test builds unattended I turned off Play Protect's scan of adb
installs. Put it back:

```bash
adb shell settings put global verifier_verify_adb_installs 1
```

### 2. Nothing else is blocking

The WAF rule, the tunnel and the homeserver are all done and verified.

---

## What needs you rather than me

**§11.7 step 5 — QR verification between two devices.** Sign in to
`https://nexlink.thvjq.com.au` in a browser, and verify that session by scanning
a QR code from the phone. It needs two screens and a pair of hands. That is the
last piece of the SDK decision, and it also settles §7.4.3's question about
whether recovery keys round-trip between Android and Element Web.

## Optional cleanup

- 4.2 GB of unusable emulator image: `rm -rf ~/Android/Sdk/system-images` and
  `~/Android/Sdk/cmdline-tools/latest/bin/avdmanager delete avd -n nexlink-test`
- Test accounts to deactivate before real users: `@gatetest`, `@bakeoff`, and
  the `@restore*` accounts.

---

## The two biggest open risks, unchanged

1. **No offsite backup.** Both pools are in one chassis, and `signing.key` is
   unrecoverable if lost. The copy in `~/NexLink-secrets/` is on a VM hosted on
   Willard itself, so it survives pool loss but not fire. Backblaze B2 closes
   this — §23.5.
2. **No alerting.** SMTP is still unconfigured on Willard, so a failed backup is
   invisible. §27.2 makes this a Phase 5 blocker.

---

## What Phase 3 still needs

Roughly in order: account creation from the acceptance gate (§22.10.2 — the gate
works, it just does not redeem the token yet), attachments (§14.5), reactions in
the UI (§14.4), the room list from a proper listener instead of polling (§13.2),
push notifications (§13.3), the device-management surface (§8.6 — which needs
raw Client-Server API calls, see §11.7.3), and encryption at rest for the SDK
store (§12.4).
