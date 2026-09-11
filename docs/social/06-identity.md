# §6 — Identity, usernames and account lifecycle

> **Confidence:** `REVISABLE`
> The MXID model is fixed by the protocol. The username policy around it is a
> product decision with real abuse consequences.

---

## 6.1 The requirement

From the brief: *"need usernames etc"*. Unpacked, that means a stable, unique,
human-chosen handle by which one user finds and addresses another — with no
phone number and no mandatory email.

That is a stronger privacy position than most messengers, which use a phone
number as the identity anchor. It also removes the discovery mechanism a phone
number provides, and §6.6 addresses what replaces it.

---

## 6.2 MXID and the two names

Matrix gives every account a permanent `@localpart:server.tld`. Three
user-facing concepts follow, and conflating them causes trouble:

| Concept | Mutable | Unique | Visible to |
|---|---|---|---|
| **Username** (MXID localpart) | No | Yes | Everyone in a shared room |
| **Display name** | Yes | No | Everyone in a shared room |
| **Avatar** | Yes | n/a | Everyone in a shared room |

**The username is the identity. The display name is decoration.**

This distinction is the single most important anti-impersonation control in the
product. A display name can be set to anything, including another user's — so
any UI that shows a display name without its username is an impersonation
vector. §6.7 states the rule.

---

## 6.3 Username rules

| Rule | Value | Reason |
|---|---|---|
| Length | 3–30 characters | Below 3 is squattable and unreadable; above 30 breaks layouts |
| Alphabet | `a–z`, `0–9`, `.`, `_`, `-` | Matrix restricts the localpart grammar; lowercase-only avoids case-confusion impersonation |
| Case | Stored and compared lowercase | `@Jeff` and `@jeff` must not be different people |
| First character | Must be a letter | Prevents all-numeric handles that read as IDs |
| Consecutive separators | Disallowed | `j..eff` vs `j.eff` is a confusion vector |
| Leading/trailing separators | Disallowed | Same |

### 6.3.1 Confusable characters

Restricting to lowercase ASCII removes the whole homoglyph class — Cyrillic `а`
for Latin `a`, and the rest. This is a deliberate rejection of Unicode
usernames: internationalised handles are a genuine accessibility good, and the
impersonation risk in a service with no other identity anchor outweighs it.

Beyond the alphabet restriction, a **skeleton check** on registration: reduce
the candidate to a canonical form collapsing visually similar characters
(`0`→`o`, `1`→`l`, `rn`→`m`, separators stripped) and reject if an existing
username shares the skeleton. `jeff-smith` and `jeffsmith` and `jeff.smith`
cannot all exist.

### 6.3.2 Reserved names

Rejected at registration, reserved to the operator:

- Service terms: `admin`, `administrator`, `support`, `help`, `system`,
  `security`, `abuse`, `moderator`, `mod`, `staff`, `official`, `team`.
- Product terms: `nexlink`, `nexlinksocial`, `thvjq`.
- Anything matching `^(the)?(real|official)` followed by a reserved term.
- A profanity list, applied to the skeleton form, not the literal string.

The list lives in server configuration, not in client code, so it can be
extended without a release.

---

## 6.4 Registration flow

Ordering matters — each step gates the next, and no account exists until all
have passed.

```
 1. Invite token entered            → validated, not yet consumed  (§9)
 2. Acceptance gate                 → 5 screens, recorded          (§9.6)
 3. Username chosen                 → availability + skeleton + reserved check
 4. Recovery path chosen            → email, or recovery key       (§7)
 5. Recovery key generated + verified (if no-email)                (§7.3.3)
 6. Account created                 → token consumed, invite tree written
 7. Device keys + cross-signing established                        (§8)
 8. First device auto-verified as the account's own
```

**Nothing is created before step 6.** A user who abandons at step 4 leaves no
account, no username reservation beyond a short hold, and an unconsumed invite
token.

**Username hold:** a candidate username is held for 15 minutes from step 3 to
prevent a race between choosing and completing. Expired holds release
automatically.

