# §21 — Reference topology

> **Confidence:** `DURABLE`
> The split in §21.2 follows from a physical fact about the host's connectivity
> and is not a preference. Sizing and versions are `REVISABLE`.

---

## 21.1 The host

The service runs on **Willard**, a TrueNAS SCALE appliance the operator already
runs, administered through the middleware API. Verified 2026-09-11:

| | |
|---|---|
| Release | TrueNAS-25.10.5 |
| CPU | 12 cores |
| RAM | 61 GB (41 GB in use, ~20 GB available; ZFS ARC is reclaimable) |
| Pools | `Pool1-MAIN` and `Pool2-BACKUP`, both ONLINE, ~1.5 TB free each |
| Replication | `Pool1-MAIN` → `Pool2-BACKUP` every 15 minutes, 2-week retention |
| Docker | 28.3.1, via TrueNAS apps |
| Existing apps | 14 running, including `cloudflared` and `nextcloud` |
| Egress | **Starlink CGNAT** — `65.181.12.240`, AS14593, shared |
| IPv6 | **None.** No global address, no default route |

Willard is chosen because it exists, it is already administered, it already has
a Cloudflare Tunnel with public hostnames on it, and it has replication and
capacity to spare. The alternative — renting a homeserver VPS — costs money
monthly to duplicate infrastructure the operator already runs well.

**The appliance rule applies.** Everything in Part VI is deployed through the
TrueNAS middleware API (`app.create` / `app.update`) or the web UI. Nothing is
hand-installed into the host filesystem; `/etc` is regenerated on update and
anything placed there is lost. Custom apps with host paths on `Pool1-MAIN` are
the established pattern on this box — `crafty-4` and `playit` both work this way
— and Social follows it.

---

## 21.2 The split, and why there is one

**Willard cannot host the media path.** §17.4 establishes this in full; the
summary is that the box is behind CGNAT with no IPv6, so nothing on the internet
can open a connection *to* it. Everything it publishes today reaches the world
by dialling out — Cloudflare Tunnel for HTTP, playit.gg for Minecraft UDP.

HTTP survives that arrangement. A WebRTC SFU does not: participants must reach
it inbound on UDP, and it must advertise a routable address in its ICE
candidates. There is no address to advertise.

So:

```
          ┌───────────────────────────────────────────────────────┐
          │  WILLARD  (TrueNAS, Starlink CGNAT, no inbound)        │
          │                                                       │
          │   synapse ── postgres ── media store (Pool1-MAIN)      │
          │      │                                                │
          │   element-web      invite-service      lk-jwt-service  │
          │      │                  │                   │         │
          │      └──────────────────┴───────────────────┘         │
          │                         │                             │
          │                    cloudflared  ──── outbound only ───┼──┐
          └───────────────────────────────────────────────────────┘  │
                                                                     │
   ┌──────────────────────────────┐                    ┌─────────────▼────────┐
   │  VPS  (public IPv4, ~AU)     │                    │  Cloudflare edge     │
   │                              │◄───── media ───────│  (HTTPS / WSS only)  │
   │   livekit SFU ── coturn      │       UDP          └──────────┬───────────┘
   └──────────────┬───────────────┘                               │
                  │                                               │
                  └──────────────── clients ──────────────────────┘
                        Android app  ·  Element Web
```

| Runs on Willard | Runs on the VPS |
|---|---|
| Synapse | LiveKit SFU |
| PostgreSQL | coturn |
| Media repository | |
| Element Web | |
| Invite service (§9.2.2) | |
| lk-jwt-service | |

**lk-jwt-service belongs with Synapse, not with the SFU.** It validates Matrix
identities against the homeserver and issues LiveKit tokens; it is an HTTP
service with no media path. Keeping it on Willard keeps its homeserver call
local and keeps the VPS to one job.

### 21.2.1 The sequencing consequence, which is good news

Messaging touches none of the VPS. **Phases 1–3 of §33 run entirely on Willard**
— homeserver, invites, acceptance gate, client, web client, encrypted messaging
end to end. The VPS is provisioned in phase 4 when calling starts.

That means the recurring cost of this project is **zero until calls are built**,
and the CGNAT problem, although real, does not block anything for months.

### 21.2.2 If the VPS is unacceptable

The honest alternatives, none of them good:

