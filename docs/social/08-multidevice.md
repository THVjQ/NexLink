# §8 — Multi-device, cross-signing and verification

> **Confidence:** `REVISABLE`
> The mechanism is stable protocol. The user experience around it is where
> encrypted messengers most often fail their users, and where the design effort
> should go.

---

## 8.1 The requirement

From the brief: *"need to be able to login on another device and still have the
account"*, plus *"a website login page so people can send messages from PC"*.

Two devices, one account, both able to read the conversation. This is the single
hardest thing in encrypted messaging, and it is the strongest argument for
adopting Matrix rather than building (§2.3).

---

## 8.2 Why it is hard

The naive expectation is that an account has a key and any device signing in
gets it. That model has two fatal properties: the key must be transmitted to
the new device, and the server is the only available transport — so the server
can obtain the key, and end-to-end encryption is over.

The correct model inverts it: **each device has its own identity key that never
leaves it.** A message is encrypted separately to every device of every
recipient. Nothing needs to be transmitted, and the server never holds a key.

Two problems follow, and they are the substance of this chapter:

1. **Trust.** If devices have separate keys, how does anyone know a new device
   is genuinely the user's, rather than one the server inserted? → §8.3
2. **History.** A new device has no past session keys, so it cannot read
   anything sent before it existed. → §8.5

---

## 8.3 Cross-signing

Three keys per user, established at account creation:

| Key | Signs | Meaning |
|---|---|---|
| **Master** | The other two | The user's cryptographic identity |
| **Self-signing** | The user's own devices | "This device is mine" |
| **User-signing** | Other users' master keys | "I have verified this person" |

The private halves are stored in SSSS, encrypted under the recovery key (§7.4).

### 8.3.1 What it achieves

Without cross-signing, verifying a contact means verifying each of their devices
individually, and again whenever they add one. Users do not do this, so in
practice nobody verifies anything and the security property is theoretical.

With cross-signing, verification happens **once per person**. Their master key
signs their devices; a device signed by a master key you have verified is
trusted automatically.

### 8.3.2 The defence against a malicious server

Restating §3.3-A4 concretely, because this is the crux of the security model:

A malicious homeserver can add a device to a user's account and claim it is
theirs. It **cannot** produce a signature from the user's self-signing key,
because it does not have it. So the injected device appears to every verified
contact as **unverified** — and the client displays a warning.

**The entire defence therefore rests on users noticing that warning.** Which
makes the following design requirements security requirements, not polish:

- Warnings must be visually prominent and must not be dismissible-and-forgotten.
- Warnings must be **rare**, so they are not trained away. Every false positive
  erodes the real one.
- The wording must state the consequence, not the mechanism: "a new device was
  added to Jeff's account and hasn't been verified — messages may be readable by
  someone else" rather than "unverified session".

---

## 8.4 Verification

Two mechanisms, both establishing an authenticated channel outside the server.

### 8.4.1 Emoji SAS

Both parties are shown a sequence of emoji derived from a shared secret and
confirm they match, out of band — in person, or over a voice call where they
recognise the other's voice.

Properties: works remotely; requires simultaneous presence; the security depends
entirely on the comparison happening over a channel the server does not control.

**Copy requirement:** the UI must say *why* — "compare these with Jeff in person
or on a call you trust" — not merely "do these match?". A user who compares them
in the chat itself has achieved nothing, and will do exactly that if not told
otherwise.

### 8.4.2 QR code

One device displays a QR code encoding key material; the other scans it. Faster,
less error-prone, and the preferred path for a user verifying **their own**
second device, where both are physically present.

This is the primary path for the web client (§20): the phone scans, or is
scanned by, the browser.

**Status 2026-09-15: not built.** `VerifyActivity` implements §8.4.1's SAS
emoji path and nothing else — no QR display, no scanner, no camera use for
verification at all. SAS passed on hardware on 2026-09-13 (§33.3.3), so devices
*can* be verified; the QR shortcut simply does not exist.

That matters because **phase 3's acceptance list says "verify by QR"** (§33.4),
and it cannot be satisfied as written. Three ways forward, and it is a decision
rather than an oversight:

1. **Accept SAS as the second-device path** and change the acceptance row. SAS
   is the stronger of the two for a user verifying their own devices with both
   in hand — it is slower, not weaker.
