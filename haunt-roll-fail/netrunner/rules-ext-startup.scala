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
import Helpers._


// ---------------------------------------------------------------------------------------------------------------------
// Generic mechanics added for the Startup sets (Elevation, Vantage Point): extra events, extra game state, reusable
// effects (self-performing pending-queue items) and a base class for card implementations.
// ---------------------------------------------------------------------------------------------------------------------

// ---- additional events ----------------------------------------------------------------------------------------------
case class ActionPhaseEnds(f : Faction) extends Event
case class ArchivesFlipped(n : Int) extends Event
case class BadPubGained(n : Int) extends Event
case class TagsRemoved(n : Int) extends Event
case class CreditsGained(f : Faction, n : Int, source : |[Card]) extends Event
case class IceBypassed(ice : Card) extends Event
case class Forfeited(card : Card) extends Event
case class RunRedirected(server : Server) extends Event
case class DiscardedToHandSize(f : Faction, cards : $[Card]) extends Event

// cards hosted on another card without being installed (Bling, Madani, Detente, Read-Write Share)
case class StashZone(host : Card) extends Zone


// ---- additional game state ------------------------------------------------------------------------------------------
trait StartupRules { game : Game =>
    var lastScoredAdvancement = 0                        // advancement counters on the last scored agenda
    var lastScoredInstalledTurn = -1                     // turn the last scored agenda was installed
    var lastStolenFrom : |[Server] = None
    var lastTrashedFrom : Zone = LimboZone
    var scoringAgenda : |[Card] = None                   // agenda whose scoring abilities are resolving
    var hostedBadPub : Map[Card, Int] = Map()            // bad publicity hosted on cards (Luana Campos)
    var protectedTitles : $[String] = $                  // Perfect Recall: titles that cannot be stolen or trashed this run
    var protectedRunKey : (Int, Int) = (-1, -1)
    var playedFrom : Map[Card, Zone] = Map()             // where an operation/event was played from (PlayCardEffect)
    var chromatophoreHosts : Map[Card, Card] = Map()     // Chromatophores -> host ice
    var linkedCard : Map[Card, Card] = Map()             // card remembered by another card (Beta Build)

    def threatLevel : Int = agendaPoints(Corp).atLeast(agendaPoints(Runner))
    def threat(n : Int) = threatLevel >= n

    def runKey : (Int, Int) = (turn, reg("runs"))

    def zoneServer(z : Zone) : |[Server] = z match {
        case HandZone(Corp) => |(HQ)
        case DeckZone(Corp) => |(RnD)
        case DiscardZone(Corp) => |(Archives)
        case RootZone(s) => |(s)
        case IceZone(s) => |(s)
        case HostedZone(h) => zoneServer(zone(h))
        case _ => None
    }

    def activeRun : |[Run] = run.%(r => !r.ended)

    def stashed(c : Card) : $[Card] = at(StashZone(c))

    def pumpDurationFor(b : Card, d : Duration) : Duration = allActive.foldLeft(d)((d, c) => impl(c).pumpDuration(c, b, d))

    // all additional steal costs of an agenda: its own, those imposed by other cards (extraStealCost / stealCostMod)
    def stealCostOf(c : Card) : $[Cost] = impl(c).accessCostPaidByRunner(c) ++ allActive./~(x => impl(x).extraStealCost(x, c) ++ impl(x).stealCostMod(x, c))

    def accessProtected(c : Card) : Boolean = allActive.exists(x => impl(x).protectsFromAccess(x, c))

    def protectedNow(title : String) = protectedRunKey == runKey && protectedTitles.has(title)

    // hosted credits on stealth cards (approximation of "spend credits only from stealth cards")
    def stealthCredits : Int = activeCards(Runner).%(c => has(c, "Stealth"))./(c => counter(c, "credit")).sum

