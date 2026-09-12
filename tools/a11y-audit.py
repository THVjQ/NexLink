#!/usr/bin/env python3
"""
§14.10 — audit the screen that is actually on the phone.

Two of §14.10's requirements are only decidable against a rendered layout:

  - "Touch targets at least 48dp, including reaction chips, which are the most
    commonly undersized element in messaging apps."
  - "Every interactive element has a content description."

A code review cannot answer either, because both depend on what the layout
measures to at runtime — a TextView with 12sp text and no minHeight is 20dp
tall no matter how the source reads. So this walks the live uiautomator tree.

Usage:  tools/a11y-audit.py [serial]

It reports; it does not fail a build. Some findings are legitimate (a decorative
label that happens to be clickable because its parent is), so the output is for
a person to read.

**Scroll before you trust it.** uiautomator reports only what is on screen, and
it reports bounds *clipped to the viewport*. A row half-cut by the bottom edge is
reported at its visible height, so a perfectly good 48dp control at the edge of a
ScrollView shows up here as 19dp. That exact false positive was hit the first
time this was run. Scroll each screen to the end and re-run; a real undersized
target is small wherever it sits.
"""
import re, subprocess, sys, xml.etree.ElementTree as ET

MIN_DP = 48

def sh(args):
    return subprocess.run(args, capture_output=True, text=True, timeout=60)

def main():
    serial = sys.argv[1] if len(sys.argv) > 1 else None
    adb = ["adb"] + (["-s", serial] if serial else [])

    dens = sh(adb + ["shell", "wm", "density"]).stdout
    m = re.search(r"(\d+)\s*$", dens.strip().splitlines()[-1]) if dens.strip() else None
    density = int(m.group(1)) / 160 if m else 1.0

    sh(adb + ["shell", "uiautomator", "dump", "/sdcard/a11y.xml"])
    xml = sh(adb + ["shell", "cat", "/sdcard/a11y.xml"]).stdout
    if "<hierarchy" not in xml:
        print("  could not read the UI tree — is a screen open?")
        return 1

    root = ET.fromstring(xml[xml.index("<hierarchy"):])
    small, undescribed, checked = [], [], 0

    for n in root.iter("node"):
        a = n.attrib
        if a.get("clickable") != "true":
            continue
        checked += 1
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        w, h = (x2 - x1) / density, (y2 - y1) / density
        label = a.get("text") or a.get("content-desc") or f"<{a.get('class','?').split('.')[-1]}>"
        if w < MIN_DP or h < MIN_DP:
            small.append((label, round(w), round(h)))
        if not (a.get("content-desc") or a.get("text")):
            undescribed.append((a.get("class", "?").split(".")[-1], a.get("bounds")))

    print(f"\n  §14.10 audit — {checked} clickable element(s), density {density:g}x\n")
    if small:
        print(f"  Touch targets under {MIN_DP}dp:")
        for label, w, h in small:
            print(f"    {w:3d} x {h:3d} dp   {label[:52]}")
    else:
        print(f"  All touch targets are at least {MIN_DP}dp.")
    print()
    if undescribed:
        print("  Clickable with no text and no content description:")
        for cls, bounds in undescribed:
            print(f"    {cls} {bounds}")
    else:
        print("  Every clickable element carries text or a content description.")
    print()
    return 0

if __name__ == "__main__":
    sys.exit(main())
