# Shared adb/uiautomator helpers for the two-device tests.
#
# Extracted rather than duplicated because the two scripts drive the same two
# handsets and the one thing that must not diverge is how a tap is resolved:
# **never from a coordinate.** The phones are different sizes, so a coordinate
# that works on one is a silent mis-tap on the other, and a mis-tap in a test
# reads as a product failure.

command -v adb >/dev/null 2>&1 || PATH="$PATH:$HOME/Android/Sdk/platform-tools"
command -v adb >/dev/null 2>&1 || { echo "adb not found; add platform-tools to PATH" >&2; exit 2; }

pass=0; fail=0
ok()   { printf '  \033[32mPASS\033[0m  %s\n' "$1"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; fail=$((fail+1)); }
note() { printf '        %s\n' "$1"; }

dump() {
  adb -s "$1" shell uiautomator dump /sdcard/v.xml >/dev/null 2>&1
  adb -s "$1" shell cat /sdcard/v.xml 2>/dev/null
}

# tap <serial> <grep -E pattern> [tries]
# The pattern is matched against the whole node, so it takes text= or
# content-desc= indifferently.
#
# **A clickable match wins over a non-clickable one**, and that is not a nicety.
# The sign-in screen has a *heading* reading "Sign in" above a *button* reading
# "Sign in"; taking the first match tapped the heading, nothing happened, and
# the run sat waiting for a home screen that was never going to arrive. Same
# shape as the welcome-screen confusion in `sign_in` — a caption is not an
# identity.
tap() {
  local s=$1 pat=$2 tries=${3:-10} nodes b n x1 y1 x2 y2
  for _ in $(seq 1 "$tries"); do
    nodes=$(dump "$s" | tr '<' '\n' | grep -E "$pat")
    b=$(echo "$nodes" | grep 'clickable="true"' \
        | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
    [ -n "$b" ] || b=$(echo "$nodes" \
        | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
    if [ -n "$b" ]; then
      n=$(echo "$b" | grep -oE '[0-9]+')
      read -r x1 y1 x2 y2 <<<"$(echo "$n" | tr '\n' ' ')"
      adb -s "$s" shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
      return 0
    fi
    sleep 2
  done
  return 1
}

present() { dump "$1" | tr '<' '\n' | grep -qE "$2"; }

# wait_for <serial> <pattern> [seconds]
wait_for() {
  local s=$1 pat=$2 n=${3:-30}
  for _ in $(seq 1 "$n"); do present "$s" "$pat" && return 0; sleep 1; done
  return 1
}

texts() { dump "$1" | tr '<' '\n' | grep -oE 'text="[^"]+"' | sed 's/text="//; s/"$//'; }

# **System-rendered labels are in the DEVICE's language, not the app's.**
# CallStyle's Answer/Decline and the ongoing-call actions come from the
# platform, so on a German handset they read "Annehmen" / "Ablehnen" /
# "Auflegen". Every answer attempt in the first two-device run missed for that
# reason and looked like a ring that never arrived. Match both.
# How to tell the two session states apart from a hierarchy dump. See `sign_in`
# for why these are a control and a button rather than a heading.
HOME_RE='content-desc="New conversation"'
SIGNED_OUT_RE='text="I have an invite code"|text="Username"'

ANSWER_RE='text="Answer"|text="Annehmen"'
DECLINE_RE='text="Decline"|text="Ablehnen"'
HANGUP_RE='text="Hang up"|text="Auflegen"'

# The heads-up collapses in a few seconds and a uiautomator dump takes longer
# than that, so polling the hierarchy for a ring loses the race. The
# notification *record* lives for the ring's whole lifetime: poll that (it is
# fast), then open the shade and act there.
wait_for_ring() {
  local s=$1 n=${2:-15} i
  for i in $(seq 1 "$n"); do
    adb -s "$s" shell dumpsys notification --noredact 2>/dev/null \
      | grep -q 'channel=social_incoming_call' && return 0
    sleep 4
  done
  return 1
}

answer_ring() {
  local s=$1
  adb -s "$s" shell cmd statusbar expand-notifications >/dev/null 2>&1
  sleep 2
  tap "$s" "$ANSWER_RE" 4
}

two_devices() {
  mapfile -t SERIALS < <(adb devices | awk '$2=="device"{print $1}')
  if [ "${#SERIALS[@]}" -lt 2 ]; then
    echo "Two authorised devices are required; found ${#SERIALS[@]}."
    echo "An 'unauthorized' device needs the Allow USB debugging prompt accepted on its screen."
    exit 2
  fi
  A=${A_SERIAL:-${SERIALS[0]}}
  B=${B_SERIAL:-${SERIALS[1]}}
  echo "A = $A  ($(adb -s "$A" shell getprop ro.product.model | tr -d '\r'))"
  echo "B = $B  ($(adb -s "$B" shell getprop ro.product.model | tr -d '\r'))"
  echo
}

install_both() {
  local apk=$1 pkg=$2 s p
  [ -f "$apk" ] || { echo "build it first: ./gradlew :social:assembleDebug"; exit 2; }
  for s in "$A" "$B"; do
    adb -s "$s" install -r "$apk" >/dev/null 2>&1 || { echo "install failed on $s"; exit 2; }
    for p in RECORD_AUDIO CAMERA POST_NOTIFICATIONS; do
      adb -s "$s" shell pm grant "$pkg" "android.permission.$p" >/dev/null 2>&1
    done
  done
}

# sign_in <serial> <pkg> <user> <pass> — no-op if already signed in.
#
# **"Sign in" names two different things** and conflating them cost a run: the
# welcome screen has a *Sign in button* next to "I have an invite code", and the
# form behind it has a *Sign in submit button*. Matching on the text alone found
# the first, then looked for a Username field that was one screen away and spun
# in `tap`'s retry loop until the whole test timed out with no output.
#
# So signed-in-ness is decided by the home screen's own heading, and the form is
# identified by its Username field rather than by a button caption.
sign_in() {
  local s=$1 pkg=$2 u=$3 p=$4
  adb -s "$s" shell am force-stop "$pkg"
  adb -s "$s" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  # **Wait for the home screen rather than glancing at it.** A relaunch restores
  # the session and starts a sync before it can draw anything, and a fixed
  # `sleep 8` then `present` caught the blank screen in between — which reads as
  # "signed out", sends the run looking for a form that is not there, and fails
  # a device that was signed in the whole time.
  # **"NexLink Social" is on the welcome screen too**, so its presence does not
  # mean signed in — the third caption collision in this file.
  #
  # It used to be decided by the caption "Not signed in", which the 2026-09-15
  # UI rebuild removed along with the rest of the old welcome screen. The
  # replacement is not a caption at all: `HOME_RE` is the *floating new-message
  # button*, which exists only when there is a session — it is hidden when
  # signed out, and a hidden view is absent from a uiautomator dump entirely.
  # A control that only exists in one state is a better identity than any text,
  # which is the lesson the three collisions above keep teaching.
  sleep 5
  local i
  for i in $(seq 1 40); do
    if present "$s" "$SIGNED_OUT_RE"; then break; fi
    present "$s" "$HOME_RE" && return 0
    sleep 1
  done
  # Genuinely signed out: the welcome screen or the form is on display.
  if ! present "$s" "$SIGNED_OUT_RE"; then
    echo "$s: neither the home screen nor the sign-in screen appeared" >&2
    return 1
  fi
  [ -n "$u" ] && [ -n "$p" ] || { echo "$s is signed out and no credentials were given" >&2; return 1; }

  # From the welcome screen, step into the form. If we are already on the form,
  # the Username field is there and this is skipped.
  present "$s" 'text="Username"' || { tap "$s" 'text="Sign in"' 5 || return 1; sleep 4; }
  present "$s" 'text="Username"' || { echo "$s: never reached the sign-in form" >&2; return 1; }

  tap "$s" 'text="Username"' && adb -s "$s" shell input text "$u"
  tap "$s" 'text="Password"' && adb -s "$s" shell input text "$p"
  adb -s "$s" shell input keyevent KEYCODE_BACK     # dismiss the keyboard
  tap "$s" 'text="Sign in"'
  # A password manager will offer to save what was just typed, and its dialog
  # sits over the app — on a German-locale handset the buttons are "Speichern"
  # and "Nein danke", which matched nothing and left the run polling a home
  # screen it could not see. Real devices have software on them.
  sleep 4
  dismiss_save_password "$s"
  # §17.6.2's rc_login is one attempt per twenty seconds; give it room.
  wait_for "$s" "$HOME_RE" 90
}

# The inbox's overflow menu — where the 2026-09-15 rebuild put the six settings
# that used to be full-width buttons down the home screen. A test that wants one
# of them has to open the menu first; before the rebuild it could tap the button
# directly, which is why every caller of this is a line that used to be a `tap`.
#
# The menu button is found by its content description, not by a glyph: the icons
# are drawn rather than typed (Icon.kt), so there is no text to match.
open_home_menu() {
  local s=$1 item=$2
  tap "$s" 'content-desc="More"' 8 || return 1
  sleep 2
  tap "$s" "text=\"$item\"" 6
}

# Decline any "save this password?" prompt, in the languages these handsets use.
dismiss_save_password() {
  local s=$1 re
  for re in 'text="Nein danke"' 'text="Never"' 'text="Not now"' 'text="No thanks"' \
            'text="Nie"' 'text="Niemals"' 'text="Neu"'; do
    if present "$s" "$re"; then
      tap "$s" "$re" 3 >/dev/null 2>&1
      sleep 2
      return 0
    fi
  done
  return 0
}
