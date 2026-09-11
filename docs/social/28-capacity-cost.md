# §28 — Capacity planning and cost model

> **Confidence:** `DURABLE`
> The *shape* of the cost model is durable — which costs are fixed, which scale
> with users, and which scale with call-minutes. The absolute numbers are
> estimates and are labelled as such.

---

## 28.1 The question this chapter answers

§1.5's fifth success criterion: *operating cost per active user is known and
does not scale super-linearly with user count.*

Two reasons that criterion exists. First, an invite-only service (§2.5) grows at
a rate the operator controls, which is only useful if the operator knows what
each new user costs. Second, the failure mode of a hobbyist service is a bill
that arrives before anyone noticed the growth.

---

## 28.2 Scale assumptions

Stated explicitly so that every number below can be re-derived when they turn
out wrong.

| | Year 1 | Year 2 |
|---|---|---|
| Registered accounts | 50 | 250 |
| Monthly active | 35 | 175 |
| Daily active | 20 | 100 |
| Messages/active user/day | 50 | 50 |
| Media uploads/user/week | 10 at ~2 MB | 10 at ~2 MB |
| Call minutes/user/month | 60 | 60 |
| Devices per user | 2 | 2 |

These are deliberately modest, and they follow from D4: invite quotas (§9.5)
cap growth at 3–5 invites per established account. **The invite quota is the
capacity control**, which is an unusually pleasant property — the same mechanism
that limits abuse limits cost.

---

## 28.3 Where the cost actually is

### 28.3.1 Willard — effectively free

| Resource | Year 1 estimate | Willard has |
|---|---|---|
| Synapse RAM | 300–800 MB | 61 GB, ~20 GB available |
| PostgreSQL RAM | 200–500 MB | |
| CPU | < 0.5 core average | 12 cores |
| Database on disk | 1–5 GB | 1.5 TB free |
| Media | 35 users × 10/wk × 2 MB × 52 ≈ **36 GB/yr** | 200 GB quota (§25.5) |

**Marginal cost: zero.** The box is bought, powered and running fourteen other
things. Social is noise on it.

This is the whole argument for hosting on Willard, and it is a strong one. The
counter-argument is §21.8 — blast radius on a box that also runs a
point-of-attention POS system and customer websites — which is a risk cost
rather than a money cost, and is managed with resource limits and quotas rather
than by paying rent elsewhere.

Storage is the only thing that grows without bound, at roughly **36 GB per year
at year-1 scale, 180 GB/yr at year-2 scale.** The 200 GB media quota is
therefore about five years at year-1 scale or one year at year-2 scale. Fine,
and worth re-reading when the second year arrives.

### 28.3.2 The VPS — the only real bill

| | |
|---|---|
| Instance | 2 vCPU / 4 GB, Australian region |
| Cost | **~AUD 10–15/month** |
| Transfer included | 2–4 TB/month |

Bandwidth is the constraint, not CPU (§24.2). The arithmetic, which is the most
useful thing in this chapter:

**A 4-participant video call**, with simulcast (§17.8) so each subscriber pulls
a modest layer:

```
each participant receives 3 streams × ~400 kbps   = 1.2 Mbps
SFU egress = 4 participants × 1.2 Mbps            = 4.8 Mbps
                                                  ≈ 0.6 MB/s
                                                  ≈ 2.2 GB per hour of call
```

**A 1:1 video call:**

```
each receives 1 stream × ~800 kbps = 0.8 Mbps
SFU egress = 2 × 0.8               = 1.6 Mbps ≈ 0.72 GB per hour
```

So a 2 TB allowance is roughly:

| Call type | Hours/month within 2 TB |
|---|---|
| 1:1 video | ~2,700 |
| 4-way video | ~900 |
| 4-way with a screen share | ~700 |

Against §28.2's assumption — 35 active users × 60 minutes = **35 call-hours per
month** — the allowance is over an order of magnitude larger than the need. Even
year 2's 175 hours is comfortable.

**Conclusion: bandwidth is not a year-1 or year-2 concern.** The alert at 70% of
allowance (§24.7) exists to catch the thing that breaks this model — a runaway
client republishing in a loop, or a call left running for a week — rather than
organic growth.

Audio-only calls are roughly a twentieth of these figures and can be ignored
entirely.

