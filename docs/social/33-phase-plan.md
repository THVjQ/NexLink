# §33 — Phase plan and acceptance criteria

> **Confidence:** `DURABLE`
> The sequencing is driven by dependency, not preference. Durations are
> estimates for one part-time developer and should be read as ordering, not
> scheduling.

---

## 33.0 The principles behind the ordering

Four rules produce the sequence below. They are worth stating because they
explain why some obviously-fun work comes late.

1. **Resolve the expensive uncertainties first.** §11's SDK choice and §17.6's
   calling integration are the two decisions that are close to a rewrite if
   wrong. Both get a timeboxed spike before anything is built on them.
2. **Nothing reaches a real user until the operator can run it.** Backups
   restore-tested (§23.6.3), alerting working (§27.2), runbooks written (§29).
3. **Each phase ends in something demonstrable.** Not "the store layer is done" —
   a thing that can be used and shown.
4. **The VPS is not provisioned until it is needed** (§21.2.1). Phases 0–3 cost
   a domain name.

An important consequence of rule 1: **phase 2 may invalidate parts of Part III,
and phase 4 may invalidate parts of Part IV.** That is the intended outcome, not
a failure. Chapters carry confidence markers so that this is expected.

---

## 33.1 Phase 0 — Foundations

*No homeserver, no SDK, no network. Buildable today.*

| | |
|---|---|
| Goal | The repository shape that every later phase assumes, with the dependency rule enforced mechanically. |
| Estimate | 1 week |
| Blocked by | Nothing |

**Work:**

1. Migrate to a Gradle version catalogue (`gradle/libs.versions.toml`) and a
   convention plugin. §10.6.1 is explicit that this is done **before** five new
   modules multiply the duplication, and §10.10 wants it as its own reviewable
   change.
2. Create `:social-contract`, `:social-core`, `:social-ui`, `:social` per
   §10.3. (`:social-rtc` waits for phase 4 — an empty module that pulls WebRTC
   is a download-size cost for nothing.)
3. Implement the dependency check in the root `build.gradle` (§10.4), with the
   error message naming the document.
4. Define `SocialSession` and the application-facing vocabulary in
   `:social-core` (§11.6). **Interfaces and a fake implementation only** — no
   SDK.
5. Define the IPC contract types in `:social-contract` (§16.4).
6. Build the acceptance gate (§9.6) and the invite-code field (§9.3) in
   `:social-ui`, against the fake session.
7. `:social` assembles, installs alongside NexLink, and runs the gate.

**Acceptance:**

- [x] `./gradlew :app:assembleRelease` succeeds and **does not configure** the
      social modules (§10.8).
- [x] Adding `implementation project(':social-core')` to `:app` **fails the
      build** with the §10.4 message. Test this by doing it and reverting.
- [x] `:app`'s release AAB size is unchanged, to the byte where possible.
- [x] `:social` installs beside NexLink; both signed with the same keystore.
- [x] All five acceptance-gate screens render; the three checkboxes gate the
      primary action; date of birth is entered and **not stored**.
- [x] The invite field accepts `x7k2 9qmf 3btd` and normalises it to
      `X7K2-9QMF-3BTD`.

**Why this is first:** it is the only phase with no external dependency, it
produces the harness every later phase tests against, and §11.6's seam has to
exist *before* the SDK arrives or it will not be honoured.

### 33.1.1 Phase 0 — built 2026-09-11

| Criterion | Result |
|---|---|
| `:app:assembleRelease` succeeds, social modules not configured | **Pass.** No `:social*` task runs in that build. |
| Adding `:social-core` to `:app` fails the build | **Pass.** Introduced deliberately, build failed with the §10.4 message naming this document, reverted. |
| `:app` unchanged | **Pass.** No `app/` source touched; the release APK's dex contains no `com.nexlink.social` class at all. 8,195,114 bytes. |
| Same keystore | **Pass.** Both release APKs report certificate SHA-256 `a97f06…34ad4c`. |
| Gate: five screens, three checkboxes, DOB not stored | **Verified on a real device 2026-09-11** — Samsung SM-G990E, Android 16 / API 36. Full walkthrough in §33.1.2. |
| Invite field normalises `x7k2 9qmf 3btd` → `X7K2-9QMF-3BTD` | **Pass.** |

Artefacts: `social-debug.apk` 5.7 MB, `social-release.aab` 2.4 MB (R8 on).

**What was built beyond the criteria:** `tools/check-invariants.sh`, the §34.2
invariant checks that §34.8 asks for in this phase. Each of its six checks was
self-tested by introducing a violation and confirming it fails.

### 33.1.2 Gate verified on hardware — and two crashes found

Run on a Samsung SM-G990E (Android 16, API 36) on 2026-09-11, driving the UI by
widget bounds rather than pixel guesses.

| Check | Result |
|---|---|
| Both apps coexist | `com.thvjq.nexlink` **and** `com.thvjq.nexlink.social.debug` installed together. **D1 demonstrated on hardware.** |
| Five screens render | Yes, with the step counter |
| §9.3 normalisation | Typed `x7k29qmf3btd` → field shows `X7K2-9QMF-3BTD` |
| §9.6.1 two-column disclosure | Both columns render at equal weight |
| §9.6.1 checkbox gating | Terms and Privacy boxes `enabled=false` until their document is opened; tapping a gated box does nothing; Continue `enabled=false` until all three are ticked |
| Age: born 2020 | Refused — *"You need to be at least 16"* |
| Age: 31 February | Refused — *"That isn't a date."* |
| Age: valid | Accepted |
| **§9.6.2 — no DOB leaves the gate** | Result reads `invite X7K29QMF3BTD, age confirmed, terms 0.1.0`. **No date of birth.** |

