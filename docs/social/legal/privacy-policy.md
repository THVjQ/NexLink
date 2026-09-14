# Privacy Policy — NexLink Social

**Version 0.2.0-draft** · Not yet published · §4.7, §32

*A plain-language summary is in the Transparency Note. Where the two differ this
document governs — but they are written to agree, and a discrepancy is a bug
worth reporting.*

---

## 1. Who is responsible, and the honest framing

### 1.1 The Operator
NexLink Social is operated by an individual in New South Wales, Australia. There
is no company, no data protection officer, and no privacy team. One person makes
these decisions and answers these requests.

### 1.2 Contact
The Operator, at the contact address given in the app.

### 1.3 Whether the Privacy Act applies
The Operator is an individual, not a business with a turnover that would
ordinarily bring them within the Australian Privacy Principles. **This policy is
written as though the Australian Privacy Principles apply regardless**, because
the alternative is to claim an exemption as a reason to handle your data less
carefully, and that is not a defensible position for a service whose entire
proposition is privacy.

---

## 2. The central point

Message and call **content** is end-to-end encrypted. The server stores
ciphertext and has never held the keys.

This is a property of the system's design rather than a promise about our
conduct. It means the Operator **cannot** produce your message content in
response to any request — from you, from a third party, or from a court.

It also means the Operator **can** see a good deal about the shape of your use,
and this policy sets that out in full rather than letting the encryption imply
more than it delivers.

---

## 3. What we collect and hold

### 3.1 Information you provide

| Data | Purpose | Lawful basis | Retention |
|---|---|---|---|
| Username (permanent) | Identifies your Account and routes messages | Contract | Permanent — see §7 |
| Password (hashed) | Authentication | Contract | Until deletion |
| Display name | Shows you to other users | Contract | Until deletion |
| Profile picture | Shows you to other users | Contract | Until deletion |
| Email address (optional) | Account recovery only | Consent | Until deletion or removal |
| Age confirmation (boolean) | Legal eligibility | Legal obligation | Account life + limitation period |
| Terms acceptance record | Evidence of consent | Legal obligation | Account life + limitation period |

**We do not collect your date of birth.** Only whether you confirmed you met the
minimum age.

### 3.2 Information created by using the Service

| Data | Purpose | Lawful basis | Retention |
|---|---|---|---|
| Encrypted message content | Delivery | Contract | Until deleted; subject to your retention setting |
| Encrypted media files | Delivery | Contract | Until deleted or orphan cleanup |
| Room membership | Routing | Contract | Until you leave or delete |
| Event metadata: who, when, size | Unavoidable in routing | Legitimate interest — operating the Service | Until account deletion |
| Reaction emoji, sender, target | Matrix sends reactions unencrypted | Contract | Until deleted |
| Read receipts, typing notifications | Feature operation | Contract | Transient |
| Device list and public device keys | Encryption and your own security visibility | Contract | Until the Device is removed |
| Push routing token | Notification delivery | Contract | Until Device removal or sign-out |

### 3.3 Information collected automatically

| Data | Purpose | Lawful basis | Retention |
|---|---|---|---|
| IP address | Abuse handling, diagnostics | Legitimate interest — security | **28 days**, and purged at account deletion |
| User agent / client version | Diagnostics, compatibility | Legitimate interest | 28 days |
| Connection timestamps | Security and diagnostics | Legitimate interest | 28 days |

### 3.4 Invitations

| Data | Purpose | Lawful basis | Retention |
|---|---|---|---|
| One-way hash of the Invite code | Prevent reuse | Contract | Life of the invite tree |
| Who issued it, and when | Abuse tracing, tree integrity | Legitimate interest | **Kept after deletion**, pseudonymised — see §7 |
| Whether and when it was redeemed | Tree integrity | Legitimate interest | As above |

### 3.5 Reports and moderation

| Data | Purpose | Lawful basis | Retention |
|---|---|---|---|
| Report: who, about whom, when, description | Safety | Legitimate interest — protecting users | 2 years |
| Message content in a report | Safety | **Explicit consent**, given per report | 2 years |
| Moderation actions taken | Accountability, appeals | Legitimate interest | 2 years |

**Content only ever reaches the Operator through a report where the reporting
user explicitly agreed to include it**, ticking an unticked box at the time.
There is no other path, and no operator key that can read a conversation.

### 3.6 What we never collect
- Your contacts or address book.
- Your location, beyond what an IP address implies.
- Advertising or tracking identifiers.
- Behavioural analytics.
- Your date of birth.
- Payment details — there are no payments.

---

## 4. What we cannot see, stated precisely

The Operator cannot read:
- The text of your messages.
- The audio or video of your calls.
- Your photos, videos or files.
- Your message history.

The Operator **can** see:
- Which Accounts are in a conversation, and when each message was sent.
- Approximate message sizes.
- Your IP address, for 28 days.
- Which Devices you use and when each last connected.
- **Which emoji you reacted with, and to whose message.** Matrix sends reactions
  unencrypted; the message itself stays encrypted. We disclose this because it
  is true and because being caught not disclosing it would be worse.

---

## 5. Third parties

The Service depends on three. None can read your message content.

### 5.1 Cloudflare
Carries network traffic between you and the server, which has no public address
of its own. Cloudflare sees your IP address, the times you connect, and the
volume of traffic. It cannot read encrypted content.

### 5.2 Google — Firebase Cloud Messaging
Delivers push notifications to Android devices. Google is told **that** a
notification should be delivered to your Device and when. The notification
carries only an event identifier and a room identifier — **never message
content, sender name or text.** Your Device then decrypts locally to display it.

