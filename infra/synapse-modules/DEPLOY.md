# Enabling user-issued invites

Two steps on Willard. Step 1 is done; step 2 needs the operator.
Full rationale: `docs/social/09-invites.md` §9.5.1.

## 1. PYTHONPATH — done 2026-09-16

`PYTHONPATH=/data` on the `social-synapse` service, via `app.update` on the
`nexlink-social` app. Verified:

```bash
ssh willard-lan 'sudo docker inspect ix-nexlink-social-social-synapse-1 \
  --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -i pythonpath'
# PYTHONPATH=/data
```

**Inert on its own.** Without step 2 nothing loads and Synapse behaves exactly
as before.

## 2. Turn the module on

The module file is already at `/mnt/Pool1-MAIN/social/synapse/nexlink_invites.py`
(that path is `/data` inside the container) and imports cleanly against Synapse
1.160. What remains is the config block and a restart:

```bash
ssh willard-lan 'sudo bash -s' <<'SH'
set -e
CFG=/mnt/Pool1-MAIN/social/synapse/homeserver.yaml
BK=/mnt/Pool1-MAIN/social/backups; mkdir -p "$BK"
cp "$CFG" "$BK/homeserver.yaml.pre-invites-$(date +%Y%m%d-%H%M%S)"
grep -q '^modules:' "$CFG" && { echo "modules: already present"; exit 0; }
cat >> "$CFG" <<'YAML'

# User-issued invites — docs/social/09-invites.md §9.5.1.
# Source of truth: NexLink/infra/synapse-modules/nexlink_invites.py
modules:
  - module: nexlink_invites.NexLinkInvites
    config:
      valid_days: 14
      record_path: /data/nexlink-invites.jsonl
YAML
docker restart ix-nexlink-social-social-synapse-1
SH
```

## 3. Verify — by request, not by reading the config

```bash
# it loaded
ssh willard-lan 'sudo docker logs --since 5m ix-nexlink-social-social-synapse-1 \
  2>&1 | grep -i "nexlink: user-issued invites registered"'

# it is up
ssh willard-lan 'sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep social-synapse'

# and it refuses an unauthenticated caller (must NOT be 200)
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  https://nexlink.thvjq.com.au/_synapse/client/nexlink/invite
```

Then in the app: overflow → **Invite someone** → *Create an invite code*.

## Rolling back

Delete the `modules:` block and restart. **Codes already issued keep working** —
they are ordinary registration tokens and redemption never touches the module.

## If Synapse will not start

Almost certainly the module. Restore the backup and restart:

```bash
ssh willard-lan 'sudo bash -c "cp /mnt/Pool1-MAIN/social/backups/homeserver.yaml.pre-invites-* \
  /mnt/Pool1-MAIN/social/synapse/homeserver.yaml && docker restart ix-nexlink-social-social-synapse-1"'
```

The failure will name the module in the log; `docker logs` shows it.
