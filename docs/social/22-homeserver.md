# §22 — Homeserver deployment

> **Confidence:** `REVISABLE`
> The choice of Synapse (§22.1) is now settled and `DURABLE`. Configuration
> tracks Synapse releases.

---

## 22.1 Synapse, and the end of the conduwuit question

§2.7 left this open, to be "decided on observed resource use during the spike,
not on documentation". Checked 2026-09-11, and the question has partly answered
itself:

| | Synapse | conduwuit |
|---|---|---|
| Status | `v1.160.0`, released 2026-09-02, actively developed by Element | **Archived upstream in early 2025.** Development continues under a fork, `continuwuity` |
| Language | Python | Rust |
| Resources | Heavier; comfortable at this scale (§28) | Lighter |
| Admin API | Complete, documented, stable | Partial |
| Registration tokens | Native (§9.2) | Verify before depending on it |
| MatrixRTC support | Documented, with a published config shape (§17.3.1) | Verify |
| Workers | Supported, unnecessary here (§21.7) | N/A |

**Decision: Synapse.**

The reasoning is not performance. It is that this product depends on a
specific set of server features — registration tokens (§9.2), the admin API for
the invite tooling (§9.8) and account deletion (§7.6), the MatrixRTC
configuration (§17.3.1), media retention controls (§25) — and Synapse is the
implementation those features are specified against. A lighter server that
requires verifying each of them individually trades a resource problem this box
does not have for a compatibility risk it does not need.

Willard has 61 GB of RAM and 12 cores, and §28 sizes Synapse at this user count
in the low hundreds of megabytes. **Resource use is not the binding constraint,
so it should not drive the decision.**

Revisit only if measured Synapse resource use contradicts §28 by an order of
magnitude. Record the outcome here either way; §11.7's rule applies — a spike
whose results are not written down has to be run twice.

---

## 22.2 Deployment as a TrueNAS custom app

The pattern already proven on this box by `playit` and `crafty-4`: a custom app
created through the middleware API, with host paths on `Pool1-MAIN`.

```
app name    nexlink-social-synapse
image       ghcr.io/element-hq/synapse:v1.160.0   ← exact tag, never :latest
port        8060 → 8008
host paths  Pool1-MAIN/social/synapse   → /data
            Pool1-MAIN/social/media     → /media
postgres    ix_volume  ← NOT a host path. §23.2 explains why, at length.
```

**Pinning the tag is not optional.** This is the process that holds every user's
encrypted message history and the server signing key. An unattended `:latest`
pull is an unreviewed change to it. Upgrades get a runbook (§29.3).

### 22.2.1 The app.config round-trip

Changing a deployed app's settings on this box has a known procedure, learned
expensively, and it is recorded in the host's operating notes. Restated because
it will be needed on every configuration change:

```bash
# round-trip app.config → edit → app.update
# FIRST strip the middleware-injected blocks, or the payload is ~300 KB
# and sudo fails with `argv mismatch`. Stripped it is ~2 KB.
sudo midclt call app.config nexlink-social-synapse \
  | jq 'del(.ix_context, .ix_certificates, .ix_certificate_authorities, .ix_volumes)' \
  > /tmp/cfg.json
# edit /tmp/cfg.json
sudo midclt call app.update nexlink-social-synapse "$(jq -c '{values:.}' /tmp/cfg.json)"
```

Run it from a script via `sudo bash /tmp/x.sh` — `/tmp` is `noexec` on this
host, so `sudo /tmp/x.sh` fails. Passwords come back from `app.config` in
cleartext, so the round-trip is lossless.

**Any `app.update` recreates the container.** For Synapse that means a restart
and a few seconds of downtime, which is acceptable. It is worth internalising
that this is not always true on this box — the same operation on `crafty-4`
kills six running Minecraft JVMs — so "it's just a config change" is not a safe
thing to believe in general here.

---

## 22.3 First start

Synapse generates its configuration and, critically, its signing key on first
run.

```bash
docker run --rm \
  -v /mnt/Pool1-MAIN/social/synapse:/data \
  -e SYNAPSE_SERVER_NAME=nexlink.thvjq.com.au \
  -e SYNAPSE_REPORT_STATS=no \
  ghcr.io/element-hq/synapse:v1.160.0 generate
```

Two things about this command are permanent:

1. **`SYNAPSE_SERVER_NAME` cannot be changed afterwards.** It is baked into
   every MXID, every event and every signature on the server. Changing it means
   a new server and new accounts for everyone. §26.2 decides it, and it is
   decided *before this command is run*, not after.
2. **`signing.key` is created here.** Back it up off Willard the same day
   (§21.6). It will never be more recoverable than it is at this moment, when
   nothing depends on it yet.

`SYNAPSE_REPORT_STATS=no` — the service is private (§2.6) and reporting usage
statistics to a third party contradicts what §9.6.1's screen tells users.

---

## 22.4 homeserver.yaml

The configuration that makes this a NexLink Social homeserver rather than a
generic one. Annotated with the section each line answers to; this is the file
where most of Parts I and II become real.

