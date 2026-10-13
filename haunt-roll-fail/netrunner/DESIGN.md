# Netrunner for Haunt Roll Fail — design contract

This directory is a game plugin for the HRF framework (Scala 2.13 / Scala.js), package `netrunner`.
Reference implementation being ported: `/Users/gareth/Developer/HRF/netrunner-src` (Clojure, jinteki.net):
rules engine in `src/clj/game/core/*.clj`, card implementations in `src/clj/game/cards/*.clj`,
card tests in `test/clj/game/cards/*.clj` (they document expected behaviour precisely).
Official rules: Null Signal Games Comprehensive Rules (NSG era). Keep the HRF plugin shape of `../coup/` (small) and `../arcs/` (large).

## Non-negotiable HRF facts (read `../base.scala`, `../meta.scala`, `../serialize.scala`, `../grey.scala` GUI trait, `../coup/*.scala`)

1. A game is a replayable journal of `ExternalAction`s. Every client replays the same actions, so `Game` must be fully
   deterministic. All randomness goes through `Continue`s: `Shuffle[T](list, ShuffledAction)`, `Random`, `Roll`.
   The framework executes them and records the resulting `ShuffledAction`/`RandomAction` in the journal.
2. `Game.performInternal(action, soft) : Continue` is a state machine: perform the action, mutate state, return the next
   `Continue`: `Ask(faction, actions)` (wait for a choice), `MultiAsk($(Ask(Corp, ..), Ask(Runner, ..)))` (both may act — use for
   paid-ability windows / simultaneous prompts), `Force(forcedAction)` (implicit via `actionToForce`), `Milestone(then)` (undo checkpoint,
   `then` must be non-soft), `Shuffle(...)`, `GameOver(winners, message, infos)`, `DelayedContinue(ms, c)`.
3. **Never return `Ask(f, Nil)`** — an empty ask is an error. Every Ask needs at least one selectable option.
4. **Soft actions.** A `UserAction` marked `with Soft` is a *menu*: performing it must NOT mutate game state; it only returns an
   `Ask` (typically `.cancel`). Non-soft user actions mutate state and must NOT return an `Ask` that contains `CancelAction`
   (framework error "cancel on non-soft external action"). Validation of a submitted action works by *exploding* soft actions
   (re-performing them to enumerate leaf choices), so soft actions must be pure and cheap. Multi-step choices: soft menu ->
   soft submenu -> non-soft leaf action.
