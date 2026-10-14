# Netrunner engine — how to implement a card

This document describes the card ability API of the HRF Netrunner plugin (`netrunner/abilities.scala`) and how the
engine (`game.scala`, `rules-*.scala`) resolves effects, so that cards can be ported from the jinteki.net Clojure
definitions (`/Users/gareth/Developer/HRF/netrunner-src/src/clj/game/cards/*.clj`). The System Gateway set
(`cards-sg.scala`) is the reference implementation: every pattern below is used there.

## 1. Engine overview

* `Game` (game.scala) holds all state: zones (`zones : Map[Zone, $[Card]]`, `location : Map[Card, Zone]`), per-card
  state (`state(c) : CardState` — rezzed, counters, host/hosted, temporary strength, broken subs, once-per-turn keys),
  credits/clicks/tags/bad publicity, the current `run : |[Run]`, the turn registers (`reg(key)`), and the
  **pending effect queue** `pending : $[ForcedAction]`.
* The game is a state machine driven by HRF: `performInternal(action)` performs an action and returns a `Continue`
  (`Ask`, `MultiAsk`, `Shuffle`, `ShuffleTake`, `GameOver`, or an implicit `Force(nextAction)`).
* **The queue.** Anything that needs to happen "next" is pushed to `pending` as a `ForcedAction` and resolved by
  returning `NextAction`, which pops and performs the head. `push(a, b)` puts `a, b` at the *front* (resolved
  next, in that order), `pushAll(list)` likewise, `enqueue` appends to the end. Nested sequences are therefore
  expressed by pushing several actions at once instead of nesting continuations. Run steps are marked with the
  `RunStep` trait and are flushed from the queue when the run ends (`EndRunEffect` → `flushRun()`).
* **Events.** `trigger(event)` collects, from every active card (identities, scored agendas, rezzed corp cards,
  installed runner cards, the event/operation being resolved in `PlayZone`), the effects returned by
  `CardImpl.triggers(self, event)` — active player's cards first — and pushes them to the front of the queue.
  Events are the `Event` records in game.scala (`TurnBegins(f)`, `AgendaScored(card)`, `SuccessfulRun(server)`,
  `RunEnds(server, successful, didSteal)`, `BreachServer`, `ApproachServer`, `EncounterIce`, `PassIce`,
  `Installed(f, card)`, `Rezzed`, `Trashed(card, by, accessed)`, `Advanced`, `TagsGained(n)`,
  `CounterAdded(card, kind)`, `ClickDraw(f)`, `Played`, `Accessed`, `DamageDone`, `SubsBroken`, `Purged`, ...).
* **Registers.** `reg("key")` counts things this turn (`"tags-gained"`, `"breach-hq"`, `"install-program"`,
  `"click-draw"`, `"trash-accessed"`, `"scored-points"`, `"successful-run"`...); `regLast` is last turn's register.
  "The first time each turn X" is tested as `g.reg("x") == 1` inside `triggers` (the register is incremented
  before the event is triggered). `successfulRunsThisTurn`, `successfulRunsLastTurn`, `runsThisTurn` are lists of
  servers. Per-card once-per-turn keys: `usedThisTurn(card, key)` / `markUsed` (or `Ability.once`).
