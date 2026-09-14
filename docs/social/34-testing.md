# §34 — Testing strategy

> **Confidence:** `REVISABLE`
> The invariant tests (§34.2) are durable. Tooling tracks the SDK decision.

---

## 34.1 What is worth testing here

This codebase has no test suite today. Rather than pretend a comprehensive one
will appear, this chapter identifies the **small number of tests whose absence
would be expensive** and argues for those.

The selection principle: test what is *silent* when it breaks. A crash is
reported by users within minutes. A message that sends unencrypted, a
notification that leaks content to a lock screen, or a foreground service that
crashes only on Xiaomi are all silent, and all of them have a cheap test.

---

## 34.2 The invariant tests

§2.8 lists seven invariants and says that violating one "is not a bug — it is a
decision reversal". A decision reversal that happens by accident is the failure
mode §2.1 exists to prevent, so the invariants get mechanical enforcement.

These run in CI on every commit. They are the highest-value tests in the
document.

| Invariant (§2.8) | Test |
|---|---|
| No code path sends content the server can read | Integration test: send to a room, read the event back from the admin API, assert the body is ciphertext and does not contain the plaintext |
| No private key material leaves the device unencrypted | Code review plus a grep gate on key-export APIs outside the §7.5 path |
| No account without a redeemed token and acceptance record | Server test: register without a token → fails. Register with → account exists **and** `acceptance_record` exists |
| No API captures screen/camera/mic to storage | **Grep `:social-*` for `MediaRecorder`, `MediaMuxer`, `AudioRecord` writing to file, `ImageReader` with a file sink.** Fail the build on a hit (§18.2) |
| Federation listener not enabled | `curl` the federation endpoint, assert no answer (§27.6) |
| NexLink gains no permissions or background work | **Diff `:app`'s merged manifest against the previous release.** Fail on any new permission, service or receiver |
| Logs carry no content, room IDs, user IDs or key material | Grep logging call sites in `:social-*` for MXID- and room-ID-shaped format strings (§27.5.1) |

Four of the seven are greps or curls. **They cost an afternoon to write and they
are the difference between a constraint and an intention.**

### 34.2.1 The manifest diff deserves emphasis

§1.5's first success criterion is that a NexLink user who never enables Social is
unaffected. The dependency rule (§10.4) protects the build graph; the manifest
diff protects everything else — a permission added "temporarily", a receiver
added for debugging, a service that crept in through a library.

Pair it with §10.4's APK size assertion: *if `:app`'s release bundle grows by
more than a set threshold between releases, fail and require an explicit
acknowledgement.* Together they are the mechanical form of D1.

---

## 34.3 Unit tests

Where the logic is pure and the bugs are real:

| Area | Why |
|---|---|
| Invite code normalisation (§9.3) | Crockford base32, hyphen stripping, case folding, the excluded `I L O U`. Pure function, many edge cases, used at the front door. |
| Token entropy and format | |
| Quota arithmetic (§9.5) | Account-age bands, concurrent-outstanding counting |
| Grapheme handling for reactions (§14.4.3) | ZWJ sequences, skin-tone modifiers, flags. **The classic source of a reaction that renders as three broken boxes.** |
| Message state machine (§14.2.2) | sending → sent → delivered → read, and every failure transition |
| Send queue (§13.5.1) | Ordering, retry, deduplication after a reconnect |
| Contract Parcelables (§16.4) | Round-trip through a `Parcel`, including version mismatch |
| Interface versioning (§16.6) | Old client against new service, and the reverse |

`SocialSession` being an interface (§11.6) means `:social-ui` is testable against
a fake with no network and no SDK. §11.6 notes this "is worth the abstraction on
its own merits" — this is where that pays.

---

## 34.4 Instrumented tests

Require a device or emulator:

| Area | Why |
|---|---|
| Local store encryption (§12.4) | Assert the database is unreadable without the Keystore key; assert it survives a reboot |
| Foreground service promotion (§15.2.2) | **Force every early-return path** and assert no `ForegroundServiceDidNotStartInTimeException`. This exact crash already shipped once. |
| Service type fallback | Simulate a refused primary type (§15.2.1) |
| Notification content (§13.4.2) | Assert no message content on a locked screen when the setting says so |
| Cross-app binding (§16.5.3) | Bind, unbind, service unavailable, version mismatch |
| **Invite-code field (§9.3)** | **Type a full code character by character** and assert the field reads `X7K2-9QMF-3BTD`. Two separate crashes lived here (§33.1.2) and neither was reachable from a unit test — the bugs were in the `TextWatcher`/`InputFilter` interaction, not the pure formatter. A test that only calls `InviteCode.format()` gives false confidence. |
| Signature permission (§16.3.1) | **Assert an app signed with a different key cannot bind.** Security control; test it. |

---

## 34.5 Manual testing, and why it is not a failure

Some things cannot be automated at reasonable cost, and pretending otherwise
produces a checklist nobody runs. These are explicit manual tests with a written
procedure, run before each release:

