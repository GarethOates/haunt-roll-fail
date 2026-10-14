# Netrunner plugin — rotated / FFG-era Resources

Implementations for every card of type `Resource` that had none (`Cards.all.%(_.kind == "Resource")` minus the
codes already in `CardImpls.all`): 164 cards, ported from jinteki.net `src/clj/game/cards/resources.clj` and
checked against `test/clj/game/cards/resources_test.clj`. After this port every Resource in the card database has an
implementation object (229/229).

Files:

* `cards-rot-resources-1.scala` — `RotResourceCards1` (titles A–L), card-specific actions/purposes at the top.
* `cards-rot-resources-2.scala` — `RotResourceCards2` (titles M–Z) and `object RotResourceCards { val all }`.
* `rules-ext-rot-resources.scala` — `trait RotResourceRules` (generic mechanics, `performRotResources`,
  `rotResourceTriggers`), the `RR(code)` base class used by all 164 cards and the shared `RotPurpose`s.
* `decks-rot-resources.scala` — `RotResourceDecks.presets`: 11 Runner preset decks that together contain every one of
  the 164 cards (two copies each, fewer if the deck limit is lower).

## Coverage (164/164 implemented)

By faction: Neutral 51, Criminal 40, Anarch 31, Shaper 31, Adam 6, Apex 3, Sunny Lebeau 2.

