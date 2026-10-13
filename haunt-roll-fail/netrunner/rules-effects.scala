package netrunner
//
//
//
//
import hrf.colmat._
import hrf.logger._
//
//
//
//

import hrf.elem._
import netrunner.elem._


// ---------------------------------------------------------------------------------------------------------------------
// Generic effects (pending queue items)
// ---------------------------------------------------------------------------------------------------------------------

case class GainCreditsEffect(f : Faction, n : Int) extends ForcedAction
case class LoseCreditsEffect(f : Faction, n : Int) extends ForcedAction
case class DrawEffect(f : Faction, n : Int) extends ForcedAction
case class GainClicksEffect(f : Faction, n : Int) extends ForcedAction
case class LoseClicksEffect(f : Faction, n : Int) extends ForcedAction
case class DamageEffect(kind : String, n : Int) extends ForcedAction
case class DamageApplyEffect(kind : String, n : Int) extends ForcedAction
case class DamageDiscardAction(kind : String, shuffled : $[Card]) extends ShuffledAction[Card]
case class TagsEffect(n : Int) extends ForcedAction
case class TagsApplyEffect(n : Int) extends ForcedAction
case class RemoveTagsEffect(n : Int) extends ForcedAction
case class BadPubEffect(n : Int) extends ForcedAction
case object EndRunEffect extends ForcedAction
case object JackOutOfferEffect extends ForcedAction
case class TrashEffect(card : Card, by : Faction, accessed : Boolean) extends ForcedAction
case class TrashCardsEffect(cards : $[Card], by : Faction) extends ForcedAction
case class DiscardFromHandEffect(f : Faction, card : Card) extends ForcedAction
case class AddCounterEffect(card : Card, kind : String, n : Int) extends ForcedAction
case class PlaceAdvancementEffect(card : Card, n : Int) extends ForcedAction
case class MoveEffect(card : Card, zone : Zone, top : Boolean) extends ForcedAction
case class ShuffleDeckEffect(f : Faction) extends ForcedAction
case class ShuffledDeckAction(f : Faction, shuffled : $[Card]) extends ShuffledAction[Card]
case class RezEffect(card : Card, ignoreCost : Boolean) extends ForcedAction
case class DerezEffect(card : Card) extends ForcedAction
case class ChooseCardEffect(f : Faction, candidates : $[Card], purpose : Purpose, prompt : String, optional : Boolean) extends ForcedAction with Reaskable
case class ChooseCardsEffect(f : Faction, candidates : $[Card], min : Int, max : Int, purpose : Purpose, prompt : String) extends ForcedAction
case class CardChosenAction(self : Faction, card : Card, purpose : Purpose) extends BaseAction()(g => g.cardElem(card))
case class CardsChosenAction(self : Faction, cards : $[Card], purpose : Purpose) extends ForcedAction
case class DeclineChoiceAction(self : Faction, purpose : Purpose) extends BaseAction()("Decline")
case class OptionalEffect(f : Faction, prompt : String, yes : $[ForcedAction], no : $[ForcedAction]) extends ForcedAction with Reaskable
case class YesAction(self : Faction, prompt : String, yes : $[ForcedAction]) extends BaseAction(prompt)("Yes")
case class NoAction(self : Faction, prompt : String, no : $[ForcedAction]) extends BaseAction(prompt)("No")
case class Alt(label : String, effects : $[ForcedAction], ok : Boolean = true) extends Record
case class ChooseOneEffect(f : Faction, prompt : String, choices : $[Alt]) extends ForcedAction with Reaskable
case class ChoiceAction(self : Faction, prompt : String, choice : Alt) extends BaseAction(prompt)(choice.label)
case class MessageEffect(f : Faction, text : String) extends ForcedAction
case object PurgeEffect extends ForcedAction
case class MillEffect(f : Faction, n : Int) extends ForcedAction
case class RunEffect(server : Server, source : |[Card]) extends ForcedAction
case class RunAnyServerEffect(source : |[Card], servers : $[Server]) extends ForcedAction with Reaskable
case class RunChoiceAction(self : Faction, server : Server, source : |[Card]) extends BaseAction("Run")(server)
case class AccessBonusEffect(server : Server, n : Int) extends ForcedAction
case object CannotStealOrTrashEffect extends ForcedAction
case class PumpIceEffect(ice : Card, n : Int, duration : Duration) extends ForcedAction
case class PumpBreakerEffect(card : Card, n : Int, duration : Duration) extends ForcedAction
case class RegisterEffect(key : String, n : Int) extends ForcedAction
case class MarkUsedEffect(card : Card, key : String) extends ForcedAction
case class RemoveFromGameEffect(card : Card) extends ForcedAction
case class RevealEffect(f : Faction, card : Card) extends ForcedAction
case class SwapIceEffect(a : Card, b : Card) extends ForcedAction
case class AddToHandEffect(card : Card) extends ForcedAction
case class TakeCreditsEffect(f : Faction, card : Card, n : Int) extends ForcedAction
case class SetFlagEffect(key : String) extends ForcedAction
case class TraceEffect(base : Int, success : $[ForcedAction], fail : $[ForcedAction]) extends ForcedAction
case class TraceCorpAction(self : Faction, n : Int) extends BaseAction("Boost trace strength")(n)
case class TraceRunnerAction(self : Faction, n : Int) extends BaseAction("Boost link")(n)
case class TraceCorpBoostAction(base : Int, success : $[ForcedAction], fail : $[ForcedAction]) extends ForcedAction with Reaskable
case class TraceRunnerBoostAction(strength : Int, success : $[ForcedAction], fail : $[ForcedAction]) extends ForcedAction with Reaskable
case class PsiEffect(equal : $[ForcedAction], differ : $[ForcedAction]) extends ForcedAction with Reaskable
case class PsiBidAction(self : Faction, n : Int) extends BaseAction("Secretly bid")(n.hl ~ " credits")
case class PsiRunnerBidAction(corp : Int, equal : $[ForcedAction], differ : $[ForcedAction]) extends ForcedAction with Reaskable
case class PsiResolveAction(corp : Int, runner : Int, equal : $[ForcedAction], differ : $[ForcedAction]) extends ForcedAction
case class PreventWindowAction(kind : String, n : Int) extends ForcedAction with Reaskable
case class PreventPassAction(self : Faction, kind : String, n : Int) extends BaseAction("Prevent " ~ kind ~ "?")("Take it")
case class PreventUseAction(self : Faction, card : Card, idx : Int, kind : String, n : Int) extends BaseAction("Prevent " ~ kind ~ "?")(g => g.cardElem(card) ~ ": " ~ g.impl(card).preventions(card, kind, n)(g).lift(idx)./(_.label).|("?"))
case class CheckWinAction() extends ForcedAction
case class ShuffleFromArchivesEffect(n : Int) extends ForcedAction
case class InstallChooseServerPurpose(ignoreCost : Boolean) extends Purpose