**Two crashes, both in code written this session, both invisible to 37 passing
unit tests and found on the fifth keystroke of the first field a user ever
touches.**

1. **`IndexOutOfBoundsException: setSpan (6 ... 6) ends beyond length 5`** in
   `InviteCodeField.Hyphenator.afterTextChanged`. Calling `setText()` from
   inside `afterTextChanged` installs a *different* `Editable` while the key
   listener is still inside `SpannableStringBuilder.replace()` on the old one,
   so the following `setSelection` indexes a stale, shorter buffer. **Fix:
   mutate the `Editable` in place with `s.replace(0, s.length, formatted)`** and
   drop `setSelection` entirely — the selection then follows on its own.

2. **The hyphens never stuck.** Masked by crash 1. The `InputFilter` stripped
   `-`, and **an `InputFilter` runs on programmatic edits too** — so the
   hyphenator inserted separators and the filter immediately removed them. The
   code would have read `X7K29QMF3BTD` forever. **Fix: the separator passes
   through the filter**; the hyphenator owns placement.

Also fixed: `Ui.body()` installed `LinkMovementMethod` on every paragraph,
making plain body text focusable and showing a grey highlight block when tapped.
Now installed only when the text actually contains a `URLSpan`.

**This is §15.9's lesson repeating exactly** — *"the bridge's original failure
was invisible on stock Android and obvious on real devices."* Neither crash was
reachable from a unit test, because both live in the Android `TextWatcher` /
`InputFilter` interaction rather than in the pure logic those tests cover.

**Deviations, both recorded where they belong:**

- **`getInterfaceVersion()` is not a usable AIDL method name** — `aidl` reserves
  it and fails the build. The method is `getContractVersion()`; §16.4.2 carries
  the correction.
- **The version catalogue covers the new modules only.** §10.6.1 wants one
  shared configuration, and `gradle/libs.versions.toml` now exists — but `:app`,
  `:shared` and `:wear` were left on their inline versions, because
  `app/build.gradle` carries unrelated uncommitted work and §10.10 asks for that
  migration as its own reviewable diff. **It is still outstanding.**

---

## 33.2 Phase 1 — Spike homeserver

| | |
|---|---|
| Goal | A working, correctly-configured, private homeserver on Willard. |
| Estimate | 1 week |
| Blocked by | ~~The domain name~~ — **decided 2026-09-11: `nexlink.thvjq.com.au`** (§26.2.1). Unblocked. |

**Work:** §21.5 datasets, §22 Synapse deployment as a TrueNAS custom app, §23
PostgreSQL with the `ix_volume` (§23.2), §26 DNS and delegation, the invite
service and its `invite_record` store (§9.2.2).

**Acceptance — the security posture, asserted rather than believed:**

- [x] `curl https://nexlink.thvjq.com.au/_matrix/client/versions` returns 200.
- [x] Registration **without** a token fails.
- [x] Registration **with** a token succeeds, and the token is then spent.
- [x] `https://nexlink.thvjq.com.au/_matrix/federation/v1/version` does **not**
      answer (§21.3).
- [x] `https://nexlink.thvjq.com.au/_synapse/admin/...` returns non-200 publicly
      (§26.5.2).
- [x] A room created by a client is encrypted by default (§22.4).
- [x] `.well-known/matrix/client` is served with
      `Access-Control-Allow-Origin: *` (§26.3.1).
- [ ] `signing.key` is backed up **off Willard** (§21.6). **Still not done, and
      it is the largest single risk in the deployment** — §21.6 said to do it
      the day the server started, which was 2026-09-11. The tool and the
      two-minute procedure are now in §29.4a; the passphrase is the operator's,
      so the last step is his.
- [x] A `pg_dump` runs, and a restore into a scratch container passes all five
      checks in §23.6.2. **Timed and recorded.**

**Resolves:** §22.9's reserve-then-redeem question — whether the acceptance gate
can run before the account exists, or whether the invite service must proxy
registration entirely. That is the single most important unknown in this phase
and it may change the shape of the invite service.

### 33.2.1 Phase 1 — built 2026-09-11

Live on Willard. `nexlink-social` (Synapse `v1.160.0` + PostgreSQL 16) and
`nexlink-social-web` (Element Web `v1.12.27`), both RUNNING.

| Criterion | Result |
|---|---|
| `/_matrix/client/versions` → 200 | **Pass.** 20 versions, 40 unstable features. |
| Registration without a token fails | **Pass.** Offers `m.login.registration_token` as a required stage. |
| Registration with a token succeeds, token spent | **Pass.** `@gatetest:nexlink.thvjq.com.au` created; token went `pending=1` → `completed=1`; replay refused. |
| Federation does not answer | **Pass.** 404. |
| Rooms encrypted by default | **Pass.** `m.room.encryption` = `m.megolm.v1.aes-sha2` on a client-created room. |
| URL previews off | **Pass.** `/preview_url` → 404. |
| `signing.key` backed up off Willard | **Partial** — copied to `~/NexLink-secrets/`, but that is a VM *on* Willard. Survives pool loss, not fire. See its README. |
| `pg_dump` + restore, five checks | **Pass, 19 s.** Collation asserted `C`; users, devices, events and encrypted-room state all survived. |
| Admin API unreachable publicly | **Pass, 2026-09-11** — but by side effect, not by control. See §33.2.3. |
| `.well-known` with CORS | **Pass.** `access-control-allow-origin: *`, and `rtc_foci` present. |

Also built: the nightly `pg_dump` as TrueNAS **cronjob id 3** (§23.4.2), and the
operator invite CLI (§9.8) with the `invite_record` / `acceptance_record` store.
The CLI's Crockford alphabet was diffed against the Android client's constant —
they match, so a minted code validates in the app.