Fully automated (printed abilities resolved by the engine), apart from the notes in the next section:
Aaron Marron, Access to Globalsec, Activist Support, Adjusted Chronotype, Akshara Sareen, Algo Trading, All-nighter,
Always Be Running (the "first click must run / play a run event" restriction is enforced (see `STATUS-fix-restrictions.md`)), Angel
Arena, Armitage Codebusting, Artist Colony, Assimilator, Bank Job, Beach Party, Beth Kilrain-Chang, Bhagat,
Bio-Modeled Network, Biometric Spoofing, Blockade Runner, Bloo Moose, Borrowed Satellite, Bug Out Bag, Caldera,
Charlatan, Chatterjee University, Citadel Sanctuary, Clan Vengeance, Compromised Employee, Corporate Defector,
Councilman, Counter Surveillance, Crowdfunding (incl. the heap install at the end of a 3-successful-run turn), Crypt,
Dadiana Chacon, Data Dealer, Data Folding, Data Leak Reversal, DDoS, Dean Lister, Decoy, District 99, DJ Fenris, Donut
Taganes, Dr. Lovegood, Drug Dealer, Duggar's, Eden / Hades / Utopia Shard (incl. the install-from-grip replacement of
the breach), Emptied Mind, Enhanced Vision, Fan Site, Fester, Film Critic, Find the Truth, First Responders, Gang Sign,
Gbahali, Gene Conditioning Shoppe (Genetics trigger twice: Adjusted Chronotype, Enhanced Vision, Symmetrical Visage,
Synthetic Blood), Ghost Runner, Globalsec Security Clearance, Grifter, Guru Davinder, Hard at Work, Human First,
Hunting Grounds, Investigative Journalism, Investigator Inez Delgado 1–4, Jak Sinclair (like jinteki, program use
is not forbidden during the run), Jarogniew Mercs, John Masanori, Joshua B., Kasi String, Kati Jones, Keros Mcintyre,
Kongamato, Laguna Velasco District, Levy Advanced Research Lab, Lewi Guilherme, Liberated Chela, Logic Bomb, London
Library, Maxwell James, Miss Bones, Motivation, Mr. Li, Muertos Gang Member, Net Mercur, Neutralize All Threats, New
Angeles City Hall, No One Home, Off-Campus Apartment, Officer Frank, Oracle May, Order of Sol, PAD Tap (incl. the
Corp's trash ability), Paige Piper, Patron, Personal Workshop, Political Operative, Power Tap, Psych Mike, Public
Sympathy, Rachel Beckman, Raymond Flint, Reclaim, Rogue Trading, Rolodex, Rosetta 2.0, Sacrificial Clone, Safety
First, Salsette Slums, Salvaged Vanadis Armory, Same Old Thing, Shadow Team, Slipstream, Spoilers, Stim Dealer, Street
Magic, Street Peddler, Symmetrical Visage, Synthetic Blood, Tallie Perrault, Tech Trader, Technical Writer, Temple of
the Liberated Mind, Temujin Contract, The Archivist, The Helpful AI, The Masque A, The Masque B, The Shadow Net, The
Source, The Supplier, The Turning Wheel, Theophilius Bagbiter, Thunder Art Gallery, Tri-maf Contact, Tyson
Observatory, Underworld Contact, Virus Breeding Ground, Wasteland, Woman in the Red Dress, Wyldside, Zona Sul
Shipping.

## Simplifications / partial cards

(Dummy Box, Sacrificial Construct, Fall Guy, Chrome Parlor and Aeneas Informant were fixed by the prevention / timing pass:
see `STATUS-fix-prevention.md`.)

Not modelled because the engine has no hook for it (and adding one would need edits to shared files):

* (Fixed in the fix-misc pass: **Paparazzi** — the Runner is tagged (`Game.tagged`); its meat damage prevention is a forced
  interrupt of the prevention pass. The Black File is enforced by the restrictions pass.)
* (Fixed in the final rotated pass, see `STATUS-fix-final-rot.md`: **Starlight Crusade Funding** ignores the additional costs of double events.)

Approximations: none left. (Fixed in the final rotated pass, see `STATUS-fix-final-rot.md`: **Hernando Cortez** is an additional rez cost; **Jackpot!** reacts to any agenda added to
the score area; **Keros Mcintyre** also sees derezzes by Runner triggered abilities; **Muertos Gang Member** reacts to every
uninstall; **Levy Advanced Research Lab** lets the Runner order the bottom cards; **Psych Mike** counts the R&D accesses of that run;
**Salvaged Vanadis Armory** is a [trash] ability usable only in the next paid ability window after damage.)

* **Street Magic** — the chosen order is used when the unbroken subroutines resolve; nothing is broken (fix-misc pass; exact).
* **Bazaar** — only hardware installed from the grip (fix-misc pass; exact).
* **DJ Fenris** — the hosted identity is a pseudo card (`Card(code, 0)`) hosted faceup; its triggers and static
  abilities are active through the normal hosted-card rules; it is removed from the game when Fenris leaves play (representation).

## New generic mechanics (`rules-ext-rot-resources.scala`)

Events: `ClickLost(f, n)` (clicks lost through a card ability — Adjusted Chronotype), `Exposed(cards)`.

Effects (handled by `performRotResources`): `ExposeEffect(cards)`, `InstallFacedownEffect(card)`,
`FlipFaceupEffect(card)` (an event is trashed instead), `ClickLossEffect(f, n)` (lose clicks and trigger
`ClickLost`), `BreakSubIndexEffect(ice, idx, breaker)` (break one specific subroutine — Gbahali, Kongamato),
`RunnerForfeitEffect(card)`, `HostUninstalledEffect(card, host, facedown)` (host without installing — Street Peddler,
The Supplier, Personal Workshop), `BreachNoRootEffect(server)` (breach without the root — Gang Sign, Raymond Flint,
Hades Shard), `LoseAllCreditsEffect(f)`, `AccessRandomFromHQEffect(n, noStealOrTrash)` (+ `AccessRandomHQPickedAction`).

Game-level triggers (`rotResourceTriggers`, composed in `Game.triggerEffects`): shard install from the grip instead
of breaching, Counter Surveillance / The Masque A / The Masque B follow-ups of runs started by a card that trashed
itself, Crowdfunding's install from the heap, Shadow Team's install when drawn, and the turn registers
`meat-damage` (Officer Frank) and `rd-accessed` (Psych Mike).

Helpers: `runnerInstallableNow(card, discount)`, `runnerInstalledFaceup`, `promptText` / `promptTitle` (ASCII,
quote-free text for prompts embedded in serialized actions — the shared `asciiTitle` keeps `"`, which breaks the
serializer for titles such as Janaina "JK" Dumont Kindelan).

`RR(code)` base class: `trashOnEmpty`, `thisServer`, `thisCardRun`, `encountering`, `activeRun`, `mark` / `unused`
(once-per-turn keys), `geneticsMark` (first time each turn, second time with Gene Conditioning Shoppe),
`firstSuccessfulRunOn`, `installableFromHand`, `creditsCheckpoint` ("whenever you have 0 credits"), `forfeitChoice`;
`chosen` dispatches the shared `RotPurpose`s (`RotInstallIgnoringPurpose`, `RotInstallPayingPurpose`,
`RotHostPurpose`, `RotPlayPurpose`, `RotRfgGainPurpose`, `RotPumpPurpose`, `RotTrashPayingPurpose`,
`RotRevealPurpose`, `RotMoveVirusPurpose`, `RotUnhostInstallPurpose`, `RotForfeitPurpose`, `RotExposePurpose`,
`RotFlipFaceupPurpose`) to `RotResourceExt.chosen` and everything else to the card's `chosenCard`.

## Shared-file edits (each a single additive term)

| File | Line | Edit | Why |
|---|---|---|---|
| `abilities.scala` | 128 | `++ RotResourceCards.all` in `CardImpls.all` | register the 164 cards |
| `game.scala` | 339 | `with RotResourceRules` on `class Game` | mix in the new mechanics |
| `game.scala` | 682 | `++ rotResourceTriggers(e)` in `triggerEffects` | game-level triggers (grip / heap cards, bookkeeping) |
| `game.scala` | 753 | `orElse performRotResources` in `performInternal` | resolve the new effects |
| `decks.scala` | 252 | `++ RotResourceDecks.presets` | harness / UI preset decks |

## Preset decks (`decks-rot-resources.scala`)

Anarch Reina, Anarch Quetzal, Criminal Steve, Criminal Ken, Criminal Sable, Shaper Kit, Shaper Padma, Apex, Adam,
Sunny Lebeau — all valid (size / influence) and legal in eternal. "Catalyst TD (rot-res)" holds the Terminal
Directive campaign cards (Investigator Inez Delgado 1–4, Shadow Team, The Masque A/B) and Salvaged Vanadis Armory,
which are not legal in any format; it is valid in size and influence and exists only so those cards get tested.

## Verification

* `sbt -batch compile` (Scala.js) green.
* JVM harness (`MergeHost`, every offered and chosen action round-tripped through the serializer, empty asks
  rejected), Runner always one of the 11 rot-res presets, Corp any preset:
  * seeds 31337 and 4242 (final code): 2 × 300 games, **0 errors**. Earlier runs on the way: seeds 999 and 2024,
    2 × 300 games, 0 errors; seed 31337 before the prompt fix, 1 serialization mismatch (fixed, see below). The
    Elevation Weyland (Zwicky) Corp preset was excluded in these runs — see below.
  * all 164 cards appear in the logs; over the final 600 games 140 cards show their effect at least 3 times. The
    rest are static effects (Donut Taganes, The Source, Access to Globalsec, Network Exchange, ...) or rare situations
    (Thunder Art Gallery, Slipstream, New Angeles City Hall, Net Mercur).
* Every card title in the database round-trips through the serializer inside a prompt built with `promptTitle`.

Bugs found by the stress test and fixed: a serialization mismatch on a Councilman prompt naming
Janaina "JK" Dumont Kindelan (quotes in a prompt; fixed by `promptTitle`); Street Magic's choices used raw subroutine
labels (now sanitized); the game-level triggers matched only the first applicable case, so a successful run on a
central server never resolved Counter Surveillance / The Masque A (now composed); Kasi String only considers the
first successful remote run each turn; the Sunny Lebeau preset was 2 cards short.

Pre-existing issue, not in these files: with the "Elevation Weyland (Zwicky)" Corp preset the bot can loop forever
rezzing Plutus (rez cost 0, additional cost unpayable → derezzed → rezzed again; `cards-elev.scala` Plutus has no
`canRez`), which the harness reports as "game too long" (1 game in 300 in a run that included that deck).

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
