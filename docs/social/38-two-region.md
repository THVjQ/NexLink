# §38 — Two regions: Switzerland and Australia

**Status: report, not a plan of record.** Written 2026-09-20 in answer to "how
could a CH server and an AU server be synced and work together, since latency
from overseas is bad". Nothing here is built.

---

## 38.1 The question behind the question

Sydney to Zurich is roughly **250–290 ms round trip** on a good path, and the
current server is behind Starlink, which adds its own 40–60 ms and jitter. For a
messenger that matters in three different ways, and they have three different
answers:

| What the user feels | Caused by | Fixable by a second server? |
|---|---|---|
| Message send feels sluggish | RTT to **their own** homeserver | **Yes** — this is the whole point |
| Message takes a moment to arrive for the other person | RTT **between** the two servers | No, and it does not matter — delivery is asynchronous |
| Call audio delayed or choppy | RTT to the **media relay** (SFU) | **Yes**, and it matters more than messaging |

Only the first and third are worth building for. The second is a red herring: a
message that takes 300 ms longer to reach the far side is invisible, because
nobody is waiting synchronously for it.

**The call path is the real prize.** A Swiss user calling another Swiss user
currently sends audio to Sydney and back — roughly 600 ms of avoidable round
trip, which is the difference between a usable call and an unusable one.

---

## 38.2 The constraint that shapes everything: Synapse has one writer

This is the fact that rules out the obvious design. **Synapse cannot run
active-active.** Its workers scale reads and background work, but they all share
**one Postgres primary**, and that primary must be a single writable node. There
is no multi-master Synapse, and Postgres streaming replication gives a read
replica, not a second writer.

So "two servers, synced, both equal" is not achievable for one homeserver. What
is achievable is one of two quite different things.

---

## 38.3 Option A — one homeserver, second site as warm standby

`nexlink.thvjq.com.au` stays the only homeserver. Switzerland runs Postgres
streaming replication and a stopped Synapse, promoted by hand if Australia is
lost.

- **Solves:** disaster recovery, and the "both pools in one chassis" gap that
  §29 already calls the largest open risk.
- **Does not solve:** latency. Every Swiss user still talks to Sydney for
  everything. This is a backup plan wearing a second server's clothes.
- **Cost:** low. One VPS, one replication stream, a documented promotion runbook.

**Worth doing regardless**, and worth doing *first*, because it is useful on its
own and is a prerequisite for anything harder.

---

## 38.4 Option B — two homeservers that federate

The Matrix-native answer, and the only one that fixes latency.

```
@luca:nexlink.thvjq.com.au     ← Australian users, server in Sydney
@someone:ch.nexlink.thvjq.com.au ← Swiss users, server in Switzerland
                ↕ federation over 443
```

Each user's client talks to a server near them. Rooms containing both are
replicated to both servers by federation, so **reads are always local** — the
Swiss user's history lives on the Swiss server. E2EE is unaffected; Megolm does
not care which server relays the event.

### What it costs

- **User IDs differ by region and are permanent** (ToS §3.5). A user cannot
  move between servers without a new identity. Choosing a user's home region at
  signup is a decision they cannot undo.
- **Federation must be turned on.** Today it is deliberately off, and this is
  not a config detail:
  - Privacy Policy §6.1 states *"No federation — the server does not exchange
    data with other Matrix servers"*. That sentence becomes false.
  - `social-drift-check` **asserts federation is not publicly reachable** and
    would fail daily until rewritten.
  - Federating with only one known partner is not the same as joining the public
    Matrix network. The design should be a **federation allowlist of exactly two
    servers**, which keeps the invite-only property intact and keeps the privacy
    claim honest in a revised form: *"federates only with the operator's own
    second server, and with nothing else."*
- **Two of everything to operate**: two Synapse instances, two Postgres, two
  module deployments, two backup chains, two sets of drift checks. The
  `social-sync` sidecar (§31.7) already solves the module half — point a second
  sidecar at the same repo and both servers converge on the same code.

### What it does not cost

- No shared database. Federation *is* the sync; there is no replication stream
  between the two homeservers, and no split-brain to resolve.
- No change to the apps. A Matrix client does not care that its homeserver
  federates.

---

## 38.5 The calling path, which is the actual win

Calls are LiveKit SFU (§17–§19), currently one instance in Sydney.

Each homeserver should advertise **its own LiveKit focus**. MatrixRTC selects a
focus per call, so:

- CH↔CH call → Swiss SFU. ~10–30 ms. This is the improvement worth building for.
- AU↔AU call → Sydney SFU. Unchanged.
- CH↔AU call → one region's SFU; one side pays the long path either way. Pick
  the caller's, or the majority's.

**This is deliverable without Option B.** A second LiveKit in Switzerland can be
added to the existing single-homeserver deployment, because the SFU is not the
homeserver. **If only one thing gets built, build this** — it is the largest
user-visible gain for the least structural change.

---

## 38.6 The obstacle nobody can design around: CGNAT

Willard is behind **Starlink CGNAT with no inbound and no IPv6** — this is why
federation, TURN and an SFU cannot be hosted there directly today, and it is
recorded as a standing constraint.

Federation requires the far server to *reach in*. Willard cannot accept that
directly. It can be done through the existing Cloudflare Tunnel, since
federation is HTTPS and `.well-known/matrix/server` can delegate to port 443 —
but it means **every federated event between the two servers traverses
Cloudflare**, which is a third party in the path of data that is currently
described as not leaving the operator's own infrastructure.

A Swiss VPS, by contrast, would have a real address and no such problem.

**This inverts the natural assumption.** The Swiss server is the better-connected
one. If the two are ever unequal, Switzerland should be primary and Australia
the tunnel-fronted satellite — not the other way round because Australia is
where the operator lives.

---

## 38.7 What two regions does to the legal pack

Mostly **good**, and cheaper than expected, because §3.3a was written keyed to
*where the Service is operated from* rather than to where the user lives:

- A Swiss homeserver is a Swiss operation. **BÜPF's six-month retention applies
  to it and to its users**, which §3.3a already describes.
- The Australian homeserver keeps 28 days for its own users.
- Two servers make the clause **more** honest, not less: today it warns that one
  jurisdiction's rule would apply to everyone. With per-region servers, each
  user's data sits under the law of the region they signed up in.

**Still needed before any Swiss server holds data:** the BÜPF classification
question (README, blocking), and confirmation of Australia's adequacy status
under the Swiss Federal Council's list, because federation between the two would
be a continuous transfer in both directions.

---

## 38.8 Recommended order

1. **A second LiveKit in Switzerland** (§38.5). Biggest user-visible win,
   smallest change, no federation, no legal change, reversible.
2. **Postgres streaming replication to a Swiss VPS** (§38.3). Closes the
   single-chassis risk in §29. Useful whether or not step 3 ever happens.
3. **Settle BÜPF** before Swiss infrastructure holds user data.
4. **Federated second homeserver** (§38.4) — only if Swiss *messaging* latency
   proves to be a real complaint rather than an assumed one. Measure first: the
   messaging half of this problem may not be worth the operational doubling.

Steps 1 and 2 are independently valuable and neither commits to step 4.
