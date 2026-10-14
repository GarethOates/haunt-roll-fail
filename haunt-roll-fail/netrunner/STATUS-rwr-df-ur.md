# Netrunner plugin — Rebellion Without Rehearsal / Downfall / Uprising

Card implementations for the sets `rwr` (65 cards, `cards-rwr.scala`), `df` (65 cards, `cards-df.scala`) and
`ur` (58 cards, `cards-ur.scala`), ported from the jinteki.net Clojure sources, plus the generic mechanics they
need (`rules-ext-rwr-df-ur.scala`) and preset decks built from the three sets (`decks-rwr-df-ur.scala`,
registered through `ExtDecks.presets` in `decks.scala`). Registered in `CardImpls.all` (`abilities.scala`).

## Coverage

Every card of the three sets present in the card database has an implementation object. "Implemented" below
means the printed abilities are automated; "partial" lists what is left to manual play or approximated.

### rwr — Rebellion Without Rehearsal (65/65 implemented, 7 partial)

Runner: Sebastiao Souza Pessoa (partial: the "Corp must trash 1 card from HQ" additional cost on the basic
trash action is not enforced), Eye for an Eye, Privileged Access, Amanuensis, The Wizard's Chest, Boi-tata
(partial: "trashed any of your installed cards" is approximated by "any of your non-event cards was trashed
this turn while Boi-tata was installed"), Heliamphora, Arruaceiras Crew, Friend of a Friend, Manuel Lattes de
Moura (partial: same basic-action trash cost as Sebastiao not enforced), Meeting of Minds, Window of
Opportunity, Alarm Clock, Jeitinho, Cupellation (the "end of breach" Archives hosting is offered when a
successful run on Archives ends), Malandragem, Physarum Entangler, Amelia Earhart, Juli Moreira Lee, Burner,
Spree, Trick Shot, Cataloguer (partial: the click ability breaches R&D through a pseudo-run, so "run ends"
abilities may fire), Coalescence, Lobisomem, Muse, Pressure Spike, "Pretty" Mary da Silva (only sees access
bonuses granted before it resolves, like jinteki), Ashen Epilogue, Valentina Ferreira Carvalho.

Corp: Thunderbolt Armaments, Lightning Laboratory, Warm Reception, Working Prototype, Lycian Multi-Munition,
Sorocaban Blade, Active Policing, Corporate Hospitality, Brasilia Government Grid, See How They Run, Sisyphus
Protocol, Charlotte Cacador, Cohort Guidance Program, Boto, Cloud Eater, Tributary, Bring Them Home, Kingmaking,
Stoke the Embers, Janaina "JK" Dumont Kindelan, Capacitor, Piranhas, Seraph, Sudden Commandment, The Holo Man,
Nuvem SA (partial: "trash a card from R&D" is only recognised for Nuvem's own trash and The Basalt Spire's
cost, i.e. `TrashTopEffect`), Eminent Domain, The Basalt Spire, Hearts and Minds, Descent, Hammer, Logjam,
Business As Usual, Isaac Liberdade, The Powers That Be.

### df — Downfall (65/65 implemented, 5 partial)

Runner: Isolation, Demolisher, Chisel, Stargate, Utae, Climactic Showdown (partial: when the Corp declines
to trash ice, the resource stays in play until the end of the turn to grant the access bonus, then is removed
from the game), Fencer Fueno, The Nihilist, Trickster Taka, Az McCaffrey, Always Have a Backup Plan, Blueberry Diesel, Flip Switch,
Lucky Charm, Masterwork (v37), Bukhgalter, "Baklan" Bochkin, The Class Act, Lat, In the Groove (partial:
"first click" is checked as "4 clicks remaining"), Khusyuk, Spec Work, Supercorridor, Gauss, Pelangi (Barrier /
Code Gate / Sentry offered), Rezeki, The Artist, Direct Access (partial: identities are not disabled during the
run), Rejig, Whistleblower (partial: the resource is trashed when the run ends instead of immediately, the named
agenda is stolen through an access ability "steal ignoring all costs").

Corp: MirrorMorph (partial: like jinteki, the reward is "gain 1 credit or gain [click]"; action kinds are the
basic actions, operations and click abilities), Architect Deployment Test, Calvin B4L3Y, Nanoetching Matrix,
Hagen, Fully Operational, Red Level Clearance, Cold Site Server, Hyoubu Institute, Project Yagi-Uda, Sting!,
Public Health Portal, Storgotic Resonator, Saisentan, Complete Image, Letheia Nisei, Remastered Edition, Daily
Quest, Tiered Subscription, Congratulations!, Loot Box, Focus Group, Game Over, Increased Drop Rates, Divested
Trust, SDS Drone Deployment, Roughneck Repair Squad, Afshar, Sandstone, Trebuchet, Secure and Protect, Reduced
Service, Vulnerability Audit, CSR Campaign, Rime.

### ur — Uprising (58/58 implemented, 2 partial)

Runner: Hoshiko Shiro (flip implemented with a "flipped" counter on the identity; gains Digital / +1 link),
Moshing, Devil Charm, Gachapon, Keiko, Odore, Paladin Poemu, Bravado, Boomerang, Mu Safecracker, Prognostic Q-Loop,
Afterimage, Makler, Penumbral Toolkit, The Back, Harmony AR Therapy, Aniccam, Simulchip, Cordyceps, Euler, Mantle, Penrose,
Self-modifying Code, Paule's Cafe, Buffer Drive, Daily Casts, DreamNet.

Corp: Project Vacheron, Bass CH1R180G4, Cerebral Overwriter, Vaporframe Fabricator, Drafter, Tyr, NEXT Activation Command,
Scapenet, Tranquility Home Grid, Flower Sermon, Prana Condenser
(prevents net damage from any source; one use per damage instance), Engram Flush, Konjin, Hyoubu Precog
Manifold, Kakurenbo, GameNET (partial: triggers on payments of "pay N credits" choices of Corp card abilities during
a run (`CreditsPaid` with `Spend.corpAbility`) and on any credit loss of the Runner during a run, whose source is not
tracked), Bellona, F2P, Gold Farmer, SYNC Rerouting, Ganked!, Earth Station, Transport Monopoly, Wall to Wall, Akhet, Colossus, Winchester,
Argus Crackdown, Cyberdex Sandbox, False Lead, NAPD Cordon.

Not in the database (so not implemented): the `ur` codes 26072, 26078, 26091, 26096, 26112, 26117, 26127.

## New generic mechanics (`rules-ext-rwr-df-ur.scala`)

* `ExtRules` trait mixed into `Game` (`performExt`, `resolveExtPurpose`): threat level (`threat(n)`),
  `runnerInstalled`, `subs(ice)` (printed subroutines plus `extraSubroutines` granted by other cards),
  `runCosts(server)` (additional run costs), `breakCosts` (per-subroutine break costs, `BreakAbility.perSub`),
  `lockdownActive`, `installRunner` (install from any zone with discount, trojans choose a host).
* Forced encounters outside the approach order: `ForceEncounterEffect(ice)` (Konjin, Ganked!, Sisyphus Protocol);
  the run remembers the interrupted ice/phase (`Run.forced`, `Run.forcedPrev`) and `EncounterEndsAction` restores
  it. New event `EncounterEnds(ice)` (Cloud Eater, Sorocaban Blade, Saisentan, Engram Flush, Pelangi).
* Run redirection: `RedirectRunEffect(position, jackOut)` (Letheia Nisei), `MoveIceOutermostEffect` (Tributary),
  `BlockSuccessEffect` / `Run.blockSuccess` (Transport Monopoly), `ReplaceBreachEffect` ("instead of breaching":
  Stargate, Khusyuk, Burner, Privileged Access, Cataloguer), `RunIgnoringCostsEffect`, `AtRunEndEffect(effects)`
  (`Run.atEnd`: Boomerang, Window of Opportunity).
* Bypass now triggers `IceBypassed(ice)` (Jeitinho); `BypassEffect` logs.
* Prevention framework extended: Corp preventions (`PreventWindowAction` asks the Corp first: Prana Condenser),
  prevention kinds `tag` (fixed: the pass action used to apply damage instead of tags), `trace` (Flip Switch:
  the prevention effect continues the trace with `FlipSwitchTraceAction`), `end-run` (Lucky Charm; `EndRunEffect`
  asks, `EndRunNowEffect` ends).
* Draw interrupts: `CardImpl.beforeDraw` (The Class Act); `DrawEffect` resolves them before `DrawNowEffect`.
* Lockdowns / lingering operations: `CardImpl.staysInPlay` keeps an operation or event in the play area after it
  resolves (NEXT Activation Command, Hyoubu Precog Manifold, SYNC Rerouting, Argus Crackdown, NAPD Cordon, In the
  Groove); the card trashes itself at the proper time. `PlayResolved(f, card)` is triggered when an
  operation/event finishes resolving (Nuvem SA, MirrorMorph) and `Trashed` is triggered when it is trashed
  afterwards (Aniccam). `EndActionPhaseEffect` for terminals.
* Expendable cards: `CardImpl.activeInHand` makes a card's abilities usable from hand (Eminent Domain, Descent);
  `ExpendResolved(card)` event. `CardImpl.activeInHeap` for cards with abilities in the heap (Jeitinho).
* Costs: `TrashInstalledCost(kind)` (SDS Drone Deployment steal cost, Simulchip), global steal cost modifiers
  `CardImpl.stealCostMod` (NAPD Cordon), additional run costs `CardImpl.runCost` (Cold Site Server, Reduced
  Service, Earth Station), paid in `startRun` and checked in the run menu.
* Installs with discounts / install-and-rez: `RunnerInstallPayingEffect`, `CorpInstallPayingEffect`,
  `ChooseServerPayingEffect`, purposes `InstallPayingPurpose`, `InstallPayingChooseServerPurpose`.
* Hosting: `HostOnEffect(card, host, uninstalled)` and `CardState.uninstalled` (hosted but not installed:
  Paule's Cafe, Cupellation, Heliamphora, Spree moves trojans), `HostOnPurpose`.
* Randomness: `RandomPickEffect(f, cards, n, purpose)` (random cards from a hand: Burner, Bring Them Home,
  Hyoubu Institute, Heliamphora). Naming a card: `ChooseCodeEffect` (Whistleblower, Complete Image; codes only,
  no titles in actions). Looking at cards: `LookAtEffect` (shown only to the chooser), `ArrangeTopEffect`.
* Other effects/purposes: `PlayFreeEffect` (Sudden Commandment), `ForfeitEffect` (False Lead, Divested Trust),
  `TurnFaceupEffect` / `TurnFacedownEffect`, `JackOutEffect`, `ExtraClicksEffect` (allotted clicks for the next
  turn: Active Policing, Tyr), `SetMemoEffect` / `SetTargetEffect` (`CardState.memo` / `CardState.target`),
  `RevealCardsEffect` (+ `Revealed` event, Hyoubu Institute), `AddToScoreAreaEffect` (Kingmaking, Jeitinho),
  `MoveToRootEffect` (The Holo Man, Isaac Liberdade), `SwapInstalledWithHandEffect` (Project Yagi-Uda),
  `RemoveCountersEffect`, `TrashTopEffect` (+ `DeckTrashed` event), `ReturnToHandEffect`, `MarkUsedRunEffect`,
  `RunnerTrashInstalledEffect`, `CorpTrashFromHandEffect`, `CopyAbilityEffect`; purposes `DerezPurpose`,
  `RemoveFromGamePurpose`, `BottomOfDeckPurpose`, `TopOfDeckPurpose`, `TurnFaceupPurpose`, `ForfeitPurpose`,
  `TrashFromHandPurpose`, `AddToScorePurpose`, `ReturnToHandPurpose`, `RemoveCountersPurpose`.
* New events: `EncounterEnds`, `IceBypassed`, `PlayResolved`, `ExpendResolved`, `Revealed`, `AbilityUsed`
  (Juli Moreira Lee, The Back), `CreditsSpentFrom` (Keiko), `CreditsLost` (GameNET), `ActionTaken` (MirrorMorph),
  `TagsRemoved` (Amanuensis, Valentina), `Forfeited`, `DeckTrashed`.
* Hooks added to `CardImpl`: `staysInPlay`, `activeInHand`, `activeInHeap`, `canScore` (Vulnerability Audit,
  Warm Reception via the `no-score` marker), `canRezIn` (Daily Quest: action phase only; Rime: any time during
  runs on its server), `runCost`, `stealCostMod`, `extraSubroutines` (Thunderbolt Armaments), `breakAllowed`
  (Hammer, Afshar, Akhet, NEXT Activation Command), `beforeDraw`.

## Shared-file edits (all additive; for the merge)

* `abilities.scala`: 10 new `CardImpl` hooks with default implementations (listed above); `CardImpls.all`
  registers `RWRCards.all ++ DFCards.all ++ URCards.all`.
* `game.scala`: `CardState` fields `target`, `memo`, `uninstalled` (+ reset); `Run` fields `blockSuccess`,
  `forced`, `forcedPrev`, `atEnd`; `Cost.desc` case for `TrashInstalledCost`; `BreakAbility.perSub` default
  parameter; `Game` mixes in `ExtRules`; `isInstalled` / `installedCards` ignore `uninstalled` hosted cards;
  `activeCards` includes heap cards with `activeInHeap`; `canPay` case for `TrashInstalledCost`;
  `performInternal` chains `orElse performExt`.
* `rules-effects.scala`: `DrawNowEffect` / `EndRunNowEffect`; `LoseCreditsEffect` triggers `CreditsLost`;
  `DrawEffect` resolves `beforeDraw` interrupts; `DamageEffect` resets the Corp prevention flag;
  `PreventWindowAction` offers Corp preventions; `PreventPassAction` dispatches by kind (fixes the tag case);
  `PreventUseAction` logs the ability label; `RemoveTagsEffect` triggers `TagsRemoved`; `EndRunEffect` offers
  `end-run` preventions then `EndRunNowEffect`; `RevealEffect` triggers `Revealed`; `TakeCreditsEffect` and
  `payCredits` trigger `CreditsSpentFrom`; `TraceEffect` offers `trace` preventions; `resolvePurpose` falls back
  to `resolveExtPurpose`; `payCosts` handles `TrashInstalledCost`.
* `rules-run.scala`: `startRun` pays additional run costs; subroutine lookups use `subs(ice)`;
  `EncounterAction` honours `breakAllowed` and per-subroutine costs; `BreakSubsAction` pays scaled costs;
  `EncounterEndsAction` handles forced encounters and triggers `EncounterEnds`; `SuccessfulRunAction` honours
  `blockSuccess`; `RunEndsAction` pushes `Run.atEnd`; `BypassEffect` logs and triggers `IceBypassed`.
* `rules-turn.scala`: `StartTurnAction` applies `extraClicks`; basic actions trigger `ActionTaken` (credit,
  draw, install, advance, trash-resource, purge, run, remove-tag; `ClickCreditAction` / `RemoveTagAction` now go
  through the queue); `RemoveTagAction` triggers `TagsRemoved`; `RunMenuAction` greys out unaffordable run
  costs; `scorableAgendas` honours `canScore` and the `no-score` marker; `rezOptions` honours `canRezIn`.
* `rules-cards.scala`: `CorpInstallEffect` records `lastInstallFrom`; `PlayCleanupAction` triggers
  `PlayResolved`, keeps `staysInPlay` cards, triggers `Trashed` for the discarded card.
* `rules-abilities.scala`: `abilityOptions` includes hand cards with `activeInHand`; `useAbility` triggers
  `AbilityUsed` and `ActionTaken`.
* `rules-access.scala`: steal costs include `stealCostMod` (decision and steal); access abilities whose label
  starts with "host" are offered on Archives cards (Heliamphora, Cupellation).
* `decks.scala`: presets list appends `ExtDecks.presets`.

## Simplifications


* "Instead of breaching" effects set `Run.preventAccess`, so the log says "is prevented from breaching".
* Access bonuses granted at "breach" time by several cards resolve in trigger order ("Pretty" Mary).
* Game Over's prevention lets the Runner choose the cards to keep before trashing the others.
* The Wizard's Chest / Gachapon reveal the set-aside cards in the log (they are faceup).
* Psi games, traces and card lookups are hidden only by the UI as in the rest of the engine; the prompts that
  list looked-at cards (`LookAtEffect`) are shown only to the looking player.

## Testing

* `sbt -batch compile` of the Scala.js project passes.
* JVM harness (a copy of `host.scala` whose bots are restricted to the ten new preset decks, half Basic / half
  Random): 2 x 200 games (plus a 30-game smoke run) with zero exceptions, empty asks or over-long games; the
  Runner wins ~89% (the Corp bot is weak with these decks). All 188 cards appear in the logs; the sequences for forced encounters (Sisyphus Protocol, Ganked!, Konjin), Cloud Eater, Prana
  Condenser, Winchester (incl. the HQ subroutine), Thunderbolt Armaments' subroutine (including Tyr's bioroid
  break), Khusyuk, Stargate, Cataloguer, Lycian Multi-Munition, Sorocaban Blade, Saisentan, Letheia Nisei, Loot
  Box, Game Over, Focus Group, F2P, Gold Farmer + GameNET, Flip Switch, "Baklan" Bochkin, Cordyceps, Alarm Clock /
  Malandragem bypasses, The Class Act, Cupellation / Heliamphora hosting, Muse, Earth Station run costs, False
  Lead, Divested Trust, Jeitinho, Kakurenbo, Eminent Domain / Descent expends, Project Vacheron, Window of
  Opportunity, Trick Shot and Always Have a Backup Plan were checked by hand in the logs.
* No browser testing was done for these sets.
