# Cloudflare Tunnel routes — NexLink Social

**This is the authoritative written record.** The `cloudflared` app on Willard
uses a **token-based tunnel**, so its public hostnames live only in the Cloudflare
dashboard — they are not in any file on Willard and cannot be added from the CLI
(docs/social/26-dns-tls.md §26.5.1). The dashboard is not covered by any backup,
so if this file and the dashboard disagree, fix the dashboard.

Last verified: 2026-09-11.

---

## DNS

| Name | Type | Target | Proxy | When |
|---|---|---|---|---|
| `nexlink` | CNAME | `<TUNNEL-ID>.cfargotunnel.com` | **Proxied** (orange) | **now** |
| `rtc` | A | the VPS public IP | **DNS only** (grey) | phase 4 |

`<TUNNEL-ID>` is the existing `cloudflared` tunnel on Willard. **Deliberately not
written here — this repository is public.** It is not a credential, but it is an
account-specific identifier and there is no reason to publish it. Read it back
with:

```bash
ssh willard 'sudo midclt call app.config cloudflared' \
  | jq -r '.. | strings' | grep -oE 'eyJ[A-Za-z0-9_-]{40,}' | head -1 \
  | python3 -c 'import sys,base64,json; t=sys.stdin.read().strip(); print(json.loads(base64.urlsafe_b64decode(t+"="*(-len(t)%4)))["t"])'
```

### The stuck managed-record trap — hit on 2026-09-11

**Adding a Public Hostname does not reliably create the DNS record, and it can
fail in a way that then blocks you from fixing it by hand.**

What was observed, in order:

1. Five Public Hostnames were added for `nexlink.thvjq.com.au`, **every one with
   a path**. The tunnel picked them up (ingress config version 43) and they are
   live in `cloudflared`.
2. `nexlink.thvjq.com.au` returned **NXDOMAIN with the `aa` flag set** from both
   `ganz.ns.cloudflare.com` and `annabel.ns.cloudflare.com` — an authoritative
   "this name does not exist".
3. Adding the CNAME by hand was refused with *"An A, AAAA, or CNAME record with
   that host already exists."*

Cloudflare therefore believed a record existed while its own nameservers served
NXDOMAIN. That is a **tunnel-managed DNS record in a half-created state**: it
reserves the name, does not appear in the DNS records list, and is not served.

**Diagnosis, always by dig and never by the dashboard:**

```bash
dig nexlink.thvjq.com.au @ganz.ns.cloudflare.com | grep -E 'status|flags'
# NXDOMAIN + "aa" = authoritative; the name really is absent
```

**Fix, in order:**

1. DNS → Records, filter for the name. If a record appears, delete it and add
   yours. Quick path; managed records are often hidden, so expect nothing.
2. Otherwise delete **every** Public Hostname for that hostname, then confirm the
   name is released by re-trying the manual DNS add — if "already exists" is gone,
   the ghost is cleared.
3. Re-add **one** Public Hostname with an **empty Path**, pointing at Element Web
   (8061). Pathless is the form that creates the DNS record — every working
   hostname on this tunnel (`thvjq.com.au`, `sospos.thvjq.com.au`,
   `cloud.reiflers.ch`) is pathless, and the one hostname whose routes all had
   paths is the one with no DNS.
4. Verify `dig` resolves, then add the path routes back **above** the pathless one.

**Resolved 2026-09-11.** Deleting the DNS record *and* all five Public Hostnames
cleared it; the name then published normally and
`https://nexlink.thvjq.com.au/` returned the tunnel's own 404 fallback — which is
the correct end-to-end proof that DNS → Cloudflare → tunnel works, with no route
yet matching. Total elapsed: the record was stuck for roughly fifteen minutes and
no amount of re-reading the dashboard would have shown it, because the dashboard
displayed the record as present and healthy throughout.

**The diagnostic that actually worked** was comparing the broken name against
every other Tunnel record in the same zone:

```bash
for n in thvjq.com.au minecraft.thvjq.com.au netdata.thvjq.com.au \
         nexlink.thvjq.com.au pricing.thvjq.com.au sospos.thvjq.com.au; do
  printf '%-30s %s\n' "$n" "$(dig "$n" @ganz.ns.cloudflare.com | grep -oE 'status: [A-Z]+' | head -1)"
done
```

