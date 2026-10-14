# Final rules-accuracy pass for the current (NSG-era) sets

Branch point: `netrunner` 53f0c4e. Scope: every approximation still listed for the eleven current sets (System Gateway, System
Update 2021, Midnight Sun + Uprising Booster Pack, Parhelion, The Automata Initiative, Rebellion Without Rehearsal, Downfall,
Uprising, Elevation, Vantage Point), plus the engine-level gaps behind them: choosing the credit sources, credit losses with their
source, Daniela Jorge Inácio's steal cost as a real cost, a "trace initiated" window, root-to-root moves / swaps, breaking an
arbitrary subset of subroutines (Matryoshka), Urban Art Vernissage / Cybersand Harvester install credits. Each card was checked
against the jinteki.net sources (`src/clj/game/cards/*.clj`, `test/clj/game/cards/*_test.clj`); where the printed NSG text and the
Clojure differ, the printed text wins (the differences are noted).

Generic code is in the new file `rules-ext-final-nsg.scala` (`trait FinalNsgRules`, documented in ENGINE.md §8.7); card logic stays
in the card files.

## Mechanisms (`rules-ext-final-nsg.scala`)

* **Mixin position**: `... with CreditRules with RestrictionRules with FixRules with PreventionRules with FinalNsgRules` (outermost;
  since the integration `... with PreventionRules with FinalRotRules with FinalNsgRules`, FinalNsgRules extending FinalRotRules),
  and `performFinalNsg` is the second partial function of the `performInternal` chain (after `performRestrictions`). It suspends
  actions (credit-source choice, additional costs) and performs them again through `performInternal` with a marker, so
  PreventionRules' interrupts and FixRules' bookkeeping see each action once, when it really happens; illegal actions are refused
  before it, and its asks go through `restrictContinue` (extended with `fnsFilter`).
* **(a) Choosing the credit sources.** If two or more restricted / hosted sources (hosted credits whose `paysFor` accepts the
  payment, bad publicity credits of the run) could pay for a user action and there is more than the cost available, the payer gets
  an Ask: `CreditAutoAction` (first option, CreditRules' deterministic order -- the only option the bots take) or
  `CreditTakeAction(f, source, m)` (`CardCreditSrc(card)` / `BadPubCreditSrc` / `PoolCreditSrc`, `NotForBots`), step by step until
  the cost is covered ("rest automatically" at any time; only options that keep the payment, its stealth part included, payable
  are offered). The parts become a plan applied by `spendCredits` to the matching payment (same Spend kind and card); the rest of
  the payment follows the default order. Covered actions (`creditPaymentOf`): play, install (Corp and Runner, hosted), rez,
  advance, the Corp's trash-resource action, remove a tag, trash costs, break, pump, paid / Runner / access abilities, steal costs,
  trace boosts, and "pay N" choices (`ChoiceAction` / `YesAction` whose first effect is a `SpendEffect`). Patchwork's interrupt is
  resolved first. The actions are records with serialisable fields (Faction, `CreditSrc` records, Int).
* **(b) Sources of effects.** (Since the integration: FixRules' `FxSourceEffect` markers, the same as the final rotated pass, and
  `effectSource` = `FinalRotRules.frSourceNow`.) `FnsSourceEffect(card, on)` markers wrap the effects of the cards' triggers (game.scala
  `triggerEffects`), "when encountered" abilities (`encounterAbility`), "when accessed" abilities (`AccessCardAction`), "when
  rezzed" abilities (`rezCard`), paid abilities (`useAbility`) and prevention abilities (`PreventUseAction`). `effectSource` is the
  innermost resolving card (markers, then FixRules' `fxSources`, the resolving subroutine `preventSubs`, the operation / event being
  played). `LoseCreditsEffect` fires `CreditsLostFrom(f, n, source)` in addition to `CreditsLost`.
