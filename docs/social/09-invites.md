# §9 — Invites and the acceptance gate

> **Confidence:** `DURABLE`
> Both decisions in this chapter were made explicitly by the product owner and
> are treated as constraints.

---

## 9.1 Why invite-only

Registration control is the highest-leverage single decision in the operational
design. It simultaneously addresses four otherwise-separate problems:

| Problem | How invite-only addresses it |
|---|---|
| **Spam accounts** | Open Matrix registration is abused within days of becoming publicly reachable. An invite requirement removes the automated path entirely. |
| **Unbounded cost** | Users arrive at a rate the operator controls. Infrastructure can be provisioned against a known ceiling rather than a hope. |
| **Moderation load** | Every account is traceable to an inviter (§9.4). Abuse has an accountable origin, and a bad subtree can be revoked wholesale. |
| **Legal exposure** | A small, traceable, non-public user base is a materially different regulatory proposition from an open public network. |

It is also cheap to reverse in one direction only: opening registration later is
a configuration change; closing it after a public launch means removing accounts
from real users. **Start closed.**

## 9.2 Invite token model

Matrix homeservers support registration tokens natively, which means this does
not require custom server code. In Synapse the relevant configuration is
`registration_requires_token`, with tokens administered through the admin API.

### 9.2.1 Token properties

Each token carries:

| Field | Purpose |
|---|---|
| `token` | The code itself. See §9.3 for format. |
| `uses_allowed` | How many accounts the token may create. Default `1`. |
| `pending` / `completed` | Consumption tracking, maintained by the server. |
| `expiry_time` | Absolute expiry. Default 14 days from issue. |

### 9.2.2 Application-level metadata

Synapse's native token model does not record *who* issued a token or *why*.
That matters for the invite tree (§9.4), so the application maintains a
supplementary record keyed on the token:

```
invite_record
  token_hash        — SHA-256 of the token; the raw token is never stored
  issued_by         — MXID of the inviting user, or NULL for operator-issued
  issued_at         — timestamp
  redeemed_by       — MXID of the created account, NULL until redeemed
  redeemed_at       — timestamp, NULL until redeemed
  revoked_at        — timestamp, NULL unless revoked
  note              — free text, operator use only
```

Storing only the hash means a database disclosure does not yield usable invite
codes.

## 9.3 Token format

The code is typed by a human, frequently from a screenshot or a read-aloud
message. Format decisions follow from that:

- **Length:** 12 characters, in three groups of four — `X7K2-9QMF-3BTD`.
- **Alphabet:** Crockford base32 — digits and uppercase letters, excluding
  `I`, `L`, `O` and `U`. Removes the 1/I/L and 0/O confusions, and `U` is
  excluded to avoid accidental profanity.
- **Case-insensitive on entry**, normalised to uppercase before validation.
- **Hyphens are cosmetic** — stripped before validation, so a user pasting
  without them succeeds.
- **Entropy:** 12 characters over a 32-symbol alphabet is 60 bits. Brute force
  is infeasible; rate limiting (§9.7) covers the rest.

The client applies an input filter mirroring NexLink's existing pairing-code
field, which already uses `InputFilter.AllCaps()` and a length filter — the same
pattern, a different alphabet.

## 9.4 The invite tree

Every account except the root records who invited it. This produces a tree with
the operator at the root.

**Why it matters:**

- **Abuse tracing.** An account behaving badly is rarely alone. The inviter and
  their other invitees are the first place to look.
- **Subtree revocation.** If a single user farms accounts for abuse, the whole
  subtree can be suspended in one operation rather than account by account.
- **Accountability pressure.** Users who know their invites are attributable
  invite more carefully. This is the mechanism doing most of the work.

**Privacy constraint:** the invite tree is operator-visible only. It is never
exposed in the client, never shown to other users, and constitutes personal data
under GDPR — covered by the retention policy in §32.

## 9.5 Invite quotas

Users may issue invites, subject to a quota. Without a quota the tree grows at
the rate of the most enthusiastic user, which defeats the cost control in §9.1.

Proposed starting values, to be tuned against real behaviour:

| Account age | Invites available | Rationale |
|---|---|---|
| < 7 days | 0 | A newly created account has not demonstrated anything. Blocks rapid tree expansion from a single compromised or farmed account. |
| 7–30 days | 3 | Enough to bring in immediate contacts. |
| > 30 days | 5 concurrent outstanding | Replenishes as invites are redeemed or expire. |
| Operator | Unlimited | Onboarding, support recovery, testing. |

Quotas are enforced server-side at token issue. A client-side check is a
convenience, never the control.

**Suspension behaviour:** a suspended account's unredeemed tokens are revoked
immediately. Already-redeemed accounts are not automatically suspended — that is
a moderator decision (§31), because collective punishment for an inviter's
behaviour is rarely proportionate.

## 9.6 The acceptance gate

The product owner's requirement: *"make them go through an accepting page."*
This is the screen between redeeming a valid invite and having an account.

**It exists for three reasons**, and the design serves all three:

1. **Informed consent.** The disclosures in §3.7 must be seen, not merely
   available.
2. **Legal record.** Acceptance of terms and privacy policy, with a recorded
   timestamp and document version.
3. **Friction as a filter.** A deliberate, unskippable step deters casual and
   automated signups.

### 9.6.1 Structure

