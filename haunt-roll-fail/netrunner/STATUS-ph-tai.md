# Parhelion (ph) and The Automata Initiative (tai) — status

Files: `cards-ph.scala` (63 cards, `PHCards.all`), `cards-tai.scala` (65 cards, `TAICards.all`), generic mechanics in
`rules-ext-ph-tai.scala` (`trait ExtRules`, new effects / events / impl wrappers), preset decks in `decks-ph-tai.scala`
(`PHTAIDecks.presets`, 5 Corp + 3 Runner decks built around the two sets, all valid). Base class `PT(code) extends SG(code)`
adds a few helpers (`encounterIce`, `runOnThisServer`, `fullyBroke`, `runnerCanInstall`, `clickOrEtr`).

## Coverage

### Parhelion — 63/63 implemented (57 standard-legal + 6 eternal-only)

All cards are implemented. Identities without abilities: Nova Initiumia, Ampère (their deck-building restrictions are
validated by `DeckRules` (see `STATUS-fix-restrictions.md`)). Partial / simplified:

* **Matryoshka** (eternal): copies are hosted in a `StashZone`; the break ability costs 1 credit per subroutine and turns 1
  hosted copy facedown per use (fix-misc pass); the engine offers X = 1 (any subroutine) or X = all remaining, not an
  arbitrary subset of 2 or more. Copies are turned faceup at the start of the Runner's turn.
* **Asmund Pudlat**: hosted cards live in a `StashZone` (not installed, not displayed by the UI); the start-of-turn
  prompt lists them. They are trashed when Asmund leaves play.
* **Tsakhia "Bankhar" Gantulga** (eternal): implemented; the chosen server is kept as a turn flag and the first
  encounter with ice protecting it replaces the subroutines with "Do 1 net damage" via an impl wrapper.
* **Nanuq**: removed from the game instead of moving anywhere else (trash, returned to the grip, moved to the stack:
  FixRules), and when an agenda is scored/stolen.
* **Reprise**: "stole an agenda this turn" uses the new `stolen` register.
* **Spark of Inspiration**: the revealed cards are set aside (hosted on the event) and shuffled back (fix-misc pass).
* **Hostile Architecture / Yakov Avdakov**: "installed card trashed by the Runner" / "trashed from this server" are
  detected with a snapshot of the installed Corp cards taken at the previous event (`lastServerOf`, `wasInstalled`);
  "except during installation" uses the `installing` flag of the ext rules.
* **Anvil**: "cannot break this ice's printed subroutines" applies to the printed subroutines only (subroutines added by
  other cards can still be broken; fix-misc pass).
* **Unsmiling Tsarevna**: "cannot break more than 1 printed subroutine per encounter" greys out further break options
  once a subroutine is broken (also bioroid breaks).
* **Hafrún**: the chosen Runner card gets a run-scoped `cannot-break` flag (its `breaks` become empty).
* **Klevetnik**: the resource is blanked (`DisabledImpl`) until the Corp's next turn ends (half-turn scoped flag).
* **Hypoxia** / **Basilar Synthgland**: -1 / +1 allotted click (fix-misc pass: `ExtraClicksEffect`, `clicksPerTurn`).
* **Issuaq Adaptics**: every win check uses the reduced threshold (FixRules `checkWin`); "did not install or advance this turn"
  uses the turn flags `installed` / `advanced`.
* **Superdeep Borehole**: uses `badpub` counters (not rendered by the UI counters), Corp wins when it is empty.
* **Nightmare Archive**: -1 agenda point through `agendaPointsBonus` while in the Runner's score area.
* **Bloop**: the additional rez cost (derez another harmonic ice) is a mandatory choice resolved right after the rez
  (`canRez` is false without another rezzed harmonic ice).
