# §3 — Threat model and security posture

> **Confidence:** `DURABLE`
> Written before implementation deliberately. A threat model produced after the
> fact describes what was built, not what should have been.

---

## 3.1 Why this chapter comes before the architecture

Every subsequent design decision — where keys live, what the server stores, what
a recovery code can do, whether a backup exists — is an answer to a question
posed here. Writing the architecture first and the threat model afterwards
produces a document that justifies whatever was convenient.

## 3.2 Assets

What is actually being protected, in rough order of severity if lost:

| Asset | Where it lives | Consequence of compromise |
|---|---|---|
| Message plaintext | Client devices only | Total confidentiality failure. Unrecoverable — messages cannot be un-read. |
| Device identity keys | Client secure storage | Impersonation of the user; ability to read future messages sent to that device. |
| Cross-signing master key | Client, and encrypted in SSSS | Attacker can sign new devices as legitimate, defeating verification for everyone who trusts that user. |
| Recovery key (no-email accounts) | User's custody only | Full account takeover, including decryption of any key backup. |
| Megolm session keys | Client store, and encrypted in key backup | Retrospective decryption of history the sessions cover. |
| Social graph and metadata | Server database | Who talks to whom, when, how often, message sizes. Not protected by E2EE. |
| Media blobs | Object storage | Encrypted at rest by the client, so ciphertext only — but volume and timing leak. |
| Account credentials | Server (hashed) and client | Account access, though not message decryption without device keys. |
| Server infrastructure | Operator-controlled | Metadata access, denial of service, ability to attempt active attacks. |

**Note the asymmetry in row 6.** End-to-end encryption protects content. It does
not protect metadata. This is a real, permanent limitation of the design and
must be disclosed honestly to users rather than glossed — see §3.7.

## 3.3 Adversaries

Modelled in ascending order of capability.

### A1 — Another user of the service
Has a legitimate account. Wants to read conversations they are not part of,
impersonate someone, or harass.

**Mitigations:** room membership is enforced server-side and cryptographically
(Megolm sessions are only shared with joined devices); usernames are unique and
non-reassignable (§6.5); blocking and reporting are mandatory (§31); invite-only
registration means every account traces back to an inviter (§9.4).

### A2 — A lost or stolen device
Physical possession of an unlocked or lockable phone.

**Mitigations:** local store encrypted at rest with a key held in Android
Keystore, gated on device credential (§12); remote device sign-out invalidates
the device's ability to receive new Megolm sessions; the user can review and
revoke sessions from any other logged-in device.

**Accepted limitation:** an *unlocked* stolen device gives full access to
history on that device. No messenger solves this. Screen-lock is the control.

### A3 — A network attacker
Controls or observes the network path between client and server. Public Wi-Fi,
hostile ISP, state-level passive collection.

**Mitigations:** TLS 1.3 for all client-server traffic; certificate validation
with no user override; E2EE means TLS compromise alone yields ciphertext;
DTLS-SRTP for call media.

**Accepted limitation:** traffic analysis. Message timing and size are visible
even under TLS.

### A4 — A compromised or malicious server operator
This includes the case where THVjQ's own infrastructure is breached, and the
case where THVjQ is legally compelled. **The design must not assume the operator
is trustworthy.** This is the central discipline of the whole document.

**What the operator can do regardless:**
- See who messages whom, when, and how large messages are.
- See IP addresses and rough device information.
- Deny service.
- Attempt to insert a device into a user's account (see below).

**What the operator cannot do:**
- Read message content.
- Read media content.
- Decrypt key backups (encrypted with a key derived from the recovery key,
  never transmitted).
- Silently add a device *without detection*, provided users verify devices —
  cross-signing surfaces an unverified new device to every conversation partner.

**The critical mitigation is cross-signing (§8).** A malicious server can inject
a device; it cannot make that device appear *verified*. The security of the
system against its own operator rests almost entirely on users noticing
verification warnings, which means those warnings must be clear, prominent, and
not trained-away by false positives.

### A5 — Legal compulsion
A court order for user data.

**Mitigations, such as they are:** the operator cannot produce plaintext because
it does not have it. This is a design property, not a policy — it survives a
change of ownership or a change of heart.

**What can be compelled:** metadata, IP logs, account records, ciphertext.
Retention policy (§25.4, §32) directly determines how much of this exists to be
produced. **Minimising retention is the mitigation.**

**The unmitigated case:** an order compelling a malicious client update to a
specific user. No cryptographic design defends against a compromised software
supply chain. Reproducible builds and transparency of releases reduce, but do
not eliminate, this.