2. **Build QR.** It needs a camera permission in `:social` that the app does
   not currently hold for this purpose, a scanner, and the SDK's QR flow. Not
   large, but it is new permission surface on a product whose §4.2.1 argument
   is that it asks for very little.
3. **Lean on §8.4.3's recovery key**, which is built and tested — a new device
   proves possession and gains trust with no second device present at all.

Whichever is chosen, the acceptance row should name it. A checklist item for a
feature nobody built is how a phase gets declared done on a technicality.

### 8.4.4 Two handsets, 2026-09-15 — two defects, and one thing still not working

The first attempt at a real second device (SM-S928B, signed in to the same
account as the SM-G990E) failed immediately, and how it failed was worse than
that it failed.

**Defect 1: a sixty-frame Rust backtrace, rendered into the screen.** Tapping
*Verify this device* produced

```
Couldn't start: msg=Failed retrieving user identity, details=Failed retrieving
user identity

Stack backtrace:
   0: <unknown>
   …
   8: ffi_matrix_sdk_ffi_rust_future_poll_u64
   …
  62: _ZL15__pthread_startPv…
```

verbatim, in the UI. The SDK's errors are Rust errors and their `message`
carries the trace; `status = "Couldn't start: ${it.message}"` put it on screen.
It tells a user nothing and it is not what §27.5's rule about what may be shown
was written for. The exception now goes to the log and the screen gets a
sentence.

**Defect 2 — and the message was true.** `Failed retrieving user identity`
meant exactly what it said: the account had **no cross-signing identity**.
Confirmed against the server — `master_keys` and `self_signing_keys` both
absent — because recovery had never been set up on it (§7.4.4 is what creates
them). So the app offered *Verify this device* on an account where verification
was impossible, and the first replacement copy — *"give it a moment and try
again"* — was no better, because it invites a retry that can never succeed.

It now checks and says the actual thing: *"Set up recovery on your other device
first. Verifying needs it, and without it there is nothing for this device to be
checked against."*

There is also a genuine race underneath, so the controller is retried for ~15
seconds: a device that has just signed in has no local identity until the first
sync, and that window is precisely when a user taps Verify.

**And then it passed — on two real handsets.**

The earlier stall was **operator error, not a defect**: both devices had been
put into the *requesting* state, so there were two requests and nobody to accept
either. The flow needs one device waiting on the Verify screen and the other to
press Start. Done that way:

- the waiting device showed the incoming request — *"It's me — continue"* /
  *"Refuse"*, which is §8.3.2's rule that a request is surfaced, never
  auto-accepted;
- **both screens showed the same seven emoji** — Penguin, Heart, Santa, Globe,
  Rocket, Trumpet, Pig;
- confirming on both completed it, and **the server agrees**: a `keys/query`
  shows both device keys carrying a self-signing signature. That is the check
  §33.3.3 used, and it is the one that cannot be faked by a UI state;
- the newly verified device then opened the room and **read the entire history**
  — six messages, zero undecryptable.

That closes phase 3's second-device row, by SAS rather than QR (§8.4.2).

**One defect left behind by it: a status line that outlived its moment.** Both
screens sat on *"Confirming…"* over a verification that had already finished —
the Done button was right there underneath. The same shape as *"Getting ready…"*
earlier the same evening. The status is now cleared whenever the step advances,
which kills the class rather than the instance: a status line is a note about
what is happening *now*, and leaving it set means it sits over whatever comes
next. Both instances looked exactly like a stall and neither was one.

### 8.4.3 Recovery key

A new device can also gain trust by proving possession of the recovery key,
which unlocks SSSS and therefore the cross-signing keys. This is the path when
no other device is available — the phone is lost, and the user is signing in on
a new one.

**It is also the reason the recovery key is equivalent to the account** (§7.3)
and must be protected accordingly.

---

## 8.5 History on a new device

Verification establishes trust for messages sent *from now on*. It does nothing
for the past — Megolm sessions from before the device existed were never shared
with it.

**Key backup** solves this: Megolm session keys, encrypted client-side under a
key held in SSSS, stored on the server. A verified new device unlocks SSSS with
the recovery key, retrieves the backup key, downloads the sessions, and can
decrypt history.

### 8.5.1 Consequences

| Situation | History available |
|---|---|
| New device, key backup enabled, recovery key held | Yes, all of it |
| New device, key backup enabled, recovery key lost | **No** |
| New device, key backup never enabled | **No** |
| Web client signed in and verified | Yes, subject to the above |

