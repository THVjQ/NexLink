<!-- Assembled by tools/legal/build-legal.py. Edit docs/legal/en/data-retention-schedule.md
     or docs/legal/definitions.en.md, never this file. -->

# Data Retention Schedule — NexLink

**Version 0.3.0-draft** · Not yet published · §32.7

> **Scope.** Applies to data held on the Operator's servers, which today means NexLink
Social only. NexLink, Wear OS and the web client store their data on your own
device or your own server.
>
> **Draft.** Not reviewed by a lawyer. See `docs/legal/README.md`.

Forms part of the Privacy Policy. Every category the Service holds, how long it
is kept, and what causes it to go.

---

## 1. Account data

| Data | Retention | Deleted by |
|---|---|---|
| Username (MXID) | **Permanent, tombstoned after deletion** | Never — see §4 |
| Password hash | Until Account deletion | Account deletion |
| Display name | Until Account deletion | Account deletion, or you clear it |
| Profile picture | Until Account deletion | Account deletion, or you clear it |
| Email address (if provided) | Until Account deletion or removal | Account deletion |
| Age confirmation (boolean) | Account life + limitation period | Not deleted with the Account |
| Terms acceptance record | Account life + limitation period | Not deleted with the Account |

## 2. Message and media data

| Data | Retention | Deleted by |
|---|---|---|
| Encrypted message events | Indefinite, or your retention setting | Account deletion; room deletion |
| Encrypted media | Until Account deletion or orphan cleanup | Account deletion; the hourly sweeper |
| Redaction originals | **7 days** | Automatic |
| Room state (membership, names) | Life of the room | Leaving and deletion |
| Reactions (unencrypted) | Until deleted | Account deletion; redaction |
| Read receipts, typing | Transient — not durably stored | — |

## 3. Technical and security data

| Data | Retention | Deleted by |
|---|---|---|
| IP addresses | **28 days** | Automatic expiry; **and purged at Account deletion** |
| User agent strings | 28 days | As above |
| Device list and public keys | Until the Device is removed | Signing out; removing the Device |
| Push routing token | Until sign-out or Device removal | Sign-out |
| Server logs | 28 days | Automatic rotation |

**IP records are purged at deletion, not merely left to expire.** A deleted
Account does not leave 28 days of connection history behind.

## 4. What survives account deletion

| Data | Why | Retention |
|---|---|---|
| Tombstoned username | Prevents reissue and impersonation of a former user | Permanent |
| Acceptance record | Evidence terms were accepted and age confirmed | Limitation period |
| Invite record — one-way hash, timestamps, pseudonymised | Abuse tracing; integrity of the invite tree | Life of the tree |

Nothing in this table can identify you from the Service alone once the Account
is gone, other than the username itself — which is retained precisely so that it
continues to refer to nobody.

## 5. Moderation data

| Data | Retention |
|---|---|
| Reports (reporter, subject, description, time) | 2 years |
| Message content attached to a report with consent | 2 years |
| Moderation decisions and actions | 2 years |

## 6. Backups — the retention hole

| Data | Retention |
|---|---|
| Encrypted database backups | **14 days** |
| Server configuration and signing key backups | 14 days |

**A deleted Account persists inside encrypted backups for up to 14 days** before
those backups expire and are destroyed.

This is stated explicitly because a deletion promise that quietly contradicts a
backup schedule is a promise not kept. Backups are encrypted, are never searched,
and are used only to restore the Service after failure.

## 7. Call data

| Data | Retention |
|---|---|
| Call membership events (who joined, when) | With the room's events |
| Call media | **Not recorded.** Relayed and discarded |
| LiveKit-side participation records | Per LiveKit's own retention; the conversation is not disclosed to them |

## 8. What is never collected

Contacts, address book, location beyond IP inference, advertising identifiers,
behavioural analytics, date of birth, payment details, biometrics.

**Payment details** remain on this list even though a donation page now exists.
Donations are processed entirely by a third party and no payment data reaches
the Operator or any NexLink system — see Privacy Policy §3.6.

## 9. Triggers for review

This schedule is reviewed when: a new data category is introduced; a retention
period changes; a new third party is added; or annually, whichever is soonest.

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
| **NexLink Products** | All software published by the Operator under the NexLink name. At the date of this version: **NexLink** (SMS, dialler and unified inbox for Android), **NexLink Social** (encrypted messenger for Android), **NexLink for Wear OS**, and the **NexLink web client**. A product added later is covered from the date it is published. |
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
