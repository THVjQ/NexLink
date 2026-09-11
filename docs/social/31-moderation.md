# §31 — Moderation and abuse handling

> **Confidence:** `DURABLE`
> This chapter contains the product's hardest unresolved tension. §31.4 states
> it rather than resolving it, because it is not resolvable by design choices.

---

## 31.1 The position

**The operator cannot read messages.** Not "does not" — cannot. There is no
mechanism, and building one would violate §2.8's first invariant and falsify
§9.6.1's acceptance screen.

Everything in this chapter follows from that. Moderation here is not content
moderation in the sense Play's UGC policy (§4.2.2) imagines, and pretending
otherwise in a Play declaration or a terms of service would be a
misrepresentation with real consequences.

What the operator *can* do is act on **accounts**, using reports from users who
can read the content, and using metadata. That is a genuinely weaker tool. It is
also the only honest one, and §31.3 shows it is not as weak as it first appears.

---

## 31.2 What abuse looks like here

An invite-only service (§2.5) has a very different abuse profile from an open
one, and the differences are the point of D4.

| Abuse | Likelihood | Why |
|---|---|---|
| Spam accounts | **Very low** | No automated registration path exists (§9.1) |
| Harassment between users | **Moderate** | The realistic case. People invite people they know, and knowing someone is not protection. |
| Unwanted content sent to a user | Moderate | Same |
| Illegal content | Low but non-zero, and the severest | §31.4 |
| Account farming for abuse | Low | Quotas (§9.5) plus the tree (§9.4) make it traceable and slow |
| Resource abuse | Low | §25.9 notes there is no per-user media quota |

**Harassment is the case to design for.** Not because it is the worst, but
because it is the one that will actually happen, and because the tools for it
are entirely client-side.

---

## 31.3 The tools that exist

### 31.3.1 Client-side, and this is where most of it happens

| Tool | Effect | Needs the operator? |
|---|---|---|
| **Block a user** | No messages, no invites, no calls from them | No |
| **Leave a conversation** | | No |
| **Report a user** | Sends a report to the operator, with **the user's consent to include content** | Yes, to action |
| Remove a participant | In a group the user administers | No |

**Blocking is the primary control and it requires nothing from the operator.**
It is instant, it is under the affected user's control, and it works at 3 a.m.
It must therefore be *good*: one tap from a message, from a profile, and from
the conversation list; no confirmation maze; and it must actually stop calls and
invites, not just messages.

A report that the operator will read the next working day is a much worse
experience than a block that works now. Build blocking first and build it
properly.

### 31.3.2 The report flow — the one place content becomes readable

The user reporting is the one who can decrypt. So:

1. User selects a message or a profile and taps Report.
2. **Explicit, unticked consent:** *"Include the reported messages in this
   report. The operator will be able to read them."*
3. If consented, the client attaches the plaintext of the selected messages and
   a short surrounding window, **encrypted to the operator's key** rather than
   sent in clear.
4. If not consented, the report carries only the accused MXID and the reporter's
   description.

This is the only path by which content reaches the operator, and it is
user-initiated, consent-gated and narrow. It does not create a capability that
exists at any other time — there is no operator key that can read the room, only
one that can read what a user chose to send.

**Reports without content are still actionable.** Several independent reports
against one account is a signal on its own.

### 31.3.3 Operator-side

| Action | Reversible | §29 |
|---|---|---|
| Warn | — | |
| **Suspend** | **Yes** | §29.2 |
| Revoke outstanding invites | Effectively | §29.2 |
| Deactivate | **No** (§7.6) | |
| Suspend an invite subtree | Yes | §9.4 |
| Report to authorities | — | §31.4 |

**Default to suspension.** It is reversible, it stops the behaviour immediately,
and it leaves room to be wrong. Deactivation destroys an account and, for a
no-email user, everything they had.

### 31.3.4 The invite tree is the distinctive tool

§9.4's tree means every account has an accountable origin. In practice:

- A report against an account immediately shows who vouched for them.
- **Contacting the inviter is often the fastest resolution** and is available to
  no open service. It is a social control, and social controls work at this
  scale in a way they do not at a million users.
- A subtree can be suspended wholesale if one person is farming accounts.
- Users who know their invites are attributable invite more carefully. §9.4 is
  explicit that this deterrent is doing most of the work.

The constraint from §9.4 holds: **the tree is operator-visible only.** It is
never shown in the client and never to another user. It is personal data (§32).

---

## 31.4 The tension, stated plainly

§4.4.3 flagged it; this is where it has to be faced.

