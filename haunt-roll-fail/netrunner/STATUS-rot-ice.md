# Netrunner plugin — rotated (FFG-era) ice

Implementations of every card of type `ICE` that had none: `Cards.all.%(_.kind == "ICE")` minus the codes
already in `CardImpls.all` at commit 07e9ddf. That is 211 cards, ported from jinteki.net
`src/clj/game/cards/ice.clj`, with `test/clj/game/cards/ice_test.clj` used as the reference for behaviour.

Files:

- `cards-rot-ice.scala`: the `RI` base class (shared helpers: bioroid breaks, trace and psi subroutines,
  "end the run unless ..." subroutines, power counter abilities, morph/advancement helpers, purposes 98/99),
  the card actions `RotSubAction` / `RotPurpose` / `RotServerAction`, and the registry
  `object RotIceCards { val all }`.
- `cards-rot-ice-1.scala` (`RotIceCards1`, titles A–L) and `cards-rot-ice-2.scala` (`RotIceCards2`, titles
  M–Z plus Bullfrog).
- `rules-ext-rot-ice.scala`: `trait RotIceRules` (the new generic mechanics, listed below).
- `decks-rot-ice.scala`: `object RotIceDecks { val presets }`, eight Corp preset decks (two per faction)
  that hold every eternal-legal rotated ice at least once.

## Coverage (211/211 implemented)

Haas-Bioroid (50): Architect, Brainstorm, Eli 2.0, Enforcer 1.0, Executive Functioning, Fairchild, Fairchild 1.0,
Fairchild 2.0, Fairchild 3.0, Fenris, Gatekeeper, Gyri Labyrinth, Heimdall 1.0, Heimdall 2.0, Holmegaard,
Hourglass, Howler, Hudson 1.0, Ichi 1.0, Ichi 2.0, IQ, Janus 1.0, Kamali 1.0, Lab Dog, Loki, Markus 1.0,
Meridian, Minelayer, Najja 1.0, Nerine 2.0, NEXT Bronze, NEXT Diamond, NEXT Gold, NEXT Opal, NEXT Sapphire,
NEXT Silver, Nightdancer, Sagittarius, Seidr Adaptive Barrier, Sherlock 1.0, Sherlock 2.0, Tapestry, Turing,
Vikram 1.0 (partial), Viktor 1.0, Viktor 2.0, Viper, Wotan, Zed 1.0, Zed 2.0.

Jinteki (49): Aiki, Aimor, Anansi, Ashigaru, Bloom, Bullfrog, Cell Portal, Chetana, Chiyashi, Chrysalis, Chum,
Clairvoyant Monitor, Cortex Lock, Crick, Data Mine, DNA Tracker, Envelope, Gemini, Harvester, Himitsu-Bako,
Inazuma, It's a Trap!, Kakugo, Kitsune, Komainu, Lockdown, Mamba, Marker, Metamorph, Mganga, Mind Game
(partial), Mirāju, Mlinzi, Neural Katana, Otoroshi, Pup, Sadaka, Sand Storm, Sensei, Shinobi, Shiro, Snowflake,
Susanoo-no-Mikoto, Thimblerig, Tsurugi, Upayoga, Wall of Thorns, Whirlpool, Yagura.

NBN (39): Archangel, Authenticator, Bandwidth, Data Hound, Data Loop, Data Raven, Data Ward, Endless EULA, Flare,
Free Lunch, Gutenberg, Herald, Hydra, Information Overload, Interrupt 0 (approximated), IP Block, Jua (partial),
Little Engine, Matrix Analyzer, Muckraker, News Hound, Pachinko, Peeping Tom, Resistor, RSVP (partial), Sandman,
Slot Machine, Snoop, Special Offer, SYNC BRE, Thoth, TL;DR, TMI, Troll, Turnpike, Universal Connectivity Fee,
Uroboros, Virgo, Waiver.

Neutral (28): Assassin, Bastion, Chimera, Cobra, Conundrum, Datapike, Dracō, Excalibur, Galahad, Grim, Guard,
Hunter, Lancelot, Machicolation A, Machicolation B, Macrophage, Merlin, Mother Goddess, Owl, Paper Wall,
Quandary, Quicksand, Rainbow, Self-Adapting Code Wall, Tribunal, Vanilla, Wall of Static, Weir.

Weyland Consortium (45): Asteroid Belt, Bailiff, Battlement, Blockchain, Bloodletter, Border Control, Builder,
Bulwark, Burke Bugs, Caduceus, Changeling, Checkpoint, Curtain Wall, Errand Boy, Fire Wall, Formicary, Hadrian's
Wall, Hailstorm, Hive, Ireress, Lycan, Masvingo, Mausolus, Meru Mati, Nebula, Negotiator, Oduduwa, Orion, Red
Tape, Salvage, Sapper, Searchlight, Shadow, Spiderweb, Surveyor, Swarm, Taurus, Tithonium (partial), Tour Guide,
Tyrant, Veritas, Watchtower, Wendigo (partial), Woodcutter, Wormhole.

## Simplifications

- **Vikram 1.0, Wendigo**: "the Runner cannot use programs / a chosen program" is only logged. jinteki does
  not implement it either.
