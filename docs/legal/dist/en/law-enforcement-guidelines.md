<!-- Assembled by tools/legal/build-legal.py. Edit docs/legal/en/law-enforcement-guidelines.md
     or docs/legal/definitions.en.md, never this file. -->

# Law Enforcement Guidelines — NexLink

**Version 0.3.0-draft** · Not yet published · §30.5

> **Scope.** Applies to all NexLink Products. Only NexLink Social has server-side data.
>
> **Draft.** Not reviewed by a lawyer. See `docs/legal/README.md`.

For law enforcement and legal practitioners. This document describes what data
exists and how requests are handled. **It is not legal advice and does not waive
any right of the Operator or of any user.**

---

## 1. What this Service is

A private, invite-only, end-to-end encrypted messaging and calling service
operated by an individual in New South Wales, Australia, and offered only to
users in Switzerland and Australia. It does not federate
with other servers. It has no corporate entity, no legal department, and no
dedicated point of contact beyond the Operator.

---

## 2. What does not exist

**Message content and call content are end-to-end encrypted. The Operator does
not hold the decryption keys and cannot produce plaintext.**

This is not a policy position that could be changed by a court order. There is
no key escrow, no server-side copy, and no mechanism by which the Operator could
comply. An order to produce message content would be an order to produce
something that does not exist in that form.

| Requested | Available? |
|---|---|
| Message content | **No** — not held in readable form, by design |
| Call content | **No** — end-to-end encrypted |
| Media files | Ciphertext only; keys not held |
| Historical message content | **No** |
| Ability to intercept future messages | **No** — would require a client-side change, and see §5 |

---

## 3. What does exist

| Requested | Available? | Retention |
|---|---|---|
| Account existence, username, creation date | Yes | Life of Account, tombstoned after |
| Email address | Only if the user provided one; many have not | Until deletion |
| Device list, last seen times | Yes | Until Device removed |
| Which Accounts share a conversation, and when messages were sent | Yes | Until deletion |
| Approximate message sizes | Yes | Until deletion |
| IP addresses and user agents | Yes | **28 days only** |
| The Invite record, including who issued the Invite | Yes | Retained after deletion |
| Reports made about an Account | Yes | 2 years |

**Note the 28-day limit on IP records.** A request received after that window
cannot be satisfied for that period, and no copy is retained elsewhere except in
database backups, which expire after 14 days.

---

## 4. How to make a request

1. Serve the request on the Operator at the address published on the **Contact Page**, `https://thvjq.com.au/nexlink/contact`, or using the address in
   the service website.
2. Include the legal basis, the specific data sought, and the relevant time
   period.
3. **Requests must be specific.** Requests for "all data" or for content the
   Operator cannot produce will be answered by explaining what exists.

Requests will be verified as genuine and properly served before any response.

---

## 5. How requests are handled

1. The request is acknowledged and the date recorded.
2. **The Operator will obtain legal advice before responding.** This is a
   solo-operated service, and that step takes time.
3. The Operator determines what is being asked for and what actually exists.
4. The Operator complies only with what is lawfully required, and only to the
   extent required.
5. **The affected user is notified, unless the Operator is legally prohibited
   from notifying them.**
6. The request is recorded for transparency reporting.

---

## 6. Emergency requests

Where there is a credible risk of imminent serious harm, the Operator will act
as quickly as they are able. **Users should understand that "as quickly as able"
for a solo operator may be hours.** This Service should not be relied upon in a
situation requiring an immediate response.

---

## 7. Requests we will refuse

- Requests for message content, because it does not exist in readable form.
- Requests to add a backdoor, weaken encryption, or modify the client to
  intercept a user. Such a request would be resisted to the extent lawfully
  possible, and if compliance were compelled the Operator would consider
  discontinuing the Service instead.
- Informal requests without legal basis.
- Requests for data about users of other services.

---

## 8. Transparency reporting

The Operator intends to publish periodic figures: the number of requests
received, the number complied with, and the categories of data produced —
consistent with any legal restriction on disclosure.

Where the Operator is prohibited from disclosing that a request was received,
the report will say only what it lawfully can.

---

## 9. Preservation requests

The Operator will honour a lawful preservation request for data that exists at
the time the request is received. **A preservation request cannot preserve data
that has already expired**, including IP records older than 28 days.

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