**Findings that contradicted this document, now corrected in place:**

- **Q2 is answered, natively and favourably** — §22.10. The invite service
  shrinks to metadata only and no longer proxies registration.
- **Synapse does not emit the `rtc_foci` block** into `.well-known` on its own;
  it needs `extra_well_known_client_content` (§26.3). The symptom of missing it
  is a *missing call button*, with nothing in any log.
- **Cloudflare's free Universal SSL stops at first-level subdomains**, so the
  `matrix.<domain>` delegation shape was abandoned for path-based routing on one
  hostname (§26.2.2).
- **§20.3's `config.json` had a duplicated `default_server_config` key** — invalid
  JSON, silently keeping only the last. Corrected.

### 33.2.2 What phase 1 could NOT finish

**The Cloudflare Tunnel routing.** §26.5.1 records why: the `cloudflared` app on
Willard uses a **token-based tunnel**, so its public hostnames live in the
Cloudflare dashboard and **cannot be added from the CLI**. Three acceptance
criteria depend on it and are outstanding:

- the admin API returning non-200 publicly (§26.5.2);
- `.well-known` served with `Access-Control-Allow-Origin: *` (§26.3.1);
- anything reachable at `https://nexlink.thvjq.com.au` at all.

**The routes, the DNS record and the six verification commands are written up in
`infra/runbooks/cloudflare-tunnel-routes.md`** — that file is the authoritative
record, because the dashboard holding the real configuration is not covered by
any backup (§26.5.1).

**Also outstanding:** `@gatetest` is a test account created to answer Q2. It must
be deactivated before phase 6, and its password appears in this phase's working
notes.

### 33.2.3 Public access — completed 2026-09-11

`https://nexlink.thvjq.com.au` is live. All six public checks pass:

| Check | Result |
|---|---|
| `/_matrix/client/versions` | 200, 20 versions |
| `.well-known/matrix/client` | served, `base_url` correct, **`rtc_foci` present** |
| CORS on `.well-known` | `access-control-allow-origin: *` |
| Element Web at `/` | 200, branded "NexLink Social" |
| `/_synapse/admin/*` | 404 — **but see the caveat below** |
| Registration without a token | refused, `m.login.registration_token` required |
| `/_matrix/federation/v1/version` | 404 |

Routes, in order, on one hostname (§26.2.3):

| Path (regex) | Service |
|---|---|
| `^/_matrix/` | `192.168.0.10:8060` |
| `^/\.well-known/matrix/client` | `192.168.0.10:8060` |
| *(empty — catch-all)* | `192.168.0.10:8061` |

**The path field is a regular expression, not a glob.** Cloudflare's own help text
confirms it: empty matches all paths, a bare word matches *anywhere* in the path,
and `^/api` is the prefix form. An unanchored `/_matrix/` would also match
`/foo/_matrix/bar`.

**The admin-API caveat.** The 404 is nginx's, not a block: the request misses
`^/_matrix/`, falls to the catch-all, and Element Web 404s it. It is protection by
routing side effect and it evaporates if the catch-all is ever repointed at
Synapse. The explicit WAF rule in `infra/runbooks/cloudflare-tunnel-routes.md`
is still outstanding and should be added.

Two further traps hit and documented in that runbook: a **tunnel-managed DNS
record stuck in a half-created state** (dashboard showed it healthy; Cloudflare's
own nameservers served NXDOMAIN; adding it by hand was refused as a duplicate),
and a **second, older tunnel** on the account that `netdata.thvjq.com.au` still
points at.

---

## 33.3 Phase 2 — SDK decision

| | |
|---|---|
| Goal | §11 resolved with evidence, and its confidence marker raised. |
| Estimate | **5 working days, timeboxed.** §11.7. |
| Blocked by | Phases 0, 1 |

Build the §11.7 client twice, once per SDK, against the phase-1 homeserver.
Steps 4–6 — cross-signing, second-device QR verification, history restore from
key backup — **are a gate with no partial credit.**

**Current evidence, gathered 2026-09-11 and recorded here so the spike starts
informed rather than neutral:**

| | matrix-rust-components-kotlin | matrix-android-sdk2 |
|---|---|---|
| Latest release | `sdk-v26.09.9`, **2026-09-09** | `v1.6.50`, **2026-02-04** |
| Last repo activity | Current | 2026-07-21 |
| Archived | No | No |

Seven months between releases against two days is exactly the divergence §11.2.1
predicted — *"a library whose primary consumer has moved on receives
maintenance, not investment"*. It strengthens §11.4's lean toward the Rust SDK
without settling it, because §11.5's disqualifiers are about whether the
bindings *work*, not how often they ship.

**Acceptance:**

- [x] Both candidates attempted, or one eliminated with a written reason.
- [x] Every §11.7.1 measure recorded, including the subjective ones.
- [x] A decision, written into §11 with the marker raised from `SPECULATIVE`.
- [x] `:social-core`'s `SocialSession` implemented against the winner **with no
      SDK type crossing the module boundary** (§11.6).

**Do not exceed the timebox.** If five days do not settle it, that is itself
information — it means the churn risk in §11.3 is real — and §11.4's lean should
break toward the more stable option rather than toward a sixth day.

### 33.3.1 Phase 2 — in progress, started 2026-09-11

Work done so far, all of it without a device:

