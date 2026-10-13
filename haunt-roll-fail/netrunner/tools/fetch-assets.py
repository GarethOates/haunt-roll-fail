#!/usr/bin/env python3 -I
"""
Fetch NetrunnerDB card images and build the webp assets for the netrunner plugin.

Usage:
    python3 -I netrunner/tools/fetch-assets.py [--src-img DIR] [--no-download] [--codes 30001,30002]

Steps:
  1. Collect every card code from the generated netrunner/cards-data-*.scala files.
  2. Download https://card-images.netrunnerdb.com/v2/large/<code>.jpg
     into netrunner/assets-cache/jpg/<code>.jpg (skip existing, resumable,
     <= 5 requests/second, retries on transient errors, 403/404s logged to
     netrunner/assets-cache/missing.txt).
  3. Convert each jpg to webp2/netrunner/images/<code>.webp
     (Pillow, quality 82, native size, metadata stripped; skipped when up to date).
  4. Convert the jinteki.net card backs and faction logos:
       img/card-backs/corp/nsg.png   -> corp-back.webp
       img/card-backs/runner/nsg.png -> runner-back.webp
       img/factions/<name>.png       -> faction-<name>.webp   (lowercase)

Both netrunner/assets-cache/ and webp2/ are git-ignored.
"""

import glob
import os
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
NETRUNNER_DIR = os.path.dirname(HERE)
HRF_DIR = os.path.dirname(NETRUNNER_DIR)

IMAGE_URL = "https://card-images.netrunnerdb.com/v2/large/%s.jpg"
CACHE_DIR = os.path.join(NETRUNNER_DIR, "assets-cache", "jpg")
MISSING_LOG = os.path.join(NETRUNNER_DIR, "assets-cache", "missing.txt")
OUT_DIR = os.path.join(HRF_DIR, "webp2", "netrunner", "images")
SRC_IMG_DIR = "/Users/gareth/Developer/HRF/netrunner-src/resources/public/img"

RATE = 5.0          # requests per second
RETRIES = 5
TIMEOUT = 30
QUALITY = 82
USER_AGENT = "haunt-roll-fail-netrunner-assets/1.0 (+https://github.com/)"


def card_codes():
    codes = []
    for p in sorted(glob.glob(os.path.join(NETRUNNER_DIR, "cards-data-*.scala"))):
        with open(p, encoding="utf-8") as f:
            for l in f:
                l = l.rstrip("\n")
                if "\t" in l:
                    code = l.split("\t", 1)[0].strip()
                    if re.fullmatch(r"\d{5,6}", code):
                        codes.append(code)
    codes = sorted(set(codes))
    if not codes:
        sys.exit("no card codes found in %s/cards-data-*.scala -- run gen-cards.py first" % NETRUNNER_DIR)
    return codes


class RateLimiter:
    def __init__(self, per_second):
        self.interval = 1.0 / per_second
        self.next_at = 0.0

    def wait(self):
        now = time.monotonic()
        if now < self.next_at:
            time.sleep(self.next_at - now)
        self.next_at = max(now, self.next_at) + self.interval


def download(code, limiter, log):
    """Returns 'ok', 'cached', 'missing' or 'failed'."""
    dest = os.path.join(CACHE_DIR, code + ".jpg")
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return "cached"
    url = IMAGE_URL % code
    for attempt in range(1, RETRIES + 1):
        limiter.wait()
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
                data = r.read()
            if not data or data[:2] != b"\xff\xd8":
                raise ValueError("not a jpeg (%d bytes)" % len(data))
            tmp = dest + ".part"
            with open(tmp, "wb") as w:
                w.write(data)
            os.replace(tmp, dest)
            return "ok"
        except urllib.error.HTTPError as e:
            if e.code in (403, 404):
                # the CDN answers 403 for images that do not exist
                log("404 %s" % code)
                return "missing"
            log("HTTP %d %s (attempt %d)" % (e.code, code, attempt))
            if e.code in (429, 500, 502, 503, 504) and attempt < RETRIES:
                time.sleep(min(60, 2 ** attempt))
                continue
            return "failed"
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as e:
            log("error %s: %s (attempt %d)" % (code, e, attempt))
            if attempt == RETRIES:
                return "failed"
            time.sleep(min(60, 2 ** attempt))
    return "failed"


def up_to_date(src, dst):
    return os.path.exists(dst) and os.path.getsize(dst) > 0 and os.path.getmtime(dst) >= os.path.getmtime(src)


