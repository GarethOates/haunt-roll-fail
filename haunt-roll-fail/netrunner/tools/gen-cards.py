#!/usr/bin/env python3 -I
"""
Generate netrunner/cards-data*.scala from the NetrunnerDB EDN export.

Usage:
    python3 -I netrunner/tools/gen-cards.py [path/to/raw_data.edn] [--check] [--out DIR]

If the EDN file is missing it is downloaded from
    https://raw.githubusercontent.com/NoahTheDuke/netrunner-data/master/edn/raw_data.edn
into netrunner/assets-cache/raw_data.edn (git-ignored).

Output (package netrunner):
    cards-data-1.scala .. cards-data-N.scala   object CardsDataN { val chunk : String = \"\"\"...\"\"\" }
    cards-data.scala                            object CardsData { chunks, sets }

Each chunk line is one card, 23 TAB-separated fields in the order of CardData's
constructor (see cards.scala, Cards.parseLine). Only the English :cards are used;
"Rules Insert" pseudo-cards are excluded. Cards are sorted by code.

--check re-parses the generated chunks with the same rules as Cards.parseLine
and prints counts per type / side / set.

Stdlib only. The EDN reader below is deliberately small: it understands exactly
what the netrunner-data export uses (maps, vectors, lists, sets, strings,
keywords, symbols, numbers, nil/true/false, chars, comments, tagged literals).
"""

import os
import re
import sys
import urllib.request

EDN_URL = "https://raw.githubusercontent.com/NoahTheDuke/netrunner-data/master/edn/raw_data.edn"

HERE = os.path.dirname(os.path.abspath(__file__))
NETRUNNER_DIR = os.path.dirname(HERE)
DEFAULT_EDN = os.path.join(NETRUNNER_DIR, "assets-cache", "raw_data.edn")

FORMATS = ["standard", "startup", "eternal", "system-gateway", "core", "throwback"]
EXCLUDED_TYPES = {"Rules Insert"}
CHUNK_LIMIT = 56 * 1000  # bytes of card lines per chunk; keeps each source file < 60 KB and each literal far below the JVM 65535-byte limit
FIELD_COUNT = 23


# ---------------------------------------------------------------- EDN reader

class Keyword(str):
    """An EDN keyword; compares equal to its name without the leading colon."""
    __slots__ = ()

    def __repr__(self):
        return ":" + str.__str__(self)


class Symbol(str):
    __slots__ = ()


_TOKEN_RE = re.compile(r"""
    (?P<ws>[\s,]+)
  | (?P<comment>;[^\n]*)
  | (?P<discard>\#_)
  | (?P<set>\#\{)
  | (?P<tag>\#[A-Za-z][A-Za-z0-9.\-_/]*)
  | (?P<open>[\[\(\{])
  | (?P<close>[\]\)\}])
  | (?P<string>"(?:[^"\\]|\\.)*")
  | (?P<char>\\(?:newline|space|tab|formfeed|backspace|return|u[0-9A-Fa-f]{4}|\S))
  | (?P<keyword>:[^\s,\[\]\(\)\{\}"';]+)
  | (?P<number>[+-]?(?:\d+\.\d*(?:[eE][+-]?\d+)?|\d+(?:[eE][+-]?\d+)?|\d+/\d+)[MN]?)
  | (?P<symbol>[^\s,\[\]\(\)\{\}"';#][^\s,\[\]\(\)\{\}"';]*)
""", re.X)

_STRING_ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "b": "\b", "f": "\f"}


def _unescape(body):
    out = []
    i = 0
    n = len(body)
    while i < n:
        c = body[i]
        if c == "\\":
            i += 1
            e = body[i]
            if e == "u":
                out.append(chr(int(body[i + 1:i + 5], 16)))
                i += 5
                continue
            out.append(_STRING_ESCAPES.get(e, e))
            i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def _tokens(src):
    pos = 0
    n = len(src)
    while pos < n:
        m = _TOKEN_RE.match(src, pos)
        if not m:
            raise ValueError("EDN: cannot tokenize at %d: %r" % (pos, src[pos:pos + 40]))
        pos = m.end()
        kind = m.lastgroup
        if kind in ("ws", "comment"):
            continue
        yield kind, m.group(kind)
    yield "eof", ""


