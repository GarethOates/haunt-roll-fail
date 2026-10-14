#!/usr/bin/env python3 -I
"""
Fetch the assets of every game plugin: every image of <game>/meta.scala, the
fonts, sounds and shared images every page loads, from the live HRF site (hrf.im).

Usage:
    python3 -I tools/fetch-assets.py [GAME ...] [--no-hrf] [--devkit ZIP_OR_DIR] [--no-download] [--force]

GAME is a plugin folder (arcs, root, vast, ...); default every folder with a meta.scala
except the ones in UNUSED (cthw) and OWN_FETCHER (netrunner, see its tools/fetch-assets.py).

Steps:
  1. Download every image of <game>/meta.scala (all of its asset lists, whatever
     their condition) from https://hrf.im/hrf/webp2/<game>/images/<dir>/<name>.webp
     into tools/assets-cache/hrf/webp2/<game>/images/ (skip existing, resumable,
     <= 5 requests/second, retries on transient errors) and copy it to
     webp2/<game>/images/<dir>/<name>.webp. These are the images the plugins are
     drawn against, so they are used as is.
  2. Download what every HRF page loads whatever the game: every font
     index.html declares (woff2 and woff; the lobby uses them all) from
     https://hrf.im/hrfn/fonts/ into fonts/, omen.png and background.png (index.html
     styles), the turn alert sounds, and the images of HRFR.original in hrf.scala
     (question-mark.png, external-link.png and the Root icons, items and quest
     cards under webp2/root/), all from https://hrf.im/hrf/.
     HRF waits for the HRFR images before its UI starts and fails without them.
  3. Arcs only, for the images hrf.im lacks (or all cards with --no-hrf): the
     Buried Giant card library. The index https://cardcdn.buriedgiant.com/cards.min.json
     (compress-json encoded, cached as tools/assets-cache/arcs/cards.min.json) lists
     the English Arcs cards and their images, cached in tools/assets-cache/arcs/cards/
     and copied under the name used in arcs/meta.scala (e.g. BC01 -> court/bc01,
     LEAD04 -> leader/leader04, F1001A -> f10/f10-01a). The CDN images are
     already webp at the size the court canvas expects (744x1039), so they are
     copied byte for byte.
  4. Arcs only, for what is still missing: the Arcs Development Kit (the "Arcs
     Development Kit and Font Guide" folder from Leder Games, as a zip or an
     extracted folder; default tools/assets-cache/arcs/devkit.zip). Map figures are
     drawn at their natural size around a fixed anchor (see arcs/ui.scala), so they
     are trimmed and fitted into the box the anchor implies; everything else
     keeps its native size. Images without a dev kit counterpart are
     approximated (aliases, darkened "damaged" sides) and listed as such.
  5. The compiled clients of hrf.im listed in CLIENTS and every image on their asset
     lists, from https://hrf.im/hrfn/<game>/target/scala-2.13/ and
     https://hrf.im/hrfn/<game>/assets/, into vendor/<game>/ (git-ignored), laid out as
     the site serves it: Arcs, newer than this source tree (the whole Blighted Reach
     campaign, not just Act I), and The Old King's Crown (tokc), which this source tree
     lacks. index.html loads a game's first version by default and the others for
     games with that ?version= (games imported from hrf.im, see import-game.py). The
     menu line announcing that Arcs on HRF is discontinued is switched off in the Arcs
     copies (--no-patch keeps it).
  6. Write tools/assets-cache/missing.txt: every image of the fetched games that
     is still absent from webp2/ or vendor/.

tools/assets-cache/ and vendor/ are git-ignored; webp2/, fonts/, sounds/ and the root images are
generated files beside the build and are not committed either. They are the
publishers' art and commercial fonts: keep them private.
"""

import argparse
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile

from PIL import Image, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
HRF_DIR = os.path.dirname(HERE)

