#!/usr/bin/env python3
"""
Render the assembled legal pack as pages for thvjq.com.au.

    tools/legal/build-site.py <path-to-THVjQ-Website-checkout>

The website carried a hand-written legal set per product — NexLink, Bridge and
Social each with their own Terms and Privacy Policy, all Australian-only, none
mentioning Switzerland. The repository pack replaced those with ONE set covering
every product. Until this script ran, the published documents and the documents
shipping inside the app disagreed, which is the failure docs/legal/README.md
calls worse than having no policy at all.

Canonical output:   /nexlink/legal/<slug>/          (English)
                    /nexlink/legal/<slug>/de/       (German)

The old per-product paths become redirect stubs rather than deletions, because
they are linked from the app, from Play, and from each other, and a legal URL
that 404s is worse than one that forwards.

Pages are GENERATED. Editing them on the website is pointless — the next run
overwrites them. Edit docs/legal/<lang>/ and re-run build-legal.py first.
"""
import html
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "docs" / "legal" / "dist"

SLUGS = {
    "terms-of-service": ("terms", "Terms of Service", "Nutzungsbedingungen"),
    "privacy-policy": ("privacy", "Privacy Policy", "Datenschutzerklärung"),
    "content-policy": ("content-policy", "Content Policy", "Inhaltsrichtlinien"),
    "data-retention-schedule": ("retention", "Data Retention Schedule", "Aufbewahrungsplan"),
    "transparency-note": ("transparency", "What we can and cannot see", "Was wir sehen können"),
    "law-enforcement-guidelines": ("law-enforcement", "Law Enforcement Guidelines", "Richtlinien für Strafverfolgungsbehörden"),
    "security-disclosure-policy": ("security", "Security Disclosure Policy", "Offenlegung von Sicherheitslücken"),
}

# Old per-product paths that must keep resolving.
REDIRECTS = {
    "nexlink/terms": "terms", "nexlink/privacy": "privacy",
    "nexlink_bridge/terms": "terms", "nexlink_bridge/privacy": "privacy",
    "nexlink_social/terms": "terms", "nexlink_social/privacy": "privacy",
    "nexlink_social/security": "security", "nexlink_social/content-policy": "content-policy",
    "nexlink_social/retention": "retention", "nexlink_social/transparency": "transparency",
    "nexlink_social/law-enforcement": "law-enforcement",
}

INLINE = re.compile(r"(\*\*.+?\*\*|(?<!\*)\*[^*]+?\*(?!\*)|`[^`]+?`)")


def inline(text):
    out = []
    for part in INLINE.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**") and len(part) > 4:
            out.append(f"<strong>{html.escape(part[2:-2])}</strong>")
        elif part.startswith("`") and part.endswith("`") and len(part) > 2:
            out.append(f"<code>{html.escape(part[1:-1])}</code>")
        elif part.startswith("*") and part.endswith("*") and len(part) > 2:
            out.append(f"<em>{html.escape(part[1:-1])}</em>")
        else:
            # Bare URLs become links; everything else is escaped text.
            esc = html.escape(part)
            esc = re.sub(r"(https?://[^\s<)]+)", r'<a href="\1" rel="noopener">\1</a>', esc)
            out.append(esc)
    return "".join(out)


def md_to_html(md):
    lines = md.split("\n")
    out, i, in_list = [], 0, None

    def close_list():
        nonlocal in_list
        if in_list:
            out.append(f"</{in_list}>")
            in_list = None

    while i < len(lines):
        raw = lines[i]
        s = raw.strip()

        if s.startswith("<!--"):                      # generator banner
            while i < len(lines) and "-->" not in lines[i]:
                i += 1
            i += 1
            continue

        if not s:
            close_list(); i += 1; continue

        if s in ("---", "***", "___"):
            close_list(); out.append("<hr />"); i += 1; continue

        m = re.match(r"^(#{1,6})\s+(.*)$", s)
        if m:
            close_list()
            lvl = min(len(m.group(1)), 4)
            txt = m.group(2).strip()
            anchor = re.sub(r"[^a-z0-9]+", "-", txt.lower()).strip("-")[:48]
            out.append(f'<h{lvl} id="{anchor}">{inline(txt)}</h{lvl}>')
            i += 1; continue

        # table
        if s.startswith("|") and i + 1 < len(lines) and set(lines[i + 1].strip()) <= set("|-: "):
            close_list()
            cells = lambda l: [c.strip() for c in l.strip().strip("|").split("|")]
            head = cells(s); i += 2
            out.append('<div class="tablewrap"><table><thead><tr>'
                       + "".join(f"<th>{inline(c)}</th>" for c in head)
                       + "</tr></thead><tbody>")
            while i < len(lines) and lines[i].strip().startswith("|"):
                row = cells(lines[i])
                row += [""] * (len(head) - len(row))
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row) + "</tr>")
                i += 1
            out.append("</tbody></table></div>")
            continue

        if s.startswith(">"):
            close_list()
            buf = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                buf.append(lines[i].strip().lstrip("> ").strip()); i += 1
            out.append(f'<div class="note"><p>{inline(" ".join(buf))}</p></div>')
            continue

        m = re.match(r"^[-*+]\s+(.*)$", s)
        if m:
            if in_list != "ul":
                close_list(); out.append("<ul>"); in_list = "ul"
            out.append(f"<li>{inline(m.group(1))}</li>"); i += 1; continue

        m = re.match(r"^\d+[.)]\s+(.*)$", s)
        if m:
            if in_list != "ol":
                close_list(); out.append("<ol>"); in_list = "ol"
            out.append(f"<li>{inline(m.group(1))}</li>"); i += 1; continue

        close_list()
        buf = [s]; i += 1
        while i < len(lines) and lines[i].strip() and not re.match(
                r"^(#{1,6}\s|[-*+]\s|\d+[.)]\s|>|\||---$)", lines[i].strip()):
            buf.append(lines[i].strip()); i += 1
        out.append(f"<p>{inline(' '.join(buf))}</p>")

    close_list()
    return "\n".join(out)