class EdnParser:
    def __init__(self, src):
        self._it = _tokens(src)
        self._peek = next(self._it)

    def _next(self):
        t = self._peek
        self._peek = next(self._it)
        return t

    def read(self):
        kind, text = self._next()
        if kind == "eof":
            raise EOFError
        if kind == "discard":
            self.read()  # drop next form
            return self.read()
        if kind == "tag":
            tag = text
            value = self.read()
            return ("#tagged", tag, value)
        if kind == "open":
            return self._collection(text)
        if kind == "set":
            items = self._items("}")
            return frozenset(items) if all(isinstance(x, (str, int, float, bool, type(None))) for x in items) else tuple(items)
        if kind == "close":
            raise ValueError("EDN: unexpected " + text)
        if kind == "string":
            return _unescape(text[1:-1])
        if kind == "keyword":
            return Keyword(text[1:])
        if kind == "char":
            return text
        if kind == "number":
            t = text.rstrip("MN")
            if "/" in t:
                a, b = t.split("/")
                return int(a) / int(b)
            if any(c in t for c in ".eE"):
                return float(t)
            return int(t)
        if kind == "symbol":
            if text == "nil":
                return None
            if text == "true":
                return True
            if text == "false":
                return False
            return Symbol(text)
        raise ValueError("EDN: unknown token " + kind)

    def _items(self, closer):
        items = []
        while True:
            kind, text = self._peek
            if kind == "close":
                if text != closer:
                    raise ValueError("EDN: expected %s got %s" % (closer, text))
                self._next()
                return items
            if kind == "eof":
                raise ValueError("EDN: unterminated collection")
            if kind == "discard":
                self._next()
                self.read()
                continue
            items.append(self.read())

    def _collection(self, opener):
        if opener == "[":
            return self._items("]")
        if opener == "(":
            return tuple(self._items(")"))
        items = self._items("}")
        if len(items) % 2:
            raise ValueError("EDN: map with odd number of forms")
        d = {}
        for i in range(0, len(items), 2):
            k = items[i]
            if isinstance(k, Keyword):
                k = str.__str__(k)
            d[k] = items[i + 1]
        return d


def read_edn(src):
    return EdnParser(src).read()


# ---------------------------------------------------------------- generation

def load_edn(path):
    if not os.path.exists(path):
        print("EDN not found at %s, downloading %s" % (path, EDN_URL), file=sys.stderr)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with urllib.request.urlopen(EDN_URL, timeout=120) as r, open(path + ".part", "wb") as w:
            while True:
                b = r.read(1 << 16)
                if not b:
                    break
                w.write(b)
        os.replace(path + ".part", path)
    with open(path, "r", encoding="utf-8") as f:
        return read_edn(f.read())


def opt_int(v):
    if v is None:
        return ""
    if isinstance(v, bool):
        raise ValueError("boolean where int expected: %r" % (v,))
    if isinstance(v, float) and v.is_integer():
        v = int(v)
    if not isinstance(v, int):
        raise ValueError("non-int value: %r" % (v,))
    return str(v)


def clean_text(s):
    if s is None:
        return ""
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    s = s.replace("\t", " ")
    s = s.replace("\n", "\\n")
    # a triple quote would terminate the Scala literal
    while '"""' in s:
        s = s.replace('"""', '""″')
    return s


def card_line(c):
    kind = c.get("type") or ""
    title = clean_text(c.get("title"))
    fmt = c.get("format") or {}
    legal = [f for f in FORMATS if isinstance(fmt.get(f), dict) and fmt[f].get("legal") is True]
    # the top-level :rotated is false for every card in this export; "rotated" here means rotated out of Standard
    rotated = bool(c.get("rotated")) or (isinstance(fmt.get("standard"), dict) and fmt["standard"].get("rotated") is True)
    deck_limit = c.get("deck-limit")
    if deck_limit is None:
        deck_limit = 1 if kind == "Identity" else 3
    fields = [
        c["code"],
        title,
        c.get("side") or "",
        c.get("faction") or "",
        kind,
        "|".join(clean_text(x) for x in (c.get("subtypes") or [])),
        opt_int(c.get("cost")),
        opt_int(c.get("strength")),
        opt_int(c.get("advancementcost")),
        opt_int(c.get("agendapoints")),
        opt_int(c.get("memoryunits")),
        opt_int(c.get("trash")),
        opt_int(c.get("factioncost")),
        opt_int(c.get("baselink")),
        opt_int(c.get("minimumdecksize")),
        opt_int(c.get("influencelimit")),
        "1" if c.get("uniqueness") else "0",
        str(int(deck_limit)),
        clean_text(c.get("text")),
        c.get("set_code") or "",
        clean_text(c.get("setname")),
        "1" if rotated else "0",
        "|".join(legal),
    ]
    assert len(fields) == FIELD_COUNT
    for f in fields:
        assert "\t" not in f and "\n" not in f and "\r" not in f, (c["code"], f)
    return "\t".join(fields)