| Step | Status |
|---|---|
| Dependency resolves and links | **Done.** `org.matrix.rustcomponents:sdk-android:26.09.9` in `:social-core` only |
| §11.5 disqualifier 1 — published and current? | **Passes.** Maven version matches the GitHub tag, same day (§11.7.0) |
| §11.5 disqualifier 2 — key backup and cross-signing complete? | **Passes.** Full audit in §11.7.2 |
| AAB size, per ABI | **Measured.** ~24.5 MB per device, 95.2 MB universal (§11.7.0) |
| `SocialSession` implemented against the SDK | **Compiles.** `RustSocialSession`, `RustRecovery` |
| §11.6 seam holds | **Enforced.** `tools/check-invariants.sh` passes with a real SDK present |
| Step 1: login | **PASS** on hardware 2026-09-11 |
| Step 2: list rooms | **PASS** |
| Step 3: send encrypted | Sent; the test's assertion was wrong (§11.7.4) |
| **Step 4: cross-signing + recovery key — the gate** | **PASS** |
| Step 6: history restore from key backup | **PASS** — `RecoveryRestoreTest` |
| **Step 5: second-device verification** | **PASS 2026-09-13** — see §33.3.3 |
| Step 7: multi-code-point reaction | **PASS** |

### 33.3.2 Step 5 — implemented, not yet completed end to end (2026-09-12)

§8.4 verification is **built**: `RustVerification` over the SDK's
`SessionVerificationController`, and `VerifyActivity` covering both sides — the
device that asks and the device that answers.

**On the wire it reaches `m.key.verification.request` → `.ready` → `.start`, and
stops there.** No `.accept`, no emoji. Held open for 108 seconds with no change,
so it is a stall, not latency.

**The rig is the suspect, and it is a bad rig.** Two builds of the app on one
phone (debug and release are separate packages) can only have one in the
foreground at a time, and the SAS handshake needs several round trips with both
sides responsive. Attributing the log lines by pid: the requester sent `.start`
twice, and the accepter — foreground and demonstrably still syncing — never
received it.

That is worth being precise about rather than declaring a pass. **Step 5 is not
complete.** The next attempt should use Element Web as the second device, which
is what §33.3.1 said in the first place: it runs on another machine, syncs
continuously, and is the reference implementation, so a failure there is a real
failure rather than an artefact of two apps fighting over one foreground.

> **Resolved 2026-09-13 — see §33.3.3. Step 5 passes and phase 2 is complete.**
> The diagnosis below was right: the rig was the problem, not the code.

**Three real bugs were found getting this far, and they are the return on the
attempt even though the step did not pass:**

1. **The release build did not start at all.** `UnsatisfiedLinkError: Can't
   obtain peer field ID for class com.sun.jna.Pointer`. JNA's native half looks
   its Java fields up by name through JNI, and R8 had renamed them; uniffi sits
   on JNA, so every Rust call goes through it. §10.6.3 enabled R8 from the first
   release precisely to catch this early — and it did, but only once someone
   ran the APK. Every check before this had been on `assembleDebug`, which is
   unminified. **A shipping blocker that no amount of debug testing would have
   found.**

2. **Debug and release could not be installed together** — both declared the
   §16.3.1 signature permission under the same name, and a custom permission is
   owned account-wide by whichever package declares it first. Fixed with a
   manifest placeholder. The trap underneath: a default in `:social-contract`'s
   own `build.gradle` is not a fallback. A library's placeholders win for its
   own manifest entries, so with one set, the debug APK's `<uses-permission>`
   took the app's suffixed value while the `<permission>` declaration kept the
   library's — and the two still collided.

3. **Incoming verification requests were invisible.** The controller was created
   when the verify screen opened, so a request arriving earlier was consumed by
   the crypto machine with no delegate attached. Silent in both directions. And
   it stays *ongoing*, so asking again logs *"Received a new verification
   request whilst another request with the same user is ongoing. Cancelling
   both requests"* — the retry kills the original. The delegate is now installed
   at `startSync()`. §8.3.2 makes this a security requirement, not a nicety: an
   unexpected request is the signal that someone is adding a device to the
   account, and a request nobody can see is a warning that was never given.

One protocol detail learned the same way: **only the requester may start SAS.**
`didAcceptVerificationRequest` fires on both devices, so starting there
unconditionally made both send `.start` — observed as four `.start` events per
attempt — and the handshake stalled with both sides showing "connecting" and no
error. Nothing in the SDK's API tells you which side you are on; it is knowable
only from which call you made.

### 33.3.3 Step 5 — PASS, 2026-09-13

`DeviceVerificationTest`, on hardware, against the live homeserver. Two devices
requested, accepted, exchanged SAS, showed **the same seven emoji**, and both
reached `Verified`. The server confirms it was real rather than a UI state
change: `e2e_cross_signing_signatures` gained a row.

**The rig, not the code.** §33.3.2 suspected the two-apps-on-one-phone setup and
that was correct — only one app can be foreground, so whichever side was
backgrounded stopped syncing, and SAS needs several round trips with both ends
responsive. The fix was **two SDK clients inside one instrumentation process**,
both syncing for the whole test. That is a better rig than Element Web would
have been: it removes the failing variable while testing exactly the code that
ships, and it is repeatable in CI.

The emoji-equality assertion is the real content of the test. SAS's entire
security property is that both devices independently derive the *same* short
string; if they differed a user would reject a legitimate device, and if they
were constant the check would be theatre.

**Two production bugs found on the way, neither in verification itself:**

1. **`currentUserId()` returned a bare localpart** where the SDK's
   user-interactive auth requires a full MXID, and rejects anything else with
   `MissingLeadingSigil`. It had never fired in the recovery flow because
   `resetIdentity()` usually returns null there and the call needing the
   identifier is never reached — the bug sat behind a path that normally does
   nothing. §8.4 hits it on the first attempt.

