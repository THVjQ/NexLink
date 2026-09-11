# §14 — Chat surface

> **Confidence:** `REVISABLE`
> Product surface. The requirements are fixed; the presentation is open.

---

## 14.1 Scope

The conversation screen and everything reachable from it: timeline, composer,
reactions, attachments, replies, edits, deletions, read state, and group
administration.

---

## 14.2 Timeline

### 14.2.1 Rendering

A messaging timeline is the most performance-sensitive surface in the app.
Requirements:

- Reverse-chronological, anchored to the newest message.
- Paginated backwards on scroll, from the local store first, network second.
- Stable item identity so a re-render does not cause visible jumps.
- Sending, sent, delivered and read states visible per message.
- Date separators, and an unread marker at the position the user left.

**The unread marker is worth specific attention.** Returning to a busy
conversation and being dropped at the bottom, with no indication of where you
stopped reading, is the single most common complaint about group chat clients.
Anchor to the unread position, not the newest message.

### 14.2.2 Message states

| State | Meaning | Presentation |
|---|---|---|
| Queued | Not yet sent — offline or waiting | Clock, muted |
| Sending | In flight | Clock |
| Sent | Server accepted | Single tick |
| Delivered | On the recipient's device | Double tick |
| Read | Recipient read it | Double tick, accent |
| Failed | Permanently failed | Warning, with retry |

This deliberately mirrors NexLink's existing SMS conventions — single tick on
send, upgraded on delivery — so that a user moving between the two surfaces in
the unified inbox reads the same vocabulary. Consistency across the two products
is worth more than any individual improvement here.

### 14.2.3 Decryption failures

Covered in §8.8; restated because it lands in this surface. A message that
cannot be decrypted shows an explanatory placeholder, retries automatically when
new keys arrive, and offers a "why?" affordance rather than a bare error.

---

## 14.3 Composer

- Multiline, growing to a ceiling then scrolling.
- Drafts persisted per conversation, surviving process death.
- Attachment picker: gallery, camera, file, with a size warning above a
  threshold.
- Emoji entry is the **system keyboard**. No custom emoji keyboard — see §14.4.
- Reply context shown inline above the input, dismissible.
- Send disabled on empty or whitespace-only input.

**No typing indicator sent while the composer is merely focused** — only on
actual input, debounced, and never pushed (§13.6.3).

---

## 14.4 Reactions

Directly from the brief: *"reactions like hearts etc — need to be able to use any
of Samsung keyboard emojis"*.

### 14.4.1 The rule

**Any emoji the user's keyboard can produce is a valid reaction.** There is no
curated set, no server-side allowlist, and no client restriction. The protocol
already permits this (§5.5); the work is not to limit it.

### 14.4.2 Entry

Two paths, both required:

1. **Quick reactions** — a small row of recent and common emoji on long-press,
   because most reactions are one of a handful and a picker for those is
   friction.
2. **Full picker** — opens the **system emoji keyboard**, so the user gets
   exactly the emoji their device provides, including Samsung's, including
   whatever their keyboard app adds.

Path 2 is the requirement. A bundled picker would ship a fixed emoji set that
diverges from the user's keyboard, which is precisely the outcome to avoid.

### 14.4.3 Grapheme handling

The implementation detail that breaks this feature if got wrong.

A single user-perceived emoji may be several code points — skin tone modifiers,
zero-width joiners for family and profession sequences, variation selectors for
presentation. Rules:

- **Iterate by grapheme cluster**, never by `char` or code point. Use
  `BreakIterator.getCharacterInstance()` or an ICU equivalent.
- **Never truncate** a reaction string. A substring operation can split a ZWJ
  sequence into two unrelated emoji.
- **Never normalise** — Unicode normalisation can alter these sequences.
- **Validate** that the input is a single grapheme cluster and is emoji-typed,
  rejecting arbitrary text. A reaction is an emoji, not a comment.
- **Render with `androidx.emoji2`**, so an emoji the sender's newer device
  supports still displays on an older recipient device rather than appearing as
  a box.

### 14.4.4 Display

- Aggregated under the message: the emoji, a count, and who reacted on tap.
- The current user's own reactions visually distinct and tappable to remove.
- A ceiling on distinct emoji shown inline, with overflow — a message with forty
  different reactions must not destroy the layout.
- Reactions are encrypted, so they arrive and render like any other event, with
  the same decryption-failure handling.

---

