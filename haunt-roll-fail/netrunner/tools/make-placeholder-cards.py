#!/usr/bin/env python3 -I
"""
Render card images for printings that have no downloadable image (NetrunnerDB
does not know the two unreleased identities 014001 The Collective and 014002
Chronos Protocol).

Usage:
    python3 -I netrunner/tools/make-placeholder-cards.py [--codes 014001,014002] [--force] [--out DIR]

Without --codes the script renders every code listed in
netrunner/assets-cache/missing.txt (written by fetch-assets.py) plus 014001 and
014002. The card data comes from the generated netrunner/cards-data-*.scala
files (fields in the order of CardData, see cards.scala). Each image is a
300x419 card in the layout of the Null Signal Games identities: a frame and
title band in the faction colour, the subtitle under the title, an "art" panel
with the faction logo (webp2/netrunner/images/faction-*.webp when present),
the minimum deck size and influence limit in circles (and the base link for
Runner identities), the "IDENTITY: subtypes" line and the rules text wrapped
to the text box (card tokens such as [click] and [credit] drawn as small
icons), then a footer naming the set. Non-identity cards get the same frame
with their cost / strength / agenda points instead.

Output: webp2/netrunner/images/<code>.webp (quality 90). Existing files are kept
unless --force is given or the file was produced by this script (same size).
Needs Pillow with WebP and FreeType support. Fonts: macOS system fonts, then
DejaVu, then Pillow's default font.
"""

import glob
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
NETRUNNER_DIR = os.path.dirname(HERE)
HRF_DIR = os.path.dirname(NETRUNNER_DIR)
OUT_DIR = os.path.join(HRF_DIR, "webp2", "netrunner", "images")
MISSING_LOG = os.path.join(NETRUNNER_DIR, "assets-cache", "missing.txt")

DEFAULT_CODES = ["014001", "014002"]

W, H = 300, 419

FIELDS = [
    "code", "title", "side", "faction", "kind", "subtypes", "cost", "strength", "advancementCost",
    "agendaPoints", "memory", "trashCost", "influence", "baseLink", "minDeckSize", "influenceLimit",
    "unique", "deckLimit", "text", "setCode", "setName", "rotated", "legal",
]

# frame colour, dark tint for the art panel, light tint for the text box
FACTIONS = {
    "Anarch":             ((222, 98, 40),   (58, 24, 12),  (250, 232, 222)),
    "Criminal":           ((52, 102, 196),  (14, 26, 56),  (226, 234, 250)),
    "Shaper":             ((70, 160, 70),   (16, 44, 18),  (228, 244, 226)),
    "Adam":               ((170, 140, 60),  (46, 38, 14),  (244, 238, 220)),
    "Apex":               ((170, 40, 40),   (48, 10, 10),  (246, 226, 226)),
    "Sunny Lebeau":       ((110, 110, 110), (28, 28, 28),  (236, 236, 236)),
    "Haas-Bioroid":       ((122, 86, 168),  (32, 22, 50),  (238, 230, 248)),
    "Jinteki":            ((178, 40, 64),   (50, 10, 18),  (248, 228, 232)),
    "NBN":                ((214, 168, 32),  (54, 42, 8),   (250, 244, 222)),
    "Weyland Consortium": ((44, 110, 84),   (12, 34, 26),  (226, 240, 234)),
    "Neutral":            ((128, 128, 128), (30, 30, 30),  (236, 236, 236)),
}

LOGOS = {
    "Anarch": "faction-runner-anarch", "Criminal": "faction-runner-criminal", "Shaper": "faction-runner-shaper",
    "Adam": "faction-runner-adam", "Apex": "faction-runner-apex", "Sunny Lebeau": "faction-runner-sunny",
    "Haas-Bioroid": "faction-corp-hb", "Jinteki": "faction-corp-jinteki", "NBN": "faction-corp-nbn",
    "Weyland Consortium": "faction-corp-weyland",
}