### 28.3.3 Everything else

| | Cost |
|---|---|
| Domain (§26.2) | ~AUD 20/year |
| Cloudflare Tunnel | Free tier, already in use |
| FCM push | Free |
| Play Console | One-time USD 25, already paid for NexLink |
| Offsite backup, Backblaze B2 (§23.5) | **~AUD 1–3/month** at this data size |
| TLS certificates | Free |

---

## 28.4 Total

| | Year 1 | Year 2 |
|---|---|---|
| VPS | AUD 150/yr | AUD 150/yr |
| Domain | AUD 20/yr | AUD 20/yr |
| Offsite backup | AUD 25/yr | AUD 40/yr |
| **Total** | **~AUD 195/yr** | **~AUD 210/yr** |
| **Per active user/month** | **~AUD 0.46** | **~AUD 0.10** |

Cost **falls** per user as the service grows, because the dominant line — the
VPS — is fixed. That is the opposite of the per-seat pricing a commercial BaaS
would impose, and it is a large part of why §2.3 rejected that option.

Phases 1–3 (§33) cost the domain and nothing else, because the VPS is not
provisioned until calling starts (§21.2.1).

### 28.4.1 What is not in this model

Honesty about the real cost, which is not money:

- **Operator time.** Upgrades, moderation (§31), support, incident response
  (§30). At 50 users this is hours per month; at 250 it is not obviously still
  hours per month, and §31.6 is the chapter that worries about it.
- **Willard's electricity and depreciation**, already being paid.
- **A second operator.** There is one. §1.5 requires every routine operation to
  have a runbook (§29) precisely because the bus factor is one.

---

## 28.5 What breaks the model

| Trigger | Effect | Response |
|---|---|---|
| **A viral moment** | Invite quotas (§9.5) cap it structurally. The tree cannot grow faster than established accounts issue invites. | None needed — this is D4 working |
| Media-heavy users | 200 GB quota reached early | Raise quota, or add object storage (§25.3) |
| Long-running group calls | Transfer allowance | 70% alert (§24.7); larger allowance is cheap |
| A user farming accounts | Invite tree makes it traceable and revocable as a subtree (§9.4) | §31 |
| Federation enabled | **Would multiply everything.** Federation traffic dominates a small public homeserver's load | D5 (§2.6). Do not. |
| Synapse resource use ≫ estimate | §22.1 said to revisit conduwuit/continuwuity if so | Measure first |

The federation row is the one worth repeating. §2.6 lists it as a
resource-consumption decision, and this chapter is where that becomes concrete:
enabling it would change every number above by an amount nobody can predict from
the user count, because the load would come from other people's servers.

---

## 28.6 The scaling path, if it is ever needed

Recorded so that growth is not a crisis. In order:

1. **Raise the media quota.** One command. Buys years.
2. **Move media to object storage** (§25.3). Unbounds storage; adds a bill that
   scales with data.
3. **Move Synapse off Willard.** The trigger is blast radius (§21.8) or a
   reliability requirement Willard cannot meet on a residential Starlink link —
   not CPU. This is the expensive step and it is a VPS or a rented server, not a
   redesign.
4. **Synapse workers** (§21.7). Only after step 3, and only with metrics showing
   a single process is the bottleneck.
5. **A second SFU.** Only on sustained concurrent-session counts.

**Steps 1 and 2 are cheap and likely. Steps 3 to 5 are unlikely at any scale
this product plausibly reaches**, and should not be pre-built. §1.5's criterion
is that cost is *known*, not that it is minimised.

---

## 28.7 Open questions

- **Is 60 call-minutes per user per month right?** It is the assumption the
  bandwidth model rests on and it is the one with no evidence behind it. Measure
  in phase 5 and revise.
- **What is Willard's actual upload capacity on Starlink**, and does it bound
  media uploads or web-client use? Media goes through the Cloudflare tunnel,
  which means every uploaded file crosses Starlink twice — once from the client
  to the edge, once from the edge to Willard. **Worth measuring.** It does not
  affect calls (§21.2), but it could make a 95 MB upload slow enough to time
  out.
- **At what user count does moderation (§31) exceed the operator's available
  time?** Probably before any infrastructure limit. That is the real ceiling on
  this product and it is not a technical one.
