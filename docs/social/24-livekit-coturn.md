# §24 — LiveKit and coturn

> **Confidence:** `REVISABLE`
> The requirement for a publicly-addressable host (§24.1) is `DURABLE` — it is a
> consequence of physics and CGNAT, not of configuration. Sizing and provider
> choice are revisable.

---

## 24.1 Why this chapter is about a rented box

§17.4 establishes it and §21.2 draws the picture. Restated once more, because
this is the chapter where someone will try to avoid the expense:

Willard has no public IPv4 address, no port forwarding and no IPv6. A SFU must
be reachable inbound on UDP and must advertise a routable address in its ICE
candidates. **There is no configuration of LiveKit that makes an unroutable host
routable.**

Attempts to work around it, and what actually happens:

| Attempt | Result |
|---|---|
| `use_external_ip: true` | LiveKit discovers the CGNAT gateway address and advertises it. Clients cannot reach it. Calls connect and carry no media — the worst failure mode, because it looks like it worked. |
| Route through Cloudflare Tunnel | The signalling WebSocket works. Media does not traverse it. Same symptom. |
| TURN on Willard | A TURN server behind CGNAT can relay nothing; it has the same reachability problem as the SFU. |
| playit.gg UDP tunnel | Works technically — it is how this box publishes Geyser on 19132/udp. Puts a hobby relay in the media path of every call, with no capacity guarantee, no SLA and a terms-of-service question about commercial use. |

The connect-but-silent failure in row one is worth internalising. **If calls
connect and nobody can hear anything, check ICE candidates before anything
else.**

### 24.1.1 A fifth option, taken 2026-09-13: a hosted SFU

The table above is right about every attempt to make *Willard* serve media. It
asks the wrong question. The constraint is not "Willard must host the SFU" — it
is "the SFU must be reachable". Those are different, and the second one can be
satisfied by someone else's box.

**The `.well-known` detail that makes this work.** MatrixRTC's focus config
points at the **JWT service**, not the SFU:

```json
"org.matrix.msc4143.rtc_foci": [
  { "type": "livekit", "livekit_service_url": "https://.../livekit/jwt" }
]
```

`lk-jwt-service` is plain HTTP. Cloudflare Tunnel carries it perfectly — it is
the media path that cannot traverse a tunnel, and the JWT service is not on it.
So the split is:

| Component | Where | Reachability |
|---|---|---|
| Synapse, media repo, Element Web | Willard | HTTP via tunnel — already live |
| `lk-jwt-service` | Willard | HTTP via tunnel |
| **LiveKit SFU** | **LiveKit Cloud** | Theirs, and routable |

**Cost: nothing.** LiveKit Cloud's Build tier is permanently free — 5,000 WebRTC
minutes per month, 50 GB egress, 100 concurrent connections. Minutes are counted
**per participant**, so a four-person half-hour call costs 120 of them: roughly
41 hours of 1:1 calling a month, or ~41 four-person half-hour calls.

**What this changes about phase 4.** §33.5 lists the phase as blocked by "the
first recurring cost". It is not, any more. The §17.6.1 spike — 1:1, a third
participant on Element Web, screen share, E2EE key rotation — fits inside the
free tier with room to spare. The decision that gates phase 4 can now be made
before any money is spent.

**Why this is better than buying the VPS first**, rather than merely cheaper:
the VPS moves from *pay before knowing whether calling works* to *pay because
calling works and people are using it*. If §17.6 resolves badly, or calling
turns out not to matter to this product, nothing was spent.

#### What it costs that is not money

Three things, recorded because they are the reasons this might later be undone:

1. **A third party carries the media.** §17.5's E2EE means LiveKit sees
   ciphertext, not conversations. But **metadata is visible to them** — who was
   in a call, when, and for how long. That is a §9.6.1 disclosure line by the
   same standard as Q44, and it must be on the "We can see" side of the wall
   before anyone but the operator uses calling.
2. ~~No confirmed Australian region.~~ **Measured 2026-09-13 and the concern
   does not hold.** `nexlink-social-ro8eroxb.livekit.cloud` resolves to
   `152.67.124.114` / `152.67.126.168` — Oracle Cloud's Sydney range — and TCP
   connect from the operator's network averaged **24–36 ms** across five
   samples, against ~25 ms to Willard's own Cloudflare endpoint. Domestic, and
   comparable to infrastructure already in the media path. §24.2's latency
   argument for an Australian VPS is satisfied by the hosted SFU as it stands.
3. **The free tier is documented as being for development.** No SLA. For an
   invite-only product whose operator is also its first support channel that is
   an acceptable risk; it stops being acceptable at the point §33.7's private
   launch has real users depending on calls.

#### The economics invert later, and that is the trigger to revisit

| | monthly |
|---|---|
| LiveKit Cloud Build | **free** |
| LiveKit Cloud Ship (next tier up) | **$50** |
| The §24.2 VPS | **$10–15** |

