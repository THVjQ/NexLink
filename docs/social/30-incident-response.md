# §30 — Incident response

> **Confidence:** `DURABLE`
> The notification obligations are law. The rest is procedure written before it
> is needed, which is the only time it can be written calmly.

---

## 30.1 Scope and the honest constraint

An incident here is any event affecting the confidentiality, integrity or
availability of the service or its users' data.

**There is one operator.** No rotation, no escalation path, no second pair of
eyes at 3 a.m. Every procedure in this chapter is written for that reality, and
where a control genuinely requires a second person, it says so rather than
pretending.

The most useful consequence: **procedures are written down in advance**, because
the person executing them will be tired, alone, and reading their own notes.

---

## 30.2 Severity

| Sev | Meaning | Examples | Response |
|---|---|---|---|
| **1** | User data confidentiality at risk | Server compromise, key material disclosure, a bug sending plaintext | Immediately. Take the service down if in doubt (§29.8). |
| **2** | Service down or badly degraded | Homeserver down, database corrupt, no message delivery | Within hours |
| **3** | Partial degradation | Calls failing for some users, push unreliable, media upload broken | Next working day |
| **4** | Cosmetic or single-user | One user's device misbehaving | Best effort |

**Sev 1 has a bias: take it down first, investigate second.** Availability is
recoverable; a confidentiality breach is not. §29.8 exists for this and users'
local history is intact throughout (§12.2), so the cost of being wrong about a
Sev 1 is a few hours of outage.

---

## 30.3 Server compromise

The scenario: unauthorised access to Willard or to the Synapse container.

### 30.3.1 What an attacker gets, and does not

**Does not get:** message content, call content, media content. All of it is
encrypted with keys the server has never held (§5.3.3, §25.1). This is the
entire payoff of the encryption posture and it is worth stating first, because
it changes the shape of the response.

**Does get:** the social graph — who talks to whom, when, how often. IP
addresses (up to 28 days, §25.6). Device lists. Password hashes. Access tokens,
which means the ability to *impersonate the server to clients*. And the
`signing.key`.

**And critically:** the ability to mount the attack §8.3.2 is designed against —
inserting a device into a user's account and hoping nobody checks. Cross-signing
is what defeats it, and it only defeats it if users actually see the "new device
added" warning.

### 30.3.2 Procedure

1. **Isolate.** Stop Synapse (§29.8). Do not stop PostgreSQL — it holds the
   evidence.
2. **Preserve.** ZFS snapshot immediately, before anything else changes:
   ```bash
   sudo midclt call pool.snapshot.create \
     '{"dataset":"Pool1-MAIN/social","name":"incident-<ts>","recursive":true}'
   ```
   The 15-minute automatic snapshots (§21.5.1) mean there is already a
   pre-incident image on both pools. **Find its name before the 2-week retention
   expires it.**
3. **Scope.** What was reachable? Willard also hosts a Nextcloud with real user
   data, a live POS system and customer websites (§21.8) — a compromise of the
   host is not a Social incident, it is a Willard incident, and the blast radius
   is everything on the box.
4. **Rotate.** `macaroon_secret_key` (signs out every user everywhere — do it),
   database password, LiveKit API key and secret, admin access tokens, Cloudflare
   tunnel token.
5. **`signing.key`.** Rotating it invalidates every device signature on the
   server and forces every user to re-verify every device (§21.6). It is close
   to a service restart from zero. **Rotate only if there is reason to believe
   it was read** — and note that an attacker with filesystem access almost
   certainly read it.
6. **Notify.** §30.6. The clock has already started.
7. **Recover.** Rebuild from a known-good state, not by cleaning the running
   one.
8. **Write it up.** §30.7.

### 30.3.3 Telling users what actually happened

The disclosure must be specific, because a vague one is worse than useless to
someone deciding whether to trust the service:

> Someone gained access to the server. **They could not read your messages,
> calls or files** — those are encrypted with keys the server never has. They
> could see who you have been messaging and when, your IP address, and the list
> of your devices. We have signed everyone out. **Check your device list and
> remove anything you do not recognise.**

The last sentence is the actionable one and belongs in bold in the real notice
too.

---

## 30.4 Key compromise

### 30.4.1 A user's device is lost or stolen

Not an operator incident — a user one, with operator support. §8.6 is the
mechanism.

1. User signs in elsewhere (or the operator assists) and removes the device.
2. Removal invalidates that device's access token and excludes it from future
   Megolm sessions.
3. **Messages the device already received remain readable on it** if the thief
   can unlock the phone. There is no remote wipe and there cannot be.
4. Advise a password change, which invalidates all tokens.

