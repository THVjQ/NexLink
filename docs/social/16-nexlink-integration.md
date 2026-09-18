# §16 — NexLink integration and the unified inbox

> **Confidence:** `REVISABLE`
> The mechanism is durable. The data contract will evolve with the UI.

---

## 16.1 What is being preserved

NexLink's product thesis is the unified inbox: SMS, calls and social app
messages in one place. D1 (§2.2) splits Social into its own package, and this
chapter is the reason that split does not damage the thesis.

Two integration levels, and the first is free.

---

## 16.2 Level 0 — notifications, three lines

`NexLinkNotificationListener` already aggregates other apps' notifications into
the inbox and deep-links back to the source app. **NexLink Social is, to that
listener, almost just another social app** — close enough that three lines of
change make it one.

This is the floor, and it matters more than it sounds: it means the integration
is never blocked on the integration. Social can ship, be used, and appear in the
inbox before a single line of §16.3 exists.

### 16.2.1 What was actually measured

Verified on hardware (Samsung SM-G990E / Galaxy S21 FE, Android 16, API 36,
NexLink 2.5.7 installed over
the user's live 2.5.3 with the same signing key so real data was preserved) on
**2026-09-12**. `Level0ProbeTest` posts a Social notification and holds it for 90
seconds; NexLink's inbox was then read on-screen.

**Result: the probe appears in NexLink's Inbox tab**, labelled "NexLink Social",
with an unread badge, timestamp, and a working open-in-app affordance. NexLink
re-posted it on its own `nexlink_social` channel with `category=msg` — i.e. the
full existing path ran, not a partial one.

### 16.2.2 The claim it falsified

§1.4.4 originally asserted the companion app "appears in the unified inbox on day
one, with no integration code written at all". **That is false.** The first
attempt produced nothing at all: the notification was live, the listener was
bound, the process was running, and the inbox was empty and silent about why.
There is no log line for a notification the listener drops.

### 16.2.3 The three gates

Each had to be found by reading `NexLinkNotificationListener.onNotificationPosted`
line by line, because every one of them fails by `return` with no diagnostic.

| # | Gate | Where | Fix |
|---|---|---|---|
| 1 | `if (pkg !in NotificationStore.watchedPackages) return` — an **allowlist**, derived from `PLATFORM_MAP` | `:app` `db/NotificationStore.kt` | two map entries: `com.thvjq.nexlink.social` and `.debug`, both → `"NexLink Social"` |
| 2 | `if (!NotificationPrefs.isPlatformEnabled(ctx, n.platform)) return` — defaults to **off** for an unknown platform | `:app` `db/NotificationPrefs.kt` | add `"NexLink Social"` to the default-enabled set |
| 3 | `extras.getCharSequence("android.title") ?: return` and the same for `android.text` | `:social` `Notifications.kt` | `MessagingStyle` does **not** populate those extras; call `setContentTitle`/`setContentText` alongside it |

Gate 3 is the interesting one and is not NexLink-specific: **any**
`NotificationListenerService` reading `android.title`/`android.text` sees nothing
from a pure `MessagingStyle` notification. Android Auto, Wear, and third-party
notification mirrors all take that path. Setting both explicitly costs nothing —
`MessagingStyle` still wins for on-device rendering — and it is the correct thing
to do regardless of §16.

### 16.2.4 What this costs D1

Three lines, of which two are in `:app`. `tools/check-invariants.sh` confirms
**"NexLink's permission set unchanged (§2.8 #6)"** still passes: no permission
added, no background work added, no dependency from `:app` onto any social
module. D1's argument survives intact — it was simply overstated.

One honest gap: the Inbox's platform cards (Signal, Telegram, WhatsApp,
Messenger) are a hardcoded 2×2 grid, so there is **no mute card for NexLink
Social**. Messages still list normally — Discord, Instagram and Steam are in
exactly the same position in the existing app — but muting it from NexLink's UI
would need a layout change. That was left undone deliberately: it is `:app` UI
work in service of the companion app, which is the cost D1 exists to avoid.

**Limitations of level 0:**

| Limitation | Consequence |
|---|---|
| Notification text only | No history, only the latest message |
| No unread counts | Beyond what the notification carries |
| Reply via notification action | Works, but constrained to what the action allows |
| Nothing when notifications are muted | A muted conversation is invisible to the inbox |
| Content is scraped, not structured | Fragile to notification format changes |
| No mute card in the Inbox grid | §16.2.4 — hardcoded 2×2 layout |

---

## 16.3 Level 1 — the direct link

Both apps ship from one repository under one signing key (§10.5.1), which makes
a private, authenticated IPC channel available.

### 16.3.1 Signature-level permission

```xml
<!-- declared by :social -->
<permission
    android:name="com.thvjq.nexlink.permission.SOCIAL_BRIDGE"
    android:protectionLevel="signature"/>

<service
    android:name=".ipc.SocialBridgeService"
    android:exported="true"
    android:permission="com.thvjq.nexlink.permission.SOCIAL_BRIDGE"/>
```

