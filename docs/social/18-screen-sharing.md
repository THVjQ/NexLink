# §18 — Screen sharing in call

> **Confidence:** `REVISABLE`
> The constraints are `DURABLE` — they come from D3 (§2.4). The implementation
> depends on which client option §17.6 selects.

---

## 18.1 The feature, stated narrowly

A participant in an active call may publish their device screen as an additional
video track. Other participants see it. When the call ends, it stops.

That sentence is the whole feature, and its narrowness is deliberate. Every
expansion of it — sharing outside a call, sharing to a file, sharing that
survives backgrounding — is either D3 (§2.4) or a Play review problem, usually
both.

---

## 18.2 The constraints from D3

Restated as implementation requirements, because this is the chapter where they
are enforced:

| Constraint | Implementation |
|---|---|
| Sharing only within a call | The share control does not exist outside the call UI. `MediaProjection` is never requested except from a call in `CONNECTED` state. |
| Explicit initiation per session | Android's consent dialog, every time. No cached grant — there is no API for one, and attempting to look like there is would be a review flag. |
| Persistent indicator for all | Every participant, including the sharer, sees a durable on-screen indicator for the entire duration. |
| Stops when the call ends | The projection is torn down in the call's teardown path, not left to garbage collection. |
| No background continuation | Leaving the app keeps the share running — that is the point — but ending the call always stops it. |
| **No file sink, ever** | The `MediaProjection` output goes to a WebRTC video source and nowhere else. No `MediaRecorder`, no `ImageReader` writing to storage, no `MediaMuxer`. |

The last row is the §2.8 invariant — *no API is called that captures screen,
camera or microphone content to persistent storage* — and it is checkable. A
grep for `MediaRecorder`, `MediaMuxer` and `createOutputFile` across `:social-*`
returning nothing is a valid, cheap review gate. Put it in CI (§34).

---

## 18.2.1 Option A changes who does the capturing — measured 2026-09-14

§17.6 chose Option A, and §18.3 below was written for Option B. **With the
widget, the app does not own the capture at all** — Element Call would call
`getDisplayMedia()` inside the WebView, and the MediaProjection plumbing in
§18.3 never runs.

Measured on the handset, in a live call: **Element Call offers no screen-share
control on Android.** `hideScreensharing=false` is passed in the widget
properties, and the in-call overflow shows only Audio / Video / Preferences /
Feedback.

That is upstream behaving correctly, and the reason was then probed directly
inside our own WebView rather than assumed:

```json
{"gdm":"undefined","gum":"function",
 "ua":"… Android 16; SM-G990E …; wv) … Chrome/152.0.7977.87 Mobile Safari/537.36"}
```

`navigator.mediaDevices.getUserMedia` is a function; **`getDisplayMedia` does
not exist**, on a current WebView (Chromium 152) on Android 16. A share button
would be a button that throws. Chrome for Android is the same engine and the
same answer.

So §18 as written is **not implementable on top of Option A**, and this is a
real cost of that decision that §17.6.3 did not price. Three ways out, in
increasing order of work:

1. **Ship without Android *originating* a share.** Note what this does and
   does not cost: **receiving** a share is ordinary video, so a phone sees a
   laptop's screen perfectly well, and Element Web's toolbar does carry the
   share control (observed in the same session, §17.6.4.4). So "share your
   screen" works in this product today — from a computer. Only "share *this
   phone's* screen" is gone.
2. **Host-side capture fed to the widget.** The app runs MediaProjection per
   §18.3 and hands frames to the page. There is no widget-API action for "here
   is a video track", and no way to construct a `MediaStream` in the page from
   native frames at video rates. This is not a hard version of (1); it is not
   available.
3. **A second, native LiveKit connection publishing only the screen track.**
   Technically the closest thing to a real answer — `livekit-android` plus
   MediaProjection, publishing into the same SFU room. It founders on §17.5:
   the per-participant E2EE key is negotiated *inside* the widget and the
   widget API does not expose it, so the native track could only be published
   unencrypted. Doing that would put plaintext video through the SFU for the
   one medium most likely to contain someone's passwords. Rejected on those
   grounds, not on effort.
4. **Option B for Android** — the native calling stack §17.6 rejected. Screen
   share is then straightforward. This is a rewrite of the call layer, and the
   §11.4 argument against it has not changed.
5. **Wait for upstream.** `getDisplayMedia` on Android is a standing Chromium
   gap, not an Element Call omission. There is no date.

**Decided 2026-09-15: (1).** Android does not originate a screen share. The
operator delegated the call and this is it, with the reasoning stated so it can
be overturned on evidence rather than on mood:

