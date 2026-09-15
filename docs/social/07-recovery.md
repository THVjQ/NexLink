# §7 — The no-email path and recovery keys

> **Confidence:** `REVISABLE`
> The cryptographic mechanism is Matrix's Secure Backup and is stable. The
> wizard design around it is a product decision that should be usability-tested
> before it ships.

---

## 7.1 The requirement

From the original brief:

> *"need usernames etc — but have email account setup but a button for no email
> if wanted and then a code or something generated and supplied with full
> warning about data loss if they lose their account"*

Two account creation paths, then:

| Path | Recovery mechanism | Failure mode |
|---|---|---|
| **Email-backed** | Password reset by email, plus a recovery key for message history | Losing the password is recoverable. Losing the recovery key still loses history. |
| **No-email** | Recovery key only | Losing the recovery key loses the account *and* the history, permanently. |

The distinction users invariably fail to grasp — and which the wizard exists to
make unmistakable — is that **these are two different secrets protecting two
different things.**

## 7.2 The two secrets, and why users conflate them

This is the single most confusing aspect of encrypted messaging account
recovery, and getting the explanation right matters more than getting the
implementation right.

**The password (or email link) controls *account access*.** It proves to the
server that you are entitled to log in. Reset it and you can sign in again.

**The recovery key controls *message decryption*.** It unlocks the encrypted
backup of your message keys. The server has never had it and cannot reset it.

The consequence users do not anticipate: **you can successfully log in and find
every message unreadable.** An account reset via email restores access to an
empty history if the recovery key is gone. Every messenger with real E2EE has
this property; most explain it badly, usually after the fact.

For the no-email path the two collapse into one secret, which is simpler to
explain and far more dangerous to lose.

## 7.3 The data-loss warning

The brief asks for a "full warning". A warning screen that users click past is
not a warning; it is a liability transfer that does not even work as one. The
design goal is *comprehension*, not consent theatre.

### 7.3.1 What makes warnings fail

- Presented as a wall of text at the moment the user wants to finish signing up.
- Phrased in terms of mechanism ("your cross-signing keys will be irrecoverable")
  rather than consequence ("you will lose every message").
- Dismissible with a single tap that carries no cognitive load.
- Shown once, at the point of least engagement.

### 7.3.2 The design

**Consequence first, mechanism second, and never mechanism alone.**

The no-email path presents a dedicated screen — not a dialog, not an aside — with
this shape:

> ### Without an email address, this key is the only way back in
>
> If you lose it, we cannot help you. Not "it will be difficult" — there is no
> process, no support request, and no override. Your account and every message
> in it are gone permanently.
>
> This is not a policy we could change. The key is the only thing that can
> decrypt your messages, and we have never had a copy.

Then the key, then the confirmation gate.

### 7.3.3 The confirmation gate

A checkbox is insufficient for a consequence this severe. The user must
demonstrate they have recorded the key:

1. The key is displayed, with **Copy** and **Save to file** actions.
2. A deliberate **"I've saved it"** action advances to verification.
3. **Verification:** the user re-enters four randomly chosen words (or four
   character groups) from the key. Not the whole thing — that trains
   copy-pasting, which defeats the point. Four positions is enough to prove
   possession without becoming an obstacle.
4. Only on correct entry does account creation complete.

**Failure handling:** a wrong entry returns to the key display, not to an error
state. The point is to get the key saved, not to punish.

**No skip.** There is deliberately no "I'll do this later" on the no-email path,
because there is no later — the account cannot exist without the key being
established.

### 7.3.4 Reinforcement

One warning at signup is not enough for a consequence that materialises months
later.

- A persistent, dismissible-but-recurring banner until the user has confirmed
  key storage from a second device or re-verified it once.
- A gentle re-check at 7 days: *"Can you still find your recovery key?"* with a
  verification prompt and an easy path to regenerate.
- Inclusion in any "account health" surface.

## 7.4 Mechanism: Secure Backup

Matrix already implements exactly this model, which is a substantial reason to
adopt the protocol rather than design an account system.

### 7.4.1 Components

**SSSS (Secure Secret Storage and Sharing, sometimes "4S")** — a server-side
store of secrets encrypted with a key the server never sees. Holds the
cross-signing private keys and the key-backup decryption key.

**The recovery key** — the root secret. Either generated randomly and shown to
the user, or derived from a user-chosen passphrase via key stretching. It
decrypts the SSSS contents.

**Key backup** — Megolm session keys, encrypted client-side and stored on the
server. This is what makes history available on a new device. The server holds
ciphertext it cannot decrypt.

### 7.4.2 The chain

