# §4 — Regulatory and platform obligations

> **Confidence:** `DURABLE` in shape, `SPECULATIVE` in detail.
>
> **This chapter is not legal advice and must not be relied on as such.** It is
> an engineer's map of the obligations that appear to attach to this product, so
> that a solicitor can be briefed efficiently and so that no obligation is
> discovered for the first time during a Play review or an Ofcom enquiry.
> Several items below carry real personal liability for the operator. Get
> advice before launch, not after.

---

## 4.1 The shape of the problem

Running a messaging service is one of the more heavily regulated things a solo
developer can do, and the regulation has tightened sharply since 2023. Four
distinct regimes apply simultaneously:

| Regime | Trigger | Enforcer |
|---|---|---|
| Google Play policy | Distributing on Play | Google, unilaterally |
| Data protection (UK GDPR / EU GDPR) | Processing personal data of UK/EU residents | ICO / national DPAs |
| Online safety (UK OSA / EU DSA) | Operating a user-to-user service | Ofcom / EU Commission and national coordinators |
| Child protection | Hosting user content, any user base | Criminal law, jurisdiction-dependent |

**The most commonly underestimated is online safety.** It is newer than the
others, applies to services far smaller than people assume, and carries the
heaviest penalties.

---

## 4.2 Google Play

Play policy is the most immediate constraint because it gates distribution and
is enforced without a hearing.

### 4.2.1 Requirements that apply to this product

| Requirement | What it means here |
|---|---|
| **Privacy policy** | Public URL, linked in the listing and reachable in-app. Must accurately describe what is collected — including the metadata in §3.2. |
| **Data safety form** | A declaration of every data type collected and shared. Must match the privacy policy and the app's actual behaviour; mismatches are a common suspension cause. |
| **Account deletion** | Apps allowing account creation must offer deletion **in-app and from a publicly reachable web URL** that does not require installing the app. See §7.6. |
| **UGC policy** | User-generated content requires an in-app reporting mechanism, the ability to block users, a published content policy, and evidence of moderation. See §31. |
| **Restricted permissions** | Each declared restricted permission needs justification. Social adds `CAMERA`, `RECORD_AUDIO`, and the foreground service types. |
| **Foreground service types** | Since Android 14, each FGS type used must be declared in Play Console with a justification and, often, a demo video. Social will declare `camera`, `microphone`, `mediaProjection` and possibly `phoneCall`. |
| **Target API level** | Must track Play's rolling minimum. Already satisfied at API 36. |
| **Encryption declaration** | Play asks whether the app uses cryptography and whether an export exemption applies. See §4.6. |

### 4.2.2 The UGC requirement in detail

This is the requirement most likely to be underestimated, because it is not a
one-off form — it is an ongoing operational commitment that must be visible in
the product:

- **In-app reporting** of both individual messages and users, reachable from the
  conversation itself, not buried in settings.
- **Blocking** that actually prevents contact, enforced server-side.
- **A published content policy** stating what is not permitted.
- **A moderation process** with a response time, and evidence it operates.

An E2EE service cannot proactively scan content, which makes the *reporting*
path the entire moderation surface. Reports must therefore carry enough context
to act on — which means the reporting client attaches the relevant decrypted
messages **with the reporter's explicit consent**, since the server cannot
obtain them otherwise. This is a genuine design constraint on §31 and it
follows directly from the encryption posture.

### 4.2.3 Play risk to NexLink

Restated because it is the basis of D1 (§2.2): every item above is enforced at
the application ID. Keeping Social in a separate package means a UGC or
moderation failure removes Social, not NexLink's SMS and dialer functionality.

---

## 4.3 Data protection

### 4.3.1 Controller status

Operating the homeserver makes THVjQ a **data controller** for account data,
metadata and any content stored server-side (ciphertext, but still personal
data). This is not avoidable by encrypting content — ciphertext linked to an
identifiable person remains personal data.

### 4.3.2 Obligations

| Obligation | Practical form |
|---|---|
| Lawful basis | Contract (providing the service) for account and message-routing data. Legitimate interests for abuse prevention, with a balancing test recorded. |
| Transparency | Privacy policy that describes the metadata in §3.2 honestly, including retention periods. |
| Data minimisation | Directly drives the retention policy in §25.4. Not collecting is the strongest compliance posture available. |
| Subject access | Respond within one month. Requires tooling (§32) — a manual database trawl will not scale and will not meet the deadline. |
| Erasure | Account deletion (§7.6), with an honest statement of what cannot be erased. |
| Portability | Export in a machine-readable format. The transfer mechanism in §7.5 largely satisfies this. |
| Breach notification | To the regulator within 72 hours of becoming aware; to affected users if high risk. **Requires a pre-written procedure — 72 hours is not enough time to invent one.** See §30. |
| Records of processing | A written record. Small-organisation exemptions are narrow and unlikely to apply to a service processing communications data. |
| DPIA | A Data Protection Impact Assessment is likely required: this is large-scale processing of communications data, which is explicitly flagged as high risk. |