INDEX_URL = "https://cardcdn.buriedgiant.com/cards.min.json"
HRF_URL = "https://hrf.im/hrf/%s"
HRF_FONTS_URL = "https://hrf.im/hrfn/fonts/%s"
HRF_EXTRAS = [
    "omen.png",             # index.html styles
    "background.png",
    "sounds/beacon.wav",    # runner.scala, your-turn alerts
    "sounds/warning.wav",
]
CACHE_DIR = os.path.join(HERE, "assets-cache")
HRF_CACHE = os.path.join(CACHE_DIR, "hrf")      # mirrors the files under HRF_DIR
ARCS_CACHE = os.path.join(CACHE_DIR, "arcs")
CARDS_CACHE = os.path.join(ARCS_CACHE, "cards")
DEVKIT_DEFAULT = os.path.join(ARCS_CACHE, "devkit.zip")
MISSING_LOG = os.path.join(CACHE_DIR, "missing.txt")

# compiled clients of hrf.im served instead of (arcs) or beside (tokc, not in this source tree) our build;
# the first version of each is the default, keep index.html's vendored map in step
CLIENTS = {
    "arcs": ["0.8.161", "0.8.160", "0.8.155"],
    "tokc": ["0.8.155"],                        # The Old King's Crown
}
HRFN_URL = "https://hrf.im/hrfn/%s"
VENDOR_DIR = os.path.join(HRF_DIR, "vendor")
CLIENT_REL = "%s/target/scala-2.13/hrf-%s-fastopt-%s.js"

RATE = 5.0          # requests per second
RETRIES = 5
TIMEOUT = 30
QUALITY = 82
USER_AGENT = "haunt-roll-fail-assets/1.0"


# compress-json (npm) decoding: [values, rootKey], keys are base62 indices into values

B62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"


def b62(s):
    n = 0
    for c in s:
        n = n * 62 + B62.index(c)
    return n


def cj_number(s):
    if s.startswith("-"):
        return -cj_number(s[1:])
    if "." in s:
        a, b = s.split(".", 1)
        return float("%d.%d" % (b62(a), b62(b)))
    return b62(s)


def cj_decode(values, key):
    if key in ("", "_"):
        return None
    v = values[b62(key)]
    if not isinstance(v, str):
        return v
    tag, rest = v[:2], v[2:]
    if tag == "b|":
        return rest == "T"
    if tag == "n|":
        return cj_number(rest)
    if tag == "s|":
        return rest
    if tag == "N|":
        return None
    if tag == "a|":
        return [cj_decode(values, k) for k in rest.split("|")] if rest else []
    if tag == "o|":
        keys = rest.split("|")
        names = cj_decode(values, keys[0])
        return {n: cj_decode(values, k) for n, k in zip(names, keys[1:])}
    return v


# card id -> (dir, name) as in arcs/meta.scala

def hrf_name(card_id):
    i = card_id[len("ARCS-"):]
    m = re.fullmatch(r"([234])SETUP(\d\d)", i)
    if m:
        return "setup", "setup-%sp-%s" % m.groups()
    if re.fullmatch(r"(BC|CC)\d\d", i):
        return "court", i.lower()
    m = re.fullmatch(r"LEAD(\d\d)", i)
    if m:
        return "leader", "leader" + m.group(1)
    m = re.fullmatch(r"L(\d\d)", i)
    if m:
        return "lore", "lore" + m.group(1)
    m = re.fullmatch(r"AID(\d\d[AB]?)", i)
    if m:
        return "empire", "aid" + m.group(1).lower()
    m = re.fullmatch(r"FATE(\d\d)", i)
    if m:
        return "fate", "fate" + m.group(1)
    # campaign cards: F + fate (1-24, unpadded) + card (2 digits) + side
    m = re.fullmatch(r"F(\d{1,2}?)(\d\d)([AB]?)", i)
    if m:
        f = "f%02d" % int(m.group(1))
        return f, "%s-%s%s" % (f, m.group(2), m.group(3).lower())
    return None


# every (dir, file) a plugin loads, parsed from the asset lists of <game>/meta.scala:
#     ConditionalAssetsList(condition, path = "", prefix = "", ext = "png", ...)(
#         ImageAsset(name) :: ImageAsset(name, filename) :: ImageAsset(name, filename, ext, ...) :: ...
# the file is <path>/<filename>; the condition is ignored (every faction and option is fetched)
# and the extension too (webp2/ holds webp only, see convert-images.scala)

