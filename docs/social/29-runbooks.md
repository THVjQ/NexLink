# §29 — Runbooks

> **Confidence:** `DURABLE`
> §1.5: *no recovery procedure requires knowledge held only in someone's head.*
> This chapter is that criterion, discharged.

---

## 29.0 How to use this chapter

Each runbook is written to be followed at 2 a.m. by someone who did not write
it, which in practice means the same person eighteen months later. So: numbered
steps, explicit commands, a stated success condition, and a note on what to do
when a step fails.

**Runbooks live in `infra/runbooks/` as well as here** (§10.9). This chapter is
the canonical text; the copies in `infra/` are what someone finds when they are
already SSHed into a host and not reading documentation.

Conventions used throughout:

```bash
ssh willard          # 10.10.10.1, host-link, preferred
ssh willard-lan      # 192.168.0.10, LAN fallback when the host-link is down
```

The host-link has been observed down while the LAN path worked. **If `ssh
willard` says "No route to host", try `willard-lan` before concluding the box is
down.**

---

## 29.1 Issue an invite

The most frequent operation. §9.8.

```bash
# 1. Mint a registration token on the homeserver
ssh willard-lan 'sudo docker exec <synapse> curl -sS \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -X POST http://localhost:8008/_synapse/admin/v1/registration_tokens/new \
  -d "{\"uses_allowed\":1,\"expiry_time\":$(( ($(date +%s)+14*86400) * 1000 ))}"'
```

2. Record it in `invite_record` (§9.2.2) with `issued_by`, `issued_at` and a
   `note`. **The service stores only the SHA-256 hash** — the raw token is
   never written to the database.
3. Format for the human: 12 characters, three groups of four, Crockford base32
   (§9.3). `X7K2-9QMF-3BTD`.
4. Send it.

**Success:** the token appears in `GET /_synapse/admin/v1/registration_tokens`
with `pending: 0, completed: 0`.

**Expiry is 14 days** and the timestamp is in **milliseconds**. A token minted
with seconds expires in 1970 and is rejected with an error that does not mention
time at all.

### 29.1.1 Bulk issue for a test cohort

Same call in a loop with a shared `note`, so the whole batch can be revoked as a
group later (§9.8). Always set the note; an unlabelled batch is unrevokable
except one at a time.

---

## 29.2 Revoke an invite or suspend an account

```bash
# Revoke an unredeemed token
DELETE /_synapse/admin/v1/registration_tokens/<token>

# Suspend an account (reversible — preferred over deactivation)
PUT /_synapse/admin/v1/suspend/@user:nexlink.thvjq.com.au   {"suspend": true}
```

Per §9.5: **suspending an account revokes its unredeemed tokens immediately.**
Accounts it already invited are *not* automatically suspended — that is a
moderator decision (§31), because collective punishment for an inviter's
behaviour is rarely proportionate.

**Suspend before deactivating.** Suspension is reversible; deactivation is not
(§7.6). If the situation is unclear, take the reversible action.

---

## 29.3 Upgrade Synapse

Scheduled, never reactive. Budget 30 minutes.

1. Read the upgrade notes for every version being crossed. They are short and
   occasionally load-bearing.
2. **Take a database dump** (§23.4.2). Not optional — schema migrations are not
   reversible, and this dump is the only rollback.
   ```bash
   ssh willard-lan 'sudo bash /mnt/Pool1-MAIN/social/backups/dump-now.sh'
   ```
3. Confirm `result=OK` in `last-run.status` and a plausible file size.
4. Update the pinned image tag via the `app.config` round-trip (§22.2.1) —
   remember to `del(.ix_context, .ix_certificates, .ix_certificate_authorities,
   .ix_volumes)` first, or the payload is ~300 KB and `sudo` fails with
   `argv mismatch`.
5. `app.update`. The container is recreated; expect seconds of downtime.
6. Watch the log for schema migration completion and for errors.
7. Verify (§29.3.1).

**Success:** all of §29.3.1 passes.

**On failure:** restore the dump (§23.6), repoint the tag to the previous
version, `app.update`. Budget an hour, not ten minutes — a restore is not a
restart.

### 29.3.1 Post-upgrade verification

```bash
curl -fsS https://nexlink.thvjq.com.au/_matrix/client/versions
```
Then, by hand:
- Log in on the Android client. Send and receive an encrypted message.
- Confirm a previously-sent message still decrypts.
- Confirm the device list is intact (§8.6).
- Re-run the three drift assertions from §27.6 — registration closed,
  federation off, admin API unreachable. **An upgrade can reintroduce a
  default.**

---