// purposes for generic card choices
case class TrashPurpose(by : Faction) extends Purpose
case class AddToHandPurpose(f : Faction, reveal : Boolean) extends Purpose
case class ShuffleIntoDeckPurpose(f : Faction) extends Purpose
case class InstallPurpose(ignoreCost : Boolean, server : |[Server]) extends Purpose
case class AdvancePurpose(n : Int) extends Purpose
case class RezPurpose(ignoreCost : Boolean) extends Purpose
case class DiscardPurpose(f : Faction) extends Purpose
case class ShuffleFromArchivesPurpose() extends Purpose
case class DamagePickPurpose(kind : String) extends Purpose
case class HostInstallPurpose(card : Card, ignoreCost : Boolean) extends Purpose


trait EffectRules { game : Game =>

    def performEffects : PartialFunction[Action, Continue] = {
        case GainCreditsEffect(f, n) =>
            if (n > 0) {
                credits += f -> (credits(f) + n)
                log(f, "gains", n.credits)
            }
            NextAction

        case LoseCreditsEffect(f, n) =>
            val m = n.atMost(credits(f))
            credits += f -> (credits(f) - m)
            log(f, "loses", m.credits)
            NextAction

        case DrawEffect(f, n) =>
            val d = deck(f)
            if (f.isCorp && d.num < n) {
                log(Corp, "cannot draw from an empty R&D")
                GameOverAction(|(Runner), "Corp has no cards left in R&D")
            }
            else {
                val l = d.take(n)
                l.foreach(c => moveTo(c, HandZone(f)))
                if (l.any) {
                    log(f, "draws", l.num.cards)
                    regInc("drawn-" + f.name, l.num)
                }
                NextAction
            }

        case GainClicksEffect(f, n) =>
            clicks += f -> (clicks(f) + n)
            log(f, "gains", n.clicks)
            NextAction

        case LoseClicksEffect(f, n) =>
            val m = n.atMost(clicks(f))
            clicks += f -> (clicks(f) - m)
            if (m > 0)
                log(f, "loses", m.clicks)
            NextAction

        case DamageEffect(kind, n) =>
            if (n <= 0)
                NextAction
            else {
                push(PreventWindowAction(kind, n))
                NextAction
            }

        case PreventWindowAction(kind, n) =>
            val l = activeCards(Runner)./~(c => impl(c).preventions(c, kind, n).indexed./((a, i) => (c, i, a)))
            if (l.none) {
                push(DamageApplyEffect(kind, n))
                NextAction
            }
            else
                Ask(Runner).add(l./{ case (c, i, a) => PreventUseAction(Runner, c, i, kind, n).!(!a.ok || !canPay(Runner, a.costs, |(c)), a.reason) }).add(PreventPassAction(Runner, kind, n))

        case PreventPassAction(f, kind, n) =>
            push(DamageApplyEffect(kind, n))
            NextAction

        case PreventUseAction(f, c, i, kind, n) =>
            val a = impl(c).preventions(c, kind, n).apply(i)
            payCosts(f, a.costs, |(c))
            log(f, "uses", c, "to prevent", kind, "damage")
            pushAll(a.effects)
            NextAction

        case DamageApplyEffect(kind, n) =>
            if (n <= 0)
                NextAction
            else {
                val grip = hand(Runner)
                log(Runner, "suffers", n.hl, kind, "damage")
                runnerDamageThisTurn += n
                if (kind == "core")
                    coreDamage += n
                if (n > grip.num) {
                    grip.foreach(c => moveTo(c, DiscardZone(Runner)))
                    GameOverAction(|(Corp), "Runner is flatlined")
                }
                else
                    ShuffleTake[Card](grip, n, DamageDiscardAction(kind, _), "Damage")
            }

        case DamageDiscardAction(kind, l) =>
            l.foreach(c => moveTo(c, DiscardZone(Runner)))
            lastDamaged = l
            log(Runner, "trashes", l.num.cards, "from the grip", "(" ~ l./(cardElem).comma ~ ")")
            trigger(DamageDone(kind, l.num))
            push(CheckWinAction())
            NextAction

        case TagsEffect(n) =>
            if (n <= 0)
                NextAction
            else {
                val l = activeCards(Runner)./~(c => impl(c).preventions(c, "tag", n).indexed./((a, i) => (c, i, a)))
                if (l.none) {
                    push(TagsApplyEffect(n))
                    NextAction
                }
                else
                    Ask(Runner).add(l./{ case (c, i, a) => PreventUseAction(Runner, c, i, "tag", n).!(!a.ok || !canPay(Runner, a.costs, |(c)), a.reason) }).add(PreventPassAction(Runner, "tag", n))
            }

        case TagsApplyEffect(n) =>
            tags += n
            regInc("tags-gained", n)
            log(Runner, "takes", n.tags)
            trigger(TagsGained(n))
            NextAction

        case RemoveTagsEffect(n) =>
            val m = n.atMost(tags)
            tags -= m
            log(Runner, "removes", m.tags)
            NextAction

        case BadPubEffect(n) =>
            badPublicity = (badPublicity + n).atLeast(0)
            log(Corp, (n > 0).?("takes").|("removes"), n.abs.hl, "bad publicity")
            NextAction

        case EndRunEffect =>
            run.foreach { r =>
                if (!r.ended) {
                    r.ended = true
                    r.unsuccessful = !r.successful
                    log("The run ends")
                    flushRun()
                    push(RunEndsAction())
                }
            }
            NextAction

        case JackOutOfferEffect =>
            if (run.exists(r => !r.ended && !r.cannotJackOut))
                Ask(Runner).add(JackOutAction(Runner)).add(ContinueRunAction(Runner).as("Continue the run"))
            else
                NextAction

        case TrashEffect(c, by, accessed) =>
            trashCard(c, by, accessed)
            NextAction

        case TrashCardsEffect(l, by) =>
            l.foreach(c => trashCard(c, by, false))
            NextAction

        case DiscardFromHandEffect(f, c) =>
            if (f.isCorp)
                toArchives(c, false)
            else
                moveTo(c, DiscardZone(Runner))
            log(f, "discards", (f.isRunner).?(cardElem(c)).|("a card".hl))
            NextAction

        case AddCounterEffect(c, k, n) =>
            addCounter(c, k, n)
            if (n > 0)
                log(n.hl, k, "counter" + (n != 1).??("s"), "placed on", c)
            else
                log((-n).hl, k, "counter" + (n != -1).??("s"), "removed from", c)
            trigger(CounterAdded(c, k))
            NextAction

        case PlaceAdvancementEffect(c, n) =>
            addCounter(c, "advancement", n)
            log(n.hl, "advancement counter" + (n != 1).??("s"), "placed on", hiddenCorpElem(c))
            NextAction

        case MoveEffect(c, z, top) =>
            moveTo(c, z, top)
            NextAction

        case ShuffleDeckEffect(f) =>
            Shuffle[Card](deck(f), ShuffledDeckAction(f, _), "Shuffle " + f.name)

        case ShuffledDeckAction(f, l) =>
            zones += DeckZone(f) -> l
            log(f, "shuffles", f.isCorp.?("R&D").|("the stack"))
            NextAction

        case RezEffect(c, ignoreCost) =>
            rezCard(c, ignoreCost)
            NextAction

        case DerezEffect(c) =>
            if (isRezzed(c)) {
                state(c).rezzed = false
                log(c, "is derezzed")
                trigger(Derezzed(c))
            }
            NextAction

        case ChooseCardEffect(f, l, p, prompt, optional) =>
            val ll = l.%(c => location.contains(c))
            if (ll.none)
                NextAction
            else if (ll.num == 1 && !optional) {
                push(CardsChosenAction(f, ll, p))
                NextAction
            }
            else
                Ask(f).group(prompt).add(ll./(c => CardChosenAction(f, c, p))).when(optional)(DeclineChoiceAction(f, p))

        case CardChosenAction(f, c, p) =>
            push(CardsChosenAction(f, $(c), p))
            NextAction

        case DeclineChoiceAction(f, p) =>
            log(f, "declines")
            NextAction

        case ChooseCardsEffect(f, l, min, max, p, prompt) =>
            val ll = l.%(c => location.contains(c))
            if (ll.none || max == 0) {
                push(CardsChosenAction(f, $, p))
                NextAction
            }
            else
                XXSelectObjectsAction(f, ll)
                    .withGroup(prompt)
                    .withRule(_.upTo(max.atMost(ll.num)).atLeast(min.atMost(ll.num)))
                    .withThenElem(CardsChosenAction(f, _, p))("Done".hl)

        case CardsChosenAction(f, l, p) =>
            resolvePurpose(f, l, p)

        case OptionalEffect(f, prompt, yes, no) =>
            Ask(f).add(YesAction(f, prompt, yes)).add(NoAction(f, prompt, no))

        case YesAction(f, prompt, yes) =>
            pushAll(yes)
            NextAction

        case NoAction(f, prompt, no) =>
            pushAll(no)
            NextAction

        case ChooseOneEffect(f, prompt, l) =>
            if (l.%(_.ok).none)
                NextAction
            else
                Ask(f).add(l./(ch => ChoiceAction(f, prompt, ch).!(!ch.ok)))

        case ChoiceAction(f, prompt, ch) =>
            log(f, "chooses", ch.label.hl)
            pushAll(ch.effects)
            NextAction

        case MessageEffect(f, text) =>
            log(f, text)
            NextAction

        case PurgeEffect =>
            var n = 0
            (installedCards(Runner) ++ installedCards(Corp)).foreach { c =>
                val v = counter(c, "virus")
                if (v > 0) {
                    n += v
                    state(c).counters += "virus" -> 0
                }
            }
            log(Corp, "purges", n.hl, "virus counters")
            trigger(Purged(n))
            NextAction

        case MillEffect(f, n) =>
            val l = deck(f).take(n)
            l.foreach { c =>
                if (f.isCorp) toArchives(c, false) else moveTo(c, DiscardZone(f))
            }
            log(l.num.cards, "milled from", f.isCorp.?("R&D").|("the stack"))
            NextAction

        case RunEffect(s, source) =>
            startRun(s, source, false)

        case RunAnyServerEffect(source, l) =>
            val ll = l.%(canRunOn)
            if (ll.none)
                NextAction
            else
                Ask(Runner).add(ll./(s => RunChoiceAction(Runner, s, source)))

        case RunChoiceAction(f, s, source) =>
            startRun(s, source, false)

        case AccessBonusEffect(s, n) =>
            run.foreach { r =>
                r.accessBonus += s.name -> (r.accessBonusFor(s.name) + n)
            }
            NextAction

        case CannotStealOrTrashEffect =>
            run.foreach(_.cannotStealOrTrash = true)
            log("The Runner cannot steal or trash Corp cards for the remainder of the run")
            NextAction

        case PumpIceEffect(c, n, d) =>
            val s = state(c)
            d match {
                case EncounterDuration => s.strengthEncounter += n
                case RunDuration => s.strengthRun += n
                case TurnDuration => s.strengthTurn += n
            }
            log(c, "strength is now", iceStrength(c).hl)
            NextAction

        case PumpBreakerEffect(c, n, d) =>
            val s = state(c)
            d match {
                case EncounterDuration => s.strengthEncounter += n
                case RunDuration => s.strengthRun += n
                case TurnDuration => s.strengthTurn += n
            }
            log(c, "strength is now", breakerStrength(c).hl)
            NextAction

        case RegisterEffect(k, n) =>
            regInc(k, n)
            NextAction

        case MarkUsedEffect(c, k) =>
            markUsed(c, k)
            NextAction

        case RemoveFromGameEffect(c) =>
            moveTo(c, RemovedZone(owner(c)))
            log(c, "is removed from the game")
            NextAction

        case RevealEffect(f, c) =>
            log(f, "reveals", c)
            state(c).seen = true
            NextAction

        case SwapIceEffect(a, b) =>
            val za = zone(a)
            val zb = zone(b)
            (za, zb) match {
                case (IceZone(sa), IceZone(sb)) =>
                    val la = ice(sa)
                    val lb = ice(sb)
                    if (sa == sb) {
                        val ia = la.indexOf(a)
                        val ib = la.indexOf(b)
                        zones += IceZone(sa) -> la.updated(ia, b).updated(ib, a)
                    }
                    else {
                        zones += IceZone(sa) -> la.updated(la.indexOf(a), b)
                        zones += IceZone(sb) -> lb.updated(lb.indexOf(b), a)
                        location += a -> IceZone(sb)
                        location += b -> IceZone(sa)
                    }
                    log("Swapped", a, "and", b)
                case _ =>
            }
            NextAction

        case AddToHandEffect(c) =>
            moveTo(c, HandZone(owner(c)))
            NextAction

        case TakeCreditsEffect(f, c, n) =>
            val m = n.atMost(counter(c, "credit"))
            if (m > 0) {
                addCounter(c, "credit", -m)
                credits += f -> (credits(f) + m)
                log(f, "takes", m.credits, "from", c)
                trigger(CounterAdded(c, "credit"))
            }
            NextAction

        case SetFlagEffect(k) =>
            k match {
                case "cannot-score" => cannotScoreThisTurn = true
                case _ => regInc(k)
            }
            NextAction

        case TraceEffect(base, success, fail) =>
            traceBase = base
            traceSuccess = success
            traceFail = fail
            log(Corp, "initiates a trace with base strength", base.hl)
            TraceCorpBoostAction(base, success, fail)

        case TraceCorpBoostAction(base, success, fail) =>
            Ask(Corp).group("Trace strength " ~ base.hl ~ ", boost by paying credits").add(0.to(credits(Corp)).$./(n => TraceCorpAction(Corp, n)))

        case TraceCorpAction(f, n) =>
            traceStrength = traceBase + n
            credits += Corp -> (credits(Corp) - n)
            log(Corp, "pays", n.credits, "for trace strength", traceStrength.hl)
            TraceRunnerBoostAction(traceStrength, traceSuccess, traceFail)

        case TraceRunnerBoostAction(strength, success, fail) =>
            Ask(Runner).group("Trace strength " ~ strength.hl ~ ", your link is " ~ link.hl ~ ", boost by paying credits").add(0.to(credits(Runner)).$./(n => TraceRunnerAction(Runner, n)))

        case TraceRunnerAction(f, n) =>
            credits += Runner -> (credits(Runner) - n)
            val l = link + n
            log(Runner, "pays", n.credits, "for link strength", l.hl)
            if (traceStrength > l) {
                log("The trace is", "successful".hl)
                pushAll(traceSuccess)
            }
            else {
                log("The trace is", "unsuccessful".hl)
                pushAll(traceFail)
            }
            NextAction

        case PsiEffect(equal, differ) =>
            psiEqual = equal
            psiDiffer = differ
            Ask(Corp).group("Psi game: secretly bid 0, 1 or 2 credits").add(0.to(2.atMost(credits(Corp))).$./(n => PsiBidAction(Corp, n)))

        case PsiBidAction(Corp, n) =>
            psiCorpBid = n
            log(Corp, "bids secretly")
            PsiRunnerBidAction(n, psiEqual, psiDiffer)

        case PsiRunnerBidAction(c, equal, differ) =>
            Ask(Runner).group("Psi game: secretly bid 0, 1 or 2 credits").add(0.to(2.atMost(credits(Runner))).$./(n => PsiBidAction(Runner, n)))

        case PsiBidAction(Runner, n) =>
            PsiResolveAction(psiCorpBid, n, psiEqual, psiDiffer)

        case PsiResolveAction(c, r, equal, differ) =>
            credits += Corp -> (credits(Corp) - c)
            credits += Runner -> (credits(Runner) - r)
            log(Corp, "bid", c.credits, "and", Runner, "bid", r.credits)
            if (c == r) pushAll(equal) else pushAll(differ)
            NextAction

        case CheckWinAction() =>
            checkWin()

        case ShuffleFromArchivesEffect(n) =>
            if (discard(Corp).any)
                push(ChooseCardsEffect(Corp, discard(Corp), 0, n, ShuffleIntoDeckPurpose(Corp), "Choose up to " + n + " cards in Archives to shuffle into R&D"))
            NextAction
    }

