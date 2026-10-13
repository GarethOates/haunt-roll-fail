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
// Generic mechanics added for the rwr / df / ur sets (see STATUS-rwr-df-ur.md)
// ---------------------------------------------------------------------------------------------------------------------

// ---- new events -----------------------------------------------------------------------------------------------------
case class EncounterEnds(ice : Card) extends Event                      // an encounter with ice ended (passed or forced encounter finished)
case class IceBypassed(ice : Card) extends Event                        // the Runner bypassed ice
case class PlayResolved(f : Faction, card : Card) extends Event         // an operation / event finished resolving
case class ExpendResolved(card : Card) extends Event                    // an expend ability (action on an expendable card) finished
case class Revealed(f : Faction, cards : $[Card]) extends Event         // f revealed cards
case class AbilityUsed(f : Faction, card : Card, click : Boolean) extends Event  // a paid ability of card was used
case class CreditsSpentFrom(card : Card, n : Int) extends Event         // hosted credits were spent / taken from card
case class CreditsLost(f : Faction, n : Int) extends Event              // f lost credits (LoseCreditsEffect)
case class ActionTaken(f : Faction, kind : String) extends Event        // a click action was taken (kind : credit, draw, install, advance, play, ...)
case class TagsRemoved(n : Int) extends Event                           // the Runner removed tags
case class Forfeited(card : Card) extends Event                         // an agenda was forfeited
case class DeckTrashed(f : Faction, cards : $[Card]) extends Event      // cards were trashed from the top of f's deck (TrashTopEffect)