```
recovery key
    └─ decrypts SSSS
         ├─ cross-signing private keys  → establishes device trust (§8)
         └─ key backup decryption key   → decrypts Megolm sessions
                                            → decrypts message history
```

Every branch depends on the root. This is precisely why losing it is terminal,
and the diagram is worth showing users who ask why no override exists.

### 7.4.3 Key format

Matrix recovery keys are conventionally rendered as a base58 string in
space-separated groups. Two presentation options:

| Option | Pros | Cons |
|---|---|---|
| **Native base58 key** | Standard, interoperable with any Matrix client | Opaque, hard to transcribe by hand, easy to mistype |
| **BIP39 mnemonic** (12 or 24 words) | Far easier to write down, read aloud, and verify; well-understood error properties | Non-standard for Matrix; requires a reversible mapping; longer |

**Recommendation: display the mnemonic, store and transmit the native key.**
The mnemonic is a presentation layer over the same entropy. Users write down
words; the protocol keeps its format; interoperability with Element Web (§20) is
preserved because the underlying key is unchanged.

**Caveat marked `SPECULATIVE`:** the mapping must be exactly reversible and
must round-trip through Element Web, where a user may be asked for the native
key. The wizard must therefore offer *both* representations, with the native key
available behind a "show the technical version" affordance. Verify during the
spike.

## 7.5 Device transfer

The brief asks for local storage "with a transfer option". Three mechanisms,
which are complementary rather than alternatives:

| Mechanism | Moves | Requires | Best for |
|---|---|---|---|
| **Key backup restore** | Message keys, then history re-downloaded | Recovery key, network | New device, old one gone |
| **Direct device transfer** | Full local store | Both devices present, same network | Phone upgrade |
| **Encrypted export file** | Full local store as a file | User-chosen passphrase | Archival, or moving without either device online |

### 7.5.1 Direct transfer

The strongest experience for the common case of upgrading a phone.

1. Old device displays a QR code containing a session identifier and an
   ephemeral public key.
2. New device scans it, establishing an authenticated channel.
3. Transfer over local network, or via the server as an encrypted relay if
   direct connection fails.
4. Old device offers to sign out and wipe once transfer verifies.

**Constraint:** never transfer over an unauthenticated channel. The QR code
carries the key material that authenticates the pairing — this is the same
trust-establishment problem as device verification (§8), and it gets the same
answer.

### 7.5.2 Export file

An encrypted archive, passphrase-protected, written to user-chosen storage.

The passphrase must be **distinct from the recovery key** and the UI must not
allow reuse, because an exported file plus a reused recovery key in the same
cloud drive is a single point of total compromise.

### 7.5.3 Built 2026-09-12 — the archive, and the half that is not built

