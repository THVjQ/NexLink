# §5 — Matrix, as it applies here

> **Confidence:** `DURABLE`
> Protocol fundamentals, restricted to what this product actually uses. Not a
> general Matrix tutorial — the specification is the reference for that.

---

## 5.1 Why Matrix

The argument in one line: **the expensive parts of an encrypted messenger are
not the encryption.**

The ratchet is well-understood and available in library form from several
sources. What consumes years is everything around it — multi-device identity,
device verification, key backup and recovery, history availability across
devices, group key rotation on membership change, and a web client. Matrix has
all of these, specified, implemented, and deployed at scale.

| What the product needs | Matrix provides |
|---|---|
| E2EE, 1:1 and group | Olm and Megolm |
| Multiple devices per account | Per-device identity keys |
| Trust across devices | Cross-signing |
| History on a new device | Server-side encrypted key backup |
| Account recovery from a key | SSSS, with a recovery key |
| Group membership semantics | Rooms with power levels |
| Reactions, edits, replies | Standard event types |
| A web client | Element Web, free and maintained |
| Server administration | Admin API |

Adopting it converts most of §1.2 from *build* to *configure*. That is the whole
case, and it is a strong one.

---

## 5.2 Core concepts

### 5.2.1 Identity

A user is a **Matrix ID**: `@localpart:server.tld`. For this product the server
part is fixed to the operator's domain, so users see and type only the
localpart — the username (§6).

The MXID is permanent. It is not an email address, cannot be changed, and is not
reassigned after deletion (§6.5).

### 5.2.2 Rooms

Everything is a **room** — a one-to-one conversation is a room with two members
and no name. There is no separate DM primitive, which is a simplification: group
features work in 1:1 conversations for free.

A room holds:
- **Timeline events** — messages, reactions, edits. Append-only.
- **State events** — membership, name, avatar, power levels. Keyed, last-write-wins.
- **Power levels** — an integer per user determining what they may do. Used for
  group admin (§14).

### 5.2.3 Events

Every action is an event with a type. The ones this product uses:

| Event type | Purpose |
|---|---|
| `m.room.message` | A message. Subtypes for text, image, video, audio, file. |
| `m.room.encrypted` | The encrypted envelope. What actually travels for E2EE rooms. |
| `m.reaction` | A reaction. Carries an arbitrary string — see §5.5. |
| `m.room.redaction` | Deletion. |
| `m.room.member` | Join, leave, invite, ban. |
| `m.room.power_levels` | Permissions within the room. |
| `m.room.encryption` | Marks a room as encrypted. **Set at creation, never removed.** |

### 5.2.4 Sync

Clients call `/sync` with a token and receive everything since. Long-polling,
resumable, the backbone of the client's data flow. Modern clients use sliding
sync (incremental, prioritised) where the server supports it — relevant to
startup performance and covered in §13.

---

## 5.3 The encryption model

### 5.3.1 Olm

The Double Ratchet, device to device. Establishes a session between two specific
devices with forward secrecy and break-in recovery. Used to transport Megolm
keys, not usually message content directly.

### 5.3.2 Megolm

A ratchet designed for groups. One sender creates a **session**; content is
encrypted once with that session and delivered to every recipient device, with
the session key distributed to each device over Olm.

Efficient — the message is encrypted once regardless of recipient count — with
one consequence that shapes the product: **a device that receives a session key
can decrypt everything from that point in the session forward.** Which is
exactly why history is unavailable to a newly added device unless key backup
provides it (§5.4).

Sessions rotate on a message count, an elapsed time, and **on membership change**
— someone leaving a room must not be able to read what follows.

### 5.3.3 What the server sees

For an encrypted room, per message:

| Visible | Not visible |
|---|---|
| Sender MXID and device ID | Message content |
| Room ID | Message type (text vs. image) |
| Timestamp | Attachment content |
| Approximate size | Reaction emoji |
| Recipient device list | Anything about meaning |

The right-hand column is the guarantee. The left-hand column is the honest
disclosure required by §3.7.