2. **`bootstrapCrossSigning` silently no-opped.** It was
   `client.encryption().resetIdentity() ?: return@io`, which made "an identity
   was created", "one already existed" and "this did nothing" indistinguishable
   to the caller. §7.4.4 exists precisely because a missing cross-signing
   identity fails *later and invisibly* — a new device reads nothing, forever,
   with no error — so discarding that answer was the wrong thing to do. It now
   returns whether it acted.

**A precondition worth stating plainly, because the error does not say it:**
verification is impossible without a cross-signing identity, and the failure is
`ClientException$Generic: Failed retrieving user identity` — which names neither
cross-signing nor the fix. The same message appears when the identity *exists*
but the requesting client has not synced it yet, so the two cases are
indistinguishable from the message alone. The test retries rather than treating
the first failure as fatal; an app should do the same.

---

**API friction (a §11.7.1 measure), recorded while it is fresh:** the API shape
was derived by running `javap` over the published AAR, and the implementation
compiled on the first attempt against it. `EncryptionInterface` in particular
reads as a product API rather than an FFI dump — `enableRecovery`, `recover`,
`resetRecoveryKey`, `isLastDevice`, `hasDevicesToVerifyAgainst` map almost
one-to-one onto §7's requirements. That is better ergonomics than §11.2.2
predicted ("a Kotlin API generated through FFI rather than hand-designed").

The counter-observation: **5,089 classes** in the AAR, a large share of them
`FfiConverter*` plumbing, and generated names like
`withRoomListTimelineLimit-WZ4Q5Ns` leak Kotlin value-class mangling through the
binding. Neither is a problem; both confirm this is generated, not designed.

**One real gap found: device management (§11.7.3).** Not a disqualifier, but
unbudgeted work in phase 3.

### 33.3.2 How steps 4-6 get tested without two phones

§11.7's gate is cross-signing, verifying a second device by QR, and restoring
history from key backup. There is one operator with one phone, and §34.9 already
flags that as a problem for §8's flows generally.

**Element Web is the second device.** It is a full Matrix client on the same
homeserver (§20.5 — "a browser session is a Matrix device like any other"), it
is already deployed and reachable, and using it exercises the same cross-signing
and key-backup machinery a second phone would.

It also kills two birds: §5.9 and §7.4.3 both want the recovery key round-tripped
between the Android client and Element Web, and this arrangement tests that as a
side effect rather than as separate work.

The first device was going to be an Android emulator, which would also have
closed Phase 0's unverified "the gate renders" criterion. **That did not work** —
the development VM has too little memory and the emulator dies during boot.
§34.10 has the detail and the conclusion: **use a real phone over USB**, which
§34.5 and §15.9 require anyway.

**This is the one thing phase 2 now needs from the operator.** Everything that
can be established without a device has been (§33.3.1), and the remaining work
is §11.7 steps 1-6 — which is the decision itself.

---

## 33.4 Phase 3 — Messaging

| | |
|---|---|
| Goal | Two people exchange encrypted messages and media, on phones, reliably. The product exists. |
| Estimate | 6–10 weeks. The largest phase by far. |
| Blocked by | Phase 2 |

**Work:** registration through the gate (§9.6) into a real account; the chat
surface (§14); the local store and encryption at rest (§12); sync and push
(§13); multi-device and verification (§8); recovery keys (§7); Element Web
deployment (§20); the NexLink Level 0 link (§16.2).

### 33.4.1 Phase 3 — progress at 2026-09-11

Built and verified on a Samsung SM-G990E against the live homeserver.

| Piece | Status |
|---|---|
| Platform init, session persistence, restore | **Done** — signs in once, survives force-stop |
| Sign-in | **Done** |
| **Account creation from the acceptance gate** | **Done** — invite → redeem → gate → account → signed in |
| Room list | **Done** (polling; a listener is the refinement, §13.2) |
| Conversation: timeline + send | **Done** |
| **Encrypted end to end** | **Verified** — a message typed on the phone is `m.room.encrypted` on the server, and the plaintext appears **zero** times in the server's response |
| §14.2.3 undecryptable placeholder | **Done**, and seen working on a new device |
| §8.5.2 backup-health warning | **Done** — shown on the inbox, routes to recovery setup |
| **Recovery key setup (§7.4)** | **Done** — cross-signing identity *and* key backup, key shown once behind a confirmation |
| Attachments, reactions UI, replies, edits | Not started (§14.4–§14.6) |
| Push notifications | Not started (§13.3) |
| Device management (§8.6) | Not started — needs raw C-S API (§11.7.3) |
| Encryption at rest for the SDK store | Not started (§12.4) |

### 33.4.2 Phase 3 — progress at 2026-09-12

Everything above still holds. Added since, all verified on the same handset
(SM-G990E, Android 16 — the §16.2.1 entry originally recorded the wrong model):

| Piece | Status |
|---|---|
| Attachments (§14.5), reactions (§14.4), replies, edits (§14.6) | **Done** |
| Search (§14.9), group creation, invitations (§14.8) | **Done** |
| Device management (§8.6) | **Done** — `DevicesActivity`, live against the server |
| Message notifications (§13.4) | **Done** — MessagingStyle, per-room channels, inline reply |
| **NexLink Level 0 link (§16.2)** | **Done and verified** — see §16.2.1; §1.4.4's "no integration code" claim was **false** and is corrected |
| Offline send failure surface (§13.5) | **Done** — §13.5.2; a failed send used to read "Sending…" forever |
| Storage and retention (§12.5) | **Done** — §12.5.4; two false claims of its own, both fixed |
| Accessibility (§14.10) | **Done** — §14.10.1; light theme failed WCAG AA on five colours |
| Encrypted transfer archive (§7.5.2) | **Done** — the restore was built 2026-09-15 (§7.5.3a). Unit-tested; **not yet exercised on a phone** |
| Recovery restore (§7.4) | **Done** — `RecoveryRestoreTest` |
| Push notifications (§13.3) | **Done** — Firebase created, Sygnal deployed; a cold push woke a killed process and rang it (§15.6.1). This row read "Blocked" until 2026-09-15 |
| Encryption at rest for the SDK store (§12.4) | **Done** — §12.4.5 measured the store, §12.4.6 closed StrongBox and the screen-lock warning. (This row said "not started" for one commit: copied from the stale §33.4.1 without checking.) |
| Direct device transfer (§7.5.1) | Not started — feature of its own size |
| Calls (§15, §17–19) | **Done** — phase 4, §33.5.3 |

