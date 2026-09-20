<!-- Assembled by tools/legal/build-legal.py. Edit docs/legal/en/transparency-note.md
     or docs/legal/definitions.en.md, never this file. -->

# What we can and cannot see — NexLink

**Version 0.3.0-draft** · Not yet published · §3.7, §4.7

> **Scope.** Applies to all NexLink Products.
>
> **Draft.** Not reviewed by a lawyer. See `docs/legal/README.md`.

This is the plain-language version. The Privacy Policy is the formal one; where
they differ that one governs — but they are written to agree, and a discrepancy
is a bug worth reporting.

---

## The short version

Your messages are end-to-end encrypted. The server stores them as scrambled data
and has never had the keys. Nobody operating this service can read what you
write, and that is a property of the encryption rather than a promise about our
behaviour.

What we **can** see is the shape of your use: who you talk to, when, and how
often. That is real information about you, and this page does not pretend
otherwise.

---

## We cannot see

- **The content of your messages.** Not now, not later, not if asked, not if
  ordered by a court. There is nothing to hand over.
- **The content of your calls.**
- **Your photos, videos and files.** Encrypted before they leave your phone.
- **Your message history.** It lives on your devices.
- **Who is in your contacts.** We never ask for them.
- **Where you are**, beyond what your IP address implies.

## We can see

- **Who you message, and when.** The server routes your messages, so it knows
  which accounts are involved and the timing.
- **How often you send messages, and roughly how large they are.**
- **Your IP address**, which approximates your location and names your internet
  provider. Kept 28 days.
- **Which devices you use**, and when each last connected.
- **Which emoji you react with, and to whose message.** Matrix sends reactions
  unencrypted. The message being reacted to stays encrypted; the reaction does
  not. We would rather tell you than let you assume otherwise.
- **That a message arrived for you, and when** — see Google, below.
- **Who invited you.**

---

## The three companies involved, and exactly what each learns

We use as few as possible. None of them can read your messages.

### Cloudflare
Carries traffic between your phone and the server, because the server sits on a
home internet connection with no public address of its own.

**Learns:** your IP address, when you connect, how much data flows.
**Cannot:** read anything encrypted, which is everything that matters.

### Google — push notifications
When a message arrives for you, the server asks Google to wake the app on your
phone. Without this, messages would only arrive while the app was open, or the
app would have to hold a connection open and flatten your battery.

**Learns:** that a notification should go to your device, and when.
**Is sent:** an event identifier and a room identifier. **Not the message, not
the sender's name, not a single word of text.** Your phone decrypts locally to
work out what the notification should say.

Most encrypted messengers have exactly this property. Most do not mention it.

### LiveKit — calls
Relays audio and video during a call.

**Learns:** that someone joined a call, when, and for how long.
**Cannot:** hear or see the call — the media is encrypted end-to-end.
**Is not told:** which conversation the call belongs to. The room is identified
to them only by a one-way hash.

---

## Who runs this

One person, not a company. There is no support team, no service level agreement
and no guarantee of uptime. If something breaks at 2 a.m. it stays broken until
they wake up.

That cuts both ways, and it is the honest trade this service asks you to make.
There is no advertising business model, nothing is sold, and nobody is
monetising your attention — because there is nobody to do it.

---

## What happens when you leave

Delete your account in the app, or at `https://nexlink.thvjq.com.au/delete/`
without the app installed.

**Removed:** your account, every session, your profile, your uploaded files,
your encrypted backup, your email address if you gave one, and the record of the
addresses you connected from.

**Kept, and why:**
- **Your username** — so nobody can register it later and be mistaken for you.
- **That you accepted the terms and confirmed your age** — the record that you
  did, not the details.
- **The invite you joined with** — a one-way code and timestamps, so abuse can
  be traced.

**And one thing worth knowing:** deleted data sits inside encrypted backups for
up to 14 days before those expire. That is normal, and we would rather say it
than have you discover the gap between "deleted" and a backup schedule.

---

## The limits of all this

- **Your phone is the weak point.** Everything above concerns the server. If
  someone unlocks your phone, they read your messages. Set a screen lock.
- **The other person can screenshot.** Encryption protects a message in transit
  and at rest. It cannot stop a recipient keeping it, and nothing can.
- **Metadata could be compelled.** We cannot be made to produce message content,
  because we do not have it. A lawful order for the "can see" list above is a
  different matter, and we would tell you about it unless legally forbidden.
- **Deleting your account does not delete messages from other people's phones.**
  We have no reach into someone else's device, and would not want one.
- **There is no scanning for bad content**, and there cannot be — that is the
  same encryption working. Safety here depends on blocking and reporting, both
  of which are in the app.

---

## If something is wrong

If any statement on this page turns out to be inaccurate, that is a bug and we
want to know. The whole value of this document is that it is checkable.

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
| **Contact Page** | `https://thvjq.com.au/nexlink/contact` — the single published source for the Operator's postal and electronic contact details, kept current so that a notice address embedded in an installed application can never go stale. |
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
