# System Update 2021 / Midnight Sun / Uprising Booster Pack — status

Files: `cards-su21.scala` (82 cards, `SU21Cards.all`), `cards-ms.scala` (65 ms cards + 7 urbp cards, `MSCards.all`),
`rules-ext-su21.scala` (generic mechanics, `ExtRules` trait mixed into `Game`), preset decks in `decks.scala`.
Registered in `CardImpls.all` (`abilities.scala`). All 154 cards of the three sets have an implementation object.

## Coverage

### su21 (82/82 implemented)

Fully implemented: Quetzal, Reina Roja, En Passant, Retrieval Run, Clot, Corroder, Imp, Mimic, Ice Carver, Liberated Account,
Scrubber, Xanadu, Ken Tenma, Steve Cambridge, Career Fair, Emergency Shutdown, Forged Activation Orders, Inside Job, Legwork,
Networking, Abagnale, Femme Fatale, Sneakdoor Beta, Security Testing, Ayla, Kit, Diesel, Test Run, The Maker's Eye, Atman,
Chameleon, Gordian Blade, Paricia, Aesop's Pawnshop, Professional Contacts, Dirty Laundry, Prepaid VoicePAD, Earthrise Hotel,
HB: Architects of Tomorrow, Project Vitruvius, Marilyn Campaign, Eli 1.0, Magnet, Ravana 1.0, Rototurret, Archived Memories,
Biotic Labor, Corporate Troubleshooter, Jinteki: PE, House of Knives, Nisei MK II, Ronin, Snare!, Lotus Field, Swordsman,
Celebrity Gift, Trick of Light, Hokusai Grid, NEH, License Acquisition, Project Beale, Daily Business Show, Reversed Accounts,
Pop-up Window, Tollbooth, Wraparound, Psychographics, SanSan City Grid, Weyland: BABW, Hostile Takeover, Oaktown Renovation,
Project Atlas, Corporate Town, Archer, Hortum, Ice Wall, Punitive Counterstrike, Crisium Grid, PAD Campaign, Enigma,
Subliminal Messaging.

Partially implemented / simplified:
* **Egret** — the host keeps the three subtypes until the host leaves play even if Egret is trashed alone (card state is reset
  before `onTrash`, so the host is unknown then).
* **En Passant** — "ice passed during the last run" is approximated by the unrezzed ice of the last run's server at positions
  the Runner moved past (`lastRun.position`).
* **Daily Business Show** — the extra draw applies to the first draw of the turn (register `drawn-Corp == 0`); with several
  rezzed DBS each one asks for one card to put on the bottom (jinteki asks once for n cards).
* **Sneakdoor Beta** — fixed: the server is changed to HQ when the run would be declared successful (`RunWouldSucceed`, see
  `STATUS-fix-prevention.md`).
* **Ayla** — the 4 set-aside cards are hosted on the identity (not shown in the UI yet); the `[click]` ability works.
* **Atman** — the break option is simply absent (not greyed out) when the ice strength differs.
* **Oaktown Renovation** — installed faceup by marking it rezzed+seen (so its advance trigger is active).
* **Corporate Town / Archer** — the forfeit happens right after the rez (`ForfeitEffect`) instead of as a cost before it; the rez
  option is unavailable without a scored agenda (`canRez`).
* **Marilyn Campaign** — "shuffle into R&D instead" is offered after the card reached Archives (it is still trashed).
* **Reina Roja** — "first ice rezzed each turn" uses a register incremented by a `Rezzed` trigger (correct for sequential rezzes).

### ms (65/65 implemented, including the eternal-only Sable, Endurance, Drago Ivanov, Extract)

