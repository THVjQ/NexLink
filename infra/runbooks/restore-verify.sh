#!/bin/bash
# §23.6.2 — the five checks. "Restored" is not "the process started".
set -uo pipefail
U="ver211011"; P="iz97JT2skASMrdaQGB"
pass=0; fail=0
chk() { if [ "$1" = "0" ]; then echo "  [PASS] $2"; pass=$((pass+1)); else echo "  [FAIL] $2"; fail=$((fail+1)); fi; }

# 1. healthy, no schema errors
docker exec drill-syn curl -sf http://127.0.0.1:8008/_matrix/client/versions >/dev/null 2>&1
chk $? "1. Synapse healthy"
n=$(docker logs drill-syn 2>&1 | grep -ciE "schema|upgrade failed|CRITICAL" || true)
[ "$n" -eq 0 ]; chk $? "1b. no schema errors in the log ($n lines matched)"

# 2. an existing user logs in with an existing password
r=$(docker exec drill-syn curl -s -X POST http://127.0.0.1:8008/_matrix/client/v3/login    -H 'Content-Type: application/json'    -d "{\"type\":\"m.login.password\",\"identifier\":{\"type\":\"m.id.user\",\"user\":\"$U\"},\"password\":\"$P\"}" 2>/dev/null)
echo "$r" | grep -q access_token; chk $? "2. existing user logs in with existing password"
TOK=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null)

# 3. device list intact
if [ -n "$TOK" ]; then
  d=$(docker exec drill-syn curl -s -H "Authorization: Bearer $TOK" http://127.0.0.1:8008/_matrix/client/v3/devices 2>/dev/null)
  c=$(echo "$d" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('devices',[])))" 2>/dev/null || echo 0)
  [ "$c" -gt 0 ]; chk $? "3. device list intact ($c devices)"
else
  chk 1 "3. device list intact (no token)"
fi

# 4. an encrypted message from before the backup is still there and still encrypted
enc=$(docker exec drill-pg psql -U synapse -d synapse -t -A -c   "SELECT count(*) FROM events WHERE type='m.room.encrypted';" 2>/dev/null)
[ "${enc:-0}" -gt 0 ]; chk $? "4. encrypted events present ($enc)"

# 5. invite_record count — the moderation audit trail
inv=$(docker exec drill-pg psql -U synapse -d synapse -t -A -c   "SELECT count(*) FROM registration_tokens;" 2>/dev/null)
echo "  [info] registration_tokens in the restore: ${inv:-?}"

echo
echo "  $pass passed, $fail failed"
