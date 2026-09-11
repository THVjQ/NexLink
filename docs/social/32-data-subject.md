# §32 — Data subject processes

> **Confidence:** `DURABLE`
> Obligations are law. Procedures are what makes them achievable by one person.

---

## 32.1 Controller status and what it means

§4.3.1 establishes it: the operator is a **data controller** for personal data
processed by this service. Not a processor — there is no other party whose
instructions are being followed.

The obligations are real and they attach to an individual. They are also
**materially lighter than they would otherwise be**, because the architecture
means most of the data a messaging service would hold does not exist here in
readable form. That is worth understanding precisely, because it is the
difference between a manageable compliance burden and an unmanageable one.

---

## 32.2 What personal data actually exists

The honest inventory. This table is the foundation of every procedure below and
of every answer given to a regulator or a user.

| Data | Held | Readable by operator | Where |
|---|---|---|---|
| Username / MXID | Yes | **Yes** | Postgres |
| Password hash | Yes | Hash only | Postgres |
| Email address | **Only if the user provided one** (§7.1) | Yes | Postgres |
| Display name, avatar | Yes | **Yes** | Postgres, media store |
| Device list, device names | Yes | **Yes** | Postgres |
| IP addresses, user agents | Yes, **28 days** (§25.6) | **Yes** | Postgres |
| Social graph — who, when, how often | Yes | **Yes** | Postgres, room state |
| Message content | Yes | **No** — ciphertext | Postgres |
| Media files | Yes | **No** — ciphertext | Media store |
| Call content | **No** | No | Nowhere — never stored |
| Encrypted key backup | Yes | **No** | Postgres |
| **Invite tree** (`invite_record`) | Yes | **Yes** | Invite service |
| **Acceptance record** (`acceptance_record`) | Yes | **Yes** | Invite service |
| Date of birth | **No** — only the boolean outcome | — | §9.6.2 |
| Local message history | **On the user's device only** | No | §12 |

Three observations that shape everything else:

- **The readable column is short.** Metadata and account details, essentially.
- **The invite tree is the most sensitive readable thing here** — it is a map of
  real social connections between identified people, and it exists because §9.4
  needs it for abuse tracing. It is personal data for *two* people per row: the
  inviter and the invitee.
- **Date of birth is deliberately not retained** (§9.6.2). Keeping it would
  create a liability with no operational benefit once the check has passed. This
  is data minimisation done correctly and it is worth pointing at.

---

## 32.3 Deletion

§1.2 requires deletion in-app **and** from a public web page — the latter is a
Play requirement (§4.2.1) and it must work without the app installed.

### 32.3.1 What deletion does

```
1. Deactivate the account via the admin API
     → access tokens invalidated, all devices signed out
     → account marked deactivated, username NOT released (§6.5)
2. Erase profile: display name, avatar
3. Purge the user's uploaded media          ← §25.7, easy to miss
4. Delete the encrypted key backup
5. Delete the email address, if one was given
6. Purge IP and user-agent logs for the account
7. Retain, and say so:
     - the MXID itself, tombstoned, so it is never reissued (§6.5)
     - acceptance_record — legal record of consent (§32.3.2)
     - invite_record rows — see §32.3.3
8. Confirm to the user, in writing, what was deleted and what was kept
```

**Step 3 is the one that gets forgotten**, and forgetting it means deletion is
incomplete and the erasure obligation is unmet. §29.5 includes a cross-check:
if routine media cleanup finds significant media belonging to deactivated
accounts, **the deletion procedure is broken** and that is a compliance bug, not
housekeeping.

### 32.3.2 What is kept, and the lawful basis

| Kept | Why | Basis |
|---|---|---|
| Tombstoned MXID | Prevents reissue and impersonation of a former user (§6.5, §6.7) | Legitimate interest — protecting other users |
| `acceptance_record` | The record that terms were accepted and age confirmed (§9.6.2) | Legal obligation / establishing and defending legal claims |
| `invite_record` (hashed token, timestamps) | Abuse tracing and the integrity of the tree (§9.4) | Legitimate interest — §32.3.3 |

**These must be disclosed in the privacy policy**, not discovered by a user
after deletion. A deletion that silently retains records is the kind of thing
that turns a routine complaint into an investigation.

### 32.3.3 The invite tree after deletion — a genuine problem

§9.9 asks what happens to an invitee when their inviter deletes their account,
and proposes: nothing, because the tree records history rather than a live
dependency.

That is operationally correct and **it is in tension with erasure**. A deleted
user's row still names them as the inviter of accounts that still exist. Their
MXID is still in the database, in a table that exists to identify people.

Proposed resolution:

- On deletion, **pseudonymise** the inviter's MXID in `invite_record` rather
  than deleting the row: replace it with a stable, salted hash.
