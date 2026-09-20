# NexLink legal documents — §4.7

**These are DRAFTS prepared for the operator's review. None has been reviewed by
a lawyer, and they were written by an AI assistant that is not qualified to give
legal advice.**

What they *are*: an accurate description of how the products actually behave,
written from measured findings rather than from a template. Every factual claim
traces to something verified against the running system — reactions really are
unencrypted, the push service really is told a message arrived, IP logs really
are purged at deletion rather than left to expire. That accuracy is the part a
lawyer cannot supply for you, and it is what makes these worth reviewing rather
than replacing.

## What changed in 0.3.0

One pack now covers **every NexLink Product**, not just NexLink Social, and every
document is published in **English and German**.

| Change | Where |
|---|---|
| One agreement across all Products | ToS §1.1, §2.1–2.6; every title |
| Minimum age **16** | ToS §3.1 — unchanged, `AgeCheck.MINIMUM_AGE` already enforced it |
| Offered only in **Switzerland and Australia**, and local law applies alongside these terms | ToS §3.7 |
| **Swiss law** throughout, alongside Australian | ToS §9.3, §10.8, §10.9; PP §1.3, §5.7, §6.3, §8 |
| **Donations** — the page exists, so "there are no payments" was no longer true | ToS §2.7; PP §3.6, §5.4; Retention §8 |
| **Contact Page** replaces the address baked into each document | every document; `web/contact/` |
| **Definitions appendix on every document**, including Terrorism and Violent Extremism | `definitions.en.md`, `definitions.de.md` |

## Structure

```
docs/legal/
  definitions.en.md   definitions.de.md    ← Appendix A, the single source
  en/  de/                                  ← the seven documents, per language
  dist/en/  dist/de/                        ← ASSEMBLED — this is what ships
tools/legal/build-legal.py                   ← appends the appendix; --check for CI
web/contact/index.html                       ← the Contact Page every document points at
```

**Edit `en/` and `de/`. Never edit `dist/`** — it is regenerated and your change
would be lost. Run `tools/legal/build-legal.py` after any edit;
`--check` exits non-zero when `dist/` is stale.

The apps ship from `dist/`, and `copyLegalDocs` fails the build if it is missing.
A document without Appendix A defines none of the terms it uses.

## Why the appendix is generated rather than pasted

Eight hand-maintained copies of a definition is eight chances for **Terrorism**
to mean one thing in the Terms of Service and another in the Content Policy. A
prohibition that means two things is precisely the vagueness these definitions
exist to remove, so the sameness is enforced mechanically and the build prints
the appendix digest on every run.

## The definitions, and why they are written that way

§A.3 defines Terrorism, Violent Extremism, Proscribed Organisation and Unlawful.
Each is anchored to statute — Criminal Code Act 1995 (Cth) s 100.1 for Australia,
StGB Art. 260ter/260quinquies for Switzerland — and **each carries an express
exclusion** for advocacy, protest, dissent, satire, journalism, research,
historical documentation and artistic depiction.

A prohibition a user cannot predict is not a fair prohibition, and enforcement
against vaguely defined conduct is both unjust and legally fragile. The
exclusions are restated in Content Policy §3.3 because that is the section most
often misread.

| Document | Version | Purpose |
|---|---|---|
| `terms-of-service.md` | 0.3.0-draft | The agreement: products, eligibility, territories, acceptable use, content, availability, termination, liability, governing law |
| `privacy-policy.md` | 0.3.0-draft | Every data category, per Product, lawful basis, retention, third parties, transfers, security, rights |
| `content-policy.md` | 0.3.0-draft | Prohibited content and conduct, enforcement, appeals — required by Play's UGC policy |
| `data-retention-schedule.md` | 0.3.0-draft | Every category and how long it is kept. Forms part of the Privacy Policy |
| `transparency-note.md` | 0.3.0-draft | What the operator can and cannot see, in plain language |
| `law-enforcement-guidelines.md` | 0.3.0-draft | What data exists, what does not, and how requests are handled |
| `security-disclosure-policy.md` | 0.3.0-draft | How to report a vulnerability, and what to expect |

The first four form the agreement (ToS §1.3). The last three are explanatory but
accurate, and users may rely on them as descriptions of behaviour.

---

## Before publication — open items

**Blocking:**

1. **Deploy the Contact Page** to `https://thvjq.com.au/nexlink/contact`. Until it
   resolves, every document cites a dead URL and the operator has no published
   address at all. The file is `web/contact/index.html`.

**Settled 2026-09-20 — the contact address:**

`google.alumni829@passmail.net` is the published address. A residential address
is **not** published, because the operator is a private individual and the
service runs from a home; it is supplied on request and to any regulator, court
or party effecting formal service. Appendix A and Privacy Policy §1.2 were
reworded so they no longer promise a postal address the page does not print.

