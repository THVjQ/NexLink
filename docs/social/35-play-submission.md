# §35 — Play Store submission

> **Confidence:** `DURABLE`
> Policy specifics move; the shape of the submission and the risks do not.

---

## 35.1 The stakes, restated

§1.4.3: *enforcement attaches to the package.* D1 (§2.2) exists so that a policy
problem with the social product removes the social product, not the user's SMS
client and dialer.

That protection is real but it is not absolute. Play takes **developer-account**
action as well as package action, and a serious enough violation in one listing
can reach the account that publishes both. So the goal is not merely approval
— it is a submission conservative enough that a dispute never escalates.

**Nothing about this submission should be clever.**

---

## 35.2 What is being declared

`com.thvjq.nexlink.social`, a new listing under the existing developer account,
signed with `nexlink-release.jks` (§10.5.1).

| Declaration | Content |
|---|---|
| **UGC** | §35.3 — the one that decides the outcome |
| **Data safety** | §35.4 |
| Foreground service types | `microphone`, `camera`, `mediaProjection`, `dataSync` — each with a justification and, in practice, a video (§15.3.1) |
| `USE_FULL_SCREEN_INTENT` | Incoming calls only (§19.5.1) |
| Account deletion | In-app **and** a public web URL (§32.3) |
| Encryption / export | §4.6 |
| Target audience | Adults. **Not** in the Families programme. |

**`:social` declares none of NexLink's restricted permissions.** No SMS, no call
log, no notification listener, no accessibility service. This is the whole point
of D1 and it makes the submission a fundamentally ordinary one — a messaging app
with calls — rather than the extraordinary one NexLink's own listing is.

---

## 35.3 The UGC declaration — the likely point of failure

§31.8 names this as the most probable cause of rejection and §31.4 explains why:
Play's UGC policy asks for a mechanism to moderate objectionable content, and
this operator structurally cannot read content.

**The declaration says what is true:**

> NexLink Social is an invite-only, end-to-end encrypted messaging service.
> Message and call content is encrypted on the sender's device and can only be
> read by the intended recipients; the operator has no technical ability to
> access it.
>
> Moderation operates at the account level:
> • Users can block any other user immediately, without operator involvement.
> • Users can report a user or specific messages. A report may, with the
>   reporter's explicit consent, include message content, which is the only
>   circumstance in which the operator receives message content.
> • Reports are actioned by warning, suspending or deactivating accounts, with
>   published response timelines.
> • Registration is invite-only. Every account is traceable to the account that
>   invited it, so abuse has an accountable origin and related accounts can be
>   suspended together.
> • Child safety reports are referred to the relevant authority immediately.

Three things this deliberately does **not** do:

1. **It does not claim content moderation exists.** Claiming a capability that
   does not exist is a misrepresentation to Play, and it is discoverable.
2. **It does not apologise for the encryption.** Encrypted messengers ship on
   Play; this is a normal product category.
3. **It does not promise timelines that §31.5 cannot meet.** The published
   numbers are deliberately unimpressive and deliberately keepable.

### 35.3.1 Preparation before submitting

- Confirm the current UGC policy wording. It changes.
- Look at how other E2EE messengers describe this. The category has precedent,
  and matching established phrasing is worth more than originality here.
- Have reporting and blocking **working and demonstrable** before submitting.
  A reviewer who can find the block button in two taps is a reviewer who
  believes the declaration.
- Have the terms of service and privacy policy published and reachable from the
  listing (§4.7).

---

## 35.4 Data safety

Accuracy matters more than minimising. A data-safety form contradicted by the
app's behaviour is a policy violation on its own.

| Data type | Collected | Shared | Note |
|---|---|---|---|
| Messages | **Yes**, stored encrypted | No | Cannot be read by the operator |
| Photos and videos | **Yes**, stored encrypted | No | Same |
| Voice/video call content | **No** | No | Never stored (§25.1) |
| Name | Optional (display name) | No | |
| Email | **Optional** — the no-email path (§7.1) | No | |
| User IDs | Yes | No | |
| App interactions | No | No | **No analytics SDK** (§27.5) |
| Crash logs | Only if added, scrubbed | No | §27.5 |
| Approximate location | **No** | No | Not collected. IP is logged for 28 days for security (§25.6) — disclose under the security practices section. |