- (2) is not a harder version of (1), it is unavailable — there is no way to
  build a `MediaStream` in the page from native frames at video rates.
- (3) would put **plaintext video through the SFU** for the one medium most
  likely to be showing somebody's passwords. That is a worse outcome than the
  missing feature, and it is not close.
- (4) is a rewrite of the call layer to buy one feature, against §11.4's whole
  argument.
- (5) has no date.

**§1.5's success criterion changes accordingly**: "four participants and one
screen share" now reads *with the share originating from a computer*. Receiving
a share on a phone is ordinary video and works. This is a narrowing of the
product and is written here rather than discovered at acceptance.

Revisit only if upstream exposes the call's encryption key to the host, which
would make (3) safe.

The §2.8 invariant is unaffected either way, and so is every row of §18.2 that
is about *not* doing something. What changes is the row that says the share
control exists.

---

## 18.3 The plumbing

```
user taps Share
      │
      ▼
MediaProjectionManager.createScreenCaptureIntent()
      │  system consent dialog — every session
      ▼
onActivityResult → resultCode, data
      │
      ▼
start ScreenShareService  (foregroundServiceType="mediaProjection")
      │  service must be running BEFORE getMediaProjection()
      ▼
MediaProjectionManager.getMediaProjection(resultCode, data)
      │
      ▼
VirtualDisplay → Surface → WebRTC video source → publish as second track
```

### 18.3.1 The ordering trap

**The foreground service must already be running, and already promoted, before
`getMediaProjection()` is called.** On Android 14+ calling it without an active
`mediaProjection`-typed foreground service throws `SecurityException`.

This collides directly with §15.2's rule — *promote first, decide afterwards* —
and the collision is the safe direction: promote the service, then acquire the
projection, and if acquisition fails, `stopCleanly()`. The failure path is a
service that started and stopped, which is harmless. The reverse ordering is a
crash.

```kotlin
// ScreenShareService
override fun onCreate() {
    super.onCreate()
    promoteToForeground()                    // §15.2.1, unchanged
}

override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
    promoteToForeground()
    val projection = runCatching {
        mpm.getMediaProjection(resultCode, resultData)   // may throw
    }.getOrNull()
    if (projection == null) { stopCleanly(); return START_NOT_STICKY }
    projection.registerCallback(stopCallback, handler)   // §18.3.2
    beginCapture(projection)
    return START_NOT_STICKY
}
```

`START_NOT_STICKY` is correct here and `START_STICKY` would be wrong: a
resurrected screen-share service has no valid projection token and no call to
attach to. If the process dies, the share is over.

### 18.3.2 The stop callback is not optional

`MediaProjection.registerCallback` fires when the **user** stops the share from
the system UI — the notification, or the status bar chip. Android 14+ throws if
a projection is started without a registered callback, but the real reason to
care is correctness: a user who stops sharing from the system UI and finds the
app still showing "sharing" has been lied to about something the entire feature
is built to be honest about.

The callback must: stop the capture, unpublish the track, update the call UI,
notify other participants, and stop the service cleanly. In that order.

---

## 18.4 The indicator

§2.4 requires that "every participant sees a persistent indicator for its full
duration". Three separate surfaces, because they fail independently:

| Surface | What | Owner |
|---|---|---|
| System | The platform's own screen-cast indicator | Android. Cannot be suppressed and must not be attempted. |
| Sharer's app | Persistent bar in the call UI with a one-tap **Stop sharing** | The app |
| Other participants | The shared tile is labelled with the sharer's display name | The app |

The platform indicator is the honest one and the one that cannot be defeated.
The app's own are additive.

**Nothing in the app may overlay, obscure or visually compete with the system
indicator.** This is explicitly a review consideration and, more to the point,
the behaviour of screen-capture malware. An app with NexLink's permission set
already attracts scrutiny; it does not need to look like that.

---

## 18.5 What gets captured, and the notification problem

`MediaProjection` captures the display. Everything on it. Including:

- Notifications that arrive while sharing — **including NexLink's SMS
  notifications**, and including Social's own message notifications.
- Keyboard input, and therefore anything typed while sharing, including
  passwords the sharer forgot they were about to type.
- Other apps, banking apps included, if the user switches to them.

`FLAG_SECURE` windows are excluded by the platform and render black. The app
cannot extend that protection to other apps' windows.

**What the product does about it:**

1. The consent dialog is Android's and says what it says. Do not try to soften
   it.
2. A one-time in-app explanation before the first ever share, in the register of
   §9.6.1 rather than a legal disclaimer: *"Everything on your screen will be
   visible to everyone in this call, including notifications."*
