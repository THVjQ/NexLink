"""
Play Store listing assets for NexLink Social.

Drawn rather than screenshotted, at the operator's instruction, for a reason
that turned out to matter: real captures of this app contain real identifiers —
Matrix addresses, device IDs and the IP addresses on the Devices screen. A store
listing is the most public surface there is.

So these reproduce the app's actual UI — the same palette from
social-ui/src/main/res/values-night/colors.xml, the same metrics from Chrome.kt
— with placeholder people in it. Nothing here shows a layout the app does not
have.
"""
from PIL import Image, ImageDraw, ImageFont
import os

#   python3 tools/play/make-listing-assets.py [output dir]

import sys
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/Downloads/play-assets")
os.makedirs(OUT, exist_ok=True)

# ── the app's dark palette, verbatim ───────────────────────────────────────
BG        = "#0A0E15"
SURFACE   = "#141A24"
SURFACE2  = "#1E2633"
SURFACE3  = "#2B3545"
ACCENT    = "#6BA6FF"
FILL      = "#1E5FD1"
ON_FILL   = "#FFFFFF"
FILL_META = "#E4EEFE"
TEXT      = "#EEF2F8"
TEXT2     = "#C9D2DF"
MUTED     = "#8E9AAB"
DIVIDER   = "#2A3444"
DANGER    = "#FF7A8C"
CANNOT    = "#4ED9A0"
CAN       = "#FFC067"
AV = ["#2C6BB0", "#7A4FA3", "#1F7A5C", "#A8452F", "#5C6B8A"]

B = "/usr/share/fonts/google-noto/NotoSans-Bold.ttf"
R = "/usr/share/fonts/google-noto/NotoSans-Regular.ttf"
M = "/usr/share/fonts/google-noto/NotoSansMono-Bold.ttf"
def f(path, size): return ImageFont.truetype(path, size)

def rr(d, box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

def text(d, xy, s, font, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)

def avatar(d, cx, cy, r, letter, tint, fsize):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=tint)
    text(d, (cx, cy + 1), letter, f(B, fsize), "#FFFFFF", anchor="mm")

def n_glyph(d, x, y, w, h, colour):
    """NexLink's N, the same path as the launcher icon, scaled into a box."""
    pts = [(30,28),(46,28),(62,72),(62,28),(78,28),(78,80),(62,80),(46,36),(46,80),(30,80)]
    sx, sy = w / 48.0, h / 52.0
    d.polygon([(x + (px - 30) * sx, y + (py - 28) * sy) for px, py in pts], fill=colour)

def bubble_mark(d, x, y, w, h, body, ink):
    """The speech bubble from the launcher icon: rounded body plus a tail."""
    rr(d, [x, y, x + w, y + h * 0.78], radius=int(h * 0.21), fill=body)
    d.polygon([(x + w * 0.20, y + h * 0.70), (x + w * 0.20, y + h),
               (x + w * 0.46, y + h * 0.74)], fill=body)
    n_glyph(d, x + w * 0.30, y + h * 0.17, w * 0.40, h * 0.44, ink)

# ══════════════════════════════════════════════════════════════════════════
# 1. App icon — 512x512, 32-bit PNG
# ══════════════════════════════════════════════════════════════════════════
def app_icon():
    S = 512
    img = Image.new("RGBA", (S, S), FILL)          # matches ic_launcher_background
    d = ImageDraw.Draw(img)
    bubble_mark(d, 96, 104, 320, 300, "#FFFFFF", FILL)
    img.convert("RGB").save(f"{OUT}/01-app-icon-512.png")
    print("  01-app-icon-512.png            512x512")

# ══════════════════════════════════════════════════════════════════════════
# 2. Feature graphic — 1024x500
# ══════════════════════════════════════════════════════════════════════════
def feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    # a soft diagonal wash, so it is not a flat rectangle
    for i in range(H):
        t = i / H
        d.line([(0, i), (W, i)],
               fill=(int(10 + 12 * t), int(14 + 26 * t), int(21 + 60 * t)))
    bubble_mark(d, 84, 150, 200, 200, "#FFFFFF", FILL)
    text(d, (330, 186), "NexLink Social", f(B, 66), TEXT)
    text(d, (330, 268), "Private messages and calls", f(R, 34), TEXT2)
    text(d, (330, 314), "End-to-end encrypted. Invite only.", f(R, 34), ACCENT)
    img.save(f"{OUT}/02-feature-graphic-1024x500.png")
    print("  02-feature-graphic-1024x500.png  1024x500")

