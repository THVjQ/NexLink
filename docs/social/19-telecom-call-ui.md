# §19 — Telecom integration and call UI

> **Confidence:** `REVISABLE`
> The Telecom decision (§19.2) is `DURABLE` and follows from D1. The rest
> depends on §17.6.

---

## 19.1 Why this chapter is unusual for this product

Most messaging apps integrating with Android Telecom are doing it from a
standing start. This one is not: **NexLink is already the device's default
dialer.** It holds `NexLinkInCallService`, `MANAGE_OWN_CALLS`,
`ANSWER_PHONE_CALLS` and the Call Log permission group (§1.4.1).

That makes the integration question sharper than usual, and it has exactly one
correct answer, which §19.2 gives. Getting it wrong does not produce a subtly
worse product; it produces two apps fighting over the call UI on the user's
actual phone.

---

## 19.2 The decision: self-managed, never an InCallService

| | `InCallService` | Self-managed `ConnectionService` |
|---|---|---|
| What it is | *The phone's dialer UI.* Renders all calls, including other apps' | An app that owns its own call UI and tells Telecom about its calls |
| Who should use it | The default dialer. One per device. | Every VoIP app that is not the dialer |
| NexLink | **Yes** — it is the dialer | — |
| Social | **Never** | **Yes** |

**Social registers a self-managed `ConnectionService` and draws its own call
UI.** It does not implement `InCallService`, does not request the dialer role,
and does not appear in the "default phone app" chooser.

The reason is D1 (§2.2) in its sharpest form. Two apps from the same developer,
under the same signing key, both claiming the dialer role, is a permission
posture that invites exactly the Play attention §1.4 exists to avoid — and
functionally, the user would be asked to choose between their SMS app and their
messaging app for the role of "phone". There is no version of that which is good.

Self-managed is also simply the correct API for what Social is: an app with its
own calls, which needs the platform to know about them so that audio routing,
interruption and Do Not Disturb behave.

### 19.2.1 What self-managed buys

- Telecom knows a call is in progress, so a cellular call can interrupt it
  correctly (§19.4) instead of both apps talking at once.
- Audio focus, routing and Bluetooth headset controls work through the platform
  rather than being re-implemented.
- Wired and Bluetooth media buttons answer and end calls.
- Do Not Disturb is respected without special-casing.

### 19.2.2 What it costs

`MANAGE_OWN_CALLS` in `:social`'s manifest, and a `ConnectionService`
implementation. The permission is not in a restricted group and is the intended
permission for this use — but note that `:social` declares it **for itself**.
Nothing in this chapter adds a permission to NexLink, per §2.8 and §16.8.

---

## 19.3 The two apps on one device

Both installed, both with calls. What the user experiences:

| Situation | Behaviour |
|---|---|
| Social call, no cellular activity | Social's UI. Telecom aware. |
| Cellular call arrives during a Social call | §19.4 |
| Social call arrives during a cellular call | Social posts a notification, does not ring aggressively, offers **End and answer** / **Decline** |
| Both idle | Nothing. No interaction whatsoever. |

**NexLink's dialer UI never renders a Social call.** They are separate call
surfaces from separate apps that happen to share a signing key. The unified
inbox (§16) unifies *messages*, deliberately not calls — a Social call in
NexLink's dialer would require NexLink to gain call-handling code for the social
product, which §16.8 forbids outright.

A missed Social call appears in NexLink's inbox the same way a Social message
does: as a conversation with unread content (§16.4.1). That is the right amount
of integration.

---

## 19.4 Interruption by a cellular call

The case that must work, because getting it wrong makes the phone unusable.

```
Social call active
        │
        ▼
cellular call arrives → NexLink (default dialer) rings
        │
        ├── user answers cellular
        │        ├── Social call → HOLD  (Telecom-driven, via onHold())
        │        ├── microphone released
        │        ├── local UI: "On hold — call in progress"
        │        └── other participants see the user as muted, not gone
        │
        │        cellular call ends → onUnhold() → resume, re-acquire mic
        │
        └── user declines cellular → Social call continues untouched
```

The self-managed `ConnectionService` is what makes this work: Telecom calls
`onHold()` on the Social connection because it knows the connection exists. An
app that did not register with Telecom would simply keep its microphone open
while the user took a phone call, which is the failure everyone has experienced
from some app or other.

**Held ≠ left.** A held participant stays in the MatrixRTC room; their
membership state is untouched (§17.7). Only the media is suspended. Dropping
them out of the call and re-joining afterwards would be visible to everyone and
would rotate the E2EE key twice (§17.5) for no reason.

### 19.4.1 Behaviour on unhold

