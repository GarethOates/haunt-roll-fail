# Netrunner plugin — rotated / FFG-era programs

Card implementations for every card of type `Program` that had no implementation after the merge of the eleven
card sets: 164 rotated / FFG-era programs (`Cards.all.%(_.kind == "Program")` minus the codes already in
`CardImpls.all`), ported from jinteki.net `src/clj/game/cards/programs.clj` and checked against
`test/clj/game/cards/programs_test.clj`. After this change all 255 programs of the card database have an
implementation object.

Files:

* `cards-rot-programs.scala` — `object RotProgramCards { val all }` (registration), the card specific actions and
  purposes, and the shared card shapes (suites) used by several cards.
* `cards-rot-programs-1.scala` (`RotProgramCards1`: Anarch, Apex, Adam), `cards-rot-programs-2.scala`
  (`RotProgramCards2`: Criminal, Neutral, Sunny Lebeau), `cards-rot-programs-3.scala` (`RotProgramCards3`: Shaper).
* `rules-ext-rot-programs.scala` — `trait RotProgramRules` (generic mechanics, `performRotPrograms`,
  `resolveRotPurpose`) and the base class `Rot(code)` (extends `NC`) with helpers for the cards.
* `decks-rot-programs.scala` — `object RotProgramDecks { val presets }`: 10 Runner preset decks ("... programs N
  (rot)", System Gateway identities) packed with the programs; every eternal-legal program of this list is in at least
  one deck, all decks validate.

## Coverage

164/164 implemented. By faction: Anarch 48, Criminal 43, Shaper 56, Neutral 8, Apex 4, Adam 2, Sunny Lebeau 3.

Anarch: Djinn, Wyrm, Yog.0, Nerve Agent, Disrupter, Pawn, Rook, Bishop, Scheherazade, Knight, Keyhole, D4v1d, BlacKat,
Origami, Cerberus "Cuj.0" H3, Incubator, Ixodidae, Eater, Gravedigger, Hivemind, Progenitor, Faust, Trope, Surfer, Diwan,
Aghora, Nfr, Paperclip, Black Orchestra, MKUltra, Sunya, Berserker, Persephone, God of War, Darwin, Hemorrhage, Morning
Star, Yusuf, Plague, eXer, Trypano, Musaazi, Cradle, Datasucker, Force of Nature, Lamprey, Medium, Parasite.

Criminal: Ninja, Snitch, Crescentus, Gorman Drip v1, Copycat, Leviathan, Expert Schedule Analyzer, Garrote,
Savoir-faire, Alias, Breach, Bug, Gingerbread, Grappling Hook, Passport, Switchblade, Cerberus "Rex" H2, Au Revoir, Spike,
Crowbar, Shiv, Mongoose, Vamadeva, Golden, Peregrine, Saker, Tapwrm, Tracker, Flashbang, Aumakua, Lustig, Mammon, Aurora,
Peacock, Pheromones, Puffer, Wari, Amina, Bankroll, Tycoon, Demara, Faerie, Cache.

Shaper: Net Shield, ZU.13 Key Master, Snowball, Creeper, Deep Thought, Cloak, Dagger, Chakana, Cyber-Cypher, Sahasrara,
Inti, False Echo, Torch, Sharpshooter, Alpha, Omega, Paintbrush, Leprechaun, LLDS Energy Regulator, Refractor,
Collective Consciousness, Sage, Study Guide, Analog Dreamers, Hyperdriver, DaVinci, Panchatantra, Sadyojata, Brahman,
Ankusa, Dai V, Houdini, Blackstone, Equivocation, Misdirection, Fawkes, Customized Secretary, Inversificator,
Mass-Driver, Maven, Na'Not'K, Adept, Savant, Dhegdheer, Magnum Opus, Upya, Takobi, Nyashia, Laamb, Engolo, Ika, Kyuban,
Battering Ram, Deus X, Pipeline, Cerberus "Lady" H1.

Neutral / mini-factions: Overmind, Baba Yaga, Surveillance Network Key, Surveillance Network Key 2, Sneakdoor Prime A,
Sneakdoor Prime B, RNG Key, Crypsis; Endless Hunger, Harbinger, Reaver, Consume (Apex); Multithreader, Algernon (Adam);
GS Striker M1, GS Shrike M2, GS Sherman M3 (Sunny Lebeau).

Shared card shapes (`cards-rot-programs.scala`): `Conspiracy` (Paperclip, Black Orchestra, MKUltra: install from the heap
when ice of the type is encountered, pump-and-break through a "break" counter), `Deva` (Aghora, Sadyojata, Vamadeva),
`Cerberus` (Cuj.0, Rex, Lady), `BreakAndEnter` (Spike, Crowbar, Shiv), `GlobalSec` (GS M1/M2/M3, cloud), `CentralOnly`
(Alias, Breach, Passport), `Bird` (Golden, Peregrine, Saker), `Fraud` (Demara, Lustig), `VirusBreaker` (Yusuf, Musaazi),
`Greek` (Adept, Sage, Savant: strength = unused MU), `Wrestler` (Laamb, Engolo: pay 2 to give the encountered ice a type).

## Simplifications / approximations

* (Fixed in the credits pass, `STATUS-fix-credits.md`: stealth costs of Dagger, Refractor, Switchblade, Dai V, Houdini, Blackstone,
  BlacKat and Fawkes are `StealthCreditCost`s; Cloak, Multithreader and Sahasrara credits pay only for using icebreakers / using
  programs / installing programs; Pheromones' credits pay for anything during HQ runs as printed.) (Fixed in the final rotated pass, see `STATUS-fix-final-rot.md`: Sahasrara's credits do
  not pay for a program whose memory would need Sahasrara to be trashed, and it cannot be trashed for that install.)
* (Fixed in the final rotated pass, see `STATUS-fix-final-rot.md`: Hivemind's counters count and pay for every virus program,
  `VirusCost` included (paid abilities ask how many come from Hivemind); Yusuf / Musaazi's pump is "any virus counter" too.)
  Remaining: a `VirusCost` of a break / pump ability spends the program's own counters first, then Hivemind's, without a choice.
* Harbinger: when trashed from play it is installed facedown from the heap (facedown programs use no MU; Blacklist does not
  stop it: jinteki releases the heap lock for it).
* (Fixed in the fix-misc pass: Tracker prevents the first subroutine that would resolve during its run; Mass-Driver's 3
  subroutines do not resolve but are not broken; Endless Hunger breaks "End the run" subroutines only.)
* Copycat is an optional "after you pass a rezzed piece of ice" trigger, as printed (jinteki: a paid ability).
* (Fixed in the final rotated pass, see `STATUS-fix-final-rot.md`: Faust's grip card is trashed as the break's cost, before the break; Surveillance Network Key 1 / 2 reveal the
  first card drawn when the Corp spends [click] to draw: basic draw, click abilities, operations played with the basic action.)
* Wari / Paintbrush offer Barrier, Code Gate and Sentry; Panchatantra offers every other ice subtype of the card pool (as printed).
* Aumakua gains its counter whenever a breach ends without stealing or trashing (fix-misc pass: `BreachEnded`).
* (Fixed in the final rotated pass, see `STATUS-fix-final-rot.md`: Progenitor keeps 1 virus counter on the hosted program when counters are purged, if it had any.)
* Customized Secretary hosts the revealed programs as uninstalled hosted cards (the UI shows them since the QA pass).
* Bug reveals randomly chosen drawn cards (like jinteki; the Runner cannot tell the drawn cards apart).

## New generic mechanics (`rules-ext-rot-programs.scala`)

* Event `Exposed(card)`; effect `ExposeEffect(card)` (marks the card seen, register `"exposed"`) — Snitch, Wari, Aumakua.
* `InstallFacedownEffect(card)` / `ForgetFacedownEffect(card)` and the `rotFacedown` list — facedown installs from the heap
  (Harbinger).
* `PreventSubsEffect(ice, n)` — the first n unbroken subroutines do not resolve this encounter (Tracker, Mass-Driver).
* `ChangeRunServerEffect(server)` (Sneakdoor Prime A/B), `SetRunPositionEffect(n)` (Surfer).
* `PayCreditsEffect(f, n)` — pay credits (hosted run credits first) as part of an effect (X costs, optional costs).
* `ReinstallEffect(card)` — put a just trashed Runner card back into the rig (no longer used: LLDS uses the trash prevention
  window of `rules-ext-prevention.scala`).
* `SpendVirusEffect(card, n, any)` (+ `SpendVirusPurposeAction`, `SpendVirusPurpose`) — virus counter costs paid from the
  card and Hivemind, or from any installed card; `virusCounters(c)`, `anyVirusCounters`.
* Purposes `HostIceSelfPurpose(card)` (Caissa / Ika re-hosting) and `MoveVirusPurpose(from, to, n)` (Hivemind).
* `iceSubtypes` (all ice subtypes of the card pool).
* Base class `Rot(code)` with helpers (`virus`, `encounteredIce`, `broke`, `startOfTurn`, `trashOnPurge`, `stealth`,
  `cloudMemory`, `hostIceAbility`, `hostedMemory`, ...), and `RotProgramCards.subtypePerform` (temporary ice subtypes).

## Shared-file edits (all additive; for the merge)

| File | Line | Edit | Why |
|---|---|---|---|
| `abilities.scala` | 128 | `++ RotProgramCards.all` appended to `CardImpls.all` | registration |
| `game.scala` | 339 | `with RotProgramRules` appended to the `Game` mixins | new mechanics trait |
| `game.scala` | 753 | `orElse performRotPrograms` appended to the `performInternal` chain | new effects |
| `rules-effects.scala` | 765 | `.orElse(resolveRotPurpose(f, l, p))` appended after `resolveRWRPurpose(f, l, p)` in `resolvePurpose` | non-card purposes (`HostIceSelfPurpose`, `MoveVirusPurpose`, `SpendVirusPurpose`) |
| `decks.scala` | 252 | `++ RotProgramDecks.presets` appended to `Decks.presets` | harness / preset decks |

No other shared file was touched; nothing was renamed, reordered or reformatted.

## Tested

* `sbt -batch compile` passes.
* JVM harness (scratchpad `rot-programs-host`, `RotHost`: copy of the merge harness pointed at this worktree; every offered
  and chosen action round-tripped through `Serialize`; Runner restricted to the rot presets, plus "focus" imported decks
  with 3 copies of 5 rot programs rotating through all 164 cards, plus an imported deck with the 7 programs that are not
  eternal-legal (Devas, Surveillance Network Keys, Sneakdoor Primes); Corp decks from every set; a `TraceGame` subclass
  records which card hooks actually produced effects):
  1200 games (seed 2026) + 700 games (seed 99) + 500 games (seed 4242): 2400 games, 0 exceptions, 0 empty asks, 0
  serialization mismatches, 0 over-long games. 153/164 cards fired in the 1200-game run (triggers with effects,
  abilities/breaks/pumps used, card actions/choices performed, non-zero static modifiers); the union with the 700-game run
  leaves 6 (Copycat, LLDS Energy Regulator, Switchblade, Dai V, Baba Yaga, Fawkes), all covered by the scripted checks.
* Scripted checks (`RotScriptTest`, 50 checks, all pass): breaking with Torch, Alpha, Omega, Leviathan, Crowbar,
  Passport, Morning Star, Nfr (+power counter), Ankusa (+ice to HQ), GS Sherman M3, Cerberus Lady, Sunya, Garrote,
  Golden, Alias, Switchblade and Dai V (stealth), Sharpshooter, D4v1d, Yusuf (Cache's counter), Musaazi, Engolo (gives
  Code Gate), Amina; Black Orchestra pump-and-break; MKUltra installed from the heap and breaking; Fawkes X pump; Copycat
  redirect; Disrupter (trace base 0); LLDS reinstall; Hyperdriver; Misdirection; Leprechaun / Baba Yaga hosting (no MU);
  Datasucker spending Hivemind's counter; Surfer swap; Mass-Driver preventing the next ice's subroutines.

## Pre-existing issue found (not fixed: outside this change)

* Plutus (35073, `cards-elev.scala`): the Corp may rez it although its additional cost (forfeit an agenda or trash 3 cards
  from HQ) cannot be paid; it is then derezzed immediately and the rez option is offered again, so the Random bot can loop
  forever ("game too long" in 4 of ~1400 harness games before the Zwicky preset was excluded with `HRF_CORP_EXCLUDE=Zwicky`).
  Suggested fix in Plutus: `override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda) || g.hand(Corp).num >= 3`.

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