**Mechanical checks now standing at 7** (`tools/check-invariants.sh`), the
newest being WCAG AA contrast in both themes — added because §14.10's contrast
requirement is fully decidable from the palette and had never been checked.
**62 unit tests** across `:social-core` and `:social-ui`.

Every one of the five §-sections closed today was closed by finding something
wrong on hardware that no amount of reading would have found. That is the
argument for §34.10's rule (the emulator is unusable here) restated as evidence:
three of the four accessibility failures were invisible because the phone runs
dark mode, and the §16 one was invisible because the listener fails by `return`.

**The §2.8 invariants that could be tested, were:**

| Invariant | Evidence |
|---|---|
| #1 no content the server can read | Server shows `m.room.encrypted`; plaintext grep returns 0 |
| #3 no account without a redeemed token **and** a completed gate | An abandoned run left its token `pending` with **no account**; the completed run's token reads `spent` |
| #6 NexLink gains nothing | `tools/check-invariants.sh` diffs its manifest every run |

**Two real bugs found on hardware that no unit test could reach**, both now
fixed: the invite field crashed on the fifth keystroke (§33.1.2), and the
conversation composer sat under Samsung's gesture navigation bar so that
tapping Send was consumed as a back gesture — the activity closed, nothing
sent, nothing logged. The second is invisible on three-button navigation.

---

**Acceptance:**

- [x] Register via invite, complete the gate, land in the app.
- [x] Send and receive text, images, files, reactions with a ZWJ emoji (§14.4.3).
- [x] Edit and delete, propagated.
- [x] Push wakes the app and the notification resolves to a **decrypted** sender
      and preview (§13.3.2), with **no persistent foreground service** (§15.3).
- [x] Sign in on a second device; ~~verify by QR~~ **verify by SAS — QR is not
      built and is a decision, §8.4.2**; **history restores from key backup**.
      Done 2026-09-15 on two handsets (§8.4.4): same seven emoji both ends,
      both device keys self-signed on the server, six messages readable and
      none undecryptable on the new device. (§8.5).
- [ ] Recovery key round-trips between Android and Element Web (§5.9, §7.4.3).
- [x] Messages sent offline queue and deliver on reconnect (§13.5.1).
- [x] Social conversations appear in NexLink's unified inbox via the existing
      notification listener, **with no code added to `:app`** (§16.2).
- [x] `:app`'s AAB size still unchanged.
- [x] Local store is encrypted at rest and survives a device reboot (§12.4).

**The unified-inbox row is the one to demonstrate to the product owner.** §1.4.4
claims a companion app reaches the inbox on day one with no integration code.
This phase proves it or falsifies it, and the whole D1 argument rests on it.

---

## 33.5 Phase 4 — Calling

| | |
|---|---|
| Goal | Voice, video, groups and screen share. |
| Estimate | 4–8 weeks, **preceded by a 5-day spike** (§17.6.1) |
| Blocked by | Phase 3. ~~The first recurring cost — the VPS~~ — **no longer true as of 2026-09-13: §24.1.1 takes a free hosted SFU, and the VPS is deferred until the free tier is outgrown.** |

**Work:** provision the VPS, LiveKit and coturn (§24); lk-jwt-service on Willard;
Synapse's MatrixRTC configuration (§17.3.1); `:social-rtc`; the §17.6 decision;
Telecom integration (§19); screen sharing (§18).

### 33.5.1 Phase 4 — progress at 2026-09-13

| Piece | Status |
|---|---|
| ~~Provision the VPS~~ | **Not needed** — §24.1.1 takes a free hosted SFU |
| LiveKit SFU | **Done** — LiveKit Cloud, credentials verified, Sydney, 24–36 ms |
| `lk-jwt-service` on Willard | **Done** — app `nexlink-social-rtc`, proxied at `/livekit/jwt` |
| Synapse MatrixRTC config (§17.3.1) | **Done** — transports endpoint answers 200 |
| **Full auth chain → SFU admits a participant** | **Verified** — §17.6.2 |
| `:social-rtc` | **Created** — §17.7 call state, §19 Telecom, §15 call service |
| §19 Telecom | **Done** — self-managed account registered, verified on the handset |
| §15 call foreground service | **Done** — 3 instrumented tests pass |
| **§17.6 decision** | **Open** — needs a client that can place a call (§17.6.2) |
| §18 screen sharing | Not started — waits on §17.6 |
| Call UI (§19.5), §15.6 incoming surface | Not started |

**The phase is now blocked on client work rather than on infrastructure or
money**, which is a materially better position than §33.5 assumed.

### 33.5.2 Phase 4 — progress at 2026-09-14

§17.6 is **resolved** (§17.6.3): Option A, the Element Call widget, self-hosted.

