# Netrunner plugin — status

## Implemented

* Plugin scaffolding: `package.scala`, `meta.scala` (name `netrunner`, Corp/Runner, bots `Smart`/`Basic`/`Random`, lazy card
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
* UI (`ui.scala`, `styles.scala`): Corp and Runner board panes with a status banner (every stat as a large badge of
  the same style: credits, clicks, points, bad publicity / tags, link, MU, hand, core damage, threat, mark, clicks next
  turn, turn; zeros subdued; see "Side indicator in the deck choice, uniform banner badges"), servers drawn from the Runner's point of view (root at the top of the
  server box, ice below it, outermost ice at the bottom), a "Runner" marker and orange outline showing the attacked
  server and the current ice position, unrezzed cards dimmed with an "unrezzed" tag for the Corp (backs for the
  Runner), facedown Archives counted, counters as badges (adv/virus/credit/power/strength), hosted cards under their
  host, rig grouped by type, scored/stolen agendas, accessed cards shown during a breach. The viewer's hand is shown
  in the action pane (see "UI rework" below). Clicking a card or a server performs the single matching action of the
  current prompt, or opens a menu of the matching actions (install destinations, break/pump, rez, advance, score,
  select toggles...), or opens the zoom overlay; cards with available actions are outlined. Hovering a card shows a
  large image of it. Deck selection is a gallery of presets with identity images on the board plus a decklist import
  overlay with readable error messages; a selected deck is previewed (its cards on the board) before it is confirmed; the
  gallery has faction / era filter tabs (see "Final pass"). Three layouts (boards left + log/actions right, wide, single
  column) for common resolutions. Hidden information is hidden by the viewer (`lastSelf`): R&D order never shown,
  unrezzed/facedown cards as backs, the opponent's hand only as the "hand" count in their header; the spectator of an
  online game sees public information only (see "Final pass").
* Bots: `Smart` (default, `bot-smart.scala`, see "Smart bot" below), `Random` (uniform over non-manual choices) and
  `Basic` (weighted heuristics: scores, steals, breaks, economy).
* JVM harness `host.scala` (excluded from the JS build): bot-vs-bot games with log dumps.

## Card sets

Every card of the card database has an implementation object: **2,056 cards** = 695 cards of the eleven current sets
(table below) + 1,361 rotated / FFG-era cards (second table). Per-set details, simplifications and the list of
generic mechanics each set introduced are in the set status files; the engine side of the merge is described in
`ENGINE.md` section 8.

