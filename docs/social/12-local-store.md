# §12 — Local store and encryption at rest

> **Confidence:** `REVISABLE`
> The division of responsibility between SDK store and application store is
> fixed. The specifics depend on §11.

---

## 12.1 The requirement

From the brief: *"all client data stored on their device with a transfer
option"*.

§7.5 covers transfer. This chapter covers the store itself: what is on the
device, how it is protected, and how it is prevented from growing without limit.

---

## 12.2 Two stores, not one

A point that causes confusion if not stated early: **the application does not
own the primary store.** The SDK does.

| Store | Owner | Contains |
|---|---|---|
| **Crypto store** | SDK | Device keys, Olm/Megolm sessions, cross-signing keys, backup key |
| **State store** | SDK | Rooms, timeline events, membership, sync token |
| **Application store** | Us | App settings, NexLink link state, draft messages, UI state, notification bookkeeping |
| **Media cache** | Us (mostly) | Decrypted attachments and thumbnails |

**The crypto store is not substitutable.** It is the SDK's internal
representation and the application must not attempt to reimplement, relocate or
inspect it. The practical consequence is that §12.4's encryption-at-rest
guarantee depends on what the SDK provides, which is a §11.9 open question and
one of the things the spike must answer.

The application store is small and holds nothing secret. Most of the design
effort here belongs to the media cache (§12.6), which is where the size and the
plaintext both live.

---

## 12.3 What is on the device

| Data | Store | Sensitivity |
|---|---|---|
| Message plaintext | SDK state store | **Highest** — this is the thing E2EE exists to protect |
| Megolm session keys | SDK crypto store | **Highest** — decrypts history |
| Device identity keys | SDK crypto store | **Highest** — identity |
| Cross-signing private keys | SDK crypto store | **Highest** |
| Room membership, metadata | SDK state store | Medium |
| Decrypted attachments | Media cache | **High** — plaintext photos and files |
| Thumbnails | Media cache | High |
| Drafts | App store | Medium |
| Settings, link state | App store | Low |

Rows marked highest are the reason §12.4 is not optional.

---

## 12.4 Encryption at rest

### 12.4.1 The threat being addressed

§3.3-A2: a lost or stolen device. Specifically the case where the device is
**locked** — an unlocked device is out of scope and no messenger solves it.

Android's file-based encryption already protects application data when the
device is locked, provided a screen lock is set. That is the baseline and it is
substantial. Application-level encryption adds defence for two cases FBE does
not fully cover: an attacker with physical access attempting offline extraction,
and a device with no screen lock configured.

### 12.4.2 Approach

| Layer | Mechanism |
|---|---|
| Platform | Android FBE, active whenever a screen lock is set |
| SDK stores | Whatever the SDK provides — verify during the spike (§11.9) |
| App store | SQLCipher, or Room with an encrypted passphrase |
| Media cache | Files encrypted at rest, decrypted on demand |
| Key custody | Android Keystore, hardware-backed where available |

### 12.4.3 Keystore configuration

The store key is generated in the Keystore and never leaves it:

- **StrongBox** where the device provides it, falling back to TEE.
- `setUserAuthenticationRequired(true)` — **but see §12.4.4.**
- Not `setInvalidatedByBiometricEnrolment(true)` on the store key: a user adding
  a fingerprint would lose their message history, which is a catastrophic
  failure mode for a trivial security gain.

### 12.4.4 The screen-lock problem

Requiring user authentication to unlock the store creates a genuine conflict:
**a background sync triggered by a push cannot authenticate the user.** If the
store key needs a device credential, message reception stops whenever the phone
is locked, which is most of the time.

Options, none free:

| Option | Consequence |
|---|---|
| No auth requirement on the store key | Background sync works. Protection reduces to FBE. |
| Auth required, with a time-bound validity window | Sync works for N seconds after unlock, then stops. Messages arrive in bursts. |
| Two keys — metadata unauthenticated, content authenticated | Notifications work while locked; content is unreadable until unlock. Complex. |

**Recommendation: no authentication requirement on the store key, with a
prominent requirement that the user has a screen lock set.** The app should
detect the absence of a device lock and warn clearly, because FBE without a
screen lock provides materially less protection.

This is the honest trade: a working messenger, protected by platform encryption,
rather than a broken one protected slightly better. It should be stated in the
security disclosure (§3.7) rather than presented as stronger than it is.

---

## 12.4.5 Implemented and measured, 2026-09-12

`ClientBuilder.sqliteStore(SqliteStoreBuilder(data, cache).key(bytes))` is the
mechanism. The key is 32 random bytes, generated once and held in
`EncryptedSharedPreferences` behind a Keystore master key (`SessionStore.storeKey`).

**What it actually protects — measured on device, not assumed:**

| | |
|---|---|
| SQLite file header | **Readable** — still `SQLite format 3` |
| Table names | **Readable** — `inbound_group_session`, `secrets`, … |
| Row values | **Encrypted** |
| The account's MXID | **0 occurrences** in the crypto store |
| `curve25519` / `ed25519` / `megolm` key material | **0 occurrences** |
| Room names, display names | **0 occurrences** in the state store |

So this is **value-level encryption, not whole-file encryption.** An attacker who
obtains the file learns the schema and roughly how many sessions and rooms exist.
They do not learn any key, identifier, or message content.

