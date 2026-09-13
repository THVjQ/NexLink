# Restore drill log — §23.6.3, §29.4 step 6

> §29.4: *"Step 6 is the one that gets skipped and the one that makes §23.4.3's
> RTO claim real rather than aspirational."*
>
> §23.6.3: *"The recorded time is the point. §23.4.3 claims an RTO under an hour;
> that claim is worth nothing until somebody has stood there with a stopwatch."*

## 2026-09-14 — first full drill

| | |
|---|---|
| Dump | `synapse-2026-09-13T17-00-01Z.dump`, 832,205 bytes |
| Config | `synapse-config-2026-09-13T17-00-01Z.tar.gz` (signing.key + homeserver.yaml) |
| Method | Scratch `drill-pg` + `drill-syn` containers. **Production untouched.** |
| **Wall clock** | **35 seconds**, dump to a Synapse answering `/_matrix/client/versions` |

RTO claim in §23.4.3 is "under an hour". Measured at 35 s for the database and
server; that excludes noticing the outage and deciding to restore, which is the
larger part of any real hour.

### §23.6.2 verification — all five

| # | Check | Result |
|---|---|---|
| 1 | Synapse healthy, no schema errors | **PASS** — `Schema now up to date`, **0** ERROR/CRITICAL lines |
| 2 | Existing user logs in with existing password | **PASS** |
| 3 | Device list intact | **PASS** — 14 devices |
| 4 | Encrypted message from before the backup still present | **PASS** — 17 `m.room.encrypted` events |
| 5 | Invite audit trail intact | **PASS** — 9 registration tokens |

### Two things the runbook was missing, both found by doing it

Neither is in §29.4, and each one stopped the restore dead with an error that
pointed somewhere else entirely.

**1. Ownership.** The config comes out of `tar` owned by root; Synapse runs as
**991**. Without `chown -R 991:991` on the restored directory it dies with:

```
PermissionError: [Errno 13] Permission denied: '/data/nexlink.thvjq.com.au.log.config'
```

which reads like a config problem and is an ownership problem.

**2. The log config.** Synapse generates one with a **file** handler pointing at
a directory that does not exist in a fresh container, and refuses to start:

```
ValueError: Unable to configure handler 'file'
```

which reads like a logging bug and is a missing directory. A restore needs
either that directory created or a console-only log config supplied.

Both now in the drill script, `infra/runbooks/restore-drill.sh`. **In a real
outage each of these would have cost the operator time while the service was
down**, which is exactly what §23.6.3 says a drill is for.

### Next drill

Quarterly, and before every Synapse major upgrade (§23.6.3).
