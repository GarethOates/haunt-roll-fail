# Rules accuracy pass: card-specific approximations ("fix-misc")

> **Integration note (merge of the four fix passes, see STATUS.md "Integration of the four passes").** FixRules is mixed in
> between RestrictionRules and PreventionRules (`... with CreditRules with RestrictionRules with FixRules with PreventionRules`,
> ENGINE.md 8.6), not last. Replaced by the other passes' mechanisms (owner of the theme): Aeneas Informant (`AccessFinished`),
> Divide and Conquer (`BreachFinished`), AMAZE Amusements (`PersistentEffect`), Heinlein Grid (`ClicksSpentOrLost`) -> prevention;
> RSVP (run flag `no-spend-credits`; the greying below reads `noSpending`) -> credits; The Black File (`corpCannotWin`) ->
> restrictions (this pass's win re-check on trash is kept). `fxResolvingSubs` is an alias of `PreventionRules.preventSubs` (the
> `FxSubEffect` markers were removed); GameNET's steal-cost trigger fires `CreditsPaid(.., Spend.corpAbility)`. Biawak, Oppo Research
> and the restricted-MU install greying pay / check through the credits pass's `Spend`. (The credits pass also made Urban Art
> Vernissage / Cybersand Harvester real install-credit sources, so the "take" ability noted below is gone.)

Scope: the card-specific approximations listed in `STATUS.md` and the `STATUS-*.md` files that are **not** about credits /
costs / credit-source restrictions, prevention / interrupts / timing windows, or install / rez / play / score restrictions and
deck building (three other passes own those). Each fix ports the jinteki.net behaviour (`src/clj/game/cards/*.clj`,
checked against `test/clj/game/cards/*_test.clj`); where the printed NSG text and the Clojure differ, the printed text wins.

Generic additions are in the new file `rules-ext-fixes.scala` (`trait FixRules`, see `ENGINE.md` §8.5); card-specific logic
stays in the card files.

## Cards fixed

