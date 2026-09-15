# Legal documents — §4.7

**These are DRAFTS prepared for the operator's review. None has been reviewed by
a lawyer, and they were written by an AI assistant that is not qualified to give
legal advice.**

What they *are*: an accurate description of how this service actually behaves,
written from measured findings rather than from a template. Every factual claim
traces to something verified against the running system — reactions really are
unencrypted, the push service really is told a message arrived, IP logs really
are purged at deletion rather than left to expire. That accuracy is the part a
lawyer cannot supply for you, and it is what makes these worth reviewing rather
than replacing.

| Document | Version | Purpose |
|---|---|---|
| `terms-of-service.md` | 0.2.0-draft | The agreement: what the service is, eligibility, acceptable use, content ownership, availability, termination, liability, governing law |
| `privacy-policy.md` | 0.2.0-draft | Every data category, lawful basis, retention, third parties, security, rights |
| `content-policy.md` | 0.2.0-draft | Prohibited content and conduct, enforcement, appeals — required by Play's UGC policy |
| `data-retention-schedule.md` | 0.2.0-draft | Every category and how long it is kept. Forms part of the Privacy Policy |
| `transparency-note.md` | 0.2.0-draft | What the operator can and cannot see, in plain language |
| `law-enforcement-guidelines.md` | 0.2.0-draft | What data exists, what does not, and how requests are handled |
| `security-disclosure-policy.md` | 0.2.0-draft | How to report a vulnerability, and what to expect |

The first four form the agreement (ToS §1.3). The last three are explanatory but
accurate, and users may rely on them as descriptions of behaviour.

## Before publication

**Placeholders — both filled 2026-09-15:**
- Minimum age is **16**, in the Terms of Service §3.1. `AgeCheck.MINIMUM_AGE`
  already enforced 16, so the gate and the agreement now say the same thing.
  **They must be changed together or not at all** — one is what the gate
  enforces, the other is what the user agreed to.
- The Operator's contact address is **google.alumni829@passmail.net**, in the
  Privacy Policy, the Security Disclosure Policy and the Law Enforcement
  Guidelines. It previously read "the address given in the app", which pointed
  at something that did not exist.

**Decisions to make:**
- Whether to publish the Law Enforcement Guidelines publicly. Publishing is the
  more transparent choice and sets expectations before a request arrives;
  not publishing avoids advertising what is retained.
- Whether transparency reporting (Security §8, Law Enforcement §8) is a
  commitment you want to make, given it must then be honoured.

**Review needed by someone qualified:**
- The Australian Consumer Law section (ToS §9.1). Those guarantees cannot be
  excluded however the document is worded, and the draft does not pretend
  otherwise — but the limitation wording should be checked.
- Whether the Privacy Act 1988 applies at this size. The draft deliberately
  assumes it does, because claiming an exemption as a reason to be less careful
  is not defensible for a privacy-positioned service.
- The indemnity (ToS §9.4) and the discontinuation notice period (ToS §7.5).

**Version discipline:** §4.7 requires a semantic version on each document,
recorded against every acceptance (§9.6.2), so re-acceptance on material change
is mechanical. These are `0.2.0-draft` and must reach `1.0.0` before §33.7's
private launch.

## Do not publish as they stand

§9.6.1's in-app screen already tells users the truth about what the operator can
and cannot see. A published policy that contradicts that screen would be worse
than having no policy at all.
