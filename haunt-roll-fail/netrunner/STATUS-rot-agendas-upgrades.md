# Rotated (FFG era) agendas and upgrades — status

Every card of type Agenda or Upgrade that had no implementation now has one: **114 agendas + 79 upgrades = 193 cards**
(`Cards.all.%(c => c.kind == "Agenda" || c.kind == "Upgrade")` minus the codes already in `CardImpls.all` at e8a95b0).
Ported from jinteki.net `src/clj/game/cards/agendas.clj` / `upgrades.clj` (tests in `test/clj/game/cards/*_test.clj`).

## Files

| File | Content |
|---|---|
| `rules-ext-rot-agendas.scala` | `trait RotAgendaRules` (new events / effects / purposes, engine refinements, `rotAgendaTriggers`, `performRotAgendas`) and the base class `RA(code)` (extends `Startup`, adds the hooks read by the trait) |
| `cards-rot-agendas.scala` | `object RotAgendaCards { val all }` — 53 agendas (core .. Data and Destiny) `++ RotAgendaCards2.all` |
| `cards-rot-agendas-2.scala` | `object RotAgendaCards2` — 61 agendas (Mumbad .. Magnum Opus, incl. the Terminal Directive campaign cards) |
| `cards-rot-upgrades.scala` | `object RotUpgradeCards { val all }` — 79 upgrades |
| `decks-rot-agendas.scala` | `object RotAgendaDecks { val presets }` — 12 Corp presets "RotAg HB/Jinteki/NBN/Weyland 1-3", generated so that every eternal-legal rotated agenda/upgrade is in a deck; all validate |

## Shared-file edits (all additive terms)

| File | Line | Change | Why |
|---|---|---|---|
| `game.scala` | 339 | `... with RWRRules with RotAgendaRules {` | mix in the new rules trait |
| `game.scala` | 682 | `... ++ ext ++ rotAgendaTriggers(e)` in `triggerEffects` | game-level triggers (PassedAllIce, Archives access hooks, persistent Overseer Matrix, bookkeeping) |
| `game.scala` | 753 | `... orElse performRWR orElse performRotAgendas` | the new effects |
| `abilities.scala` | 128 | `... ++ URCards.all ++ RotAgendaCards.all ++ RotUpgradeCards.all)` in `CardImpls.all` | register the cards |
| `decks.scala` | 252 | `... ++ ExtDecks.presets ++ RotAgendaDecks.presets` | preset decks |

No other shared file was touched. Instead of editing rule files, `RotAgendaRules` *extends* `TurnRules`, `CardRules`,
`AccessRules`, `EffectRules`, `AbilityRules`, `StartupRules` (still with self-type `Game`) and overrides some of their
methods stackable-trait style: each override handles its own cases and delegates everything else to `super`, so it composes
with other traits that do the same (merge note: if another branch also overrides one of these methods the same way, both
apply; the order is the `with` order in `game.scala`).

| Override | Used for |
|---|---|
| `performTurn` | install restrictions in `InstallWhereAction` (greyed "cannot be installed there"); `AdvanceMenuAction` greyed while `reg("rot-no-advance")` (Efficiency Committee) |
| `performCards` | install restrictions in `ChooseServerEffect` (falls back to the unfiltered ask if nothing is left); `CorpInstallEffect` / `RunnerInstallEffect` blocked by the run flag `rot-no-install` (Signal Jamming) |
| `performAccess` | steal costs that are not `Cost` records (net damage: Obokata Protocol, Ben Musashi; shuffle installed cards: Degree Mill) paid before stealing, steal greyed if unpayable; `NoAccessAction` greyed when the card must be trashed (Mumbad Virtual Tour); Corp chooses the HQ accesses (run flag `rot-corp-chooses-hq`, Dedicated Neural Net) |
| `performEffects` | base trace strength bonus (`RA.traceBonus`: Rutherford Grid, Improved Tracers), link treated as 0 for the first trace each turn (Net Quarantine), event `TraceResolved(successful, spent)` after every trace, meat damage bonus (`RA.meatBonus`: The Cleaners), event `AdvancementPlaced(card, n)` after `PlaceAdvancementEffect` |
| `performAbilities` | additional jack out costs (`RA.jackOutCosts`: "program" Port Anson Grid, "grip" Armored Servers) paid before jacking out |
| `windowOptions` | jack out greyed when the additional cost cannot be paid |
| `abilityOptions` | Runner paid abilities of non-icebreakers greyed during runs on a Navi Mumbai City Grid server |
| `stealCostOf` | persistent steal costs (flags `rot-steal-click` Strongbox, `rot-steal-credits` Red Herrings); a no-op `CreditCost(0)` so the access prompt is shown when a non-Cost steal cost applies |
| `accessProtected` | "cannot steal this card" (flag `rot-no-steal`, The Future Perfect) and persistent Old Hollywood Grid (`rot-no-steal-server`) |
| `resolvePurpose` | the generic purposes below |

