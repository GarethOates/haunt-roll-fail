# Restrictions pass: install / rez / play legality, lockdowns, advancement, deck building

> **Integration note (merge of the four fix passes, see STATUS.md "Integration of the four passes").** Final mixin order:
> `... with CreditRules with RestrictionRules with FixRules with PreventionRules` (ENGINE.md 8.6). The checks do not depend on it:
> `performRestrictions` is first in the `performInternal` chain and `restrictContinue` wraps every result, prevention-window asks
> included (blocked programs are also left out of the windows). The Black File keeps this pass's `corpCannotWin`; the fixes pass
> adds the win re-check when it is trashed. FixRules' copy of `scoreAgenda` reads `agendaValue`.

Theme of this pass: every install, rez, play and score path checks the same legality rules; global lockdowns apply to card
effects, not only to the basic actions; advancement and agenda values can be modified; decks are validated per identity.
Credit sources (recurring / stealth credits, additional costs) and prevention / timing approximations were left to the other
passes. New code: `rules-ext-restrictions.scala` (`RestrictionRules`, `InstallRules`, `DeckRules`); documented in
`ENGINE.md` section 8.3.

## Mechanisms

* **One legality check per action kind** (`RestrictionRules`):
  * `corpInstallBlock(card, server)`: Corp install lock (Signal Jamming's `rot-no-install` run flag, `corp-no-install` flag),
    Unscheduled Maintenance (1 ice per turn), remote server limit (`CardImpl.remoteLimit` of every active card: A Teia 2,
    Earth Station 1), agendas / assets only in remotes, printed server restrictions (`InstallRules.serverRule`, parsed once from
    the card text: "Remote server only", "Install only in a remote server", "Central server only", "HQ or R&D only", "Root of
    HQ or R&D only", "Install only in the root of HQ / R&D"), the rotated upgrades' `RAg.installOnly`, the card's `canInstall`.
  * `runnerInstallBlock(card, host)`: Runner install lock (Jua's `runner-no-install` turn flag, Signal Jamming), the heap locked
    by Blacklist, `RH.blocksInstall` (Apex: no non-virtual resources), the card's "install only if" requirement (`canInstall`:
    Data Leak Reversal, Bug, Tapwrm, Political Operative, Investigative Journalism, Globalsec Security Clearance, Time Bomb...).
    Facedown installs are always legal (as on jinteki).
  * `rezBlock(card, ignoreCost)`: the card's `canRez` (an additional rez cost, so skipped when all costs are ignored, except
    for "Rez only ..." requirements such as Front Company), `cannot-rez` markers (Mitosis, Mushin No Shin), `rhRezBlocked`
    (Saraswati, EMP Device...), Blackmail / Cyber Threat (`no-ice-rez` run flag), Interdiction (no non-ice rez during the
    Runner's turn), Hacktivist Meeting with an empty HQ.
  * `playBlock(card)`: The News Now Hour (no Runner current events), cards in a heap locked by Blacklist.
  * `programUseBlocked(program)`: run-scoped flags `no-program-use` (Vikram 1.0) and `cannot-use` on one program (Wendigo).
* **Where they apply.**
  * `restrictContinue` post-processes the result of every `Game.performInternal` call: illegal offered options are greyed out
    with the reason (`CorpInstallAction`, `ServerChosenAction`, `ServerDiscountChosenAction`, `ServerChosenPayingAction`,
    `RotServerChosenAction`, `MoveServerChosenAction`, `InstallWhereAction`, `RunnerInstallAction`, `RezAction`,
    `PlayCardAction`, card choices with a rez or install purpose, break / pump / ability / access-ability / prevention uses of
    a blocked program). An Ask that is left without any usable option gets `RestrictedSkipAction` ("Continue") so it never
    dead-ends.
  * `performRestrictions` is first in the `performInternal` chain and refuses illegal targets at the effect level, before any
    payment: `CorpInstallEffect`, `RunnerInstallEffect`, `CorpInstallPayingEffect`, `RunnerInstallPayingEffect`,
    `ServerDiscountChosenAction`, `RotServerChosenAction`, `RunnerInstallDiscountEffect`, `InstallDiscountEffect`,
    `CorpInstallIceAtEffect`, `RHInstallInnermostEffect`, `CorpHostInstallEffect`, `RotCorpHostInstallEffect`,
    `DiscountRezEffect`, `RezDiscountEffect`, `PlayCardFreeEffect`, `PlayFreeEffect`, `RotPlayEffect`, `PlayCardEffect`,
    `ScoreAgendaEffect` (with a `cannot-score` flag). Install-and-rez paths whose rez is blocked install without rezzing (and
    without paying the rez).
  * `rezCard` override (the universal rez sink, stackable before `RotAssetRules.rezCard`): refuses illegal rezzes from any path
    and charges Hacktivist Meeting's random trash from HQ for every non-ice rez that does not ignore all costs.
* **Overinstalling.** Installing an asset / agenda or a **region** into a server root trashes the previous asset / agenda /
  region ("limit 1 region per server", as on jinteki); the server is kept even when the trashed card was its only card. This
  also fixes a latent engine bug: overinstalling the only card of a remote used to remove the server (`cleanupRemotes` inside
  `trashCard`) and the new card landed in a server that no longer existed. An install into a remote that was emptied since the
  install was decided re-creates it.
* **Uniqueness / console limit** ignore facedown Runner cards.
* **Blacklist** locks the heap for every move (`heapLocked`, read by `Game.moveTo`), installs and plays. Harbinger is exempt
  (its trash is replaced: it never reaches the heap).
* **Satellite Grid**: `advancementBonus(c)` (+1 for ice protecting a server whose root holds a rezzed, active Satellite Grid)
  is added by `Game.advancement`; removing / moving counters reads the real counters. Morph ice (Wendigo, Changeling, Lycan)
  re-syncs its subtypes when Satellite Grid is rezzed or trashed.
* **Agenda values**: `agendaValue(c)` = printed points + own `agendaPointsBonus` + hosted bonuses (Political Graffiti). Used by
  `Game.agendaPoints`, the scored / stolen point registers and logs, and by the cards that read the value of an agenda in a score
  area (IP Enforcement, Investigator Inez Delgado A).
* **The Black File**: a Corp win other than by flatline is turned into "continue" (the Runner's own 7 points are still checked);
  the win is checked again when The Black File is removed from the game.
* **Always Be Running**: the Runner's first [click] each turn must run or play a run event (other click actions greyed out while
  a run is possible; non-run events greyed out in the play menu).
* **Deck building** (`DeckRules`, used by `Decks.validate` and the deck preview): copy limits (none for draft identities, 1 for
  Nova Initiumia / Ampere), agendas of the identity's faction or neutral (any for draft identities and Nova / Ampere), Custom
  Biotics (no Jinteki cards), Ampere (at most 2 different agendas from each faction), influence with The Professor's free
  first copy of each program and the alliance cards' 0-influence conditions (Heritage Committee, Product Recall, Jeeves Model
  Bioroids, Raman Rai, Salem's Hospitality, Executive Search Firm, Consulting Visit, Ibrahim Salem: 6 non-alliance cards of the
  faction; Mumba Temple: 15 or fewer ice; Museum of History: 50+ cards; PAD Factory: 3 PAD Campaigns; Mumbad Virtual Tour: 7+
  assets), unlimited influence for identities without an influence limit (draft identities, Nova, Ampere, the SG neutral
  identities), as jinteki's `validator.cljc`. Adam's directives stay a setup effect outside the deck (already implemented).
  Mini-faction cards (Apex, Adam, Sunny Lebeau) are splashable with influence, as printed.

## Cards fixed

| Code | Card | Before | Now |
|---|---|---|---|
| 27005 | La Costa Grid | "remote server only" not enforced | enforced on every install path |
| 26105 | Tranquility Home Grid | "remote server only" not enforced | enforced |
| 33127 | ZATO City Grid | "remote server only" / region not enforced | enforced |
| 34064 | Tucana | "remote server only" not enforced | enforced |
| 33102, 33111 | Djupstad Grid, Nanisivik Grid | "limit 1 region per server" not enforced | region overinstall |
| all Region upgrades | ChiLo City Grid, Satellite Grid, Breaker Bay Grid, Hokusai Grid, SanSan City Grid, ... | not enforced | region overinstall |
| 36045, 36064 | The Red Room, Flagship | "central only" / "HQ or R&D only" not enforced | enforced |
| 01105, 04038, 04072, 06112, 12053, 21017, 21072, 21096 | Research Station, Off the Grid, Panic Button, Self-destruct, Bamboo Dome, Calibration Testing, Bio Vault, Mwanza City Grid | basic menu and `ChooseServerEffect` only | every path |
| 26120 | Earth Station: SEA Headquarters | "limit 1 remote server" not enforced | `remoteLimit` 1, every path |
| 34039 | A Teia: IP Recovery | remote limit greyed the basic menu only | every path |
| 26100 | Vaporframe Fabricator | "not in the root of this server" not enforced | the on-trash install excludes its server's root |
| 21034 | Jua | install lock only logged | Runner install lock for the rest of the turn, every path |
| 10012 | Vikram 1.0 | "cannot use programs" only logged | programs unusable for the rest of the run |
| 06047 | Wendigo | "cannot use the chosen program" only logged | the Corp chooses a program; unusable for the run |
| 09029 | Apex: Invasive Predator | resource ban on the basic install only | every path (facedown installs allowed) |
| 06036 | Unscheduled Maintenance | basic install only | every path |
| 11087 | Interdiction | basic rez / windows only | every rez path |
| 04089, 06013 | Blackmail, Cyber Threat | basic rez / windows only | every rez path |
| 08021 | Hacktivist Meeting | basic rez only | every rez path (random trash, no rez with an empty HQ) |
| 08036 | Blacklist | generic effects / purposes only | every move out of the heap, installs, plays |
| 06045 | The News Now Hour | play menu only | every play path |
| 12020 | Signal Jamming | install effects only | also greys the install options |
| 33069, 02103, 05043, 11104, 10043, 07049, 09051 | Time Bomb, Data Leak Reversal, Bug, Tapwrm, Political Operative, Investigative Journalism, Globalsec Security Clearance | install requirement on the basic action only | every path |
| 33046 | Mitosis | "cannot rez" marker on the basic rez only | every rez path |
| 34041 | Front Company | "rez only during your turn" bypassed by "ignoring all costs" rezzes | enforced |
| 07023 | Satellite Grid | no effect | +1 advancement token on ice protecting its server |
| 10039 | Political Graffiti | adjusted the Corp's total only | the agenda's value (`agendaValue`) everywhere |
| 09034, 09035 | Harbinger, Hunting Grounds | facedown programs counted MU (status notes) | facedown programs use no MU (already true since the merge; notes updated) |
| 10099 | The Black File | "the Corp cannot win except by flatline" not enforced | enforced |
| 09041 | Always Be Running | first-click restriction not enforced | enforced |
| 03029 | The Professor | deck rule not validated | first copy of each program free |
| 03002 | Custom Biotics | deck rule not validated | no Jinteki cards |
| 33093, 33128 | Nova Initiumia, Ampère | deck rules not validated | singleton (+ Ampère's 2 agendas per faction), unlimited influence |
| 00005-00013 | draft identities | deck limits applied | no copy / influence limits |
| 10013, 10018, 10019, 10029, 10038, 10067, 10068, 10071, 10072, 10076, 10094, 10109 | alliance cards | full influence | 0 influence when the condition holds |

Six su21/ms presets had out-of-faction agendas and became illegal; they were fixed by replacing those agendas with in-faction or
neutral agendas of the same points (no influence change): HB Architects of Tomorrow (Project Beale -> Offworld Office), Jinteki
Personal Evolution (Elivagar Bifurcation x2, Project Vitruvius -> Superconducting Hub x2, Blood in the Water +1), NBN Near-Earth
Hub (Megaprix Qualifier x3, Hostile Takeover x2 -> Superconducting Hub x3, Post-Truth Dividend x2), NBN Pravdivost Consulting
(Megaprix x2, Oaktown Renovation, Project Atlas, Hostile Takeover -> Project Beale +1, Superconducting Hub x3, Offworld Office),
Weyland Building a Better World and Weyland Ob Superheavy Logistics (Megaprix Qualifier -> Superconducting Hub). All 195 presets
validate.

## Still approximate (and why)

* (Fixed in the final NSG pass, see `STATUS-fix-final-nsg.md`: root moves and swaps (`MoveToRootEffect`, `SwapRootEffect`, Daruma,
  `SwapInstalledWithHandEffect`) re-check the one asset / agenda and one region per root rules and refuse assets / agendas in
  central roots.)
* Security Leak's additional advance cost and The Price of Freedom apply to the basic advance action: no card effect "advances"
  a card (they place counters), so this is complete for the implemented cards.
* A paid-ability window whose only option is greyed out by a restriction still opens with its "pass" option (cosmetic).
* The Black File: if it is trashed (instead of removed by its own ability) while the Corp already has enough points, the Corp
  wins at the next win check (next score / steal), not immediately.
* Hacktivist Meeting's random trash is charged after a rez paid through a pre-paid discount path (`DiscountRezEffect`,
  `RezDiscountEffect`) only when the rez does not ignore all costs; those paths rez "ignoring costs" after paying, so they skip it.
* Jua / Signal Jamming do not stop facedown installs (jinteki: "can always install a card facedown").
* Genetics Pavilion (draw cap) is unchanged: it already applies to every draw (`DrawNowEffect`).

## Shared-file edits (all surgical)

| File | Edit |
|---|---|
| `game.scala` | `Game` mixes in `with RestrictionRules` (last); `performInternal` chain starts with `performRestrictions orElse` and its result is wrapped in `restrictContinue(...)`; `moveTo` returns early when `heapLocked(c, z)`; `advancement(c)` adds `advancementBonus(c)`; `agendaPoints(f)` sums `agendaValue` |
| `rules-cards.scala` | uniqueness and console-limit trashing in `RunnerInstallEffect` skip facedown cards (`&& !state(x).facedown`, 2 lines) |
| `rules-turn.scala` | `scoreAgenda` / `stealAgenda`: `lazy val points = agendaValue(c)` (2 lines; first used after the agenda is in the score area) |
| `rules-ext-rot-events.scala` | `RemoveAdvancementEffect` reads `state(c).advancement`; the Hacktivist Meeting `RezAction` interception was removed (the trash is charged by `RestrictionRules.rezCard` for every rez path) |
| `rules-ext-su21.scala` | `MoveAdvancementEffect` reads `state(from).advancement` |
| `decks.scala` | `validate` uses `DeckRules.overLimit` / `DeckRules.check` / `DeckRules.influence` / `DeckRules.influenceLimit` (unlimited shown as "unlimited"); six presets fixed (agenda lines only) |
| `ui.scala` | deck preview influence / limit via `DeckRules` (2 lines) |
| `cards-ur.scala` | Earth Station `remoteLimit = 1`; Vaporframe Fabricator's on-trash install uses `NoRootInstallPurpose(card, server)` |
| `cards-rot-ice-1.scala` | Jua's encounter adds the `runner-no-install` turn flag; `Morph` re-syncs on Satellite Grid rez / trash |
| `cards-rot-ice-2.scala` | Vikram 1.0's first subroutine adds the `no-program-use` run flag; Wendigo's subroutine chooses a program (`cannot-use` run flag) |
| `cards-rot-resources-2.scala` | The Black File checks the win again when it removes itself |
| `cards-rot-upgrades.scala` | comment on Satellite Grid |
| `cards-elev.scala`, `cards-rot-assets-2.scala` | IP Enforcement / Investigator Inez Delgado A read `agendaValue` |
| `ENGINE.md` | mixin order / chain updated, section 8.2 |
| `STATUS*.md` | fixed approximations removed (rot-agendas-upgrades, rot-assets, rot-events, rot-hardware-identities, rot-ice, rot-programs, rot-resources, rot-operations, ph-tai, su21-ms, rwr-df-ur, vp-elev, STATUS.md tables and a "Restrictions pass" section) |

## Tested

* `sbt -batch compile` (Scala.js) green.
* JVM harness (scratchpad `fix-restrictions-host`, a copy of `integ/host` pointed at this worktree):
  * new `RestrictionScript`: 74 checks, all passing (printed server restrictions through card-effect server choices and effect
    installs, the "Continue" fallback, region overinstall, overinstalling the only card of a remote, Earth Station / A Teia limits
    through effects, Unscheduled Maintenance, Jua, Apex (facedown allowed), Data Leak Reversal from the heap, Interdiction /
    no-ice-rez on effect rezzes and "ignoring all costs", Hacktivist Meeting, The News Now Hour on free / paying plays, Blacklist
    (grip, card-specific moves, installs; Harbinger exempt), Vikram 1.0 and Wendigo (lifted after the run), Satellite Grid
    (real vs. virtual tokens, unrezzed grid, Wendigo morph), Political Graffiti, facedown uniqueness / consoles, Vaporframe
    Fabricator, `ScoreAgendaEffect` with `cannot-score`, The Black File, Front Company, Always Be Running, the deck rules
    (Professor, other identity, agenda factions, Custom Biotics, Nova, Ampere, Heritage Committee, draft identities, all presets).
  * existing scripted checks all still pass: `ScriptTest`, `RotIceScript` (211 ice, 0 failures), `RotAssetScript`,
    `RotAgendaScript`, `RotEventScript`, `RotHardwareScript` (80), `RotOperationScript` (56), `RotProgramScript`, `RotMergeScript`.
  * bot games (`MergeHost`, every offered / chosen action round-tripped through the serializer, empty asks and over-long games
    counted as errors): 250 + 250 games over all 195 presets before the last changes, then 250 (seed 4242) + 250 (seed 777) over
    all presets + 200 rotated Corp vs rotated Runner presets (seed 31) on the final code: **1,200 games, 0 exceptions, 0 empty asks,
    0 serialization mismatches, 0 over-long games**. The logs show region overinstalls, Interdiction refusals, Wendigo choices.
* All 195 presets validate under the stricter deck rules.
