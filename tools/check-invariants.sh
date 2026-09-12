#!/usr/bin/env bash
# docs/social/34-testing.md §34.2 — the invariant tests.
#
# §2.8 lists seven invariants and says that violating one "is not a bug — it is
# a decision reversal". A decision reversal that happens by accident is the
# failure mode §2.1 exists to prevent, so the invariants get mechanical
# enforcement.
#
# Four of the seven are greps. §34.2: "They cost an afternoon to write and they
# are the difference between a constraint and an intention."
#
# Run from the repository root. Exits non-zero on any violation.
set -uo pipefail
cd "$(dirname "$0")/.."

fail=0
pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; fail=1; }

SOCIAL_SRC=(social social-core social-ui social-contract)
# social-rtc arrives in phase 4 (§33.5); include it once it exists.
[ -d social-rtc ] && SOCIAL_SRC+=(social-rtc)

echo "§2.8 invariant checks"
echo

# ── Invariant 4 — no capture to persistent storage (§18.2) ──────────────────
# "No API is called that captures screen, camera or microphone content to
# persistent storage." D3 (§2.4) is what keeps sharing distinct from recording,
# and §18.2 asks for exactly this grep.
hits=$(grep -rn --include='*.kt' --include='*.java' \
        -E '\b(MediaRecorder|MediaMuxer)\b' "${SOCIAL_SRC[@]}" 2>/dev/null \
        | grep -v '^\S*:[0-9]*: *[/*]' || true)
if [ -z "$hits" ]; then
  pass "no MediaRecorder / MediaMuxer in :social-* (§2.8 #4, §18.2)"
else
  bad "capture-to-file API found — this is D3 (§2.4), not a code-review nit:"
  echo "$hits" | sed 's/^/          /'
fi

# ── Invariant 7 — logs carry no identifiers (§27.5.1) ───────────────────────
# NexLink's DebugLog is reusable in :social (§10.7), but a log line carrying a
# room ID or an MXID, written to a file the user can export, is a leak with a
# friendly UI on it.
hits=$(grep -rn --include='*.kt' --include='*.java' \
        -E '(Log\.[vdiwe]|DebugLog[^(]*)\(.*\$\{?(room|roomId|mxid|userId|eventId|deviceId)' \
        "${SOCIAL_SRC[@]}" 2>/dev/null || true)
if [ -z "$hits" ]; then
  pass "no identifier-shaped interpolation in :social-* logging (§2.8 #7, §27.5.1)"
else
  bad "logging call interpolates an identifier — redact at the call site:"
  echo "$hits" | sed 's/^/          /'
fi

# ── Invariant 6 — NexLink gains nothing (§34.2.1) ───────────────────────────
# §1.5's first success criterion. The dependency rule (§10.4) protects the build
# graph; this protects everything else — a permission added "temporarily", a
# receiver added for debugging, a service that crept in through a library.
APP_MANIFEST=app/src/main/AndroidManifest.xml
BASELINE=tools/app-manifest.baseline
if [ -f "$APP_MANIFEST" ]; then
  current=$(grep -oE '(uses-permission|permission) android:name="[^"]+"' "$APP_MANIFEST" \
            | grep -oE '"[^"]+"' | tr -d '"' | sort -u)
  if [ ! -f "$BASELINE" ]; then
    echo "$current" > "$BASELINE"
    pass "app manifest baseline created ($(echo "$current" | wc -l) permissions) — commit it"
  elif diff -q <(echo "$current") "$BASELINE" >/dev/null; then
    pass "NexLink's permission set unchanged (§2.8 #6, §34.2.1)"
  else
    bad "NexLink's permissions changed. If this is intended, update the baseline"
    bad "and say so in the commit — D1 (§2.2) is what this protects:"
    diff <(echo "$current") "$BASELINE" | sed 's/^/          /'
  fi
fi

# ── :app must not link the social stack (§10.4) ─────────────────────────────
# The Gradle rule fails the build; this catches the case where someone routes
# around it. Cheap, and it runs without Gradle.
hits=$(grep -nE "project\(':social-(core|rtc|ui)'\)|project\(':social'\)" \
        app/build.gradle wear/build.gradle 2>/dev/null || true)