5. All action case classes/objects must be **top-level in package `netrunner`** (not nested in Game) and extend `Record`-based
   HRF traits (`BaseAction`, `ForcedAction`, `BaseInfo`, ...). They are serialized by reflection: `netrunner.Serialize` with
   `prefix = "netrunner."`. Supported field types: Int, Boolean, Double, String, the faction type, Option, List, Map, and other
   `Record` case classes/objects from this package. Case object names must match `[A-Z][A-Za-z0-9]*`.
   **Strings inside actions must not contain `"`, `\`, newlines, or non-ASCII** (server filters to ASCII). Use card *codes*, never titles, in actions.
6. `UserAction`s need `question`/`option` elems (use `BaseAction(questionParts...)(optionParts...)`), `self : F` via `FactionAction`.
   `Info` actions are display-only; `Hidden` ones are not rendered. `HiddenInfo`/`HiddenChoice` exist.
   `a.!(cond, "reason")` marks a choice unavailable (greyed) with a reason.
7. `Game.info(waiting, self, actions) : $[Info]` returns extra Info items for the viewer `self` (Coup uses it to show the private hand).
8. Logging: `log(...)` inside performInternal; descriptions auto-convert `Named with Styling`, `Elementary`, Strings, Ints, Elems.
9. Validation: when a client submits an action the framework checks it against the current `Ask` (direct membership, exploded soft
   actions, or `SelfValidate.validate`). For free-form input (deck import) put a `Hidden with SelfValidate` marker action in the Ask
   whose `validate(a)` accepts the matching `ImportDeckAction`.
10. UI: `netrunner.UI extends BaseUI`, class `UI(...) extends GUI` (grey.scala). Panes are declared with `newPane(name, ...)` and placed
    by a `Layouter` (see coup/ui.scala, arcs/ui.scala). Framework calls `updateStatus()` after every action. The UI receives the
    viewer's faction in `ask(faction, actions, then)` / `wait(self, ...)` (`lastSelf`); `then(action)` submits an action.
    The full game state is on every client — hidden information is hidden **by the UI only** (same as Coup/Root hands).
11. Assets: `Meta.assets : $[ConditionalAssetsList]`. Every `ImageAsset(name)` resolves to `webp2/netrunner/images/<name>.webp`
    under the CDN root (served by good-game from `haunt-roll-fail/haunt-roll-fail/`). Asset names are lowercase.
    `Laziness.OnDemand` assets are not preloaded; `Image("name")` elems load them lazily when rendered (html.scala:244).
12. Registration: add `netrunner.Meta -> netrunner.UI` to `metaUIs` in `../hrf.scala`, and
    `Compile / unmanagedSourceDirectories += baseDirectory.value / "netrunner"` to `../common.sbt`.
13. Build: `cd /Users/gareth/Developer/HRF/haunt-roll-fail/haunt-roll-fail && sbt -batch fastOptJS` (~1 min; `sbt -batch compile` to typecheck).
    `maxErrors := 5` is set — fix errors in batches. Run server (for manual testing): from `/Users/gareth/Developer/HRF/haunt-roll-fail/good-game`
    `sbt "run run ../good-game-database ../haunt-roll-fail http://localhost:7070 http://localhost:7070/hrf/ 7070"` then open http://localhost:7070/play/netrunner

## Factions / players

```scala
trait Faction extends BasePlayer with Named with Styling with Record { def name = toString ; def style = name.toLowerCase }
case object Corp extends Faction
case object Runner extends Faction
```
Exactly 2 players, seating order irrelevant (`insignificantSeating = true`). `writeFaction(f) = f.name`.

## Card data model (`cards.scala`, generated data in `cards-data-*.scala`)

```scala
case class CardData(
  code : String,            // NRDB code, e.g. "30002" — THE identity of a printing; asset name == code
  title : String,
  side : String,            // "Corp" | "Runner"
  faction : String,         // "Anarch" | "Criminal" | "Shaper" | "Haas-Bioroid" | "Jinteki" | "NBN" | "Weyland Consortium" | "Neutral" | "Apex" | "Adam" | "Sunny Lebeau"
  kind : String,            // "Identity" | "Agenda" | "Asset" | "Upgrade" | "Operation" | "ICE" | "Event" | "Hardware" | "Program" | "Resource"
  subtypes : $[String],
  cost : |[Int],            // play/install/rez cost (None = no cost printed, e.g. identities/agendas)
  strength : |[Int],
  advancementCost : |[Int],
  agendaPoints : |[Int],
  memory : |[Int],
  trashCost : |[Int],
  influence : |[Int],       // factioncost
  baseLink : |[Int],
  minDeckSize : |[Int],
  influenceLimit : |[Int],
  unique : Boolean,
  deckLimit : Int,
  text : String,            // NRDB text with [credit] [click] etc. tokens and <ul><li> html
  setCode : String,         // "sg", "su21", ...
  setName : String,
  rotated : Boolean,
  legal : $[String]         // formats where legal: "standard", "startup", "eternal", "system-gateway", "core", "throwback"
)
object Cards {
  val all : $[CardData]                 // every English card (~2065)
  def byCode : Map[String, CardData]
  def byTitle : Map[String, CardData]   // latest printing per title
  def apply(code : String) : CardData
  val sets : $[(String, String)]        // (setCode, setName) in release order
}
case class Card(code : String, n : Int) extends Record { def data = Cards(code) ; def title = data.title } // physical copy n of a printing
```

## Game structure (engine) — files `game.scala`, `rules-*.scala`, `abilities.scala`, `cards-*.scala`

- Zones per player, tracked in Game state: Corp — HQ (hand), R&D (deck, ordered), Archives (faceup/facedown), servers
  (central: HQ/R&D/Archives; remotes numbered) each with ice list (outermost last) and root (installed cards); score area;
  Runner — Grip, Stack, Heap, rig (programs/hardware/resources), score area; identity cards; counters (credits, clicks, tags,
  bad publicity, link, MU, agenda points), per-card state (rezzed, faceup, advancement/virus/power/credit counters, hosted cards, strength modifiers).
- Turn structure, basic actions, runs (approach ice / rez window / encounter / break subs / pass; approach server; access
  of HQ/R&D/Archives/remotes incl. upgrades; steal / trash-on-access / expose), paid-ability windows, scoring, damage,
  tags/bad pub, traces, psi games, hosting, purge, win conditions (7 agenda points, flatline, mill, concede), mulligan.
- Card abilities framework: constant effects, triggered events (when-X), paid abilities (click/credit/trash costs), ICE
  subroutines (with break-by-icebreaker interactions), identities. Every card implemented as a Scala object registered by code.
  Cards without an implementation are still fully playable manually (play/install/rez/advance/trash/access all work generically) and
  show their rules text; players get "manual" adjustment actions (credits, clicks, tags, counters, draw, trash, move) at all times.
- Deck selection at game start per player: preset decks (System Gateway starters) or import by pasting an NRDB-exported decklist
  (UI parses "3x Card Name" / "3 Card Name" / "Card Name x3" into codes; validates legality: side, min size, influence, agenda points, deck limits).
- Document the ability API in `ENGINE.md` so card-implementation agents can port cards from the Clojure sources.

## Assets (`cards-assets.scala`, `tools/fetch-assets.py`)

- Card images: `https://card-images.netrunnerdb.com/v2/large/<code>.jpg` -> `webp2/netrunner/images/<code>.webp` (keep native size, quality ~82).
  Cache originals in `netrunner/assets-cache/` (git-ignored). Script must be resumable, rate-limited (~5 req/s), run with `python3 -I`.
- Card backs: `/Users/gareth/Developer/HRF/netrunner-src/resources/public/img/card-backs/{corp,runner}/nsg.png` -> `corp-back.webp`, `runner-back.webp`.
  Faction logos from `.../img/factions/*.png` -> `faction-<name>.webp`.
- `webp2/` is git-ignored (same as other HRF games: assets live beside the build, not in git); the script regenerates them.