## 14.5 Attachments

### 14.5.1 Send path

```
pick → validate size → generate thumbnail → encrypt both
     → upload both → send event referencing them
```

Notes:
- Encryption happens **before** upload, client-side, with a one-time key carried
  in the encrypted event (§5.6).
- Thumbnails are generated and encrypted separately, because the server cannot
  generate them for encrypted media.
- Large uploads show progress and are cancellable.
- A failed upload leaves a retryable queued message (§13.5.1), never a partial
  send.

### 14.5.2 Receive path

Download on demand by default, honouring the auto-download setting (§12.5.2).
Decrypt to app-private storage only (§12.6.1). Never write to shared storage or
the gallery without an explicit user save action.

### 14.5.3 Types and limits

| Type | Inline preview | Limit |
|---|---|---|
| Image | Yes, with thumbnail | 100 MB |
| Video | Thumbnail, play in app | 100 MB |
| Audio | Inline player | 100 MB |
| Other files | Icon, name, size | 100 MB |

Limits are homeserver configuration (§22) and the client must read them rather
than hardcode, so raising the server limit does not require an app release.

**Voice messages are deferred** (§1.6) — recording UI, waveform, and playback
are a feature in their own right.

---

## 14.6 Replies, edits, deletions

| Action | Behaviour |
|---|---|
| **Reply** | Flat replies only. Quoted context shown above the message; tapping scrolls to the original. Threads are out of scope (§1.6). |
| **Edit** | Replaces content, marked "edited", with edit history available on demand. Only the sender may edit. |
| **Delete** | Redaction, propagated to all participants. Shows "message deleted" rather than vanishing silently — a disappearing message is more confusing than an acknowledged deletion. |

**Deletion honesty:** a deleted message is removed from other clients, but it
cannot be un-seen and a recipient may have screenshotted or copied it. The UI
must not imply retraction is guaranteed. Room admins may delete others' messages
subject to power levels (§14.8).

---

## 14.7 Read state and presence

- **Read receipts** per user, shown on the last message each has read. On by
  default; a global setting to disable, which also stops receiving others'.
  Reciprocity is the only defensible design here.
- **Typing indicators** debounced, expiring after a few seconds of inactivity,
  never pushed.
- **Online presence is not implemented.** It is a constant background traffic
  cost, a privacy leak, and a source of social pressure. Deliberately absent.

---

## 14.8 Groups

Group chats are rooms with more than two members (§5.2.2), so the machinery is
shared. The additional surface:

| Capability | Default | Governed by |
|---|---|---|
| Create group, set name and avatar | Creator | — |
| Invite members | Any member | Power level |
| Remove members | Admin only | Power level |
| Delete others' messages | Admin only | Power level |
| Change name/avatar | Admin only | Power level |
| Promote to admin | Admin only | Power level |
| Leave | Anyone | — |

**Membership changes rotate the Megolm session** (§5.3.2), so a removed member
cannot read subsequent messages. They retain what they already received —
unavoidable, and worth stating in the UI when removing someone.

Group size ceiling for the first release: **256 members.** Beyond that, key
distribution cost per message becomes significant and the moderation model needs
revisiting.

---

## 14.9 Search

Client-side only, over the local store (§1.3). Consequences to surface honestly:

- Searches only what is on this device. A newly signed-in device with restored
  history can search it; one without cannot.
- No server-side search means no cross-device search results.
- Performance is a local indexing problem, and an index over encrypted content
  is itself sensitive and belongs in the encrypted store (§12).

---

## 14.10 Accessibility

Not a late pass. Requirements:

- Every interactive element has a content description; reactions announce as
  "heart reaction, three people".
- Timeline is navigable by screen reader in a sensible order.
- Text scales with system font size without clipping — messages must reflow, not
  truncate.
- Contrast meets WCAG AA in both themes.
- No information conveyed by colour alone; message states carry a shape as well
  as a colour.
- Touch targets at least 48dp, including reaction chips, which are the most
  commonly undersized element in messaging apps.

---

## 14.11 Open questions

- Should quick reactions be user-configurable, or adaptive to recent use?
  **Leaning adaptive**, with a fixed fallback for a new user.
- Is 256 the right group ceiling? Arbitrary. Should be revisited against
  measured key-distribution cost.
- Should edit history be visible to everyone, or only indicate that an edit
  occurred? **Leaning visible** — hidden edit history in a messenger is a trust
  problem.