> ⚠️ **Google Play requires a physical address separately**, and displays it on
> the public store listing for a personal developer account. That requirement is
> not satisfied by this page and is not avoided by it. If the Play address is
> already public, publishing the same one here costs nothing further; if it is
> not, the two should at least not contradict each other. Worth checking before
> the first public release.

**Review needed by someone qualified:**

- **⚠️ BÜPF / VÜPF — the six-month period is adopted, the classification is
  not.** Privacy Policy §3.3a now retains connection metadata for six months
  while the Service is operated from Switzerland, matching the period BÜPF sets
  for a provider of derived communication services. That removes the earlier
  conflict, where the policy promised 28 days that Swiss law could override.

  **What still needs a Swiss lawyer:** whether BÜPF reaches a service of this
  size and character at all. Threema was litigated into the reduced-obligation
  category; that outcome is not automatic. The clause is written as a voluntary
  adoption of the statutory period and says explicitly that it is not a claim to
  have been classified — check that wording is one a Swiss adviser is happy with,
  because claiming a classification you do not hold is its own problem.

  **Two consequences worth confirming are acceptable:**
  - The period is keyed to the **server's** location, not the user's, because
    residence is not verified and identifying "Swiss users" would mean processing
    location data the policy says is not processed. So while the server is in
    Switzerland, **Australian users also get six months**. APP 11.2 requires
    destroying personal information when it is no longer needed — retaining an
    Australian user's data for six months under a foreign obligation is
    defensible but is exactly the kind of thing to have checked.
  - Six months **survives account deletion** for connection metadata. The
    deletion clauses in Privacy Policy §7.1, the Retention Schedule and the
    Transparency Note were all amended to say so, because the promise to purge
    at deletion cannot hold against a statutory period.

- **The Law Enforcement Guidelines describe an Australian posture only.** §1 says
  the service is offered in both territories, but nothing in the document says
  what happens when a *Swiss* prosecutor or the Federal Office of Justice makes a
  request, or how mutual legal assistance between the two is handled. One
  sentence acknowledging the territory is not the same as describing the process.
  This is the least Swiss-aware document in the pack and the one where that
  matters most.

- **Swiss coverage is uneven by design, and should be checked against intent.**
  Measured across the pack, excluding the shared appendix: Privacy Policy 20
  Swiss references, ToS 14, Content Policy 1, Law Enforcement 1, Security
  Disclosure 1, Retention Schedule 0, Transparency Note 0. The two zeros are
  defensible — the Retention Schedule is factual and the Transparency Note is
  deliberately non-legal — but the Retention Schedule stops being purely factual
  the moment BÜPF applies to it.


- **The Swiss additions are the least-tested part of this pack.** ToS §9.3
  (CO Art. 100(1), UCA Art. 8), §10.8 (PILA Art. 120), and PP §5.7 and §8.
  They were written from the statutes' substance, not from Swiss precedent.
- **Whether Australia is on the Swiss Federal Council's adequacy list.** PP §5.7
  carries a review flag on this. It determines whether a Swiss-hosted service
  may transfer to Australia on adequacy or needs contractual safeguards, and it
  must be confirmed before any server move.
- **Restricting to two territories** (ToS §3.7). It is not geo-enforced, and the
  clause says so. Whether stating a restriction you do not enforce is better or
  worse than staying silent is a judgement call worth a second opinion.
- **Whether taking donations changes the consumer-law analysis.** ToS §2.7 says
  donations are a gift conferring nothing, which is intended to keep the service
  gratuitous. That reasoning should be checked.
- **The Australian Consumer Law section** (ToS §9.2) and the **indemnity**
  (§9.6). Consumer guarantees cannot be excluded however the document is worded.
- **Whether the Privacy Act applies at this size.** The draft assumes it does,
  because claiming an exemption as a reason to be less careful is not defensible
  for a privacy-positioned service.

**Decisions to make:**

- Whether to publish the Law Enforcement Guidelines publicly.
- Whether transparency reporting is a commitment you want to make, given it must
  then be honoured.

**Version discipline:** §4.7 requires a semantic version on each document,
recorded against every acceptance (§9.6.2), so re-acceptance on material change
is mechanical. These are `0.3.0-draft` and must reach `1.0.0` before §33.7's
private launch. **0.3.0 is a material change from 0.2.0** — territories, age
confirmation and payment disclosure all moved — so it requires re-acceptance,
not a silent swap.

## Do not publish as they stand

§9.6.1's in-app screen already tells users the truth about what the operator can
and cannot see. A published policy that contradicts that screen would be worse
than having no policy at all.
