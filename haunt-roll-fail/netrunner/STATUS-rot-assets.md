# Rotated assets — status

Port of every card of type `Asset` that had no implementation (`Cards.all.%(_.kind == "Asset")` minus `CardImpls.all`):
**144 cards, all implemented** (ported from jinteki.net `assets.clj`, behaviour checked against `assets_test.clj`).

## Files

| File | Content |
|---|---|
| `rules-ext-rot-assets.scala` | `trait RotAssetRules` (generic mechanics), base class `RA(code)`, wrappers `AsAgendaImpl` / `MinusOnePointImpl` |
| `cards-rot-assets.scala` | `object RotAssetCards { val all }` (= `RotAssetCardsA.all ++ RotAssetCardsB.all`), codes 01071 – 10038 (68 cards) |
| `cards-rot-assets-2.scala` | `RotAssetCardsB`, codes 10049 – 29015 (76 cards) |
| `decks-rot-assets.scala` | `object RotAssetDecks { val presets }`: 8 Corp presets "(rot assets)", eternal-legal, together containing every eternal-legal rotated asset |

All new top-level classes are prefixed `Rot` (generic) or `RA` (card specific) to avoid collisions with other worktrees.

## Coverage

01071 Zaibatsu Loyalty, 01096 Security Subcontract, 02029 Encryption Protocol, 02053 Edge of World, 02059 Private Contracts,
02072 Dedicated Server, 02075 Net Police, 02092 Eve Campaign, 03008 Alix T4LB07, 03010 Director Haas, 03011 Haas Arcology AI,
03012 Thomas Haas, 03024 Levy University, 03025 Server Diagnostics, 04015 Jackson Howard, 04050 Isabel McGuire, 04055 City
Surveillance, 04070 Rex Campaign, 04073 Shock!, 04092 Toshiyuki Sakai, 04112 Broadcast Square, 05008 Chairman Hiro, 05009 Mental
Health Clinic, 05010 Psychic Field, 05011 Shi.Kyū, 05012 Tenma Line, 05023 Plan B, 06006 Primary Transmission Dish, 06008 The Root,
06029 Sealed Vault, 06042 Eliza's Toybox, 06045 The News Now Hour, 06050 Shattered Remains, 06072 Docklands Crackdown, 06103 IT
Department, 07008 Constellation Protocol, 07009 Mark Yale, 07010 Space Camp, 07011 The Board, 08010 Brain-Taping Warehouse, 08018
Capital Investors, 08020 Tech Startup, 08036 Blacklist, 08038 Student Loans, 08071 Test Ground, 08073 Allele Repression, 08075
Exposé, 08088 Ronald Five, 08095 Early Premiere, 08109 Cybernetics Court, 08110 Team Sponsorship, 08113 Genetics Pavilion, 08114
Franchise City, 08116 Worlds Plaza, 09008 Lily Lockwell, 09009 News Team, 09010 Shannon Claire, 09011 Victoria Jenkins, 09012
Reality Threedee, 09027 Launch Campaign, 10015 Kala Ghoda Real TV, 10018 Mumba Temple, 10019 Museum of History, 10027 Advanced
Assembly Lines, 10028 Lakshmi Smartfabrics, 10031 Pālanā Agroplex, 10036 Mumbad Construction Co., 10038 PAD Factory, 10049 Clone
Suffrage Movement, 10050 Bio-Ethics Association, 10051 Political Dealings, 10053 Sensie Actors Union, 10054 Commercial Bankers
Group, 10055 Mumbad City Hall, 10067 Jeeves Model Bioroids, 10068 Raman Rai, 10070 Aryabhata Tech, 10072 Executive Search Firm,
10073 Indian Union Stock Exchange, 10108 Full Immersion RecStudio, 10109 Ibrahim Salem, 10111 Zealous Judge, 11012 Hyoubu Research
Facility, 11015 Watchdog, 11020 Sandburg, 11036 C.I. Fund, 11055 Alexa Belsky, 11073 Fumiko Yamamori, 11077 Chief Slee, 11096 Anson
Rose, 11118 NASX, 12012 Synth DNA Modification, 12014 Net Analytics, 12017 Quarantine System, 12034 CPC Generator, 12037 Clyde Van
Rite, 12051 Bioroid Work Crew, 12079 Whampoa Reclamation, 12097 Open Forum, 12111 MCA Austerity Policy, 12113 Breached Dome, 13032
Estelle Moon, 13045 Illegal Arms Factory, 13046 Mr. Stone, 13054 Honeyfarm, 13055 Long-Term Investment, 14004 Investigator Inez
Delgado A, 14005 Investigator Inez Delgado A 2, 14006 Lt. Todachine, 14007 Lt. Todachine 2, 14008 Trojan, 20081 Dedicated Response
Team, 20083 GRNDL Refinery, 20127 Melange Mining Corp., 21012 Gene Splicer, 21015 Echo Chamber, 21018 Urban Renewal, 21020
Reconstruction Contract, 21039 NGO Front, 21049 Kuwinda K4H1U3, 21056 Personalized Portal, 21060 TechnoCo, 21069 Malia Z0L0K4, 21076
Amani Senai, 21080 Rashida Jaheem, 21093 Warden Fatuma, 21120 False Flag, 22036 API-S Keeper Isobel, 22037 Neurostasis, 22044 SIU,
22052 Drudge Work, 22058 Lady Liberty, 25070 Adonis Campaign, 25071 Aggressive Secretary, 25089 Project Junebug, 25092 Sundew, 25109
Ghost Branch, 25110 Marked Accounts, 25127 Contract Killer, 25128 Elizabeth Mills, 25129 Public Support, 29011 Hostile
Infrastructure, 29012 Turtlebacks, 29015 Executive Boot Camp.