* **Paid ability windows** (`PaidWindowAction(kind)`, rules-abilities.scala) follow the NSG timing structure:
  `turn-start` (after clicks are gained, before "when your turn begins"), `turn-begins` (after those triggers, only
  if any fired), `action` (between actions: the opponent's window is a non-blocking `MultiAsk` next to the active
  player's action prompt, see `ActionPhaseAction`), `discard` (after discarding, before the turn ends),
  `run-initiated`, `approach-ice` (the approached ice can be rezzed here), `encounter` (the Corp's side of
  `EncounterAction`), `movement` (after passing ice: jack out / continue), `approach-server`.
  `windowOptions(f, kind)` lists what `f` can do: rez non-ice cards (Corp), score agendas (Corp, `canScoreNow`:
  only the windows of its own turn outside runs), non-click paid abilities, jack out. A window is skipped for a
  player with no usable options (greyed-out `ok = false` / unaffordable abilities alone do not open it); a player who passed is not asked again while their options are unchanged
  (`passedSig`, `windowSig`), and after using a window action they are not re-asked until their options change.
  Anything that "happens" (`spendClick`, triggered effects, credit gains, breaking, rezzing) reopens the windows
  (`reopenWindows()`). Basic actions spend clicks through `spendClick(f, n)` (counts `reg("actions")` and reopens
  the windows); click abilities pay their `ClickCost` through `payCosts` and `useAbility` reopens the windows.
  The encounter ask (`EncounterAction`) offers break / pump / ability / continue to the Runner and, in the same
  `MultiAsk`, rez / abilities / done to the Corp.
* Every `Ask` produced by a `Reaskable` action automatically gets a `ManualAction` entry (manual adjustments).
  After a manual change the engine re-performs the stored `current` action to regenerate the ask, so Reaskable
  actions must be pure (no state change when performed again).
* **Ordering rule for user actions with hooks**: push the follow-up ask *before* calling the hook so that the
  hook's effects resolve first: `push(ActionPhaseAction(f)); trashCard(c, ...)` / `push(reask(ctx)); rezCard(c)` /
  `push(AccessDoneAction(c)); stealAgenda(c)`. Scoring and stealing move the agenda, then queue `onScore`/`onSteal`,
  the `AgendaScored`/`AgendaStolen` triggers and finally `CheckWinAction` at the very front (the win is checked
  before any "when scored" ability resolves).
* **Trashing** (`trashCard`): a Corp card goes to Archives faceup only if the Runner has seen it (rezzed, accessed,
  revealed / `seen`, scored, or an operation in the play area); cards trashed from HQ / R&D and unrezzed installed
  cards go facedown and the log says "a card" (the log is shared by both players, never name a hidden Corp card in
  it). Hosted cards leave play with their host (and get their own `Trashed` events), a
  `CheckMemoryAction` is queued when a Runner card leaves play (the Runner must trash programs while MU is
  exceeded), unique Corp cards trash other rezzed copies when rezzed (`rezCard`), unique Runner cards on install.
* **Access** (rules-access.scala): cards from R&D are accessed in order (top first), root cards may be accessed
  before, between or after them; HQ accesses are drawn at random when the breach starts; breaching Archives turns
  every card faceup (logged as a sorted set) and accesses all of them (cards without access effects automatically).

## 2. CardImpl API (abilities.scala)

Create an `object` extending `SG(code)` (or `CardImpl`) and register it in a list used by `CardImpls.all`
(for a new set, add `MySetCards.all` to `CardImpls.all` in abilities.scala). All methods take the card instance
`self : Card` and an implicit `g : Game`. Everything is optional; a card with no implementation is still
installable/playable/accessible generically and shows its printed text in the UI.

| Method | Purpose |
|---|---|
| `memory(self)` | +MU provided (Pennyshaver: 1) |
| `link(self)` | +link |
| `handSize(self, f)` | hand size modifier for faction `f` (T400: `f.isRunner.?(1).|(0)`) |
| `strength(self)` | own strength bonus (ice or breaker; Echelon, Pharos, Palisade) |
| `iceStrengthMod(self, ice)` / `breakerStrengthMod(self, breaker)` | strength modifiers applied to other cards |
| `rezCostMod(self, card)`, `installCostMod(self, card)`, `playCostMod`, `trashCostMod`, `advancementRequirementMod` | cost modifiers (Tread Lightly: `+3` for ice during its run; DZMZ Optimizer: `-1`) |
| `agendaPointsBonus(self)` | extra points while in a score area |
| `creditsUsableForRun(self)` | hosted credit counters can pay for anything during a run (Overclock) |
| `paysFor(self, spend)` | hosted credit counters can pay for this payment (`Spend`, see §8.2): Cloak `spend.using.exists(g.isIcebreaker)` |
| `icebreakerSurcharge(self, breaker, breaking)`, `installCostModIn(self, card, server)`, `hostedInstallCostMod(self, card)` | additional credits for icebreaker abilities (Midway Station Grid), install cost by destination server (Network Exchange) / by host (Hackerspace) |
| `advanceable(self)` | can be advanced although not an agenda (Urtica Cipher, Clearinghouse, Pharos) |
| `initialCounters(self)` | counters placed when the card becomes active (runner install / corp rez / event play): `Map("credit" -> 9)` |
| `mustBeHosted(self)`, `hostRequirement(self, host)` | trojans: must be installed on a host satisfying the requirement |
| `canBeTrashedOnAccess(self)`, `accessCostPaidByRunner(self)` | access restrictions / additional steal costs |
| `triggers(self, event)` | **triggered abilities**: return the effects to queue for this event (see §3) |
| `abilities(self)` | **paid abilities** (click abilities included): `$[Ability]` |
| `runnerAbilities(self)` | abilities the Runner may use on a Corp card (bioroid "lose [click]: break") |
| `accessAbility(self, target)` | ability usable while accessing `target` (Carnivore) |
| `preventions(self, kind, n)` | prevention abilities offered in the prevention windows (§8.2): `"net"` / `"meat"` / `"core"` damage, `"tag"`, `"trash"`, `"end-run"`, `"jack-out"`, `"bad-pub"`, `"encounter"`, `"break"` (`n` = amount left); their effects change the window (`PreventEffect`, ...) |
| `subroutines(self)` | ice subroutines `$[Sub(label, effect)]`, in order |
| `onEncounter(self)` | "when the Runner encounters this ice" effects (Funhouse) |
| `breaks(self)` | icebreaker break abilities `BreakAbility(costs, n, subtypes, label, after)`; `n = 0` = any number, empty subtypes = any ice |
| `pumps(self)` | `Pump(costs, strength, duration, label)` |
| `onPlay(self)` | operation/event effects; `makesRun(self)` = true keeps the card active in `PlayZone` until its run ends |
| `onScore`, `onSteal`, `onAccess`, `onRez`, `onInstall`, `onTrash(self, by)`, `onEncounterEnds` | lifecycle hooks returning effects |
| `perform(a : CardAction)` | handles the card's own `CardAction`s (see §4) |
| `chosen(p : CardPurpose, cards)` | completion of a card choice made with a `CardPurpose` (see §4) |

`Ability(label, costs, effects, click = false, once = None, ok = true, reason = "")`:
`costs` is a list of `Cost` (`CreditCost(n)`, `StealthCreditCost(n, stealth)` (at least `stealth` of the n credits from
Stealth cards), `ClickCost(n)`, `TrashSelfCost`, `VirusCost(n)` (hosted virus
counters), `CounterCost(kind, n)`, `TrashFromHandCost(n)` (the engine asks which cards), `LoseClickCost(n)`,
`RemoveSelfCost`, `TagCost(n)`); `click = true` marks a `[click]:` action usable only in the controller's action
phase; `once = Some("key")` makes it once per turn; `ok = false` with a `reason` shows it greyed out. The engine
checks affordability itself; the labels may use `[c]`, `[click]`.

## 3. Effects (the vocabulary of `triggers`, `onPlay`, subroutines, ...)

All effects are plain `ForcedAction` records from rules-effects.scala, so they are serialisable and composable:

* Resources: `GainCreditsEffect(f, n)`, `LoseCreditsEffect(f, n)`, `DrawEffect(f, n)`, `GainClicksEffect`,
  `LoseClicksEffect`, `TakeCreditsEffect(f, card, n)` (move hosted credits to the pool), `AddCounterEffect(card, kind, n)`,
  `PlaceAdvancementEffect(card, n)`, `MillEffect(f, n)`.
* Damage / tags: `DamageEffect("net"|"meat"|"core", n)` (random discards via `ShuffleTake`, flatline check,
  prevention window), `TagsEffect(n)`, `RemoveTagsEffect(n)`, `BadPubEffect(n)`. Inside a prevention window: `PreventEffect(kind, n)`
  (`Prevent.damage(n)`, `Prevent.allDamage`, `Prevent.tags(n)`, `Prevent.endRun`, ...), `PreventTrashChooseEffect`, `PreventBoostEffect`,
  `PreventConvertDamageEffect`, `PreventBreakChooseEffect` (§8.2).
* Runs: `EndRunEffect`, `JackOutOfferEffect` ("the Runner may jack out"), `RunEffect(server, source)`,
  `RunAnyServerEffect(source, servers)` (Runner chooses), `AccessBonusEffect(server, n)`, `CannotStealOrTrashEffect`,
  `PumpIceEffect(ice, n, duration)`, `PumpBreakerEffect`, `BypassEffect()`, `BioroidBreakEffect(ice)`.
* Cards: `TrashEffect(card, by, accessed)`, `TrashCardsEffect`, `MoveEffect(card, zone, top)`, `AddToHandEffect`,
  `ShuffleDeckEffect(f)`, `RezEffect(card, ignoreCost)`, `DerezEffect`, `CorpInstallEffect(card, server, ignoreCost)`
  (`server = None` → new remote), `RunnerInstallEffect(card, host, ignoreCost)`, `ChooseServerEffect(...)`,
  `ShuffleFromArchivesEffect(n)`, `SwapIceEffect(a, b)`, `RevealEffect`, `RemoveFromGameEffect`, `PurgeEffect`.
* Choices: `OptionalEffect(f, prompt, yesEffects, noEffects)` ("you may"), `ChooseOneEffect(f, prompt, $(Alt(label, effects, ok)))`,
  `ChooseCardEffect(f, candidates, purpose, prompt, optional)`, `ChooseCardsEffect(f, candidates, min, max, purpose, prompt)`.
  Generic purposes: `TrashPurpose(by)`, `AddToHandPurpose(f, reveal)`, `ShuffleIntoDeckPurpose(f)`, `InstallPurpose(ignoreCost, server)`,
  `InstallChooseServerPurpose(ignoreCost)`, `AdvancePurpose(n)`, `RezPurpose(ignoreCost)`, `DiscardPurpose(f)`, `HostInstallPurpose(card, ignoreCost)`.
* Bookkeeping: `MessageEffect(f, text)`, `RegisterEffect(key, n)`, `MarkUsedEffect(card, key)`, `SetFlagEffect("cannot-score")`,
  `TraceEffect(base, success, fail)`, `PsiEffect(equal, differ)`, `CheckWinAction()`.

`Helpers` (import `Helpers._`) has shortcuts: `gain(f, n)`, `lose`, `draw`, `net(n)`, `meat`, `core`, `tag(n)`, `etr`,
`c(n)` (credit cost list), `click()`, `etrSub`, `netSub(n)`, `tagSub(n)`, `gainSub(n)`, `loseSub(n)`.

Important ordering rule: `push` puts things at the *front*. To run A then B then continue, `push(A, B)`.
`trigger(e)` also pushes to the front, so call it after pushing what must happen *after* the triggered abilities.

## 4. Card-specific actions and choices

When an effect needs the card's own logic (computed at resolution time, not at queue time), define a top-level
`case class XAction(card : Card) extends CardAction` and handle it in `perform`. The engine dispatches any
`CardAction` to `CardImpls(card.code).perform`. Likewise, define `case class XPurpose(card : Card) extends CardPurpose`
for card choices: `ChooseCardEffect(f, candidates, XPurpose(self), ...)` calls `chosen(p, cards)` on the
implementation with the selected cards. Both must be **top-level** classes in package `netrunner` with
serialisable fields (Int, Boolean, String (ASCII), Faction, Card, Server, Option, List, other records).

**Build choices when they resolve, not when they are queued.** `triggers` are collected when the event fires and
several triggered effects may resolve before yours (Earthrise Hotel draws at "turn begins" before Read-Write Share
asks which grip card to host). A `ChooseCardEffect(f, g.hand(f), ...)` built inside `triggers` would offer the stale
grip; queue a `CardAction` instead and build the choice in `perform` (`RwsAskAction` in cards-vp.scala).

**Additional rez / play costs.** If a card has an additional cost that can be unpayable (Plutus: forfeit an agenda or
trash 3 cards from HQ), override `canRez` / `canPlay` so the option is greyed out; otherwise the rez is offered, fails
and is offered again (the bots loop on it).

## 5. Worked examples (ported from the Clojure sources)

### Hedge Fund (operation) — `{:on-play (gain-credits-ability 9)}`
```scala
object HedgeFund extends SG("30075") {
    override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, 9))
}
```

### Leech (program) — successful-run trigger + paid ability with a virus-counter cost
Clojure: `:events [{:event :successful-run :req (is-central? ...) :effect (add-counter card :virus 1)}]`,
`:abilities [{:cost [(->c :virus 1)] :req (active-encounter?) :effect (pump-ice current-ice -1)}]`
```scala
object Leech extends SG("30008") {
    override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
        case SuccessfulRun(s) if s.isCentral => $(AddCounterEffect(self, "virus", 1))
        case _ => $
    }
    override def abilities(self : Card)(implicit g : Game) = {
        val ice = g.run./~(_.ice).%(_ => g.run.exists(_.phase == EncounterPhase))
        $(Ability("give -1 strength to the encountered ice", $(VirusCost(1)), ice.$./(PumpIceEffect(_, -1, EncounterDuration)), ok = ice.any, reason = "not encountering ice"))
    }
}
```

### Diviner (ice) — a subroutine whose result depends on what happened
Clojure: do 1 net damage, then if the trashed card's cost is odd, end the run. The damage is random, so the
check must run *after* the damage resolves: queue the damage followed by a card action.
```scala
case class DivinerAction(card : Card) extends CardAction
case class DivinerCheckAction(card : Card) extends CardAction

object Diviner extends SG("30046") {
    override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 1 net damage; if the trashed card's cost is odd, end the run", DivinerAction(self)))
    override def perform(a : CardAction)(implicit g : Game) = a match {
        case DivinerAction(self) =>
            g.lastDamaged = $
            g.push(net(1), DivinerCheckAction(self))
            NextAction
        case DivinerCheckAction(self) =>
            if (g.lastDamaged.single.exists(c => c.data.cost.exists(_ % 2 == 1)))
                g.push(EndRunEffect)
            NextAction
    }
}
```

### Mutual Favor (event) — search, reveal, optional install, shuffle
Clojure: choose an icebreaker from the stack; if a successful run was made this turn, may install it, else add to grip; shuffle.
```scala
case class MutualFavorPurpose(card : Card) extends CardPurpose
case class MutualFavorInstallAction(card : Card, breaker : Card) extends CardAction

object MutualFavor extends SG("30011") {
    override def onPlay(self : Card)(implicit g : Game) = {
        val l = g.deck(Runner).%(_.data.isIcebreaker).sortBy(_.title)   // sorted: do not leak the stack order
        l.any.$(ChooseCardEffect(Runner, l, MutualFavorPurpose(self), "Mutual Favor: choose an icebreaker from the stack", true)) :+ ShuffleDeckEffect(Runner)
    }
    override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
        cards.single.foreach { b =>
            if (g.successfulRunsThisTurn.any && g.credits(Runner) >= g.installCost(b, None))
                g.push(OptionalEffect(Runner, "Mutual Favor: install the icebreaker?", $(MutualFavorInstallAction(p.card, b)), $(AddToHandEffect(b))))
            else
                g.push(AddToHandEffect(b))
        }
        NextAction
    }
    override def perform(a : CardAction)(implicit g : Game) = a match {
        case MutualFavorInstallAction(_, b) => g.push(RunnerInstallEffect(b, None, false)) ; NextAction
    }
}
```

### Manegarm Skunkworks (upgrade) — "when the Runner approaches this server" with a Runner choice
```scala
override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
    case ApproachServer(s) if thisServer(self, s) && g.isRezzed(self) =>
        $(ChooseOneEffect(Runner, "Manegarm Skunkworks: choose one", $(
            Alt("Spend [click][click]", $(LoseClicksEffect(Runner, 2)), g.clicks(Runner) >= 2),
            Alt("Pay 5 credits", $(LoseCreditsEffect(Runner, 5)), g.credits(Runner) >= 5),
            Alt("End the run", $(EndRunEffect)))))
    case _ => $
}
```

## 6. Mapping the Clojure vocabulary

| Clojure | Here |
|---|---|
| `:static-abilities [(mu+ 1)]` | `memory(self) = 1` |
| `(corp-hand-size+ 1)` / `{:type :hand-size}` | `handSize(self, f)` |
| `(ice-strength-bonus req n)` / `(breaker-strength-bonus ...)` | `strength(self)` (own) or `iceStrengthMod` / `breakerStrengthMod` (others) |
| `{:type :rez-cost / :install-cost ... :value n}` | `rezCostMod` / `installCostMod` |
| `:events [{:event :x :req ... :effect ...}]` | `triggers(self, e)` pattern matching the `Event` |
| `:optional {:prompt ... :yes-ability ...}` | `OptionalEffect(f, prompt, yes, no)` |
| `:choices ["A" "B"]` / `choose-one-helper` | `ChooseOneEffect(f, prompt, $(Alt(...)))` |
| `:choices {:card pred}` / `:max n` | `ChooseCardEffect` / `ChooseCardsEffect` with a purpose |
| `:abilities [{:cost [...] :action true ...}]` | `abilities(self)` with `click = true` |
| `:runner-abilities [(bioroid-break 1 1)]` | `runnerAbilities(self)` with `LoseClickCost(1)` and `BioroidBreakEffect(self)` |
| `:subroutines [end-the-run (do-net-damage 2) ...]` | `subroutines(self) = $(etrSub, netSub(2), ...)` |
| `:on-encounter` | `onEncounter(self)` |
| `(break-sub 1 2 "Code Gate")` / `(strength-pump 3 1)` | `breaks = $(BreakAbility(c(1), 2, $("Code Gate")))` / `pumps = $(Pump(c(3), 1))` |
| `:on-play`, `:on-score`, `:stolen`, `:on-access` / `advance-ambush`, `:on-rez`, `:on-install`, `:on-trash` | `onPlay`, `onScore`, `onSteal`, `onAccess` (+ `advanceable`), `onRez`, `onInstall`, `onTrash` |
| `:data {:counter {:credit 9}}` | `initialCounters` |
| `(trash-on-empty :credit)` | `SG.trashOnEmpty(self, e, "credit")` inside `triggers` |
| `this-card-run`, `this-server`, `first-event?`, `runner-reg :successful-run` | `thisCardRun(self)`, `thisServer(self, s)`, `g.reg(key) == 1`, `g.successfulRunsThisTurn.any` |
| `:interactions {:access-ability ...}` | `accessAbility(self, target)` |
| `:interactions {:pay-credits {:req ...}}` | `paysFor(self, spend)` (`(= :ability (:source-type eid))` + `target` → `spend.using`, `:runner-install` → `spend.installing`, `:rez` → `spend.rezzing`, `:play` → `spend.playing`, `:runner-trash-corp-cards` → `spend.trashing`, `:trace` / `:remove-tag` → `spend.is(...)`); `(req run)` → `creditsUsableForRun` |
| `(->c :credit n {:stealth k})` / `{:stealth :all-stealth}` | `StealthCreditCost(n, k)` / `StealthCreditCost(n, n)`; a "pay N" inside an effect: `SpendEffect(f, n, spend, stealth, then)` |
| `trojan` | `mustBeHosted` + `hostRequirement` |

## 7. Checklist for a new card

1. Find the `(defcard "Title" ...)` in the Clojure sources and the tests in `test/clj/game/cards/*.clj`.
2. Write `object X extends SG("code")` overriding the relevant hooks; keep log messages ASCII; never put card
   titles into action fields (use `Card`).
3. If the card needs a decision at resolution time, add a `CardAction`/`CardPurpose` and implement `perform`/`chosen`.
4. Add the object to the set's `all` list; `sbt -batch compile`.
5. Test with the JVM harness (`netrunner/host.scala`, see STATUS.md) and in the browser.

## 8. Extension architecture (the card-set rules files)

The engine core (`game.scala`, `rules-*.scala`) is set-agnostic. The mechanics introduced by the later card sets live in
four `rules-ext-*.scala` files, each contributing a trait that `Game` mixes in (game.scala):

| File | Trait | Base class for its cards | Card files |
|---|---|---|---|
| `rules-ext-startup.scala` | `StartupRules` | `Startup(code)` (`performCard` / `chosenCard`, generic loops) | `cards-elev.scala` (`ElevCards`), `cards-vp.scala` (`VPCards`) |
| `rules-ext-su21.scala` | `SU21Rules` (`performSU21`, `su21Triggers`, `resolveSU21Purpose`) | `Ext(code)` (cards-su21.scala) | `cards-su21.scala` (`SU21Cards`), `cards-ms.scala` (`MSCards`, incl. urbp) |
| `rules-ext-ph-tai.scala` | `PHTAIRules` (`performPHTAI`, `phTriggers`, `extImpl`) | `PT(code)` | `cards-ph.scala` (`PHCards`), `cards-tai.scala` (`TAICards`) |
| `rules-ext-rwr-df-ur.scala` | `RWRRules` (`performRWR`, `resolveRWRPurpose`) | `NC(code)` | `cards-rwr.scala`, `cards-df.scala`, `cards-ur.scala` (`RWRCards`, `DFCards`, `URCards`) |

`CardImpls.all` (abilities.scala) is the union of the seven set objects plus `SGCards`; all `CardImpl` hooks added by the
sets have neutral defaults in `trait CardImpl`, so a card of any set may use any hook.

How the pieces are chained:

* **Actions.** `performInternal` tries `performPHTAI` first (it intercepts engine asks to apply generic restrictions —
  `canRez`, `canInstall`, `allowsRun`, remote limits, `cannot-score` / `cannot-break` flags — and counts Corp actions for
  `onActionTaken`), then the core partial functions, then `performSU21` and `performRWR` for the effects those files define.
  `CardAction`s not handled by any of them go to `impl(card).perform`; anything else that is `SelfPerform` (the Startup
  effects) performs itself.
* **Triggers.** `triggerEffects(e)` = `phTriggers(e)` bookkeeping first (it snapshots the board before the cards look at it),
  then the cards' `triggers` (active player first, disabled cards skipped), then `su21Triggers(e)` (game-level triggers not
  tied to an active card: Subliminal Messaging in Archives, Light the Fire!, bad publicity credits...), then the ph/tai
  expiry effects. `trigger(e)` pushes them all and reopens the paid ability windows.