def convert(src, dst, quality=QUALITY):
    if up_to_date(src, dst):
        return "cached"
    with Image.open(src) as im:
        im.load()
        if im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info):
            im = im.convert("RGBA")
        else:
            im = im.convert("RGB")
        # a fresh image carries no exif / icc / xmp, so metadata is stripped
        clean = Image.new(im.mode, im.size)
        clean.paste(im)
        tmp = dst + ".part"
        clean.save(tmp, "WEBP", quality=quality, method=4, exif=b"", icc_profile=b"", xmp=b"")
        os.replace(tmp, dst)
    return "ok"


def convert_extras(src_img_dir, log):
    jobs = [
        (os.path.join(src_img_dir, "card-backs", "corp", "nsg.png"), "corp-back.webp"),
        (os.path.join(src_img_dir, "card-backs", "runner", "nsg.png"), "runner-back.webp"),
    ]
    for p in sorted(glob.glob(os.path.join(src_img_dir, "factions", "*.png"))):
        name = os.path.splitext(os.path.basename(p))[0].lower()
        jobs.append((p, "faction-%s.webp" % name))
    done = []
    for src, name in jobs:
        if not os.path.exists(src):
            log("extra source missing: %s" % src)
            continue
        dst = os.path.join(OUT_DIR, name)
        r = convert(src, dst)
        done.append((name, r))
    return done


def main(argv):
    src_img_dir = SRC_IMG_DIR
    if "--src-img" in argv:
        src_img_dir = argv[argv.index("--src-img") + 1]
    only = None
    if "--codes" in argv:
        only = set(argv[argv.index("--codes") + 1].split(","))
    no_download = "--no-download" in argv

    os.makedirs(CACHE_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)

    def log(msg):
        print(msg, flush=True)
        if msg.startswith("404 "):
            with open(MISSING_LOG, "a") as f:
                f.write(msg[4:] + "\n")

    codes = card_codes()
    if only:
        codes = [c for c in codes if c in only]
    print("%d card codes" % len(codes), flush=True)

    if os.path.exists(MISSING_LOG):
        os.remove(MISSING_LOG)

    limiter = RateLimiter(RATE)
    stats = {"ok": 0, "cached": 0, "missing": 0, "failed": 0}
    missing = []
    failed = []
    converted = {"ok": 0, "cached": 0, "error": 0}
    pool = ThreadPoolExecutor(max_workers=4)
    futures = []

    def convert_card(code):
        try:
            r = convert(os.path.join(CACHE_DIR, code + ".jpg"), os.path.join(OUT_DIR, code + ".webp"))
            converted[r] += 1
        except Exception as e:
            converted["error"] += 1
            log("convert error %s: %s" % (code, e))

    t0 = time.time()
    for i, code in enumerate(codes, 1):
        r = "cached" if no_download and os.path.exists(os.path.join(CACHE_DIR, code + ".jpg")) else download(code, limiter, log)
        stats[r] += 1
        if r == "missing":
            missing.append(code)
        elif r == "failed":
            failed.append(code)
        elif r in ("ok", "cached"):
            futures.append(pool.submit(convert_card, code))
        if i % 100 == 0 or i == len(codes):
            print("[%4d/%d] %.0fs downloaded %d cached %d missing %d failed %d" % (i, len(codes), time.time() - t0, stats["ok"], stats["cached"], stats["missing"], stats["failed"]), flush=True)

    for f in futures:
        f.result()
    pool.shutdown()

    extras = convert_extras(src_img_dir, log)

    have = {os.path.splitext(f)[0] for f in os.listdir(OUT_DIR) if f.endswith(".webp")}
    no_image = [c for c in codes if c not in have]
    print()
    print("download: new %d, cached %d, 404 %d, failed %d" % (stats["ok"], stats["cached"], stats["missing"], stats["failed"]))
    print("convert : new %d, up-to-date %d, errors %d" % (converted["ok"], converted["cached"], converted["error"]))
    print("extras  : %s" % ", ".join("%s(%s)" % e for e in extras))
    print("webp card images: %d of %d cards" % (len(codes) - len(no_image), len(codes)))
    if missing:
        print("404 (no image on NRDB): %s" % " ".join(missing))
    if failed:
        print("FAILED (rerun to retry): %s" % " ".join(failed))
    if no_image:
        print("cards without webp: %s" % " ".join(no_image))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