def select_cards(data):
    cards = [c for c in data["cards"] if c.get("type") not in EXCLUDED_TYPES]
    cards.sort(key=lambda c: c["code"])
    codes = [c["code"] for c in cards]
    assert len(codes) == len(set(codes)), "duplicate codes"
    return cards


def ordered_sets(data):
    """(code, name, cycle_code) in release order: by cycle position, then set position, then date."""
    cycles = {c["code"]: c for c in data.get("cycles", [])}
    sets = list(data["sets"])

    def key(s):
        cyc = cycles.get(s.get("cycle_code"), {})
        return (
            s.get("date-release") or s.get("available") or "9999",
            cyc.get("position", s.get("cycle_position", 999)),
            s.get("position", 999),
            s["code"],
        )
    sets.sort(key=key)
    return [(s["code"], s["name"], s.get("cycle_code") or "") for s in sets]


HEADER = """package netrunner
//
//
//
//
import hrf.colmat._
import hrf.logger._
//
//
//
//

"""


def scala_string(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def chunk_lines(lines):
    chunks = []
    cur = []
    size = 0
    for l in lines:
        b = len(l.encode("utf-8")) + 1
        if cur and size + b > CHUNK_LIMIT:
            chunks.append(cur)
            cur = []
            size = 0
        cur.append(l)
        size += b
    if cur:
        chunks.append(cur)
    return chunks


def previous_codes(cards):
    """(oldCode, currentCode) for every earlier printing listed in :previous-versions."""
    pairs = []
    for c in cards:
        for pv in c.get("previous-versions") or []:
            if isinstance(pv, dict) and pv.get("code"):
                pairs.append((str(pv["code"]), c["code"]))
    pairs.sort()
    assert len({a for a, _ in pairs}) == len(pairs), "old code mapped twice"
    return pairs


def write_outputs(out_dir, cards, sets):
    lines = [card_line(c) for c in cards]
    prev = previous_codes(cards)
    chunks = chunk_lines(lines)
    written = []
    for i, ch in enumerate(chunks, 1):
        body = "\n".join(ch)
        assert '"""' not in body
        src = HEADER + "// GENERATED by tools/gen-cards.py -- do not edit. Cards %s .. %s (%d cards)\n" % (ch[0].split("\t")[0], ch[-1].split("\t")[0], len(ch))
        src += "object CardsData%d {\n    val chunk : String = \"\"\"\n%s\n\"\"\"\n}\n" % (i, body)
        p = os.path.join(out_dir, "cards-data-%d.scala" % i)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(src)
        written.append(p)
    # remove stale chunk files from a previous run with more chunks
    j = len(chunks) + 1
    while os.path.exists(os.path.join(out_dir, "cards-data-%d.scala" % j)):
        os.remove(os.path.join(out_dir, "cards-data-%d.scala" % j))
        j += 1

    src = HEADER + "// GENERATED by tools/gen-cards.py -- do not edit. %d cards in %d chunks, %d sets, %d previous codes.\n" % (len(lines), len(chunks), len(sets), len(prev))
    src += "object CardsData {\n"
    src += "    val chunks : $[String] = $(" + ", ".join("CardsData%d.chunk" % i for i in range(1, len(chunks) + 1)) + ")\n\n"
    src += "    // (setCode, setName, cycleCode) in release order\n"
    src += "    val sets : $[(String, String, String)] = $(\n"
    src += ",\n".join("        (%s, %s, %s)" % (scala_string(a), scala_string(b), scala_string(c)) for a, b, c in sets)
    src += "\n    )\n\n"
    src += "    // (oldCode, currentCode): codes of earlier printings of a card (NRDB :previous-versions); only currentCode is in the chunks\n"
    src += "    val previousCodes : $[(String, String)] = $(\n"
    src += ",\n".join("        (%s, %s)" % (scala_string(a), scala_string(b)) for a, b in prev)
    src += "\n    )\n}\n"
    p = os.path.join(out_dir, "cards-data.scala")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)
    written.append(p)
    return written, lines


# ---------------------------------------------------------------- self check

def parse_line_like_scala(l):
    """Mirror of Cards.parseLine in cards.scala."""
    f = l.split("\t")
    f = f + [""] * (FIELD_COUNT - len(f))
    assert len(f) == FIELD_COUNT, "too many fields: %r" % l[:80]

    def oi(s):
        s = s.strip()
        return None if s == "" else int(s)

    def b(s):
        return s.strip() == "1"

    def lst(s):
        return [x.strip() for x in s.split("|") if x.strip() != ""]

    return {
        "code": f[0], "title": f[1], "side": f[2], "faction": f[3], "kind": f[4], "subtypes": lst(f[5]),
        "cost": oi(f[6]), "strength": oi(f[7]), "advancementCost": oi(f[8]), "agendaPoints": oi(f[9]),
        "memory": oi(f[10]), "trashCost": oi(f[11]), "influence": oi(f[12]), "baseLink": oi(f[13]),
        "minDeckSize": oi(f[14]), "influenceLimit": oi(f[15]), "unique": b(f[16]),
        "deckLimit": oi(f[17]) if oi(f[17]) is not None else 3,
        "text": f[18].replace("\\n", "\n"), "setCode": f[19], "setName": f[20], "rotated": b(f[21]), "legal": lst(f[22]),
    }