Fully implemented: Esa Afontov, Chastushka, Running Hot, Steelskin Scarring, Ghosttongue, Marrow, Begemot, Avgustina,
Light the Fire!, The Twinning, Sable, Carpe Diem, Pinhole Threading, PAN-Weave, Virtuoso, Cat's Cradle, Cezve, Revolver,
Backstitching, No Free Lunch, Padma, Deep Dive, Into the Depths, Rigging Up, Endurance, Hyperbaric, Propeller, Daeg,
Environmental Testing, Stoneship Chart Room, Elivagar Bifurcation, Midnight-3 Arcology, Refuge Campaign, Trieste Model Bioroids,
Echo, Hakarl 1.0, Wave, Big Deal, Blood in the Water, Regenesis, Bladderwort, Moon Pool, Anemone, Bathynomus, Ivik, Mitosis,
Mavirus, Pravdivost, Artificial Cryptocrash, Chekist Scion, Drago Ivanov, Ubiquitous Vig, Mestnichestvo, Vasilisa,
Backroom Machinations, Vladisibirsk City Grid, Ob Superheavy Logistics, Azef Protocol, Svyatogor Excavator, Envelopment,
Maskirovka, Stavka, Extract, Mutually Assured Destruction, Trust Operation.

Partially implemented / simplified:
* **Deep Dive** — the 8 cards are not physically set aside: the Runner chooses among the (sorted) top 8 cards of R&D and
  accesses it in place; the second access excludes the first card; R&D is shuffled at the end.
* **Into the Depths** — "times you passed ice" = number of ice protecting the server at the time of the successful run (max 3).
* **Steelskin Scarring** — the "trashed from grip or stack" trigger fires for damage (via `lastDamaged`) and for trashes through
  `trashCard`; discarding it at end of turn / milling does not trigger it.
* **Ob Superheavy Logistics** — fires on any Corp-caused trash of a rezzed asset/upgrade/ice (`lastTrashedRezzed`), including the
  trash of an asset replaced during an install (jinteki excludes "during installation").
* **Azef Protocol** — the "trash another installed card" additional cost is paid right after scoring (and scoring is blocked
  without another installed card via `preventsScoring`).
* **Light the Fire!** — "cards in the root lose all abilities" is implemented for triggered abilities (`isDisabled` filter in
  `Game.trigger`); static modifiers (e.g. SanSan) of those cards still apply during the run.
* **Mitosis** — "cannot score or rez this turn" uses per-card `usedTurn` markers (`cannot-score`, `cannot-rez`), which are
  cleared at the start of the next turn.
* **Anemone** — the HQ card trashed as a cost goes to Archives facedown (`DiscardPurpose`).
* **Hakarl 1.0** — "cannot use paid abilities printed on bioroid ice" blocks the `runnerAbilities` of all bioroid ice via the
  register `no-bioroid-abilities` (greyed out in the encounter prompt).
* **Trieste** — blocks every Runner card's break abilities on the chosen ice (identity abilities such as Quetzal's still work,
  like jinteki).
* **Mystic Maemi** — the end-of-turn choice is forced only when hosted credits >= 3; "take 1 credit" paid ability included.

### urbp (7/7 implemented)

Mystic Maemi, Swift, Cybertrooper Talut, Megaprix Qualifier, La Costa Grid, Digital Rights Management, Cayambe Grid.
* **Cayambe Grid** — the Runner is asked to pay 2 x advanced ice or end the run on approach; paying 0 is offered when no ice is
  advanced (jinteki does the same).
* **Megaprix Qualifier** — the extra point is granted whenever it has an agenda counter (counters are only placed on score).

## New generic mechanics (`rules-ext-su21.scala`)

* **Sabotage n** — `SabotageEffect(n)`: the Corp chooses which HQ cards to trash (at least n minus R&D size), the rest from the top
  of R&D; everything is trashed facedown.
* **Charge** — `ChargeEffect(f)` / `chargeable(f)`: place 1 power counter on an installed card that already has one.
* **Mark** — `IdentifyMarkEffect` (random central via `Shuffle`), stored in the per-turn register `mark` (1/2/3), `g.mark`,
  `g.isMark(s)`; cleared automatically each turn.
* **Replace breach** — `ReplaceBreachEffect` ("instead of breaching ...": sets `run.preventAccess`).
* **Access outside a breach** — `AccessCardsEffect(server, cards, noStealOrTrash)` and `BreachServerEffect(server)` create an
  `accessOnly` pseudo-run (new `Run.accessOnly` / `Run.outer` fields) so the normal access machinery can be used for Pinhole
  Threading, Deep Dive and Virtuoso's "breach HQ when the run ends"; `SyncPseudoRunAction` propagates steal/trash/access flags to
  the outer run.
