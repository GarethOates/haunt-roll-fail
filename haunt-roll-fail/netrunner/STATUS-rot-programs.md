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

* Stealth credits: abilities that need stealth credits are offered only while enough credits sit on installed Stealth
  cards (Cloak, ...); the payment itself is not restricted (hosted run credits are spent first), as elsewhere in the plugin.
* Recurring credits: Cloak, Multithreader and Pheromones (HQ runs only) are run credits usable for any payment during a
  run; Multithreader and Sahasrara also have a "take 1 hosted credit" ability (Multithreader outside runs, Sahasrara when a
  program is in the grip) instead of purpose-restricted spending.
* Hivemind: its counters are counted and spent by the virus programs of this list (`virusCounters`, `SpendVirusEffect`:
  Nerve Agent, Medium, Darwin, Datasucker, Gravedigger, Hemorrhage, Cache, God of War, Crypsis, Trypano, Parasite,
  Consume); virus cards of other sets (Leech, ...) do not see them.
* Yusuf / Musaazi "any virus counter": the break spends a counter of any installed card (the Runner chooses when several
  cards have counters); the pump is a normal `Pump` while the breaker hosts a counter, otherwise an ability that spends a
  counter of another card.
* Harbinger: when trashed from play it is installed facedown from the heap; the facedown card still counts its 1 MU.
* LLDS Energy Regulator: there is no trash-prevention window; when the Corp trashes installed hardware, LLDS offers to pay 3
  or trash itself and the hardware is reinstalled (its "when trashed" effects have already been queued).
* Net Shield: usable once per turn (instead of "only on the first net damage of the turn").
* Tracker: the prevented subroutine is the first unbroken subroutine of the first rezzed ice with unbroken subroutines
  encountered during the Tracker run (chosen at encounter time, so it counts as broken); Mass-Driver likewise marks the
  first 3 subroutines of the next encountered ice as broken.
* Copycat is an optional "after you pass a rezzed piece of ice" trigger rather than a paid ability.
* Sneakdoor Prime A/B change the attacked server when the Runner approaches the server (instead of on "pre-successful-run").
* Endless Hunger breaks any subroutine of ice that has an "end the run" subroutine (jinteki does not enforce the restriction
  at all).
* Faust: the grip card for the break is chosen right after the break.
* Surveillance Network Key (1 and 2): reveal the cards the Corp draws during its action phase.
* Wari / Paintbrush offer Barrier, Code Gate and Sentry; Panchatantra offers every other ice subtype of the card pool.
* Aumakua gains its counter when a run with an access ends without stealing or trashing (approximates "finish breaching").
* Progenitor's purge protection remembers the hosted program while it has virus counters; counters spent through a cost are
  not seen, so a program that spent all its counters before a purge still keeps 1.
* Customized Secretary hosts the revealed programs as uninstalled hosted cards (like the other `uninstalled` hosts, they are
  not rendered by the UI — known gap of `StashZone`-like hosting).
* Bug reveals randomly chosen drawn cards (like jinteki).

## New generic mechanics (`rules-ext-rot-programs.scala`)

* Event `Exposed(card)`; effect `ExposeEffect(card)` (marks the card seen, register `"exposed"`) — Snitch, Wari, Aumakua.
* `InstallFacedownEffect(card)` / `ForgetFacedownEffect(card)` and the `rotFacedown` list — facedown installs from the heap
  (Harbinger).
* `PreventSubsEffect(ice, n)` — the first n unbroken subroutines do not resolve this encounter (Tracker, Mass-Driver).
* `ChangeRunServerEffect(server)` (Sneakdoor Prime A/B), `SetRunPositionEffect(n)` (Surfer).
* `PayCreditsEffect(f, n)` — pay credits (hosted run credits first) as part of an effect (X costs, optional costs).
* `ReinstallEffect(card)` — put a just trashed Runner card back into the rig (LLDS).
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