FONT_CANDIDATES = {
    "regular": ["/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"],
    "bold": ["/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/System/Library/Fonts/Helvetica.ttc",
             "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"],
    "italic": ["/System/Library/Fonts/Supplemental/Arial Italic.ttf",
               "/usr/share/fonts/truetype/dejavu/DejaVuSans-Oblique.ttf"],
    "title": ["/System/Library/Fonts/Supplemental/Arial Narrow Bold.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"],
    "serif": ["/System/Library/Fonts/Supplemental/Georgia.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"],
}


def font(kind, size):
    for p in FONT_CANDIDATES[kind]:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except OSError:
                pass
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def load_cards():
    cards = {}
    for p in sorted(glob.glob(os.path.join(NETRUNNER_DIR, "cards-data-*.scala"))):
        with open(p, encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                parts = line.split("\t")
                if len(parts) >= len(FIELDS) and re.fullmatch(r"\d{5,6}", parts[0].strip()):
                    d = dict(zip(FIELDS, parts))
                    d["code"] = d["code"].strip()
                    cards[d["code"]] = d
    if not cards:
        sys.exit("no cards found in %s/cards-data-*.scala -- run gen-cards.py first" % NETRUNNER_DIR)
    return cards


def clean_text(s):
    s = s.replace("\\n", "\n")
    s = re.sub(r"</?(strong|em|i|b)>", "", s)
    s = s.replace("<ul>", "").replace("</ul>", "").replace("<li>", "\n- ").replace("</li>", "")
    s = s.replace("&amp;", "&")
    return s.strip()


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def text_width(draw, s, f):
    return draw.textlength(s, font=f)


def fit_font(draw, s, kind, size, max_w, min_size=9):
    while size > min_size:
        f = font(kind, size)
        if text_width(draw, s, f) <= max_w:
            return f
        size -= 1
    return font(kind, min_size)


# ---- card tokens ([click], [credit], ...) drawn as small icons inside the text --------------------------------------

TOKEN = re.compile(r"(\[[a-z-]+\])")


def draw_token(draw, x, y, tok, size, colour):
    """Draws a token icon with its top-left corner at (x, y); returns its width."""
    s = size
    if tok == "[click]":
        r = s * 0.42
        cx, cy = x + s * 0.5, y + s * 0.55
        draw.arc([cx - r, cy - r, cx + r, cy + r], 300, 240, fill=colour, width=max(2, int(s * 0.14)))
        # arrow head at the open end of the arc
        draw.polygon([(cx + r * 0.15, cy - r * 1.35), (cx + r * 0.15, cy - r * 0.55), (cx - r * 0.55, cy - r * 0.95)], fill=colour)
        return s * 1.05
    if tok in ("[credit]", "[recurring-credit]"):
        r = s * 0.42
        cx, cy = x + s * 0.5, y + s * 0.55
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=colour, width=max(2, int(s * 0.12)))
        f = font("bold", int(s * 0.62))
        draw.text((cx, cy), "c", font=f, fill=colour, anchor="mm")
        return s * 1.05
    if tok == "[subroutine]":
        draw.text((x, y), "↳", font=font("bold", s), fill=colour)
        return s
    label = {"[mu]": "MU", "[link]": "link", "[trash]": "trash"}.get(tok, tok.strip("[]"))
    f = font("bold", int(s * 0.85))
    draw.text((x, y + s * 0.1), label, font=f, fill=colour)
    return text_width(draw, label, f) + 1


def layout_words(draw, text, f, max_w, size):
    """Splits text into lines of (word, width) runs; a token counts as one word."""
    lines = []
    for para in text.split("\n"):
        words = [w for w in re.split(r"(\s+)", para) if w and not w.isspace()]
        line, line_w = [], 0.0
        space = text_width(draw, " ", f)
        for w in words:
            parts = [p for p in TOKEN.split(w) if p]
            ww = sum((size * 1.05) if TOKEN.fullmatch(p) else text_width(draw, p, f) for p in parts)
            add = ww + (space if line else 0)
            if line and line_w + add > max_w:
                lines.append(line)
                line, line_w = [], 0.0
                add = ww
            line.append(parts)
            line_w += add
        lines.append(line)
    return lines