Resuming is not free and the failure is easy to miss: the microphone must be
re-acquired, and it can fail if something else grabbed it. If re-acquisition
fails, the correct behaviour is to surface it — *"Microphone unavailable"*, with
a retry — and never to sit in a call silently transmitting nothing.

---

## 19.5 The call UI

### 19.5.1 Incoming

Per §15.6, the notification comes first and the service starts on answer.

| Element | Requirement |
|---|---|
| Notification | `CallStyle.forIncomingCall()`, `IMPORTANCE_HIGH`, ongoing |
| Full screen | `setFullScreenIntent(..., true)` — `USE_FULL_SCREEN_INTENT` is declared by `:social` in its own manifest |
| Locked device | Full-screen UI over the lock screen. Caller display name, no message content. |
| Actions | Answer, Decline. Answer with video as a third action where the call is a video call. |
| Timeout | 45 seconds ringing, then missed. Matches the delayed-event window staying comfortably inside `max_event_delay_duration` (§17.3.1). |
| Multi-device | Answering on one device cancels the notification on the others — driven by observing membership state (§17.7.1), not by a separate signal |

`USE_FULL_SCREEN_INTENT` is scrutinised at review. The justification is incoming
calls, which is the permission's intended purpose, and the app must not use it
for anything else — not for messages, not for "urgent" anything. One use, easily
defended.

### 19.5.2 In call

Controls available at all times, per §1.2:

| Control | Note |
|---|---|
| Mute | Must reflect true microphone state, including while held (§19.4) |
| Camera off | |
| Speaker / earpiece / Bluetooth | Routed through Telecom, not `AudioManager` directly |
| Share screen | §18. Hidden entirely in an audio-only call |
| End | |
| Participants | Who is in the call, who is muted, who is sharing |

Layout: one remote participant fills the frame with a local preview inset; two
to four tile; five and above tile with the active speaker promoted. A screen
share takes the primary position and pushes camera feeds to thumbnails (§18).

### 19.5.3 Returning to a call

A call in progress with the app backgrounded must be **one tap away**: the
ongoing `CallStyle` notification is the route back, and it is not dismissible.
A user who cannot find their way back to an active call will force-stop the app,
which ends the call in the worst possible way (§17.7).

---

## 19.6 Audio routing

Through Telecom's `CallAudioState`, not `AudioManager`. The platform knows about
the headset that connected mid-call; an app managing routing itself does not.

| Case | Expected |
|---|---|
| Bluetooth connects mid-call | Route follows, automatically |
| Bluetooth disconnects mid-call | Falls back to earpiece, **not speaker** — the phone may be in a pocket |
| Wired headset | Takes priority when present |
| Speaker toggle | User choice persists for the call, not across calls |
| Video call default | Speaker. Audio call default: earpiece. |
| Proximity sensor | Screen off on ear during an audio call; disabled for video |

The Bluetooth-disconnect row is the one that gets implemented wrong and is
genuinely unpleasant — a call that suddenly plays on speaker in a public place
is a privacy failure, not a UI nit.

---

## 19.7 What is deliberately not built

| Not built | Why |
|---|---|
| Social calls in NexLink's dialer | §16.8. NexLink gains no call-handling code for Social. |
| Social in the device's call log | The call log is a restricted permission surface NexLink already justifies for SMS and dialling. Social writing to it adds a restricted-permission question to a second listing for no user benefit. Social's own history lives in Social. |
| Dialer role for Social | §19.2 |
| Call recording | D3, §2.4. Not a UI omission — an architectural one (§18.2). |
| Voicemail | Not in scope (§1.2). A missed call is a missed call. |
| Call transfer, merge, conference-from-cellular | Cellular telephony features. Social is not a phone. |

---

## 19.8 Testing

Requires a real device with a real SIM. No emulator result here is meaningful.

| Test | Method |
|---|---|
| Cellular interrupts Social | Place a Social call, call the phone, answer. Confirm hold, mic released, resume. |
| Social interrupts cellular | Reverse. Confirm no aggressive ring. |
| Decline cellular | Confirm Social call unaffected. |
| Lock screen answer | Locked device, incoming call, answer from full-screen UI. |
| Multi-device cancel | Two devices signed in; answer on one; confirm the other stops ringing. |
| Bluetooth mid-call | Connect and disconnect during a call. Confirm fallback is earpiece. |
| Media button | Answer and end from a headset button. |
| DND | Confirm the platform's behaviour is respected, not bypassed. |
| Force-stop during a call | Confirm the delayed event reaps the membership (§17.7). |
| Ring timeout | Confirm missed-call state after 45s. |

The force-stop row is worth calling out: it is the only test that exercises the
dead-man's switch in §17.3.1, and that mechanism is invisible until the day it
is the thing standing between a user and a permanently ghost-occupied call.