| Set | Cards | Implemented | Simplified / approximated | Files | Details |
|---|---|---|---|---|---|
| System Gateway (sg) | 77 | 77 | none (see `STATUS-fix-misc.md`) | `cards-sg.scala` | this file |
| Elevation (elev) | 82 | 82 | 1 (Bling: hosted cards trashed when the turn ends, as jinteki; rotated grip effects do not list its cards) | `cards-elev.scala`, `rules-ext-startup.scala` | `STATUS-vp-elev.md` |
| Vantage Point (vp) | 66 | 66 | 1 (Reanimation Protocol: install-only fallback, as jinteki) | `cards-vp.scala`, `rules-ext-startup.scala` | `STATUS-vp-elev.md` |
| System Update 2021 (su21) | 82 | 82 | none (see `STATUS-fix-final-nsg.md`) | `cards-su21.scala`, `rules-ext-su21.scala` | `STATUS-su21-ms.md` |
| Midnight Sun (ms) | 65 | 65 | none | `cards-ms.scala`, `rules-ext-su21.scala` | `STATUS-su21-ms.md` |
| Uprising Booster Pack (urbp) | 7 | 7 | none | `cards-ms.scala` | `STATUS-su21-ms.md` |
| Parhelion (ph) | 63 | 63 | 1 (Mr. Hendrik: click-loss prevention as a choice) | `cards-ph.scala`, `rules-ext-ph-tai.scala` | `STATUS-ph-tai.md` |
| The Automata Initiative (tai) | 65 | 65 | none | `cards-tai.scala`, `rules-ext-ph-tai.scala` | `STATUS-ph-tai.md` |
| Rebellion Without Rehearsal (rwr) | 65 | 65 | none | `cards-rwr.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |
| Downfall (df) | 65 | 65 | none | `cards-df.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |
| Uprising (ur) | 58 | 58 | none; 7 ur codes are not in the card database | `cards-ur.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |

Preset decks (`Decks.presets`, 48): 7 System Gateway starters, 15 su21/ms decks (decks.scala), 8 Startup decks
(`StartupDecks`, cards-vp.scala), 8 ph/tai decks (`decks-ph-tai.scala`), 10 rwr/df/ur decks (`decks-rwr-df-ur.scala`); all
validate. With the rotated ports, `Decks.presets` holds **195** decks (all validate): the 48 above plus 8 rot-ice, 10
rot-programs, 11 rot-resources, 13 rot-events, 7 rot-ops, 8 rot-assets, 12 RotAg and 78 generated per-identity
rot-hardware/identity decks.

### Rotated / FFG-era cards (eight ports, merged 2026-10-14)

Ported from jinteki.net (`src/clj/game/cards/*.clj`, behaviour checked against the `*_test.clj` files), one port per card
type. "Simplified" counts the cards whose printed text is approximated or partly unenforced; each port's status file lists
them by name.

| Type | Cards | Implemented | Simplified / approximated | Files | Details |
|---|---|---|---|---|---|
| ICE | 211 | 211 | 1 (the encounter-on-access ice are not encountered when accessed while installed) | `cards-rot-ice*.scala`, `rules-ext-rot-ice.scala` | `STATUS-rot-ice.md` |
| Programs | 164 | 164 | 2 (Hivemind: no choice of the counters for break / pump virus costs; Surveillance Network Key: an operation played with the basic action that draws counts as "spends [click] to draw") | `cards-rot-programs*.scala`, `rules-ext-rot-programs.scala` | `STATUS-rot-programs.md` |
| Resources | 164 | 164 | none | `cards-rot-resources-*.scala`, `rules-ext-rot-resources.scala` | `STATUS-rot-resources.md` |
| Events | 148 | 148 | 1 (Rebirth: the format is inferred from the deck) | `cards-rot-events-*.scala`, `rules-ext-rot-events.scala` | `STATUS-rot-events.md` |
| Operations | 146 | 146 | none | `cards-rot-operations*.scala`, `rules-ext-rot-operations.scala` | `STATUS-rot-operations.md` |
| Assets | 144 | 144 | none | `cards-rot-assets*.scala`, `rules-ext-rot-assets.scala` | `STATUS-rot-assets.md` |
| Agendas | 114 | 114 | 1 (Personality Profiles: searches recognised from the printed text of the resolving card) | `cards-rot-agendas*.scala`, `rules-ext-rot-agendas.scala` | `STATUS-rot-agendas-upgrades.md` |
| Upgrades | 79 | 79 | none (Daruma's swaps re-check the root rules since the final NSG pass) | `cards-rot-upgrades.scala`, `rules-ext-rot-agendas.scala` | `STATUS-rot-agendas-upgrades.md` |
| Hardware | 101 | 101 | 1 (Patchwork's prepay ability kept for affordability in the basic menus) | `cards-rot-hardware-*.scala`, `rules-ext-rot-hardware.scala` | `STATUS-rot-hardware-identities.md` |
| Identities | 90 | 90 | none | `cards-rot-identities-*.scala`, `rules-ext-rot-hardware.scala` | `STATUS-rot-hardware-identities.md` |
| **Total rotated** | **1,361** | **1,361** | **6** (was ~176 before the four fix passes, ~70 before the final rotated pass) | | |
| **All cards** | **2,056** | **2,056** | **10** (current sets 4, one of them engine-wide + rotated 6; was ~280) | | |

The counts above are after the four rules-accuracy fix passes (credits, restrictions, prevention, card-specific fixes; see
"Rules-accuracy fix passes" below), the two final passes (`STATUS-fix-final-rot.md`, `STATUS-fix-final-nsg.md`) and their
integration; the exact list of the 10 remaining approximations is in "Integration of the final passes" below. An entry counts a
card whose printed text is approximated or partly unenforced; differences that match jinteki's own implementation, manual-only
choices and UI-only secrecy (Push Your Luck / Rigged Results amounts and Harishchandra's revealed grip are in every client's
journal; the UI hides / shows them) are not counted.

Mechanics that several rotated ports defined independently were unified in the merge (one definition each, see
`ENGINE.md` section 8): expose (`ExposeEffect(cards)` / one `Exposed(cards)` event, Corp responses such as Zaibatsu
Loyalty, `ExposePurpose`), facedown installs (`InstallFacedownEffect`, `FacedownInstalled`; facedown programs use no MU,
so Apex's MU offset was dropped), `LoseAllCreditsEffect`, `BreachNoRootEffect`, `SwapRootEffect` (`rules-ext-rot-shared.scala`);
trace results (`TraceResolved(successful, spent)`, fired once per trace; replaces `RotTraceSucceeded` / `RHTraceResolved`),
psi results (`RotPsiRevealed(corp, runner)`; replaces `RHPsiRevealed`), recurring-credit refills (`RefillCreditsEffect`,
now also for identities; replaces `RHRefillEffect`), recurring trace credits (one interception for hardware/identity and
asset sources: Making News + Net Police no longer double count), currents (one rule in `rotOperationTriggers` for Corp
and Runner currents; the duplicate in the events' `RE.lingerTriggers` was removed).

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

Known gaps carried over from the branches: none of the install-restriction kind ("remote server only" / "limit 1 region per
server" are enforced since the restrictions pass). (The UI gap for cards hosted without being installed and for cards set aside
on identities was closed in the QA pass, see "UI additions of the QA pass". Bad publicity credits are run credits and stealth /
purpose-restricted credits are enforced since the credits pass, see "Credits and costs" below.)

### Rules-accuracy fix passes (2026-10-14)

Four passes replaced most of the approximations listed by the set ports, each with one generic mechanism in its own file and a
per-theme status file (kept as the detailed record: what was fixed, what is still approximate, shared-file edits, tests):

* **Credits and costs** (`rules-ext-credits.scala`, `CreditRules`, `STATUS-fix-credits.md`): every payment has a purpose
  (`Spend`), every credit source declares what it pays for (`CardImpl.paysFor`); bad publicity credits are run credits;
  stealth-only costs (`StealthCreditCost`), RSVP ("cannot spend credits", run flag `no-spend-credits`) and Aircheck (pool locked)
  are enforced; icebreaker surcharges (Interrupt 0, Midway Station Grid, Net Watchlist), Wireless Net Pavilion, Network Exchange
  and Hackerspace are real costs. ~60 cards.
* **Restrictions** (`rules-ext-restrictions.scala`, `RestrictionRules`, `InstallRules`, `DeckRules`,
  `STATUS-fix-restrictions.md`): one legality check per action kind applied to every install / rez / play / score path (printed
  server restrictions, limit 1 region per server, remote limits, install locks, rez lockdowns, Blacklist, The News Now Hour,
  Vikram 1.0 / Wendigo, Always Be Running, The Black File), Satellite Grid (`advancementBonus`), agenda values with modifiers
  (`agendaValue`), per-identity deck validation. Six su21/ms presets had out-of-faction agendas and were fixed.
* **Prevention, interrupts and timing windows** (`rules-ext-prevention.scala`, `PreventionRules`, `STATUS-fix-prevention.md`):
  real prevention windows (damage, tags, bad publicity, end the run, jack out, trash, "when encountered" abilities, subroutine
  breaking) with forced interrupts and priority; new timing events (`ClicksSpentOrLost`, `AccessFinished`, `BreachFinished`,
  `RunWouldSucceed`), persistent run-end abilities, Patchwork as an interrupt. ~40 cards.
* **Card-specific fixes** (`rules-ext-fixes.scala`, `FixRules`, `STATUS-fix-misc.md`): ~95 card fixes and the generic support
  they needed (`ClicksSpent`, `AgendaCounterSpent`, `BreachEnded`, bioroid click-breaks as `SubsBroken`, granted / reordered /
  non-resolving subroutines, lingering restrictions, allotted clicks (`CardImpl.clicksPerTurn`), host subtypes
  (`CardImpl.hostSubtypes`), the real trace link strength).
* **Final pass of the rotated cards** (`rules-ext-final-rot.scala`, `FinalRotRules` mixed in after PreventionRules, ENGINE.md 8.7,
  `STATUS-fix-final-rot.md`): ~65 cards (rotated, plus the current-set cards that interact with them) and the generic support:
  sources of triggered abilities (every damage window knows the card doing it), unpreventable damage / trashes, "the next paid
  ability window after X", trash batches, Skorpios' pre-trash interrupt, the check after every action (`FrAddedToScoreArea`,
  `FrUninstalled`, The Black File), virtual tags, HQ / R&D accessed one card at a time, uninstalled ice encountered where they are,
  granted "when encountered" abilities in the prevention window, additional / alternative rez costs on every rez path,
  Hivemind for every virus program, "before your first turn", spending clicks by choice.

* **Final pass for the current sets** (`rules-ext-final-nsg.scala`, `FinalNsgRules` mixed in last, `STATUS-fix-final-nsg.md`, ENGINE.md 8.8):
  the player chooses the credit sources when two or more restricted sources could pay (Auto = the deterministic order, preselected
  and taken by bots); credit losses carry their source (`CreditsLostFrom`, GameNET); additional costs paid before the action (rez:
  Corporate Town, Archer, Plutus, Bloop, Biawak; score: Azef Protocol; trash-resource: Sebastião, Manuel Lattes; steal / trash:
  Daniela Jorge Inácio as real costs); a "trace initiated" window (Flip Switch, Disrupter, Power Tap); root moves / swaps re-check the
  root limits; any subset of subroutines can be broken (Matryoshka, every "break up to N"); "spend [click]" is a click cost; every
  "pay N" of the current sets goes through the credit engine; Bling, IP Enforcement, Reanimation Protocol, Hostile Architecture,
  Unsmiling Tsarevna, Bankhar, Front Company, Boi-tatá, Cataloguer, "Pretty" Mary, Nuvem SA, Cupellation, Whistleblower, MirrorMorph,
  double operations. ~45 cards; see "Integration of the final passes" for what is left.

#### Integration of the four passes

* **Mixin order**: `... with RotAgendaRules with CreditRules with RestrictionRules with FixRules with PreventionRules` (ENGINE.md
  8.6). PreventionRules is outermost because its overrides suspend an action (Patchwork, Tyr's Hand's break window, the jack-out
  window) and perform it again through `performInternal` afterwards: everything inside (FixRules' `ClicksSpent` / programs used /
  Mind Game's jack-out cost, the card-set rules) sees the action once, when it really happens. FixRules is next (its overrides
  are bookkeeping around the base rules and must see the real action). RestrictionRules' enforcement does not depend on the
  mixin order: `performRestrictions` is the first partial function of the `performInternal` chain (illegal effects are refused
  before any window or payment) and `restrictContinue` wraps the final result of every `performInternal` call (prevention-window
  asks included); its only stackable override (`rezCard`) has to be above `RotAssetRules`. CreditRules has no stackable
  override. Chain: `performRestrictions orElse <card-set and base rules> orElse performFixes`; triggers: `... ++
  preventionTriggers(e) ++ fixTriggers(e)`. Every override calls `super` (FixRules' `scoreAgenda` is a full copy of
  `TurnRules.scoreAgenda`; it now reads `agendaValue`).
* **Unified duplicates**: one resolving-subroutine stack (`PreventionRules.preventSubs`; `FixRules.fxResolvingSubs` is an alias
  and the `FxSubEffect` markers were removed: Banner, Improved Tracers), one trace link record (`fxTraceLinkStrength`; the
  credits pass's `runnerTraceBoost` was removed: Data Hound, Waiver, margin traces), one RSVP flag (`no-spend-credits` of the
  credits pass; FixRules' greying of the Runner's paying options reads `noSpending`), `payCosts(f, costs, self, spend)` (the
  prevention and fixes overrides take the credits pass's `spend`).
* **Cards touched by several passes** (owner's mechanism, consistent with the printed card and jinteki): AMAZE Amusements,
  Aeneas Informant, Divide and Conquer, Immolation Script, Heinlein Grid, Patchwork, LLDS Energy Regulator -> prevention
  (`PersistentEffect`, `AccessFinished`, `BreachFinished`, `ClicksSpentOrLost`; FixRules' own Aeneas / Divide and Conquer /
  AMAZE handling was removed, so nothing fires twice; Heinlein Grid reacts to `ClicksSpentOrLost` only, `ClickLost` is
  Adjusted Chronotype's); Paparazzi -> both (forced meat-damage prevention of the prevention pass + "you are tagged" of the fixes
  pass); The Black File -> restrictions (`corpCannotWin`) + the fixes pass's win re-check when it is trashed (FixRules'
  `checkWin` no longer handles it); Banner -> fixes semantics (only that barrier's subroutines) on the prevention stack; RSVP,
  Interrupt 0, Net Watchlist, Crash Space (remove-tag credits + damage window), Jackpot! (any number, 0 allowed as jinteki),
  GameNET (steal costs now fire `CreditsPaid(.., Spend.corpAbility)` instead of a fake `CreditsLost`) -> credits; Biawak
  (choice of the fixes pass, the 10 credits paid through `Spend.rez`), Guru Davinder / Oppo Research (paid through
  `SpendEffect`), Tyr's Hand's rez offer (`canSpend` + `rezBlock`) -> credits / restrictions checks added to the other passes'
  code; Daniela Jorge Inácio / Tucana -> fixes (stolen-from server) + restrictions (remote only); Data Hound / Waiver -> fixes
  (`fxTraceLinkStrength`). Restricted programs (Vikram 1.0 / Wendigo) are also left out of prevention windows
  (`programUseBlocked`), so a window is not opened for a greyed-out ability.

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
* Card images: a printing whose image failed to load (or is not embedded in a saved replay) is drawn as the card back
  with the title on top (board, hand, zoom, menus, deck gallery). The two unreleased identities without an image on
  NetrunnerDB (014001 The Collective, 014002 Chronos Protocol) now have generated images (see "Final pass"). Card
  images have a fixed height so the layout does not jump while images load lazily.
* Layout robustness: each board is scaled to fit its pane (all card sizes are in ex; see "UI rework" for the current
  rule), so many remotes and 10+ ice per server stay on screen (tested with 24 cards in HQ, 8 remotes and 9 ice on HQ
  before the rework; 8 servers / 10 ice in a bot game after it). Multi-value `margin`/`padding` styles were silently
  dropped by the HRF CSS generator; they are now written per side.
* Game end: the run line / attacked-server marker / "turn N" badge are hidden once the game is over. The game-over
  prompt offers "Save Replay" (restored in the final pass, see there), online games add "This game is also saved on the
  server: open any of its links to replay it"; local games offer "Save Game Online" (as Coup does) and "Play Again".

### UI rework after the first play test (product owner requests)

* Deck preset names carry no set codes any more (`decks.scala`, `decks-ph-tai.scala`, `decks-rwr-df-ur.scala`, the
  Startup presets in `cards-vp.scala`): e.g. "HB Architects of Tomorrow", "Jinteki Personal Evolution", "HB Thule
  Subsea", "Weyland Earth Station", "Elevation Anarch Ryo". The gallery and the action pane list also strip any trailing
  parenthesised suffix (`UI.cleanDeckName`), so presets added later with "(set)" suffixes are shown clean. The action
  pane list shows identity thumbnail, deck name and identity on its own line (`UI.convertActions` override for
  `ChoosePresetDeckAction`); gallery items show the identity image (hover zooms it), the name and size/influence.
* Zoom: hovering a card (board, hand, deck gallery) shows the card image only, on a transparent background; clicking
  a card without actions opens the image-only overlay. The printed text, stats and card state are shown beside the
  image only when the image is missing (014001/014002 or a failed load). The zoom is dropped on every re-render so
  it cannot stay stuck after the hovered card disappears.
* Bigger cards: only the board area of a pane is scaled (`styles.boardArea`; the header keeps the pane font size).
  `UI.fitPane` picks the largest size at which the board fits its pane (binary search on the font size), capped so
  that a board card (cardSmall) is at most 40% of the pane height; boards therefore use the pane width and shrink only
  when they would overflow (more servers, taller ice stacks, a bigger rig). Server names, counts, section titles and
  the "Runner" marker use clamped font sizes so they stay readable without dominating. During setup only the deck
  gallery is scaled. Checked at 1440x900 and 1920x1080.
* Hands in the action pane: the boards no longer draw any hand. The viewer's hand is drawn above the prompt in the
  action pane, as `HandCardInfoAction` Info items (`ViewObject[Card]` + `OnClickInfo`, built in `UI.preinfo` and
  rendered through the asker's `ZOption`s with hover zoom), titled "HQ / Grip (n cards, maximum m)". Clicking a hand
  card there does exactly what a board click does (the single matching action, or the menu of matching actions, or
  the zoom). Cards with available actions are outlined. When a selection prompt already lists the hand cards
  (discard to hand size, choose cards from HQ...) the hand is not repeated. A viewer without a seat sees both hands,
  labelled: faceup in a local bot-vs-bot game or a replay file, as card backs for the online spectator. The opponent's
  hand is only the "n/m hand" stat in their header. The
  Info items are built by the UI rather than `Game.info` so that `game.scala` (shared with the set ports) is untouched.
* Server orientation: with the Corp on top, each server box shows its name, then the root (central pile / installed
  cards), then the ice with the innermost piece just under the root and the outermost at the bottom; the "Runner"
  marker is drawn below the piece of ice being approached / encountered and between the root and the innermost ice
  once the Runner approaches the server.
* Small header tweaks: identity title slightly smaller, stats tighter, the "turn N" badge no longer says "running"
  (the run line below the header does), so the Runner header fits one line at 1440x900.

### Status banner and deck preview (second product owner pass, 2026-10-14)

* (Superseded by "uniform banner badges" below: every stat is now a large badge.)
  Status banner (`UI.statusBanner`, `UI.bigStat`; styles `bigStat`, `bigValue`, `bigLabel`, `bigCredit`, `bigClick`,
  `bigPoints`, `bigTag`, `bigEmphasis`, `bigSpent`, `headerActiveCorp/Runner`, `banner*`): each board header shows the
  player name with the identity title under it, the "turn N" badge, then credits, clicks and agenda points as large
  coloured badges (number at 240% over a small label: blue credits, yellow clicks, purple points). Tags (Runner) and bad
  publicity (Corp) become a large red badge when non-zero (small "0 tags" otherwise). Link, MU, hand, threat, core
  damage, mark and "clicks next turn" stay small. The active player's banner is tinted with their colour and underlined,
  and their credits / clicks badges get a white outline and glow; the clicks badge is dimmed once the active player
  has no clicks left. The banner is one line at 1440x900 and 1920x1080 (it wraps tidily on narrower panes); it is not
  part of the scaled board area, so it does not shrink with the board.
* Deck preview before choosing (`UI.DeckPreview`, `preview`, `deckPreviewElem`, `convertActions`): clicking a deck in
  the board gallery or in the action-pane list no longer chooses it. It sets a UI-only preview (nothing is submitted, the
  journal is unaffected) and redraws: the viewer's board pane shows the deck name and identity, a summary line (cards and
  minimum, agenda points for the Corp, influence used/limit, "legal" or the validator message) and the cards, identity
  first and then grouped by type (agendas / assets / upgrades / operations / ice; events / hardware / programs /
  resources) with "3x" counts and influence dots in the card's faction colour; hovering a card zooms it, clicking opens it.
  The action pane then shows "Previewing <deck> (n cards)", a large "Confirm <deck>" button, "Back to the deck list",
  and the preset list again ("Preview another deck", the previewed one outlined); the decklist-import entry stays below.
  Only Confirm submits, and it submits the `ChoosePresetDeckAction` of the current prompt (`confirmAction`). The board
  preview also has a "pick another deck" link.
* Decklist import: the overlay keeps the pasted text between openings; "Check and preview" validates as before
  (errors stay in the overlay) and, for a valid list, closes the overlay and previews the parsed deck the same way
  ("Imported deck"); the action pane then offers "Import <identity>" (submits `ImportDeckAction`), "Edit the decklist"
  (reopens the overlay with the text) and "Back to the deck list". The overlay no longer has a direct "Import" button.
* `rules-setup.scala` is unchanged (the preview is entirely UI state).

### Final pass: spectators, Save Replay, generated card images, deck gallery filters (2026-10-14)

* Spectators (`UI.viewer`, `UI.seats`, `UI.omniscient`, `UI.sees`, `UI.canSee`, `UI.handInfos`). Rule, documented at
  `UI.viewer`: a seated viewer sees their own hidden information plus everything public; a viewer without a seat sees
  everything only in a local game (bot-vs-bot, hotseat between prompts: `callbacks.canPlayAgain`, the journal is in
  memory) or in a replay file (`HRF.replay`, saved once the game is over); the spectator of an online game sees public
  information only: both hands as card backs under "Corp HQ (n cards)" / "Runner Grip (n cards)", unrezzed ice and
  root cards as backs (advancement counters stay visible), facedown Runner cards (rig, hosted, stashed) as backs, the
  facedown part of Archives as a count with a card back on top, R&D / the stack as backs and counts, no hover zoom or
  overlay on hidden cards, "unrezzed ice" in the run line. Secret choices are not on the board; the log only says
  "Corp bids secretly" / "spends credits secretly" until the reveal (engine, unchanged). Because the framework asks
  with no faction on the game-over screen, the UI remembers the seats this client played (`seats`, from `ask` / `wait`)
  and, after reloading a finished online game, reads the seat from the page title HRF sets for player links
  ("Corp - <title> - Netrunner"); before this, players saw both hands at game over and the spectator saw everything.
* Save Replay restored (`UI.saveReplay`, game-over prompt). Framework change (`quine.scala`): a `MetaGame` mixing in the
  new marker trait `hrf.quine.EmbedLoadedOnDemandAssetsOnly` gets its `Laziness.OnDemand` image assets embedded only if
  they were loaded in this browser session (`resources.images.loader.has(url)`); every other asset of every game is
  embedded as before. Any asset whose load failed is now left out (logged) instead of throwing "url not loaded", and a
  CSS `url(...)` that failed keeps its original URL. `netrunner.Meta` mixes the trait in; before saving, the UI loads
  the images of every card of the game (both decks, identities, cards in any zone), the two card backs and the two
  identities' faction logos (overlay "Preparing the replay file"; images that fail or take more than 30 s are left
  out). `hrf.scala` (`startGame` and the "Offline Version" download): when a page runs from a file with embedded
  assets, a lazy asset without an embedded `<img id="asset-...">` is skipped instead of throwing "embedded asset not
  found" (Immediate assets are handled as before). `loader.scala`: `DataUrlLoader` treats an HTTP status >= 400 as a
  failed load (it used to embed the error page as the image). `UI.imageMissing` treats a card that is not embedded in a
  replay file as missing (card back + title). A replay of a Netrunner game is ~68-70 MB with the development build
  (66 MB of fastOpt script + 2-4 MB of images for ~50-110 assets).
* Card images for 014001 The Collective and 014002 Chronos Protocol: `tools/make-placeholder-cards.py` renders 300x419
  cards in the NSG identity layout (faction frame and title band, subtitle, faction logo panel, minimum deck size /
  influence / base link, `IDENTITY: subtypes`, wrapped rules text with a click icon, set footer) into
  `webp2/netrunner/images/`; `fetch-assets.py` calls it for every code without a downloadable image (see
  `tools/README.md`). The UI no longer special-cases the two codes (`knownMissingImages` removed).
* Deck gallery (`UI.deckSelection`, `galleryTabs`, `eraOf`, `presetFaction`, `presetShown`; styles `tabRow`, `tab`,
  `tabOn`, `tabCount`): two rows of small tabs with counts above the gallery, factions of the side's identities
  (coloured: HB / Jinteki / NBN / Weyland / Neutral, Anarch / Criminal / Shaper / Adam / Apex / Sunny / Neutral) and
  eras (Starter: System Gateway cards only; NSG era: only cards of NSG sets, Downfall onwards; FFG era: at least one
  card of an FFG set); counts of one row follow the selection of the other; "n of m decks shown". Gallery thumbnails
  are larger (15ex wide) and the setup gallery / deck preview are never scaled below 75% (the pane scrolls instead of
  shrinking ~100 identities to stamps). The action pane deck list shows the same filtered decks (plus the previewed
  one) and "n more decks hidden by the filters above the board gallery"; preview / Confirm / Back work as before and
  the filters survive "Back to the deck list". Each gallery item shows its era next to size and influence.

### Side indicator in the deck choice, uniform banner badges (third product owner pass, 2026-10-14)

* "How do I see Corporation decks if I want to play as Corp?" Cause: the deck screen never said which side it was for,
  and the side is not chosen there. (1) Quick Game, the first button of the menu, seats the human on a random side
  (`MetaBase.randomQuickGame`: Corp or Runner with equal odds, the other side a bot), so a player could get the Runner
  gallery repeatedly with no hint why or how to change it. (2) In a hotseat game the framework shows the first human
  ask of a `MultiAsk` (`runner.scala`, `UIContinue(MultiAsk)`: debug bot, then human, then bot), and the deck prompt
  listed the sides in seating order, so a game seated Runner-first asked the Runner first. The gallery's side filter
  itself was correct (the presets of the prompt's faction, keyed on the action's `self`).
* Fix: `rules-setup.scala` (one line, `ChooseDecksAction`) asks `$(Corp, Runner)` in that order, so in a hotseat game
  the Corp always chooses first and the Runner second whatever the seating (online each link still chooses at the same
  time; the journal and the validation are unchanged). The deck screen (`UI.deckSelection`, `deckSteps`, `sideHint`,
  `isQuickGame`, `playOtherSide`; styles `deckTitle`, `sideTitle`, `sideTitleRest`, `sideName`, `sideTag`, `deckSteps`,
  `deckStep`, `deckStepOn`, `sideHint`) shows a large "Corp: choose a deck (Corp decks only)" title in the side's colour,
  a two-step line "Corp: choosing now | Runner: not chosen yet" (or "Corp: chose <identity>") with the current side
  outlined, "n of m Corp decks shown", and "Corp" / "Runner" tags on the deck preview, the action pane groups ("Corp:
  choose a deck", "Corp: previewing ...", "Corp: preview another deck") and the other board ("Runner: no deck chosen
  yet", "Corp deck chosen: ..."). In a Quick Game (`/play/netrunner/quick` or `?quick`, local journal) the deck screen
  says "Quick Game seated you as the Runner (it picks a side at random). Play the Corp instead" and explains how to
  choose a side with Local Game (select Corp and Runner, Start Setup, click Human next to the side the bot should play);
  "Play the Corp instead" (also a button at the end of the action pane) restarts the Quick Game with
  `/play/netrunner?quick=1&human=Corp` (an existing HRF quick-start parameter, `location.replace` so the new page's
  history rewind cannot return to the old one). HRF's own Local Game setup screen (Human / Bot toggles per side) was
  left as it is.
* Status banner (`UI.statusBanner`, `bigStat`, `corpStats`, `runnerStats`, `extraClicksStat`, `markMatters`; styles
  `bannerStats`, `bigBadPub`, `bigThreat`, `bigHand`, `bigLink`, `bigMU`, `bigCore`, `bigMark`, `bigTurn`, `bigAlert`,
  `bigZero`, `bigWord`): every stat is now the same large badge as credits / clicks / points (value at 220% over a small
  label, each with its own colour): Corp: credits, clicks, points, bad pub, threat (while a card in play mentions it),
  hand n/max; Runner: credits, clicks, points, tags, link, MU used/total, hand n/max, core damage, mark (the server name
  in a smaller font, while set or while a card in play says "your mark"), plus "clicks next turn" when non-zero and the
  turn number on the active player's banner. Zero values stay in place, subdued (55% opacity, desaturated); tags, bad
  publicity and core damage above zero get a red outline. The small `stat` pills and their styles are gone. Padding and
  margins were reduced so the banner stays on one line: at 1440x900 the busiest Runner banner (13 badges incl. turn,
  mark and clicks next turn) is 730 px of a 1000 px pane; the identity title stays under the player name (cut with an
  ellipsis beyond 36ex). The banner is not part of the scaled board area, so the board does not shrink.

### Smart bot (2026-10-14)

`netrunner/bot-smart.scala`, registered in `meta.scala` as `Smart` and made the default bot (`Meta.defaultBot`, quick
games); `Basic` and `Random` are still selectable. One `EvalBot` score per offered action on a common scale (10 = one
credit, 100 = one agenda point), from generic data only, so it plays any deck:
* card text keywords, cached per code (`SmartProfile`): one-shot gains, credits loaded on resources / assets, "when your
  turn begins" income, recurring credits, click gains, draws, run events, +MU / +link, tag punishment, damage, traps;
  the engine's own implementations: icebreaker `breaks` / `pumps` (with surcharges, `canBreakWith`, `preventsBreak`,
  `breakAllowed`), ice `subs`, ability costs and effect lists (`GainCreditsEffect`, `TakeCreditsEffect`, `DrawEffect`,
  `DamageEffect`, `TagsEffect`, `EndRunEffect`, `PumpBreakerEffect`, ...);
* Runner: runs scored as pass odds x access value - break cost - harm, where known ice gets the cheapest break plan of
  the installed breakers and unrezzed ice an estimate from the Corp's credits and the breaker types covered; access
  value from agenda odds of HQ / R&D, known Archives agendas, advanced / unknown remote cards (traps included), trash
  value of assets; encounters follow the cheapest plan (pump, then break ETR / harmful subroutines) or let them fire;
  jacks out when the rest of the run is not worth it; installs breakers for missing ice types, economy first when poor;
  removes tags when the Corp can punish them; damage adds up per run and an empty grip makes face-checking a flatline;
  the Runner only uses public information plus the Corp cards it accessed (`SmartMemory`, per game object);
* Corp: ice where it is needed (HQ / R&D first, then a scoring remote when agendas are in HQ), keeps credits to rez,
  installs agendas where the Runner cannot get in (its breakers and credits against the ice the Corp can rez),
  advances there, installs + advances + scores in one turn when it can, scores at once; economy assets in their own
  remote, rezzed when needed (click assets) or at the last moment (end of the Runner's turn / start of its own);
  rezzes approached ice when it protects something; trashes resources of a tagged Runner; purges at 3+ virus counters;
* every prompt: optional effects / choices / card choices (purpose + prompt keywords), traces (bid exactly what is
  needed when worth it), psi (random), prevention windows, discards (lowest keep value), mulligans (no ice / economy);
  large card selections (> 12 cards) are made greedily instead of exploding every combination;
* credits are worth less the richer a player is (avoids endless clicking for credits and stalemates); a failing heuristic
  scores 0 instead of breaking the game (`SmartBot.errors`, 0 in all test runs).
Decision time on the JVM (single process, 50 Smart-vs-Smart games): p50 0.35 ms, p99 3.4 ms, p99.9 6.6 ms, max 29 ms
(first decision, JIT warm-up), the same as `Basic` (the engine's explosion of the menus dominates).

### Integration of the final passes and the Smart bot (2026-10-14)

The final NSG pass (`FinalNsgRules`), the final rotated pass (`FinalRotRules`) and the Smart bot were merged on `netrunner`.

* **Order**: `... with CreditRules with RestrictionRules with FixRules with PreventionRules with FinalRotRules with FinalNsgRules`
  (`FinalNsgRules` extends `FinalRotRules`). Chain: `performRestrictions orElse performFinalNsg orElse <card-set and base rules>
  orElse performFixes orElse performFinalRot`; triggers: `finalRotPreTriggers ++ <cards' triggers, each between FxSourceEffect
  markers> ++ ... ++ preventionTriggers ++ fixTriggers ++ finalRotTriggers ++ finalNsgTriggers`; `restrictContinue` =
  `fnsFilter` -> FinalRotRules' check after every action -> RestrictionRules' filter, still wrapping the final result of every
  `performInternal` call. Every override of both traits calls `super` / `base`. FinalNsgRules is outside FinalRotRules because of
  `rezCard` (Archer / Corporate Town / Plutus / Bloop / Biawak's additional cost is queued first and the rez is performed again;
  with FinalRotRules outside, Hernando Cortez's surcharge would have been charged twice), `performRun` (the subset break is also
  offered on encounter-on-access ice) and `performAccess` (Gagarin's pay-or-skip choice first, then the "when accessed" abilities
  are wrapped as sources). The credit-source choice suspends a user action before any other rule (second in the chain) and
  performs it again with a marker, so PreventionRules' interrupts, FinalRotRules' Hivemind / Armored Servers / Faust costs and
  FixRules' bookkeeping see it once (ENGINE.md 8.7 / 8.8).
* **One source mechanism**: FixRules' `FxSourceEffect(card, on)` markers (the two passes had added `FnsSourceEffect` and reused
  `FxSourceEffect`); `fnsSourced` = `frSourced`; card triggers are wrapped once (game.scala), paid abilities by FixRules only (the NSG
  pass's second wrapping of `useAbility` was dropped); "when encountered" / "when accessed" / "when rezzed" / prevention abilities
  are wrapped by FinalNsgRules. `effectSource` (GameNET's `CreditsLostFrom`) is `frSourceNow`, the same card the damage windows
  get as their source; stale markers (whose closing marker left the queue) are forgotten when a turn ends.
* **One "spend [click]" effect**: `SpendClicksEffect(f, n)` (a click cost: `ClicksSpent`, `ClicksSpentOrLost` during runs,
  windows reopened, no `ClickLost`); `FrSpendClicksEffect` was removed (Aggressive Trendsetting, Deep Dive, the ph "spend [click]
  or end the run" ice, Thule Subsea, Manegarm Skunkworks, S-Dobrado, Jaguarundi use it).
* **Overlapping cards**: Bling -> the NSG version (hosted cards offered by the basic play / install actions; the rotated pass's
  credit-engine check of its removed click ability is moot); Thule Subsea -> NSG (`SpendEffect`, a Corp-imposed payment); Daruma
  -> rotated card logic (trashed first) + NSG root re-check (`SwapRootEffect` / `SwapInstalledWithHandEffect`: its swaps now keep
  one asset / agenda / region per root, so the rotated pass's Daruma approximation is gone); Power Tap / Compromised Employee /
  Flip Switch / Disrupter -> the NSG "trace initiated" window; Hivemind's `VirusCost` in `canPay` -> rotated; `ExtCost` in
  `canPay` / `Cost.desc` -> NSG.
* **Smart bot and the new prompts**: the credit-source choice -> always `CreditAutoAction` (the manual options are
  `NotForBots`); Gagarin Deep Space -> pays when the access is worth it (`rootCardValue` of the card minus the credit; an
  unknown card with an empty grip may be a trap, so it refuses then); the "trace initiated" window -> Flip Switch / Disrupter
  used when the base strength is above 0 (worth the base strength in credits); the subset-break multi-select -> never opened (the
  ordinary break options stay available); Hivemind's counter choice, Tithonium's forfeit and Surveillance Sweep's first boost
  are scored too.

**Remaining approximations (exact list, 10)**:

1. Reanimation Protocol (36062): when the Corp cannot pay the combined install and rez cost (minus 10) the ice is installed
   unrezzed (jinteki's behaviour; the printed text gives no fallback).
2. Bling (35006): hosted cards are trashed when the Runner's turn ends (jinteki's `:runner-turn-ends`) instead of "when your
   discard phase ends"; card effects of the rotated sets that pick from the grip do not list Bling's cards.
3. Mr. Hendrik (33103): "lose all [click] to prevent this damage" is a choice before the damage, not an ability in the damage
   prevention window (same outcome).
4. Credit-source choice (engine-wide): offered for the user actions listed in `STATUS-fix-final-nsg.md` when two or more
   restricted sources could pay; payments inside other effects (a `SpendEffect` that is not the first effect of a choice,
   card-specific prepaid installs, psi bids, secret spending) use the default order, and a surcharge paid just before the action
   with the same purpose (Net Watchlist, Hernando Cortez) takes the chosen sources first.
5. Hivemind (07042): a `VirusCost` of a break or pump ability spends the program's own counters first, then Hivemind's (no choice).
6. Patchwork (25009): the basic play / install options stay greyed when only Patchwork's discount would make them affordable,
   so its non-printed "prepay" ability is kept for that case.
7. Archangel / Chrysalis / Herald / Sapper accessed while installed (only through cards that access installed ice) are not
   encountered.
8. Personality Profiles (10066): a search is recognised as a stack shuffle while a card whose printed text says "search your /
   the stack" resolves.
9. Rebirth (10083): the format is inferred from the Runner's deck (the game has no declared format).
10. Surveillance Network Key 1 / 2 (14018 / 14019): an operation played with the basic action that draws counts as "spends
   [click] to draw" (jinteki only implements the basic draw action).

## Simplifications / known gaps

* Paid ability windows follow the NSG structure (see ENGINE.md): start of turn (before and after "when your turn
  begins"), between actions (the opponent's window is non-blocking and shown next to the active player's prompt),
  after discarding, run initiation, approach ice, encounter (Corp side), after passing ice (movement), approach
  server. Players who have nothing usable are skipped (greyed-out options such as an Abagnale bypass outside an
  encounter do not open a window); a player who declined a set of options is not asked again
  for the same options until something happens. In a hotseat (both humans in one browser) only the active
  player's prompt is visible, so the off-turn window is effectively unavailable there (use online play).
* The rules accuracy pass of `STATUS-fix-misc.md` replaced many card approximations with the exact behaviour (real granted
  subroutines, bioroid click-breaks as "subroutines broken" events, allotted clicks, lingering restrictions that outlast their
  ice, ...). Brân 1.0 installs the ice directly inward from itself (= jinteki's index) and Tāo's swap keeps the Runner's position
  (as jinteki); swaps no longer name unrezzed ice in the log. (AMAZE Amusements' persistence is the prevention pass's
  `PersistentEffect`.)
* Traces and psi games are exercised by later-set cards (Winchester, Punitive Counterstrike, Konjin, Letheia Nisei,
  See How They Run) and were played in the QA online games (see Tested).
* Prevention / interrupts: damage, tags, bad publicity, end the run, jack out, trash (installed Runner cards), "when
  encountered" abilities and subroutine breaking (Tyr's Hand) go through the prevention windows of
  `rules-ext-prevention.scala` (forced interrupts first, then both players with priority, the active player first); see
  `STATUS-fix-prevention.md` and ENGINE.md section 8.4. "When a trace is initiated" abilities (Flip Switch, Disrupter, Power Tap)
  use the "trace" window of `rules-ext-final-nsg.scala` (ENGINE.md 8.8).
* Bots: `Smart` (default) plays a reasonable game (see "Smart bot"); `Basic` / `Random` are weak. Bots never use the manual
  menu or concede (when an ask offers only options not meant for bots, the Smart bot still prefers anything but the manual
  adjustments and the concession), always take the
  automatic credit-source option and never open the subset-break multi-select (both `NotForBots`).
* Hidden information is hidden by the UI only (every client replays the whole journal, so a determined spectator could
  read it from the browser's memory; the game creator receives all links anyway); the Manual menu lets the Corp move
  the top cards of R&D. The visibility rule is in `UI.omniscient` / `UI.canSee` (see "Final pass"). The shared log never
  names a Corp card the Runner has not seen (Hansei Review / sabotage / random trashes from HQ are logged as "a card"),
  never names the Runner's draws, and says only that a psi bid / secret spend was made until it is revealed.
* Online play: both clients (and the spectator) race to append framework milestones and shuffle results; the losers
  get `HTTP 409` (players) / `HTTP 500` (spectator, read-only) in the console. This is the HRF framework's design and
  harmless (the journal stays consistent, replays are identical).
* Cards accessed while installed and left in place stay visible to the Runner's UI until they move (`fxRunnerKnows`; they
  are not "seen", so a later trash from there still goes to Archives facedown).
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

### Tested after the UI rework (2026-10-14)

* `sbt -batch fastOptJS` (with `-J-Xmx8g`) passes. Headless Chrome over the DevTools protocol at 1440x900 and
  1920x1080: deck selection (gallery, action-pane list, hover zoom of a gallery identity), a hotseat game saved online
  (lobby `zzdxnlubkgsdeqng`, Corp `txaecjnvlgjlfmhy`, Runner `xzmdicfhnivwqyjr`, spectator `wybzbcudmwrctyuf`):
  mulligan, Corp installs from the action-pane hand (Whitespace on HQ through the menu, Regolith straight into a new
  remote, Whitespace on Server 1 through the server menu), runs on HQ and Server 1 with the Corp's rez windows, rez
  and encounter, Runner installing Marjanah from the hand pane, both player links and the spectator link replayed on
  the new builds; a local game as the Runner against the Basic Corp bot (Tao preset against Synapse; several turns
  with installs and events played from the hand pane, runs, ETR choices, Tao's ice swap prompt); a bot-vs-bot game
  (`?speed=20`) to the end (8 servers, 10 ice: the Corp board shrinks to fit). No plugin exceptions in the consoles
  (only the shell's `NetworkError` font entries and 404s for images missing from the local cache).

### Tested after the merge of the eight rotated ports (2026-10-14)

* `sbt -batch compile` and `sbt -batch fastOptJS` green.
* JVM harness (`MergeHost` over all 195 presets, every offered and chosen action round-tripped through the serializer,
  empty asks and over-long games counted as errors): 40 + 3 × 300 games over all presets (seeds 1, 1001, 2002, 3003) and
  2 × 400 games rotated Corp presets (rot-ice / rot-ops / rot assets / RotAg) against rotated Runner presets (programs /
  rot-res / events / hardware-identity), seeds 11 and 22: **1,740 games, 0 exceptions, 0 empty asks, 0 serialization
  mismatches, 0 over-long games**. In the focused runs 179-183/211 ice, 123-135/164 programs, 121-126/164 resources,
  115-126/148 events, 137-138/146 operations, 133-139/144 assets, 106-107/114 agendas, 77-78/79 upgrades, 95-97/101 hardware
  were named in the logs (identities: 75/90 over the all-preset runs).
* Scripted checks, all passing against the merged code: Startup `ScriptTest` (40), `RotIceScript` (211 ice, 633
  encounters, 0 failures), `RotAssetScript` (52), the agendas/upgrades (34), events (40), hardware/identities (80),
  operations (56) and programs (50) `RotScriptTest`s, plus a new merge check (`RotMergeScript`, 20 checks: one `Exposed`
  event for a group with Zamba / Aumakua / Psychic Field, Zaibatsu Loyalty preventing one of two exposes, Runner and Corp
  currents trashing each other and on score/steal, `TraceResolved` once with Spinal Modem + Aryabhata Tech, Making News +
  Primary Transmission Dish trace credits without creating credits, `RotPsiRevealed` once with Nisei Division + Hyoubu,
  Apex facedown program using no MU, identity refill).
* Browser (headless Chrome, good-game server on 7070): a bot-vs-bot game (`?speed=20`) to the end; a hotseat game with
  "Weyland A ice (rot-ice)" (Weyland Consortium: Building a Better World, core) against "Anarch Events I (Rotated)" played
  for 9 turns through 180 random human clicks (installs, rotated ice rezzed: Negotiator, Battlement, Salvage, Formicary;
  rotated events: Frame Job, Itinerant Protesters, I've Had Worse, Amped Up, Vamp, Forked; runs, an agenda steal). No
  plugin exceptions in the console (only the known `NetworkError` / 404 entries of the shell).

### Tested after the status banner / deck preview change (2026-10-14)

* `sbt -batch fastOptJS` green. Headless Chrome (DevTools protocol, good-game on 7070) at 1440x900 and 1920x1080:
  hotseat game (Corp previews Jinteki then Weyland Starter, "Back to the deck list", previews again, Confirm; Runner
  previews Shaper Starter; decklist import of a 45-card Zahya list: "Check and preview", "Edit the decklist" restored the
  text, Import), mulligans, Corp installs ice on HQ / R&D, Runner run on HQ with the Corp's rez window, then 60
  automated hotseat clicks; local game human Runner (Criminal Starter preview + Confirm) against the Basic Corp bot for
  10 turns (runs, installs, a tag shown as the large red badge); local game human Corp (NBN Starter then HB Architects
  of Tomorrow preview + Confirm) against the Basic Runner bot for several turns (discard phase, installs, rez windows).
  No plugin exceptions in the consoles. The only non-network console entry, `later failed` (framework, `runner.scala`:
  an action callback of an ask that is no longer current is ignored), appears only when the automated clicker clicks
  elements of an ask that was just replaced; it did not appear in the step-by-step runs.
* Screenshots: scratchpad `ui3/shots/` (`s2-preview-corp-zoom-1440`, `s7-preview-runner-confirm-1440/1920`,
  `s13-corp-preview-1440`, `s3-import-preview-1440/1920`, `s5-run-1440/1920`, `s10-runner-run-1440/1920`,
  `s12-midgame-run-1920`, `s11-after-auto-1440`).

### Tested in the final pass (2026-10-14, final build)

* `sbt -batch fastOptJS` green. Headless Chrome on port 9666 over the DevTools protocol, good-game on 7070, each link in
  its own browser context (separate cookies: anonymous visitors), driver in the scratchpad `fin/` (`daemon.mjs`,
  `helpers.js`, `play.mjs`).
* Online game "Steal versus Ice" (Play Online > New Online Game > Corp + Runner > Start Setup > Start): Corp
  `qnxihhxxekmdfele`, Runner `yvicchjugwloldgp`, spectator `uxywrhbpfcsyhqgl`; both player links asked only for a
  name. Corp: gallery filtered to FFG era + Weyland (20 of 103 decks), previewed and confirmed "Titan Transnational"
  (FFG-era preset, Titan Transnational: Investing In Your Future, 46 cards). Runner: NetrunnerDB decklist import
  (Zahya Sadeghi, 45 cards, 7/15 influence), "Check and preview", "Import Zahya Sadeghi". Both kept; then about 300 rounds of
  clicks alternating between the two links (installs into new remotes, Business As Usual, Key Performance Indicators,
  advancing, rezzes in windows, Abagnale / Carmen / Marjanah / Pennyshaver installs, Jailbreak, runs on HQ / R&D /
  Archives / remotes, an agenda steal (3 points), Archives breach turning cards faceup, Syailendra's subroutines) until
  the Corp won on turn 11 by flatlining the Runner. Every 8 rounds the card images rendered in the spectator tab were
  compared with both player tabs: the spectator never showed a card image that was not visible in both players' views
  (36 checks, 0 differences); screenshots show both hands as backs, unrezzed ice / root cards as backs, Archives'
  facedown card hidden. Game-over screen on all three tabs; reloading the finished game on each link (Corp sees its
  HQ and unrezzed cards, spectator still sees backs). Save Replay from the Runner link produced a 70 MB file with 108
  embedded assets that opened from `file://` (game over screen, full information, clicking a log line rewound to turn 6).
  No exceptions in any console (only the shell's `NetworkError` / 404 font noise of the local setup and the known
  `HTTP 409` append races).
* Local bot-vs-bot game (`?speed=20`): Save Replay (68 MB, 47 assets) opens offline and shows the final position with
  the hands. Coup (local quick game with placeholder webp images in the git-ignored `webp2/coup/images`, which this
  checkout lacked): Save Replay still embeds all 33 assets and the file replays offline.
* Local game as the Runner against the Basic Corp bot with an imported "The Collective" deck: the generated 014001
  image in the gallery preview, the hover zoom and on the board; several turns, no exceptions.
* Screenshots (scratchpad `fin/`): `o2-corp-gallery.png`, `o3-corp-filter.png`, `o4-corp-preview.png`,
  `o6-runner-import.png`, `o7-spec.png`, `o11-spec-mid.png` / `o11-corp-mid.png`, `o12-spec.png`,
  `o13-iso-spec-over.png`, `o15-offline-replay.png`, `o16-offline-rewind.png`, `o17-local-gallery.png`,
  `o19-collective-zoom.png`, `o21-iso-corp-reload-over.png`, `coup-replay.png`.

### Tested after the integration of the four fix passes (2026-10-14)

* `sbt -batch compile` and `sbt -batch fastOptJS` green.
* JVM harness (scratchpad `merge4/host`: a copy of `integ/host` pointed at this checkout, with every script of the four fix
  passes and their updated checks; `QuineStub.scala` stands in for the JS-only `quine.scala`). All scripted checks pass:
  `ScriptTest` 40, `CreditScript` 92, `PreventionScript` 60, `RestrictionScript` 74, `FixScript` 81, `RotIceScript` (211 ice,
  633 encounters, 0 failures), `RotAssetScript` 52, `RotAgendaScript` 33, `RotEventScript` 40, `RotHardwareScript` 80,
  `RotOperationScript` 56, `RotProgramScript` 50, `RotMergeScript` 20; `PresetCheck`: 195 presets, 0 invalid. Updated checks:
  the five harness checks changed by the passes (Méliès U memo, Spear Phishing log, Interrupt 0 message, Net Watchlist
  surcharge, LLDS trash window) and `CreditScript`'s trace check (now `fxTraceLinkStrength == link + boost`).
* Bot games (`MergeHost`, every offered and chosen action round-tripped through the serializer; exceptions, empty asks,
  serialization mismatches and over-long games counted as errors) on the final code: 3 batches over all presets (250 + 250 +
  200, seeds 8001-8003, 190 of the 195 presets drawn) + 250 rotated Runner presets against rotated Corp presets (seed 8004):
  **950 games, 0 errors**; plus 850 games on the build before the last cross-pass fixes (seeds 7001-7004), 0 errors. The logs
  show tag avoidance, damage prevention, Patchwork interrupts, Tyr's Hand ("not broken"), trash prevention and bad publicity
  prevention.
* Browser (headless Chrome over the DevTools protocol, scratchpad `merge4/cdp`): a bot-vs-bot hotseat game played to the end
  (Corp won on points, turn 16); an online game with two player links and a spectator link, imported decks (The Masque: Cloak,
  Dagger, Net Shield, Sure Gamble / The Shadow: Karunā, Hostile Takeover, Hedge Fund): Dagger's "+5 strength (stealth credits
  only)" paid from Cloak ("Runner spends 1 credit from Cloak"), the net damage prevention window ("Prevent any of the 2 net
  damage?" -> Net Shield prevents 1, the Runner suffers 1), then automatic play until the Runner was flatlined. No plugin
  exceptions in any console.
### Tested after the side indicator / banner change (2026-10-14)

* `sbt -batch fastOptJS` green. Separate good-game server on port 7171 serving this checkout, headless Chrome on port
  9888 over the DevTools protocol (driver in the scratchpad `nr2/`), 1440x900 and 1920x1080:
  hotseat seated Runner-first (Corp asked first: "Corp: choose a deck", Jinteki Starter preview + Confirm, then
  "Runner: choose a deck" with "Corp: chose Jinteki: Restoring Humanity"); hotseat with manual tags 2, bad publicity 1,
  core damage 1 and a run on HQ (banner badges and alerts at both resolutions); Quick Game seated as the Runner,
  "Play the Corp instead" restarted it as the Corp (and back); Quick Game as the Corp against the Basic Runner bot for
  ~120 automated clicks (installs, advances, scoring); Local Game human Runner against the Basic Corp bot (Shaper
  Starter, ~80 automated clicks with runs) and with Criminal Sable (mark badge "Archives"); online game: the Corp link
  and the Runner link each saw only their side's gallery with the side title, the Corp confirmed NBN Starter, the
  Runner saw "Corp: chose NBN: Reality Plus" and confirmed Anarch Starter, both reached the mulligan. Worst-case banner
  width checked by adding mark and clicks-next-turn badges in the DOM. No plugin exceptions (only the shell's
  `NetworkError` / 404 font noise, the known `HTTP 409` append race and one `later failed`).
* Screenshots (scratchpad `nr2/shots/`): `h1-hot-corp-gallery`, `h2-hot-corp-preview`, `h3-hot-runner-gallery`,
  `h4-hot-runner-gallery-1920`, `b1-run-1440`, `b2-run-1920`, `q1-quick`, `q6-quick-corp-hint`,
  `q8-quick-runner-hint`, `q4-corp-preview`, `q7-corp-midgame-1440`, `v1-runner-vs-bot-gallery`,
  `v4-runner-midgame-1440`, `v5-runner-midgame-1920`, `s4-sable-mark-1440`, `w1-worst-1440`, `o1-corp-link`,
  `o2-runner-link`, `o4-runner-after-corp`.

### Tested after the final NSG pass (2026-10-14)

* `sbt -batch compile` green. JVM harness (scratchpad `final-nsg/host`, a copy of `merge4/host` pointed at this checkout): all
  scripted checks pass (`ScriptTest`, `CreditScript`, `PreventionScript`, `RestrictionScript`, `FixScript`, `PresetCheck` 195 presets /
  0 invalid, `RotIceScript` 211 ice / 0 failures, `RotAssetScript`, `RotAgendaScript`, `RotEventScript`, `RotHardwareScript` 80,
  `RotOperationScript` 56, `RotProgramScript`, `RotMergeScript`, new `FinalNsgScript` 82); `ScriptTest`'s Bling check now uses the basic
  play action. Bot games: 300 + 300 over all presets + 200 rotated vs rotated presets, **800 games, 0 errors** (plus 750 on earlier
  builds). See `STATUS-fix-final-nsg.md`.

### Tested after the final rotated pass (2026-10-14)

* `sbt -batch compile` green.
* JVM harness (scratchpad `final-rot/host`, a copy of `merge4/host` pointed at this worktree): `ScriptTest`, `FixScript` 81,
  `PreventionScript`, `CreditScript`, `RestrictionScript`, `RotIceScript` (211 ice, 633 encounters, 0 failures), `RotAssetScript`,
  `RotAgendaScript`, `RotEventScript`, `RotHardwareScript` 80 (Gagarin check updated: the Runner chooses to pay), `RotOperationScript`
  56, `RotProgramScript`, `RotMergeScript`, `PresetCheck` (195 presets, 0 invalid) and the new `FinalRotScript` (91 checks, including
  the serializer round trip of every new user action / purpose): all pass.
* Bot games (`MergeHost`, every offered and chosen action round-tripped through the serializer; exceptions, empty asks, mismatches
  and over-long games counted as errors), each batch = 250 + 250 games over all presets + 250 rotated Runner presets against rotated
  Corp presets: seeds 9201-9203 (0 errors) and 9301-9303 (1 error: Faust breaking with the last grip card, fixed), then on the final
  code seeds 9401-9403 **750 games, 0 errors** plus seed 9302 again (250 games, 0 errors).

### Tested: Smart bot (2026-10-14)

JVM harness (`BotHost`, a variant of the merge harness with a bot per side, random preset decks over all 195 presets,
serialization round trip of every offered / chosen action), 300 games per pairing, 0 exceptions, 0 empty asks,
0 serialization mismatches, 0 games over the 4000-action limit (longest: turn 40), 0 Smart heuristic errors.
Ends = Corp agendas / Runner agendas / flatline / R&D empty / other (Superdeep Borehole). "0 cr" = Runner turns
started at 0 credits; credits = average at the start of the side's turns; agendas = scored / stolen per game.

| Corp vs Runner | Corp wins | Ends | Turns | 0 cr | Runner / Corp credits | Runs per Runner turn | Agendas |
|---|---|---|---|---|---|---|---|
| Basic vs Basic (before) | 59.3% | 72/81/105/41/1 | 13.2 | 66.8% | 1.0 / 3.4 | 3.43 | 1.25 / 1.99 |
| Basic vs Random (before) | 49.0% | 44/143/103/10/0 | 12.3 | 36.4% | 2.1 / 2.3 | 2.58 | 1.14 / 2.56 |
| Random vs Basic (before) | 24.0% | 1/156/71/72/0 | 14.3 | 63.4% | 1.0 / 4.1 | 3.51 | 0.17 / 2.72 |
| Smart vs Smart | 50.7% | 143/141/7/7/2 | 15.5 | 3.8% | 6.8 / 6.4 | 1.09 | 2.23 / 2.48 |
| Smart vs Smart (after the final merge, 600 games) | 56.5% | 318/249/19/12/2 | 15.8 | 4.9% | 6.4 / 6.0 | 1.06 | 2.36 / 2.29 |
| Smart vs Smart, rotated presets only (200 games) | 63.5% | 125/67/2/6/0 | 16.3 | 4.1% | 6.6 / 6.1 | 1.01 | 2.56 / 2.00 |
| Basic vs Basic (after the final merge, 200 games) | 51.5% | 50/60/53/37/0 | 13.9 | 67.0% | 1.0 / 3.2 | 3.36 | 1.36 / 2.06 |
| Smart vs Basic | 97.0% | 202/4/86/5/3 | 12.9 | 64.6% | 1.0 / 6.7 | 3.23 | 2.73 / 0.36 |
| Basic vs Smart | 8.7% | 17/270/6/4/3 | 8.8 | 9.3% | 5.3 / 4.1 | 1.94 | 0.54 / 3.49 |
| Smart vs Random | 94.0% | 146/14/136/4/0 | 12.1 | 38.8% | 2.0 / 5.7 | 2.46 | 2.34 / 0.70 |
| Random vs Smart | 2.3% | 0/289/6/4/1 | 8.9 | 8.2% | 4.4 / 4.7 | 2.07 | 0.04 / 3.67 |

`sbt -batch compile` and `sbt -batch fastOptJS` pass.

### Tested after the integration of the final passes and the Smart bot (2026-10-14)

* `sbt -batch compile` and `sbt -batch fastOptJS` green (final build 19:04).
* JVM harness (scratchpad `fm/host`: `merge4/host` pointed at this checkout, with `FinalNsgScript` and `ScriptTest` from the NSG
  pass, `FinalRotScript` and `RotHardwareScript` from the rotated pass (renamed effects adapted: `FxSourceEffect`,
  `SpendClicksEffect`), `BotHost` from the bot branch (deck filters added; an ask whose options are all `NotForBots`, i.e. only
  the manual menu, now counts as an error) and a new `MergeFinalScript`). All scripted checks pass: `ScriptTest` 40, `CreditScript`
  92, `PreventionScript` 60, `RestrictionScript` 74, `FixScript` 81, `RotAssetScript` 52, `RotAgendaScript` 33, `RotEventScript` 40,
  `RotHardwareScript` 80, `RotOperationScript` 56, `RotProgramScript` 50, `RotMergeScript` 20, `FinalNsgScript` 82, `FinalRotScript`
  91, `MergeFinalScript` 15 (one marker pair per trigger, `effectSource` = damage source, Archer forfeit + Hernando Cortez surcharge
  paid once each, Smart bot: automatic credit option / Gagarin pay-or-refuse / Flip Switch in the trace window / subset break
  not for bots, the Anvil + Always Be Running stuck ask below), `RotIceScript` (211 ice, 633 encounters, 0 failures),
  `PresetCheck` (195 presets, 0 invalid): 866 named checks.
* Bot games (`BotHost`, random presets per side over all 195 presets, every offered and chosen action round-tripped through the
  serializer; exceptions, empty asks, manual-only asks, serialization mismatches, games over 4000 actions and Smart heuristic
  errors counted as errors) on the final code: Smart vs Smart 600 games, Basic vs Basic 200, Smart vs Smart with rotated presets
  on both sides 200: **1,000 games, 0 errors** (longest 769 actions / turn 39). On the builds before the last fixes: 2,400 Smart vs
  Smart, 400 Basic vs Basic, 400 rotated Smart vs Smart, 0 exceptions; they showed 3 games where the Runner was offered only greyed
  options: Always Be Running's (or any bioroid-style) "break 1 subroutine" on Anvil whose printed subroutines could not be broken
  asked for a subroutine with every option greyed (an engine gap older than the merge); `BioroidBreakEffect` now resolves with no
  effect when no unbroken subroutine can be broken, and the Smart bot never picks the manual menu or a concession while anything
  else is offered. Smart-vs-Smart metrics in the "Smart bot" table; single-process decision time (50 games): p50 0.5 ms, p99 3.3 ms,
  max 30 ms (bot branch: p50 0.35 ms, p99 3.4 ms, max 29 ms). The Corp wins a little more often than on the bot branch (56.5% over
  the final 600 games, 56% over all 2,400; 50.7% over 300 before): the Runner pays Gagarin, additional rez / score costs are real
  and HQ / R&D are accessed one card at a time; the Runner's economy and run counts are unchanged.
* Browser (real good-game server on port 7070 serving this checkout, index and JS checked fresh; headless Chrome on port 9668
  over the DevTools protocol, driver in the scratchpad `fm/web/` (`daemon.mjs`, `helpers.js`, `auto.mjs`, `localplay.mjs`), each
  link in its own browser context = anonymous visitor):
  * Online game 1 "Flatline at Purge" (Corp `lxhfywglysszklxf`, Runner `utxplypsqkxkoucu`, spectator `khfqjfsaazckwypi`): the
    Corp filtered the gallery to FFG era (78 decks), previewed "NBN A ice (rot-ice)" (Near-Earth Hub, 16 trace cards) and
    confirmed; the Runner pasted a NetrunnerDB decklist (Kate "Mac" McCaffrey, 45 cards, 7/15 influence: Cloak, Cyberfeeder, Net
    Shield, Feedback Filter, Gordian Blade, Corroder, Dagger, ...), "Check and preview", "Import". Both kept. About 330 rounds of
    clicks from both links: Virgo and Turnpike traces (Corp and Runner boosts chosen in the UI), Herald's subroutines broken with
    Gordian Blade after pumping, Corp scored Orbital Superiority, Runner stole Offworld Office, Tomorrow's Headline and Project
    Beale: Runner won 8-2 on turn 16.
  * Online game 2 "Purge on Breach" (Corp `hbjttbfnqeddjlhu`, Runner `swprttvekowbvyaz`, spectator `sqfnwbejkzkctedy`): Corp FFG-era
    preset "Jinteki A ice (rot-ice)" (Jinteki: Personal Evolution) previewed + confirmed, Runner the same Kate decklist imported;
    the Runner took a mulligan. Runner stole Offworld Office and Nisei MK II (Personal Evolution's net damage each time); on
    turn 4 the Runner clicked a log line and used "Undo to here" + Confirm (log: "Runner undid 2 actions, including own action",
    4 clicks back); the Corp link was reloaded mid-game (same position, HQ and own unrezzed ice visible, undo line kept). The
    Runner installed Feedback Filter and two Cloaks; on stealing Orbital Superiority the prevention window "Prevent any of the 1
    net damage?" offered "Feedback Filter: prevent 1 net damage" / "Take the damage": prevented ("All net damage is prevented").
    On turn 8 the Corp rezzed Galahad; Corroder's "Break End the run (1 credit)" opened the credit-source choice ("Pay 1 credit
    (1 left): choose where the credits come from": "Automatically: 1 from the credit pool" (stealth credits kept for Dagger),
    "Pay 1 credit from Cloak" x2, "Pay 1 credit from the credit pool"); the Runner chose a Cloak ("Runner chooses the credits: 1
    from Cloak (the rest automatically)"), and took the automatic option on the next run. Corp scored Longevity Serum and
    Offworld Office; Runner stole Orbital Superiority again for the win, 8-4 on turn 15. Save Replay from the Runner link: 73 MB
    file, opened from `file://` (final position with both hands, full log).
  * Spectator check every 8 rounds in both games (card images shown to the spectator but not visible in both player views):
    0 differences in game 2; one in game 1 (Gutenberg while it was being accessed from R&D: the access is in every client's
    log, so it is public). Spectator screenshots show both hands and unrezzed cards as backs.
  * Local game as the human Corp (Gagarin Deep Space preset) against the Smart Runner bot (default bot, "Bot / Smart" in the
    setup): five turns; the bot paid Gagarin's credit to access remote cards ("Runner pays 1 credit to access an unrezzed card
    (Gagarin Deep Space)"), stole The Basalt Spire; Plutus' additional rez cost ("Reveal and trash 3 cards from HQ") asked
    before the rez. Quick Game twice (Smart Runner bot; Weyland Starter, then RotAg Weyland 2 on the final build), a few turns.
  * No plugin exceptions in any console (only the shell's `NetworkError` / font 404 noise, the framework's `HTTP 409` /
    `HTTP 500` append races and `later failed`).
  * Screenshots at 1440x900 (scratchpad `fm/web/shots/`): `a3-corp-gallery`, `a4-corp-ffg-filter`, `a5-runner-import`,
    `b2-runner-mid`, `b3-corp-mid`, `g1-iso-spec-over`, `c1-corp-filter`, `c9-after-undo`, `c11-corp-reload`,
    `c15-prevention-window`, `c17-credit-choice`, `g2-iso-runner2-over`, `g2-iso-spec2-over`, `g2-offline-replay`,
    `l4-local-midgame`, `q2-quick-midgame`, `z1-final-quick-1440`: banner badges, side colours, hand pane, gallery and board intact.

## Known bugs

* None reproduced at the time of writing; see "Simplifications" above.
