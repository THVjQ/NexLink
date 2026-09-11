# §27 — Observability and alerting

> **Confidence:** `DURABLE`
> The alerting gap in §27.2 is a fact about this host today and is the reason
> this chapter has a prerequisite.

---

## 27.1 What this chapter is for

A solo operator cannot watch dashboards. The only observability that matters is
the kind that **interrupts them when something is wrong and stays silent
otherwise**. Everything else is forensics — valuable after the fact, worthless
at preventing the incident.

So this chapter is organised around two questions: what pages someone, and what
is merely recorded for the post-mortem.

---

## 27.2 The prerequisite: there is no alerting on this host

Stated first because it invalidates the rest of the chapter until it is fixed.

From Willard's own operating documentation:

> **No failure alerting anywhere.** SMTP is unconfigured (`mail.config` is
> empty) and 25.10 has no `alert.oneshot_create`, so a broken backup is only
> visible in `last-run.status`.

The existing backup cron job is deliberately set `stderr: false`, specifically so
that it **starts emailing the moment somebody configures Credentials → Email**.
That is a good arrangement and it has been waiting for someone to finish it.

**Configuring SMTP on Willard is a prerequisite of phase 5 (§33.5), not an
optional improvement.** Before real users exist, a silent failure costs the
operator an inconvenience. After they exist, it costs a service that is down all
weekend because nobody was told.

It is a web-UI change — Credentials → Email — and it unlocks TrueNAS's own alert
delivery, the Social alerts below, and retroactively the backup alerting that
has been queued behind it for over a month.

---

## 27.3 What pages

Five things. The list is short on purpose: an alert that fires and is ignored
has trained the operator to ignore alerts.

| Alert | Condition | Why it pages |
|---|---|---|
| **Homeserver down** | `/_matrix/client/versions` fails twice, 2 min apart | The service is down. Nothing else matters. |
| **Backup failed** | `last-run.status` not `result=OK`, or file older than 30 h | §23.5 — no offsite copy, so the local dump is the only backup. `WARN` is not success. |
| **Media store ≥ 80%** | Dataset quota (§25.5) | A full store on a shared pool is a blast-radius event (§21.8) |
| **Invite redemption failures spiking** | Sustained rise above baseline | §9.7 — codes are being guessed, or one has leaked publicly. Explicitly specified as paging-level. |
| **Certificate expiry < 14 days** | VPS only (§24.6) | Breaks calling for firewalled users, silently and partially |

Everything else is a dashboard. Resist adding a sixth without removing one.

### 27.3.1 The invite alert is the unusual one

The other four are infrastructure. This one is **abuse detection**, and it is
the only signal the operator gets that the front door is being attacked — the
redemption endpoint is the service's single unauthenticated write path (§9.7).

The baseline is near zero: invites are issued deliberately and redeemed once
each. A dozen failures in an hour is not noise, it is a campaign. Set the
threshold low and expect it never to fire.

---

## 27.4 What is recorded but does not page

| Signal | Source | Used for |
|---|---|---|
| Synapse request rate, latency, error rate | Prometheus, port 8064 | Capacity (§28), post-mortems |
| Database size, connection count, slow queries | Postgres | §23.7 |
| Active users (daily, monthly) | Synapse metrics | §28 — the input to every capacity number |
| Room and event counts | Synapse metrics | Growth |
| Media store size and growth rate | Dataset | Predicting §25.5 |
| Push delivery success rate | FCM | §13 — a silent drop here is invisible to users until they complain |
| SFU concurrent sessions, packet loss, jitter | LiveKit metrics (§24.7) | Call quality |
| Host CPU, RAM, disk, ZFS ARC | **netdata, already on this box** | §21.8 |
| Invite tree growth | `invite_record` | §9.5 — quota tuning |

Willard already runs netdata on `127.0.0.1:6999`, so host-level metrics exist
today and need nothing built. Synapse's Prometheus endpoint on 8064 is **not published to the host at all**
(§21.4) — it is reached on the container's Docker IP. Metrics endpoints leak
topology and are routinely left unauthenticated, and the cheapest way to get that
right is to have no published port to get wrong.

### 27.4.1 Push delivery deserves a note

It is the signal most likely to fail without anyone noticing. Messages arrive
when the app is open, so the operator's own testing looks fine; users experience
"notifications are unreliable" and mostly do not report it, they just use the
app less. A delivery-success ratio trending down is worth looking at monthly
even though it never pages.