**The obligations:** UK Online Safety Act duties around illegal content; the EU
DSA's notice-and-action requirements; Play's UGC policy requiring a mechanism to
moderate objectionable content (§4.2.2); child-safety obligations (§4.5) that
are not negotiable in any jurisdiction.

**The capability:** the operator cannot see content. Cannot scan, cannot
proactively detect, cannot hash-match, cannot respond to a takedown by removing
a message because there is no readable message to remove.

**This is not a gap to be closed.** It is the same position every end-to-end
encrypted messenger occupies, and it is the direct consequence of D2 and §3.5,
which were chosen deliberately. Closing it means removing end-to-end encryption.

What is actually done:

1. **Do not overclaim in any filing.** The Play declaration, the terms of
   service and any regulatory correspondence say what the service can do: user
   reporting, account-level action, and the encryption posture stated openly.
   A declaration promising content moderation that is impossible is worse than
   an honest one describing a narrower capability.
2. **Make reporting genuinely good** (§31.3.2). It is the mechanism that exists,
   so it must not be a token gesture.
3. **Act decisively on reports**, with short stated timelines (§31.5). Speed of
   account-level action is the one dimension on which this service can exceed
   what a large platform delivers.
4. **The invite tree gives accountability** that an open service lacks (§31.3.4),
   and it is worth stating in any regulatory conversation — a closed, traceable
   user base is a materially different proposition from an anonymous public one.
5. **Child safety:** any credible report goes to the relevant authority
   immediately. The operator cannot detect it proactively and says so.

### 31.4.1 The decision that follows

Because the obligations scale with jurisdiction and user count, and the
capability does not scale at all:

**Keep the service small and closed.** D4 (§2.5) was chosen for spam and cost
control. It is also the primary regulatory control, and §28.7's observation
applies — moderation capacity, not infrastructure, is the real ceiling on this
product.

If the service ever contemplates open registration, this chapter is the one that
must be rewritten first, and it will not be rewritten favourably.

---

## 31.5 Timelines

Published in the terms of service (§4.7), so they must be achievable by one
person:

| Report type | Acknowledged | Actioned |
|---|---|---|
| Child safety | Immediately | Immediately, plus referral |
| Credible threat of violence | 24 h | 24 h |
| Harassment | 3 working days | 7 working days |
| Spam / unwanted contact | 5 working days | 10 working days |
| Other | 7 working days | Case by case |

**Do not publish a timeline that cannot be met.** A 24-hour harassment SLA for a
solo operator with a day job is a commitment that will be broken and then cited.
The numbers above are deliberately unimpressive and deliberately keepable.

The child-safety row is the exception and is not negotiable regardless of
convenience.

---

## 31.6 Appeals

Every suspension and deactivation carries a route to appeal — an email address
that a human reads, named in the notice.

| | |
|---|---|
| Who decides | The operator. There is nobody else, and the terms say so. |
| Timeline | 14 days |
| Record | Decision and reasoning, retained |
| Suspension reversed | Account restored intact |
| Deactivation reversed | **Cannot be.** §7.6. This is why §31.3.3 defaults to suspension. |

The DSA expects internal complaint handling for services in scope. A one-person
process is thin, and it is what exists; document it honestly rather than
describing a review board that does not exist.

---

## 31.7 What is deliberately not built

| Not built | Why |
|---|---|
| Server-side content scanning | Impossible (§31.1) and would require breaking encryption |
| Automated content classification | Same |
| Keyword filtering | Same |
| A moderator role for non-operators | Giving a second person account-level power over other people's accounts is a larger trust decision than it looks. Revisit if volume demands it. |
| Shadow-banning | Dishonest. A suspended user is told they are suspended. |
| Message deletion by the operator | The operator cannot read messages and should not be able to delete them. Redaction is the sender's and the room admin's. |

---

## 31.8 Open questions

- **Does Play's UGC policy accept an E2EE service with reporting and
  account-level action?** Other encrypted messengers ship on Play, so the answer
  is evidently yes in practice. **Confirm the exact declaration wording before
  submission** (§35) — this is the single most likely cause of a rejection.
- **What is the operator's obligation if a user reports illegal content and the
  operator cannot verify it?** Referral, presumably, with what evidence exists.
  Needs legal input, and it needs it before launch rather than during the first
  case.
- **Should reports be possible from the web client?** It is a full client
  (§20.5), so yes, for parity. Element Web's built-in reporting sends to the
  homeserver's admin; verify where that lands and that it is read.
- **At what volume does §31.5 break?** §28.7. Probably lower than expected, and
  it is the number that decides how large this service can responsibly get.
