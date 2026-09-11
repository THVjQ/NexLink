# §23 — PostgreSQL, backup and restore

> **Confidence:** `DURABLE`
> The restore requirement and the reasoning about what a ZFS snapshot is not are
> durable. Specific commands track Synapse and PostgreSQL versions.

---

## 23.1 What the database holds, and what it does not

Worth stating precisely, because it determines how much the backup matters.

| In PostgreSQL | Not in PostgreSQL |
|---|---|
| Accounts, password hashes, device records | Message *plaintext* — the server never has it (§5.3.3) |
| Room state, membership, power levels | Decryption keys for message content |
| **Encrypted** event bodies | Media files (those are on disk, §25) |
| Access tokens, push registrations | The client's local store (§12) |
| Encrypted cross-signing and key-backup blobs (§7.4) | |
| `invite_record`, `acceptance_record` (§9.2.2, §9.6.2) | |

So: losing the database does **not** disclose message content, and restoring it
does **not** recover anything the users' own devices could not. But losing it
without a backup destroys every account, every device identity and every
encrypted key backup — which, for users on the no-email recovery path (§7),
means their history is gone even though their phones are fine.

**That is the stake. Not confidentiality — survivability.**

---

## 23.2 The uid trap, before anything else

This box has already lost an afternoon to exactly this, in the Nextcloud
install, and the note in the host's operating documentation is unambiguous:

> **Postgres in the Nextcloud app runs as uid 999, not 568.** A `host_path` for
> `postgres_data` owned by `apps:apps` makes `postgres_upgrade` exit 1 and the
> whole install roll back. Use `ix_volume` for postgres.

**Therefore: Synapse's PostgreSQL uses an `ix_volume`, not a host path.** This
is not a preference and it is not up for rediscovery.

The failure is nasty because it is misdirected: the rollback logs a
`pull access denied` line that looks like the cause and is a warning about the
hashed local build tag. The actual cause is further down, in a line reading
`didn't complete successfully`. Anyone debugging a failed Social install on this
host should look for that string first.

### 23.2.1 What using ix_volume costs

An `ix_volume` lives under `Pool1-MAIN/ix-apps`, which mounts at `/mnt/.ix-apps`
— **not** `/mnt/Pool1-MAIN/ix-apps`, because `canmount=noauto`. That path
assumption has already silently skipped 2.3 GB in this operator's backup
tooling while still reporting success.

Consequence for this chapter: the database directory is not in the tidy
`Pool1-MAIN/social/` tree of §21.5, and **any backup script that walks that tree
will miss the database entirely** while appearing to work perfectly. §23.4's
approach — dump to a file inside the tree — exists partly to make this
impossible.

---

## 23.3 Configuration

Synapse requires a specific collation. Getting it wrong is discovered late and
fixed by recreating the database.

```sql
CREATE DATABASE synapse
  ENCODING 'UTF8'
  LC_COLLATE 'C'
  LC_CTYPE 'C'
  TEMPLATE template0
  OWNER synapse;
```

`LC_COLLATE 'C'` and `LC_CTYPE 'C'` are mandatory. Synapse checks at startup and
refuses to run otherwise — which is the good outcome; the bad one is a database
created with the right settings, restored later into one with the wrong ones.
**The restore drill (§23.6) must assert collation**, not assume it.

```yaml
database:
  name: psycopg2
  args:
    user: synapse
    password: <from app config, not from this file>
    database: synapse
    host: <internal service name>
    cp_min: 5
    cp_max: 10
```

`cp_max: 10` is ample at this scale (§28). Connection pool exhaustion is a
worker-deployment problem and this is not one (§21.7).

---

## 23.4 Backup

### 23.4.1 Why ZFS snapshots are not the backup

`Pool1-MAIN` snapshots every 15 minutes and replicates to `Pool2-BACKUP`. It is
tempting to call that the database backup. It is not, for two independent
reasons:

1. **A snapshot of a running PostgreSQL data directory is a crash-consistent
   image, not a consistent database.** It will usually recover, by replaying
   WAL, the way a database recovers from a power cut. "Usually" is not a backup
   property. And the failure is silent until the day of the restore.