| Option | Verdict |
|---|---|
| Public IP from Starlink | Ties the product's availability to one ISP option and still routes media over Starlink's jitter. Not recommended, but it would work. |
| Relay UDP through playit.gg | Already proven on this box for Geyser. Wrong tool: a hobby relay in the media path of a product, with no capacity guarantee and a terms-of-service question. |
| Managed TURN (e.g. Cloudflare Realtime) | Solves NAT traversal, does not remove the need for a SFU somewhere reachable. Complements a VPS; does not replace it. |
| **No calling** | Ship §1.2 messaging only. A real option — it removes Part IV entirely — but it drops a headline feature. |

The VPS is roughly the price of a coffee a month (§28.4) and is the
straightforward answer.

---

## 21.3 D5 — federation stays off

Restating §2.6 here because this is the chapter someone reads when configuring
the server, and it is a one-line mistake.

`federation_domain_whitelist: []` and no federation listener. The consequences:

- Willard never receives unsolicited inbound traffic from other homeservers,
  which on a CGNAT box it could not anyway — **federation would not work here
  even if it were wanted**, because federating servers must reach each other
  inbound.
- Resource consumption stays proportional to the actual user base (§28).
- Port `8448` is never published.

That last point is a genuine architectural alignment: the network constraint
that forces the SFU off-box is the same one that makes federation impossible,
and D5 had already chosen not to want it. The topology is consistent with
itself.

---

## 21.4 Port allocation

Willard's `80xx` series is in use as follows, verified live 2026-09-11 with
`ss -tlnp`:

```
8010 8011 8012 8013 8014 8015   8020   8030 8031 8032   8040   8051
```

**Social takes `8060–8069`.**

The 801x run was tried first (2026-09-11) and abandoned: Social needs **four**
published ports, `8016–8019` is only a four-slot run because `8020` is
techcomplete, and leaving no headroom in a block is how the next service ends up
scattered. A fresh decade is legible and has room.

| Port | Service | Published | Public route (§26.2.3) |
|---|---|---|---|
| `8060` | Synapse client-server API | yes | `/_matrix/*`, `/.well-known/matrix/client` |
| `8061` | Element Web | yes | `/` |
| `8062` | Invite service (§9.2.2) | yes, phase 3 | `/invite/*` |
| `8063` | lk-jwt-service | yes, phase 4 | `/livekit/jwt` |
| `8064` | Synapse Prometheus metrics | **no — see below** | none, ever |
| `8065–8069` | Reserved | | |

**The metrics port is deliberately not published at all.** An earlier draft had it
published and LAN-only. Not publishing it is strictly stronger — it cannot be
reached from the LAN either, so a tunnel misconfiguration cannot expose it — and
it is still scrapeable on the container's Docker IP from the host, which is where
netdata already runs. Verified 2026-09-11: `172.16.13.3:8064/_synapse/metrics`
answers 200. Metrics endpoints leak topology and are routinely left
unauthenticated; the cheapest way to get that right is to not publish one.

PostgreSQL is **not** published. It is reachable only on the app's internal
Docker network. Publishing a database port on a host that also serves SMB to the
LAN is an unforced error.

§"Don't widen that range casually" from the host's own operating notes applies:
each published port costs about 4.5 MB in `docker-proxy` processes. Ten ports is
fine; do not publish a range.

---

## 21.5 Storage layout

A new top-level dataset, following the `Pool1-MAIN/minecraft` and
`Pool1-MAIN/files` pattern:

```
Pool1-MAIN/social/
├── postgres/          ← see §23.2 — ix_volume, NOT a host path
├── media/             ← Synapse media repository (§25)
├── synapse/
│   ├── homeserver.yaml
│   ├── log.config
│   └── signing.key    ← §21.6
├── element-web/
│   └── config.json
├── invite-service/
│   └── data/          ← invite_record, acceptance_record (§9.2.2, §9.6.2)
└── backups/           ← pg_dump output (§23.4)
```

Created via `pool.dataset.create`, not `mkdir`. A dataset gets its own
snapshots, its own quota and its own place in the replication task; a directory
inside another dataset gets none of that.

**Ownership:** every container runs as a specific uid, and guessing it is the
established way to lose an afternoon on this box. §22.5 covers it; the short
version is that Synapse's image runs as `991`, not `568`, and the PostgreSQL
trap is severe enough to have its own section (§23.2).

### 21.5.1 Replication

`Pool1-MAIN/social` is covered by the existing recursive snapshot task (id 1,
every 15 minutes, 2-week retention) and replication task (id 1, `MAIN-to-BACKUP`)
automatically, because both are recursive on `Pool1-MAIN`. No new task is needed
and creating one would duplicate work.

