# §15 — Foreground services and background execution

> **Confidence:** `DURABLE`
> Platform behaviour, learned the expensive way in this codebase already.

---

## 15.1 Why this chapter is short and firm

NexLink has already shipped a foreground-service crash to production. The
Computer Bridge's polling service could return from `onCreate` via `stopSelf()`
without ever calling `startForeground()`, and the platform responded by killing
the process — repeatedly, because a watchdog kept restarting it.

That fix is in the codebase at versionCode 35. **The discipline it produced is
reusable and this chapter codifies it before the same mistake is made again in a
new app with four more service types.**

---

## 15.2 The contract

Once `startForegroundService()` is called, the platform requires a matching
`startForeground()` within approximately five seconds, on **every** path out of
the service. A service that stops, throws, or returns early without having
promoted itself crashes the entire application with
`ForegroundServiceDidNotStartInTimeException`.

The rule that follows is absolute:

> **Promote first, decide afterwards. Never a bare `stopSelf()`.**

### 15.2.1 The pattern

Established in `BridgePollingService` and to be reused verbatim:

```kotlin
@Volatile private var isForeground = false

override fun onCreate() {
    super.onCreate()
    promoteToForeground()          // before any decision that could stop us
}

override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
    promoteToForeground()          // a sticky redelivery re-enters here
    if (!shouldRun()) { stopCleanly(); return START_NOT_STICKY }
    ...
}

private fun promoteToForeground() {
    if (isForeground) return
    val n = buildNotification()
    isForeground =
        tryStartForeground(n, PRIMARY_TYPE) ||
        tryStartForeground(n, FALLBACK_TYPE) ||
        tryStartForeground(n, TYPE_NONE)
}

private fun stopCleanly() {
    promoteToForeground()          // looks pointless; it is the crash case
    if (isForeground) stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
}
```

The type fallback chain matters: an OEM refusing a typed start throws out of
`startForeground`, and letting that escape crashes the app. Falling back to a
less-restricted type, then to untyped, keeps the contract satisfied.

> **Measured 2026-09-12 — the last rung is not what it looks like.**
>
> "Falling back to untyped" reads as an unconditional escape. It is not.
> `startForeground(id, n)` on a service whose **manifest** declares a
> `foregroundServiceType` still enforces that type's requirements, so the
> untyped call is untyped only in the source.
>
> And no rung of the chain can substitute for a missing **runtime** permission.
> On Android 14+ a `microphone` start throws unless `RECORD_AUDIO` is *granted*
> — declaring `FOREGROUND_SERVICE_MICROPHONE` is not granting `RECORD_AUDIO`.
>
> On an SM-G990E (Android 16) with the permission absent, `ActivityManager`
> logged `Background started FGS: Allowed` and then **no promotion followed**:
> all three rungs threw, `promoteToForeground` returned false silently, and the
> service sat owing the platform a `startForeground` — the exact death this
> section exists to prevent, with the fallback chain present and doing nothing.
> It was caught by the on-device test in §15.9, and could not have been caught
> any other way; the build was green throughout.
>
> `CallService` now picks its type from what is actually granted, and the chain
> handles OEM refusal — the thing it was written for — instead of pretending to
> handle a permission problem it cannot. **A silent `false` from a fallback
> chain is worse than a throw**, because the process dies seconds later
> somewhere else entirely.

### 15.2.2 The reviewable invariant

Extending §2.8, checkable mechanically:

- Every `stopSelf()` in a foreground service is inside `stopCleanly()`.
- Every fallback type used is declared in the manifest, or the fallback is
  illegal and throws.
- No path from `onCreate` or `onStartCommand` reaches a return without a
  promotion attempt.

---

## 15.3 Which services this app actually needs

Deliberately few. §13.6.2 establishes that **messaging needs no foreground
service at all** — push wakes the app, a short task syncs, the process exits.

| Service | Type | When |
|---|---|---|
| **Call service** | `microphone` + `camera` | For the duration of a call only |
| **Screen share** | `mediaProjection` | While sharing, within a call |
| **Media upload** | `dataSync` | Only for large uploads that must survive backgrounding |

