import json, subprocess, sys

APP = "nexlink-social"
BRANCH = "feat/social-foundations"
REPO = "https://github.com/THVjQ/NexLink.git"     # public: no token, unlike thvjq-sync
FILES = ["nexlink_invites.py", "nexlink_admin.py", "nexlink_admin.html"]

# Only these three files are copied, by name. /data also holds the homeserver
# signing key, operator passwords and homeserver.yaml — the websites'
# "cp -a /repo/. /site/ && rm -rf" mirror would destroy the server's identity.
# A .py that does not compile is refused rather than deployed: a module that
# fails to load puts Synapse into a restart loop (§31.6), and this runs
# unattended.
CMD = f"""set -u
apk add --no-cache git >/dev/null 2>&1 || true
if [ ! -d /repo/.git ]; then
  git clone --depth 1 --sparse -b {BRANCH} {REPO} /repo || exit 1
  git -C /repo sparse-checkout set infra/synapse-modules || exit 1
fi
git config --global --add safe.directory /repo
while true; do
  if git -C /repo fetch --depth 1 origin {BRANCH} && \
     git -C /repo reset --hard origin/{BRANCH} >/dev/null; then
    changed=0
    for f in {' '.join(FILES)}; do
      src=/repo/infra/synapse-modules/$f
      dst=/data/$f
      [ -f "$src" ] || continue
      cmp -s "$src" "$dst" && continue
      case "$f" in
        *.py)
          if ! python3 -m py_compile "$src" 2>&1; then
            echo "SYNC REFUSED: $f does not compile — not deploying"
            continue
          fi ;;
      esac
      cp "$src" "$dst" && chown 991:991 "$dst" && chmod 644 "$dst" \
        && echo "SYNC: updated $f" && changed=1
    done
    if [ "$changed" = 1 ]; then
      date -u +%Y-%m-%dT%H:%M:%SZ > /data/.modules-pending-restart
      chown 991:991 /data/.modules-pending-restart 2>/dev/null || true
      echo "SYNC: modules changed. Synapse must be RESTARTED to load them."
    fi
  else
    echo "SYNC: fetch failed, leaving existing modules untouched"
  fi
  sleep 120
done
"""

cfg = json.loads(subprocess.run(
    ["midclt", "call", "app.config", APP], capture_output=True, text=True, check=True).stdout)
for k in ("ix_context", "ix_certificates", "ix_certificate_authorities", "ix_volumes"):
    cfg.pop(k, None)

cfg["services"]["social-sync"] = {
    "image": "python:3.12-alpine",
    "restart": "unless-stopped",
    "volumes": [
        {"type": "bind", "source": "/mnt/Pool1-MAIN/social/synapse", "target": "/data"},
        {"type": "volume", "source": "social_sync_repo", "target": "/repo"},
    ],
    "command": ["/bin/sh", "-c", CMD],
}
cfg.setdefault("volumes", {})["social_sync_repo"] = {}

# Compose interpolates $VAR in the compose file itself, and $f/$src/$dst/$changed
# are unset there, so every shell variable was replaced with an empty string
# before the container started: `src=/repo/infra/synapse-modules/`, `[ -f "" ]`.
# The loop ran and did nothing, silently. `$$` is how compose is told to emit a
# literal `$`.
cfg["services"]["social-sync"]["command"][2] = \
    cfg["services"]["social-sync"]["command"][2].replace("$", "$$")

payload = json.dumps({"custom_compose_config": cfg})
print(f"payload {len(payload)} bytes; services now: {list(cfg['services'])}")
r = subprocess.run(["midclt", "call", "app.update", APP, payload],
                   capture_output=True, text=True)
print("rc", r.returncode)
print((r.stdout or r.stderr)[:600])
