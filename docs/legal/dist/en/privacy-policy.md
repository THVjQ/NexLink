<!-- Assembled by tools/legal/build-legal.py. Edit docs/legal/en/privacy-policy.md
     or docs/legal/definitions.en.md, never this file. -->

# Privacy Policy — NexLink

**Version 0.3.0-draft** · Applies to all NexLink Products · Not yet published · §4.7, §32

> **Draft.** Not reviewed by a lawyer. See `docs/legal/README.md`.

*A plain-language summary is in the Transparency Note. Where the two differ this
document governs — but they are written to agree, and a discrepancy is a bug
worth reporting.*

---

## 1. Who is responsible, and the honest framing

### 1.1 The Operator
The NexLink Products are published by an individual in New South Wales,
Australia. There is no company, no data protection officer, and no privacy team.
One person makes these decisions and answers these requests.

### 1.2 Contact
The Operator, at the address published on the **Contact Page**: `https://thvjq.com.au/nexlink/contact`

The address is published there rather than written into this document so that it
stays current. An address baked into an installed application cannot be
corrected on a device that never updates.

### 1.3 Which privacy law applies to you
The Service is offered only in Switzerland and Australia (Terms §3.7), and this
policy is written to satisfy both.

- **If you are in Switzerland**, the *Federal Act on Data Protection* (FADP)
  applies. Your supervisory authority is the **FDPIC**.
- **If you are in Australia**, the *Privacy Act 1988* (Cth) and the Australian
  Privacy Principles apply. Your supervisory authority is the **OAIC**.

**Where the two laws differ, this policy applies the stricter rule to everyone.**
Operating two standards would mean quietly giving some users less, and the
difference is not large enough to be worth that.

The Operator is an individual, not a business with a turnover that would
ordinarily bring them within the Australian Privacy Principles. **This policy is
written as though they apply regardless**, because the alternative is to claim
an exemption as a reason to handle your data less carefully, and that is not a
defensible position for a service whose entire proposition is privacy.

### 1.4 Definitions
Defined terms are set out in **Appendix A** at the end of this document.

---

## 2. The central point

Message and call **content** in NexLink Social is end-to-end encrypted. The
server stores ciphertext and has never held the keys.

This is a property of the system's design rather than a promise about our
conduct. It means the Operator **cannot** produce your message content in
response to any request — from you, from a third party, or from a court.

It also means the Operator **can** see a good deal about the shape of your use,
and this policy sets that out in full rather than letting the encryption imply
more than it delivers.

---

## 3. Which Product holds what

The Products differ enormously in what they touch. Read the section for the
Product you use.

### 3.0 NexLink (SMS, dialler, unified inbox)

**Used on its own, NexLink sends nothing to the Operator.** It has no account, no
telemetry, no analytics, and no server of the Operator's to talk to. Everything
below stays on your device.

| Data | Where it goes | Why |
|---|---|---|
| Your SMS and MMS messages | Your device only | It is your SMS app |
| Your contacts | Your device only | To show names instead of numbers |
| Your call log | Your device only | To show recent calls |
| Notifications from other messaging apps | Your device only | The unified inbox reads them to build one list |
| Photos, audio and video you attach | Your device, then your carrier | MMS |

**The unified inbox reads notifications from other apps** (Signal, WhatsApp,
Telegram, Messenger, Discord, Instagram, Steam and NexLink Social) using
Android's notification access permission, which you must grant explicitly. That
content is read on your device, shown in your inbox, and **never transmitted
anywhere**.

**The Computer Bridge is the one exception, and it is opt-in.** If you enable it,
your messages are sent to a **server you host yourself**, at an address you type
in. NexLink has no default server, no hardcoded key and no identity of the
Operator's; the Operator receives nothing and can see nothing. Messages are
encrypted to your own browser's public key before they leave the phone.

### 3.1 NexLink Social — information you provide

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
minimum age of 16.

### 3.2 NexLink Social — information created by using it

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

**These periods become six months while the Service is operated from Switzerland — see §3.3a.**