    var lastDamaged : $[Card] = $
    var traceBase = 0
    var traceStrength = 0
    var traceSuccess : $[ForcedAction] = $
    var traceFail : $[ForcedAction] = $
    var psiCorpBid = 0
    var psiEqual : $[ForcedAction] = $
    var psiDiffer : $[ForcedAction] = $

    def hiddenCorpElem(c : Card) : Elem = (isRezzed(c) || zone(c).is[ScoredZone] || state(c).seen).?(cardElem(c)).|("a card".hl)

    def checkWin() : Continue = {
        if (isOver)
            IdleAction
        else if (agendaPoints(Corp) >= pointsToWin)
            GameOverAction(|(Corp), "Corp scored " + agendaPoints(Corp) + " agenda points")
        else if (agendaPoints(Runner) >= pointsToWin)
            GameOverAction(|(Runner), "Runner stole " + agendaPoints(Runner) + " agenda points")
        else
            NextAction
    }

    def trashCard(c : Card, by : Faction, accessed : Boolean) {
        val wasInstalled = isInstalled(c)
        val hostedTrashed = wasInstalled.??(leavePlay(c))
        val from = zone(c)
        if (owner(c).isCorp)
            toArchives(c, true)
        else
            moveTo(c, DiscardZone(Runner))
        if (hostedTrashed.any)
            log("Hosted cards trashed:", hostedTrashed./(cardElem).comma)
        log(by, "trashes", cardElem(c))
        cleanupRemotes()
        if (by.isRunner) {
            regInc("runner-trash")
            if (accessed)
                regInc("trash-accessed")
            run.foreach(_.didTrash = true)
        }
        if (wasInstalled || from.is[HandZone] || from.is[DeckZone])
            pushAll(impl(c).onTrash(c, by))
        trigger(Trashed(c, by, accessed))
    }