// ---- new effects ----------------------------------------------------------------------------------------------------
case object ReplaceBreachEffect extends ForcedAction                                    // "instead of breaching": the current run does not breach
case object BlockSuccessEffect extends ForcedAction                                     // this run cannot be declared successful
case class ForceEncounterEffect(ice : Card) extends ForcedAction                        // the Runner encounters ice (outside the normal approach order)
case class RedirectRunEffect(position : Int, jackOut : Boolean) extends ForcedAction    // move the Runner to a position of the attacked server (number of ice = outermost)
case class RunIgnoringCostsEffect(server : Server, source : |[Card]) extends ForcedAction
case class RunnerInstallPayingEffect(card : Card, host : |[Card], discount : Int) extends ForcedAction        // install paying (cost - discount)
case class CorpInstallPayingEffect(card : Card, server : |[Server], discount : Int, rez : Boolean) extends ForcedAction  // corp install paying (cost - discount), optionally rezzing (discount applies to the total)
case class ChooseServerPayingEffect(card : Card, discount : Int, servers : $[Server], allowNew : Boolean, rez : Boolean) extends ForcedAction with Reaskable
case class ServerChosenPayingAction(self : Faction, card : Card, server : |[Server], discount : Int, rez : Boolean) extends BaseAction("Install")(g => g.cardElem(card) ~ " in " ~ server./(_.elem).|("a new remote server".hl))
case class RandomPickEffect(f : Faction, cards : $[Card], n : Int, purpose : Purpose) extends ForcedAction  // pick n random cards, then resolve purpose with them
case class RandomPickedAction(f : Faction, purpose : Purpose, shuffled : $[Card]) extends ShuffledAction[Card]
case class ChooseCodeEffect(f : Faction, codes : $[String], prompt : String, purpose : CardPurpose) extends ForcedAction with Reaskable  // name a card (by code) -- chosen(p, $(Card(code, 0)))
case class CodeChosenAction(self : Faction, code : String, purpose : CardPurpose) extends BaseAction()(g => Cards(code).title.styled(NRFaction.of(Cards(code).faction)))
case class LookAtEffect(f : Faction, cards : $[Card], prompt : String) extends ForcedAction with Reaskable   // show cards to f only
case class LookDoneAction(self : Faction, prompt : String) extends BaseAction()("OK")
case class ArrangeTopEffect(f : Faction, owner : Faction, n : Int) extends ForcedAction                  // f arranges the top n cards of owner's deck
case class ArrangeAction(f : Faction, owner : Faction, remaining : $[Card], chosen : $[Card]) extends ForcedAction with Reaskable
case class ArrangePickAction(self : Faction, owner : Faction, card : Card, remaining : $[Card], chosen : $[Card]) extends BaseAction("Choose the next card from the top")(g => g.cardElem(card))
case class MarkUsedRunEffect(card : Card, key : String) extends ForcedAction                             // "once per run" marker
case class ReturnToHandEffect(card : Card) extends ForcedAction                                          // add an installed/scored card to its owner's hand
case class TrashTopEffect(f : Faction, n : Int, faceup : Boolean) extends ForcedAction                   // trash the top n cards of f's deck
case class PlayFreeEffect(card : Card) extends ForcedAction                                             // play an operation/event without spending a click (paying its cost)
case class ForfeitEffect(card : Card) extends ForcedAction
case class TurnFaceupEffect(card : Card) extends ForcedAction
case class TurnFacedownEffect(card : Card) extends ForcedAction
case class HostOnEffect(card : Card, host : Card, uninstalled : Boolean) extends ForcedAction           // host a card (not installed when uninstalled)
case object JackOutEffect extends ForcedAction
case class ExtraClicksEffect(f : Faction, n : Int) extends ForcedAction                                 // +n allotted clicks for f's next turn
case class SetMemoEffect(card : Card, memo : String) extends ForcedAction
case class SetTargetEffect(card : Card, target : |[Card]) extends ForcedAction
case class RevealCardsEffect(f : Faction, cards : $[Card]) extends ForcedAction
case class AddToScoreAreaEffect(card : Card, f : Faction) extends ForcedAction                          // add a card to f's score area (as an agenda)
case class MoveToRootEffect(card : Card, server : Server) extends ForcedAction                          // move an installed corp card to the root of another server
case class MoveIceOutermostEffect(card : Card, server : Server) extends ForcedAction
case class SwapInstalledWithHandEffect(installed : Card, hand : Card) extends ForcedAction               // swap an installed corp card with a card in HQ
case class CopyAbilityEffect(effects : $[ForcedAction]) extends ForcedAction                             // resolve a list of effects (wrapper)
case class AtRunEndEffect(effects : $[ForcedAction]) extends ForcedAction                                // queue effects for when the current run ends
case class RemoveCountersEffect(card : Card, kind : String) extends ForcedAction                          // remove all counters of a kind
case object EndActionPhaseEffect extends ForcedAction                                                     // terminal operations: the action phase ends
case class FlipSwitchTraceAction(card : Card) extends ForcedAction                                       // reduce the current trace base to 0 and continue
case class ExpendTriggerEffect(card : Card) extends ForcedAction
case class RunnerTrashInstalledEffect(kind : String, by : Faction, prompt : String) extends ForcedAction  // the Runner (or Corp) chooses an installed Runner card of a kind to trash
case class CorpTrashFromHandEffect(n : Int) extends ForcedAction                                          // the Corp chooses n cards in HQ to trash

// generic purposes
case class DerezPurpose() extends Purpose
case class RemoveFromGamePurpose() extends Purpose
case class BottomOfDeckPurpose(f : Faction) extends Purpose
case class TopOfDeckPurpose(f : Faction) extends Purpose
case class HostOnPurpose(host : Card, uninstalled : Boolean) extends Purpose
case class InstallPayingPurpose(discount : Int, server : |[Server], rez : Boolean) extends Purpose     // corp: install paying less (server None = choose); runner: install paying less
case class InstallPayingChooseServerPurpose(discount : Int, servers : $[Server], allowNew : Boolean, rez : Boolean) extends Purpose
case class TurnFaceupPurpose() extends Purpose
case class ForfeitPurpose() extends Purpose
case class TrashFromHandPurpose(f : Faction) extends Purpose
case class AddToScorePurpose(f : Faction) extends Purpose
case class ReturnToHandPurpose() extends Purpose                                                            // installed card -> owner's hand
case class RemoveCountersPurpose(kind : String) extends Purpose                                             // remove all counters of a kind from the chosen cards

// cost: trash an installed Runner card of a kind ("Program", "Resource", "Hardware", "" = any)
case class TrashInstalledCost(kind : String) extends Cost { def elem = ("trash an installed " + (kind == "").?("card").|(kind.toLowerCase)).hl }