Row 3 is the one that hurts, and it is why §7.7 leans toward mandating the
recovery-key gate even on the email path. A user who declined backup at signup
discovers the consequence months later, when it is unfixable.

### 8.5.2 Backup health

Key backup can silently break — a client failing to upload new sessions leaves a
backup that appears present but is stale. The client must surface backup state
explicitly:

- A visible indicator of whether backup is on and current.
- Detection of "backup exists but this device is not uploading to it".
- A periodic verification that a recent session is actually present in the
  backup, not merely that the backup exists.

A backup nobody checks is a backup that does not exist. This is the same lesson
as §23, applied to key material.

---

## 8.6 Device management

Users need to see and control their devices. The surface:

| Element | Requirement |
|---|---|
| Device list | Name, verification state, last seen, approximate location by IP |
| Verify | Start SAS or QR from any verified device |
| Rename | User-set names; the default should be the model, not a random ID |
| Sign out remotely | Immediate; the device stops receiving new Megolm sessions |
| Sign out all others | One action, for a suspected compromise |
| New device alert | A notification to existing devices when one is added |

**The new-device alert is a security control**, not a convenience. It is how a
user learns that a device they did not add now exists — the detection half of
the §8.3.2 defence.

**Remote sign-out does not delete local data** on the signed-out device. It
cannot; that device may be offline or hostile. The UI must not imply otherwise —
"this device can no longer receive new messages" is accurate; "wiped" is not.

---

## 8.7 The web client as a device

Element Web (§20) is a device like any other: its own keys, its own verification,
its own entry in the device list.

Additional constraints because it runs in a browser:

- Keys live in IndexedDB. A shared or public computer therefore leaves the
  account accessible until signed out. The UI must warn on sign-in, and offer a
  session that does not persist.
- Browser storage can be cleared by the user or the browser without warning,
  which appears as a lost device. Recovery is the normal new-device path.
- Verification is by QR with the phone, which is the fastest path and should be
  the default presentation.

---

## 8.8 Failure modes and their handling

| Failure | User sees | Handling |
|---|---|---|
| Message from an unverified device | Shield/warning on the message | Prompt to verify; never hide the warning |
| Missing Megolm session | "Can't decrypt this message" | Request the key from own other devices; retry on backup restore |
| Backup key wrong | Restore fails | Clear error naming the recovery key; do not offer to "reset" without a full explanation of what is lost |
| Cross-signing reset by user | All prior verifications invalidated | Explicit, heavily warned action; notifies contacts |
| Clock skew | Verification fails | Detect and report as a clock problem, not a security failure |

**"Can't decrypt this message" is the most common real-world encrypted messaging
complaint.** Handling it well — explaining why, saying what to do, retrying
automatically when backup restores — is high-value UX work and should be
budgeted as a feature, not treated as an error string.

---

## 8.9 What this costs the user

Stated plainly because it is the honest counterweight to the security benefit:

- One extra step at signup (recovery key).
- Verifying each new device.
- Occasionally verifying a contact.
- Occasional undecryptable messages.
- Permanent loss of history if the recovery key is lost.

Every one of these is a direct consequence of the server not being trusted. The
acceptance gate (§9.6) should set the expectation, so these read as the design
working rather than the app being broken.

---

## 8.9a Implementation note — the SDK does not provide this surface

Found 2026-09-11 while auditing the Rust SDK bindings (§11.7.3): **there is no
device-listing API.** The bindings expose only deep links into the homeserver's
account-management web UI.

Everything in §8.6 therefore rests on raw Client-Server API calls from
`:social-core` — `GET/PUT/DELETE /_matrix/client/v3/devices/...` — sharing the
SDK's access token. Device deletion needs User-Interactive Auth, and the
new-device alert has to be built by polling and diffing the device list rather
than by observing an event.

Budget for it in phase 3. It is not large, but it is not free, and it was
written here as though the SDK would supply it.

## 8.10 Open questions

- Should verification be **mandatory** before messaging a new contact? Maximally
  secure, and would be widely abandoned at that prompt. **Leaning no** — prompt,
  do not block.
- Should the web client be offered a persistent session at all? Convenient,
  weaker. **Leaning yes with an explicit choice** at sign-in.
- How aggressively should the app nag about an unverified device? Too little and
  the §8.3.2 defence fails; too much and warnings are trained away. Needs real
  usability testing rather than a guess.