Say point 3 plainly. A user who believes remote removal retroactively protects
old messages has been misled about something that matters.

### 30.4.2 A user's recovery key is disclosed

The recovery key decrypts the key backup, which decrypts history (§7.4).

1. Generate a **new** recovery key — the old one cannot be revoked, only
   superseded.
2. Re-upload the key backup under the new key.
3. Anyone holding the old key retains access to the old backup version. **This
   cannot be undone.**

Point 3 is the reason §7.3's warnings are as heavy as they are.

### 30.4.3 The server signing key

§30.3.2 step 5. Rotation is a near-total reset. Treat as Sev 1 and expect the
recovery to be measured in days of user friction, not hours of downtime.

---

## 30.5 Legal compulsion

§3.3's adversary A5. Procedure, not legal advice:

1. **Do not act immediately.** Verify the request is genuine and properly
   served. Note the date received.
2. **Get legal advice** before responding. This is a real cost and it is the
   reason §4.7's terms of service must be written before launch, not after.
3. Determine what is actually being asked for and what exists:

| Asked for | Exists? |
|---|---|
| Message content | **No.** Not held in readable form, by design. |
| Call content | **No.** |
| Media files | Ciphertext only, keys not held |
| Who messaged whom, when | **Yes.** Room membership and event metadata. |
| IP addresses | **Yes**, up to 28 days (§25.6) |
| Account details | **Yes.** Username, creation date, devices. No email if the no-email path was taken (§7). |
| The invite tree | **Yes**, and it names the inviter (§9.4) |

4. Comply only with what is lawfully required, and only to the extent required.
5. Notify the affected user **unless legally prohibited** from doing so.
6. Record it for transparency reporting (§30.8).

**The invite tree row deserves attention.** §9.4 records it as operator-visible
personal data for abuse tracing. It is also a map of real-world social
connections that is fully readable and is exactly the sort of thing a request
would ask for. That is a cost of D4 and it is worth having considered before
being asked.

---

## 30.6 Data breach notification

Legal obligations, not procedure. §4.3 establishes controller status.

| Regime | Trigger | Deadline |
|---|---|---|
| **GDPR** (EU users) | Risk to rights and freedoms | **72 hours** to the supervisory authority, from *becoming aware*. Without undue delay to users if high risk. |
| **Australian Privacy Act** | Eligible data breach, likely serious harm | Assess within **30 days**; notify OAIC and affected individuals promptly |
| **UK GDPR** | As GDPR | 72 hours to the ICO |

Practical consequences:

- **72 hours starts when you become aware, not when you finish investigating.**
  A partial notification on time beats a complete one late.
- Awareness is a fact to be recorded. **Write down the timestamp** of the first
  moment something looked wrong.
- Which regimes apply depends on where users are, which depends on where invites
  went (§4, §9). An invite-only service with a known user base can actually
  answer this question — an advantage of D4 that is easy to overlook.
- A breach of *encrypted* data where the keys were not compromised is materially
  different in law and in fact. §30.3.1's distinction is not spin; it is the
  substance of the assessment.

**A notification template is written in advance and kept in `infra/runbooks/`.**
Drafting one under a 72-hour clock, alone, is how deadlines are missed.

---

## 30.7 After the incident

Within a week:

1. Timeline: what happened, when, when it was noticed, when it was resolved.
2. Root cause, actual not proximate.
3. What worked — including "the encryption meant content was not exposed", if
   that is what happened. Controls that worked should be known to have worked.
4. What did not.
5. Actions, each with an owner and a date. There is one owner.
6. **Whether a runbook would have helped, and if so, write it** (§29).

Even for a single operator. Especially for a single operator — the write-up is
the only mechanism by which the person handling the next incident, eighteen
months from now, learns anything from this one.

---

## 30.8 Transparency reporting

Not currently required at this scale. Worth starting anyway, because a report
that begins after the first request looks like a response to it.

Annually: number of legal requests received, number complied with, number of
accounts affected, number of suspensions and deactivations (§31). Aggregate
numbers, no identifying details.

---

## 30.9 Open questions

- **Who is the legal contact**, and is there a relationship in place before it
  is needed? §30.5 step 2 assumes one exists.
- **What is the acceptable overnight outage?** §27.8 asks it from the monitoring
  side. There is one operator who sleeps. Users should be told what to expect,
  honestly, at the acceptance gate (§9.6.1).
- **Is a canary warrant worth it?** Small service, real signalling value, and it
  becomes a commitment that must be maintained forever. Leaning no.
- **Does the operator's home insurance or any other arrangement matter** if
  Willard is seized or destroyed? §23.5's offsite gap is the technical half of
  this question; there may be a non-technical half.