## New generic mechanics (rules-ext-rot-agendas.scala)

* Events: `PassedAllIce(server)` (emitted by `rotAgendaTriggers` after the Runner passes the innermost ice, or at run
  initiation on a server without ice), `TraceResolved(successful, spent)`, `AdvancementPlaced(card, n)`.
* Effects: `FaceupInstallEffect` ("install faceup" agendas become active like rezzed cards), `CancelBypassEffect`,
  `CorpInstallIceAtEffect(card, server, index)` (+ `SetPositionAfterInstallEffect`, adjusts the run position), `SwapRootEffect`,
  `RotSwapIceEffect` (logs without naming unrezzed ice), `CorpHostInstallEffect(card, host)`, `LoseAllCreditsEffect`,
  `RezIgnoringAllCostsEffect`, `RotStealPaidAction`, `RotJackOutPaidAction`.
* Purposes: `CorpChosenHQAccessPurpose`, `ShuffleInstalledIntoStackPurpose(f)`.
* `RA` hooks: `installOnly`, `stealDamage`, `stealShuffleInstalled`, `jackOutCosts`, `traceBonus`, `linkZero`, `meatBonus`,
  `blocksRunnerAbilities`, `mustTrashOnAccess`, `onArchivesAccess` (access abilities that also apply in Archives: the engine
  does not call `onAccess` there) + helpers (`corpScored`, `runHere`, `protecting`, `inRoot`, `fromThisServer`, ...).
* Persistent upgrades ("If the Runner trashes this card while accessing it, this ability still applies for the remainder of
  this run"): `onTrash` adds a run-scoped `Flag(key, card, server)` when the rezzed upgrade is trashed by the Runner during a
  run (Strongbox, Red Herrings, Ben Musashi, Old Hollywood Grid, Overseer Matrix); Mwanza City Grid's payout is the expiry
  effect of its run flag, so it also happens if Mwanza is trashed during the breach.
* Snapshots for Personality Profiles: heap / stack at the previous event (`rotHeapPrev`, `rotStackPrev`).
* Agendas in the Runner's score area are active for the engine (their triggers fire and their `abilities` are listed for
  the Runner), so every agenda effect checks `corpScored(self)` unless the card says otherwise (SSL Endorsement, Medical
  Breakthrough, 15 Minutes, Global Food Initiative / Merger / Evidence Collection point changes).

## Simplifications / known gaps

* **Install restrictions** are enforced in the basic install menu and `ChooseServerEffect` only (other install paths, e.g.
  generic install loops, are not filtered). "Limit 1 region per server" is not enforced (as for the other sets).
* **Satellite Grid**: no effect ("considered to have an additional advancement token" would need `Game.advancement`).
* **Navi Mumbai City Grid**: blocks `abilityOptions` and the Runner's prevention abilities (fixed, see `STATUS-fix-prevention.md`).
* **Midway Station Grid / Armored Servers** break costs: the break is gated on being able to pay (1 credit / a card in the
  grip) and the cost is collected right after the break (`SubsBroken`), per break ability use / per subroutine broken.
* **Simone Diego / Dedicated Technician Team / Khondi Plaza** recurring credits: Simone refunds 1 credit after a basic advance
  of a card in its server, DTT refunds the ice install cost after an action-phase install protecting its server (up to its
  credits), Khondi lowers the rez cost of ice protecting its server by its hosted credits and spends them (Mahkota pattern).
