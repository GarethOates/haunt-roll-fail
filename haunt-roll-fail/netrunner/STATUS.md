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
* Ability framework (`abilities.scala`, documented in `ENGINE.md`).
* All 77 System Gateway cards (`cards-sg.scala`), including the beginner identities (The Catalyst, The Syndicate
  have no abilities).
* Preset decks (`decks.scala`): Weyland, Jinteki, Haas-Bioroid, NBN (Corp); Anarch, Criminal, Shaper (Runner),
  all System Gateway only and legal; decklist parser (`3x Title`, `3 Title`, `Title x3`, identity line, section
  headers, influence dots, summary lines; unicode/diacritic-insensitive title matching) and validator
  (side, one identity, min size, deck limits, influence, agenda points).
* UI (`ui.scala`): Corp and Runner board panes (identity, stats, servers with ice (rotated) and root, HQ/R&D/Archives
  piles, score areas, rig by type, counters, run status, viewer's hand as images / opponent's as backs), click on a
  card for a zoom overlay with the printed text, decklist import overlay (textarea), standard action/log/undo/settings panes.
  Hidden information is hidden by the viewer (`lastSelf`): R&D order never shown, unrezzed/facedown cards as backs.
* Bots: `Random` (uniform over non-manual choices) and `Basic` (weighted heuristics: scores, steals, breaks, economy).
* JVM harness `host.scala` (excluded from the JS build): bot-vs-bot games with log dumps.

## Simplifications / known gaps

* No paid-ability window between the opponent's actions (the Corp can rez non-ice only in run windows and at the
  start of its own turn, not after each Runner click); windows exist at turn begin, run initiation, approach ice,
  after passing ice (movement), approach server, plus the encounter prompt.
* Access order in a remote/Archives is chosen by the Runner; HQ root upgrades are accessed before the random HQ cards.
* Archives are not pseudo-shuffled when turned faceup.
* AMAZE Amusements only fires while installed and rezzed (not the "trashed during the run" case).
* Brân 1.0 installs the chosen ice directly inside itself (jinteki installs at Brân's index).
* Tāo's ice swap keeps the run position by index.
* Traces/psi games are implemented but no System Gateway card uses them (untested in play).
* The prevention window exists (`CardImpl.preventions`) but no System Gateway card prevents damage/tags.
* Bot play is weak; bots never use the manual menu or concede.
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

* Browser (headless Chrome, human Runner vs Basic Corp bot): deck import from a NetrunnerDB list, mulligan,
  install, run R&D with access, Zahya trigger, run HQ with rez, encounter (pump offered, no break for wrong
  subtype), subroutines (net damage with random discard, corp gains), pass ice, movement window, approach, HQ
  random access, end of turn, Corp bot turn (install, advance, Built to Last trigger), steal from a remote.
* Browser (human Corp vs Basic Runner bot): manual credit adjustment, install agenda in a new remote, advance (Built to
  Last trigger), install ice protecting the remote, rez window when the bot approaches (Palisade rezzed, strength shown
  as 4 in a remote, ETR fires), manual advancement counters via the card menu, scoring (3 points, on-score hook).
* Browser bot-vs-bot game (`?speed=20`, Haas-Bioroid vs Criminal) played to completion (Corp flatlined the Runner on
  turn 4) with no console exceptions.
* JVM harness: hundreds of bot-vs-bot games without errors covering scoring, stealing, trashing on access, tags,
  purge, breaking, pumping, jacking out, flatline, deck-out, Funhouse, Manegarm, Ping, AMAZE, Clearinghouse,
  Carnivore, Botulus, Cookbook, Wildcat Strike, Ansel/Brân bioroid breaks, Sprint, Nico Campaign, Tranquilizer derez,
  Tāo swaps, identities.

## Known bugs

* None reproduced at the time of writing; see "Simplifications" above.
