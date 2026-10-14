# Rotated (FFG era) Corp operations — status

Scope: every card with `kind == "Operation"` that had no implementation (146 cards). All 146 now have one. `CardImpls.all`
covers 841 codes (695 before).

Ported from jinteki.net `src/clj/game/cards/operations.clj` (helpers in `src/clj/game/core/`). Where the printed NRDB
text and the Clojure disagree, the printed text wins (Power Grid Overload: "successful run during their last turn").

## Files

| File | Content |
|---|---|
| `cards-rot-operations.scala` | `object RotOperationCards { val all }` (= A ++ B), `RotOperationCardsA` (01073 .. 11060, 82 cards), card-specific actions/purposes |
| `cards-rot-operations-2.scala` | `RotOperationCardsB` (11071 .. 29016, 64 cards) |
| `rules-ext-rot-operations.scala` | `trait RotOperationRules` (`performRotOperations`, `rotOperationTriggers`), base classes `RotOp` / `RotCondition`, generic effects |
| `decks-rot-operations.scala` | `object RotOperationDecks { val presets }`: 7 Corp decks "(rot-ops)" (HB x2, Jinteki, NBN x2, Weyland x2), eternal-legal, every eternal-legal operation of this set at least once |

## Cards (146)

01073 Precognition, 01097 Aggressive Negotiation, 02035 Big Brother, 02037 Power Grid Overload, 02040 Freelancer, 02054 Sunset,
02058 Commercialization, 02093 Rework, 02100 Foxfire, 02116 Midseason Replacements, 03013 Bioroid Efficiency Research,
03014 Successful Demonstration, 04016 Invasion of Privacy, 04019 Cyberdex Trial, 04031 Hellion Alpha Test, 04034 Shipment from
SanSan, 04040 Restructure, 04052 Accelerated Diagnostics, 04058 Power Shutdown, 04060 Interns, 04094 Restoring Face,
04111 Reclamation Order, 04113 Corporate Shuffle, 04118 Witness Tampering, 05013 Cerebral Cast, 05014 Medical Research
Fundraiser, 05015 Mushin No Shin, 05026 Diversified Portfolio, 05027 Fast Track, 06004 Mutate, 06012 Bad Times, 06022 Enhanced
Login Protocol, 06025 Cerebral Static, 06026 Targeted Marketing, 06028 Paywall Implementation, 06031 Lag Time, 06046 Manhunt,
06062 Peak Efficiency, 06070 Reuse, 06090 Snatch and Grab, 06107 Shoot the Moon, 07020 Housekeeping, 07021 Patch, 07022 Traffic
Accident, 07025 Sub Boost, 08017 Predictive Algorithm, 08035 Recruiting Trip, 08072 Defective Brainchips, 08091 An Offer You
Can't Refuse, 08096 Casting Call, 08099 Back Channels, 09019 24/7 News Cycle, 09020 Ad Blitz, 09021 Media Blitz, 09022 The
All-Seeing I, 09023 Surveillance Sweep, 10013 Heritage Committee, 10017 Dedication Ceremony, 10029 Product Recall, 10052 "Clones
are not People", 10071 Salem's Hospitality, 10075 Localized Product Line, 10092 Exchange of Information, 10094 Consulting Visit,
10104 Lateral Growth, 10106 Voter Intimidation, 10112 Election Day, 10113 Subcontract, 11016 Hard-Hitting News, 11019 Stock
Buy-Back, 11033 Enforcing Loyalty, 11034 Hatchet Job, 11035 Special Report, 11037 Liquidation, 11039 Financial Collapse,
11050 Ark Lockdown, 11051 Hellion Beta Test, 11056 Observe and Destroy, 11057 Service Outage, 11058 BOOM!, 11059 Door to Door,
11060 Scarcity of Resources, 11071 Wetwork Refit, 11074 Hasty Relocation, 11079 Best Defense, 11080 Preemptive Action,
11090 Friends in High Places, 11100 Enforced Curfew, 11111 Violet Level Clearance, 11113 Psychokinesis, 12031 Load Testing,
12033 Replanting, 12036 MCA Informant, 12039 Sacrifice, 12058 Audacity, 12059 Red Planet Couriers, 12072 Shipment from Tennin,
12078 Success, 12080 Mass Commercialization, 12090 O2 Shortage, 12096 Biased Reporting, 12099 Transparency Initiative,
12100 Rover Algorithm, 12112 Restore, 12116 Rolling Brownout, 12117 Threat Level Alpha, 12118 Priority Construction,
13038 Ultraviolet Clearance, 13051 Hunter Seeker, 14023 Net Watchlist, 20073 Green Level Clearance, 20074 Shipment from
MirrorMorph, 20092 Shipment from Kaguya, 20118 Anonymous Tip, 21014 Genotyping, 21016 Self-Growth Program, 21019 Wake Up Call,
21035 Threat Assessment, 21036 Economic Warfare, 21040 Distract the Masses, 21053 Reverse Infection, 21058 Death and Taxes,
21059 Trojan Horse, 21070 Kill Switch, 21097 Standard Procedure, 21113 Riot Suppression, 21117 Market Forces, 21119 High-Profile
Target, 22030 Divert Power, 22031 Fast Break, 22032 Game Changer, 22040 Hangeki, 22047 Eavesdrop, 22048 Attitude Adjustment,
22055 Building Blocks, 22056 Too Big to Fail, 22057 Under the Bus, 25081 Blue Level Clearance, 25101 Neural EMP, 25117 Closed
Accounts, 25119 SEA Source, 25136 Beanstalk Royalties, 25137 Oversight AI, 25147 IPO, 29013 Sweeps Week, 29016 Scorched Earth.