```xml
<!-- declared by :app -->
<uses-permission android:name="com.thvjq.nexlink.permission.SOCIAL_BRIDGE"/>
```

`signature` protection means **only an app signed with the same key can bind.**
No other application on the device can obtain the permission, regardless of what
it declares. This is the strongest IPC guarantee Android offers short of running
in the same process, and it is available here only because both apps share a
keystore.

### 16.3.2 Defence in depth

The permission is the control, but a bound service should not rely on a single
check:

1. **Verify the caller's package name** via `Binder.getCallingUid()` and
   `PackageManager`.
2. **Verify the caller's signing certificate** matches this app's own.
3. **Reject anything else**, and log it — an unexpected bind attempt is a
   security event worth knowing about.

Checks 1 and 2 defend against the case where the permission model is subverted
by a platform bug or an OEM modification. Cheap, and there is no reason not to.

---

## 16.4 The data contract

Lives in `:social-contract` (§10.3.1) — the only social module `:app` may see.

### 16.4.1 Types

```kotlin
@Parcelize
data class SocialConversation(
    val id: String,                 // opaque; never the raw room ID
    val title: String,
    val avatarUri: String?,         // content:// via the social provider
    val lastMessage: String?,       // preview text, already decrypted
    val lastMessageAt: Long,
    val unreadCount: Int,
    val isGroup: Boolean,
    val isMuted: Boolean
) : Parcelable
```

**The conversation ID is opaque.** NexLink receives an identifier it can hand
back, not a Matrix room ID. This keeps protocol details out of `:app` entirely
and means a protocol change does not reach across the boundary.

### 16.4.2 Interface

```kotlin
interface ISocialBridge {
    fun getContractVersion(): Int   // NOT getInterfaceVersion — see below
    fun getAccountState(): AccountState          // signed out / signed in / locked
    fun getConversations(limit: Int): List<SocialConversation>
    fun getUnreadTotal(): Int
    fun openConversation(id: String): PendingIntent
    fun sendReply(id: String, body: String): ReplyResult
    fun markRead(id: String)
    fun subscribe(callback: ISocialBridgeCallback)
}
```

`openConversation` returns a `PendingIntent` rather than launching directly, so
NexLink controls when and how it is fired — matching how the existing listener
handles social app deep links.

**`getInterfaceVersion()` cannot be used as a method name.** Found while building
phase 0: `aidl` reserves it for its own stable-interface versioning and fails the
build with *"method getInterfaceVersion() is reserved for internal use"*. The
method is `getContractVersion()` in `:social-contract`. Noted because the name
appears obvious and will be reached for again.

### 16.4.3 What crosses the boundary

| Crosses | Never crosses |
|---|---|
| Conversation titles and previews | Full message history |
| Unread counts | Any key material |
| Avatars, as content URIs | Raw Matrix identifiers |
| Reply text, outbound | Anything about devices or verification |

**Preview text is decrypted plaintext crossing a process boundary.** That is
acceptable — it is the same data the notification already carries at level 0,
travelling over a signature-protected channel rather than through the
notification system. But it is a real disclosure and should be bounded: previews
are truncated, and a user setting to suppress previews in the unified inbox must
be honoured on the Social side, not filtered afterwards by NexLink.

---

## 16.5 Lifecycle

### 16.5.1 Enabling

Mirrors the Computer Bridge exactly, because that pattern works and NexLink
users have already learned it:

```
Settings → NexLink Social  [ off ]
    ├─ companion not installed → "Get NexLink Social" → Play deep link
    └─ installed              → disclaimer → bind → on
```

Off by default. A NexLink user who never touches this row is unaffected in every
respect (§1.5).

### 16.5.2 States

| State | Inbox behaviour |
|---|---|
| Not installed | Row offers install. No social content. |
| Installed, toggle off | No binding attempted. Level 0 notifications still appear if the user has them on. |
| Installed, toggle on, signed out | Row shows "sign in to NexLink Social". |
| Installed, toggle on, signed in | Full integration. |
| Uninstalled while on | Binding fails; toggle reverts; cached conversations cleared. |

**Cached social conversations are cleared when the link is broken.** Leaving
them would show stale previews of an app the user has removed, which is both
confusing and a small privacy failure.

### 16.5.3 Binding discipline

- Bind lazily, when the inbox is visible — not at app start.
- Unbind when the inbox is not visible for a period.
- Never hold the binding while NexLink is backgrounded; it keeps the Social
  process alive for no reason and shows up as battery use attributed to the
  wrong app.

---

## 16.6 Interface versioning

Two independently versioned apps (§10.5) means either can be older.

`getInterfaceVersion()` is called first, and both sides degrade rather than fail:

| Situation | Behaviour |
|---|---|
| NexLink newer than Social | Uses the subset Social supports; hides newer features silently |
| Social newer than NexLink | Serves the older interface; new capabilities dormant |
| Incompatible | Fall back to level 0. **Never crash, never show an error the user cannot act on.** |