**No persistent messaging service.** The temptation to add one "for reliability"
should be resisted: it costs battery, invites a Play policy question, and buys
nothing that high-priority FCM does not already provide.

### 15.3.1 Manifest declarations

Each type requires a permission and a declaration:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>

<service
    android:name=".calls.CallService"
    android:exported="false"
    android:foregroundServiceType="microphone|camera"/>

<service
    android:name=".calls.ScreenShareService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection"/>
```

Each declared type must also be justified in Play Console (§4.2.1), often with a
demo video. **Declaring a type "just in case" is a review liability** — declare
only what is used.

---

## 15.4 Type-specific constraints

### 15.4.1 `microphone` and `camera`

Started only in response to a user action — answering or placing a call. Android
restricts starting these from the background, and correctly so.

The incoming-call case is the awkward one: a call arrives by push while the app
is in the background, and the service cannot simply start. The resolution is that
the **notification comes first** — a full-screen-intent call notification, which
NexLink already has the `USE_FULL_SCREEN_INTENT` permission for — and the
service starts when the user answers. Not before.

### 15.4.2 `mediaProjection`

- Requires a fresh user consent dialog **per session**. There is no persistent
  grant, and attempting to cache one is both impossible and a red flag at review.
- The service must be running before projection starts.
- Projection stops when the call ends. No background continuation, ever — this
  is one of the D3 constraints (§2.4) and is what keeps sharing distinct from
  recording.

### 15.4.3 `dataSync`

Time-capped on Android 14+ — roughly six hours per day, aggregated. Fine for
uploads, unusable for anything persistent, which is another reason the messaging
path does not use a service.

---

## 15.5 Background start restrictions

From Android 12, an app in the background generally cannot start a foreground
service. Exemptions exist — a user interaction, a high-priority FCM message, a
full-screen-intent notification — and the battery-optimisation exemption is
**not** among them.

That last point was learned expensively in the bridge: the watchdog was designed
to restart a killed service and largely could not, because a WorkManager job is a
background context. The fix was to have the worker do the work itself rather than
delegate.

**Applied here:** any background task must be able to complete its work without
a foreground service. If it cannot, it is designed wrongly.

---

## 15.6 Calls and background start

An incoming call is the one case that genuinely needs to interrupt.

```
high-priority FCM → app wakes
                  → post full-screen-intent call notification
                  → user answers
                  → NOW start the foreground service with microphone|camera
```

The notification, not the service, is what reaches the user. This ordering is
both what the platform permits and what the platform intends.

Requirements:
- High-priority push for calls, always (§13.6.3).
- `USE_FULL_SCREEN_INTENT` — already held by NexLink; `:social` declares its own.
  Play scrutinises this permission; the justification is incoming calls, which is
  its intended use.
- A ringing timeout, after which the notification becomes a missed call.
- Correct behaviour when the device is locked, in Do Not Disturb, and during an
  existing cellular call (§19).

### 15.6.1 Built and measured, 2026-09-14

Works end to end on the handset: a killed process was woken by push, rang, and
answering put the user in the call.

**The part that is not obvious: nothing in the push says "call".**

MatrixRTC call membership is a *state* event, and state events do not generate
pushes. The ring is a separate message-like event the **caller's** client
sends. Captured off the widget bridge:

```json
{"type":"org.matrix.msc4075.rtc.notification",
 "content":{"notification_type":"ring",
            "m.mentions":{"user_ids":[],"room":true},
            "lifetime":90000,
            "m.relates_to":{"rel_type":"m.reference","event_id":"$…"}}}