trait ExtRules { game : Game =>

    var extraClicks : Map[Faction, Int] = Map()
    var corpPreventPassed = false
    var lastInstallFrom : Zone = LimboZone
    var ignoreRunCosts = false
    var mmActions : $[String] = $

    // threat level: any player has n or more agenda points
    def threat(n : Int) : Boolean = agendaPoints(Corp) >= n || agendaPoints(Runner) >= n

    // runner installed cards owned by the runner (excludes corp cards hosted on runner cards)
    def runnerInstalled : $[Card] = installedCards(Runner).%(c => owner(c).isRunner)

    def runnerInstalledOf(kind : String) : $[Card] = runnerInstalled.%(c => kind == "" || c.data.kind == kind)

    // subroutines of ice including those granted by other active cards
    def subs(ice : Card) : $[Sub] = impl(ice).subroutines(ice) ++ allActive./~(x => impl(x).extraSubroutines(x, ice))

    // additional costs to run a server
    def runCosts(s : Server) : $[Cost] = ignoreRunCosts.?($[Cost]()).|(allActive./~(x => impl(x).runCost(x, s)))

    // costs of a break ability used on k subroutines
    def breakCosts(br : BreakAbility, k : Int) : $[Cost] = br.perSub.?(br.costs./{ case CreditCost(n) => CreditCost(n * k) ; case x => x }).|(br.costs)

    def unprotected(s : Server) = ice(s).none

    def serverByName(n : String) : |[Server] = servers.%(_.name == n).single

    def lockdownActive = at(PlayZone(Corp)).exists(c => has(c, "Lockdown"))

    def canAffordInstall(c : Card, discount : Int) : Boolean = {
        val mu = c.data.isProgram.??(c.data.memory.|(0))
        credits(Runner) >= (installCost(c, None) - discount).atLeast(0) && (!c.data.isProgram || memoryFree >= mu || installedCards(Runner).%(isProgram).any)
    }

    // install a runner card from any zone, handling trojans (choose host) and MU (not enforced beyond the generic install)
    def installRunner(c : Card, discount : Int) : $[ForcedAction] =
        if (impl(c).mustBeHosted(c)) {
            val hosts = allIce.%(h => impl(c).hostRequirement(c, h))
            hosts.any.$(ChooseCardEffect(Runner, hosts, HostInstallPurpose(c, discount >= installCost(c, None)), "Choose a host for " + c.title, false))
        }
        else
            $(RunnerInstallPayingEffect(c, None, discount))