The Terminal Directive campaign cards 14004 – 14008 are not legal in any format; they are covered by scripted checks only.

## New generic mechanics (rules-ext-rot-assets.scala)

* **Base class `RA(code) extends NC(code)`** with hooks read by the rules: `asAgendaPoints` (value when added to a score area as
  an agenda), `executive` (trashed while accessed → Runner score area as a 2-point agenda), `accessInArchives` (ambush `onAccess`
  also resolves in Archives — the engine only calls `onAccess` outside Archives), `exposeOptions` / `onExpose` (expose responses),
  `recurring(self, kind, card)` (hosted recurring credits usable for `rez` / `install` / `advance` / `trace`; `RA` turns them into
  `rezCostMod` / `installCostMod` discounts), helpers `campaign`, `refill`, `ambush`, `unprotectedServer`, `sortedDeck`.
* **"As an agenda"**: `RotAsAgendaEffect(card, f)` moves any card to a score area (uninstalling it, cleaning up remotes, checking the
  win). Such cards are wrapped in `AsAgendaImpl` (only the agenda value remains), so their abilities / triggers / hand size effects
  stop. Used by News Team, Shi.Kyū, Franchise City, Gene Splicer, Echo Chamber, False Flag, Public Support and the executives
  (Director Haas, Chairman Hiro, The Board, Victoria Jenkins).
* **Expose**: `RotExposeEffect(card)` (for future Runner cards that expose): the Corp may answer with `RA.exposeOptions` of its
  installed cards (Zaibatsu Loyalty: rez it, then pay 1 / trash to prevent), else the card is exposed (`seen`), event
  `RotExposed(card)`, `RA.onExpose` (Psychic Field). Nothing in the implemented Runner sets exposes yet.
