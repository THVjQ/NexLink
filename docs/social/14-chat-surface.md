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

## 14.9.1 Implemented 2026-09-12

The Rust SDK provides a local search index (`ClientBuilder.withSearchIndexStore`
plus `Client.searchService()`), so §14.9 did not need a bespoke index over the
local store.

**Trap: give the index its own directory.** Passing `withSearchIndexStore` the
same paths as `sqliteStore` produced **no index and an empty room list**, with
nothing in logcat and no exception — the client built and synced, and simply
never delivered a room. It cost half an hour to isolate because the symptom
(an empty inbox) looks nothing like the cause (a search setting).

What the screen tells the user, and why it must: search covers **only what this
device has decrypted**. A message from before the device was added (§8.5) is
genuinely not findable, and a user who does not know that will reasonably
conclude search is broken. §1.3's server-side-search exclusion is not a
limitation being worked around — the server holds ciphertext and could not
search if asked — so saying so plainly is both honest and reassuring.

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

### 14.10.1 Audited 2026-09-12 — four failures, three of them invisible in dark mode

Audited on hardware (SM-G990E, Android 16) with two tools written for it, both
kept in `tools/`:

- **`check-contrast.py`** computes WCAG ratios from the palette. Contrast is the
  one requirement above that is fully decidable from source, so it should never
  have been a judgement made by eye. It is now part of
  `check-invariants.sh`.
- **`a11y-audit.py`** walks the live uiautomator tree for touch-target sizes and
  missing content descriptions. Neither is answerable from source: a `TextView`
  with 12sp text and no `minHeight` measures 20dp no matter how the code reads.

**1. The light theme failed AA on five colours.** The palette is mirrored from
NexLink's, which is iOS-like and assumes dark mode; the development phone runs
dark, so nobody had ever seen it.

| colour | was | now | used for |
|---|---|---|---|
| `social_muted` | **2.92** | 4.83 | nearly every explanatory line in the app |
| `social_accent` | **3.67** | 4.81 | Retry, Discard, the selected radio |
| `social_danger` | **3.51** | 4.81 | "Not sent" on a failed message |
| `social_cannot_see` | **3.92** | 4.81 | §9.6.1's left column |
| `social_can_see` | **4.19** | 4.81 | §9.6.1's right column |

Darkened in HSV so hue and saturation are unchanged. Targeted 4.8 rather than
4.5 for headroom. The last two are at an **identical** ratio on purpose: §9.6.1
requires the two columns to carry equal visual weight, and a contrast fix must
not quietly make one louder than the other.

`social_divider` is 1.53:1 and is **not** fixed. WCAG 1.4.11 covers what is
needed to identify a component or state; a hairline between rows that are
already separated by whitespace and bold labels identifies nothing. It stays
listed in the tool's output as `info` rather than deleted, so the claim can be
re-checked if a divider ever becomes load-bearing.

**2. Reaction chips were 44 × 27 dp.** Exactly the element this section names by
name. Padding does not reach 48dp at 14sp; the minimum has to be stated.

**3. Reactions announced as "❤️ 1".** Now: *"❤️ reaction, 1 person, including
you. Tap to remove."* The third clause is the one that is easy to omit and the
most useful — reacting is a toggle (§14.4), so whether you are already in the
list is what decides what tapping does. The emoji is passed through whole
(§14.4.3) and named by the platform's own TTS, which is better localised than
any table this app could carry.

**4. The composer collapsed at large font sizes.** At font scale 1.8 the three
buttons kept their minimum widths, the weight-1 input took what was left, and
the hint "Message" wrapped to "Mes / sage" in a field too narrow to type in. A
layout weight cannot help once the siblings' minimums exceed the row. Above 1.3
the composer now stacks: input full-width on its own line, buttons beneath.

> Capping the buttons' text size would have been the one-line fix, and it is
> precisely the wrong one — it repairs the layout by undoing the accessibility
> setting that exposed the problem.