---

## 5.4 Cross-signing, SSSS and key backup

The three mechanisms that make multi-device work. Detailed in §8; introduced
here because they are the least familiar part of Matrix and everything about
account recovery depends on them.

**Cross-signing** gives a user three keys: a **master** key that is their
identity, a **self-signing** key that signs their own devices, and a
**user-signing** key that signs other users they have verified. The effect: a
user verifies another user once, rather than verifying every one of their
devices individually.

**SSSS** stores the cross-signing private keys and the key-backup key on the
server, encrypted with a key derived from the user's recovery key. The server
holds them and cannot use them.

**Key backup** stores Megolm session keys, client-encrypted, on the server. This
is what makes history available on a new device.

The dependency chain — recovery key unlocks SSSS unlocks everything — is drawn
in §7.4.2 and is the reason a lost recovery key is terminal.

---

## 5.5 Reactions

Directly relevant to a stated requirement: *"reactions like hearts etc — need to
be able to use any of Samsung keyboard emojis."*

`m.reaction` carries an arbitrary string. There is no server-side enumeration of
permitted reactions, so **any emoji the user's keyboard produces is already
supported by the protocol.** The work is entirely client-side (§14).

Two implementation constraints:

1. **Store the whole grapheme cluster.** A single user-perceived emoji may be
   several code points — skin tone modifiers, zero-width joiner sequences,
   variation selectors. Normalising, truncating, or iterating by code point
   corrupts them. Iterate by grapheme.
2. **Do not restrict to a curated set.** A fixed picker is a client limitation
   masquerading as a design choice, and it directly contradicts the requirement.

Reactions in an encrypted room are themselves encrypted, so the server does not
learn which emoji was used.

---

## 5.6 Media

Attachments upload to the homeserver's media repository and are referenced by
URI. In an encrypted room the client encrypts the file **before** upload with a
one-time key, and transmits that key inside the encrypted event. The server
stores ciphertext.

Consequences that surface later:

- The server cannot generate thumbnails for encrypted media. The client must
  generate and upload them separately, encrypted the same way (§14).
- The media repository grows monotonically unless a retention policy exists.
  This is the largest single infrastructure cost surprise (§25).
- Media is not automatically deleted when a message is redacted. Cleanup is an
  operational task, not an automatic consequence.

---

## 5.7 Push

Mobile clients cannot hold a socket open. Matrix uses a **push gateway**: the
homeserver notifies the gateway, which sends to FCM.

Because content is encrypted, the push payload cannot contain the message. The
client receives a notification, wakes, syncs, decrypts, and only then can render
the real content — which is why encrypted messengers briefly show "New message"
before resolving to a name. Design consequences in §13.

---

## 5.8 What this product does not use

Matrix is large. Explicitly out of scope:

| Feature | Why not |
|---|---|
| Federation | D5 (§2.6) |
| Spaces | No hierarchy requirement (§1.6) |
| Threads | Flat replies only (§1.6) |
| Bridges | §1.3 |
| Guest access | Invite-only (§2.5) |
| Public room directory | Private service; nothing to browse |
| Server-side search | Incompatible with E2EE (§1.3) |
| Third-party identity servers | Adds a data-sharing surface for no benefit |
| Widgets | Except as required by the calling stack (§17) |

Disabling unused features is a security measure, not just tidiness. Each one is
attack surface and each one is a moderation obligation that need not exist.

---

## 5.9 What to verify during the spike

Marked because the following are stated here from general knowledge and should
be confirmed against the actual deployed versions before anything is built on
them:

1. Sliding sync availability and behaviour on the chosen homeserver.
2. Whether recovery keys round-trip between the Android client and Element Web
   in both representations (§7.4.3).
3. Megolm session rotation defaults, and whether they are appropriate.
4. Media repository behaviour with S3-backed storage, particularly deletion.
5. The current state of the calling stack (§17) — the fastest-moving part of the
   ecosystem and the least safe to assume.
