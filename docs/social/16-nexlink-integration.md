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

## 16.2 Level 0 — notifications, no code

`NexLinkNotificationListener` already aggregates other apps' notifications into
the inbox and deep-links back to the source app. **NexLink Social is, to that
listener, just another social app.** It appears in the unified inbox from its
first install with no integration code written on either side.

This is the floor, and it matters more than it sounds: it means the integration
is never blocked on the integration. Social can ship, be used, and appear in the
inbox before a single line of §16.3 exists.

**Limitations of level 0:**

| Limitation | Consequence |
|---|---|
| Notification text only | No history, only the latest message |
| No unread counts | Beyond what the notification carries |
| Reply via notification action | Works, but constrained to what the action allows |
| Nothing when notifications are muted | A muted conversation is invisible to the inbox |
| Content is scraped, not structured | Fragile to notification format changes |

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