| Code | Card | Before | Now |
|---|---|---|---|
| 30058 | AMAZE Amusements | only while installed and rezzed | persistent: trashed by the Runner during a run on its server, it still gives 2 tags when that run ends if an agenda was stolen (run-scoped flag) |
| 30019 | Tāo Salonga (swap) | log named unrezzed ice | `SwapIceEffect` logs unrezzed ice as "a card"; the Runner's position is kept by index (as jinteki) |
| 35074 | Biawak | forfeit forced whenever an agenda was scored | the Corp chooses: forfeit an agenda or pay the 10 credits it covers (forfeit forced only when it cannot pay); nothing when rezzed ignoring all costs |
| 35081 | Petty Cash | "3+ clicks and no play/install/advance/draw" | no action finished yet (basic action or click ability) |
| 35018 | Detente | reduced access | normal access machinery (`AccessRandomFromHQEffect` pseudo-run: other cards' access abilities apply) |
| 35069 | The Zwicky Group | source = first card in the play area / scoring agenda (cleared before "when scored" resolved) | the real source: a resolving paid ability (`fxSources`), operation, or agenda "when scored" ability |
| 36008 | Stick and Poke | a choice between paying 1 and taking the effect | the encountered ice gains "[Stick] Do 1 net damage. The Runner draws 1 card." as its **first** subroutine for the first encounter of the turn (breakable, resolves like any subroutine) |
| 36025 | Word on the Street | added to the score area after the agenda was scored (the win was checked first) | added as the additional cost **before** the agenda is scored (its -1 counts for that win check) |
| 36061 | Myōshu | only the last scored agenda | any agenda scored this turn that was not installed this turn |
| 36055 | Hype Machine | keyed on points | keyed on agendas scored / stolen (0-point agendas count) |
| 36036 | Méliès U | choice stored as a visible counter; first face HQ | memo (not drawn on the board); random face before the first turn; credit "when the Runner's action phase ends" |
| 31032 | Egret | subtypes added permanently on install | static: the host has Barrier / Code Gate / Sentry while Egret is hosted on it (`hostSubtypes`) |
| 31003 | En Passant | ice at positions passed in the last run's server | the unrezzed ice actually passed during the last run (`PassIce`) |
| 31063 | Daily Business Show | each copy asked for 1 card | one prompt for 1 card per rezzed copy (also when only 1 card was drawn) |
| 33022 | Deep Dive | cards accessed in place in R&D | the top 8 are set aside (hosted faceup on the event), accessed from there, then shuffled back |
| 33023 | Into the Depths | number of ice of the server | number of times ice was passed during the run (max 3) |
| 33004 | Steelskin Scarring | drew 4 on damage (two triggers); not on card-effect grip trashes | one trigger; also on random / card-effect trashes from the grip (not on the end-of-turn discard, which is a move in jinteki) |
| 33057 | Ob Superheavy Logistics | fired during installations; once per turn even if declined | not during an installation; once per turn when used |
| 33094 | Matryoshka | "break all for n" plus single breaks at n credits each | 1 credit per subroutine (X = 1 or all), 1 hosted copy turned facedown per use |
| 33088 | Nanuq | only trash | removed from the game instead of any move out of play (grip, stack) |
| 33084 | Spark of Inspiration | revealed cards stayed in the stack | revealed cards set aside, then shuffled back |
| 33101 / 33086 | Hypoxia / Basilar Synthgland | click lost / gained at turn begin | allotted clicks (-1 next turn / +1 per turn) |
| 33104 | Issuaq Adaptics | reduced threshold only right after a score | every win check |
| 33124 | Anvil | all subroutines unbreakable | only the printed subroutines |
| 33070, 33089, 34015, 26016, 26080, 34090, 21104, 10101, 12048, 12067, 11023, 11102, Bird suite 11025 / 11044 / 11064 | Abaasy, Orca, Curupira, Bukhgalter, Makler, Lobisomem, Amina, Ankusa, Inversificator, Mass-Driver, Nfr, Sūnya, Golden / Peregrine / Saker | "broke the last subroutine" | every subroutine broken by that program (jinteki `all-subs-broken-by-card?`) |
| 34005 | Banner | blocked every end-the-run (even jacking out) | only end-the-run from that barrier's subroutines (`fxResolvingSubs`) |
| 34033 | Wage Workers | paid out after a terminal operation | no payoff once the action phase ended (terminal), checked when the payoff resolves |
| 34055 | Oppo Research | clicks set to 0 | `EndActionPhaseEffect` (terminal) |
| 34054 | Virtual Service Agent | any decoder break during the encounter | the printed subroutine broken by a decoder |
| 34053 | Starlit Knight | only the first encounter's X | each encounter adds its X end-the-run subroutines for the run (jinteki) |
| 34025 | Pichação | only its own click gains counted | every click gained during a run this turn |
| 34047 / 34064 | Daniela Jorge Inácio / Tucana | "stolen during a run on this server" | stolen from this server / its root (`lastStolenFrom`) |
| 26020 | In the Groove | "4 clicks remaining" | no action and no click ability yet this turn |
| 26028 | Direct Access | identities kept their abilities | both identities lose all abilities during its run (`isDisabled` / `DisabledImpl`) |
| 26113 | GameNET | — | also counts credits paid for additional steal costs during a run |
| 04077 | RSVP | only credit break abilities blocked | no credit payment by the Runner for the rest of the run: breaks, pumps, paid abilities, prevention costs, trash and steal costs, trace boosts, psi bids, "pay ... or" choices |
| 11092 | Mind Game | jack-out cost only on the offered jack out | every jack out for the rest of the run costs an installed card to the bottom of the stack |
| 29017 / 02074 / 08056 / 04110 | Excalibur / Uroboros / Lockdown / Gyri Labyrinth | restriction enforced by rezzed rotated ice | lingering flags enforced by FixRules: no run, no draw, -2 hand size until the Corp's next turn, even if the ice leaves play |
| 06011 / 06051 / 06091 / 29017 | Galahad / Lancelot / Merlin / Excalibur (grail) | fixed table, encounter only | the printed subroutines of the revealed grail cards (their own implementations) for the rest of the run, in front of or after the subroutines (Corp's choice) |
| 02096 / 10091 | Data Hound / Waiver | boost = credits spent | trace strength - the Runner's real link strength (`fxTraceLinkStrength`, incl. Net Quarantine's link 0) |
| 21074 / 05045 | Endless EULA / Grappling Hook | breaks without `SubsBroken` | emit `SubsBroken` |
| 11105 | Tracker | first unbroken subroutine marked broken | the first subroutine that would resolve during its run is prevented |
| 12067 | Mass-Driver | 3 subroutines marked broken | the first 3 subroutines of the next encounter do not resolve (not broken) |
| 09033 | Endless Hunger | any subroutine of an ETR ice | only "End the run" subroutines |
| 12104 | Aumakua | at run end | whenever a breach ends without stealing or trashing (`BreachEnded`) |
| 21090 | Jackpot! | all hosted credits | any number |
| 12065 | Keros Mcintyre | ice derezzed during the Runner's turn | ice derezzed while a Runner card's ability or event resolves |
| 10003 | Street Magic | subroutines fired early and marked broken | the chosen order is used when the unbroken subroutines resolve |
| 10099 | The Black File | not enforced | the Corp cannot win on agenda points while it has fewer than 3 power counters; win re-checked when it leaves |
| 08087 | Paparazzi | "you are tagged" not modelled | `Game.tagged` |
| 12044 | Aeneas Informant | at run end | right after the access of the card |
| 10065 | Bazaar | any hardware install | only hardware installed from the grip |
| 10046 / 06060 | Akshara Sareen / Rachel Beckman | click gained at turn begin | allotted clicks (`clicksPerTurn`) |
| 03031 | Escher | 6 swaps | swaps until the Runner stops (bounded by 3 swaps per installed ice, enough for any arrangement) |
| 10025 | Corporate Scandal | real bad publicity (taken, removable) | an additional bad publicity: not "taken" (no Broadcast Square / BadPubGained), cannot be removed, gone with the current |
| 11083 | Cold Read | any installed program | a program used (break / pump / ability) during the run |
| 22002 | Divide and Conquer | breaches at run end | HQ then R&D (no root) when the Archives breach ends, still during the run |
| 23100 | Watch the World Burn | same code | same title (any printing) |
| 25029 / 31018 | Spear Phishing / Inside Job | duplicate "bypasses" log line | "uses X" + the engine's bypass line |
| 11071 | Wetwork Refit | subroutine after the printed ones | before all other subroutines |
| 25137 / 03013 / 25083 | Oversight AI / Bioroid Efficiency Research / Mason Bellamy (also Forked / Knifed / Spooned 07037-07039) | did not see bioroid click-breaks | bioroid click-breaks emit `SubsBroken(ice, sub, ice)` |
| 09021 | Media Blitz | agenda triggers evaluated on the agenda in the Runner's area (silent) | the agenda's triggers, paid abilities and static modifiers are evaluated with Media Blitz as the card; the agenda base classes treat a Media Blitz in play as scored |
| 04111 / 10075 | Reclamation Order / Localized Product Line | all copies | the Corp chooses how many |
| 13051 / 11056 / 11051 | Hunter Seeker / Observe and Destroy / Hellion Beta Test | Runner cards only | any installed card (Hellion: non-program or facedown) |
| 12031 | Load Testing | -1 allotted click | the Runner loses [click] when the next turn begins |
| 03010 / 09011 | Director Haas / Victoria Jenkins | next-turn clicks at turn end | static allotted clicks while active |
| 12111 | MCA Austerity Policy | -1 allotted click | the Runner loses [click] when the next turn begins |
| 07009 | Mark Yale | first agenda ability's counter cost assumed | every agenda counter spent (cost or removal) |
| 10067 | Jeeves Model Bioroids | actions × 1 click (double ops 1) | clicks actually spent per action (`ClicksSpent`) |
| 25092 | Sundew | first click action | first click spent in the turn (action, ability, bioroid break); the 2 credits back when a run on its server begins during that action (basic run or a run event / ability) |
| 10109 | Ibrahim Salem | forfeit asked when rezzed ignoring costs | not asked then (`fxRezIgnoredCost`) |
| 02011 | Mandatory Upgrades | click at "turn begins"; kept after leaving | allotted click while scored |
| 12092 | Mandatory Seed Replacement | 6 swaps | swaps until the Corp stops (bounded like Escher) |
| 09005 | Improved Tracers | traces during an encounter | traces of subroutines |
| 08015 | Valley Grid | penalty while Valley Grid is active | until the Corp's next turn even if it leaves play |
| 06023 | Heinlein Grid | click drops between events | spends (bioroid break, ability) or losses of [click] during a run on its server |
| 07046 / 21062 / 04042 | MemStrips / Knobkierie / Deep Red | install needed free general MU | a virus / Caïssa program fitting in the unused restricted MU installs without trashing |
| 06093 | Ekomind | checked when a Runner card leaves play | checked whenever the limit is exceeded |
| — | Archives breach | facedown cards kept their trash order | the flipped cards are put in title order (no order leak) |
| — | Runner UI | accessed installed cards drawn as backs after the run | remembered until they move (`fxRunnerKnows`), without making them "seen" |
| — | "instead of breaching" | two log lines | one |
| — | `BypassIceEffect` | could log / trigger twice | once per encounter |

Verified as already exact (status text corrected): Brân 1.0's install position, Public Access Plaza, Light the Fire! static
abilities, Anemone, Hakarl 1.0, Trieste, WAKE Implant, Vera Ivanovna Shuyskaya, Stegodon MK IV, Oracle Thinktank, Lago Paranoá
Shelter, Federal Fundraising, Immolation Script, Prey, Riot Suppression, Unorthodox Predictions, Lt. Todachine 2, Toshiyuki
Sakai, Warden Fatuma (front), SSO Industries, Seidr Laboratories, Copycat (printed text), Mongoose, Harbinger / Hunting Grounds MU.

## Still approximate in this theme (and why)

* (Matryoshka's arbitrary X, Daniela Jorge Inácio's steal cost and Urban Art Vernissage / Cybersand Harvester: fixed or verified in
  the final NSG pass, see `STATUS-fix-final-nsg.md`.)
* Acme Consulting (+1 tag "considered") uses a real tag; Gagarin's access cost is automatic; Skorpios is not an interrupt
  (timing pass); encounter-on-access ice (Archangel, ...) are placed temporarily in the server; Hivemind counters are spent
  only by the rotated virus programs (counter costs: costs pass); Personality Profiles' "search the stack" detection,
  Accelerated Beta Test / Bacterial Programming shuffles, Nuvem SA's "trash from R&D", Boi-tatá, Warroid Tracker (fires per
  trashed card), Levy AR Lab (no reorder prompt), Muertos Gang Member (only when trashed), Climactic Showdown, Whistleblower,
  Cataloguer: unchanged.