2. **The data directory is in an `ix_volume` (§23.2.1)**, outside the
   `Pool1-MAIN/social` tree. It is still captured by the recursive pool-level
   task — but anyone reasoning about "the Social dataset" will not find it
   there, and a future narrowing of that task would drop it without anyone
   noticing.

Snapshots are a genuinely excellent *supplement*: they give point-in-time
rollback at 15-minute granularity for everything on the pool. They are not the
primary mechanism.

### 23.4.2 The primary mechanism: pg_dump

```bash
#!/bin/bash
# Runs as a TrueNAS cron job — NOT a host crontab entry (appliance rule).
set -euo pipefail

DEST=/mnt/Pool1-MAIN/social/backups
STAMP=$(date -u +%Y-%m-%dT%H-%M-%SZ)
mkdir -p "$DEST"

docker exec <postgres-container> \
  pg_dump -U synapse --format=custom --compress=9 synapse \
  > "$DEST/synapse-$STAMP.dump.tmp"

# Only becomes a real backup once it is complete.
mv "$DEST/synapse-$STAMP.dump.tmp" "$DEST/synapse-$STAMP.dump"

# Signing key and config travel with it — §21.6
tar czf "$DEST/synapse-config-$STAMP.tar.gz" \
  -C /mnt/Pool1-MAIN/social/synapse signing.key homeserver.yaml

find "$DEST" -name 'synapse-*.dump'        -mtime +14 -delete
find "$DEST" -name 'synapse-config-*.tar.gz' -mtime +14 -delete

echo "result=OK size=$(stat -c%s "$DEST/synapse-$STAMP.dump") when=$STAMP" \
  > "$DEST/last-run.status"
```

Design points, each of which is a mistake someone makes:

- **`.tmp` then `mv`.** A dump interrupted halfway is a file that looks like a
  backup. The rename is atomic; a partial file never wears the real name.
- **Writing into `Pool1-MAIN/social/backups`** puts the dump inside the
  replicated tree, so it reaches `Pool2-BACKUP` within 15 minutes for free. The
  dump is the thing that needs replicating, not the live data directory.
- **The signing key travels with it.** A database restore without the matching
  `signing.key` produces a server that cannot validate its own history (§21.6).
  They are one backup, not two.
- **`last-run.status` mirrors the existing convention** on this box, where
  `backup-tools/last-run.status` is checked with `result=`. Same format, same
  habit. **`WARN` is not success.**

Scheduled via `cronjob.create` through the middleware API — a host crontab entry
would be lost on update, and the appliance rule requires scheduled work to live
in the config database.

### 23.4.3 Frequency

| | |
|---|---|
| Dump | Daily, 03:00 local |
| Retention on Willard | 14 days |
| ZFS snapshots | Every 15 minutes, existing task |
| RPO | 24 hours from the dump; ~15 minutes from a snapshot |
| RTO | Target under 1 hour (§23.6 measures the real number) |

A 24-hour RPO on the dump is acceptable **because the snapshot layer sits under
it** and because the users' devices hold the primary copy of their own history
(§12.2). It would not be acceptable for a service where the server was the only
copy.

---

## 23.5 The offsite gap — say this plainly

**There is no offsite backup of anything on Willard.** Both pools are in the
same chassis. Pool1→Pool2 replication covers pool loss, bad deletes and
ransomware. It does not cover fire, theft, or a dead PSU.

This was already the largest open risk on this host before Social existed. The
Proton Drive route was attempted and abandoned — Willard egresses via Starlink
CGNAT and Proton's anti-abuse layer challenges rclone from that IP with
`422 ... CAPTCHA (Code=9001)` — and the standing recommendation is Backblaze B2,
which TrueNAS Cloud Sync supports natively at roughly two cents a month at this
data size.

**Social raises the stakes on that gap.** Specifically:

- `signing.key` is unrecoverable (§21.6). Losing it is not "restore from
  yesterday", it is "the homeserver no longer exists".
- Users on the no-email recovery path (§7.3) have an encrypted key backup on
  this server and nowhere else. Losing the server loses their history even
  though their phones still work.

