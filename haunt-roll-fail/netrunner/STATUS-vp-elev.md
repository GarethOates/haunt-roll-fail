# Netrunner plugin — Elevation (elev) and Vantage Point (vp) status

Files: `cards-elev.scala` (82 cards, `ElevCards.all`), `cards-vp.scala` (66 cards, `VPCards.all`, plus `StartupDecks.presets`),
`rules-ext-startup.scala` (generic mechanics, extra state, `Startup` base class). Registered in `CardImpls.all` (abilities.scala).
Behaviour ported from `netrunner-src/src/clj/game/cards/*.clj`; the Startup format (SG + elev + vp) is now fully implemented.

## Coverage

### Elevation — 82/82 implemented (approximations marked *)
Runner: Ryō "Phoenix" Ōno, Topan, Charm Offensive, Scrounge, Shred, Bling*, Gourmand, Hantu, Rising Tide, Cacophony, Rent Rioters,
Barry "Baz" Wong, MuslihaT, Clean Getaway, Lie Low, Maintenance Access, Transfer of Wealth, Detente, Maglectric Rapid, Sang Kancil,
Fransofia Ward, Open Market, Dewi Subrotoputri, Magdalene Keino-Chemutai, Illumination, Ritual, GAMEDRAGON Pro, Madani, Azimat,
Chromatophores, Devadatta Drone, Principia, "Knickknack" O'Brian, Side Hustle.
Corp: LEO Construction, Poétrï Luxury Brands, Aggressive Trendsetting, Project Ingatan, Humanoid Resources, Otto Campaign, Bumi 1.0,
Scatter Field, Nanomanagement, Top-Down Solutions, Mercia B4LL4RD, AU Co., PT Untaian, Proprionegation, Sericulture Expansion, Byte!,
Phật Gioan Baotixita, Empiricist, Mycoweb, Semak-samun, Peer Review, Mitra Aman, Nebula Talent Management, Synapse Global,
Embedded Reporting, Next Big Thing, Idiosyncresis, Public Access Plaza, Doomscroll, N-Pot, Bigger Picture, IP Enforcement*, Touch-ups,
BANGUN, The Zwicky Group, Greenmail, Off the Books, Anthill Excavation Contract, Plutus*, Biawak, Kessleroid, Syailendra,
Key Performance Indicators, Measured Response, Flyswatter, Lamplighter, Petty Cash, Mahkota Langit Grid.

### Vantage Point — 66/66 implemented (approximations marked *)
Runner: Chain Reaction, Take a Dive, The Tungsten Tailor, Corsair, Lampades, Hackerspace, Nurse Hạnh, Stick and Poke, Vic,
Kompromat, Sell Out, Tailgate, Borrowed Goods, Rotary, Baker, Underdome Irregulars, Hiram Svensson, Aircheck, Beta Build,
Methuselah, Touchstone, Read-Write Share, Sipa, Stowaway, Word on the Street.
Corp: Méliès City Luxury Line, Synchrocyclotron, Ansel 2.0, Reverb, Sleipnir, Vertigo, Caveat Emptor, realloc(), Retirement Plan,
Perfect Recall, Méliès U, Lotus Haze, Esca, ezaM, Knowledge Seeker, Lionsmane, Vicsek, Cultivate, Unleash, The Red Room,
Editorial Division, Witch Hunt, Magistrate Revontulet, Nihilo Agent, Grubber, Lethe, Paywall, Flood the Market, Scapegoat,
Hype Machine, Sacrifice Zone Expansion, Luana Campos, Event Horizon, Flywheel, Tocsin, Myōshu, Reanimation Protocol*, Vulture Fund,
Flagship, Shackleton Grid, Let Them Dream.

Nothing is left unimplemented.

## Simplifications / approximations
* (Fixed in the credits pass, see `STATUS-fix-credits.md`: stealth-only costs of Corsair, Lampades, Baker; Aircheck's "cannot lose or
  spend credits from your credit pool"; Open Market, Mahkota Langit Grid and Azimat credits spent as credit sources; Hackerspace's
  discount only for resources installed onto it.)