* HQ accesses are still drawn when the breach starts (equivalent unless HQ changes during the breach).
* `ClickLost` is now fired for every `LoseClicksEffect`, including a few "spend [click]" choices that cards model as a click
  loss (Adjusted Chronotype may trigger on those).
* Méliès U's secret choice is a journal action, hidden by the UI only (as psi bids).

## Shared-file edits

| File | Edit | Why |
|---|---|---|
| `game.scala` | `with FixRules` at the end of the `Game` mixins | the new trait (outermost stackable overrides) |
| `game.scala` | `++ fixTriggers(e)` at the end of `triggerEffects` | FixRules bookkeeping after the cards saw the event |
| `game.scala` | `orElse performFixes` at the end of the `performInternal` chain | FixRules' own effects |
| `game.scala` | `subtypes(c)` `++ fxHostedSubtypes(c)` | Egret (static subtypes of a host) |
| `game.scala` | `handSize(f)` `+ fxHandSizeMod(f)` | Gyri Labyrinth / Valley Grid outlast their card |
| `game.scala` | `tagged` `|| fxConsideredTagged` | Paparazzi |
| `abilities.scala` | `CardImpl.hostSubtypes(self)`, `CardImpl.clicksPerTurn(self, f)` (neutral defaults) | Egret; static allotted clicks |
| `rules-ext-ph-tai.scala` | `DelegatingImpl` delegates the two new hooks | wrapped cards keep them |
| `rules-ext-ph-tai.scala` | Banner's `no-etr` check: only while a subroutine of the flagged ice resolves | Banner |
| `rules-effects.scala` | `SwapIceEffect` log uses `hiddenCorpElem` | swaps never name unrezzed ice |
| `rules-ext-su21.scala` | Steelskin's `DamageDone` trigger removed (the on-trash hook of damage discards already fires) | Steelskin drew 4 |
| `rules-ext-rot-ice.scala` | `BreakAllSubsEffect` triggers `SubsBroken(c, l, c)` | Endless EULA breaks are events |
| `rules-ext-rot-programs.scala` | `PreventSubsEffect` records `fxNoResolve` instead of breaking | Mass-Driver |
| `rules-ext-rot-operations.scala` | `RecordTraceMarginAction` uses `fxTraceLinkStrength` | real link strength |
| `rules-ext-rot-events.scala` | Corporate Scandal leaving play: `FxAdditionalBadPubEffect(-1)` | additional bad publicity |
| `rules-ext-rot-agendas.scala` | `RAg.corpScored` also true for a Media Blitz in the Corp's play area | Media Blitz |
| `rules-ext-startup.scala` | `Startup.inCorpScore` likewise | Media Blitz |
| `ui.scala` | installed Corp cards: `|| g.fxRunnerKnows(c)` in `canSee` | accessed installed cards stay visible to the Runner |
| `ENGINE.md` | §8.2 | documents FixRules |