- **Jua**: the encounter ability "the Runner cannot install cards this turn" is only logged. No global
  install-restriction hook exists, and adding one would mean editing the shared install paths.
- **Interrupt 0**: approximated. While the run lasts, an icebreaker may break only if the Runner has 1[c] more
  than its cheapest break ability costs, and the extra 1[c] is taken right after each break (through
  `breakAllowed` and a `SubsBroken` trigger). The effect stops early if Interrupt 0 is derezzed or trashed
  during the run.
- **Tithonium**: the alternative rez cost (forfeit an agenda) is not offered. Only the normal rez cost is used.
- **RSVP**: only break abilities that cost credits are blocked. Pumps and other credit spending still work.
- **Mind Game**: the extra jack-out cost (add an installed card to the bottom of the stack) only applies to
  the jack-out offered right after the redirect.
- **Excalibur, Uroboros, Lockdown, Gyri Labyrinth**: the turn restrictions ("no other run", "no draws",
  hand size) are enforced by the hooks of *rezzed* rotated ice (`RI.allowsRun` / `RI.drawBonus` /
  `handSize`). If no rotated ice is rezzed any more, the restriction lifts.
- **Grail ice** (Galahad, Lancelot, Merlin, Excalibur): each revealed grail adds its printed subroutine from a
  fixed table for the encounter.
- **Data Hound, Waiver**: the Runner's trace boost is measured as the credits they spent during the trace.
- **Archangel, Chrysalis, Herald, Sapper** (encounter on access): the ice is temporarily placed rezzed as the
  outermost ice of the attacked server for the forced encounter, then put back where it came from.
- **Formicary**: when the Runner approaches a server, the Corp is offered "rez Formicary and move it"
  (rotIceTriggers). It moves to the innermost position of the attacked server and the Runner encounters it.

## New generic mechanics (`rules-ext-rot-ice.scala`, `trait RotIceRules`)

- `MoveIceToIndexEffect(card, index)`: move installed ice to a given position of its server and keep the
  Runner's position.
- `SwapRootEffect(a, b)`: swap two installed non-ice Corp cards (Metamorph).
- `PhantomEncounterEffect` / `PhantomRestoreAction`: force an encounter with an uninstalled ice that is being
  accessed.
- `RunFlagEffect("cannot-jack-out", on)`: the Runner cannot jack out (Whirlpool, Inazuma, Susanoo).
- `AccessBonusTotalEffect(n)`: ±n cards accessed this run (SYNC BRE).
- `BreakAllSubsEffect(ice)`: Endless EULA's runner ability.
- `RememberCreditsEffect` / `TraceKickerEffect(min, effects)`: trace boost measurement, and the "again if
  trace strength ≥ 5" kicker of the constellation ice.
- `ResolveSubOnIceEffect(card, subtype, allowSelf)`: resolve a subroutine on another rezzed ice (Orion,
  Wormhole, Upayoga).
- `EndRunIfTaggedEffect`.
- `rotIceTriggers(e)`: game-level triggers for Susanoo ("cannot jack out until the next encounter") and
  Formicary, which can fire while unrezzed.
- Helpers `runKeyString`, `rezzedAssets`, `nextIceCount`, `runServer`, `placeAt`.

## Shared-file edits (each one added term)

- `abilities.scala` line 128: `++ RotIceCards.all` in `CardImpls.all` (registry).
- `game.scala` line 339: `with RotIceRules` in the `Game` class header (mechanics).
- `game.scala` line 682: `++ rotIceTriggers(e)` in `triggerEffects` (game-level triggers, appended after
  `su21Triggers`).
- `game.scala` line 753: `orElse performRotIce` at the end of the `performInternal` chain.
- `decks.scala` line 252: `++ RotIceDecks.presets` appended to `Decks.presets` (harness preset decks).

No other shared files were edited.

## Verification

- `sbt -batch compile` (Scala.js project): green.
- Scripted JVM test (`RotIceScript`, harness in the session scratchpad `rot-ice-host/`). Each of the 211 ice
  is installed rezzed on HQ and run three times: subroutines fire; 3 advancement counters plus 1 tag; the
  bot breaks. There is also an access test for the "encounter on access" ice, and a two-ice test for the run
  modifiers (Marker, TL;DR, Chum, Sensei, Inazuma, Red Tape, Interrupt 0) with Hunter as the inner ice.
  Result: 633 encounters, 743 subroutines fired, 0 failures. Every offered and chosen action was
  round-tripped through the serializer.
- Bot-vs-bot harness (`MergeHost`): 2200 games (seeds 777, 4242, 99) with the eight rot-ice Corp decks against
  the su21/ms, ph/tai, elev and rwr/df/ur Runner presets. 0 exceptions, 0 empty asks, 0 serialization
  mismatches. 205/211 rotated ice were encountered in play and 202 had subroutines fire. The ones never
  encountered in play were Anansi, Orion, Thoth and Wotan (expensive, all covered by the scripted test) and
  Machicolation A/B. Machicolation A/B are not eternal-legal, so they appear in no preset.
- All eight preset decks pass `Decks.validate` and contain only eternal-legal cards. The faction System
  Gateway agendas have a deck limit of 1 in the card data, so each deck runs one copy plus Orbital
  Superiority.
