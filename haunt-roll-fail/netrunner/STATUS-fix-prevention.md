# Prevention, interrupts and timing windows — fix pass (2026-10-14)

> **Integration note (merge of the four fix passes, see STATUS.md "Integration of the four passes").** PreventionRules is the
> outermost mixin (`... with CreditRules with RestrictionRules with FixRules with PreventionRules`, ENGINE.md 8.6), so an action
> suspended by a window is seen once by FixRules' bookkeeping when it is performed again. `preventSubs` is the single
> resolving-subroutine stack (FixRules' `fxResolvingSubs` is an alias; Banner uses that barrier's subroutines only). Heinlein Grid
> reacts to `ClicksSpentOrLost` only (the fixes pass's `ClickLost` fires for every click loss and would double it). Aeneas
> Informant, Divide and Conquer and AMAZE Amusements keep this pass's events; the fixes pass's duplicates were removed.
> `payCosts` takes the credits pass's `spend`; Tyr's Hand's rez offer checks `canSpend` and `rezBlock`.

Theme: prevention / avoidance / interrupts / timing windows. The engine's prevention support was a per-card continuation
style (each ability pushed `PreventWindowAction(kind, n - 1)` / `TagsEffect(n - 1)` / ... and "prevent all" simply did not
continue), with no trash prevention, no jack-out / bad publicity / encounter / break windows, forced preventions offered as
optional abilities, and a re-offer loop when a "prevent 1" left 0. This pass adds one generic mechanism
(`rules-ext-prevention.scala`, documented in ENGINE.md section 8.4) and ports every card whose status entry described a
prevention / interrupt / timing approximation. Reference: jinteki `src/clj/game/core/prevention.clj`, `damage.clj`,
`tags.clj`, `trace.clj` and the card definitions / tests.

## Mechanisms (`rules-ext-prevention.scala`, trait `PreventionRules`, mixed in last)

* **Prevention windows** (`PreventCtx` stack): damage (net / meat / core), tags, bad publicity, end the run, jack out, trash
  (installed Runner cards), "when encountered" abilities, subroutine breaking. A window resolves the forced interrupts first
  (event `PreventionInterrupt(kind, stage, n)`: stage 0 increases, stage 1 constant / forced preventions), then gives
  priority to the active player and then the other player; a player uses prevention abilities (`CardImpl.preventions`,
  `n` = amount left) until they pass; the window closes when both passed in a row or nothing is left. Abilities change the
  window with `PreventEffect` & co. and the engine returns to the window: no continuation passing, no re-offer once the amount
  is 0 (the loop the hardware port noted is gone), windows nobody can act in are skipped.
* **Window flags**: `firstThisTurn` / `firstThisRun` per kind (and per damage type), `g.preventUses(self)` (uses in this
  window), `by` (the side doing it: Runner cards' install damage is recorded with `SourcedDamageEffect`, subroutine damage /
  end-the-run with the resolving subroutine `preventSubs`), `fromSub`.
* **Trash window**: `trashCard` of an installed Runner card opens a window (batched for one effect) when a card could prevent
  it; costs (`payCosts`, `TrashInstalledCost` -> `CostTrashPurpose`) and game-rule trashes (memory limit, uniqueness, console
  limit, manual adjustment) do not.
* **Jack out window**: `JackOutAction` / `JackOutEffect` when a Corp card can prevent it; a prevented jack out sets
  `cannotJackOut` and returns to the paid ability window.
* **Break window** (Tyr's Hand): `BreakSubAction` / `BreakSubsAction` / `BioroidBreakSubAction` on bioroid ice protecting a
  server with Tyr's Hand: the Corp may rez an unrezzed Tyr's Hand, then use it to prevent 1 subroutine from being broken; the
  break is then performed (costs paid) and the prevented subroutines stay unbroken.
* **Encounter window**: the ice's `onEncounter` effects are queued as `EncounterAbilityEffect(ice)`; AirbladeX / Hunting
  Grounds prevent them in a window (computed when they would resolve).
* **Timing events**: `ClicksSpentOrLost(f, n)` (click costs paid and clicks lost during a run), `AccessFinished(card)`
  (after each access), `BreachFinished(server)` (after the breach of a run, before it ends), `RunWouldSucceed(server)`
  (before the run is declared successful). `PersistentEffect(card, server)` + `preventionTriggers` (game-level triggers):
  run-end abilities of cards trashed during the run; `BreachBonusEffect(n)`: turn-lasting access bonus.
* **Patchwork interrupt** of the basic play / install actions (`PrevPatchworkPurpose`, `PatchworkResumeEffect`).
* **Trace ordering**: `TraceResolved(successful, spent)` is fired before the success / fail effects (NSG timing).
* **Banner**: the "cannot end the run" flag only applies to subroutines (`preventSubs`).
* **Navi Mumbai City Grid** also blocks the Runner's prevention abilities of non-icebreakers.

## Cards fixed

| Code | Card | Before | Now |
|---|---|---|---|
| 12108 | Dummy Box | no effect | trash a grip card of the same type: prevent the Corp from trashing an installed card of that type |
| 20054 | Sacrificial Construct | no effect | [trash]: prevent an installed program / hardware from being trashed |
| 20035 | Fall Guy | only "gain 2" | + [trash]: prevent another installed resource from being trashed |
| 06039 | LLDS Energy Regulator | reinstalled the hardware after the Corp trashed it | 3[c] or [trash]: prevent an installed hardware from being trashed (any player, not costs / game rules) |
| 01045 | Net Shield | once per turn | only on the first net damage of the turn, once |
| 02044 | Muresh Bodysuit | optional once per turn | forced: first meat damage of the turn, prevent 1 |
| 04064 | Leverage | free optional ability | forced: all damage prevented until the Runner's turn |
| 10077 | The Noble Path | free optional ability | forced during its run |
| 08087 | Paparazzi | free optional ability | forced: all meat damage |
| 10084 | Guru Davinder | optional | forced: all net / meat damage, then pay 4 or trash |
| 08044 | Chrome Parlor | no effect | forced: damage done by Cybernetic cards (their install damage) |
| 08072 | Defective Brainchips | Corp ability | forced: +1 to the first core damage of the turn |
| 05022 | Tori Hanzō | once per run, any net damage | first net damage of the run on its server, 2[c]: becomes 1 core damage (Runner may still prevent it) |
| 26107 | Prāna Condenser | could re-offer (loop) | once per damage, Corp-sourced net damage |
| 34022 | AirbladeX (JSRF Ed.) | queue surgery at encounter / net prevention by recursion | encounter window + net damage window, power counter costs |
| 09035 | Hunting Grounds | trigger at encounter | once per turn encounter-ability prevention in the window |
| 26014 | Lucky Charm | end-run ask | end-run window (Corp-sourced) |
| 34005 | Banner | every end-the-run effect | only subroutines |
| 35005 | Shred | "instead" hook re-ending the run | first end of the run: forced interrupt, the Corp trashes X random HQ cards or the run does not end |
| 06063 | Labyrinthine Servers | pre-emptive Corp ability | jack out window: power counter prevents the jack out, no jack out for the rest of the run |
| 04112 | Broadcast Square | trace at bad publicity time, no window | forced interrupt in the bad publicity window: trace[3], success prevents all |
| 01032 / 02109 / 08065 / 11082 | Decoy, New Angeles City Hall, Forger, On the Lam | recursive `TagsEffect(n - 1)` | tag window (player chooses; On the Lam up to 3 tags / damage) |
| 21045 | No One Home | could be used any time, recursion | first tag / net damage of the turn (none of the other yet), trace[0], fail = prevent all |
| 03037 / 02009 / 09032 / 10002 / 11103 / 03036 | Feedback Filter, Plascrete Carapace, Heartbeat, Ramujan-reliant 550 BMI, Recon Drone, Monolith | continuation `PreventWindowAction` | damage window (`Prevent.damage`), counter costs |
| 12006 / 13026 / 12105 / 11070 / 20034 / 12062 / 07050 / 25053 | Bio-Modeled Network, Biometric Spoofing, Caldera, Citadel Sanctuary, Crash Space, Jarogniew Mercs, Sacrificial Clone, Deus X | continuation / "prevent all" by not continuing | damage window |
| 03022 | Tyr's Hand | un-broke the last broken subroutine afterwards | interrupt before the break (rez offer + [trash]: prevent 1 subroutine) |
| 06023 | Heinlein Grid | compared clicks between events | forced trigger on `ClicksSpentOrLost` / `ClickLost` during runs on its server |
| 10110 | Navi Mumbai City Grid | prevention abilities not blocked | blocked |
| 25009 | Patchwork | once-per-turn ability only | interrupt of play / install (ability kept) |
| 12044 | Aeneas Informant | offered at run end | right after each access (`AccessFinished`); revealed only when not installed |
| 22002 | Divide and Conquer | HQ / R&D breaches at run end | right after the Archives breach, during the run (`BreachFinished`) |
| 08041 | Immolation Script | once at the Archives breach, any ice | when a piece of ice that was faceup before the breach is accessed, once per run |
| 30058 | AMAZE Amusements | only while installed | persistent when trashed (rezzed) by the Runner during a run on its server |
| 26006 | Climactic Showdown | stayed installed until the end of the turn | removed from the game when the turn begins; the bonus is a game effect for the turn |
| 31023 | Sneakdoor Beta | server changed at approach | when the run would be declared successful |
| 14026 / 14027 | Sneakdoor Prime A / B | server changed at approach (B optional) | when the run would be declared successful (mandatory) |
| — | traces | `TraceResolved` after the success effects | before (Spinal Modem damage before the success effects) |

## Still approximate (and why)

* Skorpios Defense Systems: removes a just-trashed card from the heap (not a set-aside interrupt): Runner cards reach the heap
  through many direct `moveTo` paths (damage, discards, mills), an interrupt would need all of them routed through one effect.
* Salvaged Vanadis Armory: offered right after the damage (the engine has no "next paid ability window after X" marker).
* "When a trace is initiated" abilities (Flip Switch, Disrupter, Power Tap, Compromised Employee) still use the one-shot "trace"
  ask of `TraceEffect`; Power Tap's gain is optional there.
* Tyr's Hand: the break's `SubsBroken` event still lists the prevented subroutine (the costs are paid and the subroutine is
  removed from the broken list right after the break); breaks performed by card effects (not the break actions) are not
  interruptible. Only Tyr's Hand offers the "rez when a subroutine would be broken" interrupt.
* Damage source: Runner cards' install damage and subroutine damage are attributed; other damage counts as Corp damage
  (Prāna Condenser / Tori Hanzō / Lucky Charm check that). Unpreventable damage is not marked (costs such as Riot Suppression).
* Patchwork: the interrupt covers the basic play / install actions; installs by card effects use its ability.
* Jesminder Sareen, Dorm Computer, Qianju PT: forced tag avoidance as printed, applied by the hardware rules before the tag
  window opens (not a choice).
* AirbladeX / Hunting Grounds cannot prevent "when encountered" abilities gained from ZATO City Grid (triggers).
* Whistleblower (named agenda after trashing it) and Muertos Gang Member ("when uninstalled") are unchanged.

## Shared-file edits

| File | Edit | Why |
|---|---|---|
| `game.scala` | `with PreventionRules` appended to the `Game` mixins (line 340) | mix in the mechanism (last, so its overrides run first) |
| `game.scala` | `++ preventionTriggers(e)` at the end of `triggerEffects` (line 683) | game-level triggers: persistent run-end abilities, Climactic Showdown bonus |
| `rules-effects.scala` | `PreventPassAction` / `PreventUseAction` question and pass label read `g.preventQuestion` / `g.preventPassLabel` (lines 84-85) | prompts describe the window ("Prevent any of the 2 net damage?", "Continue trashing", ...) |
| `rules-run.scala` | `pushAll(encounterAbility(c))` instead of `pushAll(impl(c).onEncounter(c))` in `EncounterStartAction` (line 117) | encounter-ability window |
| `rules-ext-rwr-df-ur.scala` | same in the forced encounter (line 154) | encounter-ability window |
| `rules-ext-ph-tai.scala` | `EndRunEffect` "no-etr" interception requires `preventSubs.any` (line 428) | Banner: subroutines only |
| `rules-ext-rot-agendas.scala` | `TraceResolved` pushed before the success / fail effects (line 228) | NSG trace timing |
| `rules-ext-rot-assets.scala` | Broadcast Square interception of `BadPubEffect` removed (lines 118-122), header comment | moved to the bad publicity window (card trigger) |
| `rules-ext-rot-hardware.scala` | `RHTrashProgramPreventPurpose` pushes `Prevent.damage(1)` (line 251) | Monolith in the damage window |
| `rules-ext-rot-shared.scala` | comment: `TraceResolved` fired before the effects | documentation |
| `ENGINE.md` | `preventions` row, effects list, mixin order, trace note, new section 8.2 | API documentation |
| `STATUS.md`, `STATUS-rot-*.md`, `STATUS-ph-tai.md`, `STATUS-rwr-df-ur.md`, `STATUS-su21-ms.md` | fixed approximations removed / reworded | status |
| card files | `cards-df`, `cards-elev`, `cards-rot-agendas`, `cards-rot-assets`, `cards-rot-events-1/2`, `cards-rot-hardware-1/2`, `cards-rot-operations`, `cards-rot-programs-2/3`, `cards-rot-resources-1/2`, `cards-rot-upgrades`, `cards-sg`, `cards-su21`, `cards-tai`, `cards-ur`: only the objects of the cards listed above | ports |

`abilities.scala` (no new `CardImpl` hook: prevention abilities keep the `preventions` signature, forced interrupts use
`triggers`), `ui.scala`, `meta.scala`, `cards-data*`, `decks*` are unchanged.

## Verification

* `sbt -batch compile` green.
* JVM harness (scratchpad `fix-prevention-host`, pointed at this worktree): `ScriptTest`, `RotIceScript`, `RotAssetScript`,
  `RotAgendaScript`, `RotEventScript`, `RotHardwareScript`, `RotOperationScript`, `RotProgramScript` (LLDS check updated to the
  trash window), `RotMergeScript` all pass; new `PreventionScript` (60 checks covering the cards above) passes.
* Bot games (`MergeHost`, every offered / chosen action round-tripped through the serializer, empty asks and over-long games
  counted as errors) on the final code: 2 x 300 games over all 195 presets (seeds 2002, 3003) + 300 games of rotated Runner
  presets (resources / events / programs / hardware-identity) against rotated Corp presets (agendas+upgrades / assets / operations
  / identities) (seed 33): **900 games, 0 exceptions, 0 empty asks, 0 serialization mismatches, 0 over-long games**; plus 900
  games on intermediate builds (seeds 1, 1001, 11, 22). The logs show the windows in use (tag avoidance, damage prevention,
  encounter-ability prevention, Tyr's Hand, Patchwork, Guru Davinder, Biometric Spoofing, ...).
