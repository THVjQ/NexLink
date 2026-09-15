#!/usr/bin/env bash
# §8.4 / §33.4 — a second device for the same account, on two real handsets.
#
# This is the rig §33.3.2 said was needed and §33.3.3 could only approximate.
# SAS verification failed there because two builds of the app on **one** phone
# can only have one in the foreground, and the handshake needs several round
# trips with both ends responsive. §33.3.3 worked around it with two SDK
# clients in one instrumentation process — a good test of the code, but not of
# the product. Two phones is the real thing.
#
# What it checks, in order:
#
#   1. A second device signs in to the same account.
#   2. Before verifying, it is **not** trusted — the app must say so.
#   3. SAS verification completes and both ends show the same emoji.
#      (§8.4.2's QR path is not built — see §8.4.2's status note.)
#   4. History **decrypts** on the new device afterwards, which is the point of
#      the whole exercise and the thing §7.4.4 warns fails silently.
#
#   ACCOUNT=user PASSWORD=pw ROOM_NAME='Call test' tools/two-device-verify-test.sh
set -uo pipefail
cd "$(dirname "$0")/.."
. tools/lib/adb-ui.sh

APK=social/build/outputs/apk/debug/social-debug.apk
PKG=com.thvjq.nexlink.social.debug
ROOM_NAME=${ROOM_NAME:-Call test}
ACCOUNT=${ACCOUNT:-}
PASSWORD=${PASSWORD:-}

two_devices
install_both "$APK" "$PKG"

# B must start from nothing: this is "a new phone", and a leftover session is
# the one thing that would make the whole test meaningless.
echo "— clearing B so it really is a new device —"
adb -s "$B" shell pm clear "$PKG" >/dev/null 2>&1
for p in RECORD_AUDIO CAMERA POST_NOTIFICATIONS; do
  adb -s "$B" shell pm grant "$PKG" "android.permission.$p" >/dev/null 2>&1
done

sign_in "$A" "$PKG" "$ACCOUNT" "$PASSWORD" || { bad "A is not signed in"; exit 1; }
if ! sign_in "$B" "$PKG" "$ACCOUNT" "$PASSWORD"; then
  bad "B could not sign in — set ACCOUNT and PASSWORD"; exit 1
fi
ok "the same account is signed in on both handsets"

# ---- 2. unverified, and saying so ------------------------------------------

echo
echo "— before verifying —"
if present "$B" 'Verify this device|not verified|unverified'; then
  ok "B says it is unverified before anything is done about it"
else
  bad "B does not say it is unverified — §8.3.2 requires that it does"
  note "texts on B: $(texts "$B" | head -8 | tr '\n' '|')"
fi

# Whether history is readable yet is recorded, not asserted: an unverified
# device legitimately cannot read history sent before it existed, and that is
# the state the next step is supposed to change.
tap "$B" "text=\"$ROOM_NAME\"" 5 >/dev/null 2>&1 && sleep 5
before=$(texts "$B" | grep -ciE "unable to decrypt|waiting for this message|encrypted message" || true)
note "B shows $before undecryptable item(s) before verification"
adb -s "$B" shell input keyevent KEYCODE_BACK; sleep 2

# ---- 3. SAS, with both ends awake ------------------------------------------

echo
echo "— SAS verification, both devices foreground —"
tap "$B" 'text="Verify this device"' 8 || { bad "B has no way to start verification"; exit 1; }
sleep 3
tap "$B" 'text="Verify"|text="Start"|text="Verify this device"' 5 >/dev/null 2>&1

# A has to accept the incoming request.
if wait_for "$A" 'text="Verify"|Verify this device|verification request' 60; then
  tap "$A" 'text="Verify"|text="Accept"|text="Verify this device"' 5 >/dev/null 2>&1
  ok "A saw the verification request"
else
  bad "A never saw the request — this is the §33.3.2 stall, on real hardware"
  note "if this fails here, the two-apps-on-one-phone theory was wrong"
  exit 1
fi

# The emoji are the security property: both sides must derive the same seven.
if wait_for "$A" 'emoji|Yes, they match|They match' 90 && \
   wait_for "$B" 'emoji|Yes, they match|They match' 90; then
  ea=$(texts "$A" | tr '\n' ' ')
  eb=$(texts "$B" | tr '\n' ' ')
  ok "both devices reached the emoji step"
  # Compared as sets of visible strings rather than parsed: the assertion that
  # matters is that the two screens agree, not what they say.
  if [ "$(echo "$ea" | tr ' ' '\n' | sort | md5sum)" = "$(echo "$eb" | tr ' ' '\n' | sort | md5sum)" ]; then
    ok "the two screens show the same emoji"
  else
    bad "the emoji differ between the devices — this is a real security failure"
    note "A: $ea"
    note "B: $eb"
  fi
  tap "$A" 'They match|Yes, they match|Confirm' 5 >/dev/null 2>&1
  tap "$B" 'They match|Yes, they match|Confirm' 5 >/dev/null 2>&1
  sleep 8
else
  bad "the emoji step was never reached"
fi

if wait_for "$B" 'Verified|verified' 45; then ok "B reports itself verified"
else bad "B does not report itself verified"; fi

# ---- 4. history decrypts ---------------------------------------------------

echo
echo "— history on the new device —"
tap "$B" "text=\"$ROOM_NAME\"" 8 >/dev/null 2>&1
sleep 10
after=$(texts "$B" | grep -ciE "unable to decrypt|waiting for this message|encrypted message" || true)
if [ "$after" -eq 0 ]; then
  ok "no undecryptable messages on the new device"
else
  bad "$after message(s) still will not decrypt after verification"
  note "§7.4.4: this is the failure that is silent — the account looks fine"
fi

echo
echo "$pass passed, $fail failed"
exit "$fail"
