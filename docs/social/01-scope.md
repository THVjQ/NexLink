# §1 — Scope, goals and non-goals

> **Confidence:** `DURABLE`
> Product boundary. Changing this changes everything downstream.

---

## 1.1 One-sentence definition

NexLink Social is an invite-only, end-to-end encrypted messaging and calling
service, delivered as an Android app and a web client, operated on
infrastructure THVjQ runs, and surfaced inside NexLink's existing unified inbox
for users who opt in.

## 1.2 What the product does

The functional surface, stated as user-visible capability rather than
implementation:

**Messaging**
- One-to-one and group conversations, end-to-end encrypted by default with no
  option to disable encryption.
- Text, images, video, audio clips, arbitrary file attachments.
- Reactions using any emoji the user's keyboard can produce.
- Read state and typing indicators.
- Message editing and deletion, propagated to all participants.

**Calling**
- One-to-one voice and video calls.
- Group voice and video calls.
- Microphone mute and camera-off controls, available at all times during a call.
- Screen sharing into an active call.

**Identity and access**
- A chosen username, unique across the service.
- Account creation gated behind an invite code and an explicit acceptance step.
- Two account recovery models: email-backed, or recovery-key-only for users who
  decline to provide an email address.
- Sign-in on additional devices, with existing conversations remaining
  accessible.
- A web client for use from a desktop computer.

**Data handling**
- Message history stored on the user's own device as the primary copy.
- An explicit device-to-device transfer path for moving to a new phone.
- Account deletion available in-app and from a public web page.

## 1.3 What the product explicitly does not do

Non-goals are as load-bearing as goals. Each of these has been considered and
rejected, and re-opening any of them is a scope change requiring a decision, not
an implementation detail.

| Non-goal | Why |
|---|---|
| **Recording calls to a file** | Consent law varies by jurisdiction and the feature attracts disproportionate Play review scrutiny. Screen *sharing* is in scope; capture-to-storage is not. See §17.1. |
| **Federation with the public Matrix network** | Multiplies resource consumption, abuse surface and moderation burden for a benefit this product's users will not ask for. See §21.3. |
| **Open public registration** | Invite-only is a deliberate cost, abuse and moderation control. See §9.1. |
| **SMS or MMS** | NexLink already does this, better, as the default SMS handler. Social is a separate transport and must not blur into it. |
| **Bridging to third-party networks** (WhatsApp, Signal, Telegram) | Every bridge is a permanent maintenance liability, a terms-of-service violation risk, and an encryption weak point. |
| **Server-side message search** | Incompatible with the encryption posture. Search is client-side over the local store. |
| **Message backup that the operator can read** | Any backup is client-encrypted. The server holds ciphertext only. See §7.4. |
| **Voice or video message transcription** | Requires either plaintext server access or a large on-device model. Neither is justified at this stage. |
| **Public profiles, discovery, or a social graph** | This is a private messenger, not a social network in the feed sense. There is no directory to browse. |
| **Payments, or anything financial** | Attracts an entirely separate regulatory regime. |

## 1.4 Why a separate app rather than a NexLink feature

This is the single most consequential structural decision in the document, so
the reasoning is recorded in full.

### 1.4.1 What NexLink already is

As of versionCode 35, the NexLink package holds four of the most heavily
reviewed capabilities available on Android, simultaneously:

| Capability | Manifest evidence | Review posture |
|---|---|---|
| Default SMS handler | `READ_SMS`, `WRITE_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Requires a Play declaration and justification; restricted permission family. |
| Default dialer | `NexLinkInCallService`, `MANAGE_OWN_CALLS`, `ANSWER_PHONE_CALLS`, `READ_CALL_LOG` | Requires a Play declaration; Call Log permission group is separately restricted. |
| Notification aggregation | `NexLinkNotificationListener` (`NotificationListenerService`) | Sensitive; policy explicitly limits acceptable uses. |
| Accessibility | `NexLinkAccessibilityService` | The single most scrutinised service type on the platform. |

Each of these already carries its own justification burden at review. The
package is, from Play's perspective, already operating at the edge of what a
single app is normally permitted to do.

### 1.4.2 What a social layer would add

Hosted user accounts. User-generated content. Encrypted video calling.
Screen capture. Media hosted on operator infrastructure. Each brings its own
policy surface — UGC moderation requirements, account deletion requirements,
data safety disclosures, and the foreground service types
`camera`, `microphone` and `mediaProjection`.

### 1.4.3 The three consequences

**Enforcement attaches to the package.** Play suspensions, warnings and forced
removals apply at the application ID. A moderation failure or a policy
disagreement about the social feature does not remove the social feature — it
removes the app that the user relies on as their phone dialer and SMS client.
The blast radius is the entire product.

**Release trains fuse.** Every social-feature update re-enters review alongside
the messaging core. This is not hypothetical: at the time of writing, a NexLink
build containing a foreground-service crash fix is held in review, and no SMS
fix can ship until that clears. Adding a second, more contentious feature set to
the same review queue compounds this permanently.

**Download size is paid by everyone.** The WebRTC native libraries, the Matrix
SDK and its local store will add substantially to the bundle — a reasonable
expectation is that the current 7.6 MB roughly triples. Users who never enable
the social feature pay that cost on every update.

### 1.4.4 The counter-argument, and why it does not hold

The obvious objection is that NexLink's product thesis *is* the unified inbox —
one place for SMS, calls and social app messages — and that splitting the social
product into a second app contradicts it.

It does not, for a specific and slightly fortunate reason: **NexLink already
aggregates other applications' notifications.** `NexLinkNotificationListener`
surfaces social app notifications into the inbox and deep-links back to the
source app. A companion app is, from NexLink's perspective, just another social
app — it appears in the unified inbox for **three lines of change** in `:app`,
none of which add a permission or any background work.

> **Measured 2026-09-12 — the original claim here was wrong.** This paragraph
> used to read "with no integration code written at all". That is false, and it
> was false in a way that only building it revealed. NexLink's listener does not
> aggregate *every* app's notifications; it filters against an allowlist, and it
> reads two specific notification extras. Verifying Level 0 (§16.1) meant opening
> three gates, described in §16.2.3. Three lines is still a strong argument for
> D1 — it is not the zero the first draft asserted, and a claim of zero would
> have been found false by whoever implemented it.

That is the floor, not the ceiling. Because both apps ship from the same
repository under the same signing key, a `signature`-level custom permission
allows a direct bound-service link between them (§16), giving the inbox real
conversation objects — threads, unread counts, inline reply, avatars — instead
of scraped notification text. The unified inbox gets *better*, not worse.

### 1.4.5 What separation costs

Recorded honestly, so the decision can be revisited on real information:

- Two Play listings, two store presences, two review queues to monitor.
- A user-visible install step: enabling the feature in NexLink requires
  installing a second app.
- Cross-app state to keep coherent (enabled/disabled, account linkage).
- A slower Gradle build if both live in one repository.
- Duplicate crash reporting, analytics and release engineering setup.

The install step is the only one the user experiences, and it is a single
Play deep-link from the settings row. This is judged an acceptable price for
isolating the regulatory risk of the operator's core product.

## 1.5 Success criteria

The project is judged against these, not against feature completeness:

1. **A NexLink user who never enables Social is unaffected** — no size penalty
   beyond a settings row, no new permissions, no new background work, no change
   in review posture.
2. **Encryption is not optional and not downgradeable.** There is no code path
   that sends message content the server can read.
3. **An account survives losing the phone**, by whichever recovery path the user
   chose at signup, and the consequences of the no-email path were made
   unmistakably clear before it was chosen.
4. **A group video call with four participants and one screen share is usable**
   on a mid-range Android phone over domestic broadband.
5. **Operating cost per active user is known** and does not scale
   super-linearly with user count.
6. **A single operator can run the service** — every routine operation has a
   runbook (§29), and no recovery procedure requires knowledge held only in
   someone's head.

## 1.6 Explicitly deferred

Not non-goals — things intended eventually, deliberately excluded from the scope
this document plans:

- Voice messages with waveform scrubbing.
- Disappearing messages.
- Multi-account support in one app install.
- iOS client.
- Desktop native clients (the web client covers desktop).
- Wear OS support for Social (NexLink's `:wear` module covers SMS only).
- Location sharing.
- Threaded replies (flat replies are in scope; threads are not).