This is metadata disclosure to a third party. Most encrypted messengers have
this property and most do not mention it. We are mentioning it.

### 5.3 LiveKit
Relays audio and video during calls. LiveKit sees that a participant joined a
call, when, and for how long. Call media is end-to-end encrypted and LiveKit
cannot hear or see it. **The conversation a call belongs to is not disclosed to
LiveKit** — the room is identified to it by a one-way hash.

### 5.4 International transfers
Cloudflare and Google operate globally, and your data may be processed outside
Australia. LiveKit's servers for this Service are in Sydney. Where data leaves
Australia it is protected in transit by encryption, and content is encrypted
end-to-end regardless of where it travels.

### 5.5 No others
We do not use analytics providers, advertising networks, crash reporting that
transmits personal data, or any service that profiles you.

---

## 6. Security

### 6.1 Measures in place
- **End-to-end encryption** of message and call content using the Matrix
  protocol (Olm and Megolm).
- **Encryption at rest** of the server database.
- **Encryption of backups** before they leave the server.
- **Invite-only registration**, so there is no open attack surface for account
  creation.
- **No federation** — the server does not exchange data with other Matrix
  servers.
- **Administrative access restricted** to the Operator, over an authenticated
  private channel, never exposed to the public internet.
- **Automated daily assertions** that the security configuration has not
  drifted, run from a separate machine.

### 6.2 The limits, stated plainly
- **Your Device is the weak point.** Anyone who can unlock your phone can read
  your messages. Set a screen lock.
- **Recipients can keep what you send.** Encryption does not control what
  happens after delivery.
- **Metadata is not protected by encryption** and could be compelled — see the
  Law Enforcement Guidelines.
- **This is a solo-operated service.** There is no 24-hour security team.

### 6.3 Data breach notification
If a breach occurs that is likely to result in serious harm, affected users will
be notified directly and promptly, and the Office of the Australian Information
Commissioner will be notified where required. A notification will state what
happened, what data was involved, what has been done, and what you should do.

Because content is end-to-end encrypted, a server breach would expose metadata
rather than messages. That distinction will be stated accurately rather than
used to minimise the event.

---

## 7. Retention, and deletion

### 7.1 Deleting your Account
You may delete your Account at any time, in the app or at
`https://nexlink.thvjq.com.au/delete/` without the app installed.

Deletion removes: the Account and all sessions, your display name and picture,
your uploaded files, your encrypted key backup, your email address if given, and
your IP and user-agent records.

### 7.2 What is kept after deletion, and why

| Kept | Why | Basis |
|---|---|---|
| Your username, tombstoned | So it can never be reissued and used to impersonate you | Legitimate interest — protecting others |
| The acceptance record | Evidence that terms were accepted and age confirmed | Legal obligation; defending legal claims |
| The Invite record — one-way hash and timestamps, pseudonymised | Abuse tracing and the integrity of the invite tree | Legitimate interest |

### 7.3 Backups — the part most policies omit
**A deleted Account persists in encrypted database backups for up to 14 days**
before those backups expire and are destroyed.

This is normal and defensible, and it is stated here because a deletion promise
that quietly contradicts a backup schedule is a promise not kept. Backups are
encrypted and are not searched or used except to restore the Service.

### 7.4 Messages in other people's possession
Deleting your Account does not delete messages you sent from the Devices of the
people you sent them to. We have no ability to reach into another person's
Device, and would not want one.

### 7.5 Full retention schedule
See the separate Data Retention Schedule document.

---

## 8. Your rights

### 8.1 What you can ask for
- **Access** — a copy of the personal information we hold about you.
- **Correction** — of anything inaccurate.
- **Erasure** — deletion of your Account, subject to §7.2.
- **Portability** — your data in a usable format.
- **Objection** — to processing based on legitimate interests.
- **Restriction** — of processing while a dispute is resolved.

### 8.2 What is self-service
- **Erasure**: in the app, or at the deletion page.
- **Portability**: the app exports your messages as JSON. **We cannot do this
  for you** — we cannot read them. This is the clearest case where encryption
  changes who can satisfy a right.
- **Correction** of your display name: in the app.

### 8.3 What to ask the Operator for
Access to server-side records, objection, restriction, or anything the app does
not expose. Contact the Operator. Expect a response within 30 days; a solo
operator may take the full period.

### 8.4 Identity verification
Before acting on a request about an Account we will verify you control it,
normally by requiring an action from within the signed-in Account. We will not
accept an email address alone as proof, because that would be a route to
someone else's data.

### 8.5 The request that cannot be satisfied
A request for the **content** of your messages held on the server cannot be
satisfied, because the server does not hold it in readable form. This is not a
refusal; there is nothing to produce. Your Device holds it, and the export tool
is how you get it.

### 8.6 Complaints
Contact the Operator first. You may also complain to the Office of the
Australian Information Commissioner (oaic.gov.au).

---

## 9. Children
The Service is not offered to anyone below the minimum age stated at sign-up.
We do not knowingly hold information about anyone below it, and an Account found
to belong to such a person is removed.

---

## 10. Automated decision-making
There is none. No profiling, no automated moderation, no algorithmic ranking. A
person makes every moderation decision.

---

## 11. Cookies and local storage
The web pages — Element Web and the deletion page — use browser local storage
for session and settings only. There are no tracking cookies, no third-party
cookies and no advertising identifiers.

---

## 12. Changes to this policy
This policy is versioned. A material change requires re-acceptance in the app,
so you cannot be moved onto different terms silently. The version history is
available on request.