That is the right security property for §12.4.1's threat — someone with the
device and time but not the screen lock — and it should be described that way.
Claiming "the local database is encrypted" would be the same kind of
overclaiming §9.6.1 forbids in the user-facing copy: true enough to sound
reassuring, wrong in a way that matters if anyone relies on it.

**Migration (§12.8): there is none.** A store created without a key cannot be
opened with one. Any existing install must sign in again, which also means
re-verifying the device (§8.4) and restoring history from backup (§8.5). That is
acceptable now, when the only accounts are test ones. **It stops being
acceptable the moment real users exist**, so this had to land before phase 6 and
did.

---

## 12.5 Size management

A messenger's local store grows without limit unless designed not to. Users
notice at the point where the app is the largest thing on their phone, and by
then it is a support problem.

### 12.5.1 Budget

| Component | Target | Hard ceiling |
|---|---|---|
| SDK state store | < 200 MB | Prune oldest timeline |
| Crypto store | < 50 MB | Never pruned — keys are not disposable |
| Media cache | User-configurable, default 1 GB | Evict LRU |
| App store | < 10 MB | — |

**The crypto store is never pruned.** Deleting Megolm sessions destroys the
ability to read history. It is small; leave it alone.

### 12.5.2 Retention controls

Surfaced in settings, with honest descriptions of what each does:

- **Keep messages:** forever (default) / 1 year / 6 months / 30 days.
- **Media cache size:** 500 MB / 1 GB / 5 GB / unlimited.
- **Auto-download media:** never / Wi-Fi only / always, with a size threshold.
- **Clear cache** — removes downloadable media only, never messages.

The distinction in the last item matters and is routinely got wrong: users
expect "clear cache" to free space, not to delete conversations. Anything that
deletes messages must say so unambiguously and confirm.

### 12.5.3 Storage visibility

A settings screen showing actual usage broken down by conversation, with the
largest first, and per-conversation media clearing. Users manage what they can
see; an opaque multi-gigabyte total produces uninstalls rather than pruning.

---

## 12.6 Media cache

The largest, most sensitive, and most neglected part of local storage.

### 12.6.1 The plaintext problem

Attachments arrive encrypted and must be decrypted to be displayed. A decrypted
file on disk is plaintext, and if it lands in shared or world-readable storage
it has escaped the encryption model entirely.

**Rules:**

1. Decrypted media lives **only** in app-private storage — never external
   storage, never `MediaStore`, never a shared cache directory.
2. Media is **not** added to the device gallery unless the user explicitly saves
   it. A "save to gallery" action is a deliberate export, and the UI should say
   so.
3. Temporary decrypted files are deleted when their viewer closes, not left for
   a cleanup pass that may never run.
4. Thumbnails are treated as sensitive: a thumbnail is a smaller copy of the
   photograph, not metadata.

### 12.6.2 Thumbnails

Because the server cannot generate thumbnails for encrypted media (§5.6), the
sending client generates them, encrypts them separately, and uploads alongside.

Consequences:
- Two uploads per image. Bandwidth cost at send time.
- Thumbnail dimensions are a client decision. Pick once and keep it stable —
  changing later means old messages have differently sized previews.
- A failed thumbnail upload must not fail the message. Degrade to a placeholder.

### 12.6.3 Eviction

LRU by last access, respecting the configured ceiling. Never evict:

- Media in the currently open conversation.
- Media referenced by an unsent or in-flight message.
- Anything the user has explicitly pinned or saved.

Evicted media is re-downloadable from the server while it exists there (§25). If
server retention has expired it, the media is gone — and the UI must distinguish
"tap to download" from "no longer available", because they require different
things of the user.

---

## 12.7 Backup and export

### 12.7.1 What is not backed up

**The application opts out of Android's automatic cloud backup entirely** —
`android:allowBackup="false"` and no `dataExtractionRules` permitting transfer.

The reason is direct: automatic backup would copy the local store to Google's
infrastructure, defeating the storage model and the encryption posture in one
line of manifest. This is a one-line decision with an outsized consequence and
it must not be quietly reverted.

### 12.7.2 What is exported

The export path from §7.5.2 produces an encrypted archive containing messages,
media and room metadata — **not** the crypto store. Device identity does not
transfer; the new device establishes its own and gains history through key
backup or direct transfer.

Format: a versioned container with a manifest, so a future version can read an
older export. An export nobody can import is a false promise.

---

## 12.8 Migration

Schema changes are inevitable and a failed migration on a messaging app means a
user's conversations are gone.

- Every migration is tested against a store populated by the previous version,
  in CI, not by hand.
- Migrations are forward-only. No downgrade path is offered or implied.
- A failed migration must **not** silently recreate an empty store. It should
  fail loudly, preserve the old store, and offer recovery from key backup.
- The SDK's own store migrations are the SDK's responsibility, which makes SDK
  upgrades a data-integrity event (§11.9) and not merely a dependency bump.

---

## 12.9 Open questions

- Does the chosen SDK encrypt its stores, and with what key custody? Blocking
  question for the spike.
- Should retention default to "forever"? Kinder to users, worse for storage and
  for §4.3.2 data minimisation — though device-local data is the user's, not the
  operator's, which weakens the argument. **Leaning forever**, with visible
  controls.
- Is per-conversation retention worth the complexity over a global setting?
  **Leaning no** for the first release.