* **Additional costs before the action.** Marker traits on the card objects: `FnsExtraRezCost.extraRezCost(self)` (paid before the
  rez in `rezCard`, and before the prepaid rez effects `RezDiscountEffect`, `DiscountRezEffect`, `CorpInstallPayingEffect(.., true)`,
  `ServerDiscountChosenAction(.., true)`, Reanimation Protocol; not when rezzed ignoring all costs; once paid the card's `canRez`
  no longer blocks it: `rezBlock` override) and `FnsExtraScoreCost.extraScoreCost(self)` (`ScoreAction`, `ScoreAgendaEffect`). The
  Corp's basic trash-resource action: `trashResourceHQCost(resource)` (Sebastião, Manuel Lattes). Queue-paid costs: `ExtCost`
  (`RandomToBottomCost(n)`), known by `canPay` / `Cost.desc`, paid before `StealAction` / `PayTrashAction` (`resumeWith`), left out
  by `payCosts`; persistent steal costs of a trashed card: run flag `fns-daniela` read by the `stealCostOf` override.
* **(d) "When a trace is initiated".** `TraceEffect` sets the trace up as before (the rotated agendas' base bonus included), then
  `TraceInitiated(base)` is triggered and a prevention-style window of kind `"trace"` opens (both players, active player first,
  `preventions(self, "trace", 1)`); `FlipSwitchTraceAction` sets the base strength to 0 and closes the window; then
  `FnsTraceBoostAction` starts the Corp's boost. The legacy one-shot trace ask is gone.
* **(e) Root moves and swaps.** `MoveToRootEffect`, `SwapRootEffect`, `SwapInstalledWithHandEffect` are refused (and
  `MoveServerChosenAction` greyed) when an asset / agenda would go to a central root or a printed server restriction forbids the
  destination; afterwards each destination root keeps one asset / agenda and one region (the card that was already there is
  trashed, like an overinstall).
* **(f) Breaking a chosen subset.** A break ability for several subroutines (`n == 0` or `n >= 2`) gets a soft
  `BreakChooseSubsAction` (`NotForBots`) when more than two subroutines can be broken: a multi-select (`XXSelectObjectsAction` of
  `SubPick`s, "up to" the affordable number) whose leaf is an ordinary `BreakSubsAction` with the chosen indices.
* **(g) Urban Art Vernissage / Cybersand Harvester**: already real install-credit sources since the credits pass (`paysFor`
  install); verified on the basic install, card-effect discounted installs and `ChooseServerEffect` installs (scripted checks).
* Helpers: `SpendClicksEffect(f, n)` ("spend [click]" = a click cost: `ClicksSpent`, `ClicksSpentOrLost`, no `ClickLost`),
  `PayIfAbleEffect(f, n, spend, otherwise)`, `fnsDoubleCost` / `fnsDoubleCanPlay` (non-Startup double operations through
  FixRules' "double" register and Synchrocyclotron), `gripCards` (grip + Bling's hosted cards), `fnsTrashInfo` (installed / rezzed /
  zone at trash time), `fnsInstallSpent`, register `fns-runner-trashed-own-installed`, run flag `fns-whistleblower`, `fnsMMExtra`
  (MirrorMorph), `canBreakSub` (Unsmiling Tsarevna, printed subroutines only), Bankhar's replacement at resolution
  (`FireSubAction`, `extImpl` keeps the printed subroutines), `RunEffect` honours `canRunServer` (Front Company).

## Cards fixed

| Code | Card | Before | Now |
|---|---|---|---|
| 31074 / 31075 | Corporate Town / Archer | agenda forfeited after the rez (also when rezzed ignoring all costs) | forfeit is an additional cost paid before the rez, on every rez path; none when ignoring all costs |
| 35073 | Plutus | forfeit / trash 3 after the rez, derezzed if unpaid | additional cost paid before the rez |
| 33098 | Bloop | harmonic ice derezzed after the rez | before the rez (also on discounted rezzes) |
| 35074 | Biawak | discount assumed, forfeit or pay 10 after the rez | the Corp chooses "forfeit an agenda (pays 10)" or "pay the full cost" before the rez is paid |
| 33058 | Azef Protocol | other card trashed after scoring | additional cost paid before the agenda is scored (ScoreAction and ScoreAgendaEffect) |
| 34047 | Daniela Jorge Inácio | steal cost paid after the steal, also with < 2 grip cards; trash cost paid after the trash | real costs paid before (steal greyed with < 2 grip cards); persistent for the run when trashed (run flag); other Runner trashes during a run still pay on trash (as jinteki) |
| 26113 | GameNET | any credit loss of the Runner during a run | only losses whose source is a Corp card ability (`CreditsLostFrom`), plus Corp-imposed payments |
| 33094 | Matryoshka | X = 1 or all | any X (multi-select of the subroutines) |
| (all) | break abilities "break up to N" | broke the first N subroutines | the Runner chooses which (multi-select) |
| 26013 | Flip Switch | one-shot ask at trace start | interrupt in the "trace initiated" window |
| 31030 | Atman | install payment from the pool only (lost, not spent) | spent through the credit engine (hosted credits, GameNET / Aircheck semantics) |
| 31066 | Tollbooth | lost 3 credits (pool only) | pays 3 if able (credit engine, Corp-imposed payment), else end the run |
| 31020 | Networking | lost 1 credit | pays 1 when it resolves (credit engine) |
| 31054, 35050, 34051, 34063, 33103, 26099, 33054 | Snare!, Byte!, Behold!, Angelique Garza Correa, Mr. Hendrik, Cerebral Overwriter, Vasilisa | Corp "pay N" as a credit loss from the pool | `SpendEffect` (hosted credits usable, `CreditsPaid`) |
| 34082, 34106, 34102, 34112, 34119, 26052, 26062, 26053 | Physarum Entangler, Sisyphus Protocol, Active Policing, Bring Them Home, Sudden Commandment, Focus Group, Reduced Service, Game Over | "pay N" as a loss | `SpendEffect`; Active Policing / Bring Them Home decide when that part resolves (Bring Them Home's random card is drawn from the current grip) |
| 30042, 33095, 33099 / 34036 (`clickOrEtr`), 34035, 34012, 33022, 35037 | Manegarm Skunkworks, Thule Subsea, Pulse / M.I.C., Jaguarundi, S-Dobrado, Deep Dive, Aggressive Trendsetting | "spend [click]" as a click loss (`ClickLost`, no `ClicksSpent`); Thule's 2 credits lost | `SpendClicksEffect`; Thule pays 2 credits as a Corp-imposed payment |
| 27007 | Cayambe Grid | "pay 0" prompt without advanced ice; pool loss | no prompt when nothing is advanced; `SpendEffect` |
| 27001 | Mystic Maemi | non-printed "take 1 credit" ability | removed (its credits pay for events) |
| 35006 | Bling | "without spending credits" = install cost 0; a non-printed click ability to play / install hosted cards | "no credits were spent on the install" (`fnsInstallSpent`); hosted cards are offered by the basic play / install actions and by the card effects that install from the grip (`gripCards`), as if they were in the grip |
| 35066 | IP Enforcement | X compared with the agenda's current value; installed in a new remote | printed agenda points; the Corp chooses the remote (or a new one); the X tags are paid even without a matching agenda |
| 36062 | Reanimation Protocol | no bad publicity for illicit ice (jinteki); additional rez costs skipped | printed: only liability ice avoids the bad publicity; the ice's additional rez cost is paid first |
| 33122 | Hostile Architecture | its own trash counted when it was unrezzed but seen | only when it was rezzed (active) |
| 33117 | Unsmiling Tsarevna | at most 1 subroutine of any kind | at most 1 printed subroutine (added subroutines can be broken) |
| 33074 | Tsakhia "Bankhar" Gantulga | subroutine list replaced (labels / break restrictions saw "Do 1 net damage") | the printed subroutines stay; each resolves as 1 net damage |
| 34041 | Front Company | card-effect runs on a given remote were not checked | `RunEffect` honours the restriction |
| 34066 / 34075 | Sebastião Souza Pessoa / Manuel Lattes de Moura | "the Corp must trash 1 card from HQ" not enforced | additional cost of the basic trash action (greyed with an empty HQ) |
| 34071 | Boi-tatá | any non-event Runner card trashed this turn while Boi-tatá was installed | installed Runner cards trashed by the Runner this turn (whenever) |
| 34088 | Cataloguer | click ability breached R&D through a full run (run events fired) | breach outside a run (`BreachServerEffect`) |
| 34093 | "Pretty" Mary da Silva | saw only the bonuses granted before it resolved | resolved last among the breach abilities (checked just before the cards are accessed) |
| 34121 | Nuvem SA | only `TrashTopEffect` trashes from R&D | any card the Corp trashes from R&D in its turn |
| 34080 | Cupellation | non-printed "end of breach" Archives hosting; pool loss | its access ability is offered on cards accessed in Archives; the HQ ability pays through the credit engine |
| 26030 | Whistleblower | trashed when the run ends; steal through an access ability | trashed at once (a cost); the named agenda is stolen ignoring all costs when accessed (run flag), mandatory |
| 26031 | MirrorMorph | reward when the third action started; "gain [click]" without restriction | reward when the third action has resolved; "take another different action, paying [click] less" = a click for an action different from the first three (the others greyed); click abilities identified per card copy |
| 31057, 33046, 33100, 34062, 26061, 34103 | Celebrity Gift, Mitosis, Distributed Tracing, Pivot, Secure and Protect, Corporate Hospitality (double) | extra click as a click loss; Synchrocyclotron ignored | spent on playing (FixRules' "double" register); Synchrocyclotron waives it |
| 06016 / 02025 / 02061 (rotated, engine gap d) | Power Tap / Compromised Employee / Disrupter | optional abilities of the old trace ask | Power Tap: forced `TraceInitiated` trigger; Compromised Employee: its credit only pays through `paysFor` (spurious "take" ability removed); Disrupter: interrupt in the window |
| 34029 / 34058 | Urban Art Vernissage / Cybersand Harvester | (status text outdated) | verified: install credits through the credit engine on every install path |

Verified as exact (status entries removed): Marilyn Campaign (it is trashed -- "It is still considered trashed" -- then shuffled
into R&D, as jinteki; nothing observes the card in between), Mitosis (per-card "cannot score / rez this turn" markers = "this
turn"; restrictions pass enforces them on every path), Megaprix Qualifier (printed text), Klevetnik (blank until the Corp's next turn
ends = jinteki's duration), Mercury (run flags), Jaguarundi / Attini / Pulse / M.I.C. choices (the Runner chooses by the rules;
Attini under threat cannot pay = takes the damage), Atman's break option absent when the strength differs (the ability cannot be
used: same as jinteki's `:req`), Oaktown Renovation faceup = rezzed (jinteki's `:install-state :face-up`), Audrey v2's grip-trash
pump as a paid ability (same timing and cost), MirrorMorph's "paying [click] less" as a click for an action that must differ
from the first three (the extra action is taken at once, so the click cannot be used for anything else).

## Still approximate (exact list for the current sets)

(All four were replaced by the printed behaviour in the last rules-accuracy pass: STATUS.md "Last rules-accuracy pass", ENGINE.md 8.9.
The list below is the record of this pass.)

1. **Reanimation Protocol** (36062): when the Corp cannot pay the combined install and rez cost (minus 10), the ice is installed
   unrezzed -- jinteki's behaviour (`corp-install` with `:install-state :rezzed`); kept because the NSG text gives no fallback.
2. **Bling** (35006): its hosted cards are trashed when the Runner's turn ends (after the discard phase's paid ability window)
   instead of "when your discard phase ends" -- as jinteki (`:runner-turn-ends`); the engine has no event between the discard
   phase and that window. Card effects of the rotated sets that pick from the grip do not list Bling's cards (`gripCards` is used by
   the current-set effects).