| Piece | Status |
|---|---|
| Element Call self-hosted | **Done** — app `nexlink-social-call`, served at `/call/` |
| `CallActivity` — the WebView call surface | **Done** — origin-locked to this deployment |
| Runtime permissions before the service starts | **Done** — §15.2.1's ordering, verified on the handset |
| §15 foreground service on **answer** | **Done** — verified: the call notification appears when a call starts |
| §19 Telecom registration | **Done** — self-managed account accepted by the platform |
| Call button in a conversation | **Done** |
| **Credential handoff to the widget** | **NOT DONE — the remaining work** |
| §17.6.1 spike steps 1–5 | Blocked on the above |
| §18 screen sharing | Blocked on the above |

#### What is left, precisely

Loading the widget works: it renders, the foreground service runs, permissions
are granted to the page only for the allowed origin. But Element Call comes up
in **standalone mode and offers "Join as guest"**, because nothing has given it
credentials — observed on the handset, with
`POST /_matrix/client/v3/register → 401` in the WebView console as it tried to
make itself a guest account.

Credentials reach an embedded Element Call through the **Matrix widget API over
`postMessage`**: the host client answers a capability negotiation and supplies
the access token. It is not a URL parameter — the bundle was searched and the
parameter names are not there. Element X Android implements this as a widget
driver, and that is the shape of the remaining work.

This is a bounded, well-defined piece rather than an open question, and it is
the last thing standing between here and the §17.6.1 spike.

#### A bug found on the first real call

Ending a call left the **"Call in progress" notification posted after the
service was gone.** `CallService.stop()` calls `stopService()`, which reaches
`onDestroy` without passing through `stopCleanly()`, so that method's
`STOP_FOREGROUND_REMOVE` never ran.

Worse than untidy: §19.5.3 makes the ongoing notification *the route back into a
live call*, so a stale one is a button that takes the user to a call which is
not happening. Fixed in `onDestroy`, and the §15.9 tests pass with no stale
notification left behind.

### 33.5.3 Phase 4 — progress at 2026-09-14 (evening)

The credential handoff is **done**, and with it the first real calls.

| Piece | Status |
|---|---|
| Credential handoff to the widget | **Done** — Rust `WidgetDriver` behind a real iframe (§17.6.4.1) |
| A call that connects, encrypts and carries media | **Done** — verified on the handset |
| Two parties, each decrypting the other | **Done** — two accounts, video both ways |
| Hang-up closes the surface and withdraws membership | **Done** — `io.element.close`, both `m.call.member` events emptied |
| Element Web loads **our** widget, not `call.element.io` | **Done** — §20.4.1, §17.6.3's condition met |
| §17.6.1 step 2 — a third party from Element Web | **Done** — the gate, §17.6.4.4 |
| Back backgrounds the call; the notification returns to it | **Done** — §19.5.4 |
| §17.6.1 step 3 — screen share from Android | **Cannot be done on Option A** — §18.2.1 |
| §17.6.1 step 4 — key rotation observed on leave | **Done** — `creating new outbound key index:2` on leave |
| §17.6.1 step 5 — incoming call from a cold start | **Done** — §15.6.1; the other gate |

Three bugs, all of which connected a call and then failed silently — see
§17.6.4.1 for each in full:

1. A shimmed `window.parent` cannot satisfy the widget API's check that a reply
   came from the frame it posted to. Every request timed out.
2. `loadDataWithBaseURL` does not reliably give the host page the base URL's
   origin, and the transport drops mismatched messages without an error.
3. `MODIFY_AUDIO_SETTINGS` was missing, so the call joined the SFU and was
   silent. Nothing at the Matrix layer said so.

Invariant 8 (§2.8) now guards 1 and 2 mechanically.

**Acceptance:**

- [x] 1:1 audio and video between two Android devices — 2026-09-15, an SM-G990E
      and an SM-S928B. Both reached `CallActivity` with the service running,
      the callee subscribed to the caller's video, `encrypted=true`. Getting
      there found §17.7.3, which was the real prize.
- [ ] **Four participants, mixed Android and Element Web, with one screen
      share, on a mid-range phone over domestic broadband.** This is §1.5's
      success criterion verbatim and it is the gate for the phase.
- [x] E2EE confirmed **on**, and keys observed rotating on join and leave
      (§17.5). Not assumed — observed: `encrypted=true` per participant, and
      `creating new outbound key index:2` when the second participant left.
- [ ] A cellular call interrupts a Social call correctly: hold, mic released,
      resume (§19.4).
- [x] Incoming call with the app swiped away reaches the user (§15.6) — §15.6.1.
- [ ] Calls work from a network that permits only outbound TCP 443 (§24.5).
- [ ] Force-stopping a client during a call leaves no ghost participant (§17.7).
      **Measured and currently FAILS: the ghost lasts an hour** (§17.7.2). The
      delayed leave is upstream's, at `delay=3600000`, and no server setting
      shortens it. This is a decision to take, not a bug to fix.
- [x] CI asserts no `MediaRecorder` / `MediaMuxer` anywhere in `:social-*`
      (§18.2) — `tools/check-invariants.sh`, check 1.

---

## 33.6 Phase 5 — Operational readiness

| | |
|---|---|
| Goal | The service can be run by one person without heroics. **No real users before this completes.** |
| Estimate | 2 weeks |
| Blocked by | Phase 4 (or phase 3, if launching without calling) |

This phase looks like overhead and is the one that decides whether the service
survives its first bad week.

**Work and acceptance:**

- [x] **SMTP configured on Willard** (§27.2) — Proton on 587; 465 and 25 are
      blocked outbound. This unblocked all alerting.
- [x] All five §27.3 alerts firing, verified **by deliberately breaking each
      one**. The fifth was a no-op pointing at a VPS that no longer exists;
      repointed at the federation TLS certificate and verified 2026-09-15
      (§27.3.3).
- [ ] Offsite backup to Backblaze B2 working (§23.5). `signing.key` and the
      database dump included, encrypted before upload. **Deferred by the
      operator** — the only phase-5 item not started.