## 29.4 Restore the database

Full procedure in §23.6. Summary for the index:

1. Stop Synapse. **Do not restore under a running server.**
2. Recreate the database with `LC_COLLATE 'C'` and `LC_CTYPE 'C'` (§23.3).
3. `pg_restore`.
4. Restore the matching `signing.key` and `homeserver.yaml` (§21.6).
5. Start, then run all five verification checks in §23.6.2.
6. **Record the wall-clock time** in `infra/runbooks/restore-log.md`.

Step 6 is the one that gets skipped and the one that makes §23.4.3's RTO claim
real rather than aspirational.

---

## 29.5 Media store is filling

Triggered by the 80% alert (§27.3).

1. Confirm: `zfs get -Hp used,quota Pool1-MAIN/social/media`
2. Find orphans — media referenced by no event:
   `POST /_synapse/admin/v1/media/delete?before_ts=<ms>`
3. Purge media belonging to deactivated accounts (§25.7). If this finds a lot,
   **the deletion procedure (§32.3) is not purging media and that is a
   compliance bug**, not a housekeeping win.
4. If still tight, raise the quota:
   `sudo midclt call pool.dataset.update Pool1-MAIN/social/media '{"quota": <bytes>}'`
5. If raising the quota is becoming routine, §25.3's object-storage path is the
   real answer.

**Never** delete media by walking the filesystem. Synapse's database will still
reference the files and clients will show permanently-broken attachments with no
explanation.

---

## 29.6 Rebuild the VPS

The SFU holds no user data (§24.9), so this is a rebuild, not a restore.

1. Provision: Debian stable, 2 vCPU / 4 GB, Australian region, dedicated IPv4.
2. Firewall per §24.4 — **SSH restricted to operator IPs first, before anything
   else is installed.**
3. Deploy `infra/livekit/` templates; inject the API key/secret and TURN secret
   from the operator's store.
4. Issue certificates (§24.6, DNS-01).
5. Point `rtc.thvjq.com.au` at the new IP (§26.4). Note TTL — set it low *before* a
   planned rebuild.
6. Verify: place a call between an Android device and Element Web, and confirm
   media flows both ways.

**Time this once and record it.** §24.9 claims a rebuild runbook is better than
backups for this host; that claim is worth nothing unmeasured.

---

## 29.7 Quarterly review

Not an incident procedure — the thing that prevents incidents. Calendar it.

| Check | Section |
|---|---|
| Restore drill, timed | §23.6.3 |
| Security-posture assertions (registration, federation, admin API) | §27.6 |
| Backup age and `result=OK` | §23.4 |
| Media store trend vs. quota | §25.5 |
| VPS transfer vs. allowance | §24.7 |
| Certificate expiry | §24.6 |
| Invite tree: outstanding tokens, quota tuning | §9.5 |
| Dependency and image versions vs. upstream | §20.7, §22.8 |
| `user_ips_max_age` actually purging | §25.6 |
| Cloudflare tunnel hostname map vs. the written record | §26.5.1 |
| Open questions in §37 — any now answerable? | §37 |

The last two are the ones that decay silently. The tunnel routing lives in a web
dashboard that is not in version control and not backed up (§26.5.1); §37's
questions accumulate answers that never get written down.

---

## 29.8 Emergency: take the service offline

Needed if the service is compromised, being abused at scale, or under legal
compulsion (§30).

```bash
# Stop accepting new connections — leaves data intact
ssh willard-lan 'sudo midclt call app.stop nexlink-social-synapse'
```

- **Stop Synapse, not the database.** The database is where the evidence is.
- Element Web and the invite service can stay up to serve a status message, or
  go down with it. Decide deliberately.
- Users see connection failures. Their local history is intact (§12.2) — this is
  the point of the storage model and it is worth saying to users during an
  outage.
- **This does not stop in-progress calls.** Media flows client↔VPS and does not
  involve Willard. Stop LiveKit separately if that is required.

Restarting is `app.start`. Nothing is lost by stopping — but note that stopping
Synapse does not stop the world, and an incident that requires the service to be
truly dark requires both hosts.

---

## 29.9 What has no runbook yet

Stated so the gaps are visible rather than discovered:

- **Recovering a user who lost their phone and their recovery key.** There is no
  procedure because there is no mechanism — that is the design (§7.3), and the
  runbook is a support script, not a technical one. §32.5.
- **Responding to a law-enforcement request.** §30.5 sketches it; it needs
  finishing before the service is public.
- **Migrating the homeserver to different hardware.** §28.6 step 3. Not needed
  yet; will be badly needed on the day it is.