```yaml
server_name: "nexlink.thvjq.com.au"          # §26.2 — PERMANENT
public_baseurl: "https://nexlink.thvjq.com.au/"
pid_file: /data/homeserver.pid

listeners:
  - port: 8008
    tls: false                   # cloudflared terminates TLS (§26.4)
    type: http
    x_forwarded: true            # trust the proxy's forwarded headers
    bind_addresses: ['0.0.0.0']
    resources:
      - names: [client]          # NOTE: no `federation` — §21.3
        compress: false

  - port: 8064                   # §21.4 — LAN only, never tunnelled
    type: metrics
    bind_addresses: ['0.0.0.0']

# ── D5: federation off (§2.6, §21.3) ────────────────────────────────
federation_domain_whitelist: []
allow_public_rooms_over_federation: false
send_federation: false

# ── D4: invite-only (§2.5, §9.2) ────────────────────────────────────
enable_registration: true
registration_requires_token: true      # THE control. Without it, open.
enable_registration_without_verification: true   # no email required (§7.1)
registration_shared_secret_path: /data/registration_shared_secret

# ── §6.6, §1.3: no discovery, no directory ──────────────────────────
enable_room_list_search: false
user_directory:
  enabled: true
  search_all_users: true          # §6.6 — exact-match lookup within the server
  prefer_local_users: true

# ── §17.3.1: MatrixRTC ──────────────────────────────────────────────
experimental_features:
  msc3266_enabled: true
  msc4143_enabled: true
  msc4222_enabled: true
max_event_delay_duration: 24h
matrix_rtc:
  transports:
    - type: livekit
      livekit_service_url: "https://rtc.thvjq.com.au/livekit/jwt"

# ── §25: media ──────────────────────────────────────────────────────
media_store_path: /media
max_upload_size: 100M            # §25.2 — Cloudflare's cap, not a choice
url_preview_enabled: false       # §20.3 — server would fetch links from E2EE rooms
dynamic_thumbnails: false

# ── §9.7: rate limiting ─────────────────────────────────────────────
rc_registration:
  per_second: 0.05
  burst_count: 3
rc_registration_token_validity:
  per_second: 0.05               # ≈ 3/min — §9.7's brute-force control
  burst_count: 5
rc_login:
  address:
    per_second: 0.05
    burst_count: 5
  failed_attempts:
    per_second: 0.05
    burst_count: 3

# ── §3.7, §32: retention and privacy ────────────────────────────────
retention:
  enabled: false                 # §25.6 — deliberate, see the note there
redaction_retention_period: 7d
forgotten_room_retention_period: 28d
user_ips_max_age: 28d            # §32.4 — IP logs are personal data

# ── §27 ─────────────────────────────────────────────────────────────
enable_metrics: true
report_stats: false

# ── §12, §7.4: encryption defaults ──────────────────────────────────
encryption_enabled_by_default_for_room_type: all
```

### 22.4.1 The four lines that are load-bearing

If a configuration review has time for only four checks:

| Line | If wrong |
|---|---|
| `registration_requires_token: true` | **The service is open to the public internet.** D4 gone, §2.8 invariant 3 violated, spam within days. |
| `resources: [client]` with no `federation` | The server federates. D5 gone. |
| `encryption_enabled_by_default_for_room_type: all` | Rooms can be created unencrypted. §2.8 invariant 1 at risk. |
| `url_preview_enabled: false` | The server fetches links out of E2EE conversations, learning content it told users it could not see (§9.6.1). |

These four belong in the acceptance criteria of phase 1 (§33.1) as explicit,
tested assertions — not as "we set it correctly". A test that registers without
a token and asserts a failure is worth more than any amount of reading the file.

---

## 22.5 Ownership and uids

Verified against `docker/start.py` in `element-hq/synapse` on 2026-09-11:

> if we are running as root, use user `991` — and `UID`/`GID` environment
> variables override it, with `GID` defaulting to `991`.

So Synapse's container writes as **991:991** unless told otherwise. Two options:

- `chown -R 991:991` the host paths; or
- set `UID`/`GID` env vars to a uid the operator prefers.

**Either works; pick one and write it down.** This box has an established
history of uid mistakes costing an afternoon — `crafty-4`'s host paths are
uid 1000 because that is what Crafty's `run_as` is, not the 568 an appliance
habit would suggest, and the PostgreSQL case (§23.2) is worse than either.

`filesystem.setperm` **rejects 4-digit modes** — it accepts 000–777 only. If a
setgid bit is needed, set it separately with `chmod g+s`, as was necessary for
the file server's `/shared`.

---

## 22.6 Resource limits

Set explicitly, through the API, at creation:

| | Limit | Why |
|---|---|---|
| Synapse | 2 GB | §28 sizes it well under this. The limit is a blast-radius control, not a target. |
| PostgreSQL | 2 GB | |
| Element Web | 256 MB | Static files |
| Invite service | 256 MB | |
| lk-jwt-service | 128 MB | |

