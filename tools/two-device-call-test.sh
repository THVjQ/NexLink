#!/usr/bin/env bash
# §17.6.1 steps 1 and 5, across two physical handsets.
#
# Everything phase 4 still owes is here, and all of it needs two devices:
#
#   1. A 1:1 call between two Android phones, each decoding the other.
#   2. A ring that reaches a **locked** phone as a full-screen takeover —
#      §15.6.1 could not observe this, because a single device is by
#      definition the one placing the call and therefore awake.
#
# Written as a script rather than done by hand because the fiddly part is not
# the calling, it is that the two handsets are different sizes: every tap has
# to be resolved from the view hierarchy, never from a coordinate that worked
# on the other phone. Learned the hard way on a one-device run.
#
#   tools/two-device-call-test.sh
#
# Expects both phones plugged in and authorised for adb, the caller already
# signed in, and CALLEE_USER / CALLEE_PASS set for the second one.
set -uo pipefail

# adb is not reliably on PATH in a fresh shell.
command -v adb >/dev/null 2>&1 || PATH="$PATH:$HOME/Android/Sdk/platform-tools"
command -v adb >/dev/null 2>&1 || { echo "adb not found; add platform-tools to PATH"; exit 2; }

APK=social/build/outputs/apk/debug/social-debug.apk
PKG=com.thvjq.nexlink.social.debug
ROOM_NAME=${ROOM_NAME:-Call test}
CALLEE_USER=${CALLEE_USER:-}
CALLEE_PASS=${CALLEE_PASS:-}

pass=0; fail=0
ok()   { printf '  \033[32mPASS\033[0m  %s\n' "$1"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; fail=$((fail+1)); }
note() { printf '        %s\n' "$1"; }

# ---- device plumbing -------------------------------------------------------

mapfile -t SERIALS < <(adb devices | awk '$2=="device"{print $1}')
if [ "${#SERIALS[@]}" -lt 2 ]; then
  echo "Two authorised devices are required; found ${#SERIALS[@]}."
  echo "An 'unauthorized' device needs the Allow USB debugging prompt accepted on its screen."
  exit 2
fi
A=${A_SERIAL:-${SERIALS[0]}}   # caller
B=${B_SERIAL:-${SERIALS[1]}}   # callee
echo "caller  A = $A  ($(adb -s "$A" shell getprop ro.product.model | tr -d '\r'))"
echo "callee  B = $B  ($(adb -s "$B" shell getprop ro.product.model | tr -d '\r'))"
echo

dump() { adb -s "$1" shell uiautomator dump /sdcard/v.xml >/dev/null 2>&1; adb -s "$1" shell cat /sdcard/v.xml 2>/dev/null; }