The interface is **append-only**: new methods may be added, existing signatures
never change. A breaking change means a new interface version served alongside
the old, not a modified one.

---

## 16.7 Avoiding double presentation

With both levels active, a message could appear twice — once from the
notification listener, once from the bridge.

Resolution: **when the bridge is connected, NexLink suppresses level-0
notifications from the Social package** in the inbox, exactly as it already
self-suppresses its own re-posted notifications. The listener's existing
`selfSuppressed` mechanism is the model.

The system notification itself still appears — Social posts its own
notifications and should continue to. Only the *inbox row* is deduplicated.

---

## 16.8 What NexLink must not gain

Restating the boundary, because integration is where it would erode:

- No new permissions in `:app` beyond the custom signature permission.
- No new background work — the binding is foreground-only (§16.5.3).
- No dependency on any social module except `:social-contract`.
- No measurable size increase; `:social-contract` is Parcelable definitions.
- No new foreground services.

If an integration feature requires violating one of these, it belongs on the
Social side of the boundary instead.

---

## 16.9 Testing

| Test | Why |
|---|---|
| Bind from an app signed with a different key | Must be refused |
| Social not installed | Row degrades correctly |
| Social uninstalled while bound | No crash; state clears |
| Version mismatch, both directions | Degradation, not failure |
| Reply from the inbox | Message actually sends and appears in Social |
| Preview suppression setting | Honoured end to end |
| `:app` size unchanged | CI assertion (§10.4) |

---

## 16.10 Open questions

- Should NexLink be able to *start* a Social call from the inbox? Attractive,
  and it drags call UI into the wrong app. **Leaning no** — deep-link to Social.
- Should the unified inbox show Social conversations when Social is signed out
  but has cached data? **Leaning no** — signed out means gone.
- Is level 1 needed for the first Social release at all? **No.** Ship level 0,
  add the bridge once Social is stable. This ordering is reflected in §33.

---

## 16.2.2 The notification that would not go away

Reported 2026-09-18: *"the notifications are not disappearing when the chat is
opened even if you go around the notification to it — and I get a notification
when I send a message."* Two separate bugs, and the first is the interesting one
because **it is not in either app.**

### What was actually happening

NexLink's unified inbox does not merely *read* Social's notifications. On
seeing one it **cancels Social's** (so there is only one in the shade) and
**posts its own copy** with NexLink's icon and a tap that routes back into the
conversation. That is what makes one inbox out of two apps, and §16.2 verified
it works.

The consequence nobody had traced: by the time the user opens the conversation,
**Social's notification no longer exists** — NexLink cancelled it seconds after
it appeared. So `Notifications.dismiss` cancels nothing, and NexLink's copy
stays in the shade indefinitely. Opening the chat, reading everything, leaving
and coming back would not shift it.

This took four wrong theories to find, and the reason is worth recording: every
observation was made through `dumpsys notification`, which lists the same
notification more than once and keeps records after cancellation. **The only
reliable instrument was a screenshot of the shade.** The moment the app was
asked what it thought it had — `activeNotifications` — it answered *nothing*,
which was true, and which immediately identified the owner as some other
package.

### The fix

Social says so explicitly: `ACTION_CONVERSATION_READ`, carrying the
conversation title, sent only to NexLink's package. NexLink cancels any of its
own mirrors whose title matches, and marks the conversation read in its store.

Three constraints shaped it:

- **A broadcast, not the AIDL bridge (§16.3).** One-way, fire-and-forget, and it
  must work whether or not anything is bound.
- **No receiver permission.** `sendBroadcast(intent, permission)` requires the
  *receiver* to hold it, and NexLink must not: §2.8 #6 asserts its declared
  permission set never changes. Passing one meant the broadcast was silently
  never delivered — indistinguishable, from the sender's side, from success.
  `setPackage` still limits delivery to NexLink.
- **`:app` does not depend on `:social-contract`.** It is the one social module
  §10.3.1 permits it to see, but merging that module's manifest would add the
  bridge permission to NexLink and break the same invariant. The two strings
  are copied, with the source named on both sides.
- **Registered in `onCreate`, not `onListenerConnected`.** The latter fires when
  the system binds the listener, which does not happen again for a service that
  is already bound — so after an app update the receiver silently never
  registered. Found by sending the broadcast by hand with `am broadcast` and
  watching nothing happen.

### And the self-notification

Separate, and entirely Social's: nothing checked who sent the message.

- The in-app watcher notified on any **rising unread count**, which is not the
  same thing as a new message. After `markRead` the count drops to zero and a
  sync already in flight reports the old value, so it rises 0 → 1 with nothing
  behind it — re-posting the notification the user just cleared. It now
  notifies on the **message timestamp**, which only moves when somebody says
  something, and skips anything where `lastMessageIsMine`.
- The push path checked neither the sender nor whether the conversation was
  already open on screen. It now does both, and dismisses rather than posts.
- The watcher also treated its **first emission as news**, so every process
  start — including the ones push causes — re-notified every unread room. The
  first emission is now a baseline.

