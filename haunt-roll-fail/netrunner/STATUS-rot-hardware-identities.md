# Rotated hardware and identities: status

Every card of type Hardware or Identity that had no implementation now has one: 101 hardware + 90 identities = 191 cards.
That covers every older printing (Core Set through Reign and Reverie, System Core 2019, Salvaged Memories, the draft
identities, the unreleased 014001/014002, Cyber Bureau and the Terminal Directive BMI Buffers). They were ported from
jinteki.net `hardware.clj` / `identities.clj`, checked against the `*_test.clj` files, and written to the HRF engine API.
After this work, `Cards.all.%(c => c.kind == "Hardware" || c.kind == "Identity")` minus `CardImpls.all` is empty.

## Files

| File | Contents |
|---|---|
| `rules-ext-rot-hardware.scala` | `trait RotHardwareRules` (mechanics + interception), base class `RH(code)` (extends `Startup`), generic effects / purposes / events (all prefixed `RH`) |
| `cards-rot-hardware-1.scala` | `RotHardwareCards1`: 49 hardware, Core Set .. Data and Destiny (01006 .. 09047) |
| `cards-rot-hardware-2.scala` | `RotHardwareCards2`: 52 hardware, 10002 .. 29003; `object RotHardwareCards { val all }` |
| `cards-rot-identities-1.scala` | `RotIdentityCards1`: 45 identities, 00005 .. 08111; `RHDraft` base class |
| `cards-rot-identities-2.scala` | `RotIdentityCards2`: 45 identities, 09001 .. 25123; `object RotIdentityCards { val all }` |
| `decks-rot-hardware.scala` | `object RotHardwareDecks { val presets }`: 78 generated eternal-legal presets, one per eternal identity, packed with the rotated hardware (all 99 eternal hardware cards appear) |

## Edits to shared files (all additive, one term or one line each)

| File | Line | Edit | Why |
|---|---|---|---|
| `abilities.scala` | 128 | `++ RotHardwareCards.all ++ RotIdentityCards.all` in `CardImpls.all` | registration (one term per object) |
| `game.scala` | 339 | `with RotHardwareRules` | mix in the trait |
| `game.scala` | 682 | `++ rotHardwareTriggers(e)` in `triggerEffects` | game-level triggers: recurring credit refills (identities too), EMP Device's "one ice rez this run" |
| `game.scala` | 753 | `performRotHardware orElse` at the **front** of the `pf` chain | see below |
| `decks.scala` | 252 | `++ RotHardwareDecks.presets` | preset append |

**Why `performRotHardware` is first and not appended.** Most of the new mechanics change how an existing engine action
resolves (the asks it returns, or what is paid), and only the first partial function in the chain sees those actions.
`performRotHardware` is defined only for the actions it intercepts (`rhWants`; it is cheap and returns false for
everything else). For those actions it runs the rest of the chain through `rhInnerPerform(a)`, which calls
`performInternal(a, NoVoid)` with a re-entrancy guard (`rhInner`). That keeps it independent of how the chain is composed
after a merge, and it then adjusts the result. The intercepted actions are:

* `EncounterAction` (break/pump options greyed "cannot pay" are re-enabled when credits usable for that breaker cover the cost;
  rez restrictions), `BreakSubAction` / `BreakSubsAction` / `PumpAction` / Runner `UseAbilityAction` on icebreakers (set
  `rhPayingFor` during the payment);
