#!/usr/bin/env python3
"""
Assemble the NexLink legal pack: every document + the shared definitions appendix.

    tools/legal/build-legal.py [--check]

Appendix A is written once, in docs/legal/definitions.<lang>.md, and appended to
every document at build time. It is NOT stored inside each document, because
eight hand-maintained copies of a definition is eight chances for "Terrorism" to
mean one thing in the Terms of Service and another in the Content Policy — and a
prohibition that means two things is exactly the vagueness these definitions
exist to remove.

Output goes to docs/legal/dist/<lang>/, which is what ships in the apps and what
is published. Nothing writes back into the source documents, so the script is
safe to run repeatedly.

--check exits non-zero if dist/ is stale, for CI and for the release checklist.
"""
import argparse
import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEGAL = ROOT / "docs" / "legal"
LANGS = ("en", "de")

# The appendix heading differs by language, and the sameness check has to know
# that. Splitting a German document on the English heading finds nothing, hands
# back the whole file, and reports seven "distinct appendices" for seven
# documents that are in fact identical — a check that fails on correct input is
# worse than no check, because the next person learns to ignore it.
APPENDIX_HEADING = {"en": "## Appendix A", "de": "## Anhang A"}

BANNER = {
    "en": "<!-- Assembled by tools/legal/build-legal.py. Edit docs/legal/{lang}/{name}\n"
          "     or docs/legal/definitions.{lang}.md, never this file. -->\n",
    "de": "<!-- Erstellt von tools/legal/build-legal.py. Bearbeiten Sie docs/legal/{lang}/{name}\n"
          "     oder docs/legal/definitions.{lang}.md, niemals diese Datei. -->\n",
}


def assemble(lang):
    """Yield (filename, text) for every document in one language."""
    appendix = (LEGAL / f"definitions.{lang}.md").read_text()
    for src in sorted((LEGAL / lang).glob("*.md")):
        body = src.read_text().rstrip("\n")
        # Documents end with a horizontal rule so the appendix reads as a part
        # of the document rather than as something bolted on after it.
        if not body.endswith("---"):
            body += "\n\n---"
        banner = BANNER[lang].format(lang=lang, name=src.name)
        yield src.name, f"{banner}\n{body}\n\n{appendix.rstrip()}\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="verify dist/ matches the sources; write nothing")
    args = ap.parse_args()

    stale, written = [], 0
    for lang in LANGS:
        out_dir = LEGAL / "dist" / lang
        if not args.check:
            out_dir.mkdir(parents=True, exist_ok=True)
        for name, text in assemble(lang):
            out = out_dir / name
            current = out.read_text() if out.exists() else None
            if current == text:
                continue
            if args.check:
                stale.append(f"{lang}/{name}")
            else:
                out.write_text(text)
                written += 1

    if args.check:
        if stale:
            print("STALE — run tools/legal/build-legal.py:")
            for s in stale:
                print(f"  {s}")
            return 1
        print("legal pack is up to date")
        return 0

    # Report, and make the shared-appendix guarantee checkable by eye.
    for lang in LANGS:
        docs = sorted((LEGAL / "dist" / lang).glob("*.md"))
        heading = APPENDIX_HEADING[lang]
        missing = [d.name for d in docs if heading not in d.read_text()]
        digests = {hashlib.sha256(
            d.read_text().split(heading)[-1].encode()).hexdigest()[:12]
            for d in docs if heading not in missing}
        if missing:
            print(f"  ERROR: no {heading} in {', '.join(missing)}")
            return 1
        state = "rebuilt" if written else "unchanged"
        print(f"{lang}: {len(docs)} documents, {state}, "
              f"appendix {digests.pop() if len(digests) == 1 else '??'}")
        if digests:
            print(f"  ERROR: the appendix differs between documents — it must not")
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