# plugins the client never draws, skipped unless named on the command line
UNUSED = {
    "cthw": "hrf.scala sends it to https://cwo.im/ and hrf.im has none of its images",
}

# plugins whose images come from their own fetcher, not from hrf.im
OWN_FETCHER = {
    "netrunner": "netrunner/tools/fetch-assets.py (NetrunnerDB card images)",
}


def games():
    return sorted(g for g in os.listdir(HRF_DIR) if os.path.exists(os.path.join(HRF_DIR, g, "meta.scala")))


def call_args(src, i):
    """The top-level arguments of the call whose "(" is at src[i], and the index after its ")"."""
    args, depth, cur, q = [], 0, "", None
    for j in range(i, len(src)):
        c = src[j]
        if q:
            cur += c
            if c == q and src[j - 1] != "\\":
                q = None
            continue
        if c == '"':
            q = c
        elif c in "([{":
            depth += 1
            if depth == 1:
                continue
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                args.append(cur.strip())
                return [a for a in args if a], j + 1
        elif c == "," and depth == 1:
            args.append(cur.strip())
            cur = ""
            continue
        cur += c
    raise ValueError("unbalanced call at %d" % i)


def literal(a):
    m = re.fullmatch(r'"([^"]*)"', a)
    return m.group(1) if m else None


def values(src, a):
    """A string argument: a literal, or a val holding one of several literals (arcs easterEgg)."""
    v = literal(a)
    if v is not None:
        return [v]
    m = re.search(r"\bval " + re.escape(a) + r"\s*=(.*?)\n\s*\n", src, re.S)
    if not m:
        raise ValueError("cannot resolve " + a)
    return re.findall(r'"([^"]*)"', m.group(1))


def strip_comments(src):
    out, i, q = [], 0, False
    while i < len(src):
        c = src[i]
        if q:
            out.append(c)
            if c == '"' and src[i - 1] != "\\":
                q = False
        elif c == '"':
            q = True
            out.append(c)
        elif src.startswith("//", i):
            i = src.find("\n", i)
            if i < 0:
                break
            continue
        elif src.startswith("/*", i):
            i = src.index("*/", i) + 2
            continue
        else:
            out.append(c)
        i += 1
    return "".join(out)


def expected_assets(game):
    """val assets = ConditionalAssetsList(...)(ImageAsset(...) :: ... :: $) :: ConditionalAssetsList(...)(...) :: ..."""
    with open(os.path.join(HRF_DIR, game, "meta.scala"), encoding="utf-8") as f:
        src = strip_comments(f.read())
    pos = src.index("val assets =") + len("val assets =")
    out = []
    while True:
        m = re.compile(r"\s*ConditionalAssetsList\(").match(src, pos)
        if not m:
            break
        args, pos = call_args(src, m.end() - 1)
        positional = [a for a in args[1:] if not re.match(r"\w+\s*=", a)]
        named = dict(re.match(r"(\w+)\s*=\s*(.*)", a, re.S).groups() for a in args[1:] if re.match(r"\w+\s*=", a))
        d = literal(named.get("path", positional[0] if positional else '""'))
        body, pos = call_args(src, src.index("(", pos))
        for im in re.finditer(r"\bImageAsset\(", body[0]):
            iargs, _ = call_args(body[0], im.end() - 1)
            for n in values(src, iargs[1] if len(iargs) > 1 else iargs[0]):
                out.append((game, d, n))
        m = re.compile(r"\s*::").match(src, pos)
        if not m:
            break
        pos = m.end()
    if not out:
        raise ValueError("no assets parsed from %s/meta.scala" % game)
    return sorted(set(out))


def rel_path(game, d, name):
    return "/".join(["webp2", game, "images"] + ([d] if d else []) + [name + ".webp"])


def out_path(game, d, name):
    return os.path.join(HRF_DIR, rel_path(game, d, name))


def arcs_path(d, name):
    return out_path("arcs", d, name)


last_request = 0.0


def fetch(url):
    global last_request
    for attempt in range(RETRIES):
        wait = last_request + 1.0 / RATE - time.time()
        if wait > 0:
            time.sleep(wait)
        last_request = time.time()
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (403, 404):
                return None
            err = e
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            err = e
        time.sleep(2 ** attempt)
    print("failed %s: %s" % (url, err), file=sys.stderr)
    return None