* **Recurring credits** — `RefillCreditsEffect(card, n)` plus the `CardImpl.creditsUsableFor(self, purpose)` hook: purposes
  `"play-event"`, `"play-operation"`, `"trash-<kind>"` (asset/upgrade/...), used by `PlayMenuAction`/`PlayCardAction` and
  `PayTrashAction` through `payCredits(f, n, self, purpose)` / `availableCredits(f, self, purpose)`.
* **Discounted installs / rezzes** — `InstallDiscountEffect(card, n)` (trojans choose a host), `RezDiscountEffect(card, n)`.
* **Forfeit** — `ForfeitEffect()` / `ForfeitPurpose()`; **install and rez ignoring all costs** — `InstallRezPurpose()`.
* **Bad publicity** — `extTriggers(RunInitiated)` gives the Runner `badPublicity` run credits when a run begins
  (`BadPublicityCreditsEffect`, `Run.credits`; spent before the pool and lost when the run ends, see `rules-ext-credits.scala`).
* Misc: `RandomTrashFromHandEffect`, `LoseAllClicksEffect`, `AddSubtypeEffect`/`RemoveSubtypeEffect`, `ScoreAgendaEffect`,
  `AddToScoreAreaEffect`, `MoveAdvancementEffect`, `SetChoiceEffect`/`SetTargetEffect` (new `CardState.choice`/`target`),
  `SetRunFlagEffect("disable-root")`, `PumpBreakerAllEffect` (turn-long breaker pump), `MarkRunUsedEffect` (once per run),
  purposes `BottomOfDeckPurpose`, `TopOfDeckPurpose`, `DerezPurpose`, `HostOnPurpose`, `TrashAndTagPurpose`.
* `extTriggers(e)` — game-level triggers not tied to an active card: Subliminal Messaging in Archives, Test Run's return to the
  stack, Steelskin Scarring trashed by damage, Light the Fire! (disable root / trash root), bad publicity credits.

## Shared-file edits (all additive, no reformatting)

* `abilities.scala` — new `CardImpl` hooks with neutral defaults: `creditsUsableFor`, `canRez`, `preventsScoring`,
  `preventsBreak`, `cannotLowerStrength`, `preventsSuccessfulRun`, `disablesHosted`, `drawBonus`, `onSetup`;
  `CardImpls.all` now includes `SU21Cards.all ++ MSCards.all`.
* `game.scala` — `Game ... with ExtRules`, `performExt` appended to the `performInternal` chain; `CardState.target`/`choice`
  (+ reset); `Run.server` is now a `var` (Sneakdoor Beta); `Run.accessOnly`/`outer`/`disableRoot`; events `CardsDrawn(f, cards)`
  and `ServerCreated(server)`; `isActive` ignores cards hosted on a `disablesHosted` host (Magnet); `toArchives` increments
  register `to-archives` (Regenesis); `newRemote` increments `remote-created` and triggers `ServerCreated` (NEH);
  `advancementRequirement`/`rezCost`/`installCost` also apply the card's *own* modifier when it is not active (Blood in the
  Water, Ivik, Carmen); `iceStrength` honours `cannotLowerStrength` (Lotus Field); `availableCredits(f, self, purpose = "")`,
  `creditSourcesFor(f, purpose)`; `trigger` skips `isDisabled` cards and appends `extTriggers(e)`.
* `rules-effects.scala` — `DrawEffect` applies `drawBonus` (only if the deck can afford it) and triggers `CardsDrawn`;
  `lastTrashedRezzed` recorded in `trashCard` before the state reset; `resolvePurpose` dispatches `ExtPurpose`;
  `payCredits(f, n, self, purpose = "")` uses `creditSourcesFor` and increments register `spent-from-card` (The Twinning).
* `rules-turn.scala` — play cost affordability/payment use purpose `"play-<kind>"`; `scorableAgendas` honours the per-card
  `cannot-score` marker and `preventsScoring`; `rezOptions` honours `canRez` and the `cannot-rez` marker; discard phase is
  skipped when register `skip-discard` is set (Midnight-3); `lastScoredAdvancement` recorded in `scoreAgenda` (Vitruvius,
  Beale, Atlas). **Bug fixes (ordering):** `ScoreAction`, `RezAction`, `TrashResourceAction`, `RunnerInstallAction` now push the
  follow-up ask *before* calling `scoreAgenda`/`rezCard`/`trashCard`, so on-score/on-rez/on-trash effects and triggers resolve
  immediately instead of lingering behind the next Ask (previously e.g. Offworld Office's credits arrived a turn later when the
  score was the last click).