3. **Mr. Hendrik** (33103): "they may lose all their [click] to prevent this damage" is a Runner choice before the damage, not an
   ability in the damage prevention window (same outcome; other prevention cards still get the window for the core damage).
4. The **credit-source prompt** covers the user actions listed above; payments made inside other effects (a `SpendEffect` that is
   not the first effect of a choice, card-specific prepaid installs, psi bids, secret spending) use the default order. Choosing
   requires two or more restricted sources (one hosted source + the pool uses the default order, which spends the hosted credits
   first and keeps stealth credits for the cards that need them).

## Shared-file edits

| File | Edit | Why |
|---|---|---|
| `game.scala` | `with FinalNsgRules` after `PreventionRules` | the new trait (outermost) |
| `game.scala` | `performFinalNsg` second in the `performInternal` chain | its interceptions / effects |
| `game.scala` | `triggerEffects`: the cards' triggers wrapped in `fnsSourced(c, ...)`; `++ finalNsgTriggers(e)` at the end | sources of effects (GameNET), bookkeeping |
| `game.scala` | `Cost.desc` and `canPay`: `case x : ExtCost` | queue-paid additional costs (Daniela) |
| `ENGINE.md` | mixin order, chain, triggers, 8.2 payment-order note, 8.4 trace note, new 8.7 | documentation |
| `cards-rot-resources-2.scala` | Power Tap: `TraceInitiated` trigger instead of a trace prevention ability | engine gap (d) |
| `cards-rot-resources-1.scala` | Compromised Employee: its spurious trace "take" ability removed (its credit pays through `paysFor`) | engine gap (d) |
| `STATUS*.md` | fixed approximations removed, this file | status |