**Recommendation, and it is a strong one: configure B2 before phase 5 opens the
service to real users.** The existing `backup-tools/proton-backup.sh` is
provider-agnostic — only `REMOTE`, `DEST_BOOT` and `DEST_POOL` at the top need
changing — so the work is creating the remote and recreating the cron job, not
writing a script.

Until that is done, §9.6.1's acceptance gate should not promise durability the
infrastructure cannot deliver, and §36's risk register carries this at its
highest severity.

---

## 23.6 Restore, and the drill

**A backup that has not been restored is a hypothesis.**

### 23.6.1 Procedure

```bash
# 1. Stop Synapse. Do not skip; restoring under a live server corrupts both.
sudo midclt call app.stop nexlink-social-synapse

# 2. Recreate the database with the correct collation (§23.3)
docker exec -i <pg> psql -U postgres <<'SQL'
DROP DATABASE IF EXISTS synapse;
CREATE DATABASE synapse ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C'
  TEMPLATE template0 OWNER synapse;
SQL

# 3. Restore
docker exec -i <pg> pg_restore -U synapse -d synapse --clean --if-exists \
  < /mnt/Pool1-MAIN/social/backups/synapse-<stamp>.dump

# 4. Restore signing.key and homeserver.yaml from the matching tarball
# 5. Start, and verify (§23.6.2)
sudo midclt call app.start nexlink-social-synapse
```

Step 2's collation clause is the one that gets skipped, because the database
"already exists" from the failed attempt before it. Synapse will refuse to start
and the error will be clear — but only after the restore has been sitting there
for twenty minutes.

### 23.6.2 Verification — what "restored" means

Not "the process started". All five:

1. Synapse reports healthy and logs no schema errors.
2. An existing user logs in with an existing password.
3. That user's device list is intact and its keys still verify (§8.6).
4. An encrypted message sent before the backup still decrypts on a client.
5. **`SELECT count(*) FROM invite_record`** matches what it was — the invite
   tree (§9.4) is the moderation audit trail and a silently truncated one is
   worse than none.

### 23.6.3 The drill

**Quarterly, and before every Synapse major upgrade.** Restore the latest dump
into a scratch container on Willard, run the five checks in §23.6.2, record the
wall-clock time in the runbook (§29.4).

The recorded time is the point. §23.4.3 claims an RTO under an hour; that claim
is worth nothing until somebody has stood there with a stopwatch. This box's
own failover documentation carries the same warning — the procedure is
*untested against a real pool loss*, and whether `docker.update` adopts or
reinitialises an existing `ix-apps` is unverified. Do not add a second untested
recovery path to that pile.

---

## 23.7 Maintenance

| Task | Frequency | Note |
|---|---|---|
| `VACUUM ANALYZE` | Autovacuum handles it | Verify it is actually running; a table that never vacuums grows without bound |
| State compression | As needed | Synapse's `state_compressor` can dramatically shrink `state_groups_state` on large rooms. Not needed at this scale; know it exists. |
| Index bloat | Watch via §27 | |
| Version upgrades | Yearly-ish | **A major PostgreSQL upgrade needs a dump/restore cycle.** The Nextcloud `postgres_upgrade` failure (§23.2) is what this looks like when it goes wrong on this box. |

---

## 23.8 Open questions

- **Is 14 days of dumps on Willard the right retention**, given the ZFS
  snapshots already give two weeks at finer granularity? Possibly redundant.
  Leaning: keep both — they fail differently, and the dump is the one that is
  known-consistent.
- **Should the dump be encrypted at rest?** It contains password hashes, access
  tokens and IP logs. It sits on a replicated pool with no offsite copy today;
  the moment it is shipped to B2 (§23.5), encryption becomes mandatory rather
  than optional. The existing backup script's model — client-side encryption
  before upload — is the right one.
- **WAL archiving for point-in-time recovery?** Would cut the 24-hour RPO to
  minutes. Real operational complexity for a service whose primary copy lives on
  users' devices. Leaning no, revisit if the user base grows past §28's
  assumptions.
