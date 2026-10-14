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
* UI (`ui.scala`, `styles.scala`): Corp and Runner board panes with a status banner (credits, clicks and agenda
  points as large badges, tags / bad publicity large when non-zero, link, MU, hand size small; see "Status banner and
  deck preview"), servers drawn from the Runner's point of view (root at the top of the
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
* Bots: `Random` (uniform over non-manual choices) and `Basic` (weighted heuristics: scores, steals, breaks, economy).
* JVM harness `host.scala` (excluded from the JS build): bot-vs-bot games with log dumps.

## Card sets

Every card of the card database has an implementation object: **2,056 cards** = 695 cards of the eleven current sets
(table below) + 1,361 rotated / FFG-era cards (second table). Per-set details, simplifications and the list of
generic mechanics each set introduced are in the set status files; the engine side of the merge is described in
`ENGINE.md` section 8.

| Set | Cards | Implemented | Simplified / approximated | Files | Details |
|---|---|---|---|---|---|
| System Gateway (sg) | 77 | 77 | none (see `STATUS-fix-misc.md`) | `cards-sg.scala` | this file |
| Elevation (elev) | 82 | 82 | 3 (Bling, IP Enforcement (printed text), Plutus) | `cards-elev.scala`, `rules-ext-startup.scala` | `STATUS-vp-elev.md` |
| Vantage Point (vp) | 66 | 66 | 1 (Reanimation Protocol) | `cards-vp.scala`, `rules-ext-startup.scala` | `STATUS-vp-elev.md` |
| System Update 2021 (su21) | 82 | 82 | 3 (Atman, Corporate Town / Archer, Marilyn Campaign) | `cards-su21.scala`, `rules-ext-su21.scala` | `STATUS-su21-ms.md` |
| Midnight Sun (ms) | 65 | 65 | 3 (Azef Protocol, Mitosis, Mystic Maemi) | `cards-ms.scala`, `rules-ext-su21.scala` | `STATUS-su21-ms.md` |
| Uprising Booster Pack (urbp) | 7 | 7 | 2 (Cayambe Grid, Megaprix Qualifier) | `cards-ms.scala` | `STATUS-su21-ms.md` |
| Parhelion (ph) | 63 | 63 | ~10 (Matryoshka (X = 1 or all), Hostile Architecture, Klevetnik, Bloop, ...) | `cards-ph.scala`, `rules-ext-ph-tai.scala` | `STATUS-ph-tai.md` |
| The Automata Initiative (tai) | 65 | 65 | ~6 (Daniela Jorge Inacio steal cost, Mercury, Jaguarundi / Attini / Pulse, ...) | `cards-tai.scala`, `rules-ext-ph-tai.scala` | `STATUS-ph-tai.md` |
| Rebellion Without Rehearsal (rwr) | 65 | 65 | 7 (Sebastiao Souza Pessoa, Boi-tata, Manuel Lattes de Moura, Cataloguer, "Pretty" Mary, Nuvem SA, Cupellation) | `cards-rwr.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |
| Downfall (df) | 65 | 65 | 2 (Whistleblower, MirrorMorph) | `cards-df.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |
| Uprising (ur) | 58 | 58 | 1 (GameNET); 7 ur codes are not in the card database | `cards-ur.scala`, `rules-ext-rwr-df-ur.scala` | `STATUS-rwr-df-ur.md` |

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
| ICE | 211 | 211 | ~6 (Tithonium's alternative cost, the encounter-on-access ice Archangel / Chrysalis / Herald / Sapper, Formicary) | `cards-rot-ice*.scala`, `rules-ext-rot-ice.scala` | `STATUS-rot-ice.md` |
| Programs | 164 | 164 | ~8 (Hivemind, Yusuf / Musaazi, Faust, Progenitor, Customized Secretary, Panchatantra, Sahasrara edge case) | `cards-rot-programs*.scala`, `rules-ext-rot-programs.scala` | `STATUS-rot-programs.md` |
| Resources | 164 | 164 | ~8 (Starlight Crusade Funding, Hernando Cortez, Keros Mcintyre, Muertos Gang Member, Levy AR Lab, Psych Mike, Salvaged Vanadis Armory, DJ Fenris) | `cards-rot-resources-*.scala`, `rules-ext-rot-resources.scala` | `STATUS-rot-resources.md` |
| Events | 148 | 148 | ~5 (Rebirth (no format), Apocalypse / Reboot faceup flip, Watch the World Burn in Archives, Push Your Luck / Rigged Results secrecy) | `cards-rot-events-*.scala`, `rules-ext-rot-events.scala` | `STATUS-rot-events.md` |
| Operations | 146 | 146 | ~9 (Surveillance Sweep, Net Watchlist (break / pump only), Enhanced Login Protocol, Bad Times / MCA Informant, Death and Taxes, Accelerated Diagnostics, "name a card" lists) | `cards-rot-operations*.scala`, `rules-ext-rot-operations.scala` | `STATUS-rot-operations.md` |
| Assets | 144 | 144 | ~6 (Genetics Pavilion, Ibrahim Salem, Lakshmi Smartfabrics, IT Department, Full Immersion RecStudio, Mumbad Construction Co.) | `cards-rot-assets*.scala`, `rules-ext-rot-assets.scala` | `STATUS-rot-assets.md` |
| Agendas | 114 | 114 | ~6 (Accelerated Beta Test, Bacterial Programming, Personality Profiles, Evidence Collection 1-3 no effect) | `cards-rot-agendas*.scala`, `rules-ext-rot-agendas.scala` | `STATUS-rot-agendas-upgrades.md` |
| Upgrades | 79 | 79 | ~4 (Armored Servers break cost, Warroid Tracker, Overseer Matrix, Daruma root swaps) | `cards-rot-upgrades.scala`, `rules-ext-rot-agendas.scala` | `STATUS-rot-agendas-upgrades.md` |
| Hardware | 101 | 101 | ~6 (Patchwork for card-effect installs, Q-Coherence Chip, Respirocytes, Top Hat, Maya, Archives Interface) | `cards-rot-hardware-*.scala`, `rules-ext-rot-hardware.scala` | `STATUS-rot-hardware-identities.md` |
| Identities | 90 | 90 | ~12 (Adam, Skorpios, Gagarin, Acme, SSO Industries, Seidr, Harishchandra, Kabonesa Wu, Hayley Kaplan, Cyber Bureau / NEXT Design / Jinteki Biotech timing) | `cards-rot-identities-*.scala`, `rules-ext-rot-hardware.scala` | `STATUS-rot-hardware-identities.md` |
| **Total rotated** | **1,361** | **1,361** | **~70** (was ~176 before the four fix passes) | | |
| **All cards** | **2,056** | **2,056** | ~108 (current sets ~38 + rotated ~70; was ~280) | | |

The counts above are after the four rules-accuracy fix passes (credits, restrictions, prevention, card-specific fixes; see
"Rules-accuracy fix passes" below). They are approximate ("~"): an entry counts a card whose printed text is approximated or
partly unenforced; differences that match jinteki's own implementation, manual-only choices and UI-only secrecy are not counted.

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

* Status banner (`UI.statusBanner`, `UI.bigStat`; styles `bigStat`, `bigValue`, `bigLabel`, `bigCredit`, `bigClick`,
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
  `STATUS-fix-prevention.md` and ENGINE.md section 8.4. "When a trace is initiated" abilities (Flip Switch, Disrupter,
  Power Tap, Compromised Employee) still use the older one-shot "trace" ask of `TraceEffect`.
* Bot play is weak; bots never use the manual menu or concede; bots pass paid ability windows most of the time.
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

## Known bugs

* None reproduced at the time of writing; see "Simplifications" above.