    // paid abilities on Runner cards usable by the Corp, and abilities usable from HQ / Archives / grip / heap
    def extraAbilityOptions(f : Faction, ctx : String, allowClick : Boolean) : $[UserAction] = {
        def opts(l : $[Card], get : Card => $[Ability], make : (Card, Int) => UserAction) = l./~ { c =>
            get(c).indexed./~ { (a, i) =>
                if (a.click && !allowClick)
                    None
                else {
                    val clickOk = !a.click || (active == f && run.none && clicks(f) >= a.costs.of[ClickCost]./(_.n).sum.atLeast(1))
                    val onceOk = a.once.forall(k => !usedThisTurn(c, k))
                    val payOk = canPay(f, a.costs, |(c))
                    |(make(c, i).!(!a.ok, a.reason).!(!onceOk, "already used this turn").!(!payOk, "cannot pay").!(!clickOk, "needs a click"))
                }
            }
        }
        f.isCorp.??(opts(activeCards(Runner), c => impl(c).corpAbilities(c), (c, i) => UseCorpAbilityAction(f, c, i, ctx))) ++
        opts(hand(f) ++ discard(f), c => impl(c).inactiveAbilities(c), (c, i) => UseInactiveAbilityAction(f, c, i, ctx))
    }

    // move a card out of play that is not trashed (added to hand / stack / removed): cleans up stashed and hosted cards
    def uninstall(c : Card) {
        stashed(c).foreach(x => moveTo(x, DiscardZone(owner(x))))
        hostedBadPub.get(c).foreach { n => hostedBadPub -= c }
    }

    def insertAfterRunCleanup(a : ForcedAction) {
        val i = pending.indexWhere(_ == RunCleanupAction)
        if (i < 0)
            push(a)
        else
            pending = pending.take(i + 1) ++ $(a) ++ pending.drop(i + 1)
    }
}


// ---- user actions ---------------------------------------------------------------------------------------------------
case class UseCorpAbilityAction(self : Faction, card : Card, idx : Int, ctx : String) extends BaseAction(g => g.cardElem(card))(g => g.impl(card).corpAbilities(card)(g).lift(idx)./(a => a.label ~ (a.costs.any).?(" (" ~ Cost.desc(a.costs) ~ ")").|(Empty)).|("?")) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = g.useAbility(self, card, g.impl(card).corpAbilities(card).apply(idx), ctx, $)
}

case class UseInactiveAbilityAction(self : Faction, card : Card, idx : Int, ctx : String) extends BaseAction(g => g.cardElem(card) ~ " (" ~ g.zoneName(g.zone(card)) ~ ")")(g => g.impl(card).inactiveAbilities(card)(g).lift(idx)./(a => a.label ~ (a.costs.any).?(" (" ~ Cost.desc(a.costs) ~ ")").|(Empty)).|("?")) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = g.useAbility(self, card, g.impl(card).inactiveAbilities(card).apply(idx), ctx, $)
}

case class ServerPickAction(self : Faction, card : Card, server : |[Server], key : String) extends BaseAction(g => g.cardElem(card))(server./(_.elem).|("a new remote server".hl)) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(ServerPickedAction(card, server, key))
        NextAction
    }
}

case class SubChoiceAction(self : Faction, ice : Card, idx : Int) extends BaseAction(g => "Resolve a subroutine on " ~ g.cardElem(ice))(g => g.impl(ice).subroutines(ice)(g).lift(idx)./(_.label).|("?")) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(ResolveSubEffect(ice, idx))
        NextAction
    }
}

case class NumberPickAction(self : Faction, card : Card, n : Int, key : String) extends BaseAction(g => g.cardElem(card))(n.hl) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(NumberPickedAction(card, n, key))
        NextAction
    }
}


// ---- card actions and purposes handled by the Startup base class ------------------------------------------------------
trait StartupPurpose extends CardPurpose