SHELL = """<!DOCTYPE html>
<html lang="{lang}" data-product="nexlink">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>NexLink — {title}</title>
  <meta name="description" content="{desc}" />
  <link rel="canonical" href="https://thvjq.com.au{canon}" />
{alt}  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&family=Syne:wght@700;800&display=swap" rel="stylesheet" />
  <link rel="stylesheet" href="/css/product.css" />
  <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>💬</text></svg>" />
  <style>
    .tablewrap{{overflow-x:auto;margin:18px 0}}
    .tablewrap table{{border-collapse:collapse;width:100%;min-width:480px;font-size:.93rem}}
    .tablewrap th,.tablewrap td{{border:1px solid var(--border);padding:9px 12px;text-align:left;vertical-align:top}}
    .tablewrap th{{background:var(--surface2);font-weight:600}}
    main.doc h3{{margin-top:22px}}
    .genwarn{{font-size:.85rem;color:var(--muted);margin-top:38px;border-top:1px solid var(--border);padding-top:16px}}
  </style>
</head>
<body class="doc">

<div data-familybar></div>

<main class="doc">
  <a class="back" href="/nexlink/">← NexLink</a>
  <p class="meta"><strong>Applies to:</strong> NexLink · NexLink Social · NexLink for Wear OS · NexLink Bridge &nbsp;·&nbsp; <a href="{other}">{otherlabel}</a></p>
{body}
  <p class="genwarn">{gen}</p>
</main>

<footer data-sitefoot></footer>

<script src="/js/product.js"></script>
</body>
</html>
"""

STUB = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="refresh" content="0; url=/nexlink/legal/{slug}/" />
  <link rel="canonical" href="https://thvjq.com.au/nexlink/legal/{slug}/" />
  <title>Moved — NexLink {title}</title>
  <meta name="robots" content="noindex" />
</head>
<body style="font-family:system-ui,sans-serif;background:#0a0a0f;color:#f0f0ff;padding:48px 20px;text-align:center">
  <p>The NexLink {title} now covers every NexLink product in one document.</p>
  <p><a style="color:#a29bfe" href="/nexlink/legal/{slug}/">Continue to the current {title}</a></p>
</body>
</html>
"""


def main(site):
    site = Path(site)
    if not (site / "css" / "product.css").exists():
        print(f"not a website checkout: {site}"); return 2

    written = 0
    for name, (slug, en_title, de_title) in SLUGS.items():
        for lang, title in (("en", en_title), ("de", de_title)):
            src = DIST / lang / f"{name}.md"
            body = md_to_html(src.read_text())
            out_dir = site / "nexlink" / "legal" / slug / ("" if lang == "en" else "de")
            out_dir.mkdir(parents=True, exist_ok=True)
            canon = f"/nexlink/legal/{slug}/" + ("" if lang == "en" else "de/")
            other = f"/nexlink/legal/{slug}/" + ("de/" if lang == "en" else "")
            alt = (f'  <link rel="alternate" hreflang="de" href="/nexlink/legal/{slug}/de/" />\n'
                   if lang == "en" else
                   f'  <link rel="alternate" hreflang="en" href="/nexlink/legal/{slug}/" />\n')
            gen = ("Generated from the NexLink repository. Edit docs/legal/ and run "
                   "tools/legal/build-site.py — changes made here are overwritten."
                   if lang == "en" else
                   "Aus dem NexLink-Repository erzeugt. Bearbeiten Sie docs/legal/ und führen Sie "
                   "tools/legal/build-site.py aus — Änderungen hier werden überschrieben.")
            (out_dir / "index.html").write_text(SHELL.format(
                lang=lang, title=html.escape(title), desc=html.escape(
                    f"{title} for the NexLink products, covering Switzerland and Australia."),
                canon=canon, alt=alt, other=other,
                otherlabel="Deutsch" if lang == "en" else "English",
                body=body, gen=gen))
            written += 1

    for old, slug in REDIRECTS.items():
        title = next(t for n, (s, t, _) in SLUGS.items() if s == slug)
        for sub in ("", "de"):
            d = site / old / sub
            if sub and not d.exists():
                continue
            d.mkdir(parents=True, exist_ok=True)
            (d / "index.html").write_text(STUB.format(slug=slug, title=html.escape(title)))
            written += 1

    print(f"{written} pages written into {site}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else ""))