    def rezCard(c : Card, ignoreCost : Boolean) {
        if (!isRezzed(c)) {
            val cost = ignoreCost.?(0).|(rezCost(c))
            credits += Corp -> (credits(Corp) - cost)
            state(c).rezzed = true
            state(c).rezzedTurn = turn
            state(c).seen = true
            impl(c).initialCounters(c).foreach { case (k, n) => addCounter(c, k, n) }
            log(Corp, "rezzes", c, ignoreCost.?("ignoring all costs").|("paying " ~ cost.credits))
            pushAll(impl(c).onRez(c))
            trigger(Rezzed(c))
        }
    }

    def canRunOn(s : Server) : Boolean = servers.has(s) && allActive.forall(c => true)

    def resolvePurpose(f : Faction, l : $[Card], p : Purpose) : Continue = p match {
        case TrashPurpose(by) =>
            l.foreach(c => trashCard(c, by, false))
            NextAction

        case AddToHandPurpose(ff, reveal) =>
            l.foreach { c =>
                if (reveal)
                    log(ff, "reveals", c, "and adds it to", ff.isCorp.?("HQ").|("the grip"))
                else
                    log(ff, "adds", (ff.isCorp && !state(c).seen).?("a card".hl).|(cardElem(c)), "to", ff.isCorp.?("HQ").|("the grip"))
                moveTo(c, HandZone(ff))
            }
            NextAction

        case ShuffleIntoDeckPurpose(ff) =>
            l.foreach(c => moveTo(c, DeckZone(ff)))
            if (l.any) {
                log(ff, "shuffles", l.num.cards, "into", ff.isCorp.?("R&D").|("the stack"))
                push(ShuffleDeckEffect(ff))
            }
            NextAction

        case InstallPurpose(ignoreCost, server) =>
            l.single match {
                case Some(c) if owner(c).isCorp =>
                    push(CorpInstallEffect(c, server, ignoreCost))
                    NextAction
                case Some(c) =>
                    push(RunnerInstallEffect(c, None, ignoreCost))
                    NextAction
                case None => NextAction
            }

        case AdvancePurpose(n) =>
            l.foreach(c => push(PlaceAdvancementEffect(c, n)))
            NextAction

        case RezPurpose(ignoreCost) =>
            l.foreach(c => push(RezEffect(c, ignoreCost)))
            NextAction

        case DiscardPurpose(ff) =>
            l.foreach(c => push(DiscardFromHandEffect(ff, c)))
            NextAction

        case InstallChooseServerPurpose(ignoreCost) =>
            l.single match {
                case Some(c) if c.data.isIce || c.data.isUpgrade =>
                    push(ChooseServerEffect(Corp, c, ignoreCost, servers, true))
                    NextAction
                case Some(c) =>
                    push(ChooseServerEffect(Corp, c, ignoreCost, remotes, true))
                    NextAction
                case None => NextAction
            }

        case HostInstallPurpose(c, ignoreCost) =>
            l.single match {
                case Some(h) =>
                    push(RunnerInstallEffect(c, |(h), ignoreCost))
                    NextAction
                case None => NextAction
            }

        case p : CardPurpose =>
            impl(p.card).chosen(p, l)

        case p =>
            throw new Error("unknown purpose " + p)
    }