case class ServerPickedAction(card : Card, server : |[Server], key : String) extends CardAction
case class NumberPickedAction(card : Card, n : Int, key : String) extends CardAction
case class InstallLoopAction(card : Card, f : Faction, remaining : Int, discount : Int, ignoreCost : Boolean, from : String, kinds : $[String]) extends CardAction
case class InstallLoopPurpose(card : Card, f : Faction, remaining : Int, discount : Int, ignoreCost : Boolean, from : String, kinds : $[String]) extends StartupPurpose
case class DiscountInstallAction(card : Card, target : Card, discount : Int, host : |[Card], server : |[Server]) extends CardAction
case class TrashFromHQPurpose(card : Card, faceup : Boolean) extends StartupPurpose
case class PlayPurpose(card : Card, payCost : Boolean, rfg : Boolean) extends StartupPurpose
case class RezDiscountPurpose(card : Card, discount : Int) extends StartupPurpose
case class ToDeckPurpose(card : Card, f : Faction, top : Boolean, shuffle : Boolean) extends StartupPurpose
case class RfgPurpose(card : Card) extends StartupPurpose
case class SubChoicePurpose(card : Card) extends StartupPurpose
case class StashPurpose(card : Card, facedown : Boolean) extends StartupPurpose
case class UninstallToHandPurpose(card : Card) extends StartupPurpose
case class LookPurpose(card : Card) extends StartupPurpose
case class RevealPurpose(card : Card) extends StartupPurpose
case class TopOrBottomPurpose(card : Card) extends StartupPurpose
case class ToHandTopPurpose(card : Card, f : Faction) extends StartupPurpose
case class RunnerTrashPurpose(card : Card) extends StartupPurpose
case class RandomHandTrashedAction(card : Card, f : Faction, reveal : Boolean, shuffled : $[Card]) extends ShuffledAction[Card] with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(RandomHandTrashedDoneAction(card, f, reveal, shuffled))
        NextAction
    }
}
case class RandomHandTrashedDoneAction(card : Card, f : Faction, reveal : Boolean, cards : $[Card]) extends CardAction


// ---- generic self-performing effects ----------------------------------------------------------------------------------

// play an operation / event from any zone without spending a click
case class PlayCardEffect(f : Faction, card : Card, payCost : Boolean, rfg : Boolean) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        val c = card
        if (!g.location.contains(c))
            NextAction
        else {
            val cost = payCost.?(g.playCost(c)).|(0)
            if (cost > g.credits(f))
                NextAction
            else {
                val from = g.zone(c)
                g.payCredits(f, cost, None)
                g.playedFrom += c -> from
                g.moveTo(c, PlayZone(f))
                g.state(c).seen = true
                g.impl(c).initialCounters(c).foreach { case (k, n) => g.addCounter(c, k, n) }
                g.log(f, "plays", c, "(" ~ cost.credits ~ ")", from match {
                    case DiscardZone(_) => "from " + (f.isCorp.?("Archives").|("the heap"))
                    case StashZone(_) => "from a hosted position"
                    case _ => ""
                })
                g.regInc("played")
                g.push(rfg.?(PlayCleanupRfgAction(c)).|(PlayCleanupAction(c)))
                g.pushAll(g.impl(c).onPlay(c))
                g.trigger(Played(f, c))
                NextAction
            }
        }
    }
}

case class PlayCleanupRfgAction(card : Card) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (g.zone(card).is[PlayZone]) {
            if (g.run.exists(r => r.source.has(card) && !r.ended))
                g.enqueue(PlayCleanupRfgAction(card))
            else {
                g.moveTo(card, RemovedZone(g.owner(card)))
                g.log(card, "is removed from the game")
            }
        }
        NextAction
    }
}

// forfeit an agenda
case class ForfeitEffect(card : Card) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (g.zone(card).is[ScoredZone]) {
            val own = g.impl(card).triggers(card, Forfeited(card))
            g.log(Corp, "forfeits", card)
            g.toArchives(card, true)
            g.push(CheckWinAction)
            g.trigger(Forfeited(card))
            g.pushAll(own)
        }
        NextAction
    }
}

// trash n random cards from a hand
case class TrashRandomFromHandEffect(source : Card, f : Faction, n : Int, reveal : Boolean) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        val h = g.hand(f)
        val m = n.atMost(h.num)
        if (m <= 0)
            NextAction
        else
            ShuffleTake[Card](h, m, RandomHandTrashedAction(source, f, reveal, _), "Random trash")
    }
}

