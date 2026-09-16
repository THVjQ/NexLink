#!/usr/bin/env python3
"""
§14.10 — WCAG AA contrast, checked mechanically in both themes.

"Contrast meets WCAG AA in both themes" is the one accessibility requirement in
§14.10 that is fully decidable from the palette, so it should not be a judgement
call made by eye. This computes it.

AA is 4.5:1 for body text and 3:1 for large text (>= 18pt, or >= 14pt bold) and
for the visual boundary of a UI component. Each pair below is a combination the
UI actually renders — checked against the source, not assumed.
"""
import re, sys, pathlib

def parse(path):
    out = {}
    for name, val in re.findall(r'<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>', path.read_text()):
        v = val[-6:] if len(val) == 8 else val          # drop any alpha prefix
        out[name] = tuple(int(v[i:i+2], 16) for i in (0, 2, 4))
    return out

def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(x) for x in c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)

# (foreground, background, minimum, where it is used)
PAIRS = [
    ("social_text",       "social_bg",      4.5, "body text on the screen background"),
    ("social_text",       "social_surface", 4.5, "body text on a card"),
    ("social_text2",      "social_bg",      4.5, "secondary text (usage figures)"),
    ("social_muted",      "social_bg",      4.5, "explanatory copy under each setting"),
    ("social_accent",     "social_bg",      4.5, "Retry / Discard, and the selected radio"),
    ("social_danger",     "social_bg",      4.5, "'Not sent' on a failed message"),
    ("social_cannot_see", "social_bg",      4.5, "§9.6.1 'we cannot see' column"),
    ("social_can_see",    "social_bg",      4.5, "§9.6.1 'we can see' column"),

    # The messaging surface (§14.1-14.3), added with the 2026-09-15 rebuild.
    #
    # These are the pairs the inbox and the timeline actually paint. They are
    # listed one per rendered combination rather than one per colour, because
    # the failure this catches is not "a colour is too light" — it is "a colour
    # that was fine on the background got reused on a card".
    ("social_text",              "social_surface",        4.5, "conversation title in the inbox"),
    ("social_text2",            "social_surface",        4.5, "the message preview under it"),
    ("social_muted",            "social_surface",        4.5, "app-bar subtitle, timestamps in the inbox"),
    ("social_on_accent_fill",   "social_accent_fill",    4.5, "Send, the unread badge, your own bubble"),
    ("social_on_accent_fill_meta", "social_accent_fill", 4.5, "the time inside your own bubble"),
    ("social_text",             "social_bubble_theirs",  4.5, "an incoming message"),
    ("social_bubble_meta",      "social_bubble_theirs",  4.5, "the time under an incoming message"),
    ("social_text",             "social_banner_bg",      4.5, "the warning card's copy"),
    ("social_danger",           "social_banner_bg",      4.5, "the warning card's headline"),
    ("social_on_avatar",        "social_avatar1",        4.5, "the initial in an avatar"),
    ("social_on_avatar",        "social_avatar2",        4.5, "the initial in an avatar"),
    ("social_on_avatar",        "social_avatar3",        4.5, "the initial in an avatar"),
    ("social_on_avatar",        "social_avatar4",        4.5, "the initial in an avatar"),
    ("social_on_avatar",        "social_avatar5",        4.5, "the initial in an avatar"),

    ("social_muted",            "social_surface2",       4.5, "hint text in an input"),
    ("social_text",             "social_surface2",       4.5, "typed text in an input"),
    ("social_text2",            "social_surface2",       4.5, "body copy on a recessed row"),
    ("social_accent",           "social_surface",        4.5, "a link on a card"),
    ("social_accent",           "social_surface2",       4.5, "a link on a recessed row"),
    ("social_danger",           "social_surface",        4.5, "a destructive action on a card"),
    ("social_text2",            "social_surface",        4.5, "body copy on a card"),
]

# Reported, but not failed.
#
# WCAG 1.4.11 covers visual information "required to identify user interface
# components and states". A hairline between list rows identifies nothing: the
# rows are already separated by whitespace and each carries its own bold label,
# so the separator is decorative and is exempt.
#
# This is listed rather than deleted because "it's decorative" is exactly the
# argument someone would make about a control that is NOT, and the number should
# stay visible so that claim can be re-checked. Darkening a hairline to 3:1 would
# make it read as a heavy rule — worse design, no accessibility gain.
#
# If a divider ever becomes the only thing distinguishing two regions, move it
# back into PAIRS.
DECORATIVE = [
    ("social_divider", "social_bg", 3.0, "hairline between list rows — decorative"),
]

def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    themes = {
        "light": root / "social-ui/src/main/res/values/colors.xml",
        "dark":  root / "social-ui/src/main/res/values-night/colors.xml",
    }
    fails = []
    for theme, path in themes.items():
        if not path.exists():
            print(f"  MISSING  {theme}: {path}")
            fails.append((theme, str(path), 0, 0, "palette file absent"))
            continue
        pal = parse(path)
        print(f"\n  {theme}")
        for fg, bg, need, where in PAIRS:
            if fg not in pal or bg not in pal:
                print(f"    ????  {fg} on {bg} — not defined in this theme")
                fails.append((theme, f"{fg}/{bg}", 0, need, "colour not defined"))
                continue
            r = ratio(pal[fg], pal[bg])
            ok = r >= need
            print(f"    {'PASS' if ok else 'FAIL'}  {r:5.2f}:1  (need {need})  {fg} on {bg} — {where}")
            if not ok:
                fails.append((theme, f"{fg}/{bg}", r, need, where))
        for fg, bg, need, where in DECORATIVE:
            if fg in pal and bg in pal:
                print(f"    info  {ratio(pal[fg], pal[bg]):5.2f}:1  (AA n/a)   {fg} on {bg} — {where}")
    if fails:
        print(f"\n  {len(fails)} contrast failure(s) — §14.10 requires WCAG AA in both themes.")
        return 1
    print("\n  All pairs meet WCAG AA in both themes.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