# Tap whatever the hierarchy says is at this label, on this device. `$2` is a
# grep -E pattern matched against the whole node, so it takes text= or
# content-desc= indifferently.
tap() {
  local s=$1 pat=$2 tries=${3:-10} b
  for _ in $(seq 1 "$tries"); do
    b=$(dump "$s" | tr '<' '\n' | grep -E "$pat" \
        | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
    if [ -n "$b" ]; then
      local n; n=$(echo "$b" | grep -oE '[0-9]+')
      local x1 y1 x2 y2; read -r x1 y1 x2 y2 <<<"$(echo "$n" | tr '\n' ' ')"
      adb -s "$s" shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
      return 0
    fi
    sleep 2
  done
  return 1
}

present() { dump "$1" | tr '<' '\n' | grep -qE "$2"; }

wait_for() {  # wait_for <serial> <pattern> <seconds>
  local s=$1 pat=$2 n=${3:-30}
  for _ in $(seq 1 "$n"); do present "$s" "$pat" && return 0; sleep 1; done
  return 1
}

in_call()  { [ "$(adb -s "$1" shell dumpsys activity services $PKG 2>/dev/null | grep -c CallService)" -gt 0 ]; }

# The call's own controls live inside the WebView, and uiautomator does not see
# WebView content — every dump of CallActivity comes back empty. So hanging up
# goes through the notification's End action (§19.5.5), which is a real Android
# view and is there precisely so ending a call never requires being in it.
hangup() {
  local s=$1
  adb -s "$s" shell input keyevent KEYCODE_HOME; sleep 1
  adb -s "$s" shell cmd statusbar expand-notifications; sleep 2
  tap "$s" 'text="Ongoing call"' 3 >/dev/null 2>&1   # expand the call row
  sleep 1
  tap "$s" 'text="Hang up"' 5 >/dev/null 2>&1
  adb -s "$s" shell input keyevent KEYCODE_BACK
}
console()  { adb -s "$1" logcat -d 2>/dev/null | grep CONSOLE; }

# ---- setup -----------------------------------------------------------------

[ -f "$APK" ] || { echo "build it first: ./gradlew :social:assembleDebug"; exit 2; }
for s in "$A" "$B"; do
  adb -s "$s" install -r "$APK" >/dev/null 2>&1 || { echo "install failed on $s"; exit 2; }
  for p in RECORD_AUDIO CAMERA POST_NOTIFICATIONS; do
    adb -s "$s" shell pm grant $PKG android.permission.$p >/dev/null 2>&1
  done
done

# B may be a fresh install. Sign it in if the sign-in screen is showing.
adb -s "$B" shell am force-stop $PKG
adb -s "$B" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
if present "$B" 'text="Sign in"'; then
  if [ -z "$CALLEE_USER" ] || [ -z "$CALLEE_PASS" ]; then
    echo "B is signed out; set CALLEE_USER and CALLEE_PASS."; exit 2
  fi
  tap "$B" 'text="Username"' && adb -s "$B" shell input text "$CALLEE_USER"
  tap "$B" 'text="Password"' && adb -s "$B" shell input text "$CALLEE_PASS"
  adb -s "$B" shell input keyevent KEYCODE_BACK        # dismiss the keyboard
  tap "$B" 'text="Sign in"'
  # §17.6.2's rc_login is one attempt per twenty seconds; give it room.
  wait_for "$B" "text=\"$ROOM_NAME\"" 90 || { echo "B did not reach the room list"; exit 2; }
fi

echo "— 1:1 between two handsets —"
adb -s "$A" logcat -c; adb -s "$B" logcat -c

# ---- test 1: A calls B, B answers -----------------------------------------

adb -s "$A" shell am force-stop $PKG
adb -s "$A" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
tap "$A" "text=\"$ROOM_NAME\"" || { bad "A could not open '$ROOM_NAME'"; exit 1; }
sleep 4
tap "$A" 'content-desc="Start a call"' || { bad "A has no call control"; exit 1; }

if wait_for "$B" 'text="Answer"' 45; then ok "B rings while awake"; else bad "B never rang"; fi
tap "$B" 'text="Answer"'
sleep 15

in_call "$A" && ok "A is in the call" || bad "A is not in the call"
in_call "$B" && ok "B is in the call" || bad "B is not in the call"

# The claim worth testing is not "connected" but "decoded the other side".
console "$A" | grep -q "Subscribed: video" && ok "A subscribed to B's video" \
  || bad "A never subscribed to a remote video track"
console "$B" | grep -q "Subscribed: video" && ok "B subscribed to A's video" \
  || bad "B never subscribed to a remote video track"
console "$A" | grep -q "encrypted=true" && ok "per-participant E2EE on (A)" \
  || bad "A did not report encrypted=true"

hangup "$A"
sleep 6
in_call "$B" && note "B still in call after A hung up (expected: B leaves on its own)"
adb -s "$B" shell am force-stop $PKG; adb -s "$A" shell am force-stop $PKG
sleep 3

# ---- test 2: the ring reaches a LOCKED phone -------------------------------

echo
echo "— the ring on a locked screen (§15.6.1's open row) —"
adb -s "$B" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
adb -s "$B" shell input keyevent KEYCODE_HOME
# `am kill`, never force-stop: a force-stopped package receives no FCM at all,
# which looks exactly like a broken push chain (§15.6.1).
adb -s "$B" shell am kill $PKG
adb -s "$B" shell input keyevent KEYCODE_SLEEP
sleep 3
adb -s "$B" logcat -c

adb -s "$A" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
tap "$A" "text=\"$ROOM_NAME\""; sleep 4
tap "$A" 'content-desc="Start a call"'

if wait_for "$B" 'text="Answer"' 45; then
  ok "the ring reaches B from cold with the screen off"
  if adb -s "$B" shell dumpsys window 2>/dev/null | grep -q "mDreamingLockscreen=true"; then
    note "B is still showing the lock screen — check the screenshot for a full-screen ring"
  fi
  adb -s "$B" exec-out screencap -p > /tmp/locked-ring.png 2>/dev/null \
    && note "screenshot: /tmp/locked-ring.png"
  tap "$B" 'text="Answer"'; sleep 12
  in_call "$B" && ok "answering from the lock screen joins the call" \
    || bad "answering from the lock screen did not join"
else
  bad "no ring on the locked phone"
  adb -s "$B" exec-out screencap -p > /tmp/locked-ring.png 2>/dev/null
fi

hangup "$A"
adb -s "$A" shell am force-stop $PKG; adb -s "$B" shell am force-stop $PKG

echo
echo "$pass passed, $fail failed"
exit "$fail"
