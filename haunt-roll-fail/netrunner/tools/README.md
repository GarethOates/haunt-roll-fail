# netrunner/tools

Scripts that generate the static card database and the image assets of the
Netrunner plugin (`gen-cards.py`, `fetch-assets.py`, `make-placeholder-cards.py`). Run them from the repository root (`haunt-roll-fail/haunt-roll-fail`)
with `python3 -I` (isolated mode: the data they read is untrusted). Stdlib only,
except `fetch-assets.py` which needs Pillow with WebP support.

Both `netrunner/assets-cache/` and `webp2/` are git-ignored: originals and
generated images live beside the build, never in git. Only the generated
`netrunner/cards-data*.scala` files are committed.

## Card data

    python3 -I netrunner/tools/gen-cards.py [path/to/raw_data.edn] --check

Reads the NetrunnerDB EDN export (`raw_data.edn` from
https://github.com/NoahTheDuke/netrunner-data; downloaded automatically into
`netrunner/assets-cache/raw_data.edn` when the file is missing) and writes

- `netrunner/cards-data-1.scala` .. `cards-data-N.scala` — `object CardsDataN { val chunk : String = """...""" }`,
  one card per line, 23 TAB-separated fields in the order of `CardData`
  (see `cards.scala`, `Cards.parseLine`). Cards are sorted by code, each file
  stays under 60 KB of source.
- `netrunner/cards-data.scala` — `object CardsData { chunks, sets, previousCodes }` with every
  set as `(setCode, setName, cycleCode)` in release order (sorted by the set's
  `:date-release`, then cycle position, then set position) and `previousCodes`
  as `(oldCode, currentCode)` pairs.

The export has exactly one entry per card title: its latest printing. Earlier
printings (e.g. Hedge Fund `01110` in Core, `20110` in Revised Core) appear only
as `:previous-versions`, exported into `CardsData.previousCodes`; those old codes
are not in the chunks and have no image.

Only the English `:cards` are used; the 9 "Rules Insert" pseudo-cards are dropped.
`--check` re-parses the generated chunks with the same rules as `Cards.parseLine`
and prints counts per type / side / faction / set (`--check-only` skips generation).

Field conventions (mirrored in `cards.scala`): optional ints are written as empty
strings, booleans as `1`/`0`, subtypes and legal formats joined with `|`, newlines
in rules text as the two characters `\n`, TABs replaced by spaces. Identities have
`deckLimit` 1, everything else 3 (or the printed deck limit). `legal` lists the
format codes whose entry has `:legal true` (standard, startup, eternal,
system-gateway, core, throwback); banned or rotated-out cards are simply absent.
`rotated` means rotated out of Standard (`:format {:standard {:rotated true}}`;
the export's top-level `:rotated` flag is false for every card).

## Images

    python3 -I netrunner/tools/fetch-assets.py

1. Collects every code from `netrunner/cards-data-*.scala` (run `gen-cards.py` first).
2. Downloads `https://card-images.netrunnerdb.com/v2/large/<code>.jpg` into
   `netrunner/assets-cache/jpg/<code>.jpg`. Existing files are skipped, so the
   script is resumable; at most 5 requests/second; transient errors are retried;
   when the CDN answers 403/404 the script also tries `static.nrdbassets.com/v1`,
   `card-images.netrunnerdb.com/v1` and the image URLs listed by the NetrunnerDB
   v3 (`api-preview`) and v2 card APIs; codes without any image are listed in
   `netrunner/assets-cache/missing.txt` (currently 014001 The Collective and
   014002 Chronos Protocol, two unreleased identities that NetrunnerDB does not
   know). A card image is generated for each of them from the card data by
   `make-placeholder-cards.py` (see below), after the faction logos exist.
3. Converts each jpg to `webp2/netrunner/images/<code>.webp` (quality 82, native
   300x420 size, metadata stripped; skipped when already up to date).
4. Converts the jinteki.net card backs and faction logos from
   `/Users/gareth/Developer/HRF/netrunner-src/resources/public/img` (override with
   `--src-img DIR`): `corp-back.webp`, `runner-back.webp`, `faction-<name>.webp`
   (`faction-corp-hb`, `faction-runner-anarch`, ...; all names lowercase).

## Generated card images

    python3 -I netrunner/tools/make-placeholder-cards.py [--codes 014001,014002] [--force] [--out DIR]

Renders a 300x419 card image for printings that have no downloadable image, in
the layout of the Null Signal Games identities: frame and title band in the
faction colour, the subtitle (the part of the title after the colon), an art
panel with the faction logo (`webp2/netrunner/images/faction-*.webp`), minimum
deck size and influence limit in circles plus the base link for Runner
identities (cost / strength or agenda points for other card types), the
`IDENTITY: subtypes` line, the rules text wrapped to the text box (`[click]`,
`[credit]`... drawn as small icons) and a footer naming the set. The data comes
from `netrunner/cards-data-*.scala`. Without `--codes` it renders 014001, 014002
and every code in `assets-cache/missing.txt` into
`webp2/netrunner/images/<code>.webp` (quality 90); an existing image of another
size (a real card image) is kept unless `--force` is given. Needs Pillow with
WebP and FreeType (fonts: macOS system fonts, then DejaVu, then Pillow's default).
`fetch-assets.py` calls it automatically for codes without any image.

A full run takes about 10 minutes (rate limit); rerunning only converts what changed.
`--codes 30001,30002` restricts the run to a few cards, `--no-download` only converts
what is already cached. The script ends with a coverage summary and the list of
cards without an image.

## Deck import samples

`decklist-samples/` holds NetrunnerDB-style exported decklists (System Gateway +
System Update 2021 only, all legal) in the formats the deck importer must accept:
`3x Card`, `3 Card`, `Card x3`, with an identity on its own line, optional
`Type (n)` section headers, influence dots (`●`) and trailing summary lines.
