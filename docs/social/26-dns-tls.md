# §26 — DNS, TLS and delegation

> **Confidence:** `DURABLE`
> The permanence of `server_name` (§26.2) is the most consequential line in
> Part VI. Tunnel specifics are revisable.

---

## 26.1 Why this chapter is short but cannot be skimmed

One decision here is irreversible and the rest are routine. §26.2 is the
irreversible one, and it is made **before the first `generate` command in §22.3
is run** — not before launch, not before the first user, before the server
exists at all.

---

## 26.2 `server_name` is permanent

Every Matrix user ID is `@username:server_name`. It appears in every event,
every signature and every user's contact list, forever. Synapse cannot change
it. There is no migration.

Changing it later means: new server, new accounts, new device identities, and
every user re-verifying every device and losing their history. It is the same
class of event as §2.2's app-merge problem.

**Therefore it is chosen once, deliberately, with an eye on what it will look
like in five years.**

### 26.2.1 DECIDED 2026-09-11 — `nexlink.thvjq.com.au`

Chosen by the product owner. Q1 (§37.1) is closed.

```
server_name   nexlink.thvjq.com.au
MXIDs         @username:nexlink.thvjq.com.au
```

It is a first-level subdomain of a domain the operator already holds and
renews, which matters more than it sounds — §36.4 lists a lapsed domain as a
catastrophic risk, and a domain that is already load-bearing for other services
is one nobody forgets to renew.

The alternatives considered and not taken: the apex `thvjq.com.au` (entangles the
homeserver with everything else that domain does), and a new dedicated domain
(cleaner identity, one more registration to keep alive).

---

### 26.2.2 The constraint this creates: Cloudflare Universal SSL

**Verified 2026-09-11.** Cloudflare's free Universal SSL covers **the apex and
first-level subdomains only** — `thvjq.com.au` and `*.thvjq.com.au`. It does
**not** cover second-level subdomains such as `matrix.nexlink.thvjq.com.au`;
those need Advanced Certificate Manager, which is a paid add-on.

That breaks the delegation shape §26.3 was written around, because every service
hostname under `nexlink.thvjq.com.au` would be one level too deep.

There are three ways out, and only one is free and simple:

| Option | Verdict |
|---|---|
| Advanced Certificate Manager | Works. A recurring bill to solve a naming problem. |
| Sibling first-level hostnames (`nexlink-chat.thvjq.com.au`, …) | Works, free, and ugly. Every service name grows a prefix. |
| **One hostname, path-based routing** | **Chosen.** Free, one certificate, one DNS record, and it is a supported Synapse deployment. |

### 26.2.3 The routing, then

Cloudflare Tunnel public hostnames support a path, so a single hostname fans out
to the services on Willard (§21.4):

| Path on `nexlink.thvjq.com.au` | Goes to | Port |
|---|---|---|
| `/_matrix/*` | Synapse | 8060 |
| `/.well-known/matrix/client` | Synapse | 8060 |
| `/_synapse/admin/*` | **Blocked — §26.5.2** | — |
| `/invite/*` | Invite service | 8062 |
| `/livekit/jwt` | lk-jwt-service (phase 4) | 8063 |
| `/` (everything else) | Element Web | 8061 |

Serving Element Web at `/` and Synapse under `/_matrix` on one origin is a
standard arrangement, not a workaround — the two path spaces do not overlap.

**One second hostname is still needed, and it is first-level so it is covered:**
`rtc.thvjq.com.au`, an A record to the VPS (§24). That is phase 4.

Consequence worth noting: because the client-server API is served at the same
hostname as `server_name`, **delegation is no longer load-bearing** for finding
the homeserver. §26.3's `.well-known` file is still served, because MatrixRTC
discovery needs it — but `m.homeserver.base_url` now points the host at itself.

---

## 26.3 Delegation

`server_name` does not have to be where the homeserver lives. Delegation via
`.well-known` separates the permanent identity from the mutable address, which
is the entire reason to use it.

`https://nexlink.thvjq.com.au/.well-known/matrix/client`:

```json
{
  "m.homeserver": {
    "base_url": "https://nexlink.thvjq.com.au"
  },
  "org.matrix.msc4143.rtc_foci": [
    {
      "type": "livekit",
      "livekit_service_url": "https://rtc.thvjq.com.au/livekit/jwt"
    }
  ]
}
```

Note `base_url` points at the same host as `server_name` — see §26.2.3. The
file is served anyway, because the `rtc_foci` block is the only way Element Web
discovers the MatrixRTC backend (§20.6), and its absence presents as a missing
call button rather than an error.

Three things this buys:

1. `server_name` stays a clean identity string while the homeserver can move
   hosts freely.
2. **Element Web discovers the MatrixRTC backend from here** (§20.6). Without
   the `rtc_foci` block, calls do not work and the failure is a silent absence
   of a call button.
3. Served as a static file, so it can live anywhere — including a Cloudflare
   Pages site or a rule on the tunnel — independent of Synapse's availability.

**FOUND IN PHASE 1 (2026-09-11): Synapse does not generate the `rtc_foci` block
on its own.** With the `matrix_rtc` section of §17.3.1 configured, Synapse still
served only `m.homeserver` at `/.well-known/matrix/client`. The RTC block has to
be added explicitly:

```yaml
extra_well_known_client_content:
  org.matrix.msc4143.rtc_foci:
    - type: livekit
      livekit_service_url: "https://rtc.thvjq.com.au/livekit/jwt"
```

This is live on the deployed homeserver. It matters because the symptom of
getting it wrong is **no call button in Element Web** — an absence, not an error,
and nothing in any log.