**Also fixed:** `☰` and `+` had no content descriptions ("People in this
conversation", "Attach a photo or file"). A screen reader reads a glyph as
punctuation or skips it, leaving the control unidentifiable.

**Also found, and not an accessibility bug:** only 2 of 11 screens handled
window insets, so titles sat under the status bar and the last row of a
scrolling screen sat under the gesture bar — where it is both unreadable and
**untappable**, because the gesture bar takes the touch. Fixed for all of them
with `Insets.kt`; recorded here because the symptom presents as an
accessibility failure even though the cause is layout. **Superseded 2026-09-15:**
`Chrome.page` builds the bar and the scrolling column together and applies the
insets itself, so a screen cannot be built without them; `Insets.kt` had no
callers left and was deleted. A shared helper every screen must remember to
call is how the next screen ends up not calling it.

**One trap in `a11y-audit.py`, hit on first use:** uiautomator reports bounds
*clipped to the viewport*, so a perfectly good 48dp control at the edge of a
`ScrollView` is reported at its visible height — 19dp, in the case that caused
the false alarm. Scroll each screen to the end before believing it.

**Not verified:** screen-reader *order* through a long timeline, which needs
TalkBack driven by hand, and contrast of text over image attachments, which
depends on the image. Both recorded rather than claimed.

---

## 14.11 Open questions

- Should quick reactions be user-configurable, or adaptive to recent use?
  **Leaning adaptive**, with a fixed fallback for a new user.
- Is 256 the right group ceiling? Arbitrary. Should be revisited against
  measured key-distribution cost.
- Should edit history be visible to everyone, or only indicate that an edit
  occurred? **Leaning visible** — hidden edit history in a messenger is a trust
  problem.

---

## 14.12 Rebuilt 2026-09-15 — the screens did not read as one product

Everything in §14 was implemented and nothing in it was wrong, and the app was
still unpleasant to use. That gap is worth recording, because none of it would
have been caught by re-reading this document: each screen satisfied the section
that specified it, and no section owns the question of whether the screens look
like the same application.

**What was actually on the phone.** The inbox opened with the app's name, the
user's Matrix ID, up to two red paragraphs of warning, and then **eight
full-width buttons** — Sign out, Search, Export my messages, Your devices,
Verify this device, Blocked people, Storage, New conversation — before the first
conversation. On a 6-inch screen the messages began below the fold. The
conversation screen had **no title bar at all**: no room name, no back control,
no indication of who was in it. Messages were flat left-aligned paragraphs with
the sender's name above every one, so a one-to-one chat looked like a mailing
list and your own words looked exactly like theirs. Nine other screens each
printed their own 26sp heading and each invented their own margins.

**What changed.**

| before | after |
|---|---|
| eight full-width buttons above the conversations | search + overflow in a title bar, new conversation on a floating button |
| two red paragraph-and-button warnings | two compact tinted cards with the action inside them |
| name + preview, no avatar, no anchor | avatar, name, time, preview, unread pill |
| no title bar in a conversation | room name, who is in it, call button, back arrow |
| identical flat paragraphs | bubbles: fill, alignment **and** a squared corner on the speaker's side |
| sender's name above every message | only at the top of a run, and only for incoming |
| reply draft as a line in the timeline | its own strip above the composer, with an X |
| five controls in the composer row | three — call and people moved to the title bar |
| ☰ 📞 + as emoji controls | drawn icons (`Icon.kt`), tinted by the theme |
| eleven screens, eleven layouts | one `Chrome` |

**Three decisions that were not obvious:**

*Emoji are not icons.* The old controls were characters, so the handset's font
decided whether a button was a flat glyph, a colour cartoon, or a tofu box —
and none of them could be tinted, so in the dark theme a black-line emoji simply
disappeared. They are now drawn paths on a 24x24 grid. The giveaway that this
was always wrong: every one of them needed a `contentDescription` to be usable
at all, because a screen reader announces an emoji as punctuation.

*`social_accent` cannot be a fill.* White text on the dark theme's accent
measures **4.09:1**, under AA. Every filled control — send, the unread badge,
your own bubble — uses `social_accent_fill`, which is a shade darker in the dark
theme for exactly that reason. Six new colour roles were added and all fourteen
new pairs are in `tools/check-contrast.py`; the palette passes AA in both themes.

*Sign out now asks.* It was the first of the eight buttons, one slip from
Search, and without a recovery key it destroys the history on the device. It is
now last in the overflow and the dialog says which of the two situations the
user is in.

**Two bugs found by building it, both invisible in the source:**

- A floating button given `WRAP_CONTENT` measures to its padding, because a
  drawn `Drawable` has no intrinsic size — a 36dp blue disc with nothing on it.
- A bubble with a `MATCH_PARENT` child inside a wrap-content parent silently
  grows to the full available width, destroying the ragged edge the whole
  left/right reading depends on.

Both are now asserted in `ChromeLayoutTest` (Robolectric, on the JVM — §34.10
rules out the emulator), and **both assertions were confirmed to fail against
the broken code before being kept**. That is the same rule the false-alarm
certificate check taught: an assertion that has never failed is not yet a test.

**Not verified on hardware.** The handsets were unavailable when this was
built. What remains to check on a phone: that the drawn icons look right at real
density, that the timeline scrolls to the bottom correctly with bubbles of
mixed heights, the stacked composer at font scale 1.8, and a re-run of
`tools/a11y-audit.py` on every screen. The two-device harnesses were updated for
the moved controls (`HOME_RE`/`SIGNED_OUT_RE`, `open_home_menu`) but have not
been run since.