* **Heinlein Grid** is a forced trigger as printed ("whenever the Runner loses or spends [click] during a run on this server");
  it now reacts to the `ClicksSpentOrLost` event (click costs paid and clicks lost) instead of comparing clicks between events
  (jinteki models it as a manual once-per-run ability).
* **Personality Profiles**: "installs a card from the heap" uses the heap snapshot; "searches the stack" is detected
  heuristically (cards left the stack from below the top, or its order changed, since the previous event).
* (Labyrinthine Servers, Tyr's Hand and Tori Hanzō were fixed by the prevention / timing pass: see `STATUS-fix-prevention.md`.)
* **Improved Tracers** trace bonus applies to traces started during an encounter (the source of a trace is not tracked).
* **Valley Grid** hand-size penalty lasts while Valley Grid stays active (the hook is on the card).
* **Mandatory Upgrades** grants the extra click at "when your turn begins" (after the turn-start window); losing it when the
  agenda leaves the score area is not modelled.
* **Mandatory Seed Replacement** is a sequence of at most 6 swaps; **Accelerated Beta Test** / **Bacterial Programming** do
  not track shuffles during the effect; Bacterial Programming on steal does not remove R&D cards moved to HQ from the
  current access list.
* **Unorthodox Predictions** prevents breaking (`preventsBreak`) until the turn counter changes (next Corp turn).
* **Will-o'-the-Wisp** candidates are the installed icebreakers that broke a subroutine this run (`SubsBroken`; bioroid
  click-breaks are not counted). **Mason Bellamy** also only sees `SubsBroken` breaks.
* **Warroid Tracker / Overseer Matrix** fire once per trashed card. Overseer Matrix does not fire for its own trash.
* **Daruma** trashes itself after the second choice; root-to-root swaps do not re-check the one agenda/asset per root rule.
* **Evidence Collection 1-3** have no in-game effect; **Corporate Oversight A/B** and Evidence Collection / Hired Help are not
  eternal-legal and are not in the presets (covered by scripted checks).
* **NAPD Contract / Ikawah Project / Fetal AI / Utopia Fragment** steal costs use the engine `Cost` records.

## Tested

* `sbt -batch compile` green.
* JVM harness (scratchpad `rot-agendas-host`, a copy of `mergehost` built from this worktree, every offered and chosen
  action round-tripped through `Serialize`; `MergeHost` now also tallies the rotated cards):
  * 320 games, the 12 RotAg Corp presets against all Runner presets (`HRF_SEED=777`): **0 exceptions, 0 empty asks, 0
    serialization mismatches, 0 over-long games**; 185/193 cards mentioned in the logs (the 8 others: the 7 non-eternal
    cards not in any preset, and Puppet Master, which was simply never drawn — covered by a scripted check). Log tallies:
    agendas scored and stolen (e.g. Research Grant 7 scores / 19 steals, Bifrost Array 3/13, Illicit Sales 4/7), upgrades
    rezzed and used (Georgia Emelyov 98, Bamboo Dome 89, Shell Corporation 51, Mwanza City Grid 36, Product Placement 35, ...).
  * 40-game smoke run: 0 errors. Mixed regression run over all 60 presets (200 games, `HRF_SEED=4242`): 0 errors.
  * The existing Startup `ScriptTest` (40 checks) still passes.
  * New `RotScriptTest` (34 checks, all pass): Obokata Protocol damage steal cost, Degree Mill (greyed with 1 installed card,
    shuffles 2), Port Anson Grid (jack out greyed / program trashed), Strongbox persistence after being trashed, Old Hollywood
    Grid (and the copy exception), Rutherford Grid + ChiLo City Grid traces, The Cleaners, Timely Public Release position,
    Panic Button install restriction, 15 Minutes from the Runner's score area, Corporate Oversight A, Puppet Master, Global
    Food Initiative points, Hired Help, Awakening Center forced encounter + trash at run end, Personality Profiles heap
    install, Dedicated Neural Net, Mandatory Upgrades.
* No browser testing.

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
