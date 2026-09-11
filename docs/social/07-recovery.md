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
