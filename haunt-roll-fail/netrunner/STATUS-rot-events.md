# Rotated / FFG-era Runner events — status

Port of every card of type `Event` that had no implementation (`Cards.all.%(_.kind == "Event")` minus the codes already in
`CardImpls.all`): **148 events, all implemented**, ported from jinteki.net `src/clj/game/cards/events.clj` and checked
against `test/clj/game/cards/events_test.clj`. They come from 51 FFG-era products (System Core 2019, Order and Chaos,
Honor and Profit, Reign and Reverie, the Terminal Directive cards, the data packs of every cycle, Magnum Opus, ...).

| File | Contents |
|---|---|
| `cards-rot-events-1.scala` | `RotEventCards1` (76 events: Core Set .. Kala Ghoda) and their card-specific actions/purposes |
| `cards-rot-events-2.scala` | `RotEventCards2` (72 events: The Devil and the Dragon .. System Core 2019 / reprints) and `RotEventCards.all` |
| `rules-ext-rot-events.scala` | `trait RotEventRules` (generic mechanics) and the card base class `RE(code)` |
| `decks-rot-events.scala` | `RotEventDecks.presets`: 13 eternal-legal Runner decks (3 Anarch, 4 Criminal, 3 Shaper, Adam, Apex, Sunny Lebeau) |

Every eternal-legal event is in at least one preset deck. Security Leak (Terminal Directive Campaign) and Watch the World Burn
(Magnum Opus) are not legal in any format of the card database, so they appear in no preset and are covered by the scripted
tests only.

## Card base class `RE(code)` (rules-ext-rot-events.scala)

* `lingering(self)`: how long the event stays in the play area after resolving — `""` (not at all), `"turn"`, `"corp-turn"`,
  `"runner-begins"`, `"corp-begins"`, `"current"` (default for the *Current* subtype: until another current is played or the
  opponent scores/steals an agenda). `staysInPlay` follows it; the end of the duration resolves `lingerEnd(self)` (trash, or
  remove from the game for The Price of Freedom). The expiry triggers are game-level (`lingerTriggers`), so the card's own
  `triggers` still fire on the same event (Three Steps Ahead pays out on `TurnEnds` before it is trashed).
* Helpers: `thisCardRun`, `activeRun`, `inPlay`, `firstClick` ("play only as your first [click]"), `doubleOk(n)` /
  `doubleCost(n)` (double events: extra clicks as an additional cost), `centralsRun` / `centralRun`, `runAny` / `runRemote` /
  `runOn`, `runnerCanInstall` / `installable(l, discount)`, `breakers`, `unrezzedIce`, `remoteRootUnrezzed`.

## New generic mechanics (`trait RotEventRules`, `performRotEvents`, `rotEventTriggers`)

* Effects: `ExposeEffect(cards)` (+ `Exposed` event, `"exposed"` register, `ExposePurpose`), `RunnerInstallFacedownEffect`,
  `TurnRigFacedownEffect`, `ExtraTurnEffect(f)` (additional turns, handled at `EndTurnCleanupAction`), `SwitchIdentityEffect(f, code)`,
  `DrainCreditsEffect(max, mult, tags)` (Account Siphon / Diversion of Funds), `BreachNoRootEffect(server)` (pseudo-breach without
  root cards), `SetRunMarkerEffect(key)` (`"bottom"`: access R&D from the bottom, `"no-hand"`: no HQ cards this breach),
  `TrashAccessedEffect` / `RfgAccessedEffect` (an access trigger disposes of the accessed card; its access decision is skipped),
  `RemoveFlagsScopeEffect(scope)`, `RemoveAdvancementEffect`, `RunnerTrashCardsEffect`, `CorpAddAllToTopEffect` (CBI Raid),
  `SecretSpendEffect(card, max)` → `SecretSpendAction` (the amount is only in the Runner's ask; the log says "spends credits
  secretly") → `SecretSpentAction(card, n)` dispatched to the card (Push Your Luck, Rigged Results; Government Investigations
  removes the option 2 via `secretAmounts`).
* Game-level restrictions applied by intercepting the engine asks (`rotFix` on `ActionPhaseAction`, `PaidWindowAction`,
  `EncounterAction`, `InstallWhereAction`, `RunMenuAction`, `InstallMenuAction`, `RezMenuAction`, `AdvanceMenuAction`; only while
  `rotActive`): no ice rez during a run (`no-ice-rez` flag: Blackmail, Cyber Threat), no non-ice rez on the Runner's turn
  (Interdiction), rezzing non-ice trashes a random HQ card (Hacktivist Meeting), advancing costs 1 more (Security Leak) / is
  forbidden (`no-advance` flag: The Price of Freedom), 1 ice install per turn (Unscheduled Maintenance), servers that cannot be
  run (`no-run` flag: Peace in Our Time, Marathon).
