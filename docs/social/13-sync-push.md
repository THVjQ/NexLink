# §13 — Sync, offline behaviour and push

> **Confidence:** `REVISABLE`
> The constraints are Android's and are durable. The mechanisms depend on §11.

---

## 13.1 The problem

A messenger must deliver a message within seconds, to a device that is asleep,
on a battery the user is protective of, through an operating system actively
trying to stop it. NexLink has already fought this battle once for the Computer
Bridge, and §13.6 draws on what that cost.

The additional constraint here: **push payloads cannot carry message content**,
because the server does not have it (§5.7). Every notification therefore
requires a wake, a sync and a decrypt before it can say anything useful.

---

## 13.2 Sync

### 13.2.1 Foreground

While the app is open, the SDK maintains a long-poll sync. This is the SDK's
concern, not the application's, and should be left alone.

The application's responsibility is lifecycle: sync runs while the process is
foregrounded and stops promptly when it is not. A sync loop that continues after
`onStop` is a battery complaint.

### 13.2.2 Cold start

The measure users feel. Opening the app must show *something* immediately —
rooms from the local store — while sync catches up in the background. A spinner
on a populated store is a self-inflicted wound.

Target: room list visible in **under 500 ms** from cold start, from local data,
with no network dependency. Sync results arrive and update in place.

### 13.2.3 Sliding sync

Where available, sliding sync fetches the visible window first rather than the
entire account state. The difference on an account with many rooms is
substantial, and it is one of the arguments in §11.4.

Where unavailable, initial sync on a large account can take tens of seconds.
This is a first-run experience problem, and the mitigation is honest progress
reporting rather than an indeterminate spinner.

---

## 13.3 Push

### 13.3.1 The chain

```
sender → homeserver → push gateway → FCM → device
                                            └→ wake, sync, decrypt, notify
```

The homeserver never sends content to the gateway for an encrypted room. The
push carries an event ID and a room ID at most.

### 13.3.2 The notification resolution problem

The user sees a notification before the app knows what it says. Handling:

1. Push arrives. Post a placeholder notification **only if resolution is likely
   to take more than ~1 second** — otherwise resolve first and post once.
2. Sync the specific event.
3. Decrypt.
4. Update the notification in place with sender, content and avatar.

**If decryption fails** — a missing Megolm session (§8.8) — the notification must
say something honest and useful: "New message from Jeff" if the sender is known,
"New message" if not. Never leave a placeholder that says "Encrypted message"
indefinitely; that is the visible symptom users report as the app being broken.

### 13.3.3 FCM and the alternative

FCM is the pragmatic default: it is what Android devices already maintain a
connection for, and any other approach means holding a socket open, which is a
battery disaster.

**UnifiedPush** is the alternative for de-Googled devices. It is a genuine
consideration for a privacy-positioned product and a real cost in additional
paths to test. §2.7 leaves it open; the recommendation is FCM first, with the
push implementation abstracted enough that UnifiedPush is an addition rather
than a rewrite.

**What FCM sees:** that a notification was sent to a device, and when. Not
content. This is metadata leakage to a third party and belongs in the §3.7
disclosure honestly — most encrypted messengers have exactly this property and
most do not mention it.

---

## 13.3.4 Built and verified 2026-09-13

End to end on hardware: a message sent from another account produced a
notification reading **"sender205427: Third push test — …"** on a phone whose
app process had been killed. Real sender, decrypted body, ~8 seconds. Not the
fallback — §13.3.2's full resolve path.

**§21.7 was wrong and is corrected.** It listed Sygnal as deliberately not
deployed because *"FCM direct is the phase-3 default"*. Synapse cannot talk to
FCM: it ships `emailpusher` and `httppusher` and nothing else — verified by
listing `synapse/push/` inside the container. A Matrix push gateway is
mandatory, not a matter of preference. Sygnal runs as TrueNAS app
`nexlink-social-push` on port 8062.

Sygnal rather than a hand-rolled gateway because FCM v1 needs OAuth2 with a
signed service-account assertion. That is protocol-adjacent code this project
would then own — §11's argument, applied to push.

The pusher registers with `PushFormat.EVENT_ID_ONLY`, so the payload is a room
id and an event id and nothing else. The notification's text comes from the
local store after decryption, never from the server.

### Three failures, each invisible from the code

**1. Synapse refuses to talk to its own push gateway.**

```
synapse.http.client: Blocking access to 192.168.0.10
SynapseError 403: IP address blocked
```

Synapse blocks outbound requests to private addresses by default — SSRF
protection, and a good default. Fixed with an `ip_range_whitelist` of a **single
/32**, not the RFC1918 range: the blacklist exists to stop a malicious pusher
URL turning the homeserver into a probe of the local network, and opening
192.168.0.0/16 would hand that straight back.

**2. A cold push had no session, and the handler reacted by destroying push.**

