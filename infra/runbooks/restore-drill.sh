#!/bin/bash
# §23.6.3 — restore drill into a SCRATCH stack. Production is not touched.
set -uo pipefail
B=/mnt/Pool1-MAIN/social/backups
DUMP=$(ls -t $B/synapse-*.dump | head -1)
CFG=$(ls -t $B/synapse-config-*.tar.gz | head -1)
echo "  dump:   $(basename "$DUMP")  ($(stat -c%s "$DUMP") bytes)"
echo "  config: $(basename "$CFG")"
START=$(date +%s)

docker rm -f drill-pg drill-syn >/dev/null 2>&1
rm -rf /tmp/drill && mkdir -p /tmp/drill/data

echo "  [1] starting scratch postgres"
docker run -d --name drill-pg \
  -e POSTGRES_USER=synapse -e POSTGRES_PASSWORD=drill \
  -e POSTGRES_DB=postgres \
  -e POSTGRES_INITDB_ARGS="--encoding=UTF8 --lc-collate=C --lc-ctype=C" \
  postgres:16-alpine >/dev/null
for i in $(seq 1 30); do
  docker exec drill-pg pg_isready -U synapse >/dev/null 2>&1 && break; sleep 2
done

echo "  [2] creating database with C collation (§23.3)"
docker exec drill-pg psql -U synapse -d postgres -c \
  "CREATE DATABASE synapse ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C' TEMPLATE template0;" >/dev/null 2>&1 \
  && echo "      created" || { echo "      FAILED"; exit 1; }

echo "  [3] pg_restore"
docker cp "$DUMP" drill-pg:/tmp/d.dump >/dev/null
docker exec drill-pg pg_restore -U synapse -d synapse --no-owner --no-acl /tmp/d.dump >/tmp/drill/restore.log 2>&1
echo "      exit $? ($(wc -l < /tmp/drill/restore.log) log lines)"

echo "  [4] restoring signing.key + homeserver.yaml (§21.6)"
tar xzf "$CFG" -C /tmp/drill/data && ls /tmp/drill/data | sed 's/^/      /'

echo "  [5] starting scratch Synapse against the restored database"
PGIP=$(docker inspect drill-pg --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
python3 - "$PGIP" <<'PY'
import sys,re
ip=sys.argv[1]
p="/tmp/drill/data/homeserver.yaml"
s=open(p).read()
s=re.sub(r'(\n\s*host:\s*)\S+', r'\g<1>'+ip, s)
s=re.sub(r'(\n\s*password:\s*)\S+', r'\g<1>drill', s)
# The drill server must not try to reach the real world.
s=s.replace('registration_requires_token: true','registration_requires_token: true')
open(p,'w').write(s)
print("      homeserver.yaml repointed at", ip)
PY
# The restored files come out of tar owned by root, and Synapse runs as 991.
# Without this it dies on "Permission denied: .../log.config" — which reads
# like a config error and is an ownership error.
# Synapse generates a log.config with a FILE handler pointing at a directory
# that does not exist in a fresh container, and dies with
# "ValueError: Unable to configure handler 'file'" — which reads like a logging
# bug and is a missing directory. Give the drill a console-only config.
cat > /tmp/drill/data/nexlink.thvjq.com.au.log.config <<'LOGEOF'
version: 1
formatters:
  precise:
    format: '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
handlers:
  console:
    class: logging.StreamHandler
    formatter: precise
root:
  level: INFO
  handlers: [console]
disable_existing_loggers: false
LOGEOF
chown -R 991:991 /tmp/drill/data
docker run -d --name drill-syn -v /tmp/drill/data:/data \
  -e SYNAPSE_CONFIG_PATH=/data/homeserver.yaml -e UID=991 -e GID=991 \
  ghcr.io/element-hq/synapse:v1.160.0 >/dev/null
for i in $(seq 1 45); do
  sleep 2
  docker exec drill-syn curl -sf http://127.0.0.1:8008/_matrix/client/versions >/dev/null 2>&1 && { echo "      synapse up after $((i*2))s"; break; }
  [ $i -eq 45 ] && echo "      synapse DID NOT START"
done
END=$(date +%s)
echo
echo "  wall clock: $((END-START))s"
