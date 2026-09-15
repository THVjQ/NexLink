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
tap() {
  local s=$1 pat=$2 tries=${3:-10} b n x1 y1 x2 y2
  for _ in $(seq 1 "$tries"); do
    b=$(dump "$s" | tr '<' '\n' | grep -E "$pat" \
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
sign_in() {
  local s=$1 pkg=$2 u=$3 p=$4
  adb -s "$s" shell am force-stop "$pkg"
  adb -s "$s" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 8
  present "$s" 'text="Sign in"' || return 0
  [ -n "$u" ] && [ -n "$p" ] || { echo "$s is signed out and no credentials were given" >&2; return 1; }
  tap "$s" 'text="Username"' && adb -s "$s" shell input text "$u"
  tap "$s" 'text="Password"' && adb -s "$s" shell input text "$p"
  adb -s "$s" shell input keyevent KEYCODE_BACK     # dismiss the keyboard
  tap "$s" 'text="Sign in"'
  # §17.6.2's rc_login is one attempt per twenty seconds; give it room.
  wait_for "$s" 'text="NexLink Social"' 90
}