Follows the pattern already proven in `BridgeSetupActivity` — disclaimer
screen, explicit checkbox, primary action disabled until ticked. That pattern
works and users of NexLink have already encountered it.

**Screen 1 — What this is**

Plain-language description. Encrypted messaging and calling, operated by an
individual, invite-only, not a company product.

**Screen 2 — What we can and cannot see**

The single most important screen, and the one most services get wrong by
overclaiming. Two columns, equal visual weight:

> **We cannot see**
> The content of your messages. The content of your calls. Your photos and
> files. Your message history.
>
> **We can see**
> Who you message, and when. How often, and roughly how much. Your IP address.
> Which devices you use. **Which emoji you react with, and to whose message.**
> **That a message arrived for you, and when** — Google's push service is told
> this, though never what the message says (§13.3.3).

Overclaiming here — "we can't see anything" — is both false and, under
consumer protection law, a misrepresentation. The honest version is also the
more trustworthy one.

**The reactions line was added 2026-09-13, answering Q44.** Matrix sends
`m.reaction` unencrypted — verified against the live homeserver (§5.5) — so the
emoji, who sent it and which event it targets are all readable by the operator.
The message being reacted to stays encrypted.

The operator's first instinct was to encrypt reactions instead. **That is not
available**: encrypting relation events breaks the server-side aggregation that
makes reactions renderable at all, and the alternatives were to drop reactions
from encrypted rooms — which is every room — or to implement a non-standard
encrypted-reaction scheme that no other client would understand and that this
project would then own forever, the precise liability §11 exists to avoid.

So the choice was between losing a feature the brief asked for by name
("reactions like hearts etc") and disclosing a metadata leak. Disclosure won,
which is the same conclusion this section reaches in general: **the honest
version is the more trustworthy one, and it is the only one that survives
someone checking.**

**Screen 3 — Your data lives on your device**

Explains the storage model, that losing the device without a recovery path
means losing history, and that transfer exists (§7.5).

**Screen 4 — Terms and acceptance**

Links to terms of service and privacy policy, each opening in full and each
requiring the user to have opened it before the checkbox enables. Three separate
checkboxes, none pre-ticked:

- [ ] I have read and accept the Terms of Service
- [ ] I have read and accept the Privacy Policy
- [ ] I understand this service is operated by an individual and provided without warranty

A single combined checkbox is legally weaker and is not used.

**Screen 5 — Age confirmation**

A date-of-birth entry, not a yes/no checkbox — a checkbox is trivially defeated
and provides no defensible record. Minimum age is set by the strictest
jurisdiction served; 16 is the safe default for EU users absent a specific
analysis. See §4 for the obligations that attach to minor users.

### 9.6.2 What is recorded

On acceptance, stored server-side against the account:

```
acceptance_record
  mxid
  accepted_at            — server timestamp
  terms_version          — semantic version of the ToS document
  privacy_version        — semantic version of the privacy policy
  age_confirmed          — boolean; DOB itself is NOT retained
  invite_token_hash      — links to the invite tree
  client_version         — app versionCode at acceptance
```

**The date of birth is not stored.** Only the boolean outcome. Retaining the
DOB creates a personal-data liability with no operational benefit once the check
has passed.

### 9.6.3 Re-acceptance

When terms or the privacy policy change materially, the gate is shown again at
next launch with a diff summary. `terms_version` makes it a simple comparison.
Users who decline are not locked out immediately — they get a read-only grace
period (proposed: 30 days) and an export path, because immediate lockout on a
terms change is both hostile and, for a service holding their conversations,
arguably unlawful.

## 9.7 Rate limiting and abuse of the invite endpoint

The redemption endpoint is the only unauthenticated write path in the service
and is therefore the primary attack surface.

| Control | Value | Purpose |
|---|---|---|
| Per-IP redemption attempts | 5 per hour | Blocks distributed guessing |
| Per-IP account creations | 3 per day | Blocks farming from a single origin |
| Global redemption failures | Alert above baseline | Detects a campaign |
| Invalid token backoff | Exponential from 1s | Makes brute force uneconomic |
| Token expiry | 14 days | Bounds the window for a leaked code |

Synapse provides per-IP registration rate limiting natively; the invite-specific
counters sit in the application layer alongside `invite_record`.

**Alerting:** a sustained rise in redemption failures means codes are being
guessed or a code has leaked publicly. This is a paging-level alert (§27), not
a dashboard curiosity.

## 9.8 Operator invite issuance

The operator needs a path that does not depend on the app being installed:

- **CLI over the admin API**, callable from the ops host, generating a token
  with a note field for record-keeping.
- **Bulk generation** for a testing cohort, with a shared note for later
  revocation as a group.
- **A support path** for a user who has lost access and needs a fresh account —
  noting that a new account does not recover the old one's history (§7.3), and
  that this must be said clearly rather than implied.

## 9.9 Open questions

- Should invites be tied to a specific person (email or phone entered by the
  inviter), or be bearer tokens? Bearer is simpler and privacy-preserving;
  targeted is harder to leak usefully. **Leaning bearer**, with a short expiry.
- Should a user be able to see who they invited? Useful socially; leaks the
  tree. **Leaning no**, but this is a product call.
- What happens to an invitee when their inviter deletes their account? Nothing,
  is the proposed answer — the tree records history, not a live dependency.
