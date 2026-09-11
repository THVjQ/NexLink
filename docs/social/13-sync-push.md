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