**The host has no swap.** Overcommit on this box means the OOM killer, not
slowdown, and the OOM killer does not care that the process it picks is a
customer's point-of-sale system. Limits are how a runaway homeserver stays a
homeserver problem.

Set them via `app.update`, not `docker update`. A live `docker update` works on
this host — cgroups v2 accepts it, and it is how `crafty-4`'s cap was raised
with zero downtime — but **the middleware does not learn about it**, so the next
redeploy silently reverts. A limit that disappears on redeploy is worse than no
limit, because nobody is watching for it.

---

## 22.7 Admin access

The admin API is how §9.8 issues invites, §7.6 deletes accounts and §31
moderates. It authenticates with an ordinary access token belonging to a user
with `admin: true`.

```bash
# create the first admin — needs registration_shared_secret
register_new_matrix_user -c /data/homeserver.yaml -a -u operator http://localhost:8008
```

Rules for that account:

- It is an **operator account, not the operator's personal account.** The
  personal account is an ordinary user. Separating them means a stolen phone
  does not carry an admin token.
- Its access token is not stored in the repository, in `infra/`, or in any file
  synced anywhere. `infra/` holds templates with placeholders (§10.9).
- The admin API is **never exposed through the tunnel.** It is reachable on the
  LAN, or over SSH to Willard, and nowhere else. `/_synapse/admin` on a public
  hostname is a full compromise waiting for one leaked token.

The last point needs enforcing in the tunnel configuration, not just intended —
§26.5 covers how.

---

## 22.8 Upgrades

| | |
|---|---|
| Cadence | Within a few weeks of upstream. Security releases promptly. |
| Breaking changes | Synapse publishes an upgrade notes document per release. Read it; it is short and occasionally load-bearing. |
| Procedure | §29.3. Back up the database first (§23.4), pull the pinned tag, `app.update`, verify. |
| Schema | Synapse migrates its own schema at startup. Migrations are **not reversible** — a rollback after a schema migration needs the pre-upgrade dump, which is why §29.3 takes one first. |
| Rollback | Restore the dump, repoint the tag. Assume it costs the downtime of a restore, not of a restart. |

The irreversible-migration row is the one that turns a bad upgrade into an
incident. **Take the dump. It is one command and it is the difference between a
ten-minute rollback and a very bad evening.**

---

## 22.9 Open questions

- ~~**Does `registration_requires_token` interact correctly with the acceptance
  gate?**~~ **ANSWERED in phase 1, 2026-09-11 — yes, natively.** See §22.10.
- **Is `user_directory.search_all_users` the right discovery posture?** It makes
  every account findable by exact username within the server. §6.6 wants exactly
  that and no more; verify Synapse does not also expose prefix search.
- **`retention.enabled: false`** — see §25.6. Server-side retention purges
  events the operator cannot read anyway, and the local store (§12) is the
  primary copy. Deliberate, but worth revisiting when disk becomes the
  constraint.


---

## 22.10 Q2 answered — the gate fits inside UIA

Verified against the deployed homeserver on 2026-09-11. This was the single most
important unknown in phase 1 (§33.2) and the answer simplifies §9 considerably.

Synapse's User-Interactive Authentication stages registration, and **the token is
consumed in a step that completes before the account is created**:

```
POST /register {username, password}
   → 401 + session, flows: [["m.login.registration_token", "m.login.dummy"]]

POST /register {..., auth: {type: m.login.registration_token, token, session}}
   → 401, completed: ["m.login.registration_token"]
   → token is now pending=1, completed=0
   → **no account exists**          ← the acceptance gate runs HERE

POST /register {..., auth: {type: m.login.dummy, session}}
   → 200, user_id: @name:nexlink.thvjq.com.au
   → token is now pending=0, completed=1
```

Observed exactly as written: after the token stage, `GET /_synapse/admin/v2/users/@gatetest:…`
returned `M_NOT_FOUND` while the token sat at `pending=1`. Replaying a spent
token on a fresh session was refused.

There is also a **non-consuming validity probe**, which is the "reserve" half on
its own:

```
GET /_matrix/client/v1/register/m.login.registration_token/validity?token=CODE
   → {"valid": true}
```

### 22.10.1 What this changes

**The invite service does not need to proxy registration.** §9.2.2 worried it
might have to; it does not. Its job shrinks to maintaining the metadata Synapse
has no place for — `invite_record` (§9.2.2) and `acceptance_record` (§9.6.2) —
while Synapse owns token issue, validity and redemption natively.

That is a materially smaller service, and it removes the unauthenticated
registration proxy that §9.7 identified as the primary attack surface. **Synapse's
own rate limiting now covers that path**, which is code this project does not
have to write or defend.

### 22.10.2 The client's obligation

The ordering is now the client's responsibility, and it is a §2.8 invariant:

> The token stage is completed, **then** the gate is shown, **then** the dummy
> stage completes the account.

A client that runs the gate first and the token stage afterwards would create
accounts for people who abandoned the gate. `AcceptanceGateState` already
enforces the user-visible half — the invite code is submitted before screen 1
and `toOutcome()` returns null unless every screen completed — and phase 3 wires
that to the UIA session.