def load_cards(download):
    index = os.path.join(ARCS_CACHE, "cards.min.json")
    if download or not os.path.exists(index):
        data = fetch(INDEX_URL)
        if data is None:
            sys.exit("cannot download " + INDEX_URL)
        with open(index, "wb") as f:
            f.write(data)
    with open(index, encoding="utf-8") as f:
        values, root = json.load(f)
    cards = cj_decode(values, root)
    return [c for c in cards if c["game"] == "arcs" and c["locale"] == "en-US"]


def download_to(url, cached):
    data = fetch(url)
    if data is None:
        return False
    os.makedirs(os.path.dirname(cached), exist_ok=True)
    with open(cached, "wb") as f:
        f.write(data)
    return True


def copy_if_newer(cached, dst, force):
    if force or not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(cached):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(cached, "rb") as s, open(dst, "wb") as f:
            f.write(s.read())
        return True
    return False


def import_hrf(expected, download, force):
    written = unavailable = 0
    for a in expected:
        rel = rel_path(*a)
        cached = os.path.join(HRF_CACHE, rel)
        if not os.path.exists(cached):
            if not download:
                continue
            if not download_to(HRF_URL % rel, cached):
                print("not on hrf.im: " + rel)
                unavailable += 1
                continue
        written += copy_if_newer(cached, os.path.join(HRF_DIR, rel), force)
    print("hrf.im images: %d written, %d unavailable" % (written, unavailable))


def global_images():
    """The images HRF loads on every page whatever the game: HRFR.original in hrf.scala."""
    with open(os.path.join(HRF_DIR, "hrf.scala"), encoding="utf-8") as f:
        src = f.read()
    block = src[src.index("object HRFR"):src.index("val loader", src.index("object HRFR"))]
    return [p[len("/hrf/"):] for p in re.findall(r'"(/hrf/[^"]+)"', block)]


def fonts():
    """Every font index.html declares: the lobby shows each game in its own typeface."""
    with open(os.path.join(HRF_DIR, "index.html"), encoding="utf-8") as f:
        names = sorted(set(re.findall(r'url\("fonts/([^"]+)"\)', f.read())))
    return [(HRF_FONTS_URL % n, "fonts/" + n) for n in names]


def import_hrf_extras(download, force):
    written = 0
    for url, rel in fonts() + [(HRF_URL % r, r) for r in HRF_EXTRAS + global_images()]:
        cached = os.path.join(HRF_CACHE, rel)
        if not os.path.exists(cached):
            if not download or not download_to(url, cached):
                print("not on hrf.im: " + url)
                continue
        written += copy_if_newer(cached, os.path.join(HRF_DIR, rel), force)
    print("hrf.im fonts and shared images: %d written" % written)


def import_cards(cards, download, replace, force):
    """Copy the card images; replace=False only fills cards absent from webp2."""
    written = 0
    for c in cards:
        target = hrf_name(c["id"])
        if target is None:
            print("unmapped card %s %s" % (c["id"], c["name"]))
            continue
        dst = arcs_path(*target)
        if os.path.exists(dst) and not replace:
            continue
        cached = os.path.join(CARDS_CACHE, c["id"][len("ARCS-"):] + ".webp")
        if not os.path.exists(cached):
            if not download or not download_to(c["image"], cached):
                print("no image for %s %s" % (c["id"], c["image"]))
                continue
        written += copy_if_newer(cached, dst, force)
    print("buried giant cards: %d in index, %d written" % (len(cards), written))


# Arcs Development Kit

class DevKit:
    def __init__(self, path):
        self.files = {}
        if os.path.isdir(path):
            for root, _, names in os.walk(path):
                for n in names:
                    if n.lower().endswith(".png"):
                        self.files[n[:-4]] = os.path.join(root, n)
            self.zip = None
        else:
            self.zip = zipfile.ZipFile(path)
            for n in self.zip.namelist():
                if n.lower().endswith(".png"):
                    self.files[os.path.basename(n)[:-4]] = n

    def image(self, name):
        if name not in self.files:
            name = "arcs dev_" + name
        p = self.files[name]
        data = self.zip.read(p) if self.zip else open(p, "rb").read()
        return Image.open(io.BytesIO(data)).convert("RGBA")