## New generic mechanics (`rules-ext-rot-operations.scala`)

* **`RotOp(code)`** (extends `Startup`, so it reuses `doubleCanPlay` / `doubleCost` / `corpInstallable`): an operation is described
  by `playReq` ("play only if"), `additionalCosts` (forfeit, remove a tag, trash a card...), `effects`, `rfg` (remove from the game
  instead of trashing) and the printed subtypes: **Double** (`doubleCost`, Synchrocyclotron aware), **Triple** (2 extra clicks),
  **Terminal** (`EndActionPhaseEffect` after resolving). `canPlay = clicks ok && playReq`. `onPlay` starts with
  `RotPlayStartAction` (resets stale state of a reused card). `onPlayNoAdditional` is used by "ignoring additional costs".
  `performCard` / `chosenCard` handle the generic loops and route the rest to `performOp` / `chosenOp`.
* **Currents** (`isCurrent` = subtype "Current", `staysInPlay`): `rotOperationTriggers` trashes every other current in the play
  areas when a current is played, Corp currents when an agenda is stolen and Runner currents when an agenda is scored (same rule
  as jinteki's `make-current-event-handler`), through the idempotent `TrashLingeringEffect(card)`. Runner currents from other
  worktrees are handled too, as long as their implementation returns `staysInPlay = true`.
* **Condition counters** (`RotCondition(code)`): the operation stays in the play area with `state.target = host`
  (`HostConditionEffect`); `candidates` / `hostEffects` describe the hosting; `rotOperationTriggers` trashes a condition when its
  host is no longer valid (`hostValid`, default: installed); `onLeave` runs when it is trashed (removes granted subtypes, the MCA
  tag, the Cerebral Static flag).
* **Trace margin**: `MarginTraceEffect(base, success, fail)` records the Runner's credits, and `RecordTraceMarginAction` (first
  success effect) sets `rotTraceMargin = trace strength - (link + Runner boost)`.
* **Installs**: `RotInstallEffect(card, servers, allowNew, payInstall, rez, adv, source)` offers only affordable destinations
  (install cost if paying + rez cost if `rez == 1`; `rez == 2` rezzes ignoring costs; agendas are never rezzed), respects the remote
  limit, places `adv` advancement counters, records the server in `source`'s memo. `RotInstallLoopAction` / `RotInstallLoopPurpose`
  install up to n cards from HQ / Archives / both, optionally filtered by subtype, one at a time.