// change the attacked server of the current run
case class RedirectRunEffect(server : Server, position : Int, restart : Boolean) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.run.foreach { r =>
            if (!r.ended) {
                val nr = new Run(server, r.source, r.clickRun)
                nr.position = position
                nr.phase = restart.?(ApproachIcePhase : RunPhase).|(r.phase)
                nr.ice = None
                nr.successful = r.successful
                nr.didSteal = r.didSteal
                nr.didTrash = r.didTrash
                nr.didAccess = r.didAccess
                nr.accessed = r.accessed
                nr.accessBonus = r.accessBonus
                nr.cannotStealOrTrash = r.cannotStealOrTrash
                nr.preventAccess = r.preventAccess
                nr.subsFired = r.subsFired
                nr.cannotJackOut = r.cannotJackOut
                nr.credits = r.credits
                nr.accessCount = r.accessCount
                r.ice.foreach { i =>
                    g.state(i).brokenSubs = $
                    g.state(i).strengthEncounter = 0
                }
                g.installedCards(Runner).foreach(b => g.state(b).strengthEncounter = 0)
                g.run = |(nr)
                if (!g.runsThisTurn.has(server))
                    g.runsThisTurn :+= server
                g.log("The attacked server is now", server)
                if (restart) {
                    g.flushRun()
                    g.windowPassed = $
                    g.push(RunNextAction)
                }
                g.trigger(RunRedirected(server))
            }
        }
        NextAction
    }
}

// resolve a subroutine of a piece of ice (outside of its own encounter)
case class ResolveSubEffect(ice : Card, idx : Int) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.impl(ice).subroutines(ice).lift(idx).foreach { sub =>
            g.log("Subroutine", sub.label.hl, "on", ice, "resolves")
            g.run.foreach(_.subsFired += 1)
            g.push(sub.effect)
        }
        NextAction
    }
}

// the Corp chooses a subroutine of a rezzed piece of ice to resolve
case class ChooseSubEffect(f : Faction, ice : Card, unbrokenOnly : Boolean, optional : Boolean) extends ForcedAction with SelfPerform with Reaskable {
    def perform(soft : Void)(implicit g : Game) = {
        val l = unbrokenOnly.?(g.unbrokenSubs(ice)).|(g.impl(ice).subroutines(ice).indices.$)
        if (l.none || !g.isRezzed(ice))
            NextAction
        else
            Ask(f).add(l./(i => SubChoiceAction(f, ice, i))).when(optional)(DeclineChoiceAction(f, LookPurpose(ice)))
    }
}

// choose a server for a card-specific purpose
case class ChooseServerForAction(f : Faction, card : Card, servers : $[Server], allowNew : Boolean, key : String, prompt : String) extends ForcedAction with SelfPerform with Reaskable {
    def perform(soft : Void)(implicit g : Game) = {
        val l = servers.%(s => g.servers.has(s))
        if (l.none && !allowNew)
            NextAction
        else
            Ask(f).group(prompt).add(l./(s => ServerPickAction(f, card, |(s), key))).when(allowNew)(ServerPickAction(f, card, None, key))
    }
}

// choose a number
case class ChooseNumberEffect(f : Faction, card : Card, min : Int, max : Int, key : String, prompt : String) extends ForcedAction with SelfPerform with Reaskable {
    def perform(soft : Void)(implicit g : Game) = {
        if (max < min)
            NextAction
        else
            Ask(f).group(prompt).add(min.to(max).$./(n => NumberPickAction(f, card, n, key)))
    }
}

// rez a card paying n less
case class DiscountRezEffect(card : Card, discount : Int) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (!g.isRezzed(card) && g.isInstalled(card)) {
            val cost = (g.rezCost(card) - discount).atLeast(0)
            if (g.credits(Corp) >= cost) {
                g.credits += Corp -> (g.credits(Corp) - cost)
                g.log(Corp, "pays", cost.credits, "(" ~ discount.hl ~ " less)")
                g.push(RezEffect(card, true))
            }
        }
        NextAction
    }
}