def fit(im, w, h, valign="center"):
    """Trim transparent borders, scale to fit w x h, place in a w x h canvas."""
    box = im.getbbox()
    if box:
        im = im.crop(box)
    s = min(w / im.width, h / im.height)
    im = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    y = {"center": (h - im.height) // 2, "bottom": h - im.height}[valign]
    canvas.alpha_composite(im, ((w - im.width) // 2, y))
    return canvas


def darken(im):
    rgb = ImageEnhance.Brightness(im.convert("RGB")).enhance(0.55)
    rgb = ImageEnhance.Color(rgb).enhance(0.6)
    rgb.putalpha(im.getchannel("A"))
    return rgb


def blank_die(im):
    return Image.new("RGBA", im.size, im.getpixel((im.width // 2, 4)))


def save(im, d, name, force, written):
    dst = arcs_path(d, name)
    if os.path.exists(dst) and not force:
        return
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    lossless = im.width <= 200 or im.height <= 200   # the rule of convert-images.scala
    im.save(dst, "WEBP", lossless=lossless, quality=QUALITY if not lossless else 100, method=6)
    written.append((d, name))


COLORS = {"r": "red", "b": "blue", "w": "white", "y": "yellow"}

# sizes of the map figures: ImageRect(img(...), anchorX, anchorY, scale) in ui.scala draws
# the image at its natural size around the anchor, the hitboxes give the extent
BUILDING = (122, 122)       # city / starport, anchor (61, 61)
SHIP = (194, 81)            # anchor (97, 71), hull spans x -97..97, y -70..10
FLAGSHIP = (132, 180)       # anchor (66, 170)
AGENT = (42, 68)            # anchor (21, 66), 42x68 in the court pane
BLIGHT = (86, 86)           # anchor (43, 79)

# HRF die faces (game.scala) <- dev kit die faces, matched by their symbols
DICE = {
    "assault-die-1": "dice assault 6",       # blank
    "assault-die-2": "dice assault 2 5",     # self hit + hit
    "assault-die-3": "dice assault 1",       # intercept + hit
    "assault-die-4": "dice assault 3",       # self hit + 2 hits
    "assault-die-5": "dice assault 4",       # 2 hits
    "assault-die-6": "dice assault 2 5",     # self hit + hit
    "raid-die-1": "dice raid 1",             # intercept + 2 keys
    "raid-die-2": "dice raid 2",             # self hit + key
    "raid-die-3": "dice raid 6",             # intercept
    "raid-die-4": "dice raid 3-4",           # self hit + building hit
    "raid-die-5": "dice raid 5",             # building hit + key
    "raid-die-6": "dice raid 3-4",           # self hit + building hit
    "skirmish-die-1": "dice skirmish 4-5",   # blank
    "skirmish-die-2": "dice skirmish 4-5",
    "skirmish-die-3": "dice skirmish 4-5",
    "skirmish-die-4": "dice skirmish 1-3",   # hit
    "skirmish-die-5": "dice skirmish 1-3",
    "skirmish-die-6": "dice skirmish 1-3",
}

AMBITIONS = ["front 2-0", "front 3-2", "front 5-3", "back 4-2", "back 6-3", "back 9-4"]

RESOURCES = ["material", "fuel", "weapon", "relic", "psionic"]


def import_devkit(kit, force):
    written = []
    approximated = []

    def approx(why, d, *names):
        for n in names:
            if (d, n) in written:
                approximated.append("%s/%s: %s" % (d, n, why))

    for c, color in COLORS.items():
        piece = lambda p: kit.image("player piece %s %s" % (color, p))
        city, city_back = fit(piece("city"), *BUILDING), fit(piece("city back"), *BUILDING)
        port, port_back = fit(piece("starport"), *BUILDING), fit(piece("starport back"), *BUILDING)
        ship = fit(piece("ship"), *SHIP, valign="bottom")
        flagship = fit(piece("flagship"), *FLAGSHIP, valign="bottom")
        agent = fit(piece("agent"), *AGENT, valign="bottom")

        save(city, "figure", c + "-city", force, written)
        save(city_back, "figure", c + "-city-damaged", force, written)
        save(port, "figure", c + "-starport", force, written)
        save(port_back, "figure", c + "-starport-damaged", force, written)
        save(ship, "figure", c + "-ship", force, written)
        save(flagship, "figure", c + "-flagship", force, written)
        save(agent, "figure", c + "-agent", force, written)

        save(city, "figure", c + "-city-seat", force, written)
        save(city_back, "figure", c + "-city-seat-damaged", force, written)
        approx("plain city, no seat marker", "figure", c + "-city-seat", c + "-city-seat-damaged")
        save(port, "figure", c + "-starport-alt", force, written)
        save(port_back, "figure", c + "-starport-alt-damaged", force, written)
        approx("triangle starport, the dev kit has no star-shaped one", "figure", c + "-starport-alt", c + "-starport-alt-damaged")
        save(darken(ship), "figure", c + "-ship-damaged", force, written)
        save(darken(agent), "figure", c + "-agent-damaged", force, written)
        approx("darkened piece", "figure", c + "-ship-damaged", c + "-agent-damaged")
        for x in "abc":
            save(agent, "figure", c + "-agent-" + x, force, written)
        approx("plain agent, no A/B/C label", "figure", c + "-agent-a", c + "-agent-b", c + "-agent-c")

    free_city = fit(kit.image("free city"), *BUILDING)
    free_port = fit(kit.image("free starport"), *BUILDING)
    free_port_back = fit(kit.image("free starport back"), *BUILDING)
    save(free_city, "figure", "free-city", force, written)
    save(fit(kit.image("free city back"), *BUILDING), "figure", "free-city-damaged", force, written)
    save(free_port, "figure", "free-starport", force, written)
    save(free_port_back, "figure", "free-starport-damaged", force, written)
    save(free_port, "figure", "free-starport-alt", force, written)
    save(free_port_back, "figure", "free-starport-alt-damaged", force, written)
    approx("triangle starport", "figure", "free-starport-alt", "free-starport-alt-damaged")

    imperial = fit(kit.image("imperial ship"), *SHIP, valign="bottom")
    save(imperial, "figure", "imperial-ship", force, written)
    save(darken(imperial), "figure", "imperial-ship-damaged", force, written)
    approx("darkened piece", "figure", "imperial-ship-damaged")

    save(fit(kit.image("punchboard blight front"), *BLIGHT, valign="bottom"), "figure", "blight", force, written)
    save(fit(kit.image("punchboard blight back"), *BLIGHT, valign="bottom"), "figure", "blight-damaged", force, written)
    approx("round Blight token, not a standing figure", "figure", "blight", "blight-damaged")

    for r in RESOURCES:
        save(kit.image("punchboard resource " + r), "icon", r, force, written)
    save(kit.image("icon objective"), "icon", "objective", force, written)

    for name, src in DICE.items():
        save(kit.image(src), "icon", name, force, written)
    save(kit.image("dice assault 6"), "icon", "assault-die", force, written)
    save(kit.image("dice skirmish 4-5"), "icon", "skirmish-die", force, written)
    save(blank_die(kit.image("dice raid 6")), "icon", "raid-die", force, written)
    approx("blank face filled with the raid die colour", "icon", "raid-die")

    for a in AMBITIONS:
        save(kit.image("punchboard ambition " + a), "ambition", "ambition-values-" + a.split(" ")[1], force, written)

    back = kit.image("CardAsset-Back-Action")
    save(back, "action", "card-back", force, written)
    save(back, "icon", "card-back-small", force, written)
    approx("full action card back", "icon", "card-back-small")

    print("dev kit: %d images written" % len(written))
    return approximated


# the compiled Arcs client of hrf.im

def client_assets(js, meta):
    """(dir, file) of every image on a compiled client's asset list. Compiled, the Scala
    ConditionalAssetsList(cond, path, prefix, ext, ...)(ImageAsset(name, path, filename, ext, ...) :: ...)
    reads new L(meta, new F(cond), path, prefix, ext, ...) followed by new A(name, path, filename, ext, ...),
    a linked list (new w(head, tail)) the meta builds where it sets its name, a.x("arcs") or this.x="tokc".
    The class names change from build to build: they are found from the first list and asset."""
    marker = re.search(r'a\.[\w$]+\("%s"\);|this\.[\w$]+="%s";' % (meta, meta), js)
    if not marker:
        raise ValueError("cannot find where the %s meta is set up" % meta)
    lst = re.compile(r"\bnew ([\w$]+)\((?:a|this),new [\w$]+\(\([\w$,]*\)=>").search(js, marker.end())
    if not lst:
        raise ValueError("cannot find the %s asset lists" % meta)
    cons = js.rindex("new ", 0, lst.start() - 1)          # new w( holding the first list
    body, _ = call_args(js, js.index("(", cons))
    body = ",".join(body)
    asset = re.search(r'\bnew ([\w$]+)\("[^"]*","[^"]*",[^,]*,"[^"]*",', body)
    out = []
    d = ""
    for t in re.finditer(r"\bnew (%s|%s)\(" % (re.escape(lst.group(1)), re.escape(asset.group(1))), body):
        args, _ = call_args(body, t.end() - 1)
        # default arguments compile to (a.la(),"") or (b1(this),""): the value is the last one
        lit = lambda a: literal(re.sub(r'^\([\w$.]+\([\w$]*\),(.*)\)$', r"\1", a, flags=re.S))
        if t.group(1) == lst.group(1):
            d = lit(args[2]) or ""
        else:
            path = lit(args[1]) or d
            name = lit(args[2])
            names = [name] if name is not None else re.findall(r'"(map-out-3[^"]*)"', js)   # the arcs easter egg
            for n in names:
                out.append((path, n))
    if len(out) < 50:
        raise ValueError("only %d assets parsed from the %s client" % (len(out), meta))
    return sorted(set(out))


# the menu line "Arcs[/BR] on HRF will be discontinued on September 30th", a link to a video, is shown
# when the meta is arcs or arcs-br: (cmp(eq(), "arcs", meta.name()) ? line : nothing). Compiled names
# change from build to build, so the condition is found by going back from the notice to the nearest one.

NOTICE = re.compile(r'"Arcs(/BR)? on HRF will be discontinued')
NOTICE_CONDITION = re.compile(r'[\w$]+\([\w$]+\(\),"arcs(-br)?",[\w$.]+\(\)\)\?')


def switch_off_notices(js):
    """Only a condition directly guarding a menu line with the notice's video link counts; anything
    else (the notice's mention elsewhere, another condition) is left alone."""
    n = 0
    for m in reversed(list(NOTICE.finditer(js))):
        window = js[max(0, m.start() - 600):m.start()]
        conds = list(NOTICE_CONDITION.finditer(window))
        if not conds:
            continue
        c = conds[-1]
        between = window[c.end():]
        if len(between) > 250 or "youtube.com/watch" not in between or "?" in between.replace("watch?v", ""):
            continue
        start = m.start() - len(window) + c.start()
        js = js[:start] + "!1?" + js[start + len(c.group(0)):]
        n += 1
    return js, n


def import_clients(metas, download, force, patch):
    os.makedirs(VENDOR_DIR, exist_ok=True)
    with open(os.path.join(VENDOR_DIR, ".gitignore"), "w") as f:
        f.write("# hrf.im's compiled clients and their images (tools/fetch-assets.py): never commit\n*\n")

    vendored = []
    for meta in metas:
        assets = set()
        for version in CLIENTS[meta]:
            rel = CLIENT_REL % (meta, meta, version)
            cached = os.path.join(HRF_CACHE, "hrfn", rel)
            if not os.path.exists(cached):
                if not download or not download_to(HRFN_URL % rel, cached):
                    print("not on hrf.im: " + HRFN_URL % rel)
                    continue
            with open(cached, encoding="utf-8") as f:
                js = f.read()
            n = 0
            if patch and meta == "arcs":
                js, n = switch_off_notices(js)
            dst = os.path.join(VENDOR_DIR, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst + ".tmp", "w", encoding="utf-8") as f:
                f.write(js)
            os.replace(dst + ".tmp", dst)
            found = client_assets(js, meta)
            assets.update(found)
            print("%s client %s: %d images%s" % (meta, version, len(found), ", %d discontinued notices switched off" % n if meta == "arcs" else ""))

        written = unavailable = 0
        for d, n in sorted(assets):
            rel = meta + "/assets/" + (d + "/" if d else "") + n + ".webp"
            vendored.append(os.path.join("vendor", rel))
            cached = os.path.join(HRF_CACHE, "hrfn", rel)
            if not os.path.exists(cached):
                if not download:
                    continue
                if not download_to(HRFN_URL % rel, cached):
                    print("not on hrf.im: " + HRFN_URL % rel)
                    unavailable += 1
                    continue
            written += copy_if_newer(cached, os.path.join(VENDOR_DIR, rel), force)
        print("%s client images: %d, %d written, %d unavailable" % (meta, len(assets), written, unavailable))
    return vendored


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("games", nargs="*", help="plugin folders to fetch (default: %s)" % ", ".join(g for g in games() if g not in UNUSED))
    ap.add_argument("--devkit", default=DEVKIT_DEFAULT, help="Arcs Development Kit zip or folder")
    ap.add_argument("--no-download", action="store_true", help="use the cached index and images only")
    ap.add_argument("--no-hrf", action="store_true", help="do not use hrf.im; for Arcs take the cards from Buried Giant and the rest from the dev kit")
    ap.add_argument("--force", action="store_true", help="rewrite existing webp files")
    ap.add_argument("--no-clients", action="store_true", help="skip hrf.im's compiled clients (CLIENTS)")
    ap.add_argument("--no-patch", action="store_true", help="keep the discontinued notice in the Arcs client")
    args = ap.parse_args()

    selected = args.games or [g for g in games() if g not in UNUSED and g not in OWN_FETCHER] + [m for m in CLIENTS if m not in games()]
    for g, why in UNUSED.items():
        if g not in selected:
            print("skipping %s: %s" % (g, why))
    for g, other in OWN_FETCHER.items():
        if g in selected:
            sys.exit("%s has its own fetcher: %s" % (g, other))
        print("skipping %s: its images come from %s" % (g, other))
    for g in selected:
        if g not in games() and g not in CLIENTS:
            sys.exit("no %s/meta.scala and no hrf.im client for it" % g)
    clients = [g for g in selected if g in CLIENTS]
    selected = [g for g in selected if g in games()]

    os.makedirs(CACHE_DIR, exist_ok=True)

    expected = [a for g in selected for a in expected_assets(g)]
    for g in selected:
        print("%s: %d images" % (g, sum(1 for a in expected if a[0] == g)))
    download = not args.no_download

    if not args.no_hrf:
        import_hrf(expected, download, args.force)
        import_hrf_extras(download, args.force)

    approximated = []
    if "arcs" in selected:
        arcs = [a for a in expected if a[0] == "arcs"]
        if args.no_hrf or any(not os.path.exists(out_path(*a)) for a in arcs):
            os.makedirs(ARCS_CACHE, exist_ok=True)
            import_cards(load_cards(download), download, args.no_hrf, args.force)

        # the dev kit only fills gaps, it never replaces a card or an hrf.im image
        if os.path.exists(args.devkit):
            approximated = import_devkit(DevKit(args.devkit), args.force and args.no_hrf)
        else:
            print("no dev kit at %s, skipping Arcs pieces and tokens" % args.devkit)

    vendored = []
    if clients and not args.no_hrf and not args.no_clients:
        vendored = import_clients(clients, download, args.force, not args.no_patch)

    missing = [rel_path(*a) for a in expected if not os.path.exists(out_path(*a))]
    missing += [v for v in vendored if not os.path.exists(os.path.join(HRF_DIR, v))]
    with open(MISSING_LOG, "w", encoding="utf-8") as f:
        for a in missing:
            f.write("%s\n" % a)
        if approximated:
            f.write("\n# approximated\n")
            for a in approximated:
                f.write("%s\n" % a)
    total = len(expected) + len(vendored)
    print("expected %d images, %d present, %d missing (see %s)" % (
        total, total - len(missing), len(missing), os.path.relpath(MISSING_LOG, HRF_DIR)))


if __name__ == "__main__":
    main()