### 3.3a Connection metadata under Swiss operation — six months

**While the Service is operated from Switzerland, IP addresses, user-agent
strings, connection timestamps and server logs are retained for six months
rather than 28 days.** No additional category is collected, and nothing else in
this policy changes.

**Why six months**

Switzerland's Federal Act on the Surveillance of Post and Telecommunications
(BÜPF), and the ordinance under it (VÜPF), can require a *provider of derived
communication services* to retain connection metadata for **six months** and to
cooperate with a lawful order. Six months is that statutory period. It is not a
number the Operator chose for convenience.

Whether those duties reach a service of this size and character has not been
determined, and the Operator does not claim it has. The period is adopted in
advance rather than after a classification, for a practical reason: a 28-day
window can expire before a lawful Swiss request is even served. That is the
worst outcome available — the Operator is obliged to answer, the data is already
gone, and the user gains nothing from its absence, because the request was made
regardless.

**Adopting the period is not a claim to have been classified**, and this clause
should not be read as one.

**If the obligation is determined not to apply, the period returns to 28 days.**
Retaining data longer than necessary is itself a privacy cost, not a neutral
precaution, and a longer period will not be kept simply because it was once
adopted.

**Why this is keyed to where the Service runs, not to where you live**

The Operator does not verify your location and collects no location data (Terms
§3.7, and §3.7 of this policy). "Users in Switzerland" is therefore not a group
the server can identify, and identifying it would mean processing location data
this policy says is not processed — a worse trade than a longer retention
period.

The trigger is the Service's own location, which is knowable and is what Swiss
law attaches to. **The consequence, stated plainly: while the Service is operated
from Switzerland, the six-month period applies to every user, including users in
Australia.** It is disclosed here rather than left to be discovered, and it is
why the decision to move the server is one you are told about in advance
(Terms §7.5).

**What this does to deletion**

Elsewhere this policy says your IP and user-agent records are purged when you
delete your Account. **Where a statutory retention obligation applies, that
promise cannot be kept for connection metadata**, and it is corrected here
rather than left to contradict itself:

- Under Swiss operation, connection metadata is retained for the six-month
  period **even after Account deletion**, and is then destroyed.
- Everything else listed in §7.1 is still removed at deletion, immediately.
- Under Australian operation, connection metadata continues to be purged at
  deletion, as §7.1 says.

**Today the Service is operated from Australia, the period is 28 days, and
connection metadata is purged at Account deletion.**

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

### 3.6 Donations — what happens if you use the donation page

The Products are free (Terms §2.7). A **voluntary donation page** is linked from
inside them, hosted by a third party (Buy Me a Coffee).

| Data | Who holds it | What the Operator sees |
|---|---|---|
| Your card or bank details | The donation provider, never the Operator | **Nothing** |
| Your name or chosen handle, and any message you write | The donation provider | Whatever you chose to put there |
| The amount and date | The donation provider | The amount and date |
| Your email address, if the provider passes it on | The donation provider | Possibly, depending on their settings |

- **The Operator never receives, sees or stores your payment details.** They do
  not pass through any NexLink system.
- **Donating is not linked to your Account.** The Operator does not connect a
  donation to a NexLink username, and does not ask you to identify your Account
  when donating. If you volunteer that connection in a donation message, you
  have made it yourself.
- **The donation provider is a separate controller** operating under its own
  privacy policy and its own jurisdiction. Read it before donating; the Operator
  does not control it.
- Donating confers no benefit and not donating has no consequence (Terms §2.7).

### 3.7 What we never collect
- Your contacts or address book, on any server.
- Your location. No NexLink Product requests a location permission.
- Advertising or tracking identifiers.
- Behavioural analytics.
- Your date of birth.
- **Your payment details** — see §3.6. Donations are handled entirely by a
  third party and no payment data reaches the Operator.

---

## 4. What we cannot see, stated precisely

For NexLink Social, the Operator cannot read:
- The text of your messages.
- The audio or video of your calls.
- Your photos, videos or files.
- Your message history.