FCM revives a killed process to deliver, so nothing has run `restore()` and
`current()` is null even though credentials are on disk. The first version read
that as "signed out" and called `unregister` — **one cold delivery would have
disabled push permanently**, and the symptom would have been "push worked for a
day and then stopped". It survived only because `unregister` also needs a
session and quietly did nothing.

The handler now restores the session, which is the entire point of being woken:
§13.3.1's chain is *wake, sync, decrypt, notify*, and this is the wake. It no
longer unregisters from a push handler at all — that is the wrong place to make
a destructive change to server state that nobody will observe.

**3. Registration ran once, before there was anything to register.**

It was called from `onCreate`, and `HomeActivity` is created *before* the user
signs in. So it ran against a null session and never ran again. Now driven by
the session state reaching `SignedIn`, with a retry if it fails — a transient
failure must not disable push for the process lifetime with nothing said.

### Both variants, 2026-09-14

`com.thvjq.nexlink.social.debug` was registered in the same Firebase project —
**the same project, a second app**, because push credentials are per-project and
a new project would have invalidated the service-account key already working on
Willard.

Verified: `FirebaseApp initialization successful` in the debug build, the pusher
registers as `com.thvjq.nexlink.social.debug.android`, and a message sent to a
**killed** debug process produced a notification in **8 seconds**. Sygnal was
already configured with both app ids, which is why nothing server-side needed
changing — and that mapping is worth checking first if a variant ever stops
receiving, because a mismatched app id fails silently on both sides.

The conditional plugin application in `social/build.gradle` stays. A clean clone
of this public repository still has no `google-services.json`, and it must build
without one.

---

## 13.4 Notifications

### 13.4.1 Requirements

| Requirement | Detail |
|---|---|
| Per-conversation channels | Android notification channels per room, so users control granularly |
| Grouping | Messages grouped by conversation, conversations grouped by app |
| `MessagingStyle` | Required for correct rendering, and for Conversations surface support |
| Reply inline | Direct reply from the notification, without opening the app |
| Mark read inline | An action, so a notification can be dismissed meaningfully |
| Mute per conversation | Honoured client-side and pushed to server push rules |
| No content on lock screen (optional) | User setting; default to showing, respecting the system's own sensitive-content setting |

### 13.4.2 What must not happen

- **No notification for a message the user has already read elsewhere.** Read
  receipts from another device must dismiss it. Multi-device users otherwise
  drown in already-handled notifications, which is the most common complaint
  about multi-device messengers.
- **No duplicate notifications** from a re-delivered push.
- **No notification content in logs.**

---

## 13.5 Offline behaviour

The app must be fully usable with no network:

| Action | Offline behaviour |
|---|---|
| Read history | Works — local store |
| Search | Works — local, client-side |
| Send message | Queued, shown as pending, sent on reconnect |
| Send attachment | Queued with the file referenced, uploaded on reconnect |
| React | Queued |
| Start a call | Fails with a clear message |

### 13.5.1 The send queue

Non-negotiable properties, and the direct lesson from NexLink's bridge (§5.1 of
that work): **a queued message is never silently lost.**

- Queued messages survive process death and reboot.
- Retries use exponential backoff with a ceiling.
- A message that has failed permanently is shown as failed, with a retry action
  — never quietly dropped, never silently marked sent.
- **Order within a conversation is preserved.** A queued message that fails must
  not let a later one overtake it, or the conversation reads wrongly.
- Idempotency: a send retried after an ambiguous failure must not produce two
  messages. Transaction IDs handle this and must be persisted with the queue
  entry, not regenerated on retry.

That last point is precisely the bug fixed in NexLink's bridge poll loop — an
identifier marked handled before the dispatch succeeded, so a failure looked
like a success. **The same class of error is available here and the same
discipline applies: record success after the operation, never before.**

### 13.5.2 The surface — built 2026-09-12

The SDK owns the queue itself: persistence across process death, backoff,
ordering and transaction-ID idempotency are all `matrix-rust-sdk`'s, and
§13.5.1's properties are satisfied by not reimplementing them. What the
application owns is **saying which state a message is in**, and that is where
this went wrong first.

**The bug.** `RustTimeline` mapped send state in one expression:

```kotlin
state = if (ev.isRemote) MessageState.SENT else MessageState.SENDING
```

A permanently failed message is not remote. So it rendered as "Sending…"
**forever** — no crash, no log line, no error path taken. The app looked
healthy and the message never arrived. This is worse than the loss §13.5.1
warns about, because a spinner that never resolves gives the user positive
evidence that it is still working.

**What replaced it.** The SDK's `EventTimelineItem.localSendState` carries
`Sent` / `NotSentYet` / `SendingFailed(error, isRecoverable)`. Those reduce to
plain values which `SendState.classify` turns into a state:

| SDK | App state | Because |
|---|---|---|
| `Sent`, or `isRemote` | `SENT` | Confirmed by the server |
| `NotSentYet`, or no state | `SENDING` | In flight |
| `SendingFailed(isRecoverable = true)` | `QUEUED_OFFLINE` | **The SDK is still retrying by itself** |
| `SendingFailed(isRecoverable = false)` | `FAILED` | Nothing more happens without the user |

