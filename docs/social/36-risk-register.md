# §36 — Risk register

> **Confidence:** `DURABLE`
> Reviewed at each phase boundary (§33) and at the quarterly review (§29.7).

---

## 36.0 How to read this

Risks are scored **likelihood × impact**, both High/Medium/Low, and ordered by
severity. Each carries a mitigation and, where one exists, a **trigger** — the
observable event that means the risk is materialising and the contingency should
start.

A risk without a trigger is a worry. A risk with one is manageable.

---

## 36.1 The top five

These are the ones that would end or seriously damage the project. Everything
else is a schedule problem.

### R1 — No offsite backup

| | |
|---|---|
| Likelihood | Low (per year) |
| Impact | **Catastrophic** |
| Score | **HIGH** |

Both pools are in one chassis (§23.5). Fire, theft or a dead PSU loses
everything: the database, the media, and `signing.key` — which is
unrecoverable (§21.6) and whose loss means the homeserver ceases to exist as an
identity. Users on the no-email recovery path (§7) lose their history even
though their phones are fine.

This was **already the largest open risk on Willard before Social existed**, and
Social raises the stakes on it.

- **Mitigation:** Backblaze B2 before phase 5 (§33.6). The existing
  `proton-backup.sh` is provider-agnostic — three variables at the top. Proton
  was abandoned for a documented reason (Starlink CGNAT triggers its CAPTCHA)
  and should not be retried.
- **Trigger:** phase 5 beginning. This is a **blocker**, not a nice-to-have.

### R2 — Play rejects the UGC declaration

| | |
|---|---|
| Likelihood | **Medium** |
| Impact | High |
| Score | **HIGH** |

§31.4's tension made concrete: Play's UGC policy expects content moderation; an
E2EE service cannot provide it (§31.8, §35.3).

- **Mitigation:** honest declaration, excellent blocking and reporting, published
  timelines that are actually met, precedent from other E2EE messengers.
- **Trigger:** rejection citing UGC policy. **Contingency:** §35.8 — clarify,
  strengthen reporting, and if it cannot be satisfied without breaking
  encryption, §33.10's stopping condition applies. **Never move the feature into
  the NexLink package.**

### R3 — The operator's time is the real ceiling

| | |
|---|---|
| Likelihood | **High** |
| Impact | Medium |
| Score | **HIGH** |

One person, a day job, and an operational surface spanning moderation (§31.5),
incident response (§30), upgrades (§29.3) and support (§32.5). §28.7 observes
this is likely to bind long before any infrastructure limit.

- **Mitigation:** invite quotas cap growth structurally (§9.5); runbooks reduce
  the cost of each operation (§29); timelines are set to what one person can
  keep (§31.5).
- **Trigger:** report backlog exceeding the §31.5 timelines two months running.
  **Contingency:** stop issuing invites. Growth is a tap this product can turn
  off, which is a genuine advantage of D4.

### R4 — SDK churn or a bad SDK choice

| | |
|---|---|
| Likelihood | Medium |
| Impact | High |
| Score | **MEDIUM-HIGH** |

§11.1: the SDK is the substrate, and changing it later is close to rewriting the
client.

- **Mitigation:** the §11.6 seam — one module to rewrite, not an application;
  the §11.7 decision procedure; §33.3's timebox. Evidence gathered 2026-09-11
  favours the Rust bindings (released two days prior, against seven months for
  the alternative).
- **Trigger:** an SDK upgrade costing more than two days, twice running. §11.9
  asks for this to be measured deliberately once.

### R5 — Willard is a shared production host

| | |
|---|---|
| Likelihood | Medium |
| Impact | High |
| Score | **MEDIUM-HIGH** |

§21.8. The box runs a live point-of-sale system, a Nextcloud with real user
data, customer websites and six Minecraft servers. **There is no swap**, so
memory overcommit means the OOM killer rather than slowdown — and the OOM killer
does not choose politely.

- **Mitigation:** explicit resource limits set **through the API** so they
  survive redeploys (§22.6); a dataset quota on media (§25.5); no host
  networking for Social containers.
- **Trigger:** any OOM kill on Willard, or the media dataset passing 80%.
- **Note:** the host has an existing, documented instance of this class of
  problem — `crafty-4`'s memory cap was raised live with `docker update`, which
  the middleware does not know about, so any redeploy silently reverts it. Do
  not create a second such discrepancy.

---

## 36.2 Technical risks