if [ -z "$hits" ]; then
  pass ":app and :wear declare no forbidden social dependency (§10.1)"
else
  bad "forbidden dependency declared:"
  echo "$hits" | sed 's/^/          /'
fi

# ── SDK types must not escape :social-core (§11.6) ──────────────────────────
# "The dependency rule from §10.4 should be extended to forbid SDK imports
# outside :social-core, enforced the same mechanical way."
OUTSIDE=(social social-ui social-contract)
hits=$(grep -rn --include='*.kt' -E '^import (org\.matrix|io\.element|net\.folivo)' \
        "${OUTSIDE[@]}" 2>/dev/null || true)
if [ -z "$hits" ]; then
  pass "no Matrix SDK import outside :social-core (§11.6)"
else
  bad "SDK type has leaked past the seam — that seam is the whole insurance"
  bad "policy against §11's SDK decision being wrong:"
  echo "$hits" | sed 's/^/          /'
fi

# ── The DOB is never stored (§9.6.2, §32.2) ─────────────────────────────────
hits=$(grep -rn --include='*.kt' -iE 'va[lr] +(dateOfBirth|dob)\b' \
        "${SOCIAL_SRC[@]}" 2>/dev/null | grep -viE 'dob(Day|Month|Year)' || true)
if [ -z "$hits" ]; then
  pass "no date-of-birth field persisted (§9.6.2, §32.2)"
else
  bad "date of birth looks like it is being retained — §9.6.2 stores only the"
  bad "boolean outcome:"
  echo "$hits" | sed 's/^/          /'
fi

# ── Every stopSelf() in a foreground service is inside stopCleanly() (§15.2.2) ─
#
# §15.2.2 makes this checkable and it is worth checking, because the failing
# version looks correct. A foreground service owes the platform a
# startForeground() on EVERY path out of onCreate/onStartCommand; a bare
# stopSelf() on a path that decided not to run exits with that debt and the
# platform kills the process. stopCleanly() promotes first — which reads as
# pointless right up until it is the only thing keeping the app alive.
fgs_files=$(grep -rl --include='*.kt' 'startForeground(' "${SOCIAL_SRC[@]}" 2>/dev/null || true)
hits=""
for f in $fgs_files; do
  # A stopSelf() is legal only on a line inside the stopCleanly() definition.
  # Comment lines are skipped: the KDoc on stopCleanly() quotes the rule it
  # enforces, and matching that was the check's first false positive.
  bad=$(awk '
    { line = $0; sub(/^[[:space:]]+/, "", line) }
    line ~ /^(\*|\/\/|\/\*)/ { next }
    /private fun stopCleanly/   { inclean = 1 }
    inclean && /^    \}/        { inclean = 0 }
    /stopSelf\(\)/ && !inclean  { print FILENAME ":" FNR ": " line }
  ' "$f")
  [ -n "$bad" ] && hits="${hits}${bad}
"
done
if [ -z "$hits" ]; then
  pass "every stopSelf() in a foreground service is inside stopCleanly() (§15.2.2)"
else
  bad "a foreground service calls stopSelf() outside stopCleanly() — the process"
  bad "still owes the platform a startForeground() and will be killed (§15.2.1):"
  printf "%b" "$hits" | sed 's/^/          /'
fi

# ── WCAG AA contrast in both themes (§14.10) ────────────────────────────────
# Mechanically decidable from the palette, so it should not be a judgement made
# by eye. Added after the light theme was found failing on five colours — the
# development phone runs dark, so nobody had ever looked.
if python3 "$(dirname "$0")/check-contrast.py" >/tmp/contrast.$$ 2>&1; then
  pass "WCAG AA contrast in both themes (§14.10)"
else
  bad "a colour pair is below WCAG AA — §14.10 requires it in BOTH themes:"
  grep -E "FAIL|MISSING|\?\?\?\?" /tmp/contrast.$$ | sed 's/^/        /'
fi
rm -f /tmp/contrast.$$

echo
if [ "$fail" -eq 0 ]; then
  echo "All invariant checks passed."
else
  echo "Invariant check FAILED. See docs/social/02-decisions.md §2.8 before changing anything above."
fi
exit "$fail"