---

## 6.5 Deletion and non-reuse

When an account is deleted (§7.6), the username is **retired permanently**. It
is not returned to the pool.

The reason is impersonation. A username is a person's identity to everyone who
knows them; releasing it lets a stranger become that person to anyone who has
not noticed the change. The cost of retaining a string forever is effectively
zero. This is one of the cheapest security decisions available and it is
frequently got wrong.

Retired usernames are held in a table that the availability check consults
alongside live accounts.

---

## 6.6 Discovery

With no phone number, there is no contact-book matching, which is both the
privacy benefit and the usability cost.

**How users find each other:**

1. **Exact username.** The primary mechanism. A user shares `@jeff` out of band.
2. **Invite links.** The invite that created the account can carry the inviter's
   identity, so a new user has at least one contact on arrival.
3. **A QR code / deep link** encoding the MXID, for in-person exchange.

**Explicitly not provided:**

- A searchable user directory. A private, invite-only service should not be
  enumerable, and a directory is an abuse and harassment surface.
- Prefix or fuzzy search. Same reason — it enables enumeration.
- Contact-book upload. Would require collecting the phone numbers the design
  deliberately avoids.

**Consequence:** a user who mistypes a username gets "no such user" rather than
a helpful suggestion. That is the correct trade and should be stated in the UI
copy rather than apologised for.

---

## 6.7 Impersonation controls

Following from §6.2, a set of UI rules that are security requirements rather
than style preferences:

1. **Any surface showing a display name for a user not already known to the
   viewer must also show the username.** First message from a new contact, room
   member lists, invitations, reaction attributions.
2. **Display name changes are surfaced in the timeline** — "jeff changed their
   display name to …" — so a mid-conversation switch is visible.
3. **Verified contacts are visually distinct** (§8), and verification attaches to
   the username, never the display name.
4. **Display names are never trusted in notifications** without the username
   where the contact is unverified.

---

## 6.8 Profile data

Minimised deliberately. Every field is a disclosure surface and a moderation
obligation.

| Field | Stored | Notes |
|---|---|---|
| Username | Yes | Immutable |
| Display name | Yes | User-set, moderatable |
| Avatar | Yes | User-set, moderatable, media-repo backed |
| Email | Optional | Only if the email recovery path was chosen; used for recovery, never for discovery |
| Everything else | No | No bio, no status, no links, no pronouns field, no birthday |

**No bio field** is a deliberate choice: free-text profile fields visible to
strangers are a spam and abuse vector requiring moderation, in a product where
profiles serve no discovery function.

Display names and avatars are still moderatable content and fall under §31.

---

## 6.9 Account states

| State | Can sign in | Receives messages | Reversible | Trigger |
|---|---|---|---|---|
| **Active** | Yes | Yes | — | Normal |
| **Suspended** | No | Queued | Yes | Moderation (§31) |
| **Deactivated** | No | No | No | User deletion (§7.6) |
| **Locked** | No | Yes | Yes | Suspected compromise |

**Suspended** preserves data and can be lifted — the correct state for a
moderation action under appeal, and required for the DSA's statement-of-reasons
obligation (§4.4.2) to be meaningful.

**Locked** is the security response to a suspected account compromise: sign-in
blocked, existing sessions invalidated, but nothing destroyed while it is
investigated.

**Deactivated** is terminal. Username retired, SSSS and key backup removed,
media scheduled for deletion.

---

## 6.10 Open questions

- Should a username be changeable once, ever? Users ask for it constantly. The
  clean answer is no; the humane answer is a one-time change with the old
  username retired and a timeline notice to all shared rooms. **Leaning
  one-time change**, with the retirement rule from §6.5 applied to the old one.
- Should the inviter be pre-added as a contact? Convenient, but it reveals the
  invite relationship to the invitee. **Leaning yes** — the invitee already knows
  who invited them.
- Is a display name necessary at all, given the impersonation cost? Removing it
  would be unusually strict. **Keeping it**, with the §6.7 controls.