def check(out_dir):
    from collections import Counter
    chunks = []
    i = 1
    while True:
        p = os.path.join(out_dir, "cards-data-%d.scala" % i)
        if not os.path.exists(p):
            break
        src = open(p, encoding="utf-8").read()
        m = re.search(r'object CardsData%d \{\n    val chunk : String = """\n(.*?)\n"""\n\}' % i, src, re.S)
        assert m, "chunk %d has unexpected shape" % i
        assert len(src.encode("utf-8")) < 60 * 1000, "chunk %d too large" % i
        chunks.append(m.group(1))
        i += 1
    assert chunks, "no chunk files found"
    main = open(os.path.join(out_dir, "cards-data.scala"), encoding="utf-8").read()
    for j in range(1, len(chunks) + 1):
        assert "CardsData%d.chunk" % j in main, "cards-data.scala does not reference CardsData%d" % j
    assert "CardsData%d.chunk" % (len(chunks) + 1) not in main
    sets = re.findall(r'\("([^"]*)", "((?:[^"\\]|\\.)*)", "([^"]*)"\)', main)
    prev = re.findall(r'\("(\d+)", "(\d+)"\)', main)

    cards = []
    for ch in chunks:
        for l in ch.split("\n"):
            l = l.strip()
            if l == "":
                continue
            cards.append(parse_line_like_scala(l))
    codes = [c["code"] for c in cards]
    assert len(codes) == len(set(codes)), "duplicate codes"
    assert codes == sorted(codes), "not sorted by code"
    set_codes = {s[0] for s in sets}
    for c in cards:
        assert c["side"] in ("Corp", "Runner"), c
        assert c["kind"] in ("Identity", "Agenda", "Asset", "Upgrade", "Operation", "ICE", "Event", "Hardware", "Program", "Resource"), c
        assert c["setCode"] in set_codes, "unknown set %s for %s" % (c["setCode"], c["code"])
        assert c["deckLimit"] >= 1
        if c["kind"] == "Identity":
            assert c["deckLimit"] == 1, c
        for fmt in c["legal"]:
            assert fmt in FORMATS, c
        assert "\t" not in c["text"] and "\r" not in c["text"]
    by = lambda k: Counter(c[k] for c in cards)
    code_set = set(codes)
    for old, cur in prev:
        assert cur in code_set, "previousCodes target %s unknown" % cur
        assert old not in code_set, "previousCodes source %s is itself a card" % old
    print("cards: %d  chunks: %d  sets: %d  previous codes: %d" % (len(cards), len(chunks), len(sets), len(prev)))
    print("by type:", dict(by("kind").most_common()))
    print("by side:", dict(by("side").most_common()))
    print("by faction:", dict(by("faction").most_common()))
    print("by set:", dict(sorted(by("setCode").items())))
    print("legal per format:", {f: sum(1 for c in cards if f in c["legal"]) for f in FORMATS})
    print("rotated out of standard:", sum(1 for c in cards if c["rotated"]))
    print("check OK")
    return cards


# ---------------------------------------------------------------- main

def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    opts = [a for a in argv if a.startswith("--")]
    out_dir = NETRUNNER_DIR
    if "--out" in argv:
        out_dir = argv[argv.index("--out") + 1]
        args = [a for a in args if a != out_dir]
    edn_path = args[0] if args else DEFAULT_EDN

    if "--check-only" not in opts:
        data = load_edn(edn_path)
        all_cards = data["cards"]
        cards = select_cards(data)
        sets = ordered_sets(data)
        written, lines = write_outputs(out_dir, cards, sets)
        print("read %d English cards, excluded %d (%s), wrote %d cards" % (len(all_cards), len(all_cards) - len(cards), ", ".join(sorted(EXCLUDED_TYPES)), len(cards)))
        for p in written:
            print("  %s (%d bytes)" % (os.path.relpath(p, NETRUNNER_DIR), os.path.getsize(p)))
    if "--check" in opts or "--check-only" in opts:
        check(out_dir)


if __name__ == "__main__":
    main(sys.argv[1:])