* Run handling: second run of R&D after the cleanup of the first (Data Breach, Mobius: `memo = "run-again"`), R&D accessed from
  the bottom (Showing Off), access restrictions for run markers and "cannot access this card this turn" marks (Queen's Gambit).
* Game-level triggers: registers `ice-installed`, `runner-install`, `click-ability`; `virus-placed` marks (Surge); I've Had Worse
  drawing when trashed by net/meat damage; Political Graffiti trashed on purge; Employee Strike / Rumor Mill flags and Corporate
  Scandal's bad publicity removed when the current leaves play.

## Simplifications

* Escher: rearranging is done by up to 6 pairwise swaps of installed ice (any arrangement of up to 7 pieces is reachable).
* Corporate Scandal: implemented as a real bad publicity gained on play and removed when the current leaves play.
* Cold Read: the program trashed at the end of the run is any installed program (no tracking of "used during the run").
* Immolation Script: the replacement is offered once when Archives is breached (choose a faceup ice in Archives with a rezzed
  copy), not per accessed ice.
* Divide and Conquer: the HQ and R&D breaches (no root cards) happen when the Archives run ends.
* Prey: only rezzed ice can be trashed.
* Forked / Knifed / Spooned: "fully broken" is checked on the engine's `SubsBroken` event, which bioroid click-breaking does
  not emit.
* The restrictions of Interdiction, Hacktivist Meeting, Unscheduled Maintenance, Blackmail and Cyber Threat apply to every rez /
  install path, card effects included (see `STATUS-fix-restrictions.md`); Security Leak and The Price of Freedom restrict the basic advance
  action (no card effect "advances" a card). A Corp window whose only option is greyed out by them still shows its "pass" button.
* Leverage / The Noble Path: damage prevention is offered as a free prevention ability (not applied automatically).
* Watch the World Burn: later accesses remove copies with the same card code (other printings of the title are not matched).
* Rebirth: the candidates are all format-legal Runner identities of the same faction (not restricted to the deck's format).
* Apocalypse / Reboot: facedown Runner cards have no abilities and use no MU; turning them faceup again is left to manual
  adjustment.
* Push Your Luck / Rigged Results: the secret amount is hidden by the UI only (like psi bids); Government Investigations
  restricts these two cards' secret spending, not psi bids.

## Shared-file edits

| File | Line | Edit | Why |
|---|---|---|---|
| `abilities.scala` | 128 | `++ RotEventCards.all` in `CardImpls.all` | registration |
| `decks.scala` | 252 | `++ RotEventDecks.presets` | preset decks |
| `game.scala` | 339 | `with RotEventRules` | mix in the mechanics |
| `game.scala` | 573 | `agendaPoints` adds `hosted(c)./(h => impl(h).agendaPointsBonus(h)).sum` for scored cards | Political Graffiti hosted on a scored agenda makes it worth 1 less point |
| `game.scala` | 581 | `memoryUsed` skips facedown programs (`.%!(c => state(c).facedown)`) | facedown cards (Apocalypse, Reboot) use no MU |
| `game.scala` | 682 | `++ rotEventTriggers(e)` in `triggerEffects` | game-level triggers and lingering-event expiry |
| `game.scala` | 753 | `performRotEvents orElse` at the **front** of the chain | it wraps `performPHTAI` / `performTurn` asks to apply the restrictions, so it must see those actions first; it only matches when `rotActive` (or for its own effects) |

## Verification

* `sbt -batch compile` green.
* JVM harness (`scratchpad/rot-events-host`, `RotHost` = mergehost + rot-event tally, Runner decks restricted to the rotated
  presets, Corp decks from all presets): 3000+ bot-vs-bot games over 4 seeds (300 + 2000 + 1000 + 1000 final), plus 500 games with
  all Runner decks: no exception, empty ask, non-ASCII or serialization mismatch from these cards; 146/148 events appear in the
  logs and 141 are played (the rest need three central runs in one turn, or are not legal). Every game too long (1 per ~1000)
  is the pre-existing Plutus rez loop of `cards-elev.scala` (Zwicky Corp deck; also seen with su21 Runner decks and in the
  other agents' harnesses): Plutus has no `canRez` check for its additional cost, so a bot repeatedly rezzes it for 0, cannot
  pay the additional cost, derezzes it. Suggested fix (not applied, not this port's file): `override def canRez(self : Card)(implicit
  g : Game) = g.scored(Corp).exists(_.data.isAgenda) || g.hand(Corp).num >= 3`.
* Scripted scenarios (`RotScriptTest`, 40 checks, all OK): Apocalypse, Encore, Exploit, Quest Completed, Notoriety, Security
  Leak, Watch the World Burn, Kraken, Day Job, Populist Rally, On the Lam, Singularity, Corporate "Grant", Spear Phishing,
  Political Graffiti (incl. purge), Rebirth, Data Breach, Showing Off, "Freedom Through Equality", Reboot, Divide and Conquer,
  Black Hat, Account Siphon, Knifed, Unscheduled Maintenance / Interdiction (current replacement, rez restriction), Push Your Luck.

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
