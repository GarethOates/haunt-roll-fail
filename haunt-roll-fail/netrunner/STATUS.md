# Netrunner plugin — status

## Implemented

* Plugin scaffolding: `package.scala`, `meta.scala` (name `netrunner`, Corp/Runner, bots `Basic`/`Random`, lazy card
  image assets), `serialize.scala`, `styles.scala`, registration in `../common.sbt` and `../hrf.scala`.
* Engine (`game.scala` + `rules-*.scala`): zones/servers/remotes, per-card state, pending-effect queue, events,
  registers; setup (deck choice: presets or NetrunnerDB import, shuffle, draw 5, mulligan), turn structure
  (start-of-turn window + triggers, mandatory draw, action phase, discard to hand size, end of turn triggers),
  basic actions (credit, draw, play, install incl. trashing programs for MU / uniqueness / console limit / trojan
  hosting, advance, score, rez, trash resource, purge, run, remove tag, end turn, concede), runs (initiation
  window, approach ice with rez window, encounter with break/pump/bioroid-break/paid abilities, subroutine
  resolution, pass ice, movement window with jack out, approach server window, success, breach, access of HQ
  (random), R&D (top cards), Archives (facedown turned faceup), remotes, root upgrades, steal, pay-to-trash,
  access abilities, run end triggers), damage (net/meat/core, random discards via `ShuffleTake`, flatline,
  prevention window hook), tags, bad publicity, traces (corp boosts then runner boosts), psi games (sequential
  blind bids, hidden only by the UI), hosting, purge, counters, win conditions (7 points, flatline, R&D empty,
  hand size below zero, concede), manual adjustment menu on every re-askable prompt.
* Timing structure: paid ability windows at turn start (before and after "when your turn begins" triggers, the
  latter only if any fired), between actions for both players (non-blocking `MultiAsk` for the opponent), after the
  discard phase, at run initiation, approach ice (rez), encounter (Corp side), movement (jack out), approach server;
  windows auto-skip when a player has nothing usable or already declined the same options; scoring is allowed in
  the Corp's own turn windows only. Accessed cards are revealed; R&D accesses in order with root cards accessible
  at any point; Archives is turned faceup as a set and accessed completely; steal/score move the agenda, check the
  win, then resolve "when scored/stolen" abilities; trashing a host trashes hosted cards with their own events;
  memory is re-checked when Runner cards leave play (forced program trashing); unique Corp cards trash other
  rezzed copies on rez; hand size enforced with a discard prompt (Runner flatlined below zero).
* Ability framework (`abilities.scala`, documented in `ENGINE.md`).
* All 77 System Gateway cards (`cards-sg.scala`), including the beginner identities (The Catalyst, The Syndicate
  have no abilities).
* Preset decks (`decks.scala`): Weyland, Jinteki, Haas-Bioroid, NBN (Corp); Anarch, Criminal, Shaper (Runner),
  all System Gateway only and legal; decklist parser (`3x Title`, `3 Title`, `Title x3`, identity line, section
  headers, influence dots, summary lines; unicode/diacritic-insensitive title matching) and validator
  (side, one identity, min size, deck limits, influence, agenda points).
* UI (`ui.scala`, `styles.scala`): Corp and Runner board panes with stat badges (credits, clicks, agenda points,
  tags, bad publicity, link, MU, hand size), servers as boxes with the ice stack (rotated, outermost on top) above
  the root, a "Runner" marker and orange outline showing the attacked server and the current ice position,
  unrezzed cards dimmed with an "unrezzed" tag for the Corp (backs for the Runner), facedown Archives counted,
  counters as badges (adv/virus/credit/power/strength), hosted cards under their host, rig grouped by type,
  scored/stolen agendas, accessed cards shown during a breach, the viewer's hand as images. Clicking a card or a
  server performs the single matching action of the current prompt, or opens a menu of the matching actions
  (install destinations, break/pump, rez, advance, score, select toggles...), or opens the zoom overlay; cards with
  available actions are outlined. Hovering a card shows a large preview with its text and state. Deck selection
  is a gallery of presets with identity images on the board plus a decklist import overlay with Check/Import and
  readable error messages. Three layouts (boards left + log/actions right, wide, single column) for common
  resolutions. Hidden information is hidden by the viewer (`lastSelf`): R&D order never shown, unrezzed/facedown
  cards as backs, opponent's hand as backs.
