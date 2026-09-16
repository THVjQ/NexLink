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

**Status 2026-09-15: none of this is built, and that is not a gap — it is a
feature that has no caller.** Invites are operator-only (§9.8): the `invite`
CLI mints a Synapse registration token and records it in the tree. There is no
path by which a *user* issues an invite, so there is nothing for a quota to
limit. The app has an invite *redemption* flow and no invite *issuing* flow.

**Decided 2026-09-15: the private launch runs on operator-issued invites, and
phase 6's quota row is dropped.**

For 10–20 people who know it is early, the operator issuing every invite by hand
is not a limitation, it is the control: §9.1's cost argument and §9.7's abuse
argument are both strongest when exactly one person decides who gets in. Quotas
exist to make *delegated* invitation safe, and nothing is being delegated yet.

So §9.5 stays written, unbuilt, and honest about it. It is the specification for
the day user-issued invites are built — with that table as the starting values,
tuned against behaviour observed during the private launch rather than guessed
at now. Building it before there are users to observe would be tuning numbers
against an imagination.

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

## 9.6.4 The open-the-document-first rule, removed 2026-09-16

§9.6.1 required each policy document to be **opened** before its checkbox would
enable. The operator removed that requirement. Recorded here with the trade
stated, because a consent rule that quietly disappears is exactly the kind of
change a later reader must be able to find.

**What it cost.** "I have read and accept" now records an acceptance that the
product did not observe the user in a position to make. That is weaker evidence
of informed consent than the original design, and if the acceptance record is
ever relied on, this is the sentence that matters.

**What it was actually enforcing.** Nothing readable. `openPolicy` is still a
stub that shows a toast — *"document not yet written (§4.7, phase 5)"* — so the
rule required the user to open something that does not exist. It was gating
consent on a gesture, not on reading. The honest fix is to ship the documents
(they are drafted, `docs/social/legal/`) and then re-impose the rule; until
then the requirement was theatre with a real cost, which is how it managed to
hide a crash for four days.

**Re-imposing it is one line** in `AcceptanceGateState.setTermsChecked` /
`setPrivacyChecked`, and `termsOpened` / `privacyOpened` are still tracked for
exactly that reason. `AcceptanceGateStateTest` pins the current behaviour rather
than having been deleted, so the difference between a decision and a regression
stays visible.

### 9.6.4a The crash it was hiding

**Account creation was impossible, and nothing said so.** Found 2026-09-16 while
creating the operator's own account on a handset.

`AcceptanceGateActivity.renderTerms` declared its Continue button as a
`lateinit var` above the three checkboxes and assigned it below them. Every
checkbox listener touched it. That is safe while a box is only toggled by a
finger — but `Ui.checkbox` installs its change listener *before* the caller's
`.apply { isChecked = … }` runs, so **restoring a ticked box during a re-render
fires the listener**, and a re-render happens whenever the screen is rebuilt:
tapping either "Read the…" link, for instance. The process died with
`UninitializedPropertyAccessException`.

From the user's side there was no crash dialog and no message — the gate simply
vanished and an empty account form came back, which reads as "it went back a
step". The invite token was left `pending` with no account, which is §2.8's
third invariant behaving perfectly and is also why nothing looked broken on the
server: a token that reads `pending: 1, completed: 0` is indistinguishable from
someone who changed their mind.

Two things follow, and both are now done:

- The fix is **ordering, not a null check**: the button is built before the
  listeners that close over it.
- The regression test has to build the views, because the bug lived in the
  interaction between a view builder and an activity and no test of the state
  machine could have reached it. `AcceptanceGateTermsScreenTest` drives the real
  screen under Robolectric, and was confirmed to fail against the unfixed code.

---

## 9.5.1 User-issued invites — built 2026-09-16

§9.5 stood written and unbuilt on the argument that nothing was being delegated
yet. The operator asked for delegation, so it is built.

**Quota: none.** The operator chose unlimited invites over the earned quotas in
the table above. The cost is stated plainly rather than buried: with no cap, a
single compromised or careless account becomes open registration, and §9.1's
cost argument stops holding at that moment. The mitigations that remain are the
record (below) and the operator's ability to revoke tokens and suspend an
account — not prevention. §9.5's table stays as the specification for the day
that trade stops being acceptable; re-imposing it is a config value, not a
rewrite.