| Test | Section |
|---|---|
| Second-device verification by QR | §8.4.2 |
| History restore from key backup | §8.5 |
| Recovery key round-trip Android ↔ Element Web | §7.4.3 |
| Four-way call with a screen share | §33.5 |
| Cellular call interrupting a Social call | §19.8 |
| **Back during a call backgrounds it; the notification returns to it** | §19.5.4 |
| **A call is still up after 15 minutes** | §17.6.4 |
| Doze delivery | §15.9 |
| Battery over a real day | §13.7 |
| **OEM behaviour — Samsung plus one aggressive OEM** | §15.9 |

**The OEM row is not optional.** §15.9 records the reason: the bridge's original
foreground-service failure was invisible on stock Android and obvious on real
devices. That is the general shape of Android bugs in this area.

---

## 34.6 Server testing

| Test | When |
|---|---|
| §33.2's acceptance criteria as a script | Every deploy |
| §27.6's drift assertions | Daily, from outside Willard |
| Restore drill | Quarterly, and before every Synapse upgrade (§23.6.3) |
| Rate limits actually limit (§9.7) | Once, then after config changes |
| Media store full behaviour (§25.5) | Once, in phase 1 |

The drift assertions are the highest-value server tests, for the same reason as
§34.2: they catch a security posture that has quietly changed. A configuration
file is not a test.

---

## 34.7 What is not tested

Stated so the gaps are chosen rather than discovered:

| Not tested | Why |
|---|---|
| The SDK's crypto | It is the SDK's job and it has more review than this project could add (§2.3) |
| Synapse itself | Upstream's suite |
| Element Web | §20.1 — adopting it means adopting its testing too |
| Load and scale | §28 says the scale does not warrant it. Revisit if the assumptions break. |
| Penetration testing | Worth doing before public launch (§35). Out of scope for CI. |

---

## 34.8 CI

The repository has no CI today. The minimum worth having, in priority order:

1. **The §34.2 invariant checks.** Greps and a manifest diff. Cheap, and they
   protect the decisions.
2. `./gradlew :app:assembleRelease` — proving the dependency rule holds and the
   size assertion passes.
3. Unit tests.
4. `./gradlew :social:assembleRelease`.
5. Instrumented tests on an emulator. Slowest, least reliable, last.

Steps 1 and 2 together take a couple of minutes and deliver most of the value in
this chapter. **Build them in phase 0** (§33.1), when the module skeleton lands
and there is nothing yet to break.

**Built 2026-09-11: `tools/check-invariants.sh`.** Six checks — capture-to-file
APIs, identifier-shaped logging, NexLink's permission set against a committed
baseline, forbidden project dependencies, SDK imports outside `:social-core`,
and a retained date of birth. It needs no Gradle and runs in under a second.

Each check was verified by introducing a violation and confirming it fails.
That step is not optional: a check that has never failed is a check nobody has
tested, and it will be quietly broken by a refactor months from now.

---

## 34.9 Open questions

- **Does the chosen SDK provide a test harness** — an in-memory homeserver, or a
  fixture for crypto flows? §11.9 asks a related question. If it does not,
  integration tests need a real homeserver in CI, which is a materially larger
  commitment.
- ~~**Is a second physical device available?**~~ **Partly answered 2026-09-11.**
  Element Web is the second device (§33.3.2) — a real Matrix device on the same
  homeserver, which also discharges §7.4.3's recovery-key round-trip test.
  The *first* device is the open problem: see §34.10.
- **Who tests the OEM matrix (§34.5)** when the operator owns one phone? Possibly
  the phase 6 cohort, which makes their device diversity a selection criterion
  rather than an accident.


---

## 34.10 The emulator does not work on the development machine

Attempted 2026-09-11 and abandoned. Recorded so nobody spends the afternoon again.

An Android 14 AVD (`google_apis/x86_64`, Pixel 6) was created successfully and
KVM is available, but the emulator **dies silently 30-45 seconds into boot** —
no error in its own log, the process simply disappears. Reducing it to 2 GB RAM
and 2 cores did not help.

The cause is memory. The Fedora desktop VM has **15 GB total with ~11 GB already
committed**, leaving under 4 GB, and swap saturated to 7.8 GB of 8 GB during
each attempt. There is not enough headroom, and the VM is itself a guest on
Willard, so the shortfall is structural rather than something to tune.

**Use a real phone over USB instead**, which is the better answer regardless:

- §34.5 and §15.9 both already require real-device testing and say explicitly
  that an emulator result is not a substitute — *"the bridge's original failure
  was invisible on stock Android and obvious on real devices."*
- QR-code verification (§8.4.2) between a phone and a browser is a genuinely
  physical interaction that an emulator models badly.
- The operator develops and runs NexLink on a phone already, so the device
  exists.

`adb` over USB with that phone gives everything the emulator would have, and
more honestly. The AVD and its 4.2 GB system image can be deleted:

```bash
~/Android/Sdk/cmdline-tools/latest/bin/avdmanager delete avd -n nexlink-test
rm -rf ~/Android/Sdk/system-images
```

**What this blocks until a phone is attached:** Phase 0's "the acceptance gate
renders" criterion (§33.1.1), and §11.7 steps 1-6 — the SDK bake-off's decision
gate (§33.3.1). Everything else in both phases is done.