* **Card implementation wrappers.** `impl(c)` goes through `extImpl(c)`: a card with a lingering flag (`disabled`,
  `net-subs`, `cannot-break`), a host that disables it (`disablesHost`) or sitting in the root during a Light the Fire!
  run gets a `DisabledImpl` / `NetDamageSubsImpl` / `NoBreakImpl` wrapper (`DelegatingImpl` delegates every hook).
  `isDisabled(c)` is the single predicate for "lost all abilities". Static hooks of a *host* (`disablesHosted`) are read
  from the raw `CardImpls(h.code)` to avoid recursion.
* **Purposes.** `resolvePurpose` (rules-effects.scala) dispatches `SU21Purpose`s to `resolveSU21Purpose`, `CardPurpose`s
  to the card (`StartupPurpose`s are `CardPurpose`s handled by the `Startup` base class), and everything else to
  `resolveRWRPurpose` (the plain generic purposes: `DerezPurpose`, `BottomOfDeckPurpose(f)`, `TopOfDeckPurpose(f)`,
  `HostOnPurpose(host, uninstalled)`, `ForfeitPurpose` (SU21), `TrashFromHandPurpose(f)`, ...). `PHPurpose`s are
  resolved by `performPHTAI` intercepting `CardsChosenAction`.
* **One definition per mechanic.** Where several sets needed the same thing there is exactly one definition; the other
  files only reference it: `SabotageEffect(n)`, `ChargeEffect(f)`, `ReplaceBreachEffect` (su21); marks (`mark`,
  `IdentifyMarkEffect`, `SetMarkEffect`, `MarkIdentified`), `StashEffect`, flags (ph/tai); `StashZone(host)`,
  `ForfeitEffect(card)` (+ `ChooseForfeitEffect` for "forfeit an agenda of your choice"), `RedirectRunEffect(server, ...)`
  (change the attacked server), `threat(n)`, `lastScoredAdvancement`, events `IceBypassed` / `TagsRemoved` / `Forfeited` /
  `CreditsGained` (startup); `ArrangeTopEffect(f, owner, n)`, `LookAtEffect`, `MoveToRootEffect`, `JackOutEffect`,
  `MoveRunnerEffect(position, jackOut)` (move within the server), `SetTargetEffect(card, |[Card])`, `AddToScoreAreaEffect(card, f)`,
  `ExtraClicksEffect` (allotted clicks next turn), `CreditsSpentFrom(card, n)` (rwr/df/ur). `CardState` keeps `target`,
  `choice`, `memo` and `uninstalled`; `Run` keeps the pseudo-run fields (`accessOnly`, `outer`, `disableRoot`) and the
  forced-encounter fields (`forced`, `forcedPrev`, `blockSuccess`, `atEnd`).