Claims made:
- **Encrypted in transit:** yes.
- **Deletion available:** yes, in-app and by web (§32.3).
- **Independent security review:** **no.** Do not claim it. §35.7.

The honest "messages are collected but cannot be read by the operator" framing
is more defensible than declaring no collection at all — the ciphertext *is* on
the operator's server, and a reviewer comparing the form to the architecture
will see that.

---

## 35.5 Listing content

| | |
|---|---|
| Title | NexLink Social |
| Short description | Private, encrypted messaging and calls. Invite only. |
| Screenshots | The app as it actually is. **Screen-share screenshots must show it inside a call** (§18.9). |
| Video | Optional. Likely required as justification for the FGS types. |
| Category | Communication |
| Content rating | Complete the questionnaire honestly, including that users can communicate freely |

**Do not describe features that do not ship yet.** If phase 4 is deferred and
the build has no calling (§33.9.1), the listing describes messaging. A listing
that promises calls to an app without them is both a policy issue and a refund
queue.

---

## 35.6 Release track strategy

| Track | When |
|---|---|
| Internal testing | Phase 6 (§33.7). Up to 100 testers, fast review, no UGC scrutiny at this level |
| Closed testing | Wider invited cohort if wanted |
| Production | Phase 7, after §33.7's criteria are met |

**Use internal testing first.** It gets the build onto real devices with real
Play delivery — which surfaces signing, ABI split (§10.6.2) and AAB problems
before any of it matters — without entering full review.

### 35.6.1 The release train, which is the point of all this

§1.4.3: *release trains fuse.* At the time this document was first drafted, a
NexLink build containing a foreground-service crash fix was held in review, and
no SMS fix could ship until it cleared.

After D1, `:social`'s review queue is its own. A rejected Social update does not
hold an SMS fix. **When submitting, verify the two listings are genuinely
independent**, and treat any coupling observed as a problem to solve rather than
a curiosity — it would mean D1 is not delivering what it was chosen for.

---

## 35.7 Before submitting

- [ ] Both apps signed with the same keystore, verified (§10.5.1)
- [ ] `:social` ships as an **AAB**, never a universal APK (§10.6.2)
- [ ] R8 enabled and the release build smoke-tested (§10.6.3)
- [ ] Manifest declares **only** the FGS types actually used (§15.3.1)
- [ ] Terms and privacy policy live, versioned, linked
- [ ] Deletion works from the web page with the app uninstalled
- [ ] Block and report work and are easy to find (§35.3.1)
- [ ] Data safety form matches the app's real behaviour
- [ ] **Pen test or at minimum a careful security review** — not claimed in the
      data safety form (§35.4), but worth doing before strangers use it
- [ ] NexLink's own listing unchanged and unaffected

---

## 35.8 If it is rejected

1. **Read the actual policy cited.** Not the summary email.
2. **Do not resubmit unchanged.** Repeated identical submissions are themselves
   a signal.
3. If the issue is the UGC declaration (§35.3), the options are: clarify the
   wording, strengthen the reporting mechanism, or — the outcome §33.10 names as
   a stopping condition — conclude it cannot be satisfied without breaking
   encryption.
4. **Never weaken encryption to satisfy a review.** That is a §2.8 invariant and
   a D2 reversal. If it genuinely comes to that, the answer is not to ship.
5. Appeal with specifics if the rejection appears mistaken.

**Under no circumstances resolve a Social rejection by moving the feature into
the NexLink package.** That is the exact scenario D1 exists to prevent, and the
pressure to do it will be highest at precisely the moment it is most dangerous.

---

## 35.9 Ongoing obligations

| | |
|---|---|
| Target API level | Play raises the requirement yearly. NexLink already targets 36 against this deadline. |
| Policy changes | Reviewed as announced |
| Data safety | Updated when data handling changes, not at the next release |
| Account deletion URL | Must keep working. **A dead deletion page is a policy violation**, and it is a static page nobody will notice has broken — put it in §29.7's quarterly review. |
| Annual declarations | Play periodically re-requires confirmation of sensitive declarations |