# ══════════════════════════════════════════════════════════════════════════
# Phone screenshots — 1080x1920, an exact 9:16 so Play never argues
# ══════════════════════════════════════════════════════════════════════════
W, H = 1080, 1920
BAR_H, CAP_H, STATUS_H = 150, 200, 66

def frame(caption):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    text(d, (W // 2, 96), caption, f(B, 44), TEXT, anchor="mm")
    return img, d

def status_bar(d, y=CAP_H):
    d.rectangle([0, y, W, y + STATUS_H], fill=SURFACE)
    text(d, (40, y + 34), "9:41", f(B, 26), TEXT, anchor="lm")
    for i, w in enumerate([8, 14, 20, 26]):
        d.rectangle([W - 150 + i * 16, y + 44 - w, W - 150 + i * 16 + 9, y + 44], fill=TEXT)
    rr(d, [W - 68, y + 20, W - 26, y + 46], 8, fill=TEXT)

def title_bar(d, y, title, subtitle=None, back=False, icons=()):
    d.rectangle([0, y, W, y + BAR_H], fill=SURFACE)
    x = 40
    if back:
        d.line([(x + 34, y + 74), (x, y + 74)], fill=TEXT, width=5)
        d.line([(x + 14, y + 58), (x, y + 74), (x + 14, y + 90)], fill=TEXT, width=5, joint="curve")
        x += 76
    text(d, (x, y + (58 if subtitle else 74)), title, f(B, 42), TEXT, anchor="lm")
    if subtitle:
        text(d, (x, y + 100), subtitle, f(R, 27), MUTED, anchor="lm")
    ix = W - 60
    for kind in reversed(icons):
        if kind == "more":
            for k in range(3):
                d.ellipse([ix - 5, y + 54 + k * 20, ix + 5, y + 64 + k * 20], fill=TEXT)
        elif kind == "search":
            d.ellipse([ix - 26, y + 48, ix + 8, y + 82], outline=TEXT, width=5)
            d.line([(ix + 5, y + 79), (ix + 20, y + 94)], fill=TEXT, width=5)
        elif kind == "video":
            rr(d, [ix - 30, y + 54, ix + 2, y + 80], 7, outline=TEXT, width=5)
            d.polygon([(ix + 4, y + 62), (ix + 22, y + 52), (ix + 22, y + 82), (ix + 4, y + 72)],
                      outline=TEXT, width=4)
        ix -= 74
    return y + BAR_H

def card(d, y, h, margin=28, radius=44):
    rr(d, [margin, y, W - margin, y + h], radius, fill=SURFACE)
    return y + h

def divider(d, y, inset=170):
    d.line([(inset, y), (W - 28, y)], fill=DIVIDER, width=2)

def section(d, y, label):
    text(d, (62, y), label, f(B, 26), MUTED)
    return y + 46

# ── 1. the inbox ───────────────────────────────────────────────────────────
def shot_inbox():
    img, d = frame("Every message end-to-end encrypted")
    status_bar(d)
    y = title_bar(d, CAP_H + STATUS_H, "NexLink Social", "@alex:nexlink.social",
                  icons=("search", "more"))
    y += 26
    rows = [("Maya Patel", "2m", "Can you hear the ring now?", 0, 3, True),
            ("Dad", "1h", "You: sent it just now", 1, 0, False),
            ("Rosa & Dev", "Yesterday", "Rosa: floorplan-v4.pdf", 3, 0, False),
            ("Thursday football", "3d", "Dev: I'll bring the second one", 4, 0, False)]
    top = y
    card(d, y, len(rows) * 156)
    for i, (name, when, prev, tint, unread, bold) in enumerate(rows):
        ry = top + i * 156
        if i: divider(d, ry)
        avatar(d, 104, ry + 78, 52, name[0], AV[tint], 40)
        text(d, (176, ry + 56), name, f(B, 36), TEXT, anchor="lm")
        text(d, (W - 60, ry + 56), when, f(R, 26), ACCENT if unread else MUTED, anchor="rm")
        text(d, (176, ry + 104), prev, f(B if bold else R, 30), TEXT2 if bold else MUTED, anchor="lm")
        if unread:
            rr(d, [W - 104, ry + 88, W - 56, ry + 124], 18, fill=FILL)
            text(d, (W - 80, ry + 106), str(unread), f(B, 26), ON_FILL, anchor="mm")
    # the floating new-conversation button
    d.ellipse([W - 200, H - 210, W - 70, H - 80], fill=FILL)
    # A pencil: a body along the diagonal with a tip at the lower-left. Drawn as
    # a quadrilateral rather than two strokes — two crossed lines read as a
    # hammer, which is what the first attempt looked like.
    cx, cy = W - 135, H - 145
    import math
    ang = math.radians(-45)
    ux, uy = math.cos(ang), math.sin(ang)          # along the pencil
    px, py = -uy, ux                               # across it
    hw, L = 11, 30
    def pt(a, b): return (cx + ux * a + px * b, cy + uy * a + py * b)
    d.polygon([pt(-L, -hw), pt(L - 12, -hw), pt(L - 12, hw), pt(-L, hw)], fill=ON_FILL)
    d.polygon([pt(L - 12, -hw), pt(L + 6, 0), pt(L - 12, hw)], fill=ON_FILL)
    d.line([pt(-L + 9, -hw), pt(-L + 9, hw)], fill=FILL, width=3)
    img.save(f"{OUT}/03-screenshot-inbox.png")

# ── 2. a conversation ──────────────────────────────────────────────────────
def shot_chat():
    img, d = frame("Nobody can read them. Not even us.")
    status_bar(d)
    y = title_bar(d, CAP_H + STATUS_H, "Maya Patel", "@maya:nexlink.social",
                  back=True, icons=("video", "more"))
    y += 34
    rr(d, [W // 2 - 90, y, W // 2 + 90, y + 52], 26, fill=SURFACE3)
    text(d, (W // 2, y + 26), "Today", f(R, 26), MUTED, anchor="mm")
    y += 96

    def msg(y, who, body, when, mine):
        lines = body.split("\n")
        tw = max(d.textlength(l, f(R, 34)) for l in lines)
        bw = int(min(tw + 56, W * 0.76))
        bh = 40 + len(lines) * 46 + 34
        x0 = W - 34 - bw if mine else 34
        if not mine and who:
            text(d, (x0 + 28, y), who, f(B, 26), MUTED)
            y += 38
        radii = 38
        rr(d, [x0, y, x0 + bw, y + bh], radii, fill=FILL if mine else SURFACE2)
        # the squared corner on the speaker's side
        cs = 16
        if mine:
            d.rectangle([x0 + bw - cs, y + bh - cs, x0 + bw - 1, y + bh - 1], fill=FILL)
        else:
            d.rectangle([x0 + 1, y + bh - cs, x0 + cs, y + bh - 1], fill=SURFACE2)
        for i, l in enumerate(lines):
            text(d, (x0 + 28, y + 26 + i * 46), l, f(R, 34), ON_FILL if mine else TEXT)
        text(d, (x0 + bw - 24, y + bh - 30), when, f(R, 22),
             FILL_META if mine else MUTED, anchor="rt")
        return y + bh + 22

    y = msg(y, "Maya Patel", "Did the call come through\non the locked phone?", "8:52 pm", False)
    y = msg(y, None, "Yes — it rang from cold.", "8:54 pm", True)
    y = msg(y, None, "Video both ways, encrypted\nat both ends.", "8:54 pm", True)
    y = msg(y, "Maya Patel", "Perfect.", "9:38 pm", False)

    cy = H - 150
    d.rectangle([0, cy, W, H], fill=SURFACE)
    d.line([(0, cy), (W, cy)], fill=DIVIDER, width=2)
    d.line([(62, cy + 74), (110, cy + 74)], fill=MUTED, width=6)
    d.line([(86, cy + 50), (86, cy + 98)], fill=MUTED, width=6)
    rr(d, [140, cy + 32, W - 160, cy + 116], 42, fill=SURFACE2)
    text(d, (176, cy + 74), "Message", f(R, 32), MUTED, anchor="lm")
    d.ellipse([W - 140, cy + 30, W - 34, cy + 118], fill=FILL)
    d.polygon([(W - 116, cy + 100), (W - 54, cy + 74), (W - 116, cy + 48),
               (W - 105, cy + 74)], fill=ON_FILL)
    img.save(f"{OUT}/04-screenshot-conversation.png")

# ── 3. what the operator can and cannot see ───────────────────────────────
def shot_transparency():
    img, d = frame("We tell you exactly what we can see")
    status_bar(d)
    y = title_bar(d, CAP_H + STATUS_H, "What we can and cannot see")
    y += 40
    top = y
    card(d, y, 720)
    y += 46
    text(d, (66, y), "WE CANNOT SEE", f(B, 28), CANNOT); y += 56
    for s in ["The content of your messages", "The content of your calls",
              "Your photos and files", "Your message history"]:
        text(d, (66, y), "•  " + s, f(R, 31), TEXT2); y += 52
    y += 24
    divider(d, y, inset=66); y += 34
    text(d, (66, y), "WE CAN SEE", f(B, 28), CAN); y += 56
    for s in ["Who you message, and when", "How often, and roughly how much",
              "Your IP address", "Which devices you use"]:
        text(d, (66, y), "•  " + s, f(R, 31), TEXT2); y += 52
    y = top + 720 + 40
    for line in ["We are telling you the right-hand column",
                 "because it is true. Any app that says it",
                 "can see nothing at all is not being straight",
                 "with you."]:
        text(d, (66, y), line, f(R, 30), MUTED); y += 46
    img.save(f"{OUT}/05-screenshot-transparency.png")

# ── 4. invites ─────────────────────────────────────────────────────────────
def shot_invite():
    img, d = frame("Invite only, by design")
    status_bar(d)
    y = title_bar(d, CAP_H + STATUS_H, "Invite someone", back=True)
    y += 34
    for line in ["NexLink Social is invite only. Create a code and",
                 "give it to the person you want to add.",
                 "Each code works once and expires in two weeks."]:
        text(d, (62, y), line, f(R, 29), MUTED); y += 44
    y += 30
    card(d, y, 260)
    text(d, (W // 2, y + 58), "INVITE CODE", f(B, 26), MUTED, anchor="mm")
    text(d, (W // 2, y + 148), "8WGJ-RYJV-4MZ0", f(M, 56), TEXT, anchor="mm")
    y += 300
    rr(d, [28, y, W // 2 - 10, y + 96], 30, fill=FILL)
    text(d, (W // 4 + 9, y + 48), "Share", f(B, 34), ON_FILL, anchor="mm")
    rr(d, [W // 2 + 10, y, W - 28, y + 96], 30, outline=DIVIDER, width=3)
    text(d, (W * 3 // 4 - 9, y + 48), "Copy", f(B, 34), ACCENT, anchor="mm")
    y += 150
    y = section(d, y, "WAITING TO BE USED")
    top = y
    card(d, y, 300)
    for i, (code, when) in enumerate([("8WGJ-RYJV-4MZ0", "Expires 30 Sept"),
                                      ("4M9H-J953-SVT6", "Expires 28 Sept"),
                                      ("YZ2P-4X24-CTE2", "Expires 27 Sept")]):
        ry = top + i * 100
        if i: divider(d, ry, inset=62)
        text(d, (62, ry + 38), code, f(M, 32), TEXT, anchor="lm")
        text(d, (62, ry + 74), when, f(R, 24), MUTED, anchor="lm")
        rr(d, [W - 220, ry + 24, W - 62, ry + 78], 22, fill=SURFACE2)
        text(d, (W - 141, ry + 51), "Revoke", f(B, 28), DANGER, anchor="mm")
    img.save(f"{OUT}/06-screenshot-invites.png")

# ── 5. storage ─────────────────────────────────────────────────────────────
def shot_storage():
    img, d = frame("You control what stays on your phone")
    status_bar(d)
    y = title_bar(d, CAP_H + STATUS_H, "Storage", back=True)
    y += 34
    card(d, y, 190)
    text(d, (62, y + 62), "11.5 MB", f(B, 68), TEXT, anchor="lm")
    text(d, (62, y + 134), "used by NexLink Social on this phone", f(R, 28), MUTED, anchor="lm")
    y += 220
    top = y
    card(d, y, 640)
    # the proportional bar, in the same colours as the rows beneath it
    bx, bw = 62, W - 124
    segs = [(0.42, ACCENT), (0.26, AV[1]), (0.10, CAN), (0.22, CANNOT)]
    x = bx
    rr(d, [bx, y + 40, bx + bw, y + 66], 13, fill=SURFACE3)
    for frac, col in segs:
        d.rectangle([x, y + 40, x + bw * frac, y + 66], fill=col)
        x += bw * frac
    yy = y + 110
    for (name, size, note, col) in [
            ("Photos, videos and files", "4.8 MB", "Cleared safely — they download again.", ACCENT),
            ("Message cache", "3.0 MB", "A local copy. Re-syncs from the server.", AV[1]),
            ("Account data", "1.2 MB", "Room names, members and settings.", CAN),
            ("Encryption keys", "2.5 MB", "Never cleared. These read your history.", CANNOT)]:
        d.ellipse([66, yy + 12, 86, yy + 32], fill=col)
        text(d, (104, yy + 22), name, f(B, 33), TEXT, anchor="lm")
        text(d, (W - 62, yy + 22), size, f(R, 30), TEXT2, anchor="rm")
        text(d, (104, yy + 66), note, f(R, 26), MUTED, anchor="lm")
        yy += 130
    y = top + 690
    y = section(d, y, "MEDIA CACHE LIMIT")
    top = y
    card(d, y, 300)
    for i, (label, sel) in enumerate([("500 MB", False), ("1 GB", True), ("5 GB", False)]):
        ry = top + i * 100
        if i: divider(d, ry, inset=62)
        if sel:
            d.ellipse([66, ry + 36, 106, ry + 76], fill=FILL)
        else:
            d.ellipse([66, ry + 36, 106, ry + 76], outline=DIVIDER, width=4)
        text(d, (132, ry + 56), label, f(B if sel else R, 33), TEXT if sel else TEXT2, anchor="lm")
    img.save(f"{OUT}/07-screenshot-storage.png")

# ── 6. devices ─────────────────────────────────────────────────────────────
def shot_devices():
    img, d = frame("See every device on your account")
    status_bar(d)
    y = title_bar(d, CAP_H + STATUS_H, "Your devices", back=True)
    y += 34
    for line in ["Every place you're signed in. If you see something",
                 "here you don't recognise, remove it and change",
                 "your password."]:
        text(d, (62, y), line, f(R, 29), MUTED); y += 44
    y += 24
    top = y
    card(d, y, 560)
    entries = [("Pixel 8", True, "Last used just now"),
               ("Galaxy S24", False, "Last used 15 Sept, 8:12 pm"),
               ("Element Web", False, "Last used 14 Sept, 9:38 pm")]
    for i, (name, current, when) in enumerate(entries):
        ry = top + i * 186
        if i: divider(d, ry, inset=62)
        text(d, (62, ry + 52), name, f(B, 36), TEXT, anchor="lm")
        if current:
            w = int(d.textlength("This device", f(B, 24))) + 36
            rr(d, [62, ry + 76, 62 + w, ry + 118], 20, fill=FILL)
            text(d, (62 + w // 2, ry + 97), "This device", f(B, 24), ON_FILL, anchor="mm")
        else:
            rr(d, [W - 224, ry + 44, W - 62, ry + 100], 22, fill=SURFACE2)
            text(d, (W - 143, ry + 72), "Remove", f(B, 28), DANGER, anchor="mm")
        text(d, (62, ry + 146), when, f(R, 25), MUTED, anchor="lm")
    img.save(f"{OUT}/08-screenshot-devices.png")

app_icon(); feature_graphic()
for fn, label in [(shot_inbox, "03-screenshot-inbox.png"),
                  (shot_chat, "04-screenshot-conversation.png"),
                  (shot_transparency, "05-screenshot-transparency.png"),
                  (shot_invite, "06-screenshot-invites.png"),
                  (shot_storage, "07-screenshot-storage.png"),
                  (shot_devices, "08-screenshot-devices.png")]:
    fn(); print(f"  {label:32s} {W}x{H}")
