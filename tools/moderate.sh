#!/usr/bin/env bash
# Operator moderation — docs/social/31-moderation.md, runbooks §29.2.
#
# The admin API is deliberately unreachable from the internet (§26.5.2 — a
# Cloudflare Access policy denies /_synapse/admin/*), so every call here goes
# over SSH and hits Synapse on localhost. That is the whole reason this script
# exists: the moderation surface is a bearer token inside a container, and
# "ssh, docker exec, curl, remember the JSON" is not something to compose
# correctly while something is actually going wrong.
#
#   tools/moderate.sh reports              what has been reported
#   tools/moderate.sh report <id>          one report, in full
#   tools/moderate.sh whois <user>         account summary
#   tools/moderate.sh suspend <user>       reversible. Do this first.
#   tools/moderate.sh unsuspend <user>
#   tools/moderate.sh deactivate <user>    NOT reversible (§7.6)
#   tools/moderate.sh invites              outstanding invite codes
#   tools/moderate.sh revoke <code>
#
# <user> may be a localpart or a full @user:server.
set -uo pipefail

HOST=${WILLARD:-willard-lan}
C=ix-nexlink-social-social-synapse-1
DOMAIN=nexlink.thvjq.com.au

die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
note() { printf '\033[2m%s\033[0m\n' "$*"; }

mxid() {
  case "$1" in
    @*:*) printf '%s' "$1" ;;
    @*)   printf '%s:%s' "$1" "$DOMAIN" ;;
    *)    printf '@%s:%s' "$1" "$DOMAIN" ;;
  esac
}

# All privileged calls funnel through here so the token is read on the host and
# never lands in this machine's shell history or process list.
api() {
  local method=$1 path=$2 body=${3:-}
  local q="sudo docker exec $C curl -sS -X $method -H \"Authorization: Bearer \$TOK\""
  [ -n "$body" ] && q="$q -H 'Content-Type: application/json' -d '$body'"
  ssh "$HOST" "TOK=\$(sudo cat /mnt/Pool1-MAIN/social/synapse/.operator-token); $q 'http://localhost:8008$path'"
}

cmd=${1:-help}
case "$cmd" in

reports)
  api GET "/_synapse/admin/v1/event_reports?limit=${2:-50}&dir=b" | python3 -c '
import json, sys, datetime
d = json.load(sys.stdin)
rs = d.get("event_reports", [])
if not rs:
    print("  No reports.")
    raise SystemExit
print("  %d report(s), newest first" % d.get("total", len(rs)))
print()
for r in rs:
    when = datetime.datetime.fromtimestamp(r["received_ts"] / 1000).strftime("%d %b %Y %H:%M")
    reason = (r.get("reason") or "").strip() or "(none given)"
    print("  #%s  %s" % (r["id"], when))
    print("      reported by : %s" % r["user_id"])
    print("      against     : %s" % (r.get("sender") or "(unknown)"))
    print("      reason      : %s" % reason)
    print("      room        : %s" % r["room_id"])
    print()
print("  Message CONTENT is not here and cannot be: it is end-to-end encrypted.")
print("  A report is who, where and why (S31.3.2).")'
  ;;

report)
  [ $# -ge 2 ] || die "usage: moderate.sh report <id>"
  api GET "/_synapse/admin/v1/event_reports/$2" | python3 -m json.tool
  ;;

whois)
  [ $# -ge 2 ] || die "usage: moderate.sh whois <user>"
  api GET "/_synapse/admin/v2/users/$(mxid "$2")" | python3 -c '
import json, sys, datetime
u = json.load(sys.stdin)
if "errcode" in u:
    print("  " + u.get("error", "not found"))
    raise SystemExit
print("  %s" % u["name"])
print("      display name : %s" % (u.get("displayname") or "(none)"))
print("      created      : %s" % datetime.datetime.fromtimestamp(u["creation_ts"]).strftime("%d %b %Y"))
print("      admin        : %s" % bool(u.get("admin")))
print("      deactivated  : %s" % bool(u.get("deactivated")))
print("      suspended    : %s" % bool(u.get("suspended")))
print("      locked       : %s" % bool(u.get("locked")))'
  ;;

suspend|unsuspend)
  [ $# -ge 2 ] || die "usage: moderate.sh $cmd <user>"
  want=$([ "$cmd" = suspend ] && echo true || echo false)
  api PUT "/_synapse/admin/v1/suspend/$(mxid "$2")" "{\"suspend\": $want}" | python3 -m json.tool
  note "Suspension is reversible. Per §9.5 it also revokes that account's unredeemed invites."
  ;;

deactivate)
  [ $# -ge 2 ] || die "usage: moderate.sh deactivate <user>"
  who=$(mxid "$2")
  printf '\033[31mDeactivating %s cannot be undone (§7.6).\033[0m\n' "$who"
  printf 'Suspend is reversible and is usually the right first move.\n'
  read -r -p "Type the full user id to confirm: " typed
  [ "$typed" = "$who" ] || die "Not confirmed — nothing done."
  api POST "/_synapse/admin/v1/deactivate/$who" '{"erase": true}' | python3 -m json.tool
  ;;

invites)
  api GET "/_synapse/admin/v1/registration_tokens" | python3 -c '
import json, sys, datetime
ts = json.load(sys.stdin).get("registration_tokens", [])
if not ts:
    print("  No invite codes outstanding.")
    raise SystemExit
now = datetime.datetime.now().timestamp() * 1000
rows = []
for t in ts:
    ms = t.get("expiry_time")
    allowed = t.get("uses_allowed")
    spent = allowed is not None and t["completed"] >= allowed
    expired = ms is not None and ms < now
    # Live = someone could still create an account with it. That is the only
    # distinction that matters here; everything else is history.
    live = not spent and not expired
    code = t["token"]
    pretty = "-".join(code[i:i + 4] for i in range(0, len(code), 4)) if len(code) == 12 else code
    when = datetime.datetime.fromtimestamp(ms / 1000).strftime("%d %b %Y") if ms else "never"
    state = "LIVE " if live else ("spent" if spent else "expired")
    rows.append((live, "  %-6s %-16s used %s/%s  pending %s  expires %s"
                 % (state, pretty, t["completed"], allowed, t["pending"], when)))
for live, line in sorted(rows, key=lambda r: not r[0]):
    print(line)
print()
print("  Only LIVE codes can still create an account. Revoke one with:")
print("    tools/moderate.sh revoke <code>")'
  ;;

revoke)
  [ $# -ge 2 ] || die "usage: moderate.sh revoke <code>"
  api DELETE "/_synapse/admin/v1/registration_tokens/$(echo "$2" | tr -d '-' | tr 'a-z' 'A-Z')" >/dev/null \
    && echo "  revoked"
  ;;

*)
  sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
  ;;
esac