There *is* a burst guard — 30 per hour per user — which is **not** a quota. It
exists so a stuck client or a loop cannot fill `registration_tokens`; a person
inviting people by hand will never reach it.

### Why a Synapse module rather than a service

Minting a registration token needs Synapse **admin** credentials. A phone can
never hold those: an admin token reads every room's metadata and can deactivate
any account. So the mint has to happen somewhere the user cannot reach.

The obvious shape is a small service beside Synapse. The deciding constraint was
routing: public routing here is **per-hostname in the Cloudflare dashboard**
(§26.5.2), so a new service means a new hostname, a new certificate path, and a
new piece of public attack surface whose whole job is creating accounts. A
module is served by Synapse itself on a host already routed and already TLS'd,
and `get_user_by_req` authenticates the caller through the same code path as
every other endpoint. Nothing new is exposed and no authentication is
re-implemented.

`POST /_matrix/nexlink/v1/invite` → `{code, formatted, expires_at}`;
`GET` lists the caller's own; `DELETE ?code=` revokes an unredeemed one.

### 9.5.2 The path took three attempts, and routing decided it

Worth writing down, because the first two both *looked* right:

1. **`/_synapse/client/nexlink/invite`** — the natural home for a module's
   endpoint, and it works perfectly on localhost. From a phone it returns
   **404**: the Cloudflare tunnel routes `/_matrix/` to Synapse and sends
   everything else to a catch-all that lands on Element Web
   (`infra/runbooks/cloudflare-tunnel-routes.md`), so the request never arrived.
   The same trap `DeviceManager` documents for a trailing slash. Fixing it in
   the dashboard was possible but means a route nobody diffs — §2.1's drift
   argument — and a step the operator has to remember on every rebuild.

2. **`/_matrix/client/unstable/com.thvjq.nexlink/invite`** — the *correct*
   namespace for a custom Client-Server API. It 404s **even on localhost**:
   Synapse's own `JsonResource` owns `/_matrix/client` and answers for every
   child of it, so a module cannot nest inside. `register_web_resource` still
   logs `Attaching …`, which means the log line is not evidence of anything.

3. **`/_matrix/nexlink/v1/invite`** — a sibling of `client`, `federation`,
   `media` and `key`, which Synapse does not claim, inside the one prefix the
   tunnel already sends here. Verified 401 unauthenticated from the public
   internet and end-to-end from the handset.

**Verify by request, never by reading the config** (§26.5.2). Every one of those
three was "obviously correct" until it was asked.

### The coupling, stated

`registration_tokens` is an **internal** Synapse table, not a public interface.
The module writes to it directly rather than calling the admin API, so that no
admin token has to exist in configuration for the module to leak. The table has
been stable since Synapse 1.35 and its shape was verified against 1.160 before
deploying — but an upstream schema change will break this, and that is the price
of not keeping an admin credential on disk.

### The record

Every issuance appends `{issuer, code_sha256, issued_at, expires_at}` to
`/data/nexlink-invites.jsonl`. **The raw code is never written** — §29.1 already
requires that of the operator CLI, and a file of live invite codes is a file of
account-creation credentials. With no quota in force, this record *is* the
accountability: it is how "where did this account come from" gets answered, and
how one person's invites get revoked as a group.

### Deployment

Two changes on Willard, both reversible:

1. `PYTHONPATH=/data` on the Synapse service (an `app.update`, done
   2026-09-16 — inert on its own).
2. A `modules:` block in `homeserver.yaml` naming
   `nexlink_invites.NexLinkInvites`, plus the module file at `/data`.

**Both done 2026-09-16; the feature is live.** Verified end to end: a code
created from the phone appears in `registration_tokens`, lists back under
"Waiting to be used", and revoking it from the phone removes the row.

Disabling is deleting the block and restarting. **Codes already issued keep
working** — they are ordinary registration tokens, and nothing about redemption
goes through the module.

---

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