* `rules-run.scala` — the Runner identity is included in the breaker list (Quetzal); break options honour `preventsBreak`;
  `SuccessfulRunAction` ends the run without success when a rezzed root card `preventsSuccessfulRun` (Crisium Grid; the run is
  then recorded as unsuccessful, a simplification).
* `rules-access.scala` — trash cost affordability/payment use purpose `"trash-<kind>"`; `BreachEndAction` restores the outer run
  for `accessOnly` pseudo-runs; **ordering fix:** `StealAction`/auto-steal/`PayTrashAction` push `AccessDoneAction` before the
  steal/trash so steal/trash triggers resolve before the next access.
* `rules-abilities.scala` — runner abilities on bioroid ice are greyed out while register `no-bioroid-abilities` is set.
* `rules-setup.scala` — identities' `onSetup` effects are queued after the shuffles and before the starting hands (Ayla).
* `rules-manual.scala` — ordering fix in `ManualTrashAction` (re-ask pushed before the trash).
* `ui.scala` — agenda counters are displayed on cards (one line in `countersElem`).
* `decks.scala` — 15 new preset decks (6 Corp, 9 Runner, 45 cards, all legal) built from su21/ms/urbp.
* `host.scala` (JVM harness only) — `HRF_DECK_MIN_INDEX=n` restricts bots to preset decks with index >= n.

## Testing

* `sbt -batch compile` (Scala.js project) passes; JVM harness (copy of the scratchpad `host` project pointed at this worktree)
  compiles and runs.
* Bot-vs-bot stress test with `HRF_DECK_MIN_INDEX=7` (only the new decks): 100 + 200 + 200 games, final batch 200 games with
  0 errors (no exceptions, no empty asks, no over-long games). Log inspection confirmed: sabotage (HQ/R&D choice), mark
  identification, charge, Deep Dive, Virtuoso's end-of-run HQ breach, Pinhole Threading access of another server, Ravana
  resolving other bioroid subroutines, Hortum, Forged Activation Orders, Moon Pool, Trieste, Hakarl (derez + block), Mitosis,
  Big Deal, Psychographics, Snare! (tag + 3 net), Envelopment's dynamic subroutines and self-trash, Archer/Corporate Town
  forfeits, Test Run returning to the stack, Chameleon, Atman, Femme Fatale targeting, Sneakdoor Beta, Security Testing
  replacement, Ayla set-aside, Kit's Code Gate, Into the Depths, Rigging Up, Light the Fire! (root trashed), Backstitching bypass,
  Carpe Diem, Steve Cambridge, Daily Business Show bottom-decking, Marilyn, Ob installs, Regenesis, Corporate Troubleshooter,
  Celebrity Gift, bad publicity credits, Subliminal Messaging recursion, Cayambe/La Costa advancement, DRM, Trust Operation,
  Mutually Assured Destruction, Extract, Stavka, Anemone, Wave, Echo, Mavirus, Chekist Scion, Vladisibirsk, Azef, Svyatogor,
  recurring credit refills and spending, Tollbooth, Pop-up Window, Magnet hosting, Punitive traces, Crisium Grid, Hostile
  Takeover (7 credits + bad publicity), Steelskin, Environmental Testing, Daeg charges, derez effects (Emergency Shutdown,
  Elivagar).
* 120 unfiltered games (SG starter decks mixed with the new decks): 0 errors.
* Bugs found and fixed during the stress test: two Ravana 1.0 resolving each other forever (now excluded), Trust Operation
  offering agendas to "install and rez", Light the Fire! run flag missing, the on-score/on-rez/on-trash ordering bug above.

## Not done / ideas

* Hosted cards on identities (Ayla) are not rendered in the UI.
* The Twinning's power counter was not observed in the bot logs (needs a run credit source and a card spending from it in the
  same game); the logic follows the `spent-from-card` register.