case class ExtraSubtypesEffect(card : Card, subtypes : $[String], add : Boolean) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        val s = g.state(card)
        if (add)
            s.extraSubtypes = (s.extraSubtypes ++ subtypes).distinct
        else
            s.extraSubtypes = s.extraSubtypes.%!(subtypes.has)
        NextAction
    }
}

case class SetCounterEffect(card : Card, kind : String, n : Int) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.state(card).counters += kind -> n
        NextAction
    }
}

// swap an installed piece of ice with a card from HQ or Archives
case class SwapIceWithEffect(installed : Card, other : Card) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        (g.zone(installed), g.zone(other)) match {
            case (IceZone(s), z) if (z.is[HandZone] || z.is[DiscardZone]) && other.data.isIce =>
                val l = g.ice(s)
                val idx = l.indexOf(installed)
                val wasRezzed = g.isRezzed(installed)
                g.moveTo(installed, z)
                g.place(other, IceZone(s))
                val ll = g.ice(s).but(other)
                g.zones += IceZone(s) -> (ll.take(idx) ++ $(other) ++ ll.drop(idx))
                g.state(other).installedTurn = g.turn
                g.state(other).rezzed = false
                g.run.foreach(r => if (r.ice.has(installed)) r.ice = |(other))
                g.log(Corp, "swaps", wasRezzed.?(g.cardElem(installed)).|("a piece of ice".hl), "with a piece of ice from", z.is[HandZone].?("HQ").|("Archives"))
                g.trigger(Installed(Corp, other))
            case _ =>
        }
        NextAction
    }
}

// Runner accesses one card from HQ at random outside of a run (Detente)
case class AccessRandomHQEffect(source : Card) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (g.hand(Corp).none)
            NextAction
        else
            ShuffleTake[Card](g.hand(Corp), 1, OutsideAccessAction(source, _), "HQ access")
    }
}

case class OutsideAccessAction(card : Card, shuffled : $[Card]) extends ShuffledAction[Card] with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(OutsideAccessDoneAction(card, shuffled))
        NextAction
    }
}
case class OutsideAccessDoneAction(card : Card, cards : $[Card]) extends CardAction
case class OutsideAccessTrashAction(card : Card, target : Card) extends CardAction


// ---------------------------------------------------------------------------------------------------------------------
// Base class for the Startup set cards: shared helpers and generic action / purpose handling
// ---------------------------------------------------------------------------------------------------------------------

abstract class Startup(val code : String) extends CardImpl {
    def trashOnEmpty(self : Card, e : Event, kind : String)(implicit g : Game) : $[ForcedAction] = e match {
        case CounterAdded(c, k) if c == self && k == kind && g.counter(self, kind) == 0 && g.isInstalled(self) => $(TrashEffect(self, g.owner(self), false))
        case _ => $
    }

    def thisServer(self : Card, s : Server)(implicit g : Game) = g.serverOf(self).has(s)

    def thisCardRun(self : Card)(implicit g : Game) = g.run.exists(_.source.has(self))

    def inCorpScore(self : Card)(implicit g : Game) = g.zone(self) == ScoredZone(Corp) || (self.code == "09021" && g.zone(self) == PlayZone(Corp))

    def inRunnerScore(self : Card)(implicit g : Game) = g.zone(self) == ScoredZone(Runner)

    def encountering(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && r.phase == EncounterPhase && r.ice.has(self))

    def runOnThisServer(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && thisServer(self, r.server))

    def encounteredIce(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice)

    def bioroidBreak(self : Card, n : Int, clicks : Int)(implicit g : Game) : $[Ability] =
        $(Ability("break " + n + " subroutine" + (n != 1).??("s"), $(LoseClickCost(clicks)), n.times(BioroidBreakEffect(self) : ForcedAction), ok = encountering(self) && g.unbrokenSubs(self).any, reason = "not encountered"))