The Operator **can** see:
- Which Accounts are in a conversation, and when each message was sent.
- Approximate message sizes.
- Your IP address, for 28 days — six months under Swiss operation (§3.3a).
- Which Devices you use and when each last connected.
- **Which emoji you reacted with, and to whose message.** Matrix sends reactions
  unencrypted; the message itself stays encrypted. We disclose this because it
  is true and because being caught not disclosing it would be worse.

For NexLink and NexLink Bridge, the Operator can see **nothing at all**, because there is no server
to see it with.

---

## 5. Third parties

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

### 5.4 Buy Me a Coffee — donations only
Processes voluntary donations if you choose to make one. It never touches
message data, account data or any Product function. See §3.6. **If you never
open the donation page, this third party receives nothing about you.**

### 5.5 Google Play
Distributes the Android applications and tells the Operator aggregate,
non-identifying install and crash counts. This is Play's own function; the
Operator cannot switch it off and does not receive your identity through it.

### 5.6 No others
We do not use analytics providers, advertising networks, crash reporting that
transmits personal data, or any service that profiles you.

### 5.7 International transfers, and the planned move to Switzerland

The Service's servers are **currently in Australia**. Cloudflare and Google
operate globally, so traffic metadata may be processed outside both Permitted
Territories. Content is end-to-end encrypted regardless of where it travels.

**The Operator intends to operate servers in Switzerland.** When that happens:

- Personal Data of Australian users will be transferred to Switzerland.
  Switzerland is recognised by the OAIC as having a comparable privacy regime,
  and the transfer will be made under APP 8.
- Personal Data of Swiss users will be held in Switzerland, and any transfer to
  Australia will be made under the FADP's rules for transfers abroad —
  Article 16 FADP, relying on adequacy where the Federal Council has recognised
  it, and on contractual safeguards otherwise.
- **You will be told before the move**, because it changes which authority
  supervises your data. This policy will be updated and re-acceptance sought if
  the change is material.

> ⚠️ **Review flag.** Whether Australia appears on the Swiss Federal Council's
> list of states with adequate data protection must be confirmed before any
> transfer is made, and the safeguard chosen accordingly. Do not rely on this
> paragraph's summary.

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
be notified directly and promptly.

- **Australia:** the OAIC will be notified where the Notifiable Data Breaches
  scheme requires it.
- **Switzerland:** the FDPIC will be notified as soon as possible where Article
  24 FADP requires it, and affected users informed where necessary for their
  protection or where the FDPIC requires it.

A notification will state what happened, what data was involved, what has been
done, and what you should do.

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

**Under Swiss operation, connection metadata is the one exception** — it is held
for the statutory six-month period and then destroyed. See §3.3a.

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
These rights exist under both the FADP and the Privacy Act. Where the two give
different scope, the wider is applied.

- **Access** — a copy of the personal information we hold about you.
- **Correction** — of anything inaccurate.
- **Erasure** — deletion of your Account, subject to §7.2.
- **Portability** — your data in a common electronic format (Article 28 FADP).
- **Objection** — to processing based on legitimate interests.
- **Restriction** — of processing while a dispute is resolved.
- **Information about a disclosure abroad** — which country, and on what
  safeguard (Article 19 FADP).
- **Not to be subject to an automated individual decision** — see §10; there are
  none.

### 8.2 What is self-service
- **Erasure**: in the app, or at the deletion page.
- **Portability**: the app exports your messages as JSON. **We cannot do this
  for you** — we cannot read them. This is the clearest case where encryption
  changes who can satisfy a right.
- **Correction** of your display name: in the app.

### 8.3 What to ask the Operator for
Access to server-side records, objection, restriction, or anything the app does
not expose. Contact the Operator through the Contact Page.

Expect a response **within 30 days**. The FADP allows an extension where a
request is complex; if one is needed you will be told within the 30 days, with
the reason. A solo operator may take the full period.

**Access requests are free**, as both laws require.

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
Contact the Operator first, through the Contact Page.