* **Serialization rule.** User actions are written to the journal with the reflective serializer, and they embed purposes
  and effect lists (`CardChosenAction(f, c, purpose)`, `YesAction(f, prompt, effects)`). A parameterless `case class X()`
  does not survive the round trip (it parses back as its companion object), so every parameterless purpose or action
  that can be embedded must be a `case object` (`DerezPurpose`, `BypassEffect`, `CheckWinAction`, `RunNextAction`, ...).
  The JVM harness checks every offered and chosen action for this.

### 8.1 The rotated (FFG-era) extension files

The 1,361 rotated cards add nine more rules files, one per ported card type plus one for the mechanics that several
ports shared:

| File | Trait (entry points) | Base class for its cards | Card files |
|---|---|---|---|
| `rules-ext-rot-shared.scala` | `RotSharedRules` (`performRotShared`, `resolveRotSharedPurpose`) | (none) | (none) |
| `rules-ext-rot-ice.scala` | `RotIceRules` (`performRotIce`, `rotIceTriggers`) | `RI(code)` (cards-rot-ice.scala) | `cards-rot-ice*.scala` (`RotIceCards`) |
| `rules-ext-rot-programs.scala` | `RotProgramRules` (`performRotPrograms`, `resolveRotPurpose`) | `Rot(code)` | `cards-rot-programs*.scala` (`RotProgramCards`) |
| `rules-ext-rot-resources.scala` | `RotResourceRules` (`performRotResources`, `rotResourceTriggers`) | `RR(code)` | `cards-rot-resources-*.scala` (`RotResourceCards`) |
| `rules-ext-rot-events.scala` | `RotEventRules` (`performRotEvents`, `rotEventTriggers`) | `RE(code)` | `cards-rot-events-*.scala` (`RotEventCards`) |
| `rules-ext-rot-operations.scala` | `RotOperationRules` (`performRotOperations`, `rotOperationTriggers`) | `RotOp(code)`, `RotCondition(code)` | `cards-rot-operations*.scala` (`RotOperationCards`) |
| `rules-ext-rot-hardware.scala` | `RotHardwareRules` (`performRotHardware`, `rotHardwareTriggers`) | `RH(code)` | `cards-rot-hardware-*.scala`, `cards-rot-identities-*.scala` (`RotHardwareCards`, `RotIdentityCards`) |
| `rules-ext-rot-assets.scala` | `RotAssetRules` (`performRotAssets`, `rotAssetTriggers`, stackable overrides) | `RA(code)` | `cards-rot-assets*.scala` (`RotAssetCards`) |
| `rules-ext-rot-agendas.scala` | `RotAgendaRules` (`performRotAgendas`, `rotAgendaTriggers`, stackable overrides) | `RAg(code)` | `cards-rot-agendas*.scala`, `cards-rot-upgrades.scala` (`RotAgendaCards`, `RotUpgradeCards`) |