* Bots: `Random` (uniform over non-manual choices) and `Basic` (weighted heuristics: scores, steals, breaks, economy).
* JVM harness `host.scala` (excluded from the JS build): bot-vs-bot games with log dumps.

## Card sets

All cards of eleven sets have an implementation object (695 cards). Per-set details, simplifications and the list of
generic mechanics each set introduced are in the set status files; the engine side of the merge is described in
`ENGINE.md` section 8.

| Set | Cards | Implemented | Simplified / approximated | Files | Details |
|---|---|---|---|---|---|
| System Gateway (sg) | 77 | 77 | AMAZE Amusements, Bran 1.0, Tao's swap | `cards-sg.scala` | this file |
| Elevation (elev) | 82 | 82 | 11 (Bling, Detente, Open Market, Azimat, Public Access Plaza, IP Enforcement, The Zwicky Group, Plutus, Biawak, Petty Cash, Mahkota Langit Grid) | `cards-elev.scala`, `rules-ext-startup.scala` | `STATUS-vp-elev.md` |
| Vantage Point (vp) | 66 | 66 | 14 (Corsair, Lampades, Hackerspace, Stick and Poke, Baker, Aircheck, Word on the Street, Perfect Recall, Melies U, The Red Room, Hype Machine, Myoshu, Reanimation Protocol, Flagship) | `cards-vp.scala`, `rules-ext-startup.scala` | `STATUS-vp-elev.md` |
| System Update 2021 (su21) | 82 | 82 | 12 (Egret, En Passant, Daily Business Show, Architects of Tomorrow, Sneakdoor Beta, Ayla, Atman, Oaktown Renovation, Corporate Town / Archer, Marilyn Campaign, Reina Roja) | `cards-su21.scala`, `rules-ext-su21.scala` | `STATUS-su21-ms.md` |
| Midnight Sun (ms) | 65 | 65 | 12 (Deep Dive, Into the Depths, Steelskin Scarring, Ob Superheavy Logistics, Azef Protocol, Light the Fire!, Mitosis, Anemone, Hakarl 1.0, Trieste, Mystic Maemi, ...) | `cards-ms.scala`, `rules-ext-su21.scala` | `STATUS-su21-ms.md` |
| Uprising Booster Pack (urbp) | 7 | 7 | 3 (La Costa Grid, Cayambe Grid, Megaprix Qualifier) | `cards-ms.scala` | `STATUS-su21-ms.md` |
| Parhelion (ph) | 63 | 63 | ~22 (Matryoshka, Asmund Pudlat, Nanuq, Hostile Architecture, Anvil, Klevetnik, Issuaq Adaptics, Superdeep Borehole, Bloop, ...) | `cards-ph.scala`, `rules-ext-ph-tai.scala` | `STATUS-ph-tai.md` |
| The Automata Initiative (tai) | 65 | 65 | ~22 (Urban Art Vernissage, Bahia Bands, Daniela Jorge Inacio, Tucana, Banner, Wage Workers, Front Company, A Teia, AirbladeX, Starlit Knight, ...) | `cards-tai.scala`, `rules-ext-ph-tai.scala` | `STATUS-ph-tai.md` |
| Rebellion Without Rehearsal (rwr) | 65 | 65 | 7 (Sebastiao Souza Pessoa, Boi-tata, Manuel Lattes de Moura, Cataloguer, "Pretty" Mary, Nuvem SA, Cupellation) | `cards-rwr.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |
| Downfall (df) | 65 | 65 | 6 (Climactic Showdown, Trickster Taka, In the Groove, Direct Access, Whistleblower, MirrorMorph) | `cards-df.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |
| Uprising (ur) | 58 | 58 | 6 (Paladin Poemu, Mantle, Vaporframe Fabricator, GameNET, Earth Station, stealth restrictions); 7 ur codes are not in the card database | `cards-ur.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |

Preset decks (`Decks.presets`, 48): 7 System Gateway starters, 15 su21/ms decks (decks.scala), 8 Startup decks
(`StartupDecks`, cards-vp.scala), 8 ph/tai decks (`decks-ph-tai.scala`), 10 rwr/df/ur decks (`decks-rwr-df-ur.scala`); all
validate.

### Merge of the four set branches (unification)

The sets were ported in parallel on top of the first engine commit and merged into the engine update one at a time.
Mechanics implemented more than once were reduced to a single definition (see ENGINE.md section 8 for the surviving
names and files); the branch-specific traits were renamed `StartupRules`, `SU21Rules`, `PHTAIRules`, `RWRRules` and
their `performExt` / `extTriggers` / purpose resolvers chained. Engine-level changes made by several branches
(self cost modifiers for inactive cards, the "re-ask before score / rez / trash / install hooks" ordering, `TagsRemoved`
on tag removal, `IceBypassed` on bypass, `lastScoredAdvancement`) exist once; the ph/tai interception of
`ScoreAction` / `RezAction` / `TrashResourceAction` / `RunnerInstallAction` was dropped in favour of the engine's version
(which also handles the paid ability windows). Parameterless purposes and actions that can be embedded in user actions
were turned into `case object`s so that the journal round trip works (the branches' harnesses had not checked this for
them). The `isActive` check of a hosted card reads the host's `disablesHosted` from the raw implementation (the
`extImpl` wrapper made it recursive).

Known gaps carried over from the branches: bad publicity credits are a permanent gain instead of run-only credits;
"remote server only" / "limit 1 region per server" install restrictions are not enforced; stealth-credit restrictions
are relaxed (hosted credits are usable during runs). (The UI gap for cards hosted without being installed and for
cards set aside on identities was closed in the QA pass, see "UI additions of the QA pass".)

### UI additions of the QA pass

* Cards hosted without being installed (`StashZone`: Bling, Madani, Detente, Read-Write Share, Asmund Pudlat,
  Matryoshka's copies, ...) are drawn under their host like installed hosted cards, tagged "not installed" (or dimmed
  when facedown); the opponent sees card backs unless the card is faceup / seen. Cards set aside on an identity (Ayla)
  are drawn under the identity, tagged "set aside", and are visible to their owner only. Hover text says "hosted on X
  (not installed)".
* Every zone has a place on the board: removed-from-game piles (per side, count + overlay listing the visible cards),
  the play area (operations / events that stay active: run events with their hosted credits, lockdowns), "installed"
  rig section for Runner cards that are neither program, hardware nor resource.
* Markers: the mark (Runner header + "(mark)" next to the server name), the threat level (Corp header, only while a
  card in play mentions Threat), allotted clicks for next turn (Wage Workers...), any extra counter kind on a card
  (bad pub, agenda, ...), bad publicity hosted on cards, per-card lingering flags (disabled, cannot break, net damage
  subs, ...) as tags, game-level lingering flags as an "in effect:" line, run restrictions/bonuses in the run line
  ("cannot steal or trash", "no access", "+1 access (R&D)", ...). Header stats are singular/plural aware.
* Card images: a printing without an image (the two unreleased identities 014001 The Collective and 014002 Chronos
  Protocol have none on NetrunnerDB; `fetch-assets.py` now also tries the v1 hosts and the v2/v3 APIs) or whose image
  failed to load is drawn as the card back with the title on top (board, hand, zoom, menus, deck gallery). Card
  images have a fixed height so the layout does not jump while images load lazily.
* Layout robustness: ice is stacked vertically above the root (outermost on top); each board shrinks its font size
  (all card sizes are in ex) until it fits its pane, so very long hands, many remotes and 10+ ice per server stay on
  screen (tested with 24 cards in HQ, 8 remotes and 9 ice on HQ). Multi-value `margin`/`padding` styles were silently
  dropped by the HRF CSS generator; they are now written per side.
* Game end: the run line / attacked-server marker / "turn N" badge are hidden once the game is over. "Save Replay"
  (self-contained HTML) is not offered: the framework embeds every declared image asset as a data URL and Netrunner
  declares ~2,000 lazily loaded card images (62 MB), so the save crashed with "url not loaded". Online games show
  "This game is saved on the server: open any of its links to replay it"; local games offer "Save Game Online" (as Coup
  does) and "Play Again".

## Simplifications / known gaps

* Paid ability windows follow the NSG structure (see ENGINE.md): start of turn (before and after "when your turn
  begins"), between actions (the opponent's window is non-blocking and shown next to the active player's prompt),
  after discarding, run initiation, approach ice, encounter (Corp side), after passing ice (movement), approach
  server. Players who have nothing usable are skipped (greyed-out options such as an Abagnale bypass outside an
  encounter do not open a window); a player who declined a set of options is not asked again
  for the same options until something happens. In a hotseat (both humans in one browser) only the active
  player's prompt is visible, so the off-turn window is effectively unavailable there (use online play).
* AMAZE Amusements only fires while installed and rezzed (not the "trashed during the run" case).
* Brân 1.0 installs the chosen ice directly inside itself (jinteki installs at Brân's index).
* Tāo's ice swap keeps the run position by index.
* Traces and psi games are exercised by later-set cards (Winchester, Punitive Counterstrike, Konjin, Letheia Nisei,
  See How They Run) and were played in the QA online games (see Tested).
* Prevention: net damage (AirbladeX, Prāna Condenser), end-the-run (Lucky Charm), trace strength (Flip Switch) and core
  damage (Mr. Hendrik) preventions exist; no card of the eleven implemented sets avoids or prevents tags, so the
  "tag" prevention window is reachable only by future cards.
* Bot play is weak; bots never use the manual menu or concede; bots pass paid ability windows most of the time.
* Hidden information is hidden by the UI only; the Manual menu lets the Corp move the top cards of R&D. A viewer
  without a seat (the spectator link, bot-vs-bot games) sees everything, including both hands and facedown cards
  (the game creator receives all links anyway). The shared log never names a Corp card the Runner has not seen
  (Hansei Review / sabotage / random trashes from HQ are logged as "a card").
* Online play: both clients (and the spectator) race to append framework milestones and shuffle results; the losers
  get `HTTP 409` (players) / `HTTP 500` (spectator, read-only) in the console. This is the HRF framework's design and
  harmless (the journal stays consistent, replays are identical).
* Cards accessed while installed and left in place are not remembered as "seen" by the Runner's UI (they are drawn
  as card backs again after the run).
* The framework logs `NetworkError` exceptions in headless Chrome for every game (also Coup): they come from the
  shell (font loading), not from the plugin.
* `cards-data*.scala` and `webp2/netrunner/images` are produced by the asset agent; `question-mark.png`,
  `external-link.png` and the `webp2/root/...` images used by the HRF shell are placeholders created for local
  testing only (webp2 is git-ignored).

## How to test

* Compile/JS: `cd haunt-roll-fail/haunt-roll-fail && sbt -batch fastOptJS`.
* Server: from `haunt-roll-fail/good-game`: `sbt "run run ../good-game-database ../haunt-roll-fail http://localhost:7070 http://localhost:7070/hrf/ 7070"`
  (`run create ...` once), then open `http://localhost:7070/play/netrunner`, Local Game, pick Corp and Runner,
  set one seat to a bot, Start. Choose a preset deck or paste a NetrunnerDB decklist.
  Add `?speed=20` to the URL to make bot-vs-bot games fast.