So the moment the free tier is outgrown, self-hosting becomes the *cheap*
option, not the expensive one. **The trigger to provision the VPS is exceeding
the free tier, not reaching phase 4** — and by then the §29.6 rebuild runbook
has a working configuration to describe rather than a hypothetical one.

#### Cloudflare specifically, since it is the obvious thing to ask

| Product | Why not |
|---|---|
| Tunnel | The table above. Media does not traverse it. |
| Realtime **SFU** | Not LiveKit-compatible. Deliberately unopinionated — no rooms concept, just Sessions and Tracks in a pub/sub model. MatrixRTC's focus type is `livekit` and Element Call speaks LiveKit's protocol, so adopting it means implementing a new focus, which is protocol work on a moving target — the exact liability §17.6 leans away from. |
| Realtime **TURN** | Free only alongside their own SFU, and it does not address the problem: TURN relays a client that cannot reach a **reachable** server. The unreachable thing here is the server. |

---

---

## 24.2 The VPS

| | |
|---|---|
| Location | **Australia** — Sydney or Melbourne. Media latency is the product quality metric that users feel; every millisecond is one they experience. |
| Spec | 2 vCPU, 4 GB RAM, 2–4 TB/month transfer |
| Public IPv4 | Required, dedicated |
| IPv6 | Take it if offered; dual-stack helps mobile clients on v6-only carriers |
| OS | Debian stable. A plain Linux box with Docker Compose — **none of the appliance rules in this repository's host documentation apply here.** |
| Cost | ~AUD 10–15/month (§28.4) |

**Transfer allowance is the spec line that matters**, not CPU. A SFU is a packet
forwarder; it is bandwidth-bound long before it is CPU-bound. §28.3 does the
arithmetic; the summary is that four-participant calls at a few hundred kbps
each, for a small user base, sit comfortably inside 2 TB — and a provider that
charges per-GB overage rather than shaping is the one that produces a surprise
invoice.

### 24.2.1 This box is not Willard

Worth stating because the operator's habits are TrueNAS habits. The VPS is an
ordinary Debian server: `apt install` is fine, editing `/etc` is fine, a systemd
unit is fine, a host crontab is fine. The appliance discipline exists because
TrueNAS regenerates its root filesystem, and that is not true here.

It follows the same pattern as the planned Docker server VM, and its
configuration lives in `infra/livekit/` in this repository (§10.9), as templates
with placeholders. Not secrets.

---

## 24.3 What runs there

```
                     :443/tcp  ─── coturn TLS ────┐   §24.5
   clients ─────────►:3478/udp ─── coturn ────────┤
                     :7880/tcp ─── livekit ws ────┤   signalling
                     :7881/tcp ─── livekit ice ───┤   TCP fallback
                     :50000-60000/udp ── media ───┘
```

| Service | Version | Port |
|---|---|---|
| LiveKit SFU | `v1.13.6` (2026-08-26) | 7880 ws, 7881 tcp, 50000–60000/udp |
| coturn | Distribution package | 3478 udp/tcp, 5349 tls, 443 tls |

Note what is **not** here: `lk-jwt-service` stays on Willard (§21.2), because it
talks to the homeserver and carries no media.

### 24.3.1 livekit.yaml

```yaml
port: 7880
rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 60000
  use_external_ip: true        # correct HERE — this box has a real one
keys:
  <api-key>: <api-secret>      # must match lk-jwt-service on Willard
turn:
  enabled: false               # coturn runs separately (§24.5)
logging:
  level: info
```

`use_external_ip: true` is right on a VPS with a real public address and
catastrophic on Willard. The same line, two hosts, opposite outcomes — which is
the clearest illustration available of why the split in §21.2 exists.

**The API key and secret must match `lk-jwt-service`'s configuration exactly.**
A mismatch produces tokens the SFU rejects, which surfaces as "call connects,
then immediately disconnects" — a different symptom from §24.1's silent
failure, and worth learning to tell apart.

---

## 24.4 The firewall

The UDP range is large and it is the whole point. Do not narrow it without
understanding that each concurrent publisher needs a port.

```
allow 22/tcp          from operator IPs only
allow 443/tcp         coturn TLS (§24.5)
allow 3478/udp,tcp    coturn
allow 5349/tcp        coturn TLS
allow 7880/tcp        livekit signalling  ← behind TLS termination
allow 7881/tcp        livekit ICE/TCP
allow 50000-60000/udp livekit media
deny  everything else
```

SSH restricted to known addresses is the one line that is not optional. This box
has a public IP and will be scanned within minutes of existing.

---

## 24.5 coturn

LiveKit can serve TURN itself. It is run separately here for a specific reason:
**port 443**.

A meaningful fraction of users are behind corporate or institutional firewalls
that permit outbound TCP 443 and essentially nothing else. TURN over TLS on 443
is indistinguishable from HTTPS and is the difference between a call that works
from an office and one that does not.