Seven NOERROR and one NXDOMAIN localises the fault to a single record in seconds,
and rules out the zone, the nameservers, the registrar delegation and the tunnel
all at once.

### A second tunnel exists on this account

`netdata.thvjq.com.au` points at a tunnel named **"Websites On TrueNAS"**, while
everything else points at **"Websites on TrueNAS Scale v2"** — the one running on
Willard. The netdata name resolves but returns **530**: its tunnel has no
matching route.

It is a dangling record from an older setup. Harmless today, worth deleting: if a
catch-all route is ever added to that tunnel, a stale name like this starts
resolving to something real without anyone intending it.

`rtc` is **grey-clouded on purpose** — it carries WebRTC media and must not be
proxied through Cloudflare, or the whole point of giving the SFU a public IP is
lost (§24.6).

Both are **first-level** subdomains of `thvjq.com.au`, which is deliberate:
Cloudflare's free Universal SSL covers the apex and one level only. A name like
`matrix.nexlink.thvjq.com.au` would be two levels deep and would have no
certificate (§26.2.2).

---

## Tunnel public hostnames

Zero Trust → Networks → Tunnels → the existing tunnel → Public Hostnames.

All routes use the **same hostname** and differ by path.

**Order is everything, and it is not "most specific wins".** cloudflared
evaluates ingress rules **top to bottom, first match wins**, and the Path field
is an **unanchored regular expression**, not a glob. Two consequences that bite:

- A rule with path `/` matches **every** request, because the regex `/` matches
  any path containing a slash. **It must be the last rule in the list.**
- `/_matrix/*` works, but as a regex it means "`/_matrix` then zero or more
  slashes", matching anywhere in the path. It happens to do the right thing here.
  Do not read these as shell globs.

| Order | Path | Service | Add when |
|---|---|---|---|
| 1st | `/_synapse/admin/` | **BLOCK — see below** | **now** |
| 2nd | `/_matrix/` | `http://192.168.0.10:8060` | **now** |
| 3rd | `/.well-known/matrix/client` | `http://192.168.0.10:8060` | **now** |
| 4th | `/invite/` | `http://192.168.0.10:8062` | phase 3 |
| 5th | `/livekit/jwt` | `http://192.168.0.10:8063` | phase 4 |
| **last** | `/` (catch-all) | `http://192.168.0.10:8061` | **now** |

Service type is **HTTP**, not HTTPS — TLS terminates at Cloudflare and Synapse
runs `tls: false` with `x_forwarded: true` behind it (§26.5). Do **not** enable
"No TLS Verify"; there is no TLS on the origin to verify.

### Blocking `/_synapse/admin/` — NOT a tunnel route

`/_synapse/admin/*` is the full admin API: create and delete accounts, read the
user list, issue registration tokens. **One leaked token behind an open admin API
is a full compromise** (§22.7).

**It cannot be done as a Public Hostname.** A tunnel route requires a service
(type + address); there is no "deny" service, so there is nothing to enter. The
block belongs at the **edge**, in front of the tunnel. Either of these works and
neither needs a port:

**Option A — WAF custom rule (simplest, free plan includes five):**

> Security → WAF → Custom rules → Create rule
>
> - When incoming requests match:
>   `Hostname` **equals** `nexlink.thvjq.com.au`
>   **AND** `URI Path` **starts with** `/_synapse/admin`
> - Then: **Block**

**Option B — Zero Trust Access application:**

> Zero Trust → Access → Applications → Add → Self-hosted
>
> - Subdomain `nexlink`, domain `thvjq.com.au`, path `_synapse/admin`
> - Policy → Action: **Block**, Include: Everyone

Option A is fewer moving parts and is the recommendation. Option B is preferable
only if the admin API should later be opened to a named person rather than nobody.

### Why it currently returns 404 without any rule — and why that is not enough

**Verified 2026-09-11:** `https://nexlink.thvjq.com.au/_synapse/admin/v1/server_version`
returns **404**, with no WAF rule and no Access application in place.