- **Switzerland:** you may report the matter to the Federal Data Protection and
  Information Commissioner (edoeb.admin.ch), and you may bring a civil claim
  under Articles 32 and 41 FADP.
- **Australia:** you may complain to the Office of the Australian Information
  Commissioner (oaic.gov.au).

You do not have to complain to the Operator first, and nothing in this policy
requires you to.

---

## 9. Children
The Service is not offered to anyone under **16** (Terms §3.1). We do not
knowingly hold information about anyone below that age, and an Account found to
belong to such a person is removed.

---

## 10. Automated decision-making
There is none. No profiling, no automated moderation, no algorithmic ranking. A
person makes every moderation decision.

---

## 11. Cookies and local storage
The web pages — Element Web, the deletion page and the Contact Page — use
browser local storage for session and settings only. There are no tracking
cookies, no third-party cookies and no advertising identifiers.

---

## 12. Changes to this policy
This policy is versioned. A material change requires re-acceptance in the app,
so you cannot be moved onto different terms silently. The version history is
available on request.

---

## Appendix A — Definitions

This appendix is identical in every NexLink legal document. It is maintained as a
single source and appended mechanically, so a term cannot come to mean one thing
in the Terms of Service and another in the Content Policy.

Where a term is defined by reference to a statute, the statutory definition
governs and this text is a plain-language summary of it. Where the statute and
this summary differ, **the statute prevails**.

### A.1 The service and the parties

| Term | Meaning |
|---|---|
| **NexLink Products** | All software published by the Operator under the NexLink name. At the date of this version: **NexLink** (SMS, dialler and unified inbox for Android), **NexLink Social** (encrypted messenger for Android), **NexLink for Wear OS**, and **NexLink Bridge** (the Computer Bridge in NexLink together with its web client). A product added later is covered from the date it is published. |
| **Service** | Whichever NexLink Products you use, and the server infrastructure that supports them. |
| **Operator** | The individual who publishes the NexLink Products and runs the Service. Contact details are published at the Contact Page. |
| **Contact Page** | `https://thvjq.com.au/nexlink/contact` — the single published source for the Operator's contact details, kept current so that a notice address embedded in an installed application can never go stale. It publishes an email address; a postal address is supplied on request and to any authority or party entitled to it. |
| **You**, **User** | The natural person who has accepted these documents and uses the Service. |
| **Account** | Your identity on a NexLink Product that requires registration. |
| **Device** | A phone, tablet, watch or computer signed in to your Account or running a NexLink Product. |

### A.2 Data and content

| Term | Meaning |
|---|---|
| **Content** | Anything you send, receive, upload, store or transmit using the Service. |
| **Metadata** | Information generated by your use that is not Content — who communicated with whom and when, message sizes, IP addresses, device identifiers. The Privacy Policy states exactly which items exist. |
| **End-to-end encryption** | Encryption in which only the participating Devices hold the decryption keys, and the Operator does not. |
| **Personal Data** | Has the meaning given by Article 5(a) of the Swiss Federal Act on Data Protection (FADP) for Users in Switzerland, and the meaning given to "personal information" by section 6 of the Privacy Act 1988 (Cth) for Users in Australia. Both mean, in substance: any information relating to an identified or identifiable person. |
| **Recovery Key** | The code that restores access to your encrypted message history. The Operator does not hold a copy and cannot reissue it. |
| **Invite** | A single-use code required to create an Account on an invite-only NexLink Product. |

### A.3 Prohibited-conduct terms

These four definitions exist because a prohibition a user cannot predict is not
a fair prohibition, and because enforcement against vaguely defined conduct is
both unjust and legally fragile. Each is drawn from statute and each carries an
express exclusion.

#### **Terrorism** / **Terrorist Act**

For a User in **Australia**, as defined by section 100.1 of the *Criminal Code Act 1995* (Cth): an action or threat of action that