Naming: the base classes and purpose traits of the ports are distinct (`RI`, `Rot`, `RR`, `RE`, `RotOp`, `RH`, `RA`, `RAg`;
`RotIcePurpose`, `RotProgPurpose`, `RotResPurpose`, `RotAgPurpose`, `RHPurpose`). New top-level names in a rotated file must
not collide with another file of the package.

**Mixin order** (game.scala):

    class Game ... with SetupRules with TurnRules with CardRules with RunRules with AccessRules with EffectRules with ManualRules with AbilityRules
        with StartupRules with SU21Rules with PHTAIRules with RWRRules
        with RotSharedRules with RotIceRules with RotProgramRules with RotResourceRules with RotEventRules with RotOperationRules with RotHardwareRules
        with RotAssetRules with RotAgendaRules
        with CreditRules with RestrictionRules with FixRules with PreventionRules with FinalNsgRules

Among the card-set traits only `RotAssetRules` and `RotAgendaRules` override engine methods (the four rules-accuracy traits
mixed in after them are described in 8.2-8.6; their composition in 8.6). They extend the core traits they refine (assets:
`EffectRules, TurnRules, CardRules, RunRules, PHTAIRules, RWRRules`; agendas: `TurnRules, CardRules, AccessRules,
EffectRules, AbilityRules, StartupRules`) and every override handles its own cases and delegates the rest to `super`.
Scala linearization runs the **last** mixed-in trait's override first: `RotAgendaRules` → `RotAssetRules` → the base
definition (`EffectRules.performEffects`, `TurnRules.performTurn`, `CardRules.performCards`, `RunRules.performRun`,
`AccessRules.performAccess`, `AbilityRules.performAbilities / windowOptions / abilityOptions`, `EffectRules.rezCard /
resolvePurpose`, `PHTAIRules.extImpl`, `RWRRules.subs`, `StartupRules.stealCostOf / accessProtected`). `SU21Rules`,
`PHTAIRules` and `RWRRules` define but do not override these methods, so they are the bottom of the stack. A new
override must call `super` for everything it does not fully handle, and must not take over an action another override
already reimplements: `TraceRunnerAction` is reimplemented by `RotAgendaRules` (link treated as 0, `TraceResolved`), so
nothing below it sees that action. Every call through `performEffects` / `performTurn` / ... (including the ones in
`performPHTAI` and `performRotEvents`) goes through the whole stack.

**`performInternal` chain**:

    performRestrictions orElse performFinalNsg orElse performRotHardware orElse performRotEvents orElse performPHTAI orElse
    performSetup orElse performTurn orElse performCards orElse performRun orElse performAccess orElse performEffects orElse
    performManual orElse performAbilities orElse performSU21 orElse performRWR orElse
    performRotShared orElse performRotIce orElse performRotPrograms orElse performRotResources orElse performRotOperations orElse
    performRotAssets orElse performRotAgendas orElse performFixes

and the whole result is post-processed by `restrictContinue(...)` (RestrictionRules, 8.3).

* Front interceptors. `performRotHardware` is defined only for the actions in `rhWants` (breaker credits, mod hosting, rez
  surcharges / restrictions, win-point modifiers, SYNC, ice-advance and trace credits, damage choice, tag avoidance,
  after-access, mulligan bonus). It performs the rest of the chain through `rhInnerPerform` (re-entrant `performInternal`
  with the `rhInner` guard) and adjusts the result. `performRotEvents` comes next: while `rotActive` it wraps the asks of
  `performPHTAI` / `performTurn` to grey out options restricted by currents and flags, and it handles a few run steps
  (run R&D again, access from the bottom, extra turns). `performPHTAI` keeps its role of intercepting engine asks.
* Then the core chain and the older extension effects, then the rotated effects. These are distinct case classes, so
  their relative order does not matter.
* Purposes: `resolvePurpose` → (assets/agendas overrides: Blacklist filtering, `CorpChosenHQAccessPurpose`,
  `ShuffleInstalledIntoStackPurpose`) → base dispatch → `resolveRWRPurpose` → `resolveRotPurpose` (programs) →
  `resolveRotSharedPurpose` (`ExposePurpose`).

**Triggers**:

    cards' triggers ++ su21Triggers ++ rotIceTriggers ++ rotResourceTriggers ++ rotHardwareTriggers ++ phTriggers
        ++ rotEventTriggers ++ rotOperationTriggers ++ rotAssetTriggers ++ rotAgendaTriggers ++ preventionTriggers ++ fixTriggers ++ finalNsgTriggers