### A6 — A malicious inviter
Specific to invite-only registration: a user who invites accounts in bulk to
farm them, or invites someone in order to harass them.

**Mitigations:** invite quotas per user (§9.5); invite tree is recorded, so
abuse can be traced and a whole subtree revoked; acceptance gate requires
positive action from the invitee (§9.6).

## 3.4 Out of scope

Stated so the boundary is explicit rather than implied by omission:

- **Compromised client operating system.** A rooted or malware-bearing Android
  install can read anything the app can read. Out of scope.
- **Screenshots and physical observation.** Any recipient can photograph their
  screen. Disappearing messages and screenshot detection are theatre; neither is
  in scope (§1.6).
- **Malicious recipients.** Anyone in a conversation can save, forward or
  publish its contents. E2EE protects against third parties, never against
  participants.
- **Traffic analysis resistance.** No padding, cover traffic or mixnet
  behaviour. Out of scope permanently — it is incompatible with a battery- and
  bandwidth-sensitive mobile product.
- **Post-quantum cryptography.** Not addressed by Matrix's current primitives.
  Tracked as a risk (§36), not a requirement.

## 3.5 Security posture: the non-negotiables

These are invariants. Any change request that violates one is a security
decision requiring explicit sign-off, not a feature.

1. **No plaintext fallback, ever.** There is no code path, no error handler, no
   compatibility mode that sends message content unencrypted. If encryption
   fails, the send fails visibly.
2. **Private keys never leave the device unencrypted.** Not in backups, not in
   logs, not in crash reports, not in support tooling.
3. **The server is never trusted for authorisation of decryption.** Room
   membership determines Megolm key distribution client-side.
4. **No operator-accessible backdoor**, including for account recovery. A user
   who loses their only recovery path loses their history. This is the cost of
   the guarantee and must be stated plainly at signup (§7.3).
5. **Verification warnings are never suppressed** to reduce user friction.
6. **Crash reports and analytics carry no message content, no room IDs, no user
   IDs, and no key material.** This constrains debugging and is accepted.
7. **Screen sharing is explicitly initiated per session** and visibly indicated
   to all call participants for its full duration.

## 3.6 The encryption stack, briefly

Detailed in §5; summarised here for threat-model completeness.

| Layer | Mechanism | Protects |
|---|---|---|
| Transport | TLS 1.3 | Client↔server traffic from network observers |
| One-to-one sessions | Olm (Double Ratchet) | Device-to-device key exchange, forward secrecy |
| Group messages | Megolm | Room message content, efficiently for many recipients |
| Device trust | Cross-signing | Identity continuity across a user's devices |
| Secret storage | SSSS / 4S | Cross-signing keys and backup key, encrypted with a user-held key |
| Key backup | Server-side encrypted Megolm session backup | History recovery, opaque to operator |
| Call media | DTLS-SRTP | Call audio/video in transit |
| Group call media | SFrame / insertable streams | Call media from the SFU itself |
| Local store | SQLCipher or equivalent, key in Keystore | History at rest on the device |
| Media at rest | Client-side AES before upload | Attachment content from the operator |

**The weakest link is the group call path.** An SFU necessarily terminates and
re-forwards media; keeping it end-to-end encrypted requires insertable streams,
which is the least mature part of the stack. §17 treats this as the primary
technical risk of the project.

## 3.7 What users must be told

The security posture is only honest if it is communicated. The following are
required disclosures, to appear in the acceptance gate (§9.6) and the privacy
policy — not buried in a help page:

1. Message and call *content* is end-to-end encrypted and the operator cannot
   read it.
2. The operator **can** see who you communicate with, when, and how much — and
   retains that for a stated period.
3. Your messages are stored on your device. If you lose the device and have no
   recovery path configured, they are gone.
4. If you choose an account without an email address, **losing your recovery key
   means losing the account permanently.** No one can restore it.
5. Anyone you message can save or share what you send them.
6. The service is operated by an individual, not a company with a support
   department — with the reliability expectations that implies.

Point 6 is unusual to state and should be stated anyway. Users of a solo-operated
service deserve to know that is what they are using.

## 3.8 Review triggers

The threat model is re-examined, not merely re-read, when any of these occur:

- The federation decision (§21.3) is revisited.
- Any form of server-side search, indexing or moderation-scanning is proposed.
- A second operator or employee gains infrastructure access.
- The invite-only constraint is relaxed.
- Any third-party SDK gaining message-content access is introduced.
- A jurisdiction imposes a scanning or key-escrow obligation.