| Risk | L | I | Mitigation | Trigger |
|---|---|---|---|---|
| **Calling stack churn** | High | Medium | §17.6 leans toward the widget so upstream carries compatibility; §33.9.1's messaging-only cut is clean | A calling upgrade breaking interop twice |
| **Android-to-web call interop fails** | Medium | High | §17.6.1's spike gates on exactly this, before the phase is committed | Spike step 2 fails |
| **Push delivery unreliable** | Medium | Medium | §13; measured in phase 6 (§27.4.1) | Delivery ratio trending down |
| **FGS crash on an OEM** | Medium | Medium | §15.2's pattern, already proven in this codebase; §34.4 forces every early-return path | Any `ForegroundServiceDidNotStartInTime` in the wild |
| **Local store corruption** | Low | High | SDK-owned; §12.8 migration discipline | Users reporting lost history |
| **Key backup does not restore** | Low | **Catastrophic per user** | §33.3 gates the SDK choice on it; §33.4 tests it | Any failure in phase 3 |
| **Media store fills the pool** | Medium | High | Quota at creation (§25.5), alert at 80% | 80% alert |
| **Cloudflare 100 MB cap surprises users** | High | Low | Client-side 95 MB cap with a clear message (§25.2) | — |
| **Synapse upgrade breaks** | Medium | Medium | Dump first, always (§29.3) | — |
| **Starlink upload bounds media** | Medium | Low | §28.7 — measure it | Upload timeouts reported |

---

## 36.3 Operational risks

| Risk | L | I | Mitigation | Trigger |
|---|---|---|---|---|
| **No alerting exists today** | — | High | §27.2 — SMTP is a phase-5 blocker | Phase 5 |
| **Restore never tested** | Medium | **Catastrophic** | §23.6.3 quarterly drill, timed and recorded | Phase 1 acceptance |
| **Willard offline (Starlink, power)** | Medium | Medium | Users' local history is intact throughout (§12.2); say so during outages | — |
| **Server compromise** | Low | High | §30.3. Content is not exposed — that is the payoff of the encryption posture | — |
| **`signing.key` lost** | Low | **Catastrophic** | Back up on day one (§22.3), off Willard | — |
| **Keystore lost** | Low | **Catastrophic** | §10.5.1 — loses *both* products and the cross-app link | — |
| **Cloudflare tunnel routing lost** | Low | Medium | It lives in a dashboard, not in version control (§26.5.1). Keep a written record in `infra/runbooks/` | — |
| **Bus factor of one** | — | High | Runbooks (§29). Partially mitigated at best; §29.9 lists what has none | — |

---

## 36.4 Product and legal risks

| Risk | L | I | Mitigation |
|---|---|---|---|
| **Users lose history and blame the product** | **High** | Medium | §7.3's warnings are designed for this; §32.5's support response is written in advance. This *will* happen — the goal is that it was understood beforehand |
| **No-email path chosen carelessly** | High | Medium | §7.3.3's confirmation gate; §33.7 tests comprehension with real users |
| Regulatory obligation the operator cannot meet | Medium | High | §31.4's honest position; keep the service small and closed (§31.4.1) |
| Illegal content on the service | Low | **High** | §31.4 — cannot detect, will refer. Invite tree gives accountability |
| Data breach notification deadline missed | Low | High | §30.6 — template written in advance, timestamp of awareness recorded |
| Domain lapses | Low | **Catastrophic** | §26.2 — `server_name` is permanent; a lapsed domain is a dead homeserver. Auto-renew and a calendar reminder |
| Nobody uses it | Medium | Low | It is invite-only; small is the design |

---

## 36.5 Risks that were accepted deliberately

Not to be re-raised as discoveries. Each was a decision.

| Accepted | Decision |
|---|---|
| No federation — the product is an island | D5 (§2.6) |
| No call recording | D3 (§2.4) |
| No server-side search | §1.3 |
| Growth capped by invites | D4 (§2.5) |
| Two apps, two listings, an install step | D1 (§2.2) |
| Larger download for `:social` | §1.4.3, mitigated by ABI splits |
| A recurring VPS cost for calling | §17.4, ~AUD 150/yr |
| The operator can see the social graph | §3.7, disclosed at the gate (§9.6.1) |
| The invite tree is readable personal data | §9.4, §32.3.3 |

---

## 36.6 Review

At every phase boundary (§33) and quarterly (§29.7):

1. Has any trigger fired?
2. Has a likelihood or impact changed with new evidence?
3. Is there a new risk?
4. **Can anything be closed?** A register that only grows is not being used.

Phase-boundary review is the more valuable of the two, because each phase exists
to resolve an uncertainty — and a resolved uncertainty should visibly retire a
risk.