* **Events**: `RotPsiRevealed(corp, runner)` after every psi game, `RotTraceSucceeded` after every successful trace, `RotExposed`.
* **Corp cards hosted on Corp cards**: `RotCorpHostInstallEffect(card, host)` (Worlds Plaza, Full Immersion RecStudio); the hosted
  cards are installed (rez, advance, score, access with the host's server work through the existing engine).
* `RotChooseInstallServerEffect(card, ignoreCost)` (install from any zone choosing the server, greying unaffordable servers),
  `RotBadPubNowEffect(n)`, `RotTrashIfEmptyEffect(card, kind)` (counter costs do not fire `CounterAdded`, Drudge Work needs it).
* **Registers** counted by `rotAssetTriggers` after the cards saw the event (`reg(k) == 0` = first time this turn):
  `rot-ice-rezzed` (Watchdog), `rot-runner-installs` (Docklands Crackdown), `rot-runner-clicks` / `rot-runner-click-credit`
  (Sundew, CPC Generator), `rot-ap-broken` (Synth DNA Modification), `rot-psi` (Hyoubu Research Facility).
* Malia Z0L0K4 uses the ph/tai `disabled` flag with scope `rot-malia`; `rotAssetTriggers` removes it once Malia is no longer rezzed.

### Stackable overrides (instead of shared-file edits)

`RotAssetRules extends EffectRules with TurnRules with CardRules with RunRules with PHTAIRules with RWRRules` (self-type `Game`) and
overrides, each calling `super` and guarded by the relevant card being in play:

| Override | Purpose |
|---|---|
| `performEffects` | `RotPsiRevealed` after `PsiResolveAction`; recurring trace credits in `TraceCorpBoostAction` / `TraceCorpAction` (Net Police, Primary Transmission Dish); `RotTraceSucceeded` after `TraceRunnerAction`; (Broadcast Square: moved to the prevention window); Genetics Pavilion cap on `DrawNowEffect(Runner)`; Blacklist on `AddToHandEffect` / `MoveEffect` / `RemoveFromGameEffect` out of the heap |
| `performCards` | Blacklist on `RunnerInstallEffect` from the heap |
| `performTurn` | greys the Runner's click draw (Genetics Pavilion) and current events (The News Now Hour) |
| `performRun` | remembers the unbroken subroutines when an encounter ends (`EncounterEndsAction` resets them before the event; Chief Slee) |

| `resolvePurpose` | Blacklist: generic (non-card) purposes cannot take heap cards |
| `extImpl` | `AsAgendaImpl` for `RA` cards in a score area; `MinusOnePointImpl` (−1 point) for Runner score area cards while The Board is rezzed |
| `subs` | Warden Fatuma: rezzed bioroid ice get "The Runner loses [click]" as their first subroutine |

If another worktree also overrides one of these, both compose as long as each calls `super`.

## Simplifications

* Recurring credits (Dedicated Server, Net Police, Primary Transmission Dish, The Root, Mumba Temple) are credit sources of
  `rules-ext-credits.scala` (`RA.paysFor` from `RA.recurring`). Net Police refills to the current link at the start of the Corp's turn.
* (Broadcast Square now uses the bad publicity prevention window of `rules-ext-prevention.scala`: see `STATUS-fix-prevention.md`.)
* Director Haas / Victoria Jenkins: +1 / −1 allotted click applied as next-turn clicks at the end of the opponent's / own turn
  (`ExtraClicksEffect`); MCA Austerity Policy's "lose [click] when the Runner's next turn begins" is a −1 allotted click.
* Mark Yale gains for agenda counters spent through a scored agenda's ability (the first ability with an agenda counter cost is
  assumed) and through its own ability.
* Jeeves Model Bioroids counts clicks per action kind via `onActionTaken` (basic actions 1 click, purge 3, click abilities their
  click cost); double operations count 1.
* Sundew: "the first time the Runner spends [click]" = first click action (or click-played event) of the Runner's turn; the
  2-credit payment applies when the first run of the turn on its server starts during that first action.
* Genetics Pavilion caps draws at 2 per Runner turn ignoring draw bonuses. (Blacklist now locks the heap for every move and The
  News Now Hour blocks current events on every play path (see `STATUS-fix-restrictions.md`).)