Current-set card files changed (only the objects of the cards above): `cards-sg.scala`, `cards-su21.scala`, `cards-ms.scala`,
`cards-ph.scala`, `cards-tai.scala`, `cards-rwr.scala`, `cards-df.scala`, `cards-ur.scala`, `cards-elev.scala`, `cards-vp.scala`.
No edits to `abilities.scala`, `rules-*.scala` other than the new file, `ui.scala`, `bot.scala`, `meta.scala`, decks or card data.

## Verification

* `sbt -batch compile` (Scala.js project) green.
* JVM harness (scratchpad `final-nsg/host`: a copy of `merge4/host` pointed at this worktree): all scripted checks pass --
  `ScriptTest` (the Bling check now plays the hosted Sure Gamble with the basic play action instead of the removed click ability),
  `CreditScript`, `PreventionScript`, `RestrictionScript`, `FixScript`, `PresetCheck` (195 presets, 0 invalid), `RotIceScript`,
  `RotAssetScript`, `RotAgendaScript`, `RotEventScript`, `RotHardwareScript`, `RotOperationScript`, `RotProgramScript`,
  `RotMergeScript`, and the new `FinalNsgScript` (every fix above: credit choice for the Runner and the Corp, partial manual choice,
  default order, single source without prompt, Urban Art / Cybersand on three install paths, GameNET with subroutine / Runner /
  sourceless / "when accessed" losses and Tollbooth, Daniela steal / trash / persistent costs, Power Tap + Flip Switch trace window,
  trace without window, root moves / region swap / move greying, Matryoshka subset, Tsarevna, Archer / Bloop / Plutus / Biawak /
  Azef additional costs, Sebastião, spend [click], Cayambe, Boi-tatá, Pretty Mary + Manuel Lattes, Nuvem SA, Cupellation,
  Whistleblower, MirrorMorph, Hostile Architecture, Bling, IP Enforcement, Cataloguer, Mystic Maemi, Atman, Reanimation Protocol,
  Bankhar, Front Company, double operations + Synchrocyclotron).
  `FinalNsgScript`: 82 checks, all passing.
* Bot games (`MergeHost`, every offered and chosen action round-tripped through the serializer; exceptions, empty asks,
  serialization mismatches and over-long games counted as errors) on the final code: 300 + 300 games over all presets (seeds 9101,
  9102; 190 of the 195 presets drawn) + 200 rotated Runner against rotated Corp presets (seed 9103): **800 games, 0 errors**; plus
  750 games on earlier builds of the pass (seeds 9001-9003), 0 errors. The logs show additional rez / score costs (Archer,
  Corporate Town, Plutus, Bloop, Biawak, Azef), Daniela, Sebastiao, MirrorMorph, Whistleblower, Matryoshka, the trace window,
  region overinstalls, Nuvem SA, "Pretty" Mary, GameNET, Tollbooth, Bling. (Bots always take the automatic credit choice; the
  manual choice is covered by the scripted checks.)