    def performExt : PartialFunction[Action, Continue] = {
        case ReplaceBreachEffect =>
            run.foreach(_.preventAccess = true)
            NextAction

        case BlockSuccessEffect =>
            run.foreach(_.blockSuccess = true)
            log("This run cannot be declared successful")
            NextAction

        case ForceEncounterEffect(c) =>
            run match {
                case Some(r) if !r.ended && isRezzed(c) && isInstalled(c) =>
                    r.forced +:= c
                    r.forcedPrev +:= ((r.ice, r.phase))
                    r.ice = |(c)
                    r.phase = EncounterPhase
                    r.bypass = false
                    state(c).brokenSubs = $
                    log(Runner, "encounters", c)
                    push(EncounterAction(c))
                    pushAll(impl(c).onEncounter(c))
                    trigger(EncounterIce(c))
                    NextAction
                case _ => NextAction
            }

        case RedirectRunEffect(n, jackOut) =>
            run match {
                case Some(r) if !r.ended =>
                    pending = pending.%!(a => a.is[RunStep] || a.is[PaidWindowAction])
                    r.position = n.atMost(ice(r.server).num).atLeast(0)
                    r.ice = None
                    r.phase = MovementPhase
                    log(Runner, "is moved to position", n.hl, "of", r.server)
                    if (jackOut)
                        push(JackOutOfferEffect, RunNextAction())
                    else
                        push(RunNextAction())
                    NextAction
                case _ => NextAction
            }

        case RunIgnoringCostsEffect(s, source) =>
            ignoreRunCosts = true
            val c = startRun(s, source, false)
            ignoreRunCosts = false
            c

        case RunnerInstallPayingEffect(c, h, d) =>
            if (location.get(c).exists(inPlayZone))
                NextAction
            else {
                val cost = (installCost(c, None) - d).atLeast(0)
                if (cost > 0) {
                    payCredits(Runner, cost, None)
                    log(Runner, "pays", cost.credits)
                }
                push(RunnerInstallEffect(c, h, true))
                NextAction
            }

        case CorpInstallPayingEffect(c, so, d, rez) =>
            if (location.get(c).exists(inPlayZone))
                NextAction
            else {
                val s : Server = so.|(newRemote())
                val cost = (installCost(c, |(s)) + rez.??(rezCost(c)) - d).atLeast(0)
                if (cost > 0) {
                    credits += Corp -> (credits(Corp) - cost)
                    log(Corp, "pays", cost.credits)
                }
                if (rez)
                    push(CorpInstallEffect(c, |(s), true), RezEffect(c, true))
                else
                    push(CorpInstallEffect(c, |(s), true))
                NextAction
            }

        case ChooseServerPayingEffect(c, d, l, allowNew, rez) =>
            Ask(Corp).group("Install " ~ cardElem(c)).add(l./(s => ServerChosenPayingAction(Corp, c, |(s), d, rez))).when(allowNew)(ServerChosenPayingAction(Corp, c, None, d, rez))

        case ServerChosenPayingAction(f, c, s, d, rez) =>
            push(CorpInstallPayingEffect(c, s, d, rez))
            NextAction

        case RandomPickEffect(f, l, n, p) =>
            val ll = l.%(c => location.contains(c))
            if (ll.none)
                NextAction
            else
                ShuffleTake[Card](ll, n.atMost(ll.num), RandomPickedAction(f, p, _), "Random")

        case RandomPickedAction(f, p, l) =>
            push(CardsChosenAction(f, l, p))
            NextAction

        case ChooseCodeEffect(f, codes, prompt, p) =>
            val l = codes.distinct.%(c => Cards.get(c).any)
            if (l.none)
                NextAction
            else
                Ask(f).group(prompt).add(l.sortBy(c => Cards(c).title)./(c => CodeChosenAction(f, c, p)))

        case CodeChosenAction(f, code, p) =>
            log(f, "names", Cards(code).title.hl)
            impl(p.card).chosen(p, $(Card(code, 0)))

        case LookAtEffect(f, l, prompt) =>
            val ll = l.%(c => location.contains(c))
            if (ll.none)
                NextAction
            else
                Ask(f).group(prompt ~ Break ~ ll./(cardElem).comma).add(LookDoneAction(f, prompt))

        case LookDoneAction(f, prompt) =>
            NextAction

        case ArrangeTopEffect(f, o, n) =>
            val l = deck(o).take(n)
            if (l.num <= 1)
                NextAction
            else
                ArrangeAction(f, o, l, $)

        case ArrangeAction(f, o, remaining, chosen) =>
            if (remaining.num <= 1) {
                val order = chosen ++ remaining
                zones += DeckZone(o) -> (order ++ deck(o).%!(order.has))
                log(f, "arranges the top", order.num.cards, "of", o.isCorp.?("R&D").|("the stack"))
                NextAction
            }
            else
                Ask(f).group("Arrange: " ~ chosen./(cardElem).comma ~ (chosen.any.?(" then ...").|(""))).add(remaining./(c => ArrangePickAction(f, o, c, remaining, chosen)))

        case ArrangePickAction(f, o, c, remaining, chosen) =>
            ArrangeAction(f, o, remaining.but(c), chosen :+ c)

        case MarkUsedRunEffect(c, k) =>
            state(c).usedRun :+= k
            NextAction

        case ReturnToHandEffect(c) =>
            if (location.contains(c)) {
                log(c, "is added to", owner(c).isCorp.?("HQ").|("the grip"))
                moveTo(c, HandZone(owner(c)))
                cleanupRemotes()
            }
            NextAction

        case TrashTopEffect(f, n, faceup) =>
            val l = deck(f).take(n)
            l.foreach { c =>
                if (f.isCorp) toArchives(c, faceup) else moveTo(c, DiscardZone(f))
            }
            if (l.any) {
                log(l.num.cards, "trashed from the top of", f.isCorp.?("R&D").|("the stack"))
                regInc("trashed-from-deck-" + f.name, l.num)
                trigger(DeckTrashed(f, l))
            }
            NextAction

        case PlayFreeEffect(c) =>
            val f = owner(c)
            if (!zone(c).is[HandZone] || credits(f) < playCost(c) || !impl(c).canPlay(c))
                NextAction
            else {
                payCredits(f, playCost(c), None)
                moveTo(c, PlayZone(f))
                state(c).seen = true
                impl(c).initialCounters(c).foreach { case (k, n) => addCounter(c, k, n) }
                log(f, "plays", c, "(" ~ playCost(c).credits ~ ")")
                regInc("played")
                push(PlayCleanupAction(c))
                pushAll(impl(c).onPlay(c))
                trigger(Played(f, c))
                NextAction
            }

        case ForfeitEffect(c) =>
            if (zone(c).is[ScoredZone]) {
                moveTo(c, DiscardZone(Corp))
                state(c).seen = true
                log(owner(c), "forfeits", c)
                push(CheckWinAction())
                trigger(Forfeited(c))
            }
            NextAction

        case TurnFaceupEffect(c) =>
            if (zone(c) == DiscardZone(Corp) && !state(c).seen) {
                state(c).seen = true
                log(c, "is turned faceup in Archives")
            }
            NextAction

        case TurnFacedownEffect(c) =>
            if (zone(c) == DiscardZone(Corp))
                state(c).seen = false
            NextAction

        case HostOnEffect(c, h, uninstalled) =>
            if (location.contains(h) && c != h && !allHosted(c).has(h)) {
                if (uninstalled && inPlayZone(zone(c)))
                    leavePlay(c)
                place(c, HostedZone(h))
                state(c).uninstalled = uninstalled
                if (owner(c).isCorp)
                    state(c).seen = true
                if (uninstalled)
                    state(c).rezzed = false
                log(c, "is hosted on", h)
                cleanupRemotes()
            }
            NextAction

        case JackOutEffect =>
            run.foreach { r =>
                if (!r.ended) {
                    r.jackedOut = true
                    log(Runner, "jacks out")
                    push(EndRunEffect)
                }
            }
            NextAction

        case ExtraClicksEffect(f, n) =>
            extraClicks += f -> (extraClicks.get(f).|(0) + n)
            log(f, "gets", ((n > 0).?("+").|("") + n).hl, "allotted click" + (n.abs != 1).??("s"), "next turn")
            NextAction

        case SetMemoEffect(c, m) =>
            state(c).memo = m
            NextAction

        case SetTargetEffect(c, t) =>
            state(c).target = t
            NextAction

        case RevealCardsEffect(f, l) =>
            val ll = l.%(c => location.contains(c))
            if (ll.any) {
                log(f, "reveals", ll./(cardElem).comma)
                ll.foreach(c => if (owner(c).isCorp && !zone(c).is[DeckZone]) state(c).seen = true)
                regInc("revealed-" + f.name)
                trigger(Revealed(f, ll))
            }
            NextAction

        case AddToScoreAreaEffect(c, f) =>
            moveTo(c, ScoredZone(f))
            state(c).seen = true
            log(f, "adds", c, "to the score area")
            push(CheckWinAction())
            NextAction

        case MoveToRootEffect(c, s) =>
            if (isInstalled(c) && servers.has(s) && !serverOf(c).has(s)) {
                val wasRezzed = isRezzed(c)
                val st = state(c)
                val counters = st.counters
                place(c, RootZone(s))
                st.rezzed = wasRezzed
                st.counters = counters
                log(hiddenCorpElem(c), "moves to the root of", s)
                cleanupRemotes()
            }
            NextAction

        case MoveIceOutermostEffect(c, s) =>
            if (zone(c).is[IceZone] && servers.has(s)) {
                val wasRezzed = isRezzed(c)
                val st = state(c)
                val counters = st.counters
                val hosted = st.hosted
                place(c, IceZone(s))
                st.rezzed = wasRezzed
                st.counters = counters
                log(hiddenCorpElem(c), "moves to the outermost position protecting", s)
                run.foreach(r => if (r.server == s) r.position = ice(s).num)
                cleanupRemotes()
            }
            NextAction

        case SwapInstalledWithHandEffect(a, b) =>
            (zone(a), zone(b)) match {
                case (z @ IceZone(s), HandZone(Corp)) =>
                    val l = ice(s)
                    val idx = l.indexOf(a)
                    leavePlay(a)
                    place(a, HandZone(Corp))
                    place(b, z)
                    zones += z -> (ice(s).but(b).take(idx) ++ $(b) ++ ice(s).but(b).drop(idx))
                    state(b).installedTurn = turn
                    state(b).rezzed = false
                    log(Corp, "swaps a piece of ice with a card from HQ")
                case (z @ RootZone(s), HandZone(Corp)) =>
                    leavePlay(a)
                    place(a, HandZone(Corp))
                    place(b, z)
                    state(b).installedTurn = turn
                    state(b).rezzed = false
                    log(Corp, "swaps a card in", s, "with a card from HQ")
                case _ =>
            }
            NextAction

        case CopyAbilityEffect(l) =>
            pushAll(l)
            NextAction

        case AtRunEndEffect(l) =>
            run match {
                case Some(r) if !r.ended => r.atEnd ++= l
                case _ => pushAll(l)
            }
            NextAction

        case RemoveCountersEffect(c, k) =>
            val n = counter(c, k)
            if (n > 0) {
                state(c).counters += k -> 0
                log(n.hl, k, "counters removed from", c)
                trigger(CounterAdded(c, k))
            }
            NextAction

        case EndActionPhaseEffect =>
            if (active.isCorp && clicks(Corp) > 0) {
                log(Corp, "loses the remaining", clicks(Corp).clicks, "(terminal)")
                clicks += Corp -> 0
            }
            NextAction

        case FlipSwitchTraceAction(c) =>
            traceBase = 0
            log("The base trace strength is reduced to", 0.hl)
            TraceCorpBoostAction(0, traceSuccess, traceFail)

        case ExpendTriggerEffect(c) =>
            trigger(ExpendResolved(c))
            NextAction

        case RunnerTrashInstalledEffect(kind, by, prompt) =>
            val l = runnerInstalledOf(kind)
            if (l.any)
                push(ChooseCardEffect(by, l, TrashPurpose(by), prompt, false))
            NextAction

        case CorpTrashFromHandEffect(n) =>
            if (hand(Corp).any)
                push(ChooseCardsEffect(Corp, hand(Corp), n.atMost(hand(Corp).num), n.atMost(hand(Corp).num), TrashFromHandPurpose(Corp), "Choose " + n + " card" + (n != 1).??("s") + " in HQ to trash"))
            NextAction
    }