* Headless bot-vs-bot stress test: create an sbt project whose `unmanagedSources` are the top-level `*.scala` files of
  `haunt-roll-fail/haunt-roll-fail` (minus the Scala.js-only ones listed in `host.xsbt`, plus `info.scala`) and the
  `netrunner`, `coup`, `arcs` directories, with the JVM (`%%`) versions of the dependencies from `common.sbt` and
  `scala-parallel-collections`; then `HRF_HOST_OUT=out sbt "runMain netrunner.Host 100 logs"` writes `out/game-N.log`
  and `out/game-error-N.txt` for any exception, empty ask or over-long game.

## Tested

* Online play by URL sharing (good-game server on port 7070, headless Chrome driven through the DevTools protocol,
  two player tabs): `http://localhost:7070/play/netrunner` > Play Online > New Online Game > Corp + Runner > Start
  Setup > player names > Start; the Corp link and the Runner link opened in separate tabs, each asked for a name
  (no account); deck selection (presets from the board gallery and from the action pane, decklist import with
  error messages for unknown cards / too few cards and a valid Tao decklist), mulligan and keep, Corp turn (install
  ice by clicking the hand card and choosing the server from the menu, install assets/agendas, click credit, rez an
  asset in the "no clicks" window, use Regolith's click ability by clicking the card, play operations, Hansei
  Review's trash prompt, Spin Doctor's remove-from-game ability with the select prompt), Runner turn (install
  programs/hardware/trojans hosted on ice by clicking the hand card, Sure Gamble, Jailbreak with the server
  choice, runs by clicking the server name), paid ability windows (run initiated, approach ice with rez, encounter,
  movement with jack out, approach server, end-of-turn discard window, turn-start window with scoring, the
  non-blocking between-actions window in which the Corp rezzed Urtica Cipher during the Runner's turn), breaking
  Whitespace with Unity, Tithe subroutines (net damage with random discard, Corp gains), Whitespace ending the run,
  HQ/R&D/Archives/remote accesses (random HQ card, R&D in order, Archives turned faceup and accessed as a set,
  pay-to-trash Spin Doctor and Regolith, agenda steals with Zahya's and Tao's triggers), scoring Offworld Office
  from the turn-start window (gain 7), Restoring Humanity's end-of-turn credit, Tranquilizer derezzing Whitespace,
  Funhouse encounter tag with Reality Plus, discard phase (9 cards, choose 4 by clicking the hand), manual
  adjustments (advancement counters, tags, core damage), remove tag, undo of a run ("Undo to here" + Confirm,
  reflected on both tabs), concede with the game-over screen on both tabs, reloading a player link to replay the
  journal. No plugin exceptions in either console.
* Hover zoom, board menus, card outlines and layouts checked at 1440x900, 1280x720, 1920x1080 and 1024x768.
* Browser bot-vs-bot local games on the final build (`?speed=20`, Basic vs Basic and Basic vs Random) played to
  completion (flatline on turn 7; 8 stolen points) with the new windows and no console exceptions.
* Earlier (previous iteration): JVM harness with hundreds of bot-vs-bot games (see host.scala).

### Tested after the merge of the four set branches

* `sbt -batch compile` and `sbt -batch fastOptJS` (the linker needs `sbt -J-Xmx8g` now) pass.
* JVM harness (scratchpad `mergehost`, `MergeHost`: preset decks of all sets chosen uniformly at random, every offered and
  chosen action round-tripped through `Serialize`): 300 games over all 48 presets + 100 games each restricted to the
  Startup, ph/tai, rwr/df/ur and su21/ms presets + 60 mixed games: 760 games, 0 exceptions, 0 empty asks, 0 serialization
  mismatches, no over-long games; 668 of the 695 cards appear in the logs of the mixed run (the rest are identities not
  used by any preset and a few rarely played cards); the per-set runs mention 81/82 su21, 64/65 ms, 7/7 urbp, 61/63 ph,
  64/65 tai, 75/82 elev, 63/66 vp and all rwr/df/ur cards.
* The 40 scripted checks of the Startup sets (`ScriptTest`, scratchpad `mergehost/src`) pass against the merged engine
  (the test's game builder now sets `current = ActionPhaseAction(Corp)` because scoring re-asks the current prompt).
* Browser (headless Chrome through the DevTools protocol, good-game server on port 7070, final build): a bot-vs-bot game
  (`?speed=20`) with Weyland Ob (ms) against Shaper Ayla (su21/ms) played to the Corp's 7-point win on turn 12; a local game
  as the human Runner with the Elevation Anarch (Ryo) preset against the Jinteki PE (su21/ms) bot: deck gallery, mulligan,
  click for credit, install Lampades from the hand (power counters shown), run on the remote, steal Nisei MK II with the
  Personal Evolution net damage, draw, end of turn with the discard window, the Corp bot's next turn with the Runner's
  non-blocking "No action" window. No plugin exceptions in the console (only the shell's `NetworkError` font-loading
  entries and 404s for card images that are not in the local asset cache).

### Tested in the QA pass (2026-10-14, final build)

* Online game 1 "Rez at Trace" (good-game on port 7070, headless Chrome over the DevTools protocol, one tab per link,
  plus the spectator tab): Play Online > New Online Game > Corp + Runner > Start Setup > Start gave three links
  (Corp `wctbjntlvxzuhxfl`, Runner `povjudvowfaczejm`, spectator `dyzdrravjkzwfmbr`); each player link only asked for
  a name (no account). Both decks were NetrunnerDB decklist imports (Corp "Jinteki: Restoring Humanity" with Brân,
  Ansel, Winchester, Konjin, Karunā, 41 cards, 13/15 influence; Runner "Zahya Sadeghi" with Carmen, Abagnale,
  AirbladeX, Lucky Charm, Flip Switch, Read-Write Share, 45 cards, 7/15 influence), including the error messages for
  an unknown card and a 4th copy. Played 12 turns from both seats to the Runner's 7-point win (stole See How They Run
  twice, Offworld Office, Superconducting Hub; the Corp scored Offworld Office): mulligan, Hedge Fund / Hansei Review
  (trashed card now facedown and logged as "a card"), installs by clicking hand cards, rez windows, Karunā net damage
  with random discards and jack out, Brân click-breaks (lose [click]) and its "install ice from HQ" sub (Tithe
  installed behind it), Abagnale break and trash-to-bypass, Carmen pump + break, Overclock run credits paying a
  trash cost, pay-to-trash Regolith, Inside Job bypass, Palisade ETR prevented by Lucky Charm (removed-from-game pile
  shown), AirbladeX preventing 2 net damage one at a time, Konjin psi game (Corp bid hidden from the Runner until both
  bid), Security Testing replacing breaches, Archives breach turning facedown cards faceup, R&D and HQ accesses,
  Zahya's credits, Read-Write Share hosting 4 facedown cards (drawn under it, card backs for the Corp), the
  scoring of Offworld Office from a window, undo from the Runner ("Undo to here" undid 2 actions) and from the Corp,
  reloading both player links and the spectator link mid-game and after the game (journal replay with newer builds),
  the game-over screen on all three tabs.
* Online game 2 "Purge against Steal" (Corp `gzxtzroimjfbuesj`, Runner `qouajghxfmhzkqzw`, spectator
  `iinsmcycoqqsiqmf`, same decklists, started by the previous QA iteration on an older build and replayed on the
  new one): traces from Winchester (Corp boosting, the Runner paying 4 for link 4 to beat a strength 4 trace, a
  successful ETR trace, a hardware-trash trace with no hardware), Konjin with differing bids and the Corp choosing
  Ansel for the forced encounter, Ansel click-breaks and Carmen breaking Ansel, Ansel's "install a card from HQ or
  Archives" (Letheia Nisei installed in the attacked server), "cannot steal or trash" shown in the run line, Urtica
  Cipher's net damage on access, Send a Message scored with its free rez, Red Team, Pennyshaver, Legwork's extra HQ
  accesses, Dirty Laundry; ended by the Corp conceding (turn 14, game over on both tabs).
* Bot games in the browser (`?speed=20`, Local Game, Basic vs Basic, "Play Again" chained): 20 games to completion
  (wins by agenda points, flatline and R&D decking) across random presets including Ayla, Quetzal, Ken, Sable,
  Hoshiko, Sebastião, NEH, A Teia, Thule, GameNET, Thunderbolt, Pravdivost; no console exception.
* Local human-vs-bot games: Ayla's 4 set-aside cards drawn under the identity; an imported "The Collective" deck
  (identity without an image) drawn as card back + title; a stress board built with the Manual menu (24 cards in HQ,
  8 remotes, 9 ice on HQ) fits the pane; "Save Game Online" turned the local game into an online one (lobby
  `tgzepqetyxojwlzy`, Corp link `jygbtodwfrmfjqeg`) which loaded with the full board.
* JVM harness (scratchpad `mergehost`, all 48 presets, every action round-tripped through `Serialize`): 600 games,
  0 errors, after fixing the Plutus rez loop it had exposed ("game too long" in 1 of ~300 games before).
* Console noise that is not the plugin's: `HTTP 409` / `HTTP 500` on `/append` (framework milestone races, see
  Simplifications).

## Known bugs

* None reproduced at the time of writing; see "Simplifications" above.