def draw_rules(draw, box, text, colour, max_size=14, min_size=9, italic_tail=None):
    x0, y0, x1, y1 = box
    max_w = x1 - x0
    for size in range(max_size, min_size - 1, -1):
        f = font("serif", size)
        lines = layout_words(draw, text, f, max_w, size)
        lh = size * 1.28
        total = len(lines) * lh + (size * 0.4) * text.count("\n")
        if total <= y1 - y0:
            break
    y = y0
    space = text_width(draw, " ", f)
    for i, line in enumerate(lines):
        x = x0
        for parts in line:
            for p in parts:
                if TOKEN.fullmatch(p):
                    x += draw_token(draw, x, y + size * 0.05, p, size, colour)
                else:
                    draw.text((x, y), p, font=f, fill=colour)
                    x += text_width(draw, p, f)
            x += space
        y += lh
    return y


# ---- the card ------------------------------------------------------------------------------------------------------

def circle_stat(draw, cx, cy, r, value, frame, fill):
    draw.ellipse([cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2], fill=(20, 20, 20))
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=frame, width=3)
    f = fit_font(draw, value, "bold", int(r * 1.05), r * 1.6)
    draw.text((cx, cy), value, font=f, fill=(255, 255, 255), anchor="mm")


def render(card):
    frame, dark, light = FACTIONS.get(card["faction"], FACTIONS["Neutral"])
    im = Image.new("RGB", (W, H), (16, 16, 16))
    d = ImageDraw.Draw(im)

    # outer frame
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=14, fill=mix(frame, (0, 0, 0), 0.35))
    d.rounded_rectangle([5, 5, W - 6, H - 6], radius=11, fill=dark)

    # art panel: diagonal bands in the faction colour and the faction logo
    art = (12, 64, W - 12, 236)
    panel = Image.new("RGB", (art[2] - art[0], art[3] - art[1]), dark)
    pd = ImageDraw.Draw(panel)
    pw, ph = panel.size
    for i in range(-ph, pw, 18):
        pd.line([(i, ph), (i + ph, 0)], fill=mix(dark, frame, 0.22), width=6)
    for k in range(5):
        r = 30 + k * 26
        pd.ellipse([pw / 2 - r, ph / 2 - r, pw / 2 + r, ph / 2 + r], outline=mix(dark, frame, 0.35 - k * 0.05), width=2)
    logo = LOGOS.get(card["faction"])
    logo_path = logo and os.path.join(OUT_DIR, logo + ".webp")
    if logo_path and os.path.exists(logo_path):
        with Image.open(logo_path) as lg:
            lg = lg.convert("RGBA")
            s = int(ph * 0.66)
            k = s / max(lg.width, lg.height)
            lg = lg.resize((max(1, int(lg.width * k)), max(1, int(lg.height * k))), Image.LANCZOS)
            panel.paste(lg, ((pw - lg.width) // 2, (ph - lg.height) // 2), lg)
    else:
        f = font("bold", 46)
        pd.text((pw / 2, ph / 2), card["faction"][:2].upper(), font=f, fill=mix(dark, frame, 0.8), anchor="mm")
    im.paste(panel, art[:2])
    d.rectangle(art, outline=frame, width=2)

    # title band (title before the colon) and subtitle band (after it)
    title, _, subtitle = card["title"].partition(":")
    title, subtitle = title.strip(), subtitle.strip()
    d.polygon([(8, 8), (W - 8, 8), (W - 8, 38), (8, 38)], fill=frame)
    unique = card.get("unique") == "1"
    tf = fit_font(d, ("♦ " if unique else "") + title.upper(), "title", 19, W - 30)
    d.text((14, 23), ("♦ " if unique else "") + title.upper(), font=tf, fill=(255, 255, 255), anchor="lm")
    d.polygon([(8, 38), (W - 50, 38), (W - 62, 58), (8, 58)], fill=light)
    if subtitle:
        sf = fit_font(d, subtitle, "bold", 13, W - 80)
        d.text((14, 48), subtitle, font=sf, fill=mix(frame, (0, 0, 0), 0.45), anchor="lm")

    # stats: minimum deck size (left), influence limit (right), base link for Runner identities
    is_id = card["kind"] == "Identity"
    y_stats = 250
    if is_id:
        if card["minDeckSize"]:
            circle_stat(d, 34, y_stats, 20, card["minDeckSize"], frame, mix(dark, (0, 0, 0), 0.2))
        infl = card["influenceLimit"] or "∞"
        circle_stat(d, W - 34, y_stats, 20, infl, frame, frame)
        if card["side"] == "Runner":
            lx, ly = 72, y_stats - 13
            d.rounded_rectangle([lx, ly, lx + 34, ly + 26], radius=5, fill=(20, 20, 20), outline=frame, width=2)
            lf = font("bold", 16)
            d.text((lx + 17, ly + 13), card["baseLink"] or "0", font=lf, fill=(255, 255, 255), anchor="mm")
            d.text((lx + 17, ly + 33), "link", font=font("regular", 9), fill=(200, 200, 200), anchor="mm")
    else:
        if card["cost"]:
            circle_stat(d, 34, y_stats, 20, card["cost"], frame, mix(dark, (0, 0, 0), 0.2))
        right = card["agendaPoints"] or card["strength"] or card["influence"]
        if right:
            circle_stat(d, W - 34, y_stats, 20, right, frame, frame)

    # text box
    box = (12, 274, W - 12, H - 28)
    d.rounded_rectangle(box, radius=6, fill=light)
    kind = card["kind"].upper()
    subtypes = " - ".join(s for s in card["subtypes"].split("|") if s)
    hf = font("bold", 13)
    x = box[0] + 8
    d.text((x, box[1] + 6), kind + (":" if subtypes else ""), font=hf, fill=(20, 20, 20))
    if subtypes:
        d.text((x + text_width(d, kind + ": ", hf), box[1] + 6), subtypes, font=font("regular", 13), fill=(20, 20, 20))
    draw_rules(d, (box[0] + 8, box[1] + 26, box[2] - 8, box[3] - 4), clean_text(card["text"]), (24, 24, 24))

    # footer
    ff = font("regular", 9)
    d.text((14, H - 15), "%s - generated image" % card["setName"], font=ff, fill=(190, 190, 190), anchor="lm")
    d.text((W - 14, H - 15), card["code"], font=ff, fill=(190, 190, 190), anchor="rm")
    return im


def codes_to_render(argv, cards):
    if "--codes" in argv:
        return [c for c in argv[argv.index("--codes") + 1].split(",") if c]
    codes = list(DEFAULT_CODES)
    if os.path.exists(MISSING_LOG):
        with open(MISSING_LOG) as f:
            for line in f:
                c = line.strip()
                if c and c not in codes:
                    codes.append(c)
    return codes


def main(argv):
    out_dir = OUT_DIR
    if "--out" in argv:
        out_dir = argv[argv.index("--out") + 1]
    force = "--force" in argv
    os.makedirs(out_dir, exist_ok=True)
    cards = load_cards()
    made = []
    for code in codes_to_render(argv, cards):
        card = cards.get(code)
        if card is None:
            print("unknown card code %s" % code)
            continue
        dst = os.path.join(out_dir, code + ".webp")
        if os.path.exists(dst) and not force:
            with Image.open(dst) as old:
                if old.size != (W, H):
                    print("kept %s (an image already exists; --force to replace it)" % dst)
                    continue
        im = render(card)
        tmp = dst + ".part"
        im.save(tmp, "WEBP", quality=90, method=6)
        os.replace(tmp, dst)
        made.append(code)
        print("wrote %s (%s)" % (dst, card["title"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