(the cards' own triggers are wrapped in `FnsSourceEffect` source markers, see 8.7)

The bookkeeping registers of the events, operations and assets ports are counted after the cards have seen the event
(`reg(k) == 0` means "first time this turn" inside a card trigger).

**One definition per rotated mechanic** (the other files only reference it):

* expose: `ExposeEffect(cards)` (shared). It keeps installed unrezzed Corp cards, offers the Corp's responses
  (`RA.exposeOptions`: Zaibatsu Loyalty, through `RotExposeEffect(card)` / `RotExposePreventedEffect(card)`), then
  `ExposeResolveAction` exposes the rest, increments `reg("exposed")` and fires **one** `Exposed(cards)` event (`RA.onExpose`
  reacts through `rotAssetTriggers`). Use `ExposePurpose` for "choose cards to expose".
* facedown installs: `InstallFacedownEffect(card)` + `FacedownInstalled(card)` (shared). It records `rotFacedown` (Harbinger).
  `Game.memoryUsed` skips facedown programs. `FlipFaceupEffect` (resources) and `TurnRigFacedownEffect` (events) remain
  separate effects.
* `LoseAllCreditsEffect(f)`, `BreachNoRootEffect(server)` (`access-exclude` flags around `BreachServerEffect`),
  `SwapRootEffect(a, b)` (shared).
* trace result: `TraceResolved(successful, spent)` (agendas). It is fired once per trace, before the success/fail effects.
* psi result: `RotPsiRevealed(corp, runner)` (assets). It is fired once per psi game.
* recurring credits: `RefillCreditsEffect(card, n)` (su21) refills installed cards and active identities. What they pay for
  is declared with `paysFor` (`RH.breakerCredits` and `RA.recurring` are turned into `paysFor` by the base classes) and
  spent by the payment engine of §8.2.
* currents: `rotOperationTriggers` trashes (`TrashLingeringEffect`, idempotent) every other current when a current is
  played, Corp currents on a steal and Runner currents on a score. This covers any card whose implementation has
  `staysInPlay` and the Current subtype. `RE.lingering` handles only the other durations.
* secret spending (`SecretSpendEffect`, events), damage choice and tag avoidance (`RH.damageChoice` / `RH.avoidTags`,
  hardware), and trace-margin measuring (`MarginTraceEffect` in operations, `RememberCreditsEffect` / `TraceKickerEffect` in
  ice; the Runner's link strength in the last trace, boost included, is `Game.fxTraceLinkStrength`) each exist only once.

### 8.2 Credits and costs (`rules-ext-credits.scala`, trait `CreditRules`)

Every credit payment has a purpose, `Spend(kind, card, target, server)`: `Spend.play(c)`, `install(c, server, host)`, `rez(c)`,
`advance(c)`, `trash(c)` (a trash cost), `trashResource(c)`, `removeTag`, `trace`, `psi`, `ability(c)`, `break(b, ice)`,
`pump(b, ice)`, `run(server)` (additional run costs), `corpAbility` ("end the run unless the Runner pays N"), `effect(source)`,
`other`. Pay with `g.spendCredits(f, n, spend, stealth = 0)`; check with `g.canSpend(f, n, spend, stealth)` / `g.creditsFor(f, spend)`;
inside effect lists use `SpendEffect(f, n, spend, stealth, then)`. `canPay` / `payCosts` take an optional `spend` (default:
the ability of `self`); the old `payCredits(f, n, self, purpose)` / `availableCredits` still work (`Spend.legacy`).

Credit sources: the pool (unless `poolLocked`: Aircheck), bad publicity credits of the run (`Run.credits`, set by
`BadPublicityCreditsEffect` when the run begins), and the hosted credits of every active card whose `paysFor(self, spend)`
(or `creditsUsableForRun` during a run, or `creditsUsableFor(self, spend.purpose)`) accepts the payment. The run flag
`no-spend-credits` (RSVP) blocks all of them. Default payment order (deterministic; the player may choose the sources instead when two or more restricted sources could pay, 8.7): the stealth part from Stealth sources,
bad publicity credits, credits on cards in the play area, recurring credits, other hosted credits (non-stealth before stealth),
the pool; while a card that needs stealth credits is active (`isStealthConsumer`) the unrequired stealth credits are spent after
the pool. Spending hosted credits fires `CreditsSpentFrom(card, n)`, bad publicity credits `BadPublicityCreditsSpent(n)`,
`SpendEffect` fires `CreditsPaid(f, n, spend)`.

Costs: `removeTagCost`, `trashResourceCost` (SYNC, Wireless Net Pavilion), `breakCostsWith` / `pumpCostsWith` (icebreaker
surcharges: `CardImpl.icebreakerSurcharge` + run flags `break-surcharge`), `hostedInstallCost(c, host)`, `installParts` /
`canSpendAll` / `spendAll` for "install and rez paying n less" (install part and rez part paid with their own Spends).


### 8.3 Restrictions (`rules-ext-restrictions.scala`)

`RestrictionRules` (mixed in after `CreditRules`, see 8.6; its enforcement does not depend on the position) holds one legality check per kind of
action, used by every path: `corpInstallBlock(card, server)` / `runnerInstallBlock(card, host)` (install locks, Unscheduled
Maintenance, remote limits from `CardImpl.remoteLimit`, printed server restrictions parsed from the card text by
`InstallRules.serverRule`, `RAg.installOnly`, `canInstall`, Apex, the locked heap), `rezBlock(card, ignoreCost)` (`canRez` --
skipped when all costs are ignored unless the text says "Rez only" --, "cannot-rez" markers, `rhRezBlocked`, Blackmail / Cyber
Threat, Interdiction, Hacktivist Meeting), `playBlock(card)` (The News Now Hour, the locked heap) and `programUseBlocked(program)`
(run flags `no-program-use` / `cannot-use`). New restrictions belong there (or in a card hook they read), not in a menu.

* `performRestrictions` is first in the `performInternal` chain and is defined only for the cases it refuses or rewrites:
  `CorpInstallEffect` / `RunnerInstallEffect` and the effects that pay before installing or rezzing (paying / discounted installs,
  install-and-rez, `DiscountRezEffect`, `RezDiscountEffect`) refuse illegal targets before any payment; region / asset / agenda
  overinstalls trash the previous card and keep the server; plays of a blocked card are refused.
* `restrictContinue` post-processes the result of every `performInternal` call: offered options that are illegal are greyed out
  with the reason (`restrictOption`), an Ask left without a usable option gets `RestrictedSkipAction` ("Continue"), Always Be
  Running's first-click rule is applied, and a Corp win other than by flatline is cancelled while The Black File is active.
* `rezCard` is overridden (stackable, runs before `RotAssetRules.rezCard`): the universal rez sink refuses illegal rezzes and
  charges Hacktivist Meeting's random trash.
* `Game.advancement(c)` adds `advancementBonus(c)` (Satellite Grid); effects that remove or move counters read
  `state(c).advancement`. `Game.agendaPoints` sums `agendaValue(c)` (printed + own + hosted `agendaPointsBonus`); use
  `agendaValue` whenever a card refers to the agenda points of an agenda in a score area. `Game.moveTo` ignores moves out of a
  heap locked by Blacklist (`heapLocked`).
* `DeckRules` (object) is the per-identity part of `Decks.validate` and of the deck preview: copy limits (draft / singleton
  identities), allowed cards (agenda factions, Custom Biotics), influence (The Professor, alliance cards, unlimited when the
  identity has no influence limit).


### 8.4 Prevention, interrupts and timing windows (`rules-ext-prevention.scala`)

`PreventionRules` is mixed in last (outermost, see 8.6), so its stackable overrides (`performEffects`, `performRun`, `performAbilities`,
`performTurn`, `performCards`, `performManual`, `performAccess`, `trashCard`, `payCosts`, `resolvePurpose`) run before all
others and delegate everything else to `super`. Modelled on jinteki's `game.core.prevention`:

* A **window** is a `PreventCtx` on `preventStack` (kind, amount left, cards, source, `by`, `firstThisTurn` / `firstThisRun`,
  uses). It opens when something preventable would happen: damage (`DamageEffect` -> the rules below adjust the amount ->
  `PreventOpenDamageAction`), tags (`TagsEffect`, after the hardware rules' automatic avoidance), bad publicity
  (`BadPubEffect` n > 0), end the run (`EndRunEffect` during a run; `preventSubs` knows whether a subroutine ends it), jack out
  (`JackOutAction` / `JackOutEffect` when a Corp card can prevent it), trash (`trashCard` of an installed Runner card, not as a
  cost (`payCosts`, `TrashInstalledCost`) nor by the game rules (memory limit, uniqueness, console limit, manual), when a card
  can prevent it; the trashes of one effect are batched), the "when encountered" ability of ice (`encounterAbility(ice)` ->
  `EncounterAbilityEffect`), and breaking a subroutine on bioroid ice protecting a server with Tyr's Hand ("break").
* **Forced interrupts** resolve first: the event `PreventionInterrupt(kind, stage, n)` goes to the cards' `triggers` (stage 0:
  increases, e.g. Defective Brainchips; stage 1: constant / forced preventions, e.g. Leverage, The Noble Path, Paparazzi,
  Guru Davinder, Muresh Bodysuit, Chrome Parlor, Shred, Broadcast Square). `kind` is `"net"` / `"meat"` / `"core"` for damage.
* Then **priority**: the active player first; a player keeps using `preventions` abilities until they pass
  (`PreventPassAction`), the window closes when both passed in a row or nothing is left (`PreventStepAction`, `Reaskable`).
  A used ability (`PreventUseAction`) pays its costs, resolves its effects and returns to the window. Abilities may be used
  any number of times (cards with a printed limit check `g.preventUses(self)` or the window flags; cap 20 per window).
* Effects for cards: `PreventEffect(kind, n)` (n < 0 = all), `PreventBoostEffect`, `PreventConvertDamageEffect(dtype, n)`
  (Tori Hanzō), `PreventTrashOfEffect(card)`, `PreventTrashChooseEffect(self, kinds)`, `PreventBreakChooseEffect(self)`;
  helpers in `object Prevent` (`Prevent.ctx`, `Prevent.damageCtx`, `Prevent.damage(n)`, `Prevent.allTags`, ...).
  `SourcedDamageEffect(kind, n, card)` records the card doing the damage (Runner cards' "when you install" damage).
* After the window: damage -> `DamageApplyEffect`, tags -> `TagsApplyEffect`, bad publicity -> `BadPubNowEffect`, end the run
  -> `EndRunNowEffect`, jack out -> the jack out (or back to the paid ability window, `cannotJackOut` set), trash -> the
  remaining cards are trashed, encounter -> the ice's `onEncounter` effects, break -> the break action with the prevented
  subroutines left unbroken.
* Timing events added: `ClicksSpentOrLost(f, n)` (during runs), `AccessFinished(card)` (after each access),
  `BreachFinished(server)` (after the breach of a run, before it ends), `RunWouldSucceed(server)` (before the run is declared
  successful). `PersistentEffect(card, server)` keeps a trashed card's run-end trigger until the run ends (AMAZE Amusements);
  `BreachBonusEffect(n)` is a turn-lasting access bonus (Climactic Showdown). Patchwork is an interrupt of `PlayCardAction` /
  `RunnerInstallAction`.
* "When a trace is initiated" abilities (Flip Switch, Disrupter; Power Tap is a `TraceInitiated` trigger) are used in the "trace"
  window of `FinalNsgRules` (8.7). The legacy continuation `PreventWindowAction(kind, n)` (sets the amount left of an open damage
  window) still works.


### 8.5 Rules accuracy fixes (`rules-ext-fixes.scala`)

`FixRules` (rules-ext-fixes.scala) is mixed in between `RestrictionRules` and `PreventionRules` (see 8.6), so its stackable overrides
(`performRun`, `performAccess`, `performEffects`, `performAbilities`, `performTurn`, `performSU21`, `performPHTAI`, `performRWR`,
`resolveRWRPurpose`, `rezCard`, `scoreAgenda`, `payCosts`, `useAbility`, `subs`, `canBreakSub`, `canRunServer`, `checkWin`,
`isDisabled`, `extImpl`, `abilityOptions`, `windowOptions`) run before every override of the card-set traits (and after PreventionRules') and delegate the rest to `super`.
Its own effects are handled by `performFixes` (end of the `performInternal` chain) and its bookkeeping by `fixTriggers` (end
of `triggerEffects`). It provides, for any card:

* events `ClicksSpent(f, n, cause)` (basic actions, click abilities, bioroid "lose [click]" breaks, the extra click of a
  double card), `AgendaCounterSpent(card, n)`, `BreachEnded(server, stoleOrTrashed)`, `ClickLost` for every click loss, and
  `SubsBroken(ice, subs, ice)` for bioroid click-breaks / Endless EULA (the ice is given as the breaker);
* `fxFullyBroke(ice, subs, b)` / `fxAllBrokenBy(ice, b)` (every subroutine broken by `b`), `fxPrintedSubIndices(ice)`,
  `fxNoResolve` (subroutines that do not resolve, not broken), `fxSubOrder` (resolution order), `fxResolvingSubs` (the ice
  whose subroutine is resolving: an alias of `PreventionRules.preventSubs`), `fxSources` (the card whose paid ability is resolving), `fxRunnerResolving`;
* `fxPassedThisRun` / `fxPassedLastRun` (one entry per `PassIce`), `fxUsedThisRun` (programs used during the run),
  `fxTraceLinkStrength` (the Runner's link strength in the last trace), `fxRezIgnoredCost` (inside `rezCard`);
* hooks `CardImpl.hostSubtypes(self)` (subtypes of the host while hosted: Egret) and `CardImpl.clicksPerTurn(self, f)` (static
  allotted clicks), lingering flags that outlast their card (`no-run`, `no-draw`, `gyri` / `rot-valley` hand size,
  `mind-game-jack-out`, `fx-tracker`, `stick-poke`, `cannot-break-printed`), `FxLoseClicksAtTurnBeginsEffect(f, n)`,
  `FxAdditionalBadPubEffect(n)`.

See `STATUS-fix-misc.md` for the cards that use them.


### 8.6 Composition of the rules-accuracy traits (credits, restrictions, prevention, fixes)

    ... with RotAssetRules with RotAgendaRules with CreditRules with RestrictionRules with FixRules with PreventionRules with FinalNsgRules

Scala runs the last mixed-in override first, so for a method overridden by several of them the order of execution is
(FinalNsgRules, 8.7 ->) PreventionRules -> FixRules -> RestrictionRules -> RotAgendaRules -> RotAssetRules -> base. Why this order:

* **PreventionRules outermost.** Its overrides suspend an action and perform it again later through `performInternal` with a
  bypass flag: `PlayCardAction` / `RunnerInstallAction` (Patchwork interrupt, `patchworkBypass`), `BreakSubAction` /
  `BreakSubsAction` / `BioroidBreakSubAction` (Tyr's Hand's break window, `breakBypass`), `JackOutAction` / `JackOutEffect` (jack-out
  window, `jackOutBypass`), `SuccessfulRunAction` (`RunWouldSucceed`), `BreachEndAction` (`BreachFinished`). Everything inside sees
  the action once, when it really happens: FixRules' `ClicksSpent` for the basic actions, `fxUsedThisRun`, the bioroid
  `SubsBroken`, Mind Game's jack-out cost (paid only when the jack out is not prevented) and Aumakua's `BreachEnded` (after the
  `BreachFinished` interrupts such as Divide and Conquer's extra breaches). With FixRules outside, a Patchwork play would have fired
  `ClicksSpent` twice.
* **FixRules next.** Its overrides are bookkeeping and card rules around the base behaviour (`performRun`, `performAccess`,
  `performEffects`, `performAbilities`, `performTurn`, `performSU21`, `performPHTAI`, `performRWR`, `resolveRWRPurpose`,
  `rezCard`, `scoreAgenda` (a full copy of `TurnRules.scoreAgenda`, reading `agendaValue`), `payCosts`, `useAbility`, `subs`,
  `canBreakSub`, `canRunServer`, `checkWin`, `isDisabled`, `extImpl`, `abilityOptions`, `windowOptions`); each calls `super`.
  Where FixRules and PreventionRules both intercept an action (`LoseClicksEffect`, `FireSubAction`, `PlayCardAction`,
  `BreachEndAction`, `JackOutAction`, `payCosts`) both call their `base` / `super`, so both effects happen once.
* **RestrictionRules** does its work outside the override stack: `performRestrictions` is the first partial function of the
  `performInternal` chain (illegal installs / rezzes / plays / scores are refused before any window, interrupt or payment) and
  `restrictContinue` wraps the final result of every `performInternal` call, so prevention-window asks, FixRules' RSVP-greyed
  asks and `rhInnerPerform`'s re-entrant results are all filtered (the filter is idempotent). Its one stackable override,
  `rezCard`, only has to be above `RotAssetRules.rezCard`; FixRules' `rezCard` (which records `fxRezIgnoredCost`) runs first.
  Prevention windows also leave out abilities of programs blocked by `programUseBlocked`.
* **CreditRules** has no stackable override: payments go through `spendCredits` / `canSpend`, which every other trait calls.
  `payCosts(f, costs, self, spend)` is overridden by PreventionRules (trash costs, `ClicksSpentOrLost`) and FixRules
  (`AgendaCounterSpent`); both pass `spend` on.

Shared state with one owner: the resolving-subroutine stack is `PreventionRules.preventSubs` (`FixRules.fxResolvingSubs` is an
alias); the Runner's link strength in the last trace is `FixRules.fxTraceLinkStrength`; "the Runner cannot spend credits" is the
run flag `no-spend-credits` read by `CreditRules.noSpending` (FixRules greys the paying options with it); click events:
`ClicksSpentOrLost(f, n)` (prevention: costs paid and clicks lost during runs; Heinlein Grid), `ClicksSpent(f, n, cause)` (fixes:
clicks spent per action / ability, all turn; Jeeves, Sundew), `ClickLost(f, n)` (every click loss; Adjusted Chronotype) -- a card
must listen to only one of them for the same click. Breach events: `BreachFinished` (prevention: before the run ends; Divide and
Conquer) and `BreachEnded` (fixes: any breach, with "stole or trashed"; Aumakua).


### 8.7 Final rules-accuracy pass for the current sets (`rules-ext-final-nsg.scala`, `FinalNsgRules`)

`FinalNsgRules` is mixed in last (`... with FixRules with PreventionRules with FinalNsgRules`), so its stackable overrides run first,
and its partial function `performFinalNsg` is the second of the `performInternal` chain (right after `performRestrictions`). Like
PreventionRules it suspends an action and performs it again through `performInternal` afterwards (with a marker so it is not
suspended twice), so everything inside it -- PreventionRules' interrupts (Patchwork, Tyr's Hand), FixRules' bookkeeping, the
card-set rules -- sees the action once, when it really happens. Illegal actions are still refused first (`performRestrictions`) and
its asks are filtered by `restrictContinue` (which it extends with its own greying: `fnsFilter`). See `STATUS-fix-final-nsg.md`.

* **Choosing the credit sources.** When two or more restricted / hosted sources (hosted credits whose `paysFor` accepts the Spend,
  bad publicity credits of the run) could pay for a user action and the total exceeds the cost, the action is suspended and the
  payer chooses where the credits come from: `CreditAutoAction(f)` (first option; CreditRules' deterministic order, the only option
  bots take) or `CreditTakeAction(f, source, m)` (`CardCreditSrc(card)`, `BadPubCreditSrc`, `PoolCreditSrc`; `NotForBots`), one step
  at a time until the cost is covered ("the rest automatically" at any point). The chosen parts become a plan applied by
  `spendCredits` to the next payment with the same Spend kind and card; the rest follows the default order (the stealth part
  included). The payment of an action is predicted by `creditPaymentOf` (play, install, rez, advance, trash-resource, remove-tag,
  trash cost, break / pump, paid / runner / access abilities, steal costs, trace boosts, "pay N" choices: `ChoiceAction` / `YesAction`
  whose first effect is a `SpendEffect`). Patchwork's interrupt is asked first.
* **Sources of effects.** The effects of the cards' triggers (game.scala `triggerEffects`), "when encountered" abilities
  (`encounterAbility`), "when accessed" abilities, "when rezzed" abilities, paid abilities (`useAbility`) and prevention abilities are
  wrapped in `FnsSourceEffect(card, on)` markers; `effectSource` is the innermost card whose ability is resolving (falling back on
  `fxSources`, the resolving subroutine `preventSubs` and the operation / event being played). Credit losses fire
  `CreditsLostFrom(f, n, source)` after `CreditsLost` (GameNET listens to it).
* **Additional costs paid before the action.** `FnsExtraRezCost.extraRezCost(self)` (marker trait on the card object): the effects
  that pay the cost are queued before the rez in `rezCard` (and before the prepaid rez effects `RezDiscountEffect`,
  `DiscountRezEffect`, `CorpInstallPayingEffect(.., true)`, `ServerDiscountChosenAction(.., true)`, Reanimation Protocol), then the card
  is rezzed (`fnsRezCostPaid`: its `canRez` no longer applies); nothing when rezzed ignoring all costs. `FnsExtraScoreCost` likewise
  for `ScoreAction` / `ScoreAgendaEffect`. The Corp's basic "trash a resource" action can carry "the Corp must trash N cards from HQ"
  (`trashResourceHQCost`). Costs that need the queue (random cards) are `ExtCost`s (`RandomToBottomCost(n)`): `canPay` / `Cost.desc`
  know them (game.scala), steal costs / trash costs containing them are paid before `StealAction` / `PayTrashAction` (`resumeWith`),
  and `payCosts` leaves them out.
* **Traces.** `TraceEffect` sets the trace up (base strength, the rotated agendas' bonus), triggers `TraceInitiated(base)` and opens a
  prevention-style window of kind `"trace"` (both players, priority, `preventions(self, "trace", 1)`: Flip Switch / Disrupter's
  `FlipSwitchTraceAction` sets the base strength to 0 and closes it); `FnsTraceBoostAction` then starts the Corp's boost.
* **Root moves and swaps** (`MoveToRootEffect`, `SwapRootEffect`, `SwapInstalledWithHandEffect`): refused (and greyed in
  `MoveServerChosenAction`) when an asset / agenda would go to a central root or a printed server restriction forbids it; afterwards
  the destination keeps one asset / agenda and one region (the card that was there is trashed, like an overinstall).
* **Breaking a chosen subset.** A break ability that breaks several subroutines (`n == 0` or `n >= 2`) gets a soft
  `BreakChooseSubsAction` when more than two subroutines can be broken: a multi-select (`XXSelectObjectsAction` of `SubPick`s) whose
  leaf is an ordinary `BreakSubsAction` with the chosen subroutines (greyed when the scaled cost cannot be paid or `canBreakSub`
  refuses it).
* Other helpers: `SpendClicksEffect(f, n)` ("spend [click]" choices are click costs: `ClicksSpent`, `ClicksSpentOrLost`, not
  `ClickLost`), `PayIfAbleEffect(f, n, spend, otherwise)` ("must pay N if able"), `fnsDoubleCost` / `fnsDoubleCanPlay` (double
  operations of the non-Startup sets, Synchrocyclotron), `gripCards` (the grip plus the cards hosted on Bling, played / installed
  as if they were in the grip), `fnsTrashInfo` (installed / rezzed / zone of a card when it was trashed: `fnsWasInstalled`,
  `fnsWasRezzed`, `fnsTrashedFrom`), `fnsInstallSpent` (credits were spent on a card's install: Bling), register
  `fns-runner-trashed-own-installed` (Boi-tata), `canBreakSub` for Unsmiling Tsarevna's printed subroutines, Bankhar's replacement
  at resolution (`FireSubAction`), `RunEffect` honours run restrictions (Front Company).

