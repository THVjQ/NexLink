#!/usr/bin/env bash
# NexLink's permission set has not changed.
#
# This was invariant #6 of NexLink Social's §2.8, and it guarded NexLink rather
# than Social: "a NexLink user who never enables Social is unaffected" (§1.5)
# collapses if :app quietly gains a permission, a receiver, or a service.
#
# It lived in the Social repository until the 2026-09-21 split, which would have
# left it grepping app/build.gradle in a repository that no longer has one —
# finding nothing and reporting success. A check that stops checking while still
# passing is worse than no check, so it moved here, next to the manifest it is
# about.
#
# Run from the repository root. Exits non-zero on a change.
set -uo pipefail
cd "$(dirname "$0")/.."

MANIFEST=app/src/main/AndroidManifest.xml
BASELINE=tools/app-manifest.baseline

if [ ! -f "$MANIFEST" ]; then
  printf '  \033[31mFAIL\033[0m  %s is missing — this check cannot run\n' "$MANIFEST"
  exit 1
fi

current=$(grep -oE '(uses-permission|permission) android:name="[^"]+"' "$MANIFEST" \
          | grep -oE '"[^"]+"' | tr -d '"' | sort -u)

if [ ! -f "$BASELINE" ]; then
  echo "$current" > "$BASELINE"
  printf '  \033[33mNEW\033[0m   baseline created (%s permissions) — commit it\n' "$(echo "$current" | wc -l)"
  exit 0
fi

if diff -q <(echo "$current") "$BASELINE" >/dev/null; then
  printf '  \033[32mPASS\033[0m  NexLink permission set unchanged (%s permissions)\n' "$(echo "$current" | wc -l)"
  exit 0
fi

printf '  \033[31mFAIL\033[0m  NexLink'"'"'s permissions changed.\n'
printf '        If this is intended, update the baseline and SAY SO in the commit.\n'
printf '        Every added permission is one a user who only wanted an SMS app now grants.\n'
diff <(echo "$current") "$BASELINE" | sed 's/^/          /'
exit 1
