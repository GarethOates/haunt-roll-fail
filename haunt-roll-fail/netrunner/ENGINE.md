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
* **Paid ability windows** (`PaidWindowAction(kind)`): kinds `turn-begins`, `run-initiated`, `approach-ice`,
  `movement` (after passing ice: jack out / continue), `approach-server`. Both players get a `MultiAsk` with their
  non-click abilities (and rez options for the Corp; the approached ice can only be rezzed in `approach-ice`).
  The encounter has its own ask (`EncounterAction`) with break / pump / ability / continue options.
* Every `Ask` produced by a `Reaskable` action automatically gets a `ManualAction` entry (manual adjustments).
  After a manual change the engine re-performs the stored `current` action to regenerate the ask, so Reaskable
  actions must be pure (no state change when performed again).

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
| `creditsUsableForRun(self)` | hosted credit counters can pay for things during a run (Overclock) |
| `advanceable(self)` | can be advanced although not an agenda (Urtica Cipher, Clearinghouse, Pharos) |
| `initialCounters(self)` | counters placed when the card becomes active (runner install / corp rez / event play): `Map("credit" -> 9)` |
| `mustBeHosted(self)`, `hostRequirement(self, host)` | trojans: must be installed on a host satisfying the requirement |
| `canBeTrashedOnAccess(self)`, `accessCostPaidByRunner(self)` | access restrictions / additional steal costs |
| `triggers(self, event)` | **triggered abilities**: return the effects to queue for this event (see §3) |
| `abilities(self)` | **paid abilities** (click abilities included): `$[Ability]` |
| `runnerAbilities(self)` | abilities the Runner may use on a Corp card (bioroid "lose [click]: break") |
| `accessAbility(self, target)` | ability usable while accessing `target` (Carnivore) |
| `preventions(self, kind, n)` | prevention abilities offered before damage (`"net"`, `"meat"`, `"core"`) or `"tag"` is applied |
| `subroutines(self)` | ice subroutines `$[Sub(label, effect)]`, in order |
| `onEncounter(self)` | "when the Runner encounters this ice" effects (Funhouse) |
| `breaks(self)` | icebreaker break abilities `BreakAbility(costs, n, subtypes, label, after)`; `n = 0` = any number, empty subtypes = any ice |
| `pumps(self)` | `Pump(costs, strength, duration, label)` |
| `onPlay(self)` | operation/event effects; `makesRun(self)` = true keeps the card active in `PlayZone` until its run ends |
| `onScore`, `onSteal`, `onAccess`, `onRez`, `onInstall`, `onTrash(self, by)`, `onEncounterEnds` | lifecycle hooks returning effects |
| `perform(a : CardAction)` | handles the card's own `CardAction`s (see §4) |
| `chosen(p : CardPurpose, cards)` | completion of a card choice made with a `CardPurpose` (see §4) |

`Ability(label, costs, effects, click = false, once = None, ok = true, reason = "")`:
`costs` is a list of `Cost` (`CreditCost(n)`, `ClickCost(n)`, `TrashSelfCost`, `VirusCost(n)` (hosted virus
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
  prevention window), `TagsEffect(n)`, `RemoveTagsEffect(n)`, `BadPubEffect(n)`.
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
| `:interactions {:pay-credits ...}` | `creditsUsableForRun` (run credits only) |
| `trojan` | `mustBeHosted` + `hostRequirement` |

## 7. Checklist for a new card

1. Find the `(defcard "Title" ...)` in the Clojure sources and the tests in `test/clj/game/cards/*.clj`.
2. Write `object X extends SG("code")` overriding the relevant hooks; keep log messages ASCII; never put card
   titles into action fields (use `Card`).
3. If the card needs a decision at resolution time, add a `CardAction`/`CardPurpose` and implement `perform`/`chosen`.
4. Add the object to the set's `all` list; `sbt -batch compile`.
5. Test with the JVM harness (`netrunner/host.scala`, see STATUS.md) and in the browser.