* Plutus: if neither additional cost can be paid the asset is derezzed again after rezzing.
* Hackerspace: hosting on it is offered in the install menu (`canHost` hook).
* Bling: "install without spending credits" = install cost 0 at the time of install. Hosted (not installed) cards (Bling, Madani,
  Detente, Read-Write Share) live in a new `StashZone(host)` which the UI does not render yet.
* (Fixed in the fix-misc pass, see `STATUS-fix-misc.md`: Stick and Poke grants a real first subroutine for the encounter; Biawak's
  forfeit is optional (pay the 10 credits instead) and not asked when rezzed ignoring costs; Detente's access uses the normal
  access machinery; Word on the Street is added to the score area *before* the agenda is scored (its -1 counts for that win check);
  Myōshu looks at every agenda scored this turn; Petty Cash = no action (basic action or click ability) finished yet; The Zwicky
  Group sees the real source of a credit gain (an agenda's paid or "when scored" ability, a resolving operation); Hype Machine
  counts agendas scored / stolen, not points; Méliès U's hidden face is not a visible counter and starts random.)
* Public Access Plaza tags when trashed by the Runner even if unrezzed (as jinteki).
* IP Enforcement follows the printed text (remove X tags only; the Clojure version also charges X credits).
* Perfect Recall's protection is stored per run and checked through the hook `protectsFromAccess`; it stops if Perfect Recall leaves play.
* Méliès U: the secret choice is a journal action (like psi bids, hidden by the UI only); it is stored in the identity's memo (not
  drawn on the board), and the face before the first choice is random (`MeliesSetupAction`).
* Reanimation Protocol: if the Corp cannot afford (install + rez - 10), the ice is only installed.
* Dividends use `lastScoredAdvancement` (recorded before the agenda leaves play); "excess" = counters - current requirement.
* Chromatophores keeps its host in `chromatophoreHosts` (the engine resets host links before `onTrash`).

## New generic mechanics (rules-ext-startup.scala)
* Events: `ActionPhaseEnds(f)`, `ArchivesFlipped(n)`, `BadPubGained(n)`, `TagsRemoved(n)`, `CreditsGained(f, n, source)`,
  `CreditsSpentFromCard(card, n)`, `IceBypassed(ice)`, `Forfeited(card)`, `RunRedirected(server)`, `DiscardedToHandSize(f, cards)`.
* `StartupState` (mixed into `Game`): bonus clicks for the next turn, last scored advancement / installed turn, last stolen-from
  server, last trashed-from zone, scoring agenda, hosted bad publicity, run-scoped protected titles, played-from zones, Chromatophores
  hosts, linked cards; helpers `threatLevel`/`threat(n)`, `zoneServer`, `activeRun`, `stashed`, `stealthCredits`, `stealCostOf`,
  `accessProtected`, `extraAbilityOptions`, `uninstall`, `insertAfterRunCleanup`, `pumpDurationFor`.
* Zone `StashZone(host)` for hosted-but-not-installed cards.
* Effects (self-performing queue items): `PlayCardEffect` (play from any zone without a click, optional RFG), `ForfeitEffect`,
  `SabotageEffect`, `TrashRandomFromHandEffect`, `RedirectRunEffect` (change the attacked server / move the Runner), `JackOutEffect`,
  `ResolveSubEffect` / `ChooseSubEffect`, `ArrangeTopEffect`, `ChooseServerForAction`, `ChooseNumberEffect`, `DiscountRezEffect`,
  `RefillCreditsEffect` (recurring credits), `ExtraSubtypesEffect`, `SetCounterEffect`, `BonusClicksEffect`, `MoveToRootEffect`,
  `SwapIceWithEffect` (swap installed ice with ice from HQ/Archives), `AccessRandomHQEffect`.
* User actions: `UseCorpAbilityAction` (Corp uses an ability on a Runner card), `UseInactiveAbilityAction` (Expend / flashback from
  HQ or Archives), `ServerPickAction`, `SubChoiceAction`, `ArrangePickAction`, `NumberPickAction`.
* `Startup(code)` base class: `performCard`/`chosenCard` for card specifics, generic purposes (`InstallLoopPurpose` "install up to N
  one at a time" with discounts, `ForfeitPurpose`, `TrashFromHQPurpose`, `SabotagePurpose`, `PlayPurpose`, `RezDiscountPurpose`,
  `DerezPurpose`, `ToDeckPurpose`, `RfgPurpose`, `SubChoicePurpose`, `StashPurpose`, `UninstallToHandPurpose`, `LookPurpose`,
  `TopOrBottomPurpose`, ...), helpers `dividends`, `doubleCanPlay`/`doubleCost` (double operations, Synchrocyclotron), `bioroidBreak`,
  identity `flip`, `runnerCanInstall`.
* New `CardImpl` hooks (abilities.scala, all with defaults): `corpAbilities`, `inactiveAbilities`, `canHost`, `canBreakWith`,
  `endRunPrevention`, `blocksSuccessfulRun`, `accessFilter`, `extraStealCost`, `protectsFromAccess`, `pumpDuration`,
  `untrashableByRunner`.

## Shared-file edits (for merging)
* `abilities.scala`: 11 new hook methods on `CardImpl` (defaults); `CardImpls.all` includes `ElevCards.all ++ VPCards.all`.
* `game.scala`: `Game` mixes in `StartupState`; `rezCost`/`installCost`/`playCost` also consult the card's own modifier while the card
  is not active (bug fix: Biawak, Reverb, Hype Machine, Principia, Tailgate, and SG's Carmen never got their own discount).
* `rules-turn.scala`: bonus clicks applied at `StartTurnAction`; `trigger(ActionPhaseEnds)` in `EndTurnClickAction`;
  `RemoveTagAction` triggers `TagsRemoved`; `DiscardCardsAction` triggers `DiscardedToHandSize`; `scoreAgenda` records
  `lastScoredAdvancement`/`lastScoredInstalledTurn`/`scoringAgenda`; `stealAgenda` records `lastStolenFrom`; `InstallWhereAction` offers
  hosts from `canHost`; **bug fix**: `ScoreAction`, `TrashResourceAction`, `RezAction` now push `ActionPhaseAction`/reask *before* the
  score/trash/rez effects (previously on-score, on-rez and trash triggers were queued behind the action-phase prompt and never resolved).
* `rules-effects.scala`: `GainCreditsEffect` triggers `CreditsGained`; `RemoveTagsEffect` → `TagsRemoved`; `BadPubEffect` →
  `BadPubGained`; `EndRunEffect` consults `endRunPrevention`; `CheckWinAction` clears `scoringAgenda`; `trashCard` records
  `lastTrashedFrom`; `payCredits` triggers `CreditsSpentFromCard`.
* `rules-run.scala`: break options honour `canBreakWith`; pumps use `pumpDurationFor`; `SuccessfulRunAction` honours
  `blocksSuccessfulRun`; `BypassEffect` triggers `IceBypassed`.
* `rules-access.scala`: Archives access triggers `ArchivesFlipped`; `AccessNextAction` applies `accessFilter`; steal/trash decisions use
  `stealCostOf` and `accessProtected`.
* `rules-abilities.scala`: `abilityOptions` appends `extraAbilityOptions` (Corp abilities on Runner cards, inactive-zone abilities).
* `decks.scala`: `presets ++ StartupDecks.presets` (8 Startup decks: 4 Runner, 4 Corp, all validated).

## Testing
* `sbt -batch compile` passes.
* Harness (scratchpad `vphost`, `VPHost`): 200 + 200 bot-vs-bot games with the Startup decks and random same-faction identity swaps,
  every offered action and every chosen action round-tripped through `Serialize` (ASCII check): 0 exceptions, 0 empty asks,
  0 validation errors; all 148 cards appear in the logs.
* Scripted checks (`ScriptTest`, 40 assertions): Lotus Haze / Proprionegation on-score counters, dividends, Nurse Hạnh, Flagship,
  Proprionegation redirect, Shred prevention, Word on the Street, self cost modifiers, Detente corp ability + random access, Tocsin
  expend, Petty Cash flashback, Bling play from host, Madani install, Méliès U secret choice/flip/recursion, Chain Reaction (Kessleroid
  untrashable), IP Enforcement, Biawak forfeit, Caveat Emptor bonus clicks, Semak-samun fracter-only breaking, Lethe full-break tag,
  Sipa swap, Aircheck follow-up run, GAMEDRAGON Pro pump duration. All pass.
* Not tested in the browser.