```
listening-port=3478
tls-listening-port=5349
alt-tls-listening-port=443

external-ip=<public-ip>
realm=thvjq.com.au
use-auth-secret
static-auth-secret=<shared with livekit>

# Do not become an open relay into private networks:
no-multicast-peers
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
denied-peer-ip=127.0.0.0-127.255.255.255
```

**The `denied-peer-ip` block is a security control, not tidiness.** A TURN
server without it relays to arbitrary addresses, including private ranges —
turning it into an SSRF pivot and, historically, an open relay abused for
amplification. An open TURN server is a liability the operator becomes
responsible for.

`use-auth-secret` with a shared secret means credentials are derived and
time-limited rather than static. No long-lived TURN passwords.

### 24.5.1 The conflict with LiveKit's own TURN

Known upstream behaviour worth flagging: LiveKit has had issues where
configuring `turn_servers` causes it to discover and advertise a NAT gateway
address even when `use_external_ip` is false, and where it expects a TLS
certificate even with TLS disabled. Both are avoided here by running coturn
independently and leaving `turn.enabled: false` in `livekit.yaml`.

If TURN behaviour looks wrong, **check what addresses appear in the ICE
candidates** rather than re-reading the config. The candidates are the ground
truth.

---

## 24.6 TLS on the VPS

Unlike Willard, this box terminates its own TLS — it is not behind Cloudflare
Tunnel, and it must not be: proxying media through Cloudflare would defeat the
point of having a public IP.

| | |
|---|---|
| Certificates | Let's Encrypt, DNS-01 challenge |
| Renewal | Automated, with a **monitored** failure path (§27.5) |
| Hostname | `rtc.thvjq.com.au`, A record pointing at the VPS (§26.3) |
| Used by | coturn (5349, 443), and the reverse proxy in front of LiveKit's ws port |

DNS-01 rather than HTTP-01 because port 80 does not need to be open on this box
for anything else, and a challenge method that requires opening a port is a
reason to leave it open.

**An expired certificate here breaks calling silently for the subset of users
behind restrictive firewalls** — everyone else keeps working over UDP, so the
failure looks like "calls don't work for some people" rather than "the
certificate expired". Renewal monitoring is not optional (§27.5).

---

## 24.7 Monitoring

The VPS is the one piece of infrastructure not covered by Willard's existing
netdata (§27).

| Signal | Threshold | Why |
|---|---|---|
| Host reachable | 2 failed checks | Calls fail entirely |
| LiveKit process | Not running | Same |
| Monthly transfer | 70% of allowance | Overage is the surprise-invoice failure |
| Certificate expiry | < 14 days | §24.6 |
| Concurrent sessions | Trend | Capacity signal (§28) |
| Packet loss / jitter | Trend | Quality signal; the thing users actually feel |
| SSH auth failures | Sustained rise | A public-IP box is scanned constantly; a rise above the constant background is different |

LiveKit exposes Prometheus metrics. Scraping them from Willard means either an
authenticated endpoint or a tunnel; **do not publish a metrics port on a public
IP.** Metrics endpoints leak topology and are routinely left unauthenticated.

---

## 24.8 Failure behaviour

**When the VPS is down, messaging is completely unaffected.** This is the most
valuable property of the split in §21.2 and it should be preserved deliberately:

| Component down | Effect |
|---|---|
| VPS entirely | No calls. Messaging, media, web client, invites all fine. |
| LiveKit only | No calls. Signalling state in rooms is harmless and self-cleans via delayed events (§17.3.1). |
| coturn only | Calls work for most; fail for users behind restrictive firewalls. **This is the failure that gets misdiagnosed as "flaky calls".** |
| Willard | Everything is down, including calls — lk-jwt-service lives there, so no tokens are issued. |

The client must fail honestly: a call that cannot get a token says *"Calling is
temporarily unavailable"*, not a spinner that never resolves.

That last row deserves a note. lk-jwt-service on Willard means Willard is a
dependency of calling even though it carries no media. Moving it to the VPS
would decouple them — at the cost of exposing homeserver-authenticated requests
from a box outside the operator's network. **Keep it on Willard**; a call
outage during a Willard outage is not a meaningful additional loss.

---

## 24.9 Open questions

- **Provider.** Unresolved. Wants: Australian region, dedicated IPv4, generous
  and *shaped* rather than billed transfer, and a snapshot facility so §29's
  rebuild runbook is short.
- **Does the Android client honour TURN-over-443?** Depends on §17.6. If the
  Element Call widget path is chosen, this is upstream's behaviour to verify
  rather than the app's to implement.
- **Is one SFU enough, and what happens when it is not?** LiveKit clusters, but
  clustering is a different deployment. At the scale in §28 a single instance is
  ample; the trigger to revisit is sustained concurrent-session counts rather
  than user growth.
- **Does the VPS need its own backup?** It holds no user data — only
  configuration, which is in `infra/`. A rebuild-from-template runbook (§29.6)
  is a better answer than backups, and it should be *timed* once so the claim is
  real.