The mechanism is incidental. `^/_matrix/` does not match `/_synapse/admin/...`,
so the request falls through to the catch-all route, reaches **Element Web** on
8061, and nginx 404s it. The response body is nginx's HTML 404 page, not
Synapse's JSON — which is how you can tell. Synapse never sees the request.

**This is protection by side effect, not by control.** It holds only while the
catch-all points at Element Web. It disappears, silently and with no error
anywhere, the moment someone:

- repoints the catch-all at Synapse; or
- adds a broader route such as `^/_synapse` or an unanchored `_synapse`; or
- puts a reverse proxy in front that forwards unknown paths to the homeserver.

Any of those turns a full admin API — create and delete accounts, dump the user
list, mint registration tokens — public in one edit, and nothing would report it.

**So still add the explicit rule.** The difference matters: a WAF rule is a
control someone has to deliberately remove, and removing it is visible. The
current 404 is a coincidence nobody wrote down until now.

Either way, **verify by request, not by reading the policy**:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://nexlink.thvjq.com.au/_synapse/admin/v1/server_version
# MUST NOT be 200
```

The operator reaches the admin API over SSH to Willard instead
(`http://127.0.0.1:8060/_synapse/admin/...`), which is how the `invite` CLI
already works. Nothing is lost by blocking it at the edge.

---

### The catch-all route must have NO path

Observed 2026-09-11: five hostname routes were added, every one of them with a
path, and **Cloudflare never created the DNS record** — `nexlink.thvjq.com.au`
returned NXDOMAIN from Cloudflare's own authoritative nameservers.

Set the Element Web route's Path field to **empty**, not `/`. That is both:

- the correct catch-all — cloudflared treats a rule with no path as matching
  everything, whereas `/` is an unanchored regex that also matches everything but
  for the wrong reason; and
- the form Cloudflare recognises as the bare hostname, which is what triggers the
  DNS record.

If the record still does not appear, add it by hand (see DNS, above).

### What must NOT be routed

| Never | Why |
|---|---|
| Port `8064` (metrics) | Not published to the host at all (§21.4). Nothing to route. |
| Port `5432` (PostgreSQL) | Not published. Internal Docker network only. |
| `/_matrix/federation/*` | Federation is off (§21.3). Synapse 404s it, but do not route it either. |
| `/.well-known/matrix/server` | The *federation* delegation file. Advertising it would announce an endpoint that does not exist. |

---

## After adding the routes

Run these from a machine that is **not** Willard — a check that runs inside the
thing it is checking cannot detect the thing being unreachable (§27.6).

```bash
# 1. The homeserver answers
curl -fsS https://nexlink.thvjq.com.au/_matrix/client/versions | head -c 80

# 2. Delegation, and it MUST carry the CORS header or Element Web fails with a
#    browser error that looks nothing like "your delegation is misconfigured"
curl -sSI https://nexlink.thvjq.com.au/.well-known/matrix/client | grep -i access-control-allow-origin
curl -fsS  https://nexlink.thvjq.com.au/.well-known/matrix/client

# 3. Element Web loads
curl -sS -o /dev/null -w '%{http_code}\n' https://nexlink.thvjq.com.au/

# 4. Admin API is NOT reachable  (must not print 200)
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://nexlink.thvjq.com.au/_synapse/admin/v1/server_version

# 5. Registration is still closed  (must mention registration_token)
curl -sS -X POST https://nexlink.thvjq.com.au/_matrix/client/v3/register \
  -H 'Content-Type: application/json' -d '{"username":"probe","password":"probe-password-123"}'

# 6. Federation is off  (must not be 200)
curl -sS -o /dev/null -w '%{http_code}\n' https://nexlink.thvjq.com.au/_matrix/federation/v1/version
```

**If check 2 shows no `Access-Control-Allow-Origin` header**, add a Cloudflare
Transform Rule setting `Access-Control-Allow-Origin: *` on that path. It is the
single most common self-hosting mistake in this area (§26.3.1), and Synapse does
send the header itself — so its absence means something at the edge stripped it.

Checks 4, 5 and 6 are not health checks; they are **assertions that the security
posture has not drifted**. They belong in the daily run (§27.6) and the quarterly
review (§29.7).
