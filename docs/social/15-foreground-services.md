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