```

In an encrypted room that travels as `m.room.encrypted` like everything else.
So **the homeserver cannot tell a call from a message** — which is §2.8 #1
working as intended — and neither can the `EVENT_ID_ONLY` payload (§13.3.1).
The classification happens on the device, after decryption, in
`RustSocialSession.incomingCall`. Anything else would mean telling the server
which of your events are calls.

The caller's client only sends that event if it is asked to:
`sendNotificationType = RING` in the widget config. With it null — the value
this project shipped first — everything else about a call works and **the
callee's phone never rings**, which is a silent failure of exactly the kind
§17.6.4.1 collects.

**The ringing timeout is the caller's.** §15.6 asks for one; `lifetime` is
already on the wire, so `setTimeoutAfter` is set from it rather than inventing
a second deadline that could disagree about when the caller gave up. Measured:
90 seconds later the ring is gone from the lock screen without the app running.

#### Testing this needs `am kill`, not `am force-stop`

A force-stopped package receives no FCM at all — Android's stopped-package
rule. The first attempt used `force-stop` and the push simply never arrived,
which looks exactly like a broken push chain. `adb shell am kill <pkg>` kills
the process without the stopped flag, which is what "swiped away" actually
means.

#### What is verified, and what is not

| | |
|---|---|
| Cold process woken by push, ring posted | **Verified** |
| `CallStyle` heads-up with Answer / Decline, no MXID on it | **Verified** — it names the conversation |
| Answer joins the call | **Verified** — `CallActivity`, service promoted |
| Ring expires on the caller's `lifetime` | **Verified** — gone from the lock screen at 90 s |
| Ring dismissed the moment it is answered | **Verified** 2026-09-15, two handsets |
| Answering from the notification joins the call | **Verified** — both sides then subscribed to the other's video with `encrypted=true` |
| Full-screen takeover on a **locked** screen | **Still not observed** — see below |

### 15.6.2 Two handsets, and three things that made the ring look broken

The cross-device run took a long evening, and none of the three causes was in
the ring code. All three are worth knowing before anyone repeats it.

**1. The caller's stale membership (§17.7.3).** A ghost makes the next call a
*rejoin*, and a rejoin sends no ring. Fixed in the client.

**2. A ghost belonging to the *other* party does it too.** If anyone at all is
already "in" the call — including a device that no longer exists — Element Call
treats a new participant as joining, and joining rings nobody. The client fix
only withdraws **its own** membership, so this half is still open: two accounts
whose apps both crashed mid-call will not ring each other until the delayed
leaves fire.

**3. A stale FCM token, which had killed push outright.** §13.3.6.

And two harness lessons, because they cost more than the bugs did:

- **The Answer button is in the device's language.** `CallStyle`'s actions are
  rendered by the platform, so on a German handset they read *Annehmen* and
  *Ablehnen*. Every automated answer missed, and the symptom was identical to a
  ring that never arrived.
- **A heads-up collapses faster than `uiautomator dump` returns.** Polling the
  view hierarchy for the ring loses the race every time. The notification
  *record* lives for the ring's full lifetime, so poll `dumpsys notification`
  and then open the shade.

`tools/lib/adb-ui.sh` now has `wait_for_ring` and `answer_ring` doing both.

### 15.6.3 Why a sleeping phone does not ring — and it is architectural

The locked-screen row did not just fail; it now has an explanation, and the
explanation is a consequence of the product's central promise rather than a bug
in the calling code.

**What was measured, and what follows from it — kept apart on purpose.**

The device-side observation is the weaker half and is reported as such. Across
several attempts with the callee locked, FCM accepted the pushes
(`sygnal_gcm_status_codes_total{code="200"}` rose by four) and no ring was ever
posted. **But the setup for those runs lost the handset** — its log, read
afterwards, shows `adb: device not found` partway through, so neither the
`am kill` nor the stay-awake release reached it and its state is unknown.

The specific alternative is not exotic: if the `am force-stop` *did* land and
the `am kill` did not, B spent those runs in the **stopped** state, which
receives no FCM at all — the trap §15.6.1 records in its own testing note. A
device that is not being delivered to cannot demonstrate anything about Doze.

So the device half is set aside entirely rather than argued over.

The server-side evidence needs no device at all, and it is what the conclusion
rests on. Asking Synapse what it thought it was sending:

```
m.room.encrypted  @call190359:…  sound_tweak=False  -> fcm prio=NORMAL
```

every time. And a **normal-priority FCM message is deferred while the device is
in Doze**. That is the entire failure.

**The chain, and why each link is load-bearing:**

1. §2.8 #1 — the homeserver cannot read the event. That is the product.
2. So it cannot tell a **call** from a **message**: the ring travels as
   `m.room.encrypted` like everything else (§13.3.5).
3. Push priority is decided from the matching push rule. Checked against the
   live rule set: `.m.rule.encrypted_room_one_to_one` carries a `sound` tweak,
   **`.m.rule.encrypted` does not**. The test room has three members, so the
   second one applies.
4. No sound tweak → Matrix `prio: low` → Sygnal sends FCM `normal`
   (`gcmpushkin.py`: `"normal" if n.prio == "low" else "high"`, which also
   **overrides** the `fcm_options.android.priority: high` in our config).
5. Normal priority in Doze → delivered when the device next wakes. Which, for a
   call, is the same as not delivered.

Steps 1–4 are measured against this deployment. Step 5 is Android's documented
behaviour rather than something observed here, and **a clean locked-device run
is still owed** — with the handset verifiably connected, killed and asleep
throughout — before the row is called closed either way.

**§15.6 says "High-priority push for calls, always (§13.6.3)". The deployment
does not achieve that, and cannot without a decision.** Three options, none free:

1. **Give encrypted events a sound tweak in every room.** One push rule, set by
   the client at sign-in. Every message then becomes a high-priority wake —
   which is precisely the battery cost §13's design exists to avoid, paid on
   every message to make the rare call work.
2. **Accept that group-room calls do not wake a sleeping phone.** One-to-one
   calls would still ring, because `.m.rule.encrypted_room_one_to_one` already
   carries the tweak — which means the product would ring reliably for the case
   it is mostly for, and silently fail for group calls. That asymmetry has to
   be *stated*, not discovered.
3. **Let the server see that an event is a call.** Sending the ring unencrypted,
   or with a readable type, would fix priority at the cost of telling the
   homeserver who is calling whom and when. That is metadata §1.3 spends the
   whole design avoiding.

**(2) is the honest default and (1) is the one to consider**, because a
messenger whose calls do not ring is not a calling product. This belongs to the
operator, not to the code.

**Still literally unobserved**, and worth separating from the above: the
full-screen takeover of a locked screen. Even with priority fixed, nobody has
yet *seen* the ring paint over a keyguard. The obstacle is mundane — the handset
re-locks with a pattern and `monkey` cannot launch past the keyguard, so making
the app push-eligible while locked silently does nothing. It needs a device with
no lock set, or a person to unlock it and hold it awake
(`adb shell svc power stayon true`) while the other phone calls.

---

## 15.7 Boot and process death

| Event | Behaviour |
|---|---|
| Boot completed | Re-register for push. Start no service. |
| Process killed | Nothing to restore for messaging — push will wake it again. |
| Process killed **during a call** | The call is lost. Attempt to signal the peer; do not try to resurrect. |
| App updated | Re-register push; sessions and keys survive. |

**No `START_STICKY` messaging service to be resurrected**, because there is no
messaging service. This is the simplification §13.6.2 buys.

---

## 15.8 Battery optimisation

NexLink requests exemption for the bridge, because polling genuinely needs it.

**Social should not request it.** Push-driven delivery works within the standard
battery model, and asking for an exemption the app does not need is both a
user-trust cost and a Play question. If delivery proves unreliable without it,
that is evidence of a design problem in §13, not a reason to ask.

---

## 15.9 Testing

Foreground service behaviour cannot be verified in an emulator alone. Required
before any release:

| Test | Method |
|---|---|
| Promotion on every path | Force each early-return branch; confirm no crash |
| Type fallback | Simulate a refused primary type |
| Doze delivery | `adb shell dumpsys deviceidle force-idle` |
| Background call start | Push a call with the app swiped away |
| Screen share consent | Verify re-consent per session |
| OEM behaviour | Test on Samsung and at least one aggressive OEM (Xiaomi, Huawei) |

The OEM row is not optional. The bridge's original failure was invisible on
stock Android and obvious on real devices.