    def flipped(self : Card)(implicit g : Game) = g.counter(self, "flip") > 0

    def flip(self : Card, back : String)(implicit g : Game) : ForcedAction = FlipAction(self, back)

    def corpInstallable(c : Card)(implicit g : Game) = c.data.isAgenda || c.data.isAsset || c.data.isUpgrade || c.data.isIce

    // can the Runner install c right now, with a discount
    def runnerCanInstall(c : Card, discount : Int)(implicit g : Game) : Boolean = {
        val ok = c.data.isProgram || c.data.isHardware || c.data.isResource
        val cost = (g.installCost(c, None) - discount).atLeast(0)
        val mu = c.data.isProgram.??(c.data.memory.|(0))
        val hosts = g.impl(c).mustBeHosted(c).?(g.allIce.%(h => g.impl(c).hostRequirement(c, h))).|($(c))
        ok && g.credits(Runner) >= cost && (!c.data.isProgram || g.memoryFree >= mu) && hosts.any
    }

    def runnerInstallable(l : $[Card], discount : Int)(implicit g : Game) = l.%(c => runnerCanInstall(c, discount))

    def cannotBeTrashedByRunner(c : Card)(implicit g : Game) = g.impl(c).untrashableByRunner(c)

    // Dividends N: agenda counters for excess advancement counters when scored
    def dividends(self : Card, k : Int)(implicit g : Game) : $[ForcedAction] = {
        val excess = g.lastScoredAdvancement - g.advancementRequirement(self)
        (excess > 0).$(AddCounterEffect(self, "agenda", k * excess))
    }

    // double operations / events: additional click cost (Synchrocyclotron waives the first Corp double each turn)
    def doubleFree(f : Faction)(implicit g : Game) = f.isCorp && g.reg("double") == 0 && g.activeCards(Corp).exists(c => c.code == "36027" && g.isRezzed(c))
    def doubleCanPlay(f : Faction)(implicit g : Game) = g.clicks(f) >= 2 || doubleFree(f)
    def doubleCost(f : Faction)(implicit g : Game) : $[ForcedAction] = $(RegisterEffect("double", 1)) ++ (!doubleFree(f)).$(LoseClicksEffect(f, 1))

    final override def perform(a : CardAction)(implicit g : Game) : Continue = StartupExt.perform(this, a).|(performCard(a))

    def performCard(a : CardAction)(implicit g : Game) : Continue = throw new Error("unhandled card action " + a)

    final override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = p match {
        case p : StartupPurpose => StartupExt.chosen(this, p, cards)
        case _ => chosenCard(p, cards)
    }

    def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = throw new Error("unhandled card purpose " + p)

    // hook for the generic loops: called when a card is installed by InstallLoop / DiscountInstall
    def onLoopInstall(self : Card, installed : Card)(implicit g : Game) : $[ForcedAction] = $
}

case class FlipAction(card : Card, back : String) extends CardAction