* **Vera Ivanovna Shuyskaya**: the grip is revealed in the log, the Corp chooses a card to trash.
* **Simulation Reset / Hypoxia**: removed from the game after resolution.
* **WAKE Implant**: the number of counters to remove is chosen from a list (1..3).
* **Time Bomb**: the install restriction greys out the install option.
* **Info Bounty / Tunnel Vision**: use the new mark mechanic (random central at the start of the Runner's turn).

### The Automata Initiative — 65/65 implemented

Partial / simplified:

* **Daniela Jorge Inácio**: the steal cost is paid after the steal (2 random grip cards to the bottom of the stack), also with
  fewer than 2 cards in the grip; the upgrade can only be trashed on access with at least 2 cards in the grip; "stolen from this
  server" uses the zone the agenda was stolen from (fix-misc pass).
* **Tucana**: "scored or stolen from the root of this server" uses the zone the agenda left (fix-misc pass); the ice is installed
  and rezzed paying a total of 3 less (as jinteki). ("Remote server only" is enforced by the restrictions pass.)
* (Fixed in the fix-misc pass: "fully breaks" = every subroutine broken by that card (**Abaasy, Orca, Curupira** and the
  other programs using jinteki's `all-subs-broken-by-card?`); **Banner** only stops "end the run" from that barrier's
  subroutines (jack out and other ETR effects work); **Wage Workers** gives nothing after a terminal operation;
  **Oppo Research** ends the action phase through `EndActionPhaseEffect`.)
* **Front Company**: rezzable only during the Corp's turn; the first run of a turn cannot target a remote (run
  options greyed out); net damage when the first run on Archives begins while unprotected.
* **A Teia**: the remote limit greys out "new remote server" options; the second install ignores all costs; the
  second card gets a turn flag `cannot-score` honoured by the score options.
* **Mercury**: "did not break any subroutines during that run" via run flags (bioroid click-breaks count since the fix-misc
  pass).
* **Eru Ayase-Pessoa, Beatriz Friere Gonzalez**: the breach replacement rewrites the queued `BreachAction`.
* **AirbladeX**: the encounter ability and net damage preventions use the windows of `rules-ext-prevention.scala` (see
  `STATUS-fix-prevention.md`); abilities gained from ZATO City Grid are triggers and cannot be prevented.
* **Virtual Service Agent**: the breaker of the printed subroutine is recorded (fix-misc pass).
* **Starlit Knight**: X is fixed when the encounter starts and the extra subroutines last for the run (as jinteki; each
  encounter adds its own).
* **Stegodon MK IV**: icebreakers get -2 while any ice has been derezzed during the current run (as jinteki).
* **Pichação**: every [click] gained during a run this turn counts (fix-misc pass).
* **Chrysopoeian Skimming, Epiphany Analytica, Balanced Coverage, Federal Fundraising**: "look at" is an info
  prompt shown to that player only; Federal Fundraising arranges the top 3 cards with successive choices.
* **Oracle Thinktank**: the Corp's click ability is offered during its action phase while the agenda is in the
  Runner's score area.
* **Audrey v2**: the "+3 strength" pump with a trash-from-hand cost is a paid ability (the engine's `Pump` cannot
  ask for cards).
* **Slash and Burn Agriculture, Tree Line, Angelique Garza Correa** (expend): click abilities offered while the card
  is in HQ (`[click], 1[c]` plus trashing the card faceup as the first effect).
* **Jaguarundi, Attini, Pulse, M.I.C.**: "unless the Runner pays/spends" are Runner choices.
* **Vovô Ozetti, Adrian Seis**: optional move to another server at the end of the Corp's turn.
* **Lago Paranoá Shelter**: "installs a card in the root of a server" = first non-ice Corp install of the turn.

## New generic mechanics (`rules-ext-ph-tai.scala`)

* `trait ExtRules` mixed into `Game`: `threat(n)`, `halfTurn`, `mark : |[Server]`, lingering **flags**
  (`Flag(key, card, server, scope, until, expire)` with scopes `encounter`, `run`, `turn`, `halfturn`; `expire`
  effects resolve when the scope ends; `hasFlag`, `flagsOf`, `addFlag`, `encounterFlag`, `runFlag`, `turnFlag`),
  per-run bookkeeping (`encountersThisRun`, `subsBrokenThisRun`), registers `stolen`, `runner-trash-corp`,
  `runner-trash-installed`, turn flags `installed` / `advanced` per card, a snapshot of installed Corp cards per
  event (`lastServerOf`, `wasInstalled`), `installing`, `actionCounts`.
* `extImpl(c)`: `impl(c)` now returns a wrapper when a card is altered: `DisabledImpl` (blank card / ice keeping its
  printed subroutines; flag `disabled` or a hosted card with `disablesHost`), `NetDamageSubsImpl` (flag `net-subs`),
  `NoBreakImpl` (flag `cannot-break` on a Runner card). `DelegatingImpl` is a reusable full delegate.
* `extTriggers(e)` is consulted by `Game.trigger` before the card triggers (flag expiry, bookkeeping, `on-steal`
  persistent effects, trashing stashed cards).
* `performExt` (first in the `performInternal` chain) intercepts engine asks to apply generic restrictions
  (`fixAction`: `canRez`, remote limit, `cannot-score`, `canInstall`, `allowsRun`, break restrictions) and to add
  click abilities of cards outside the active zones (expendable cards in HQ, Corp agendas in the Runner's score
  area); it also fixes the ordering of `ScoreAction` / `RezAction` / `TrashResourceAction` / `RunnerInstallAction`
  (their follow-up ask is now queued before the effects, so on-score / on-rez / on-trash hooks resolve immediately —
  previously they resolved after the next click, also for System Gateway cards such as Offworld Office).
* New `CardImpl` hooks: `canRez`, `canInstall`, `allowsRun(server)`, `remoteLimit`, `disablesHost`,
  `onActionTaken(key, n)`.
* New events: `BypassedIce(ice)`, `MarkIdentified(server)`.
* New effects: `SabotageEffect(n)`, `ChargeEffect(f)`, `IdentifyMarkEffect`, `SetMarkEffect`, `AddFlagEffect`,
  `RemoveFlagEffect`, `BypassIceEffect(ice)` (bypass + event), `ReplaceBreachEffect(from, to)`,
  `LookAtEffect(f, cards, prompt)`, `RunnerInstallDiscountEffect(card, n)` (handles trojans),
  `InstallFromListDiscountEffect`, `ChooseServerDiscountEffect(card, n, rez, servers, allowNew)`,
  `RandomToBottomEffect(f, n)`, `StashEffect(card, host)` (+ `StashZone`), `PreventEncounterAbilityEffect(ice)`,
  `ArrangeTopEffect(f, n)`, `ChooseServerFlagEffect`, `ChooseServerMoveEffect` / `MoveToRootEffect`,
  `SwapWithHandIceEffect`, `PlayCardFreeEffect(card)`, `SeqEffect(l)`, `WinEffect`.
* `MillEffect` and damage discards now call the `onTrash` hook of the trashed Runner cards (Strike Fund).
* Access restrictions: run flags `access-only` / `access-exclude` applied in `AccessNextAction` (Adrian Seis).

## Shared-file edits (to merge)

* `game.scala`: class `Game` mixes in `with ExtRules`; `def impl(c) = extImpl(c)`; `trigger(e)` computes
  `val ext = extTriggers(e)` first and does `pushAll(effects ++ ext)`; `advancementRequirement` sums the modifiers of
  `(allActive :+ c).distinct` (so an installed agenda's own modifier counts: Ontological Dependence, Freedom of
  Information, Regulatory Capture); `performInternal` chain starts with `performExt orElse`.
* `abilities.scala`: six new hooks with default implementations at the end of `trait CardImpl` (see above);
  `CardImpls.all` registers `PHCards.all ++ TAICards.all`.
* `decks.scala`: `Decks.presets` ends with `) ++ PHTAIDecks.presets`.
* No edits to `rules-*.scala`, `ui.scala`, `meta.scala`, `cards.scala`, `cards-data*.scala`, `tools/`.

## Testing

* `sbt -batch compile` (Scala.js project) passes.
* JVM harness (scratchpad `ph-tai/host`, sources pointed at this worktree, `HostPT` restricts bots to the ph/tai
  presets and tallies every prompt): 30 + 200 + 250 bot-vs-bot games, 0 exceptions, 0 empty asks, no over-long games
  after the fixes. Tallies show every optional / choice prompt of the two sets being exercised (sabotage, marks,
  charge, bypass, breach replacement, psi game of Adrian Seis, expend abilities, Wage Workers, Stegodon derez,
  Tatu-Bola swap, Starlit Knight, Issuaq / Borehole wins, ...), except for rarely reachable ones (Curupira's 3-counter
  bypass, Flux Capacitor / Orca charge targets, Phoneutria with 4+ cards in grip, Klevetnik declined).
* Browser testing was not done.
