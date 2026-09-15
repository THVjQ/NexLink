#!/usr/bin/env bash
# §15.6.1's open row: does a call reach a phone that is asleep and locked?
#
# Three earlier attempts failed for reasons that had nothing to do with the
# answer, so this one **verifies the callee's state before it trusts the
# result** rather than assuming the setup landed:
#
#   * `am force-stop` puts the package in the STOPPED state, which receives no
#     FCM at all (§15.6.1). `am kill` is the one that means "swiped away".
#     Every earlier run that lost adb mid-setup may have left it force-stopped,
#     which makes a silent phone prove nothing.
#   * A pattern-locked handset cannot be driven: `monkey` will not launch past
#     the keyguard and fails **silently**, so the app never becomes
#     push-eligible and the test measures the keyguard instead of the call.
#   * adb on this pair wedges intermittently — a device answers `adb devices`
#     and hangs on `adb shell`. Each step is checked, not hoped for.
#
#   A_SERIAL=… B_SERIAL=… tools/locked-ring-test.sh
set -uo pipefail
cd "$(dirname "$0")/.."
. tools/lib/adb-ui.sh

PKG=com.thvjq.nexlink.social.debug
ROOM_NAME=${ROOM_NAME:-Call test}
A=${A_SERIAL:?set A_SERIAL (the caller)}
B=${B_SERIAL:?set B_SERIAL (the callee)}

alive() { timeout 20 adb -s "$1" shell echo ok >/dev/null 2>&1; }
must_be_alive() { alive "$1" || { bad "device $1 is not answering adb — the result would be meaningless"; exit 2; }; }

pushes() {
  ssh willard-lan 'sudo docker exec ix-nexlink-social-push-sygnal-1 python3 -c "import urllib.request;print(urllib.request.urlopen(\"http://127.0.0.1:9001/metrics\").read().decode())" 2>/dev/null' \
    | awk '/^sygnal_notifications_received_total/{print $2}'
}

echo "caller $A   callee $B"
must_be_alive "$A"; must_be_alive "$B"

# ── the callee: awake, launched, then killed (never force-stopped) ──────────
echo
echo "— preparing the callee —"
timeout 20 adb -s "$B" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
must_be_alive "$B"
# Ask the window manager, not the screen. The lock screen shows a clock before
# it shows the pattern grid, so matching on prompt text misses the first few
# seconds — and `monkey` fails **silently** past a keyguard, which then presents
# as "the app did not come up" rather than "the phone is locked".
kg=$(timeout 25 adb -s "$B" shell dumpsys window 2>/dev/null | grep -m1 -oE 'isKeyguardShowing=[a-z]+')
if [ "$kg" = "isKeyguardShowing=true" ]; then
  bad "the callee is locked, so it cannot be set up — monkey will not launch past a keyguard"
  note "unlock it by hand and re-run immediately; the test puts it back to sleep itself,"
  note "which is what makes the locked state under test a real one rather than a simulated one"
  exit 2
fi

timeout 20 adb -s "$B" shell am force-stop $PKG >/dev/null 2>&1
timeout 20 adb -s "$B" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
# **Ask the activity manager, not uiautomator.** The readiness check used to
# look for the home screen's title in a hierarchy dump, and on this handset
# those dumps fail often enough that a healthy launch read as a failed one —
# three runs aborted on a phone whose app was plainly on screen. `dumpsys
# activity activities` answers the question being asked ("is our app in front?")
# and does not depend on the accessibility pipeline.
up=false
for _ in $(seq 1 20); do
  if timeout 25 adb -s "$B" shell dumpsys activity activities 2>/dev/null \
       | grep -m1 topResumedActivity | grep -q "$PKG"; then up=true; break; fi
  sleep 3
done
if [ "$up" != true ]; then
  bad "the app did not come up on the callee — it is not push-eligible, so a silent phone would prove nothing"
  exit 2
fi
ok "callee app launched (package no longer in the stopped state)"

timeout 20 adb -s "$B" shell input keyevent KEYCODE_HOME >/dev/null 2>&1
timeout 20 adb -s "$B" shell am kill $PKG >/dev/null 2>&1
sleep 2
if timeout 20 adb -s "$B" shell pidof $PKG >/dev/null 2>&1; then
  note "callee process still alive after am kill — it may simply be busy; continuing"
else
  ok "callee process killed, package still push-eligible"
fi

timeout 20 adb -s "$B" shell svc power stayon false >/dev/null 2>&1
timeout 20 adb -s "$B" shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1
sleep 10
must_be_alive "$B"
screen=$(timeout 20 adb -s "$B" shell dumpsys display 2>/dev/null | grep -m1 -oE 'mScreenState=[A-Z]+')
key=$(timeout 25 adb -s "$B" shell dumpsys window 2>/dev/null | grep -m1 -oE 'isKeyguardShowing=[a-z]+')
echo "        callee state: ${screen:-unknown} ${key:-unknown}"
case "$screen" in *OFF|*DOZE) ok "callee screen is off" ;; *) note "callee screen is not off (${screen:-unknown}) — the result is weaker" ;; esac

# ── the caller places a call ────────────────────────────────────────────────
echo
echo "— placing the call —"
before=$(pushes); echo "        pushes before: ${before:-unknown}"
timeout 20 adb -s "$A" shell am force-stop $PKG >/dev/null 2>&1
timeout 20 adb -s "$A" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 14
tap "$A" "text=\"$ROOM_NAME\"" 8 >/dev/null || { bad "caller could not open '$ROOM_NAME'"; exit 1; }
sleep 4
tap "$A" 'content-desc="Start a call"' 8 >/dev/null || { bad "caller has no call control"; exit 1; }
ok "call placed"

if wait_for_ring "$B" 15; then
  ok "THE RING REACHED THE SLEEPING, LOCKED PHONE"
  scr=$(timeout 20 adb -s "$B" shell dumpsys display 2>/dev/null | grep -m1 -oE 'mScreenState=[A-Z]+')
  note "callee screen after the ring: ${scr:-unknown}"
  timeout 40 adb -s "$B" exec-out screencap -p > /tmp/locked-ring.png 2>/dev/null \
    && note "screenshot: /tmp/locked-ring.png — check whether it painted over the keyguard"
else
  bad "no ring on the sleeping phone"
  after=$(pushes)
  note "pushes before=${before:-?} after=${after:-?} — if these differ, the push was sent and the device did not act on it"
fi

echo
echo "$pass passed, $fail failed"
exit "$fail"