### 4.3.3 What is not required

Realistic scoping, so effort goes where it matters:

- **A DPO** is unlikely to be required at this scale, though the analysis should
  be recorded rather than assumed.
- **An EU representative** may be required if EU users are served from outside
  the EU. Depends on where users and infrastructure actually are.

---

## 4.4 Online safety

**The chapter's most important section, and the one most likely to be a
surprise.**

### 4.4.1 UK Online Safety Act

The OSA applies to "user-to-user services" with links to the UK. A private
messaging service with UK users is in scope. Crucially, **there is no small
service exemption** — duties scale, but they do not disappear, and Ofcom has
been explicit that small services are in scope.

Duties that appear to attach:

- **Illegal content risk assessment**, written, kept current, covering the
  priority offence categories.
- **Children's access assessment** — a written determination of whether children
  are likely to access the service. Invite-only with an age gate (§9.6) makes
  this defensible, but the assessment must still be *written down*.
- **Safety duties proportionate to risk** — reporting, complaints, terms
  transparency.
- **Record-keeping** demonstrating all of the above.

**Penalties** are substantial — up to the greater of £18m or 10% of global
revenue — and senior management liability exists for certain failures. For a
solo operator, "senior management" means the operator.

**Practical position:** the risk assessments are documents, not engineering.
They must exist before launch and be dated. This is a weekend of writing and it
is not optional.

### 4.4.2 EU Digital Services Act

Applies to hosting and intermediary services with EU users. Micro and small
enterprises are exempt from the heavier duties, but the baseline still applies:

- A published point of contact.
- Notice-and-action mechanism for illegal content.
- Terms of service stating content restrictions clearly.
- Statement of reasons when content or accounts are actioned.

The reporting and blocking work required by Play (§4.2.2) satisfies most of the
baseline. The gap is usually the *statement of reasons* — telling a user why
they were actioned, in writing.

### 4.4.3 The encryption tension

Both regimes contain provisions that sit uneasily with E2EE — the OSA's
"accredited technology" notice power being the sharpest example. The current
practical position is that no such technology has been accredited as compatible
with E2EE and no notices have been issued on that basis.

**This is a live policy risk, not a settled question, and it is tracked in the
risk register (§36).** The design does not attempt to pre-comply with a
requirement that does not yet exist, and could not do so without abandoning §3.5.

---

## 4.5 Child protection

Hosting user media creates obligations regardless of encryption.

- **Act on reports.** Where content is reported and can be reviewed with the
  reporter's consent (§4.2.2), there is a duty to act and, in relevant
  jurisdictions, to report.
- **Preserve and report** in accordance with local law. In the UK this involves
  the IWF and law enforcement; in the US, NCMEC reporting duties apply to
  providers.
- **Age assurance.** The date-of-birth gate (§9.6.1) is the minimum. Whether
  stronger assurance is required depends on the children's access assessment.

**E2EE does not exempt the operator from acting on what is reported.** It only
means proactive scanning is impossible — which is a defensible position, stated
honestly in the risk assessment, not a loophole.

---

## 4.6 Export control

Distributing cryptographic software crosses export control regimes. For a
mass-market messaging app the practical position is usually:

- The cryptography is standard, published, and mass-market — the category that
  attracts the lightest treatment in most regimes.
- Play's console asks an encryption question at submission; answering it
  accurately is generally the extent of the obligation for a UK/EU developer.
- US EAR self-classification and notification applies principally to
  US-origin distribution.

**Marked `SPECULATIVE`.** Low risk, low effort to confirm, and worth confirming
rather than assuming.

---

## 4.7 Terms of service

The acceptance gate (§9.6) requires documents that actually exist. Minimum set:

| Document | Must state |
|---|---|
| **Terms of Service** | What the service is; acceptable use; that it is operated by an individual without warranty; termination grounds; governing law. |
| **Privacy Policy** | Every data type in §3.2; retention periods; lawful bases; subject rights; contact point; the honest metadata disclosure from §3.7. |
| **Content Policy** | What is not permitted. Required by Play's UGC policy and useful as the basis for any moderation action. |
| **Transparency note** (optional, recommended) | What the operator can and cannot see, in plain language. Reduces support load and builds the trust the product depends on. |

All four carry a semantic version, recorded against each acceptance (§9.6.2), so
re-acceptance on material change is mechanical.

---

## 4.8 Sequencing

Compliance work that must complete **before** the first non-operator user:

1. Privacy policy, terms, content policy written and published.
2. Account deletion web URL live and functional.
3. In-app reporting and blocking implemented and tested.
4. UK OSA illegal content risk assessment written and dated.
5. Children's access assessment written and dated.
6. DPIA written.
7. Breach response procedure written (§30).
8. Data safety form completed to match actual behaviour.

Items 1–3 are engineering. Items 4–7 are writing. **None is optional, and all of
them are cheaper before launch than after.**