* Warden Fatuma, Lt. Todachine 2 (−1 access on HQ/R&D only while tagged, never below 1), Ibrahim Salem (forfeit as an on-rez
  effect; still asked when rezzed ignoring costs), Lakshmi Smartfabrics (protection lasts while Lakshmi is active this turn),
  IT Department (strength bonus fixed at use time), Full Immersion RecStudio (trash cost bonus only while rezzed), Bioroid Work
  Crew (usable in the Corp's turn, like jinteki), Mumbad Construction Co. (moves to rezzed Corp cards only), Toshiyuki Sakai (no
  install event for the swapped card), Trojan follows jinteki (the Corp loses 2 credits).
* Shock!/Space Camp/News Team/Breached Dome/Honeyfarm/Shi.Kyū resolve in Archives through `RotAssetTriggers` (`Accessed` event);
  "must be revealed while accessed in R&D" needs nothing (accessed cards are always shown).
* Edge of World / ambush prompts use ASCII titles (Shi.Kyu, Elizas Toybox) in action strings.

## Shared-file edits (all one-term additive changes)

| File | Line | Change | Why |
|---|---|---|---|
| `game.scala` | 339 | `... with RWRRules with RotAssetRules {` | mix in the trait |
| `game.scala` | 682 | `... ++ ext ++ rotAssetTriggers(e)` | game-level triggers (Archives ambushes, executives, expose, registers) |
| `game.scala` | 753 | `... orElse performRWR orElse performRotAssets` | the new effects |
| `abilities.scala` | 128 | `... ++ URCards.all ++ RotAssetCards.all)` | register the cards |
| `decks.scala` | 252 | `... ++ ExtDecks.presets ++ RotAssetDecks.presets` | preset decks |

No other shared file was touched (`rules-*.scala`, `ui.scala`, `meta.scala`, `cards-data*`, `cards.scala`, `tools/` unchanged).

## Tested

* `sbt -batch compile` green (no warnings in the new files).
* JVM harness (scratchpad `rot-assets-host`, copy of `mergehost` pointed at this worktree; `MergeHost` gained
  `HRF_CORP_FILTER` / `HRF_RUNNER_FILTER` and a rot-asset mention tally; every offered and chosen action round-tripped through
  `Serialize`): 300 games (seed 4242) + 300 games (seed 99) with the 8 "(rot assets)" Corp presets against all Runner presets,
  and 150 mixed games over all 56 presets: **750 games, 0 exceptions, 0 empty asks, 0 serialization mismatches, 0 over-long
  games**. 139/144 rotated assets appear in the logs of each 300-game run (the 5 missing are the format-illegal 14004 – 14008);
  log counts confirm firing of e.g. Shock!, Psychic Field psi damage, Hostile Infrastructure, Broadcast Square prevention, Warden
  Fatuma subroutines, Sundew, Aryabhata Tech, Hyoubu Research Facility, Fumiko, CPC Generator, Toshiyuki swaps, hosted installs,
  executives and News Team / Franchise City / Public Support as agendas, recurring credit spending, Malia, Jeeves, Chief Slee.
* Scripted checks (`RotAssetScript`, 52 checks, all pass): Inez Delgado A / A 2 swaps, Lt. Todachine / 2, Trojan, Mr. Stone,
  Dedicated Response Team, Genetics Pavilion (draw cap, greyed click draw), Blacklist, The Board, Chairman Hiro as a 2-point
  Runner agenda without hand-size effect, Zaibatsu Loyalty + Psychic Field expose/psi, Mumba Temple (spent / kept on free rez),
  Primary Transmission Dish trace credits, The Root advance credit, Broadcast Square + Aryabhata Tech, Warden Fatuma, Shi.Kyū from
  HQ, Shock! from Archives, Synth DNA Modification (first time only), Hyoubu Research Facility + Fumiko, Lakshmi Smartfabrics,
  Echo Chamber, Contract Killer, False Flag, Elizabeth Mills, Long-Term Investment, C.I. Fund, Zealous Judge, Full Immersion
  RecStudio (hosting, trash cost, access), Jeeves Model Bioroids.

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