* `UseAbilityAction` / `PreventUseAction` of the Runner (fires `RHTrashAbilityUsed` for [trash] abilities, used by Geist);
* `InstallWhereAction(Runner, c)` (mods that must be hosted on an icebreaker; extra hosting options on cards that host
  programs without MU), `InstallMenuAction(Runner)` (Apex's restriction, mods without a host),
  `RunnerInstallEffect` (records the zone the card came from: Exile, Autoscripter);
* `RezAction` / `RezEffect` / `ActionPhaseAction` / `PaidWindowAction` / `RezMenuAction`, only while a rez restriction
  flag exists (Cortez Chip surcharge, Saraswati "cannot rez", EMP Device) or SYNC is in play;
* `CheckWinAction` (only while a card modifies the agenda points needed: Harmony Medtech, draft identities);
* `RemoveTagAction` / `TrashResourceAction` (SYNC), `AdvanceAction` on ice (Weyland: Because We Built It),
  `TraceCorpBoostAction` / `TraceCorpAction` (NBN: Making News), `TraceRunnerAction` (fires `RHTraceResolved`, used by Spinal Modem),
  `PsiResolveAction` (fires `RHPsiRevealed`, used by Nisei Division);
* `DamageApplyEffect` (damage choice: Titanium Ribs, Chronos Protocol), `TagsEffect` (automatic tag avoidance: Jesminder,
  Dorm Computer, Qianju PT), `AccessDoneAction` ("after accessing a card": Maya, Maw), `MulliganAction` (Andromeda, Cyber Bureau).

No other shared file was touched (no `rules-*.scala`, `ui.scala`, `meta.scala`, hooks in `abilities.scala`, `cards-data*`, `tools/`).

## New generic mechanics (`rules-ext-rot-hardware.scala`)

Hooks on `RH` (read by the rules through `impl(c)`, so disabled cards lose them):
`recurring` (max recurring credits, refilled when the owner's turn begins, also for identities), `breakerCredits(self, breaker)`,
`hostsWithoutMU`, `modHosts`, `blocksInstall`, `winPointsMod`, `removeTagExtraCost` / `trashResourceCostMod`,
`damageChoice`, `avoidTags`, `afterAccess`, `startingHandBonus`, `rezSurcharge`, and `prevent` (prevention abilities, offered only
while the amount is > 0; the engine re-offers prevention abilities after a "prevent 1" leaves 0, which looped).

Effects: `RHExposeEffect` (expose, `RHExposed` event), `RHFacedownInstallEffect` (Apex, `RHFacedownInstalled`), `RHRefillEffect`,
`RHDamageResolveEffect` / `RHDamageRandomAction` / `RHDamageDiscardEffect` / `RHDamageApplyNowEffect` (chosen + random damage
discards), `RHChronosStartEffect`, `RHStashFromHeapEffect` (BMI Buffer), `RHSpendVirusEffect` (Freedom Khumalo),
`RHRemoveAllCopiesEffect` (Chronos Protocol HB), `RHSwapAgendasEffect` (Turntable), `RHInstallInnermostEffect` (Mti Mwekundu).
Generic purposes (`RHPurpose`, resolved by `RotHardwareExt`): expose, damage pick, trash a program to prevent, add counters,
advance, pump for the run, to top of deck, shuffle into deck, search to hand.
Other helpers: `rhHasMostFaction` (draft identities), `says(self, text)` (log line naming the card, for example "Corp uses Blue Sun to ...").

Techniques reused from the other sets: `Startup` loops (`InstallLoopAction` for Monolith / Cyber Bureau, `ChooseServerForAction`,
`ChooseNumberEffect`, `FlipAction`, `PlayPurpose`, `RevealPurpose`, `StashPurpose`, `TrashRandomFromHandEffect`), PH/TAI flags
(Cortez Chip, EMP Device, Saraswati, The Gauntlet), `RedirectRunEffect`, `MoveRunnerEffect`, `AccessCardsEffect`,
`BreachServerEffect`, `insertAfterRunCleanup` (Doppelgänger).

## Simplifications

* **Recurring / restricted credits.** Icebreaker-only credits (Toolbox, Lockpick, Silencer, Dyson Fractal Generator, Spinal
  Modem, Cyberfeeder, Omni-drive, Flame-out, Ele "Smoke") are exact for break / pump / breaker abilities. Install-only and
  play-only credits (Cybsoft MacroDrive, Cyberfeeder's virus installs, Public Terminal) are a 1-credit discount that consumes the
  hosted credit. Weyland BWBI's credit is spent automatically when advancing ice. Māui's credits pay for anything during HQ runs.
  Patchwork is a once-per-turn ability (trash 1 grip card: the next play/install this turn costs 2 less), not an interrupt.
* **Memory restrictions.** MemStrips / Knobkierie / Deep Red give MU equal to the MU used by virus / Caïssa programs (capped at 3).
  Hosted programs on Omni-drive / NetChip / Dinosaurus offset their MU. Ekomind sets MU to the grip size, but the limit is only
  re-checked when a Runner card leaves play. Apex's facedown programs are offset as well.
* **Apex.** Only the basic install action is blocked for non-virtual resources (installs from card effects are not). Facedown cards
  can't be turned faceup by any card implemented so far.
* **Adam.** The 3 directives (of the 4 Directive resources in the database) are installed as extra copies `Card(code, 100+i)`
  outside the deck. Their own abilities depend on their implementations (resources, other sets).
* **Expose** reveals unrezzed installed cards only. Zaibatsu Loyalty-style expose prevention doesn't exist.
* Skorpios works on cards already in the heap (once per turn the Corp may remove one just-trashed card from the game), not as a
  set-aside interrupt. Gagarin charges 1 credit automatically when a remote root card is accessed (cards are not accessed while the
  Runner has 0 credits). Acme Consulting adds a real tag for the duration of the encounter. SSO Industries counts installed
  agendas only when rezzed (faceup), so it practically never triggers. Seidr Laboratories is a manual once-per-turn ability during runs
  (as on jinteki). Archives Interface flips Archives faceup and offers the removal once when Archives is breached. Mind's Eye's breach
  includes the root. Maya / Maw resolve when the access of the card is finished. Top Hat replaces the breach and accesses the chosen
  card through a pseudo-run. Harishchandra logs the grip (revealed to the Corp in the shared log) while tagged. Kabonesa Wu removes
  programs installed this way at the end of the Runner's turn. Hayley Kaplan doesn't offer mods that need a host. Q-Coherence Chip uses
  the zone the trashed program left. Respirocytes checks for an empty grip on every event.
* Cyber Bureau's and NEXT Design's "before your first turn" happens when the Corp's first turn begins (before the mandatory draw).
  Jinteki Biotech's choice is made then as well.
* **Deck building** (no per-identity hook in `Decks.validate`; not enforced): The Professor (first copy of each program free),
  Custom Biotics (no Jinteki cards), Apex (no non-virtual resources), Adam / Sunny Lebeau / Apex mini-faction rules, The Shadow
  (agendas of all factions), draft-only identities, "Limit 6 per deck" cards are enforced (`deckLimit`). Minimum deck size and
  influence limits come from the card data and are enforced. The generated presets only use cards that pass the existing validator.

## Testing

Harness: scratchpad `rot-hardware-host/` (copy of `mergehost` pointed at this worktree), with the round trip of every offered and
chosen action through `Serialize`. A rotated card counts as having fired when a log line other than the setup lines names it
("uses X to ...", "(X)", "spends N from X", "hosted on X", ...).

* `sbt -batch compile` in `haunt-roll-fail/` is green.
* `MergeHost`, preset filter `(rot)`, 20 + 100 + 300 + 300 games (seeds 12345 / 777): 0 exceptions, 0 empty asks, 0 serialization
  mismatches, no over-long games. All 126 presets validate (the first 300-game run found 6 presets over their influence limit
  because the generator had applied The Professor's rule and treated neutral influence as free; this is fixed). 149 and 153 of the 191
  cards fired in the two 300-game runs. Their union, plus the scripted checks, covers every card that has a triggered or active ability.
  The ones that never "fire" in logs are static (MU / link / hand size / cost modifiers: Cerebral Imaging, Industrial Genomics,
  Cybernetics Division, Chaos Theory, The Professor, Custom Biotics, Sunny Lebeau, Harmony Medtech, HB Stronger Together, ...) or are
  not in any preset (draft identities, The Collective, Chronos Protocol HB, Cyber Bureau, BMI Buffers: not eternal-legal).
* `MergeHost` over all 126 presets (every set), 150 games: 0 errors, which checks that the interception doesn't affect other sets.
* `RotScriptTest` (scratchpad `rot-hardware-host/src/RotScriptTest.scala`): 79 scripted checks, all passing. They cover the
  Toolbox and Ele Smoke credits paying for breaks; Omni-drive / NetChip / Dinosaurus hosting with no free MU; Adjusted Matrix hosting
  and AI; Apex facedown install and its resource restriction; Adam's directives; Harmony / draft 6-point wins; Titanium Ribs and
  Chronos Protocol damage choice; Chronos HB removing every copy; Cortez Chip's surcharge; EMP Device; Jesminder / Dorm Computer tag
  avoidance; SYNC; Making News trace credits; Cyber Bureau and NEXT Design setup; Mti Mwekundu; AgInfusion; Top Hat; Maw; Spinal
  Modem; Monolith; Gagarin; Haarpsichord; Omar Keung; BMI Buffer; Respirocytes; Acme; Kate; Whizzard; Saraswati; Severnius;
  Turntable; Andromeda's mulligan; Security Nexus; Freedom Khumalo; Hippo; Boris / Information Dynamics / Fringe Applications;
  The Collective; Exile with Clone Chip; Nisei Division; Builder of Nations; Jemison; Heartbeat / Feedback Filter / Plascrete;
  Māui; Mâché; e3 Feedback Implants; Net-Ready Eyes; Cerebral Imaging; Industrial Genomics; Cybernetics Division; Jinteki: RP.
  The existing 40 Startup `ScriptTest` checks still pass.
* New Angeles Sol was not exercised: no Corp current has an implementation yet.