    def payCosts(f : Faction, costs : $[Cost], self : |[Card]) {
        costs.foreach {
            case CreditCost(n) => payCredits(f, n, self)
            case ClickCost(n) => clicks += f -> (clicks(f) - n)
            case LoseClickCost(n) => clicks += f -> (clicks(f) - n)
            case TrashSelfCost => self.foreach(c => trashCard(c, f, false))
            case VirusCost(n) => self.foreach(c => addCounter(c, "virus", -n))
            case CounterCost(k, n) => self.foreach(c => addCounter(c, k, -n))
            case TrashFromHandCost(n) =>
            case RemoveSelfCost => self.foreach(c => moveTo(c, RemovedZone(f)))
            case TagCost(n) => tags += n
        }
    }

    // pay credits, using hosted run credits first if in a run
    def payCredits(f : Faction, n : Int, self : |[Card]) {
        var left = n
        if (f.isRunner && run.any) {
            creditSources(f).foreach { c =>
                val m = left.atMost(counter(c, "credit"))
                if (m > 0) {
                    addCounter(c, "credit", -m)
                    left -= m
                    log(Runner, "spends", m.credits, "from", c)
                    trigger(CounterAdded(c, "credit"))
                }
            }
        }
        credits += f -> (credits(f) - left)
    }
}