* **Play from any zone**: `RotPlayEffect(card, ignoreAdditional)` (R&D, HQ, Archives) paying the play cost.
* `AdvanceTimesEffect(card, n)` ("advance n times", triggers `Advanced`), `SwapScoredEffect(a, b)`.
* **Registers** kept by `rotOperationTriggers` (bookkeeping, evaluated after the cards' triggers): `install-resource`,
  `runner-installs`, `runner-played-event`, `core-damage`, `click-run`; `rotClickRunStarting` marks the initiation of a run made
  with the basic action (Enhanced Login Protocol). `rot-no-rez` flags re-mark `cannot-rez` at every turn start (Mushin No Shin).
* "Operation as an agenda": `agendaPointsBonus` while in a score area (An Offer You Can't Refuse +1, "Clones are not People" +1,
  Hangeki -1 in the Runner's area), moved there with the existing `AddToScoreAreaEffect`.

## Simplifications / approximations

* **Surveillance Sweep**: the trace order is not changed (the engine's trace flow cannot be intercepted from an appended rules
  file); it only stays in play as a current.
* **Net Watchlist**: the 2 credits are an additional cost of icebreaker break and pump abilities (`CardImpl.icebreakerSurcharge`);
  other paid abilities of icebreakers are not taxed. (Not legal in any format; not in the presets.)
* **Enhanced Login Protocol**: the extra click is charged when the first click-run is initiated; the Run menu does not grey out the
  run for a Runner with exactly one click left (the run then fails "cannot pay the additional costs").
* **Bad Times / MCA Informant** are moved to the Runner's play area (`PlayZone(Runner)`) so that the Runner-side hooks apply
  (memory -2; MCA's "[click], 2 credits: trash the host" ability for the Runner). MCA's additional tag is a real tag added when
  hosted and removed when MCA leaves play (the Runner can also remove it normally).
* **Wetwork Refit**'s core damage subroutine is added after the printed subroutines (not before).
* **Oversight AI / Bioroid Efficiency Research** react to `SubsBroken` (icebreakers); breaking all subroutines with bioroid click
  abilities is not detected.
* **Media Blitz** copies the chosen agenda's triggers and static cost/strength/hand-size modifiers (implementations that check
  they are in the Corp's score area stay silent).
* **Reclamation Order / Localized Product Line** take all copies (no "how many" prompt). **Death and Taxes** gains automatically.
  **Hunter Seeker / Observe and Destroy / Hellion Beta Test / Best Defense / Self-Growth Program** target Runner cards only.
  **Load Testing** is -1 allotted click for the Runner's next turn. **Riot Suppression**'s core damage is not preventable (cost).
  **Accelerated Diagnostics** ignores Double/Triple `canPlay` click checks and drops leading click costs of other sets' operations.
* Alliance influence reductions (Heritage Committee, Product Recall, Salem's Hospitality, Consulting Visit) are not modelled by the
  deck validator. "Name a card" (Targeted Marketing, Salem's Hospitality) lists every Runner card title (~1000 options).
* `CreditsGained.source` is the first card in the Corp's play area, so a lingering current can be reported as the source of a
  credit gain (affects only The Zwicky Group).

## Shared-file edits (all additive, one term each)

| File | Line | Change | Why |
|---|---|---|---|
| `abilities.scala` | 128 | `++ RotOperationCards.all` in `CardImpls.all` | registration |
| `game.scala` | 339 | `with RotOperationRules` | mix in the rules trait |
| `game.scala` | 682 | `++ rotOperationTriggers(e)` in `triggerEffects` | currents, conditions, registers |
| `game.scala` | 753 | `orElse performRotOperations` (appended to the chain) | generic effects |
| `decks.scala` | 252 | `++ RotOperationDecks.presets` | preset decks |

No other shared file was touched (no `rules-*.scala`, `ui.scala`, `meta.scala`, `cards-data*`, `tools/` edits). No purpose
resolver was added to `resolvePurpose`: all new purposes are `CardPurpose`s handled by `RotOp`.

**Merge notes**: other worktrees porting Runner currents should rely on the current-trashing rule above (or make their own rule
idempotent: `TrashLingeringEffect` checks that the card is still in a play area).

## Tested

* `sbt -batch compile` green.
* JVM harness (scratchpad `rot-operations-host`, copy of `mergehost` pointed at this worktree; every offered and chosen action
  round-tripped through `Serialize`; empty asks and > limit games are errors): 300 games (7 rot-ops Corp presets vs 18 Runner
  presets, seed 4242) 0 errors, 132/146 operations played, 144/146 mentioned; 200 more games (seed 9001) 0 errors, 130 played;
  150 games over all 55 presets (seed 777) 0 errors.
* Scripted checks (`RotScriptTest`, 56 checks, all pass) for the cards bots rarely play and the generic mechanics: Accelerated
  Diagnostics, Restoring Face, Bad Times (memory, forced program trash, expiry), Traffic Accident, BOOM! (extra click, flatline),
  High-Profile Target, Scorched Earth, Product Recall, Hellion Beta Test, Wake Up Call, Threat Assessment, Riot Suppression,
  Hangeki (-1 point), Net Watchlist, Double/Triple/Terminal click accounting (Witness Tampering, Red Planet Couriers, IPO),
  currents (Lag Time replaced by Enforced Curfew, trashed on steal, Cerebral Static disabling/enabling the identity), conditions
  (Patch strength and trash with host, Sub Boost subroutine, MCA Informant tag/ability/trash), trace margin (Midseason
  Replacements), Enhanced Login Protocol + Service Outage, Defective Brainchips, An Offer You Can't Refuse, Subcontract, Success.
  The 40 Startup `ScriptTest` checks still pass.

## Merge note (2026-10-14)

After the eight rotated ports were merged into `netrunner`, mechanics that several ports had defined independently were
reduced to one definition each (see `ENGINE.md` §8.1 and `rules-ext-rot-shared.scala`). Expose is now
`ExposeEffect(cards)` / `Exposed(cards)`, facedown installs are `InstallFacedownEffect`, and the shared effects are
`LoseAllCreditsEffect`, `BreachNoRootEffect` and `SwapRootEffect`. The trace result event is `TraceResolved(successful, spent)`,
the psi result event is `RotPsiRevealed(corp, runner)`, recurring refills use `RefillCreditsEffect`, and the currents
rule lives in `rotOperationTriggers`. Renames made to avoid clashes: the agendas/upgrades base class `RA` became `RAg`, and
the purpose traits became `RotIcePurpose` / `RotProgPurpose` / `RotResPurpose` / `RotAgPurpose`. The agendas' `rotActive`
became `rotAgActive`, and the events' `LevyAction` became `LevyARLabAccessAction`. Apex no longer offsets the MU of facedown
programs, because facedown programs now use no MU at all. Some notes above may still name the old definitions.