All other changes are in card files (`cards-*.scala`) and the new `rules-ext-fixes.scala`.

## Verification

* `sbt -batch compile` (Scala.js project) green.
* JVM harness (scratchpad `fix-misc-host`, a copy of the integration host pointed at this worktree): `ScriptTest` (40),
  `RotIceScript` (211 ice / 633 encounters, 0 failures), `RotAssetScript`, `RotAgendaScript`, `RotEventScript`,
  `RotHardwareScript` (80), `RotOperationScript` (56), `RotProgramScript`, `RotMergeScript` all pass; two checks of the
  harness were updated because they read implementation details that changed (Méliès U's choice is in the memo, not a
  counter; Spear Phishing logs "uses Spear Phishing" + "bypasses Palisade"); the Mass-Driver check passes unchanged.
* New `FixScript` (81 checks, all pass): every card of the table above that bots rarely exercise (bioroid break +
  Oversight AI, AMAZE persistence, Stick and Poke's real subroutine, Word on the Street at 6 points, Myōshu, Petty Cash, Biawak
  pay / ignore costs, Egret, En Passant / Into the Depths, DBS, Deep Dive set-aside, Steelskin, Ob, Nanuq, Spark, allotted
  clicks (Hypoxia, Basilar, Director Haas, MCA / Load Testing), Issuaq, Abaasy fully-broken, Banner + jack out, grail
  subroutines, RSVP, Excalibur / Lockdown / Gyri after trashing, Mind Game jack out, Data Hound, Tracker, Endless Hunger,
  Escher, Corporate Scandal, Cold Read, Divide and Conquer, Wetwork Refit, Localized Product Line / Reclamation Order, Hunter
  Seeker, Mark Yale, Jeeves, Sundew, Heinlein Grid, MemStrips, Jackpot!, Street Magic, The Black File, Paparazzi, Aeneas
  Informant, known accessed card / facedown trash, Archives order, In the Groove, Direct Access, Aumakua, The Zwicky Group,
  Pichação).
* Bot-vs-bot (`MergeHost`, every offered and chosen action round-tripped through the serializer): 4 × 450 games (all presets
  seeds 2026 and 4242; rotated Corp vs rotated Runner presets seed 777; rotated / Startup Corp presets seed 99): **1,800
  games, 0 exceptions, 0 empty asks, 0 serialization mismatches, 0 over-long games**.