3. **Social suppresses its own notification content while it is the sharer.**
   For the duration of a share, Social's notifications collapse to a count with
   no sender and no body. This is cheap and it is the one case the app actually
   controls.

Point 3 does **not** extend to NexLink. Social cannot and must not suppress
another app's notifications — §16.8 is explicit that NexLink gains nothing from
Social's existence, and reaching across to mute it would be exactly the kind of
cross-app coupling D1 exists to prevent. The in-app explanation covers it
instead, honestly: *your other apps' notifications will also be visible.*

---

## 18.6 Media parameters

A screen share is a different kind of video from a camera feed — high
resolution, mostly static, with text that must stay legible and sudden full-frame
changes when a window moves.

| Parameter | Value | Why |
|---|---|---|
| Resolution | Cap at 1280×720, preserving aspect | A 1440p phone screen at native resolution is bandwidth this product cannot spend for a tile on someone else's phone. |
| Frame rate | 5–15 fps, adaptive | Text legibility beats motion smoothness for the common case. |
| Degradation preference | **Maintain resolution**, drop frame rate | The opposite of a camera feed. Blurry text is a failed share; a jerky one is a usable one. |
| Bitrate | ~600 kbps–1.5 Mbps, adaptive | |
| Simulcast | On | §17.8. A participant viewing a thumbnail should not pull the full stream. |

The degradation preference is the row that matters and the one most likely to be
wrong by default, because WebRTC's defaults are tuned for camera video. Set it
explicitly and verify it under constrained bandwidth.

### 18.6.1 Audio

Sharing **does not** capture device audio. `AudioPlaybackCapture` exists, and it
is deliberately not used:

- It captures other apps' audio output, which is a far larger consent surface
  than the screen.
- It is one small step from a call recorder, which is the thing D3 exists to
  keep the product away from.
- Nobody has asked for it.

The sharer's microphone continues as normal. If a user wants others to hear a
video they are sharing, they hold the phone up to it, and that is a fine
outcome.

---

## 18.7 Interaction with the rest of the call

| Event during a share | Behaviour |
|---|---|
| Camera was on | Stays on. Screen is an additional track, not a replacement. |
| Second participant starts sharing | Allowed. Both tracks published; UI shows the most recent as primary. Not worth blocking. |
| Sharer leaves the call | Share ends with the call. |
| Incoming cellular call | Share pauses with the rest of the call (§19.4), resumes on return. |
| App backgrounded | **Share continues.** This is the point of the feature. |
| Screen locks | Projection continues but captures the lock screen. Acceptable; the indicator persists. |
| Process death | Share is over. No resurrection (§18.3.1). |

---

## 18.8 Testing

| Test | Method |
|---|---|
| Consent required every session | Share, stop, share again. Second dialog must appear. |
| Service ordering | Force `getMediaProjection` to throw; confirm clean stop, no crash |
| Stop from system UI | Stop via the status bar chip; confirm app state updates |
| `FLAG_SECURE` exclusion | Share, open a banking app, confirm black |
| Own-notification suppression | Receive a Social message while sharing; confirm no content visible |
| Teardown on call end | End the call while sharing; confirm projection released and service stopped |
| Teardown on process death | Kill the process; confirm no orphaned projection, no leaked notification |
| Legibility | Share a text-heavy screen; confirm readable on a 5-inch receiver at the §18.6 caps |
| No file sink | CI grep for `MediaRecorder`, `MediaMuxer`, `ImageReader` in `:social-*` |
| OEM behaviour | Samsung plus one aggressive OEM (§15.9) |

The last two are the ones that will be skipped under time pressure and are the
two worth protecting. The grep is the §2.8 invariant made mechanical, and the
OEM row is where `mediaProjection` behaviour genuinely diverges.

---

## 18.9 Play review

`FOREGROUND_SERVICE_MEDIA_PROJECTION` requires a declaration and, in practice, a
demonstration video. The justification is short and should stay short:

> Screen sharing into an active, user-initiated video call. The user grants
> permission through the system dialog each time. Nothing is recorded or written
> to storage. Sharing ends with the call.

Two failure modes to avoid, both self-inflicted:

- **Declaring the type before the feature ships.** If phases 1–3 (§33) ship a
  messaging-only build, the manifest must not carry `mediaProjection`. §15.3.1
  says declare only what is used, and a declared-but-unused type is a question
  at review with no good answer.
- **Screenshots or a demo video that show the feature outside a call.** The
  justification says "in a call"; the evidence must agree with it.