The `isRecoverable` split is the load-bearing one. Telling a user to retry
something that is already retrying is how an app teaches people to ignore its
warnings; staying silent about something genuinely stuck is the original bug
again. They must be different words on screen.

`QueueWedgeError` is reduced to `SendFailure`, collapsing cases that share a
remedy and keeping apart the ones that do not:

| `SendFailure` | Shown as | Offers |
|---|---|---|
| `OFFLINE` | "Waiting for network" | Retry now |
| `SERVER_REJECTED` | "Not sent — the server refused it" | Retry, Discard |
| `MEDIA_REJECTED` | "Not sent — this attachment was refused" | Retry, Discard |
| `VERIFICATION_REQUIRED` | "Held — an unverified device is in this chat" | **no Retry** — Discard only |
| `UNKNOWN` | "Not sent" | Retry, Discard |

**`VERIFICATION_REQUIRED` deliberately has no Retry.** The send is being held
because someone in the room has a device or identity this account has not
vouched for — a §8 security decision, not a transport error. A "Retry" there
would not send, and offering it trains the user to tap past the one prompt in
the app that must not be tapped past. The remedy is §8.4's verification flow.

**Discard exists, and it asks.** "Never silently dropped" does not mean "kept
forever": a failed bubble that cannot be got rid of is its own broken state. So
the user can discard — explicitly, through a confirmation, because the text
exists nowhere else. `SendHandle.abort` returns false when the message was sent
between the tap and the cancel; the UI says so rather than pretending.

**Why `SendState.classify` is a separate object.** The original one-line bug was
untestable where it lived: it read an `EventTimelineItem`, which is an FFI
object with a native peer and cannot be constructed in a unit test. So the
single line carrying the "is this message lost?" decision had no test, and was
wrong. The judgement now lives on plain types in `:social-core`, and
`SendFailureTest` covers it — verified by reintroducing the original expression,
which fails two of the eight tests.

**`SendHandle` lifetime.** Handles are resolved from the current timeline at the
moment the user taps, used, and destroyed — never cached on the item. A queued
message is replaced by a `TimelineDiff.Set` on *every* state change, so a cached
handle is either leaked on each transition or destroyed while the UI still
points at it.

---

## 13.6 Background execution: the hard-won part

NexLink has already paid for this knowledge. It transfers directly.

### 13.6.1 What was learned from the bridge

| Lesson | Application here |
|---|---|
| A `ScheduledExecutorService` is **not** a wake source. Doze freezes it. | Never rely on an in-process timer for delivery. |
| `startForegroundService()` demands `startForeground()` on **every** path within seconds, or the platform kills the process. | §15 makes this structural. |
| WorkManager cannot reliably start a foreground service on Android 12+. | A worker must do the work itself, not delegate to a service that may not start. |
| Battery-optimisation exemption does **not** grant background FGS starts. | Do not design around it. |
| `setAndAllowWhileIdle` alarms fire through Doze; exact alarms need a permission. | Available as a fallback tick if ever needed. |

### 13.6.2 Why Social needs less of this than the bridge did

The bridge polls. Social is pushed. FCM's high-priority messages are an
OS-sanctioned wake mechanism that bypasses Doze, which is exactly the thing the
bridge lacked and had to reconstruct with alarms.

**Consequence: Social should not need a persistent foreground service for
messaging at all.** Push wakes the app, a short background task syncs and
notifies, the process exits. Foreground services are needed for calls (§15),
not for message delivery.

This is a significant simplification and should be defended — the temptation to
add a permanent service "for reliability" is real, and it would be both a
battery cost and a Play policy question for no delivery benefit.

### 13.6.3 High-priority push budget

Android limits how many high-priority FCM messages an app receives when the
device is idle, replenished by user interaction. An app that marks everything
high-priority exhausts its allowance and its genuinely urgent messages get
delayed.

Allocation:

| Event | Priority |
|---|---|
| Direct message, or mention | High |
| Incoming call | High — always |
| Group message, no mention | Normal |
| Reaction, read receipt, typing | Normal or suppressed entirely |

Typing indicators must **never** generate a push.

---

## 13.7 Battery

Users uninstall messengers that appear in the battery screen. Targets:

| Scenario | Target |
|---|---|
| Idle, no messages, 24h | < 1% battery |
| 100 messages received, dispersed | < 2% |
| One hour of video call | Dominated by the call; measured separately (§17) |

Practices that matter: no wakelock held longer than the work requires; no
polling; batch sync rather than syncing per event; never sync on a metered
connection for non-urgent updates.

---

## 13.8 Open questions

- Should the app show a foreground-service notification during a long background
  sync? It would be honest and it would look like a battery drain. **Leaning no**
  — keep background syncs short enough not to need one.
- How should a decryption failure that persists be surfaced after some time?
  A silent permanent placeholder is bad; a scary error is worse. Needs design.
- Is UnifiedPush in scope for the first release? **Leaning no**, with the
  abstraction in place so it is cheap later.