`/.well-known/matrix/server` is **not** served. That is the federation
delegation file, and federation is off (§2.6, §21.3). Serving it would advertise
a federation endpoint that does not exist.

### 26.3.1 CORS

The `.well-known` file must be served with `Access-Control-Allow-Origin: *`.
Element Web fetches it from a different origin, and without the header the fetch
fails with a browser CORS error that looks nothing like "your delegation is
misconfigured". It is the single most common self-hosting mistake in this area.

---

## 26.4 The record set

| Name | Type | Target | Notes |
|---|---|---|---|
| `nexlink.thvjq.com.au` | CNAME | Cloudflare tunnel | **Everything on Willard**, fanned out by path (§26.2.3) |
| `rtc.thvjq.com.au` | **A** | **VPS public IP** | LiveKit + coturn. Phase 4. First-level, so Universal SSL covers it. |

Two records. The whole service.

### 26.4.1 The `rtc` record needs care

§17.3.1 routes both `/livekit/jwt` (lk-jwt-service, on **Willard**) and
`/livekit/sfu` (LiveKit, on the **VPS**) under one hostname, which is what
Element Call's documented deployment shape expects.

But the two services are on different hosts, and one of them is behind a tunnel
that carries no media. So `rtc.thvjq.com.au` is an A record to the VPS, and the VPS's
reverse proxy forwards `/livekit/jwt` back to Willard — through the tunnel, over
HTTPS.

The alternative — two hostnames, `rtc.` for the SFU and `jwt.` for the token
service — is simpler to reason about and diverges from the documented shape.

**Decide during phase 4 by trying the documented shape first.** Record which was
used; a future reader debugging a token failure needs to know which host answers
which path. This is the concrete place where §21.2's split leaks into
configuration, and it will confuse someone.

---

## 26.5 TLS

Two different mechanisms, which is unusual and worth stating plainly:

| Host | TLS | Why |
|---|---|---|
| Willard | Terminated by **Cloudflare**, tunnel to plain HTTP internally | The box has no inbound connectivity. This is the existing arrangement for Nextcloud and three websites. |
| VPS | **Let's Encrypt on the box** (§24.6) | Media must not be proxied. |

Synapse therefore runs `tls: false` and `x_forwarded: true` (§22.4). It never
sees a certificate and never renews one.

### 26.5.1 The tunnel configuration is not in this repository

The `cloudflared` app on Willard uses a **token-based tunnel**, so its public
hostnames live in the Cloudflare dashboard — Zero Trust → Networks → Tunnels —
and **not in any file on the host**. They cannot be added from the CLI.

This is already documented behaviour on this box and it catches people twice:
once when looking for a config file that does not exist, and again when
`infra/` (§10.9) turns out not to contain the routing after all.

`infra/runbooks/` should carry a written record of the hostname→service mapping,
since the authoritative copy is in a web dashboard that is not backed up with
everything else.

**And the dashboard can lie.** Hit on 2026-09-11: adding Public Hostnames created
a tunnel-managed DNS record in a half-state that reserved the name, refused a
manual record with *"already exists"*, and served **NXDOMAIN** from Cloudflare's
own authoritative nameservers. `infra/runbooks/cloudflare-tunnel-routes.md`
carries the full diagnosis and fix. The general lesson is the one §26.5.2 already
draws for the admin API: **verify DNS and routing by request, never by reading the
dashboard.**

### 26.5.2 The admin API must not be routed

§22.7 requires `/_synapse/admin` to be unreachable publicly. Since routing is
per-hostname in the dashboard, the control is a **Cloudflare Access policy on
the path** for `nexlink.thvjq.com.au/_synapse/admin/*`, denying everything.

Verify it by request, not by reading the policy:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://nexlink.thvjq.com.au/_synapse/admin/v1/server_version
# must not be 200
```

Put that curl in the phase-1 acceptance criteria (§33.1) and in the quarterly
review (§29.7). A routing change in a dashboard nobody diffs is exactly the kind
of drift §2.1 warns about.

---

## 26.6 Split DNS

Willard's own operating notes carry this as an outstanding item for Nextcloud:
`cloud.reiflers.ch` resolves to the Cloudflare edge even from the house, so LAN
traffic round-trips over Starlink to reach a machine in the next room.

**The same will apply to Social**, and worse — a message between two people in
the same house travels to the Cloudflare edge and back over a satellite link.

The fix is a local DNS override on the UniFi gateway pointing `nexlink.thvjq.com.au`
and `nexlink.thvjq.com.au` at `192.168.0.10`. Two caveats:

- Cloudflare terminates TLS at the edge, so a LAN client hitting Willard
  directly gets plain HTTP on 8060 unless the gateway can also terminate TLS.
  A certificate mismatch will break clients that are strict about it — which,
  for a client holding E2EE keys, they should be.
- The Nextcloud precedent shows the shape of the fix: overrides **scoped** so
  that LAN and tunnel paths both keep working. `overwritecondaddr` was what made
  that work there, by applying rewrites only to traffic from the docker gateway.

Not required for correctness. Worth doing for latency, and it is a §29 runbook
item rather than a launch blocker.

---

## 26.7 Open questions

- **The domain (§26.2).** Blocks phase 1. Product owner's decision.
- **Where does `.well-known` get served from?** If the domain is dedicated to
  this product, Cloudflare Pages or a tunnel rule is simplest and has no
  dependency on Synapse being up. If it is a subdomain of an existing site, it
  shares that site's fate.
- **One `rtc.` hostname or two (§26.4.1)?** Phase 4.
- **Does split DNS break TLS for strict clients (§26.6)?** Test before rolling
  it out; a client that refuses to connect on the home network is a worse
  outcome than a slow one.