**Built and verified: the archive format and the export side.**
`TransferArchive` writes AES-256-GCM over a zip, keyed by PBKDF2-HMAC-SHA256 at
600,000 iterations (OWASP's floor for SHA-256). Argon2id would be the better
KDF and is not in the platform; adding a native KDF to `:social-core` is a
dependency decision in its own right (§11), so the parameters are written into
the header instead and raising them later still reads old files.

The header is fed to GCM as **AAD**, which binds the salt and the iteration
count to the ciphertext: an attacker cannot edit the header down to 1,000
iterations to make a dictionary attack cheap without invalidating the tag.
`TransferArchiveTest` asserts exactly that by doing it.

Verified on hardware, end to end:

| check | result |
|---|---|
| Archive written from a real signed-in account | 127,169 bytes, 25 entries |
| Account id / homeserver / `SQLite format 3` present in the file | **none** |
| Shannon entropy of the body | **7.998** bits/byte |
| Header self-describing | `magic=NLSOCIAL version=1 iterations=600000 salt=16B nonce=12B` |
| GCM tag verifies | yes |
| `matrix-sdk-crypto.sqlite3` recovered | 192,512 bytes, intact |

The decryption was done by a **separate implementation** written for the purpose
(`scratchpad/decrypt.py`, hashlib plus a pure-Python AES-GCM), sharing no code
with the app. That is the point: a format decrypted only by its own writer is
not a format, it is a coincidence. This one is specified by its header.

**How §7.5.2's reuse rule is actually enforced — and why not the obvious way.**
Comparing the passphrase against the recovery key would require *having* the
recovery key, and §7.2 is emphatic that the app never keeps it. Storing a copy
to police reuse would manufacture precisely the asset the design exists to
avoid — a worse outcome than the reuse it prevents.

So it is enforced by **shape**. A recovery key, generated on the test account
and recorded here because the rule depends on the real form rather than an
assumed one:

```
EsU3 G5nq 5QkF N9Br Svbn uz7a xUHE pzVy xetu wLAh WSTQ UzFf
```

48 base58 characters, twelve groups of four, prefix `Es`. Anything with that
shape is refused, spacing ignored, with a message that says why. Verified on the
phone: the dialog stays open, the field is marked, and **no file is written**.

This is weaker than a comparison, and that is stated rather than glossed: it
cannot catch someone who retypes the key with one character altered. It catches
the copy-and-paste, which is how reuse actually happens. Its cost is that a
48-character base58 string beginning `Es` cannot be used as a passphrase, which
costs nobody anything real.

### 7.5.3a The other half, built 2026-09-15

§7.5.3 called the restore *"the half that is not built"*. It is built now, and
the thing worth recording is what was already there: `TransferArchive.read` and
`SessionStore.importBundle` were both written, both tested, and **called by
nothing in the app**. The feature was a file format with no product attached —
a backup that could not be restored, which is not a backup but a file that makes
people feel safe.

`ImportActivity` is reachable from the sign-in screen, which is where the person
who needs it actually is: they cannot sign in on a phone they no longer have,
and a restore is not a sign-in.

**Two rules it enforces.**

*Only when signed out.* Restoring over a live session replaces the store key and
the credentials underneath a running client — §7.4.4's failure mode, an account
that looks signed in and decrypts nothing.

*Nothing lands until the whole archive has decrypted and been checked.*
Extraction goes to a staging directory; the store is only overwritten once the
archive has decrypted **and** carried §7.5.2's credential bundle.

#### The justification for the staging was wrong, and the test caught it

It was written believing `read()` streams entries to disk and only discovers a
bad archive at the GCM tag, leaving a partial store. A test was added to prove
that. **It proved the opposite:** on the JVM, SunJCE buffers the ciphertext and
releases nothing until the tag verifies — that is what authenticated encryption
is for — and not one byte reached disk at 8 KB or at 8 MB.

Android uses Conscrypt rather than SunJCE, so whether a real phone can leave a
partial store is **unmeasured**, and is now written down as unknown rather than
assumed either way.

The staging stays on a reason that does hold: it makes the *whole restore*
atomic rather than just the decryption. The credential check happens after
extraction, so without staging a bundle-less archive — §7.5.4's defect seen from
the other side — would have overwritten the store before it could be refused.

The test now asserts the behaviour that is actually true, and says in its own
failure message what it would mean if that ever changed.

---

### 7.5.4 The first build of this was useless, and the audit caught it

Written the same day, a few hours later, by listing every entry of the archive
that had just been shipped rather than the first six.

**Defect 1 — the archive held stores that nothing could open.** It packed
`filesDir` and `cacheDir`, which *looked* complete: every SQLite file was there,
the crypto store recovered byte-intact, the independent decryptor was happy. But
**the store key lives in `EncryptedSharedPreferences`, and that is
`shared_prefs/` — a sibling of `filesDir`, not inside it.** So the archive
carried encrypted databases and no key. It would have restored nothing.

Copying `shared_prefs/social_session.xml` in would not have fixed it either:
that file is sealed by a Keystore master key which by design never leaves the
device. On a new phone it is undecryptable ciphertext.

The real conclusion is that **custody has to differ by destination**, and §12.4.3
and §7.5.2 are describing two different problems:

| Where the secret lives | Protected by |
|---|---|
| On this device | Keystore, hardware-backed where available (§12.4.3) |
| In an archive | the user's passphrase (§7.5.2) |

The store key and session credentials now travel as a synthetic entry,
`keys/session.json`, inside the passphrase-encrypted blob — and come back
through a callback rather than being written to the filesystem, since they
belong in the Keystore-backed prefs on arrival.

This also sharpens why [Passphrase]'s rules matter: **that entry is a complete
account takeover to anyone who can read it.** The passphrase is the only thing
protecting it, which is the whole reason §7.5.2 forbids reusing the recovery key
and why the length floor is not decoration.

**Defect 2 — the archive contained itself.** The output was written to
`cacheDir` while `cacheDir` was being archived. The shipped file has
`cache/nexlink-social-backup.nlsx` in its entry list. Harmless here, unbounded in
principle. `write` now takes an `exclude` set.

Both are covered by regression tests. Re-verified on the device: 25 entries, no
`.nlsx` inside, `keys/session.json` present with a 32-byte store key and the
account's credentials.

> The lesson is narrower than "test more". The first verification was real — an
> independent implementation decrypted the file and checked the crypto store
> byte-for-byte. It proved the *format* was sound and said nothing about whether
> the *contents* were sufficient, and the six-entry listing hid the gap. Verify
> the thing the feature is for, not the thing that is easy to assert.

**Not built — the restore side.** The archive can be created and saved; it
cannot yet be loaded back. Restoring the store means writing into `filesDir`
*before* any client is constructed and then restarting the process, because the
SDK holds the SQLite files open. That sequencing is the whole difficulty and a
half-done version is dangerous: a partially restored store presents as a working
account with silently missing keys. `TransferArchive.read` exists and is tested
(including zip-slip and tamper rejection); what is missing is the app-lifecycle
work around it.

**Not built — §7.5.1 direct transfer.** QR pairing plus a local-network
transport with a server relay fallback is a feature of its own size, and the
CGNAT constraint (§26) means the relay path cannot be hand-waved. Recorded as
outstanding rather than quietly dropped.

**What §7.5 delivers today,** against its own three rows: key backup restore
works (§7.4, verified by `RecoveryRestoreTest`); the encrypted export file is
half-delivered — it writes, it does not read back; direct transfer is not
started.

## 7.6 Account deletion

Play policy requires deletion to be available in-app and from a public web URL
(§4). The encryption model makes some of this genuinely straightforward and one
part genuinely impossible.

| What | Deletable | Note |
|---|---|---|
| Account record and credentials | Yes | Full removal |
| SSSS contents and key backup | Yes | Removes any possibility of history recovery |
| Media uploaded by the user | Yes | Subject to §25 retention |
| Room membership | Yes | User leaves all rooms |
| **Messages already delivered to others** | **No** | They are on recipients' devices. Cannot be recalled. |
| Metadata in server logs | Time-bounded | Removed on the retention schedule, not instantly |

The deletion flow must state the fourth row plainly. Implying that deletion
retracts sent messages is a misrepresentation.

**Username reuse:** deleted usernames are **not** released for re-registration.
See §6.5 — reuse enables impersonation of a departed user, and the cost of
retaining a string forever is nil.

## 7.4.4 Verified on hardware, 2026-09-11 — and two things that must be got right

§11.7 step 6 **passes**: a recovery key, and nothing else, restored history on a
genuinely new device (SM-G990E, live homeserver, `RecoveryRestoreTest`). §7's
central promise holds.

Getting there exposed two requirements that are **product requirements, not test
details**. Both produce the same user-visible symptom — *"I typed my recovery key
and my messages are still unreadable"* — which is the single worst outcome §7 can
produce, because the user did everything right.

### 1. Setting up recovery MUST bootstrap cross-signing, not just key backup

Calling `enableRecovery()` on a fresh account creates a key backup and puts its
key into 4S, but creates **no cross-signing identity**. The consequence on a
second device:

```
recover(key) succeeds, throws nothing
  backup:        UNKNOWN -> ENABLED      (the key was restored)
  verification:  UNVERIFIED              (device is not trusted)
  recoveryState: INCOMPLETE              (secrets missing from 4S)
  history:       never decrypts
```

The device holds the backup key and still cannot read anything, indefinitely.
After bootstrapping a cross-signing identity first (`resetIdentity()`, which
needs UIA), the same flow gives `VERIFIED` / `ENABLED` and history decrypts.

**Requirement:** the recovery-setup flow in §7.4 creates a cross-signing identity
*and* a key backup, in that order, as one user-facing step. It must never be
possible to finish onboarding with one and not the other.

### 2. "Backup complete" must mean the backup contains keys

`waitForBackupUploadSteadyState()` returns `Done` when nothing is *currently*
queued. Called immediately after sending a message, that is true and meaningless
— the new megolm session has not been queued yet. Observed: the wait reported
`Done`, and the server showed `room_keys/version` → **`count: 0`**, an empty
backup. The second device was then correct to fail.

**Requirement for §8.5.2's backup health:** health is *"the backup contains keys
for the sessions this device knows about"*, not *"a steady state was observed
once"*. A client that tells the user their history is safe on the strength of a
single `Done` is telling them something false, and they will only discover it on
the day they replace their phone.

This is also why §7.3's warnings are not enough on their own. The warning says
*keep your key*. It cannot help a user whose key is fine and whose backup is
empty.

---

## 7.7 Open questions

- Should the email path *also* mandate the recovery-key gate, or offer it as a
  strong recommendation? Mandating it makes signup longer but prevents the
  "logged in, no history" surprise (§7.2). **Leaning mandate**, with lighter
  wording than the no-email path since the consequence is smaller.
- Should a no-email user be able to add an email later? Yes, and it should be
  prominently offered — it is a strict improvement in recoverability. Needs a
  verification flow.
- Is passphrase-derived recovery (instead of a random key) worth offering?
  Easier to remember, much weaker, and users choose badly. **Leaning no** —
  offer only the generated key.