- [x] Restore drill timed and recorded (§23.6.3).
- [ ] Every §29 runbook written and at least once *followed* by the operator
      reading it, not from memory. Restore has been; the rest have not.
- [ ] Terms of service and privacy policy published and versioned (§4.7), and
      §32's retained-after-deletion items disclosed in them. **Drafted and
      exported; waiting on the operator's review** — see `docs/social/legal/`.
- [x] Deletion works in-app **and** from the public web page, and purges media
      (§32.3, §25.7) — re-verified 2026-09-15 end to end on a throwaway
      account: media 200 before, 404 after; the web path leaves media until
      the hourly sweeper, which was watched doing it.
- [x] Reporting and blocking work end to end (§31.3) — verified against the
      live homeserver 2026-09-15 (§31.3.2b). Blocking holds for messages *and*
      invites, server-side. Reporting had a hole where declined-consent reports
      went nowhere readable; fixed. §31.3.2's consented-content mechanism is
      still unbuilt and is now a stated decision, not a silent gap.
- [x] §27.6's drift assertions running daily, from **outside** Willard — a
      systemd user timer on the desktop VM; six assertions, green two days
      running.

---

## 33.7 Phase 6 — Private launch

| | |
|---|---|
| Goal | 10–20 real invited users. |
| Estimate | 4 weeks of running it |
| Blocked by | Phase 5 |

Operator-issued invites (§9.8) to people who know it is early. Internal Play
testing track.

**Acceptance:**

- [ ] 20 accounts created through the real flow.
- [ ] Two weeks with no Sev 1 or Sev 2 incident (§30.2).
- [ ] Push delivery ratio measured and acceptable (§27.4.1). **The measurement
      exists now** — `social-push-ratio`, from Sygnal's Prometheus counters
      (§13.3.5). It needs real users before the number means anything.
- [ ] Battery impact measured on real phones over real days (§13.7).
- [ ] Invite quotas (§9.5) exercised — and **tuned against observed behaviour**,
      which is what those numbers were always waiting for. **Blocked on a
      feature that does not exist:** invites are operator-only today (§9.8,
      the `invite` CLI), there is no path by which a *user* issues one, so
      there is no quota to exercise. Either build user-issued invites before
      phase 6 or drop this row — it cannot be satisfied as written.
- [ ] At least one user successfully sets up a second device unaided.
- [ ] At least one user reads the §9.6.1 screens and can accurately say what the
      operator can see. **If they cannot, the screens have failed** and they are
      the most important screens in the product.

---

## 33.8 Phase 7 — Play submission and public availability

| | |
|---|---|
| Goal | `com.thvjq.nexlink.social` on Play; the settings row in NexLink. |
| Estimate | 2–6 weeks, mostly waiting |
| Blocked by | Phase 6 |

§35 covers this in detail. The NexLink-side change is a **settings row and a
Play deep-link** (§16.2) — and it is the first change to `:app` in the entire
plan.

**Acceptance:**

- [ ] All §4.2.1 declarations accepted: UGC, data safety, foreground service
      types, `USE_FULL_SCREEN_INTENT`.
- [ ] Approved on Play.
- [ ] NexLink ships the settings row, with **no new permissions and no new
      background work** (§2.8, §16.8).
- [ ] Level 1 IPC (§16.3) — **optional here**, and a candidate for deferral.
      Level 0 already delivers the unified inbox.

---

## 33.9 Sequencing summary

```
Phase 0  Foundations          1 wk    ← no dependencies. Start here.
Phase 1  Spike homeserver     1 wk    ← BLOCKED ON THE DOMAIN (§26.2)
Phase 2  SDK decision         1 wk    ← timeboxed, hard
Phase 3  Messaging           6-10 wk  ← the product exists at the end
Phase 4  Calling             5-9 wk   ← first recurring cost
Phase 5  Operational          2 wk    ← no real users before this
Phase 6  Private launch       4 wk
Phase 7  Play                2-6 wk
                        ────────────
                             22-34 weeks part-time
```

**Phases 0 and 1 are independent of each other** and can run in parallel — one
is Android, one is server. Everything after is a chain.

### 33.9.1 If the timeline is too long

Honest options, with what each costs:

| Cut | Saves | Costs |
|---|---|---|
| **Ship messaging-only** (stop after phase 3 + 5 + 6) | 5–9 weeks and the VPS bill | The headline calling feature. §21.2.1 makes this genuinely clean — no infrastructure is wasted, and phase 4 can be added later without rework. |
| Defer the web client to phase 6 | ~1 week | §17.6's decision procedure needs it (§20.6), so this only works in the messaging-only variant |
| Defer Level 1 IPC (§16) | 1–2 weeks | Already deferred — §33.8 |
| Skip phase 5 | 2 weeks | **Do not.** This is the phase that prevents the incident that ends the project. |

**The messaging-only cut is the strong option** and should be on the table from
the start rather than considered under pressure at week 20.

---

## 33.10 What would justify stopping entirely

Recorded now, calmly, because a project without a stopping condition never
stops:

- **Phase 2 eliminates both SDKs.** Unlikely, but it would mean the client
  cannot be built on either available substrate.
- **Phase 4's spike shows Android-to-web calls cannot be made to interoperate**
  within a reasonable effort, *and* the messaging-only cut is unattractive.
- **Play rejects the UGC declaration** in a way that cannot be satisfied without
  breaking encryption (§31.8). This is the most likely of the three.
- The operator's available time is consumed by moderation (§31.5) before the
  user base is large enough to be worth it (§28.7).

None of these is likely. All of them are cheaper to have thought about in
advance.