1. causes death, serious physical harm, serious damage to property, endangers life, creates a serious risk to public health or safety, or seriously interferes with essential electronic systems; **and**
2. is done with the intention of advancing a political, religious or ideological cause; **and**
3. is done with the intention of coercing or influencing by intimidation a government, or intimidating the public or a section of the public.

For a User in **Switzerland**, as understood by Articles 260ter and 260quinquies of the Swiss Criminal Code (StGB/CP) and the *Federal Act on Police Measures to Combat Terrorism*: participation in, support of, or financing of an organisation that pursues its aims through crimes of violence, or the commission of such crimes to intimidate a population or coerce a state or international organisation.

> **Express exclusion.** Advocacy, protest, dissent, satire, journalism, academic
> research, historical documentation, artistic depiction and industrial action
> are **not** terrorism, and are not prohibited by any NexLink document, unless
> the conduct itself satisfies every limb of the definition above. All three
> limbs of the Australian definition must be met; any one alone is not enough.
> This exclusion mirrors section 100.1(3) of the Criminal Code and is stated
> here so that no reader has to find it.

#### **Violent Extremism**

Neither Australian nor Swiss law supplies a single statutory definition, so the
Operator adopts a deliberately narrow one and states it rather than leaving it
to be inferred:

Content or conduct that **intentionally promotes, incites, instructs in, or
solicits participation in unlawful violence against a person or group** by
reason of their race, religion, nationality, ethnicity, sex, gender identity,
sexual orientation, disability, or political opinion.

> **Express exclusion.** Reporting on, condemning, analysing, satirising,
> educating about, or counter-speaking against violent extremism is **not**
> violent extremism. Describing an event is not promoting it. Holding or
> expressing an unpopular, offensive or radical political or religious opinion
> is **not** violent extremism and is not prohibited; the prohibition attaches
> to the incitement of unlawful violence, and to nothing else.
>
> The Operator will not treat a User as engaged in violent extremism on the
> basis of their political or religious beliefs, their nationality, or their
> association with others, in the absence of the intentional incitement
> described above.

#### **Proscribed Organisation**

An organisation listed as a terrorist organisation under Division 102 of the
*Criminal Code Act 1995* (Cth), or subject to prohibition or sanction under the
Swiss *Embargo Act* or Federal Council ordinance. The Operator applies the list
of the jurisdiction in which the User is located, and does not maintain a list
of its own.

#### **Unlawful**

Contrary to the law of Switzerland or of the Commonwealth of Australia and the
Australian State or Territory in which the User is located. Where conduct is
lawful in the User's own jurisdiction it is not "unlawful" under these documents
merely because it is unlawful elsewhere.

### A.4 Legal and jurisdictional terms

| Term | Meaning |
|---|---|
| **Permitted Territories** | Switzerland and Australia. The Service is offered only to Users resident in these two countries — see Terms of Service §3.7. |
| **FADP** | The Swiss *Federal Act on Data Protection* of 25 September 2020, in force 1 September 2023, together with the Data Protection Ordinance (DPO). |
| **FDPIC** | The Swiss *Federal Data Protection and Information Commissioner* (EDÖB/PFPDT), the supervisory authority for Users in Switzerland. |
| **Privacy Act** | The *Privacy Act 1988* (Cth) and the Australian Privacy Principles made under it. |
| **OAIC** | The *Office of the Australian Information Commissioner*, the supervisory authority for Users in Australia. |
| **ACL** | The *Australian Consumer Law*, Schedule 2 to the *Competition and Consumer Act 2010* (Cth). |
| **Consumer Guarantees** | The guarantees given by the ACL that cannot be excluded, restricted or modified by agreement. |

### A.5 Reading these documents

- **"Including"** means "including without limitation".
- **"Writing"** includes electronic messages sent to the address on the Contact Page.
- Headings are for navigation and do not affect interpretation.
- A reference to a statute is a reference to it as amended, and to any statute that replaces it.
- Where the English and German versions of a document differ, **the English version prevails**, except where the User is a consumer resident in Switzerland and Swiss law requires otherwise.