object StartupExt {
    def perform(impl : Startup, a : CardAction)(implicit g : Game) : |[Continue] = a match {
        case FlipAction(self, back) =>
            val f = g.counter(self, "flip") > 0
            g.state(self).counters += "flip" -> f.?(0).|(1)
            g.log(g.owner(self), "flips the identity to", f.?(self.title.hl).|(back.hl))
            |(NextAction)

        case InstallLoopAction(self, f, remaining, discount, ignoreCost, from, kinds) =>
            if (remaining > 0) {
                val pool : $[Card] = from match {
                    case "hand" => g.hand(f)
                    case "discard" => g.discard(f)
                    case "stash" => g.stashed(self)
                    case _ => $
                }
                val l = pool.%(c => kinds.none || kinds.has(c.data.kind)).%(c => f.isCorp.?(impl.corpInstallable(c)).|(ignoreCost || impl.runnerCanInstall(c, discount)))
                if (l.any)
                    g.push(ChooseCardEffect(f, l, InstallLoopPurpose(self, f, remaining, discount, ignoreCost, from, kinds), "Install a card (" + remaining + " remaining)", true))
            }
            |(NextAction)

        case DiscountInstallAction(self, c, discount, host, server) =>
            val f = g.owner(c)
            if (!g.location.contains(c) || g.location.get(c).exists(g.inPlayZone))
                |(NextAction)
            else {
                val cost = (g.installCost(c, server) - discount).atLeast(0)
                if (f.isCorp)
                    g.credits += Corp -> (g.credits(Corp) - cost)
                else
                    g.payCredits(Runner, cost, None)
                g.log(f, "pays", cost.credits, "(" ~ discount.hl ~ " less)")
                g.pushAll(impl.onLoopInstall(self, c))
                if (f.isCorp)
                    g.push(CorpInstallEffect(c, server, true))
                else
                    g.push(RunnerInstallEffect(c, host, true))
                |(NextAction)
            }

        case DiscountInstallPayAction(self, target, discount) =>
            val cost = (g.installCost(target, None) - discount).atLeast(0)
            g.payCredits(Runner, cost, None)
            g.log(Runner, "pays", cost.credits, "(" ~ discount.hl ~ " less)")
            |(NextAction)

        case ServerPickedAction(self, server, key) if key.startsWith("loop-install:") =>
            val parts = key.split(':')
            val c = Card(parts(1), parts(2).toInt)
            val discount = parts(3).toInt
            val ignore = parts(4).toBoolean
            if (ignore)
                g.push(CorpInstallEffect(c, server, true))
            else
                g.push(DiscountInstallAction(self, c, discount, None, server))
            |(NextAction)

        case RandomHandTrashedDoneAction(self, f, reveal, l) =>
            l.foreach { c =>
                if (f.isCorp) {
                    if (reveal) g.log(Corp, "reveals and trashes", c, "from HQ") else g.log(Corp, "trashes a card from HQ")
                    g.toArchives(c, reveal)
                }
                else {
                    g.log(Runner, "trashes", c, "from the grip")
                    g.moveTo(c, DiscardZone(Runner))
                }
            }
            |(NextAction)

        case OutsideAccessDoneAction(self, l) =>
            l.single match {
                case Some(c) =>
                    g.log(Runner, "accesses", c, "from HQ")
                    g.state(c).seen = true
                    g.trigger(Accessed(c))
                    if (c.data.isAgenda) {
                        val extra = g.stealCostOf(c)
                        if (g.canPay(Runner, extra, None) && !g.accessProtected(c)) {
                            g.payCosts(Runner, extra, None)
                            g.stealAgenda(c)
                        }
                        else
                            g.log(Runner, "cannot steal", c)
                    }
                    else {
                        g.trashCost(c) match {
                            case Some(tc) if g.credits(Runner) >= tc && !g.accessProtected(c) =>
                                g.push(OptionalEffect(Runner, "Pay " + tc + " credits to trash the accessed card?", $(OutsideAccessTrashAction(self, c)), $))
                            case _ =>
                        }
                        g.pushAll(g.impl(c).onAccess(c))
                    }
                case None =>
            }
            |(NextAction)

        case OutsideAccessTrashAction(self, c) =>
            if (g.zone(c) == HandZone(Corp)) {
                val tc = g.trashCost(c).|(0)
                g.payCredits(Runner, tc, None)
                g.log(Runner, "pays", tc.credits, "to trash", c)
                g.trashCard(c, Runner, true)
            }
            |(NextAction)

        case _ => None
    }