    def resolveExtPurpose(f : Faction, l : $[Card], p : Purpose) : |[Continue] = p match {
        case DerezPurpose() =>
            l.foreach(c => push(DerezEffect(c)))
            |(NextAction)

        case RemoveFromGamePurpose() =>
            l.foreach(c => push(RemoveFromGameEffect(c)))
            |(NextAction)

        case BottomOfDeckPurpose(ff) =>
            l.foreach { c =>
                moveTo(c, DeckZone(ff))
                log(ff, "adds", (ff.isCorp).?("a card".hl).|(cardElem(c)), "to the bottom of", ff.isCorp.?("R&D").|("the stack"))
            }
            |(NextAction)

        case TopOfDeckPurpose(ff) =>
            l.foreach { c =>
                moveTo(c, DeckZone(ff), true)
                log(ff, "adds", (ff.isCorp && !state(c).seen).?("a card".hl).|(cardElem(c)), "to the top of", ff.isCorp.?("R&D").|("the stack"))
            }
            |(NextAction)

        case HostOnPurpose(h, uninstalled) =>
            l.single.foreach { c =>
                if (uninstalled)
                    push(HostOnEffect(c, h, true))
                else
                    push(RunnerInstallEffect(c, |(h), false))
            }
            |(NextAction)

        case InstallPayingPurpose(d, server, rez) =>
            l.single.foreach { c =>
                if (owner(c).isCorp) {
                    if (server.any || !(c.data.isIce || c.data.isUpgrade) && remotes.none)
                        push(CorpInstallPayingEffect(c, server, d, rez))
                    else
                        push(ChooseServerPayingEffect(c, d, (c.data.isIce || c.data.isUpgrade).?(servers).|(remotes), true, rez))
                }
                else
                    pushAll(installRunner(c, d))
            }
            |(NextAction)

        case InstallPayingChooseServerPurpose(d, ss, allowNew, rez) =>
            l.single.foreach { c =>
                push(ChooseServerPayingEffect(c, d, ss, allowNew, rez))
            }
            |(NextAction)

        case TurnFaceupPurpose() =>
            l.foreach(c => push(TurnFaceupEffect(c)))
            |(NextAction)

        case ForfeitPurpose() =>
            l.foreach(c => push(ForfeitEffect(c)))
            |(NextAction)

        case TrashFromHandPurpose(ff) =>
            l.foreach { c =>
                if (ff.isCorp) toArchives(c, false) else moveTo(c, DiscardZone(ff))
            }
            if (l.any) {
                log(ff, "trashes", l.num.cards, "from", ff.isCorp.?("HQ").|("the grip"))
                regInc("trashed-from-hand-" + ff.name, l.num)
                l.foreach(c => trigger(Trashed(c, ff, false)))
            }
            |(NextAction)

        case AddToScorePurpose(ff) =>
            l.foreach(c => push(AddToScoreAreaEffect(c, ff)))
            |(NextAction)

        case ReturnToHandPurpose() =>
            l.foreach(c => push(ReturnToHandEffect(c)))
            |(NextAction)

        case RemoveCountersPurpose(k) =>
            l.foreach(c => push(RemoveCountersEffect(c, k)))
            |(NextAction)

        case _ => None
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Shared helpers for the new sets
// ---------------------------------------------------------------------------------------------------------------------

abstract class NC(val code : String) extends CardImpl {
    def trashOnEmpty(self : Card, e : Event, kind : String)(implicit g : Game) : $[ForcedAction] = e match {
        case CounterAdded(c, k) if c == self && k == kind && g.counter(self, kind) == 0 && g.isInstalled(self) => $(TrashEffect(self, g.owner(self), false))
        case _ => $
    }

    def rfgOnEmpty(self : Card, e : Event, kind : String)(implicit g : Game) : $[ForcedAction] = e match {
        case CounterAdded(c, k) if c == self && k == kind && g.counter(self, kind) == 0 && g.isInstalled(self) => $(RemoveFromGameEffect(self))
        case _ => $
    }

    def thisServer(self : Card, s : Server)(implicit g : Game) = g.serverOf(self).has(s)

    def thisCardRun(self : Card)(implicit g : Game) = g.run.exists(_.source.has(self))

    def encountering(self : Card)(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice)

    def encounteringThis(self : Card)(implicit g : Game) = encountering(self).has(self)

    def installedBreakers(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && c.data.isIcebreaker).num

    def virtualResources(implicit g : Game) = g.activeCards(Runner).%(c => c.data.isResource && g.has(c, "Virtual")).num

    def installedThisTurn(self : Card)(implicit g : Game) = g.state(self).installedTurn == g.turn && g.active == g.owner(self)

    def bioroidBreak(self : Card)(implicit g : Game) = Ability("break 1 subroutine", $(LoseClickCost(1)), $(BioroidBreakEffect(self)), ok = g.run.exists(r => r.ice.has(self) && r.phase == EncounterPhase) && g.unbrokenSubs(self).any, reason = "not encountered")

    def mark(self : Card, key : String) = MarkUsedEffect(self, key)

    def stealthActive(implicit g : Game) = g.activeCards(Runner).exists(c => g.has(c, "Stealth"))

    def corpInstallable(c : Card) = c.data.isAgenda || c.data.isAsset || c.data.isUpgrade || c.data.isIce

    def trashSub(kind : String, label : String)(implicit g : Game) = Sub(label, RunnerTrashInstalledEffect(kind, Corp, label))
}