- The tree's *structure* survives, so subtree revocation (§9.4) still works.
- The link back to a named individual does not.
- Document it in the privacy policy.

This is a defensible middle position. It should be confirmed with legal input
before launch, and it is listed in §37.

### 32.3.4 What deletion cannot do

Stated to the user at the point of deletion, plainly:

- **Messages already delivered to other people's devices remain on those
  devices.** Deletion removes the account, not what was said to others.
- Other participants' copies of shared media remain.
- The user's own local history is on their device and is theirs to delete.
- **Deletion is irreversible** (§7.6). There is no grace period and no restore.

---

## 32.4 Access requests

A user asking for a copy of their personal data. One month to respond, GDPR.

What is provided:

| Provided | Note |
|---|---|
| Account details | Username, creation date, email if given |
| Device list | With first-seen and last-seen |
| IP log | The 28 days that exist (§25.6) |
| Room membership | Which conversations, with whom, joined when |
| `acceptance_record` | Terms version, timestamp |
| Invite data | Who invited them; **not** who they invited (§9.4 — the tree is not exposed to users, and the invitees are third parties with their own privacy interest) |
| Encrypted content | Offered, but it is ciphertext and useless without their keys. Say so. |

**What is not provided:** decrypted message content, because the operator cannot
produce it. The response should say this explicitly — *"we cannot read your
messages and therefore cannot provide them; your own device holds them and the
app can export them (§12.7.2)"* — and point at the client-side export, which is
the answer the user actually wants.

The right split: **the operator answers for server-side data; the app answers
for the user's content.** §12.7.2's export is a data-protection feature, not
only a convenience.

### 32.4.1 Identity verification

Before answering, confirm the requester controls the account — a challenge sent
to the account, answered from a signed-in device.

**The obvious failure mode is disclosing one user's data to an attacker who asks
nicely.** For a no-email account (§7.1) there is no out-of-band channel at all,
so the in-account challenge is the only mechanism. Do not accept an email from
an address that merely claims to be the user.

---

## 32.5 The request that cannot be satisfied

A user who has lost their phone and their recovery key asks for their history
back.

**There is no mechanism. The answer is no.** §7.3 is built so this is
understood *before* it happens, and §29.9 notes the runbook here is a support
script rather than a technical procedure.

What the support response must do:

1. Say clearly that the history cannot be recovered, and why — the operator
   never had the keys.
2. Not imply that a different request, or more persistence, would produce a
   different answer.
3. Offer what does exist: a new account (§9.8), and the note that a new account
   does not recover the old one's history.
4. Not blame the user.

Getting this response right matters more than most engineering in this document,
because it is the moment the product's central trade-off lands on a real person.
The honest version — *this is the cost of the thing that also means nobody can
read your messages* — is the only one that holds up.

---

## 32.6 Rectification, portability, objection

| Right | How |
|---|---|
| Rectification | Display name and avatar are user-editable. **The username is not** (§6.5) — immutability is an anti-impersonation control (§6.7), and that must be disclosed at signup, since a user cannot correct it later. |
| Portability | Client-side export (§12.7.2). Matrix is an open protocol, so the export is genuinely portable rather than nominally so. |
| Objection / restriction | Account suspension at user request, short of deletion. Worth offering — a user who wants to stop without destroying their history has no other option. |

---

## 32.7 Retention summary

| Data | Retained |
|---|---|
| Account | Until deleted |
| IP / user-agent logs | 28 days (§25.6) |
| Encrypted events | Indefinitely (§25.6) |
| Media | Until account deletion or orphan cleanup (§25.7) |
| `acceptance_record` | Life of account + limitation period |
| `invite_record` | Life of the tree, pseudonymised on deletion (§32.3.3) |
| Redaction originals | 7 days |
| Database backups | 14 days (§23.4.3) |
| Reports and moderation records | 2 years (§31) |

**Backups are the retention hole everyone forgets.** A deleted account persists
in dumps for up to 14 days. That is normal, defensible, and must be *stated* in
the privacy policy rather than left as an implicit inconsistency between a
deletion promise and a backup schedule.

---

## 32.8 Open questions

- **Pseudonymising the inviter MXID (§32.3.3)** — needs legal confirmation.
- **Which jurisdictions actually apply?** §2.7 leaves minimum age open pending
  this, and it depends on where invites go. Invite-only means this is
  *knowable*, which is unusual and worth exploiting: the operator can decide to
  serve specific jurisdictions and decline others.
- **Is a formal privacy notice at the acceptance gate sufficient**, or does the
  service need a designated contact point and a public data-protection page?
  §4.3.2 leans toward yes for a public-facing service.
- **What happens to a group conversation when its last member deletes?**
  Orphaned room state, `forgotten_room_retention_period` at 28 days (§22.4).
  Verify it actually purges.