    def chosen(impl : Startup, p : StartupPurpose, cards : $[Card])(implicit g : Game) : Continue = {
        p match {
            case InstallLoopPurpose(self, f, remaining, discount, ignoreCost, from, kinds) =>
                cards.single.foreach { c =>
                    g.push(InstallLoopAction(self, f, remaining - 1, discount, ignoreCost, from, kinds))
                    if (f.isCorp) {
                        val l = (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes)
                        g.push(ChooseServerForAction(Corp, self, l, true, "loop-install:" + c.code + ":" + c.n + ":" + discount + ":" + ignoreCost, "Install " + c.title.filter(_ < 128)))
                    }
                    else if (g.impl(c).mustBeHosted(c)) {
                        val hosts = g.allIce.%(h => g.impl(c).hostRequirement(c, h))
                        if (hosts.any)
                            g.push(ChooseCardEffect(Runner, hosts, HostInstallPurpose(c, true), "Choose a host", false))
                        if (!ignoreCost)
                            g.push(DiscountInstallPayAction(self, c, discount))
                    }
                    else {
                        g.pushAll(impl.onLoopInstall(self, c))
                        if (ignoreCost)
                            g.push(RunnerInstallEffect(c, None, true))
                        else
                            g.push(DiscountInstallAction(self, c, discount, None, None))
                    }
                }
                NextAction

            case TrashFromHQPurpose(self, faceup) =>
                cards.foreach { c =>
                    if (faceup) g.log(Corp, "trashes", c, "from HQ") else g.log(Corp, "trashes a card from HQ")
                    g.trashCard(c, Corp, false)
                    if (!faceup) g.state(c).seen = false
                }
                NextAction

            case PlayPurpose(self, payCost, rfg) =>
                cards.single.foreach(c => g.push(PlayCardEffect(g.owner(c), c, payCost, rfg)))
                NextAction

            case RezDiscountPurpose(self, discount) =>
                cards.single.foreach(c => g.push(DiscountRezEffect(c, discount)))
                NextAction

            case ToDeckPurpose(self, f, top, shuffle) =>
                cards.foreach { c =>
                    if (g.isInstalled(c)) g.uninstall(c)
                    g.moveTo(c, DeckZone(f), top)
                    g.log(c, "is added to the", top.?("top").|("bottom"), "of", f.isCorp.?("R&D").|("the stack"))
                }
                if (shuffle && cards.any)
                    g.push(ShuffleDeckEffect(f))
                NextAction

            case RfgPurpose(self) =>
                cards.foreach { c =>
                    if (g.isInstalled(c)) g.uninstall(c)
                    g.push(RemoveFromGameEffect(c))
                }
                NextAction

            case SubChoicePurpose(self) =>
                cards.single.foreach(c => g.push(ChooseSubEffect(Corp, c, false, false)))
                NextAction

            case StashPurpose(self, facedown) =>
                cards.foreach { c =>
                    g.moveTo(c, StashZone(self))
                    g.state(c).facedown = facedown
                    g.state(c).seen = !facedown
                    g.log(facedown.?("A card".hl).|(g.cardElem(c)), "is hosted on", self)
                }
                NextAction

            case UninstallToHandPurpose(self) =>
                cards.foreach { c =>
                    val o = g.owner(c)
                    g.uninstall(c)
                    g.moveTo(c, HandZone(o))
                    g.log(c, "is added to", o.isCorp.?("HQ").|("the grip"))
                    g.cleanupRemotes()
                }
                NextAction

            case LookPurpose(self) =>
                NextAction

            case RevealPurpose(self) =>
                cards.foreach(c => g.push(RevealEffect(g.owner(c), c)))
                NextAction

            case TopOrBottomPurpose(self) =>
                cards.single.foreach { c =>
                    g.push(ChooseOneEffect(Corp, "Add the card to the top or the bottom of R&D?", $(
                        Alt("Top of R&D", $(MoveEffect(c, DeckZone(Corp), true))),
                        Alt("Bottom of R&D", $(MoveEffect(c, DeckZone(Corp), false))))))
                }
                NextAction

            case ToHandTopPurpose(self, f) =>
                cards.foreach { c =>
                    g.log(f, "adds", (f.isCorp).?("a card".hl).|(g.cardElem(c)), "to", f.isCorp.?("HQ").|("the grip"))
                    g.moveTo(c, HandZone(f))
                }
                NextAction

            case RunnerTrashPurpose(self) =>
                cards.foreach(c => g.trashCard(c, Runner, false))
                NextAction
        }
    }
}

case class DiscountInstallPayAction(card : Card, target : Card, discount : Int) extends CardAction