**This is not a backup.** §23.4 is explicit about why a ZFS snapshot of a
running PostgreSQL data directory is not a restorable database, and §23.5 about
why two pools in one chassis is not an offsite copy.

---

## 21.6 The secrets, and which of them are unrecoverable

| Secret | Where | Losing it means |
|---|---|---|
| **`signing.key`** | `Pool1-MAIN/social/synapse/` | The homeserver's identity. Generated once at first start. Replacing it invalidates every device signature on the server. **Effectively unrecoverable.** |
| `macaroon_secret_key` | `homeserver.yaml` | All access tokens invalid; every user signed out everywhere |
| `form_secret` | `homeserver.yaml` | Minor; regenerable |
| PostgreSQL password | App config | Regenerable with a DB password change |
| LiveKit API key/secret | VPS + lk-jwt-service | Regenerable; must match on both sides |
| Cloudflare tunnel token | `cloudflared` app | Regenerable from the dashboard |
| **`nexlink-release.jks`** | Operator's machine | §10.5.1. Both apps, and the cross-app link, irrecoverable |

`signing.key` is the one that deserves alarm. It is generated silently on first
start, it is a small text file, and it is the root of the server's cryptographic
identity. It must be backed up **the day the server first starts**, off Willard,
and it must never be regenerated to "fix" something.

Note the interaction with the operator's existing backup arrangements: the
`backup-tools/staging` directory on this box already holds a copy of
`pwenc_secret`, and there is **no offsite backup at all** (§23.5). Adding a
homeserver signing key to a box with no offsite copy raises the stakes on a gap
that was already the largest open risk on the appliance.

---

## 21.7 What is deliberately not deployed

| Not deployed | Why |
|---|---|
| Synapse workers | Single-process handles this scale comfortably (§28). Workers are an answer to a load problem that does not exist. |
| A separate reverse proxy | TrueNAS already runs nginx on 80/443 and cloudflared terminates public traffic. A third proxy is a third place for a routing bug. |
| Federation listener (8448) | §21.3 |
| An identity server | §1.3 |
| A TURN server on Willard | §17.4. It would advertise an unroutable address. |
| ~~Sygnal (push gateway)~~ | **WRONG — corrected 2026-09-13. Sygnal IS deployed** (app `nexlink-social-push`, port 8062). "FCM direct" is not implementable: Synapse ships only `emailpusher` and `httppusher`, so it cannot reach FCM at all. See §13.3.4. |
| Redis | Only needed for workers. |
| Object storage for media | §25.3. Filesystem on a replicated pool, until the pool is the constraint. |

---

## 21.8 Blast radius

Willard hosts the operator's SMS product infrastructure, a Nextcloud with real
user data, three customer-facing websites, a live point-of-sale system and six
Minecraft servers. **Adding a homeserver adds a failure mode to all of them.**

Specific risks and their controls:

| Risk | Control |
|---|---|
| Synapse or Postgres eats the RAM | Explicit container memory limits (§22.6). Note the host has **no swap** — overcommit means the OOM killer, not slowdown. |
| The media store fills `Pool1-MAIN` | Dataset quota on `Pool1-MAIN/social/media` (§25.5), set at creation, not after |
| Snapshot churn from a busy database | §23.4 — the database is dumped for backup, not snapshotted for it |
| An `app.update` on a Social app restarts something else | It does not; apps are independent. But note the box's existing trap: an `app.update` on `crafty-4` kills every Minecraft server. Nothing here touches that app. |
| A compromised homeserver reaches the LAN | §30.3. Social apps get no host networking, unlike `playit`. |

**The memory row is the one to watch.** The host's memory cap notes record that
`crafty-4`'s container limit was raised to 16 GiB live, and that the middleware
still believes it is 10240 M — so any redeploy of that app silently drops it
back and overcommits six running JVMs. Social's containers must carry explicit
limits set *through the API*, so they survive a redeploy rather than depending
on a runtime adjustment nobody remembers.

---

## 21.9 Open questions

- ~~**Domain name.**~~ **Closed 2026-09-11: `nexlink.thvjq.com.au`** (§26.2.1).
  One hostname fans out to every service by path, because Cloudflare's free
  Universal SSL does not reach a second-level subdomain (§26.2.2).
- **VPS provider and region.** §24.2 leans Australian for latency. Unresolved
  until phase 4.
- **Should the invite service be a separate app, or a route inside a small
  application container alongside Synapse?** Separate is cleaner and costs a
  container. Leaning separate — it is the only unauthenticated write path (§9.7)
  and deserves its own blast radius.