---

## 27.5 What must never be recorded

This is §2.8's last invariant, made operational:

> Crash reports, logs and analytics carry no message content, room IDs, user IDs
> or key material.

| Forbidden | Where it leaks from |
|---|---|
| Message content | Impossible server-side (§25.1); **possible client-side**, in logs |
| Room IDs | Synapse logs them by default at DEBUG |
| User IDs / MXIDs | Synapse access logs, client crash reports |
| Access tokens | Request logging |
| Key material | Client logs during verification flows |
| IP addresses beyond 28 days | §25.6, `user_ips_max_age` |

Concrete controls:

- Synapse log level **INFO in production, never DEBUG**. DEBUG logs room IDs and
  request bodies. Turning it on to debug an issue is legitimate; leaving it on
  is a standing disclosure.
- Client crash reporting, if added, must strip identifiers before upload. §20.3
  disables Element Web's rageshake for exactly this reason.
- Logs are rotated and bounded. An unbounded log is an unbounded retention
  period for whatever it happens to contain.
- **No analytics SDK in `:social`.** Not Firebase Analytics, not Crashlytics
  without scrubbing. Every one of them is a third party receiving usage data
  from an E2EE messenger, and §9.6.1's screen does not mention them.

### 27.5.1 The debug-log trap this codebase already has

NexLink ships `DebugLog`, and §10.7 marks it as reusable in `:social`. It is —
but the category model that is fine for SMS debugging is not automatically fine
here. A `DebugLog` line containing a room ID or an MXID, written to a file a
user can export and attach to a support request, is a leak with a friendly UI on
it.

**Requirement:** `:social`'s use of `DebugLog` redacts identifiers at the call
site. A CI grep for MXID-shaped and room-ID-shaped format strings in `:social-*`
logging calls is cheap and worth having (§34).

---

## 27.6 Health checks

```bash
# Homeserver — the one that pages
curl -fsS https://nexlink.thvjq.com.au/_matrix/client/versions >/dev/null

# Admin API must NOT be reachable publicly (§26.5.2)
test "$(curl -sS -o /dev/null -w '%{http_code}' \
  https://nexlink.thvjq.com.au/_synapse/admin/v1/server_version)" != "200"

# Registration must NOT be open (§22.4.1) — the four-line check, as a test
curl -sS -X POST https://nexlink.thvjq.com.au/_matrix/client/v3/register \
  -d '{"username":"probe","password":"probe123456"}' \
  | grep -q 'M_MISSING_PARAM\|registration_token' || echo "OPEN REGISTRATION"

# Federation must be off (§21.3)
curl -fsS https://nexlink.thvjq.com.au/_matrix/federation/v1/version && echo "FEDERATION ON"

# Media store headroom
zfs get -Hp -o value used,quota Pool1-MAIN/social/media
```

The middle three are not health checks in the usual sense — they are
**assertions that the security posture has not drifted**. They cost nothing to
run daily and they catch the class of mistake that a configuration change
introduces silently: someone edits `homeserver.yaml` to fix an unrelated
problem, loses a line, and the service is open to the public internet with no
error anywhere.

Run them from the operator's machine, not from Willard — a check that runs
inside the thing it is checking cannot detect the thing being unreachable.

---

## 27.7 Dashboards

One page, checked weekly, not watched:

- Active users, daily and monthly
- Messages and calls per day
- Media store used vs. quota
- Database size
- Synapse p95 request latency
- SFU concurrent sessions and monthly transfer vs. allowance
- Backup: last success, age, size
- Invite tree: issued, redeemed, outstanding

Grafana against the Prometheus endpoint is the obvious build. It is also
optional — the same numbers can be produced by a weekly script that emails
them, once §27.2 is fixed, and for a single operator that is frequently the
better tool. **Do not build a dashboard stack before the alerting works.**

---

## 27.8 Open questions

- **Where do the alerts run from?** They must be external to Willard (§27.6).
  An uptime service, a cron on the VPS, or the operator's own machine. The VPS
  is attractive — it exists for other reasons, it is outside the house, and it
  is already monitored.
- **Who is paged out of hours, and is that acceptable?** There is one operator.
  §30 needs an honest answer about what "down overnight" means for a service
  people rely on for messaging.
- **Does TrueNAS's own alert system cover any of §27.3 once SMTP exists?** Pool
  health and dataset quota, probably. Homeserver liveness, no. Check before
  building duplicates.
